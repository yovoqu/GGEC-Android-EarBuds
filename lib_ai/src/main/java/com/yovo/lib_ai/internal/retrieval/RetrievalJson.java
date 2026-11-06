package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiProtocolException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 检索类服务解码共用的宽松取值与报文解析。
 */
final class RetrievalJson {

    private RetrievalJson() {
    }

    @NonNull
    static JSONObject parseObject(@Nullable String raw, @NonNull String what) {
        if (raw == null || raw.isBlank()) {
            throw new YoAiProtocolException(what + " body is empty", raw);
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException exception) {
            throw new YoAiProtocolException(what + " body is not a JSON object", raw, exception);
        }
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
    static Integer integer(@Nullable JSONObject json, String... keys) {
        if (json == null) {
            return null;
        }
        for (String key : keys) {
            if (!json.has(key) || json.isNull(key)) {
                continue;
            }
            Object value = json.opt(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text) {
                try {
                    return Integer.valueOf(text.trim());
                } catch (NumberFormatException ignored) {
                    // 继续尝试下一个别名。
                }
            }
        }
        return null;
    }

    static long longValue(@Nullable JSONObject json, String... keys) {
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
                if (raw > 0) {
                    return raw;
                }
            }
        }
        return -1L;
    }

    static double doubleValue(@Nullable JSONObject json, @NonNull String key) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return 0d;
        }
        Object value = json.opt(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return 0d;
            }
        }
        return 0d;
    }

    @Nullable
    static JSONObject object(@Nullable JSONObject json, @NonNull String key) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.optJSONObject(key);
    }

    @Nullable
    static JSONArray array(@Nullable JSONObject json, @NonNull String key) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        return json.optJSONArray(key);
    }

    @NonNull
    static List<String> stringList(@Nullable JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            if (array.opt(i) instanceof String text && !text.isBlank()) {
                values.add(text.trim());
            }
        }
        return values;
    }

    /** 秒或毫秒时间戳，或 RFC 3339 字符串；无法解析返回 -1。 */
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
