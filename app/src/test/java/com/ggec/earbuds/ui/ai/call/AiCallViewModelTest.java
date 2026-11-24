package com.ggec.earbuds.ui.ai.call;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.data.ai.voice.BluetoothStreamAudioSource;
import com.ggec.earbuds.data.ai.voice.ExternalPcmAudioSource;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiAudioSource;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSession;
import com.ggec.earbuds.domain.ai.voice.AiVoiceSnapshot;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeAudioSource;
import com.ggec.earbuds.testing.ai.FakeAudioSourceCatalog;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.ui.ai.call.subtitle.AiSubtitleItem;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * 通话页 ViewModel 单元测试：
 * 验证严格单向数据流、前/中置音频源切换、静音/双工预置透传与不可变状态投影。
 */
public class AiCallViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private FakeVoiceSession fakeSession;
    private FakeTicker fakeTicker;
    private FakeAudioSourceCatalog sourceCatalog;
    private FakeAudioSource phoneMic;
    private MemorySettingsRepository settingsRepo;
    private AiCallViewModel viewModel;

    @Before
    public void setUp() {
        fakeSession = new FakeVoiceSession();
        fakeTicker = new FakeTicker();
        phoneMic = new FakeAudioSource("android:builtin:default",
                AiAudioSource.Kind.BUILTIN_MIC, "手机麦克风", 16_000);
        sourceCatalog = new FakeAudioSourceCatalog()
                .withSource(phoneMic)
                .setDefaultSourceId("android:builtin:default");
        settingsRepo = new MemorySettingsRepository(AiSettings.EMPTY);
        viewModel = newViewModel(settingsRepo);
    }

    private AiCallViewModel newViewModel(AiSettingsRepository repo) {
        return new AiCallViewModel(
                initialDuplex -> {
                    fakeSession.initialDuplex = initialDuplex;
                    fakeSession.duplex = initialDuplex;
                    return fakeSession;
                },
                fakeTicker,
                new LoadAiSettings(repo),
                new ObserveAiSettings(repo),
                sourceCatalog);
    }

    @Test
    public void initialStateIsIdle() {
        assertFalse(viewModel.isStarted());
        AiCallUiState state = viewModel.uiState().getValue();
        assertEquals(AiCallUiState.Phase.IDLE, state.phase());
        assertEquals(R.string.ai_call_phase_idle, state.title());
        assertFalse(state.live());
        assertFalse(state.ended());
    }

    @Test
    public void startInitializesConnectingStateAndStartsSession() {
        assertFalse(viewModel.isStarted());
        viewModel.start();

        assertTrue(viewModel.isStarted());
        assertTrue(fakeSession.started);
        AiCallUiState state = viewModel.uiState().getValue();
        assertEquals(AiCallUiState.Phase.CONNECTING, state.phase());
        assertEquals(AiCallUiState.OutputMode.SPEAKER, state.outputMode());
        assertTrue(state.captionsVisible());
        assertEquals(0, state.durationSeconds());
        assertEquals(VoiceDuplex.HALF_DUPLEX, fakeSession.initialDuplex);
    }

    @Test
    public void retryStopsTheOldSessionAndStartsANewOne() {
        viewModel.start();

        viewModel.retry();

        assertTrue(fakeSession.stopped);
        assertTrue(viewModel.isStarted());
    }

    @Test
    public void hangUpTransitionsLiveCallToOffline() {
        viewModel.start();
        assertTrue(viewModel.uiState().getValue().live());

        viewModel.hangUp();

        assertFalse(viewModel.isStarted());
        assertTrue(fakeSession.stopped);
        AiCallUiState state = viewModel.uiState().getValue();
        assertEquals(AiCallUiState.Phase.OFFLINE, state.phase());
        assertEquals(R.string.ai_call_phase_ended, state.title());
        assertTrue(state.ended());
        assertFalse(state.live());
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
    public void outputModeUpdatesSessionAndUiState() {
        viewModel.start();
        assertEquals(AiCallUiState.OutputMode.SPEAKER, viewModel.uiState().getValue().outputMode());

        viewModel.setOutputMode(AiCallUiState.OutputMode.BLUETOOTH);
        assertEquals(com.ggec.earbuds.domain.ai.port.AiAudioOutputRoute.BLUETOOTH, fakeSession.outputRoute);
        assertEquals(AiCallUiState.OutputMode.BLUETOOTH, viewModel.uiState().getValue().outputMode());

        viewModel.setOutputMode(AiCallUiState.OutputMode.EARPIECE);
        assertEquals(com.ggec.earbuds.domain.ai.port.AiAudioOutputRoute.EARPIECE, fakeSession.outputRoute);
        assertEquals(AiCallUiState.OutputMode.EARPIECE, viewModel.uiState().getValue().outputMode());

        viewModel.setOutputMode(AiCallUiState.OutputMode.MUTED);
        assertEquals(com.ggec.earbuds.domain.ai.port.AiAudioOutputRoute.MUTED, fakeSession.outputRoute);
        assertEquals(AiCallUiState.OutputMode.MUTED, viewModel.uiState().getValue().outputMode());
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
        assertEquals(VoiceDuplex.FULL_DUPLEX, fakeSession.initialDuplex);
        assertEquals("bt:sco:1", fakeSession.selectedSourceId);
    }

    @Test
    public void initialIdleStateAndStartedSessionReflectConfiguredFullDuplex() {
        MemorySettingsRepository fullRepo = new MemorySettingsRepository(
                AiFixtures.settings(null,
                        AiFixtures.voice(AiVoiceModel.QWEN, "sk-test")
                                .withDefaultDuplex(VoiceDuplex.FULL_DUPLEX)));
        AiCallViewModel fullVm = newViewModel(fullRepo);

        assertEquals("进入页面即展示设置中的全双工，而非先显示半双工",
                VoiceDuplex.FULL_DUPLEX, fullVm.uiState().getValue().duplex());

        fullVm.start();
        assertEquals(VoiceDuplex.FULL_DUPLEX, fullVm.uiState().getValue().duplex());
        assertEquals(VoiceDuplex.FULL_DUPLEX, fakeSession.initialDuplex);
    }

    @Test
    public void asyncSettingsLoadUpdatesIdleDuplexUnlessUserOverrodeIt() {
        CompletableFuture<AiSettings> pending = new CompletableFuture<>();
        MemorySettingsRepository backing = new MemorySettingsRepository(AiSettings.EMPTY);
        AiSettingsRepository deferredRepo = new DeferredSettingsRepository(backing, pending);
        AiCallViewModel deferredVm = newViewModel(deferredRepo);
        assertEquals(VoiceDuplex.HALF_DUPLEX, deferredVm.uiState().getValue().duplex());

        pending.complete(AiFixtures.settings(null,
                AiFixtures.voice(AiVoiceModel.QWEN, "sk-test")
                        .withDefaultDuplex(VoiceDuplex.FULL_DUPLEX)));

        assertEquals(VoiceDuplex.FULL_DUPLEX, deferredVm.uiState().getValue().duplex());

        // 用户在通话前手动切回半双工后，后续设置变更不得覆盖用户本通选择
        deferredVm.setDuplex(VoiceDuplex.HALF_DUPLEX);
        backing.update(s -> s.withVoice(s.voice().withDefaultDuplex(VoiceDuplex.FULL_DUPLEX)));
        assertEquals(VoiceDuplex.HALF_DUPLEX, deferredVm.uiState().getValue().duplex());

        deferredVm.start();
        assertEquals(VoiceDuplex.HALF_DUPLEX, fakeSession.initialDuplex);
    }

    @Test
    public void micMenuSwitchesBetweenPhoneBluetoothAndMute() {
        assertFalse(viewModel.uiState().getValue().bluetoothMicAvailable());
        assertEquals(AiCallUiState.MicMode.PHONE_MIC, viewModel.uiState().getValue().micMode());

        // 未连接蓝牙设备时选择蓝牙 MIC 提示不可用且不改变当前状态
        viewModel.selectBluetoothMic();
        assertEquals(Integer.valueOf(R.string.ai_call_mic_bluetooth_unavailable),
                viewModel.toasts().getValue());
        assertEquals(AiCallUiState.MicMode.PHONE_MIC, viewModel.uiState().getValue().micMode());

        // 蓝牙设备就绪后可切换到蓝牙 MIC，再静音、再切回手机 MIC 自动解除静音
        FakeAudioSource btMic = new FakeAudioSource(BluetoothStreamAudioSource.SOURCE_ID,
                AiAudioSource.Kind.BLUETOOTH_BLE, "HS02", 16_000);
        sourceCatalog.registerExternal(btMic);
        assertTrue(viewModel.uiState().getValue().bluetoothMicAvailable());

        viewModel.selectBluetoothMic();
        assertEquals(AiCallUiState.MicMode.BLUETOOTH_MIC, viewModel.uiState().getValue().micMode());

        viewModel.muteMic();
        assertEquals(AiCallUiState.MicMode.MUTED, viewModel.uiState().getValue().micMode());
        assertTrue(viewModel.uiState().getValue().micMuted());

        viewModel.selectPhoneMic();
        assertEquals(AiCallUiState.MicMode.PHONE_MIC, viewModel.uiState().getValue().micMode());
        assertFalse(viewModel.uiState().getValue().micMuted());

        // 选中蓝牙 MIC 后再断开蓝牙，IDLE 态自动回退到手机 MIC
        viewModel.selectBluetoothMic();
        assertEquals(AiCallUiState.MicMode.BLUETOOTH_MIC, viewModel.uiState().getValue().micMode());
        sourceCatalog.unregisterExternal(BluetoothStreamAudioSource.SOURCE_ID);
        assertFalse(viewModel.uiState().getValue().bluetoothMicAvailable());
        assertEquals(AiCallUiState.MicMode.PHONE_MIC, viewModel.uiState().getValue().micMode());
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
        com.ggec.earbuds.domain.ai.port.AiAudioOutputRoute outputRoute =
                com.ggec.earbuds.domain.ai.port.AiAudioOutputRoute.SPEAKER;
        boolean interrupted = false;
        VoiceDuplex initialDuplex;
        VoiceDuplex duplex = VoiceDuplex.defaultMode();
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
        public void setOutputRoute(@NonNull com.ggec.earbuds.domain.ai.port.AiAudioOutputRoute route) {
            this.outputRoute = route;
        }

        @Override
        public void setDuplex(@NonNull VoiceDuplex duplex) {
            this.duplex = duplex;
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

    private static final class DeferredSettingsRepository implements AiSettingsRepository {
        private final MemorySettingsRepository delegate;
        private final CompletableFuture<AiSettings> pending;

        DeferredSettingsRepository(MemorySettingsRepository delegate, CompletableFuture<AiSettings> pending) {
            this.delegate = delegate;
            this.pending = pending;
        }

        @Override
        public AiSettings load() {
            return delegate.load();
        }

        @Override
        public CompletableFuture<AiSettings> loadAsync() {
            return pending;
        }

        @Override
        public boolean ready() {
            return pending.isDone();
        }

        @Override
        public AiSettings update(UnaryOperator<AiSettings> change) {
            return delegate.update(change);
        }

        @Override
        public AiPlatformConfig savePlatform(AiPlatformConfig platform, String apiKey) {
            return delegate.savePlatform(platform, apiKey);
        }

        @Override
        public AiSettings saveVoiceKey(AiVoiceModel model, String apiKey) {
            return delegate.saveVoiceKey(model, apiKey);
        }

        @Override
        public Subscription observe(Consumer<AiSettings> observer) {
            return delegate.observe(observer);
        }

        @Override
        public Subscription observePersistenceStatus(Consumer<AiSettingsPersistenceStatus> observer) {
            return delegate.observePersistenceStatus(observer);
        }

        @Override
        public Subscription observePersistenceFailures(Consumer<AiFailure> observer) {
            return delegate.observePersistenceFailures(observer);
        }

        @Override
        public CompletableFuture<Void> flush() {
            return delegate.flush();
        }
    }
}
