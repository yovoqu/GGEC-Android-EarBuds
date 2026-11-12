package com.ggec.earbuds.ui.device;

import java.util.OptionalInt;

/**
 * 档位行（LEVEL）的呈现结论：与开关同一条裁定——值未知只影响值的显示，不影响控件是否存在，
 * 也不让它变成不可写。
 *
 * <ul>
 *   <li>{@link #unknown()}：没有上报值，行上把值显示成「未上报」；</li>
 *   <li>{@link #value()}：滑杆位置——有上报值用上报值，否则用用户本次的未确认选择，再否则取下界；</li>
 *   <li>{@link #enabled()}：是否可写（连接就绪即可写，与值是否已知无关）。</li>
 * </ul>
 *
 * <p>「未确认」只存在于视图层，不写回 {@code DeviceState}。</p>
 */
public record LevelPresentation(boolean unknown, int value, boolean enabled) {

    public static LevelPresentation of(OptionalInt reported, OptionalInt pending, int min,
                                       boolean interactive) {
        OptionalInt reportedValue = reported == null ? OptionalInt.empty() : reported;
        OptionalInt pendingValue = pending == null ? OptionalInt.empty() : pending;
        OptionalInt position = reportedValue.isPresent() ? reportedValue : pendingValue;
        return new LevelPresentation(reportedValue.isEmpty(), position.orElse(min), interactive);
    }
}
