package com.ambiq.blevideoplayer

import kotlin.math.ceil

/**
 * 把 PCM 16-bit 小端数据下混为单声道，并以连续线性插值重采样到 16 kHz。
 * 跨数据块保留最后一个输入采样和插值相位，供 BLE 与本地文件共同使用。
 */
class PcmNormalizer {

    companion object {
        const val TARGET_SAMPLE_RATE = 16_000
    }

    private var configuredRate = 0
    private var tail: Float? = null
    private var phase = 0.0

    fun reset() {
        configuredRate = 0
        tail = null
        phase = 0.0
    }

    fun normalize(pcm: ByteArray, sampleRate: Int, channels: Int): FloatArray {
        require(sampleRate > 0 && channels in 1..2)
        val frames = pcm.size / (channels * 2)
        val mono = FloatArray(frames)
        var offset = 0
        for (i in 0 until frames) {
            var sum = 0
            repeat(channels) {
                val lo = pcm[offset].toInt() and 0xff
                val hi = pcm[offset + 1].toInt()
                sum += ((hi shl 8) or lo).toShort().toInt()
                offset += 2
            }
            mono[i] = sum / channels / 32768.0f
        }
        return resample(mono, sampleRate)
    }

    fun normalize(samples: ShortArray, sampleRate: Int, channels: Int): FloatArray {
        require(sampleRate > 0 && channels in 1..2)
        val frames = samples.size / channels
        val mono = FloatArray(frames)
        var offset = 0
        for (i in 0 until frames) {
            var sum = 0
            repeat(channels) {
                sum += samples[offset++].toInt()
            }
            mono[i] = sum / channels / 32768.0f
        }
        return resample(mono, sampleRate)
    }

    private fun resample(input: FloatArray, sampleRate: Int): FloatArray {
        if (configuredRate != sampleRate) {
            configuredRate = sampleRate
            tail = null
            phase = 0.0
        }
        if (input.isEmpty() || sampleRate == TARGET_SAMPLE_RATE) return input

        val previous = tail
        val data = if (previous == null) {
            input
        } else {
            FloatArray(input.size + 1).also {
                it[0] = previous
                input.copyInto(it, 1)
            }
        }
        val step = sampleRate.toDouble() / TARGET_SAMPLE_RATE
        val output = FloatArray(ceil(data.size / step).toInt() + 2)
        var count = 0
        var position = phase
        while (position + 1.0 < data.size) {
            val index = position.toInt()
            val fraction = (position - index).toFloat()
            output[count++] = data[index] * (1.0f - fraction) + data[index + 1] * fraction
            position += step
        }
        tail = data.last()
        phase = position - (data.size - 1)
        return output.copyOf(count)
    }
}
