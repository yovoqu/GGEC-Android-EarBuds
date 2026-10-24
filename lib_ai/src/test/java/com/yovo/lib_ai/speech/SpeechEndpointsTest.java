package com.yovo.lib_ai.speech;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * URL 拼装是这一层最容易悄悄错的地方：拼错只会得到 404，而 404 在界面上被解释成
 * "模型不存在"，很难倒推回地址。所以把规则逐条钉死。
 */
public final class SpeechEndpointsTest {

    @Test
    public void appendsVersionWhenBaseUrlHasNone() {
        assertEquals(
                "https://api.openai.com/v1/audio/transcriptions",
                SpeechEndpoints.transcriptionUrl("https://api.openai.com"));
    }

    @Test
    public void doesNotDuplicateVersionSegment() {
        String expected = "https://api.openai.com/v1/audio/transcriptions";
        assertEquals(expected, SpeechEndpoints.transcriptionUrl("https://api.openai.com/v1"));
        assertEquals(expected, SpeechEndpoints.transcriptionUrl("https://api.openai.com/v1/"));
        assertEquals(expected, SpeechEndpoints.transcriptionUrl("https://api.openai.com/v1///"));
    }

    @Test
    public void keepsGatewayPathPrefix() {
        assertEquals(
                "https://gw.example.com/openai/v1/audio/transcriptions",
                SpeechEndpoints.transcriptionUrl("https://gw.example.com/openai"));
        assertEquals(
                "https://gw.example.com/openai/v1/audio/transcriptions",
                SpeechEndpoints.transcriptionUrl("https://gw.example.com/openai/v1"));
    }

    @Test
    public void acceptsAFullEndpointAsIs() {
        String endpoint = "https://api.openai.com/v1/audio/transcriptions";
        assertEquals(endpoint, SpeechEndpoints.transcriptionUrl(endpoint));
        // 结尾的斜杠会被规范化掉，不会拼出 "transcriptions/"，也不会重复补 /v1。
        assertEquals(endpoint, SpeechEndpoints.transcriptionUrl(endpoint + "/"));
    }

    @Test
    public void trimsSurroundingWhitespace() {
        assertEquals(
                "http://127.0.0.1:8080/v1/audio/transcriptions",
                SpeechEndpoints.transcriptionUrl("  http://127.0.0.1:8080/  "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSchemeLessBaseUrl() {
        SpeechEndpoints.transcriptionUrl("api.openai.com");
    }
}
