package com.yovo.biz_ggec_hs.internal.center.protocol;

/** 一条录音文件记录。字段来自协议帧，还不是对外契约。 */
public record ProtocolRecording(String fileId, String name, int durationSeconds, long sizeBytes) {
}
