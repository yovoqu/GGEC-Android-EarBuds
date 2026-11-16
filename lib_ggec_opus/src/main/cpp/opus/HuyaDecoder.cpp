//
// Created by Administrator on 2017/8/25.
//

#include "include/HuyaDecoder.h"
#include "android/log.h"
extern "C" {


int mFramePeriod = -1 ;
int mSingleFrameInputSize = -1 ;
int mTotalFrameInputSize = -1 ;
int mSingnleFrameOutputSize = -1 ;
int mBiteRate = 0;
/**
 *
 * @return
 */
int getFramePeriod(){
    if(mFramePeriod == -1){
        mFramePeriod = VOICE_FRAME_PERIOD ;
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
        setFramePeriod(VOICE_FRAME_PERIOD);
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
    opus_int32 skip;
    opus_decoder_ctl(opusDecoder, OPUS_SET_BITRATE(mBiteRate));
    opus_decoder_ctl(opusDecoder, OPUS_SET_BANDWIDTH(VOICE_OPUS_BANDWIDTH));
    opus_decoder_ctl(opusDecoder, OPUS_SET_VBR(VOICE_OPUS_USE_VBR));
    opus_decoder_ctl(opusDecoder, OPUS_SET_VBR_CONSTRAINT(VOICE_OPUS_CONSTRAINT_USE_VBR));
    opus_decoder_ctl(opusDecoder, OPUS_SET_COMPLEXITY(VOICE_OPUS_COMPLEXITY));
    opus_decoder_ctl(opusDecoder, OPUS_SET_INBAND_FEC(VOICE_OPUS_USE_INBANDFEC));
    opus_decoder_ctl(opusDecoder, OPUS_SET_FORCE_CHANNELS(VOICE_OPUS_CHANNEL_COUNT));
    opus_decoder_ctl(opusDecoder, OPUS_SET_DTX(VOICE_OPUS_USE_DTX));
    opus_decoder_ctl(opusDecoder, OPUS_SET_PACKET_LOSS_PERC(VOICE_OPUS_PACKET_LOSS_PERC));
    opus_decoder_ctl(opusDecoder, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
    opus_decoder_ctl(opusDecoder, OPUS_SET_EXPERT_FRAME_DURATION(getFramePeriod()));
    opus_decoder_ctl(opusDecoder, OPUS_GET_LOOKAHEAD(&skip));
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