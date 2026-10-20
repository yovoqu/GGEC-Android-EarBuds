package com.yovo.lib_ai.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.chat.YoChatPartEncoder;
import com.yovo.lib_ai.chat.YoChatRequestPolicy;
import com.yovo.lib_ai.messages.YoMessagesPolicy;
import com.yovo.lib_ai.platform.YoPlatform;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.responses.YoResponsesPolicy;

/**
 * 测试用：把任意平台包一层，去掉它的模型列表能力，模拟不支持列表的主机。
 */
final class NoListingPlatform implements YoPlatform {

    private final YoPlatform delegate;

    NoListingPlatform(YoPlatform delegate) {
        this.delegate = delegate;
    }

    @NonNull
    @Override
    public String id() {
        return delegate.id();
    }

    @Nullable
    @Override
    public YoServiceTarget chatTarget() {
        return delegate.chatTarget();
    }

    @Nullable
    @Override
    public YoServiceTarget responsesTarget() {
        return delegate.responsesTarget();
    }

    @Nullable
    @Override
    public YoServiceTarget messagesTarget() {
        return delegate.messagesTarget();
    }

    @NonNull
    @Override
    public YoChatPartEncoder chatPartEncoder() {
        return delegate.chatPartEncoder();
    }

    @NonNull
    @Override
    public YoChatRequestPolicy chatPolicy() {
        return delegate.chatPolicy();
    }

    @NonNull
    @Override
    public YoResponsesPolicy responsesPolicy() {
        return delegate.responsesPolicy();
    }

    @NonNull
    @Override
    public YoMessagesPolicy messagesPolicy() {
        return delegate.messagesPolicy();
    }
}
