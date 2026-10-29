package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

/**
 * 一路语音会话。语音交流模式只有一种实现：一条实时链路直连全/多模态模型。
 *
 * <p>APP 侧不再拼"识别 → 对话 → 合成"三段：转写、回答、发声都由实时模型在服务端完成，
 * 客户端只负责上下行音频与文字增量。所以这个接口没有"用哪种语音链路"的概念，
 * 页面也不必按模式分支。</p>
 *
 * <p><b>为什么 start 不再接收"用哪个模型"</b>：语音模型是 APP 预设的一项能力
 * （{@code AiVoiceConfig}），不是界面挑出来的平台引用。会话自己去设置里读它，
 * 页面因此不必知道端点和密钥，也不会出现"界面传的模型和设置里的不是同一个"。</p>
 *
 * <p>为什么把它做成"会话"而不是"方法调用"：一次通话是长事务——采集、网络、播放同时在跑，
 * 中途会被静音、插话、挂断打断。用一次性调用去表达，调用方就得自己维护一套状态，
 * 等于把状态机搬进页面。</p>
 *
 * <p>实现方负责：拿到预设模型与密钥、把三路（采集/网络/播放）收敛成一条事件流。
 * 调用方负责：把 {@link AiVoiceSnapshot} 画出来、把用户操作转回来。</p>
 */
public interface AiVoiceSession {

    /**
     * 开始一路通话。必须在主线程以外的执行器上做初始化（采集与网络都会阻塞）。
     * 缺模型或密钥时由实现方报成失败快照，页面不替它猜。
     */
    void start(@NonNull Listener listener);

    /** 静音只停上行，不停正在播放的回答。 */
    void setMuted(boolean muted);

    /** 切换扬声器/听筒路由。 */
    void setSpeakerphone(boolean speaker);

    /** 用户插话：立刻停止正在生成的回答并回到聆听。 */
    void interrupt();

    /** 挂断：停止采集、取消请求、释放播放。必须幂等。 */
    void stop();

    interface Listener {
        /** 状态、电平、发言记录都从这里出来；实现要保证回调串行。 */
        void onSnapshot(@NonNull AiVoiceSnapshot snapshot);
    }
}
