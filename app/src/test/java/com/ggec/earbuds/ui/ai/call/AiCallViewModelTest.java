package com.ggec.earbuds.ui.ai.call;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

/**
 * 通话页 ViewModel 单元测试：
 * 验证严格 MVVM 架构中状态单向流动、意图处理与不可变快照投影。
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
        AiVoiceSessionFactory factory = () -> fakeSession;
        AiCallUiMapper mapper = new AiCallUiMapper();
        viewModel = new AiCallViewModel(factory, mapper, fakeTicker);
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
    }

    @Test
    public void muteUpdatesSessionAndUiState() {
        viewModel.start();
        assertFalse(viewModel.uiState().getValue().muted());

        viewModel.setMuted(true);
        assertTrue(fakeSession.muted);
        assertTrue(viewModel.uiState().getValue().muted());

        viewModel.setMuted(false);
        assertFalse(fakeSession.muted);
        assertFalse(viewModel.uiState().getValue().muted());
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
    public void onSnapshotUpdatesTranscriptFlow() {
        viewModel.start();
        assertEquals("", viewModel.transcript().getValue());

        List<AiVoiceSnapshot.Turn> turns = List.of(
                new AiVoiceSnapshot.Turn("1", true, "你好", true, 1000L),
                new AiVoiceSnapshot.Turn("2", false, "很高兴为你服务", false, 2000L)
        );
        AiVoiceSnapshot snapshot = new AiVoiceSnapshot(
                AiVoiceSnapshot.State.SPEAKING, turns, 0.5f, null);

        fakeSession.listener.onSnapshot(snapshot);

        String expected = "你：你好\nAI：很高兴为你服务…";
        assertEquals(expected, viewModel.transcript().getValue());
    }

    @Test
    public void toggleDuplexInvertsDuplexMode() {
        viewModel.start();
        assertEquals(VoiceDuplex.FULL_DUPLEX, viewModel.duplex());
        assertEquals(VoiceDuplex.FULL_DUPLEX, viewModel.uiState().getValue().duplex());

        viewModel.toggleDuplex();
        assertEquals(VoiceDuplex.HALF_DUPLEX, viewModel.duplex());
        assertEquals(VoiceDuplex.HALF_DUPLEX, fakeSession.duplex);
        assertEquals(VoiceDuplex.HALF_DUPLEX, viewModel.uiState().getValue().duplex());

        viewModel.toggleDuplex();
        assertEquals(VoiceDuplex.FULL_DUPLEX, viewModel.duplex());
        assertEquals(VoiceDuplex.FULL_DUPLEX, fakeSession.duplex);
        assertEquals(VoiceDuplex.FULL_DUPLEX, viewModel.uiState().getValue().duplex());
    }

    @Test
    public void setDuplexUpdatesSessionAndState() {
        viewModel.start();
        viewModel.setDuplex(VoiceDuplex.HALF_DUPLEX);
        assertEquals(VoiceDuplex.HALF_DUPLEX, viewModel.duplex());
        assertEquals(VoiceDuplex.HALF_DUPLEX, fakeSession.duplex);
        assertEquals(VoiceDuplex.HALF_DUPLEX, viewModel.uiState().getValue().duplex());
    }

    private static final class FakeTicker implements AiCallViewModel.Ticker {
        Runnable scheduled;
        long delay;

        @Override
        public void postDelayed(@NonNull Runnable r, long delayMs) {
            this.scheduled = r;
            this.delay = delayMs;
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
        boolean muted = false;
        boolean speakerphone = true;
        boolean interrupted = false;
        VoiceDuplex duplex = VoiceDuplex.FULL_DUPLEX;
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
        public void setMuted(boolean muted) {
            this.muted = muted;
        }

        @Override
        public void setSpeakerphone(boolean speakerphone) {
            this.speakerphone = speakerphone;
        }

        @Override
        public void setDuplex(@NonNull VoiceDuplex duplex) {
            this.duplex = duplex;
        }

        @Override
        public void interrupt() {
            this.interrupted = true;
        }
    }
}
