package com.yovo.biz_appupdate.internal.infrastructure.platform.pgy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 将蒲公英 {@code /apiv2/app/check} 字段映射为可下载地址。
 *
 * <p>官方约定：{@code downloadURL} 才是应用安装地址（{@code /apiv2/app/install}）；
 * {@code appURl} 是安装单页短链，不能当作 APK 直链。缺失合法 {@code downloadURL}
 * 时，用 {@code _api_key} + {@code buildKey} 拼接安装接口。</p>
 */
public final class PgyerCheckMapper {

    static final String INSTALL_ENDPOINT = "https://www.pgyer.com/apiv2/app/install";

    private PgyerCheckMapper() {
    }

    @NonNull
    public static String resolveDownloadUrl(@Nullable String downloadUrl,
                                            @Nullable String buildKey,
                                            @Nullable String apiKey) {
        String trimmedDownload = trim(downloadUrl);
        if (isPgyerInstallUrl(trimmedDownload)) {
            return trimmedDownload;
        }
        String constructed = constructInstallUrl(apiKey, buildKey);
        if (!constructed.isEmpty()) {
            return constructed;
        }
        if (isHttpUrl(trimmedDownload) && !isPgyerNonInstallPage(trimmedDownload)) {
            return trimmedDownload;
        }
        return "";
    }

    public static long parseFileSize(@Nullable Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static int parseVersionCode(@Nullable String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @NonNull
    static String constructInstallUrl(@Nullable String apiKey, @Nullable String buildKey) {
        String key = trim(apiKey);
        String build = trim(buildKey);
        if (key.isEmpty() || build.isEmpty()) {
            return "";
        }
        try {
            return INSTALL_ENDPOINT
                    + "?_api_key=" + URLEncoder.encode(key, StandardCharsets.UTF_8.name())
                    + "&buildKey=" + URLEncoder.encode(build, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    static boolean isPgyerInstallUrl(@Nullable String url) {
        return isHttpUrl(url) && url.toLowerCase(Locale.US).contains("/apiv2/app/install");
    }

    static boolean isHttpUrl(@Nullable String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("https://") || lower.startsWith("http://");
    }

    /**
     * 蒲公英安装单页 / 落地页，不能作为应用内 APK 下载地址。
     */
    static boolean isPgyerNonInstallPage(@Nullable String url) {
        if (!isHttpUrl(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("/apiv2/app/install") || lower.contains(".apk")) {
            return false;
        }
        return lower.contains("://www.pgyer.com/")
                || lower.contains("://pgyer.com/")
                || lower.contains("://www.xcxwo.com/")
                || lower.contains("://www.pgyerapp.com/");
    }

    @NonNull
    private static String trim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
