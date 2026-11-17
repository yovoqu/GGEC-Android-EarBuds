package com.ggec.earbuds.domain.device.record;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 耳机录音状态机的输入（design §4.2）。
 *
 * <p>两类事件走同一条通道：{@code *Requested} 是用户意图（ViewModel 只做转发），其余是端口回调的
 * 落地结果。事件只带分类与结构化载荷，不含任何用户文案；命令类结果的「成功 / 失败」是两个独立
 * 子类型，避免用可空字段或魔法值表达失败。</p>
 */
public sealed interface RecordEvent {

    // ---------------- 设备与模式 ----------------

    /** 设备就绪：能力目录给出的可用录音模式 + 设备当前上报的录音文件列表。 */
    record Connected(@NonNull Set<RecordMode> supported, @NonNull List<RecordFileView> deviceFiles)
            implements RecordEvent {

        public Connected {
            supported = Set.copyOf(Objects.requireNonNull(supported, "supported"));
            deviceFiles = List.copyOf(Objects.requireNonNull(deviceFiles, "deviceFiles"));
        }
    }

    /** 设备断开：所有与设备相关的进行中状态到此为止。 */
    record Disconnected() implements RecordEvent {
    }

    /**
     * 用户切换录音模式。
     *
     * <p>{@code supported} 为空集时这条事件得到一个显式的「不支持」结果，状态不变——
     * 旧实现在这里 {@code supported.iterator().next()} 直接崩。</p>
     */
    record ModeSelected(@NonNull RecordMode mode) implements RecordEvent {

        public ModeSelected {
            Objects.requireNonNull(mode, "mode");
        }
    }

    // ---------------- 随录随传 ----------------

    record StreamStartRequested() implements RecordEvent {
    }

    record StreamStopRequested() implements RecordEvent {
    }

    /** 端口上报的随录随传会话状态（时长由端口给出，状态机不读时钟）。 */
    record StreamState(@NonNull StreamAudioSessionState state) implements RecordEvent {

        public StreamState {
            Objects.requireNonNull(state, "state");
        }
    }

    /** 录音文件已落盘。 */
    record StreamSaved(@NonNull SavedRecordItem item) implements RecordEvent {

        public StreamSaved {
            Objects.requireNonNull(item, "item");
        }
    }

    /** 录音过程出错（端口分类）。 */
    record StreamFailed(@NonNull RecordFailure failure) implements RecordEvent {

        public StreamFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record StreamStopSucceeded() implements RecordEvent {
    }

    record StreamStopFailed(@NonNull RecordFailure failure) implements RecordEvent {

        public StreamStopFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    // ---------------- 耳机本地录音 ----------------

    record DeviceRecordStartRequested() implements RecordEvent {
    }

    /** 耳机已开始本地录制；{@code atMs} 是计时起点（由注入的时钟给出）。 */
    record DeviceRecordStarted(long atMs) implements RecordEvent {
    }

    record DeviceRecordStartFailed(@NonNull RecordFailure failure) implements RecordEvent {

        public DeviceRecordStartFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record DeviceRecordStopRequested() implements RecordEvent {
    }

    record DeviceRecordStopped() implements RecordEvent {
    }

    record DeviceRecordStopFailed(@NonNull RecordFailure failure) implements RecordEvent {

        public DeviceRecordStopFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    /** 定时投递：由 ViewModel 的 Ticker + 注入时钟产生，状态机据此推进已录时长。 */
    record DeviceRecordTicked(long nowMs) implements RecordEvent {
    }

    record RefreshRequested() implements RecordEvent {
    }

    record RefreshSucceeded() implements RecordEvent {
    }

    record RefreshFailed(@NonNull RecordFailure failure) implements RecordEvent {

        public RefreshFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    // ---------------- 设备文件同步 ----------------

    record SyncRequested(@NonNull String fileId) implements RecordEvent {

        public SyncRequested {
            Objects.requireNonNull(fileId, "fileId");
        }
    }

    record SyncProgress(@NonNull String fileId, int percent, long bytesReceived, long totalBytes)
            implements RecordEvent {

        public SyncProgress {
            Objects.requireNonNull(fileId, "fileId");
        }
    }

    record SyncSucceeded(@NonNull SavedRecordItem item) implements RecordEvent {

        public SyncSucceeded {
            Objects.requireNonNull(item, "item");
        }
    }

    record SyncFailed(@NonNull RecordFailure failure) implements RecordEvent {

        public SyncFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    record SyncCancelRequested() implements RecordEvent {
    }

    record SyncCancelSucceeded() implements RecordEvent {
    }

    record SyncCancelFailed(@NonNull RecordFailure failure) implements RecordEvent {

        public SyncCancelFailed {
            Objects.requireNonNull(failure, "failure");
        }
    }

    // ---------------- 本地已存文件与播放 ----------------

    record LocalFilesReloadRequested() implements RecordEvent {
    }

    record LocalFilesReloaded(@NonNull List<SavedRecordItem> files) implements RecordEvent {

        public LocalFilesReloaded {
            files = List.copyOf(Objects.requireNonNull(files, "files"));
        }
    }

    /** 播放 / 停止：同一个意图（点击正在播放的那一条即停止）。 */
    record PlayRequested(@NonNull SavedRecordItem item) implements RecordEvent {

        public PlayRequested {
            Objects.requireNonNull(item, "item");
        }
    }

    record PlaybackStopRequested() implements RecordEvent {
    }

    /** 播放自然结束（端口回调）。 */
    record PlaybackFinished() implements RecordEvent {
    }

    record LocalDeleteRequested(@NonNull SavedRecordItem item) implements RecordEvent {

        public LocalDeleteRequested {
            Objects.requireNonNull(item, "item");
        }
    }

    record LocalDeleteSucceeded(@NonNull String filePath) implements RecordEvent {

        public LocalDeleteSucceeded {
            Objects.requireNonNull(filePath, "filePath");
        }
    }

    record LocalDeleteFailed(@NonNull String filePath, @NonNull RecordFailure failure)
            implements RecordEvent {

        public LocalDeleteFailed {
            Objects.requireNonNull(filePath, "filePath");
            Objects.requireNonNull(failure, "failure");
        }
    }

    record DeviceDeleteRequested(@NonNull String fileId) implements RecordEvent {

        public DeviceDeleteRequested {
            Objects.requireNonNull(fileId, "fileId");
        }
    }

    record DeviceDeleteSucceeded(@NonNull String fileId) implements RecordEvent {

        public DeviceDeleteSucceeded {
            Objects.requireNonNull(fileId, "fileId");
        }
    }

    record DeviceDeleteFailed(@NonNull String fileId, @NonNull RecordFailure failure)
            implements RecordEvent {

        public DeviceDeleteFailed {
            Objects.requireNonNull(fileId, "fileId");
            Objects.requireNonNull(failure, "failure");
        }
    }
}
