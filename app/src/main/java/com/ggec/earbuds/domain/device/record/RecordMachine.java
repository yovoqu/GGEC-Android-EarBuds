package com.ggec.earbuds.domain.device.record;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 耳机录音的迁移函数（design §4.2）：纯函数，{@code (state, event) -> (state, effects)}。
 *
 * <p>约束（结构性，不靠注释）：</p>
 * <ul>
 *   <li>穷尽 {@link RecordEvent} 的每个变体，没有 {@code default} 分支，没有可空返回；</li>
 *   <li>不读时钟：计时只由 {@link RecordEvent.DeviceRecordTicked} 带着 {@code nowMs} 推进；</li>
 *   <li>不下发指令、不拼文案：要端口做的事写成 {@link RecordEffect}，要告诉用户的事写成
 *       {@link RecordEffect.Notify}。</li>
 * </ul>
 */
public final class RecordMachine {

    /** 一次迁移的结果：新状态 + 需要执行的效果（顺序即执行顺序）。 */
    public record Transition(@NonNull RecordState state, @NonNull List<RecordEffect> effects) {

        public Transition {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
        }

        @NonNull
        static Transition unchanged(@NonNull RecordState state) {
            return new Transition(state, List.of());
        }

        @NonNull
        static Transition of(@NonNull RecordState state, @NonNull RecordEffect... effects) {
            return new Transition(state, List.of(effects));
        }
    }

    private RecordMachine() {
    }

    @NonNull
    public static Transition reduce(@NonNull RecordState state, @NonNull RecordEvent event) {
        return switch (event) {
            // ---------------- 设备与模式 ----------------
            case RecordEvent.Connected connected -> {
                RecordState next = state
                        .withConnection(true, connected.supported())
                        .withDeviceFiles(connected.deviceFiles());
                yield Transition.unchanged(
                        next.withMode(selectMode(state.mode(), connected.supported())));
            }
            case RecordEvent.Disconnected ignored -> Transition.unchanged(state
                    .withConnection(false, Set.of())
                    .withStream(RecordState.Stream.idle())
                    .withDeviceRecord(RecordState.DeviceRecord.idle())
                    .withSync(RecordState.Sync.idle())
                    .withDeviceFiles(List.of()));
            case RecordEvent.ModeSelected selected -> {
                if (!state.supported().contains(selected.mode())) {
                    yield rejected(state, new RecordEffect.Notice.ModeUnsupported());
                }
                yield Transition.unchanged(state.withMode(selected.mode()));
            }

            // ---------------- 随录随传 ----------------
            case RecordEvent.StreamStartRequested ignored -> {
                if (!state.connected()) {
                    yield rejected(state, new RecordEffect.Notice.DeviceNotConnected());
                }
                if (!state.supported().contains(RecordMode.STREAM)) {
                    yield rejected(state, new RecordEffect.Notice.ModeUnsupported());
                }
                if (state.stream().active()) {
                    yield Transition.unchanged(state);
                }
                yield Transition.of(
                        state.withStream(new RecordState.Stream(
                                RecordState.StreamPhase.STARTING, 0L, 0f)),
                        new RecordEffect.StartStream());
            }
            case RecordEvent.StreamStopRequested ignored -> {
                if (!state.stream().active()) {
                    yield Transition.unchanged(state);
                }
                if (!state.connected()) {
                    yield rejected(state, new RecordEffect.Notice.DeviceNotConnected());
                }
                RecordState.Stream current = state.stream();
                yield Transition.of(
                        state.withStream(new RecordState.Stream(
                                RecordState.StreamPhase.STOPPING,
                                current.durationMs(), current.amplitude())),
                        new RecordEffect.StopStream());
            }
            case RecordEvent.StreamState reported ->
                    Transition.unchanged(state.withStream(streamOf(reported.state())));
            case RecordEvent.StreamSaved saved -> Transition.of(
                    state.withStream(RecordState.Stream.idle()),
                    new RecordEffect.ReloadLocal(),
                    new RecordEffect.Notify(
                            new RecordEffect.Notice.StreamSaved(saved.item().fileName())));
            case RecordEvent.StreamFailed failed -> Transition.of(
                    state.withStream(RecordState.Stream.idle()),
                    new RecordEffect.Notify(
                            new RecordEffect.Notice.StreamFailed(failed.failure())));
            case RecordEvent.StreamStopSucceeded ignored -> Transition.unchanged(state);
            case RecordEvent.StreamStopFailed failed -> {
                // 停止指令失败 = 录音仍在继续：回到录音态，不假装已停。
                RecordState.Stream current = state.stream();
                RecordState.StreamPhase phase = current.durationMs() > 0
                        ? RecordState.StreamPhase.RECORDING
                        : RecordState.StreamPhase.WAITING_DATA;
                yield Transition.of(
                        state.withStream(new RecordState.Stream(
                                phase, current.durationMs(), current.amplitude())),
                        new RecordEffect.Notify(
                                new RecordEffect.Notice.StreamStopFailed(failed.failure())));
            }

            // ---------------- 耳机本地录音 ----------------
            case RecordEvent.DeviceRecordStartRequested ignored -> {
                if (!state.connected()) {
                    yield rejected(state, new RecordEffect.Notice.DeviceNotConnected());
                }
                if (!state.supported().contains(RecordMode.LOCAL)) {
                    yield rejected(state, new RecordEffect.Notice.ModeUnsupported());
                }
                if (state.deviceRecord().phase() != RecordState.DeviceRecordPhase.IDLE) {
                    yield Transition.unchanged(state);
                }
                yield Transition.of(
                        state.withDeviceRecord(RecordState.DeviceRecord.starting()),
                        new RecordEffect.StartDeviceRecord());
            }
            case RecordEvent.DeviceRecordStarted started -> Transition.of(
                    state.withDeviceRecord(
                            RecordState.DeviceRecord.active(started.atMs(), 0L)),
                    new RecordEffect.Notify(new RecordEffect.Notice.DeviceRecordStarted()));
            case RecordEvent.DeviceRecordStartFailed failed -> Transition.of(
                    state.withDeviceRecord(RecordState.DeviceRecord.idle()),
                    new RecordEffect.Notify(
                            new RecordEffect.Notice.DeviceRecordStartFailed(failed.failure())));
            case RecordEvent.DeviceRecordStopRequested ignored -> {
                RecordState.DeviceRecord current = state.deviceRecord();
                if (current.phase() != RecordState.DeviceRecordPhase.ACTIVE) {
                    yield Transition.unchanged(state);
                }
                if (!state.connected()) {
                    yield rejected(state, new RecordEffect.Notice.DeviceNotConnected());
                }
                long startedAt = current.startedAtMs().orElseThrow(
                        () -> new IllegalStateException("device record ACTIVE requires startedAtMs"));
                yield Transition.of(
                        state.withDeviceRecord(RecordState.DeviceRecord.stopping(
                                startedAt, current.durationMs())),
                        new RecordEffect.StopDeviceRecord());
            }
            case RecordEvent.DeviceRecordStopped ignored -> Transition.of(
                    state.withDeviceRecord(RecordState.DeviceRecord.idle()),
                    new RecordEffect.RefreshDeviceFiles(),
                    new RecordEffect.Notify(new RecordEffect.Notice.DeviceRecordStopped()));
            case RecordEvent.DeviceRecordStopFailed failed -> {
                RecordState.DeviceRecord current = state.deviceRecord();
                long startedAt = current.startedAtMs().orElseThrow(
                        () -> new IllegalStateException("device record STOPPING requires startedAtMs"));
                yield Transition.of(
                        state.withDeviceRecord(RecordState.DeviceRecord.active(
                                startedAt, current.durationMs())),
                        new RecordEffect.Notify(
                                new RecordEffect.Notice.DeviceRecordStopFailed(
                                        failed.failure())));
            }
            case RecordEvent.DeviceRecordTicked ticked -> {
                RecordState.DeviceRecord current = state.deviceRecord();
                if (current.phase() != RecordState.DeviceRecordPhase.ACTIVE) {
                    yield Transition.unchanged(state);
                }
                long startedAt = current.startedAtMs().orElseThrow(
                        () -> new IllegalStateException("device record ACTIVE requires startedAtMs"));
                long duration = Math.max(0L, ticked.nowMs() - startedAt);
                yield Transition.unchanged(state.withDeviceRecord(
                        RecordState.DeviceRecord.active(startedAt, duration)));
            }
            case RecordEvent.RefreshRequested ignored -> {
                if (!state.connected()) {
                    yield rejected(state, new RecordEffect.Notice.DeviceNotConnected());
                }
                yield Transition.of(state, new RecordEffect.RefreshDeviceFiles());
            }
            case RecordEvent.RefreshSucceeded ignored -> Transition.unchanged(state);
            case RecordEvent.RefreshFailed failed -> Transition.of(state,
                    new RecordEffect.Notify(
                            new RecordEffect.Notice.RefreshFailed(failed.failure())));

            // ---------------- 设备文件同步 ----------------
            case RecordEvent.SyncRequested requested -> {
                if (!state.connected()) {
                    yield rejected(state, new RecordEffect.Notice.DeviceNotConnected());
                }
                if (state.sync().inFlight()) {
                    yield rejected(state, new RecordEffect.Notice.SyncBusy());
                }
                yield Transition.of(
                        state.withSync(new RecordState.Sync(
                                RecordState.SyncPhase.IN_FLIGHT,
                                Optional.of(requested.fileId()), 0, 0L, 0L)),
                        new RecordEffect.SyncFile(requested.fileId()));
            }
            case RecordEvent.SyncProgress progress -> {
                RecordState.Sync current = state.sync();
                // 不是本次同步的进度（取消后又换了文件）：属于过期代际，不改状态。
                if (!current.fileId().equals(Optional.of(progress.fileId()))) {
                    yield Transition.unchanged(state);
                }
                yield Transition.unchanged(state.withSync(new RecordState.Sync(
                        RecordState.SyncPhase.IN_FLIGHT,
                        Optional.of(progress.fileId()),
                        progress.percent(),
                        progress.bytesReceived(),
                        progress.totalBytes())));
            }
            case RecordEvent.SyncSucceeded saved -> Transition.of(
                    state.withSync(RecordState.Sync.idle()),
                    new RecordEffect.ReloadLocal(),
                    new RecordEffect.Notify(
                            new RecordEffect.Notice.SyncSucceeded(saved.item().fileName())));
            case RecordEvent.SyncFailed failed -> Transition.of(
                    state.withSync(RecordState.Sync.idle()),
                    new RecordEffect.Notify(
                            new RecordEffect.Notice.SyncFailed(failed.failure())));
            case RecordEvent.SyncCancelRequested ignored -> {
                if (!state.sync().inFlight()) {
                    yield Transition.unchanged(state);
                }
                yield Transition.of(state, new RecordEffect.CancelSync());
            }
            case RecordEvent.SyncCancelSucceeded ignored -> Transition.of(
                    state.withSync(RecordState.Sync.idle()),
                    new RecordEffect.Notify(new RecordEffect.Notice.SyncCancelled()));
            case RecordEvent.SyncCancelFailed failed -> Transition.of(state,
                    new RecordEffect.Notify(
                            new RecordEffect.Notice.SyncCancelFailed(failed.failure())));

            // ---------------- 本地已存文件与播放 ----------------
            case RecordEvent.LocalFilesReloadRequested ignored ->
                    Transition.of(state, new RecordEffect.ReloadLocal());
            case RecordEvent.LocalFilesReloaded reloaded ->
                    Transition.unchanged(state.withLocalFiles(reloaded.files()));
            case RecordEvent.PlayRequested requested -> {
                String filePath = requested.item().filePath();
                if (state.playback().filePath().equals(Optional.of(filePath))) {
                    yield Transition.of(
                            state.withPlayback(RecordState.Playback.idle()),
                            new RecordEffect.StopPlayback());
                }
                yield Transition.of(
                        state.withPlayback(RecordState.Playback.playing(
                                filePath, requested.item().durationMs())),
                        new RecordEffect.StopPlayback(),
                        new RecordEffect.Play(filePath));
            }
            case RecordEvent.PlaybackStopRequested ignored -> {
                if (!state.playback().active()) {
                    yield Transition.unchanged(state);
                }
                yield Transition.of(
                        state.withPlayback(RecordState.Playback.idle()),
                        new RecordEffect.StopPlayback());
            }
            case RecordEvent.PlaybackProgress progress -> {
                RecordState.Playback playback = state.playback();
                if (!playback.active()) {
                    yield Transition.unchanged(state);
                }
                yield Transition.unchanged(state.withPlayback(
                        playback.withProgress(progress.positionMs(), progress.durationMs())));
            }
            case RecordEvent.PlaybackRouteChanged route -> Transition.unchanged(
                    state.withPlayback(state.playback().withRoute(route.output(), route.outputs())));
            case RecordEvent.PlaybackOutputSelectRequested requested -> {
                RecordState.Playback playback = state.playback();
                if (!playback.active()) {
                    yield Transition.unchanged(state);
                }
                boolean alreadyCurrent = playback.output()
                        .map(output -> output.id().equals(requested.outputId()))
                        .orElse(false);
                if (alreadyCurrent) {
                    yield Transition.unchanged(state);
                }
                yield Transition.of(state, new RecordEffect.SelectPlaybackOutput(requested.outputId()));
            }
            case RecordEvent.PlaybackFinished ignored ->
                    Transition.unchanged(state.withPlayback(RecordState.Playback.idle()));
            case RecordEvent.LocalDeleteRequested requested -> {
                String filePath = requested.item().filePath();
                List<RecordEffect> effects = new ArrayList<>(2);
                RecordState next = state;
                if (state.playback().filePath().equals(Optional.of(filePath))) {
                    next = state.withPlayback(RecordState.Playback.idle());
                    effects.add(new RecordEffect.StopPlayback());
                }
                effects.add(new RecordEffect.DeleteLocalFile(filePath));
                yield new Transition(next, effects);
            }
            case RecordEvent.LocalDeleteSucceeded ignored -> Transition.of(
                    state,
                    new RecordEffect.ReloadLocal(),
                    new RecordEffect.Notify(new RecordEffect.Notice.LocalFileDeleted()));
            case RecordEvent.LocalDeleteFailed failed -> Transition.of(state,
                    new RecordEffect.Notify(
                            new RecordEffect.Notice.LocalFileDeleteFailed(failed.failure())));
            case RecordEvent.DeviceDeleteRequested requested -> {
                if (!state.connected()) {
                    yield rejected(state, new RecordEffect.Notice.DeviceNotConnected());
                }
                yield Transition.of(state,
                        new RecordEffect.DeleteDeviceFile(requested.fileId()));
            }
            case RecordEvent.DeviceDeleteSucceeded ignored -> Transition.of(
                    state,
                    new RecordEffect.RefreshDeviceFiles(),
                    new RecordEffect.Notify(new RecordEffect.Notice.DeviceFileDeleteSent()));
            case RecordEvent.DeviceDeleteFailed failed -> Transition.of(state,
                    new RecordEffect.Notify(
                            new RecordEffect.Notice.DeviceFileDeleteFailed(failed.failure())));
        };
    }

    /** 拒绝类结果：状态不变，只把分类通知交出去。 */
    @NonNull
    private static Transition rejected(@NonNull RecordState state,
                                       @NonNull RecordEffect.Notice notice) {
        return Transition.of(state, new RecordEffect.Notify(notice));
    }

    /**
     * 设备就绪后选一个可用模式：保留用户当前选择；不可用时优先随录随传，其次取目录里的第一个。
     *
     * <p>空集不是「取第一个」的场合：保留当前选择，界面渲染禁用态。</p>
     */
    @NonNull
    private static RecordMode selectMode(@NonNull RecordMode current,
                                         @NonNull Set<RecordMode> supported) {
        if (supported.contains(current)) {
            return current;
        }
        if (supported.contains(RecordMode.STREAM)) {
            return RecordMode.STREAM;
        }
        return supported.stream().findFirst().orElse(current);
    }

    /** 端口会话状态 → 录音阶段。时长 0 表示会话已进入录音但还没有音频数据回传。 */
    @NonNull
    private static RecordState.Stream streamOf(@NonNull StreamAudioSessionState reported) {
        RecordState.StreamPhase phase = switch (reported.status()) {
            case IDLE -> RecordState.StreamPhase.IDLE;
            case STARTING -> RecordState.StreamPhase.STARTING;
            case RECORDING -> reported.durationMs() > 0
                    ? RecordState.StreamPhase.RECORDING
                    : RecordState.StreamPhase.WAITING_DATA;
            case STOPPING -> RecordState.StreamPhase.STOPPING;
        };
        long duration = phase == RecordState.StreamPhase.IDLE ? 0L : reported.durationMs();
        float amplitude = phase == RecordState.StreamPhase.IDLE ? 0f : reported.currentAmplitude();
        return new RecordState.Stream(phase, duration, amplitude);
    }
}
