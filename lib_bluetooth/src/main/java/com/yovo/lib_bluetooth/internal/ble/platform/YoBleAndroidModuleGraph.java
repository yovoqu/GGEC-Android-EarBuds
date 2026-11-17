package com.yovo.lib_bluetooth.internal.ble.platform;



import android.content.Context;



import androidx.annotation.NonNull;



import com.yovo.lib_bluetooth.internal.ble.YoBleModuleCore;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleAndroidCapabilityInspector;

import com.yovo.lib_bluetooth.internal.ble.device.YoBleCapabilityInspector;

import com.yovo.lib_bluetooth.internal.ble.platform.gatt.YoBleAndroidConnectionPlatform;

import com.yovo.lib_bluetooth.internal.ble.platform.hiddenapi.YoBleHiddenApiPolicy;

import com.yovo.lib_bluetooth.internal.ble.platform.receiver.YoBleAdapterStateReceiver;

import com.yovo.lib_bluetooth.internal.ble.platform.receiver.YoBleAndroidReceiverRegistry;

import com.yovo.lib_bluetooth.internal.ble.platform.receiver.YoBleBondStateReceiver;

import com.yovo.lib_bluetooth.internal.ble.runtime.YoBleRuntimeConfig;

import com.yovo.lib_bluetooth.internal.common.bluetooth.YoBluetoothAdapterProvider;

import com.yovo.lib_bluetooth.internal.common.bluetooth.radio.YoBtRadioCoordinator;

import com.yovo.lib_bluetooth.internal.common.diagnostics.BluetoothDiagnosticsBus;

import com.yovo.lib_bluetooth.internal.common.permission.YoBluetoothPermissionChecker;

import com.yovo.lib_bluetooth.model.ble.BleConfig;



/**

 * Android 平台 BLE 依赖装配图：组装真机 Scan / GATT / Bond / L2CAP / Peripheral 适配器与系统广播监听。

 *

 * <p>权限检查器由装配根 {@code BluetoothSdkCore} 构造后传入，本图只做向下分发。</p>

 */

public final class YoBleAndroidModuleGraph {



    public final YoBleModuleCore core;

    public final YoBleAndroidReceiverRegistry receiverRegistry;

    private final YoBtRadioCoordinator radioCoordinator;

    private final BluetoothDiagnosticsBus diagnostics;

    private YoBleAndroidModuleGraph(@NonNull YoBleModuleCore core,

                                    @NonNull YoBleAndroidReceiverRegistry receiverRegistry,

                                    @NonNull YoBtRadioCoordinator radioCoordinator,

                                    @NonNull BluetoothDiagnosticsBus diagnostics) {

        this.core = core;

        this.receiverRegistry = receiverRegistry;

        this.radioCoordinator = radioCoordinator;

        this.diagnostics = diagnostics;

    }

    @NonNull

    public static YoBleAndroidModuleGraph create(@NonNull Context context,

                                                 @NonNull BleConfig config,

                                                 @NonNull YoBluetoothPermissionChecker permissionChecker,

                                                 @NonNull YoBtRadioCoordinator radioCoordinator,

                                                 @NonNull BluetoothDiagnosticsBus diagnostics) {

        Context appContext = context.getApplicationContext();

        YoBluetoothAdapterProvider adapterProvider = new YoBluetoothAdapterProvider(appContext);

        YoBleCapabilityInspector capabilityInspector =

                new YoBleAndroidCapabilityInspector(appContext, permissionChecker);

        YoBleHiddenApiPolicy cachePolicy = config.allowHiddenApiCacheRefresh()

                ? YoBleHiddenApiPolicy.EXPLICITLY_ENABLED

                : YoBleHiddenApiPolicy.DISABLED;



        YoBleRuntimeConfig runtimeConfig = YoBleRuntimeConfig.builder()

                .callbackOnMainThread(config.callbackOnMainThread())

                .build();



        YoBleModuleCore core = new YoBleModuleCore(

                config,

                runtimeConfig,

                diagnostics,

                capabilityInspector,

                new YoBleAndroidScanPlatform(adapterProvider, radioCoordinator),

                new YoBleAndroidConnectionPlatform(appContext, adapterProvider, cachePolicy, radioCoordinator),

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



        return new YoBleAndroidModuleGraph(core, registry, radioCoordinator, diagnostics);

    }



    public void releaseAll() {

        core.release(receiverRegistry::unregisterAll);

        radioCoordinator.releaseAllLeases();

        diagnostics.clear();

    }

}

