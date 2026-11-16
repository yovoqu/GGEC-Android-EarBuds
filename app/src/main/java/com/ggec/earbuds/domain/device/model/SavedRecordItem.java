package com.ggec.earbuds.domain.device.model;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.Objects;

/**
 * 手机本地已存录音文件模型。
 */
public record SavedRecordItem(
        @NonNull String filePath,
        @NonNull String fileName,
        long sizeBytes,
        long lastModifiedTime,
        long durationMs
) {
    public SavedRecordItem {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(fileName, "fileName");
    }

    public static SavedRecordItem of(@NonNull File file, long durationMs) {
        return new SavedRecordItem(
                file.getAbsolutePath(),
                file.getName(),
                file.length(),
                file.lastModified(),
                durationMs
        );
    }
}
