package com.yovo.biz_ggec_record.model;

import androidx.annotation.NonNull;

/**
 * 随库打包的端侧 ASR 模型。路径相对 library assets。
 */
public enum RecordAsrModel {
    ZIPFORMER_SMALL(
            "Zipformer Small 中英",
            "models/sherpa-onnx-streaming-zipformer-small-bilingual-zh-en-2023-02-16"),
    PARAFORMER(
            "Paraformer 中英",
            "models/sherpa-onnx-streaming-paraformer-bilingual-zh-en");

    private final String displayName;
    private final String assetsDir;

    RecordAsrModel(@NonNull String displayName, @NonNull String assetsDir) {
        this.displayName = displayName;
        this.assetsDir = assetsDir;
    }

    @NonNull
    public String displayName() {
        return displayName;
    }

    @NonNull
    public String assetsDir() {
        return assetsDir;
    }
}
