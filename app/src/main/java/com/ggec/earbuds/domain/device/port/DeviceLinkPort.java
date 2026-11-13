package com.ggec.earbuds.domain.device.port;

import com.ggec.earbuds.domain.device.model.ConnectSettlement;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceTarget;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.ScanResult;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 一条设备链路：扫描、连接、观察、写控制、执行动作、释放。
 *
 * <p>语义是"一次只有一条链路"，与 {@code biz_ggec_hs} 的单会话一致；多设备管理由
 * {@code domain/device/roster} 负责，不在端口里做并行会话。</p>
 *
 * <p>真实实现由 {@code provider/biz_ggec_hs/HsDeviceLinkAdapter} 提供。APP 只通过该端口
 * 表达意图；端口内部如何处理 BLE / 协议帧 / 能力路由与 APP 无关。</p>
 */
public interface DeviceLinkPort {

    DeviceSnapshot snapshot();

    Subscription observe(Consumer<DeviceSnapshot> listener);

    /** 发起一次连接。{@code onSettled} 在这次尝试到达终态时调用一次：就绪，或失败。 */
    void connect(DeviceTarget target, Consumer<ConnectSettlement> onSettled);

    void disconnect();

    /** 只按型号重投影 biz 里的设备表。空集表示该级不限。 */
    void filterModels(Set<String> modelIds);

    /**
     * 启动一次批量扫描：结果按批次回调、失败回调、结束回调（超时/停止/失败均会触发一次）、
     * 设备过期移除回调。这是扫描的唯一入口与唯一签名。
     */
    void startScanBatch(Consumer<List<ScanResult>> onResults,
                        Consumer<DeviceFailure> onFailed,
                        Runnable onFinished,
                        Consumer<ScanResult> onRemoved);

    void stopScan();

    boolean isScanning();

    /** 写入一个能力的控制值。结果经 {@code callback} 回传，不在端口内当异常抛出。 */
    void write(String capabilityId, ControlValue value, Consumer<DeviceWriteResult> callback);

    /** 执行一个类型化动作（找耳机、重命名、录音、双连接等）。 */
    void perform(DeviceAction action, Consumer<DeviceWriteResult> callback);

    /** 释放端口持有的底层资源。由装配根在进程生命周期结束时调用。 */
    void close();
}
