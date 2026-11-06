package com.ggec.earbuds.domain.ai.port;

/**
 * 播放。语音交流模式播实时模型回传的音频块。
 *
 * <p><b>为什么必须能 flush</b>：用户插话时，已经排队但还没播完的旧回答必须立刻丢掉，
 * 否则会出现"人已经在说下一句，耳机里还在念上一段"。</p>
 *
 * <p><b>为什么要有"已播进度"</b>：打断时服务端需要知道用户实际听到了多少毫秒
 * （OpenAI/通义的 {@code conversation.item.truncate}）。按"收到了多少字节"算会把
 * 还在队列里的音频也算进去，服务端于是以为用户已经听过后半句，下一轮回答就接在那半句上。
 * 所以进度必须由播放实现按真正写出的字节记账。</p>
 */
public interface AiAudioPlayback {

    /** 按给定采样率打开播放（实时下行是 24k）。 */
    void start(int sampleRateHz);

    /**
     * 写入一段 PCM16 单声道小端数据。
     *
     * @param itemId 这段音频属于哪一条助手回答（服务端 item id）；为空表示无法截断
     * @param contentIndex 同一 item 内的音频内容序号
     */
    void write(byte[] pcm16, String itemId, int contentIndex);

    /** 丢弃未播完的数据，并把已播进度清零（截断只该发生一次）。 */
    void flush();

    /**
     * 取出"用户已经听到"的进度并清零。
     *
     * @return 没有正在播的条目时返回 null
     */
    Heard captureHeard();

    /** 扬声器 true / 听筒 false。 */
    void setSpeakerphone(boolean speaker);

    /** 停止并释放。幂等。 */
    void stop();

    /** 一条回答里用户已经听到的部分。 */
    record Heard(String itemId, int contentIndex, int elapsedMs) {
    }
}
