package com.ggec.earbuds.ui.device.record;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.record.RecordState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 录音页面的渲染状态：{@link RecordUiMapper} 从 {@link RecordState} 纯派生的投影。
 *
 * <p>本记录不含任何「界面自己的事实」——没有未确认值、没有加载中标志、没有默认值填充：
 * 每个字段都能在领域状态里找到同名来源，界面只做「渲染 + 转发意图」。</p>
 */
public record RecordUiState(
        @NonNull RecordMode mode,
        @NonNull Set<RecordMode> supportedModes,
        boolean connected,
        @NonNull RecordState.StreamPhase streamPhase,
        long streamDurationMs,
        float streamAmplitude,
        @NonNull RecordState.DeviceRecordPhase deviceRecordPhase,
        long deviceRecordDurationMs,
        @NonNull RecordState.SyncPhase syncPhase,
        @NonNull Optional<String> syncingFileId,
        int syncPercent,
        long syncTransferredBytes,
        long syncTotalBytes,
        @NonNull List<SavedRecordItem> localFiles,
        @NonNull List<RecordFileView> deviceFiles,
        @NonNull Optional<String> playingFile) {

    public RecordUiState {
        Objects.requireNonNull(mode, "mode");
        supportedModes = Set.copyOf(Objects.requireNonNull(supportedModes, "supportedModes"));
        Objects.requireNonNull(streamPhase, "streamPhase");
        Objects.requireNonNull(deviceRecordPhase, "deviceRecordPhase");
        Objects.requireNonNull(syncPhase, "syncPhase");
        syncingFileId = Objects.requireNonNull(syncingFileId, "syncingFileId");
        localFiles = List.copyOf(Objects.requireNonNull(localFiles, "localFiles"));
        deviceFiles = List.copyOf(Objects.requireNonNull(deviceFiles, "deviceFiles"));
        playingFile = Objects.requireNonNull(playingFile, "playingFile");
    }

    /** 该模式此刻是否可发起：已连接且能力目录给出这个模式。空集 → 恒为 false（禁用态）。 */
    public boolean canRecord(@NonNull RecordMode target) {
        return connected && supportedModes.contains(target);
    }

    /** 只有一个（或零个）可用模式时，段落控制器没有意义。 */
    public boolean singleMode() {
        return supportedModes.size() <= 1;
    }

    public boolean streamActive() {
        return streamPhase != RecordState.StreamPhase.IDLE;
    }

    public boolean deviceRecordActive() {
        return deviceRecordPhase != RecordState.DeviceRecordPhase.IDLE;
    }

    public boolean syncing() {
        return syncPhase == RecordState.SyncPhase.IN_FLIGHT;
    }

    public boolean playing(@NonNull String filePath) {
        return playingFile.equals(Optional.of(filePath));
    }
}
