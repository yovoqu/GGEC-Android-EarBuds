package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestGateway;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚本化的测速网关：只记下要测的模型和监听器，由测试决定哪个模型什么时候开始、出什么结果、是否结束。
 */
public final class ScriptedSpeedGateway implements AiSpeedTestGateway {

    public final List<List<String>> runs = new ArrayList<>();
    public final List<AiSpeedTestListener> listeners = new ArrayList<>();
    public final List<Handle> handles = new ArrayList<>();
    public AiFailure failOnRun;

    @Override
    public AiCancellable run(AiPlatformConfig platform, List<String> modelIds, AiSpeedTestListener listener) {
        if (failOnRun != null) {
            throw failOnRun;
        }
        runs.add(modelIds);
        listeners.add(listener);
        Handle handle = new Handle();
        handles.add(handle);
        return handle;
    }

    public List<String> lastModelIds() {
        return runs.get(runs.size() - 1);
    }

    public AiSpeedTestListener lastListener() {
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
