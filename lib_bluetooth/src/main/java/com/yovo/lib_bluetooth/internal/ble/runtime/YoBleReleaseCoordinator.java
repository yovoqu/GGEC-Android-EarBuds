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
    private final List<YoReleasable> resources = new ArrayList<>();
    private boolean released;

    public synchronized void register(@NonNull YoReleasable resource) {
        if (released) {
            try {
                resource.release();
            } catch (Throwable ignored) {
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
            } catch (Throwable t) {
                BluetoothDiagnosticsBus.get().recordException(TAG,
                        "释放组件异常: " + resource.getClass().getSimpleName(), t);
            }
        }
    }
}
