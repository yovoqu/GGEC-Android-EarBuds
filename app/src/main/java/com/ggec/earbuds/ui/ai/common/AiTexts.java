package com.ggec.earbuds.ui.ai.common;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;

import java.time.ZoneId;

/**
 * 把结构化的数据变成界面上的一句话。数字和单位在 {@link AiFormat}、{@link AiTimeLabel} 里已经算好，
 * 这里只按字符串资源拼接，所以换语言只需要改资源。
 */
public final class AiTexts {

    private AiTexts() {
    }

    @NonNull
    public static String relativeTime(@NonNull Context context, long nowMillis, long thenMillis) {
        AiTimeLabel label = AiTimeLabel.of(nowMillis, thenMillis, ZoneId.systemDefault());
        return switch (label.kind()) {
            case NOW -> context.getString(R.string.ai_time_now);
            case MINUTES -> context.getString(R.string.ai_time_minutes, label.first());
            case HOURS -> context.getString(R.string.ai_time_hours, label.first());
            case YESTERDAY -> context.getString(R.string.ai_time_yesterday);
            case DATE -> context.getString(R.string.ai_time_date, label.first(), label.second());
        };
    }

    /** "128K 上下文"；平台没有提供上下文长度时返回空串。 */
    @NonNull
    public static String contextWindow(@NonNull Context context, int tokens) {
        return tokens <= 0 ? "" : context.getString(R.string.ai_model_context, AiFormat.tokens(tokens));
    }
}
