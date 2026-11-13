package com.ambiq.blevideoplayer

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * H.264 / H.265 Annex B 裸码流硬解器。
 *
 * 码流约定：对端通过 BLE Notify 推送连续的 Annex B elementary stream
 * （含 00 00 00 01 start code，无任何私有帧头）。分辨率/帧率由码流
 * 内嵌的 SPS 决定，configure 时不设置 csd-0/csd-1。
 *
 * 关键约束：MediaCodec 的输入必须按完整 NAL 单元对齐 —— 高通等硬解器
 * 把每次 queueInputBuffer 提交当作完整访问单元处理，NAL 被拆到多个
 * 输入 buffer 会导致只解出前几帧然后卡死（黑屏）。因此本类先在
 * [enqueue] 里把字节流按起始码切成完整 NAL（跨 BLE 通知块缓存拼接），
 * 喂 codec 时一个输入 buffer 只容纳完整 NAL，放不下的留到下一个 buffer。
 *
 * 线程模型：BLE 回调线程调用 [enqueue] 切帧入队（队列满时丢弃最旧帧并
 * 计数，绝不阻塞）；内部解码线程消费队列并渲染到 Surface。
 */
class VideoStreamDecoder {

    companion object {
        private const val TAG = "VideoStreamDecoder"

        /** 输入队列上限 4MB，约可缓存数秒码流 */
        private const val MAX_QUEUE_BYTES = 4 * 1024 * 1024
        private const val INPUT_TIMEOUT_US = 10_000L
    }

    interface Listener {
        fun onDecoderError(message: String)

        /** SPS 解析完成、拿到真实分辨率时回调（解码线程，UI 操作需切主线程） */
        fun onVideoSizeChanged(width: Int, height: Int)
    }

    var listener: Listener? = null

    // 解码帧队列（队列元素是完整 NAL，含起始码）
    private val lock = ReentrantLock()
    private val notEmpty = lock.newCondition()
    private val chunks = ArrayDeque<ByteArray>()
    private var queueBytes = 0

    // NAL 切分器状态（只在 enqueue 内、enqueueLock 保护下访问）
    private val enqueueLock = Any()
    private var acc = ByteArray(256 * 1024)
    private var accLen = 0
    private var streamStarted = false

    /** 队列溢出时被丢弃的帧数（UI 显示用） */
    val droppedChunks = AtomicLong(0)

    /** 累计解码输出帧数（统计线程据此计算 fps） */
    val decodedFrames = AtomicLong(0)

    /**
     * 累计检测到的完整 NAL 个数（每个 NAL 以一个 Annex B 起始码开头）。
     * 诊断用：收到大量数据而此值为 0，说明对端码流不是 Annex B 裸流。
     */
    val startCodeCount = AtomicLong(0)

    /** 累计入队字节数（诊断用，会话级） */
    private val totalEnqueued = AtomicLong(0)

    /** 已打印的 NAL 头个数（最多 32 个） */
    private val nalLogged = AtomicLong(0)

    @Volatile
    private var running = false
    private var decodeThread: Thread? = null

    // ---------------- 入队与 NAL 切分（BLE 回调线程调用） ----------------

    /** 入队一段码流；切成完整 NAL 后送入解码队列，队列满丢最旧帧。 */
    fun enqueue(data: ByteArray) {
        // 会话开头 dump 前 256 字节原始数据到 logcat，便于分析码流封装格式
        val pos = totalEnqueued.getAndAdd(data.size.toLong())
        if (pos < 256) {
            val n = minOf(data.size, (256 - pos).toInt())
            Log.i(TAG, "HEAD ${pos..(pos + n - 1)}: " +
                data.copyOfRange(0, n).joinToString(" ") { "%02x".format(it) })
        }
        synchronized(enqueueLock) {
            splitNals(data)
        }
    }

    /**
     * 判断 b[p] 处是否为 Annex B 起始码，返回起始码长度（4 或 3），否则 0。
     * limit 是可安全读取的末尾（不含）。
     */
    private fun scLenAt(b: ByteArray, p: Int, limit: Int): Int {
        if (p + 4 <= limit &&
            b[p] == 0.toByte() && b[p + 1] == 0.toByte() &&
            b[p + 2] == 0.toByte() && b[p + 3] == 1.toByte()
        ) {
            return 4
        }
        if (p + 3 <= limit &&
            b[p] == 0.toByte() && b[p + 1] == 0.toByte() && b[p + 2] == 1.toByte()
        ) {
            return 3
        }
        return 0
    }

    private fun ensureAccCapacity(needed: Int) {
        if (needed <= acc.size) return
        var cap = acc.size * 2
        while (cap < needed) cap *= 2
        acc = acc.copyOf(cap)
    }

    /**
     * 把字节流切成完整 NAL 入队。H.264/H.265 有 emulation prevention，
     * payload 内不会出现 00 00 00 01 / 00 00 01，按起始码切分是安全的。
     * 起始码可能跨 BLE 块：只扫描到 accLen-4 为止，尾部不完整部分留待下一块。
     */
    private fun splitNals(data: ByteArray) {
        ensureAccCapacity(accLen + data.size)
        System.arraycopy(data, 0, acc, accLen, data.size)
        accLen += data.size

        // 防御：长时间找不到起始码说明格式不对，避免累积 buffer 无限膨胀
        if (accLen > MAX_QUEUE_BYTES) {
            accLen = 0
            streamStarted = false
            return
        }

        if (!streamStarted) {
            // 对齐到第一个完整起始码，之前的字节（上一个 NAL 的残尾/前导零）丢弃
            var p = 0
            while (p + 4 <= accLen && scLenAt(acc, p, accLen) == 0) p++
            if (p + 4 > accLen) {
                // 没找到：只保留末尾 3 字节（可能是不完整起始码前缀）
                if (accLen > 3) {
                    System.arraycopy(acc, accLen - 3, acc, 0, 3)
                    accLen = 3
                }
                return
            }
            System.arraycopy(acc, p, acc, 0, accLen - p)
            accLen -= p
            streamStarted = true
        }

        // 不变式：acc 以完整起始码开头。每找到下一个起始码，[0,p) 就是一个完整 NAL。
        while (true) {
            val firstSc = scLenAt(acc, 0, accLen)
            if (firstSc == 0) {
                // 防御：不变式被破坏（不应发生），丢弃并重新对齐
                accLen = 0
                streamStarted = false
                return
            }
            var p = firstSc
            while (p + 4 <= accLen && scLenAt(acc, p, accLen) == 0) p++
            if (p + 4 > accLen) return // 尾部可能是不完整起始码，留给下一块

            val frame = acc.copyOfRange(0, p)
            pushFrame(frame)
            startCodeCount.incrementAndGet()
            logNalHeader(frame, scLenAt(frame, 0, frame.size))

            System.arraycopy(acc, p, acc, 0, accLen - p)
            accLen -= p
        }
    }

    /** 把一个完整 NAL（含起始码）压入解码队列；满则丢最旧帧，不阻塞 BLE 线程。 */
    private fun pushFrame(frame: ByteArray) {
        lock.withLock {
            while (queueBytes + frame.size > MAX_QUEUE_BYTES && chunks.isNotEmpty()) {
                val old = chunks.removeFirst()
                queueBytes -= old.size
                droppedChunks.incrementAndGet()
            }
            chunks.addLast(frame)
            queueBytes += frame.size
            notEmpty.signal()
        }
    }

    /** 前 32 个 NAL 各打印头部 8 字节（NAL header），用于判断有无 SPS/PPS/IDR */
    private fun logNalHeader(nal: ByteArray, hdrStart: Int) {
        if (nalLogged.get() >= 32) return
        val logged = nalLogged.incrementAndGet()
        if (logged > 32) return
        val end = minOf(nal.size, hdrStart + 8)
        if (hdrStart >= end) return
        Log.i(TAG, "NAL #$logged hdr: " +
            nal.copyOfRange(hdrStart, end).joinToString(" ") { "%02x".format(it) })
    }

    // ---------------- 队列操作（解码线程调用） ----------------

    fun queuedBytes(): Int = lock.withLock { queueBytes }

    private fun clearQueue() = lock.withLock {
        chunks.clear()
        queueBytes = 0
    }

    /** 阻塞最多 timeoutMs 取一帧；超时仍为空则返回 null。 */
    private fun takeChunk(timeoutMs: Long): ByteArray? {
        lock.withLock {
            if (chunks.isEmpty()) {
                notEmpty.await(timeoutMs, TimeUnit.MILLISECONDS)
            }
            val c = chunks.pollFirst() ?: return null
            queueBytes -= c.size
            return c
        }
    }

    /** 非阻塞取一帧。 */
    private fun pollChunk(): ByteArray? {
        lock.withLock {
            val c = chunks.pollFirst() ?: return null
            queueBytes -= c.size
            return c
        }
    }

    // ---------------- 解码生命周期 ----------------

    fun start(surface: Surface, mime: String) {
        stop()
        clearQueue()
        synchronized(enqueueLock) {
            // 解码器重建时切分器重新对齐到下一个起始码（中途接入码流）
            accLen = 0
            streamStarted = false
        }
        running = true
        decodeThread = Thread { decodeLoop(surface, mime) }.apply {
            name = "video-decode"
            start()
        }
    }

    fun stop() {
        running = false
        lock.withLock { notEmpty.signalAll() } // 唤醒可能在 await 的解码线程
        decodeThread?.join(1_000)
        decodeThread = null
    }

    fun isRunning(): Boolean = running

    // ---------------- 解码主循环（独立线程） ----------------

    private fun decodeLoop(surface: Surface, mime: String) {
        var codec: MediaCodec? = null
        try {
            codec = MediaCodec.createDecoderByType(mime)
            // Annex B 码流自带 SPS/PPS，不设置 csd；宽高只是占位，实际由 SPS 决定
            val format = MediaFormat.createVideoFormat(mime, 1280, 720)
            codec.configure(format, surface, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var pending: ByteArray? = null // 已出队但尚未喂进 codec 的完整 NAL
            var fedBytes = 0L              // 累计喂给 codec 的字节数（诊断用）
            var lastStatLogMs = 0L

            while (running) {
                // ---- 喂入压缩数据 ----
                // 两个不变式：
                // 1) 先确保手里有完整 NAL，再向 codec 申请输入 buffer —— 绝不提交
                //    0 字节空 buffer（高通 CCodec 会把空 buffer 当 flushed work
                //    丢弃，空 buffer 风暴会挤占流水线导致真实帧被丢弃）。
                // 2) 一个输入 buffer 只装完整 NAL；放不下的 NAL 留到下一个
                //    buffer（硬解器要求输入按访问单元边界对齐）。
                if (pending == null) {
                    pending = takeChunk(20)
                }
                if (pending != null) {
                    val inIdx = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        if (buf != null) {
                            buf.clear()
                            var filled = 0
                            var c: ByteArray? = pending
                            while (c != null) {
                                if (c.size > buf.remaining()) {
                                    if (filled == 0) {
                                        // 兜底：单 NAL 比整个输入 buffer 还大
                                        // （异常大的 IDR），只能拆分喂入
                                        val part = buf.remaining()
                                        buf.put(c, 0, part)
                                        c = c.copyOfRange(part, c.size)
                                        filled += part
                                    }
                                    break
                                }
                                buf.put(c)
                                filled += c.size
                                c = pollChunk()
                            }
                            pending = c
                            if (filled > 0) {
                                fedBytes += filled
                                // 直播场景：时间戳取提交时刻的真实时间，解出立即
                                // 显示；不要用按 buffer 递增的伪时钟（会漂移）
                                codec.queueInputBuffer(
                                    inIdx, 0, filled, System.nanoTime() / 1000, 0
                                )
                            }
                        }
                    }
                }

                // ---- 取出解码结果，直接渲染到 Surface ----
                var outIdx = codec.dequeueOutputBuffer(info, 0)
                while (outIdx >= 0) {
                    if (info.size > 0) decodedFrames.incrementAndGet()
                    codec.releaseOutputBuffer(outIdx, true)
                    outIdx = codec.dequeueOutputBuffer(info, 0)
                }
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 首帧 SPS 解析完成后会触发一次，这里拿到真实分辨率。
                    // 优先用裁剪区（crop）算有效分辨率：1080p 的 buffer 高度是
                    // 1088（16 对齐），crop 才是真实的 1920x1080
                    val f = codec.outputFormat
                    var w = f.getInteger(MediaFormat.KEY_WIDTH)
                    var h = f.getInteger(MediaFormat.KEY_HEIGHT)
                    val cropRight = if (f.containsKey("crop-right")) f.getInteger("crop-right") else w - 1
                    val cropBottom = if (f.containsKey("crop-bottom")) f.getInteger("crop-bottom") else h - 1
                    val cropLeft = if (f.containsKey("crop-left")) f.getInteger("crop-left") else 0
                    val cropTop = if (f.containsKey("crop-top")) f.getInteger("crop-top") else 0
                    w = cropRight - cropLeft + 1
                    h = cropBottom - cropTop + 1
                    Log.i(TAG, "输出格式变化: ${w}x${h}")
                    listener?.onVideoSizeChanged(w, h)
                }

                // 每 5 秒向 logcat 打一条喂入/输出统计，便于 adb 侧诊断
                val nowMs = System.currentTimeMillis()
                if (nowMs - lastStatLogMs > 5000) {
                    lastStatLogMs = nowMs
                    Log.i(
                        TAG, "stat: fed=${fedBytes}B out=${decodedFrames.get()}帧 " +
                            "queue=${queuedBytes() / 1024}KB drop=${droppedChunks.get()}"
                    )
                }
            }
        } catch (e: Exception) {
            if (running) {
                Log.e(TAG, "解码器异常", e)
                listener?.onDecoderError("解码器异常: ${e.message}")
            }
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
        }
    }
}
