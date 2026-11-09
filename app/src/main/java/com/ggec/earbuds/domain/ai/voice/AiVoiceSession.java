package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.port.AiAudioSource;

/**
 * 一路语音会话。语音交流模式只有一种实现：一条实时链路直连全/多模态模型。
 *
 * <p>会话内部通过统一的上行音频管线将“输入源切换”与“网络链路/状态机”完全解耦：
 * <ul>
 *   <li>通话期间可随时通过 {@link #selectAudioSource(String)} 或 {@link #setAudioSource(AiAudioSource)}
 *       无缝热切换输入源，无需重连 WebSocket 链路、不重置对话上下文与双工状态；</li>
 *   <li>无论输入源采用内置麦克风、蓝牙耳机、USB 外设或外部推流（16k/48k/8k 等），
 *       管线均按实时链路协商线速率自动适配重采样并上行。</li>
 * </ul>
 * </p>
 */
public interface AiVoiceSession {

    /**
     * 开始一路通话。必须在主线程以外的执行器上做初始化（音频管线与网络都会阻塞）。
     * 缺模型或密钥时由实现方报成失败快照。
     */
    void start(@NonNull Listener listener);

    /** 本地输入静音开关控制（仅闭合上行闸门，绝不中断正在播放的回答与 AI 会话）。 */
    void setMicMuted(boolean micMuted);

    /** 设置双工通信方式（全双工/半双工）。严格单点控制。 */
    void setDuplex(@NonNull VoiceDuplex duplex);

    /** 按源 id 热切换当前使用的音频输入源（若会话已就绪则立即无缝切流）。 */
    void selectAudioSource(@NonNull String sourceId);

    /** 直接指定并热切换到一个自定义音频输入源实例。 */
    void setAudioSource(@NonNull AiAudioSource source);

    /** 切换扬声器/听筒路由。 */
    void setSpeakerphone(boolean speaker);

    /** 用户插话：立刻停止正在生成的回答并回到聆听。 */
    void interrupt();

    /** 挂断：停止上行管线、取消请求、释放播放与目录监听。必须幂等。 */
    void stop();

    interface Listener {
        /** 状态、电平、源信息与发言记录都从这里出来；实现要保证回调串行。 */
        void onSnapshot(@NonNull AiVoiceSnapshot snapshot);
    }
}
