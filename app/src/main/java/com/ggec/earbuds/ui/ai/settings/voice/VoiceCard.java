package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 语音设置各卡片共用的轻量排版原语：分隔线、主机名格式化与常用布局参数。
 */
final class VoiceCard {

    private VoiceCard() {
    }

    /** 概览卡里的整宽水平线。 */
    @NonNull
    static View horizontalLine(@NonNull Context context, @NonNull AiTheme theme) {
        View line = new View(context);
        line.setBackgroundColor(theme.divider);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1))));
        return line;
    }

    /** 事实网格里的竖线：占满所在行的高度。 */
    @NonNull
    static View verticalLine(@NonNull Context context, @NonNull AiTheme theme) {
        View line = new View(context);
        line.setBackgroundColor(theme.divider);
        line.setLayoutParams(new LinearLayout.LayoutParams(
                Math.max(1, theme.dp(1)), ViewGroup.LayoutParams.MATCH_PARENT));
        return line;
    }

    @NonNull
    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
