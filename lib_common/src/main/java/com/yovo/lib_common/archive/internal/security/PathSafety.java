package com.yovo.lib_common.archive.internal.security;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_common.archive.model.UnpackLayout;

import java.io.File;
import java.io.IOException;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description Zip Slip 防护：在解压循环内将条目名解析为安全目标文件。
 */
public final class PathSafety {

    private PathSafety() {
    }

    /**
     * @return 安全目标文件；不安全或无效条目返回 {@code null}。
     */
    @Nullable
    public static File resolveTarget(@NonNull File destinationDir,
                                     @Nullable String entryName,
                                     @NonNull UnpackLayout layout) throws IOException {
        if (entryName == null || entryName.isEmpty()) {
            return null;
        }
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.contains(":")) {
            return null;
        }

        String relative;
        if (layout == UnpackLayout.FLATTEN) {
            int slash = normalized.lastIndexOf('/');
            relative = slash >= 0 ? normalized.substring(slash + 1) : normalized;
            if (relative.isEmpty() || ".".equals(relative) || "..".equals(relative)) {
                return null;
            }
        } else {
            if (normalized.contains("../") || normalized.startsWith("..")
                    || normalized.endsWith("/..") || "/..".equals(normalized)) {
                return null;
            }
            relative = normalized;
            while (relative.startsWith("./")) {
                relative = relative.substring(2);
            }
            if (relative.isEmpty()) {
                return null;
            }
        }

        File destCanonical = destinationDir.getCanonicalFile();
        if (!destCanonical.exists() && !destCanonical.mkdirs()) {
            throw new IOException("cannot create destination: " + destCanonical);
        }
        File target = new File(destCanonical, relative);
        File targetCanonical = target.getCanonicalFile();
        String destPath = destCanonical.getPath();
        String targetPath = targetCanonical.getPath();
        if (!targetPath.equals(destPath)
                && !targetPath.startsWith(destPath + File.separator)) {
            return null;
        }
        return targetCanonical;
    }
}
