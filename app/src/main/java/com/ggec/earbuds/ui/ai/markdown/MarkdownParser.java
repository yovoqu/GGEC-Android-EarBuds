package com.ggec.earbuds.ui.ai.markdown;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把模型输出的 Markdown 解析成块。覆盖聊天里实际会出现的部分：段落、标题、围栏代码、引用、
 * 有序/无序/任务列表（含缩进嵌套）、分隔线、表格。每次输入变化都从头解析整段文字，
 * 解析是线性的而且没有状态，所以流式输出到任意位置都能得到稳定的结果；还没闭合的围栏按"代码块进行中"处理。
 */
public final class MarkdownParser {

    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})\\s*([^`\\s]*)[^`]*$");
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.*?)(?:\\s+#+\\s*)?$");
    private static final Pattern RULE = Pattern.compile("^ {0,3}([-*_])(?:\\s*\\1){2,}\\s*$");
    private static final Pattern QUOTE = Pattern.compile("^ {0,3}>\\s?(.*)$");
    private static final Pattern LIST = Pattern.compile("^([ \\t]*)([-*+]|\\d{1,9}[.)])[ \\t]+(.*)$");
    private static final Pattern TASK = Pattern.compile("^\\[([ xX])\\][ \\t]+(.*)$");
    private static final Pattern TABLE_DELIMITER =
            Pattern.compile("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$");

    private final InlineParser inline = new InlineParser();

    public List<MdBlock> parse(@Nullable String source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        return blocks(Arrays.asList(normalized.split("\n", -1)));
    }

    /** 行内样式也对外开放，设置页之类需要渲染一小段文字时使用。 */
    public List<MdSpan> parseInline(String text) {
        return inline.parse(text);
    }

    private List<MdBlock> blocks(List<String> lines) {
        List<MdBlock> blocks = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank()) {
                i++;
                continue;
            }
            Matcher fence = FENCE.matcher(line);
            if (fence.matches()) {
                i = code(lines, i, fence, blocks);
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                blocks.add(new MdBlock.Heading(heading.group(1).length(), inline.parse(heading.group(2).strip())));
                i++;
                continue;
            }
            if (RULE.matcher(line).matches()) {
                blocks.add(new MdBlock.Rule());
                i++;
                continue;
            }
            if (QUOTE.matcher(line).matches()) {
                i = quote(lines, i, blocks);
                continue;
            }
            if (tableStarts(lines, i)) {
                i = table(lines, i, blocks);
                continue;
            }
            if (LIST.matcher(line).matches()) {
                i = list(lines, i, blocks);
                continue;
            }
            i = paragraph(lines, i, blocks);
        }
        return blocks;
    }

    private int code(List<String> lines, int start, Matcher opening, List<MdBlock> out) {
        String marker = opening.group(1);
        String language = opening.group(2).isEmpty() ? null : opening.group(2);
        StringBuilder text = new StringBuilder();
        int i = start + 1;
        boolean closed = false;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (closesFence(line, marker)) {
                closed = true;
                i++;
                break;
            }
            if (text.length() > 0 || i > start + 1) {
                text.append('\n');
            }
            text.append(line);
            i++;
        }
        out.add(new MdBlock.Code(language, text.toString(), closed));
        return i;
    }

    private static boolean closesFence(String line, String marker) {
        String stripped = line.strip();
        if (stripped.length() < marker.length()) {
            return false;
        }
        for (int k = 0; k < stripped.length(); k++) {
            if (stripped.charAt(k) != marker.charAt(0)) {
                return false;
            }
        }
        return true;
    }

    private int quote(List<String> lines, int start, List<MdBlock> out) {
        List<String> inner = new ArrayList<>();
        int i = start;
        while (i < lines.size()) {
            Matcher quote = QUOTE.matcher(lines.get(i));
            if (quote.matches()) {
                inner.add(quote.group(1));
            } else if (!lines.get(i).isBlank() && !inner.isEmpty() && !startsBlock(lines, i)) {
                inner.add(lines.get(i));
            } else {
                break;
            }
            i++;
        }
        out.add(new MdBlock.Quote(blocks(inner)));
        return i;
    }

    private int paragraph(List<String> lines, int start, List<MdBlock> out) {
        StringBuilder text = new StringBuilder();
        int i = start;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank() || (i > start && startsBlock(lines, i))) {
                break;
            }
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(line.strip());
            i++;
        }
        out.add(new MdBlock.Paragraph(inline.parse(text.toString())));
        return i;
    }

    /** 这一行是否开启一个新的块，用来打断段落。 */
    private boolean startsBlock(List<String> lines, int i) {
        String line = lines.get(i);
        return FENCE.matcher(line).matches()
                || HEADING.matcher(line).matches()
                || RULE.matcher(line).matches()
                || QUOTE.matcher(line).matches()
                || LIST.matcher(line).matches()
                || tableStarts(lines, i);
    }

    private static final class RawItem {
        final int level;
        final boolean ordered;
        final int number;
        final StringBuilder text;

        RawItem(int level, boolean ordered, int number, String text) {
            this.level = level;
            this.ordered = ordered;
            this.number = number;
            this.text = new StringBuilder(text);
        }
    }

    private int list(List<String> lines, int start, List<MdBlock> out) {
        List<RawItem> raw = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();
        int i = start;
        while (i < lines.size()) {
            String line = lines.get(i);
            Matcher item = LIST.matcher(line);
            if (item.matches() && !RULE.matcher(line).matches()) {
                int indent = indentWidth(item.group(1));
                while (!indents.isEmpty() && indent < indents.get(indents.size() - 1)) {
                    indents.remove(indents.size() - 1);
                }
                if (indents.isEmpty() || indent >= indents.get(indents.size() - 1) + 2) {
                    indents.add(indent);
                }
                String marker = item.group(2);
                boolean ordered = Character.isDigit(marker.charAt(0));
                int number = ordered ? parseNumber(marker) : 0;
                raw.add(new RawItem(indents.size() - 1, ordered, number, item.group(3)));
                i++;
            } else if (line.isBlank()) {
                int next = nextNonBlank(lines, i);
                if (next < lines.size() && continuesList(lines.get(next))) {
                    i = next;
                } else {
                    break;
                }
            } else if (!raw.isEmpty() && !startsBlock(lines, i)) {
                RawItem last = raw.get(raw.size() - 1);
                last.text.append('\n').append(line.strip());
                i++;
            } else {
                break;
            }
        }
        List<MdBlock.ListItem> items = new ArrayList<>();
        for (RawItem entry : raw) {
            String content = entry.text.toString();
            Boolean checked = null;
            Matcher task = TASK.matcher(content);
            if (task.matches()) {
                checked = !" ".equals(task.group(1));
                content = task.group(2);
            }
            items.add(new MdBlock.ListItem(entry.level, entry.ordered, entry.number, checked, inline.parse(content)));
        }
        out.add(new MdBlock.ListBlock(items));
        return i;
    }

    private static boolean continuesList(String line) {
        return LIST.matcher(line).matches() && !RULE.matcher(line).matches()
                || (indentWidth(leadingWhitespace(line)) >= 2 && !FENCE.matcher(line).matches());
    }

    private static int nextNonBlank(List<String> lines, int from) {
        int i = from;
        while (i < lines.size() && lines.get(i).isBlank()) {
            i++;
        }
        return i;
    }

    private static String leadingWhitespace(String line) {
        int end = 0;
        while (end < line.length() && (line.charAt(end) == ' ' || line.charAt(end) == '\t')) {
            end++;
        }
        return line.substring(0, end);
    }

    private static int indentWidth(String whitespace) {
        int width = 0;
        for (int k = 0; k < whitespace.length(); k++) {
            width += whitespace.charAt(k) == '\t' ? 4 : 1;
        }
        return width;
    }

    private static int parseNumber(String marker) {
        try {
            return Integer.parseInt(marker.substring(0, marker.length() - 1));
        } catch (NumberFormatException overflow) {
            return 1;
        }
    }

    private boolean tableStarts(List<String> lines, int i) {
        if (i + 1 >= lines.size() || lines.get(i).indexOf('|') < 0) {
            return false;
        }
        String delimiter = lines.get(i + 1);
        return delimiter.indexOf('-') >= 0 && TABLE_DELIMITER.matcher(delimiter).matches()
                && (delimiter.indexOf('|') >= 0 || lines.get(i).indexOf('|') >= 0)
                && cells(lines.get(i)).size() == cells(delimiter).size();
    }

    private int table(List<String> lines, int start, List<MdBlock> out) {
        List<List<MdSpan>> header = new ArrayList<>();
        for (String cell : cells(lines.get(start))) {
            header.add(inline.parse(cell));
        }
        List<MdBlock.Align> aligns = new ArrayList<>();
        for (String cell : cells(lines.get(start + 1))) {
            boolean left = cell.startsWith(":");
            boolean right = cell.endsWith(":");
            aligns.add(left && right ? MdBlock.Align.CENTER : right ? MdBlock.Align.END : MdBlock.Align.START);
        }
        List<List<List<MdSpan>>> rows = new ArrayList<>();
        int i = start + 2;
        while (i < lines.size() && !lines.get(i).isBlank() && lines.get(i).indexOf('|') >= 0) {
            List<List<MdSpan>> row = new ArrayList<>();
            List<String> rowCells = cells(lines.get(i));
            for (int column = 0; column < header.size(); column++) {
                row.add(column < rowCells.size() ? inline.parse(rowCells.get(column)) : List.of());
            }
            rows.add(row);
            i++;
        }
        out.add(new MdBlock.Table(aligns, header, rows));
        return i;
    }

    /** 按未转义的竖线切分一行，去掉首尾的竖线和每格的空白。 */
    private static List<String> cells(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        for (int k = 0; k < trimmed.length(); k++) {
            char c = trimmed.charAt(k);
            if (c == '\\' && k + 1 < trimmed.length() && trimmed.charAt(k + 1) == '|') {
                cell.append('|');
                k++;
            } else if (c == '|') {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().strip());
        return cells;
    }
}
