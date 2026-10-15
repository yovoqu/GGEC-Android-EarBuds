package com.yovo.lib_common.system.download.internal.android;

import com.yovo.lib_common.system.download.model.SystemDownloadRequest;
import com.yovo.lib_common.system.download.model.SystemDownloadResult;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.yovo.lib_common.internal.util.FileNames;
import com.yovo.lib_common.log.YoLog;

/**
 * @author tingfeng.yang
 * @date 2026-06-24
 * @description Android 系统 {@link DownloadManager} 下载能力适配。
 * 系统服务负责后台下载、重试和通知栏展示，本类只负责构造并提交请求。
 */
public final class DownloadManagerLauncher {

    private static final String TAG = "DownloadManagerLauncher";

    private DownloadManagerLauncher() {
    }

    @NonNull
    public static SystemDownloadResult enqueue(@NonNull Context context,
                                               @NonNull SystemDownloadRequest request) {
        if (!request.hasValidUrl()) {
            YoLog.w(TAG, "系统下载已忽略：URL 无效");
            return SystemDownloadResult.invalidUrl();
        }
        DownloadManager manager = (DownloadManager) context.getApplicationContext()
                .getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            YoLog.w(TAG, "系统下载入队失败：DownloadManager 服务不可用");
            return SystemDownloadResult.noHandler();
        }
        try {
            DownloadManager.Request systemRequest =
                    new DownloadManager.Request(Uri.parse(request.url()));
            String fileName = FileNames.sanitize(request.resolvedFileName(), "download");
            systemRequest.setDestinationInExternalFilesDir(
                    context.getApplicationContext(), Environment.DIRECTORY_DOWNLOADS, fileName);
            systemRequest.setTitle(!TextUtils.isEmpty(request.title()) ? request.title() : fileName);
            if (!TextUtils.isEmpty(request.description())) {
                systemRequest.setDescription(request.description());
            }
            if (!TextUtils.isEmpty(request.mimeType())) {
                systemRequest.setMimeType(request.mimeType());
            }
            systemRequest.setNotificationVisibility(request.notificationVisible()
                    ? DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    : DownloadManager.Request.VISIBILITY_HIDDEN);
            long id = manager.enqueue(systemRequest);
            YoLog.i(TAG, "系统下载已入队：id=" + id + "，文件=" + fileName);
            return SystemDownloadResult.success(id);
        } catch (Exception e) {
            YoLog.w(TAG, "系统下载入队失败", e);
            return SystemDownloadResult.error();
        }
    }
}
