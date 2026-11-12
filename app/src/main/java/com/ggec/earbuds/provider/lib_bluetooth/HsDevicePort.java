package com.ggec.earbuds.provider.lib_bluetooth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.Subscription;
import com.yovo.biz_ggec_hs.YoHsSdk;
import com.yovo.biz_ggec_hs.api.HsConnectionObserver;
import com.yovo.biz_ggec_hs.api.HsNearbyObserver;
import com.yovo.biz_ggec_hs.api.HsRuntime;
import com.yovo.biz_ggec_hs.api.YoHsClient;
import com.yovo.biz_ggec_hs.api.YoHsNearby;
import com.yovo.biz_ggec_hs.model.HsBattery;
import com.yovo.biz_ggec_hs.model.HsDeviceInfo;
import com.yovo.biz_ggec_hs.model.HsDeviceObserver;
import com.yovo.biz_ggec_hs.model.HsNearbyDevice;
import com.yovo.biz_ggec_hs.model.HsScanFailure;
import com.yovo.lib_common.log.YoLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 把 HS 运行时投影成设备端口。扫描参数、广播识别、GATT 与连接就绪超时都在 {@code biz_ggec_hs}；
 * 本类只把 {@link YoHsNearby}/{@link HsConnectionObserver}/{@link HsDeviceObserver} 事件投影为
 * {@link DeviceSnapshot}/{@link ScanResult}，不持有连接超时、代次或主线程 Handler。
 */
public final class HsDevicePort implements DevicePort {

    private static final String TAG = "HsDevicePort";

    private final YoHsNearby nearby;
    private final YoHsClient hsClient;
    private final Runnable releaseAction;
    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    private volatile DeviceSnapshot currentSnapshot = DeviceSnapshot.idle();
    private String targetModelId = "";
    private String targetVariantId = "";
    private String targetMac = "";
    private String targetBleName = "";
    private String reportedFirmware = "";
    private int reportedEarColor = DeviceSnapshot.UNKNOWN_EAR_COLOR;
    private int reportedEarInCaseStatus = HsDeviceInfo.UNKNOWN_EAR_IN_CASE;

    public HsDevicePort(@NonNull Context context) {
        this(YoHsSdk.open(context));
    }

    HsDevicePort(@NonNull HsRuntime runtime) {
        this(runtime.nearby(), runtime.client(), runtime::close);
    }

    HsDevicePort(@NonNull YoHsNearby nearby, @NonNull YoHsClient client) {
        this(nearby, client, () -> {
            nearby.stop();
            client.close();
        });
    }

    private HsDevicePort(@NonNull YoHsNearby nearby,
                         @NonNull YoHsClient client,
                         @NonNull Runnable releaseAction) {
        this.nearby = Objects.requireNonNull(nearby, "nearby");
        this.hsClient = Objects.requireNonNull(client, "client");
        this.releaseAction = Objects.requireNonNull(releaseAction, "releaseAction");
    }

    @Override
    public DeviceSnapshot snapshot() {
        return currentSnapshot;
    }

    @Override
    public Subscription observe(Consumer<DeviceSnapshot> listener) {
        if (listener == null) {
            return () -> {
            };
        }
        listeners.add(listener);
        listener.accept(currentSnapshot);
        return () -> listeners.remove(listener);
    }

    @Override
    public void connect(String modelId, String variantId, String bleMac, String bleName) {
        if (bleMac == null || bleMac.isEmpty()) {
            throw new IllegalArgumentException("connect requires a non-empty BLE mac address");
        }
        synchronized (lock) {
            targetModelId = modelId == null ? "" : modelId;
            targetVariantId = variantId == null ? "" : variantId;
            targetMac = bleMac;
            targetBleName = bleName == null ? "" : bleName;
            // 换设备即作废旧设备的静态信息：新会话未回报前保持未知，不回放上一台设备的值。
            resetReportedDeviceInfoLocked();
        }
        publish(ConnectionState.CONNECTING, BatterySnapshot.unknown());
        Bridge bridge = new Bridge();
        hsClient.setConnectionObserver(bridge);
        // 顺序即契约：设备信息观察者必须在新会话开始之后才挂上。
        // biz 在 connect() 入口同步清空本会话域（HsProtocolEngine.connect → clearSessionState），
        // 而 setDeviceObserver 会同步回放域里已有的固件 / 颜色。若先挂观察者再连接，回放读到的是
        // 上一台设备的值——此刻 A 会话仍 ready，任何「ready 才采纳」的门槛都挡不住旧值。
        // 改成连接之后挂：回放只可能读到已清空的新会话，因此不会污染本台设备的快照。
        // 连接返回后才 READY（READY 由链路回调在 biz 执行器上异步给出），这段同步窗口不会丢事件。
        hsClient.connect(bleMac);
        hsClient.setDeviceObserver(bridge);
    }

    @Override
    public void disconnect() {
        hsClient.disconnect();
        synchronized (lock) {
            resetReportedDeviceInfoLocked();
        }
        publish(ConnectionState.DISCONNECTED, BatterySnapshot.unknown());
    }

    @Override
    public void startScanBatch(@NonNull Consumer<List<ScanResult>> onResults,
                               @NonNull Consumer<ScanFailure> onFailed,
                               @NonNull Runnable onFinished,
                               @NonNull Consumer<ScanResult> onRemoved) {
        nearby.start(new HsNearbyObserver() {
            @Override
            public void onUpdated(@NonNull List<HsNearbyDevice> devices) {
                if (devices.isEmpty()) {
                    return;
                }
                List<ScanResult> mapped = new ArrayList<>(devices.size());
                for (HsNearbyDevice device : devices) {
                    mapped.add(toResult(device));
                }
                onResults.accept(List.copyOf(mapped));
            }

            @Override
            public void onLost(@NonNull HsNearbyDevice device) {
                onRemoved.accept(toResult(device));
            }

            @Override
            public void onFailed(@NonNull HsScanFailure failure) {
                onFailed.accept(toFailure(failure));
            }

            @Override
            public void onFinished() {
                onFinished.run();
            }
        });
    }

    @Override
    public void stopScan() {
        nearby.stop();
    }

    @Override
    public boolean isScanning() {
        return nearby.isScanning();
    }

    @Override
    public void close() {
        try {
            releaseAction.run();
        } catch (RuntimeException releaseFailure) {
            YoLog.w(TAG, "release of HS runtime failed during close", releaseFailure);
        }
        listeners.clear();
    }

    @NonNull
    private static ScanResult toResult(@NonNull HsNearbyDevice device) {
        return new ScanResult(
                device.modelId(),
                device.variantId(),
                device.bleName(),
                device.bleMac(),
                device.rssi());
    }

    @NonNull
    private static ScanFailure toFailure(@NonNull HsScanFailure failure) {
        return switch (failure) {
            case BLUETOOTH_DISABLED -> ScanFailure.BLUETOOTH_DISABLED;
            case PERMISSION_DENIED -> ScanFailure.PERMISSION_DENIED;
            case LOCATION_DISABLED -> ScanFailure.LOCATION_DISABLED;
            case BLE_UNSUPPORTED -> ScanFailure.BLE_UNSUPPORTED;
            case SCAN_THROTTLED -> ScanFailure.SCAN_THROTTLED;
            // 端口契约 ScanFailure 只声明 6 类用户可见原因；协议层更细的扫描失败分类
            // 在这里显式合并到 FAILED，逐分支可审计，不用 default 兜底。
            case SCAN_START_FAILED, SCAN_FEATURE_UNSUPPORTED, SCAN_OUT_OF_RESOURCES,
                 SCAN_INTERNAL_ERROR, RELEASED -> ScanFailure.FAILED;
        };
    }

    private void publish(@NonNull ConnectionState state, @NonNull BatterySnapshot battery) {
        DeviceSnapshot next;
        synchronized (lock) {
            next = new DeviceSnapshot(
                    state,
                    targetModelId,
                    targetVariantId,
                    battery,
                    wearStateOf(reportedEarInCaseStatus),
                    reportedFirmware,
                    targetBleName,
                    targetMac,
                    reportedEarColor);
        }
        currentSnapshot = next;
        for (Consumer<DeviceSnapshot> listener : listeners) {
            listener.accept(next);
        }
    }

    /**
     * 由耳机在仓状态推导佩戴状态，唯一规则。
     *
     * <p>推导方向只有「不在耳朵上」这一侧成立：两耳都在仓（{@link HsDeviceInfo#EAR_IN_CASE_BOTH}）
     * 时耳机在充电盒里，确知未佩戴。反方向不成立——有耳出仓只说明它离开了充电盒，也可能放在桌上，
     * 协议没有任何一帧能证明「戴在耳朵上」，所以不做正向断言，一律投未知；设备未上报同样是未知。
     * 用「未上报 = 未佩戴」或「出仓 = 佩戴中」把缺失的数据补成结论，就是替协议编造事实。</p>
     */
    @NonNull
    private static WearState wearStateOf(int earInCaseStatus) {
        return earInCaseStatus == HsDeviceInfo.EAR_IN_CASE_BOTH
                ? WearState.NOT_WORN
                : WearState.UNKNOWN;
    }

    /** 调用方须持锁。新会话 / 断开后回到「未回报」，不沿用上一台设备的值。 */
    private void resetReportedDeviceInfoLocked() {
        reportedFirmware = "";
        reportedEarColor = DeviceSnapshot.UNKNOWN_EAR_COLOR;
        reportedEarInCaseStatus = HsDeviceInfo.UNKNOWN_EAR_IN_CASE;
    }

    @NonNull
    private BatterySnapshot currentBattery() {
        HsBattery battery = hsClient.battery();
        return new BatterySnapshot(
                battery.leftLevel(),
                battery.rightLevel(),
                battery.caseLevel(),
                battery.caseCharging());
    }

    private final class Bridge implements HsConnectionObserver, HsDeviceObserver {

        @Override
        public void onConnecting(@NonNull String macAddress) {
            publish(ConnectionState.CONNECTING, BatterySnapshot.unknown());
        }

        @Override
        public void onConnected(@NonNull String macAddress) {
            publish(ConnectionState.READY, currentBattery());
        }

        @Override
        public void onDisconnected(@Nullable String reason) {
            publish(ConnectionState.DISCONNECTED, BatterySnapshot.unknown());
        }

        @Override
        public void onConnectFailed(@Nullable String reason, boolean willRetry) {
            publish(ConnectionState.DISCONNECTED, BatterySnapshot.unknown());
        }

        @Override
        public void onBatteryStateChanged(boolean leftUpdated, boolean rightUpdated, boolean caseUpdated) {
            if (currentSnapshot.isConnected()) {
                publish(currentSnapshot.connection(), currentBattery());
            }
        }

        /**
         * 固件版本 / 耳机颜色 / 在仓状态新值到达。三项都是协议原值，未知标记与领域常量同值，
         * 因此直接落到快照字段，不在 provider 里做文案或范围映射。
         */
        @Override
        public void onDeviceInfo(@NonNull HsDeviceInfo info) {
            synchronized (lock) {
                reportedFirmware = info.firmware();
                reportedEarColor = info.earColor();
                reportedEarInCaseStatus = info.earInCaseStatus();
            }
            // 与 onBatteryStateChanged 同一约定：只有已连接才推新快照；
            // 未连接期间到达的值先留在字段里，由随后的 READY 快照带上。
            if (currentSnapshot.isConnected()) {
                publish(currentSnapshot.connection(), currentBattery());
            }
        }
    }
}
