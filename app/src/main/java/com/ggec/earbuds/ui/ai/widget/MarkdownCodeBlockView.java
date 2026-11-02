package com.ggec.earbuds.ui.ai.widget;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.markdown.MdBlock;

/** Markdown 代码块：语言标签、复制动作和可横向滚动的正文。 */
final class MarkdownCodeBlockView extends LinearLayout {

    private final AiTheme theme;
    private final MarkdownView.Callbacks callbacks;
    private final TextView language;
    private final ImageView copyIcon;
    private final TextView copyLabel;
    private final TextView code;
    private final Runnable restoreCopy;
    private String current = "";

    MarkdownCodeBlockView(@NonNull AiTheme theme, @NonNull MarkdownView.Callbacks callbacks) {
        super(theme.context);
        this.theme = theme;
        this.callbacks = callbacks;
        setOrientation(VERTICAL);
        setBackground(theme.rounded(theme.code, 12));
        setClipToOutline(true);
        setOutlineProvider(ViewOutlineProvider.BACKGROUND);

        LinearLayout header = new LinearLayout(theme.context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(theme.dp(12), 0, theme.dp(4), 0);
        language = theme.text(12, theme.text3, AiTheme.Weight.MEDIUM);
        header.addView(language, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout copy = new LinearLayout(theme.context);
        copy.setOrientation(HORIZONTAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(theme.dp(8), 0, theme.dp(8), 0);
        copy.setBackground(theme.pressable(8));
        copy.setContentDescription(getContext().getString(R.string.ai_code_copy));
        copyIcon = theme.icon(R.drawable.ic_ai_copy, theme.text2);
        copy.addView(copyIcon, new LinearLayout.LayoutParams(theme.dp(14), theme.dp(14)));
        copyLabel = theme.text(12, theme.text2, AiTheme.Weight.MEDIUM);
        copyLabel.setText(R.string.ai_copy);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMarginStart(theme.dp(4));
        copy.addView(copyLabel, labelParams);
        header.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, theme.dp(34)));
        addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(34)));

        HorizontalScrollView scroller = new HorizontalScrollView(theme.context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setOverScrollMode(OVER_SCROLL_NEVER);
        code = theme.text(13, theme.text1);
        code.setTypeface(Typeface.MONOSPACE);
        code.setLineSpacing(theme.dpf(3), 1f);
        code.setPadding(theme.dp(12), theme.dp(2), theme.dp(12), theme.dp(12));
        scroller.addView(code, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        restoreCopy = () -> {
            copyIcon.setImageResource(R.drawable.ic_ai_copy);
            copyLabel.setText(R.string.ai_copy);
        };
        copy.setOnClickListener(view -> {
            callbacks.onCopyCode(current);
            copyIcon.setImageResource(R.drawable.ic_ai_check);
            copyLabel.setText(R.string.ai_copied);
            removeCallbacks(restoreCopy);
            postDelayed(restoreCopy, 1500);
        });
    }

    void bind(@NonNull MdBlock.Code block) {
        current = block.text();
        String name = block.language() == null ? "" : block.language().strip();
        language.setText(name.isEmpty() ? "code" : name);
        if (!block.text().contentEquals(code.getText())) {
            code.setText(block.text());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(restoreCopy);
        super.onDetachedFromWindow();
    }
}
