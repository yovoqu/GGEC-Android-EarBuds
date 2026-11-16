/**
 * Opus编码器JNI实现
 *
 * 本文件实现了Java层OpusEncoder类的native方法，提供Opus音频编码功能。
 * 通过JNI接口连接Java层和底层的HuyaEncoder C++实现，支持多种编码模式。
 *
 * 支持的编码模式：
 * - VOIP模式：优化语音通话，低延迟
 * - Audio模式：优化音乐播放，高音质
 * - Restricted Low Delay模式：受限低延迟模式
 *
 * 主要功能：
 * - 初始化不同模式的Opus编码器
 * - 执行PCM数据编码为Opus格式
 * - 管理编码器生命周期和内存释放
 *
 * @author Administrator
 * @since 2017/8/28
 */

#include "com_bes_opus_OpusEncoder.h"
#include "include/HuyaEncoder.h"
#include "android/log.h"

extern "C" {
/**
 * 初始化VOIP模式编码器
 *
 * 创建并初始化适用于语音通话的Opus编码器，优化低延迟和语音质量。
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @param fs 采样率（如16000Hz）
 * @param channels 声道数（1=单声道，2=立体声）
 * @return 编码器实例指针（转换为jlong）
 */
JNIEXPORT jlong JNICALL Java_com_bes_opus_OpusEncoder_init_1voip_1native
        (JNIEnv *env, jobject thiz, jint fs, jint channels) {
    HuyaEncoder *huyaEncoder = new HuyaEncoder();
    huyaEncoder->initVoip(fs, channels);
    return (jlong) huyaEncoder;
}

/**
 * 初始化Audio模式编码器
 *
 * 创建并初始化适用于音乐播放的Opus编码器，优化音质和压缩效率。
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @param fs 采样率（如44100Hz）
 * @param channels 声道数（1=单声道，2=立体声）
 * @return 编码器实例指针（转换为jlong）
 */
JNIEXPORT jlong JNICALL Java_com_bes_opus_OpusEncoder_init_1audio_1native
        (JNIEnv *env, jobject thiz, jint fs, jint channels) {
    HuyaEncoder *huyaEncoder = new HuyaEncoder();
    huyaEncoder->initAudio(fs, channels);
    return (jlong) huyaEncoder;
}

/**
 * 初始化受限低延迟模式编码器
 *
 * 创建并初始化受限低延迟模式的Opus编码器，在保证低延迟的同时提供较好的音质。
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @param fs 采样率
 * @param channels 声道数（1=单声道，2=立体声）
 * @return 编码器实例指针（转换为jlong）
 */
JNIEXPORT jlong JNICALL Java_com_bes_opus_OpusEncoder_init_1restricted_1low_1delay_1native
        (JNIEnv *env, jobject thiz, jint fs, jint channels) {
    HuyaEncoder *huyaEncoder = new HuyaEncoder();
    huyaEncoder->initRestrictedLowDelay(fs, channels);
    return (jlong) huyaEncoder;
}

/**
 * 编码PCM数据为Opus格式
 *
 * 将输入的PCM短整型数组编码为Opus格式的字节数组。
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @param encoder 编码器实例指针
 * @param pcm 输入的PCM短整型数组
 * @param frame_size 帧大小（样本数）
 * @return 编码后的Opus字节数组
 */
JNIEXPORT jbyteArray JNICALL Java_com_bes_opus_OpusEncoder_encode_1native
        (JNIEnv *env, jobject thiz, jlong encoder, jshortArray pcm, jint frame_size) {
    // 获取PCM数据指针和长度
    short *pcm_data = env->GetShortArrayElements(pcm, JNI_FALSE);
    int data_len = env->GetArrayLength(pcm);

    // 记录PCM数据长度到日志
    __android_log_print(ANDROID_LOG_ERROR, "HuyaEncoder", "pcm_len:%d", data_len);

    // 分配输出缓冲区（预估大小为输入数据的2倍）
    unsigned char *data = new unsigned char[data_len * 2];

    // 执行编码操作
    int out_len = ((HuyaEncoder *) encoder)->encode(pcm_data, frame_size, data);

    // 创建Java字节数组
    jbyteArray out = env->NewByteArray(out_len);

    // 释放PCM数据内存（JNI_ABORT表示不回写修改）
    env->ReleaseShortArrayElements(pcm, pcm_data, JNI_ABORT);

    // 将编码结果复制到Java数组
    env->SetByteArrayRegion(out, 0, out_len, (jbyte *) data);

    // 释放输出缓冲区
    delete[] data;

    return out;
}

/**
 * 销毁编码器实例
 *
 * 释放编码器资源并删除实例。
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @param encoder 编码器实例指针
 */
JNIEXPORT void JNICALL Java_com_bes_opus_OpusEncoder_destory_1native
        (JNIEnv *env, jobject thiz, jlong encoder) {
    // 调用编码器的销毁方法释放内部资源
    ((HuyaEncoder *) encoder)->destory();

    // 删除编码器实例
    delete ((HuyaEncoder *) encoder);
}
}


