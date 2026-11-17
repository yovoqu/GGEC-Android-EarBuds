package com.ggec.earbuds.ui.device.record;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 录音页面整体 UI 状态模型。
 */
public record RecordUiState(
        @NonNull RecordMode mode,
        @NonNull Set<RecordMode> supportedModes,
        boolean isConnected,
        @NonNull String deviceName,
        boolean isStreamRecording,
        boolean isStreamDataReceiving,
        long streamDurationMs,
        float streamAmplitude,
        @NonNull List<SavedRecordItem> localFiles,
        @Nullable String playingFilePath,
        @NonNull List<RecordFileView> deviceFiles,
        boolean isDeviceRecording,
        long deviceRecordDurationMs,
        boolean isRefreshingDevice,
        boolean isSyncing,
        @Nullable String syncingFileId,
        int syncProgressPercent,
        long syncTransferredBytes,
        long syncTotalBytes
) {
    public RecordUiState {
        Objects.requireNonNull(mode, "mode");
        supportedModes = Set.copyOf(Objects.requireNonNull(supportedModes, "supportedModes"));
        Objects.requireNonNull(deviceName, "deviceName");
        localFiles = List.copyOf(Objects.requireNonNull(localFiles, "localFiles"));
        deviceFiles = List.copyOf(Objects.requireNonNull(deviceFiles, "deviceFiles"));
    }

    public static RecordUiState initial() {
        return new RecordUiState(
                RecordMode.STREAM,
                Set.of(RecordMode.STREAM, RecordMode.LOCAL),
                false,
                "",
                false,
                false,
                0L,
                0f,
                List.of(),
                null,
                List.of(),
                false,
                0L,
                false,
                false,
                null,
                0,
                0L,
                0L
        );
    }
}
