package com.yovo.lib_bluetooth.internal.common.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoStateMachine} 状态转换与守卫逻辑的单元测试。
 */

public class YoStateMachineTest {

    enum TestState { IDLE, RUNNING, STOPPED, FAILED, RELEASED }

    @Test
    public void initialState() {
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE).build();
        assertEquals(TestState.IDLE, sm.state());
        assertNull(sm.previousState());
    }

    @Test
    public void allowTransition() {
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allow(TestState.IDLE, TestState.RUNNING)
                .build();
        sm.transitionTo(TestState.RUNNING);
        assertEquals(TestState.RUNNING, sm.state());
        assertEquals(TestState.IDLE, sm.previousState());
    }

    @Test
    public void disallowTransition() {
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allow(TestState.IDLE, TestState.RUNNING)
                .build();
        sm.transitionTo(TestState.STOPPED);
        assertEquals(TestState.IDLE, sm.state());
    }

    @Test
    public void canTransitionTo() {
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allow(TestState.IDLE, TestState.RUNNING)
                .allow(TestState.RUNNING, TestState.STOPPED)
                .build();
        assertTrue(sm.canTransitionTo(TestState.RUNNING));
        assertFalse(sm.canTransitionTo(TestState.STOPPED));
        sm.transitionTo(TestState.RUNNING);
        assertTrue(sm.canTransitionTo(TestState.STOPPED));
    }

    @Test
    public void guardBlocksTransition() {
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allow(TestState.IDLE, TestState.RUNNING)
                .guard(TestState.IDLE, TestState.RUNNING,
                        (current, next, event) -> false)
                .build();
        sm.transitionTo(TestState.RUNNING);
        assertEquals(TestState.IDLE, sm.state());
    }

    @Test
    public void guardAllowsTransition() {
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allow(TestState.IDLE, TestState.RUNNING)
                .guard(TestState.IDLE, TestState.RUNNING,
                        (current, next, event) -> true)
                .build();
        sm.transitionTo(TestState.RUNNING);
        assertEquals(TestState.RUNNING, sm.state());
    }

    @Test
    public void entryActionFires() {
        AtomicInteger count = new AtomicInteger();
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allow(TestState.IDLE, TestState.RUNNING)
                .onEntry(TestState.RUNNING, count::incrementAndGet)
                .build();
        sm.transitionTo(TestState.RUNNING);
        assertEquals(1, count.get());
    }

    @Test
    public void exitActionFires() {
        AtomicInteger count = new AtomicInteger();
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allow(TestState.IDLE, TestState.RUNNING)
                .onExit(TestState.IDLE, count::incrementAndGet)
                .build();
        sm.transitionTo(TestState.RUNNING);
        assertEquals(1, count.get());
    }

    @Test
    public void observerNotified() {
        AtomicInteger count = new AtomicInteger();
        YoStateObserver<TestState> observer = (from, to, event) -> count.incrementAndGet();
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allow(TestState.IDLE, TestState.RUNNING)
                .observer(observer)
                .build();
        sm.transitionTo(TestState.RUNNING);
        assertEquals(1, count.get());
    }

    @Test
    public void forceTransitionBypassesGuard() {
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allow(TestState.IDLE, TestState.RUNNING)
                .guard(TestState.IDLE, TestState.RUNNING,
                        (current, next, event) -> false)
                .build();
        sm.forceTransitionTo(TestState.RUNNING);
        assertEquals(TestState.RUNNING, sm.state());
    }

    @Test
    public void isAndIsAny() {
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE).build();
        assertTrue(sm.is(TestState.IDLE));
        assertFalse(sm.is(TestState.RUNNING));
        assertTrue(sm.isAny(TestState.RUNNING, TestState.IDLE, TestState.STOPPED));
        assertFalse(sm.isAny(TestState.RUNNING, TestState.STOPPED));
    }

    @Test
    public void sameStateNoTransition() {
        AtomicInteger count = new AtomicInteger();
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .onExit(TestState.IDLE, count::incrementAndGet)
                .build();
        sm.transitionTo(TestState.IDLE);
        assertEquals(0, count.get());
    }

    @Test
    public void allowFromAndAllowTo() {
        YoStateMachine<TestState> sm = YoStateMachine.<TestState>builder(TestState.IDLE)
                .allowFrom(TestState.IDLE, TestState.RUNNING, TestState.STOPPED)
                .allowTo(TestState.FAILED, TestState.IDLE, TestState.RUNNING, TestState.STOPPED)
                .build();
        assertTrue(sm.canTransitionTo(TestState.RUNNING));
        assertTrue(sm.canTransitionTo(TestState.STOPPED));
        sm.transitionTo(TestState.RUNNING);
        assertTrue(sm.canTransitionTo(TestState.FAILED));
    }
}
