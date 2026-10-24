package com.ggec.earbuds.domain.ai.policy;

import com.ggec.earbuds.domain.ai.model.AiPlainText;

/**
 * 用第一条用户消息给对话起标题：压缩空白，超长截断加省略号。内容为空返回空串，由界面显示默认名。
 */
public final class AiTitlePolicy {

    /**
     * 列表行的标题必须在一行内放得下：行首有勾/图标、行尾有时刻和「更多」，
     * 留给标题的宽度不到整行的八成。24 个码点时中文标题会折成两行，
     * 把居中的勾和右尾巴一起顶歪——所以收到 16。
     */
    static final int MAX_CODE_POINTS = 16;
    static final int MAX_RENAMED_CODE_POINTS = 40;

    public String titleFor(String firstUserText) {
        String collapsed = AiPlainText.of(firstUserText);
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
        return AiPlainText.of(text);
    }
}
