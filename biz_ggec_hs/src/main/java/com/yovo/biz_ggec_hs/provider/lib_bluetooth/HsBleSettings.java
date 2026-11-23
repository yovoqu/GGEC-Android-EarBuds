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

    /** 音频流专用通道 UUID（与固件 Opus dump 一致） */
    public static final UUID AUDIO_SERVICE_UUID =
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f820000");
    public static final UUID AUDIO_NOTIFY_UUID =
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f820003");
    public static final UUID AUDIO_WRITE_UUID =
            UUID.fromString("65786365-6c70-6f69-6e74-2e636f820004");

    /**
     * 命令通道写入类型。
     *
     * <p><b>真机结论（HS02 / motorola edge 60 pro）：</b>命令特征 {@link #WRITE_UUID} 用
     * {@code WITH_RESPONSE} 时，固件不回 ATT Write Response，导致 lib 的写操作等满 10s
     * {@code GATT_OPERATION_TIMEOUT}（实测停止/佩戴检测命令均 10s 超时失败）。故命令一律用
     * {@code WITHOUT_RESPONSE} 发送（不等待 ATT 应答）；命令的成功/失败以设备在 Notify 通道
     * 回的业务响应帧为准（HS02 实测写后约 84ms 即回 99ECxx 应答帧）。</p>
     *
     * <p>音频通道只订阅/接收通知、不向特征写入，不受本设置影响；本字段仅作用于命令特征。</p>
     */
    public static final BleWriteType WRITE_TYPE = BleWriteType.WITHOUT_RESPONSE;
    public static final BleNotificationSetupMode NOTIFY_MODE = BleNotificationSetupMode.DEFAULT;
    public static final BleConnectionConfig CONNECTION = BleConnectionConfig.builder().build();
    public static final BleConfig CLIENT = BleConfig.builder().callbackOnMainThread(false).build();
    public static final BleScanPlan SCAN = new BleScanPlan(BleScanMode.BALANCED, 0L, false, List.of());

    private HsBleSettings() {
    }
}
