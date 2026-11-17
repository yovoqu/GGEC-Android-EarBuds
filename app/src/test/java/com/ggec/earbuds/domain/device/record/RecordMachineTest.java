package com.ggec.earbuds.domain.device.record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;

import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 录音状态机的迁移：穷尽、无默认值、计时由事件驱动。
 *
 * <p>锁死的三条回归：</p>
 * <ul>
 *   <li>可用模式为空集时不得从空集里取元素（旧实现 {@code iterator().next()} 崩溃）；</li>
 *   <li>耳机本地录音的时长只由 {@code DeviceRecordTicked(nowMs)} 推进，状态机不读时钟；</li>
 *   <li>同步的 发起 / 进度 / 成功 / 失败 / 取消 五个分支各自有明确迁移，没有静默吞掉。</li>
 * </ul>
 */
public final class RecordMachineTest {

    private static final String FILE_ID = "dev-file-1";
    private static final SavedRecordItem LOCAL_FILE =
            new SavedRecordItem("/tmp/a.mp3", "a.mp3", 1024L, 1_700_000_000_000L, 5_000L);

    @Test
    public void initialStateHasNoSupportedModeAndIsNotConnected() {
        RecordState state = RecordState.initial();

        assertFalse(state.connected());
        assertTrue(state.supported().isEmpty());
        assertSame(RecordState.StreamPhase.IDLE, state.stream().phase());
        assertSame(RecordState.DeviceRecordPhase.IDLE, state.deviceRecord().phase());
        assertSame(RecordState.SyncPhase.IDLE, state.sync().phase());
        assertTrue(state.playingFile().isEmpty());
    }

    @Test
    public void emptySupportedModesNeverCrashesAndRejectsModeSelection() {
        RecordState state = RecordState.initial();

        RecordMachine.Transition transition =
                RecordMachine.reduce(state, new RecordEvent.ModeSelected(RecordMode.LOCAL));

        assertSame(state, transition.state());
        assertTrue(transition.effects().get(0) instanceof RecordEffect.Notify notify
                && notify.notice() instanceof RecordEffect.Notice.ModeUnsupported);
    }

    @Test
    public void connectedWithEmptyModesKeepsCurrentModeAndStaysDisabled() {
        RecordState state = connectedWith(Set.of());

        assertTrue(state.connected());
        assertTrue(state.supported().isEmpty());
        assertSame("空集不得从目录里取第一个", RecordMode.STREAM, state.mode());
        assertTrue(RecordMachine.reduce(state,
                new RecordEvent.StreamStartRequested()).effects().get(0)
                instanceof RecordEffect.Notify);
    }

    @Test
    public void connectedSelectsTheOnlySupportedMode() {
        RecordState state = connectedWith(Set.of(RecordMode.LOCAL));

        assertSame(RecordMode.LOCAL, state.mode());
        assertEquals(connectedDeviceFiles(), state.deviceFiles());
    }

    @Test
    public void streamStartRequiresConnection() {
        RecordMachine.Transition transition = RecordMachine.reduce(RecordState.initial(),
                new RecordEvent.StreamStartRequested());

        assertTrue(transition.effects().get(0) instanceof RecordEffect.Notify notify
                && notify.notice() instanceof RecordEffect.Notice.DeviceNotConnected);
    }

    @Test
    public void streamStartEmitsStartEffectAndWaitsForData() {
        RecordMachine.Transition started = RecordMachine.reduce(connectedWith(Set.of(
                RecordMode.STREAM, RecordMode.LOCAL)), new RecordEvent.StreamStartRequested());

        assertTrue(started.effects().get(0) instanceof RecordEffect.StartStream);
        assertSame(RecordState.StreamPhase.STARTING, started.state().stream().phase());

        RecordMachine.Transition noData = RecordMachine.reduce(started.state(),
                new RecordEvent.StreamState(
                        new StreamAudioSessionState(StreamAudioSessionState.Status.RECORDING,
                                0L, 0.3f)));
        assertSame(RecordState.StreamPhase.WAITING_DATA, noData.state().stream().phase());

        RecordMachine.Transition receiving = RecordMachine.reduce(noData.state(),
                new RecordEvent.StreamState(
                        new StreamAudioSessionState(StreamAudioSessionState.Status.RECORDING,
                                2_500L, 0.6f)));
        assertSame(RecordState.StreamPhase.RECORDING, receiving.state().stream().phase());
        assertEquals(2_500L, receiving.state().stream().durationMs());
        assertEquals(0.6f, receiving.state().stream().amplitude(), 0.001f);
    }

    @Test
    public void streamSavedResetsStreamAndReloadsLocalFiles() {
        RecordMachine.Transition transition = RecordMachine.reduce(connectedWith(Set.of(
                        RecordMode.STREAM)), new RecordEvent.StreamSaved(LOCAL_FILE));

        assertSame(RecordState.StreamPhase.IDLE, transition.state().stream().phase());
        assertTrue(transition.effects().get(0) instanceof RecordEffect.ReloadLocal);
        assertTrue(transition.effects().get(1) instanceof RecordEffect.Notify notify
                && notify.notice() instanceof RecordEffect.Notice.StreamSaved saved
                && saved.fileName().equals("a.mp3"));
    }

    @Test
    public void streamStopFailureKeepsTheRecordingState() {
        RecordState recording = RecordMachine.reduce(connectedWith(Set.of(RecordMode.STREAM)),
                new RecordEvent.StreamState(new StreamAudioSessionState(
                        StreamAudioSessionState.Status.RECORDING, 4_000L, 0.5f))).state();

        RecordMachine.Transition stopped = RecordMachine.reduce(recording,
                new RecordEvent.StreamStopRequested());
        assertTrue(stopped.effects().get(0) instanceof RecordEffect.StopStream);
        assertSame(RecordState.StreamPhase.STOPPING, stopped.state().stream().phase());

        RecordMachine.Transition failed = RecordMachine.reduce(stopped.state(),
                new RecordEvent.StreamStopFailed(RecordFailure.REJECTED));
        assertSame("停止失败仍然是录音中", RecordState.StreamPhase.RECORDING,
                failed.state().stream().phase());
        assertEquals(4_000L, failed.state().stream().durationMs());
    }

    @Test
    public void deviceRecordDurationComesOnlyFromTickEvents() {
        RecordState connected = connectedWith(Set.of(RecordMode.LOCAL));

        RecordMachine.Transition requested =
                RecordMachine.reduce(connected, new RecordEvent.DeviceRecordStartRequested());
        assertTrue(requested.effects().get(0) instanceof RecordEffect.StartDeviceRecord);
        assertSame(RecordState.DeviceRecordPhase.STARTING,
                requested.state().deviceRecord().phase());

        RecordMachine.Transition started = RecordMachine.reduce(requested.state(),
                new RecordEvent.DeviceRecordStarted(10_000L));
        assertSame(RecordState.DeviceRecordPhase.ACTIVE,
                started.state().deviceRecord().phase());
        assertEquals(0L, started.state().deviceRecord().durationMs());

        RecordMachine.Transition ticked = RecordMachine.reduce(started.state(),
                new RecordEvent.DeviceRecordTicked(13_500L));
        assertEquals(3_500L, ticked.state().deviceRecord().durationMs());
        assertTrue(ticked.effects().isEmpty());

        RecordMachine.Transition afterStop = RecordMachine.reduce(ticked.state(),
                new RecordEvent.DeviceRecordTicked(99_000L));
        assertEquals(89_000L, afterStop.state().deviceRecord().durationMs());

        RecordMachine.Transition stopped = RecordMachine.reduce(afterStop.state(),
                new RecordEvent.DeviceRecordStopped());
        assertSame(RecordState.DeviceRecordPhase.IDLE,
                stopped.state().deviceRecord().phase());
        assertTrue(stopped.effects().get(0) instanceof RecordEffect.RefreshDeviceFiles);

        RecordState idleAfterStop = stopped.state();
        RecordMachine.Transition tickWhenIdle = RecordMachine.reduce(idleAfterStop,
                new RecordEvent.DeviceRecordTicked(120_000L));
        assertSame("非录制态的 tick 不改状态", idleAfterStop, tickWhenIdle.state());
    }

    @Test
    public void syncLifecycleIsFullyMapped() {
        RecordState connected = connectedWith(Set.of(RecordMode.LOCAL));

        RecordMachine.Transition started =
                RecordMachine.reduce(connected, new RecordEvent.SyncRequested(FILE_ID));
        assertTrue(started.effects().get(0) instanceof RecordEffect.SyncFile sync
                && sync.fileId().equals(FILE_ID));
        assertSame(RecordState.SyncPhase.IN_FLIGHT, started.state().sync().phase());

        RecordMachine.Transition busy =
                RecordMachine.reduce(started.state(), new RecordEvent.SyncRequested(FILE_ID));
        assertTrue(busy.effects().get(0) instanceof RecordEffect.Notify notify
                && notify.notice() instanceof RecordEffect.Notice.SyncBusy);
        assertSame(RecordState.SyncPhase.IN_FLIGHT, busy.state().sync().phase());

        RecordMachine.Transition progressed = RecordMachine.reduce(started.state(),
                new RecordEvent.SyncProgress(FILE_ID, 40, 400L, 1_000L));
        assertEquals(40, progressed.state().sync().percent());
        assertEquals(400L, progressed.state().sync().bytesReceived());
        assertEquals(1_000L, progressed.state().sync().totalBytes());

        RecordMachine.Transition stale = RecordMachine.reduce(progressed.state(),
                new RecordEvent.SyncProgress("other", 90, 900L, 1_000L));
        assertSame("别的文件的进度不得改写当前同步", progressed.state(), stale.state());

        RecordMachine.Transition cancelled =
                RecordMachine.reduce(progressed.state(), new RecordEvent.SyncCancelRequested());
        assertTrue(cancelled.effects().get(0) instanceof RecordEffect.CancelSync);
        RecordMachine.Transition cancelDone = RecordMachine.reduce(cancelled.state(),
                new RecordEvent.SyncCancelSucceeded());
        assertSame(RecordState.SyncPhase.IDLE, cancelDone.state().sync().phase());

        RecordMachine.Transition failed = RecordMachine.reduce(started.state(),
                new RecordEvent.SyncFailed(RecordFailure.TIMEOUT));
        assertSame(RecordState.SyncPhase.IDLE, failed.state().sync().phase());
        assertTrue(failed.effects().get(0) instanceof RecordEffect.Notify notify
                && notify.notice() instanceof RecordEffect.Notice.SyncFailed);
    }

    @Test
    public void disconnectStopsEveryDeviceBoundState() {
        RecordState recording = RecordMachine.reduce(connectedWith(Set.of(
                        RecordMode.STREAM, RecordMode.LOCAL)),
                new RecordEvent.DeviceRecordStartRequested()).state();
        RecordState active = RecordMachine.reduce(recording,
                new RecordEvent.DeviceRecordStarted(1_000L)).state();

        RecordState disconnected =
                RecordMachine.reduce(active, new RecordEvent.Disconnected()).state();

        assertFalse(disconnected.connected());
        assertTrue(disconnected.supported().isEmpty());
        assertTrue(disconnected.deviceFiles().isEmpty());
        assertSame(RecordState.DeviceRecordPhase.IDLE,
                disconnected.deviceRecord().phase());
        assertSame(RecordState.StreamPhase.IDLE, disconnected.stream().phase());
        assertSame(RecordState.SyncPhase.IDLE, disconnected.sync().phase());
    }

    @Test
    public void playingTheSameFileTwiceStopsPlayback() {
        RecordState connected = connectedWith(Set.of(RecordMode.STREAM));

        RecordMachine.Transition playing = RecordMachine.reduce(connected,
                new RecordEvent.PlayRequested(LOCAL_FILE));
        assertEquals(Optional.of(LOCAL_FILE.filePath()), playing.state().playingFile());
        assertTrue(playing.effects().get(0) instanceof RecordEffect.StopPlayback);
        assertTrue(playing.effects().get(1) instanceof RecordEffect.Play play
                && play.filePath().equals(LOCAL_FILE.filePath()));

        RecordMachine.Transition stopping = RecordMachine.reduce(playing.state(),
                new RecordEvent.PlayRequested(LOCAL_FILE));
        assertTrue(stopping.state().playingFile().isEmpty());
        assertTrue(stopping.effects().get(0) instanceof RecordEffect.StopPlayback);
        assertEquals(1, stopping.effects().size());
    }

    @Test
    public void localDeleteStopsPlaybackAndDeletes() {
        RecordState playing = RecordMachine.reduce(connectedWith(Set.of(RecordMode.STREAM)),
                new RecordEvent.PlayRequested(LOCAL_FILE)).state();

        RecordMachine.Transition deleting =
                RecordMachine.reduce(playing, new RecordEvent.LocalDeleteRequested(LOCAL_FILE));

        assertTrue(deleting.state().playingFile().isEmpty());
        assertTrue(deleting.effects().get(0) instanceof RecordEffect.StopPlayback);
        assertTrue(deleting.effects().get(1) instanceof RecordEffect.DeleteLocalFile delete
                && delete.filePath().equals(LOCAL_FILE.filePath()));

        RecordMachine.Transition deleted = RecordMachine.reduce(deleting.state(),
                new RecordEvent.LocalDeleteSucceeded(LOCAL_FILE.filePath()));
        assertTrue(deleted.effects().get(0) instanceof RecordEffect.ReloadLocal);
    }

    @Test
    public void deviceDeleteRequiresConnection() {
        RecordMachine.Transition rejected = RecordMachine.reduce(RecordState.initial(),
                new RecordEvent.DeviceDeleteRequested(FILE_ID));
        assertTrue(rejected.effects().get(0) instanceof RecordEffect.Notify notify
                && notify.notice() instanceof RecordEffect.Notice.DeviceNotConnected);

        RecordMachine.Transition accepted = RecordMachine.reduce(
                connectedWith(Set.of(RecordMode.LOCAL)),
                new RecordEvent.DeviceDeleteRequested(FILE_ID));
        assertTrue(accepted.effects().get(0) instanceof RecordEffect.DeleteDeviceFile delete
                && delete.fileId().equals(FILE_ID));
    }

    private static RecordState connectedWith(Set<RecordMode> supported) {
        return RecordMachine.reduce(RecordState.initial(),
                new RecordEvent.Connected(supported, connectedDeviceFiles())).state();
    }

    private static List<RecordFileView> connectedDeviceFiles() {
        return List.of(new RecordFileView(FILE_ID, "录音 1", 3, 512L));
    }

    @Test
    public void localFilesReloadRequestEmitsReloadEffect() {
        RecordMachine.Transition transition = RecordMachine.reduce(RecordState.initial(),
                new RecordEvent.LocalFilesReloadRequested());

        assertTrue(transition.effects().get(0) instanceof RecordEffect.ReloadLocal);
        assertEquals(Optional.empty(), transition.state().playingFile());
    }
}
