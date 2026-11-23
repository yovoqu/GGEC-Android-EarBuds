//
// Created by Administrator on 2017/8/25.
//

#include "include/HuyaDecoder.h"
#include "android/log.h"
extern "C" {


/*
 * 进程内单配置（single-process single-configuration）：
 * 本 native 库由同一进程内所有解码器共享（HS / WY 共用同一份），下列全局量在 initDecoder()
 * 时被改写。约定：同一进程内只持有一种解码配置（16 kHz / 1ch / 20 ms / 16 kbps，HS 与 WY 同参）。
 * 真正的多实例隔离需把配置移入 HuyaDecoder 成员变量（本次不做高风险大改，仅注明约束）。
 */
int mFramePeriod = -1 ;
int mSingleFrameInputSize = -1 ;
int mTotalFrameInputSize = -1 ;
int mSingnleFrameOutputSize = -1 ;
int mBiteRate = 0;
/**
 * 返回帧周期（单位：毫秒）。
 *
 * <p>历史实现返回 {@code OPUS_FRAMESIZE_20_MS} 枚举值(=5004)，被 JNI 日志当作"帧周期=5004"
 * 打印，语义误导（既不是 5004 毫秒也不是 5.004 秒）。现统一返回 {@code VOICE_FRAME_PERIOD_IN_MS}
 * (=20 ms)；5004 仅为 Opus 帧时长档位枚举，在 init() 日志里单独说明。</p>
 *
 * @return 帧周期毫秒数（20）
 */
int getFramePeriod(){
    if(mFramePeriod == -1){
        mFramePeriod = VOICE_FRAME_PERIOD_IN_MS ;
    }
    return mFramePeriod ;
}
int getSingleFrameInputSize(){
    // 包长由每次 decode 的 in_len 决定，不再用码率反推
    return mSingleFrameInputSize < 0 ? 0 : mSingleFrameInputSize ;
}
int getTotalFrameInputSize(){
    return mTotalFrameInputSize < 0 ? 0 : mTotalFrameInputSize ;
}
int getSingleFrameOutputSize(){
    if(mSingnleFrameOutputSize <= 0){
        mSingnleFrameOutputSize = VOICE_OPUS_SAMPLE_RATE * VOICE_FRAME_PERIOD_IN_MS / 1000
                * VOICE_SIZE_PER_SAMPLE * VOICE_OPUS_CHANNEL_COUNT;
    }
    return mSingnleFrameOutputSize ;
}
int getDecoderBitRate(){
    return mBiteRate;
}

void setFramePeriod(int framePeriod){
    mFramePeriod = framePeriod ;
}
void setSingleFrameInputSize(int frameSize){
    mSingleFrameInputSize = frameSize ;
}
void setTotalFrameInputSize(int totalFrameSize){
    mTotalFrameInputSize = totalFrameSize ;
}
void setSingleFrameOutputSize(int pcmOutputSize){
    mSingnleFrameOutputSize = pcmOutputSize ;
}

void initDecoder(int framePeriod , int singleFrameInputSize , int totalFrameInputSize , int pcmOutputSize, int bitRate){
    if (bitRate != 0) {
       mBiteRate = bitRate;
    } else if (mBiteRate == 0) {
       mBiteRate = VOICE_OPUS_BITRATE;
    }
    if (framePeriod == 0) {
        // 默认帧周期按毫秒语义存储，与 getFramePeriod() 返回值统一（20 ms）。
        setFramePeriod(VOICE_FRAME_PERIOD_IN_MS);
    } else {
        setFramePeriod(framePeriod);
    }
    setSingleFrameInputSize(singleFrameInputSize);
    setTotalFrameInputSize(totalFrameInputSize);
    if (pcmOutputSize <= 0) {
        setSingleFrameOutputSize(VOICE_OPUS_SAMPLE_RATE * VOICE_FRAME_PERIOD_IN_MS / 1000
                * VOICE_SIZE_PER_SAMPLE * VOICE_OPUS_CHANNEL_COUNT);
    } else {
        setSingleFrameOutputSize(pcmOutputSize);
    }
}

void updateDecoderBitRate(int bitRate) {
    if (bitRate <= 0) {
        return;
    }
    mBiteRate = bitRate;
}

void HuyaDecoder::init() {
    int error;
    m_channels = VOICE_OPUS_CHANNEL_COUNT;
    opusDecoder = opus_decoder_create(VOICE_OPUS_SAMPLE_RATE, VOICE_OPUS_CHANNEL_COUNT, &error);

    if (error != OPUS_OK) {
        __android_log_print(ANDROID_LOG_ERROR, "GGEC-Opus",
            "初始化失败 error=%d", error);
    }

    // 仅保留对 decoder 有意义/可观测的控制项：
    //   - OPUS_SET_FORCE_CHANNELS：强制解出单声道，与设备 1ch 一致；
    //   - OPUS_GET_LOOKAHEAD：读取解码前瞻样本数（仅观测，用于诊断）。
    // 说明：BITRATE / VBR / VBR_CONSTRAINT / COMPLEXITY / INBAND_FEC / DTX /
    //       PACKET_LOSS_PERC / SIGNAL / BANDWIDTH / EXPERT_FRAME_DURATION 都是
    //       encoder-only 参数，对 decoder 返回 OPUS_UNIMPLEMENTED 或空操作；
    //       此前"配了一堆其实没生效"，现移除以免误导。decode 行为不受影响。
    opus_decoder_ctl(opusDecoder, OPUS_SET_FORCE_CHANNELS(VOICE_OPUS_CHANNEL_COUNT));
    opus_int32 skip = 0;
    opus_decoder_ctl(opusDecoder, OPUS_GET_LOOKAHEAD(&skip));

    // 明确区分"帧周期(毫秒)"与"Opus 帧时长枚举档位"，消除 5004 被误读成 5.004 秒/毫秒的误导。
    __android_log_print(ANDROID_LOG_INFO, "GGEC-Opus",
        "解码器帧周期=%d ms（OPUS_FRAMESIZE_20_MS=%d 仅为 Opus 帧时长档位枚举值，非毫秒）",
        VOICE_FRAME_PERIOD_IN_MS, OPUS_FRAMESIZE_20_MS);
}

void HuyaDecoder::setBitRate(int bitRate) {
    if (bitRate <= 0) {
        return;
    }
    mBiteRate = bitRate;
    if (opusDecoder != nullptr) {
        opus_decoder_ctl(opusDecoder, OPUS_SET_BITRATE(bitRate));
    }
}

int
HuyaDecoder::decode(const unsigned char *in, opus_int32 in_len, opus_int16 *out, int frame_size) {
    int size = opus_decode(opusDecoder, in, in_len, out, frame_size, 0);
    if (size < 0) {
        __android_log_print(ANDROID_LOG_ERROR, "GGEC-Opus",
            "解码失败 入长=%d 帧采样上限=%d PCM缓冲=%d opus=%s",
            in_len, frame_size, frame_size * VOICE_SIZE_PER_SAMPLE * m_channels,
            opus_strerror(size));
    }
    return size * m_channels; // 返回采样size ，需要 * 声道数
}

void HuyaDecoder::destory() {
    opus_decoder_destroy(opusDecoder);
}
}