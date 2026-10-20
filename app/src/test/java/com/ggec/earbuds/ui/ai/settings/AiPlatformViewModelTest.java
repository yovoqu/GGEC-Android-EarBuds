package com.ggec.earbuds.ui.ai.settings;

import static com.ggec.earbuds.testing.ai.AiFixtures.model;
import static com.ggec.earbuds.testing.ai.AiFixtures.platform;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.policy.AiModelIdPolicy;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;
import com.ggec.earbuds.domain.ai.usecase.AddManualModel;
import com.ggec.earbuds.domain.ai.usecase.FetchPlatformModels;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.RemoveModel;
import com.ggec.earbuds.domain.ai.usecase.RemovePlatform;
import com.ggec.earbuds.domain.ai.usecase.RunSpeedTest;
import com.ggec.earbuds.domain.ai.usecase.SavePlatform;
import com.ggec.earbuds.domain.ai.usecase.SelectModel;
import com.ggec.earbuds.testing.ai.FakeCatalog;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.QueueExecutor;
import com.ggec.earbuds.testing.ai.ScriptedModelGateway;
import com.ggec.earbuds.testing.ai.ScriptedSpeedGateway;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

public class AiPlatformViewModelTest {

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final MemorySettingsRepository settings = new MemorySettingsRepository();
    private final ManualClock clock = new ManualClock();
    private final FakeCatalog catalog = new FakeCatalog();
    private final ScriptedModelGateway models = new ScriptedModelGateway();
    private final ScriptedSpeedGateway speed = new ScriptedSpeedGateway();
    private final List<AiPlatformEvent> events = new ArrayList<>();

    private AiPlatformViewModel open(String platformId) {
        return open(platformId, Runnable::run);
    }

    private AiPlatformViewModel open(String platformId, Executor io) {
        AiPlatformServices services = new AiPlatformServices(
                new LoadAiSettings(settings),
                new ObserveAiSettings(settings),
                new LoadPlatformTemplates(catalog),
                new SavePlatform(settings, catalog, new AiApiKeyPolicy(), new AiBaseUrlPolicy(catalog), clock),
                new FetchPlatformModels(settings, models, clock),
                new AddManualModel(settings, new AiModelIdPolicy()),
                new RemoveModel(settings),
                new RemovePlatform(settings),
                new SelectModel(settings),
                new RunSpeedTest(settings, speed, clock),
                new AiApiKeyPolicy(),
                new AiBaseUrlPolicy(catalog),
                new AiModelIdPolicy());
        AiPlatformViewModel viewModel = new AiPlatformViewModel(platformId, services, new AiPlatformUiMapper(), io);
        viewModel.events().observeForever(events::add);
        return viewModel;
    }

    private void configure(String platformId, String... modelIds) {
        AiPlatformConfig config = platform(platformId, "sk-test-key-1234", modelIds);
        settings.update(s -> s.withPlatform(config.withModels(config.models(), 5L)));
    }

    private List<Integer> messages() {
        return events.stream().map(AiPlatformEvent::message).toList();
    }

    private static List<String> ids(AiPlatformUiState state) {
        return state.models().rows().stream().map(AiPlatformUiState.ModelRow::id).toList();
    }

    private static AiPlatformUiState.ModelRow row(AiPlatformUiState state, String modelId) {
        return state.models().rows().stream().filter(r -> r.id().equals(modelId)).findFirst().orElseThrow();
    }

    private static AiSpeedResult ok(String modelId, long firstToken, long total) {
        return new AiSpeedResult(modelId, AiSpeedResult.Status.OK, firstToken, total, null, null, 0);
    }

    // ---- 打开页面 ----

    @Test
    public void aBuiltInPlatformThatWasNeverConfiguredShowsAnEmptyDraft() {
        AiPlatformUiState state = open("deepseek").uiState().getValue();

        assertTrue(state.loaded());
        assertFalse(state.missing());
        assertEquals("DeepSeek", state.name());
        assertFalse(state.custom());
        assertFalse(state.configured());
        assertEquals("", state.maskedKey());
        assertEquals("https://api.deepseek.com", state.baseUrl());
        assertTrue(state.defaultBaseUrl());
        assertEquals(List.of(AiProtocol.CHAT_COMPLETIONS, AiProtocol.MESSAGES), state.protocols());
        assertEquals(0, state.models().totalCount());
        assertEquals("without a key there is nothing to fetch with", 0, models.fetches);
    }

    @Test
    public void unknownPlatformsAndCustomOnesWithoutConfigAreMissing() {
        assertTrue(open("nobody").uiState().getValue().missing());
        assertTrue(open("custom-1234abcd").uiState().getValue().missing());
        assertTrue(open("openai-compatible").uiState().getValue().missing());
    }

    @Test
    public void aCustomPlatformRemovedElsewhereBecomesMissing() {
        settings.update(s -> s.withPlatform(custom("custom-1", "公司网关")));
        AiPlatformViewModel viewModel = open("custom-1");
        assertFalse(viewModel.uiState().getValue().missing());

        settings.update(s -> s.withoutPlatform("custom-1"));

        assertTrue(viewModel.uiState().getValue().missing());
    }

    // ---- 连接信息 ----

    @Test
    public void savingTheFirstKeyCreatesThePlatformThenFetchesModelsOnce() {
        models.models = List.of(model("deepseek-chat"), model("deepseek-reasoner"));
        clock.now = 7_000L;
        AiPlatformViewModel viewModel = open("deepseek");

        assertNull(viewModel.saveKey(" sk-abc123xyz "));

        AiPlatformUiState state = viewModel.uiState().getValue();
        assertTrue(state.configured());
        assertEquals("sk-••••••••3xyz", state.maskedKey());
        assertEquals(1, models.fetches);
        assertEquals(2, state.models().totalCount());
        assertEquals(AiPlatformUiState.Fetch.Status.IDLE, state.models().fetch().status());
        assertEquals(7_000L, state.models().fetchedAtMillis());
        assertTrue(messages().contains(R.string.ai_key_saved));
        assertTrue(messages().contains(R.string.ai_fetch_done));
        assertEquals("sk-abc123xyz", settings.load().platform("deepseek").orElseThrow().apiKey());
    }

    @Test
    public void invalidKeysAreRejectedBeforeAnythingIsSaved() {
        AiPlatformViewModel viewModel = open("deepseek");

        assertEquals(AiValidationError.API_KEY_EMPTY, viewModel.saveKey("  "));
        assertEquals(AiValidationError.API_KEY_INVALID, viewModel.saveKey("sk with space"));
        assertEquals(AiValidationError.PLATFORM_UNKNOWN, open("nobody").saveKey("sk-1"));
        assertEquals(0, settings.updates);
    }

    @Test
    public void theBaseUrlNeedsAKeyFirstAndMustBeHttps() {
        AiPlatformViewModel noKey = open("deepseek");
        assertEquals(AiValidationError.API_KEY_EMPTY, noKey.saveBaseUrl("https://x.example.com"));

        configure("deepseek", "deepseek-chat");
        AiPlatformViewModel viewModel = open("deepseek");
        assertEquals(AiValidationError.BASE_URL_NOT_HTTPS, viewModel.saveBaseUrl("http://x.example.com"));
        assertEquals(AiValidationError.BASE_URL_INVALID, viewModel.saveBaseUrl("ftp://x.example.com"));
    }

    @Test
    public void changingTheHostDropsTheOldModelsAndFetchesFromTheNewOne() {
        configure("deepseek", "old-model");
        models.models = List.of(model("proxy-model"));
        AiPlatformViewModel viewModel = open("deepseek");

        assertNull(viewModel.saveBaseUrl("proxy.example.com/v1"));

        AiPlatformUiState state = viewModel.uiState().getValue();
        assertEquals("https://proxy.example.com", state.baseUrl());
        assertFalse(state.defaultBaseUrl());
        assertEquals(List.of("proxy-model"), ids(state));
        assertEquals(1, models.fetches);

        assertNull(viewModel.saveBaseUrl(""));
        assertTrue(viewModel.uiState().getValue().defaultBaseUrl());
        assertEquals("https://api.deepseek.com", viewModel.uiState().getValue().baseUrl());
    }

    @Test
    public void customPlatformsNeedAHostAndCanBeRenamed() {
        settings.update(s -> s.withPlatform(custom("custom-1", "公司网关")));
        AiPlatformViewModel viewModel = open("custom-1");

        AiPlatformUiState before = viewModel.uiState().getValue();
        assertTrue(before.custom());
        assertEquals("公司网关", before.name());
        assertEquals("https://gw.example.com", before.baseUrl());

        assertEquals(AiValidationError.BASE_URL_REQUIRED, viewModel.saveBaseUrl(""));
        assertEquals(AiValidationError.NAME_EMPTY, viewModel.saveName("  "));
        assertNull(viewModel.saveName(" 新名字 "));

        assertEquals("新名字", viewModel.uiState().getValue().name());
        assertEquals("the models survive a rename", List.of("m1"), ids(viewModel.uiState().getValue()));
    }

    @Test
    public void protocolChangesAreCheckedAgainstTheTemplate() {
        configure("deepseek", "deepseek-chat");
        AiPlatformViewModel viewModel = open("deepseek");

        assertEquals(AiValidationError.PROTOCOL_UNSUPPORTED, viewModel.setProtocol(AiProtocol.RESPONSES));
        assertNull(viewModel.setProtocol(AiProtocol.MESSAGES));

        assertEquals(AiProtocol.MESSAGES, viewModel.uiState().getValue().protocol());
        int updates = settings.updates;
        assertNull(viewModel.setProtocol(AiProtocol.MESSAGES));
        assertEquals("choosing the current protocol again saves nothing", updates, settings.updates);
    }

    // ---- 拉取模型 ----

    @Test
    public void aFailedFetchIsShownInlineAndCanBeRetried() {
        settings.update(s -> s.withPlatform(platform("deepseek", "sk-key")));
        models.failure = new AiFailure(AiFailureKind.AUTHENTICATION, "bad key");

        AiPlatformViewModel viewModel = open("deepseek");

        AiPlatformUiState.Fetch failed = viewModel.uiState().getValue().models().fetch();
        assertEquals(AiPlatformUiState.Fetch.Status.FAILED, failed.status());
        assertEquals(AiFailureKind.AUTHENTICATION, failed.failureKind());
        assertEquals("bad key", failed.detail());
        assertEquals("a platform that was never fetched is fetched when its page opens", 1, models.fetches);

        settings.update(s -> s.withPreferences(s.preferences().withShowReasoning(false)));
        assertEquals("unrelated changes never retrigger the automatic fetch", 1, models.fetches);

        models.failure = null;
        models.models = List.of(model("a"));
        viewModel.fetchModels();

        assertEquals(AiPlatformUiState.Fetch.Status.IDLE, viewModel.uiState().getValue().models().fetch().status());
        assertEquals(List.of("a"), ids(viewModel.uiState().getValue()));
        assertEquals(2, models.fetches);
    }

    @Test
    public void aPlatformThatReturnsNoModelsIsNotFetchedAgainAutomatically() {
        configureWithoutFetching();
        models.models = List.of();

        AiPlatformViewModel viewModel = open("deepseek");

        assertEquals(1, models.fetches);
        assertTrue(messages().contains(R.string.ai_fetch_empty));
        assertTrue(viewModel.uiState().getValue().models().fetchedAtMillis() > 0);
        open("deepseek");
        assertEquals("fetch time is remembered, so the next visit stays quiet", 1, models.fetches);
    }

    @Test
    public void fetchingAgainWhileOneIsRunningIsIgnored() {
        QueueExecutor io = new QueueExecutor();
        configure("deepseek", "x");
        models.models = List.of(model("a"));
        AiPlatformViewModel viewModel = open("deepseek", io);
        io.drain();

        viewModel.fetchModels();
        viewModel.fetchModels();
        assertEquals(AiPlatformUiState.Fetch.Status.RUNNING,
                viewModel.uiState().getValue().models().fetch().status());
        io.drain();

        assertEquals("the second tap joined the first", 1, models.fetches);
        assertEquals(List.of("a"), ids(viewModel.uiState().getValue()));
    }

    // ---- 列表 ----

    @Test
    public void searchAndSortShapeTheModelList() {
        AiPlatformConfig base = platform("deepseek", "k", "alpha", "bravo-chat", "charlie", "delta-chat", "echo",
                "foxtrot", "golf", "hotel", "india", "juliet");
        settings.update(s -> s.withPlatform(base.withModels(base.models(), 5L)
                .withSpeedResult(ok("bravo-chat", 500, 900))
                .withSpeedResult(ok("delta-chat", 200, 800))
                .withSpeedResult(new AiSpeedResult("alpha", AiSpeedResult.Status.FAILED, -1, 300,
                        AiFailureKind.AUTHENTICATION, null, 1))
                .withSpeedResult(new AiSpeedResult("echo", AiSpeedResult.Status.TIMEOUT, -1, 25_000,
                        AiFailureKind.TIMEOUT, null, 1))));
        AiPlatformViewModel viewModel = open("deepseek");

        AiPlatformUiState.Models plain = viewModel.uiState().getValue().models();
        assertTrue(plain.searchable());
        assertTrue(plain.hasSpeedResults());
        assertEquals(10, plain.rows().size());
        assertEquals("alpha", plain.rows().get(0).id());

        viewModel.setQuery("CHAT");
        assertEquals(List.of("bravo-chat", "delta-chat"), ids(viewModel.uiState().getValue()));
        assertEquals(10, viewModel.uiState().getValue().models().totalCount());

        viewModel.setQuery("");
        viewModel.setSortBySpeed(true);
        assertEquals(List.of("delta-chat", "bravo-chat", "charlie", "foxtrot", "golf", "hotel", "india", "juliet",
                "alpha", "echo"), ids(viewModel.uiState().getValue()));
        AiPlatformUiState.Speed fastest = row(viewModel.uiState().getValue(), "delta-chat").speed();
        assertEquals(AiPlatformUiState.Speed.State.OK, fastest.state());
        assertEquals(200, fastest.firstTokenMillis());
        assertEquals(AiPlatformUiState.Speed.State.FAILED, row(viewModel.uiState().getValue(), "alpha").speed().state());
        assertEquals(AiPlatformUiState.Speed.State.TIMEOUT, row(viewModel.uiState().getValue(), "echo").speed().state());
        assertEquals(AiPlatformUiState.Speed.State.NONE, row(viewModel.uiState().getValue(), "golf").speed().state());
    }

    @Test
    public void smallListsAreNotSearchableUntilAQueryIsActive() {
        configure("deepseek", "a", "b");
        AiPlatformViewModel viewModel = open("deepseek");
        assertFalse(viewModel.uiState().getValue().models().searchable());

        viewModel.setQuery("zzz");

        assertTrue("an active query keeps the box visible so it can be cleared",
                viewModel.uiState().getValue().models().searchable());
        assertTrue(viewModel.uiState().getValue().models().rows().isEmpty());
    }

    @Test
    public void rowsCarryModelFeaturesAndTheSelection() {
        AiModel reasoning = new AiModel("r1", "R1", 64_000, 8_000, Set.of(AiModelFeature.REASONING), false);
        AiModel vision = new AiModel("v1", "V1", null, null, Set.of(AiModelFeature.VISION), false);
        AiPlatformConfig base = platform("deepseek", "k").withModels(List.of(reasoning, vision, AiModel.manual("mine")), 5L);
        settings.update(s -> s.withPlatform(base).withSelection(new AiSelection("deepseek", "v1")));

        AiPlatformUiState state = open("deepseek").uiState().getValue();

        AiPlatformUiState.ModelRow first = state.models().rows().get(0);
        assertTrue(first.reasoning());
        assertFalse(first.vision());
        assertEquals(64_000, first.contextWindow());
        assertTrue(state.models().rows().get(1).vision());
        assertEquals(0, state.models().rows().get(1).contextWindow());
        assertTrue(state.models().rows().get(1).selected());
        assertTrue(state.models().rows().get(2).manual());
        assertTrue("a platform that owns the selected model is the one in use", state.active());
    }

    // ---- 测速 ----

    @Test
    public void aSpeedTestReportsProgressRowByRowThenOffersTheFastestModel() {
        configure("deepseek", "a", "b", "c");
        AiPlatformViewModel viewModel = open("deepseek");

        viewModel.startSpeedTest();

        assertEquals(List.of("a", "b", "c"), speed.lastModelIds());
        AiPlatformUiState queued = viewModel.uiState().getValue();
        assertEquals(new AiPlatformUiState.SpeedRun(true, 0, 3), queued.models().speed());
        assertEquals(AiPlatformUiState.Speed.State.QUEUED, row(queued, "a").speed().state());
        assertNull("no banner while a run is in progress", queued.models().fastest());

        speed.lastListener().onStarted("a");
        assertEquals(AiPlatformUiState.Speed.State.RUNNING, row(viewModel.uiState().getValue(), "a").speed().state());
        assertEquals(AiPlatformUiState.Speed.State.QUEUED, row(viewModel.uiState().getValue(), "b").speed().state());

        speed.lastListener().onResult(ok("a", 300, 700));
        assertEquals(1, viewModel.uiState().getValue().models().speed().done());
        assertEquals(AiPlatformUiState.Speed.State.OK, row(viewModel.uiState().getValue(), "a").speed().state());

        speed.lastListener().onStarted("b");
        speed.lastListener().onResult(new AiSpeedResult("b", AiSpeedResult.Status.FAILED, -1, 900,
                AiFailureKind.PERMISSION, "denied", 0));
        speed.lastListener().onStarted("c");
        speed.lastListener().onResult(ok("c", 150, 400));
        speed.lastListener().onComplete(false);

        AiPlatformUiState done = viewModel.uiState().getValue();
        assertFalse(done.models().speed().active());
        assertEquals("c", done.models().fastest().modelId());
        assertEquals(150, done.models().fastest().firstTokenMillis());
        assertEquals(AiPlatformUiState.Speed.State.FAILED, row(done, "b").speed().state());
        assertEquals(AiFailureKind.PERMISSION, row(done, "b").speed().failureKind());
        assertEquals("results are persisted as they arrive", 3,
                settings.load().platform("deepseek").orElseThrow().speedResults().size());

        viewModel.useFastest();

        assertEquals(new AiSelection("deepseek", "c"), settings.load().selection());
        assertNull(viewModel.uiState().getValue().models().fastest());
        assertTrue(row(viewModel.uiState().getValue(), "c").selected());
        assertEquals(R.string.ai_model_switched, events.get(events.size() - 1).message());
    }

    @Test
    public void aSpeedTestOnlyCoversTheVisibleModelsAndIsCapped() {
        String[] names = new String[30];
        for (int i = 0; i < names.length; i++) {
            names[i] = String.format("m%02d", i);
        }
        configure("deepseek", names);
        AiPlatformViewModel viewModel = open("deepseek");

        viewModel.startSpeedTest();

        assertEquals(AiPlatformViewModel.MAX_SPEED_TEST_MODELS, speed.lastModelIds().size());
        assertEquals("m00", speed.lastModelIds().get(0));
        assertEquals(R.string.ai_speed_capped, events.get(events.size() - 1).message());
        assertEquals("24", events.get(events.size() - 1).argument());
        speed.lastListener().onComplete(true);

        viewModel.setQuery("m2");
        viewModel.startSpeedTest();
        assertEquals("only the models matching the search are tested, in list order",
                List.of("m20", "m21", "m22", "m23", "m24", "m25", "m26", "m27", "m28", "m29"),
                speed.lastModelIds());
    }

    @Test
    public void stoppingASpeedTestCancelsTheGatewayAndWaitsForItsCompletion() {
        configure("deepseek", "a", "b");
        AiPlatformViewModel viewModel = open("deepseek");
        viewModel.startSpeedTest();

        viewModel.stopSpeedTest();

        assertTrue(speed.lastHandle().cancelled);
        assertTrue("the run is over only when the gateway says so",
                viewModel.uiState().getValue().models().speed().active());
        speed.lastListener().onComplete(true);
        assertFalse(viewModel.uiState().getValue().models().speed().active());
        assertEquals(AiPlatformUiState.Speed.State.NONE, row(viewModel.uiState().getValue(), "a").speed().state());
    }

    @Test
    public void stoppingBeforeTheGatewayHandleExistsCancelsItAsSoonAsItArrives() {
        QueueExecutor io = new QueueExecutor();
        configure("deepseek", "a", "b");
        AiPlatformViewModel viewModel = open("deepseek", io);
        io.drain();
        viewModel.startSpeedTest();

        viewModel.stopSpeedTest();
        io.drain();

        assertTrue(speed.lastHandle().cancelled);
    }

    @Test
    public void closingThePageCancelsARunningSpeedTestAndIgnoresLateCallbacks() {
        configure("deepseek", "a", "b");
        AiPlatformViewModel viewModel = open("deepseek");
        viewModel.startSpeedTest();
        AiPlatformUiState before = viewModel.uiState().getValue();

        viewModel.onCleared();
        speed.lastListener().onResult(ok("a", 1, 2));

        assertTrue(speed.lastHandle().cancelled);
        assertEquals(before, viewModel.uiState().getValue());
        viewModel.startSpeedTest();
        assertEquals("a cleared view model starts nothing", 1, speed.runs.size());
    }

    @Test
    public void callbacksFromAnEarlierRunDoNotDisturbTheNextOne() {
        configure("deepseek", "a", "b");
        AiPlatformViewModel viewModel = open("deepseek");
        viewModel.startSpeedTest();
        AiSpeedTestListener firstRun = speed.lastListener();
        firstRun.onComplete(false);
        viewModel.startSpeedTest();

        firstRun.onStarted("a");
        firstRun.onResult(ok("a", 1, 2));
        firstRun.onComplete(false);

        AiPlatformUiState state = viewModel.uiState().getValue();
        assertTrue(state.models().speed().active());
        assertEquals(0, state.models().speed().done());
        assertEquals(AiPlatformUiState.Speed.State.QUEUED, row(state, "a").speed().state());
    }

    @Test
    public void aSpeedTestNeedsAKeyAndModels() {
        AiPlatformViewModel viewModel = open("deepseek");
        viewModel.startSpeedTest();
        assertEquals(R.string.ai_fetch_need_key, events.get(0).message());

        settings.update(s -> s.withPlatform(platform("deepseek", "k").withModels(List.of(), 5L)));
        viewModel.startSpeedTest();
        assertEquals(R.string.ai_speed_need_models, events.get(1).message());
        assertEquals(0, speed.runs.size());
    }

    @Test
    public void aGatewayThatRefusesToStartResetsTheRunAndExplainsWhy() {
        configure("deepseek", "a");
        speed.failOnRun = new AiFailure(AiFailureKind.NETWORK, "offline");
        AiPlatformViewModel viewModel = open("deepseek");

        viewModel.startSpeedTest();

        assertFalse(viewModel.uiState().getValue().models().speed().active());
        assertEquals(R.string.ai_failure_network, events.get(events.size() - 1).message());
        speed.failOnRun = null;
        viewModel.startSpeedTest();
        assertTrue("the next attempt works", viewModel.uiState().getValue().models().speed().active());
    }

    // ---- 手动模型、选用、移除 ----

    @Test
    public void manualModelNamesAreValidatedAndAdded() {
        configure("deepseek", "x");
        AiPlatformViewModel viewModel = open("deepseek");

        assertEquals(AiValidationError.MODEL_ID_EMPTY, viewModel.addModel(" "));
        assertEquals(AiValidationError.MODEL_ID_INVALID, viewModel.addModel("two words"));
        assertNull(viewModel.addModel(" my/model:7b "));

        assertTrue(row(viewModel.uiState().getValue(), "my/model:7b").manual());
        assertEquals(2, viewModel.uiState().getValue().models().totalCount());
    }

    @Test
    public void manualModelsCannotBeAddedToAnUnconfiguredPlatform() {
        assertEquals(AiValidationError.PLATFORM_NOT_CONFIGURED, open("deepseek").addModel("some-model"));
    }

    @Test
    public void selectingAModelSwitchesTheConversationModel() {
        configure("deepseek", "deepseek-chat", "deepseek-reasoner");
        AiPlatformViewModel viewModel = open("deepseek");

        viewModel.selectModel("deepseek-reasoner");

        assertEquals(new AiSelection("deepseek", "deepseek-reasoner"), settings.load().selection());
        assertTrue(row(viewModel.uiState().getValue(), "deepseek-reasoner").selected());
        assertFalse(row(viewModel.uiState().getValue(), "deepseek-chat").selected());
        assertEquals(R.string.ai_model_switched, events.get(0).message());
        assertEquals("deepseek-reasoner", events.get(0).argument());

        viewModel.selectModel("ghost");
        assertEquals(R.string.ai_error_model_unknown, events.get(1).message());
        assertEquals(new AiSelection("deepseek", "deepseek-reasoner"), settings.load().selection());
    }

    @Test
    public void removingAModelUpdatesTheListAndClearsItsSelection() {
        configure("deepseek", "keep", "drop");
        settings.update(s -> s.withSelection(new AiSelection("deepseek", "drop")));
        AiPlatformViewModel viewModel = open("deepseek");

        viewModel.removeModel("drop");

        assertEquals(List.of("keep"), ids(viewModel.uiState().getValue()));
        assertNull(settings.load().selection());
        assertFalse(viewModel.uiState().getValue().active());
    }

    @Test
    public void removingThePlatformAsksThePageToCloseAndForgetsTheKey() {
        configure("deepseek", "a");
        settings.update(s -> s.withSelection(new AiSelection("deepseek", "a")));
        AiPlatformViewModel viewModel = open("deepseek");

        viewModel.removePlatform();

        assertTrue(settings.load().platforms().isEmpty());
        assertNull(settings.load().selection());
        assertEquals(AiPlatformEvent.Type.FINISH, events.get(events.size() - 1).type());
        assertFalse("a built-in platform falls back to its empty draft", viewModel.uiState().getValue().configured());
    }

    @Test
    public void thePageStaysUsableWhileSettingsLoadInTheBackground() {
        QueueExecutor io = new QueueExecutor();
        configure("deepseek", "a");
        AiPlatformViewModel viewModel = open("deepseek", io);

        AiPlatformUiState loading = viewModel.uiState().getValue();
        assertFalse(loading.loaded());
        assertFalse(loading.missing());
        io.drain();

        assertTrue(viewModel.uiState().getValue().loaded());
        assertEquals(List.of("a"), ids(viewModel.uiState().getValue()));
    }

    // ---- 夹具 ----

    private void configureWithoutFetching() {
        settings.update(s -> s.withPlatform(platform("deepseek", "sk-key")));
    }

    private static AiPlatformConfig custom(String id, String name) {
        return new AiPlatformConfig(id, "openai-compatible", name, "https://gw.example.com", "sk-custom-key",
                AiProtocol.CHAT_COMPLETIONS, List.of(model("m1")), 5L, Map.of());
    }
}
