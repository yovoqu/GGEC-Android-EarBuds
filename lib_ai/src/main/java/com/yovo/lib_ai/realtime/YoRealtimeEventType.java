package com.yovo.lib_ai.realtime;

/**
 * 全双工语音链路上，调用方真正需要处理的事件。
 *
 * <p>Realtime 协议里有几十种帧（{@code session.updated}、{@code conversation.item.*}、
 * {@code response.output_item.*} …）。它们全部不出现在这里：通话页只关心
 * 「连上了 / 人开始说了 / 人说完了 / 来了一段字 / 来了一段声音 / 这轮说完了 / 出错了 / 结束了」
 * 这八件事。后面三个是会话层才用得上的信号：配置已生效、用户那一句的转写、函数调用。
 * 通话页的状态机可以不接它们。把协议的帧名泄漏到界面层，会让界面层的状态机和网关实现绑定死，
 * 换一个兼容端点就得改界面。</p>
 */
enum YoRealtimeEventType {

    /**
     * 会话已建立（收到 {@code session.created}），可以开始往上送音频了。
     *
     * <p>注意：这不是「TCP/WebSocket 打开」。握手成功但服务端从没发 {@code session.created}，
     * 说明对端不是 Realtime 端点，这种情况不会给出 {@code OPENED}，
     * 而是超时后的 {@code ERROR}（{@code PROTOCOL}）。</p>
     */
    OPENED,

    /** 服务端 VAD 判定人开始说话。上层据此把状态切到 LISTENING、并打断正在播放的声音。 */
    INPUT_SPEECH_STARTED,

    /** 服务端 VAD 判定人说完了一句。上层据此把状态切到 THINKING。 */
    INPUT_SPEECH_STOPPED,

    /**
     * 回答的文字增量，{@link YoRealtimeEvent#text()} 是一小段新文字，不是全量。
     * 上层自己拼接，这样首字延迟最短。
     */
    ASSISTANT_TEXT_DELTA,

    /**
     * 回答的声音增量，{@link YoRealtimeEvent#audioPcm16()} 是这一段 PCM16 字节
     * （默认 24kHz/16bit/单声道，见 {@code YoRealtimeConfig#outputSampleRateHz()}）。
     * 上层直接喂给 {@code AudioTrack}，不要在这里做拼接：音频是流，攒着播会卡。
     */
    ASSISTANT_AUDIO_CHUNK,

    /** 一轮回答结束（{@code response.done}）。上层据此回到 LISTENING。用量在事件上，不在这个枚举里。 */
    RESPONSE_DONE,

    /**
     * {@code session.update} 已被服务端接受。GA 方言要等这一帧才放行缓存音频。
     */
    SESSION_UPDATED,

    /**
     * 用户刚才那一句的转写完成。{@link YoRealtimeEvent#text()} 是整句，不是增量。
     */
    USER_TRANSCRIPT,

    /**
     * 模型要调用一个函数，参数已经拼完。名字、call id 和参数 JSON 在事件上。
     */
    TOOL_CALL,

    /**
     * 出错了，{@link YoRealtimeEvent#errorKind()} 给出分类，{@link YoRealtimeEvent#message()} 给出可读原因。
     *
     * <p>{@code ERROR} 不一定终结链路：服务端可以在会话中途报一次可恢复的错误
     * （例如一次无效的 {@code response.cancel}），链路还活着。
     * 链路真的断了的话，后面一定还会跟一条 {@code CLOSED}。</p>
     */
    ERROR,

    /**
     * 链路结束，**终态**：此后不会再有这个监听器的任何事件。
     * 自己调 {@code close()} 时只有 {@code CLOSED}；被动断开时是 {@code ERROR} + {@code CLOSED}。
     */
    CLOSED
}
