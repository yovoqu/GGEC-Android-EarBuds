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
 *   <li>{@link #position()}：开关位置——写入过程中（SENDING/PROCESSING）与成功确认后（CONFIRMED）
 *       优先呈现本次用户目标值（点击即先切换、成功后保持），失败或空闲时设备上报值优先；</li>
 *   <li>{@link #enabled()}：是否可写——连接就绪且没有一次同能力的写入正在进行（SENDING/PROCESSING 时禁用）；</li>
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
        // 点击即先切换：写入未落地（SENDING / PROCESSING）期间先呈现用户本次目标值（pending），
        // 让开关立即翻到目标位置，同时 enabled=false 禁用重复点击；**命令成功后（CONFIRMED）继续
        // 呈现用户选择的值**——设备可能不回读或回读滞后（HS02 实测：命令回执成功但状态上报仍为旧值），
        // 以本次写入为准而非旧上报值；失败（FAILED）丢弃 pending 回退到设备上报值。
        boolean targetWins = pending.isPresent()
                && (writeState == DeviceSettingsUiState.WriteState.SENDING
                || writeState == DeviceSettingsUiState.WriteState.PROCESSING
                || writeState == DeviceSettingsUiState.WriteState.CONFIRMED);
        boolean position = targetWins
                ? pending.get()
                : reported.or(() -> pending).orElse(Boolean.FALSE);
        return new TogglePresentation(
                reported.isEmpty(),
                position,
                interactive && !writeState.inFlight(),
                writeState);
    }
}
