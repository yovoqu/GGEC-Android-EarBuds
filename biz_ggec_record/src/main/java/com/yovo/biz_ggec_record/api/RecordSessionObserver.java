package com.yovo.biz_ggec_record.api;

import androidx.annotation.NonNull;

import com.yovo.biz_ggec_record.model.RecordLinkStats;

/**
 * 音视频会话事件。主线程派发。
 */
public interface RecordSessionObserver {

    default void onLinkStats(@NonNull RecordLinkStats stats) {
    }

    default void onSessionError(@NonNull String message) {
    }

    /** @param path 绝对路径 */
    default void onRecordingStarted(@NonNull String path) {
    }

    /** @param path 绝对路径 */
    default void onRecordingFinished(@NonNull String path, long bytes, long durationMs) {
    }

    default void onRecordingFailed(@NonNull String message) {
    }
}