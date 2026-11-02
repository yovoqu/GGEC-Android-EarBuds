package com.ggec.earbuds.ui.ai.widget;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.markdown.MdLinks;
import com.ggec.earbuds.ui.ai.markdown.MdBlock;
import com.ggec.earbuds.ui.ai.markdown.MdSpan;
import com.yohu.youi.api.theme.YoFonts;

import java.util.List;

/** Markdown 的文本排版与行内样式。只负责把值模型变成可直接绘制的文本。 */
final class MarkdownTextFactory {

    private final AiTheme theme;
    private final float textSp;
    private final int textColor;
    private final MarkdownView.Callbacks callbacks;

    MarkdownTextFactory(@NonNull AiTheme theme, float textSp, @ColorInt int textColor,
                        @NonNull MarkdownView.Callbacks callbacks) {
        this.theme = theme;
        this.textSp = textSp;
        this.textColor = textColor;
        this.callbacks = callbacks;
    }

    @NonNull
    TextView body(float sp, @ColorInt int color) {
        LinkTextView view = new LinkTextView(theme.context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(theme.dpf(4), 1f);
        YoFonts.applyRegular(view);
        return view;
    }

    float textSize() {
        return textSp;
    }

    @ColorInt
    int textColor() {
        return textColor;
    }

    @NonNull
    TextView paragraph(@NonNull MdBlock.Paragraph block) {
        TextView view = body(textSp, textColor);
        view.setText(spannable(block.spans()));
        return view;
    }

    @NonNull
    TextView heading(@NonNull MdBlock.Heading block) {
        float size = switch (block.level()) {
            case 1 -> textSp + 5f;
            case 2 -> textSp + 3f;
            case 3 -> textSp + 1.5f;
            default -> textSp + 0.5f;
        };
        TextView view = body(size, textColor);
        YoFonts.applyBold(view);
        view.setText(spannable(block.spans()));
        return view;
    }

    @NonNull
    SpannableStringBuilder spannable(@NonNull List<MdSpan> spans) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        for (MdSpan span : spans) {
            int start = builder.length();
            builder.append(span.text());
            int end = builder.length();
            if (end == start) {
                continue;
            }
            int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
            if (span.has(MdSpan.BOLD)) {
                builder.setSpan(new TypefaceSpan(YoFonts.bold(theme.context)), start, end, flags);
            }
            if (span.has(MdSpan.ITALIC)) {
                builder.setSpan(new StyleSpan(Typeface.ITALIC), start, end, flags);
            }
            if (span.has(MdSpan.CODE)) {
                builder.setSpan(new TypefaceSpan("monospace"), start, end, flags);
                builder.setSpan(new RelativeSizeSpan(0.92f), start, end, flags);
                builder.setSpan(new BackgroundColorSpan(theme.code), start, end, flags);
            }
            if (span.has(MdSpan.STRIKE)) {
                builder.setSpan(new StrikethroughSpan(), start, end, flags);
            }
            String url = span.url();
            if (span.has(MdSpan.LINK) && url != null) {
                builder.setSpan(new LinkSpan(url), start, end, flags);
            }
        }
        return builder;
    }

    private final class LinkSpan extends ClickableSpan {

        private final String url;

        LinkSpan(String url) {
            this.url = url;
        }

        @Override
        public void onClick(@NonNull View widget) {
            if (MdLinks.isSafe(url)) {
                callbacks.onLinkClick(url.strip());
            }
        }

        @Override
        public void updateDrawState(@NonNull TextPaint paint) {
            paint.setColor(theme.brand);
            paint.setUnderlineText(false);
        }
    }
}
