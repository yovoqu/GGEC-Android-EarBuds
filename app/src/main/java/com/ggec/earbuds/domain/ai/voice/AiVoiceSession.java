package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiVoiceSettings;

/**
 * 一路语音会话。OMNI 与 CASCADED 是两个实现，页面只认这个接口。
 *
 * <p>为什么把它做成"会话"而不是"方法调用"：一次通话是长事务——采集、识别、生成、播放同时在跑，
 * 中途会被静音、插话、挂断打断。用一次性调用去表达，调用方就得自己维护一套状态，等于把状态机搬进页面。</p>
 *
 * <p>实现方负责：拿到平台与密钥、选协议、把三路（采集/网络/播放）收敛成一条事件流。
 * 调用方负责：把 {@link AiVoiceSnapshot} 画出来、把用户操作转回来。</p>
 */
public interface AiVoiceSession {

    /**
     * 开始一路通话。必须在主线程以外的执行器上做初始化（采集与网络都会阻塞）。
     *
     * @param platformId 用哪个平台；级别低于设置里的显式指定
     */
    void start(@NonNull AiVoiceSettings settings, @NonNull String platformId, @NonNull Listener listener);

    /** 静音只停上行，不停识别中的音频与正在播放的回答。 */
    void setMuted(boolean muted);

    /** 切换扬声器/听筒路由。 */
    void setSpeakerphone(boolean speaker);

    /** 用户插话：立刻停止正在生成的回答并回到聆听（OMNI 会发取消，CASCADED 会取消对话请求）。 */
    void interrupt();

    /** 挂断：停止采集、取消请求、释放播放与合成。必须幂等。 */
    void stop();

    interface Listener {
        /** 状态、电平、发言记录都从这里出来；实现要保证回调串行。 */
        void onSnapshot(@NonNull AiVoiceSnapshot snapshot);
    }
}
