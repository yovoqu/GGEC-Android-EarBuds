package com.yovo.lib_common.system.download;

import com.yovo.lib_common.system.download.model.SystemDownloadRequest;
import com.yovo.lib_common.system.download.model.SystemDownloadResult;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.download.internal.android.BrowserLauncher;
import com.yovo.lib_common.system.download.internal.android.DownloadManagerLauncher;

/**
 * 系统下载能力对外门面。
 */
public final class YoSystemDownload {

    private YoSystemDownload() {
    }

    @NonNull
    public static SystemDownloadResult enqueueSystem(@NonNull Context context,
                                                     @NonNull SystemDownloadRequest request) {
        return DownloadManagerLauncher.enqueue(context, request);
    }

    @NonNull
    public static SystemDownloadResult launchBrowser(@NonNull Context context,
                                                     @NonNull SystemDownloadRequest request) {
        return BrowserLauncher.launch(context, request);
    }
}
