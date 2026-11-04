package com.ggec.earbuds.data.device;

import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.ScanResult;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.port.DevicePort;
import com.ggec.earbuds.domain.device.port.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 假设备端口：完整模拟连接状态机、电量游走与扫描结果，不接触真实蓝牙。
 * 之后接 SDK 时，用真实适配器替换本类即可，上层不改。
 */
public final class FakeDevicePort implements DevicePort {

    private final ScheduledExecutorService scheduler;
    private final CopyOnWriteArrayList<Consumer<DeviceSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final Random random = new Random();
    private final List<ScanResult> scanCatalog = buildScanCatalog();

    private volatile DeviceSnapshot current = DeviceSnapshot.idle();
    private volatile boolean scanning;
    private volatile boolean tickerRunning;

    public FakeDevicePort() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable task) {
                Thread thread = new Thread(task, "fake-device-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private static List<ScanResult> buildScanCatalog() {
        List<ScanResult> results = new ArrayList<>();
        results.add(new ScanResult("HS01", "v1", "HS01", "AA:BB:CC:00:00:01", -48, true));
        results.add(new ScanResult("HS01", "v2", "HS01", "AA:BB:CC:00:00:02", -63, false));
        results.add(new ScanResult("HS01", "v3", "HS01", "AA:BB:CC:00:00:03", -71, false));
        results.add(new ScanResult("HS02", "v1", "HS02", "AA:BB:CC:00:00:04", -57, false));
        return results;
    }

    @Override
    public DeviceSnapshot snapshot() {
        return current;
    }

    @Override
    public Subscription observe(Consumer<DeviceSnapshot> listener) {
        listeners.add(listener);
        listener.accept(current);
        return () -> listeners.remove(listener);
    }

    private void publish(DeviceSnapshot snapshot) {
        current = snapshot;
        for (Consumer<DeviceSnapshot> listener : listeners) {
            listener.accept(snapshot);
        }
    }

    @Override
    public void connect(String modelId, String variantId, String mac) {
        publish(new DeviceSnapshot(ConnectionState.CONNECTING, modelId, variantId,
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", modelId, mac, ""));
        scheduler.schedule(() -> publish(connectedSnapshot(modelId, variantId, mac, false)),
                500, TimeUnit.MILLISECONDS);
        scheduler.schedule(() -> {
            publish(connectedSnapshot(modelId, variantId, mac, true));
            startTicker();
        }, 1100, TimeUnit.MILLISECONDS);
    }

    private DeviceSnapshot connectedSnapshot(String modelId, String variantId, String mac, boolean ready) {
        BatterySnapshot battery = new BatterySnapshot(70 + random.nextInt(20),
                70 + random.nextInt(20), 60 + random.nextInt(30), false);
        return new DeviceSnapshot(
                ready ? ConnectionState.READY : ConnectionState.CONNECTED_NOT_READY,
                modelId, variantId, battery, WearState.BOTH,
                "", modelId, mac, "");
    }

    private void startTicker() {
        if (tickerRunning) {
            return;
        }
        tickerRunning = true;
        scheduler.scheduleAtFixedRate(() -> {
            DeviceSnapshot snapshot = current;
            if (snapshot.connection() != ConnectionState.READY) {
                return;
            }
            BatterySnapshot battery = snapshot.battery();
            int left = clamp(battery.left() + random.nextInt(3) - 1);
            int right = clamp(battery.right() + random.nextInt(3) - 1);
            int caseLevel = clamp(battery.caseLevel() + random.nextInt(3) - 1);
            WearState wear = WearState.values()[random.nextInt(WearState.values().length)];
            publish(new DeviceSnapshot(snapshot.connection(), snapshot.modelId(), snapshot.variantId(),
                    new BatterySnapshot(left, right, caseLevel, battery.caseCharging()),
                    wear, snapshot.firmware(), snapshot.deviceName(), snapshot.mac(), snapshot.colorLabel()));
        }, 6, 6, TimeUnit.SECONDS);
    }

    private static int clamp(int value) {
        if (value < 1) {
            return 1;
        }
        return Math.min(value, 100);
    }

    @Override
    public void disconnect() {
        DeviceSnapshot snapshot = current;
        publish(new DeviceSnapshot(ConnectionState.DISCONNECTED, snapshot.modelId(), snapshot.variantId(),
                BatterySnapshot.unknown(), WearState.NOT_WORN, "", snapshot.deviceName(), snapshot.mac(), ""));
    }

    @Override
    public void startScan(Consumer<ScanResult> onResult, Runnable onFinished) {
        if (scanning) {
            return;
        }
        scanning = true;
        long delay = 250;
        for (ScanResult result : scanCatalog) {
            scheduler.schedule(() -> onResult.accept(result), delay, TimeUnit.MILLISECONDS);
            delay += 220;
        }
        scheduler.schedule(() -> {
            scanning = false;
            onFinished.run();
        }, delay + 300, TimeUnit.MILLISECONDS);
    }

    @Override
    public void stopScan() {
        scanning = false;
    }

    @Override
    public boolean isScanning() {
        return scanning;
    }
}
