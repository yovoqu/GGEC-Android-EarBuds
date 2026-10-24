package com.ggec.earbuds.domain.ai.policy;

import static com.ggec.earbuds.testing.ai.AiFixtures.model;
import static com.ggec.earbuds.testing.ai.AiFixtures.platform;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelKind;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.testing.ai.FakeCatalog;

import org.junit.Test;

import java.util.List;
import java.util.Set;

/** 平台页的纯规则：连接前置条件与测试模型、失败指向、命名、地址预览、模型草稿、测速结论。 */
public class AiPlatformRulesTest {

    private final FakeCatalog catalog = new FakeCatalog();

    // ------------------------------------------------------------------ CON-03

    @Test
    public void testModelFollowsTheChosenThenTheCurrentThenTheFirstChatModel() {
        AiPlatformConfig p = platform("p", "k").withModels(List.of(embedding("e"), model("a"), model("b")), 0L);

        assertEquals("当前模型在本平台", "b", AiConnectionPolicy.testModel(p, new AiSelection("p", "b")));
        assertEquals("当前模型不在本平台", "a", AiConnectionPolicy.testModel(p, new AiSelection("q", "b")));
        assertEquals("a", AiConnectionPolicy.testModel(p, null));
        assertEquals("用户选的优先", "b", AiConnectionPolicy.testModel(p.withTestModel("b"), new AiSelection("p", "a")));
        AiPlatformConfig chosenRemoved = p.withTestModel("b").withoutModels(Set.of("b"));
        assertEquals("所选被删回到默认", "a", AiConnectionPolicy.testModel(chosenRemoved, null));
        assertNull(AiConnectionPolicy.testModel(platform("p", "k").withModels(List.of(embedding("e")), 0L), null));
    }

    // ------------------------------------------------------------------ CON-04

    @Test
    public void blockersAreReportedOneAtATimeInPriorityOrder() {
        AiPlatformConfig ready = platform("p", "k", "a");
        assertNull(AiConnectionPolicy.blocker(ready, false, true, true));
        assertEquals(AiConnectionPolicy.Blocker.OFFLINE, AiConnectionPolicy.blocker(ready, false, true, false));
        assertEquals(AiConnectionPolicy.Blocker.NO_MODEL,
                AiConnectionPolicy.blocker(platform("p", "k"), false, true, false));
        AiPlatformConfig badUrl = ready.withBaseUrl("nonsense");
        assertEquals(AiConnectionPolicy.Blocker.BAD_BASE_URL, AiConnectionPolicy.blocker(badUrl, false, false, true));
        assertEquals(AiConnectionPolicy.Blocker.NO_BASE_URL, AiConnectionPolicy.blocker(ready, true, true, true));
        assertEquals("多个不满足时取最上面一个", AiConnectionPolicy.Blocker.NO_API_KEY,
                AiConnectionPolicy.blocker(platform("p", ""), true, false, false));
    }

    // ------------------------------------------------------------------ CON-05

    @Test
    public void failuresPointAtTheRowToFix() {
        assertEquals(AiConnectionPolicy.Target.API_KEY, AiConnectionPolicy.target(AiFailureKind.AUTHENTICATION));
        assertEquals(AiConnectionPolicy.Target.TEST_MODEL, AiConnectionPolicy.target(AiFailureKind.PERMISSION));
        assertEquals(AiConnectionPolicy.Target.BASE_URL, AiConnectionPolicy.target(AiFailureKind.NOT_FOUND));
        assertEquals(AiConnectionPolicy.Target.PROTOCOL, AiConnectionPolicy.target(AiFailureKind.BAD_REQUEST));
        assertEquals(AiConnectionPolicy.Target.PROTOCOL, AiConnectionPolicy.target(AiFailureKind.PROTOCOL));
        for (AiFailureKind kind : List.of(AiFailureKind.NETWORK, AiFailureKind.TIMEOUT, AiFailureKind.SERVER,
                AiFailureKind.RATE_LIMITED, AiFailureKind.QUOTA, AiFailureKind.UNKNOWN)) {
            assertEquals(kind.name(), AiConnectionPolicy.Target.NONE, AiConnectionPolicy.target(kind));
        }
        assertEquals(AiConnectionPolicy.Target.NONE, AiConnectionPolicy.target(null));
    }

    @Test
    public void fingerprintsNeverContainTheKeyAndChangeWithEachInput() {
        AiPlatformConfig p = platform("p", "sk-secret-value", "a");
        String base = AiFingerprint.speed(p);
        assertFalse(base.contains("sk-secret"));
        assertNotEquals(base, AiFingerprint.speed(p.withApiKey("sk-other")));
        assertNotEquals(base, AiFingerprint.speed(p.withBaseUrl("https://proxy.example.com")));
        assertNotEquals(base, AiFingerprint.speed(p.withProtocol(AiProtocol.MESSAGES)));
        assertEquals(base, AiFingerprint.speed(p.withName("改个名")));
        assertNotEquals(AiFingerprint.connection(p, "a"), AiFingerprint.connection(p, "b"));
    }

    // ------------------------------------------------------------------ PLT-03/04

    @Test
    public void defaultNamesTakeTheSmallestFreeNumber() {
        assertEquals("OpenAI", AiPlatformNamePolicy.defaultName("OpenAI", List.of()));
        assertEquals("OpenAI 2", AiPlatformNamePolicy.defaultName("OpenAI", List.of("OpenAI")));
        assertEquals("OpenAI 2", AiPlatformNamePolicy.defaultName("OpenAI", List.of("OpenAI", "OpenAI 3")));
        assertEquals("OpenAI 4", AiPlatformNamePolicy.defaultName("OpenAI",
                List.of("OpenAI", "OpenAI 2", "OpenAI 3")));
    }

    @Test
    public void namesAreStrippedAndLimitedTo32CodePoints() {
        assertEquals(AiPlatformNamePolicy.Error.EMPTY, AiPlatformNamePolicy.validate(" ", List.of()));
        assertEquals(AiPlatformNamePolicy.Error.EMPTY, AiPlatformNamePolicy.validate(null, List.of()));
        assertNull(AiPlatformNamePolicy.validate("😀".repeat(32), List.of()));
        assertEquals(AiPlatformNamePolicy.Error.TOO_LONG, AiPlatformNamePolicy.validate("😀".repeat(33), List.of()));
        assertEquals(AiPlatformNamePolicy.Error.DUPLICATE, AiPlatformNamePolicy.validate(" A ", List.of("A")));
        assertNull(AiPlatformNamePolicy.validate("a", List.of("A")));
    }

    // ------------------------------------------------------------------ ADV-02

    @Test
    public void endpointPreviewAppendsTheProtocolPathAfterNormalising() {
        assertEquals("https://api.example.com/v1/chat/completions",
                AiEndpointPreview.of(catalog, "https://api.example.com", AiProtocol.CHAT_COMPLETIONS));
        assertEquals("https://api.example.com/v1/responses",
                AiEndpointPreview.of(catalog, "https://api.example.com/v1", AiProtocol.RESPONSES));
        assertEquals("https://api.example.com/v1/messages",
                AiEndpointPreview.of(catalog, "https://api.example.com/v1/", AiProtocol.MESSAGES));
        assertNull(AiEndpointPreview.of(catalog, " ", AiProtocol.MESSAGES));
        assertNull(AiEndpointPreview.of(catalog, "ftp://x", AiProtocol.MESSAGES));
        assertTrue(AiEndpointPreview.valid(catalog, null));
        assertFalse(AiEndpointPreview.valid(catalog, "api.example.com"));
        assertEquals("api.example.com", AiEndpointPreview.host("https://api.example.com/v1"));
        assertNull(AiEndpointPreview.host(null));
    }

    // ------------------------------------------------------------------ MOD-08

    @Test
    public void contextIsEnteredInThousands() {
        assertEquals(0L, AiModelDraftPolicy.parseContext(""));
        assertEquals(128_000L, AiModelDraftPolicy.parseContext("128"));
        assertEquals(128_000L, AiModelDraftPolicy.parseContext(" 128K "));
        assertEquals(1_500L, AiModelDraftPolicy.parseContext("1.5"));
        assertTrue(AiModelDraftPolicy.parseContext("0") < 0);
        assertTrue(AiModelDraftPolicy.parseContext("abc") < 0);
        assertTrue(AiModelDraftPolicy.parseContext("NaN") < 0);
    }

    @Test
    public void editingRoundTripsAndKeepsCapabilities() {
        AiModel original = new AiModel("gpt", "GPT", 128_000, 16_384, Set.of(), false, AiModelKind.CHAT, true);
        AiModelDraftPolicy.Draft draft = AiModelDraftPolicy.Draft.of(original);
        AiModel same = AiModelDraftPolicy.toModel(draft, original);
        assertEquals(original, same);
        assertNull(AiModelDraftPolicy.validate(draft, platform("p", "k").withModels(List.of(original), 0L),
                "gpt"));
    }

    // ------------------------------------------------------------------ SPD-B-04/06

    @Test
    public void batchesOfTwoOrMoreAskFirstAndTheConclusionNamesTheFastest() {
        assertFalse(AiBatchSpeedPolicy.needsConfirm(1));
        assertTrue(AiBatchSpeedPolicy.needsConfirm(4));
        assertNull(AiBatchSpeedPolicy.conclude(List.of()));

        AiBatchSpeedPolicy.Conclusion mixed = AiBatchSpeedPolicy.conclude(List.of(
                ok("a", 900), ok("b", 300), failed("c"), ok("d", 500)));
        assertEquals("b", mixed.fastestModelId());
        assertEquals(300L, mixed.fastestMillis());
        assertEquals(1, mixed.failed());
        assertFalse(mixed.allFailed());

        AiBatchSpeedPolicy.Conclusion none = AiBatchSpeedPolicy.conclude(List.of(failed("a"), failed("b")));
        assertNull(none.fastestModelId());
        assertTrue(none.allFailed());
    }

    // ------------------------------------------------------------------ 工具

    private static AiModel embedding(String id) {
        return new AiModel(id, id, null, null, Set.of(), false, AiModelKind.EMBEDDING, false);
    }

    private static AiSpeedResult ok(String id, long firstToken) {
        return new AiSpeedResult(id, AiSpeedResult.Status.OK, firstToken, firstToken, null, null, 0);
    }

    private static AiSpeedResult failed(String id) {
        return new AiSpeedResult(id, AiSpeedResult.Status.FAILED, -1, 0, AiFailureKind.SERVER, null, 0);
    }
}
