package com.yovo.biz_ggec_hs.internal.record;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ggec_audio.record.RecordFileName;

import java.io.File;
import java.util.function.Supplier;

/**
 * 录音存储默认路径与文件名生成器（与 Demo App 架构对齐）。
 *
 * <p>默认根路径：{@code Android/data/<package>/Record/}，
 * 随录随传子目录：{@code stream/}，
 * 本地同步子目录：{@code transfer/}。</p>
 *
 * <p>文件名统一按 {@code 产品型号_录音类型[_来源标识]_时间戳.扩展名} 生成，
 * 格式化规则集中在 {@link RecordFileName}；产品型号通过 {@code deviceCodeSupplier}
 * 取当前连接型号（组合根绑定 {@code session.peer().modelId()}）。</p>
 */
public final class HsRecordPathResolver {

    private static final String ROOT_NAME = "Record";
    private static final String DIR_STREAM = "stream";
    private static final String DIR_TRANSFER = "transfer";
    private static final String PCM_EXTENSION = "pcm";

    private final Context context;
    @Nullable
    private final Supplier<String> deviceCodeSupplier;

    public HsRecordPathResolver(@NonNull Context context) {
        this(context, null);
    }

    public HsRecordPathResolver(@NonNull Context context,
                                @Nullable Supplier<String> deviceCodeSupplier) {
        this.context = context.getApplicationContext();
        this.deviceCodeSupplier = deviceCodeSupplier;
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
        String fileName = RecordFileName.build(currentDeviceCode(), RecordFileName.TYPE_STREAM,
                System.currentTimeMillis(), PCM_EXTENSION);
        return new File(getStreamDir(), fileName);
    }

    @NonNull
    public File generateDefaultTransferFile(@NonNull String fileId) {
        String fileName = RecordFileName.build(currentDeviceCode(), RecordFileName.TYPE_SYNC, fileId,
                System.currentTimeMillis(), PCM_EXTENSION);
        return new File(getTransferDir(), fileName);
    }

    @NonNull
    private String currentDeviceCode() {
        if (deviceCodeSupplier == null) {
            return "";
        }
        String code = deviceCodeSupplier.get();
        return code == null ? "" : code;
    }
}
