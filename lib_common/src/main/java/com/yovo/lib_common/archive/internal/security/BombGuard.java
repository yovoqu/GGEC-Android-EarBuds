package com.yovo.lib_common.archive.internal.security;

import androidx.annotation.NonNull;

import com.yovo.lib_common.archive.model.ArchiveLimits;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description Zip Bomb 防护：按实际写出字节累计，超过 {@link ArchiveLimits} 则中止。
 */
public final class BombGuard {

    private static final int BUFFER_SIZE = 8 * 1024;

    private final ArchiveLimits limits;
    private int entryCount;
    private long totalBytes;

    public BombGuard(@NonNull ArchiveLimits limits) {
        this.limits = limits;
    }

    public void onEntryStart() throws IOException {
        entryCount++;
        if (entryCount > limits.maxEntries()) {
            throw new IOException("archive entry count exceeds limit: " + limits.maxEntries());
        }
    }

    /**
     * 将 {@code in} 复制到 {@code out}，同时检查单条目与总量上限。
     *
     * @return 实际写出字节数
     */
    public long copyBounded(@NonNull InputStream in, @NonNull OutputStream out) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long entryBytes = 0L;
        int read;
        while ((read = in.read(buffer)) != -1) {
            entryBytes += read;
            totalBytes += read;
            if (entryBytes > limits.maxEntryBytes()) {
                throw new IOException("archive entry size exceeds limit: " + limits.maxEntryBytes());
            }
            if (totalBytes > limits.maxTotalBytes()) {
                throw new IOException("archive total size exceeds limit: " + limits.maxTotalBytes());
            }
            out.write(buffer, 0, read);
        }
        out.flush();
        return entryBytes;
    }

    public int entryCount() {
        return entryCount;
    }

    public long totalBytes() {
        return totalBytes;
    }
}
