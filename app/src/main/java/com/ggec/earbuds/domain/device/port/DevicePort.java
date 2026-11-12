package com.ggec.earbuds.domain.device.port;

import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.List;
import java.util.function.Consumer;

/**
 * 设备端口：扫描、连接、观察、写控制、执行动作、释放。
 *
 * <p>真实实现由 SDK 适配层提供（{@code provider/biz_ggec_hs/HsDevicePortAdapter}）。
 * APP 只通过该端口表达意图；端口内部如何处理 BLE / 协议帧 / 能力路由与 APP 无关。</p>
 */
public interface DevicePort {

    DeviceSnapshot snapshot();

    Subscription observe(Consumer<DeviceSnapshot> listener);

    /**
     * 用这台双模设备的 BLE 地址发起 GATT 连接。经典地址一并交给会话，不参与寻址。
     */
    void connect(String modelId, String bleAddress, String bleName,
                 String classicAddress, String classicName);

    /** 只按型号重投影 biz 里的设备表。空集表示该级不限。 */
    void filterModels(java.util.Set<String> modelIds);

    void disconnect();

    /**
     * 启动一次批量扫描：结果按批次回调、失败回调、结束回调（超时/停止/失败均会触发一次）、
     * 设备过期移除回调。这是扫描的唯一入口与唯一签名，四类回调全部必填，
     * 避免「单条/批量/带失败/带移除」多重重载造成的实现分叉。
     */
    void startScanBatch(Consumer<List<ScanResult>> onResults,
                        Consumer<DeviceFailure> onFailed,
                        Runnable onFinished,
                        Consumer<ScanResult> onRemoved);

    void stopScan();

    boolean isScanning();

    /**
     * 写入一个能力的控制值。{@code capabilityId} 是 biz 能力标识字符串；
     * 结果经 {@code callback} 回传，不在端口内当异常抛出。
     */
    void write(String capabilityId, ControlValue value, Consumer<DeviceWriteResult> callback);

    /** 执行一个类型化动作（找耳机、重命名、录音、双连接等）。 */
    void perform(DeviceAction action, Consumer<DeviceWriteResult> callback);

    /** 释放端口持有的底层资源（停止扫描、关闭协议会话）。由装配根在进程生命周期结束时调用。 */
    void close();
}
