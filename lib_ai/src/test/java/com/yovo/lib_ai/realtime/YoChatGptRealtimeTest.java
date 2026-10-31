package com.yovo.lib_ai.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.models.YoModelInfo;
import com.yovo.lib_ai.models.YoModelInference;

import org.junit.Test;

import java.util.List;

public final class YoChatGptRealtimeTest {

    @Test
    public void chatGptSpeaksBetaAndGaAndDefaultsToBeta() {
        assertEquals(List.of(YoRealtimeDialect.OPENAI_BETA, YoRealtimeDialect.OPENAI_GA),
                YoChatGptRealtime.dialects());
        assertEquals(YoRealtimeDialect.OPENAI_BETA, YoChatGptRealtime.defaultDialect());
        assertFalse(YoChatGptRealtime.dialects().contains(YoRealtimeDialect.QWEN));
        assertEquals("https://api.openai.com", YoChatGptRealtime.officialBaseUrl());
    }

    @Test
    public void onlyRealtimeModelsAreOffered() {
        List<YoModelInfo> offered = YoChatGptRealtime.offered(List.of(
                info("gpt-4o"),
                info("gpt-4o-realtime-preview"),
                info("gpt-realtime")));

        assertEquals(2, offered.size());
        assertEquals("gpt-4o-realtime-preview", offered.get(0).id());
        assertEquals("gpt-realtime", offered.get(1).id());
        assertTrue(YoChatGptRealtime.offered(List.of(info("whisper-1"))).isEmpty());
    }

    private static YoModelInfo info(String id) {
        return YoModelInfo.builder(YoModelInference.infer(id)).build();
    }
}
