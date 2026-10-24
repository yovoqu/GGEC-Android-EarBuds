package com.yovo.lib_ai.models;

/**
 * 能力是从哪来的。界面那句"支持看图"必须有出处：
 * {@link #INFERRED} 的模型在设置页显示成待确认，用户勾选后才升为 {@link #USER}。
 */
public enum YoCapabilitySource {
    /** 内置表按 family + id 精确声明。 */
    DECLARED,
    /** 内置表没有命中，按 id 词元推断出来的。 */
    INFERRED,
    /** 用户在设置页手动勾选，永久优先。 */
    USER;

    /**
     * 够不够在界面上直接断言"支持这个能力"。推断出来的只能当提示。
     */
    public boolean trusted() {
        return this != INFERRED;
    }
}
