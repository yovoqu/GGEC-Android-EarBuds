package com.yovo.lib_ai.internal.responses;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoResponsesConnection;
import com.yovo.lib_ai.platform.YoServiceTarget;
import com.yovo.lib_ai.responses.YoResponsesPolicy;
import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.error.YoAiTransportException;
import com.yovo.lib_ai.internal.protocol.WirePaths;
import com.yovo.lib_ai.internal.runtime.OrderedCallbacks;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.internal.transport.WebSocketPipe;
import com.yovo.lib_ai.responses.YoResponseInput;
import com.yovo.lib_ai.responses.YoResponseObject;
import com.yovo.lib_ai.responses.YoResponseRequest;
import com.yovo.lib_ai.responses.YoResponseStreamEvent;
import com.yovo.lib_ai.responses.YoResponseStreamListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * Responses 的全双工会话。读帧和发送 create、steer 分开。
 * HTTP SSE 不走这里。
 */
public final class ResponsesSession implements YoResponsesConnection {

    private final ResponsesLaneTable lanes = new ResponsesLaneTable();
    private final OrderedCallbacks callbacks;
    private final YoResponsesPolicy policy;
    private volatile WebSocketPipe pipe;
    private volatile boolean closed;

    private ResponsesSession(OrderedCallbacks callbacks, YoResponsesPolicy policy) {
        this.callbacks = callbacks;
        this.policy = policy;
    }

    public static YoResponsesConnection open(
            OkHttpClient client,
            YoServiceTarget target,
            YoResponsesPolicy policy,
            OrderedCallbacks callbacks,
            long connectTimeoutMillis) {
        ResponsesSession session = new ResponsesSession(callbacks, policy);
        Request request = new Request.Builder()
                .url(webSocketUrl(target.baseUrl()))
                .headers(OkHttpTransport.headersOf(target.headers()))
                .build();
        session.pipe = WebSocketPipe.open(client, request, new WebSocketPipe.Incoming() {
            @Override
            public void onText(@NonNull String text) {
                session.onText(text);
            }

            @Override
            public void onTransportClosed(@NonNull Throwable cause) {
                session.onTransportClosed(cause);
            }
        }, connectTimeoutMillis);
        return session;
    }

    @NonNull
    @Override
    public YoAiCall create(
            @Nullable String streamId,
            @NonNull YoResponseRequest request,
            @NonNull YoResponseStreamListener listener) {
        ensureOpen();
        validateStreamId(streamId);
        String laneKey = streamId == null ? "" : streamId;
        lanes.begin(laneKey, listener);
        try {
            policy.check(request);
            JSONObject body = ResponsesCodec.encodeWebSocket(request, streamId);
            policy.apply(body);
            pipe.send(body.toString());
        } catch (JSONException exception) {
            lanes.cancel(laneKey);
            throw new YoAiProtocolException("Failed to encode response.create", null, exception);
        } catch (RuntimeException exception) {
            lanes.cancel(laneKey);
            throw exception;
        }
        return () -> lanes.cancel(laneKey);
    }

    @Override
    public void steer(@NonNull String previousResponseId, @NonNull String inputText) {
        if (previousResponseId.isBlank()) {
            throw new IllegalArgumentException("previousResponseId is required");
        }
        ensureOpen();
        policy.checkSteer(previousResponseId);
        try {
            pipe.send(ResponsesCodec.encodeSteer(previousResponseId, inputText).toString());
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Failed to encode response.steer", null, exception);
        }
    }

    @Override
    public void steer(@NonNull String previousResponseId, @NonNull List<YoResponseInput> inputItems) {
        if (previousResponseId.isBlank()) {
            throw new IllegalArgumentException("previousResponseId is required");
        }
        if (inputItems.isEmpty()) {
            throw new IllegalArgumentException("input is required");
        }
        ensureOpen();
        policy.checkSteer(previousResponseId);
        try {
            pipe.send(ResponsesCodec.encodeSteer(previousResponseId, ResponsesCodec.encodeItems(inputItems)).toString());
        } catch (JSONException exception) {
            throw new YoAiProtocolException("Failed to encode response.steer", null, exception);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        failOpen(new YoAiTransportException("WebSocket closed", null));
        pipe.close();
    }

    private void onText(String text) {
        if (closed) {
            return;
        }
        try {
            JSONObject event = unwrap(new JSONObject(text));
            String type = event.optString("type");
            String laneKey = event.has("stream_id") && !event.isNull("stream_id")
                    ? event.optString("stream_id")
                    : "";
            if (ResponsesEvents.ERROR.equals(type) && laneKey.isEmpty()) {
                failOpen(new YoAiProtocolException(ResponsesTurn.errorMessage(event), event.toString()));
                return;
            }
            deliver(laneKey, lanes.push(laneKey, event));
        } catch (Exception exception) {
            YoAiException error = exception instanceof YoAiException ai
                    ? ai
                    : new YoAiProtocolException(
                            exception.getMessage() == null ? "Invalid WebSocket event" : exception.getMessage(),
                            text,
                            exception);
            failOpen(error);
        }
    }

    private void onTransportClosed(Throwable cause) {
        if (closed) {
            return;
        }
        closed = true;
        YoAiException error = cause instanceof YoAiException ai
                ? ai
                : new YoAiTransportException(
                        cause.getMessage() == null ? "WebSocket failed" : cause.getMessage(),
                        cause);
        failOpen(error);
    }

    private void failOpen(YoAiException error) {
        for (YoResponseStreamListener listener : lanes.failOpen()) {
            callbacks.submit(() -> listener.onFailure(error));
        }
    }

    private void deliver(@Nullable String laneKey, @Nullable ResponsesLaneTable.ResponsesDispatch dispatch) {
        if (dispatch == null) {
            return;
        }
        callbacks.submit(() -> {
            try {
                dispatch.listener.onEvent(dispatch.event);
            } catch (RuntimeException exception) {
                if (laneKey != null) {
                    lanes.cancel(laneKey);
                }
                dispatch.listener.onFailure(new YoAiProtocolException(
                        exception.getMessage() == null ? "Listener failed" : exception.getMessage(),
                        null,
                        exception));
                return;
            }
            if (dispatch.failure != null) {
                dispatch.listener.onFailure(dispatch.failure);
            } else if (dispatch.completed != null) {
                dispatch.listener.onComplete(dispatch.completed);
            }
        });
    }

    private void ensureOpen() {
        if (closed) {
            throw new YoAiTransportException("WebSocket is closed", null);
        }
    }

    static void validateStreamId(@Nullable String streamId) {
        if (streamId == null) {
            return;
        }
        if (streamId.length() < 1 || streamId.length() > 256 || !streamId.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException(
                    "streamId must be 1-256 characters of letters, digits, underscore, hyphen, or dot");
        }
    }

    static String webSocketUrl(String baseUrl) {
        String url = baseUrl;
        if (url.startsWith("https://")) {
            url = "wss://" + url.substring("https://".length());
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring("http://".length());
        }
        return url + WirePaths.RESPONSES;
    }

    private static JSONObject unwrap(JSONObject event) {
        if ("message".equals(event.optString("type")) && event.optJSONObject("message") != null) {
            JSONObject message = event.optJSONObject("message");
            if (!message.has("stream_id") && event.has("stream_id")) {
                try {
                    message.put("stream_id", event.get("stream_id"));
                } catch (JSONException exception) {
                    throw new YoAiProtocolException("Invalid WebSocket envelope", event.toString(), exception);
                }
            }
            return message;
        }
        return event;
    }
}
