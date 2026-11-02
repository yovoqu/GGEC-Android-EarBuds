package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.port.AiCancellable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 平台测速的运行状态：只由 ViewModel 在页面生命周期内持有。 */
final class PlatformSpeedTestState {

    private final Set<String> testing = ConcurrentHashMap.newKeySet();
    private boolean running;
    private long generation;
    @Nullable
    private AiCancellable current;

    boolean running() {
        return running;
    }

    @NonNull
    Set<String> testing() {
        return testing;
    }

    void begin(@NonNull Iterable<String> modelIds) {
        testing.clear();
        for (String modelId : modelIds) {
            testing.add(modelId);
        }
        running = true;
        generation++;
    }

    long generation() {
        return generation;
    }

    void attach(@Nullable AiCancellable test) {
        current = test;
    }

    @Nullable
    AiCancellable detach() {
        AiCancellable test = current;
        current = null;
        return test;
    }

    void result(@NonNull String modelId) {
        testing.remove(modelId);
    }

    void finish() {
        current = null;
        running = false;
        testing.clear();
        generation++;
    }

    /** 取消/销毁时让旧回调立即失效；不必等待底层网关真的停止。 */
    void invalidate() {
        finish();
    }
}
