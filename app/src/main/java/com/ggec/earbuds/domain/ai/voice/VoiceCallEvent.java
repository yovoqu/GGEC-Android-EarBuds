package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;

/**
 * 通话状态机唯一的输入事件。所有事件都走同一串行执行器。
 *
 * <p>每个事件对应"谁在什么时候做了什么"的一种事实，不是 UI 动作的直译：
 * 页面点静音发 {@link Muted}，服务端说用户开口发 {@link SpeechStarted}，
 * 本地播放队列排空发 {@link PlaybackDrained}。状态机只认这些事实。</p>
 */
public sealed interface VoiceCallEvent {

    /** 用户按下通话（或页面恢复）。 */
    record Start() implements VoiceCallEvent {
    }

    /** 链路握手完成，可以开始上行。 */
    record Opened() implements VoiceCallEvent {
    }

    /**
     * 采集启动后确认了设备真实的回声消除能力。
     *
     * <p>装配根先请求一个双工方式；只有设备真的挂上了 AEC，才允许是全双工。
     * 这样"能不能打断"由事实决定，不由配置猜。</p>
     */
    record DuplexResolved(@NonNull VoiceDuplex duplex) implements VoiceCallEvent {
    }

    /** 服务端判定有人开口。可能是用户，也可能是我们自己的外放（回声）。 */
    record SpeechStarted() implements VoiceCallEvent {
    }

    /** 服务端判定这一句说完了。 */
    record SpeechStopped() implements VoiceCallEvent {
    }

    /** 助手文字增量。 */
    record AssistantText(@NonNull String delta) implements VoiceCallEvent {
    }

    /**
     * 助手音频块。
     *
     * @param itemId 服务端给这一条回答的 id，打断时用它定位要截断的条目；老网关可能为空
     * @param contentIndex 同一 item 内的音频序号
     */
    record AssistantAudio(@NonNull byte[] pcm16, int sampleRateHz,
                          @Nullable String itemId, int contentIndex) implements VoiceCallEvent {
    }

    /** 服务端说这一轮生成完了。不代表本地播完了。 */
    record ResponseDone() implements VoiceCallEvent {
    }

    /** 本地播放队列已排空（由调度器按音频时长投递）。 */
    record PlaybackDrained() implements VoiceCallEvent {
    }

    /** 采集帧到达：用于电平显示，也用于半双工的上行闸门。 */
    record CaptureFrame(float level) implements VoiceCallEvent {
    }

    /** 用户切换静音。 */
    record Muted(boolean muted) implements VoiceCallEvent {
    }

    /** 用户主动切换双工模式（全双工/半双工）。 */
    record SetDuplex(@NonNull VoiceDuplex duplex) implements VoiceCallEvent {
    }

    /** 用户主动插话（点光晕）。 */
    record Interrupt() implements VoiceCallEvent {
    }

    /** 挂断。 */
    record Stop() implements VoiceCallEvent {
    }

    /** 链路或设备报错。 */
    record Failed(@NonNull AiFailure failure) implements VoiceCallEvent {
    }
}
