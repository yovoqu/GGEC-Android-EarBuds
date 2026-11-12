package com.yovo.biz_ggec_hs.internal.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.api.HsConnectionObserver;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.HomePageStatusV12FirstCommand;
import com.yovo.biz_ggec_hs.model.HsBattery;
import com.yovo.biz_ggec_hs.model.HsDeviceInfo;
import com.yovo.biz_ggec_hs.model.HsDeviceObserver;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 会话起点的时序契约：{@code connect(...)} 返回时本会话域已经清空。
 *
 * <p>上层 {@code HsDevicePort.connect} 的次序是
 * {@code setConnectionObserver → connect → setDeviceObserver}，而挂载设备观察者会在调用线程
 * 同步回放域里已有的固件 / 颜色 / 电量。因此判定只有一条：
 * <b>{@code connect} 返回后立刻挂观察者，回放必须是空域</b>。用例全部用门闩与可控执行器推进，
 * 不 sleep、不轮询。</p>
 */
public class HsSessionOriginTest {
    /** {@code EarColorCommand} 的上行载荷。 */
    private static final String EAR_COLOR_QUERY = "99EC910001001234";
    /** 耳机颜色回包：颜色字节 {@code 0x2A}。 */
    private static final String EAR_COLOR_RESPONSE = "99EC9100012A1234";
    private static final int EAR_COLOR_VALUE = 0x2A;
    /** {@code HomePageStatusV12FirstCommand} 的上行载荷。 */
    private static final String HOME_STATUS_QUERY = "99ECC70001001234";
    /** 固件版本 "V1.2.3" 的 UTF-8 十六进制。 */
    private static final String FIRMWARE_TEXT_HEX = "56312E322E33";
    private static final String FIRMWARE_TEXT = "V1.2.3";
    /** 首页 TLV：类型 {@code 0x94}、长度 6。 */
    private static final String TLV_FIRMWARE = "9406";
    /** 首页 TLV：类型 {@code 0x80}、长度 6，左右耳与充电盒电量均 {@code 0x50}（80）。 */
    private static final String TLV_BATTERY = "8006" + "015002500350";
    private static final int BATTERY_LEVEL = 80;
    private static final int UNKNOWN_BATTERY_LEVEL = HsBattery.UNKNOWN_LEVEL;
    /** 会话 A / 会话 B 的设备地址：同一引擎先后连两台设备。 */
    private static final String MAC_A = "00:11:22:33:44:55";
    private static final String MAC_B = "66:77:88:99:AA:BB";

    /**
     * app 侧依赖的契约：换设备时 {@code connect} 返回即清域，紧接着挂上的观察者回放为空域——
     * 固件与耳机颜色都没有可回放的值（两项都未知时域不投递设备信息），电量回放为
     * {@link HsBattery#UNKNOWN_LEVEL}。
     */
    @Test
    public void connectReturnsWithClearedDomainSoObserverReplayIsEmpty() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            DeviceReplay previous = new DeviceReplay();
            engine.setDeviceObserver(previous);
            CountDownLatch disconnected = new CountDownLatch(1);
            engine.setConnectionObserver(new HsConnectionObserver() {
                @Override
                public void onDisconnected(String reason) {
                    disconnected.countDown();
                }
            });
            seedSessionADomain(link, engine, previous);

            // 换设备：connect 返回后立刻挂观察者（app 的 HsDevicePort 就是这个次序）。
            engine.connect(MAC_B);
            DeviceReplay next = new DeviceReplay();
            engine.setDeviceObserver(next);

            HsDeviceInfo replayed = next.info.get();
            assertNull("connect 返回时域必须已清空：不得回放上一台设备的固件 / 颜色", replayed);
            assertEquals("回放电量必须是未知",
                    UNKNOWN_BATTERY_LEVEL, engine.battery().leftLevel());
            assertEquals("回放电量必须是未知",
                    UNKNOWN_BATTERY_LEVEL, engine.battery().rightLevel());
            assertEquals("回放电量必须是未知",
                    UNKNOWN_BATTERY_LEVEL, engine.battery().caseLevel());
            assertEquals("挂载观察者必须真的回放过一次电量（证明回放通道在工作）",
                    1, next.batteryReplays.get());

            // 屏障：旧会话终点在引擎线程上收尾后，回放也不得冒出上一台设备的值。
            assertTrue("旧会话必须在引擎轮次里收尾", disconnected.await(3, TimeUnit.SECONDS));
            assertNull("旧会话收尾后仍不得回放上一台设备的信息", next.info.get());
        } finally {
            engine.release();
        }
    }

    /**
     * 门闩钉住的时序：引擎线程<b>还没有拾取</b> {@code startConnectAttempt} 时，回放也必须已经是
     * 空域。旧会话的断连此时尚未发出（证明引擎任务确实没跑），清域却已完成——起点清域不依赖
     * 引擎轮次，只依赖 {@code connect} 的调用线程。
     */
    @Test
    public void observerReplayIsEmptyWhileEngineHasNotRunConnectAttempt() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        GatedExecutor gated = new GatedExecutor();
        HsProtocolEngine engine = new HsProtocolEngine(link, gated);
        try {
            DeviceReplay previous = new DeviceReplay();
            engine.setDeviceObserver(previous);
            AtomicInteger disconnected = new AtomicInteger();
            CountDownLatch downSeen = new CountDownLatch(1);
            engine.setConnectionObserver(new HsConnectionObserver() {
                @Override
                public void onDisconnected(String reason) {
                    disconnected.incrementAndGet();
                    downSeen.countDown();
                }
            });
            seedSessionADomain(link, engine, previous);

            // 扣住引擎队列：此后的 startConnectAttempt 只会留在队列里，引擎线程不会执行它。
            gated.hold();
            engine.connect(MAC_B);

            DeviceReplay next = new DeviceReplay();
            engine.setDeviceObserver(next);

            assertNull("引擎任务未执行时回放也必须为空域：不得回放上一台设备的信息",
                    next.info.get());
            assertEquals("回放电量必须是未知",
                    UNKNOWN_BATTERY_LEVEL, engine.battery().leftLevel());
            assertEquals("回放电量必须是未知",
                    UNKNOWN_BATTERY_LEVEL, engine.battery().rightLevel());
            assertEquals("回放电量必须是未知",
                    UNKNOWN_BATTERY_LEVEL, engine.battery().caseLevel());
            assertEquals("挂载观察者必须真的回放过一次电量（证明回放通道在工作）",
                    1, next.batteryReplays.get());
            assertEquals("引擎任务此时必须真的没跑：旧会话的断连尚未发出",
                    0, disconnected.get());

            gated.open();
            assertTrue("放行后旧会话必须收尾", downSeen.await(3, TimeUnit.SECONDS));
            assertEquals("一次换目标只发一条断连", 1, disconnected.get());
            assertNull("旧会话收尾后新观察者仍不得收到上一台设备的信息", next.info.get());
        } finally {
            gated.open();
            engine.release();
        }
    }

    /**
     * 让会话 A 的域状态真的带上固件 / 颜色 / 电量：没有这三项，"回放为空"就区分不出
     * 「已被清空」与「从来没写过」。
     */
    private static void seedSessionADomain(FakeHsCommandLink link,
                                           HsProtocolEngine engine,
                                           DeviceReplay observer) throws InterruptedException {
        engine.connect(MAC_A);
        link.ready();
        assertTrue("READY 必须补查耳机颜色", link.awaitWritten(EAR_COLOR_QUERY));
        link.inbound(EAR_COLOR_RESPONSE);
        engine.submit(new HomePageStatusV12FirstCommand(), null);
        assertTrue("首页聚合命令必须下发", link.awaitWritten(HOME_STATUS_QUERY));
        link.inbound(homeStatusFrame(TLV_FIRMWARE + FIRMWARE_TEXT_HEX + TLV_BATTERY));

        assertTrue("会话 A 的固件必须已回放落地",
                observer.firmwareSeen.await(3, TimeUnit.SECONDS));
        HsDeviceInfo seeded = observer.info.get();
        assertEquals("会话 A 的固件必须先真的存在", FIRMWARE_TEXT, seeded.firmware());
        assertEquals("会话 A 的耳机颜色必须先真的存在", EAR_COLOR_VALUE, seeded.earColor());
        assertEquals("会话 A 的电量必须先真的存在", BATTERY_LEVEL, engine.battery().leftLevel());
    }

    /** 首页聚合状态回包：{@code 99ECC7 + 长度 + TLV + 1234}。 */
    private static String homeStatusFrame(String tlvHex) {
        int payloadBytes = tlvHex.length() / 2;
        return "99ECC7"
                + String.format("%04X", payloadBytes)
                + tlvHex
                + "1234";
    }

    /** 记录回放内容的设备观察者：最后一条设备信息与一次电量回放的计数。 */
    private static final class DeviceReplay implements HsDeviceObserver {
        private final AtomicReference<HsDeviceInfo> info = new AtomicReference<>();
        private final AtomicInteger batteryReplays = new AtomicInteger();
        private final CountDownLatch firmwareSeen = new CountDownLatch(1);

        @Override
        public void onDeviceInfo(HsDeviceInfo value) {
            info.set(value);
            if (value.hasFirmware()) {
                firmwareSeen.countDown();
            }
        }

        @Override
        public void onBatteryStateChanged(boolean leftUpdated,
                                          boolean rightUpdated,
                                          boolean caseUpdated) {
            batteryReplays.incrementAndGet();
        }
    }

    /**
     * 可控引擎线程：{@link #hold()} 之后所有 {@code execute} 提交的任务只留在队列里，引擎线程不会
     * 被唤醒——用来证明「引擎任务未执行」这一状态下的起点清域。{@code schedule} 仍直连真实定时器，
     * 避免把命令内部超时也一起扣住。
     */
    private static final class GatedExecutor extends ScheduledThreadPoolExecutor {
        private final Object lock = new Object();
        private final Deque<Runnable> held = new ArrayDeque<>();
        private boolean gateOpen = true;

        GatedExecutor() {
            super(1, runnable -> {
                Thread thread = new Thread(runnable, "yo-hs-protocol-gated");
                thread.setDaemon(true);
                return thread;
            });
        }

        @Override
        public void execute(Runnable task) {
            if (admit(task)) {
                super.execute(task);
            }
        }

        void hold() {
            synchronized (lock) {
                gateOpen = false;
            }
        }

        void open() {
            List<Runnable> released;
            synchronized (lock) {
                gateOpen = true;
                released = new ArrayList<>(held);
                held.clear();
            }
            for (Runnable task : released) {
                super.execute(task);
            }
        }

        /** @return true 表示该任务应当立即投递给引擎线程。 */
        private boolean admit(Runnable task) {
            synchronized (lock) {
                if (gateOpen) {
                    return true;
                }
                held.addLast(task);
                return false;
            }
        }
    }
}
