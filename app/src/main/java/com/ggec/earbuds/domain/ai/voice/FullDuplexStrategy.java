package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

/**
 * 全双工策略：双向并发通道。
 *
 * <p>助手思考/播音期间仍保持麦克风上行打开；当服务端 VAD 检测到用户开口时，
 * 判定为真实插话并立即打断当前回答（停止播放、截断对话史并回到聆听）。</p>
 */
public final class FullDuplexStrategy implements VoiceDuplexStrategy {

    public static final FullDuplexStrategy INSTANCE = new FullDuplexStrategy();

    private FullDuplexStrategy() {
    }

    @NonNull
    @Override
    public VoiceDuplex mode() {
        return VoiceDuplex.FULL_DUPLEX;
    }

    @Override
    public boolean allowsUplink(boolean assistantActive) {
        return true;
    }

    @Override
    public boolean shouldInterruptOnSpeech(boolean assistantActive) {
        return assistantActive;
    }
}
