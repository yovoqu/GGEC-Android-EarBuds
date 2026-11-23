//
// Created by Administrator on 2017/8/25.
//

#ifndef OPUS_ANDROID_HUYADECODER_H
#define OPUS_ANDROID_HUYADECODER_H

#ifdef __cplusplus
extern "C" {
#endif

#include "opus.h"
#include "Constants.h"


// setup the opus begin
#define VOICE_OPUS_BITRATE (32000)
#define VOICE_OPUS_SAMPLE_RATE (16000)
#define VOICE_FRAME_PERIOD_IN_MS		(20) // 真实帧周期 20 ms（日志/时长换算用这个）
#define VOICE_SIZE_PER_SAMPLE			(2)	// 16 bits = 2*byte
#define VOICE_OPUS_CHANNEL_COUNT (1)  // 1 channel
// setup the opus end

#define VOICE_OPUS_APP (OPUS_APPLICATION_VOIP)
#define VOICE_OPUS_BANDWIDTH (OPUS_BANDWIDTH_WIDEBAND)
#define VOICE_OPUS_USE_VBR  (0)
#define VOICE_OPUS_CONSTRAINT_USE_VBR (0)
#define VOICE_OPUS_COMPLEXITY  (0)
#define VOICE_OPUS_USE_INBANDFEC  (0)
#define VOICE_OPUS_USE_DTX (0)
#define VOICE_OPUS_PACKET_LOSS_PERC (0)

#define VOICE_OPUS_20MS_FRAME_COUNT (1)



/**
 *
 * @return
 */
int getFramePeriod();
int getSingleFrameInputSize();
int getTotalFrameInputSize();
int getSingleFrameOutputSize();
int getDecoderBitRate();

void setFramePeriod(int framePeriod);
void setSingleFrameInputSize(int frameSize);
void setTotalFrameInputSize(int totalFrameSize);
void setSingleFrameOutputSize(int pcmOutputSize);

void initDecoder(int framePeriod , int singleFrameInputSize , int totalFrameInputSize , int pcmOutputSize, int bitRate);
void updateDecoderBitRate(int bitRate);

class HuyaDecoder {
public:
    HuyaDecoder() = default;
    void init();
    void setBitRate(int bitRate);
    int decode(const unsigned char * in, opus_int32 in_len, opus_int16 *out, int frame_size);
    void destory();

private:
    OpusDecoder *opusDecoder;
    int m_channels;
};

#ifdef __cplusplus
}
#endif
#endif //OPUS_ANDROID_HUYADECODER_H
