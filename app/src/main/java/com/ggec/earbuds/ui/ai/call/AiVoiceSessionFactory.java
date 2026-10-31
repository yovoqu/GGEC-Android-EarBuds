package com.ggec.earbuds.ui.ai.call;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;

/**
 * 造一路语音会话的工厂。
 *
 * <p>通话页不该知道会话要接哪些端口（WebSocket、采集、播放），那是装配根的事。
 * 把"造会话"收成一个函数式接口，页面就只依赖 {@link AiVoiceSession} 这个行为契约：
 * 换成一条假会话做界面自测，也不需要改这里一行。</p>
 */
@FunctionalInterface
public interface AiVoiceSessionFactory {

    /** 每次通话要一条新的会话；实现方不要复用已经 {@code stop()} 过的实例。 */
    @NonNull
    AiVoiceSession create();
}
