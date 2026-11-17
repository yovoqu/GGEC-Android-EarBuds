package com.ggec.earbuds.data.json;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.store.DocumentFormatException;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Optional;

/**
 * 数据层共用的 JSON 取值工具：只有两种语义，没有第三种。
 *
 * <ul>
 *   <li>{@code required*}：缺失、JSON null、类型不符都抛 {@link DocumentFormatException}。
 *       文档里该有的字段没读到，就是文档坏了，不允许在取值处补默认值。</li>
 *   <li>{@code optional*}：缺失或 JSON null 返回 {@link Optional#empty()}；字段存在但类型不符同样抛，
 *       因为"存在却是错的类型"是损坏，不是缺失。</li>
 * </ul>
 *
 * <p>编码只有 {@link #put}（新对象写入，可空值跳过）与 {@link #putOrRemove}
 * （复用对象上覆盖写，空值表示删掉这个键）。所有"缺值就给默认值"的旧方法已删除：
 * 需要默认值的地方由领域模型自己声明"没有这个字段等于哪种事实"，不在取值处猜。</p>
 */
public final class JsonValues {

    private JsonValues() {
    }

    public static String requiredString(JSONObject json, String key) {
        Object value = present(json, key, "string");
        if (!(value instanceof String text)) {
            throw typeError(key, "string", value);
        }
        return text;
    }

    public static int requiredInt(JSONObject json, String key) {
        long value = requiredLong(json, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new DocumentFormatException(key, key + " is out of the int range");
        }
        return (int) value;
    }

    public static long requiredLong(JSONObject json, String key) {
        Object value = present(json, key, "number");
        if (!(value instanceof Number number)) {
            throw typeError(key, "number", value);
        }
        return number.longValue();
    }

    public static boolean requiredBoolean(JSONObject json, String key) {
        Object value = present(json, key, "boolean");
        if (!(value instanceof Boolean flag)) {
            throw typeError(key, "boolean", value);
        }
        return flag;
    }

    public static Optional<String> optionalString(JSONObject json, String key) {
        Object value = optional(json, key);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String text)) {
            throw typeError(key, "string", value);
        }
        return Optional.of(text);
    }

    public static Optional<Integer> optionalInt(JSONObject json, String key) {
        return optionalLong(json, key).map(value -> {
            long number = value;
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new DocumentFormatException(key, key + " is out of the int range");
            }
            return (int) number;
        });
    }

    public static Optional<Long> optionalLong(JSONObject json, String key) {
        Object value = optional(json, key);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Number number)) {
            throw typeError(key, "number", value);
        }
        return Optional.of(number.longValue());
    }

    public static Optional<Boolean> optionalBoolean(JSONObject json, String key) {
        Object value = optional(json, key);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Boolean flag)) {
            throw typeError(key, "boolean", value);
        }
        return Optional.of(flag);
    }

    /** 编码：往新对象写一个键；空值不写（字段"没有"由 Optional 表达，不由默认值表达）。 */
    public static void put(JSONObject json, String key, @Nullable Object value) {
        if (value == null) {
            return;
        }
        try {
            json.put(key, value);
        } catch (JSONException impossible) {
            throw new IllegalStateException("Cannot encode " + key, impossible);
        }
    }

    /** 编码：在复用对象上覆盖写；空值表示这个键不该存在，删掉而不是写成 JSON null。 */
    public static void putOrRemove(JSONObject json, String key, @Nullable Object value) {
        if (value == null) {
            json.remove(key);
            return;
        }
        put(json, key, value);
    }

    @Nullable
    private static Object optional(JSONObject json, String key) {
        return !json.has(key) || json.isNull(key) ? null : json.opt(key);
    }

    private static Object present(JSONObject json, String key, String expected) {
        Object value = optional(json, key);
        if (value == null) {
            throw new DocumentFormatException(key, "missing required " + expected + " " + key);
        }
        return value;
    }

    private static DocumentFormatException typeError(String key, String expected, Object actual) {
        return new DocumentFormatException(key,
                "expected " + expected + " at " + key + " but found " + describe(actual));
    }

    private static String describe(Object value) {
        if (value == null) {
            return "nothing";
        }
        if (value instanceof JSONObject) {
            return "an object";
        }
        return value.getClass().getSimpleName();
    }
}
