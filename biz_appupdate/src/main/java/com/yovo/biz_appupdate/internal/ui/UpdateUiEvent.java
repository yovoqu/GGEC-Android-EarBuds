package com.yovo.biz_appupdate.internal.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.model.DownloadMode;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * SDK 更新 UI 一次性事件。
 */
public final class UpdateUiEvent {

    public enum Kind {
        CHECK_ALREADY_LATEST,
        CHECK_FAILED,
        SHOW_UPDATE_DIALOG,
        SHOW_LOCAL_APK_PICKER,
        INSTALL_APK,
        INSTALL_NOW,
        INSTALL_PERMISSION_DENIED,
        INSTALL_VERIFY_FAILED,
        DOWNLOAD_STARTED_INTERNAL,
        DOWNLOAD_STARTED_SYSTEM,
        DOWNLOAD_STARTED_BROWSER,
        DOWNLOAD_START_FAILED,
        DOWNLOAD_SUCCESS,
        DOWNLOAD_FAILED,
        INSTALL_INVALID_FILE,
        INSTALL_NO_HANDLER
    }

    @NonNull
    private final Kind kind;
    @Nullable
    private final RemoteUpdate update;
    @Nullable
    private final File apkFile;
    @Nullable
    private final String message;
    private final boolean autoPrompt;
    @NonNull
    private final List<File> apkFiles;
    @Nullable
    private final DownloadMode mode;

    private UpdateUiEvent(@NonNull Kind kind,
                          @Nullable RemoteUpdate update,
                          @Nullable File apkFile,
                          @Nullable String message,
                          boolean autoPrompt,
                          @NonNull List<File> apkFiles,
                          @Nullable DownloadMode mode) {
        this.kind = kind;
        this.update = update;
        this.apkFile = apkFile;
        this.message = message;
        this.autoPrompt = autoPrompt;
        this.apkFiles = apkFiles;
        this.mode = mode;
    }

    @NonNull
    public Kind kind() {
        return kind;
    }

    @Nullable
    public RemoteUpdate update() {
        return update;
    }

    @Nullable
    public File apkFile() {
        return apkFile;
    }

    @Nullable
    public String message() {
        return message;
    }

    public boolean autoPrompt() {
        return autoPrompt;
    }

    @NonNull
    public List<File> apkFiles() {
        return apkFiles;
    }

    @Nullable
    public DownloadMode mode() {
        return mode;
    }

    @NonNull
    public static UpdateUiEvent alreadyLatest() {
        return simple(Kind.CHECK_ALREADY_LATEST);
    }

    @NonNull
    public static UpdateUiEvent checkFailed(@NonNull String message) {
        return new UpdateUiEvent(Kind.CHECK_FAILED, null, null, message, false,
                Collections.emptyList(), null);
    }

    @NonNull
    public static UpdateUiEvent showUpdateDialog(@NonNull RemoteUpdate update,
                                                 boolean autoPrompt) {
        return new UpdateUiEvent(Kind.SHOW_UPDATE_DIALOG, update, null, null, autoPrompt,
                Collections.emptyList(), null);
    }

    @NonNull
    public static UpdateUiEvent showLocalApkPicker(@NonNull List<File> files) {
        return new UpdateUiEvent(Kind.SHOW_LOCAL_APK_PICKER, null, null, null, false,
                files, null);
    }

    @NonNull
    public static UpdateUiEvent showInstall(@NonNull RemoteUpdate update,
                                            @NonNull File apkFile) {
        return new UpdateUiEvent(Kind.INSTALL_APK, update, apkFile, null, true,
                Collections.emptyList(), null);
    }

    @NonNull
    public static UpdateUiEvent installNow(@NonNull File apkFile,
                                           @Nullable RemoteUpdate update) {
        return new UpdateUiEvent(Kind.INSTALL_NOW, update, apkFile, null, false,
                Collections.emptyList(), null);
    }

    @NonNull
    public static UpdateUiEvent downloadStarted(@NonNull DownloadMode mode) {
        return simple(modeToKind(mode));
    }

    @NonNull
    public static UpdateUiEvent downloadStartFailed(@NonNull DownloadMode mode) {
        return new UpdateUiEvent(Kind.DOWNLOAD_START_FAILED, null, null, null, false,
                Collections.emptyList(), mode);
    }

    @NonNull
    public static UpdateUiEvent downloadSuccess(@Nullable File file,
                                                @Nullable RemoteUpdate update) {
        return new UpdateUiEvent(Kind.DOWNLOAD_SUCCESS, update, file, null, false,
                Collections.emptyList(), null);
    }

    @NonNull
    public static UpdateUiEvent downloadFailed(@Nullable String message) {
        return new UpdateUiEvent(Kind.DOWNLOAD_FAILED, null, null, message, false,
                Collections.emptyList(), null);
    }

    @NonNull
    public static UpdateUiEvent installVerifyFailed() {
        return simple(Kind.INSTALL_VERIFY_FAILED);
    }

    @NonNull
    public static UpdateUiEvent installInvalidFile() {
        return simple(Kind.INSTALL_INVALID_FILE);
    }

    @NonNull
    public static UpdateUiEvent installNoHandler() {
        return simple(Kind.INSTALL_NO_HANDLER);
    }

    @NonNull
    public static UpdateUiEvent installPermissionDenied() {
        return simple(Kind.INSTALL_PERMISSION_DENIED);
    }

    @NonNull
    private static UpdateUiEvent simple(@NonNull Kind kind) {
        return new UpdateUiEvent(kind, null, null, null, false, Collections.emptyList(), null);
    }

    private static Kind modeToKind(@NonNull DownloadMode mode) {
        switch (mode) {
            case INTERNAL:
                return Kind.DOWNLOAD_STARTED_INTERNAL;
            case BROWSER:
                return Kind.DOWNLOAD_STARTED_BROWSER;
            case SYSTEM:
            default:
                return Kind.DOWNLOAD_STARTED_SYSTEM;
        }
    }
}
