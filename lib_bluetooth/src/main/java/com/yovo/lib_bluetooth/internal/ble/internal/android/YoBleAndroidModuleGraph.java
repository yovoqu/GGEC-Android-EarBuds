package com.yovo.lib_bluetooth.internal.ble.internal.android;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_bluetooth.internal.ble.YoBleModuleCore;
import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;
import com.yovo.lib_bluetooth.internal.ble.internal.android.gatt.YoBleAndroidConnectionPlatform;
import com.yovo.lib_bluetooth.internal.ble.internal.android.hiddenapi.YoBleHiddenApiPolicy;
import com.yovo.lib_bluetooth.internal.ble.internal.android.receiver.YoBleAdapterStateReceiver;
import com.yovo.lib_bluetooth.internal.ble.internal.android.receiver.YoBleAndroidReceiverRegistry;
import com.yovo.lib_bluetooth.internal.ble.internal.android.receiver.YoBleBondStateReceiver;
import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntimeConfig;
import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;
import com.yovo.lib_bluetooth.model.ble.BleConfig;

/**
 * Android 平台 BLE 依赖装配图：组装真机 Scan / GATT / Bond / L2CAP / Peripheral 适配器与系统广播监听。
 */
public final class YoBleAndroidModuleGraph {

    public final YoBleModuleCore core;
    public final YoBleAndroidReceiverRegistry receiverRegistry;

    private YoBleAndroidModuleGraph(@NonNull YoBleModuleCore core,
                                    @NonNull YoBleAndroidReceiverRegistry receiverRegistry) {
        this.core = core;
        this.receiverRegistry = receiverRegistry;
    }

    @NonNull
    public static YoBleAndroidModuleGraph create(@NonNull Context context,
                                                 @NonNull BleConfig config) {
        Context appContext = context.getApplicationContext();
        YoBluetoothAdapterProvider adapterProvider = new YoBluetoothAdapterProvider(appContext);
        YoBleCapabilityInspector capabilityInspector = new YoBleCapabilityInspector(appContext);
        YoBleHiddenApiPolicy cachePolicy = config.allowHiddenApiCacheRefresh()
                ? YoBleHiddenApiPolicy.EXPLICITLY_ENABLED
                : YoBleHiddenApiPolicy.DISABLED;

        YoBleRuntimeConfig runtimeConfig = YoBleRuntimeConfig.builder()
                .callbackOnMainThread(config.callbackOnMainThread())
                .build();

        YoBleModuleCore core = new YoBleModuleCore(
                config,
                runtimeConfig,
                capabilityInspector,
                new YoBleAndroidScanPlatform(adapterProvider),
                new YoBleAndroidConnectionPlatform(appContext, adapterProvider, cachePolicy),
                new YoBleAndroidBondPlatform(adapterProvider),
                new YoBleAndroidL2capPlatform(adapterProvider),
                new YoBleAndroidPeripheralPlatform(appContext, adapterProvider),
                System::currentTimeMillis);

        YoBleAndroidReceiverRegistry registry = new YoBleAndroidReceiverRegistry();
        YoBleAdapterStateReceiver adapterReceiver =
                new YoBleAdapterStateReceiver(core::onAdapterStateChanged);
        YoBleBondStateReceiver bondReceiver =
                new YoBleBondStateReceiver(core::onBondStateChanged);

        adapterReceiver.register(appContext, registry);
        bondReceiver.register(appContext, registry);

        return new YoBleAndroidModuleGraph(core, registry);
    }

    public void releaseAll() {
        core.release(receiverRegistry::unregisterAll);
    }
}
