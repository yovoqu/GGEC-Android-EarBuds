package com.yovo.biz_appupdate.internal.presenter;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.api.UpdateDownloadMode;
import com.yovo.biz_appupdate.api.UpdateUiEffect;
import com.yovo.biz_appupdate.api.UpdateUiEvent;
import com.yovo.biz_appupdate.api.UpdateUiPort;
import com.yovo.biz_appupdate.api.UpdateUiSession;
import com.yovo.biz_appupdate.api.UpdateUiState;
import com.yovo.biz_appupdate.internal.contract.DownloadProvider;
import com.yovo.biz_appupdate.internal.contract.InstallOutcome;
import com.yovo.biz_appupdate.internal.contract.UpdateCheckProvider;
import com.yovo.biz_appupdate.internal.engine.AppUpdateEngine;
import com.yovo.biz_appupdate.internal.model.DownloadMode;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.biz_appupdate.provider.lib_common.log.YoUpdateLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 更新 UI 呈现器：更新流程的唯一状态机，**不认识任何视图**。
 *
 * <p>它把「检查、下载、安装」三段流程收敛成 {@code UpdateUiState} + 一次性事件 + 系统级
 * effect，经 {@link UpdateUiPort} 推给宿主；宿主只渲染并把它换成动作回灌
 * {@link UpdateUiSession}。文案、弹窗形态、Loading 组件全部不在本模块。</p>
 *
 * <p>回调一律切到主线程：引擎的下载/检查回调可能来自工作线程，而宿主一定是主线程渲染。</p>
 */
public final class UpdateUiPresenter implements UpdateUiSession {

    /** 主线程调度口子：默认 {@code Handler(main)}，测试可以换成同步执行。 */
    public interface MainThread {
        void post(@NonNull Runnable action);
    }

    private static final String TAG = "UpdateUiPresenter";

    private final AppUpdateEngine engine;
    private final MainThread mainThread;

    @Nullable
    private Context hostContext;
    @Nullable
    private UpdateUiPort port;
    private boolean detached;

    private UpdateUiState state = UpdateUiState.idle();

    @Nullable
    private RemoteUpdate currentUpdate;
    @Nullable
    private RemoteUpdate lastDownloadedUpdate;

    @Nullable
    private File confirmTargetFile;
    @Nullable
    private RemoteUpdate confirmTargetUpdate;
    @Nullable
    private File permissionTargetFile;
    @Nullable
    private RemoteUpdate permissionTargetUpdate;

    @Nullable
    private List<File> localApkCandidates;
    private boolean localPickerResolveRequested;

    public UpdateUiPresenter(@NonNull AppUpdateEngine engine) {
        this(engine, defaultMainThread());
    }

    public UpdateUiPresenter(@NonNull AppUpdateEngine engine, @NonNull MainThread mainThread) {
        this.engine = engine;
        this.mainThread = mainThread;
    }

    private static MainThread defaultMainThread() {
        if (Looper.getMainLooper() == null) {
            return Runnable::run;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        return handler::post;
    }

    /** 绑定宿主：立即推一次当前状态，再消费启动 Intent 的决策。 */
    public void attach(@NonNull Context context, @Nullable Intent launchIntent,
                       @NonNull UpdateUiPort port) {
        this.hostContext = context;
        this.port = port;
        this.detached = false;
        publishState(state);
        LaunchDecision decision = engine.consumeSplashIntent(launchIntent);
        if (decision != null) {
            emitDecision(decision);
        }
    }

    @Override
    public void detach() {
        detached = true;
        port = null;
        hostContext = null;
        confirmTargetFile = null;
        confirmTargetUpdate = null;
        permissionTargetFile = null;
        permissionTargetUpdate = null;
        localApkCandidates = null;
    }

    @Override
    public void checkUpdate() {
        publishState(state.withChecking(true));
        engine.checkUpdate(new UpdateCheckProvider.Callback() {
            @Override
            public void onSuccess(@NonNull RemoteUpdate update) {
                publishState(state.withChecking(false));
                if (update.hasNewVersion()) {
                    showPrompt(update, false);
                } else {
                    emit(UpdateUiEvent.Notice.of(UpdateUiEvent.Notice.Kind.ALREADY_LATEST));
                }
            }

            @Override
            public void onError(@NonNull String message) {
                publishState(state.withChecking(false));
                emit(new UpdateUiEvent.Notice(
                        UpdateUiEvent.Notice.Kind.CHECK_FAILED, message));
            }
        });
    }

    @Override
    public void download(@NonNull UpdateDownloadMode mode) {
        RemoteUpdate update = currentUpdate;
        if (update == null) {
            YoUpdateLogger.w(TAG, "没有待下载版本，忽略下载请求");
            return;
        }
        startDownload(toInternalMode(mode), update);
    }

    @Override
    public void deferUpdate() {
        RemoteUpdate update = currentUpdate;
        if (update == null) {
            return;
        }
        engine.ignoreVersion(update.version());
        currentUpdate = null;
    }

    @Override
    public void confirmInstall() {
        File file = confirmTargetFile;
        RemoteUpdate update = confirmTargetUpdate;
        confirmTargetFile = null;
        confirmTargetUpdate = null;
        if (file == null) {
            return;
        }
        if (engine.canInstallUnknownSources()) {
            install(file, update);
            return;
        }
        permissionTargetFile = file;
        permissionTargetUpdate = update;
        emitEffect(UpdateUiEffect.Kind.REQUEST_INSTALL_UNKNOWN_SOURCES);
    }

    @Override
    public void selectLocalApk(int index) {
        List<File> candidates = localApkCandidates;
        localApkCandidates = null;
        if (candidates == null || index < 0 || index >= candidates.size()) {
            YoUpdateLogger.w(TAG, "本地安装包选择越界: index=" + index);
            return;
        }
        // 用户已经在列表里选过包，不再多要一次确认；直接进权限判定后的安装。
        confirmTargetFile = candidates.get(index);
        confirmTargetUpdate = null;
        confirmInstall();
    }

    /**
     * 安装未知来源权限的设置页返回：拿到权限就直接安装，否则提示被拒并请宿主打开应用设置页。
     */
    @Override
    public void onInstallPermissionResult() {
        File file = permissionTargetFile;
        RemoteUpdate update = permissionTargetUpdate;
        permissionTargetFile = null;
        permissionTargetUpdate = null;
        if (file == null) {
            return;
        }
        if (engine.canInstallUnknownSources()) {
            install(file, update);
            return;
        }
        emit(UpdateUiEvent.Notice.of(UpdateUiEvent.Notice.Kind.INSTALL_PERMISSION_DENIED));
        emitEffect(UpdateUiEffect.Kind.OPEN_APP_SETTINGS);
    }

    private void refreshLaunchPrompt() {
        engine.runLaunchResolution(this::emitDecision);
    }

    private void emitDecision(@NonNull LaunchDecision decision) {
        switch (decision.action()) {
            case INSTALL_DIRECT -> {
                RemoteUpdate update = decision.update();
                LocalApk localApk = decision.localApk();
                if (update == null || localApk == null) {
                    return;
                }
                engine.markAutoPromptShown(update.version());
                requestInstallConfirm(localApk.file(), update, true);
            }
            case SHOW_UPDATE_DIALOG -> {
                RemoteUpdate update = decision.update();
                if (update != null) {
                    showPrompt(update, true);
                }
            }
            case SELECT_LOCAL_APK -> handleSelectLocalApk(decision.localApks());
            case NONE -> {
            }
        }
    }

    private void handleSelectLocalApk(@NonNull List<LocalApk> apks) {
        if (apks.isEmpty()) {
            if (!localPickerResolveRequested) {
                localPickerResolveRequested = true;
                refreshLaunchPrompt();
            }
            return;
        }
        localPickerResolveRequested = false;
        List<File> files = new ArrayList<>(apks.size());
        List<String> names = new ArrayList<>(apks.size());
        for (LocalApk apk : apks) {
            files.add(apk.file());
            names.add(apk.file().getName());
        }
        localApkCandidates = List.copyOf(files);
        emit(new UpdateUiEvent.ShowLocalApkPicker(names));
    }

    private void showPrompt(@NonNull RemoteUpdate update, boolean autoPrompt) {
        currentUpdate = update;
        if (autoPrompt) {
            engine.markAutoPromptShown(update.version());
        }
        emit(new UpdateUiEvent.ShowUpdatePrompt(
                update.version(), update.description(), update.forceUpdate()));
    }

    private void requestInstallConfirm(@NonNull File apkFile, @Nullable RemoteUpdate update,
                                       boolean ask) {
        confirmTargetFile = apkFile;
        confirmTargetUpdate = update;
        if (!ask) {
            return;
        }
        emit(new UpdateUiEvent.ConfirmInstall(apkFile.getName()));
    }

    private void startDownload(@NonNull DownloadMode mode, @NonNull RemoteUpdate update) {
        lastDownloadedUpdate = update;
        engine.download(mode, update, new DownloadProvider.Callback() {
            @Override
            public void onStarted(@NonNull DownloadMode startedMode, @NonNull String taskId) {
                publishState(state.withDownload(startedMode == DownloadMode.INTERNAL, 0));
                emitNoticeForStarted(startedMode);
            }

            @Override
            public void onStartFailed(@NonNull DownloadMode failedMode,
                                      @NonNull DownloadProvider.StartFailure failure) {
                publishState(state.withDownload(false, 0));
                emit(UpdateUiEvent.Notice.of(startFailureKind(failedMode)));
            }

            @Override
            public void onProgress(@NonNull String taskId, int percent) {
                publishState(state.withDownload(true, percent));
            }

            @Override
            public void onSuccess(@NonNull String taskId, @Nullable File file) {
                publishState(state.withDownload(false, 0));
                if (file == null) {
                    emit(UpdateUiEvent.Notice.of(UpdateUiEvent.Notice.Kind.DOWNLOAD_FAILED));
                    return;
                }
                emit(new UpdateUiEvent.Notice(
                        UpdateUiEvent.Notice.Kind.DOWNLOAD_COMPLETED, file.getName()));
                requestInstallConfirm(file, lastDownloadedUpdate, true);
            }

            @Override
            public void onError(@NonNull String taskId, @NonNull String message) {
                publishState(state.withDownload(false, 0));
                emit(new UpdateUiEvent.Notice(
                        UpdateUiEvent.Notice.Kind.DOWNLOAD_FAILED, message));
            }
        });
    }

    private void install(@NonNull File apkFile, @Nullable RemoteUpdate update) {
        Context context = hostContext;
        if (context == null) {
            YoUpdateLogger.w(TAG, "宿主已解绑，跳过安装");
            return;
        }
        InstallOutcome outcome = engine.install(context, apkFile, update);
        switch (outcome) {
            case SUCCESS -> {
            }
            case VERIFY_FAILED ->
                    emit(UpdateUiEvent.Notice.of(UpdateUiEvent.Notice.Kind.INSTALL_VERIFY_FAILED));
            case INVALID_FILE ->
                    emit(UpdateUiEvent.Notice.of(UpdateUiEvent.Notice.Kind.INSTALL_INVALID_FILE));
            case NO_INSTALLER ->
                    emit(UpdateUiEvent.Notice.of(UpdateUiEvent.Notice.Kind.INSTALL_NO_INSTALLER));
        }
    }

    private void emitNoticeForStarted(@NonNull DownloadMode mode) {
        switch (mode) {
            case SYSTEM -> emit(UpdateUiEvent.Notice.of(
                    UpdateUiEvent.Notice.Kind.DOWNLOAD_STARTED_SYSTEM));
            case BROWSER -> emit(UpdateUiEvent.Notice.of(
                    UpdateUiEvent.Notice.Kind.DOWNLOAD_STARTED_BROWSER));
            case INTERNAL -> YoUpdateLogger.d(TAG, "应用内下载已启动，进度走状态通道");
        }
    }

    private static UpdateUiEvent.Notice.Kind startFailureKind(@NonNull DownloadMode mode) {
        return switch (mode) {
            case INTERNAL -> UpdateUiEvent.Notice.Kind.DOWNLOAD_START_FAILED_INTERNAL;
            case SYSTEM -> UpdateUiEvent.Notice.Kind.DOWNLOAD_START_FAILED_SYSTEM;
            case BROWSER -> UpdateUiEvent.Notice.Kind.DOWNLOAD_START_FAILED_BROWSER;
        };
    }

    private static DownloadMode toInternalMode(@NonNull UpdateDownloadMode mode) {
        return switch (mode) {
            case INTERNAL -> DownloadMode.INTERNAL;
            case SYSTEM -> DownloadMode.SYSTEM;
            case BROWSER -> DownloadMode.BROWSER;
        };
    }

    private void publishState(@NonNull UpdateUiState next) {
        state = next;
        UpdateUiPort target = port;
        if (target == null || detached) {
            return;
        }
        mainThread.post(() -> {
            UpdateUiPort current = port;
            if (current != null && !detached) {
                current.onState(next);
            }
        });
    }

    private void emit(@NonNull UpdateUiEvent event) {
        UpdateUiPort target = port;
        if (target == null || detached) {
            return;
        }
        mainThread.post(() -> {
            UpdateUiPort current = port;
            if (current != null && !detached) {
                current.onEvent(event);
            }
        });
    }

    private void emitEffect(@NonNull UpdateUiEffect.Kind kind) {
        UpdateUiPort target = port;
        if (target == null || detached) {
            return;
        }
        UpdateUiEffect effect = UpdateUiEffect.of(kind);
        mainThread.post(() -> {
            UpdateUiPort current = port;
            if (current != null && !detached) {
                current.onEffect(effect);
            }
        });
    }
}
