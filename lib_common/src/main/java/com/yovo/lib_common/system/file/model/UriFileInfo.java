package com.yovo.lib_common.system.file.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * ContentResolver 可获取的 URI 文件元数据。全部字段均来自外部 Provider，不可信。
 */
public final class UriFileInfo {

    private final String displayName;

    @Nullable
    private final String mimeType;

    private final long sizeHint;

    private final boolean supportsWrite;

    public UriFileInfo(@NonNull String displayName,
                       @Nullable String mimeType,
                       long sizeHint) {
        this(displayName, mimeType, sizeHint, false);
    }

    public UriFileInfo(@NonNull String displayName,
                       @Nullable String mimeType,
                       long sizeHint,
       boolean supportsWrite) {
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.sizeHint = sizeHint;
        this.supportsWrite = supportsWrite;
    }

    @NonNull
    public String displayName() {
        return displayName;
    }

    @Nullable
    public String mimeType() {
        return mimeType;
    }

    /**
     * Provider 声明的大小；未知时为 -1。复制限制必须按实际读取字节执行。
     */
    public long sizeHint() {
        return sizeHint;
    }

    /** 是否为写访问：COLUMN_FLAGS 含 FLAG_SUPPORTS_WRITE。 */
    public boolean supportsWrite() {
        return supportsWrite;
    }
}
