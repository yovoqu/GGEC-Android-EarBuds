package com.yovo.lib_ai.models;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * 模型列表解码共用的宽松取值。Android 的 {@code optString} 会把 JSON null 读成 "null"，这里统一绕开。
 */
final class ModelJson {

    private ModelJson() {
    }

    @Nullable
    static String string(@Nullable JSONObject json, String... keys) {
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            if (!json.has(key) || json.isNull(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value instanceof String text && !text.isBlank()) {
                return text.trim();
            }
        }
        return null;
    }

    @Nullable
    static Integer positiveInt(@Nullable JSONObject json, String... keys) {
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            if (!json.has(key) || json.isNull(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value instanceof Number number) {
                long asLong = number.longValue();
                if (asLong > 0) {
                    return (int) Math.min(asLong, Integer.MAX_VALUE);
                }
            }
        }
        return null;
    }

    /**
     * 数组里的字符串，或对象里值为 true 的键，统一成小写集合。
     */
    static Set<String> lowerNames(@Nullable JSONObject json, String... keys) {
        Set<String> names = new HashSet<>();
        if (json == null) {
            return names;
        }
        for (String key : keys) {
            if (!json.has(key) || json.isNull(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value instanceof JSONArray array) {
                for (int i = 0; i < array.length(); i++) {
                    Object item = array.opt(i);
                    if (item instanceof String text) {
                        names.add(text.toLowerCase(Locale.ROOT));
                    }
                }
            } else if (value instanceof JSONObject object) {
                for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                    String name = it.next();
                    if (object.optBoolean(name, false)) {
                        names.add(name.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }
        return names;
    }

    /**
     * 秒或毫秒的时间戳，或 RFC 3339 字符串。无法解析时返回 -1。
     */
    static long millis(@Nullable JSONObject json, String... keys) {
        if (json == null) {
            return -1L;
        }
        for (String key : keys) {
            if (!json.has(key) || json.isNull(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value instanceof Number number) {
                long raw = number.longValue();
                if (raw <= 0) {
                    continue;
                }
                return raw < 100_000_000_000L ? raw * 1000L : raw;
            }
            if (value instanceof String text) {
                long parsed = parseTime(text.trim());
                if (parsed > 0) {
                    return parsed;
                }
            }
        }
        return -1L;
    }

    private static long parseTime(String text) {
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (RuntimeException ignored) {
            // 带偏移量的写法再试一次。
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }
}
