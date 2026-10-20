package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiChatGateway;
import com.ggec.earbuds.domain.ai.port.AiChatListener;
import com.ggec.earbuds.domain.ai.port.AiChatRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本化的对话网关：每次 stream 只记下请求和监听器，由测试决定什么时候推送增量、完成还是失败。
 * 这样可以精确演练"流到一半被停止""停止后又来了迟到的回调"这类时序。
 */
public final class ScriptedChatGateway implements AiChatGateway {

    public final List<AiChatRequest> requests = new ArrayList<>();
    public final List<AiChatListener> listeners = new ArrayList<>();
    public final List<Handle> handles = new ArrayList<>();
    public AiFailure failOnStream;

    @Override
    public AiCancellable stream(AiChatRequest request, AiChatListener listener) {
        if (failOnStream != null) {
            throw failOnStream;
        }
        requests.add(request);
        listeners.add(listener);
        Handle handle = new Handle();
        handles.add(handle);
        return handle;
    }

    public AiChatRequest lastRequest() {
        return requests.get(requests.size() - 1);
    }

    public AiChatListener lastListener() {
        return listeners.get(listeners.size() - 1);
    }

    public Handle lastHandle() {
        return handles.get(handles.size() - 1);
    }

    public static final class Handle implements AiCancellable {
        public boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
