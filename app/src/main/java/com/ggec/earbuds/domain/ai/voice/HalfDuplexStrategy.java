package com.ggec.earbuds.domain.ai.voice;

import androidx.annotation.NonNull;

/**
 * 半双工策略：单向交替轮流交谈。
 *
 * <p>助手思考/说话/排空期间闭合麦克风上行闸门，彻底隔绝外放回声回传；
 * 此期间若仍收到延迟到达的服务端 {@code SpeechStarted}，仅视为残余音频误判，不触发自动打断
 * （用户仍可通过点击光晕等显式交互发起 {@link VoiceCallEvent.Interrupt}）。</p>
 */
public final class HalfDuplexStrategy implements VoiceDuplexStrategy {

    public static final HalfDuplexStrategy INSTANCE = new HalfDuplexStrategy();

    private HalfDuplexStrategy() {
    }

    @NonNull
    @Override
    public VoiceDuplex mode() {
        return VoiceDuplex.HALF_DUPLEX;
    }

    @Override
    public boolean allowsUplink(boolean assistantActive) {
        return !assistantActive;
    }

    @Override
    public boolean shouldInterruptOnSpeech(boolean assistantActive) {
        return false;
    }
}
