package com.yovo.biz_ggec_hs.internal.scan;





import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;
import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsBroadcastRuntime;
import com.yovo.biz_ggec_hs.internal.center.broadcast.HsBroadcastSession;
import com.yovo.biz_ggec_hs.internal.center.port.HsScanFault;
import com.yovo.biz_ggec_hs.internal.center.port.HsBleScanLink;
import com.yovo.biz_ggec_hs.internal.center.port.HsRawAdvert;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 附近设备：识别 → 入库 → 规则投影 → 视图快照。
 *
 * <p>分工：识别规则在 {@link HsNearbyDevices}，设备表（去重/RSSI 平均/TTL/lost）在
 * {@link HsNearbyRegistry}，规则求值在 {@link HsNearbyRules}；本类只做编排与投影。</p>
 *
 * <p>规则变化只重投影：既不重启扫描也不清表。传输层句柄、蓝牙库错误码归一、订阅代次
 * 都在 {@link HsBleScanLink} 的实现里。</p>
 */
public final class HsNearbyImpl {

    /** 扫描中超过该时长没有任何帧视为静默（平台可能已把扫描停掉）。 */
    static final long SILENT_WINDOW_MILLIS = 5_000L;
    /** 离开扫描页或停止扫描后，产品表保留多久再清空。 */
    static final long RETAIN_MILLIS = 120_000L;

    private final HsBleScanLink link;
    private final HsCatalogImpl catalog;
    private final HsNearbyTicker ticker;
    /** 时间源：静默窗口、TTL 与启动时刻都用它。 */
    private final java.util.function.LongSupplier clock;
    private final HsNearbyScanSession scanSession = new HsNearbyScanSession();
    private final HsNearbyRegistry registry = new HsNearbyRegistry();
    private final HsBroadcastSession broadcastSession = HsBroadcastRuntime.openSession();

    private volatile HsScan.Listener observer;
    private volatile HsScan.Rule rule = HsScan.Rule.none();
    private volatile long startedAtMillis;
    private volatile long lastFrameAtMillis;
    private volatile boolean throttled;
    /** 上一帧已经交给界面的顺序。排序带宽相对它计算，避免 RSSI 噪声来回换位。 */
    private List<HsScan.Found> lastOrdered = List.of();

    /** 装配入口：系统时钟 + 系统 TTL 心跳，内部类型不外露。 */
    public static HsNearbyImpl open(HsBleScanLink link, HsCatalogImpl catalog) {
        return new HsNearbyImpl(link, catalog, new HsNearbySystemTicker(),
                System::currentTimeMillis);
    }

    public HsNearbyImpl(HsBleScanLink link, HsCatalogImpl catalog, HsNearbyTicker ticker) {
        this(link, catalog, ticker, System::currentTimeMillis);
    }

    /** 包内装配入口：时间源可注入，便于用固定时刻验证静默窗口与 TTL。 */
    HsNearbyImpl(HsBleScanLink link, HsCatalogImpl catalog, HsNearbyTicker ticker,
                 java.util.function.LongSupplier clock) {
        this.link = Objects.requireNonNull(link, "link");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void start(HsScan.Listener observer) {
        Objects.requireNonNull(observer, "observer");
        haltRadio();
        this.observer = observer;
        this.startedAtMillis = clock.getAsLong();
        this.lastFrameAtMillis = 0L;
        this.throttled = false;
        long generation = scanSession.begin();
        applyFramePredicate();
        link.start(new ScanCallbacks(generation));
    }

    public void stop() {
        haltRadio();
        ticker.scheduleOnce(this::dropRetained, RETAIN_MILLIS);
    }

    /** 停掉无线电和保留倒计时。不清产品表。 */
    private void haltRadio() {
        scanSession.stop();
        ticker.cancel();
        link.setFramePredicate(null);
        link.stop();
    }

    private void dropRetained() {
        registry.clear();
        publish();
    }

    public boolean isScanning() {
        return link.isScanning();
    }

    public void setFilterRule(HsScan.Rule rule) {
        this.rule = Objects.requireNonNull(rule, "rule");
        // 纯优化：只有能用原始帧字段表达的维度才下推给扫描栈，真源仍是注册表。
        applyFramePredicate();
        publish();
    }

    public HsScan.View view() {
        List<HsNearbyRegistry.Entry> entries = registry.entries();
        List<HsScan.Found> devices = new ArrayList<>();
        Map<HsDevice.Type, Integer> countsByType = new EnumMap<>(HsDevice.Type.class);
        Map<String, Integer> countsByProject = new LinkedHashMap<>();
        Map<String, Integer> countsByModel = new LinkedHashMap<>();
        for (HsNearbyRegistry.Entry entry : entries) {
            if (entry.deviceType() != null) {
                countsByType.merge(entry.deviceType(), 1, Integer::sum);
            }
            if (entry.projectId() != null) {
                countsByProject.merge(entry.projectId(), 1, Integer::sum);
            }
            if (!entry.modelId().isEmpty()) {
                countsByModel.merge(entry.modelId(), 1, Integer::sum);
            }
            if (HsNearbyRules.matches(rule, entry)) {
                devices.add(entry.toDevice());
            }
        }
        List<HsScan.Found> ordered = HsScanOrder.arrange(devices, lastOrdered);
        lastOrdered = ordered;
        return new HsScan.View(
                ordered,
                Map.copyOf(countsByType),
                Map.copyOf(countsByProject),
                Map.copyOf(countsByModel),
                entries.size(),
                isScanning());
    }

    public HsScan.Health health() {
        long now = clock.getAsLong();
        boolean scanning = isScanning();
        // 业务侧只记「认出来的设备帧」；机制层还知道任何平台帧，两者取大更贴近「有帧在流动」。
        long lastActivity = Math.max(Math.max(lastFrameAtMillis, link.lastFrameAtMillis()),
                startedAtMillis);
        boolean silent = scanning
                && lastActivity > 0L
                && now - lastActivity > SILENT_WINDOW_MILLIS;
        return new HsScan.Health(scanning, silent, throttled, lastFrameAtMillis);
    }

    /**
     * 只有 RSSI 下界能用原始帧字段表达，因此只有它能下推给扫描栈；其余维度一律在注册表上求值。
     * 下推是省流量的前置过滤，谓词永远不是真源。
     */
    private void applyFramePredicate() {
        HsScan.Rule current = rule;
        boolean rssiOnly = current.deviceTypes().isEmpty()
                && current.projectIds().isEmpty()
                && current.modelIds().isEmpty()
                && !current.onlyConnectable()
                && current.minRssi() > HsScan.Rule.MIN_RSSI_UNRESTRICTED;
        if (rssiOnly) {
            link.setFramePredicate(advert -> advert.rssi() >= current.minRssi());
        } else {
            link.setFramePredicate(null);
        }
    }

    private void publish() {
        HsScan.Listener current = observer;
        if (current == null) {
            return;
        }
        current.onUpdated(view().devices());
    }

    /** 一次订阅的回调桥：代次失效后静默丢弃，旧会话的帧与 lost 不会串到新会话。 */
    private static HsControl.Failure toFailure(HsScanFault fault) {
        return switch (fault) {
            case BLUETOOTH_DISABLED -> HsControl.Failure.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> HsControl.Failure.PERMISSION_DENIED;
            case LOCATION_DISABLED -> HsControl.Failure.LOCATION_DISABLED;
            case BLE_UNSUPPORTED -> HsControl.Failure.BLE_UNSUPPORTED;
            case SCAN_THROTTLED -> HsControl.Failure.SCAN_THROTTLED;
            case SCAN_START_FAILED -> HsControl.Failure.SCAN_START_FAILED;
            case SCAN_FEATURE_UNSUPPORTED -> HsControl.Failure.SCAN_FEATURE_UNSUPPORTED;
            case SCAN_OUT_OF_RESOURCES -> HsControl.Failure.SCAN_OUT_OF_RESOURCES;
            case SCAN_INTERNAL_ERROR -> HsControl.Failure.SCAN_INTERNAL_ERROR;
            case RELEASED -> HsControl.Failure.RELEASED;
        };
    }

    private final class ScanCallbacks implements HsBleScanLink.Observer {

        private final long generation;

        private ScanCallbacks(long generation) {
            this.generation = generation;
        }

        @Override
        public void onBatch(List<HsRawAdvert> adverts) {
            if (adverts == null || !scanSession.isCurrent(generation)) {
                return;
            }
            boolean changed = false;
            for (HsRawAdvert advert : adverts) {
                if (advert == null) {
                    continue;
                }
                lastFrameAtMillis = Math.max(lastFrameAtMillis, advert.observedAtMillis());
                HsScan.Found device = HsNearbyDevices.map(advert, broadcastSession);
                if (device == null) {
                    continue;
                }
                HsDevice.Product model = catalog.model(device.modelId()).orElse(null);
                registry.record(advert, device, model);
                changed = true;
            }
            if (changed) {
                publish();
            }
        }

        @Override
        public void onLost(String address) {
            if (address == null || !scanSession.isCurrent(generation)) {
                return;
            }
            HsNearbyRegistry.Entry removed = registry.remove(address);
            if (removed == null) {
                return;
            }
            HsScan.Listener current = observer;
            if (current != null) {
                current.onLost(removed.toDevice());
            }
            publish();
        }

        @Override
        public void onFailed(HsScanFault failure) {
            if (!scanSession.end(generation)) {
                return;
            }
            ticker.cancel();
            HsControl.Failure reported = toFailure(failure);
            if (reported == HsControl.Failure.SCAN_THROTTLED) {
                throttled = true;
            }
            HsScan.Listener current = observer;
            if (current != null) {
                current.onFailed(reported);
                current.onFinished();
            }
            publish();
        }

        @Override
        public void onStopped() {
            if (!scanSession.end(generation)) {
                return;
            }
            ticker.cancel();
            HsScan.Listener current = observer;
            if (current != null) {
                current.onFinished();
            }
            publish();
        }
    }
}
