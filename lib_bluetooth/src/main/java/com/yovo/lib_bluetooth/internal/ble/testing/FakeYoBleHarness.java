package com.yovo.lib_bluetooth.internal.ble.testing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_bluetooth.api.YoBleClient;
import com.yovo.lib_bluetooth.internal.ble.BleClientImpl;
import com.yovo.lib_bluetooth.internal.ble.YoBleModuleCore;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntimeConfig;
import com.yovo.lib_bluetooth.internal.common.diagnostics.BluetoothLogPort;
import com.yovo.lib_bluetooth.internal.common.diagnostics.YoBluetoothLogger;
import com.yovo.lib_bluetooth.internal.common.testing.FakeClock;
import com.yovo.lib_bluetooth.internal.common.testing.FakeDispatcher;
import com.yovo.lib_bluetooth.model.ble.BleConfig;

/**
 * 纯 JVM 端到端 BLE 单测装配台：聚合受控时钟、定时队列、State/Callback Dispatcher 与全套 Fake 平台。
 */
public final class FakeYoBleHarness {

    private static final BluetoothLogPort NO_OP_LOG_PORT = new BluetoothLogPort() {
        @Override public void v(@NonNull String tag, @NonNull String message) {}
        @Override public void d(@NonNull String tag, @NonNull String message) {}
        @Override public void d(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {}
        @Override public void i(@NonNull String tag, @NonNull String message) {}
        @Override public void i(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {}
        @Override public void w(@NonNull String tag, @NonNull String message) {}
        @Override public void w(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {}
        @Override public void e(@NonNull String tag, @NonNull String message) {}
        @Override public void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {}
    };

    public static void ensureTestLoggerInstalled() {
        if (!YoBluetoothLogger.isPortInstalled()) {
            YoBluetoothLogger.setPort(NO_OP_LOG_PORT);
        }
    }

    public final FakeClock clock;
    public final FakeTaskScheduler scheduler;
    public final FakeDispatcher stateDispatcher = new FakeDispatcher();
    public final FakeDispatcher callbackDispatcher = new FakeDispatcher();
    public final FakeYoBleEnvironmentPort env = new FakeYoBleEnvironmentPort();
    public final FakeYoBleScanPlatform scanPlatform = new FakeYoBleScanPlatform();
    public final FakeYoBleConnectionPlatform connectionPlatform = new FakeYoBleConnectionPlatform();
    public final FakeYoBleBondPlatform bondPlatform = new FakeYoBleBondPlatform();
    public final FakeYoBleL2capPlatform l2capPlatform = new FakeYoBleL2capPlatform();
    public final FakeYoBlePeripheralPlatform peripheralPlatform = new FakeYoBlePeripheralPlatform();

    public final YoBleModuleCore core;
    public final YoBleClient client;

    public FakeYoBleHarness() {
        this(BleConfig.builder().build(), 1_000_000L);
    }

    public FakeYoBleHarness(@NonNull BleConfig config) {
        this(config, 1_000_000L);
    }

    public FakeYoBleHarness(@NonNull BleConfig config, long initialTimeMillis) {
        ensureTestLoggerInstalled();
        this.clock = new FakeClock(initialTimeMillis);
        this.scheduler = new FakeTaskScheduler(clock);
        YoBleRuntimeConfig runtimeConfig = YoBleRuntimeConfig.builder()
                .callbackOnMainThread(false)
                .customStateDispatcher(stateDispatcher)
                .customCallbackDispatcher(callbackDispatcher)
                .customScheduler(scheduler)
                .build();

        this.core = new YoBleModuleCore(
                config,
                runtimeConfig,
                new YoBleCapabilityInspector(env),
                scanPlatform,
                connectionPlatform,
                bondPlatform,
                l2capPlatform,
                peripheralPlatform,
                clock::nowMillis);

        this.bondPlatform.setAutoCompleteSink(core::onBondStateChanged);
        this.client = new BleClientImpl(core);
    }

    public void drain() {
        scheduler.drain(stateDispatcher, callbackDispatcher);
    }

    public void advanceMillis(long deltaMillis) {
        scheduler.advanceAndFlush(deltaMillis, stateDispatcher, callbackDispatcher);
    }

    public void release() {
        client.release();
        drain();
    }
}
