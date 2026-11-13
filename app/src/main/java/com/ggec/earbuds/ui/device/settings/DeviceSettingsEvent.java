package com.ggec.earbuds.ui.device.settings;

import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.ui.device.DeviceControlOutcome;

/** 设置页的一次性事件：与 UiState 分开，旋转不重放。 */
public sealed interface DeviceSettingsEvent {

    /** 写入/动作失败分类，宿主翻成文案。 */
    record Failure(DeviceFailure failure) implements DeviceSettingsEvent {
    }

    /** 某个能力 id 的写入落地结果（成功/失败），用于让开关回到真实呈现。 */
    record ControlOutcome(DeviceControlOutcome outcome) implements DeviceSettingsEvent {
    }

    /** 台账里已经没有这台设备（改名/忘记时目标消失）。 */
    record RosterMissing() implements DeviceSettingsEvent {
    }

    /** 忘记设备成功：宿主关闭本页，回到设备页。 */
    record Closed() implements DeviceSettingsEvent {
    }
}
