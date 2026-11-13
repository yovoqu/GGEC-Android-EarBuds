package com.yovo.biz_ggec_hs.provider.lib_bluetooth;

import com.yovo.lib_bluetooth.api.BleScanPlan;
import com.yovo.lib_bluetooth.model.ble.BleConfig;
import com.yovo.lib_bluetooth.model.ble.BleConnectionConfig;
import com.yovo.lib_bluetooth.model.ble.BleNotificationSetupMode;
import com.yovo.lib_bluetooth.model.ble.BleScanMode;
import com.yovo.lib_bluetooth.model.ble.BleWriteType;

import java.util.List;
import java.util.UUID;

/**
 * HS 用到的蓝牙库设置，只在这一处。扫描档位、GATT 特征和连接参数都不从上层传入。
 */
public final class HsBleSettings {

    public static final UUID SERVICE_UUID =
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f820100");
    public static final UUID WRITE_UUID =
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f820104");
    public static final UUID NOTIFY_UUID =
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f820105");

    public static final BleWriteType WRITE_TYPE = BleWriteType.WITHOUT_RESPONSE;
    public static final BleNotificationSetupMode NOTIFY_MODE = BleNotificationSetupMode.DEFAULT;
    public static final BleConnectionConfig CONNECTION = BleConnectionConfig.builder().build();
    public static final BleConfig CLIENT = BleConfig.builder().callbackOnMainThread(false).build();
    public static final BleScanPlan SCAN = new BleScanPlan(BleScanMode.BALANCED, 0L, false, List.of());

    private HsBleSettings() {
    }
}
