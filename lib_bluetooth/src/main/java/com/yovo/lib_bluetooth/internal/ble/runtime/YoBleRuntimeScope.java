package com.yovo.lib_bluetooth.internal.ble.runtime;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.lifecycle.YoCloseGuard;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;

import java.util.Objects;

/**
 * 单次 BLE 操作的作用域生命周期；作用域释放后，内部排队的后续回调/任务将被静默丢弃。
 */
public final class YoBleRuntimeScope implements YoReleasable {

    @NonNull
    private final YoBleRuntime runtime;
    private final YoCloseGuard closeGuard = new YoCloseGuard();

    public YoBleRuntimeScope(@NonNull YoBleRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    public boolean isReleased() {
        return closeGuard.isReleased() || runtime.isReleased();
    }

    public void state(@NonNull Runnable task) {
        if (!isReleased()) {
            runtime.state(() -> {
                if (!isReleased()) {
                    task.run();
                }
            });
        }
    }

    public void io(@NonNull Runnable task) {
        if (!isReleased()) {
            runtime.io(() -> {
                if (!isReleased()) {
                    task.run();
                }
            });
        }
    }

    public void callback(@NonNull Runnable task) {
        if (!isReleased()) {
            runtime.callback(() -> {
                if (!isReleased()) {
                    task.run();
                }
            });
        }
    }

    @Override
    public void release() {
        closeGuard.markReleased();
    }
}
