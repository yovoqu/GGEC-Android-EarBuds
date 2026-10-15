package com.yovo.yotheme.color;

import androidx.annotation.NonNull;

/**
 * yotheme 对外公开的主题门面：
 * <ul>
 *     <li>{@link #resolve(YoThemeColor)}：把颜色令牌解析为颜色整型值；</li>
 *     <li>{@link #setBrandColor(YoThemeColor)} / {@link #resetBrandColor()}：
 *     运行时品牌色覆盖（主题定制入口）。</li>
 * </ul>
 *
 * <p>模块外（app / 业务 SDK）一律通过本类使用主题体系，
 * 禁止直接引用内部 {@link ColorMapper} / {@link YoColors}。</p>
 */
public final class YoTheme {

    private YoTheme() {
    }

    /** 将公共颜色令牌解析为颜色整型值；null 返回默认色。 */
    public static int resolve(@NonNull YoThemeColor color) {
        return ColorMapper.map(color);
    }

    /** 覆盖品牌色（主题定制入口）。 */
    public static void setBrandColor(@NonNull YoThemeColor color) {
        ColorMapper.setBrandColor(ColorMapper.map(color));
    }

    /** 恢复默认品牌色。 */
    public static void resetBrandColor() {
        ColorMapper.resetBrandColor();
    }
}
