package com.yovo.biz_ggec_wy.internal.record;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ggec_audio.record.RecordFileName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * 录音存储默认路径与文件名生成器。
 *
 * <p>文件名统一按 {@code 产品型号_录音类型[_来源标识]_时间戳.扩展名} 生成，
 * 格式化规则集中在 {@link RecordFileName}；产品型号通过 {@code deviceCodeSupplier}
 * 取当前连接型号（组合根绑定 {@code session.peer().modelId()}）。</p>
 */
public class WyRecordPathResolver {

    private static final String ROOT_NAME = "Record";
    private static final String DIR_STREAM = "stream";
    private static final String PCM_EXTENSION = "pcm";

    @Nullable
    private final Context context;
    @Nullable
    private final Supplier<String> deviceCodeSupplier;

    public WyRecordPathResolver(@Nullable Context context) {
        this(context, null);
    }

    public WyRecordPathResolver(@Nullable Context context,
                                @Nullable Supplier<String> deviceCodeSupplier) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.deviceCodeSupplier = deviceCodeSupplier;
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
        String fileName = RecordFileName.build(currentDeviceCode(), RecordFileName.TYPE_STREAM,
                System.currentTimeMillis(), PCM_EXTENSION);
        return new File(getStreamDir(), fileName);
    }

    @NonNull
    public List<File> listLocalSavedFiles() {
        File root = getRootDir();
        List<File> result = new ArrayList<>();
        collectAudioFiles(root, result);
        result.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    public boolean deleteLocalSavedFile(@NonNull String filePath) {
        File file = new File(filePath);
        return file.exists() && file.delete();
    }

    @NonNull
    private String currentDeviceCode() {
        if (deviceCodeSupplier == null) {
            return "";
        }
        String code = deviceCodeSupplier.get();
        return code == null ? "" : code;
    }

    private void collectAudioFiles(@Nullable File dir, @NonNull List<File> result) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                collectAudioFiles(file, result);
            } else {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".pcm") || name.endsWith(".wav") || name.endsWith(".mp3")) {
                    result.add(file);
                }
            }
        }
    }
}
