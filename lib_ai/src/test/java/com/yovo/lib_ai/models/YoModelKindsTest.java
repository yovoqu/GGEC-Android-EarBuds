package com.yovo.lib_ai.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class YoModelKindsTest {

    @Test
    public void nonChatModelsAreRecognisedByIdTokens() {
        assertEquals(YoModelKind.EMBEDDING, YoModelKinds.kindOf("text-embedding-3-large"));
        assertEquals(YoModelKind.EMBEDDING, YoModelKinds.kindOf("BAAI/bge-m3"));
        assertEquals(YoModelKind.IMAGE, YoModelKinds.kindOf("dall-e-3"));
        assertEquals(YoModelKind.IMAGE, YoModelKinds.kindOf("gpt-image-1"));
        assertEquals(YoModelKind.IMAGE, YoModelKinds.kindOf("black-forest-labs/FLUX.1-dev"));
        assertEquals(YoModelKind.IMAGE, YoModelKinds.kindOf("sensenova-u1-fast"));
        assertEquals(YoModelKind.AUDIO, YoModelKinds.kindOf("whisper-1"));
        assertEquals(YoModelKind.AUDIO, YoModelKinds.kindOf("gpt-4o-mini-tts"));
        assertEquals(YoModelKind.AUDIO, YoModelKinds.kindOf("gpt-4o-transcribe"));
        assertEquals(YoModelKind.MODERATION, YoModelKinds.kindOf("omni-moderation-latest"));
        assertEquals(YoModelKind.OTHER, YoModelKinds.kindOf("davinci-002"));
        assertEquals(YoModelKind.OTHER, YoModelKinds.kindOf("gpt-3.5-turbo-instruct"));
        assertEquals(YoModelKind.OTHER, YoModelKinds.kindOf("gpt-4o-realtime-preview"));
    }

    @Test
    public void unfamiliarIdsStayVisibleAsChatModels() {
        for (String id : new String[] {
                "gpt-4o", "gpt-4o-audio-preview", "deepseek-chat", "claude-opus-5-5",
                "qwen2.5:7b", "llama3.1-70b", "my-company-model", "sensenova-6.7-flash-lite"}) {
            assertTrue(id, YoModelKinds.kindOf(id).chatCapable());
        }
    }

    @Test
    public void reasoningHintCoversCommonNamingOnly() {
        assertTrue(YoModelKinds.looksLikeReasoning("deepseek-reasoner"));
        assertTrue(YoModelKinds.looksLikeReasoning("o3-mini"));
        assertTrue(YoModelKinds.looksLikeReasoning("qwen3-235b-thinking"));
        assertFalse(YoModelKinds.looksLikeReasoning("gpt-4o"));
        assertFalse(YoModelKinds.looksLikeReasoning("deepseek-chat"));
    }
}
