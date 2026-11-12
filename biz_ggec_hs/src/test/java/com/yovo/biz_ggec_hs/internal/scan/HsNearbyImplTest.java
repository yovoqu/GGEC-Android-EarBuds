package com.yovo.biz_ggec_hs.internal.scan;

import com.yovo.biz_ggec_hs.internal.center.broadcast.HsMarket;
import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;


import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;
import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.internal.center.port.ScanPace;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.port.HsBleScanLink;
import com.yovo.biz_ggec_hs.internal.center.port.HsRawAdvert;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 附近设备的编排：识别 → 入库 → 规则投影 → 视图/健康度。
 *
 * <p>规则变化必须零重扫（只重投影）；TTL 与 lost 只有注册表一个来源；谓词下推只做优化。</p>
 */
public final class HsNearbyImplTest {

    private static final String FEATURE_CN = "86103800900000100000fffe";
    /** 厂商数据里未知市场号：协议认得是 HS02 耳机，但产品目录没有对应型号 → modelId="". */
    private static final int UNKNOWN_MARKET = 9;
    private static final long START_AT = 100_000L;

    private static final String ADDRESS_A = "11:22:33:44:55:01";
    private static final String ADDRESS_B = "11:22:33:44:55:02";
    private static final String ADDRESS_C = "11:22:33:44:55:03";

    private final AtomicLong now = new AtomicLong(START_AT);
    private final FakeLink link = new FakeLink();
    private final FakeTicker ticker = new FakeTicker();
    private final RecordingObserver observer = new RecordingObserver();
    private final HsNearbyImpl nearby =
            new HsNearbyImpl(link, new HsCatalogImpl(), ticker, now::get);

    @Test
    public void identifiesDevicesAndProjectsFacetCounts() {
        nearby.start(observer);
        link.onBatch(List.of(
                hs01(ADDRESS_A, -60),
                hs02(ADDRESS_B, -50, "Vidda"),
                unregistered(ADDRESS_C, -70)));

        HsScan.View view = nearby.view();
        assertEquals("未知市场不入表", 2, view.totalInRange());
        assertEquals(2, view.devices().size());
        assertEquals("列表按 RSSI 降序",
                List.of(ADDRESS_B, ADDRESS_A),
                view.devices().stream().map(device -> device.radios().bleAddress()).toList());
        assertEquals(Integer.valueOf(2), view.countsByType().get(HsDevice.Type.EARBUDS));
        assertEquals(Integer.valueOf(2), view.countsByProject().get("HS"));
        assertEquals(Integer.valueOf(1), view.countsByModel().get("HS01"));
        assertEquals(Integer.valueOf(1), view.countsByModel().get("HS02"));
        assertFalse("未登记型号不进型号分面", view.countsByModel().containsKey(""));
        assertEquals(ADDRESS_B, observer.lastUpdated().get(0).radios().bleAddress());
        assertEquals("HS01", view.devices().get(1).modelId());
    }

    @Test
    public void modelFilterExcludesUnregisteredDevices() {
        nearby.start(observer);
        link.onBatch(List.of(hs01(ADDRESS_A, -60), unregistered(ADDRESS_C, -70)));

        nearby.setFilterRule(rule(Set.of(), Set.of(), Set.of("HS01"), false,
                HsScan.Rule.MIN_RSSI_UNRESTRICTED));

        HsScan.View view = nearby.view();
        assertEquals(1, view.devices().size());
        assertEquals("HS01", view.devices().get(0).modelId());
        assertEquals("未知市场根本不入表", 1, view.totalInRange());
    }

    @Test
    public void typeAndProjectFiltersAlsoExcludeUnregisteredDevices() {
        nearby.start(observer);
        link.onBatch(List.of(hs01(ADDRESS_A, -60), unregistered(ADDRESS_C, -70)));

        // 未登记型号没有设备类型/项目归属：任一维度筛选即按「不匹配」排除。
        nearby.setFilterRule(rule(Set.of(HsDevice.Type.EARBUDS), Set.of(), Set.of(), false,
                HsScan.Rule.MIN_RSSI_UNRESTRICTED));
        assertEquals(1, nearby.view().devices().size());

        nearby.setFilterRule(rule(Set.of(), Set.of("HS"), Set.of(), false,
                HsScan.Rule.MIN_RSSI_UNRESTRICTED));
        assertEquals(1, nearby.view().devices().size());

        // 空规则下未知市场仍然不出现。
        nearby.setFilterRule(HsScan.Rule.none());
        assertEquals(1, nearby.view().devices().size());
    }

    @Test
    public void dimensionsAreAndedWhileValuesWithinADimensionAreOred() {
        nearby.start(observer);
        link.onBatch(List.of(hs01(ADDRESS_A, -60), hs02(ADDRESS_B, -50, "Vidda")));

        // 维内 OR：型号集合命中任一
        nearby.setFilterRule(rule(Set.of(), Set.of(), Set.of("HS01", "HS02"), false,
                HsScan.Rule.MIN_RSSI_UNRESTRICTED));
        assertEquals(2, nearby.view().devices().size());

        // 维间 AND：类型 + 项目同时满足
        nearby.setFilterRule(rule(Set.of(HsDevice.Type.EARBUDS), Set.of("HS"), Set.of(), false,
                HsScan.Rule.MIN_RSSI_UNRESTRICTED));
        assertEquals(2, nearby.view().devices().size());

        // 类型不匹配：AND 直接排除
        nearby.setFilterRule(rule(Set.of(HsDevice.Type.SPEAKER), Set.of("HS"), Set.of(), false,
                HsScan.Rule.MIN_RSSI_UNRESTRICTED));
        assertEquals(0, nearby.view().devices().size());

        // 空集=不限
        nearby.setFilterRule(HsScan.Rule.none());
        assertEquals(2, nearby.view().devices().size());
    }

    @Test
    public void minRssiAndConnectableFilterOnTheRegistry() {
        nearby.start(observer);
        link.onBatch(List.of(hs01(ADDRESS_A, -80), hs02(ADDRESS_B, -40, "Vidda")));

        nearby.setFilterRule(rule(Set.of(), Set.of(), Set.of(), false, -50));
        assertEquals(1, nearby.view().devices().size());
        assertEquals(ADDRESS_B, nearby.view().devices().get(0).radios().bleAddress());

        nearby.setFilterRule(rule(Set.of(), Set.of(), Set.of(), true,
                HsScan.Rule.MIN_RSSI_UNRESTRICTED));
        assertEquals("两台都声明可连接", 2, nearby.view().devices().size());

        link.onBatch(List.of(notConnectable(ADDRESS_C, -45)));
        assertEquals("仅显示可连接：不可连接的那台被排除", 2, nearby.view().devices().size());
    }

    @Test
    public void ruleChangeReprojectsWithoutRestartingScanOrClearingTheTable() {
        nearby.start(observer);
        link.onBatch(List.of(hs01(ADDRESS_A, -60), hs02(ADDRESS_B, -50, "Vidda")));
        int startsBefore = link.startCount;

        nearby.setFilterRule(rule(Set.of(), Set.of(), Set.of("HS02"), false,
                HsScan.Rule.MIN_RSSI_UNRESTRICTED));
        nearby.setFilterRule(HsScan.Rule.none());

        assertEquals("规则变化不得重启扫描", startsBefore, link.startCount);
        assertEquals("规则变化不得清表", 2, nearby.view().totalInRange());
    }

    @Test
    public void stopKeepsTheTableUntilRetainFires() {
        nearby.start(observer);
        link.onBatch(List.of(hs01(ADDRESS_A, -60)));
        nearby.stop();
        assertEquals(1, nearby.view().totalInRange());

        ticker.tick();

        assertEquals(0, nearby.view().totalInRange());
    }

    @Test
    public void startMergesIntoTheRetainedTable() {
        nearby.start(observer);
        link.onBatch(List.of(hs01(ADDRESS_A, -60)));
        nearby.stop();
        nearby.start(observer);
        assertEquals("重新开始扫描不得清表", 1, nearby.view().totalInRange());
    }

    @Test
    public void libraryLostMergesIntoTheSameTable() {
        nearby.start(observer);
        link.onBatch(List.of(hs01(ADDRESS_A, -60)));

        link.currentObserver().onLost(ADDRESS_A);
        assertEquals(1, observer.lost.size());
        assertEquals(0, nearby.view().totalInRange());

        // 表里已经没有它：重复 lost 不再上报。
        link.currentObserver().onLost(ADDRESS_A);
        assertEquals(1, observer.lost.size());
    }

    @Test
    public void onlyRawFieldExpressibleRulesArePushedDown() {
        nearby.start(observer);
        assertNull("默认放行全部", link.predicate);

        nearby.setFilterRule(rule(Set.of(), Set.of(), Set.of(), false, -50));
        Predicate<HsRawAdvert> predicate = link.predicate;
        assertNotNull("只有 RSSI 下界可下推", predicate);
        assertTrue(predicate.test(hs01(ADDRESS_A, -40)));
        assertFalse(predicate.test(hs01(ADDRESS_A, -60)));

        nearby.setFilterRule(rule(Set.of(), Set.of(), Set.of("HS01"), false, -50));
        assertNull("型号维度无法用原始帧表达，谓词必须回落到放行全部", link.predicate);
    }

    @Test
    public void scanProfileIsForwardedToTheMechanismLayer() {
        nearby.start(observer, ScanPace.lowPower());
        assertEquals(ScanPace.Mode.LOW_POWER, link.profile.mode());
    }

    @Test
    public void healthReportsSilentAndThrottled() {
        nearby.start(observer);
        assertFalse("刚开始扫描不算静默", nearby.health().silent());
        assertFalse(nearby.health().throttled());

        now.set(START_AT + HsNearbyImpl.SILENT_WINDOW_MILLIS + 1L);
        assertTrue("扫描中长时间无帧应报静默", nearby.health().silent());

        link.failCurrent(HsControl.Failure.SCAN_THROTTLED);
        assertTrue("限流拒绝时应报 throttled", nearby.health().throttled());
        assertFalse("失败后不再处于扫描中", nearby.health().scanning());
        assertEquals(HsControl.Failure.SCAN_THROTTLED, observer.failures.get(0));
    }

    @Test
    public void staleCallbacksFromPreviousSessionAreDropped() {
        RecordingObserver previous = new RecordingObserver();
        nearby.start(previous);
        HsBleScanLink.Observer previousCallbacks = link.currentObserver();

        nearby.start(observer);
        int updatesBefore = previous.updates.size();
        previousCallbacks.onBatch(List.of(hs01(ADDRESS_A, -60)));
        previousCallbacks.onLost(ADDRESS_A);

        assertEquals("旧会话不得再收到更新", updatesBefore, previous.updates.size());
        assertTrue("旧会话不得再收到 lost", previous.lost.isEmpty());
        assertTrue("新会话仍在扫描", nearby.isScanning());

        // 新会话照常工作。
        link.onBatch(List.of(hs01(ADDRESS_A, -60)));
        assertEquals(1, nearby.view().totalInRange());
    }

    @Test
    public void startDoesNotClearTheRetainedTable() {
        nearby.start(observer);
        link.onBatch(List.of(hs01(ADDRESS_A, -60)));
        nearby.stop();
        assertEquals("stop 不停表", 1, nearby.view().totalInRange());

        nearby.start(observer);
        assertEquals("start 不得清表", 1, nearby.view().totalInRange());
    }

    private static HsScan.Rule rule(Set<HsDevice.Type> deviceTypes,
                                           Set<String> projectIds,
                                           Set<String> modelIds,
                                           boolean onlyConnectable,
                                           int minRssi) {
        return new HsScan.Rule(deviceTypes, projectIds, modelIds, onlyConnectable, minRssi);
    }

    private HsRawAdvert hs01(String address, int rssi) {
        return adopt(address, rssi, "buds", List.of(hs01Uuid(FEATURE_CN)), new byte[0], true);
    }

    private HsRawAdvert hs02(String address, int rssi, String name) {
        return adopt(address, rssi, name, Collections.emptyList(), hs02Packet(name, HsMarket.CN2), true);
    }

    private HsRawAdvert unregistered(String address, int rssi) {
        return adopt(address, rssi, "Unknown", Collections.emptyList(),
                hs02Packet("Unknown", UNKNOWN_MARKET), true);
    }

    private HsRawAdvert notConnectable(String address, int rssi) {
        return adopt(address, rssi, "Vidda", Collections.emptyList(),
                hs02Packet("Vidda", 0), false);
    }

    private HsRawAdvert adopt(String address, int rssi, String name, List<UUID> uuids,
                              byte[] raw, boolean connectable) {
        return new HsRawAdvert(address, name, rssi, uuids, raw, connectable, now.get());
    }

    private static UUID hs01Uuid(String featureCode) {
        byte[] prefix = new byte[9];
        for (int index = 0; index < prefix.length; index++) {
            prefix[index] = (byte) Integer.parseInt(
                    featureCode.substring(index * 2, index * 2 + 2), 16);
        }
        byte[] uuidBytes = new byte[16];
        uuidBytes[0] = (byte) 0xFF;
        uuidBytes[1] = (byte) 0xEE;
        uuidBytes[2] = (byte) 0xDD;
        uuidBytes[3] = (byte) 0xCC;
        uuidBytes[4] = (byte) 0xBB;
        uuidBytes[5] = (byte) 0xAA;
        uuidBytes[6] = 0x02;
        // 特征码在 UUID 里是逆序存放（解码时按 uuidBytes[15 - i] 读回）。
        for (int index = 0; index < prefix.length; index++) {
            uuidBytes[15 - index] = prefix[index];
        }
        ByteBuffer buffer = ByteBuffer.wrap(uuidBytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static byte[] hs02Packet(String name, int marketId) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        byte[] nameField = new byte[nameBytes.length + 2];
        nameField[0] = (byte) (nameBytes.length + 1);
        nameField[1] = 0x09;
        System.arraycopy(nameBytes, 0, nameField, 2, nameBytes.length);
        byte[] manufacturer = new byte[]{
                (byte) 0xFF, (byte) 0xFF, 0x48, 0x43, 0x01, 0x02, 0x12,
                0x01, 0x00, 0x00, 0x00,
                0x0A, 0x06,
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF,
                0x0B, 0x01, (byte) marketId
        };
        byte[] mfgField = new byte[manufacturer.length + 2];
        mfgField[0] = (byte) (manufacturer.length + 1);
        mfgField[1] = (byte) 0xFF;
        System.arraycopy(manufacturer, 0, mfgField, 2, manufacturer.length);
        byte[] out = new byte[nameField.length + mfgField.length];
        System.arraycopy(nameField, 0, out, 0, nameField.length);
        System.arraycopy(mfgField, 0, out, nameField.length, mfgField.length);
        return out;
    }

    /** 传输层端口替身：记录订阅次数、谓词与档位，回调由用例显式投递。 */
    private static final class FakeLink implements HsBleScanLink {
        private final List<Observer> observers = new ArrayList<>();
        private boolean scanning;
        private int startCount;
        private Predicate<HsRawAdvert> predicate;
        private ScanPace profile;

        @Override
        public void start(Observer observer) {
            observers.add(observer);
            scanning = true;
            startCount++;
        }

        @Override
        public void stop() {
            scanning = false;
        }

        @Override
        public boolean isScanning() {
            return scanning;
        }

        @Override
        public void setFramePredicate(Predicate<HsRawAdvert> predicate) {
            this.predicate = predicate;
        }

        @Override
        public void applyScanProfile(ScanPace profile) {
            this.profile = profile;
        }

        Observer currentObserver() {
            return observers.get(observers.size() - 1);
        }

        void onBatch(List<HsRawAdvert> adverts) {
            for (Observer observer : observers) {
                observer.onBatch(adverts);
            }
        }

        void failCurrent(HsControl.Failure failure) {
            scanning = false;
            currentObserver().onFailed(failure);
        }
    }

    /** TTL 心跳替身：只记录周期任务，由用例显式触发。 */
    private static final class FakeTicker implements HsNearbyTicker {
        private Runnable task;

        @Override
        public void schedule(Runnable task, long periodMillis) {
            this.task = task;
        }

        @Override
        public void scheduleOnce(Runnable task, long delayMillis) {
            this.task = task;
        }

        @Override
        public void cancel() {
            task = null;
        }

        void tick() {
            assertNotNull("心跳未启动", task);
            task.run();
        }
    }

    /** 记录上报。 */
    private static final class RecordingObserver implements HsScan.Listener {
        private final List<List<HsScan.Found>> updates = new ArrayList<>();
        private final List<HsScan.Found> lost = new ArrayList<>();
        private final List<HsControl.Failure> failures = new ArrayList<>();
        private int finished;

        @Override
        public void onUpdated(List<HsScan.Found> devices) {
            updates.add(devices);
        }

        @Override
        public void onLost(HsScan.Found device) {
            lost.add(device);
        }

        @Override
        public void onFailed(HsControl.Failure failure) {
            failures.add(failure);
        }

        @Override
        public void onFinished() {
            finished++;
        }

        List<HsScan.Found> lastUpdated() {
            assertFalse("没有收到过更新", updates.isEmpty());
            return updates.get(updates.size() - 1);
        }
    }
}
