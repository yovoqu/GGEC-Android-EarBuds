package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiFailure;

import java.util.List;

/**
 * 通话页要画的全部东西。页面不判断状态，只画这个快照。
 *
 * @param state      当前阶段
 * @param turns      本轮通话的对话记录（用户与助手交替，含未定稿的识别结果）
 * @param inputLevel 0..1 的输入电平，页面用它画光晕
 * @param failure    非空表示这一路已经失败，页面显示原因而不是假装在听
 */
public record AiVoiceSnapshot(
        @NonNull State state,
        @NonNull List<Turn> turns,
        float inputLevel,
        @Nullable AiFailure failure) {

    public AiVoiceSnapshot {
        turns = List.copyOf(turns);
        inputLevel = Math.max(0f, Math.min(1f, inputLevel));
    }

    public static AiVoiceSnapshot idle() {
        return new AiVoiceSnapshot(State.IDLE, List.of(), 0f, null);
    }

    /** 通话页需要区分的阶段。没有 RINGING：这不是电话，没有"对方接听"这件事。 */
    public enum State {
        IDLE,
        CONNECTING,
        LISTENING,
        THINKING,
        SPEAKING,
        MUTED,
        FAILED,
        ENDED
    }

    /**
     * 一轮发言。
     *
     * @param finalTranscript 识别结果是否定稿；false 表示还在逐字上屏
     */
    public record Turn(@NonNull String id, boolean user, @NonNull String text,
                       boolean finalTranscript, long atMillis) {
    }
}
