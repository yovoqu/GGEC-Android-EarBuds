package com.yovo.lib_bluetooth.internal.common.bluetooth.radio;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.provider.lib_common.log.YoBtLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 射频与扫描仲裁核心状态机。
 *
 * <p>维护当前活动租约与射频状态；在申请经典发现时协调停止/挂起 BLE 扫描；
 * 在申请 BLE 扫描时确保取消经典发现；在发起任何连接（GATT 或 SPP）前强制执行
 * {@link #preemptForConnection(String)} 确保 cancelDiscovery 并等待底层就绪。</p>
 */
public final class YoBtRadioCoordinator {

    private static final String TAG = "YoBtRadioCoordinator";
    private static final long DEFAULT_SETTLE_TIMEOUT_MS = 200L;
    private static final long DEFAULT_SETTLE_DELAY_MS = 20L;

    private static final Object INSTANCE_LOCK = new Object();
    private static volatile YoBtRadioCoordinator sDefaultInstance;

    /**
     * BLE 扫描与经典蓝牙发现的射频协同钩子。
     */
    public interface BleScanCoordinationHook {
        /**
         * 经典发现启动，通知挂起底层 BLE 硬件扫描以避让射频。
         */
        void onSuspendBleScan();

        /**
         * 经典发现结束，通知恢复底层 BLE 硬件扫描。
         */
        void onResumeBleScan();
    }

    private final YoBtRadioPlatform radioPlatform;
    private final long settleDelayMs;
    private final Object stateLock = new Object();
    private final AtomicLong leaseSeq = new AtomicLong(0L);

    private final Map<String, YoBtRadioLease> activeLeases = new LinkedHashMap<>();
    private final CopyOnWriteArrayList<YoBtRadioStateObserver> observers = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<BleScanCoordinationHook> bleScanHooks = new CopyOnWriteArrayList<>();

    private YoBtRadioState currentState = YoBtRadioState.IDLE;

    /**
     * 获取或创建进程级共享协调器实例。
     */
    @NonNull
    public static YoBtRadioCoordinator get() {
        YoBtRadioCoordinator local = sDefaultInstance;
        if (local == null) {
            synchronized (INSTANCE_LOCK) {
                local = sDefaultInstance;
                if (local == null) {
                    YoBluetoothAdapterProvider adapterProvider = new YoBluetoothAdapterProvider(null);
                    local = sDefaultInstance = new YoBtRadioCoordinator(new YoBtAndroidRadioPlatform(adapterProvider));
                }
            }
        }
        return local;
    }

    /**
     * 根据上下文构造或复用协调器。
     */
    @NonNull
    public static YoBtRadioCoordinator forContext(@Nullable Context context) {
        YoBtRadioCoordinator local = sDefaultInstance;
        if (local == null) {
            synchronized (INSTANCE_LOCK) {
                local = sDefaultInstance;
                if (local == null) {
                    YoBluetoothAdapterProvider adapterProvider = new YoBluetoothAdapterProvider(context);
                    local = sDefaultInstance = new YoBtRadioCoordinator(new YoBtAndroidRadioPlatform(adapterProvider));
                }
            }
        }
        return local;
    }

    /**
     * 根据适配器提供者构造或复用协调器。
     */
    @NonNull
    public static YoBtRadioCoordinator forAdapterProvider(@NonNull YoBluetoothAdapterProvider adapterProvider) {
        YoBtRadioCoordinator local = sDefaultInstance;
        if (local == null) {
            synchronized (INSTANCE_LOCK) {
                local = sDefaultInstance;
                if (local == null) {
                    local = sDefaultInstance = new YoBtRadioCoordinator(new YoBtAndroidRadioPlatform(adapterProvider));
                }
            }
        }
        return local;
    }

    @VisibleForTesting
    public static void setInstanceForTesting(@Nullable YoBtRadioCoordinator coordinator) {
        synchronized (INSTANCE_LOCK) {
            sDefaultInstance = coordinator;
        }
    }

    public YoBtRadioCoordinator(@NonNull YoBtRadioPlatform radioPlatform) {
        this(radioPlatform, DEFAULT_SETTLE_DELAY_MS);
    }

    public YoBtRadioCoordinator(@NonNull YoBtRadioPlatform radioPlatform, long settleDelayMs) {
        this.radioPlatform = Objects.requireNonNull(radioPlatform, "radioPlatform cannot be null");
        this.settleDelayMs = Math.max(0, settleDelayMs);
    }

    @NonNull
    public YoBtRadioPlatform getRadioPlatform() {
        return radioPlatform;
    }

    public void addObserver(@NonNull YoBtRadioStateObserver observer) {
        observers.addIfAbsent(Objects.requireNonNull(observer, "observer cannot be null"));
    }

    public void removeObserver(@NonNull YoBtRadioStateObserver observer) {
        observers.remove(observer);
    }

    public void registerBleScanHook(@NonNull BleScanCoordinationHook hook) {
        bleScanHooks.addIfAbsent(Objects.requireNonNull(hook, "hook cannot be null"));
    }

    public void unregisterBleScanHook(@NonNull BleScanCoordinationHook hook) {
        bleScanHooks.remove(hook);
    }

    /**
     * 获取当前射频状态。
     */
    @NonNull
    public YoBtRadioState getState() {
        synchronized (stateLock) {
            return currentState;
        }
    }

    /**
     * 获取当前所有活动中的射频租约快照。
     */
    @NonNull
    public List<YoBtRadioLease> getActiveLeases() {
        synchronized (stateLock) {
            return Collections.unmodifiableList(new ArrayList<>(activeLeases.values()));
        }
    }

    /**
     * 是否存在指定类型的活动租约。
     */
    public boolean hasActiveLease(@NonNull YoBtRadioLease.Type type) {
        synchronized (stateLock) {
            for (YoBtRadioLease lease : activeLeases.values()) {
                if (lease.getType() == type) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 是否允许启动经典蓝牙发现。
     * 若当前正在进行连接抢占（CONNECTION 租约有效），则禁止启动发现。
     */
    public boolean isClassicDiscoveryAllowed() {
        synchronized (stateLock) {
            return !hasActiveLease(YoBtRadioLease.Type.CONNECTION);
        }
    }

    /**
     * 安全取消底层经典发现。
     */
    public boolean cancelDiscoverySafely() {
        return radioPlatform.cancelDiscovery();
    }

    /**
     * 申请射频租约。
     *
     * <ul>
     *   <li>{@link YoBtRadioLease.Type#BLE_SCAN}：若经典发现处于活动中，主动 cancelDiscovery 并释放经典租约。</li>
     *   <li>{@link YoBtRadioLease.Type#CLASSIC_DISCOVERY}：若处于 CONNECTION 状态则拒绝；否则协调通知挂起 BLE 扫描。</li>
     *   <li>{@link YoBtRadioLease.Type#CONNECTION}：委托 {@link #preemptForConnection(String)} 执行抢占。</li>
     * </ul>
     *
     * @param type 租约类型
     * @param holder 持有者标识
     * @return 成功获取到的租约，若被高优先级冲突拒绝则返回 {@code null}
     */
    @Nullable
    public YoBtRadioLease acquireLease(@NonNull YoBtRadioLease.Type type, @NonNull String holder) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(holder, "holder cannot be null");

        if (type == YoBtRadioLease.Type.CONNECTION) {
            return preemptForConnection(holder);
        }

        List<YoBtRadioLease> cancelledClassicLeases = new ArrayList<>();
        boolean shouldSuspendBle = false;
        YoBtRadioLease grantedLease;
        YoBtRadioState oldState;
        YoBtRadioState newState;

        synchronized (stateLock) {
            if (type == YoBtRadioLease.Type.CLASSIC_DISCOVERY) {
                if (hasActiveLease(YoBtRadioLease.Type.CONNECTION)) {
                    YoBtLogger.w(TAG, "acquireLease rejected CLASSIC_DISCOVERY: radio is preempted for connection");
                    return null;
                }
                shouldSuspendBle = hasActiveLease(YoBtRadioLease.Type.BLE_SCAN);
            } else if (type == YoBtRadioLease.Type.BLE_SCAN) {
                // BLE 扫描申请射频：若经典发现在运行，强制协调取消经典发现
                for (YoBtRadioLease lease : activeLeases.values()) {
                    if (lease.getType() == YoBtRadioLease.Type.CLASSIC_DISCOVERY) {
                        cancelledClassicLeases.add(lease);
                    }
                }
                for (YoBtRadioLease lease : cancelledClassicLeases) {
                    activeLeases.remove(lease.getLeaseId());
                }
            }

            String id = "lease-" + type.name().toLowerCase() + "-" + leaseSeq.incrementAndGet();
            grantedLease = new YoBtRadioLease(id, type, holder, System.currentTimeMillis(), this::releaseLeaseInternal);
            activeLeases.put(id, grantedLease);

            oldState = currentState;
            currentState = recalculateStateLocked();
            newState = currentState;
        }

        // 锁外执行副作用与通知
        if (!cancelledClassicLeases.isEmpty()) {
            radioPlatform.cancelDiscovery();
            for (YoBtRadioLease cancelled : cancelledClassicLeases) {
                cancelled.release();
                notifyDiscoveryPreempted("BLE scan acquired by " + holder);
            }
        }

        if (shouldSuspendBle) {
            for (BleScanCoordinationHook hook : bleScanHooks) {
                try {
                    hook.onSuspendBleScan();
                } catch (Throwable t) {
                    YoBtLogger.w(TAG, "BleScanCoordinationHook.onSuspendBleScan failed", t);
                }
            }
        }

        notifyLeaseAcquired(grantedLease);
        if (oldState != newState) {
            notifyStateChanged(oldState, newState);
        }
        return grantedLease;
    }

    /**
     * 在发起任何连接（GATT 或 SPP）前强制执行抢占：确保 cancelDiscovery、撤销可能残留的经典发现租约，
     * 并等待底层硬件就绪。
     *
     * @param holder 连接持有者（如 GattConnect:MAC 或 SppConnect:MAC）
     * @return CONNECTION 类型的射频占用租约
     */
    @NonNull
    public YoBtRadioLease preemptForConnection(@NonNull String holder) {
        return preemptForConnection(holder, DEFAULT_SETTLE_TIMEOUT_MS);
    }

    /**
     * 在发起任何连接（GATT 或 SPP）前强制执行抢占，并指定底层发现取消等待超时。
     *
     * @param holder 连接持有者标识
     * @param settleTimeoutMs 最大等待发现取消超时（毫秒）
     * @return CONNECTION 类型的射频占用租约
     */
    @NonNull
    public YoBtRadioLease preemptForConnection(@NonNull String holder, long settleTimeoutMs) {
        Objects.requireNonNull(holder, "holder cannot be null");
        List<YoBtRadioLease> cancelledClassicLeases = new ArrayList<>();
        YoBtRadioLease connLease;
        YoBtRadioState oldState;
        YoBtRadioState newState;

        synchronized (stateLock) {
            // 找出并撤销经典发现租约
            for (YoBtRadioLease lease : activeLeases.values()) {
                if (lease.getType() == YoBtRadioLease.Type.CLASSIC_DISCOVERY) {
                    cancelledClassicLeases.add(lease);
                }
            }
            for (YoBtRadioLease lease : cancelledClassicLeases) {
                activeLeases.remove(lease.getLeaseId());
            }

            String id = "lease-conn-" + leaseSeq.incrementAndGet();
            connLease = new YoBtRadioLease(id, YoBtRadioLease.Type.CONNECTION, holder,
                    System.currentTimeMillis(), this::releaseLeaseInternal);
            activeLeases.put(id, connLease);

            oldState = currentState;
            currentState = recalculateStateLocked();
            newState = currentState;
        }

        // 锁外执行底层硬件 cancelDiscovery 与等待就绪
        boolean hadDiscovery = radioPlatform.isDiscovering() || !cancelledClassicLeases.isEmpty();
        radioPlatform.cancelDiscovery();

        for (YoBtRadioLease cancelled : cancelledClassicLeases) {
            cancelled.release();
        }
        if (hadDiscovery) {
            notifyDiscoveryPreempted("Connection preempted by " + holder);
        }

        // 轮询等待底层 cancelDiscovery 生效（避免 133 超时与射频冲突）
        long deadline = System.currentTimeMillis() + Math.max(0, settleTimeoutMs);
        while (radioPlatform.isDiscovering() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 给予射频基带短暂恢复时间
        if (settleDelayMs > 0) {
            try {
                Thread.sleep(settleDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        notifyLeaseAcquired(connLease);
        if (oldState != newState) {
            notifyStateChanged(oldState, newState);
        }
        return connLease;
    }

    private void releaseLeaseInternal(@NonNull YoBtRadioLease lease) {
        boolean shouldResumeBle = false;
        YoBtRadioState oldState;
        YoBtRadioState newState;

        synchronized (stateLock) {
            activeLeases.remove(lease.getLeaseId());
            oldState = currentState;
            currentState = recalculateStateLocked();
            newState = currentState;

            // 若经典发现释放，且当前仍有 BLE 扫描且无连接抢占，则协调恢复 BLE 扫描
            if (lease.getType() == YoBtRadioLease.Type.CLASSIC_DISCOVERY
                    && !hasActiveLease(YoBtRadioLease.Type.CLASSIC_DISCOVERY)
                    && !hasActiveLease(YoBtRadioLease.Type.CONNECTION)
                    && hasActiveLease(YoBtRadioLease.Type.BLE_SCAN)) {
                shouldResumeBle = true;
            }
        }

        if (shouldResumeBle) {
            for (BleScanCoordinationHook hook : bleScanHooks) {
                try {
                    hook.onResumeBleScan();
                } catch (Throwable t) {
                    YoBtLogger.w(TAG, "BleScanCoordinationHook.onResumeBleScan failed", t);
                }
            }
        }

        notifyLeaseReleased(lease);
        if (oldState != newState) {
            notifyStateChanged(oldState, newState);
        }
    }

    /**
     * 释放所有当前处于活动中的租约。
     */
    public void releaseAllLeases() {
        List<YoBtRadioLease> leasesToRelease;
        synchronized (stateLock) {
            leasesToRelease = new ArrayList<>(activeLeases.values());
            activeLeases.clear();
            YoBtRadioState old = currentState;
            currentState = YoBtRadioState.IDLE;
            if (old != YoBtRadioState.IDLE) {
                notifyStateChanged(old, YoBtRadioState.IDLE);
            }
        }
        for (YoBtRadioLease lease : leasesToRelease) {
            lease.release();
        }
    }

    private YoBtRadioState recalculateStateLocked() {
        boolean hasConn = false;
        boolean hasDiscovery = false;
        boolean hasBleScan = false;
        for (YoBtRadioLease lease : activeLeases.values()) {
            switch (lease.getType()) {
                case CONNECTION:
                    hasConn = true;
                    break;
                case CLASSIC_DISCOVERY:
                    hasDiscovery = true;
                    break;
                case BLE_SCAN:
                    hasBleScan = true;
                    break;
            }
        }
        if (hasConn) {
            return YoBtRadioState.CONNECTING;
        } else if (hasDiscovery) {
            return YoBtRadioState.CLASSIC_DISCOVERING;
        } else if (hasBleScan) {
            return YoBtRadioState.BLE_SCANNING;
        } else {
            return YoBtRadioState.IDLE;
        }
    }

    private void notifyStateChanged(@NonNull YoBtRadioState oldState, @NonNull YoBtRadioState newState) {
        for (YoBtRadioStateObserver observer : observers) {
            try {
                observer.onRadioStateChanged(oldState, newState);
            } catch (Throwable t) {
                YoBtLogger.w(TAG, "observer.onRadioStateChanged failed", t);
            }
        }
    }

    private void notifyLeaseAcquired(@NonNull YoBtRadioLease lease) {
        for (YoBtRadioStateObserver observer : observers) {
            try {
                observer.onLeaseAcquired(lease);
            } catch (Throwable t) {
                YoBtLogger.w(TAG, "observer.onLeaseAcquired failed", t);
            }
        }
    }

    private void notifyLeaseReleased(@NonNull YoBtRadioLease lease) {
        for (YoBtRadioStateObserver observer : observers) {
            try {
                observer.onLeaseReleased(lease);
            } catch (Throwable t) {
                YoBtLogger.w(TAG, "observer.onLeaseReleased failed", t);
            }
        }
    }

    private void notifyDiscoveryPreempted(@NonNull String reason) {
        for (YoBtRadioStateObserver observer : observers) {
            try {
                observer.onDiscoveryPreempted(reason);
            } catch (Throwable t) {
                YoBtLogger.w(TAG, "observer.onDiscoveryPreempted failed", t);
            }
        }
    }
}
