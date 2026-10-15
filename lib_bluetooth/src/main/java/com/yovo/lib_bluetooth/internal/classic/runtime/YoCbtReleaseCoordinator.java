package com.yovo.lib_bluetooth.internal.classic.runtime;

import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.common.lifecycle.YoReleasable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 协调多个 {@link YoReleasable} 组件按注册逆序释放，保证依赖安全 teardown。
 */
public final class YoCbtReleaseCoordinator {

    private static final String TAG = "YoCbtReleaseCoordinator";

    private final LinkedHashMap<String, YoReleasable> components;
    private boolean released;

    public YoCbtReleaseCoordinator() {
        this.components = new LinkedHashMap<>();
        this.released = false;
    }

    /**
     * 注册待协调释放的组件。
     *
     * @param name       组件名称，用于日志与查找
     * @param releasable 在 {@link #releaseAll()} 时释放的组件
     * @throws IllegalStateException    若协调器已被释放
     * @throws IllegalArgumentException 若同名组件已注册
     */
    public synchronized void register(@NonNull String name, @NonNull YoReleasable releasable) {
        if (released) {
            throw new IllegalStateException("ReleaseCoordinator has already been released");
        }
        if (components.containsKey(name)) {
            throw new IllegalArgumentException(
                    "Component '" + name + "' is already registered");
        }
        components.put(name, releasable);
    }

    /**
     * 按注册的<b>逆序</b>释放所有已注册组件。
     * <p>
     * 方法返回后协调器标记为已释放，后续调用无效。
     * <p>
     * 单个组件释放失败仅记录日志，不影响其余组件释放。
     */
    public synchronized void releaseAll() {
        if (released) {
            YoBluetoothLogger.w(TAG, "releaseAll() 被重复调用——已忽略");
            return;
        }
        released = true;

        String[] names = components.keySet().toArray(new String[0]);
        for (int i = names.length - 1; i >= 0; i--) {
            String name = names[i];
            YoReleasable component = components.get(name);
            if (component != null) {
                try {
                    YoBluetoothLogger.d(TAG, "正在释放组件: " + name);
                    component.release();
                } catch (Throwable t) {
                    YoBluetoothLogger.e(TAG, "释放组件 '" + name + "' 出错", t);
                }
            }
        }
        components.clear();
    }

    /**
     * 检查指定组件是否已视为释放（即协调器整体已释放）。
     *
     * @param name 组件名称
     * @return 若协调器已释放则返回 true
     */
    public synchronized boolean isReleased(@NonNull String name) {
        return released || !components.containsKey(name);
    }
}
