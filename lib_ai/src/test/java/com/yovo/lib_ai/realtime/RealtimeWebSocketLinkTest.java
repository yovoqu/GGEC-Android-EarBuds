package com.yovo.lib_ai.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiErrorKind;

import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * {@link YoRealtime} / {@link RealtimeWebSocketLink} 端到端 MockWebServer 链路测试：
 * 验证建连协商速率透传、GA session.updated 放行门禁、指令编码与对端断开/主动关闭幂等性。
 */
public final class RealtimeWebSocketLinkTest {

    @Test
    public void qwenEmitsNegotiatedRatesAndForwardsTruncateCommand() throws Exception {
        BlockingQueue<JSONObject> serverReceived = new LinkedBlockingQueue<>();
        BlockingQueue<YoRealtimeOutput> outputs = new LinkedBlockingQueue<>();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                    webSocket.send("{\"type\":\"session.created\",\"session\":{\"id\":\"s1\"}}");
                }

                @Override
                public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                    try {
                        serverReceived.add(new JSONObject(text));
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                    webSocket.close(code, reason);
                }
            }));
            server.start();

            YoRealtimeConfig config = YoRealtimeConfig.builder()
                    .baseUrl(server.url("/").toString())
                    .apiKey("sk-qwen")
                    .model("qwen3.8-omni-flash-realtime")
                    .dialect(YoRealtimeDialect.QWEN)
                    .build();

            try (YoRealtime.Connection conn = YoRealtime.open(config, outputs::add)) {
                YoRealtimeOutput.Opened opened = assertInstanceOf(
                        YoRealtimeOutput.Opened.class, outputs.poll(3, TimeUnit.SECONDS));
                assertEquals(16_000, opened.inputSampleRateHz());
                assertEquals(24_000, opened.outputSampleRateHz());

                JSONObject sessionUpdate = serverReceived.poll(3, TimeUnit.SECONDS);
                assertNotNull(sessionUpdate);
                assertEquals("session.update", sessionUpdate.getString("type"));

                // 非 GA 方言建连后应立即放行上行音频与截断指令
                conn.send(new YoRealtimeInput.Audio(new byte[]{1, 2, 3, 4}));
                conn.send(new YoRealtimeInput.Truncate("item-42", 1, 350));

                JSONObject append = serverReceived.poll(3, TimeUnit.SECONDS);
                assertNotNull(append);
                assertEquals("input_audio_buffer.append", append.getString("type"));

                JSONObject truncate = serverReceived.poll(3, TimeUnit.SECONDS);
                assertNotNull(truncate);
                assertEquals("conversation.item.truncate", truncate.getString("type"));
                assertEquals("item-42", truncate.getString("item_id"));
                assertEquals(1, truncate.getInt("content_index"));
                assertEquals(350, truncate.getInt("audio_end_ms"));
            }

            // 本地主动 close() 不应投递 Closed 或 Failed 噪声
            YoRealtimeOutput unexpected = outputs.poll(200, TimeUnit.MILLISECONDS);
            assertEquals(null, unexpected);
        }
    }

    @Test
    public void gaHoldsBufferedAudioUntilSessionUpdatedArrives() throws Exception {
        BlockingQueue<String> serverTypes = new LinkedBlockingQueue<>();
        BlockingQueue<YoRealtimeOutput> outputs = new LinkedBlockingQueue<>();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                    webSocket.send("{\"type\":\"session.created\"}");
                }

                @Override
                public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                    try {
                        String type = new JSONObject(text).getString("type");
                        serverTypes.add(type);
                        if ("session.update".equals(type)) {
                            // 服务端处理完 update 后才发 session.updated
                            webSocket.send("{\"type\":\"session.updated\"}");
                        }
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                    webSocket.close(code, reason);
                }
            }));
            server.start();

            YoRealtimeConfig config = YoRealtimeConfig.builder()
                    .baseUrl(server.url("/").toString())
                    .apiKey("sk-openai")
                    .model("gpt-4o-realtime-preview")
                    .dialect(YoRealtimeDialect.OPENAI_GA)
                    .build();

            try (YoRealtime.Connection conn = YoRealtime.open(config, outputs::add)) {
                YoRealtimeOutput.Opened opened = assertInstanceOf(
                        YoRealtimeOutput.Opened.class, outputs.poll(3, TimeUnit.SECONDS));
                assertEquals(24_000, opened.inputSampleRateHz());

                // 在收到 session.updated 之前发送音频应先缓存，等待 session.updated 后补发
                conn.send(new YoRealtimeInput.Audio(new byte[]{10, 20}));

                assertEquals("session.update", serverTypes.poll(3, TimeUnit.SECONDS));
                assertEquals("input_audio_buffer.append", serverTypes.poll(3, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void peerCloseEmitsNetworkFailureAndClosed() throws Exception {
        BlockingQueue<YoRealtimeOutput> outputs = new LinkedBlockingQueue<>();

        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
                @Override
                public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                    webSocket.close(1001, "going away");
                }

                @Override
                public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                    webSocket.close(code, reason);
                }

                @Override
                public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t,
                                      @Nullable Response response) {
                }
            }));
            server.start();

            YoRealtimeConfig config = YoRealtimeConfig.builder()
                    .baseUrl(server.url("/").toString())
                    .apiKey("sk-test")
                    .model("gpt-4o-realtime-preview")
                    .build();

            YoRealtime.Connection conn = YoRealtime.open(config, outputs::add);
            YoRealtimeOutput first = outputs.poll(3, TimeUnit.SECONDS);
            YoRealtimeOutput second = outputs.poll(3, TimeUnit.SECONDS);

            assertInstanceOf(YoRealtimeOutput.Failed.class, first);
            assertEquals(YoAiErrorKind.NETWORK, ((YoRealtimeOutput.Failed) first).kind());
            assertInstanceOf(YoRealtimeOutput.Closed.class, second);
            assertNotNull(conn);
            conn.close();
        }
    }

    @Test
    public void closingBeforeOpenCompletesCancelsHandshakeCleanly() throws Exception {
        BlockingQueue<YoRealtimeOutput> outputs = new LinkedBlockingQueue<>();

        try (MockWebServer server = new MockWebServer()) {
            server.start();
            // 不 enqueue 响应并立刻关闭，验证建连期 close() 不阻塞且幂等安全
            YoRealtimeConfig config = YoRealtimeConfig.builder()
                    .baseUrl(server.url("/").toString())
                    .apiKey("sk-test")
                    .model("gpt-4o-realtime-preview")
                    .build();

            YoRealtime.Connection conn = YoRealtime.open(config, outputs::add);
            conn.close();
            conn.close();

            YoRealtimeOutput maybeEvent = outputs.poll(250, TimeUnit.MILLISECONDS);
            assertFalse("主动 close 后不应上报异常失败",
                    maybeEvent instanceof YoRealtimeOutput.Failed);
            assertTrue("主动 close 后不应再上报 Closed",
                    maybeEvent == null);
        }
    }

    private static <T> T assertInstanceOf(Class<T> type, Object value) {
        assertTrue("expected " + type.getSimpleName() + " but got "
                + (value == null ? "null" : value.getClass().getSimpleName()),
                type.isInstance(value));
        return type.cast(value);
    }
}
