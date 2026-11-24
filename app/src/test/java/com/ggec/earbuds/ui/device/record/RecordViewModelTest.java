package com.ggec.earbuds.ui.device.record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.PlaybackOutput;
import com.ggec.earbuds.domain.device.model.RecordMode;
import com.ggec.earbuds.domain.device.model.RecordOutcome;
import com.ggec.earbuds.domain.device.model.SavedRecordItem;
import com.ggec.earbuds.domain.device.model.StreamAudioSessionState;
import com.ggec.earbuds.domain.device.model.SyncProgressState;
import com.ggec.earbuds.domain.device.port.PcmPlaybackPort;
import com.ggec.earbuds.domain.device.port.RecordPort;
import com.ggec.earbuds.domain.device.record.RecordEffect;
import com.ggec.earbuds.domain.device.record.RecordFailure;
import com.ggec.earbuds.domain.device.record.RecordState;
import com.ggec.earbuds.domain.device.session.DeviceSession;
import com.ggec.earbuds.domain.device.usecase.ObserveDevice;
import com.ggec.earbuds.testing.device.FakeDeviceLinkPort;
import com.ggec.earbuds.ui.device.DeviceProfileFixtures;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * ViewModel 只剩「意图 → 事件 → 状态机 → 效果」：这里验证意图确实转换成端口调用，
 * 端口结果确实回到状态，计时确实由 tick 事件推进。
 */
public final class RecordViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private FakeRecordPort recordPort;
    private FakeTicker ticker;
    private FakePlayback playback;
    private FakeDeviceLinkPort link;
    private RecordViewModel viewModel;
    private final List<RecordEffect.Notice> notices = new ArrayList<>();

    /** 注入时钟：用例自己决定「现在几点」，ViewModel 不读系统时钟。 */
    private long nowMs = 1_000L;

    @Before
    public void setUp() {
        recordPort = new FakeRecordPort();
        ticker = new FakeTicker();
        playback = new FakePlayback();
        link = new FakeDeviceLinkPort();
        DeviceSession session = new DeviceSession(link);
        viewModel = new RecordViewModel(recordPort, new ObserveDevice(session), playback, ticker,
                () -> nowMs);
        viewModel.notices().observeForever(notices::add);
    }

    @Test
    public void noDeviceMeansNothingIsRecordable() {
        RecordUiState state = viewModel.uiState().getValue();

        assertFalse(state.canRecord(RecordMode.STREAM));
        viewModel.startStreamRecord();
        assertEquals("未连接不得下发端口指令", 0, recordPort.streamStarts);
        assertTrue(notices.get(0) instanceof RecordEffect.Notice.DeviceNotConnected);
    }

    @Test
    public void readyDeviceEnablesTheModesThePortReports() {
        emitReady();

        RecordUiState state = viewModel.uiState().getValue();
        assertTrue(state.connected());
        assertEquals(Set.of(RecordMode.STREAM, RecordMode.LOCAL), state.supportedModes());
        assertTrue(state.canRecord(RecordMode.STREAM));
    }

    @Test
    public void streamIntentReachesThePortAndItsStateBackToTheUi() {
        emitReady();

        viewModel.startStreamRecord();
        assertEquals(1, recordPort.streamStarts);
        assertSame(RecordState.StreamPhase.STARTING, viewModel.uiState().getValue().streamPhase());

        recordPort.streamObserver.onStateChanged(new StreamAudioSessionState(
                StreamAudioSessionState.Status.RECORDING, 3_000L, 0.5f));

        RecordUiState state = viewModel.uiState().getValue();
        assertSame(RecordState.StreamPhase.RECORDING, state.streamPhase());
        assertEquals(3_000L, state.streamDurationMs());
        assertEquals(0.5f, state.streamAmplitude(), 0.001f);
    }

    @Test
    public void savedStreamFileReloadsLocalFiles() {
        emitReady();
        SavedRecordItem saved = new SavedRecordItem("/tmp/a.mp3", "a.mp3", 10L, 20L, 1_000L);
        recordPort.localFiles.add(saved);

        viewModel.startStreamRecord();
        recordPort.streamObserver.onFileSaved(saved);

        RecordUiState state = viewModel.uiState().getValue();
        assertSame(RecordState.StreamPhase.IDLE, state.streamPhase());
        assertEquals(List.of(saved), state.localFiles());
        assertTrue(notices.get(notices.size() - 1)
                instanceof RecordEffect.Notice.StreamSaved notice
                && notice.fileName().equals("a.mp3"));
    }

    @Test
    public void deviceRecordDurationIsDrivenByTheInjectedClock() {
        emitReady();

        viewModel.startDeviceRecord();
        assertEquals(1, recordPort.deviceStarts);
        recordPort.settleDeviceStart(RecordOutcome.ok());
        assertSame(RecordState.DeviceRecordPhase.ACTIVE,
                viewModel.uiState().getValue().deviceRecordPhase());

        nowMs = 5_000L;
        ticker.fireOnce();

        assertEquals(4_000L, viewModel.uiState().getValue().deviceRecordDurationMs());
        assertEquals("录制中要持续排下一次 tick", 1, ticker.pendingCount());

        viewModel.stopDeviceRecord();
        assertSame(RecordState.DeviceRecordPhase.IDLE,
                viewModel.uiState().getValue().deviceRecordPhase());
        assertEquals("停止后不再排 tick", 0, ticker.pendingCount());
    }

    @Test
    public void deviceRecordStartFailureIsClassifiedNotSwallowed() {
        emitReady();

        viewModel.startDeviceRecord();
        recordPort.settleDeviceStart(RecordOutcome.failed(RecordFailure.REJECTED));

        assertSame(RecordState.DeviceRecordPhase.IDLE,
                viewModel.uiState().getValue().deviceRecordPhase());
        assertTrue(notices.get(notices.size() - 1)
                instanceof RecordEffect.Notice.DeviceRecordStartFailed failed
                && failed.failure() == RecordFailure.REJECTED);
    }

    @Test
    public void syncIntentCarriesTheFileIdAndProgressLands() {
        emitReady();

        viewModel.syncDeviceRecord("dev-1");
        assertEquals("dev-1", recordPort.syncedFileId);
        assertTrue(viewModel.uiState().getValue().syncing());

        recordPort.syncObserver.onProgress(
                new SyncProgressState("dev-1", true, 60, 600L, 1_000L));

        RecordUiState state = viewModel.uiState().getValue();
        assertEquals(60, state.syncPercent());
        assertEquals(600L, state.syncTransferredBytes());

        SavedRecordItem synced = new SavedRecordItem("/tmp/b.mp3", "b.mp3", 10L, 20L, 2_000L);
        recordPort.localFiles.add(synced);
        recordPort.syncObserver.onSuccess(synced);

        RecordUiState done = viewModel.uiState().getValue();
        assertFalse(done.syncing());
        assertEquals(List.of(synced), done.localFiles());
    }

    private void emitReady() {
        link.emit(DeviceProfileFixtures.ready("HS01", new DeviceState(Map.of())));
    }

    // ---------------- 测试替身 ----------------

    private static final class FakeTicker implements RecordViewModel.Ticker {

        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void postDelayed(Runnable runnable, long delayMs) {
            pending.add(runnable);
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            pending.remove(runnable);
        }

        void fireOnce() {
            List<Runnable> due = List.copyOf(pending);
            pending.clear();
            for (Runnable runnable : due) {
                runnable.run();
            }
        }

        int pendingCount() {
            return pending.size();
        }
    }

    private static final class FakePlayback implements PcmPlaybackPort {

        @Override
        public void play(String filePath, Runnable onCompletion) {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isPlaying() {
            return false;
        }

        @Override
        public String playingFilePath() {
            return null;
        }

        @Override
        public long positionMs() {
            return 0L;
        }

        @Override
        public long durationMs() {
            return 0L;
        }

        @Override
        public List<PlaybackOutput> availableOutputs() {
            return List.of();
        }

        @Override
        public PlaybackOutput currentOutput() {
            return null;
        }

        @Override
        public boolean selectOutput(String outputId) {
            return false;
        }

        @Override
        public void release() {
        }
    }

    private static final class FakeRecordPort implements RecordPort {

        final List<SavedRecordItem> localFiles = new ArrayList<>();
        int streamStarts;
        int deviceStarts;
        StreamRecordObserver streamObserver;
        SyncProgressObserver syncObserver;
        String syncedFileId;
        private Consumer<RecordOutcome> pendingDeviceStart;

        @Override
        public void startStreamRecord(StreamRecordObserver observer) {
            streamStarts++;
            streamObserver = observer;
        }

        @Override
        public void startStreamAudio(StreamAudioObserver observer) {
            streamStarts++;
        }

        @Override
        public void stopStreamRecord(Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public boolean isStreamRecording() {
            return streamStarts > 0;
        }

        @Override
        public void startDeviceRecord(Consumer<RecordOutcome> onComplete) {
            deviceStarts++;
            pendingDeviceStart = onComplete;
        }

        @Override
        public void stopDeviceRecord(Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public void refreshDeviceRecords(Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public void syncDeviceRecord(String fileId, SyncProgressObserver observer) {
            syncedFileId = fileId;
            syncObserver = observer;
        }

        @Override
        public void cancelSyncDeviceRecord(Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public void deleteDeviceRecord(String fileId, Consumer<RecordOutcome> onComplete) {
            onComplete.accept(RecordOutcome.ok());
        }

        @Override
        public List<SavedRecordItem> listLocalSavedFiles() {
            return List.copyOf(localFiles);
        }

        @Override
        public RecordOutcome deleteLocalSavedFile(String filePath) {
            return RecordOutcome.ok();
        }

        @Override
        public Set<RecordMode> supportedModes() {
            return Set.of(RecordMode.STREAM, RecordMode.LOCAL);
        }

        void settleDeviceStart(RecordOutcome outcome) {
            Consumer<RecordOutcome> callback = pendingDeviceStart;
            if (callback != null) {
                callback.accept(outcome);
            }
        }
    }
}
