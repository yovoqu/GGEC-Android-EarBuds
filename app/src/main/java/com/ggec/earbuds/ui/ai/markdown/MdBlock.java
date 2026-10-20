package com.ggec.earbuds.ui.ai.markdown;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * 块级结构。每种块是一个值类型，渲染器靠值相等判断一个块有没有变化，流式输出时只重绘变化的那一块。
 */
public interface MdBlock {

    enum Type {
        PARAGRAPH,
        HEADING,
        CODE,
        QUOTE,
        LIST,
        RULE,
        TABLE
    }

    Type type();

    record Paragraph(List<MdSpan> spans) implements MdBlock {
        public Paragraph {
            spans = List.copyOf(spans);
        }

        @Override
        public Type type() {
            return Type.PARAGRAPH;
        }
    }

    record Heading(int level, List<MdSpan> spans) implements MdBlock {
        public Heading {
            spans = List.copyOf(spans);
        }

        @Override
        public Type type() {
            return Type.HEADING;
        }
    }

    /** {@code complete} 为假表示围栏还没闭合，流式输出到一半。 */
    record Code(@Nullable String language, String text, boolean complete) implements MdBlock {
        @Override
        public Type type() {
            return Type.CODE;
        }
    }

    record Quote(List<MdBlock> blocks) implements MdBlock {
        public Quote {
            blocks = List.copyOf(blocks);
        }

        @Override
        public Type type() {
            return Type.QUOTE;
        }
    }

    record ListItem(int level, boolean ordered, int number, @Nullable Boolean checked, List<MdSpan> spans) {
        public ListItem {
            spans = List.copyOf(spans);
        }
    }

    record ListBlock(List<ListItem> items) implements MdBlock {
        public ListBlock {
            items = List.copyOf(items);
        }

        @Override
        public Type type() {
            return Type.LIST;
        }
    }

    record Rule() implements MdBlock {
        @Override
        public Type type() {
            return Type.RULE;
        }
    }

    enum Align {
        START,
        CENTER,
        END
    }

    record Table(List<Align> aligns, List<List<MdSpan>> header, List<List<List<MdSpan>>> rows) implements MdBlock {
        public Table {
            aligns = List.copyOf(aligns);
            header = List.copyOf(header);
            rows = List.copyOf(rows);
        }

        @Override
        public Type type() {
            return Type.TABLE;
        }
    }
}
