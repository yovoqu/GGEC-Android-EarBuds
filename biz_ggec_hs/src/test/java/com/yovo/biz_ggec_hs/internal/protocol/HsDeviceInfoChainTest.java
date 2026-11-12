package com.yovo.biz_ggec_hs.internal.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.biz_ggec_hs.api.HsConnectionObserver;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.EarColorCommand;
import com.yovo.biz_ggec_hs.internal.protocol.devicecmd.HomePageStatusV12FirstCommand;
import com.yovo.biz_ggec_hs.internal.protocol.musiccmd.EQModeCommand;
import com.yovo.biz_ggec_hs.model.HsBattery;
import com.yovo.biz_ggec_hs.model.HsDeviceInfo;
import com.yovo.biz_ggec_hs.model.HsDeviceObserver;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 设备信息链路：首页聚合的固件（TLV {@code 0x94}）与耳机颜色命令回包都要落到
 * {@link HsDeviceObserver#onDeviceInfo}，并在新观察者挂载时回放。
 *
 * <p>另一半是会话边界：域状态只属于一次会话，新会话不得继承上一台设备的值，
 * 断连后读到的是未知；READY 自动查询耳机颜色也不得占住串行队列拖慢业务命令。</p>
 */
public class HsDeviceInfoChainTest {
    /** {@code HomePageStatusV12FirstCommand} 的上行载荷。 */
    private static final String HOME_STATUS_QUERY = "99ECC70001001234";
    /** {@code EarColorCommand} 的上行载荷。 */
    private static final String EAR_COLOR_QUERY = "99EC910001001234";
    /** {@code EQModeCommand} 的上行载荷。 */
    private static final String EQ_MODE_QUERY = "99EC830001001234";
    /** 耳机颜色回包：颜色字节 {@code 0x2A}。 */
    private static final String EAR_COLOR_RESPONSE = "99EC9100012A1234";
    private static final int EAR_COLOR_VALUE = 0x2A;
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
    /** 自动颜色查询的响应等待：设备不支持 {@code 99EC91} 时的快速失败窗口。 */
    private static final long AUTO_QUERY_TIMEOUT_MS = 800L;
    /**
     * 业务命令下发预算：{@link #AUTO_QUERY_TIMEOUT_MS} + 调度器命令间隔（30ms）+ 余量。
     * 该值必须明显小于 {@link com.yovo.biz_ggec_hs.internal.protocol.Command} 的 2000ms 默认等待，
     * 否则「自动查询让出队列」这条断言就区分不出长短超时。
     */
    private static final long DISPATCH_BUDGET_MS = 1600L;

    @Test
    public void homeStatusFirmwareReachesDeviceInfo() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            AtomicReference<HsDeviceInfo> latest = new AtomicReference<>();
            CountDownLatch firmwareSeen = new CountDownLatch(1);
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    latest.set(info);
                    if (info.hasFirmware()) {
                        firmwareSeen.countDown();
                    }
                }
            });

            engine.connect(MAC_A);
            link.ready();
            // READY 会自动查一次颜色，先让它收尾，首页命令才能拿到应答窗口。
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            engine.submit(new HomePageStatusV12FirstCommand(), null);
            link.inbound(EAR_COLOR_RESPONSE);
            assertTrue(link.awaitWritten(HOME_STATUS_QUERY));

            link.inbound(homeStatusFrame(TLV_FIRMWARE + FIRMWARE_TEXT_HEX));

            assertTrue(firmwareSeen.await(3, TimeUnit.SECONDS));
            HsDeviceInfo info = latest.get();
            assertNotNull(info);
            assertEquals(FIRMWARE_TEXT, info.firmware());
            assertTrue(info.hasFirmware());
        } finally {
            engine.release();
        }
    }

    @Test
    public void earColorResponseReachesDeviceInfo() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            AtomicReference<HsDeviceInfo> latest = new AtomicReference<>();
            CountDownLatch colorSeen = new CountDownLatch(1);
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    latest.set(info);
                    colorSeen.countDown();
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            link.inbound(EAR_COLOR_RESPONSE);

            assertTrue(colorSeen.await(3, TimeUnit.SECONDS));
            HsDeviceInfo info = latest.get();
            assertNotNull(info);
            assertEquals(EAR_COLOR_VALUE, info.earColor());
            assertTrue(info.hasEarColor());
            // 首页状态还没回，固件保持「未知」而不是空指针或上一台设备的值。
            assertFalse(info.hasFirmware());
            assertEquals(HsDeviceInfo.UNKNOWN_FIRMWARE, info.firmware());
        } finally {
            engine.release();
        }
    }

    @Test
    public void observerAttachReplaysStoredDeviceInfo() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            CountDownLatch bothSeen = new CountDownLatch(2);
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    bothSeen.countDown();
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            engine.submit(new HomePageStatusV12FirstCommand(), null);
            link.inbound(EAR_COLOR_RESPONSE);
            assertTrue(link.awaitWritten(HOME_STATUS_QUERY));
            link.inbound(homeStatusFrame(TLV_FIRMWARE + FIRMWARE_TEXT_HEX));
            assertTrue(bothSeen.await(3, TimeUnit.SECONDS));

            AtomicReference<HsDeviceInfo> replayed = new AtomicReference<>();
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    replayed.set(info);
                }
            });

            HsDeviceInfo info = replayed.get();
            assertNotNull(info);
            assertEquals(FIRMWARE_TEXT, info.firmware());
            assertEquals(EAR_COLOR_VALUE, info.earColor());
        } finally {
            engine.release();
        }
    }

    /**
     * 缺陷 1 的会话起点侧：同一引擎换了设备，新会话不得继承上一台设备的固件 / 电量。
     */
    @Test
    public void secondSessionDoesNotReplayPreviousDeviceInfo() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            AtomicReference<HsDeviceInfo> firstSession = new AtomicReference<>();
            CountDownLatch firstSessionFirmware = new CountDownLatch(1);
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    firstSession.set(info);
                    if (info.hasFirmware()) {
                        firstSessionFirmware.countDown();
                    }
                }
            });
            CountDownLatch disconnected = new CountDownLatch(1);
            engine.setConnectionObserver(new HsConnectionObserver() {
                @Override
                public void onDisconnected(String reason) {
                    disconnected.countDown();
                }
            });

            // 会话 A：首页聚合带回固件与电量，证明域状态里确实存着 A 的值。
            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            link.inbound(EAR_COLOR_RESPONSE);
            engine.submit(new HomePageStatusV12FirstCommand(), null);
            assertTrue(link.awaitWritten(HOME_STATUS_QUERY));
            link.inbound(homeStatusFrame(TLV_FIRMWARE + FIRMWARE_TEXT_HEX + TLV_BATTERY));
            assertTrue(firstSessionFirmware.await(3, TimeUnit.SECONDS));
            assertEquals(FIRMWARE_TEXT, firstSession.get().firmware());
            assertEquals(BATTERY_LEVEL, engine.battery().leftLevel());

            // 会话终点：断开 A。
            engine.disconnect();
            assertTrue(disconnected.await(3, TimeUnit.SECONDS));

            // 会话 B：起点必须清干净，观察者挂载时不能拿到 A 的固件 / 电量。
            AtomicReference<HsDeviceInfo> secondSession = new AtomicReference<>();
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    secondSession.set(info);
                }
            });
            engine.connect(MAC_B);
            assertNull("会话起点不得回放上一台设备的设备信息", secondSession.get());
            assertEquals(UNKNOWN_BATTERY_LEVEL, engine.battery().leftLevel());

            link.ready();
            assertNull("新会话 READY 后仍不得回放上一台设备的设备信息", secondSession.get());
        } finally {
            engine.release();
        }
    }

    /**
     * 缺陷 1 的会话终点侧：断连之后读到的是未知，而不是上一台设备的值。
     */
    @Test
    public void disconnectClearsDomainStateSoReadsAreUnknown() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            CountDownLatch firmwareSeen = new CountDownLatch(1);
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    if (info.hasFirmware()) {
                        firmwareSeen.countDown();
                    }
                }
            });
            CountDownLatch disconnected = new CountDownLatch(1);
            engine.setConnectionObserver(new HsConnectionObserver() {
                @Override
                public void onDisconnected(String reason) {
                    disconnected.countDown();
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            link.inbound(EAR_COLOR_RESPONSE);
            engine.submit(new HomePageStatusV12FirstCommand(), null);
            assertTrue(link.awaitWritten(HOME_STATUS_QUERY));
            link.inbound(homeStatusFrame(TLV_FIRMWARE + FIRMWARE_TEXT_HEX + TLV_BATTERY));
            assertTrue(firmwareSeen.await(3, TimeUnit.SECONDS));
            assertEquals(BATTERY_LEVEL, engine.battery().leftLevel());

            engine.disconnect();
            assertTrue(disconnected.await(3, TimeUnit.SECONDS));

            assertEquals(UNKNOWN_BATTERY_LEVEL, engine.battery().leftLevel());
            assertEquals(UNKNOWN_BATTERY_LEVEL, engine.battery().rightLevel());
            assertEquals(UNKNOWN_BATTERY_LEVEL, engine.battery().caseLevel());
            AtomicReference<HsDeviceInfo> replayed = new AtomicReference<>();
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    replayed.set(info);
                }
            });
            assertNull("断连后不得回放上一台设备的设备信息", replayed.get());
        } finally {
            engine.release();
        }
    }

    /**
     * 缺陷 2：READY 自动查询占住串行队列时，业务命令必须等该查询自己的短超时结束就能下发，
     * 而不是被默认 2s 等待一起拖住。用例回假设备一条颜色应答都不给，只让自动查询自然到期。
     */
    @Test
    public void autoEarColorQueryReleasesQueueForNextCommand() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            engine.connect(MAC_A);
            link.ready();
            assertTrue("自动颜色查询必须先下发", link.awaitWritten(EAR_COLOR_QUERY));

            long submittedAtMs = System.nanoTime() / 1_000_000L;
            engine.submit(new EQModeCommand(EQModeCommand.MUSIC_MODE), null);
            assertTrue("业务命令必须在颜色查询超时后立即获得下发机会",
                    link.awaitWrittenWithin(EQ_MODE_QUERY, DISPATCH_BUDGET_MS));
            long waitedMs = link.firstWrittenAtMs(EQ_MODE_QUERY) - submittedAtMs;
            assertTrue("业务命令等待了 " + waitedMs + "ms，超过下发预算 " + DISPATCH_BUDGET_MS + "ms",
                    waitedMs < DISPATCH_BUDGET_MS);

            // 队列是被颜色查询超时释放的：没有任何颜色应答，颜色值不得凭空落地。
            AtomicReference<HsDeviceInfo> replayed = new AtomicReference<>();
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    replayed.set(info);
                }
            });
            assertNull("设备未回应颜色查询时不得落地颜色值", replayed.get());
        } finally {
            engine.release();
        }
    }

    /**
     * 缺陷 2 的机制钉：自动补查用 800ms 短等待，手动查询用 2000ms——设备 800–2000ms 的
     * 正常回包不得被手动查询判成超时，而自动补查不能拖住串行队列。
     */
    @Test
    public void earColorQueryTimeoutFollowsQueryOrigin() {
        EarColorCommand auto = new EarColorCommand(EarColorCommand.QueryOrigin.AUTO);
        EarColorCommand manual = new EarColorCommand(EarColorCommand.QueryOrigin.MANUAL);

        // 协议可见的取值：自动补查 800ms、手动查询 2000ms。
        assertEquals("自动补查用短等待", 800L, auto.getTimeoutMs());
        assertEquals("手动查询用默认等待", 2000L, manual.getTimeoutMs());
        // 并与命令自己声明的常量同源，避免测试与实现各写一套数字。
        assertEquals(EarColorCommand.AUTO_QUERY_TIMEOUT_MS, auto.getTimeoutMs());
        assertEquals(EarColorCommand.MANUAL_QUERY_TIMEOUT_MS, manual.getTimeoutMs());
        assertTrue("短等待必须明显小于手动等待",
                auto.getTimeoutMs() < manual.getTimeoutMs());
    }

    @Test
    public void readyQueriesEarColorOncePerSession() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            link.inbound(EAR_COLOR_RESPONSE);

            // 同一会话重复 READY：引擎幂等，不得再下发颜色查询。
            link.ready();
            engine.submit(new EQModeCommand(EQModeCommand.MUSIC_MODE), null);
            assertTrue(link.awaitWritten(EQ_MODE_QUERY));
            assertEquals(1, link.countWrittenWithPrefix("99EC91"));

            // 新会话（断开后再就绪）重新查一次：store 已随会话清空，旧值不能沿用。
            engine.disconnect();
            link.ready();
            assertEquals(2, link.awaitCountWithPrefix("99EC91", 2));
        } finally {
            engine.release();
        }
    }

    /**
     * 在仓状态的主动上报（{@code 99EC96}）必须作为设备信息的一部分投递：
     * 上层拿它推导佩戴状态，而 {@code onBatteryStateChanged} 只有三个 boolean，带不出状态值。
     */
    @Test
    public void earInCaseReportReachesDeviceInfo() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            BlockingQueue<HsDeviceInfo> infos = new LinkedBlockingQueue<>();
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    infos.add(info);
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));

            link.inbound(earInCaseFrame(3));
            assertEarInCaseDelivered(infos, 3);

            link.inbound(earInCaseFrame(1));
            assertEarInCaseDelivered(infos, 1);
        } finally {
            engine.release();
        }
    }

    /** 在仓状态进入会话域后，新挂的观察者必须能直接回放到它。 */
    @Test
    public void observerAttachReplaysStoredEarInCase() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            BlockingQueue<HsDeviceInfo> infos = new LinkedBlockingQueue<>();
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    infos.add(info);
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            link.inbound(earInCaseFrame(3));
            assertEarInCaseDelivered(infos, 3);

            AtomicReference<HsDeviceInfo> replayed = new AtomicReference<>();
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    replayed.set(info);
                }
            });

            HsDeviceInfo info = replayed.get();
            assertNotNull(info);
            assertEquals(3, info.earInCaseStatus());
        } finally {
            engine.release();
        }
    }

    /**
     * 会话终点清域：断连后回放为空，上层读到的是「未上报」
     * （{@link HsDeviceInfo#UNKNOWN_EAR_IN_CASE}），不是上一台设备的在仓状态。
     */
    @Test
    public void disconnectClearsStoredEarInCase() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            BlockingQueue<HsDeviceInfo> infos = new LinkedBlockingQueue<>();
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    infos.add(info);
                }
            });
            CountDownLatch disconnected = new CountDownLatch(1);
            engine.setConnectionObserver(new HsConnectionObserver() {
                @Override
                public void onDisconnected(String reason) {
                    disconnected.countDown();
                }
            });

            engine.connect(MAC_A);
            link.ready();
            assertTrue(link.awaitWritten(EAR_COLOR_QUERY));
            link.inbound(earInCaseFrame(3));
            assertEarInCaseDelivered(infos, 3);

            engine.disconnect();
            assertTrue(disconnected.await(3, TimeUnit.SECONDS));

            AtomicReference<HsDeviceInfo> replayed = new AtomicReference<>();
            engine.setDeviceObserver(new HsDeviceObserver() {
                @Override
                public void onDeviceInfo(HsDeviceInfo info) {
                    replayed.set(info);
                }
            });
            assertNull("断连后不得回放上一台设备的在仓状态", replayed.get());
        } finally {
            engine.release();
        }
    }

    /**
     * 等待一条 earInCaseStatus 为 {@code expected} 的设备信息；超时报出已收到的值。
     */
    private static void assertEarInCaseDelivered(BlockingQueue<HsDeviceInfo> infos, int expected)
            throws InterruptedException {
        long deadlineMs = System.currentTimeMillis() + 3_000L;
        List<Integer> seen = new ArrayList<>();
        while (true) {
            long remainingMs = deadlineMs - System.currentTimeMillis();
            HsDeviceInfo info = remainingMs > 0
                    ? infos.poll(remainingMs, TimeUnit.MILLISECONDS)
                    : null;
            if (info == null) {
                fail("未收到 earInCaseStatus=" + expected + " 的设备信息，已收到 " + seen);
            }
            if (info.earInCaseStatus() == expected) {
                return;
            }
            seen.add(info.earInCaseStatus());
        }
    }

    /** 在仓状态主动上报帧：{@code 99EC96 + 0001 + 状态 + 1234}。 */
    private static String earInCaseFrame(int status) {
        return "99EC96" + "0001" + String.format(Locale.ROOT, "%02X", status) + "1234";
    }

    /** 首页聚合状态回包：{@code 99ECC7 + 长度 + TLV + 1234}。 */
    private static String homeStatusFrame(String tlvHex) {
        int payloadBytes = tlvHex.length() / 2;
        return "99ECC7"
                + String.format("%04X", payloadBytes)
                + tlvHex
                + "1234";
    }
}
