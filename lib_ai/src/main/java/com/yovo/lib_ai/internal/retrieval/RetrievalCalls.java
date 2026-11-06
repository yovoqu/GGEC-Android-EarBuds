package com.yovo.lib_ai.internal.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.platform.YoServiceTarget;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** 检索类服务共用的请求装配。 */
public final class RetrievalCalls {

    private RetrievalCalls() {
    }

    @NonNull
    public static Map<String, String> headers(@NonNull YoServiceTarget target) {
        Map<String, String> headers = new LinkedHashMap<>(target.headers());
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "lib_ai");
        return headers;
    }

    @NonNull
    public static HttpExchange json(
            @NonNull RetrievalHttp http,
            @NonNull YoServiceTarget target,
            @NonNull String method,
            @NonNull String path,
            @Nullable JSONObject body,
            @NonNull CancelToken token) {
        return http.execute(
                method,
                ProtocolSupport.url(target.baseUrl(), path),
                headers(target),
                body == null ? null : RetrievalHttp.jsonBody(body.toString()),
                token);
    }
}
