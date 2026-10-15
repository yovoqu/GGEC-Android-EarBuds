package com.yovo.lib_bluetooth.internal.classic.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommand;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandQueue;
import com.yovo.lib_bluetooth.internal.classic.protocol.command.YoCbtCommandRequest;
import com.yovo.lib_bluetooth.internal.classic.protocol.packet.YoCbtPacket;

import org.junit.Test;

/**
 * @author tingfeng.yang
 * @date 2026-06-18
 * @description {@link YoCbtCommandQueue} 有界队列与匹配逻辑的单元测试。
 */

public class YoCbtCommandQueueTest {

    @Test
    public void enqueueAndSize() {
        YoCbtCommandQueue queue = new YoCbtCommandQueue(10);
        assertTrue(queue.isEmpty());
        queue.enqueue(buildRequest("cmd-1"), () -> {});
        assertEquals(1, queue.size());
        assertFalse(queue.isEmpty());
    }

    @Test
    public void capacityEnforcement() {
        YoCbtCommandQueue queue = new YoCbtCommandQueue(2);
        assertTrue(queue.enqueue(buildRequest("a"), () -> {}));
        assertTrue(queue.enqueue(buildRequest("b"), () -> {}));
        assertTrue(queue.isFull());
        assertFalse(queue.enqueue(buildRequest("c"), () -> {}));
    }

    @Test
    public void matchAndRemove() {
        YoCbtCommandQueue queue = new YoCbtCommandQueue();
        queue.enqueue(buildRequest("id-a"), () -> {});
        queue.enqueue(buildRequest("id-b"), () -> {});

        YoCbtPacket packet = new YoCbtPacket("match".getBytes());
        YoCbtCommandRequest matched = queue.matchAndRemove(packet);
        assertNotNull(matched);
        assertEquals("id-a", matched.getCommandId());
        assertEquals(1, queue.size());
    }

    @Test
    public void removeById() {
        YoCbtCommandQueue queue = new YoCbtCommandQueue();
        queue.enqueue(buildRequest("keep"), () -> {});
        queue.enqueue(buildRequest("delete"), () -> {});

        YoCbtCommandRequest removed = queue.remove("delete");
        assertNotNull(removed);
        assertEquals("delete", removed.getCommandId());
        assertEquals(1, queue.size());
    }

    @Test
    public void removeNonExistentReturnsNull() {
        YoCbtCommandQueue queue = new YoCbtCommandQueue();
        assertNull(queue.remove("no-such-id"));
    }

    @Test
    public void clear() {
        YoCbtCommandQueue queue = new YoCbtCommandQueue();
        queue.enqueue(buildRequest("a"), () -> {});
        queue.enqueue(buildRequest("b"), () -> {});
        queue.enqueue(buildRequest("c"), () -> {});
        queue.clear();
        assertTrue(queue.isEmpty());
    }

    @Test
    public void pollTimeoutRemovesOldest() {
        YoCbtCommandQueue queue = new YoCbtCommandQueue();
        queue.enqueue(buildRequest("oldest"), () -> {});
        queue.enqueue(buildRequest("newest"), () -> {});
        Runnable timeout = queue.pollTimeout();
        assertNotNull(timeout);
        assertEquals(1, queue.size());
        // "oldest" should have been removed
        assertNull(queue.remove("oldest"));
        assertNotNull(queue.remove("newest"));
    }

    private static YoCbtCommandRequest buildRequest(String commandId) {
        return YoCbtCommandRequest.builder(commandId,
                new YoCbtCommand(new YoCbtPacket(commandId.getBytes()))).build();
    }
}
