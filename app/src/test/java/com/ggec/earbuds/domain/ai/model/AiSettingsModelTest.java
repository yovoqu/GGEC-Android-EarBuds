package com.ggec.earbuds.domain.ai.model;

import static com.ggec.earbuds.testing.ai.AiFixtures.model;
import static com.ggec.earbuds.testing.ai.AiFixtures.platform;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.testing.ai.AiFixtures;

import org.junit.Test;

import java.util.List;
import java.util.Set;
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
        assertTrue(replaced.platform("a").orElseThrow().configured());

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
    public void switchingProvidersKeepsEachProvidersOwnState() {
        AiVoiceConfig qwen = AiFixtures.voice(AiVoiceModel.QWEN, "sk-qwen")
                .withBaseUrl("https://qwen.example.com")
                .withRealtimeModels(List.of(
                        new AiModel("qwen-audio-3.1-realtime-flash", "qwen-audio-3.1-realtime-flash",
                                null, null, Set.of(), false, AiModelCategory.REALTIME)));

        AiVoiceConfig switched = qwen.withModel(AiVoiceModel.CHATGPT).withModel(AiVoiceModel.QWEN);

        assertEquals("切走再切回，Qwen 的地址还在", "https://qwen.example.com",
                switched.effectiveBaseUrl());
        assertEquals("切走再切回，Qwen 的模型还在", 1, switched.realtimeModels().size());
        assertEquals("qwen-audio-3.1-realtime-flash", switched.realtimeModelId());
        assertEquals("切到 ChatGPT 时 Qwen 的模型不能串过去", 0,
                qwen.withModel(AiVoiceModel.CHATGPT).realtimeModels().size());
    }

    @Test
    public void lateFetchResultsLandOnTheProviderThatStartedTheFetch() {
        AiVoiceConfig config = AiFixtures.voice(AiVoiceModel.CHATGPT, "")
                .withModel(AiVoiceModel.QWEN);
        // 用户已切到 ChatGPT，此时 Qwen 的拉取结果才回来。
        AiVoiceConfig switched = config.withModel(AiVoiceModel.CHATGPT)
                .withRealtimeModelsFor(AiVoiceModel.QWEN, List.of(
                        new AiModel("qwen-audio-3.1-realtime-flash", "qwen-audio-3.1-realtime-flash",
                                null, null, Set.of(), false, AiModelCategory.REALTIME)));

        assertEquals("当前选中的 ChatGPT 不能被写入", 0, switched.realtimeModels().size());
        assertEquals("Qwen 名下的结果必须落到 Qwen", 1,
                switched.withModel(AiVoiceModel.QWEN).realtimeModels().size());
    }

    @Test
    public void eachVoiceModelKeepsItsOwnConfiguredFlagWhenSwitching() {
        AiVoiceConfig config = new AiVoiceConfig(AiVoiceModel.CHATGPT, Set.of(),
                Map.of())
                .withConfigured(true)
                .withModel(AiVoiceModel.QWEN)
                .withConfigured(true);

        assertTrue(config.configured());
        assertTrue(config.withModel(AiVoiceModel.CHATGPT).configured());

        AiVoiceConfig cleared = config.withConfigured(false);
        assertFalse(cleared.configured());
        assertTrue("清掉 Qwen 的配置不该动 ChatGPT 的",
                cleared.withModel(AiVoiceModel.CHATGPT).configured());
    }

    @Test
    public void voiceIsOnlyReadyWhenItsOwnKeyIsFilled() {
        assertFalse(AiVoiceConfig.DEFAULT.configured());
        assertTrue(new AiVoiceConfig(AiVoiceModel.CHATGPT, Set.of(AiVoiceModel.CHATGPT),
                Map.of()).configured());
        assertFalse("没有 Key 的模型不算配好",
                new AiVoiceConfig(AiVoiceModel.QWEN, Set.of(AiVoiceModel.CHATGPT),
                        Map.of()).configured());
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
    public void defaultDuplexIsIndependentFromProviderSwitchesAndCredentialDerivation() {
        AiVoiceConfig config = AiFixtures.voice(AiVoiceModel.QWEN, "sk-qwen")
                .withDefaultDuplex(VoiceDuplex.FULL_DUPLEX)
                .withModel(AiVoiceModel.CHATGPT)
                .withConfigured(true)
                .withConfiguredProviders(Set.of(AiVoiceModel.CHATGPT));

        assertEquals(VoiceDuplex.FULL_DUPLEX, config.defaultDuplex());
        assertEquals(VoiceDuplex.HALF_DUPLEX, AiVoiceConfig.DEFAULT.defaultDuplex());
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

        AiPlatformConfig newKey = cached.withConnection("DeepSeek", true, null, AiProtocol.CHAT_COMPLETIONS);
        assertEquals(2, newKey.models().size());
        assertEquals(99L, newKey.modelsFetchedAtMillis());
        assertEquals(result, newKey.speedResults().get("m1"));

        AiPlatformConfig newHost = cached.withConnection("DeepSeek", true, "https://proxy.example.com",
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
        assertTrue(text.contains("configured="));
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
