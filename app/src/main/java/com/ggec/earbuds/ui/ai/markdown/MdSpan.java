package com.ggec.earbuds.ui.ai.markdown;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 一段行内文字及其样式。样式是位标志，可以叠加（粗体加斜体）；链接的目标放在 {@code url}。
 */
public record MdSpan(String text, int style, @Nullable String url) {

    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int CODE = 4;
    public static final int STRIKE = 8;
    public static final int LINK = 16;

    public MdSpan {
        Objects.requireNonNull(text, "text");
    }

    public boolean has(int flag) {
        return (style & flag) != 0;
    }
}
