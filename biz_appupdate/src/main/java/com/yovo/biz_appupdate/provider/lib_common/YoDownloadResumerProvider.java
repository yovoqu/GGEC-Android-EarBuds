package com.yovo.biz_appupdate.provider.lib_common;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.internal.contract.PendingDownloadResumer;
import com.yovo.lib_common.download.YoDownload;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * 启动时恢复下载目录内被中断的 APK 任务（SQLite 断点 + 同名续传）。
 */
public final class YoDownloadResumerProvider implements PendingDownloadResumer {

    private final Context appContext;

    public YoDownloadResumerProvider(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    public void resume(@NonNull File rootDir, @NonNull String apkFilePrefix) {
        List<YoDownload.Task> pending = YoDownload.queryAll();
        if (pending.isEmpty()) {
            return;
        }
        String prefix = apkFilePrefix.trim().toLowerCase(Locale.US);
        for (YoDownload.Task task : pending) {
            if (isTerminal(task.state())) {
                continue;
            }
            File target = task.destinationFile();
            if (target == null) {
                continue;
            }
            String name = target.getName().toLowerCase(Locale.US);
            if (!target.getAbsolutePath().startsWith(rootDir.getAbsolutePath())) {
                continue;
            }
            if (name.endsWith(".apk") && prefix.length() > 0 && name.startsWith(prefix)) {
                YoDownload.resume(task.id(), null);
            }
        }
    }

    private static boolean isTerminal(@NonNull YoDownload.State state) {
        return state == YoDownload.State.COMPLETED
                || state == YoDownload.State.CANCELED
                || state == YoDownload.State.REMOVED;
    }
}
