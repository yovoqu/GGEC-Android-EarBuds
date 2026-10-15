package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface PromptProvider {

    boolean shouldPrompt(@Nullable String remoteVersion);

    void markPrompted(@NonNull String remoteVersion);

    /** 用户是否已忽略该版本（仅对非强制更新生效）。 */
    boolean isIgnored(@NonNull String remoteVersion);

    /** 记录忽略的版本。 */
    void ignore(@NonNull String remoteVersion);
}
