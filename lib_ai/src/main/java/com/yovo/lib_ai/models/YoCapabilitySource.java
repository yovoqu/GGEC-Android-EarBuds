package com.yovo.lib_ai.models;

/**
 * 能力是从哪来的。界面那句"支持看图"必须有出处：
 * {@link #INFERRED} 的模型在设置页显示成待确认，用户勾选后才升为 {@link #USER}。
 *
 * <p>没有 {@code DECLARED}：SDK 不再内置厂商模型清单，除了用户明确勾选，
 * 任何能力都只能算推断。</p>
 */
public enum YoCapabilitySource {
    /** 按 id 词元推断出来的。 */
    INFERRED,
    /** 用户在设置页手动勾选，永久优先。 */
    USER;

    /**
     * 够不够在界面上直接断言"支持这个能力"。推断出来的只能当提示。
     */
    public boolean trusted() {
        return this == USER;
    }
}
