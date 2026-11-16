package com.ambiq.blevideoplayer

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

/** 本地音频解码器：容器走 MediaExtractor，扩展名为 .pcm 的文件按原始 PCM 读取。 */
class LocalAudioDecoder(private val context: Context) {

    interface Listener {
        fun onPcm(samples: FloatArray)
        fun onProgress(percent: Int)
        fun onCompleted()
        fun onError(message: String)
    }

    var listener: Listener? = null

    @Volatile
    private var running = false
    private var worker: Thread? = null

    fun start(uri: Uri, pcmSampleRate: Int, pcmChannels: Int) {
        cancel()
        running = true
        worker = Thread {
            try {
                if (displayName(uri).endsWith(".pcm")) {
                    decodeRawPcm(uri, pcmSampleRate, pcmChannels)
                } else {
                    decodeContainer(uri)
                }
                if (running) listener?.onCompleted()
            } catch (e: Exception) {
                if (running) listener?.onError("本地音频解码失败: ${e.message}")
            }
        }.apply {
            name = "local-audio-decode"
            start()
        }
    }

    fun cancel() {
        running = false
        worker?.interrupt()
        worker?.join(1_000)
        worker = null
    }

    private fun decodeRawPcm(uri: Uri, sampleRate: Int, channels: Int) {
        val normalizer = PcmNormalizer()
        val length = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        var total = 0L
        var carry = -1
        context.contentResolver.openInputStream(uri)?.buffered(64 * 1024)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (running) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                var data = buffer.copyOf(count)
                if (carry >= 0) {
                    data = ByteArray(data.size + 1)
                    data[0] = carry.toByte()
                    buffer.copyInto(data, 1, 0, count)
                    carry = -1
                }
                val evenSize = data.size - data.size % 2
                if (evenSize < data.size) carry = data.last().toInt() and 0xff
                if (evenSize > 0) {
                    val samples = normalizer.normalize(data.copyOf(evenSize), sampleRate, channels)
                    if (samples.isNotEmpty()) listener?.onPcm(samples)
                }
                total += count
                if (length > 0) listener?.onProgress((total * 100 / length).toInt().coerceAtMost(99))
            }
        } ?: error("无法打开所选文件")
    }

    private fun decodeContainer(uri: Uri) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("文件中没有音轨")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("未知音频编码")
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val duration = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                format.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            val normalizer = PcmNormalizer()
            codec = MediaCodec.createDecoderByType(mime).also {
                it.configure(format, null, null, 0)
                it.start()
            }
            val info = MediaCodec.BufferInfo()
            var inputFinished = false
            var outputFinished = false
            while (running && !outputFinished) {
                if (!inputFinished) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = codec.getInputBuffer(inputIndex) ?: error("无法获取解码输入")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputFinished = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex, 0, size, extractor.sampleTime, extractor.sampleFlags
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val changed = codec.outputFormat
                        sampleRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        normalizer.reset()
                    }
                    else -> if (outputIndex >= 0) {
                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(outputIndex)
                            if (buffer != null) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val pcm = ByteArray(info.size)
                                buffer.get(pcm)
                                val samples = normalizer.normalize(pcm, sampleRate, channels)
                                if (samples.isNotEmpty()) listener?.onPcm(samples)
                            }
                        }
                        if (duration > 0 && info.presentationTimeUs >= 0) {
                            listener?.onProgress(
                                (info.presentationTimeUs * 100 / duration).toInt().coerceIn(0, 99)
                            )
                        }
                        outputFinished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }

    private fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0).lowercase(Locale.ROOT)
                }
            }
        return (uri.lastPathSegment ?: "").lowercase(Locale.ROOT)
    }
}
