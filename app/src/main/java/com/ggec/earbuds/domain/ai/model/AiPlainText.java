package com.ggec.earbuds.domain.ai.model;

import java.util.regex.Pattern;

/**
 * 把 Markdown 压成"能塞进一行的地方"用的纯文本。
 *
 * <p>为什么需要：历史列表的摘要只有一行位置，直接显示助手原文会出现
 * {@code ## 耳机连接问题排查 **请按以下…} 这种记号；标题也会把 `#`、`**` 算进字数，
 * 截断后更难认。参考成品聊天应用的做法，列表里只留语义文字，记号留给正文渲染。</p>
 *
 * <p>这不是解析器：只处理"出现在行首或成对出现"的那几类记号，认不出来的原样保留，
 * 宁可少删也不要把正文吃掉。</p>
 */
public final class AiPlainText {

    private static final Pattern FENCED_CODE = Pattern.compile("(?s)```.*?```");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s*");
    private static final Pattern QUOTE = Pattern.compile("(?m)^\\s{0,3}>\\s?");
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s{0,3}(?:[-*+]|\\d{1,3}[.)])\\s+");
    private static final Pattern TABLE_RULE = Pattern.compile("(?m)^\\s*\\|?[\\s:|-]+\\|[\\s:|-]*$");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|\\*|_|~~|`)");
    private static final Pattern TABLE_PIPE = Pattern.compile("\\s*\\|\\s*");
    private static final Pattern BLANKS = Pattern.compile("\\s+");

    private AiPlainText() {
    }

    /**
     * @return 去掉记号、压平空白后的一行文字；输入为空返回空串。
     */
    public static String of(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String text = FENCED_CODE.matcher(markdown).replaceAll(" ");
        text = IMAGE.matcher(text).replaceAll("$1");
        text = LINK.matcher(text).replaceAll("$1");
        text = TABLE_RULE.matcher(text).replaceAll(" ");
        text = HEADING.matcher(text).replaceAll("");
        text = QUOTE.matcher(text).replaceAll("");
        text = LIST_MARKER.matcher(text).replaceAll("");
        text = TABLE_PIPE.matcher(text).replaceAll(" ");
        text = EMPHASIS.matcher(text).replaceAll("");
        return BLANKS.matcher(text).replaceAll(" ").strip();
    }

    /**
     * 截到指定码点数，超出加省略号。按码点切，避免把 emoji 劈成半个。
     */
    public static String truncate(String text, int maxCodePoints) {
        if (text == null) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= maxCodePoints) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxCodePoints)).strip() + "…";
    }
}
