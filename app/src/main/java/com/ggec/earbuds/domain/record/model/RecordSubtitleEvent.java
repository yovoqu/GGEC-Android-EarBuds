package com.ggec.earbuds.domain.record.model;

/**
 * ASR 字幕事件（最终/临时）。
 */
public record RecordSubtitleEvent(String text, boolean finalResult) {
}
