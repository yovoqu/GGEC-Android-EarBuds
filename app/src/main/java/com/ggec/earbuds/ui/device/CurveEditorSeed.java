package com.ggec.earbuds.ui.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 曲线编辑器（{@code CURVE}）的初始曲线。
 *
 * <p>与开关、滑杆同一条裁定：**值未知不影响控件是否可用**。编辑器在设备未上报曲线时从一个
 * 编辑器自带的默认曲线开始——它只是编辑器的初值，不写回 {@code DeviceState}、也不代表设备状态；
 * 序列优先取设备上报值，其次取本次未确认的编辑结果，最后取默认曲线。</p>
 */
public final class CurveEditorSeed {

    /** 编辑器默认曲线：等带宽、全部 0dB 的 10 段。仅作为编辑起点。 */
    private static final int DEFAULT_BANDS = 10;
    private static final int DEFAULT_GAIN = 0;

    private CurveEditorSeed() {
    }

    public static List<Integer> of(Optional<List<Integer>> reported, Optional<List<Integer>> draft) {
        Optional<List<Integer>> reportedValue = reported == null ? Optional.empty() : reported;
        Optional<List<Integer>> draftValue = draft == null ? Optional.empty() : draft;
        Optional<List<Integer>> source = reportedValue.isPresent() ? reportedValue : draftValue;
        return source.map(List::copyOf).orElseGet(CurveEditorSeed::defaults);
    }

    private static List<Integer> defaults() {
        List<Integer> points = new ArrayList<>(DEFAULT_BANDS);
        for (int index = 0; index < DEFAULT_BANDS; index++) {
            points.add(DEFAULT_GAIN);
        }
        return List.copyOf(points);
    }
}
