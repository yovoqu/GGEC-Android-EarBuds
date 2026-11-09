package com.ggec.earbuds.ui.ai.call;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.DeferredSettingsRepository;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleItem;

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
    private MemorySettingsRepository settingsRepository;
    private AiCallViewModel viewModel;

    @Before
    public void setUp() {
        fakeSession = new FakeVoiceSession();
        fakeTicker = new FakeTicker();
        settingsRepository = new MemorySettingsRepository(AiFixtures.readySettings());
        AiVoiceSessionFactory factory = () -> fakeSession;
        AiCallUiMapper mapper = new AiCallUiMapper();
        viewModel = new AiCallViewModel(factory, mapper, new LoadAiSettings(settingsRepository), fakeTicker);
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
    public void onSnapshotUpdatesSubtitleItemsFlow() {
        viewModel.start();
        assertEquals(List.of(), viewModel.subtitleItems().getValue());

        List<AiVoiceSnapshot.Turn> turns = List.of(
                new AiVoiceSnapshot.Turn("1", true, "你好", true, 1000L),
                new AiVoiceSnapshot.Turn("2", false, "很高兴为你服务", false, 2000L)
        );
        AiVoiceSnapshot snapshot = new AiVoiceSnapshot(
                AiVoiceSnapshot.State.SPEAKING, turns, 0.5f, null, VoiceDuplex.FULL_DUPLEX, false);

        fakeSession.listener.onSnapshot(snapshot);

        List<AiSubtitleItem> items = viewModel.subtitleItems().getValue();
        assertEquals(2, items.size());
        assertEquals("1", items.get(0).id());
        assertTrue(items.get(0).isUser());
        assertEquals("你好", items.get(0).text());
        assertTrue(items.get(0).isFinal());

        assertEquals("2", items.get(1).id());
        assertFalse(items.get(1).isUser());
        assertEquals("很高兴为你服务", items.get(1).text());
        assertFalse(items.get(1).isFinal());
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
    public void initializesDefaultDuplexFromConfiguredSettings() {
        MemorySettingsRepository fullRepo = new MemorySettingsRepository(
                AiFixtures.settings(null,
                        AiFixtures.voice(AiVoiceModel.QWEN, "sk-qwen")
                                .withDefaultDuplex(VoiceDuplex.FULL_DUPLEX)));
        AiCallViewModel fullVm = new AiCallViewModel(
                () -> fakeSession, new AiCallUiMapper(), new LoadAiSettings(fullRepo), fakeTicker);

        assertEquals(VoiceDuplex.FULL_DUPLEX, fullVm.duplex());
        assertEquals(VoiceDuplex.FULL_DUPLEX, fullVm.uiState().getValue().duplex());

        fullVm.start();
        assertEquals("未手动覆盖时，交由 session 自行读取配置默认模式，不硬写 setDuplex",
                null, fakeSession.explicitDuplexSet);
    }

    @Test
    public void preStartToggleOverridesAsyncLoadedDefaultDuplex() {
        DeferredSettingsRepository deferred = new DeferredSettingsRepository();
        AiCallViewModel vm = new AiCallViewModel(
                () -> fakeSession, new AiCallUiMapper(), new LoadAiSettings(deferred), fakeTicker);

        vm.setDuplex(VoiceDuplex.FULL_DUPLEX);
        deferred.complete(AiFixtures.settings(null,
                AiFixtures.voice(AiVoiceModel.QWEN, "sk-qwen")
                        .withDefaultDuplex(VoiceDuplex.HALF_DUPLEX)));

        assertEquals("用户在异步读取完成前已手动切换，异步结果不得覆盖用户选择",
                VoiceDuplex.FULL_DUPLEX, vm.duplex());
        vm.start();
        assertEquals(VoiceDuplex.FULL_DUPLEX, fakeSession.explicitDuplexSet);
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
        boolean micMuted = false;
        boolean speakerphone = true;
        boolean interrupted = false;
        VoiceDuplex duplex = VoiceDuplex.defaultMode();
        VoiceDuplex explicitDuplexSet;
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
        public void interrupt() {
            this.interrupted = true;
        }
    }
}
