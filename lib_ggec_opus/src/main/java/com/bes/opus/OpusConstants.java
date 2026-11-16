package com.bes.opus;

import android.media.AudioFormat;

/**
 * Opus音频编解码常量配置类
 *
 * 定义了Opus音频处理过程中使用的默认参数，包括采样率、声道配置、
 * 音频格式和帧大小等关键参数。这些常量确保了音频处理的一致性。
 *
 * @author all0
 * @since 2017/12/07
 */
public class OpusConstants {

    /** 默认采样率：16kHz，适用于语音通话场景 */
    public static final int DEFAULT_SAMPLE_RATE = 16000;

    /** 默认声道配置：使用系统默认输入声道配置 */
    public static final int DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_DEFAULT;

    /** 默认音频格式：16位PCM编码，提供良好的音质和兼容性 */
    public static final int DEFAULT_AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    /** 默认帧大小：1600字节，对应16kHz采样率下100ms的音频数据 */
    public static final int DEFAULT_FRAME_SIZE = 1600;
}
