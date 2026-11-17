package com.ggec.earbuds.ui.device.settings;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.device.model.DeviceFailure;

import java.util.Objects;

/** 设置页的一次性事件：与 UiState 分开，旋转不重放。 */
public sealed interface DeviceSettingsEvent {

    /** 写入 / 动作失败分类，宿主翻成文案。 */
    record Failure(@NonNull DeviceFailure failure) implements DeviceSettingsEvent {

        public Failure {
            Objects.requireNonNull(failure, "failure");
        }
    }

    /**
     * 行级通道无法表达的动作：该 {@code kind} 需要载荷（名称、开始或结束、目标文件），
     * 必须由行内控件带着载荷发起。显式上报，不静默丢弃。
     */
    record ActionUnsupported(@NonNull DeviceSettingRow.ActionKind kind)
            implements DeviceSettingsEvent {

        public ActionUnsupported {
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** 台账里已经没有这台设备（改名 / 忘记时目标消失）。 */
    record RosterMissing() implements DeviceSettingsEvent {
    }

    /** 忘记设备成功：宿主关闭本页，回到设备页。 */
    record Closed() implements DeviceSettingsEvent {
    }
}
