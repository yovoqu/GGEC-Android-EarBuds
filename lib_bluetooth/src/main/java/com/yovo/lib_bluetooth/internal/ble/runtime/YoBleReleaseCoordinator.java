package com.yovo.lib_bluetooth.internal.ble.runtime;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.diagnostics.BluetoothDiagnosticsBus;
import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * BLE 资源释放协调器：按逆序安全释放所有已注册组件，单点异常不阻断其余资源关闭。
 */
public final class YoBleReleaseCoordinator {

    private static final String TAG = "YoBleRelease";
    private final BluetoothDiagnosticsBus diagnostics;
    private final List<YoReleasable> resources = new ArrayList<>();
    private boolean released;

    public YoBleReleaseCoordinator(@NonNull BluetoothDiagnosticsBus diagnostics) {
        this.diagnostics = diagnostics;
    }

    public synchronized void register(@NonNull YoReleasable resource) {
        if (released) {
            // 已释放后注册：立即释放该资源（调用方无法再自己释放），失败进同一诊断通道。
            try {
                resource.release();
            } catch (RuntimeException releaseFailure) {
                diagnostics.recordException(TAG,
                        "释放后注册组件异常: " + resource.getClass().getSimpleName(), releaseFailure);
            }
            return;
        }
        resources.add(resource);
    }

    public void releaseAll() {
        List<YoReleasable> snapshot;
        synchronized (this) {
            if (released) {
                return;
            }
            released = true;
            snapshot = new ArrayList<>(resources);
            resources.clear();
        }
        Collections.reverse(snapshot);
        for (YoReleasable resource : snapshot) {
            try {
                resource.release();
            } catch (RuntimeException releaseFailure) {
                diagnostics.recordException(TAG,
                        "释放组件异常: " + resource.getClass().getSimpleName(), releaseFailure);
            }
        }
    }
}
