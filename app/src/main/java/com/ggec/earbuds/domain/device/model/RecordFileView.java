package com.ggec.earbuds.domain.device.model;

import java.util.Objects;

/** 设备本地录音文件的一条记录。 */
public record RecordFileView(String fileId, String name, int durationSeconds, long sizeBytes) {

    public RecordFileView {
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(name, "name");
    }
}
