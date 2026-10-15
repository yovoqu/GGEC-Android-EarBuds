package com.yovo.lib_common.system.file.internal.resolve;

import androidx.annotation.NonNull;

import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;
import com.yovo.lib_common.system.file.model.FileScope;
import com.yovo.lib_common.system.file.model.PathTarget;

import java.io.File;
import java.io.IOException;

/**
 * 路径解析与逃逸检测：规范化后必须仍位于根目录下（ABSOLUTE 除外）。
 */
public final class PathResolver {

    private PathResolver() {
    }

    /**
     * 解析 PathTarget 为规范化 File。成功不代表可写。
     */
    @NonNull
    public static FileResult<File> resolve(@NonNull PathTarget target,
                                           @NonNull PathRootProvider provider) {
        if (target.scope() == FileScope.ABSOLUTE) {
            String path = target.absolutePath();
            if (path == null || path.trim().isEmpty()) {
                return FileResult.failure(
                        FileFailureCode.INVALID_TARGET, "empty absolute path");
            }
            File file = new File(path);
            try {
                return FileResult.ok(file.getCanonicalFile());
            } catch (IOException e) {
                return FileResult.failure(
                        FileFailureCode.IO_ERROR, "cannot canonicalize: " + path, e);
            }
        }

        File root = provider.root(target);
        if (root == null) {
            return FileResult.failure(
                    FileFailureCode.UNSUPPORTED,
                    "root unavailable for " + target);
        }

        String subPath = target.subPath();
        File resolved = subPath.isEmpty() ? root : new File(root, subPath);
        try {
            File canonical = resolved.getCanonicalFile();
            File canonicalRoot = root.getCanonicalFile();
            String rootPath = canonicalRoot.getPath();
            String targetPath = canonical.getPath();
            if (!targetPath.equals(rootPath)
                    && !targetPath.startsWith(rootPath + File.separator)) {
                return FileResult.failure(
                        FileFailureCode.TARGET_ESCAPE,
                        "subPath escapes root: " + subPath);
            }
            return FileResult.ok(canonical);
        } catch (IOException e) {
            return FileResult.failure(
                    FileFailureCode.IO_ERROR,
                    "cannot canonicalize " + resolved,
                    e);
        }
    }
}
