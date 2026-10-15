package com.yovo.lib_common.system.file.model;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * 文件目标双形态：{@link PathTarget}（路径）或 {@link UriTarget}（URI/SAF）。
 */
public abstract class FileTarget {

    protected FileTarget() {
    }

    @NonNull
    public static FileTarget path(@NonNull PathTarget target) {
        return target;
    }

    @NonNull
    public static FileTarget uri(@NonNull UriTarget target) {
        return target;
    }

    @NonNull
    public static PathTarget internalFiles(@NonNull String subPath) {
        return PathTarget.internalFiles(subPath);
    }

    @NonNull
    public static PathTarget internalCache(@NonNull String subPath) {
        return PathTarget.internalCache(subPath);
    }

    @NonNull
    public static PathTarget externalFiles(@NonNull String subPath) {
        return PathTarget.externalFiles(subPath);
    }

    @NonNull
    public static PathTarget externalCache(@NonNull String subPath) {
        return PathTarget.externalCache(subPath);
    }

    @NonNull
    public static PathTarget custom(@NonNull File root, @NonNull String subPath) {
        return PathTarget.custom(root, subPath);
    }

    @NonNull
    public static PathTarget absolute(@NonNull String absolutePath) {
        return PathTarget.absolute(absolutePath);
    }

    public final boolean isPath() {
        return this instanceof PathTarget;
    }

    public final boolean isUri() {
        return this instanceof UriTarget;
    }

    @NonNull
    public final PathTarget asPath() {
        if (!(this instanceof PathTarget)) {
            throw new IllegalStateException("Not a path target: " + this);
        }
        return (PathTarget) this;
    }

    @NonNull
    public final UriTarget asUri() {
        if (!(this instanceof UriTarget)) {
            throw new IllegalStateException("Not a uri target: " + this);
        }
        return (UriTarget) this;
    }
}
