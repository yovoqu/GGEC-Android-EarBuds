package com.yovo.lib_ai.models.spec;

/**
 * 对"支持不支持"的三态判断。
 *
 * <p>为什么不是布尔：平台没说过，不等于模型不支持。{@link #UNKNOWN} 与 {@link #NO} 必须分开，
 * 否则"平台只回了 id"会被当成"这个模型什么都不会"，把能用的模型挡在路由之外；
 * 反过来，平台或用户明确说过"不支持"的，也要能压过名字上看起来像支持的推断。</p>
 */
public enum YoSupport {
    /** 没人表过态。 */
    UNKNOWN,
    /** 明确不支持。 */
    NO,
    /** 明确支持。 */
    YES;

    public static YoSupport of(boolean supported) {
        return supported ? YES : NO;
    }
}
