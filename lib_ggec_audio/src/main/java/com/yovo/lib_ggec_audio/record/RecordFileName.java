package com.yovo.lib_ggec_audio.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 录音文件命名的唯一格式化实现：{@code 设备标识_录音类型[_来源标识]_时间戳.扩展名}。
 *
 * <p>本类只做字符串拼接，不感知任何具体产品：设备标识由上层以不透明字符串传入
 * （例如当前连接设备的型号 / 广播名），因此产物与具体产品解耦。</p>
 *
 * <p>规则：</p>
 * <ul>
 *   <li>时间戳固定 {@code yyyyMMdd_HHmmss}（本地时区、24 小时制）；</li>
 *   <li>各段只保留字母、数字与连字符，其它字符折叠成单个连字符，首尾连字符去除；</li>
 *   <li>设备标识缺省时回落 {@value #UNKNOWN_CODE}，录音类型缺省时回落 {@value #FALLBACK_TYPE}；</li>
 *   <li>来源标识（例如设备文件序号）可选，仅在该段非空时插入。</li>
 * </ul>
 */
public final class RecordFileName {

    /** 随录随传 / 实时录音。 */
    public static final String TYPE_STREAM = "stream";
    /** 设备内录音文件同步到手机本地。 */
    public static final String TYPE_SYNC = "sync";
    /** 音视频旁路录制。 */
    public static final String TYPE_AV = "av";

    /** 设备标识缺省时的回落值。 */
    public static final String UNKNOWN_CODE = "UNKNOWN";
    /** 录音类型缺省时的回落值。 */
    public static final String FALLBACK_TYPE = "rec";

    private static final int MAX_CODE_LEN = 32;
    private static final int MAX_TYPE_LEN = 16;
    private static final int MAX_TAG_LEN = 16;

    private RecordFileName() {
    }

    /**
     * 生成不带来源标识的文件名。
     *
     * @param deviceCode 设备标识（不透明字符串，可为空）
     * @param recordType 录音类型 token（见 {@link #TYPE_STREAM} 等）
     * @param epochMillis 录制开始时间（毫秒）
     * @param extension 扩展名（可带点，可为空）
     */
    @NonNull
    public static String build(@Nullable String deviceCode,
                               @Nullable String recordType,
                               long epochMillis,
                               @Nullable String extension) {
        return build(deviceCode, recordType, null, epochMillis, extension);
    }

    /**
     * 生成文件名，可带来源标识（如设备文件序号）。
     */
    @NonNull
    public static String build(@Nullable String deviceCode,
                               @Nullable String recordType,
                               @Nullable String sourceTag,
                               long epochMillis,
                               @Nullable String extension) {
        StringBuilder name = new StringBuilder(48);
        name.append(sanitize(deviceCode, UNKNOWN_CODE, MAX_CODE_LEN));
        name.append('_').append(sanitize(recordType, FALLBACK_TYPE, MAX_TYPE_LEN));
        String tag = sanitize(sourceTag, "", MAX_TAG_LEN);
        if (!tag.isEmpty()) {
            name.append('_').append(tag);
        }
        name.append('_').append(formatTimestamp(epochMillis));
        String ext = normalizeExtension(extension);
        if (!ext.isEmpty()) {
            name.append('.').append(ext);
        }
        return name.toString();
    }

    @NonNull
    static String formatTimestamp(long epochMillis) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(epochMillis));
    }

    @NonNull
    static String sanitize(@Nullable String raw, @NonNull String fallback, int maxLen) {
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        StringBuilder sb = new StringBuilder(trimmed.length());
        boolean lastWasDash = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9');
            if (keep) {
                sb.append(c);
                lastWasDash = false;
            } else if (!lastWasDash) {
                sb.append('-');
                lastWasDash = true;
            }
        }
        int start = 0;
        int end = sb.length();
        while (start < end && sb.charAt(start) == '-') {
            start++;
        }
        while (end > start && sb.charAt(end - 1) == '-') {
            end--;
        }
        String result = sb.substring(start, end);
        if (result.isEmpty()) {
            return fallback;
        }
        if (result.length() > maxLen) {
            result = result.substring(0, maxLen);
        }
        return result;
    }

    @NonNull
    static String normalizeExtension(@Nullable String extension) {
        if (extension == null) {
            return "";
        }
        String ext = extension.trim();
        while (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        StringBuilder sb = new StringBuilder(ext.length());
        for (int i = 0; i < ext.length(); i++) {
            char c = ext.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
