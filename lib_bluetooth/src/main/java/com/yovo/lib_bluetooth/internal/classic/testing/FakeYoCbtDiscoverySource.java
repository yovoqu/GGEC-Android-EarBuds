package com.yovo.lib_bluetooth.internal.classic.testing;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.classic.device.identity.YoCbtDeviceClass;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtDeviceSnapshot;
import com.yovo.lib_bluetooth.internal.classic.device.snapshot.YoCbtObservedDevice;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryObserver;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryResult;
import com.yovo.lib_bluetooth.internal.classic.discovery.YoCbtDiscoveryState;
import com.yovo.lib_bluetooth.internal.classic.link.YoCbtAclLinkState;
import com.yovo.lib_bluetooth.internal.classic.pairing.YoCbtBondState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description 模拟经典蓝牙设备扫描的 Fake 发现源，无需真实 Android 蓝牙适配器。
 */
public final class FakeYoCbtDiscoverySource {

    private final CopyOnWriteArrayList<YoCbtObservedDevice> configuredDevices =
            new CopyOnWriteArrayList<>();
    private final AtomicBoolean discovering = new AtomicBoolean(false);
    private final List<YoCbtObservedDevice> discoveredDuringSession =
            Collections.synchronizedList(new ArrayList<YoCbtObservedDevice>());

    private YoCbtDiscoveryObserver currentObserver;
    private Handler handler;
    private Runnable discoveryRunner;
    private long durationMs;

    /**
     * 将设备加入下次假发现会话中会被「发现」的设备集合。
     *
     * @param device 要添加的观测设备
     */
    public void addDevice(@NonNull YoCbtObservedDevice device) {
        if (device == null) return;
        configuredDevices.add(device);
    }

    /**
     * 一次性添加多个设备。
     *
     * @param devices 要添加的观测设备
     */
    public void addDevices(@NonNull List<YoCbtObservedDevice> devices) {
        if (devices == null) return;
        configuredDevices.addAll(devices);
    }

    /**
     * 从预配置集合中移除设备。
     *
     * @param device 要移除的设备
     */
    public void removeDevice(@NonNull YoCbtObservedDevice device) {
        configuredDevices.remove(device);
    }

    /**
     * 清除所有预配置设备。
     */
    public void clearDevices() {
        configuredDevices.clear();
    }

    /**
     * 返回当前已配置设备的不可修改视图。
     */
    @NonNull
    public List<YoCbtObservedDevice> getConfiguredDevices() {
        return Collections.unmodifiableList(new ArrayList<>(configuredDevices));
    }

    /**
     * 返回最近一次发现会话中上报的设备。
     */
    @NonNull
    public List<YoCbtObservedDevice> getDiscoveredDevices() {
        synchronized (discoveredDuringSession) {
            return new ArrayList<>(discoveredDuringSession);
        }
    }

    /**
     * 启动模拟发现会话。
     *
     * <p>在主线程上依次向观察者投递设备，状态依次经过：
     * {@code IDLE -> DISCOVERING -> deviceFound*n -> FINISHED}。
     *
     * @param observer   接收发现事件的观察者
     * @param durationMs 假发现总时长（毫秒，须为正数）
     */
    public void startFakeDiscovery(@NonNull YoCbtDiscoveryObserver observer,
                                   long durationMs) {
        if (observer == null) {
            throw new IllegalArgumentException("observer must not be null");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be positive");
        }
        if (!discovering.compareAndSet(false, true)) {
            // 已在发现中 —— 通知并失败
            observer.onDiscoveryFailed(
                    com.yovo.lib_bluetooth.internal.classic.error.YoCbtFailure.of(
                            com.yovo.lib_bluetooth.internal.classic.error.YoCbtErrorCode.YO_CBT_DISCOVERY_ALREADY_RUNNING,
                            "Fake discovery already in progress",
                            "FakeYoCbtDiscoverySource"));
            return;
        }

        this.currentObserver = observer;
        this.durationMs = durationMs;
        this.discoveredDuringSession.clear();

        this.handler = new Handler(Looper.getMainLooper());

        // 通知 PREPARING
        observer.onDiscoveryStateChanged(YoCbtDiscoveryState.PREPARING);

        // 短暂延迟后调度 DISCOVERING 状态
        handler.postDelayed(() -> {
            if (!discovering.get()) return;
            observer.onDiscoveryStateChanged(YoCbtDiscoveryState.DISCOVERING);

            // 逐个投递设备，间隔较短
            deliverDevices(0);

            // 所有设备投递完成后调度发现结束
            long perDeviceDelay = Math.min(50, durationMs / Math.max(1, configuredDevices.size()));
            long totalDeliveryTime = configuredDevices.size() * perDeviceDelay;
            long finishDelay = Math.max(durationMs, totalDeliveryTime);

            handler.postDelayed(() -> {
                if (!discovering.get()) return;
                discovering.set(false);
                observer.onDiscoveryStateChanged(YoCbtDiscoveryState.FINISHED);
                cleanup();
            }, finishDelay);
        }, 100);
    }

    /**
     * 停止进行中的假发现会话。
     */
    public void stopFakeDiscovery() {
        if (!discovering.compareAndSet(true, false)) {
            return; // 未在发现中 —— 空操作
        }

        currentObserver.onDiscoveryStateChanged(YoCbtDiscoveryState.STOPPING);
        currentObserver.onDiscoveryStateChanged(YoCbtDiscoveryState.IDLE);
        cleanup();
    }

    /**
     * 返回假发现会话当前是否活跃。
     */
    public boolean isDiscovering() {
        return discovering.get();
    }

    // ── 内部 ─────────────────────────────────────────────────────

    private void deliverDevices(int index) {
        if (!discovering.get() || index >= configuredDevices.size()) return;

        YoCbtObservedDevice device = configuredDevices.get(index);
        if (device == null) return;

        long now = System.currentTimeMillis();
        YoCbtDeviceSnapshot snapshot = buildSnapshot(device, now);

        discoveredDuringSession.add(device);
        currentObserver.onDeviceFound(new YoCbtDiscoveryResult(snapshot, now));

        // 短延迟后投递下一个设备
        handler.postDelayed(() -> deliverDevices(index + 1), 50);
    }

    @NonNull
    private static YoCbtDeviceSnapshot buildSnapshot(@NonNull YoCbtObservedDevice device,
                                                      long now) {
        return new YoCbtDeviceSnapshot(
                device.getIdentity(),
                YoCbtBondState.NONE,
                YoCbtAclLinkState.UNKNOWN,
                new java.util.ArrayList<>(),
                new java.util.EnumMap<>(com.yovo.lib_bluetooth.internal.classic.profiles.core.YoCbtProfileType.class),
                new java.util.ArrayList<>(),
                device.getRssi(),
                device.getObservedAtMillis(),
                now);
    }

    private void cleanup() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        currentObserver = null;
    }
}
