package com.ggec.earbuds.domain.record.port;

import com.ggec.earbuds.domain.record.model.RecordConnectionState;
import com.ggec.earbuds.domain.record.model.RecordLinkStats;
import com.ggec.earbuds.domain.record.model.RecordSubtitleEvent;

/**
 * Record 会话与 ASR 事件（领域侧观察者）。与 biz 两组 observer 由 provider 归并为单一回调面。
 */
public interface RecordObserver {

    default void onConnectionStateChanged(RecordConnectionState state) {
    }

    default void onConnectionFailed(String message) {
    }

    default void onLinkStats(RecordLinkStats stats) {
    }

    default void onSubtitle(RecordSubtitleEvent event) {
    }

    default void onSessionError(String message) {
    }

    default void onRecordingStarted(String path) {
    }

    default void onRecordingFinished(String path, long bytes, long durationMs) {
    }

    default void onRecordingFailed(String message) {
    }
}
