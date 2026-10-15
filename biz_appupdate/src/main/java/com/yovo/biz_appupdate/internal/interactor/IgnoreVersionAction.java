package com.yovo.biz_appupdate.internal.interactor;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.PromptProvider;

/**
 * 记录“忽略该版本”：用户选择稍后且非强制更新时调用。
 */
public final class IgnoreVersionAction {

    private final PromptProvider promptProvider;

    public IgnoreVersionAction(@NonNull PromptProvider promptProvider) {
        this.promptProvider = promptProvider;
    }

    public void run(@NonNull String remoteVersion) {
        promptProvider.ignore(remoteVersion);
    }
}
