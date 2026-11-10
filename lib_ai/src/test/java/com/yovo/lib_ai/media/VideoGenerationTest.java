package com.yovo.lib_ai.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_ai.api.YoJob;
import com.yovo.lib_ai.api.YoJobStatus;

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
 * F20 视频生成：请求编码、提交拿 task_id、轮询成功、超时。
 */
public final class VideoGenerationTest {

    private static final String API_KEY = "sk-test";

    @Test
    public void encodesRequestMatchingFixture() throws Exception {
        JSONObject expected = new JSONObject(resource("/fixtures/f20/request.json"));
        JSONObject actual = YoVideoServiceImpl.encode(videoRequest());

        assertEquals(expected.getString("model"), actual.getString("model"));
        assertEquals(expected.getJSONObject("input").getString("prompt"),
                actual.getJSONObject("input").getString("prompt"));
        JSONObject expectedMedia = expected.getJSONObject("input")
                .getJSONArray("media").getJSONObject(0);
        JSONObject actualMedia = actual.getJSONObject("input")
                .getJSONArray("media").getJSONObject(0);
        assertEquals(expectedMedia.getString("type"), actualMedia.getString("type"));
        assertEquals(expectedMedia.getString("url"), actualMedia.getString("url"));

        JSONObject expectedParams = expected.getJSONObject("parameters");
        JSONObject actualParams = actual.getJSONObject("parameters");
        assertEquals(expectedParams.getString("resolution"), actualParams.getString("resolution"));
        assertEquals(expectedParams.getString("ratio"), actualParams.getString("ratio"));
        assertEquals(expectedParams.getInt("duration"), actualParams.getInt("duration"));
        assertEquals(expectedParams.getBoolean("prompt_extend"), actualParams.getBoolean("prompt_extend"));
    }

    @Test
    public void decodesSubmitTaskId() throws Exception {
        YoVideoServiceImpl.Submit submit = YoVideoServiceImpl.decodeSubmit(
                resource("/fixtures/f20/submit_response.json"));

        assertEquals("task_abc123", submit.taskId);
        assertEquals("PENDING", submit.taskStatus);
    }

    @Test
    public void pollsUntilSucceeded() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.replyPost(new MediaTransport.Response(200, "req_vid_1",
                resource("/fixtures/f20/submit_response.json").getBytes(StandardCharsets.UTF_8),
                "application/json"));
        transport.replyGet(new MediaTransport.Response(200, "req_vid_2",
                resource("/fixtures/f20/task_succeeded.json").getBytes(StandardCharsets.UTF_8),
                "application/json"));

        long[] now = {0L};
        YoVideoServiceImpl service = new YoVideoServiceImpl(
                transport, 5000L, 100L, () -> now[0], millis -> now[0] += millis);

        YoJob<YoVideoResult> job = service.generate("https://dashscope.aliyuncs.com", API_KEY, videoRequest());

        assertEquals(YoJobStatus.SUCCEEDED, job.status());
        assertNotNull(job.result());
        assertEquals("https://dashscope-result.oss.aliyuncs.com/video/out.mp4", job.result().videoUrl());
        assertEquals("task_abc123", job.result().taskId());
        assertEquals("enable", transport.postHeaders().get("X-DashScope-Async"));
        assertTrue(transport.lastGetUrl().endsWith("/api/v1/tasks/task_abc123"));
        assertEquals("https://dashscope.aliyuncs.com/api/v1/services/aigc/video-generation/video-synthesis",
                transport.postUrl());
    }

    @Test
    public void timesOutAsFailedJob() {
        FakeTransport transport = new FakeTransport();
        transport.replyPost(new MediaTransport.Response(200, "req_vid_1",
                "{\"request_id\":\"r\",\"output\":{\"task_id\":\"task_abc123\",\"task_status\":\"PENDING\"}}"
                        .getBytes(StandardCharsets.UTF_8),
                "application/json"));
        transport.fallbackGet(new MediaTransport.Response(200, "r2",
                "{\"output\":{\"task_id\":\"task_abc123\",\"task_status\":\"RUNNING\"}}"
                        .getBytes(StandardCharsets.UTF_8),
                "application/json"));

        long[] now = {0L};
        YoVideoServiceImpl service = new YoVideoServiceImpl(
                transport, 1000L, 100L, () -> now[0], millis -> now[0] += millis);

        YoJob<YoVideoResult> job = service.generate("https://dashscope.aliyuncs.com", API_KEY, videoRequest());

        assertEquals(YoJobStatus.FAILED, job.status());
        assertTrue(job.failureMessage().contains("timed out"));
    }

    @Test
    public void immediateVideoUrlSkipsPolling() {
        FakeTransport transport = new FakeTransport();
        transport.replyPost(new MediaTransport.Response(200, "r",
                ("{\"request_id\":\"r\",\"output\":{\"task_id\":\"t1\",\"task_status\":\"SUCCEEDED\","
                        + "\"video_url\":\"https://cdn.example.com/out.mp4\"}}")
                        .getBytes(StandardCharsets.UTF_8),
                "application/json"));

        YoVideoServiceImpl service = new YoVideoServiceImpl(
                transport, 1000L, 100L, System::currentTimeMillis, millis -> { });

        YoJob<YoVideoResult> job = service.generate("https://dashscope.aliyuncs.com", API_KEY, videoRequest());

        assertEquals(YoJobStatus.SUCCEEDED, job.status());
        assertEquals("https://cdn.example.com/out.mp4", job.result().videoUrl());
        assertEquals("immediate success should not poll", 0, transport.getCount());
    }

    @Test
    public void rejectsNonPositiveDuration() {
        try {
            YoVideoRequest.builder().prompt("x").model("m").duration(0);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duration"));
        }
    }

    // ---------------------------------------------------------------- 辅助

    private static YoVideoRequest videoRequest() {
        return YoVideoRequest.builder()
                .model("wan3.0-video")
                .prompt("一只柴犬在草地上奔跑")
                .addMedia("reference_image", "https://example.com/ref.png")
                .resolution("480P")
                .ratio("adaptive")
                .duration(20)
                .promptExtend(true)
                .build();
    }

    private static String resource(String path) throws Exception {
        try (InputStream stream = VideoGenerationTest.class.getResourceAsStream(path)) {
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

        private final ArrayDeque<MediaTransport.Response> postReplies = new ArrayDeque<>();
        private final ArrayDeque<MediaTransport.Response> getReplies = new ArrayDeque<>();
        private MediaTransport.Response fallbackGet;
        private final List<String> postUrls = new ArrayList<>();
        private final List<Map<String, String>> postHeaders = new ArrayList<>();
        private final List<String> getUrls = new ArrayList<>();

        FakeTransport replyPost(MediaTransport.Response response) {
            postReplies.add(response);
            return this;
        }

        FakeTransport replyGet(MediaTransport.Response response) {
            getReplies.add(response);
            return this;
        }

        FakeTransport fallbackGet(MediaTransport.Response response) {
            this.fallbackGet = response;
            return this;
        }

        String postUrl() {
            return postUrls.get(0);
        }

        Map<String, String> postHeaders() {
            return postHeaders.get(0);
        }

        String lastGetUrl() {
            return getUrls.get(getUrls.size() - 1);
        }

        int getCount() {
            return getUrls.size();
        }

        @Override
        public MediaTransport.Response post(
                String url, Map<String, String> headers, byte[] body, String contentType) {
            postUrls.add(url);
            postHeaders.add(Map.copyOf(headers));
            MediaTransport.Response response = postReplies.poll();
            if (response == null) {
                throw new AssertionError("unexpected extra POST to " + url);
            }
            return response;
        }

        @Override
        public MediaTransport.Response get(String url, Map<String, String> headers) {
            getUrls.add(url);
            MediaTransport.Response response = getReplies.poll();
            if (response != null) {
                return response;
            }
            if (fallbackGet != null) {
                return fallbackGet;
            }
            throw new AssertionError("unexpected GET to " + url);
        }

        @Override
        public void close() {
        }
    }
}
