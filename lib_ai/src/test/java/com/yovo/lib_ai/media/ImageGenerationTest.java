package com.yovo.lib_ai.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_ai.api.YoJob;
import com.yovo.lib_ai.api.YoJobStatus;
import com.yovo.lib_ai.error.YoAiErrorKind;
import com.yovo.lib_ai.error.YoAiErrors;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiHttpException;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * F19 图像生成：Qwen 多模态同步与 OpenAI Images 的编码/解码、n 数量、失败可读。
 */
public final class ImageGenerationTest {

    private static final String API_KEY = "sk-test";

    @Test
    public void encodesQwenRequestMatchingFixture() throws Exception {
        JSONObject expected = new JSONObject(resource("/fixtures/f19/request.json"));
        JSONObject actual = YoImageServiceImpl.encode(qwenRequest());

        assertEquals(expected.getString("model"), actual.getString("model"));
        JSONObject expectedContent = expected.getJSONObject("input")
                .getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content").getJSONObject(0);
        JSONObject actualContent = actual.getJSONObject("input")
                .getJSONArray("messages").getJSONObject(0)
                .getJSONArray("content").getJSONObject(0);
        assertEquals(expectedContent.getString("text"), actualContent.getString("text"));

        JSONObject expectedParams = expected.getJSONObject("parameters");
        JSONObject actualParams = actual.getJSONObject("parameters");
        assertEquals(expectedParams.getString("size"), actualParams.getString("size"));
        assertEquals(expectedParams.getInt("n"), actualParams.getInt("n"));
        assertEquals(expectedParams.getBoolean("prompt_extend"), actualParams.getBoolean("prompt_extend"));
        assertEquals(expectedParams.getInt("seed"), actualParams.getInt("seed"));
        assertEquals(expectedParams.getBoolean("watermark"), actualParams.getBoolean("watermark"));
    }

    @Test
    public void decodesQwenImageResponse() throws Exception {
        String raw = resource("/fixtures/f19/response.json");

        YoImageResult result = YoImageServiceImpl.decode(YoImageRequest.Dialect.QWEN, raw, null);

        assertEquals(1, result.imageCount());
        assertEquals("https://dashscope-result.oss.aliyuncs.com/img/1.png", result.firstUrl());
        assertEquals("req_img_1", result.requestId());
    }

    @Test
    public void decodesOpenAiImagesResponse() throws Exception {
        String raw = resource("/fixtures/f19/openai_response.json");

        YoImageResult result = YoImageServiceImpl.decode(YoImageRequest.Dialect.OPENAI, raw, "req_o");

        assertEquals(1, result.urls().size());
        assertEquals(1, result.base64().size());
        assertEquals(1, result.revisedPrompts().size());
        assertEquals(1, result.imageCount());
    }

    @Test
    public void generateReturnsSucceededJobWithUrl() {
        FakeTransport transport = new FakeTransport();
        try {
            transport.reply(new MediaTransport.Response(200, "req_img_1",
                    resource("/fixtures/f19/response.json").getBytes(StandardCharsets.UTF_8),
                    "application/json"));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
        YoImageServiceImpl service = new YoImageServiceImpl(transport);

        YoJob<YoImageResult> job = service.generate(
                "https://dashscope.aliyuncs.com", API_KEY, qwenRequest());

        assertEquals(YoJobStatus.SUCCEEDED, job.status());
        assertTrue(job.isDone());
        assertNotNull(job.result());
        assertEquals("https://dashscope-result.oss.aliyuncs.com/img/1.png", job.result().firstUrl());
        assertEquals("https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                transport.onlyUrl());
        assertEquals("Bearer " + API_KEY, transport.onlyHeaders().get("Authorization"));
    }

    @Test
    public void openAiEncodeCarriesN() throws Exception {
        YoImageRequest request = YoImageRequest.builder()
                .dialect(YoImageRequest.Dialect.OPENAI)
                .model("gpt-image-1")
                .prompt("a cat")
                .n(2)
                .size("1024x1024")
                .responseFormat("b64_json")
                .build();

        JSONObject json = YoImageServiceImpl.encode(request);

        assertEquals(2, json.getInt("n"));
        assertEquals("1024x1024", json.getString("size"));
        assertEquals("b64_json", json.getString("response_format"));
    }

    @Test
    public void mapsBadRequestToReadableMessage() {
        FakeTransport transport = new FakeTransport();
        transport.reply(new MediaTransport.Response(400, "req_bad",
                "{\"error\":{\"message\":\"invalid size\",\"type\":\"invalid_request_error\"}}".getBytes(
                        StandardCharsets.UTF_8),
                "application/json"));
        YoImageServiceImpl service = new YoImageServiceImpl(transport);

        try {
            service.generate("https://api.openai.com", API_KEY,
                    YoImageRequest.builder().dialect(YoImageRequest.Dialect.OPENAI)
                            .model("gpt-image-1").prompt("a cat").build());
            fail("expected failure");
        } catch (YoAiException failure) {
            assertTrue(failure instanceof YoAiHttpException);
            assertEquals("invalid size", failure.getMessage());
            assertEquals(YoAiErrorKind.BAD_REQUEST, YoAiErrors.kindOf(failure));
        }
    }

    @Test
    public void rejectsNonPositiveN() {
        try {
            YoImageRequest.builder().prompt("x").model("m").n(0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("n"));
        }
    }

    // ---------------------------------------------------------------- 辅助

    private static YoImageRequest qwenRequest() {
        return YoImageRequest.builder()
                .dialect(YoImageRequest.Dialect.QWEN)
                .model("qwen-image-3.0-pro")
                .prompt("一只戴宇航头盔的柴犬，电影感")
                .size("2048*2048")
                .n(1)
                .promptExtend(true)
                .seed(123)
                .watermark(false)
                .build();
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = ImageGenerationTest.class.getResourceAsStream(path)) {
            assertNotNull("missing fixture " + path, stream);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static final class FakeTransport implements MediaTransport {

        private final ArrayDeque<MediaTransport.Response> replies = new ArrayDeque<>();
        private final List<String> urls = new ArrayList<>();
        private final List<Map<String, String>> headers = new ArrayList<>();

        FakeTransport reply(MediaTransport.Response response) {
            replies.add(response);
            return this;
        }

        String onlyUrl() {
            assertEquals("应当只发一次 POST", 1, urls.size());
            return urls.get(0);
        }

        Map<String, String> onlyHeaders() {
            return headers.get(0);
        }

        @Override
        public MediaTransport.Response post(
                String url, Map<String, String> requestHeaders, byte[] body, String contentType) {
            urls.add(url);
            headers.add(Map.copyOf(requestHeaders));
            MediaTransport.Response response = replies.poll();
            if (response == null) {
                throw new AssertionError("unexpected extra request to " + url);
            }
            return response;
        }

        @Override
        public MediaTransport.Response get(String url, Map<String, String> requestHeaders) {
            throw new AssertionError("image generation should not GET");
        }

        @Override
        public void close() {
        }
    }
}
