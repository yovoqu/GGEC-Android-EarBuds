package com.yovo.lib_ai.internal.protocol;

import androidx.annotation.Nullable;

import com.yovo.lib_ai.error.YoAiException;
import com.yovo.lib_ai.error.YoAiHttpException;
import com.yovo.lib_ai.error.YoAiProtocolException;
import com.yovo.lib_ai.internal.json.Jsons;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.runtime.OrderedCallbacks;
import com.yovo.lib_ai.internal.transport.HttpExchange;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProtocolSupport {

    private ProtocolSupport() {
    }

    public static Map<String, String> jsonHeaders(Map<String, String> auth, boolean stream) {
        Map<String, String> headers = new LinkedHashMap<>(auth);
        headers.put("Content-Type", "application/json");
        headers.put("Accept", stream ? "text/event-stream" : "application/json");
        headers.put("User-Agent", "lib_ai");
        return headers;
    }

    public static String url(String baseUrl, String path) {
        return baseUrl + path;
    }

    public static void requireSuccess(HttpExchange exchange) {
        int status = exchange.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }
        String body = exchange.body() == null ? "" : exchange.body();
        throw httpException(status, exchange.requestId(), body);
    }

    public static YoAiHttpException httpException(int status, @Nullable String requestId, @Nullable String body) {
        String message = "HTTP " + status;
        String type = null;
        if (body != null && body.trim().startsWith("{")) {
            try {
                JSONObject json = new JSONObject(body);
                JSONObject error = Jsons.optObject(json, "error");
                if (error != null) {
                    String errorMessage = Jsons.optString(error, "message");
                    if (errorMessage != null && !errorMessage.isBlank()) {
                        message = errorMessage;
                    }
                    type = Jsons.optString(error, "type");
                } else {
                    String topMessage = Jsons.optString(json, "message");
                    if (topMessage != null && !topMessage.isBlank()) {
                        message = topMessage;
                    }
                }
            } catch (JSONException ignored) {
                message = "HTTP " + status;
            }
        }
        return new YoAiHttpException(message, status, requestId, type, Jsons.truncate(body, 8192));
    }

    public static void fail(
            OrderedCallbacks callbacks,
            CancelToken token,
            AtomicBoolean settled,
            FailureListener listener,
            YoAiException error) {
        if (token.isCancelled() || !settled.compareAndSet(false, true)) {
            return;
        }
        callbacks.submit(() -> listener.onFailure(error));
    }

    public static YoAiException asAi(Throwable throwable) {
        if (throwable instanceof YoAiException aiException) {
            return aiException;
        }
        if (throwable instanceof JSONException jsonException) {
            return new YoAiProtocolException(jsonException.getMessage(), null, jsonException);
        }
        if (throwable instanceof java.io.IOException) {
            return new com.yovo.lib_ai.error.YoAiTransportException(
                    throwable.getMessage() == null ? "Transport failure" : throwable.getMessage(),
                    throwable);
        }
        return new YoAiProtocolException(
                throwable.getMessage() == null ? "Protocol failure" : throwable.getMessage(),
                null,
                throwable);
    }

    public interface FailureListener {
        void onFailure(YoAiException error);
    }
}
