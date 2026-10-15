package com.yovo.lib_common.system.download.model;

import android.text.TextUtils;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * @author tingfeng.yang
 * @date 2026-06-24
 * @description 系统下载能力请求。用于浏览器跳转与 Android {@code DownloadManager}
 * 等系统能力适配层，不承载应用内下载队列、状态或断点信息。
 */
public final class SystemDownloadRequest {

    private final String url;
    private final String fileName;
    private final String title;
    private final String description;
    private final String mimeType;
    private final boolean notificationVisible;

    private SystemDownloadRequest(@NonNull Builder builder) {
        this.url = builder.url;
        this.fileName = builder.fileName;
        this.title = builder.title;
        this.description = builder.description;
        this.mimeType = builder.mimeType;
        this.notificationVisible = builder.notificationVisible;
    }

    @NonNull
    public static SystemDownloadRequest of(@Nullable String url) {
        return builder(url).build();
    }

    @NonNull
    public static Builder builder(@Nullable String url) {
        return new Builder(url);
    }

    @Nullable
    public String url() {
        return url;
    }

    @Nullable
    public String fileName() {
        return fileName;
    }

    @Nullable
    public String title() {
        return title;
    }

    @Nullable
    public String description() {
        return description;
    }

    @Nullable
    public String mimeType() {
        return mimeType;
    }

    public boolean notificationVisible() {
        return notificationVisible;
    }

    public boolean hasValidUrl() {
        return !TextUtils.isEmpty(url) && URLUtil.isNetworkUrl(url);
    }

    @NonNull
    public String resolvedFileName() {
        if (!TextUtils.isEmpty(fileName)) {
            return fileName;
        }
        String guessed = URLUtil.guessFileName(url, null, mimeType);
        return TextUtils.isEmpty(guessed)
                ? "download_" + System.currentTimeMillis()
                : guessed;
    }

    public static final class Builder {

        private final String url;
        private String fileName;
        private String title;
        private String description;
        private String mimeType;
        private boolean notificationVisible = true;

        private Builder(@Nullable String url) {
            this.url = url;
        }

        @NonNull
        public Builder fileName(@Nullable String fileName) {
            this.fileName = fileName;
            return this;
        }

        @NonNull
        public Builder title(@Nullable String title) {
            this.title = title;
            return this;
        }

        @NonNull
        public Builder description(@Nullable String description) {
            this.description = description;
            return this;
        }

        @NonNull
        public Builder mimeType(@Nullable String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        @NonNull
        public Builder notificationVisible(boolean notificationVisible) {
            this.notificationVisible = notificationVisible;
            return this;
        }

        @NonNull
        public SystemDownloadRequest build() {
            return new SystemDownloadRequest(this);
        }
    }
}
