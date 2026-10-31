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

        assertTrue(AiFixtures.settings(selection, platform("deepseek", "k", "deepseek-chat")).textModel().isPresent());
        assertTrue("an unset key is a placeholder, not a usable platform",
                AiFixtures.settings(selection, platform("deepseek", "", "deepseek-chat")).textModel().isEmpty());
        assertTrue(AiFixtures.settings(selection, platform("deepseek", "k", "deepseek-reasoner")).textModel().isEmpty());
        assertTrue(AiFixtures.settings(selection, platform("openai", "k", "deepseek-chat")).textModel().isEmpty());
        assertTrue(AiFixtures.settings(null, platform("deepseek", "k", "deepseek-chat")).textModel().isEmpty());
    }

    @Test
    public void removingAPlatformDropsASelectionThatPointsAtIt() {
        AiSettings settings = AiFixtures.settings(new AiSelection("deepseek", "deepseek-chat"),
                platform("deepseek", "k", "deepseek-chat"), platform("openai", "k", "gpt-4o"));

        AiSettings withoutOther = settings.withoutPlatform("openai");
        assertEquals(List.of("deepseek"), withoutOther.platforms().stream().map(AiPlatformConfig::id).toList());
        assertEquals(settings.textSelection(), withoutOther.textSelection());
        assertNull(settings.withoutPlatform("deepseek").textSelection());
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
        assertSame(valid, valid.withValidSelections());

        AiSettings staleModel = valid.withTextSelection(new AiSelection("deepseek", "gone"));
        assertNull(staleModel.withValidSelections().textSelection());
        AiSettings stalePlatform = valid.withTextSelection(new AiSelection("gone", "deepseek-chat"));
        assertNull(stalePlatform.withValidSelections().textSelection());
    }

    @Test
    public void textSelectionAndVoiceModelAreIndependent() {
        AiSettings settings = AiFixtures.settings(new AiSelection("openai", "gpt-4o"),
                AiFixtures.voice(AiVoiceModel.QWEN, "sk-qwen"),
                platform("openai", "k", "gpt-4o"));

        assertTrue(settings.textModel().isPresent());
        assertTrue(settings.voiceReady());
        assertEquals("gpt-4o", settings.textSelection().modelId());
        assertEquals(AiVoiceModel.QWEN, settings.voice().model());

        AiSettings voiceOnly = settings.withTextSelection(null);
        assertTrue(voiceOnly.textModel().isEmpty());
        assertTrue("改文本选择不该动语音模型", voiceOnly.voiceReady());
    }

    @Test
    public void eachVoiceModelKeepsItsOwnKeyWhenSwitching() {
        AiVoiceConfig config = new AiVoiceConfig(AiVoiceModel.CHATGPT, Map.of())
                .withApiKey("sk-gpt")
                .withModel(AiVoiceModel.QWEN)
                .withApiKey("sk-qwen");

        assertEquals("sk-qwen", config.apiKey());
        assertTrue(config.configured());
        assertEquals("sk-gpt", config.withModel(AiVoiceModel.CHATGPT).apiKey());

        AiVoiceConfig cleared = config.withApiKey("");
        assertFalse(cleared.configured());
        assertEquals("清掉 Qwen 的 Key 不该动 ChatGPT 的",
                "sk-gpt", cleared.withModel(AiVoiceModel.CHATGPT).apiKey());
    }

    @Test
    public void voiceIsOnlyReadyWhenItsOwnKeyIsFilled() {
        assertFalse(AiVoiceConfig.DEFAULT.configured());
        assertTrue(new AiVoiceConfig(AiVoiceModel.CHATGPT, Map.of(AiVoiceModel.CHATGPT, "k")).configured());
        assertFalse("没有 Key 的模型不算配好",
                new AiVoiceConfig(AiVoiceModel.QWEN, Map.of(AiVoiceModel.CHATGPT, "k")).configured());
    }

    @Test
    public void removingAPlatformDoesNotTouchTheVoiceModel() {
        AiSettings settings = AiFixtures.settings(new AiSelection("deepseek", "deepseek-chat"),
                AiFixtures.voice(AiVoiceModel.CHATGPT, "sk-voice"),
                platform("deepseek", "k", "deepseek-chat"));

        AiSettings cleaned = settings.withoutPlatform("deepseek");

        assertNull(cleaned.textSelection());
        assertTrue("语音模型与平台无关，删平台不该把它一起清掉", cleaned.voiceReady());
        assertEquals(AiVoiceModel.CHATGPT, cleaned.voice().model());
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
