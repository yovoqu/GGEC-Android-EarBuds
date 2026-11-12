package com.ggec.earbuds.ui.device;

import java.util.Optional;

/**
 * 开关行的呈现结论。
 *
 * <p>裁定：**值未知只影响「值怎么显示」，不影响「控件是否存在」。** 写入是绝对设定语义
 * （set on / set off），不需要已知当前值；因此即使设备从未上报该能力的状态（例如协议只有
 * Set、没有 Query 的那批功能开关），开关也必须存在且可交互，否则该能力永远发不出写入。</p>
 *
 * <ul>
 *   <li>{@link #unknown()}：没有上报值，行上把值显示成「未上报」；</li>
 *   <li>{@link #position()}：开关位置——有上报值用上报值，否则用用户本次的未确认选择，再否则取关；</li>
 *   <li>{@link #enabled()}：是否可写（连接就绪即可写，与值是否已知无关）。</li>
 * </ul>
 *
 * <p>「未确认」只存在于视图层：不写回 {@code DeviceState}，也不伪装成设备状态。</p>
 */
public record TogglePresentation(boolean unknown, boolean position, boolean enabled) {

    public static TogglePresentation of(Optional<Boolean> reported, Optional<Boolean> pending,
                                        boolean interactive) {
        Optional<Boolean> reportedValue = reported == null ? Optional.empty() : reported;
        Optional<Boolean> pendingValue = pending == null ? Optional.empty() : pending;
        return new TogglePresentation(
                reportedValue.isEmpty(),
                reportedValue.or(() -> pendingValue).orElse(Boolean.FALSE),
                interactive);
    }
}
