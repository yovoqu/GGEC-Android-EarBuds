package com.ggec.earbuds.domain.record.model;

/**
 * 端侧 ASR 模型选择（APP 呈现词汇，与 biz {@code RecordAsrModel} 由 provider 一一映射）。
 */
public enum RecordAsrModel {
    ZIPFORMER_SMALL,
    PARAFORMER
}
