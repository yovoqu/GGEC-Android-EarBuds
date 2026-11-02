package com.ggec.earbuds.ui.ai.widget;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.markdown.MdBlock;

/** Markdown 引用块：左侧强调线与一份独立的嵌套 Markdown。 */
final class MarkdownQuoteBlockView extends LinearLayout {

    private final MarkdownView inner;

    MarkdownQuoteBlockView(@NonNull AiTheme theme, float textSp,
                           @NonNull MarkdownView.Callbacks callbacks) {
        super(theme.context);
        setOrientation(HORIZONTAL);
        View bar = new View(theme.context);
        bar.setBackground(theme.rounded(
                ColorUtils.setAlphaComponent(theme.brand, 0x99), 2));
        addView(bar, new LinearLayout.LayoutParams(
                theme.dp(3), ViewGroup.LayoutParams.MATCH_PARENT));
        inner = new MarkdownView(theme, textSp, theme.text2, callbacks);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginStart(theme.dp(10));
        addView(inner, params);
    }

    void bind(@NonNull MdBlock.Quote block) {
        inner.setBlocks(block.blocks());
    }
}
