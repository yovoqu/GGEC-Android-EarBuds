package com.ggec.earbuds.ui.device;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;

import java.util.Objects;
import java.util.Optional;

/**
 * 开关行的呈现结论：**纯呈现，不持状态**。
 *
 * <p>裁定：值未知只影响「值怎么显示」，不影响「控件是否存在」。写入是绝对设定语义
 * （set on / set off），不需要已知当前值；因此即使设备从未上报该能力的状态（协议只有 Set、
 * 没有 Query 的那批功能开关），开关也必须存在且可交互，否则该能力永远发不出写入。</p>
 *
 * <ul>
 *   <li>{@link #unknown()}：设备没有上报值，行上把值显示成「未上报」；</li>
 *   <li>{@link #position()}：开关位置——设备上报值优先；没有上报值时用写入槽里的未确认值；
 *       两者都没有则取关；</li>
 *   <li>{@link #enabled()}：是否可写——连接就绪且没有一次同能力的写入正在进行；</li>
 *   <li>{@link #writeState()}：本次写入的落地状态，界面据此显示「写入中 / 已确认 / 失败已回退」。</li>
 * </ul>
 *
 * <p>「未确认」只存在于 {@link DeviceSettingsUiState.WriteSlot}，由 ViewModel 维护；
 * 本记录是它的纯函数投影。</p>
 */
public record TogglePresentation(boolean unknown, boolean position, boolean enabled,
                                 @NonNull DeviceSettingsUiState.WriteState writeState) {

    public TogglePresentation {
        Objects.requireNonNull(writeState, "writeState");
    }

    @NonNull
    public static TogglePresentation of(@NonNull DeviceSettingsUiState.WriteState writeState,
                                        @NonNull Optional<Boolean> reported,
                                        @NonNull Optional<Boolean> pending,
                                        boolean interactive) {
        Objects.requireNonNull(writeState, "writeState");
        Objects.requireNonNull(reported, "reported");
        Objects.requireNonNull(pending, "pending");
        return new TogglePresentation(
                reported.isEmpty(),
                reported.or(() -> pending).orElse(Boolean.FALSE),
                interactive && writeState != DeviceSettingsUiState.WriteState.IN_FLIGHT,
                writeState);
    }
}
