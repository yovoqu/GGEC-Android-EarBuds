package com.yovo.biz_ggec_hs.internal.link;

import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;


import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsControl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.protocol.FakeHsCommandLink;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;

import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * 电量契约：协议电平文本在快照投影处一次转成 {@code int}，未知只用
 * {@link HsDevice.Battery#UNKNOWN_LEVEL} 表达，模型里不出现 {@code --} 这类协议文本形态。
 *
 * <p>断言都等「期望的快照」而不是等固定时间，因此不依赖引擎线程调度。</p>
 */
public final class HsBatteryProjectionTest {
    private static final String MAC = "00:11:22:33:44:55";
    private static final String EAR_COLOR_QUERY = "99EC910001001234";
    private static final String EAR_COLOR_RESPONSE = "99EC9100012A1234";
    private static final String HOME_STATUS_QUERY = "99ECC70001001234";
    private static final String LDAC_QUERY = "99ECCF0001001234";
    private static final String LDAC_ON_RESPONSE = "99ECCF0001011234";
    private static final String HOME_STATUS_RESPONSE =
            "99ECC7" + "0008" + "9406" + "56312E322E33" + "1234";

    @Test
    public void unknownLevelsStartAsStructuredUnknown() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady();
            HsDevice.Battery battery = harness.session.snapshot().battery();
            assertEquals(HsDevice.Battery.UNKNOWN_LEVEL, battery.leftLevel());
            assertEquals(HsDevice.Battery.UNKNOWN_LEVEL, battery.rightLevel());
            assertEquals(HsDevice.Battery.UNKNOWN_LEVEL, battery.caseLevel());
            assertEquals(false, battery.caseCharging());
        } finally {
            harness.close();
        }
    }

    @Test
    public void decimalTextBecomesStructuredLevel() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady();
            // 设备主动上报组合电量：左 80 / 右 83 / 盒 100，再报充电盒充电中。
            harness.link.inbound("99EC800003" + "0150" + "0253" + "0364" + "1234");
            harness.link.inbound("99EC95000101" + "1234");

            assertTrue("电量必须进入快照",
                    harness.awaitSnapshot(snapshot -> snapshot.battery().leftLevel() == 80
                            && snapshot.battery().caseCharging()));
            HsDevice.Battery battery = harness.session.snapshot().battery();
            assertEquals(80, battery.leftLevel());
            assertEquals(83, battery.rightLevel());
            assertEquals(100, battery.caseLevel());
        } finally {
            harness.close();
        }
    }

    @Test
    public void levelOutsideProtocolRangeBecomesUnknownNotZero() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady();
            // 左耳电平 0 不在 1..100：按未知处理，不得显示成 0%。
            harness.link.inbound("99EC800003" + "0100" + "0250" + "0350" + "1234");

            assertTrue("右耳 80 必须先落地",
                    harness.awaitSnapshot(snapshot -> snapshot.battery().rightLevel() == 80));
            HsDevice.Snapshot snapshot = harness.session.snapshot();
            assertEquals(HsDevice.Battery.UNKNOWN_LEVEL, snapshot.battery().leftLevel());
            assertEquals(80, snapshot.battery().rightLevel());
        } finally {
            harness.close();
        }
    }

    @Test
    public void proactiveCombinedBatteryFrameUpdatesAllThree() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady();
            harness.link.inbound("99EC800003" + "0101" + "0202" + "0303" + "1234");

            assertTrue("组合电量帧必须整体落地",
                    harness.awaitSnapshot(snapshot -> snapshot.battery().leftLevel() == 1
                            && snapshot.battery().rightLevel() == 2
                            && snapshot.battery().caseLevel() == 3));
        } finally {
            harness.close();
        }
    }


    /** 会话 + 假链路 + 快照观察者：等待条件通知，不用 sleep 轮询。 */
    private static final class Harness {
        private static final long AWAIT_TIMEOUT_MILLIS = 3_000L;

        private final FakeHsCommandLink link = new FakeHsCommandLink();
        private final HsProtocolEngine engine = new HsProtocolEngine(link);
        private final HsSessionImpl session =
                new HsSessionImpl(engine, new HsCatalogImpl(), new FakeMainThread());
        private final List<HsDevice.Snapshot> snapshots = new CopyOnWriteArrayList<>();
        private final Object lock = new Object();
        private volatile boolean ready;

        private Harness() {
            session.observe(new HsControl.Listener() {
                @Override
                public void onConnectionChanged(HsControl.State state, HsControl.Failure failure) {
                    if (state == HsControl.State.READY) {
                        synchronized (lock) {
                            ready = true;
                            lock.notifyAll();
                        }
                    }
                }

                @Override
                public void onSnapshotChanged(HsDevice.Snapshot snapshot) {
                    snapshots.add(snapshot);
                }
            });
        }

        private void connectReady() throws Exception {
            session.connect("HS02", new HsDevice.Radios(MAC, "", "", ""));
            link.ready();
            awaitReady();
            if (link.awaitWritten(EAR_COLOR_QUERY)) {
                link.inbound(EAR_COLOR_RESPONSE);
            }
            if (link.awaitWritten(HOME_STATUS_QUERY)) {
                link.inbound(HOME_STATUS_RESPONSE);
            }
            if (link.awaitWritten(LDAC_QUERY)) {
                link.inbound(LDAC_ON_RESPONSE);
            }
        }

        private void awaitReady() throws InterruptedException {
            long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
            synchronized (lock) {
                while (!ready) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        throw new AssertionError("会话未在预算内就绪");
                    }
                    lock.wait(remaining);
                }
            }
        }

        private boolean awaitSnapshot(Predicate<HsDevice.Snapshot> condition) throws InterruptedException {
            long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
            synchronized (lock) {
                while (true) {
                    for (HsDevice.Snapshot snapshot : snapshots) {
                        if (condition.test(snapshot)) {
                            return true;
                        }
                    }
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        return false;
                    }
                    lock.wait(Math.min(remaining, 50L));
                }
            }
        }

        private void close() {
            session.close();
        }
    }
}
