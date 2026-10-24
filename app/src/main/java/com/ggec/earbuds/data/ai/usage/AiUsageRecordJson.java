package com.ggec.earbuds.data.ai.usage;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.model.AiUsageSource;
import com.ggec.earbuds.domain.ai.model.AiUsageStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * 一条用量流水的单行编解码（JSON Lines）。
 *
 * <p>为什么用行而不是整份数组文档：追加一条只需重写它能影响的最后一段，
 * 出错时也只坏一行。费用写成字符串而不是数字——JSON 数字经 double 往返会掉精度，
 * 而这里的钱是要拿来核对的。</p>
 */
public final class AiUsageRecordJson {

    /**
     * @return 单行 JSON，不含换行符
     */
    public String encode(AiUsageRecord record) {
        JSONObject json = new JSONObject();
        try {
            json.put("ts", record.timestampMillis());
            if (record.conversationId() != null) {
                json.put("conversation", record.conversationId());
            }
            json.put("platform", record.platformId());
            json.put("model", record.modelId());
            json.put("in", record.inputTokens());
            json.put("out", record.outputTokens());
            json.put("total", record.totalTokens());
            if (record.cost() != null) {
                json.put("cost", record.cost().toPlainString());
            }
            json.put("elapsed", record.elapsedMillis());
            json.put("status", record.status().name());
            json.put("source", record.source().name());
            if (record.failureReason() != null) {
                json.put("failure", record.failureReason());
            }
        } catch (JSONException impossible) {
            throw new IllegalStateException("Cannot encode a usage record", impossible);
        }
        return json.toString();
    }

    /**
     * 解一行。认不出的状态、缺时间戳、坏 JSON 都返回 null——这行当作没写过，
     * 而不是猜一个值塞进统计里。
     */
    @Nullable
    public AiUsageRecord decode(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        JSONObject json;
        try {
            json = new JSONObject(line.trim());
        } catch (JSONException malformed) {
            return null;
        }
        AiUsageStatus status = AiUsageStatus.parse(string(json, "status"));
        if (status == null) {
            return null;
        }
        Object timestamp = json.opt("ts");
        if (!(timestamp instanceof Number number)) {
            return null;
        }
        return new AiUsageRecord(
                number.longValue(),
                string(json, "conversation"),
                string(json, "platform", ""),
                string(json, "model", ""),
                integer(json, "in"),
                integer(json, "out"),
                integer(json, "total"),
                cost(json),
                longValue(json, "elapsed"),
                status,
                string(json, "failure"),
                source(string(json, "source")));
    }

    @Nullable
    private static AiUsageSource source(String name) {
        if (name != null) {
            for (AiUsageSource source : AiUsageSource.values()) {
                if (source.name().equals(name)) {
                    return source;
                }
            }
        }
        return AiUsageSource.CHAT;
    }

    private static String string(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }
        Object value = json.opt(key);
        return value instanceof String text ? text : null;
    }

    private static String string(JSONObject json, String key, String fallback) {
        String value = string(json, key);
        return value == null ? fallback : value;
    }

    private static int integer(JSONObject json, String key) {
        Object value = json.opt(key);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long longValue(JSONObject json, String key) {
        Object value = json.opt(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * 费用既可能是字符串（我们自己写的），也可能是数字（手工造的测试数据或旧格式）。
     */
    @Nullable
    private static BigDecimal cost(JSONObject json) {
        if (!json.has("cost") || json.isNull("cost")) {
            return null;
        }
        Object value = json.opt("cost");
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
