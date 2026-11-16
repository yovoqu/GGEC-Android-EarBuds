package com.ambiq.blevideoplayer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/** BLE 音频输入格式。AAC 必须是 ADTS 流，Opus 必须一次 Notify 一个完整包。 */
enum class BleAudioCodec(val displayName: String) {
    PCM("PCM 16bit"),
    AAC("AAC ADTS"),
    OPUS("Opus")
}

data class AudioDecoderConfig(
    val codec: BleAudioCodec,
    val sampleRate: Int = 16_000,
    val channels: Int = 1
)

/**
 * 非阻塞 BLE 音频队列 + MediaCodec 解码 + AudioTrack 播放 + 16 kHz 识别 PCM 输出。
 * 编码格式切换时必须重新 [start]，以便清空旧码流并重建解码器。
 */
class AudioStreamDecoder {

    companion object {
        private const val TAG = "AudioStreamDecoder"
        private const val MAX_QUEUE_BYTES = 1024 * 1024
        private const val INPUT_TIMEOUT_US = 10_000L
    }

    interface Listener {
        fun onAudioSamples(samples: FloatArray)
        fun onAudioError(message: String)
    }

    var listener: Listener? = null

    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val chunks = ArrayDeque<ByteArray>()
    private var queueBytes = 0
    private val normalizer = PcmNormalizer()

    @Volatile
    private var running = false

    @Volatile
    private var muted = false
    private var decodeThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    val droppedChunks = AtomicLong(0)

    fun queuedBytes(): Int = lock.withLock { queueBytes }

    fun setMuted(value: Boolean) {
        muted = value
    }

    fun enqueue(data: ByteArray) {
        if (!running || data.isEmpty()) return
        lock.withLock {
            while (queueBytes + data.size > MAX_QUEUE_BYTES && chunks.isNotEmpty()) {
                queueBytes -= chunks.removeFirst().size
                droppedChunks.incrementAndGet()
            }
            if (data.size <= MAX_QUEUE_BYTES) {
                chunks.addLast(data.copyOf())
                queueBytes += data.size
                notEmpty.signal()
            } else {
                droppedChunks.incrementAndGet()
            }
        }
    }

    fun start(config: AudioDecoderConfig) {
        stop()
        clearQueue()
        normalizer.reset()
        running = true
        decodeThread = Thread { decodeLoop(config) }.apply {
            name = "audio-decode"
            start()
        }
    }

    fun stop() {
        running = false
        lock.withLock { notEmpty.signalAll() }
        decodeThread?.join(1_000)
        decodeThread = null
        clearQueue()
    }

    private fun clearQueue() = lock.withLock {
        chunks.clear()
        queueBytes = 0
    }

    private fun takeChunk(timeoutMs: Long): ByteArray? = lock.withLock {
        if (chunks.isEmpty()) notEmpty.await(timeoutMs, TimeUnit.MILLISECONDS)
        chunks.pollFirst()?.also { queueBytes -= it.size }
    }

    private fun decodeLoop(config: AudioDecoderConfig) {
        try {
            if (config.codec == BleAudioCodec.PCM) {
                startAudioTrack(config.sampleRate, config.channels)
                pcmLoop(config)
            } else {
                compressedLoop(config)
            }
        } catch (e: Exception) {
            if (running) {
                Log.e(TAG, "音频解码异常", e)
                listener?.onAudioError("音频解码异常: ${e.message}")
            }
        } finally {
            running = false
            audioTrack?.run {
                try {
                    stop()
                } catch (_: Exception) {
                }
                release()
            }
            audioTrack = null
        }
    }

    private fun pcmLoop(config: AudioDecoderConfig) {
        var carry = -1
        while (running) {
            val chunk = takeChunk(20) ?: continue
            var data = chunk
            if (carry >= 0) {
                data = ByteArray(chunk.size + 1)
                data[0] = carry.toByte()
                chunk.copyInto(data, 1)
                carry = -1
            }
            val evenSize = data.size - data.size % 2
            if (evenSize < data.size) carry = data.last().toInt() and 0xff
            if (evenSize > 0) {
                emitPcm(data.copyOf(evenSize), config.sampleRate, config.channels)
            }
        }
    }

    private fun compressedLoop(config: AudioDecoderConfig) {
        val mime = if (config.codec == BleAudioCodec.AAC) {
            MediaFormat.MIMETYPE_AUDIO_AAC
        } else {
            "audio/opus"
        }
        val format = MediaFormat.createAudioFormat(mime, 48_000, config.channels)
        if (config.codec == BleAudioCodec.OPUS) configureOpus(format, config)
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val adts = AdtsParser()
            var pending: ByteArray? = null
            var outputRate = 48_000
            var outputChannels = config.channels
            while (running) {
                if (pending == null) {
                    if (config.codec == BleAudioCodec.AAC) {
                        pending = adts.nextFrame()
                        while (pending == null && running) {
                            val chunk = takeChunk(20) ?: break
                            adts.append(chunk)
                            pending = adts.nextFrame()
                        }
                    } else {
                        pending = takeChunk(20)
                    }
                }
                pending?.let { packet ->
                    val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex)
                        if (buffer != null && packet.size <= buffer.remaining()) {
                            buffer.clear()
                            buffer.put(packet)
                            codec.queueInputBuffer(
                                inputIndex, 0, packet.size, System.nanoTime() / 1000, 0
                            )
                        } else {
                            listener?.onAudioError("音频包超过解码器输入容量: ${packet.size} B")
                        }
                        pending = null
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(info, 0)
                while (outputIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        val pcm = ByteArray(info.size)
                        buffer.get(pcm)
                        emitPcm(pcm, outputRate, outputChannels)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    outputIndex = codec.dequeueOutputBuffer(info, 0)
                }
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val changed = codec.outputFormat
                    outputRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    outputChannels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    startAudioTrack(outputRate, outputChannels)
                    normalizer.reset()
                    Log.i(TAG, "音频输出格式: $outputRate Hz, $outputChannels 声道")
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
        }
    }

    private fun configureOpus(format: MediaFormat, config: AudioDecoderConfig) {
        val preSkip = 312
        val header = ByteBuffer.allocate(19).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("OpusHead".toByteArray(Charsets.US_ASCII))
            put(1)
            put(config.channels.toByte())
            putShort(preSkip.toShort())
            putInt(config.sampleRate)
            putShort(0)
            put(0)
        }
        format.setByteBuffer("csd-0", ByteBuffer.wrap(header.array()))
        format.setByteBuffer(
            "csd-1",
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
                putLong(preSkip.toLong())
                flip()
            }
        )
        format.setByteBuffer(
            "csd-2",
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
                putLong(0)
                flip()
            }
        )
    }

    private fun emitPcm(pcm: ByteArray, sampleRate: Int, channels: Int) {
        if (!muted) audioTrack?.write(pcm, 0, pcm.size)
        val samples = normalizer.normalize(pcm, sampleRate, channels)
        if (samples.isNotEmpty()) listener?.onAudioSamples(samples)
    }

    private fun startAudioTrack(sampleRate: Int, channels: Int) {
        audioTrack?.release()
        val channelMask = if (channels == 1) {
            AudioFormat.CHANNEL_OUT_MONO
        } else {
            AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer, sampleRate * channels * 2 / 10)
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }
}

/** 可从任意 Notify 切片中恢复完整 ADTS 帧的有界解析器。 */
private class AdtsParser {

    companion object {
        private const val MAX_BUFFER_BYTES = 512 * 1024
    }

    private var buffer = ByteArray(32 * 1024)
    private var length = 0

    fun append(data: ByteArray) {
        if (length + data.size > buffer.size) {
            var capacity = buffer.size
            while (capacity < length + data.size) capacity *= 2
            buffer = buffer.copyOf(capacity)
        }
        data.copyInto(buffer, length)
        length += data.size
        if (length > MAX_BUFFER_BYTES) {
            val keep = minOf(length, 8 * 1024)
            buffer.copyInto(buffer, 0, length - keep, length)
            length = keep
        }
    }

    fun nextFrame(): ByteArray? {
        var offset = 0
        while (offset + 7 <= length) {
            if (buffer[offset].toInt() and 0xff != 0xff ||
                buffer[offset + 1].toInt() and 0xf6 != 0xf0
            ) {
                offset++
                continue
            }
            val frameLength = ((buffer[offset + 3].toInt() and 0x03) shl 11) or
                ((buffer[offset + 4].toInt() and 0xff) shl 3) or
                ((buffer[offset + 5].toInt() and 0xe0) ushr 5)
            val headerLength = if (buffer[offset + 1].toInt() and 0x01 == 0x01) 7 else 9
            if (frameLength < headerLength) {
                offset++
                continue
            }
            if (offset + frameLength > length) break
            val frame = buffer.copyOfRange(offset, offset + frameLength)
            val remaining = length - offset - frameLength
            buffer.copyInto(buffer, 0, offset + frameLength, length)
            length = remaining
            return frame
        }
        if (offset > 0) {
            buffer.copyInto(buffer, 0, offset, length)
            length -= offset
        }
        return null
    }
}
