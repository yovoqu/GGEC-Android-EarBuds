package com.ggec.earbuds.domain.device.record;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 耳机录音功能的完整状态（design §4.2）。
 *
 * <p>三条录音业务（随录随传、耳机本地录音、文件同步）与两条本地事实（已存文件、播放中文件）
 * 各自只有一个字段承载，界面不再另存一份：{@code RecordUiState} 全部由
 * {@code RecordUiMapper} 从这里纯派生。</p>
 *
 * <p>契约：所有字段非 null；缺失语义写成显式值（{@code Set.of()}、{@code List.of()}、
 * {@code Optional.empty()}、{@link Stream#idle()} 等），构造点不做默认值填充。</p>
 */
public record RecordState(
        RecordMode mode,
        Set<RecordMode> supported,
        boolean connected,
        Stream stream,
        DeviceRecord deviceRecord,
        Sync sync,
        List<SavedRecordItem> localFiles,
        List<RecordFileView> deviceFiles,
        Optional<String> playingFile) {

    public RecordState {
        Objects.requireNonNull(mode, "mode");
        supported = Set.copyOf(Objects.requireNonNull(supported, "supported"));
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(deviceRecord, "deviceRecord");
        Objects.requireNonNull(sync, "sync");
        localFiles = List.copyOf(Objects.requireNonNull(localFiles, "localFiles"));
        deviceFiles = List.copyOf(Objects.requireNonNull(deviceFiles, "deviceFiles"));
        playingFile = Objects.requireNonNull(playingFile, "playingFile");
    }

    /**
     * 任何业务流程开始前的状态：没有活动设备、没有可用模式、没有任何录音事实。
     *
     * <p>这不是「界面默认值」：它描述的是「还没有读到设备」这一真实状态，{@code supported} 为空集
     * 是它的显式结论，界面据此渲染禁用态（旧实现在空集上 {@code iterator().next()} 会崩）。</p>
     */
    @NonNull
    public static RecordState initial() {
        return new RecordState(
                RecordMode.STREAM,
                Set.of(),
                false,
                Stream.idle(),
                DeviceRecord.idle(),
                Sync.idle(),
                List.of(),
                List.of(),
                Optional.empty());
    }

    // ---- 单字段迁移：状态机只换被事件影响的那一个字段 ----

    @NonNull
    public RecordState withMode(@NonNull RecordMode next) {
        return new RecordState(next, supported, connected, stream, deviceRecord, sync,
                localFiles, deviceFiles, playingFile);
    }

    @NonNull
    public RecordState withConnection(boolean nextConnected, @NonNull Set<RecordMode> nextSupported) {
        return new RecordState(mode, nextSupported, nextConnected, stream, deviceRecord, sync,
                localFiles, deviceFiles, playingFile);
    }

    @NonNull
    public RecordState withStream(@NonNull Stream next) {
        return new RecordState(mode, supported, connected, next, deviceRecord, sync,
                localFiles, deviceFiles, playingFile);
    }

    @NonNull
    public RecordState withDeviceRecord(@NonNull DeviceRecord next) {
        return new RecordState(mode, supported, connected, stream, next, sync,
                localFiles, deviceFiles, playingFile);
    }

    @NonNull
    public RecordState withSync(@NonNull Sync next) {
        return new RecordState(mode, supported, connected, stream, deviceRecord, next,
                localFiles, deviceFiles, playingFile);
    }

    @NonNull
    public RecordState withLocalFiles(@NonNull List<SavedRecordItem> next) {
        return new RecordState(mode, supported, connected, stream, deviceRecord, sync,
                next, deviceFiles, playingFile);
    }

    @NonNull
    public RecordState withDeviceFiles(@NonNull List<RecordFileView> next) {
        return new RecordState(mode, supported, connected, stream, deviceRecord, sync,
                localFiles, next, playingFile);
    }

    @NonNull
    public RecordState withPlayingFile(@NonNull Optional<String> next) {
        return new RecordState(mode, supported, connected, stream, deviceRecord, sync,
                localFiles, deviceFiles, next);
    }

    // ---- 随录随传 ----

    /**
     * 随录随传的阶段。
     *
     * <p>{@code WAITING_DATA} 与 {@code RECORDING} 的区别是设备的真实事实：会话已进入录音，但还没有
     * 任何音频数据回传（端口上报的时长为 0）。界面据此显示「等待数据」而不是假的 00:00:00 计时。</p>
     */
    public enum StreamPhase {
        IDLE,
        STARTING,
        WAITING_DATA,
        RECORDING,
        STOPPING
    }

    /** 随录随传会话：阶段 + 已录时长 + 实时幅度。 */
    public record Stream(@NonNull StreamPhase phase, long durationMs, float amplitude) {

        public Stream {
            Objects.requireNonNull(phase, "phase");
        }

        @NonNull
        public static Stream idle() {
            return new Stream(StreamPhase.IDLE, 0L, 0f);
        }

        public boolean active() {
            return phase != StreamPhase.IDLE;
        }
    }

    // ---- 耳机本地录音 ----

    public enum DeviceRecordPhase {
        IDLE,
        STARTING,
        ACTIVE,
        STOPPING
    }

    /**
     * 耳机端本地录音：阶段 + 起点时刻 + 已录时长。
     *
     * <p>{@code startedAtMs} 只在 {@link DeviceRecordPhase#ACTIVE} 与
     * {@link DeviceRecordPhase#STOPPING} 有值：计时由状态机外部的 tick 事件带着 {@code nowMs} 推进，
     * 状态机自己不读时钟。{@code durationMs} 是最近一次 tick 算出的值（非 {@code ACTIVE} 时为 0）。</p>
     */
    public record DeviceRecord(@NonNull DeviceRecordPhase phase,
                               @NonNull OptionalLong startedAtMs,
                               long durationMs) {

        public DeviceRecord {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(startedAtMs, "startedAtMs");
        }

        @NonNull
        public static DeviceRecord idle() {
            return new DeviceRecord(DeviceRecordPhase.IDLE, OptionalLong.empty(), 0L);
        }

        @NonNull
        public static DeviceRecord starting() {
            return new DeviceRecord(DeviceRecordPhase.STARTING, OptionalLong.empty(), 0L);
        }

        @NonNull
        public static DeviceRecord active(long startedAtMs, long durationMs) {
            return new DeviceRecord(DeviceRecordPhase.ACTIVE, OptionalLong.of(startedAtMs), durationMs);
        }

        @NonNull
        public static DeviceRecord stopping(long startedAtMs, long durationMs) {
            return new DeviceRecord(DeviceRecordPhase.STOPPING, OptionalLong.of(startedAtMs), durationMs);
        }
    }

    // ---- 设备文件同步 ----

    public enum SyncPhase {
        IDLE,
        IN_FLIGHT
    }

    /** 一次设备文件同步：只有同步中才带文件 id 与字节计数。 */
    public record Sync(@NonNull SyncPhase phase,
                       @NonNull Optional<String> fileId,
                       int percent,
                       long bytesReceived,
                       long totalBytes) {

        public Sync {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(fileId, "fileId");
        }

        @NonNull
        public static Sync idle() {
            return new Sync(SyncPhase.IDLE, Optional.empty(), 0, 0L, 0L);
        }

        public boolean inFlight() {
            return phase == SyncPhase.IN_FLIGHT;
        }
    }
}
