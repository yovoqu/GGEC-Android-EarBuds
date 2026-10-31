package com.yovo.lib_ai.realtime;

import androidx.annotation.NonNull;

/**
 * 一条到 Realtime 端点的全双工链路（OMNI 模式用的那个端口）。
 *
 * <p>生命周期：{@link #open} 一次 → 若干次 {@link #appendAudio} / {@link #cancelResponse}
 * → {@link #close} 一次（可以多调，见下）。</p>
 *
 * <p><b>为什么 {@link #close()} 必须幂等：</b>挂断的触发点太多了——用户点挂断、
 * 页面被系统回收、通话超时、网络断开后上层收尾。这些路径经常在同一次通话里撞上，
 * 谁也不知道自己是第一个。如果 close 不幂等，第二条路径要么抛异常把收尾流程打断，
 * 要么对已经关掉的 socket 再发一次 close 帧、再回调一次 {@code CLOSED}，
 * 让上层把已经结算过的一次通话再结算一次。这里的 close 只认同一次，剩下的都是空操作。</p>
 *
 * <p><b>重连不在这一层。</b>断线只会折成一个 {@code ERROR} 加一个 {@code CLOSED}。
 * 要不要重开、要不要告诉用户，是上层（OmniVoiceStrategy）的事——它才知道
 * 这一断是不是用户自己挂的，也才有地方显示「网络不稳，重连中」。</p>
 */
interface YoRealtimeLink extends AutoCloseable {

    /**
     * 建立连接。**非阻塞**：返回时通常还没连上，结果全部走监听器
     * （{@code OPENED} 或 {@code ERROR} + {@code CLOSED}）。
     *
     * <p>一个实例只能 open 一次。重开要新建实例，这样旧实例的收尾事件不会串到新会话里。</p>
     *
     * @param config 连接参数，见 {@link YoRealtimeConfig}
     * @param listener 事件出口。所有回调在同一条后台线程上顺序发出。
     * @throws IllegalStateException 已经 open 过，或者已经 close
     */
    void open(@NonNull YoRealtimeConfig config, @NonNull RealtimeSink listener);

    /**
     * 往上送一段采集到的声音。
     *
     * <p>可以直接在 {@code AudioRecord} 的读线程上调用：这里只做一次 base64 和一次入队，
     * 不做网络等待。协议要求 PCM16，所以奇数长度的尾巴（半个采样）会被丢掉，
     * 否则后面整段字节都会错位，听起来是持续噪声。</p>
     *
     * <p>会话还没建立（没收到 {@code OPENED}）时帧会先缓存在内存里，等 {@code OPENED}
     * 再补发；缓存有上限，超了从最旧的丢——实时语音里过期音频没有价值，
     * 但内存涨上去会把进程拖死。挂断之后调用是空操作（采集线程上抛异常等于让通话页崩在收尾阶段）。</p>
     */
    void appendAudio(@NonNull byte[] pcm16);

    /**
     * 打断正在生成的回答：只发 {@code response.cancel}。
     *
     * <p>这一层不知道用户听到了哪一毫秒。要连同对话史一起截断，用
     * {@link YoRealtimeSession}，它会在 cancel 之后补 {@link #truncate}。</p>
     */
    void cancelResponse();

    /**
     * 客户端认为这一句说完了。只在关掉服务端 VAD（按住说话）时需要。
     */
    void commitInput();

    /**
     * 丢掉还没提交的上行音频。打断之后清掉半句，避免它被算进下一轮。
     */
    void clearInput();

    /**
     * 请模型现在开口。工具结果写回之后要再调一次，否则模型不会把结果说出来。
     */
    void createResponse();

    /**
     * 把一条助手音频截断到用户实际听到的位置。
     *
     * @param audioEndMs 从这条音频开头算起、已经播放的毫秒
     */
    void truncate(@NonNull String itemId, int contentIndex, int audioEndMs);

    /**
     * 把函数结果写回对话。不会自动 {@link #createResponse()}，
     * 调用方可以先写多个结果再开口一次。
     */
    void submitToolOutput(@NonNull String callId, @NonNull String output);

    /**
     * 结束通话。幂等，可以在任意线程反复调用。
     */
    @Override
    void close();
}
