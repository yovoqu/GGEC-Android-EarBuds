package com.ambiq.blevideoplayer

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineStream
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** 识别器生命周期与模型加载状态。 */
enum class AsrStatus {
    IDLE,
    LOADING,
    READY,
    FINISHED
}

/**
 * sherpa-onnx 流式识别封装。音频入队不阻塞，模型加载、推理和释放均在专用线程。
 * 每次 [start] 创建独立识别会话，实时流和本地文件使用完全相同的处理路径。
 */
class SpeechRecognizer(context: Context) {

    companion object {
        private const val TAG = "SpeechRecognizer"
        private const val MAX_QUEUED_SAMPLES = 16_000 * 30
    }

    interface Listener {
        fun onAsrStatus(status: AsrStatus)
        fun onAsrPartial(text: String)
        fun onAsrFinal(text: String)
        fun onAsrError(message: String)
    }

    var listener: Listener? = null

    private val assetManager = context.applicationContext.assets
    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val samplesQueue = ArrayDeque<FloatArray>()
    private var queuedSamples = 0

    @Volatile
    private var running = false

    @Volatile
    private var finishRequested = false
    private var worker: Thread? = null

    @Volatile
    private var sessionId = 0

    @Volatile
    var status = AsrStatus.IDLE
        private set

    val droppedSamples = AtomicLong(0)

    fun start(model: AsrModel) {
        stop()
        lock.withLock {
            samplesQueue.clear()
            queuedSamples = 0
        }
        finishRequested = false
        running = true
        val id = ++sessionId
        worker = Thread { recognitionLoop(model, id) }.apply {
            name = "speech-recognizer"
            start()
        }
    }

    fun acceptPcm(samples: FloatArray) {
        if (!running || finishRequested || samples.isEmpty()) return
        lock.withLock {
            while (queuedSamples + samples.size > MAX_QUEUED_SAMPLES && samplesQueue.isNotEmpty()) {
                queuedSamples -= samplesQueue.removeFirst().size
                droppedSamples.incrementAndGet()
            }
            if (samples.size <= MAX_QUEUED_SAMPLES) {
                samplesQueue.addLast(samples)
                queuedSamples += samples.size
                notEmpty.signal()
            } else {
                droppedSamples.addAndGet(samples.size.toLong())
            }
        }
    }

    /** 本地文件读完后调用；等待队列消费完并输出最终结果。 */
    fun finishInput() {
        if (!running) return
        finishRequested = true
        lock.withLock { notEmpty.signalAll() }
    }

    fun stop() {
        sessionId++
        running = false
        finishRequested = false
        lock.withLock { notEmpty.signalAll() }
        worker?.join(2_000)
        worker = null
        lock.withLock {
            samplesQueue.clear()
            queuedSamples = 0
        }
        status = AsrStatus.IDLE
    }

    private fun takeSamples(): FloatArray? = lock.withLock {
        if (samplesQueue.isEmpty()) notEmpty.await(20, TimeUnit.MILLISECONDS)
        samplesQueue.pollFirst()?.also { queuedSamples -= it.size }
    }

    private fun isActive(id: Int): Boolean = running && id == sessionId

    private fun recognitionLoop(model: AsrModel, id: Int) {
        var recognizer: OnlineRecognizer? = null
        var stream: OnlineStream? = null
        try {
            updateStatus(AsrStatus.LOADING, id)
            val nativeRecognizer = OnlineRecognizer(
                assetManager = assetManager,
                config = model.recognizerConfig()
            )
            recognizer = nativeRecognizer
            if (!isActive(id)) return
            val nativeStream = nativeRecognizer.createStream()
            stream = nativeStream
            updateStatus(AsrStatus.READY, id)

            while (isActive(id)) {
                val samples = takeSamples()
                if (!isActive(id)) break
                if (samples != null) {
                    nativeStream.acceptWaveform(samples, PcmNormalizer.TARGET_SAMPLE_RATE)
                    processStream(nativeRecognizer, nativeStream, model)
                } else if (finishRequested) {
                    nativeStream.inputFinished()
                    val remaining = processStream(nativeRecognizer, nativeStream, model)
                    if (remaining.isNotBlank()) listener?.onAsrFinal(remaining)
                    listener?.onAsrPartial("")
                    updateStatus(AsrStatus.FINISHED, id)
                    break
                }
            }
        } catch (e: Exception) {
            if (isActive(id)) {
                Log.e(TAG, "语音识别异常", e)
                listener?.onAsrError("语音识别异常: ${e.message}")
            }
        } finally {
            try {
                stream?.release()
            } catch (_: Exception) {
            }
            try {
                recognizer?.release()
            } catch (_: Exception) {
            }
            if (id == sessionId && status != AsrStatus.FINISHED) status = AsrStatus.IDLE
        }
    }

    /** 返回尚未因 endpoint 提交的当前文本。 */
    private fun processStream(
        recognizer: OnlineRecognizer,
        stream: OnlineStream,
        model: AsrModel
    ): String {
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        val endpoint = recognizer.isEndpoint(stream)
        if (endpoint && model == AsrModel.PARAFORMER) {
            stream.acceptWaveform(FloatArray((0.8 * 16_000).toInt()), 16_000)
            while (recognizer.isReady(stream)) recognizer.decode(stream)
        }
        val text = recognizer.getResult(stream).text
        if (endpoint) {
            if (text.isNotBlank()) listener?.onAsrFinal(text)
            recognizer.reset(stream)
            listener?.onAsrPartial("")
            return ""
        }
        listener?.onAsrPartial(text)
        return text
    }

    private fun updateStatus(value: AsrStatus, id: Int) {
        if (id != sessionId) return
        status = value
        listener?.onAsrStatus(value)
    }
}
