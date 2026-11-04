package com.ggec.earbuds.ui.device.settings;

/**
 * 设置项语义 ID：只属于界面意图层，View 用它表达“用户点了哪一项”，
 * 由 ViewModel 翻译成领域操作，View 不依赖任何领域枚举。
 */
public enum SettingId {
    // 控制 - 手势
    LONG,
    LONG_LEFT,
    LONG_RIGHT,
    SHORT,
    SHORT_LEFT,
    SHORT_RIGHT,
    DOUBLE_LEFT,
    DOUBLE_RIGHT,
    TAP_TWICE,
    TAP_TRIPLE,
    TAP_TWICE_LEFT,
    TAP_TRIPLE_LEFT,
    TAP_TWICE_RIGHT,
    TAP_TRIPLE_RIGHT,
    RESET_CONTROL,
    // 功能
    WEAR_DETECT,
    VOICE_WAKEUP,
    GAME_MODE,
    FALL_ALERT,
    VOLUME_REMIND,
    BEGINNER_MODE,
    INTERPRET,
    FIND_LEFT,
    FIND_RIGHT,
    FIND_STOP,
    // 双连接
    DUAL_CONNECTION,
    // 录音
    RECORD_START,
    RECORD_STOP,
    RECORD_SYNC,
    // 关于设备
    CLEAR_PAIRING
}
