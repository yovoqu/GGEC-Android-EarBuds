package com.yovo.lib_common.download.internal.core;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.download.YoDownload;

/**
 * 下载域的装配持有者：唯一保存「当前安装的 {@link DownloadCore} 实例」这一装配状态。
 *
 * <p>状态属于本对象实例，由门面 {@link YoDownload} 持有唯一一份；
 * {@link DownloadCore} 自身不再有静态可写字段。</p>
 */
public final class DownloadInstallation {

    private final Object lock = new Object();

    @Nullable
    private DownloadCore core;

    public DownloadInstallation() {
    }

    /** 替换当前引擎：旧引擎先关停，再装入新实例。 */
    public void install(@NonNull Context context, @NonNull YoDownload.Config config) {
        synchronized (lock) {
            DownloadCore previous = core;
            core = null;
            if (previous != null) {
                previous.shutdown();
            }
            core = DownloadCore.create(context, config);
        }
    }

    /**
     * 取当前引擎，未安装时抛出明确异常。
     */
    @NonNull
    public DownloadCore require() {
        DownloadCore local;
        synchronized (lock) {
            local = core;
        }
        if (local == null) {
            throw new IllegalStateException("YoDownload is not initialized. Call YoDownload.init() first.");
        }
        return local;
    }

    /** 关停并摘除当前引擎；未安装时为空操作。 */
    public void shutdown() {
        DownloadCore previous;
        synchronized (lock) {
            previous = core;
            core = null;
        }
        if (previous != null) {
            previous.shutdown();
        }
    }
}
