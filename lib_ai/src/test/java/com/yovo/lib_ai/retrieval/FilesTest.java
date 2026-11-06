package com.yovo.lib_ai.retrieval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.retrieval.FilesResource;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class FilesTest {

    @Test
    public void uploadsMultipartAndDecodesFileRef() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f15/upload.json")));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                FilesResource resource = new FilesResource(
                        client, target(server), new CallRegistry(), FilesResource.Dialect.OPENAI);
                YoFileRef file = resource.upload(YoFileUploadRequest.builder()
                        .data("{\"line\":1}\n".getBytes(StandardCharsets.UTF_8))
                        .filename("batch.jsonl")
                        .purpose("batch")
                        .contentType("application/jsonl")
                        .build());
                assertEquals("file-abc123", file.id());
                assertEquals("batch.jsonl", file.filename());
                assertEquals(120, file.bytes());
                assertEquals("batch", file.purpose());
                assertTrue("created_at 秒要转成毫秒", file.createdAtMillis() > 1_000_000_000_000L);

                RecordedRequest recorded = server.takeRequest();
                assertEquals("POST", recorded.getMethod());
                assertEquals("/v1/files", recorded.getPath());
                assertTrue(recorded.getHeader("Content-Type").startsWith("multipart/form-data; boundary="));
                String body = recorded.getBody().readUtf8();
                assertTrue(body.contains("name=\"file\"; filename=\"batch.jsonl\""));
                assertTrue(body.contains("name=\"purpose\""));
                assertTrue(body.contains("batch"));
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    @Test
    public void listsFiles() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f15/list.json")));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                FilesResource resource = new FilesResource(
                        client, target(server), new CallRegistry(), FilesResource.Dialect.ANTHROPIC);
                List<YoFileRef> files = resource.list();
                assertEquals(2, files.size());
                assertEquals("file-def456", files.get(1).id());
                assertEquals("/v1/files", server.takeRequest().getPath());
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    @Test
    public void getsOneFile() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f15/upload.json")));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                FilesResource resource = new FilesResource(
                        client, target(server), new CallRegistry(), FilesResource.Dialect.OPENAI);
                assertEquals("file-abc123", resource.get("file-abc123").id());
                RecordedRequest recorded = server.takeRequest();
                assertEquals("GET", recorded.getMethod());
                assertEquals("/v1/files/file-abc123", recorded.getPath());
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    @Test
    public void deleteIsIdempotent() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f15/delete.json")));
            server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"error\":{\"message\":\"gone\"}}"));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                FilesResource resource = new FilesResource(
                        client, target(server), new CallRegistry(), FilesResource.Dialect.OPENAI);
                assertTrue(resource.delete("file-abc123"));
                assertTrue("重复删除 404 也算成功", resource.delete("file-abc123"));
                RecordedRequest first = server.takeRequest();
                assertEquals("DELETE", first.getMethod());
                assertEquals("/v1/files/file-abc123", first.getPath());
                assertEquals("DELETE", server.takeRequest().getMethod());
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    @Test
    public void temporaryUrlOnlyForQwen() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("{\"url\":\"https://dashscope.example/tmp/file-abc123\"}"));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                FilesResource qwen = new FilesResource(
                        client, target(server), new CallRegistry(), FilesResource.Dialect.QWEN);
                assertEquals("https://dashscope.example/tmp/file-abc123", qwen.temporaryUrl("file-abc123"));
                assertEquals("/api/v1/files/file-abc123/temporary-url", server.takeRequest().getPath());

                FilesResource openAi = new FilesResource(
                        client, target(server), new CallRegistry(), FilesResource.Dialect.OPENAI);
                assertThrows(UnsupportedOperationException.class, () -> openAi.temporaryUrl("file-abc123"));
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    private static YoServiceTarget target(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/+$", "");
        return new YoServiceTarget(baseUrl, Map.of("Authorization", "Bearer file-key"));
    }
}
