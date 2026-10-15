package com.yovo.biz_appupdate.internal.ui;

import androidx.annotation.NonNull;

/**
 * SDK 更新 UI 需要宿主执行的系统级 Effect。
 */
public final class UpdateUiEffect {

    public enum Kind {
        SAF_PICKER,
        REQUEST_DOWNLOAD_DIR_PERMISSION,
        REQUEST_INSTALL_UNKNOWN_SOURCES,
        OPEN_APP_SETTINGS
    }

    @NonNull
    private final Kind kind;

    private UpdateUiEffect(@NonNull Kind kind) {
        this.kind = kind;
    }

    @NonNull
    public Kind kind() {
        return kind;
    }

    @NonNull
    public static UpdateUiEffect safPicker() {
        return new UpdateUiEffect(Kind.SAF_PICKER);
    }

    @NonNull
    public static UpdateUiEffect requestDownloadDirPermission() {
        return new UpdateUiEffect(Kind.REQUEST_DOWNLOAD_DIR_PERMISSION);
    }

    @NonNull
    public static UpdateUiEffect requestInstallUnknownSources() {
        return new UpdateUiEffect(Kind.REQUEST_INSTALL_UNKNOWN_SOURCES);
    }

    @NonNull
    public static UpdateUiEffect openAppSettings() {
        return new UpdateUiEffect(Kind.OPEN_APP_SETTINGS);
    }
}
