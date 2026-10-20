package com.ggec.earbuds.ui.ai.markdown;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 模型回答里的链接只能由用户主动点开，并且只放行网页和邮件：
 * javascript:、intent:、file:、content: 之类的地址一律不打开。
 */
public final class MdLinks {

    private MdLinks() {
    }

    public static boolean isSafe(@Nullable String url) {
        if (url == null) {
            return false;
        }
        String lower = url.strip().toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return lower.length() > lower.indexOf("//") + 2 && !hasControlCharacters(lower);
        }
        return lower.startsWith("mailto:") && lower.length() > "mailto:".length() && !hasControlCharacters(lower);
    }

    private static boolean hasControlCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i)) || Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
