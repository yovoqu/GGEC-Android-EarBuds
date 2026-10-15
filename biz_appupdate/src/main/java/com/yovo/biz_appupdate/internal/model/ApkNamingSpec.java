package com.yovo.biz_appupdate.internal.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * APK 命名规范：{@code {prefix}_{versionName}_{versionCode}.apk}。
 *
 * <p>SDK 默认前缀为 {@code App}；APP 初始化时可通过 {@code apkFilePrefix} 覆盖。
 * 不兼容 {@code {prefix}_{versionName}.apk} 等旧命名。</p>
 */
public final class ApkNamingSpec {

    public static final String DEFAULT_PREFIX = "App";

    private static final Pattern SAFE_NAME = Pattern.compile("[\\\\/:*?\"<>|\\s]+");
    private static final String VERSION_CODE_PART = "[0-9]+";

    @NonNull
    private final String prefix;
    @NonNull
    private final Pattern strictPattern;

    public ApkNamingSpec(@NonNull String prefix) {
        String trimmed = prefix.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("apkFilePrefix must not be empty");
        }
        this.prefix = trimmed;
        this.strictPattern = Pattern.compile(
                Pattern.quote(trimmed) + "_(.+)_(" + VERSION_CODE_PART + ")\\.apk",
                Pattern.CASE_INSENSITIVE);
    }

    @NonNull
    public String prefix() {
        return prefix;
    }

    /**
     * 生成严格命名的新文件名。
     */
    @NonNull
    public String fileName(@NonNull String versionName, int versionCode) {
        String safeVersion = SAFE_NAME.matcher(versionName.trim()).replaceAll("_");
        if (safeVersion.isEmpty()) {
            safeVersion = "unknown";
        }
        return String.format(Locale.US, "%s_%s_%d.apk", prefix, safeVersion, Math.max(0, versionCode));
    }

    /**
     * 是否为当前规范下的严格安装包文件名。
     */
    public boolean matches(@NonNull String fileName) {
        return strictPattern.matcher(fileName).matches();
    }

    /**
     * 是否属于本 SDK 前缀管理的文件（含旧命名/无效命名），用于清理。
     */
    public boolean hasOwnedPrefix(@NonNull String fileName) {
        String lowerName = fileName.toLowerCase(Locale.US);
        String lowerPrefix = prefix.toLowerCase(Locale.US);
        return lowerName.endsWith(".apk") && lowerName.startsWith(lowerPrefix + "_");
    }

    @Nullable
    public String parseVersionName(@NonNull String fileName) {
        Matcher matcher = strictPattern.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    public int parseVersionCode(@NonNull String fileName) {
        Matcher matcher = strictPattern.matcher(fileName);
        if (!matcher.matches()) {
            return 0;
        }
        try {
            return Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
