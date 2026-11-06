package com.yovo.lib_ai.retrieval;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ai.internal.retrieval.BatchCodec;
import com.yovo.lib_ai.internal.retrieval.BatchResource;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public final class BatchTest {

    @Test
    public void decodesCreateJob() {
        YoBatchJob job = BatchCodec.decodeJob(EmbeddingsTest.fixture("f16/create.json"), false);
        assertEquals("batch_abc123", job.id());
        assertEquals(YoBatchStatus.VALIDATING, job.status());
        assertFalse(job.status().terminal());
        assertEquals("/v1/chat/completions", job.endpoint());
        assertEquals("file-abc123", job.inputFileId());
        assertEquals(2, job.totalRequests());
    }

    @Test
    public void decodesCompletedJobWithCountsAndErrors() {
        YoBatchJob job = BatchCodec.decodeJob(EmbeddingsTest.fixture("f16/retrieve.json"), false);
        assertEquals(YoBatchStatus.COMPLETED, job.status());
        assertTrue(job.status().terminal());
        assertEquals("file-out-1", job.outputFileId());
        assertEquals("file-err-1", job.errorFileId());
        assertEquals(1, job.completedRequests());
        assertEquals(1, job.failedRequests());
        assertTrue(job.errors().contains("line 2 is malformed"));
    }

    @Test
    public void mapsAnthropicStatuses() {
        assertEquals(YoBatchStatus.IN_PROGRESS, BatchCodec.statusFromAnthropic("in_progress"));
        assertEquals(YoBatchStatus.COMPLETED, BatchCodec.statusFromAnthropic("ended"));
        assertEquals(YoBatchStatus.CANCELLING, BatchCodec.statusFromAnthropic("canceling"));
        assertEquals(YoBatchStatus.CANCELLED, BatchCodec.statusFromAnthropic("canceled"));
    }

    @Test
    public void alignsResultsByCustomId() {
        List<YoBatchResult> results = BatchCodec.decodeResultsJsonl(
                EmbeddingsTest.fixture("f16/results.jsonl"));
        assertEquals(3, results.size());
        assertEquals("r1", results.get(0).customId());
        assertTrue(results.get(0).succeeded());
        assertEquals(Integer.valueOf(200), results.get(0).statusCode());
        assertEquals(Integer.valueOf(400), results.get(1).statusCode());
        assertFalse(results.get(1).succeeded());
        assertEquals("r3", results.get(2).customId());
        assertEquals("request expired", results.get(2).error());
    }

    @Test
    public void encodesSubmitForBothDialects() throws Exception {
        YoBatchRequest openAi = YoBatchRequest.builder()
                .inputFileId("file-abc123")
                .endpoint("/v1/chat/completions")
                .completionWindow("24h")
                .metadata(new JSONObject().put("k", "v"))
                .build();
        JSONObject openAiJson = BatchCodec.encodeSubmit(openAi, false);
        assertEquals("file-abc123", openAiJson.getString("input_file_id"));
        assertEquals("24h", openAiJson.getString("completion_window"));

        YoBatchRequest anthropic = YoBatchRequest.builder()
                .addRequest(new JSONObject().put("custom_id", "c1").put("params", new JSONObject()))
                .build();
        JSONObject anthropicJson = BatchCodec.encodeSubmit(anthropic, true);
        assertEquals(1, anthropicJson.getJSONArray("requests").length());
    }

    @Test
    public void submitRetrieveAndDownloadResults() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f16/create.json")));
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f16/retrieve.json")));
            server.enqueue(new MockResponse().setBody(EmbeddingsTest.fixture("f16/results.jsonl")));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                BatchResource resource = new BatchResource(
                        client, target(server), new CallRegistry(), BatchResource.Dialect.OPENAI);
                YoBatchJob created = resource.submit(YoBatchRequest.builder()
                        .inputFileId("file-abc123")
                        .endpoint("/v1/chat/completions")
                        .completionWindow("24h")
                        .build());
                assertEquals(YoBatchStatus.VALIDATING, created.status());

                List<YoBatchResult> results = resource.results("batch_abc123");
                assertEquals(3, results.size());

                RecordedRequest submit = server.takeRequest();
                assertEquals("POST", submit.getMethod());
                assertEquals("/v1/batches", submit.getPath());
                assertTrue(submit.getBody().readUtf8().contains("\"input_file_id\":\"file-abc123\""));

                RecordedRequest retrieve = server.takeRequest();
                assertEquals("GET", retrieve.getMethod());
                assertEquals("/v1/batches/batch_abc123", retrieve.getPath());

                RecordedRequest content = server.takeRequest();
                assertEquals("GET", content.getMethod());
                assertEquals("/v1/files/file-out-1/content", content.getPath());
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    @Test
    public void anthropicUsesMessagesBatchesPath() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            server.enqueue(new MockResponse().setBody("""
                    {"id":"msgbatch_1","processing_status":"in_progress",
                     "request_counts":{"processing":1,"succeeded":2,"errored":0,"canceled":0,"expired":0}}
                    """));
            OkHttpClient client = OkHttpTransport.newClient(5_000, 5_000, 5_000);
            try {
                BatchResource resource = new BatchResource(
                        client, target(server), new CallRegistry(), BatchResource.Dialect.ANTHROPIC);
                resource.submit(YoBatchRequest.builder()
                        .addRequest(new JSONObject().put("custom_id", "c1").put("params", new JSONObject()))
                        .build());
                RecordedRequest recorded = server.takeRequest();
                assertEquals("/v1/messages/batches", recorded.getPath());
                assertTrue(recorded.getBody().readUtf8().contains("\"requests\""));
            } finally {
                OkHttpTransport.shutdown(client);
            }
        }
    }

    private static YoServiceTarget target(MockWebServer server) {
        String baseUrl = server.url("/").toString().replaceAll("/+$", "");
        return new YoServiceTarget(baseUrl, Map.of("Authorization", "Bearer batch-key"));
    }
}
