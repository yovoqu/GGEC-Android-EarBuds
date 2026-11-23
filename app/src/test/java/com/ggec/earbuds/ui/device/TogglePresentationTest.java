package com.ggec.earbuds.ui.device;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Optional;

/**
 * 开关呈现规则：**值未知只影响「值怎么显示」，不影响「控件是否存在」**；写入未落地时
 * **点击即先切换**（先呈现目标值）并**禁用重复点击**，成功后解除，失败回退。
 *
 * <p>这条锁死的是功能回归：协议只有 Set、没有 Query 的那批功能开关（VOLUME_ADAPTIVE /
 * WEAR_DETECT / VOICE_WAKEUP / FALL_ALERT / VOLUME_REMIND / INTERPRET）永远拿不到上报值，
 * 如果「未上报」时取消开关，它们就永远发不出写入。</p>
 *
 * <p>呈现是纯函数：写入状态（{@link WriteState}）与两个值都是入参，
 * 本类不持有任何未确认副本。</p>
 */
public final class TogglePresentationTest {

    private static final WriteState IDLE =
            WriteState.IDLE;
    private static final WriteState SENDING =
            WriteState.SENDING;
    private static final WriteState PROCESSING =
            WriteState.PROCESSING;
    private static final WriteState CONFIRMED =
            WriteState.CONFIRMED;
    private static final WriteState FAILED =
            WriteState.FAILED;

    @Test
    public void unknownValueStillYieldsAnEnabledSwitch() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.empty(), Optional.empty(), true);

        assertTrue("未上报必须仍然给出可写开关", presentation.enabled());
        assertTrue(presentation.unknown());
        assertFalse("未知时取值关作为未确认呈现", presentation.position());
        assertSame(IDLE, presentation.writeState());
    }

    @Test
    public void unknownValueKeepsTheUnconfirmedChoice() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.empty(), Optional.of(true), true);

        assertTrue(presentation.unknown());
        assertEquals(Boolean.TRUE, presentation.position());
        assertTrue(presentation.enabled());
    }

    @Test
    public void reportedValueWinsOverTheUnconfirmedChoice() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.of(true), Optional.of(false), true);

        assertFalse(presentation.unknown());
        assertEquals(Boolean.TRUE, presentation.position());
    }

    @Test
    public void reportedOffIsNotUnknown() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.of(false), Optional.empty(), true);

        assertFalse(presentation.unknown());
        assertFalse(presentation.position());
        assertTrue(presentation.enabled());
    }

    @Test
    public void notWritableKeepsTheSwitchButDisablesIt() {
        TogglePresentation presentation =
                TogglePresentation.of(IDLE, Optional.empty(), Optional.empty(), false);

        assertFalse(presentation.enabled());
        assertTrue(presentation.unknown());
    }

    @Test
    public void sendingWriteDisablesAndShowsTheTargetImmediately() {
        TogglePresentation presentation =
                TogglePresentation.of(SENDING, Optional.of(false), Optional.of(true), true);

        assertFalse("发送中不重复下发", presentation.enabled());
        assertSame(SENDING, presentation.writeState());
        assertFalse(presentation.unknown());
        assertTrue("点击即先切换：发送中先呈现目标值", presentation.position());
    }

    @Test
    public void processingWriteStillDisablesAndShowsTheTarget() {
        TogglePresentation presentation =
                TogglePresentation.of(PROCESSING, Optional.of(false), Optional.of(true), true);

        assertFalse("处理中仍禁用点击", presentation.enabled());
        assertSame(PROCESSING, presentation.writeState());
        assertTrue("处理中继续呈现目标值", presentation.position());
    }

    @Test
    public void confirmedWriteKeepsTheUserTargetEvenIfDeviceReportsStale() {
        TogglePresentation presentation =
                TogglePresentation.of(CONFIRMED, Optional.of(false), Optional.of(true), true);

        assertTrue("成功后解除禁用", presentation.enabled());
        assertSame(CONFIRMED, presentation.writeState());
        assertTrue("成功后保持用户目标值（设备上报可能滞后/不回读）", presentation.position());
    }

    @Test
    public void confirmedWriteWithoutReportedValueKeepsTheUserTarget() {
        TogglePresentation presentation =
                TogglePresentation.of(CONFIRMED, Optional.empty(), Optional.of(true), true);

        assertTrue(presentation.enabled());
        assertTrue(presentation.position());
    }

    @Test
    public void failedWriteRevertsToReportedValue() {
        TogglePresentation presentation =
                TogglePresentation.of(FAILED, Optional.of(false), Optional.empty(), true);

        assertTrue("失败后恢复可点击", presentation.enabled());
        assertFalse("失败后回退到设备上报值", presentation.position());
    }

    @Test
    public void nullInputsAreContractViolations() {
        org.junit.Assert.assertThrows(NullPointerException.class,
                () -> TogglePresentation.of(null, Optional.empty(), Optional.empty(), true));
    }
}
