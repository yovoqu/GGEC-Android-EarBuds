package com.yovo.biz_appupdate.internal.model;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

/**
 * APP 更新包下载目录定义。
 *
 * <ul>
 *     <li>{@link Type#APP_PRIVATE}：应用包目录下的相对目录（例如
 *     /Android/data/&lt;pkg&gt;/files/AppUpdate），无需存储权限；</li>
 *     <li>{@link Type#ABSOLUTE}：调用方指定的绝对目录，SDK 会执行存储权限检查。</li>
 *     <li>{@link Type#SAF}：系统文件选择器授权的目录树，免存储权限。</li>
 * </ul>
 */
public final class DownloadDirSpec {

    /** 默认下载目录名（应用包目录下）。 */
    public static final String DEFAULT_DIR_NAME = "AppUpdate";

    public enum Type {
        /** 应用私有目录（应用包目录下），默认 AppUpdate。 */
        APP_PRIVATE,
        /** 调用方指定的绝对目录，需要存储权限。 */
        ABSOLUTE,
        /** SAF 目录树授权（ACTION_OPEN_DOCUMENT_TREE），免存储权限。 */
        SAF
    }

    @NonNull
    private final Type type;
    @Nullable
    private final String relativeName;
    @Nullable
    private final File absoluteDir;
    @Nullable
    private final Uri safTreeUri;

    private DownloadDirSpec(@NonNull Type type,
                            @Nullable String relativeName,
                            @Nullable File absoluteDir,
                            @Nullable Uri safTreeUri) {
        this.type = type;
        this.relativeName = relativeName;
        this.absoluteDir = absoluteDir;
        this.safTreeUri = safTreeUri;
    }

    /**
     * 应用包目录下的相对下载目录。
     *
     * @param relativeName 子目录名，不允许包含路径分隔符或 ".."。
     */
    @NonNull
    public static DownloadDirSpec appPrivate(@NonNull String relativeName) {
        String name = relativeName.trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("Invalid app-private download dir: " + relativeName);
        }
        return new DownloadDirSpec(Type.APP_PRIVATE, name, null, null);
    }

    /**
     * 调用方指定的绝对下载目录。SDK 在使用前会执行存储权限检查。
     */
    @NonNull
    public static DownloadDirSpec absolute(@NonNull File dir) {
        if (!dir.isAbsolute()) {
            throw new IllegalArgumentException("Absolute download dir required: " + dir);
        }
        return new DownloadDirSpec(Type.ABSOLUTE, null, dir, null);
    }

    /** SAF 目录树：实际 treeUri 由 SDK 持久化存储提供（免存储权限）。 */
    @NonNull
    public static DownloadDirSpec saf() {
        return new DownloadDirSpec(Type.SAF, null, null, null);
    }

    /** 显式指定 SAF 目录树（高级用法/测试）。 */
    @NonNull
    public static DownloadDirSpec saf(@NonNull Uri treeUri) {
        return new DownloadDirSpec(Type.SAF, null, null, treeUri);
    }

    @NonNull
    public Type type() {
        return type;
    }

    @Nullable
    public String relativeName() {
        return relativeName;
    }

    @Nullable
    public File absoluteDir() {
        return absoluteDir;
    }

    @Nullable
    public Uri safTreeUri() {
        return safTreeUri;
    }

}
