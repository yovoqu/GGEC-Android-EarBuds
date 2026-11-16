package com.yovo.biz_ggec_hs.internal.record;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 录音存储默认路径与文件名生成器（与 Demo App 架构对齐）。
 *
 * <p>默认根路径：{@code Android/data/<package>/Record/}，
 * 随录随传子目录：{@code stream/}，
 * 本地同步子目录：{@code transfer/}。</p>
 */
public final class HsRecordPathResolver {

    private static final String ROOT_NAME = "Record";
    private static final String DIR_STREAM = "stream";
    private static final String DIR_TRANSFER = "transfer";

    private final Context context;

    public HsRecordPathResolver(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    public File getRootDir() {
        File externalFiles = context.getExternalFilesDir(null);
        File packageDir = externalFiles != null ? externalFiles.getParentFile() : null;
        File root = packageDir != null ? new File(packageDir, ROOT_NAME) : new File(context.getFilesDir(), ROOT_NAME);
        if (!root.exists()) {
            root.mkdirs();
        }
        return root;
    }

    @NonNull
    public File getStreamDir() {
        File dir = new File(getRootDir(), DIR_STREAM);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    public File getTransferDir() {
        File dir = new File(getRootDir(), DIR_TRANSFER);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    @NonNull
    public File generateDefaultStreamFile() {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(getStreamDir(), "live_" + timestamp + ".pcm");
    }

    @NonNull
    public File generateDefaultTransferFile(@NonNull String fileId) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return new File(getTransferDir(), "sync_" + fileId + "_" + timestamp + ".pcm");
    }
}
