package com.yovo.biz_appupdate.internal.interactor;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.PromptProvider;

public final class MarkPromptAction {

    private final PromptProvider promptProvider;

    public MarkPromptAction(@NonNull PromptProvider promptProvider) {
        this.promptProvider = promptProvider;
    }

    public void run(@NonNull String remoteVersion) {
        promptProvider.markPrompted(remoteVersion);
    }
}
