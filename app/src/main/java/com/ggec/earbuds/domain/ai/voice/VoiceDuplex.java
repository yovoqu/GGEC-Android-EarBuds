package com.ggec.earbuds.domain.ai.voice;

/**
 * 通话的双工方式。
 *
 * <p><b>为什么这是一个领域概念，而不是采集实现里的一个开关</b>：它决定"助手说话时要不要继续上行"，
 * 而这直接决定状态机允不允许打断、以及服务端会不会被自己的声音触发。把它藏在 AudioRecord 里，
 * 状态机就只能靠猜。</p>
 *
 * <p>{@link #FULL_DUPLEX} 要求设备侧真的在做回声消除（{@code AcousticEchoCanceler} 生效）。
 * 没有回声消除却按全双工跑，扬声器的声音会被麦克风收回去，服务端 VAD 判成"用户开口"，
 * 于是助手自己把自己打断、自己跟自己对话——这不是模型分不清谁在说话，是上行里混进了它自己的声音。</p>
 */
public enum VoiceDuplex {

    /**
     * 半双工：助手发声期间关闭上行。像对讲机，用户要等这一轮念完才能插话。
     *
     * <p>这是没有回声消除时的正确选择：宁可牺牲打断，也不能把助手的声音当成用户。</p>
     */
    HALF_DUPLEX,

    /**
     * 全双工：助手发声期间继续上行，允许用户随时插话。
     *
     * <p>前提是设备侧回声消除真的生效；否则就是自激。</p>
     */
    FULL_DUPLEX;

    /** 助手发声期间是否继续把麦克风送上去。 */
    public boolean streamsWhileAssistantSpeaks() {
        return this == FULL_DUPLEX;
    }
}
