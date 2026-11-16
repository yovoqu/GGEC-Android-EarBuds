package com.bes.opus;

/**
 * Opus音频编码器
 *
 * 提供PCM音频数据的Opus编码功能，支持多种编码模式以适应不同应用场景。
 * 通过JNI调用底层native库实现高效的音频编码处理。
 *
 * 支持的编码模式：
 * - VOIP模式：优化语音通话，低延迟
 * - Audio模式：优化音乐播放，高音质
 * - Restricted Low Delay模式：受限低延迟模式
 *
 * @author all0
 * @since 2017/12/07
 */
public class OpusEncoder {

    /**
     * 初始化VOIP模式编码器（native方法）
     * @param fs 采样率
     * @param channels 声道数
     * @return 编码器实例指针
     */
    private native long init_voip_native(int fs, int channels);

    /**
     * 初始化Audio模式编码器（native方法）
     * @param fs 采样率
     * @param channels 声道数
     * @return 编码器实例指针
     */
    private native long init_audio_native(int fs, int channels);

    /**
     * 初始化受限低延迟模式编码器（native方法）
     * @param fs 采样率
     * @param channels 声道数
     * @return 编码器实例指针
     */
    private native long init_restricted_low_delay_native(int fs, int channels);

    /**
     * 编码PCM数据为Opus格式（native方法）
     * @param encoder 编码器实例指针
     * @param pcm PCM短整型数组
     * @param frameSize 帧大小
     * @return 编码后的Opus字节数组
     */
    private native byte[] encode_native(long encoder, short[] pcm, int frameSize);

    /**
     * 销毁native编码器，释放资源
     * @param encoder 编码器实例指针
     */
    private native void destory_native(long encoder);

    /** 编码器实例指针，指向native层编码器对象 */
    private long encoder;

    /**
     * 初始化VOIP模式编码器
     * 适用于语音通话场景，优化低延迟和语音质量
     * @param fs 采样率（如16000Hz）
     * @param channels 声道数（1=单声道，2=立体声）
     */
    public void initVoip(int fs, int channels) {
        encoder = init_voip_native(fs, channels);
    }

    /**
     * 初始化Audio模式编码器
     * 适用于音乐播放场景，优化音质和压缩效率
     * @param fs 采样率（如44100Hz）
     * @param channels 声道数（1=单声道，2=立体声）
     */
    public void initAudio(int fs, int channels) {
        encoder = init_audio_native(fs, channels);
    }

    /**
     * 初始化受限低延迟模式编码器
     * 在保证低延迟的同时提供较好的音质
     * @param fs 采样率
     * @param channels 声道数（1=单声道，2=立体声）
     */
    public void initRestrictedLowDelay(int fs, int channels) {
        encoder = init_restricted_low_delay_native(fs, channels);
    }

    /**
     * 编码PCM音频数据为Opus格式
     * @param pcm PCM短整型数组，包含待编码的音频样本
     * @param frameSize 帧大小，指定每帧的样本数
     * @return 编码后的Opus字节数组，编码失败返回null
     */
    public byte[] encode(short[] pcm, int frameSize) {
        return encode_native(encoder, pcm, frameSize);
    }

    /**
     * 销毁编码器，释放native资源
     * 注意：方法名应为destroy，这里保持原有拼写以维持API兼容性
     */
    public void destory() {
        destory_native(encoder);
    }
}
