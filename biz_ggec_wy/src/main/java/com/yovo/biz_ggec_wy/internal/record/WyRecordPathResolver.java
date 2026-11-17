package com.yovo.biz_ggec_wy.internal.record;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 录音存储默认路径与文件名生成器。
 */
public class WyRecordPathResolver {

    private static final String ROOT_NAME = "Record";
    private static final String DIR_STREAM = "stream";

    private final Context context;

    public WyRecordPathResolver(@Nullable Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    @NonNull
    public File getRootDir() {
        File externalFiles = context != null ? context.getExternalFilesDir(null) : null;
        File packageDir = externalFiles != null ? externalFiles.getParentFile() : null;
        File root = packageDir != null
                ? new File(packageDir, ROOT_NAME)
                : (context != null ? new File(context.getFilesDir(), ROOT_NAME) : new File(System.getProperty("java.io.tmpdir", "."), ROOT_NAME));
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
    public File generateDefaultStreamFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "WY_stream_" + timeStamp + ".pcm";
        return new File(getStreamDir(), fileName);
    }
}
