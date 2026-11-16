/*
 * JNI bridge to LAME 3.100 for streaming PCM -> MP3 encoding on Android.
 *
 * Surface (matching com.bes.lame.Mp3Encoder):
 *   long  nativeInit(int sampleRate, int channels, int bitRateKbps, int quality);
 *   int   nativeEncodeMono(long handle, short[] pcm, int offset, int samples,
 *                          byte[] mp3, int mp3Capacity);
 *   int   nativeFlush(long handle, byte[] mp3, int mp3Capacity);
 *   void  nativeClose(long handle);
 *
 * Threading: callers are expected to use a single handle from one thread at a
 * time (the StreamRecordTransfer encoder thread). LAME itself is not thread
 * safe across the same lame_t instance.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#include "lame/include/lame.h"

#define LOG_TAG "BesMp3Encoder"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_bes_lame_Mp3Encoder_nativeInit(JNIEnv *env, jclass clazz,
                                        jint sampleRate, jint channels,
                                        jint bitRateKbps, jint quality) {
    (void)env;
    (void)clazz;

    lame_t lame = lame_init();
    if (!lame) {
        LOGE("lame_init failed");
        return 0;
    }

    lame_set_in_samplerate(lame, sampleRate);
    lame_set_num_channels(lame, channels);
    lame_set_out_samplerate(lame, sampleRate);
    lame_set_brate(lame, bitRateKbps);
    lame_set_quality(lame, quality);

    if (channels == 1) {
        lame_set_mode(lame, MONO);
    } else {
        lame_set_mode(lame, JOINT_STEREO);
    }

    if (lame_init_params(lame) < 0) {
        LOGE("lame_init_params failed");
        lame_close(lame);
        return 0;
    }

    LOGI("LAME init ok: sr=%d ch=%d br=%dkbps q=%d",
         sampleRate, channels, bitRateKbps, quality);
    return (jlong)(intptr_t)lame;
}

JNIEXPORT jint JNICALL
Java_com_bes_lame_Mp3Encoder_nativeEncodeMono(JNIEnv *env, jclass clazz,
                                              jlong handle,
                                              jshortArray pcmArr, jint offset, jint samples,
                                              jbyteArray mp3Arr, jint mp3Capacity) {
    (void)clazz;
    if (handle == 0 || pcmArr == NULL || mp3Arr == NULL) {
        return -1;
    }
    lame_t lame = (lame_t)(intptr_t)handle;

    jshort *pcm = (*env)->GetShortArrayElements(env, pcmArr, NULL);
    jbyte  *mp3 = (*env)->GetByteArrayElements(env, mp3Arr, NULL);
    if (pcm == NULL || mp3 == NULL) {
        if (pcm) (*env)->ReleaseShortArrayElements(env, pcmArr, pcm, JNI_ABORT);
        if (mp3) (*env)->ReleaseByteArrayElements(env, mp3Arr, mp3, JNI_ABORT);
        return -2;
    }

    int written = lame_encode_buffer(lame,
                                     pcm + offset, pcm + offset,
                                     samples,
                                     (unsigned char *)mp3, (int)mp3Capacity);

    (*env)->ReleaseShortArrayElements(env, pcmArr, pcm, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, mp3Arr, mp3, 0);

    if (written < 0) {
        LOGE("lame_encode_buffer failed: %d", written);
    }
    return written;
}

JNIEXPORT jint JNICALL
Java_com_bes_lame_Mp3Encoder_nativeFlush(JNIEnv *env, jclass clazz,
                                         jlong handle,
                                         jbyteArray mp3Arr, jint mp3Capacity) {
    (void)clazz;
    if (handle == 0 || mp3Arr == NULL) {
        return -1;
    }
    lame_t lame = (lame_t)(intptr_t)handle;

    jbyte *mp3 = (*env)->GetByteArrayElements(env, mp3Arr, NULL);
    if (mp3 == NULL) {
        return -2;
    }

    int written = lame_encode_flush(lame, (unsigned char *)mp3, (int)mp3Capacity);

    (*env)->ReleaseByteArrayElements(env, mp3Arr, mp3, 0);

    if (written < 0) {
        LOGE("lame_encode_flush failed: %d", written);
    }
    return written;
}

JNIEXPORT void JNICALL
Java_com_bes_lame_Mp3Encoder_nativeClose(JNIEnv *env, jclass clazz, jlong handle) {
    (void)env;
    (void)clazz;
    if (handle == 0) return;
    lame_t lame = (lame_t)(intptr_t)handle;
    lame_close(lame);
}
