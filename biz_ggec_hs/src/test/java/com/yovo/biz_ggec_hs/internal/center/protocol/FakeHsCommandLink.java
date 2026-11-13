package com.yovo.biz_ggec_hs.internal.center.protocol;

import com.yovo.biz_ggec_hs.internal.center.port.HsCommandLink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 命令链路测试替身：记录每次上行载荷及其首次写入时刻，并让测试按载荷条件等待。
 * 会话就绪只由用例显式触发，因此上行顺序不受用例自身提交顺序影响。
 *
 * <p>就绪状态由本类自己记账，用来忠实复刻生产链路
 * {@code YoBleCommandLink} 的两种失效形状：失效先发 {@code onLinkError}（原因先到），
 * 只有此前就绪时才补一次 {@code onReady(false)}（状态后变）；尚未就绪时只有
 * {@code onLinkError}。{@code close()} 恒定发一次 {@code onReady(false)}（与生产实现一致）。</p>
 */
public final class FakeHsCommandLink implements HsCommandLink {
    private static final long WAIT_TIMEOUT_MS = 10_000L;

    private final List<String> payloads = new ArrayList<>();
    /** 载荷首次写入的单调毫秒时刻；与 {@link #payloads} 同锁保护。 */
    private final Map<String, Long> firstWrittenAtMs = new HashMap<>();
    private volatile Listener listener;
    private volatile boolean ready;
    private volatile String openedAddress;

    @Override
    public void open(String macAddress) {
        openedAddress = macAddress;
        // 与生产实现的 retireLocked 一致：换链路只清就绪标志，不补发 onReady(false)。
        ready = false;
    }

    public String openedAddress() {
        return openedAddress;
    }

    @Override
    public void close() {
        ready = false;
        Listener current = listener;
        if (current != null) {
            current.onReady(false);
        }
    }

    @Override
    public void release() {
        close();
    }

    @Override
    public boolean write(byte[] payload, WriteCallback callback) {
        // 先投递写入回调再公布载荷：用例在 awaitWritten 返回后立刻投喂应答帧，
        // 此时调度器必须已经知道本次写入完成，否则应答帧会被判为未开窗而丢弃。
        callback.onStarted();
        callback.onCompleted(0);
        String hex = HsHex.encode(payload);
        long nowMs = System.nanoTime() / 1_000_000L;
        synchronized (payloads) {
            payloads.add(hex);
            if (!firstWrittenAtMs.containsKey(hex)) {
                firstWrittenAtMs.put(hex, nowMs);
            }
            payloads.notifyAll();
        }
        return true;
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void ready() {
        ready = true;
        listener.onReady(true);
    }

    /**
     * 触发一次链路失效，按生产顺序投递：先 {@code onLinkError}，此前就绪过时再补一次
     * {@code onReady(false)}；从未就绪时只有 {@code onLinkError}。
     */
    public void linkError(String message) {
        boolean wasReady = ready;
        ready = false;
        Listener current = listener;
        if (current == null) {
            return;
        }
        current.onLinkError(message);
        if (wasReady) {
            current.onReady(false);
        }
    }

    public void inbound(String hexFrame) {
        listener.onInbound(HsHex.decode(hexFrame));
    }

    /** 等待指定上行载荷出现；超时返回 false。 */
    public boolean awaitWritten(String payloadHex) throws InterruptedException {
        return awaitWrittenWithin(payloadHex, WAIT_TIMEOUT_MS);
    }

    /** 等待指定上行载荷在 budgetMs 内出现；超时返回 false。 */
    public boolean awaitWrittenWithin(String payloadHex, long budgetMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + budgetMs;
        synchronized (payloads) {
            while (!payloads.contains(payloadHex)) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return false;
                }
                payloads.wait(remaining);
            }
            return true;
        }
    }

    /** 载荷首次写入的单调毫秒时刻；从未写入返回 -1。 */
    public long firstWrittenAtMs(String payloadHex) {
        synchronized (payloads) {
            Long value = firstWrittenAtMs.get(payloadHex);
            return value == null ? -1L : value;
        }
    }

    /** 以指定前缀开头的上行载荷条数。 */
    public int countWrittenWithPrefix(String payloadPrefixHex) {
        synchronized (payloads) {
            return countLocked(payloadPrefixHex);
        }
    }

    /** 等待以指定前缀开头的载荷累计到 expected 条；返回等待结束时的条数。 */
    public int awaitCountWithPrefix(String payloadPrefixHex, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        synchronized (payloads) {
            while (countLocked(payloadPrefixHex) < expected) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0L) {
                    return countLocked(payloadPrefixHex);
                }
                payloads.wait(remaining);
            }
            return countLocked(payloadPrefixHex);
        }
    }

    /** 调用方须持锁。 */
    private int countLocked(String payloadPrefixHex) {
        int count = 0;
        for (String payload : payloads) {
            if (payload.startsWith(payloadPrefixHex)) {
                count++;
            }
        }
        return count;
    }
}
