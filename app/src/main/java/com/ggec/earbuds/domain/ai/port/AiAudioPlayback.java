package com.ggec.earbuds.domain.ai.port;

/**
 * 播放。两种模式都要用：OMNI 播平台回传的音频块，CASCADED 播合成结果。
 *
 * <p>为什么要 `flush()`：用户插话时，已经排队但还没播完的旧回答必须立刻丢掉，
 * 否则会出现"人已经在说下一句，耳机里还在念上一段"。</p>
 */
public interface AiAudioPlayback {

    /** 按给定采样率打开播放（OMNI 是 24k，合成通常是 16k 或 24k）。 */
    void start(int sampleRateHz);

    /** 写入一段 PCM16 单声道小端数据；不阻塞调用线程太久，实现可自行排队。 */
    void write(byte[] pcm16);

    /** 丢弃未播完的数据。 */
    void flush();

    /** 扬声器 true / 听筒 false。 */
    void setSpeakerphone(boolean speaker);

    /** 停止并释放。幂等。 */
    void stop();
}
