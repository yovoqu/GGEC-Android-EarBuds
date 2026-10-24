package com.ggec.earbuds.domain.ai.model;

import static com.ggec.earbuds.testing.ai.AiFixtures.model;
import static com.ggec.earbuds.testing.ai.AiFixtures.platform;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.testing.ai.AiFixtures;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class AiSettingsModelTest {

    @Test
    public void activeModelNeedsAConfiguredPlatformAndAListedModel() {
        AiSelection selection = new AiSelection("deepseek", "deepseek-chat");

        assertTrue(AiFixtures.settings(selection, platform("deepseek", "k", "deepseek-chat")).activeModel().isPresent());
        assertTrue("an unset key is a placeholder, not a usable platform",
                AiFixtures.settings(selection, platform("deepseek", "", "deepseek-chat")).activeModel().isEmpty());
        assertTrue(AiFixtures.settings(selection, platform("deepseek", "k", "deepseek-reasoner")).activeModel().isEmpty());
        assertTrue(AiFixtures.settings(selection, platform("openai", "k", "deepseek-chat")).activeModel().isEmpty());
        assertTrue(AiFixtures.settings(null, platform("deepseek", "k", "deepseek-chat")).activeModel().isEmpty());
    }

    @Test
    public void removingAPlatformDropsASelectionThatPointsAtIt() {
        AiSettings settings = AiFixtures.settings(new AiSelection("deepseek", "deepseek-chat"),
                platform("deepseek", "k", "deepseek-chat"), platform("openai", "k", "gpt-4o"));

        AiSettings withoutOther = settings.withoutPlatform("openai");
        assertEquals(List.of("deepseek"), withoutOther.platforms().stream().map(AiPlatformConfig::id).toList());
        assertEquals(settings.selection(), withoutOther.selection());
        assertNull(settings.withoutPlatform("deepseek").selection());
    }

    @Test
    public void withPlatformReplacesInPlaceOrAppends() {
        AiSettings settings = AiFixtures.settings(null, platform("a", "k1"), platform("b", "k2"));

        AiSettings replaced = settings.withPlatform(platform("a", "changed"));
        assertEquals(List.of("a", "b"), replaced.platforms().stream().map(AiPlatformConfig::id).toList());
        assertEquals("changed", replaced.platform("a").orElseThrow().apiKey());

        AiSettings appended = settings.withPlatform(platform("c", "k3"));
        assertEquals(List.of("a", "b", "c"), appended.platforms().stream().map(AiPlatformConfig::id).toList());
    }

    @Test
    public void staleSelectionsAreDroppedButValidOnesAreKept() {
        AiSettings valid = AiFixtures.readySettings();
        assertSame(valid, valid.withValidSelection());

        AiSettings staleModel = valid.withSelection(new AiSelection("deepseek", "gone"));
        assertNull(staleModel.withValidSelection().selection());
        AiSettings stalePlatform = valid.withSelection(new AiSelection("gone", "deepseek-chat"));
        assertNull(stalePlatform.withValidSelection().selection());
    }

    @Test
    public void hasConfiguredPlatformIgnoresPlaceholders() {
        assertFalse(AiFixtures.settings(null, platform("a", "")).hasConfiguredPlatform());
        assertTrue(AiFixtures.settings(null, platform("a", ""), platform("b", "k")).hasConfiguredPlatform());
    }

    @Test
    public void changingTheHostDropsTheModelCacheButChangingTheKeyKeepsIt() {
        AiSpeedResult result = new AiSpeedResult("m1", AiSpeedResult.Status.OK, 100, 200, null, null, 5);
        AiPlatformConfig cached = platform("deepseek", "k", "m1", "m2").withModels(
                List.of(model("m1"), model("m2")), 99L).withSpeedResult(result);

        AiPlatformConfig newKey = cached.withCredentials("DeepSeek", "other-key", null, AiProtocol.CHAT_COMPLETIONS);
        assertEquals(2, newKey.models().size());
        assertEquals(99L, newKey.modelsFetchedAtMillis());
        assertEquals(result, newKey.speedResults().get("m1"));

        AiPlatformConfig newHost = cached.withCredentials("DeepSeek", "k", "https://proxy.example.com",
                AiProtocol.CHAT_COMPLETIONS);
        assertTrue(newHost.models().isEmpty());
        assertEquals(0L, newHost.modelsFetchedAtMillis());
        assertTrue(newHost.speedResults().isEmpty());
    }

    @Test
    public void refreshingModelsKeepsSpeedResultsOnlyForModelsThatSurvive() {
        AiSpeedResult kept = new AiSpeedResult("m1", AiSpeedResult.Status.OK, 100, 200, null, null, 5);
        AiSpeedResult dropped = new AiSpeedResult("m2", AiSpeedResult.Status.OK, 300, 400, null, null, 5);
        AiPlatformConfig config = platform("p", "k").withModels(List.of(model("m1"), model("m2")), 1L)
                .withSpeedResult(kept).withSpeedResult(dropped);

        AiPlatformConfig refreshed = config.withModels(List.of(model("m1"), model("m3")), 2L);

        assertEquals(Map.of("m1", kept), refreshed.speedResults());
    }

    @Test
    public void platformConfigNeverPrintsItsKey() {
        String text = platform("deepseek", "sk-super-secret-key", "m").toString();
        assertFalse(text.contains("sk-super-secret-key"));
        assertTrue(text.contains("redacted"));
    }

    @Test
    public void messageTransitionsKeepIdentityAndPreserveWhatWasReceived() {
        AiMessage streaming = AiMessage.assistantPlaceholder("a1", "deepseek-chat", 10)
                .appendReasoning("think").appendReasoning("ing").appendText("Hel").appendText("lo");

        assertTrue(streaming.streaming());
        assertEquals("thinking", streaming.reasoning());
        assertEquals("Hello", streaming.text());

        AiMessage done = streaming.complete(AiFinishReason.LENGTH, AiUsage.ofTokens(5, 7), 321);
        assertEquals(AiMessageStatus.COMPLETE, done.status());
        assertEquals(AiFinishReason.LENGTH, done.finishReason());
        assertEquals(12, done.usage().totalTokens());
        assertEquals(321, done.elapsedMillis());
        assertEquals("a1", done.id());

        AiMessage failed = streaming.fail(AiFailureKind.NETWORK, "reset", 50);
        assertEquals(AiMessageStatus.ERROR, failed.status());
        assertEquals(AiFailureKind.NETWORK, failed.failure().kind());
        assertEquals("Hello", failed.text());
        assertNull(failed.complete(AiFinishReason.STOP, null, 1).failure());

        assertEquals(AiMessageStatus.STOPPED, streaming.stop(9).status());
        assertTrue(AiMessage.assistantPlaceholder("a2", null, 1).blank());
        assertFalse(streaming.blank());
    }
}
