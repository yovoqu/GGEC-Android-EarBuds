package com.yovo.lib_common.system.share;

import com.yovo.lib_common.system.share.model.ArchiveTimestampStyle;
import com.yovo.lib_common.system.share.model.ShareResult;

import com.yovo.lib_common.system.share.internal.core.ShareCore;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * 分享域对外门面。
 */
public final class YoShare {

    private final ShareCore delegate;

    private YoShare(@NonNull ShareCore delegate) {
        this.delegate = delegate;
    }

    @NonNull
    public static YoShare from(@NonNull Context context) {
        return new YoShare(ShareCore.from(context));
    }

    @NonNull
    public YoShare file(@Nullable File file) {
        delegate.file(file);
        return this;
    }

    @NonNull
    public YoShare files(@Nullable List<File> source) {
        delegate.files(source);
        return this;
    }

    @NonNull
    public YoShare chooserTitle(@Nullable String title) {
        delegate.chooserTitle(title);
        return this;
    }

    @NonNull
    public YoShare archiveName(@Nullable String name) {
        delegate.archiveName(name);
        return this;
    }

    @NonNull
    public YoShare archiveTimestamp(@Nullable ArchiveTimestampStyle style) {
        delegate.archiveTimestamp(style);
        return this;
    }

    @NonNull
    public YoShare archiveNonMediaMultiple(boolean enabled) {
        delegate.archiveNonMediaMultiple(enabled);
        return this;
    }

    /**
     * 指定 FileProvider authority；默认使用 {@code packageName + ".fileprovider"}。
     */
    @NonNull
    public YoShare authority(@Nullable String authority) {
        delegate.authority(authority);
        return this;
    }

    @NonNull
    public ShareResult share() {
        return delegate.share();
    }

    /**
     * 异步分享：打包在调用方执行器上完成，Chooser 与回调固定在主线程。
     */
    public void shareAsync(@NonNull Executor executor, @NonNull ShareCallback callback) {
        delegate.shareAsync(executor, callback);
    }

    public static void clearArchives(@NonNull Context context) {
        ShareCore.clearArchives(context);
    }

    public interface ShareCallback {
        void onResult(@NonNull ShareResult result);
    }
}
