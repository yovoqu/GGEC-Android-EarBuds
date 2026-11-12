package com.yovo.biz_ggec_hs.provider.lib_bluetooth;


import com.yovo.biz_ggec_hs.internal.center.port.ScanPace;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_ggec_hs.internal.center.port.HsBleScanLink;
import com.yovo.biz_ggec_hs.internal.center.port.HsRawAdvert;
import com.yovo.lib_bluetooth.api.BleScanFrameFilter;
import com.yovo.lib_bluetooth.api.BleScanHealth;
import com.yovo.lib_bluetooth.api.BleScanPlan;
import com.yovo.lib_bluetooth.api.BleScanSubscription;
import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.model.ble.BleFailure;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;
import com.yovo.lib_bluetooth.model.ble.BleScanObserver;
import com.yovo.lib_bluetooth.model.ble.BleScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * 订阅 lib 的原始 BLE 扫描，把 {@link BleScanResult} 原样翻成 {@link HsRawAdvert}。
 *
 * <p>本类只负责订阅生命周期（谁在扫、句柄何时取消、陈旧回调何时丢弃）、错误码映射与
 * 机制层两条热路径（帧级过滤、扫描档位）：不判断产品、不组装广播分片、不持有业务会话。</p>
 *
 * <p>帧级过滤器与扫描档位都由上层注入：过滤器永远只是「省流量的前置过滤」，
 * 档位变更走 lib 唯一的显式重扫入口 {@link YoBleClient#reconfigureScan(BleScanPlan)}，
 * 且只在目标档位与上次应用的不一致时才重扫，避免每次 start 都多打一次平台启动
 * （30 秒内超过 5 次启动会被系统限流）。</p>
 */
public final class HsNearbyScanner implements HsBleScanLink {

    private final YoBleClient bleClient;
    private final Object lock = new Object();

    /** 订阅代次：每次 start/stop 递增，用来丢弃已失效订阅的回调。 */
    private long subscription;
    private boolean scanning;
    @Nullable
    private BleScanSubscription handle;
    @Nullable
    private Predicate<HsRawAdvert> framePredicate;
    @Nullable
    private BleScanPlan plan;
    @Nullable
    private BleScanPlan appliedPlan;

    public HsNearbyScanner(@NonNull YoBleClient bleClient) {
        this.bleClient = Objects.requireNonNull(bleClient, "bleClient");
    }

    @Override
    public void start(@NonNull Observer observer) {
        Objects.requireNonNull(observer, "observer");
        stop();
        long current;
        BleScanPlan pendingPlan;
        Predicate<HsRawAdvert> pendingPredicate;
        synchronized (lock) {
            current = ++subscription;
            scanning = true;
            pendingPlan = plan;
            pendingPredicate = framePredicate;
        }
        Callbacks callbacks = new Callbacks(current, observer);
        BleScanSubscription subscribed = bleClient.subscribeScan(callbacks);
        boolean retained;
        synchronized (lock) {
            retained = isCurrentLocked(current);
            if (retained) {
                handle = subscribed;
            }
        }
        if (!retained) {
            subscribed.cancel();
            return;
        }
        subscribed.setFrameFilter(toFilter(pendingPredicate));
        applyPlanIfChanged(pendingPlan);
    }

    @Override
    public void stop() {
        BleScanSubscription current;
        synchronized (lock) {
            scanning = false;
            subscription++;
            current = handle;
            handle = null;
        }
        cancel(current);
    }

    @Override
    public boolean isScanning() {
        synchronized (lock) {
            return scanning;
        }
    }

    /**
     * 热替换帧级过滤器：只影响后续帧，不重启扫描、不清空。未订阅时记下来，下次订阅时生效。
     */
    @Override
    public void setFramePredicate(@Nullable Predicate<HsRawAdvert> predicate) {
        BleScanSubscription current;
        synchronized (lock) {
            framePredicate = predicate;
            current = handle;
        }
        if (current != null) {
            current.setFrameFilter(toFilter(predicate));
        }
    }

    /** 记下目标档位；真正生效在 start（唯一合法重扫点由上层动作触发）。 */
    @Override
    public void applyScanProfile(@NonNull ScanPace profile) {
        synchronized (lock) {
            plan = toPlan(profile);
        }
    }

    /** 机制层最近一帧时刻（毫秒，0 表示本会话尚未收到帧）。 */
    @Override
    public long lastFrameAtMillis() {
        BleScanHealth health = bleClient.scanHealth();
        return health == null ? 0L : health.lastFrameAtMillis();
    }

    private void applyPlanIfChanged(@Nullable BleScanPlan pendingPlan) {
        if (pendingPlan == null) {
            return;
        }
        synchronized (lock) {
            if (pendingPlan.equals(appliedPlan)) {
                return;
            }
            appliedPlan = pendingPlan;
        }
        // reconfigureScan 是 lib 唯一的显式重扫入口：平台参数变更必须走它。
        bleClient.reconfigureScan(pendingPlan);
    }

    @Nullable
    private static BleScanFrameFilter toFilter(@Nullable Predicate<HsRawAdvert> predicate) {
        if (predicate == null) {
            return null;
        }
        // 谓词是纯函数；这里只做帧结构转换，不持有设备表。
        return frame -> predicate.test(HsRawAdverts.from(frame));
    }

    @NonNull
    private static BleScanPlan toPlan(@NonNull ScanPace profile) {
        return new BleScanPlan(
                toLibMode(profile.mode()),
                profile.reportDelayMs(),
                false,
                List.of());
    }

    @NonNull
    private static BleScanMode toLibMode(@NonNull ScanPace.Mode mode) {
        return switch (mode) {
            case LOW_POWER -> BleScanMode.LOW_POWER;
            case BALANCED -> BleScanMode.BALANCED;
            case LOW_LATENCY -> BleScanMode.LOW_LATENCY;
        };
    }

    private boolean isCurrent(long candidate) {
        synchronized (lock) {
            return isCurrentLocked(candidate);
        }
    }

    /** 结束指定代次的订阅；返回 false 表示该回调属于已失效的旧订阅。 */
    private boolean end(long candidate) {
        BleScanSubscription current;
        synchronized (lock) {
            if (!isCurrentLocked(candidate)) {
                return false;
            }
            scanning = false;
            subscription++;
            current = handle;
            handle = null;
        }
        cancel(current);
        return true;
    }

    private boolean isCurrentLocked(long candidate) {
        return scanning && subscription == candidate;
    }

    private static void cancel(@Nullable BleScanSubscription subscription) {
        if (subscription != null) {
            subscription.cancel();
        }
    }

    /** 一次订阅的回调桥：代次失效后静默丢弃，不把旧订阅的帧送到上层。 */
    private final class Callbacks implements BleScanObserver {
        private final long generation;
        private final Observer observer;

        private Callbacks(long generation, Observer observer) {
            this.generation = generation;
            this.observer = observer;
        }

        @Override
        public void onDeviceFound(@NonNull BleScanResult result) {
            publish(Collections.singletonList(result));
        }

        @Override
        public void onBatchScanResults(@NonNull List<BleScanResult> results) {
            publish(results);
        }

        @Override
        public void onScanResultLost(@NonNull BleScanResult lostResult) {
            if (lostResult == null || !isCurrent(generation)) {
                return;
            }
            observer.onLost(lostResult.identity().deviceId().value());
        }

        @Override
        public void onScanFailed(@NonNull BleFailure failure) {
            if (!end(generation)) {
                return;
            }
            observer.onFailed(HsScanFailures.map(failure.errorCode()));
        }

        @Override
        public void onScanStopped() {
            if (end(generation)) {
                observer.onStopped();
            }
        }

        private void publish(@NonNull List<BleScanResult> rawResults) {
            if (!isCurrent(generation)) {
                return;
            }
            List<HsRawAdvert> batch = new ArrayList<>(rawResults.size());
            for (BleScanResult raw : rawResults) {
                if (raw == null) {
                    continue;
                }
                batch.add(HsRawAdverts.from(raw));
            }
            if (!batch.isEmpty()) {
                observer.onBatch(Collections.unmodifiableList(batch));
            }
        }
    }
}
