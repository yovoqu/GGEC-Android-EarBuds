package com.yovo.lib_ai.models.spec;

/**
 * 一条事实是谁说的。界面那句"支持看图"必须有出处：
 * 只有 {@link #INFERRED} 的事实在界面上要显示成待确认，用户勾选后才升为 {@link #USER}。
 *
 * <p>声明顺序就是优先级，后面的盖过前面的；同一级里后写的盖过先写的。
 * SDK 不内置任何厂商模型清单，所以没有"内置表"这一档：除了平台上报和用户明确指定，
 * 其余全是按 id 词元做的推断。</p>
 */
public enum YoSpecSource {
    /** 按 id 词元猜出来的提示，不是事实。 */
    INFERRED,
    /** 平台自己在模型列表里声明的。 */
    REPORTED,
    /** 用户手动确认或指定的，永久优先。 */
    USER;

    /**
     * 够不够在界面上直接断言。推断出来的只能当提示。
     */
    public boolean trusted() {
        return this != INFERRED;
    }

    /**
     * 这个出处的话能不能盖过 {@code other} 已经说过的话（同级算能，后写的为准）。
     */
    public boolean overrides(YoSpecSource other) {
        return ordinal() >= other.ordinal();
    }

    /**
     * 两个出处里更靠谱的那个。
     */
    public static YoSpecSource stronger(YoSpecSource a, YoSpecSource b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
