package com.yovo.yotheme.dialog.api.option;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 选项列表选择的公共模式
 */
public enum OptionSelectionMode {
    /** 无选择控件。 */
    NONE,
    /** 单行点击后立即提交的单选。 */
    SINGLE_IMMEDIATE,
    /** 通过确认操作提交的单选。 */
    SINGLE_CONFIRM,
    /** 通过确认操作提交的多选。 */
    MULTI_CONFIRM
}


