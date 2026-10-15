package com.yovo.lib_common.system.file.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Objects;

/**
 * 路径类目标：范围 + 根种类 + 相对子路径（或绝对路径）。
 *
 * <p>约束：APP_* 禁止传绝对路径穿越根；ABSOLUTE 必须使用 {@link #absolutePath()}，
 * 禁止再用 subPath 拼接。</p>
 */
public final class PathTarget extends FileTarget {

    @NonNull
    private final FileScope scope;

    @Nullable
    private final RootKind rootKind;

    @Nullable
    private final String subPath;

    @Nullable
    private final String absolutePath;

    /** 仅 {@link RootKind#CUSTOM_SUBDIR} 使用。 */
    @Nullable
    private final String customRootPath;

    private PathTarget(@NonNull FileScope scope,
                       @Nullable RootKind rootKind,
                       @Nullable String subPath,
                       @Nullable String absolutePath,
                       @Nullable String customRootPath) {
        super();
        this.scope = scope;
        this.rootKind = rootKind;
        this.subPath = normalizeSubPath(subPath);
        this.absolutePath = absolutePath;
        this.customRootPath = customRootPath;
        validate();
    }

    @NonNull
    private static String normalizeSubPath(@Nullable String subPath) {
        if (subPath == null || subPath.isEmpty()) {
            return "";
        }
        String value = subPath.replace('\\', '/');
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private void validate() {
        if (scope == FileScope.ABSOLUTE) {
            if (absolutePath == null || absolutePath.trim().isEmpty()) {
                throw new IllegalArgumentException("ABSOLUTE target requires absolutePath");
            }
            if (rootKind != null || !subPath.isEmpty()) {
                throw new IllegalArgumentException(
                        "ABSOLUTE target must not combine rootKind/subPath");
            }
            return;
        }
        if (rootKind == RootKind.CUSTOM_SUBDIR) {
            if (customRootPath == null || customRootPath.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "CUSTOM_SUBDIR requires custom root path");
            }
        } else if (rootKind == null) {
            throw new IllegalArgumentException("APP_* target requires rootKind");
        }
    }

    @NonNull
    public static PathTarget internalFiles(@NonNull String subPath) {
        return new PathTarget(FileScope.APP_INTERNAL, RootKind.FILES, subPath, null, null);
    }

    @NonNull
    public static PathTarget internalCache(@NonNull String subPath) {
        return new PathTarget(FileScope.APP_INTERNAL, RootKind.CACHE, subPath, null, null);
    }

    @NonNull
    public static PathTarget externalFiles(@NonNull String subPath) {
        return new PathTarget(FileScope.APP_EXTERNAL, RootKind.EXT_FILES, subPath, null, null);
    }

    @NonNull
    public static PathTarget externalCache(@NonNull String subPath) {
        return new PathTarget(FileScope.APP_EXTERNAL, RootKind.EXT_CACHE, subPath, null, null);
    }

    /**
     * 宿主已持有具体根目录（如业务自行创建的外部目录）时使用；
     * 视为应用内部能力，不做存储权限门禁。
     */
    @NonNull
    public static PathTarget custom(@NonNull File root, @NonNull String subPath) {
        return new PathTarget(
                FileScope.APP_INTERNAL,
                RootKind.CUSTOM_SUBDIR,
                subPath,
                null,
                root.getAbsolutePath());
    }

    @NonNull
    public static PathTarget absolute(@NonNull String absolutePath) {
        return new PathTarget(FileScope.ABSOLUTE, null, null, absolutePath, null);
    }

    @NonNull
    public FileScope scope() {
        return scope;
    }

    @Nullable
    public RootKind rootKind() {
        return rootKind;
    }

    /** APP_* 下相对根目录的子路径；ABSOLUTE 下为空。 */
    @NonNull
    public String subPath() {
        return subPath == null ? "" : subPath;
    }

    @Nullable
    public String absolutePath() {
        return absolutePath;
    }

    @Nullable
    public String customRootPath() {
        return customRootPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PathTarget)) {
            return false;
        }
        PathTarget that = (PathTarget) o;
        return scope == that.scope
                && rootKind == that.rootKind
                && subPath.equals(that.subPath)
                && Objects.equals(absolutePath, that.absolutePath)
                && Objects.equals(customRootPath, that.customRootPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scope, rootKind, subPath, absolutePath, customRootPath);
    }

    @NonNull
    @Override
    public String toString() {
        if (scope == FileScope.ABSOLUTE) {
            return "PathTarget{ABSOLUTE:" + absolutePath + "}";
        }
        return "PathTarget{" + scope + "/" + rootKind + ":" + subPath + "}";
    }
}
