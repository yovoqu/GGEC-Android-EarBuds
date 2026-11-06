package com.ggec.earbuds.ui.ai.call.subtitle;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.text.LineBreaker;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 单条字幕回合独立视图组件 (Single Subtitle Turn Item View)。
 *
 * <p>遵循高内聚组件化原则：
 * <ul>
 *   <li><b>独立标头</b>：角色微型微光圆点指示灯与角色字标；</li>
 *   <li><b>自适应正文</b>：彻底移除一切固定定高硬编码，采用 {@code WRAP_CONTENT} 与自然排版折行；</li>
 *   <li><b>严格基准线</b>：正文左边缘与标头垂直对齐，多行自然折行排版。</li>
 * </ul>
 * </p>
 */
public final class AiSubtitleItemView extends LinearLayout {

    private static final float MESSAGE_TEXT_SIZE_SP = 14f;

    private final AiTheme theme;
    private final View roleDot;
    private final TextView roleText;
    private final TextView contentText;

    public AiSubtitleItemView(@NonNull Context context, @NonNull AiTheme theme) {
        super(context);
        this.theme = theme;
        setOrientation(VERTICAL);

        // 1. 角色标识行：[微型彩色状态点] [角色标签 "AI" / "你"]
        LinearLayout roleHeader = new LinearLayout(context);
        roleHeader.setOrientation(HORIZONTAL);
        roleHeader.setGravity(Gravity.CENTER_VERTICAL);

        roleDot = new View(context);
        int dotSize = theme.dp(5f);
        roleDot.setBackground(createDotDrawable(theme.brand));
        LayoutParams dotLp = new LayoutParams(dotSize, dotSize);
        dotLp.rightMargin = theme.dp(5f);
        roleHeader.addView(roleDot, dotLp);

        roleText = theme.text(11.5f, theme.brand, AiTheme.Weight.BOLD);
        roleText.setSingleLine(true);
        roleHeader.addView(roleText, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(roleHeader, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 2. 消息正文：自适应高度包裹，多行自然折行
        contentText = theme.text(MESSAGE_TEXT_SIZE_SP, theme.text1);
        contentText.setLineSpacing(theme.dp(2f), 1.25f);
        contentText.setGravity(Gravity.START);
        contentText.setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY);

        LayoutParams contentLp = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.topMargin = theme.dp(2.5f);
        addView(contentText, contentLp);
    }

    public void bind(@NonNull AiSubtitleItem item, boolean isLast) {
        LayoutParams lp = (LayoutParams) getLayoutParams();
        if (lp == null) {
            lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        lp.bottomMargin = isLast ? 0 : theme.dp(10f);
        setLayoutParams(lp);

        int roleColor = item.isUser() ? theme.text2 : theme.brand;
        roleDot.setBackground(createDotDrawable(roleColor));
        roleText.setTextColor(roleColor);
        roleText.setText(item.isUser() ? R.string.ai_call_tag_user : R.string.ai_call_tag_ai);

        String displayText = item.text();
        if (!item.isFinal() && !displayText.endsWith("…")) {
            displayText = displayText + "…";
        }
        contentText.setText(displayText);
    }

    private static GradientDrawable createDotDrawable(int color) {
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(color);
        return dotBg;
    }
}
