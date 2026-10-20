package com.ggec.earbuds.domain.ai.usecase;

import static com.ggec.earbuds.testing.ai.AiFixtures.model;
import static com.ggec.earbuds.testing.ai.AiFixtures.platform;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelFeature;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.policy.AiModelIdPolicy;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiModelGateway;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestGateway;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.FakeCatalog;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PlatformUseCasesTest {

    private final MemorySettingsRepository repository = new MemorySettingsRepository();
    private final ManualClock clock = new ManualClock();
    private final FakeCatalog catalog = new FakeCatalog();
    private final SavePlatform save = new SavePlatform(repository, catalog, new AiApiKeyPolicy(),
            new AiBaseUrlPolicy(catalog), clock);

    @Test
    public void savingABuiltInPlatformCreatesItOnceUnderItsTemplateId() {
        AiPlatformConfig first = save.execute(command(null, "deepseek", null, " sk-one ", null, null));
        AiPlatformConfig second = save.execute(command(null, "deepseek", null, "sk-two", "https://proxy.example.com/v1", null));

        assertEquals("deepseek", first.id());
        assertEquals("DeepSeek", first.name());
        assertEquals("sk-one", first.apiKey());
        assertEquals(AiProtocol.CHAT_COMPLETIONS, first.protocol());
        assertNull(first.baseUrl());
        assertEquals(1, repository.load().platforms().size());
        assertEquals("sk-two", second.apiKey());
        assertEquals("https://proxy.example.com", second.baseUrl());
    }

    @Test
    public void customPlatformsGetFreshIdsAndNeedANameAndAHost() {
        AiPlatformConfig a = save.execute(command(null, "openai-compatible", "公司网关", "k1", "https://gw.example.com", null));
        AiPlatformConfig b = save.execute(command(null, "openai-compatible", "家里的", "k2", "https://home.example.com", null));

        assertTrue(a.id().startsWith("custom-"));
        assertEquals(15, a.id().length());
        assertFalse(a.id().equals(b.id()));
        assertEquals("公司网关", a.name());
        assertEquals(2, repository.load().platforms().size());

        assertSaveError(command(null, "openai-compatible", " ", "k", "https://gw.example.com", null), AiValidationError.NAME_EMPTY);
        assertSaveError(command(null, "openai-compatible", "x", "k", null, null), AiValidationError.BASE_URL_REQUIRED);
    }

    @Test
    public void editingKeepsTheIdAndRejectsUnknownOnes() {
        AiPlatformConfig created = save.execute(command(null, "openai-compatible", "旧名", "k1", "https://gw.example.com", null));

        AiPlatformConfig edited = save.execute(command(created.id(), "openai-compatible", "新名", "k2",
                "https://gw.example.com", AiProtocol.RESPONSES));

        assertEquals(created.id(), edited.id());
        assertEquals("新名", edited.name());
        assertEquals(AiProtocol.RESPONSES, edited.protocol());
        assertEquals(1, repository.load().platforms().size());
        assertSaveError(command("custom-ghost", "openai-compatible", "x", "k", "https://gw.example.com", null),
                AiValidationError.PLATFORM_UNKNOWN);
    }

    @Test
    public void invalidInputIsRejectedBeforeAnythingIsSaved() {
        assertSaveError(command(null, "nobody", null, "k", null, null), AiValidationError.PLATFORM_UNKNOWN);
        assertSaveError(command(null, "deepseek", null, " ", null, null), AiValidationError.API_KEY_EMPTY);
        assertSaveError(command(null, "deepseek", null, "k", "http://insecure.example.com", null),
                AiValidationError.BASE_URL_NOT_HTTPS);
        assertSaveError(command(null, "deepseek", null, "k", null, AiProtocol.RESPONSES),
                AiValidationError.PROTOCOL_UNSUPPORTED);
        assertEquals(0, repository.updates);
    }

    @Test
    public void fetchingReplacesTheListKeepsManualModelsAndTimestampsIt() {
        repository.update(settings -> settings.withPlatform(platform("deepseek", "k")
                .withModels(List.of(model("old-fetched"), AiModel.manual("my-own")), 1L)));
        clock.now = 5_000L;
        FetchPlatformModels fetch = new FetchPlatformModels(repository,
                gateway(List.of(model("deepseek-chat"), model("deepseek-reasoner"), model("my-own"))), clock);

        AiPlatformConfig result = fetch.execute("deepseek");

        assertEquals(List.of("deepseek-chat", "deepseek-reasoner", "my-own"),
                result.models().stream().map(AiModel::id).toList());
        assertFalse("the fetched copy of a duplicate wins over the manual one", result.model("my-own").orElseThrow().manual());
        assertEquals(5_000L, result.modelsFetchedAtMillis());
        assertEquals(result, repository.load().platform("deepseek").orElseThrow());
    }

    @Test
    public void manualModelsSurviveARefetchThatDoesNotListThem() {
        repository.update(settings -> settings.withPlatform(platform("deepseek", "k")
                .withModels(List.of(AiModel.manual("my-own")), 1L)));

        AiPlatformConfig result = new FetchPlatformModels(repository, gateway(List.of(model("a"))), clock)
                .execute("deepseek");

        assertEquals(List.of("a", "my-own"), result.models().stream().map(AiModel::id).toList());
        assertTrue(result.model("my-own").orElseThrow().manual());
    }

    @Test
    public void fetchingClearsASelectionWhoseModelWentAway() {
        repository.update(settings -> AiFixtures.readySettings());

        new FetchPlatformModels(repository, gateway(List.of(model("deepseek-reasoner"))), clock).execute("deepseek");

        assertNull(repository.load().selection());
    }

    @Test
    public void fetchingKeepsASelectionThatStillExists() {
        repository.update(settings -> AiFixtures.readySettings());

        new FetchPlatformModels(repository, gateway(List.of(model("deepseek-chat"))), clock).execute("deepseek");

        assertEquals(new AiSelection("deepseek", "deepseek-chat"), repository.load().selection());
    }

    @Test
    public void fetchFailuresPropagateAndLeaveSettingsUntouched() {
        repository.update(settings -> settings.withPlatform(platform("deepseek", "k", "keep")));
        int before = repository.updates;
        AiModelGateway failing = platform -> {
            throw new AiFailure(AiFailureKind.AUTHENTICATION, "bad key");
        };

        try {
            new FetchPlatformModels(repository, failing, clock).execute("deepseek");
            fail("the failure must reach the caller");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.AUTHENTICATION, failure.kind());
        }
        assertEquals(before, repository.updates);
        assertEquals(1, repository.load().platform("deepseek").orElseThrow().models().size());
    }

    @Test
    public void fetchingRequiresAConfiguredPlatform() {
        repository.update(settings -> settings.withPlatform(platform("deepseek", "")));
        FetchPlatformModels fetch = new FetchPlatformModels(repository, gateway(List.of()), clock);

        assertValidation(() -> fetch.execute("deepseek"), AiValidationError.PLATFORM_NOT_CONFIGURED);
        assertValidation(() -> fetch.execute("nobody"), AiValidationError.PLATFORM_UNKNOWN);
    }

    @Test
    public void manualModelsAreValidatedAndDeduplicated() {
        repository.update(settings -> settings.withPlatform(platform("deepseek", "k", "deepseek-chat")));
        AddManualModel add = new AddManualModel(repository, new AiModelIdPolicy());

        AiModel added = add.execute("deepseek", " custom/model:7b ");
        AiModel again = add.execute("deepseek", "custom/model:7b");
        AiModel existing = add.execute("deepseek", "deepseek-chat");

        assertTrue(added.manual());
        assertEquals("custom/model:7b", added.id());
        assertEquals(added, again);
        assertFalse("an existing fetched model is not turned into a manual one", existing.manual());
        assertEquals(2, repository.load().platform("deepseek").orElseThrow().models().size());
        assertValidation(() -> add.execute("deepseek", "two words"), AiValidationError.MODEL_ID_INVALID);
        assertValidation(() -> add.execute("nobody", "x"), AiValidationError.PLATFORM_UNKNOWN);
    }

    @Test
    public void removingAModelClearsItsSelection() {
        repository.update(settings -> AiFixtures.readySettings());

        new RemoveModel(repository).execute("deepseek", "deepseek-chat");

        AiSettings settings = repository.load();
        assertEquals(List.of("deepseek-reasoner"),
                settings.platform("deepseek").orElseThrow().models().stream().map(AiModel::id).toList());
        assertNull(settings.selection());
    }

    @Test
    public void selectingNeedsAConfiguredPlatformAndAListedModel() {
        repository.update(settings -> settings
                .withPlatform(platform("deepseek", "k", "deepseek-chat"))
                .withPlatform(platform("openai", "", "gpt-4o")));
        SelectModel select = new SelectModel(repository);

        select.execute("deepseek", "deepseek-chat");
        assertEquals(new AiSelection("deepseek", "deepseek-chat"), repository.load().selection());
        assertValidation(() -> select.execute("deepseek", "nope"), AiValidationError.MODEL_UNKNOWN);
        assertValidation(() -> select.execute("openai", "gpt-4o"), AiValidationError.PLATFORM_NOT_CONFIGURED);
        assertEquals(new AiSelection("deepseek", "deepseek-chat"), repository.load().selection());
    }

    @Test
    public void removingAPlatformAlsoRemovesItsSelection() {
        repository.update(settings -> AiFixtures.readySettings());

        new RemovePlatform(repository).execute("deepseek");

        assertTrue(repository.load().platforms().isEmpty());
        assertNull(repository.load().selection());
    }

    @Test
    public void speedResultsAreStampedStoredAndForwarded() {
        repository.update(settings -> AiFixtures.readySettings());
        clock.now = 9_000L;
        CapturingSpeedGateway gateway = new CapturingSpeedGateway();
        RecordingObserver observer = new RecordingObserver();

        AiCancellable handle = new RunSpeedTest(repository, gateway, clock)
                .execute("deepseek", List.of("deepseek-chat", "deepseek-reasoner"), observer);
        gateway.listener.onStarted("deepseek-chat");
        gateway.listener.onResult(new AiSpeedResult("deepseek-chat", AiSpeedResult.Status.OK, 320, 700, null, null, 0));
        gateway.listener.onResult(new AiSpeedResult("deepseek-reasoner", AiSpeedResult.Status.TIMEOUT, -1, 25_000,
                AiFailureKind.TIMEOUT, null, 0));
        gateway.listener.onComplete(false);

        assertEquals(List.of("start:deepseek-chat", "result:deepseek-chat:9000", "result:deepseek-reasoner:9000", "done:false"),
                observer.log);
        AiPlatformConfig stored = repository.load().platform("deepseek").orElseThrow();
        assertEquals(320, stored.speedResults().get("deepseek-chat").firstTokenMillis());
        assertEquals(9_000L, stored.speedResults().get("deepseek-chat").testedAtMillis());
        assertEquals(AiSpeedResult.Status.TIMEOUT, stored.speedResults().get("deepseek-reasoner").status());
        handle.cancel();
        assertTrue(gateway.cancelled);
    }

    @Test
    public void speedTestsRejectUnknownModelsAndEmptySelections() {
        repository.update(settings -> AiFixtures.readySettings());
        RunSpeedTest run = new RunSpeedTest(repository, new CapturingSpeedGateway(), clock);

        assertValidation(() -> run.execute("deepseek", List.of(), new RecordingObserver()), AiValidationError.MODEL_UNKNOWN);
        assertValidation(() -> run.execute("deepseek", List.of("ghost"), new RecordingObserver()), AiValidationError.MODEL_UNKNOWN);
        assertValidation(() -> run.execute("nobody", List.of("x"), new RecordingObserver()), AiValidationError.PLATFORM_UNKNOWN);
    }

    @Test
    public void lateSpeedResultsForARemovedPlatformAreDropped() {
        repository.update(settings -> AiFixtures.readySettings());
        CapturingSpeedGateway gateway = new CapturingSpeedGateway();
        new RunSpeedTest(repository, gateway, clock).execute("deepseek", List.of("deepseek-chat"), new RecordingObserver());

        new RemovePlatform(repository).execute("deepseek");
        gateway.listener.onResult(new AiSpeedResult("deepseek-chat", AiSpeedResult.Status.OK, 1, 2, null, null, 0));

        assertTrue(repository.load().platforms().isEmpty());
    }

    @Test
    public void featuresSurviveTheMerge() {
        AiModel reasoning = new AiModel("r1", "R1", 64_000, 8_000, Set.of(AiModelFeature.REASONING), false);
        repository.update(settings -> settings.withPlatform(platform("deepseek", "k")));

        AiPlatformConfig result = new FetchPlatformModels(repository, gateway(List.of(reasoning)), clock)
                .execute("deepseek");

        assertTrue(result.models().get(0).reasoning());
        assertEquals(Integer.valueOf(64_000), result.models().get(0).contextWindow());
    }

    private static SavePlatform.Command command(String platformId, String templateId, String name, String key,
                                                String baseUrl, AiProtocol protocol) {
        return new SavePlatform.Command(platformId, templateId, name, key, baseUrl, protocol);
    }

    private static AiModelGateway gateway(List<AiModel> models) {
        return platform -> models;
    }

    private void assertSaveError(SavePlatform.Command command, AiValidationError expected) {
        assertValidation(() -> save.execute(command), expected);
    }

    private static void assertValidation(Runnable action, AiValidationError expected) {
        try {
            action.run();
            fail("expected " + expected);
        } catch (AiValidationException error) {
            assertEquals(expected, error.error());
        }
    }

    private static final class CapturingSpeedGateway implements AiSpeedTestGateway {
        AiSpeedTestListener listener;
        boolean cancelled;

        @Override
        public AiCancellable run(AiPlatformConfig platform, List<String> modelIds, AiSpeedTestListener listener) {
            this.listener = listener;
            return () -> cancelled = true;
        }
    }

    private static final class RecordingObserver implements AiSpeedTestListener {
        final List<String> log = new ArrayList<>();

        @Override
        public void onStarted(String modelId) {
            log.add("start:" + modelId);
        }

        @Override
        public void onResult(AiSpeedResult result) {
            log.add("result:" + result.modelId() + ":" + result.testedAtMillis());
        }

        @Override
        public void onComplete(boolean cancelled) {
            log.add("done:" + cancelled);
        }
    }
}
