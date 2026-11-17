package com.ggec.earbuds.domain.device.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.OptionalInt;

/** LEVEL/CURVE 控件的取值域：来自目录，界不猜值域，构造期就拒绝自相矛盾的取值域。 */
public final class ControlRangeTest {

    @Test
    public void validRangeKeepsDeclaredBounds() {
        ControlRange range = ControlRange.of(0, 100, 5);

        assertEquals(0, range.min());
        assertEquals(100, range.max());
        assertEquals(5, range.step());
        assertTrue("Range.of 不声明中立档", range.neutral().isEmpty());
    }

    @Test
    public void declaredNeutralIsKept() {
        ControlRange range = new ControlRange(0, 24, 1, OptionalInt.of(12));

        assertEquals(OptionalInt.of(12), range.neutral());
    }

    @Test
    public void neutralOutsideTheBoundsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ControlRange(0, 24, 1, OptionalInt.of(25)));
        assertThrows(IllegalArgumentException.class,
                () -> new ControlRange(0, 24, 1, OptionalInt.of(-1)));
    }

    @Test
    public void nullNeutralIsRejectedInsteadOfBeingTreatedAsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new ControlRange(0, 24, 1, null));
    }

    @Test
    public void zeroStepIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ControlRange.of(0, 100, 0));
    }

    @Test
    public void negativeStepIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ControlRange.of(0, 100, -1));
    }

    @Test
    public void invertedBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ControlRange.of(100, 0, 1));
    }

    @Test
    public void singleValueRangeIsAllowed() {
        ControlRange range = ControlRange.of(3, 3, 1);

        assertEquals(range.min(), range.max());
    }
}
