package com.ggec.earbuds.ui.device;

import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 曲线编辑器（{@code CURVE}）的编辑契约与初值：段数取描述符的频段 {@code options}，取值域取描述符的
 * 增益 {@code range}。
 *
 * <p>biz 的能力描述符是唯一真源：{@code CURVE} 能力必须同时带频段表与增益值域（目录构造期就校验）。
 * 所以 APP 里没有第二份频段表，也不推导段数或值域——<b>缺任何一个都表示这条能力对编辑器不可用</b>：
 * 不生成草稿、不渲染编辑器（不猜默认）。</p>
 *
 * <p>编辑起点优先级：设备上报曲线 &gt; 本次未确认的编辑结果 &gt; 由描述符派生的平直曲线
 * （段数 = 频段表长度，每段 = 描述符声明的中立档）。与描述符对不上的值不作为起点（段数不等于频段表，
 * 或任一段落在 {@code range} 之外）：编辑器按描述符逐段呈现，对不上的值没有可渲染的对应关系。</p>
 */
public final class CurveEditorSeed {

    private CurveEditorSeed() {
    }

    /** 描述符是否给出可编辑的曲线契约：频段表非空且带增益值域。 */
    public static boolean editable(List<CapabilityOption> bands, Optional<ControlRange> range) {
        return presentBands(bands).isPresent() && presentRange(range).isPresent();
    }

    /**
     * 平直曲线：段数取频段表长度，每段都取描述符声明的中立档（曲线形状是水平直线）。
     *
     * <p>中立档由 biz 描述符给出（自定义 EQ 即协议 {@code 0x0C} = 0 dB），所以「平直」不必靠猜
     * 「0 dB 是哪一格」，也不受值域下界影响。</p>
     *
     * <p>描述符没有声明中立档时回落到 {@code range.min()}：这是<b>显式产品默认</b>——曲线编辑器必须
     * 有一个起点，而下界是描述符唯一给出的合法端点；它不是「设备上报值」的替身，也不写回领域状态。</p>
     *
     * <p>缺描述符（无频段表或无值域）= 空，不生成草稿。</p>
     */
    public static Optional<List<Integer>> flat(List<CapabilityOption> bands,
                                               Optional<ControlRange> range) {
        Optional<List<CapabilityOption>> bandOptions = presentBands(bands);
        Optional<ControlRange> gainRange = presentRange(range);
        if (bandOptions.isEmpty() || gainRange.isEmpty()) {
            return Optional.empty();
        }
        int count = bandOptions.get().size();
        ControlRange declared = gainRange.get();
        int rest = declared.neutral().orElse(declared.min());
        List<Integer> points = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            points.add(rest);
        }
        return Optional.of(List.copyOf(points));
    }

    /**
     * 编辑起点：设备上报值优先，其次本次未确认的编辑结果，最后是描述符派生的平直曲线。
     *
     * @return 无描述符（不可编辑）时为空，调用方据此不打开编辑器
     */
    public static Optional<List<Integer>> of(List<CapabilityOption> bands, Optional<ControlRange> range,
                                             Optional<List<Integer>> reported,
                                             Optional<List<Integer>> draft) {
        Optional<List<Integer>> flat = flat(bands, range);
        if (flat.isEmpty()) {
            return Optional.empty();
        }
        int count = flat.get().size();
        ControlRange gainRange = presentRange(range).orElseThrow();
        Optional<List<Integer>> reportedPoints = conforming(reported, count, gainRange);
        if (reportedPoints.isPresent()) {
            return reportedPoints;
        }
        Optional<List<Integer>> draftPoints = conforming(draft, count, gainRange);
        if (draftPoints.isPresent()) {
            return draftPoints;
        }
        return flat;
    }

    private static Optional<List<CapabilityOption>> presentBands(List<CapabilityOption> bands) {
        if (bands == null || bands.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bands);
    }

    private static Optional<ControlRange> presentRange(Optional<ControlRange> range) {
        return range == null ? Optional.empty() : range;
    }

    /** 段数与描述符频段表一致、且每段都落在描述符值域内时才采用该值。 */
    private static Optional<List<Integer>> conforming(Optional<List<Integer>> points, int count,
                                                      ControlRange range) {
        if (points == null) {
            return Optional.empty();
        }
        return points.filter(value -> value.size() == count && inRange(value, range))
                .map(List::copyOf);
    }

    private static boolean inRange(List<Integer> points, ControlRange range) {
        for (Integer point : points) {
            if (point == null || point < range.min() || point > range.max()) {
                return false;
            }
        }
        return true;
    }
}
