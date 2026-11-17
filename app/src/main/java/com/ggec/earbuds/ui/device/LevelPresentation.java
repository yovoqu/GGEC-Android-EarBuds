package com.ggec.earbuds.ui.device;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * 档位行（LEVEL）的呈现结论：与开关同一条裁定，纯呈现、不持状态。
 *
 * <ul>
 *   <li>{@link #unknown()}：设备没有上报值，行上把值显示成「未上报」；</li>
 *   <li>{@link #value()}：滑杆位置——设备上报值优先，否则用写入槽里的未确认值，再否则取下界；
 *       下界只是「还没上报时滑杆停在哪」的呈现值，不代表设备状态；</li>
 *   <li>{@link #enabled()}：是否可写——连接就绪且没有一次同能力的写入正在进行；</li>
 *   <li>{@link #writeState()}：本次写入的落地状态。</li>
 * </ul>
 */
public record LevelPresentation(boolean unknown, int value, boolean enabled,
                                @NonNull DeviceSettingsUiState.WriteState writeState) {

    public LevelPresentation {
        Objects.requireNonNull(writeState, "writeState");
    }

    @NonNull
    public static LevelPresentation of(@NonNull DeviceSettingsUiState.WriteState writeState,
                                       @NonNull OptionalInt reported,
                                       @NonNull OptionalInt pending,
                                       int min,
                                       boolean interactive) {
        Objects.requireNonNull(writeState, "writeState");
        Objects.requireNonNull(reported, "reported");
        Objects.requireNonNull(pending, "pending");
        OptionalInt position = reported.isPresent() ? reported : pending;
        return new LevelPresentation(
                reported.isEmpty(),
                position.orElse(min),
                interactive && writeState != DeviceSettingsUiState.WriteState.IN_FLIGHT,
                writeState);
    }
}
