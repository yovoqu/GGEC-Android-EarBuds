package com.ggec.earbuds.ui.ai.call;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.data.ai.voice.ExternalPcmAudioSource;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleItem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * 通话页 ViewModel 单元测试：
 * 验证严格单向数据流、前/中置音频源切换、静音/双工预置透传与不可变状态投影。
 */
public class AiCallViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private FakeVoiceSession fakeSession;
    private FakeTicker fakeTicker;
    private AiCallViewModel viewModel;

    @Before
    public void setUp() {
        fakeSession = new FakeVoiceSession();
        fakeTicker = new FakeTicker();
        viewModel = new AiCallViewModel(() -> fakeSession, fakeTicker);
    }

    @Test
    public void startInitializesConnectingStateAndStartsSession() {
        assertFalse(viewModel.isStarted());
        viewModel.start();

        assertTrue(viewModel.isStarted());
        assertTrue(fakeSession.started);
        AiCallUiState state = viewModel.uiState().getValue();
        assertEquals(AiCallUiState.Phase.CONNECTING, state.phase());
        assertTrue(state.speakerOn());
        assertTrue(state.captionsVisible());
        assertEquals(0, state.durationSeconds());
        assertNull("未手动切换双工时，交由会话内部按设置默认值决策",
                fakeSession.explicitDuplexSet);
    }

    @Test
    public void retryStopsTheOldSessionAndStartsANewOne() {
        viewModel.start();

        viewModel.retry();

        assertTrue(fakeSession.stopped);
        assertTrue(viewModel.isStarted());
    }

    @Test
    public void duplexTogglePublishesItsHintInsteadOfLettingTheViewChooseText() {
        viewModel.start();

        viewModel.toggleDuplex();

        assertEquals(Integer.valueOf(R.string.ai_call_duplex_full_hint),
                viewModel.toasts().getValue());
    }

    @Test
    public void muteUpdatesSessionAndUiState() {
        viewModel.start();
        assertFalse(viewModel.uiState().getValue().micMuted());

        viewModel.setMicMuted(true);
        assertTrue(fakeSession.micMuted);
        assertTrue(viewModel.uiState().getValue().micMuted());

        viewModel.setMicMuted(false);
        assertFalse(fakeSession.micMuted);
        assertFalse(viewModel.uiState().getValue().micMuted());
    }

    @Test
    public void speakerphoneUpdatesSessionAndUiState() {
        viewModel.start();
        assertTrue(viewModel.uiState().getValue().speakerOn());

        viewModel.setSpeakerphone(false);
        assertFalse(fakeSession.speakerphone);
        assertFalse(viewModel.uiState().getValue().speakerOn());
    }

    @Test
    public void toggleCaptionsInvertsVisibility() {
        viewModel.start();
        assertTrue(viewModel.uiState().getValue().captionsVisible());

        viewModel.toggleCaptions();
        assertFalse(viewModel.uiState().getValue().captionsVisible());

        viewModel.toggleCaptions();
        assertTrue(viewModel.uiState().getValue().captionsVisible());
    }

    @Test
    public void hangUpStopsSession() {
        viewModel.start();
        assertTrue(viewModel.isStarted());

        viewModel.hangUp();
        assertTrue(fakeSession.stopped);
        assertFalse(viewModel.isStarted());
    }

    @Test
    public void interruptDelegatesToSession() {
        viewModel.start();
        viewModel.interrupt();
        assertTrue(fakeSession.interrupted);
    }

    @Test
    public void onSnapshotUpdatesSubtitlesAndAvailableSourcesInUiState() {
        viewModel.start();
        assertEquals(List.of(), viewModel.uiState().getValue().subtitleItems());

        AiAudioSource.Descriptor mic = new AiAudioSource.Descriptor(
                "builtin:1", AiAudioSource.Kind.BUILTIN_MIC, "手机麦", 16_000);
        AiAudioSource.Descriptor ble = new AiAudioSource.Descriptor(
                "ble:1", AiAudioSource.Kind.BLUETOOTH_BLE, "GGEC Buds", 48_000);
        List<AiVoiceSnapshot.Turn> turns = List.of(
                new AiVoiceSnapshot.Turn("1", true, "你好", true, 1000L),
                new AiVoiceSnapshot.Turn("2", false, "很高兴为你服务", false, 2000L)
        );
        AiVoiceSnapshot snapshot = new AiVoiceSnapshot(
                AiVoiceSnapshot.State.SPEAKING, turns, 0.5f, null,
                VoiceDuplex.FULL_DUPLEX, false, ble, List.of(ble, mic));

        fakeSession.listener.onSnapshot(snapshot);

        List<AiSubtitleItem> items = viewModel.uiState().getValue().subtitleItems();
        assertEquals(2, items.size());
        assertEquals("1", items.get(0).id());
        assertTrue(items.get(0).isUser());
        assertEquals("你好", items.get(0).text());
        assertTrue(items.get(0).isFinal());

        assertEquals("2", items.get(1).id());
        assertFalse(items.get(1).isUser());
        assertEquals("很高兴为你服务", items.get(1).text());
        assertFalse(items.get(1).isFinal());

        AiCallUiState ui = viewModel.uiState().getValue();
        assertEquals(VoiceDuplex.FULL_DUPLEX, ui.duplex());
        assertEquals(ble, ui.activeSource());
        assertEquals(List.of(ble, mic), ui.availableSources());
        assertEquals("非听取阶段电平归零，与视觉阶段一致", 0f, ui.meter(), 0.0001f);
    }

    @Test
    public void listeningLevelAndSubtitlesTravelWithTheSameStateStream() {
        viewModel.start();
        AiVoiceSnapshot snapshot = new AiVoiceSnapshot(
                AiVoiceSnapshot.State.LISTENING,
                List.of(new AiVoiceSnapshot.Turn("1", true, "你好", true, 1000L)),
                0.5f, null, VoiceDuplex.FULL_DUPLEX, false, null, List.of());

        fakeSession.listener.onSnapshot(snapshot);

        AiCallUiState ui = viewModel.uiState().getValue();
        assertEquals(AiCallUiState.Phase.LISTENING, ui.phase());
        assertEquals(0.5f, ui.meter(), 0.0001f);
        assertEquals(1, ui.subtitleItems().size());
    }

    @Test
    public void toggleDuplexInvertsDuplexMode() {
        viewModel.start();
        assertEquals(VoiceDuplex.HALF_DUPLEX, viewModel.duplex());
        assertEquals(VoiceDuplex.HALF_DUPLEX, viewModel.uiState().getValue().duplex());

        viewModel.toggleDuplex();
        assertEquals(VoiceDuplex.FULL_DUPLEX, viewModel.duplex());
        assertEquals(VoiceDuplex.FULL_DUPLEX, fakeSession.duplex);
        assertEquals(VoiceDuplex.FULL_DUPLEX, viewModel.uiState().getValue().duplex());

        viewModel.toggleDuplex();
        assertEquals(VoiceDuplex.HALF_DUPLEX, viewModel.duplex());
        assertEquals(VoiceDuplex.HALF_DUPLEX, fakeSession.duplex);
        assertEquals(VoiceDuplex.HALF_DUPLEX, viewModel.uiState().getValue().duplex());
    }

    @Test
    public void setDuplexUpdatesSessionAndState() {
        viewModel.start();
        viewModel.setDuplex(VoiceDuplex.FULL_DUPLEX);
        assertEquals(VoiceDuplex.FULL_DUPLEX, viewModel.duplex());
        assertEquals(VoiceDuplex.FULL_DUPLEX, fakeSession.duplex);
        assertEquals(VoiceDuplex.FULL_DUPLEX, viewModel.uiState().getValue().duplex());
    }

    @Test
    public void preStartControlsForwardToCreatedSessionOnStart() {
        viewModel.setMicMuted(true);
        viewModel.setDuplex(VoiceDuplex.FULL_DUPLEX);
        viewModel.selectAudioSource("bt:sco:1");

        viewModel.start();

        assertTrue(fakeSession.micMuted);
        assertEquals(VoiceDuplex.FULL_DUPLEX, fakeSession.explicitDuplexSet);
        assertEquals("bt:sco:1", fakeSession.selectedSourceId);
    }

    @Test
    public void midCallSourceSwitchingDelegatesToSession() {
        viewModel.start();
        viewModel.selectAudioSource("usb:1");
        assertEquals("usb:1", fakeSession.selectedSourceId);

        ExternalPcmAudioSource external = new ExternalPcmAudioSource(
                "ext:1", "自定义音源", 16_000);
        viewModel.setAudioSource(external);
        assertSame(external, fakeSession.customSource);
    }

    private static final class FakeTicker implements AiCallViewModel.Ticker {
        Runnable scheduled;

        @Override
        public void postDelayed(@NonNull Runnable r, long delayMs) {
            this.scheduled = r;
        }

        @Override
        public void removeCallbacks(@NonNull Runnable r) {
            if (this.scheduled == r) {
                this.scheduled = null;
            }
        }
    }

    private static final class FakeVoiceSession implements AiVoiceSession {
        boolean started = false;
        boolean stopped = false;
        boolean micMuted = false;
        boolean speakerphone = true;
        boolean interrupted = false;
        VoiceDuplex duplex = VoiceDuplex.defaultMode();
        VoiceDuplex explicitDuplexSet;
        String selectedSourceId;
        AiAudioSource customSource;
        Listener listener;

        @Override
        public void start(@NonNull Listener listener) {
            this.started = true;
            this.listener = listener;
        }

        @Override
        public void stop() {
            this.stopped = true;
        }

        @Override
        public void setMicMuted(boolean micMuted) {
            this.micMuted = micMuted;
        }

        @Override
        public void setSpeakerphone(boolean speakerphone) {
            this.speakerphone = speakerphone;
        }

        @Override
        public void setDuplex(@NonNull VoiceDuplex duplex) {
            this.duplex = duplex;
            this.explicitDuplexSet = duplex;
        }

        @Override
        public void selectAudioSource(@NonNull String sourceId) {
            this.selectedSourceId = sourceId;
        }

        @Override
        public void setAudioSource(@NonNull AiAudioSource source) {
            this.customSource = source;
        }

        @Override
        public void interrupt() {
            this.interrupted = true;
        }
    }
}
