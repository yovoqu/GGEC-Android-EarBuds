package com.ggec.earbuds.domain.ai.policy;

/**
 * 用第一条用户消息给对话起标题：压缩空白，超长截断加省略号。内容为空返回空串，由界面显示默认名。
 */
public final class AiTitlePolicy {

    static final int MAX_CODE_POINTS = 24;
    static final int MAX_RENAMED_CODE_POINTS = 40;

    public String titleFor(String firstUserText) {
        String collapsed = collapse(firstUserText);
        if (collapsed.isEmpty()) {
            return "";
        }
        if (collapsed.codePointCount(0, collapsed.length()) <= MAX_CODE_POINTS) {
            return collapsed;
        }
        int end = collapsed.offsetByCodePoints(0, MAX_CODE_POINTS);
        return collapsed.substring(0, end).strip() + "…";
    }

    /**
     * 用户手动改名：同样压缩空白，但不加省略号，直接截到上限。
     */
    public String renamed(String typed) {
        String collapsed = collapse(typed);
        if (collapsed.codePointCount(0, collapsed.length()) <= MAX_RENAMED_CODE_POINTS) {
            return collapsed;
        }
        return collapsed.substring(0, collapsed.offsetByCodePoints(0, MAX_RENAMED_CODE_POINTS)).strip();
    }

    private static String collapse(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }
}
