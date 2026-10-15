package com.yovo.biz_appupdate.internal.ui;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.yovo.biz_appupdate.internal.contract.DownloadProvider;
import com.yovo.biz_appupdate.internal.contract.InstallOutcome;
import com.yovo.biz_appupdate.internal.contract.UpdateCheckProvider;
import com.yovo.biz_appupdate.internal.engine.AppUpdateEngine;
import com.yovo.biz_appupdate.internal.model.DownloadMode;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.lib_common.event.OneShotLiveData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * SDK 更新 UI ViewModel：只依赖 {@link AppUpdateEngine}，不接触全局单例。
 */
public final class UpdateViewModel extends ViewModel {

    private final AppUpdateEngine engine;
    private final MutableLiveData<UpdateUiState> state =
            new MutableLiveData<>(UpdateUiState.idle());
    private final OneShotLiveData<UpdateUiEvent> events = new OneShotLiveData<>();
    private final OneShotLiveData<UpdateUiEffect> effects = new OneShotLiveData<>();
    @Nullable
    private File pendingInstallFile;
    @Nullable
    private RemoteUpdate pendingUpdate;
    @Nullable
    private RemoteUpdate lastDownloadedUpdate;
    @Nullable
    private DownloadMode pendingDownloadMode;
    @Nullable
    private RemoteUpdate pendingDownloadUpdate;
    private boolean localPickerResolveRequested;

    public UpdateViewModel(@NonNull AppUpdateEngine engine) {
        this.engine = engine;
    }

    @NonNull
    public LiveData<UpdateUiState> state() {
        return state;
    }

    @NonNull
    public LiveData<UpdateUiEvent> events() {
        return events;
    }

    @NonNull
    public LiveData<UpdateUiEffect> effects() {
        return effects;
    }

    public void attach(@Nullable Intent launchIntent) {
        LaunchDecision decision = engine.consumeSplashIntent(launchIntent);
        if (decision != null) {
            emitDecision(decision);
        }
        onStartup();
    }

    public void onStartup() {
        if (engine.isDownloadDirAccessible()) {
            return;
        }
        if (engine.shouldPickSafDownloadDir()) {
            effects.setValue(UpdateUiEffect.safPicker());
        } else if (!engine.isSafDownloadDir()) {
            effects.setValue(UpdateUiEffect.requestDownloadDirPermission());
        }
    }

    public void refreshLaunchPrompt() {
        engine.runLaunchResolution(this::emitDecision);
    }

    public void checkUpdate() {
        setChecking(true);
        engine.checkUpdate(new UpdateCheckProvider.Callback() {
            @Override
            public void onSuccess(@NonNull RemoteUpdate update) {
                setChecking(false);
                if (update.hasNewVersion()) {
                    events.setValue(UpdateUiEvent.showUpdateDialog(update, false));
                } else {
                    events.setValue(UpdateUiEvent.alreadyLatest());
                }
            }

            @Override
            public void onError(@NonNull String message) {
                setChecking(false);
                events.setValue(UpdateUiEvent.checkFailed(message));
            }
        });
    }

    public void onUpdateDialogShown(@NonNull RemoteUpdate update, boolean autoPrompt) {
        if (autoPrompt) {
            engine.markAutoPromptShown(update.version());
        }
    }

    public void ignoreVersion(@NonNull RemoteUpdate update) {
        engine.ignoreVersion(update.version());
    }

    public void download(@NonNull DownloadMode mode, @NonNull RemoteUpdate update) {
        lastDownloadedUpdate = update;
        if (mode == DownloadMode.INTERNAL && !engine.isDownloadDirAccessible()) {
            pendingDownloadMode = mode;
            pendingDownloadUpdate = update;
            effects.setValue(engine.isSafDownloadDir()
                    ? UpdateUiEffect.safPicker()
                    : UpdateUiEffect.requestDownloadDirPermission());
            return;
        }
        engine.download(mode, update, new DownloadProvider.Callback() {
            @Override
            public void onStarted(@NonNull DownloadMode mode, @NonNull String taskId) {
                setDownload(mode == DownloadMode.INTERNAL, 0);
                events.setValue(UpdateUiEvent.downloadStarted(mode));
            }

            @Override
            public void onStartFailed(@NonNull DownloadMode mode,
                                      @NonNull DownloadProvider.StartFailure failure) {
                setDownload(false, 0);
                events.setValue(UpdateUiEvent.downloadStartFailed(mode));
            }

            @Override
            public void onProgress(@NonNull String taskId, int percent) {
                setDownload(true, percent);
            }

            @Override
            public void onSuccess(@NonNull String taskId, @Nullable File file) {
                setDownload(false, 0);
                events.setValue(UpdateUiEvent.downloadSuccess(file, lastDownloadedUpdate));
            }

            @Override
            public void onError(@NonNull String taskId, @NonNull String message) {
                setDownload(false, 0);
                events.setValue(UpdateUiEvent.downloadFailed(message));
            }
        });
    }

    public void install(@NonNull Context context,
                        @NonNull File apkFile,
                        @Nullable RemoteUpdate update) {
        InstallOutcome result = engine.install(context, apkFile, update);
        if (result == InstallOutcome.VERIFY_FAILED) {
            events.setValue(UpdateUiEvent.installVerifyFailed());
        } else if (result == InstallOutcome.INVALID_FILE) {
            events.setValue(UpdateUiEvent.installInvalidFile());
        } else if (result == InstallOutcome.NO_INSTALLER) {
            events.setValue(UpdateUiEvent.installNoHandler());
        }
    }

    public void requestInstall(@NonNull File apkFile) {
        requestInstall(apkFile, lastDownloadedUpdate);
    }

    public void requestInstall(@NonNull File apkFile, @Nullable RemoteUpdate update) {
        pendingUpdate = update;
        if (engine.canInstallUnknownSources()) {
            events.setValue(UpdateUiEvent.installNow(apkFile, update));
            return;
        }
        pendingInstallFile = apkFile;
        effects.setValue(UpdateUiEffect.requestInstallUnknownSources());
    }

    public void onInstallPermissionResult() {
        if (pendingInstallFile == null) {
            return;
        }
        File file = pendingInstallFile;
        pendingInstallFile = null;
        if (engine.canInstallUnknownSources()) {
            RemoteUpdate update = pendingUpdate;
            pendingUpdate = null;
            events.setValue(UpdateUiEvent.installNow(file, update));
        } else {
            pendingUpdate = null;
            events.setValue(UpdateUiEvent.installPermissionDenied());
        }
    }

    public void onSafTreeResult(boolean granted) {
        if (granted) {
            resumePendingDownloadOrRefresh();
        } else {
            pendingDownloadMode = null;
            pendingDownloadUpdate = null;
        }
    }

    public void onDownloadDirPermissionResume() {
        if (engine.isDownloadDirAccessible()) {
            onDownloadDirPermissionResult(true);
        }
    }

    public void onDownloadDirPermissionResult(boolean granted) {
        if (granted) {
            resumePendingDownloadOrRefresh();
            return;
        }
        pendingDownloadMode = null;
        pendingDownloadUpdate = null;
    }

    public void onDownloadDirPermissionPermanentlyDenied() {
        pendingDownloadMode = null;
        pendingDownloadUpdate = null;
        effects.setValue(UpdateUiEffect.openAppSettings());
    }

    private void resumePendingDownloadOrRefresh() {
        if (pendingDownloadMode != null && pendingDownloadUpdate != null) {
            DownloadMode mode = pendingDownloadMode;
            RemoteUpdate update = pendingDownloadUpdate;
            pendingDownloadMode = null;
            pendingDownloadUpdate = null;
            download(mode, update);
            return;
        }
        refreshLaunchPrompt();
    }

    private void emitDecision(@NonNull LaunchDecision decision) {
        switch (decision.action()) {
            case INSTALL_DIRECT:
                if (decision.update() != null && decision.localApk() != null) {
                    events.setValue(UpdateUiEvent.showInstall(
                            decision.update(), decision.localApk().file()));
                }
                break;
            case SHOW_UPDATE_DIALOG:
                if (decision.update() != null) {
                    events.setValue(UpdateUiEvent.showUpdateDialog(decision.update(), true));
                }
                break;
            case SELECT_LOCAL_APK:
                if (decision.localApks().isEmpty()) {
                    if (!localPickerResolveRequested) {
                        localPickerResolveRequested = true;
                        refreshLaunchPrompt();
                    }
                } else {
                    localPickerResolveRequested = false;
                    events.setValue(UpdateUiEvent.showLocalApkPicker(toFiles(decision.localApks())));
                }
                break;
            case NEED_DOWNLOAD_DIR:
                onStartup();
                break;
            case NONE:
            default:
                break;
        }
    }

    private void setChecking(boolean value) {
        UpdateUiState current = state.getValue();
        if (current != null) {
            state.setValue(current.copyWithChecking(value));
        }
    }

    private void setDownload(boolean inProgress, int percent) {
        UpdateUiState current = state.getValue();
        if (current != null) {
            state.setValue(current.copyWithDownload(inProgress, percent));
        }
    }

    @NonNull
    private static List<File> toFiles(@NonNull List<LocalApk> apks) {
        List<File> files = new ArrayList<>();
        for (LocalApk apk : apks) {
            files.add(apk.file());
        }
        return files;
    }
}
