package com.ggec.earbuds.ui.ai.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;

import java.util.Locale;

/**
 * 数字的显示格式。只出纯文本和数字，带单位的整句话由页面用字符串资源拼，这样可以单测也方便翻译。
 */
public final class AiFormat {

    /** 并列信息（平台·模型、协议·地址、标题·数量）的统一分隔符。 */
    public static final String SEPARATOR = " · ";

    private AiFormat() {
    }

    /**
     * 副标题里的主机名：去掉协议头与尾斜杠。一段 {@code https://} 会把地址挤到看不见，
     * 而它从来不变。null/空白返回空串，由调用方决定是否隐藏。
     */
    public static String host(@Nullable String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String value = baseUrl.strip();
        int scheme = value.indexOf("://");
        if (scheme >= 0) {
            value = value.substring(scheme + 3);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * 令牌数的紧凑写法。整千的按十进制写（128000 → "128K"），其余按二进制写（131072 → "128K"，8192 → "8K"），
     * 两种都是模型厂商标注上下文长度的习惯，这样显示出来的数和厂商文档里的一致。
     */
    public static String tokens(int count) {
        if (count >= 1_000_000) {
            return trim(count % 1_000_000 == 0 ? count / 1_000_000d : count / 1_048_576d) + "M";
        }
        if (count >= 1_000) {
            return trim(count % 1_000 == 0 ? count / 1_000d : count / 1_024d) + "K";
        }
        return String.valueOf(count);
    }

    /** 毫秒的紧凑写法：不到 1 秒写毫秒，之后写一位小数的秒。 */
    public static String duration(long millis) {
        if (millis < 1_000) {
            return Math.max(millis, 0) + " ms";
        }
        return String.format(Locale.ROOT, "%.1f s", millis / 1_000d);
    }

    /**
     * 生成速率。只用能确证的口径：这段时间里出了多少 token。
     *
     * <p>没有首字时间，所以这是"整轮平均"，不是"生成阶段速率"。数据不足时返回 -1，
     * 界面据此不显示，而不是显示 0 或估算值。</p>
     */
    public static int tokensPerSecond(int outputTokens, long elapsedMillis) {
        if (outputTokens <= 0 || elapsedMillis < 300L) {
            return -1;
        }
        return (int) Math.round(outputTokens * 1000d / elapsedMillis);
    }

    /**
     * 消息尾注：耗时 · 速率 · token 数。三项都可能缺失，缺哪个就不写哪个，
     * 分隔符只在“已有内容之后”追加。View 只负责把它放进 TextView。
     */
    @NonNull
    public static String messageMeta(@NonNull Context context, long elapsedMillis,
                                     int tokensPerSecond, int outputTokens) {
        StringBuilder text = new StringBuilder();
        if (elapsedMillis > 0) {
            text.append(duration(elapsedMillis));
        }
        if (tokensPerSecond > 0) {
            if (text.length() > 0) {
                text.append(context.getString(R.string.ai_footer_separator));
            }
            text.append(context.getString(R.string.ai_value_speed, tokensPerSecond));
        }
        if (outputTokens > 0) {
            if (text.length() > 0) {
                text.append(context.getString(R.string.ai_footer_separator));
            }
            text.append(context.getString(R.string.ai_value_tokens, outputTokens));
        }
        return text.toString();
    }

    /** 分区标题：标题 + 数量，比如「模型 · 12」。 */
    @NonNull
    public static String sectionTitle(@NonNull Context context, @StringRes int titleRes, int count) {
        return context.getString(titleRes) + SEPARATOR + count;
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
