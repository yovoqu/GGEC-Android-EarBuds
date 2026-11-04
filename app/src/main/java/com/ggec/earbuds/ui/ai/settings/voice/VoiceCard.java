package com.ggec.earbuds.ui.ai.settings.voice;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.ui.ai.AiTheme;

import java.util.Locale;

/**
 * 语音设置的自绘卡片原语：卡片、行、分隔线、胶囊、选中圆点、尾部值。
 *
 * <p>这一页不使用系统的列表容器：行里要放能力角标、双行事实、实心/空心圆点，
 * 系统行模板塞不下也不该为了塞而变形。四个原语把"卡片长什么样、行怎么排"固定下来，
 * 各区块只负责内容，排版只有一处口径。</p>
 */
final class VoiceCard {

    private VoiceCard() {
    }

    @NonNull
    static LinearLayout plate(@NonNull Context context, @NonNull AiTheme theme) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(theme.roundedStroke(theme.card, AiTheme.RADIUS_SHEET, theme.cardStroke, 1));
        card.setClipToOutline(true);
        return card;
    }

    static void addDivider(@NonNull LinearLayout card, @NonNull Context context,
                           @NonNull AiTheme theme) {
        View line = new View(context);
        line.setBackgroundColor(theme.divider);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1)));
        params.leftMargin = theme.dp(AiTheme.SPACE_L + 20f + AiTheme.SPACE_M);
        card.addView(line, params);
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

    /** 副标题里不放协议头：一段 https:// 会把地址挤到看不见，而它从来不变。 */
    @NonNull
    static String host(@NonNull String baseUrl) {
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

    /** 一行：可选行首图标 + 标题/副标题 + 尾部容器。 */
    static final class Row {
        final LinearLayout view;
        final LinearLayout trailing;
        final TextView title;
        final TextView subtitle;

        private Row(LinearLayout view, LinearLayout trailing, TextView title, TextView subtitle) {
            this.view = view;
            this.trailing = trailing;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    @NonNull
    static Row row(@NonNull Context context, @NonNull AiTheme theme, @DrawableRes int iconRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(theme.dp(AiTheme.ROW_HEIGHT));
        row.setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_M),
                theme.dp(AiTheme.SPACE_M), theme.dp(AiTheme.SPACE_M));
        row.setBackground(theme.pressable(AiTheme.RADIUS_ROW));

        if (iconRes != 0) {
            ImageView icon = theme.icon(iconRes, theme.text2);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    theme.dp(20f), theme.dp(20f));
            iconParams.setMarginEnd(theme.dp(AiTheme.SPACE_M));
            row.addView(icon, iconParams);
        }

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = theme.text(AiTheme.TEXT_BODY, theme.text1, AiTheme.Weight.MEDIUM);
        texts.addView(title, matchWrap());
        TextView subtitle = theme.text(AiTheme.TEXT_CAPTION, theme.text3);
        subtitle.setVisibility(View.GONE);
        LinearLayout.LayoutParams subParams = matchWrap();
        subParams.topMargin = theme.dp(2f);
        texts.addView(subtitle, subParams);

        LinearLayout trailing = new LinearLayout(context);
        trailing.setOrientation(LinearLayout.HORIZONTAL);
        trailing.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trailingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trailingParams.setMarginStart(theme.dp(AiTheme.SPACE_S));
        row.addView(trailing, trailingParams);

        return new Row(row, trailing, title, subtitle);
    }

    @NonNull
    static TextView chip(@NonNull Context context, @NonNull AiTheme theme, @NonNull CharSequence text,
                         @ColorInt int textColor, @ColorInt int background) {
        TextView chip = theme.text(11f, textColor, AiTheme.Weight.MEDIUM);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(theme.dp(AiTheme.SPACE_S), theme.dp(2f), theme.dp(AiTheme.SPACE_S), theme.dp(2f));
        chip.setBackground(theme.rounded(background, 8f));
        return chip;
    }

    /** 14dp 选中圆点：选中实心品牌、未选中空心三级灰；形状先于颜色。 */
    @NonNull
    static View dot(@NonNull Context context, @NonNull AiTheme theme, boolean selected) {
        View dot = new View(context);
        dot.setBackground(selected ? theme.circle(theme.brand) : theme.ring(theme.text3, 1.5f));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                theme.dp(14f), theme.dp(14f));
        params.setMarginEnd(theme.dp(AiTheme.SPACE_M));
        dot.setLayoutParams(params);
        return dot;
    }

    @NonNull
    static TextView chevron(@NonNull Context context, @NonNull AiTheme theme) {
        TextView chevron = theme.text(18f, theme.text3);
        chevron.setText("›");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(theme.dp(AiTheme.SPACE_XS));
        chevron.setLayoutParams(params);
        return chevron;
    }

    @NonNull
    static String firstLetter(@Nullable String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.strip();
        if (trimmed.isEmpty()) {
            return "";
        }
        int end = trimmed.offsetByCodePoints(0, 1);
        return trimmed.substring(0, end).toUpperCase(Locale.ROOT);
    }

    @NonNull
    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
