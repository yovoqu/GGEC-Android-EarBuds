package com.yovo.lib_common.system.download.internal.android;

import com.yovo.lib_common.system.download.model.SystemDownloadRequest;
import com.yovo.lib_common.system.download.model.SystemDownloadResult;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.yovo.lib_common.log.YoLog;

/**
 * @author tingfeng.yang
 * @date 2026-06-24
 * @description 浏览器跳转下载能力。只封装 Android 系统 {@link Intent#ACTION_VIEW}
 * 能力，不参与应用内下载队列、进度或状态管理。
 */
public final class BrowserLauncher {

    private static final String TAG = "BrowserLauncher";

    private BrowserLauncher() {
    }

    @NonNull
    public static SystemDownloadResult launch(@NonNull Context context,
                                              @NonNull SystemDownloadRequest request) {
        if (!request.hasValidUrl()) {
            YoLog.w(TAG, "浏览器启动已忽略：URL 无效");
            return SystemDownloadResult.invalidUrl();
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(request.url()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.getApplicationContext().startActivity(intent);
            YoLog.d(TAG, "浏览器已启动: " + safeHost(request.url()));
            return SystemDownloadResult.success();
        } catch (ActivityNotFoundException e) {
            YoLog.w(TAG, "浏览器启动失败：没有可处理 " + safeHost(request.url()) + " 的应用", e);
            return SystemDownloadResult.noHandler();
        } catch (Exception e) {
            YoLog.w(TAG, "浏览器启动失败: " + safeHost(request.url()), e);
            return SystemDownloadResult.error();
        }
    }

    @NonNull
    private static String safeHost(@NonNull String url) {
        String host = Uri.parse(url).getHost();
        return host != null ? host : "unknown-host";
    }
}
