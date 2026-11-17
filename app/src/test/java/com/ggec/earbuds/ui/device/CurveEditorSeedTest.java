package com.ggec.earbuds.ui.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * 曲线编辑器的契约与初值：段数取描述符的频段 options，平直值取描述符声明的中立档（缺省时是值域下界
 * 这一显式产品默认）。
 *
 * <p>这条锁死两件事：APP 里没有第二份频段表（段数只能来自描述符）；描述符缺频段表或值域时
 * 不生成草稿、不猜默认（能力对编辑器不可用）。测试输入按段数现场生成标签，不引用任何生产频段表。</p>
 */
public final class CurveEditorSeedTest {

    /** 描述符频段表：测试输入，只声明段数与标签，不代表产品的真实频段。 */
    private static List<CapabilityOption> bands(int count) {
        List<CapabilityOption> options = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            options.add(new CapabilityOption("band-" + index, "band-" + index));
        }
        return List.copyOf(options);
    }

    /** 没有中立档的值域。 */
    private static ControlRange range(int min, int max, int step) {
        return ControlRange.of(min, max, step);
    }

    /** 声明了中立档的值域：中立档只来自描述符，测试不自己推中点。 */
    private static ControlRange withNeutral(int min, int max, int step, int neutral) {
        return new ControlRange(min, max, step, OptionalInt.of(neutral));
    }

    @Test
    public void descriptorWithoutBandOptionsIsNotEditable() {
        assertFalse("缺频段表 = 不可编辑，不猜段数",
                CurveEditorSeed.editable(List.of(), Optional.of(range(0, 24, 1))));
        assertTrue(CurveEditorSeed.of(List.of(), Optional.of(range(0, 24, 1)),
                Optional.of(List.of(1, 2, 3)), Optional.empty()).isEmpty());
        assertTrue(CurveEditorSeed.flat(List.of(), Optional.of(range(0, 24, 1))).isEmpty());
    }

    @Test
    public void descriptorWithoutRangeIsNotEditable() {
        assertFalse("缺增益值域 = 不可编辑，不猜上下界",
                CurveEditorSeed.editable(bands(4), Optional.empty()));
        assertTrue(CurveEditorSeed.of(bands(4), Optional.empty(), Optional.empty(), Optional.empty())
                .isEmpty());
    }

    @Test
    public void flatCurveTakesItsLengthFromBandsAndItsValueFromRangeMin() {
        List<Integer> flat = CurveEditorSeed.flat(bands(7), Optional.of(range(3, 24, 1)))
                .orElseThrow();

        assertEquals("段数 = 描述符频段数", 7, flat.size());
        for (Integer gain : flat) {
            assertEquals("无中立档时平直值 = 描述符值域下界", 3, gain.intValue());
        }
    }

    @Test
    public void flatCurveUsesTheDeclaredNeutralWhenThereIsOne() {
        // 中立档来自描述符（自定义 EQ 即 0x0C = 0 dB）：平直曲线用它，而不是值域下界。
        List<Integer> flat = CurveEditorSeed.flat(bands(5),
                Optional.of(withNeutral(0, 24, 1, 12))).orElseThrow();

        assertEquals(5, flat.size());
        for (Integer gain : flat) {
            assertEquals("有中立档时平直值 = 中立档", 12, gain.intValue());
        }
    }

    @Test
    public void declaredNeutralWinsOverTheRangeMinInTheEditorStart() {
        // 编辑器起点同样由描述符派生：未上报、也没有草稿时，落到中立档而不是下界。
        List<Integer> seed = CurveEditorSeed.of(bands(4),
                Optional.of(withNeutral(0, 24, 2, 10)),
                Optional.empty(), Optional.empty()).orElseThrow();

        assertEquals(4, seed.size());
        for (Integer gain : seed) {
            assertEquals(10, gain.intValue());
        }
    }

    @Test
    public void unreportedCurveFallsBackToTheDescriptorDerivedFlatCurve() {
        List<Integer> seed = CurveEditorSeed.of(bands(5), Optional.of(range(2, 20, 1)),
                Optional.empty(), Optional.empty()).orElseThrow();

        assertEquals(5, seed.size());
        for (Integer gain : seed) {
            assertEquals(2, gain.intValue());
        }
    }

    @Test
    public void unreportedCurveKeepsTheUsersLastEdit() {
        List<Integer> seed = CurveEditorSeed.of(bands(3), Optional.of(range(0, 24, 1)),
                Optional.empty(), Optional.of(List.of(1, 2, 3))).orElseThrow();

        assertEquals(List.of(1, 2, 3), seed);
    }

    @Test
    public void reportedCurveWinsOverTheLastEdit() {
        List<Integer> seed = CurveEditorSeed.of(bands(2), Optional.of(range(0, 24, 1)),
                Optional.of(List.of(4, 5)), Optional.of(List.of(1, 2, 3))).orElseThrow();

        assertEquals(List.of(4, 5), seed);
    }

    @Test
    public void pointsWhoseLengthDisagreesWithTheDescriptorBandsAreNotUsedAsTheStart() {
        // 与描述符频段表长度对不上的值没有逐段对应关系，退回描述符派生的平直曲线。
        List<Integer> seed = CurveEditorSeed.of(bands(4), Optional.of(range(0, 24, 1)),
                Optional.of(List.of(9, 9)), Optional.of(List.of(7, 7))).orElseThrow();

        assertEquals(4, seed.size());
        for (Integer gain : seed) {
            assertEquals(0, gain.intValue());
        }
    }

    @Test
    public void pointsOutsideTheDescriptorRangeAreNotUsedAsTheStart() {
        // 取值落在描述符值域之外的曲线同样与描述符矛盾：不采用，退回平直曲线。
        List<Integer> seed = CurveEditorSeed.of(bands(3), Optional.of(range(2, 10, 2)),
                Optional.of(List.of(4, 99, 6)), Optional.empty()).orElseThrow();

        assertEquals(3, seed.size());
        for (Integer gain : seed) {
            assertEquals(2, gain.intValue());
        }
    }

    @Test
    public void nullInputsAreAbsentValuesNotDefaults() {
        assertFalse(CurveEditorSeed.editable(null, null));
        assertTrue(CurveEditorSeed.of(null, null, null, null).isEmpty());
        assertTrue(CurveEditorSeed.flat(null, null).isEmpty());
    }
}
