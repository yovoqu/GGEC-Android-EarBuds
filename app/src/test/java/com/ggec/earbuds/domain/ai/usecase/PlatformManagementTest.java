package com.ggec.earbuds.domain.ai.usecase;

import static com.ggec.earbuds.testing.ai.AiFixtures.model;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.domain.ai.model.AiFetchedModels;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelKind;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.policy.AiModelDraftPolicy;
import com.ggec.earbuds.testing.ai.FakeCatalog;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.ScriptedModelGateway;

import org.junit.Test;

import java.util.List;
import java.util.Set;

/** 新平台页的写操作：添加、改名、改连接参数、模型增删改、当前模型、单模型参数。 */
public class PlatformManagementTest {

    private final MemorySettingsRepository repository = new MemorySettingsRepository();
    private final ManualClock clock = new ManualClock();
    private final FakeCatalog catalog = new FakeCatalog();
    private final AddPlatform add = new AddPlatform(repository, catalog, clock);
    private final EditPlatform edit = new EditPlatform(repository, catalog, new AiApiKeyPolicy(),
            new AiBaseUrlPolicy(catalog));
    private final ScriptedModelGateway gateway = new ScriptedModelGateway();
    private final ManageModels models = new ManageModels(repository, gateway, clock);

    // ------------------------------------------------------------------ PLT-01/02/03

    @Test
    public void addingKeepsOrderUsesTheTemplateDefaultsAndNumbersRepeatedNames() {
        AiPlatformConfig first = add.execute("deepseek");
        AiPlatformConfig second = add.execute("deepseek");
        AiPlatformConfig third = add.execute("deepseek");

        assertEquals(List.of("DeepSeek", "DeepSeek 2", "DeepSeek 3"), names());
        assertEquals(List.of(first.id(), second.id(), third.id()),
                repository.load().platforms().stream().map(AiPlatformConfig::id).toList());
        assertEquals("deepseek", first.templateId());
        assertNull("留空＝用模板默认地址", first.baseUrl());
        assertEquals(AiProtocol.CHAT_COMPLETIONS, first.protocol());
        assertTrue(first.id().startsWith("deepseek-"));
        assertEquals(3, Set.of(first.id(), second.id(), third.id()).size());
    }

    @Test
    public void aRemovedNumberIsReusedFirst() {
        add.execute("deepseek");
        AiPlatformConfig second = add.execute("deepseek");
        add.execute("deepseek");
        repository.update(settings -> settings.withoutPlatform(second.id()));

        assertEquals("DeepSeek 2", add.execute("deepseek").name());
    }

    @Test
    public void unknownTemplatesAreRejected() {
        expect(AiValidationError.PLATFORM_UNKNOWN, () -> add.execute("nope"));
        assertTrue(repository.load().platforms().isEmpty());
    }

    // ------------------------------------------------------------------ PLT-04

    @Test
    public void renameRejectsEmptyTooLongAndDuplicateNamesWithoutSaving() {
        AiPlatformConfig a = add.execute("deepseek");
        add.execute("openai-compatible");
        int before = repository.updates;

        expect(AiValidationError.NAME_EMPTY, () -> edit.rename(a.id(), "   "));
        expect(AiValidationError.NAME_TOO_LONG, () -> edit.rename(a.id(), "名".repeat(33)));
        expect(AiValidationError.NAME_DUPLICATE, () -> edit.rename(a.id(), " OpenAI Compatible "));
        assertEquals("DeepSeek", platform(a.id()).name());

        edit.rename(a.id(), "  我的 DeepSeek ");
        assertEquals("我的 DeepSeek", platform(a.id()).name());
        edit.rename(a.id(), "名".repeat(32));
        assertEquals(32, platform(a.id()).name().length());
        assertTrue(repository.updates > before);
    }

    @Test
    public void renamingToItsOwnNameIsAllowed() {
        AiPlatformConfig a = add.execute("deepseek");
        edit.rename(a.id(), "DeepSeek");
        assertEquals("DeepSeek", platform(a.id()).name());
    }

    // ------------------------------------------------------------------ 连接参数

    @Test
    public void apiKeyIsTrimmedAndAnEmptyValueClearsIt() {
        AiPlatformConfig a = add.execute("deepseek");
        edit.setApiKey(a.id(), "  sk-abc  ");
        assertEquals("sk-abc", platform(a.id()).apiKey());
        edit.setApiKey(a.id(), " ");
        assertEquals("", platform(a.id()).apiKey());
        assertFalse(platform(a.id()).configured());
    }

    @Test
    public void baseUrlEqualToTheDefaultIsStoredAsDefault() {
        AiPlatformConfig a = add.execute("deepseek");
        edit.setBaseUrl(a.id(), "https://proxy.example.com/v1/");
        assertEquals("https://proxy.example.com", platform(a.id()).baseUrl());
        edit.setBaseUrl(a.id(), "https://api.deepseek.com/");
        assertNull(platform(a.id()).baseUrl());
        edit.setBaseUrl(a.id(), "https://proxy.example.com");
        edit.resetBaseUrl(a.id());
        assertNull(platform(a.id()).baseUrl());
    }

    @Test
    public void customTemplatesNeedAnAddress() {
        AiPlatformConfig custom = add.execute("openai-compatible");
        expect(AiValidationError.BASE_URL_REQUIRED, () -> edit.setBaseUrl(custom.id(), " "));
        edit.setBaseUrl(custom.id(), "https://llm.example.com/v1");
        assertEquals("https://llm.example.com", platform(custom.id()).baseUrl());
    }

    @Test
    public void protocolsAreLimitedToTheTemplate() {
        AiPlatformConfig a = add.execute("deepseek");
        edit.setProtocol(a.id(), AiProtocol.MESSAGES);
        assertEquals(AiProtocol.MESSAGES, platform(a.id()).protocol());
        expect(AiValidationError.PROTOCOL_UNSUPPORTED, () -> edit.setProtocol(a.id(), AiProtocol.RESPONSES));
        assertEquals(AiProtocol.MESSAGES, platform(a.id()).protocol());
    }

    @Test
    public void theTestModelMustBeAChatModelOfThisPlatform() {
        String id = platformWith(model("chat-a"), embedding("embed-a"));
        edit.setTestModel(id, "chat-a");
        assertEquals("chat-a", platform(id).testModelId());
        expect(AiValidationError.MODEL_NOT_CHAT, () -> edit.setTestModel(id, "embed-a"));
        expect(AiValidationError.MODEL_UNKNOWN, () -> edit.setTestModel(id, "gone"));
        edit.setTestModel(id, null);
        assertNull(platform(id).testModelId());
    }

    // ------------------------------------------------------------------ MOD-06/07

    @Test
    public void fetchingReturnsEveryRemoteModelAndMarksWhatIsAlreadyAdded() {
        String id = platformWith(model("b"), model("d"));
        gateway.models = List.of(model("a"), model("b"), model("c"), model("d"), model("e"));

        AiFetchedModels fetched = models.fetch(id);

        assertEquals(5, fetched.remote().size());
        assertEquals(Set.of("b", "d"), fetched.alreadyAdded());
        assertEquals(3, fetched.selectableCount());
        assertEquals("拉取不自动添加", 2, platform(id).models().size());

        models.addFetched(id, List.of(model("e"), model("a")));
        assertEquals(List.of("b", "d", "e", "a"), ids(id));
        models.addFetched(id, List.of(model("a")));
        assertEquals("已存在的跳过", 4, platform(id).models().size());
    }

    @Test
    public void modelsMissingFromTheListingAreMarkedOfflineUntilTheyReturn() {
        String id = platformWith(model("x"), model("z"));
        models.addManual(id, new AiModelDraftPolicy.Draft("y", "", "", AiModelKind.CHAT));

        gateway.models = List.of(model("z"));
        models.fetch(id);
        assertTrue(platform(id).model("x").orElseThrow().remoteMissing());
        assertFalse("手动添加的不受影响", platform(id).model("y").orElseThrow().remoteMissing());
        assertFalse(platform(id).model("z").orElseThrow().remoteMissing());

        gateway.models = List.of(new AiModel("x", "X 模型", 64_000, null, Set.of(), false, AiModelKind.CHAT,
                false), model("z"));
        models.fetch(id);
        AiModel back = platform(id).model("x").orElseThrow();
        assertFalse(back.remoteMissing());
        assertEquals("X 模型", back.name());
        assertEquals(Integer.valueOf(64_000), back.contextWindow());
        assertEquals(clock.now(), platform(id).modelsFetchedAtMillis());
    }

    // ------------------------------------------------------------------ MOD-08/09

    @Test
    public void manualModelsAreValidatedAndContextIsInThousands() {
        String id = platformWith(model("a"));
        expect(AiValidationError.MODEL_ID_DUPLICATE,
                () -> models.addManual(id, new AiModelDraftPolicy.Draft(" a ", "", "", AiModelKind.CHAT)));
        expect(AiValidationError.MODEL_ID_EMPTY,
                () -> models.addManual(id, new AiModelDraftPolicy.Draft("  ", "", "", AiModelKind.CHAT)));
        expect(AiValidationError.MODEL_ID_INVALID,
                () -> models.addManual(id, new AiModelDraftPolicy.Draft("my model", "", "", AiModelKind.CHAT)));
        expect(AiValidationError.CONTEXT_INVALID,
                () -> models.addManual(id, new AiModelDraftPolicy.Draft("b", "", "-1", AiModelKind.CHAT)));

        AiModel added = models.addManual(id, new AiModelDraftPolicy.Draft(" b ", " 我的 B ", "128",
                AiModelKind.CHAT));
        assertEquals("b", added.id());
        assertEquals("我的 B", added.name());
        assertEquals(Integer.valueOf(128_000), added.contextWindow());
        assertTrue(added.manual());
        assertEquals(List.of("a", "b"), ids(id));
    }

    @Test
    public void editingKeepsTheIdAndDropsTheCurrentModelWhenItStopsBeingChat() {
        String id = platformWith(model("a"), model("b"));
        models.select(id, "a");

        models.edit(id, "b", new AiModelDraftPolicy.Draft("ignored", "Bee", "32", AiModelKind.CHAT));
        AiModel b = platform(id).model("b").orElseThrow();
        assertEquals("Bee", b.name());
        assertEquals(Integer.valueOf(32_000), b.contextWindow());
        assertEquals(new AiSelection(id, "a"), repository.load().selection());

        models.edit(id, "a", new AiModelDraftPolicy.Draft("a", "", "", AiModelKind.EMBEDDING));
        assertEquals(AiModelKind.EMBEDDING, platform(id).model("a").orElseThrow().kind());
        assertNull(repository.load().selection());
    }

    // ------------------------------------------------------------------ PLT-06 / MOD-11 / MOD-12

    @Test
    public void onlyChatModelsCanBecomeCurrentAndRemovingItClearsTheSelection() {
        String id = platformWith(model("a"), embedding("e"));
        models.select(id, "a");
        assertEquals(new AiSelection(id, "a"), repository.load().selection());
        expect(AiValidationError.MODEL_NOT_CHAT, () -> models.select(id, "e"));
        expect(AiValidationError.MODEL_UNKNOWN, () -> models.select(id, "gone"));

        assertTrue(models.remove(id, Set.of("a")));
        assertNull(repository.load().selection());
        assertFalse(models.remove(id, Set.of()));
    }

    @Test
    public void removingAModelLeavesNoSpeedResultOrParamsBehind() {
        String id = platformWith(model("a"), model("b"));
        repository.update(settings -> settings.withPlatform(platform(id)
                .withSpeedResult(ok("a", 300))
                .withModelParams("a", new AiModelParams(0.5, null, null))
                .withTestModel("a")));

        assertFalse(models.remove(id, Set.of("a")));

        AiPlatformConfig after = platform(id);
        assertEquals(List.of("b"), ids(id));
        assertFalse(after.speedResults().containsKey("a"));
        assertFalse(after.modelParams().containsKey("a"));
        assertNull(after.testModelId());
    }

    @Test
    public void failedSpeedSelectsOnlyFailedChatModels() {
        String id = platformWith(model("a"), model("b"), model("c"), model("d"), model("e"));
        repository.update(settings -> settings.withPlatform(platform(id)
                .withSpeedResult(failed("a")).withSpeedResult(failed("b")).withSpeedResult(failed("c"))
                .withSpeedResult(ok("d", 100)).withSpeedResult(ok("e", 200))));

        Set<String> failed = ManageModels.failedSpeed(platform(id));
        assertEquals(Set.of("a", "b", "c"), failed);
        models.remove(id, failed);
        assertEquals(List.of("d", "e"), ids(id));
    }

    // ------------------------------------------------------------------ GEN-01/03

    @Test
    public void perModelParamsAreRangeCheckedAndNullClearsThem() {
        String id = platformWith(new AiModel("a", "a", null, 8_192, Set.of(), false));
        models.setParams(id, "a", new AiModelParams(1.5, 0.9, 4_096));
        assertEquals(new AiModelParams(1.5, 0.9, 4_096), platform(id).modelParams().get("a"));

        expect(AiValidationError.PARAM_OUT_OF_RANGE, () -> models.setParams(id, "a", new AiModelParams(2.5, null, null)));
        expect(AiValidationError.PARAM_OUT_OF_RANGE, () -> models.setParams(id, "a", new AiModelParams(null, 1.1, null)));
        expect(AiValidationError.PARAM_OUT_OF_RANGE, () -> models.setParams(id, "a", new AiModelParams(null, null, 9_000)));
        expect(AiValidationError.PARAM_OUT_OF_RANGE, () -> models.setParams(id, "a", new AiModelParams(null, null, 0)));
        assertEquals(new AiModelParams(1.5, 0.9, 4_096), platform(id).modelParams().get("a"));

        models.setParams(id, "a", AiModelParams.EMPTY);
        AiModelParams cleared = platform(id).modelParams().get("a");
        assertTrue(cleared == null || cleared.equals(AiModelParams.EMPTY));
    }

    // ------------------------------------------------------------------ 工具

    private String platformWith(AiModel... list) {
        AiPlatformConfig created = add.execute("deepseek");
        repository.update(settings -> settings.withPlatform(settings.platform(created.id()).orElseThrow()
                .withApiKey("sk-test").withModels(List.of(list), 0L)));
        return created.id();
    }

    private static AiModel embedding(String id) {
        return new AiModel(id, id, null, null, Set.of(), false, AiModelKind.EMBEDDING, false);
    }

    private static AiSpeedResult ok(String id, long firstToken) {
        return new AiSpeedResult(id, AiSpeedResult.Status.OK, firstToken, firstToken, null, null, 1L);
    }

    private static AiSpeedResult failed(String id) {
        return new AiSpeedResult(id, AiSpeedResult.Status.FAILED, -1, 0, null, "x", 1L);
    }

    private AiPlatformConfig platform(String id) {
        return repository.load().platform(id).orElseThrow();
    }

    private List<String> ids(String id) {
        return platform(id).models().stream().map(AiModel::id).toList();
    }

    private List<String> names() {
        return repository.load().platforms().stream().map(AiPlatformConfig::name).toList();
    }

    private static void expect(AiValidationError error, Runnable action) {
        try {
            action.run();
            fail("expected " + error);
        } catch (AiValidationException expected) {
            assertEquals(error, expected.error());
        }
    }
}
