package com.yovo.lib_ai.internal.protocol;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.internal.json.Jsons;

import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * 各协议 {@code usage} 对象的读取口径。
 *
 * <p>为什么集中在这里：同一个概念在不同平台上叫法完全不同（{@code prompt_tokens} /
 * {@code input_tokens}；{@code cached_tokens} / {@code cache_read_input_tokens} /
 * {@code prompt_cache_hit_tokens}），分散到三份编解码里迟早会漏掉一家。
 * 这里只做“有就读、没有就是 null”，绝不按字符长度估算 token——
 * 估出来的数字不能拿去记账。</p>
 */
public final class UsageFields {

    /** OpenAI 的提示详情对象。 */
    private static final String PROMPT_DETAILS = "prompt_tokens_details";
    /** Responses 的输入详情对象。 */
    private static final String INPUT_DETAILS = "input_tokens_details";
    /** 兼容网关可能把费用塞进 cost_details。 */
    private static final String COST_DETAILS = "cost_details";

    private UsageFields() {
    }

    /**
     * 按顺序取第一个存在的整数字段，都缺失时返回 0。
     */
    public static int intOrZero(@Nullable JSONObject usage, @NonNull String... keys) {
        Integer value = firstInt(usage, keys);
        return value == null ? 0 : value;
    }

    /**
     * 按顺序取第一个存在的整数字段，都缺失时返回 null。
     */
    @Nullable
    public static Integer firstInt(@Nullable JSONObject usage, @NonNull String... keys) {
        if (usage == null) {
            return null;
        }
        for (String key : keys) {
            Integer value = intAt(usage, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 命中缓存的令牌。三家写法各不相同，按“详情对象 → 顶层常见别名”的顺序找。
     */
    @Nullable
    public static Integer cachedTokens(@Nullable JSONObject usage) {
        if (usage == null) {
            return null;
        }
        Integer detail = firstInt(Jsons.optObject(usage, PROMPT_DETAILS), "cached_tokens");
        if (detail == null) {
            detail = firstInt(Jsons.optObject(usage, INPUT_DETAILS), "cached_tokens");
        }
        if (detail != null) {
            return detail;
        }
        return firstInt(usage, "cached_tokens", "cache_read_input_tokens", "prompt_cache_hit_tokens");
    }

    /**
     * 写入缓存的令牌。目前只有 Anthropic 会报。
     */
    @Nullable
    public static Integer cacheCreationTokens(@Nullable JSONObject usage) {
        return firstInt(usage, "cache_creation_input_tokens");
    }

    /**
     * 网关给出的费用。字段名各家不同，读不到就是 null，不做任何推算。
     */
    @Nullable
    public static BigDecimal cost(@Nullable JSONObject usage) {
        if (usage == null) {
            return null;
        }
        BigDecimal direct = decimalAt(usage, "cost");
        if (direct == null) {
            direct = decimalAt(usage, "total_cost");
        }
        if (direct == null) {
            direct = decimalAt(Jsons.optObject(usage, COST_DETAILS), "total_cost");
        }
        return direct;
    }

    @Nullable
    private static Integer intAt(@Nullable JSONObject json, @NonNull String key) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        // 少数网关把整数字段写成字符串，能读就读，读不动当没有。
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim()).intValue();
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    private static BigDecimal decimalAt(@Nullable JSONObject json, @NonNull String key) {
        if (json == null || !json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text) {
            try {
                return new BigDecimal(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
