/**
 * Opus解码器JNI实现
 *
 * 本文件实现了Java层OpusDecoder类的native方法，提供Opus音频解码功能。
 * 通过JNI接口连接Java层和底层的HuyaDecoder C++实现，实现高效的音频解码处理。
 *
 * 主要功能：
 * - 初始化Opus解码器实例
 * - 获取解码器配置参数
 * - 执行Opus数据解码为PCM格式
 * - 管理解码器生命周期和内存释放
 *
 * @author Administrator
 * @since 2017/8/25
 */

#include <malloc.h>
#include "com_bes_opus_OpusDecoder.h"
#include "include/HuyaDecoder.h"
#include "android/log.h"

extern "C" {

/**
 * 初始化Opus解码器
 *
 * 创建并初始化HuyaDecoder实例，设置解码参数。
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @param framePeriod 帧周期
 * @param singleFrameInputSize 单帧输入大小（字节）
 * @param totalFrameInputSize 总帧输入大小（字节）
 * @param pcmOutputSize PCM输出大小（字节）
 * @param bitRate 比特率
 * @return 解码器实例指针（转换为jlong）
 */
JNIEXPORT jlong JNICALL Java_com_bes_opus_OpusDecoder_init_1native
        (JNIEnv *env, jobject thiz, jint framePeriod, jint singleFrameInputSize, jint totalFrameInputSize, jint pcmOutputSize, jint bitRate) {
    // 初始化全局解码器配置
    initDecoder(framePeriod, singleFrameInputSize, totalFrameInputSize, pcmOutputSize, bitRate);

    // 创建解码器实例并初始化
    HuyaDecoder *huyaDecoder = new HuyaDecoder();
    huyaDecoder->init();

    __android_log_print(ANDROID_LOG_INFO, "GGEC-Opus",
        "初始化 采样率=%d 声道=%d PCM=%d 帧周期=%d 配置入长=%d 配置总长=%d 入参码率=%d 生效码率=%d",
        VOICE_OPUS_SAMPLE_RATE, VOICE_OPUS_CHANNEL_COUNT,
        getSingleFrameOutputSize(), getFramePeriod(),
        getSingleFrameInputSize(), getTotalFrameInputSize(),
        bitRate, getDecoderBitRate());

    // 返回解码器实例指针
    return (jlong) huyaDecoder;
}

/**
 * 获取单帧解码器输入数据长度
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @return 单帧输入数据的字节长度
 */
JNIEXPORT jint JNICALL Java_com_bes_opus_OpusDecoder_getOpusSingleFrameDecoderIntLen_1native
        (JNIEnv *env, jobject thiz) {
    return getSingleFrameInputSize();
}

/**
 * 获取总帧解码器输入数据长度
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @return 总帧输入数据的字节长度
 */
JNIEXPORT jint JNICALL Java_com_bes_opus_OpusDecoder_getOpusFrameTotalFrameDecoderIntLen_1native
        (JNIEnv *env, jobject thiz) {
    return getTotalFrameInputSize();
}

/**
 * 解码Opus数据为PCM格式
 *
 * 将输入的Opus编码数据解码为PCM短整型数组。
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @param decoder 解码器实例指针
 * @param in 输入的Opus编码数据字节数组
 * @return 解码后的PCM短整型数组
 */
JNIEXPORT jshortArray JNICALL Java_com_bes_opus_OpusDecoder_decode_1native
        (JNIEnv *env, jobject thiz, jlong decoder, jbyteArray in, jint encodedLength) {
    // 获取输入数据指针；入长只用 Java 数组长度（CBR 包含 padding 零，禁止截短）
    jbyte *in_data = env->GetByteArrayElements(in, JNI_FALSE);
    int in_data_size = env->GetArrayLength(in);
    (void) encodedLength;

    // 获取输出数据缓冲区大小（字节数，不是短整型数）
    int out_data_byte_size = getSingleFrameOutputSize();
    opus_int16 *out_data = (short *) malloc(out_data_byte_size);

    // 执行解码操作，输出长度为字节大小除以2（因为输出是短整型）
    int out_len = ((HuyaDecoder *) decoder)->decode((unsigned char *) in_data, in_data_size, out_data,
                                                    out_data_byte_size / 2);

    if (out_len <= 0 || out_len > out_data_byte_size / 2) {
        env->ReleaseByteArrayElements(in, in_data, JNI_ABORT);
        free(out_data);
        return nullptr;
    }

    // 创建Java短整型数组
    jshortArray out = env->NewShortArray(out_len);

    // 释放输入数据内存（JNI_ABORT表示不回写修改）
    env->ReleaseByteArrayElements(in, in_data, JNI_ABORT);

    // 将解码结果复制到Java数组
    env->SetShortArrayRegion(out, 0, out_len, out_data);

    // 释放输出数据缓冲区
    free(out_data);

    return out;
}

/**
 * 运行时更新 Opus 解码码率（16000–64000 bps）。
 */
JNIEXPORT void JNICALL Java_com_bes_opus_OpusDecoder_setBitRate_1native
        (JNIEnv *env, jobject thiz, jlong decoder, jint bitRate) {
    if (bitRate <= 0) {
        return;
    }
    updateDecoderBitRate(bitRate);
    if (decoder != 0) {
        ((HuyaDecoder *) decoder)->setBitRate(bitRate);
    }
    __android_log_print(ANDROID_LOG_INFO, "GGEC-Opus", "码率更新 bitRate=%d", bitRate);
}

/**
 * 销毁解码器实例
 *
 * 释放解码器资源并删除实例。
 *
 * @param env JNI环境指针
 * @param thiz Java对象实例
 * @param decoder 解码器实例指针
 */
JNIEXPORT void JNICALL Java_com_bes_opus_OpusDecoder_destory_1native
        (JNIEnv *env, jobject thiz, jlong decoder) {
    // 调用解码器的销毁方法释放内部资源
    ((HuyaDecoder *) decoder)->destory();

    // 删除解码器实例
    delete ((HuyaDecoder *) decoder);
}
}