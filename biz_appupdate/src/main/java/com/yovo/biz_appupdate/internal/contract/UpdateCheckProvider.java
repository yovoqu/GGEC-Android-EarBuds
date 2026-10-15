package com.yovo.biz_appupdate.internal.contract;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

public interface UpdateCheckProvider {

    interface Callback {
        void onSuccess(@NonNull RemoteUpdate update);

        void onError(@NonNull String message);
    }

    void check(@NonNull String currentVersion, @NonNull Callback callback);
}
