package com.yovo.lib_ai.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.models.YoModelInfo;
import com.yovo.lib_ai.platform.YoPlatform;

import org.junit.Test;

import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class PlatformCatalogTest {

    private final YoPlatformCatalog catalog = YoPlatformCatalog.standard();

    @Test
    public void listsBuiltInPlatformsFollowedByTheCompatibleTemplates() {
        assertEquals(
                List.of("openai", "anthropic", "deepseek", "sensenova",
                        YoPlatformCatalog.OPENAI_COMPATIBLE, YoPlatformCatalog.ANTHROPIC_COMPATIBLE),
                catalog.descriptors().stream().map(YoPlatformDescriptor::templateId).toList());

        YoPlatformDescriptor deepSeek = catalog.find("deepseek");
        assertEquals("DeepSeek", deepSeek.displayName());
        assertEquals("https://api.deepseek.com", deepSeek.defaultBaseUrl());
        assertEquals(YoProtocol.CHAT_COMPLETIONS, deepSeek.defaultProtocol());
        assertTrue(deepSeek.protocols().contains(YoProtocol.MESSAGES));
        assertFalse(deepSeek.custom());
        assertNotNull(deepSeek.consoleUrl());
        assertEquals(List.of(YoProtocol.MESSAGES), catalog.find("anthropic").protocols());
        assertTrue(catalog.find(YoPlatformCatalog.OPENAI_COMPATIBLE).custom());
        assertEquals("", catalog.find(YoPlatformCatalog.OPENAI_COMPATIBLE).defaultBaseUrl());
        assertNull(catalog.find("nobody"));
    }

    @Test
    public void everyDescriptorProtocolIsProvidedByThePlatformItCreates() {
        for (YoPlatformDescriptor descriptor : catalog.descriptors()) {
            YoPlatform platform = catalog.create(YoPlatformSettings.builder(descriptor.templateId())
                    .apiKey("k")
                    .baseUrl(descriptor.custom() ? "https://gateway.example.com" : null)
                    .build());
            for (YoProtocol protocol : descriptor.protocols()) {
                boolean provided = switch (protocol) {
                    case CHAT_COMPLETIONS -> platform.chatTarget() != null;
                    case RESPONSES -> platform.responsesTarget() != null;
                    case MESSAGES -> platform.messagesTarget() != null;
                };
                assertTrue(descriptor.templateId() + " should provide " + protocol, provided);
            }
            assertNotNull(descriptor.templateId() + " should list models", platform.modelListing());
        }
    }

    @Test
    public void createsBuiltInPlatformsWithTheirOfficialHostAndKey() {
        YoPlatform platform = catalog.create(YoPlatformSettings.builder("deepseek").apiKey(" sk-1 ").build());
        assertEquals("deepseek", platform.id());
        assertEquals("https://api.deepseek.com", platform.chatTarget().baseUrl());
        assertEquals("Bearer sk-1", platform.chatTarget().headers().get("Authorization"));
        assertEquals("https://api.deepseek.com/anthropic", platform.messagesTarget().baseUrl());

        YoPlatform anthropic = catalog.create(YoPlatformSettings.builder("anthropic").apiKey("ak").build());
        assertEquals("ak", anthropic.messagesTarget().headers().get("x-api-key"));
    }

    @Test
    public void customTemplatesNeedAHostAndCanCoexistUnderDifferentInstanceIds() {
        try {
            catalog.create(YoPlatformSettings.builder(YoPlatformCatalog.OPENAI_COMPATIBLE).apiKey("k").build());
            fail("a compatible endpoint needs a base url");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("baseUrl"));
        }
        YoPlatform first = catalog.create(YoPlatformSettings.builder(YoPlatformCatalog.OPENAI_COMPATIBLE)
                .instanceId("custom-1").apiKey("a").baseUrl("https://one.example.com/v1/").build());
        YoPlatform second = catalog.create(YoPlatformSettings.builder(YoPlatformCatalog.OPENAI_COMPATIBLE)
                .instanceId("custom-2").apiKey("b").baseUrl("https://two.example.com/api").build());
        assertEquals("custom-1", first.id());
        assertEquals("https://one.example.com", first.chatTarget().baseUrl());
        assertEquals("https://two.example.com/api", second.chatTarget().baseUrl());
        try (YoAiClient client = YoAiClient.builder().platform(first).platform(second).build()) {
            assertNotNull(client.chatCompletions("custom-1"));
            assertNotNull(client.models("custom-2"));
        }
        YoPlatform anthropic = catalog.create(YoPlatformSettings.builder(YoPlatformCatalog.ANTHROPIC_COMPATIBLE)
                .instanceId("custom-3").apiKey("c").baseUrl("https://x.example.com/anthropic/v1").build());
        assertEquals("https://x.example.com/anthropic", anthropic.messagesTarget().baseUrl());
        assertNull(anthropic.chatTarget());
    }

    @Test
    public void rejectsUnknownTemplatesAndBadSettings() {
        assertRejected(() -> catalog.create(YoPlatformSettings.builder("nobody").apiKey("k").build()));
        assertRejected(() -> YoPlatformSettings.builder("openai").apiKey("  ").build());
        assertRejected(() -> YoPlatformSettings.builder("openai").build());
        assertRejected(() -> YoPlatformSettings.builder(" "));
        assertRejected(() -> catalog.create(YoPlatformSettings.builder("openai").apiKey("k").baseUrl("ftp://x").build()));
    }

    @Test
    public void settingsNeverPrintTheKey() {
        String text = YoPlatformSettings.builder("openai").apiKey("sk-secret").baseUrl("https://x.com").build().toString();
        assertFalse(text.contains("sk-secret"));
        assertTrue(text.contains("redacted"));
    }

    @Test
    public void normalizesPastedAddressesToTheHostBase() {
        assertEquals("https://api.openai.com", YoPlatformCatalog.normalizeBaseUrl("https://api.openai.com/v1"));
        assertEquals("https://api.openai.com", YoPlatformCatalog.normalizeBaseUrl("  https://api.openai.com/v1/  "));
        assertEquals("https://x.com/api", YoPlatformCatalog.normalizeBaseUrl("https://x.com/api/v1/chat/completions"));
        assertEquals("https://x.com", YoPlatformCatalog.normalizeBaseUrl("https://x.com/chat/completions?x=1#top"));
        assertEquals("https://x.com/anthropic", YoPlatformCatalog.normalizeBaseUrl("https://x.com/anthropic/v1/messages"));
        assertEquals("https://x.com", YoPlatformCatalog.normalizeBaseUrl("https://x.com/v1/v1"));
        assertEquals("https://x.com/proxy", YoPlatformCatalog.normalizeBaseUrl("https://x.com/proxy/"));
        assertEquals("HTTPS://X.COM", YoPlatformCatalog.normalizeBaseUrl("HTTPS://X.COM/V1"));
        assertEquals("http://10.0.0.2:11434", YoPlatformCatalog.normalizeBaseUrl("http://10.0.0.2:11434/v1"));
        assertRejected(() -> YoPlatformCatalog.normalizeBaseUrl("api.openai.com"));
        assertRejected(() -> YoPlatformCatalog.normalizeBaseUrl("https://"));
        assertRejected(() -> YoPlatformCatalog.normalizeBaseUrl("https:///v1"));
    }

    @Test
    public void aCatalogPlatformWorksEndToEndThroughTheClient() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("{\"data\":[{\"id\":\"m1\"}]}"));
            String base = server.url("/").toString().replaceAll("/+$", "") + "/v1";
            YoPlatform platform = catalog.create(YoPlatformSettings.builder(YoPlatformCatalog.OPENAI_COMPATIBLE)
                    .instanceId("c1").apiKey("k").baseUrl(base).build());
            try (YoAiClient client = YoAiClient.builder().platform(platform).callbackExecutor(Runnable::run).build()) {
                List<YoModelInfo> models = client.models().list();
                assertEquals("m1", models.get(0).id());
                RecordedRequest request = server.takeRequest();
                assertEquals("/v1/models", request.getPath());
                assertEquals("Bearer k", request.getHeader("Authorization"));
            }
        }
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 期望的拒绝。
        }
    }
}
