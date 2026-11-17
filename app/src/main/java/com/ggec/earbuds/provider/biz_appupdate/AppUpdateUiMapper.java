package com.ggec.earbuds.provider.biz_appupdate;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.update.port.UpdateUiPort;
import com.yovo.biz_appupdate.api.UpdateDownloadMode;
import com.yovo.biz_appupdate.api.UpdateUiEffect;
import com.yovo.biz_appupdate.api.UpdateUiEvent;
import com.yovo.biz_appupdate.api.UpdateUiState;

/**
 * biz_appupdate 的公开类型 ↔ 本应用更新端口词汇（纯函数，无状态）。
 *
 * <p>两个方向都只做等价翻译，不加策略、不拼文案：文案在
 * {@code res/values/strings_update.xml}。翻译目标是 {@code domain/update/port/UpdateUiPort}
 * 里的词汇，因此 SDK 类型一步都出不了 provider 目录。</p>
 */
public final class AppUpdateUiMapper {

    private AppUpdateUiMapper() {
    }

    @NonNull
    public static UpdateUiPort.State toState(@NonNull UpdateUiState state) {
        return new UpdateUiPort.State(
                state.checking(), state.downloadInProgress(), state.downloadPercent());
    }

    @NonNull
    public static UpdateUiPort.Event toEvent(@NonNull UpdateUiEvent event) {
        return switch (event) {
            case UpdateUiEvent.Notice notice ->
                    new UpdateUiPort.Event.Notify(toNotice(notice.kind()), notice.detail());
            case UpdateUiEvent.ShowUpdatePrompt prompt ->
                    new UpdateUiPort.Event.ShowUpdatePrompt(toPrompt(prompt));
            case UpdateUiEvent.ShowLocalApkPicker picker ->
                    new UpdateUiPort.Event.ShowLocalApkPicker(picker.apkFileNames());
            case UpdateUiEvent.ConfirmInstall confirm ->
                    new UpdateUiPort.Event.ConfirmInstall(confirm.apkFileName());
        };
    }

    @NonNull
    public static UpdateUiPort.Notice toNotice(@NonNull UpdateUiEvent.Notice.Kind kind) {
        return switch (kind) {
            case ALREADY_LATEST -> UpdateUiPort.Notice.ALREADY_LATEST;
            case CHECK_FAILED -> UpdateUiPort.Notice.CHECK_FAILED;
            case DOWNLOAD_STARTED_SYSTEM -> UpdateUiPort.Notice.DOWNLOAD_STARTED_SYSTEM;
            case DOWNLOAD_STARTED_BROWSER -> UpdateUiPort.Notice.DOWNLOAD_STARTED_BROWSER;
            case DOWNLOAD_START_FAILED_INTERNAL -> UpdateUiPort.Notice.DOWNLOAD_START_FAILED_INTERNAL;
            case DOWNLOAD_START_FAILED_SYSTEM -> UpdateUiPort.Notice.DOWNLOAD_START_FAILED_SYSTEM;
            case DOWNLOAD_START_FAILED_BROWSER -> UpdateUiPort.Notice.DOWNLOAD_START_FAILED_BROWSER;
            case DOWNLOAD_COMPLETED -> UpdateUiPort.Notice.DOWNLOAD_COMPLETED;
            case DOWNLOAD_FAILED -> UpdateUiPort.Notice.DOWNLOAD_FAILED;
            case INSTALL_INVALID_FILE -> UpdateUiPort.Notice.INSTALL_INVALID_FILE;
            case INSTALL_VERIFY_FAILED -> UpdateUiPort.Notice.INSTALL_VERIFY_FAILED;
            case INSTALL_NO_INSTALLER -> UpdateUiPort.Notice.INSTALL_NO_INSTALLER;
            case INSTALL_PERMISSION_DENIED -> UpdateUiPort.Notice.INSTALL_PERMISSION_DENIED;
        };
    }

    @NonNull
    public static UpdateUiPort.Prompt toPrompt(@NonNull UpdateUiEvent.ShowUpdatePrompt event) {
        return new UpdateUiPort.Prompt(
                event.version(), event.description(), event.forceUpdate());
    }

    @NonNull
    public static UpdateUiPort.Effect toEffect(@NonNull UpdateUiEffect.Kind kind) {
        return switch (kind) {
            case REQUEST_INSTALL_UNKNOWN_SOURCES -> UpdateUiPort.Effect.REQUEST_INSTALL_PERMISSION;
            case OPEN_APP_SETTINGS -> UpdateUiPort.Effect.OPEN_APP_SETTINGS;
        };
    }

    @NonNull
    public static UpdateDownloadMode toDownloadMode(@NonNull UpdateUiPort.DownloadChoice choice) {
        return switch (choice) {
            case INTERNAL -> UpdateDownloadMode.INTERNAL;
            case SYSTEM -> UpdateDownloadMode.SYSTEM;
            case BROWSER -> UpdateDownloadMode.BROWSER;
        };
    }
}
