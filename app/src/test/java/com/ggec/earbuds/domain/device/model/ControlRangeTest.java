package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** LEVEL 控件的取值域：来自目录，界不猜值域，构造期就拒绝自相矛盾的取值域。 */
public final class ControlRangeTest {

    @Test
    public void validRangeKeepsDeclaredBounds() {
        ControlRange range = new ControlRange(0, 100, 5);

        assertEquals(0, range.min());
        assertEquals(100, range.max());
        assertEquals(5, range.step());
    }

    @Test
    public void zeroStepIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ControlRange(0, 100, 0));
    }

    @Test
    public void negativeStepIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ControlRange(0, 100, -1));
    }

    @Test
    public void invertedBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ControlRange(100, 0, 1));
    }

    @Test
    public void singleValueRangeIsAllowed() {
        ControlRange range = new ControlRange(3, 3, 1);

        assertEquals(range.min(), range.max());
    }
}
