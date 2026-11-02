package com.ggec.earbuds.ui.ai.widget;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.markdown.MarkdownParser;
import com.ggec.earbuds.ui.ai.markdown.MdBlock;

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
    private final MarkdownTextFactory text;
    private List<MdBlock> shown = List.of();

    public MarkdownView(@NonNull AiTheme theme, float textSp, @ColorInt int textColor, @NonNull Callbacks callbacks) {
        super(theme.context);
        this.theme = theme;
        this.callbacks = callbacks;
        this.textSp = textSp;
        this.text = new MarkdownTextFactory(theme, textSp, textColor, callbacks);
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
                return text.heading((MdBlock.Heading) block);
            case CODE: {
                MarkdownCodeBlockView view = new MarkdownCodeBlockView(theme, callbacks);
                view.bind((MdBlock.Code) block);
                return view;
            }
            case QUOTE: {
                MarkdownQuoteBlockView view = new MarkdownQuoteBlockView(theme, textSp, callbacks);
                view.bind((MdBlock.Quote) block);
                return view;
            }
            case LIST: {
                MarkdownListBlockView view = new MarkdownListBlockView(theme, text);
                view.bind((MdBlock.ListBlock) block);
                return view;
            }
            case TABLE: {
                MarkdownTableBlockView view = new MarkdownTableBlockView(theme, text);
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
                ((android.widget.TextView) view).setText(
                        text.spannable(((MdBlock.Paragraph) block).spans()));
                return true;
            case HEADING:
                ((android.widget.TextView) view).setText(
                        text.spannable(((MdBlock.Heading) block).spans()));
                return true;
            case CODE:
                ((MarkdownCodeBlockView) view).bind((MdBlock.Code) block);
                return true;
            case QUOTE:
                ((MarkdownQuoteBlockView) view).bind((MdBlock.Quote) block);
                return true;
            case LIST:
                ((MarkdownListBlockView) view).bind((MdBlock.ListBlock) block);
                return true;
            case TABLE:
                ((MarkdownTableBlockView) view).bind((MdBlock.Table) block);
                return true;
            default:
                return false;
        }
    }

    private android.widget.TextView paragraph(MdBlock.Paragraph block) {
        return text.paragraph(block);
    }

}
