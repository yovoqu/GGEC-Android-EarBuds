package com.ggec.earbuds.ui.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

/**
 * 曲线编辑器初值：设备未上报曲线时从一个编辑器自带默认曲线开始，控件因此恒可用。
 *
 * <p>这条锁死「CURVE 行以 points 是否上报为可点前提」的回归：该前提一旦回来，编辑器就没有可用的
 * 起点，自定音效与实时曲线命令都不可达。</p>
 */
public final class CurveEditorSeedTest {

    @Test
    public void unreportedCurveFallsBackToTheEditorDefault() {
        List<Integer> seed = CurveEditorSeed.of(Optional.empty(), Optional.empty());

        assertFalse("默认曲线必须有段数，否则编辑器打不开", seed.isEmpty());
        assertEquals(10, seed.size());
        for (Integer gain : seed) {
            assertEquals(0, gain.intValue());
        }
    }

    @Test
    public void unreportedCurveKeepsTheUsersLastEdit() {
        List<Integer> seed = CurveEditorSeed.of(Optional.empty(), Optional.of(List.of(1, 2, 3)));

        assertEquals(List.of(1, 2, 3), seed);
    }

    @Test
    public void reportedCurveWinsOverTheLastEdit() {
        List<Integer> seed = CurveEditorSeed.of(Optional.of(List.of(4, 5)), Optional.of(List.of(1, 2, 3)));

        assertEquals(List.of(4, 5), seed);
    }

    @Test
    public void nullInputsAreAbsentValues() {
        assertEquals(10, CurveEditorSeed.of(null, null).size());
    }
}
