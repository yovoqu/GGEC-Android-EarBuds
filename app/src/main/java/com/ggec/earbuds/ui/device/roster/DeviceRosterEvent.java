package com.ggec.earbuds.ui.device.roster;

import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceId;

/** 设备页的一次性事件：与 UiState 分开，旋转不重放。 */
public sealed interface DeviceRosterEvent {

    /** 打开设备失败（含缺经典地址、被拒绝、超时）。 */
    record OpenFailed(DeviceFailure failure) implements DeviceRosterEvent {
    }

    /** 打开成功：宿主可以进该设备的设置页。 */
    record OpenSucceeded(DeviceId id) implements DeviceRosterEvent {
    }

    /** 台账读写失败（含冷启动损坏）。 */
    record StoreFailed() implements DeviceRosterEvent {
    }

    /** 操作的设备已不在台账里。 */
    record DeviceMissing() implements DeviceRosterEvent {
    }
}
