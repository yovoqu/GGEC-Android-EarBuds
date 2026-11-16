package com.ggec.earbuds.domain.device.model;

/**
 * 耳机录音业务模式。
 */
public enum RecordMode {
    /** 随录随传：耳机边录边回传 Opus 音频数据至 App */
    STREAM,
    /** 本地录音：耳机在端侧 Flash 录音，手动同步时才传输音频文件 */
    LOCAL
}
