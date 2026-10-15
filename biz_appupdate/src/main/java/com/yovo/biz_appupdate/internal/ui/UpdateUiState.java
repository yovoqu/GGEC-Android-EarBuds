package com.yovo.biz_appupdate.internal.ui;

import androidx.annotation.NonNull;

/**
 * SDK 更新 UI 状态：可跨配置变更保存。
 */
public final class UpdateUiState {

    private final boolean checking;
    private final boolean downloadInProgress;
    private final int downloadPercent;

    public UpdateUiState(boolean checking,
                         boolean downloadInProgress,
                         int downloadPercent) {
        this.checking = checking;
        this.downloadInProgress = downloadInProgress;
        this.downloadPercent = downloadPercent;
    }

    @NonNull
    public static UpdateUiState idle() {
        return new UpdateUiState(false, false, 0);
    }

    public boolean checking() {
        return checking;
    }

    public boolean downloadInProgress() {
        return downloadInProgress;
    }

    public int downloadPercent() {
        return downloadPercent;
    }

    @NonNull
    public UpdateUiState copyWithChecking(boolean value) {
        return new UpdateUiState(value, downloadInProgress, downloadPercent);
    }

    @NonNull
    public UpdateUiState copyWithDownload(boolean inProgress, int percent) {
        return new UpdateUiState(checking, inProgress, percent);
    }
}
