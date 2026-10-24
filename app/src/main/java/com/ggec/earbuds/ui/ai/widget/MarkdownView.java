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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.markdown.MarkdownParser;
import com.ggec.earbuds.ui.ai.markdown.MdBlock;
import com.ggec.earbuds.ui.ai.markdown.MdLinks;
import com.ggec.earbuds.ui.ai.markdown.MdSpan;
import com.yohu.youi.api.theme.YoFonts;

import java.util.List;

/**
 * 把 Markdown 画成一列原生视图。流式输出时每来一段新文字都会重新解析并调用 {@link #setMarkdown}，
 * 这里按位置比较前后两次的块：没变的块不碰，同类型的块原地更新文字，类型变了才换视图，
 * 所以长回答一边生成一边显示也不会闪烁或掉帧。
 */
public final class MarkdownView extends LinearLayout {

    public interface Callbacks {
        void onLinkClick(@NonNull String url);

        void onCopyCode(@NonNull String code);
    }

    private final MarkdownParser parser = new MarkdownParser();
    private final AiTheme theme;
    private final Callbacks callbacks;
    private final float textSp;
    @ColorInt
    private final int textColor;
    private List<MdBlock> shown = List.of();

    public MarkdownView(@NonNull AiTheme theme, float textSp, @ColorInt int textColor, @NonNull Callbacks callbacks) {
        super(theme.context);
        this.theme = theme;
        this.callbacks = callbacks;
        this.textSp = textSp;
        this.textColor = textColor;
        setOrientation(VERTICAL);
    }

    public void setMarkdown(@NonNull String markdown) {
        setBlocks(parser.parse(markdown));
    }

    public void setBlocks(@NonNull List<MdBlock> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            MdBlock next = blocks.get(i);
            if (i < shown.size()) {
                MdBlock previous = shown.get(i);
                if (previous.equals(next)) {
                    continue;
                }
                if (previous.type() == next.type() && update(getChildAt(i), next)) {
                    continue;
                }
                removeViewAt(i);
            }
            addView(create(next), i, params(next, i));
        }
        while (getChildCount() > blocks.size()) {
            removeViewAt(getChildCount() - 1);
        }
        shown = blocks;
    }

    // ---- 创建与更新 ----

    private ViewGroup.LayoutParams params(MdBlock block, int index) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index > 0) {
            float top = switch (block.type()) {
                case HEADING -> 18f;
                case CODE, TABLE, QUOTE -> 12f;
                case RULE -> 12f;
                case LIST -> 8f;
                case PARAGRAPH -> 14f;
            };
            params.topMargin = theme.dp(top);
        }
        if (block.type() == MdBlock.Type.RULE) {
            params.bottomMargin = theme.dp(4);
        }
        return params;
    }

    private View create(MdBlock block) {
        switch (block.type()) {
            case PARAGRAPH:
                return paragraph((MdBlock.Paragraph) block);
            case HEADING:
                return heading((MdBlock.Heading) block);
            case CODE: {
                CodeBlockView view = new CodeBlockView();
                view.bind((MdBlock.Code) block);
                return view;
            }
            case QUOTE: {
                QuoteView view = new QuoteView();
                view.bind((MdBlock.Quote) block);
                return view;
            }
            case LIST: {
                ListBlockView view = new ListBlockView();
                view.bind((MdBlock.ListBlock) block);
                return view;
            }
            case TABLE: {
                TableBlockView view = new TableBlockView();
                view.bind((MdBlock.Table) block);
                return view;
            }
            default: {
                View rule = new View(theme.context);
                rule.setBackgroundColor(theme.divider);
                rule.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        Math.max(1, theme.dp(1))));
                return rule;
            }
        }
    }

    private boolean update(View view, MdBlock block) {
        switch (block.type()) {
            case PARAGRAPH:
                ((TextView) view).setText(spannable(((MdBlock.Paragraph) block).spans(), textColor));
                return true;
            case HEADING:
                ((TextView) view).setText(spannable(((MdBlock.Heading) block).spans(), textColor));
                return true;
            case CODE:
                ((CodeBlockView) view).bind((MdBlock.Code) block);
                return true;
            case QUOTE:
                ((QuoteView) view).bind((MdBlock.Quote) block);
                return true;
            case LIST:
                ((ListBlockView) view).bind((MdBlock.ListBlock) block);
                return true;
            case TABLE:
                ((TableBlockView) view).bind((MdBlock.Table) block);
                return true;
            default:
                return false;
        }
    }

    private TextView paragraph(MdBlock.Paragraph block) {
        TextView view = body(textSp, textColor);
        view.setText(spannable(block.spans(), textColor));
        return view;
    }

    private TextView heading(MdBlock.Heading block) {
        float size = switch (block.level()) {
            case 1 -> textSp + 5f;
            case 2 -> textSp + 3f;
            case 3 -> textSp + 1.5f;
            default -> textSp + 0.5f;
        };
        TextView view = body(size, textColor);
        YoFonts.applyBold(view);
        view.setText(spannable(block.spans(), textColor));
        return view;
    }

    private TextView body(float sp, @ColorInt int color) {
        LinkTextView view = new LinkTextView(theme.context);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setLineSpacing(theme.dpf(4), 1f);
        YoFonts.applyRegular(view);
        return view;
    }

    // ---- 行内样式 ----

    SpannableStringBuilder spannable(List<MdSpan> spans, @ColorInt int baseColor) {
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

    // ---- 代码块 ----

    private final class CodeBlockView extends LinearLayout {

        private final TextView language;
        private final ImageView copyIcon;
        private final TextView copyLabel;
        private final TextView code;
        private String current = "";
        private final Runnable restoreCopy;

        CodeBlockView() {
            super(theme.context);
            setOrientation(VERTICAL);
            setBackground(theme.rounded(theme.code, 12));
            setClipToOutline(true);
            setOutlineProvider(ViewOutlineProvider.BACKGROUND);

            LinearLayout header = new LinearLayout(theme.context);
            header.setOrientation(HORIZONTAL);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(theme.dp(12), 0, theme.dp(4), 0);
            language = theme.text(12, theme.text3, AiTheme.Weight.MEDIUM);
            header.addView(language, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

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
            header.addView(copy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, theme.dp(34)));
            addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(34)));

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

        void bind(MdBlock.Code block) {
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

    // ---- 引用 ----

    private final class QuoteView extends LinearLayout {

        private final MarkdownView inner;

        QuoteView() {
            super(theme.context);
            setOrientation(HORIZONTAL);
            View bar = new View(theme.context);
            bar.setBackground(theme.rounded(ColorUtils.setAlphaComponent(theme.brand, 0x99), 2));
            addView(bar, new LinearLayout.LayoutParams(theme.dp(3), ViewGroup.LayoutParams.MATCH_PARENT));
            inner = new MarkdownView(theme, textSp, theme.text2, callbacks);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMarginStart(theme.dp(10));
            addView(inner, params);
        }

        void bind(MdBlock.Quote block) {
            inner.setBlocks(block.blocks());
        }
    }

    // ---- 列表 ----

    private final class ListBlockView extends LinearLayout {

        ListBlockView() {
            super(theme.context);
            setOrientation(VERTICAL);
        }

        void bind(MdBlock.ListBlock block) {
            List<MdBlock.ListItem> items = block.items();
            for (int i = 0; i < items.size(); i++) {
                MdBlock.ListItem item = items.get(i);
                ItemRow row = i < getChildCount() ? (ItemRow) getChildAt(i) : null;
                if (row == null) {
                    row = new ItemRow();
                    addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                }
                row.bind(item, i == 0);
            }
            while (getChildCount() > items.size()) {
                removeViewAt(getChildCount() - 1);
            }
        }

        private final class ItemRow extends LinearLayout {

            private final TextView marker;
            private final View dot;
            private final TextView content;

            ItemRow() {
                super(theme.context);
                setOrientation(HORIZONTAL);
                content = body(textSp, textColor);
                // 项目符号画成小圆点：字体里的 "•" 又小又淡，和正文放在一起显得没有层次。
                android.graphics.Paint.FontMetrics metrics = content.getPaint().getFontMetrics();
                float firstLine = metrics.descent - metrics.ascent;

                FrameLayout slot = new FrameLayout(theme.context);
                marker = theme.text(textSp, textColor);
                marker.setGravity(Gravity.END);
                marker.setLineSpacing(theme.dpf(4), 1f);
                slot.addView(marker, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                dot = new View(theme.context);
                FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(
                        theme.dp(6), theme.dp(6), Gravity.END | Gravity.TOP);
                dotParams.topMargin = Math.round(firstLine / 2f - theme.dp(3));
                dotParams.setMarginEnd(theme.dp(4));
                slot.addView(dot, dotParams);
                addView(slot, new LinearLayout.LayoutParams(theme.dp(22), ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                params.setMarginStart(theme.dp(6));
                addView(content, params);
            }

            void bind(MdBlock.ListItem item, boolean first) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
                params.topMargin = first ? 0 : theme.dp(4);
                params.setMarginStart(theme.dp(18f * item.level()));
                setLayoutParams(params);
                boolean bullet = item.checked() == null && !item.ordered();
                dot.setVisibility(bullet ? VISIBLE : GONE);
                marker.setVisibility(bullet ? GONE : VISIBLE);
                if (bullet) {
                    // 第一层实心，第二层空心，更深的层再回到实心。
                    dot.setBackground(item.level() % 2 == 0
                            ? theme.circle(theme.text1)
                            : theme.ring(theme.text1, 1.5f));
                } else if (item.checked() != null) {
                    marker.setText(item.checked() ? "☑" : "☐");
                } else {
                    marker.setText(item.number() + ".");
                }
                content.setText(spannable(item.spans(), textColor));
            }
        }
    }
    // ---- 表格 ----

    private final class TableBlockView extends HorizontalScrollView {

        private final TableLayout table;

        TableBlockView() {
            super(theme.context);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            table = new TableLayout(theme.context);
            table.setBackground(theme.roundedStroke(0, 10, theme.cardStroke, 1));
            table.setClipToOutline(true);
            table.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
            addView(table, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        void bind(MdBlock.Table block) {
            table.removeAllViews();
            addRow(block, block.header(), true);
            for (List<List<MdSpan>> row : block.rows()) {
                addRow(block, row, false);
            }
        }

        private void addRow(MdBlock.Table block, List<List<MdSpan>> cells, boolean header) {
            TableRow row = new TableRow(theme.context);
            if (header) {
                row.setBackgroundColor(theme.fill);
            } else if (table.getChildCount() > 0) {
                View separator = new View(theme.context);
                separator.setBackgroundColor(theme.cardStroke);
                table.addView(separator, new TableLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, theme.dp(1))));
            }
            for (int column = 0; column < block.aligns().size(); column++) {
                TextView cell = body(textSp - 1f, textColor);
                cell.setMaxWidth(theme.dp(220));
                cell.setMinWidth(theme.dp(64));
                cell.setPadding(theme.dp(10), theme.dp(8), theme.dp(10), theme.dp(8));
                if (header) {
                    YoFonts.applyBold(cell);
                }
                MdBlock.Align align = block.aligns().get(column);
                cell.setGravity(align == MdBlock.Align.END ? Gravity.END
                        : align == MdBlock.Align.CENTER ? Gravity.CENTER_HORIZONTAL : Gravity.START);
                List<MdSpan> spans = column < cells.size() ? cells.get(column) : List.of();
                cell.setText(spannable(spans, textColor));
                row.addView(cell, new TableRow.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            table.addView(row, new TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }
}
