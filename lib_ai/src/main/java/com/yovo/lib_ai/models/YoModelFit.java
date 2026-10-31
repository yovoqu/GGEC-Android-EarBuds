package com.yovo.lib_ai.models;

/**
 * 一个模型满不满足一次调用的要求，以及这个结论有多可靠。
 *
 * <p>分两档是为了守住"看起来支持、实际 400"这条防线：{@link #INFERRED} 只是名字上看着行，
 * 路由在有 {@link #CONFIRMED} 的模型时永远先选后者，界面也应该把前者显示成待确认。</p>
 */
public enum YoModelFit {
    /** 不满足：类别不对，或某项必需的模态、能力、推理明确没有（或根本没人说过），或体量不够。 */
    REJECTED,
    /** 满足，但至少有一条依据只是按 id 推断出来的。 */
    INFERRED,
    /** 满足，每条依据都来自平台上报或用户指定。 */
    CONFIRMED;

    /** 满足（不论可靠程度）。 */
    public boolean accepted() {
        return this != REJECTED;
    }
}
