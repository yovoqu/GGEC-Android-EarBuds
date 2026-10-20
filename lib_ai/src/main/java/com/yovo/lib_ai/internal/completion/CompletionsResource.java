package com.yovo.lib_ai.internal.completion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoAiCall;
import com.yovo.lib_ai.api.YoAnthropicMessages;
import com.yovo.lib_ai.api.YoChatCompletions;
import com.yovo.lib_ai.api.YoCompletions;
import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.api.YoResponses;
import com.yovo.lib_ai.completion.YoCompletionListener;
import com.yovo.lib_ai.completion.YoCompletionRequest;
import com.yovo.lib_ai.completion.YoCompletionResult;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 统一补全入口：按请求里的协议挑适配器，未指定时用平台的默认协议。
 */
public final class CompletionsResource implements YoCompletions {

    private final Map<YoProtocol, CompletionAdapter> adapters = new EnumMap<>(YoProtocol.class);

    public CompletionsResource(
            @Nullable YoChatCompletions chat,
            @Nullable YoResponses responses,
            @Nullable YoAnthropicMessages messages) {
        if (chat != null) {
            adapters.put(YoProtocol.CHAT_COMPLETIONS, new ChatCompletionAdapter(chat));
        }
        if (messages != null) {
            adapters.put(YoProtocol.MESSAGES, new MessagesCompletionAdapter(messages));
        }
        if (responses != null) {
            adapters.put(YoProtocol.RESPONSES, new ResponsesCompletionAdapter(responses));
        }
    }

    @NonNull
    @Override
    public Set<YoProtocol> protocols() {
        return adapters.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(adapters.keySet()));
    }

    @NonNull
    @Override
    public YoProtocol defaultProtocol() {
        for (YoProtocol protocol : new YoProtocol[] {
                YoProtocol.CHAT_COMPLETIONS, YoProtocol.MESSAGES, YoProtocol.RESPONSES}) {
            if (adapters.containsKey(protocol)) {
                return protocol;
            }
        }
        throw new IllegalStateException("No protocol is available");
    }

    @NonNull
    @Override
    public YoCompletionResult complete(@NonNull YoCompletionRequest request) {
        return adapter(request).complete(request);
    }

    @NonNull
    @Override
    public YoAiCall stream(@NonNull YoCompletionRequest request, @NonNull YoCompletionListener listener) {
        return adapter(request).stream(request, listener);
    }

    private CompletionAdapter adapter(YoCompletionRequest request) {
        YoProtocol protocol = request.protocol() == null ? defaultProtocol() : request.protocol();
        CompletionAdapter adapter = adapters.get(protocol);
        if (adapter == null) {
            throw new IllegalArgumentException("Platform does not provide " + protocol.id());
        }
        return adapter;
    }
}
