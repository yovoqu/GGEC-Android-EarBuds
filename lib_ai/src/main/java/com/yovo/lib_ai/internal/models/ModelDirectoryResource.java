package com.yovo.lib_ai.internal.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoModels;
import com.yovo.lib_ai.internal.protocol.ProtocolSupport;
import com.yovo.lib_ai.internal.runtime.CallRegistry;
import com.yovo.lib_ai.internal.runtime.CancelToken;
import com.yovo.lib_ai.internal.transport.HttpExchange;
import com.yovo.lib_ai.internal.transport.HttpRequestSpec;
import com.yovo.lib_ai.internal.transport.OkHttpTransport;
import com.yovo.lib_ai.models.YoModelInfo;
import com.yovo.lib_ai.models.YoModelListing;
import com.yovo.lib_ai.models.YoModelPage;
import com.yovo.lib_ai.platform.YoServiceTarget;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模型列表的执行与翻页循环。路径、翻页参数和解码都来自平台给的 {@link YoModelListing}，
 * 这里只负责发请求、校验状态码、合并页并去重。
 */
public final class ModelDirectoryResource implements YoModels {

    /** 防止异常主机无限翻页。每页 1000 条时足以覆盖任何真实账号。 */
    static final int MAX_PAGES = 20;

    private final OkHttpTransport transport;
    private final YoModelListing listing;
    private final CallRegistry registry;

    public ModelDirectoryResource(OkHttpTransport transport, YoModelListing listing, CallRegistry registry) {
        this.transport = transport;
        this.listing = listing;
        this.registry = registry;
    }

    @NonNull
    @Override
    public List<YoModelInfo> list() {
        CancelToken token = new CancelToken();
        registry.register(token);
        try {
            Map<String, YoModelInfo> byId = new LinkedHashMap<>();
            Set<String> visited = new HashSet<>();
            String cursor = null;
            for (int page = 0; page < MAX_PAGES; page++) {
                YoModelPage decoded = fetch(cursor, token);
                for (YoModelInfo model : decoded.models()) {
                    byId.putIfAbsent(model.id(), model);
                }
                cursor = decoded.nextCursor();
                if (cursor == null || !visited.add(cursor)) {
                    break;
                }
            }
            return List.copyOf(byId.values());
        } finally {
            registry.unregister(token);
        }
    }

    private YoModelPage fetch(@Nullable String cursor, CancelToken token) {
        YoServiceTarget target = listing.target();
        Map<String, String> headers = new LinkedHashMap<>(target.headers());
        headers.put("Accept", "application/json");
        headers.put("User-Agent", "lib_ai");
        HttpExchange exchange = transport.execute(new HttpRequestSpec(
                "GET",
                ProtocolSupport.url(target.baseUrl(), listing.path(cursor)),
                headers,
                null,
                false,
                token));
        try {
            ProtocolSupport.requireSuccess(exchange);
            return listing.decode(exchange.body() == null ? "" : exchange.body());
        } finally {
            exchange.close();
        }
    }
}
