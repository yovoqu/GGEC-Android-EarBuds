package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.model.DownloadMode;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import java.io.File;

public interface DownloadProvider {

    enum StartFailure {
        INVALID_URL,
        NO_HANDLER,
        NO_PERMISSION,
        WIFI_REQUIRED,
        ERROR
    }

    interface Callback {
        void onStarted(@NonNull DownloadMode mode, @NonNull String taskId);

        void onStartFailed(@NonNull DownloadMode mode, @NonNull StartFailure failure);

        default void onProgress(@NonNull String taskId, int percent) {
        }

        default void onSuccess(@NonNull String taskId, @Nullable File file) {
        }

        default void onError(@NonNull String taskId, @NonNull String message) {
        }
    }

    void download(@NonNull DownloadMode mode,
                  @NonNull RemoteUpdate update,
                  @NonNull Callback callback);
}
