package com.ggec.earbuds.ui.ai.markdown;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 行内语法：转义、行内代码、链接与图片、自动链接、粗体斜体删除线。
 * 只实现模型输出里真正常见的那部分 CommonMark，匹配不上的符号一律当普通字符，
 * 所以流式输出到一半（比如 {@code **} 还没闭合）时文字是稳定的，不会闪烁成别的样子。
 */
final class InlineParser {

    private static final String ESCAPABLE = "\\`*_{}[]()#+-.!|~<>";

    List<MdSpan> parse(String text) {
        List<MdSpan> out = new ArrayList<>();
        String normalized = text.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n");
        parse(normalized, 0, null, out);
        return merge(out);
    }

    private void parse(String s, int flags, @Nullable String url, List<MdSpan> out) {
        StringBuilder plain = new StringBuilder();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n && ESCAPABLE.indexOf(s.charAt(i + 1)) >= 0) {
                plain.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '`') {
                int run = runLength(s, i, '`');
                int close = findBacktickRun(s, i + run, run);
                if (close >= 0) {
                    flush(plain, flags, url, out);
                    out.add(new MdSpan(codeText(s.substring(i + run, close)), flags | MdSpan.CODE, url));
                    i = close + run;
                } else {
                    plain.append(s, i, i + run);
                    i += run;
                }
                continue;
            }
            if (c == '!' && i + 1 < n && s.charAt(i + 1) == '[') {
                Link image = link(s, i + 1);
                if (image != null) {
                    flush(plain, flags, url, out);
                    String label = image.label.isBlank() ? image.url : image.label;
                    parse(label, flags | MdSpan.LINK, image.url, out);
                    i = image.end;
                    continue;
                }
            }
            if (c == '[') {
                Link link = link(s, i);
                if (link != null) {
                    flush(plain, flags, url, out);
                    parse(link.label, flags | MdSpan.LINK, link.url, out);
                    i = link.end;
                    continue;
                }
            }
            if (c == '<') {
                int close = autolinkEnd(s, i);
                if (close > 0) {
                    flush(plain, flags, url, out);
                    String target = s.substring(i + 1, close);
                    out.add(new MdSpan(target, flags | MdSpan.LINK, target));
                    i = close + 1;
                    continue;
                }
            }
            if (c == 'h' && url == null && startsWithUrl(s, i) && (i == 0 || !isWordChar(s.charAt(i - 1)))) {
                int end = urlEnd(s, i);
                flush(plain, flags, url, out);
                String target = s.substring(i, end);
                out.add(new MdSpan(target, flags | MdSpan.LINK, target));
                i = end;
                continue;
            }
            if (c == '*' || c == '_' || c == '~') {
                Emphasis emphasis = emphasis(s, i, c);
                if (emphasis != null) {
                    flush(plain, flags, url, out);
                    parse(emphasis.inner, flags | emphasis.flag, url, out);
                    i = emphasis.end;
                    continue;
                }
            }
            plain.append(c);
            i++;
        }
        flush(plain, flags, url, out);
    }

    private static void flush(StringBuilder plain, int flags, @Nullable String url, List<MdSpan> out) {
        if (plain.length() > 0) {
            out.add(new MdSpan(plain.toString(), flags, url));
            plain.setLength(0);
        }
    }

    private static List<MdSpan> merge(List<MdSpan> spans) {
        List<MdSpan> merged = new ArrayList<>();
        for (MdSpan span : spans) {
            if (span.text().isEmpty()) {
                continue;
            }
            MdSpan last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && last.style() == span.style() && Objects.equals(last.url(), span.url())) {
                merged.set(merged.size() - 1, new MdSpan(last.text() + span.text(), last.style(), last.url()));
            } else {
                merged.add(span);
            }
        }
        return merged;
    }

    private static String codeText(String raw) {
        if (raw.length() >= 2 && raw.startsWith(" ") && raw.endsWith(" ") && !raw.isBlank()) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static int runLength(String s, int from, char c) {
        int end = from;
        while (end < s.length() && s.charAt(end) == c) {
            end++;
        }
        return end - from;
    }

    /** 找到与开头等长的反引号串，返回它的起点；没有则 -1。 */
    private static int findBacktickRun(String s, int from, int length) {
        int i = from;
        while (i < s.length()) {
            if (s.charAt(i) == '`') {
                int run = runLength(s, i, '`');
                if (run == length) {
                    return i;
                }
                i += run;
            } else {
                i++;
            }
        }
        return -1;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }

    private static final class Emphasis {
        final String inner;
        final int flag;
        final int end;

        Emphasis(String inner, int flag, int end) {
            this.inner = inner;
            this.flag = flag;
            this.end = end;
        }
    }

    @Nullable
    private static Emphasis emphasis(String s, int i, char c) {
        int run = runLength(s, i, c);
        int length;
        int flag;
        if (c == '~') {
            if (run < 2) {
                return null;
            }
            length = 2;
            flag = MdSpan.STRIKE;
        } else {
            length = Math.min(run, 3);
            flag = length == 1 ? MdSpan.ITALIC : length == 2 ? MdSpan.BOLD : MdSpan.BOLD | MdSpan.ITALIC;
        }
        int start = i + length;
        if (start >= s.length() || Character.isWhitespace(s.charAt(start))) {
            return null;
        }
        if (c == '_' && i > 0 && isWordChar(s.charAt(i - 1))) {
            return null;
        }
        int from = start;
        while (from < s.length()) {
            int close = closing(s, from, c, length);
            if (close < 0) {
                return null;
            }
            boolean flanked = close > start && !Character.isWhitespace(s.charAt(close - 1));
            boolean intraword = c == '_' && close + length < s.length() && isWordChar(s.charAt(close + length));
            if (flanked && !intraword) {
                return new Emphasis(s.substring(start, close), flag, close + length);
            }
            from = close + 1;
        }
        return null;
    }

    /**
     * 从 {@code from} 起找闭合分隔符。代码片段和转义里的符号不算；找单个星号时整串多星号视为嵌套的粗体，跳过。
     */
    private static int closing(String s, int from, char c, int length) {
        int i = from;
        while (i < s.length()) {
            char current = s.charAt(i);
            if (current == '\\') {
                i += 2;
            } else if (current == '`') {
                int run = runLength(s, i, '`');
                int close = findBacktickRun(s, i + run, run);
                i = close >= 0 ? close + run : i + run;
            } else if (current == c) {
                int run = runLength(s, i, c);
                if (run == length || (length == 3 && run > 3)) {
                    return i;
                }
                if (length == 2 && run == 3) {
                    return i + 1;
                }
                i += run;
            } else {
                i++;
            }
        }
        return -1;
    }

    private static final class Link {
        final String label;
        final String url;
        final int end;

        Link(String label, String url, int end) {
            this.label = label;
            this.url = url;
            this.end = end;
        }
    }

    /** {@code s[open]} 是 '['。要求紧跟 "](地址)"，地址里的括号允许成对出现。 */
    @Nullable
    private static Link link(String s, int open) {
        int depth = 0;
        int i = open;
        int closeBracket = -1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    closeBracket = i;
                    break;
                }
            }
            i++;
        }
        if (closeBracket < 0 || closeBracket + 1 >= s.length() || s.charAt(closeBracket + 1) != '(') {
            return null;
        }
        int parens = 1;
        int j = closeBracket + 2;
        while (j < s.length() && parens > 0) {
            char c = s.charAt(j);
            if (c == '(') {
                parens++;
            } else if (c == ')') {
                parens--;
            }
            j++;
        }
        if (parens != 0) {
            return null;
        }
        String target = s.substring(closeBracket + 2, j - 1).strip();
        int space = target.indexOf(' ');
        if (space > 0) {
            target = target.substring(0, space);
        }
        if (target.startsWith("<") && target.endsWith(">") && target.length() > 2) {
            target = target.substring(1, target.length() - 1);
        }
        if (target.isEmpty()) {
            return null;
        }
        return new Link(s.substring(open + 1, closeBracket), target, j);
    }

    private static int autolinkEnd(String s, int open) {
        if (!s.startsWith("<http://", open) && !s.startsWith("<https://", open) && !s.startsWith("<mailto:", open)) {
            return -1;
        }
        int close = s.indexOf('>', open);
        if (close < 0) {
            return -1;
        }
        String body = s.substring(open + 1, close);
        return body.indexOf(' ') >= 0 || body.indexOf('\n') >= 0 ? -1 : close;
    }

    private static boolean startsWithUrl(String s, int i) {
        return s.startsWith("http://", i) || s.startsWith("https://", i);
    }

    /** 地址读到空白为止，再去掉句末标点和不成对的右括号。 */
    private static int urlEnd(String s, int start) {
        int end = start;
        while (end < s.length() && !Character.isWhitespace(s.charAt(end)) && "<>\"`".indexOf(s.charAt(end)) < 0) {
            end++;
        }
        while (end > start) {
            char last = s.charAt(end - 1);
            if (".,;:!?'\"".indexOf(last) >= 0) {
                end--;
            } else if (last == ')' && count(s, start, end, '(') < count(s, start, end, ')')) {
                end--;
            } else if (last == ']' || last == '}') {
                end--;
            } else {
                break;
            }
        }
        return end;
    }

    private static int count(String s, int from, int to, char c) {
        int count = 0;
        for (int i = from; i < to; i++) {
            if (s.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }
}
