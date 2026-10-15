package com.yovo.biz_appupdate.provider.lib_common;

import android.content.Context;
import android.text.TextUtils;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.DownloadDirAccessPort;
import com.yovo.biz_appupdate.internal.contract.DownloadProvider;
import com.yovo.biz_appupdate.internal.infrastructure.local.ApkStorage;
import com.yovo.biz_appupdate.internal.model.DownloadMode;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.lib_common.download.YoDownload;
import com.yovo.lib_common.system.download.YoSystemDownload;
import com.yovo.lib_common.system.download.model.SystemDownloadRequest;
import com.yovo.lib_common.system.download.model.SystemDownloadResult;

import java.io.File;

public final class YoDownloadProvider implements DownloadProvider {

    private static final String MIME = "application/vnd.android.package-archive";

    private final Context appContext;
    private final ApkStorage apkStorage;
    private final DownloadDirAccessPort downloadDirAccess;

    public YoDownloadProvider(@NonNull Context context,
                              @NonNull ApkStorage apkStorage,
                              @NonNull DownloadDirAccessPort downloadDirAccess) {
        appContext = context.getApplicationContext();
        this.apkStorage = apkStorage;
        this.downloadDirAccess = downloadDirAccess;
    }

    @Override
    public void download(@NonNull DownloadMode mode,
                         @NonNull RemoteUpdate update,
                         @NonNull Callback callback) {
        switch (mode) {
            case INTERNAL:
                downloadInternal(update, callback);
                break;
            case SYSTEM:
                downloadSystem(update, callback);
                break;
            case BROWSER:
                downloadBrowser(update, callback);
                break;
            default:
                callback.onStartFailed(mode, StartFailure.ERROR);
                break;
        }
    }

    private void downloadInternal(@NonNull RemoteUpdate update,
                                  @NonNull Callback callback) {
        String url = update.downloadUrl() == null ? "" : update.downloadUrl().trim();
        if (TextUtils.isEmpty(url) || !URLUtil.isNetworkUrl(url)) {
            callback.onStartFailed(DownloadMode.INTERNAL, StartFailure.INVALID_URL);
            return;
        }
        if (!downloadDirAccess.isAccessible()) {
            callback.onStartFailed(DownloadMode.INTERNAL, StartFailure.NO_PERMISSION);
            return;
        }
        if (!apkStorage.ensureReady()) {
            callback.onStartFailed(DownloadMode.INTERNAL, StartFailure.ERROR);
            return;
        }
        YoDownload.Request request = YoDownload.Request.builder(url)
                .destinationDir(apkStorage.rootDir())
                .fileName(apkStorage.fileName(update.version(), update.versionCode()))
                .mimeType(MIME)
                .blockCount(3)
                .replaceExisting(true)
                .build();
        YoDownload.enqueue(request, new YoDownload.Listener() {
            @Override
            public void onQueued(@NonNull YoDownload.Task task) {
                YoDownload.notification().showStarted(
                        appContext, task.id(), task.destinationFile().getName());
                callback.onStarted(DownloadMode.INTERNAL, task.id());
            }

            @Override
            public void onProgress(@NonNull YoDownload.Task task,
                                   @NonNull YoDownload.Progress progress) {
                YoDownload.notification().showProgress(
                        appContext, task.id(), task.destinationFile().getName(),
                        progress.percent());
                callback.onProgress(task.id(), progress.percent());
            }

            @Override
            public void onSuccess(@NonNull YoDownload.Task task,
                                  @NonNull YoDownload.Result result) {
                YoDownload.notification().cancel(appContext, task.id());
                File file = result.file();
                if (file != null && apkStorage.isSaf()) {
                    if (!apkStorage.copyToSaf(file)) {
                        callback.onError(task.id(), "SAF download dir write failed");
                        return;
                    }
                }
                callback.onSuccess(task.id(), file);
            }

            @Override
            public void onError(@NonNull YoDownload.Task task,
                                @NonNull YoDownload.Result result) {
                YoDownload.notification().cancel(appContext, task.id());
                callback.onError(task.id(), resolveMessage(result));
            }

            @Override
            public void onCanceled(@NonNull YoDownload.Task task,
                                   @NonNull YoDownload.Result result) {
                YoDownload.notification().cancel(appContext, task.id());
                callback.onError(task.id(), resolveMessage(result));
            }
        });
    }

    private void downloadSystem(@NonNull RemoteUpdate update,
                                @NonNull Callback callback) {
        String fileName = apkStorage.fileName(update.version(), update.versionCode());
        SystemDownloadResult result = YoSystemDownload.enqueueSystem(appContext,
                SystemDownloadRequest.builder(update.downloadUrl())
                        .fileName(fileName)
                        .title(fileName)
                        .description(update.description())
                        .mimeType(MIME)
                        .notificationVisible(true)
                        .build());
        dispatchSystem(DownloadMode.SYSTEM, result, callback);
    }

    private void downloadBrowser(@NonNull RemoteUpdate update,
                                 @NonNull Callback callback) {
        SystemDownloadResult result = YoSystemDownload.launchBrowser(appContext,
                SystemDownloadRequest.of(update.downloadUrl()));
        dispatchSystem(DownloadMode.BROWSER, result, callback);
    }

    private void dispatchSystem(@NonNull DownloadMode mode,
                                @NonNull SystemDownloadResult result,
                                @NonNull Callback callback) {
        if (result.isSuccess()) {
            callback.onStarted(mode, String.valueOf(result.systemDownloadId()));
            return;
        }
        if (result.status() == SystemDownloadResult.Status.INVALID_URL) {
            callback.onStartFailed(mode, StartFailure.INVALID_URL);
        } else if (result.status() == SystemDownloadResult.Status.NO_HANDLER) {
            callback.onStartFailed(mode, StartFailure.NO_HANDLER);
        } else {
            callback.onStartFailed(mode, StartFailure.ERROR);
        }
    }

    @NonNull
    private static String resolveMessage(@NonNull YoDownload.Result result) {
        Throwable throwable = result.throwable();
        if (throwable != null && throwable.getMessage() != null) {
            return throwable.getMessage();
        }
        return "download failed";
    }
}
