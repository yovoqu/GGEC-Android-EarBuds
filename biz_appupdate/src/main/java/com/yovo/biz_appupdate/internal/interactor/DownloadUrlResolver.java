package com.yovo.biz_appupdate.internal.interactor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import java.util.Locale;

/**
 * 下载地址解析：只接受 trim 后的 {@code http(s)} 地址。
 * 服务端地址非法时回退初始化传入的 APP 下载地址；仍非法则得到空串。
 */
public final class DownloadUrlResolver {

    private DownloadUrlResolver() {
    }

    @NonNull
    public static RemoteUpdate resolve(@NonNull RemoteUpdate update,
                                       @NonNull String initDownloadUrl) {
        String remoteUrl = trim(update.downloadUrl());
        if (isHttpUrl(remoteUrl)) {
            return update.withDownloadUrl(remoteUrl);
        }
        String fallback = trim(initDownloadUrl);
        if (isHttpUrl(fallback)) {
            return update.withDownloadUrl(fallback);
        }
        return update.withDownloadUrl("");
    }

    public static boolean hasUsableUrl(@NonNull RemoteUpdate update) {
        return isHttpUrl(trim(update.downloadUrl()));
    }

    static boolean isHttpUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    @NonNull
    static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
