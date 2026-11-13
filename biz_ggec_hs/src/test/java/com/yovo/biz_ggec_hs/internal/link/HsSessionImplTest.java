package com.yovo.biz_ggec_hs.internal.link;

import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;


import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsControl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.port.HsClassicRadio;
import com.yovo.biz_ggec_hs.internal.center.protocol.FakeHsCommandLink;
import com.yovo.biz_ggec_hs.internal.center.protocol.HsProtocolEngine;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQRealtimeCommand;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 会话契约的端到端行为：连接态迁移、快照投影、能力写入、动作下发、结果与事件的归一。
 *
 * <p>假链路只回固定帧，主线程替身就地执行，因此断言不看时序、只看契约。</p>
 */
public final class HsSessionImplTest {
    private static final String MAC = "00:11:22:33:44:55";
    /** 连接目标：默认按 HS02 判定能力集合。 */
    private static final String HS02_MODEL = "HS02";
    /** {@code HomePageStatusV12FirstCommand} 上行载荷与含固件的回包（READY 自动补查）。 */
    private static final String HOME_STATUS_QUERY = "99ECC70001001234";
    private static final String HOME_STATUS_RESPONSE =
            "99ECC7" + "0008" + "9406" + "56312E322E33" + "1234";
    private static final String FIRMWARE_TEXT = "V1.2.3";
    /** {@code LDACQueryCommand} 上行载荷与「开启」回包（READY 自动补查）。 */
    private static final String LDAC_QUERY = "99ECCF0001001234";
    private static final String LDAC_ON_RESPONSE = "99ECCF0001011234";
    /** {@code ResetControlCommand} / {@code DeviceRebootCommand} 上行载荷。 */
    private static final String RESET_CONTROL_QUERY = "99EC860001001234";
    private static final String REBOOT_QUERY = "99ECA60001001234";
    /** {@code BeginnerModeCommand} 进入/退出新手模式的上行载荷（应答与请求同形）。 */
    private static final String BEGINNER_MODE_ENTER = "99ECCC0001001234";
    private static final String BEGINNER_MODE_EXIT = "99ECCD0001001234";
    /** {@code EarColorCommand} 上行载荷与回包。 */
    private static final String EAR_COLOR_QUERY = "99EC910001001234";
    private static final String EAR_COLOR_RESPONSE = "99EC9100012A1234";
    private static final int EAR_COLOR_VALUE = 0x2A;
    /** {@code EQModeCommand} 上行载荷与 V12 回包（模式 1、状态 0）。 */
    private static final String EQ_MODE_QUERY = "99EC830001011234";
    private static final String EQ_MODE_RESPONSE = "99EC83000201001234";
    /** 已配对列表查询（空列表）与双连接页聚合查询。 */
    private static final String PAIRED_DEVICES_QUERY = "99ECA70001001234";
    private static final String PAIRED_DEVICES_EMPTY_RESPONSE = "99ECA70001001234";
    private static final String DUAL_PAGE_QUERY = "99ECBD0001001234";
    /** 双连接页回包：双连接开、优先设备 AA:BB:CC:DD:EE:FF、一条已配对设备。 */
    private static final String DUAL_PAGE_RESPONSE = "99ECBD" + "002B"
            + "0101" + "01"
            + "0207" + "00" + "AABBCCDDEEFF"
            + "03" + "001C" + "01"
            + "0002" + "5A5A"
            + "0106" + "AABBCCDDEEFF"
            + "020101" + "030101" + "040100" + "050100" + "060100"
            + "1234";
    /** 录音列表查询与一条记录的回包（索引 3、时长 16 秒、大小 4096 字节）。 */
    private static final String RECORD_LIST_QUERY = "99ECC00002000C1234";
    private static final String RECORD_LIST_RESPONSE = "99ECC0" + "0016" + "00" + "01"
            + "EA070B0C030A141E" + "03" + "21" + "1000" + "00100000" + "00000000"
            + "1234";

    @Test
    public void connectReadyPublishesConnectionStateAndSnapshot() throws Exception {
        Harness harness = new Harness();
        try {
            RecordingObserver observer = new RecordingObserver();
            harness.session.observe(observer);

            assertEquals("挂观察者必须立刻回放当前连接态",
                    HsControl.State.IDLE, observer.lastConnection());

            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            assertTrue("READY 必须通知观察者",
                    observer.awaitConnection(HsControl.State.READY));

            HsDevice.Snapshot snapshot = harness.session.snapshot();
            assertEquals(HsControl.State.READY, snapshot.connection());
            assertEquals("未上报电量必须是结构化未知",
                    HsDevice.Battery.UNKNOWN_LEVEL, snapshot.battery().leftLevel());
            assertEquals("未上报设备信息时三项都未知",
                    HsDevice.Ear.UNKNOWN, snapshot.deviceInfo().earInCase());
            assertTrue("未上报过任何控制值时不出现键", snapshot.values().isEmpty());
            assertTrue(harness.session.isReady());
        } finally {
            harness.close();
        }
    }

    @Test
    public void batteryAndEarInCaseLandInSnapshot() throws Exception {
        Harness harness = new Harness();
        try {
            RecordingObserver observer = new RecordingObserver();
            harness.session.observe(observer);
            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            answerAutoQueries(harness);

            // 组合电量主动上报：左右耳与充电盒都是 80。
            harness.link.inbound("99EC800003" + "0150" + "0250" + "0350" + "1234");
            // 在仓状态主动上报：3 = 双耳都在仓。
            harness.link.inbound("99EC960001031234");

            assertTrue("电量变化必须推快照", observer.awaitSnapshotWithEarInCase(HsDevice.Ear.BOTH));
            HsDevice.Snapshot snapshot = harness.session.snapshot();
            assertEquals(80, snapshot.battery().leftLevel());
            assertEquals(80, snapshot.battery().rightLevel());
            assertEquals(80, snapshot.battery().caseLevel());
            assertEquals(HsDevice.Ear.BOTH, snapshot.deviceInfo().earInCase());
            assertEquals(EAR_COLOR_VALUE, snapshot.deviceInfo().earColor());
        } finally {
            harness.close();
        }
    }

    @Test
    public void writeRoutesThroughCapabilityRouterAndNormalizesSuccess() throws Exception {
        Harness harness = new Harness();
        try {
            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            answerAutoQueries(harness);

            RecordingCallback callback = new RecordingCallback();
            harness.session.write(HsDevice.Capability.EQ_MODE, new HsControl.Value.Choice("1"), callback);
            assertTrue("EQ 写入必须按能力路由下发", harness.link.awaitWritten(EQ_MODE_QUERY));
            harness.link.inbound(EQ_MODE_RESPONSE);

            assertTrue(callback.awaitResult());
            assertTrue("设备成功后回执是 Success", callback.result() instanceof HsControl.Outcome.Ok);
        } finally {
            harness.close();
        }
    }

    @Test
    public void writeOnUnsupportedCapabilityIsRejectedNotSilentlyDropped() throws Exception {
        Harness harness = new Harness();
        try {
            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            awaitReady(harness);

            RecordingCallback callback = new RecordingCallback();
            harness.session.write(HsDevice.Capability.OTA, new HsControl.Value.Toggle(true), callback);

            assertTrue(callback.awaitResult());
            assertEquals(HsControl.Failure.UNSUPPORTED_CAPABILITY, callback.failure());
        } finally {
            harness.close();
        }
    }

    @Test
    public void writeBeforeReadyReportsNotReady() throws Exception {
        Harness harness = new Harness();
        try {
            RecordingCallback callback = new RecordingCallback();
            harness.session.write(HsDevice.Capability.EQ_MODE, new HsControl.Value.Choice("1"), callback);

            assertTrue(callback.awaitResult());
            assertEquals(HsControl.Failure.NOT_READY, callback.failure());
        } finally {
            harness.close();
        }
    }

    @Test
    public void writeWithMismatchedValueKindThrows() throws Exception {
        Harness harness = new Harness();
        try {
            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            awaitReady(harness);

            assertThrows(IllegalArgumentException.class, () -> harness.session.write(
                    HsDevice.Capability.EQ_MODE, new HsControl.Value.Toggle(true), result -> {
                    }));
        } finally {
            harness.close();
        }
    }

    @Test
    public void readReturnsCachedValueAndUnknownValueIsNotReady() throws Exception {
        Harness harness = new Harness();
        try {
            RecordingObserver observer = new RecordingObserver();
            harness.session.observe(observer);
            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            answerAutoQueries(harness);
            assertTrue(observer.awaitConnection(HsControl.State.READY));

            assertEquals("设备还没上报 EQ 模式：不得编默认值",
                    HsControl.Failure.NOT_READY, failureOf(harness.session.read(HsDevice.Capability.EQ_MODE)));

            // 设备主动上报的 EQ 模式会落域，此后同步读必须给出值。
            harness.link.inbound("99EC830001021234");
            assertTrue("EQ 上报必须先进入快照",
                    observer.awaitSnapshotWithValue(HsDevice.Capability.EQ_MODE));
            HsControl.Outcome<HsControl.Value> afterEvent = harness.session.read(HsDevice.Capability.EQ_MODE);
            assertTrue(afterEvent instanceof HsControl.Outcome.Ok<HsControl.Value>);
            assertEquals(new HsControl.Value.Choice("2"),
                    ((HsControl.Outcome.Ok<HsControl.Value>) afterEvent).value());
        } finally {
            harness.close();
        }
    }

    @Test
    public void performReadPairedDevicesPublishesDualStateAndEvents() throws Exception {
        Harness harness = new Harness();
        try {
            RecordingObserver observer = new RecordingObserver();
            harness.session.observe(observer);
            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            answerAutoQueries(harness);

            RecordingCallback callback = new RecordingCallback();
            harness.session.perform(new HsControl.Action.ReadPairedDevices(), callback);

            // 调度器串行：先答已配对列表，聚合页那条才会下发。
            assertTrue("已配对列表查询必须下发", harness.link.awaitWritten(PAIRED_DEVICES_QUERY));
            harness.link.inbound(PAIRED_DEVICES_EMPTY_RESPONSE);
            assertTrue("双连接页聚合查询必须下发", harness.link.awaitWritten(DUAL_PAGE_QUERY));
            harness.link.inbound(DUAL_PAGE_RESPONSE);

            assertTrue(callback.awaitResult());
            assertTrue("两条查询都成功才算成功", callback.result() instanceof HsControl.Outcome.Ok);

            assertTrue("双连接状态变化必须以事件推出",
                    observer.awaitDeviceEvent(HsControl.Event.DualConnectionChanged.class));
            HsControl.Dual dual = dualOf(observer);
            assertNotNull("双连接状态必须落到事件里", dual);
            assertTrue(dual.dualEnabled());
            assertEquals("AA:BB:CC:DD:EE:FF", dual.priorityAddress());
            assertEquals(1, dual.devices().size());
            HsControl.Paired device = dual.devices().get(0);
            assertEquals("ZZ", device.name());
            assertEquals(HsControl.Paired.Kind.PHONE, device.type());
            assertTrue(device.connected());
            assertEquals(HsControl.Paired.Activity.IDLE, device.business());
            assertTrue(device.currentDevice());
            assertTrue(device.priority());

            assertEquals("双连接开关进入快照控制值",
                    new HsControl.Value.Toggle(true),
                    harness.session.snapshot().values().get(HsDevice.Capability.DUAL_CONNECTION));
        } finally {
            harness.close();
        }
    }

    @Test
    public void performReadRecordListPublishesRecordFilesEvent() throws Exception {
        Harness harness = new Harness();
        try {
            RecordingObserver observer = new RecordingObserver();
            harness.session.observe(observer);
            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            answerAutoQueries(harness);

            RecordingCallback callback = new RecordingCallback();
            harness.session.perform(new HsControl.Action.ReadRecordList(), callback);
            assertTrue(harness.link.awaitWritten(RECORD_LIST_QUERY));
            harness.link.inbound(RECORD_LIST_RESPONSE);

            assertTrue(callback.awaitResult());
            assertTrue(callback.result() instanceof HsControl.Outcome.Ok);
            assertTrue("录音列表必须以事件推出",
                    observer.awaitDeviceEvent(HsControl.Event.RecordFilesChanged.class));

            HsControl.Event.RecordFilesChanged changed =
                    (HsControl.Event.RecordFilesChanged) observer.lastEventOf(
                            HsControl.Event.RecordFilesChanged.class);
            assertEquals(1, changed.files().size());
            HsControl.Recording file = changed.files().get(0);
            assertEquals("3", file.fileId());
            assertEquals(16, file.durationSeconds());
            assertEquals(4096L, file.sizeBytes());
        } finally {
            harness.close();
        }
    }

    @Test
    public void commandAbortedBySessionSwitchReportsRejected() throws Exception {
        Harness harness = new Harness();
        try {
            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            answerAutoQueries(harness);

            RecordingCallback callback = new RecordingCallback();
            harness.session.write(HsDevice.Capability.EQ_MODE, new HsControl.Value.Choice("1"), callback);
            // 换目标：在途命令被引擎中止，回执必须是 REJECTED 而不是成功。
            harness.session.connect("HS02", new HsDevice.Radios("66:77:88:99:AA:BB", "", "E8:00:00:00:00:02", ""));

            assertTrue(callback.awaitResult());
            assertEquals(HsControl.Failure.REJECTED, callback.failure());
        } finally {
            harness.close();
        }
    }

    /**
     * READY 后的会话自愈：自动补查一次聚合状态，固件随快照落地；同一会话不重复下发。
     */
    @Test
    public void readyQueriesAggregatedStatusOnceAndFirmwareLandsInSnapshot() throws Exception {
        Harness harness = new Harness();
        try {
            RecordingObserver observer = new RecordingObserver();
            harness.session.observe(observer);
            harness.session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();

            assertTrue(harness.link.awaitWritten(EAR_COLOR_QUERY));
            harness.link.inbound(EAR_COLOR_RESPONSE);
            assertTrue("READY 后必须自动补查一次聚合状态",
                    harness.link.awaitWritten(HOME_STATUS_QUERY));
            harness.link.inbound(HOME_STATUS_RESPONSE);
            // 同一批自愈查询里的 LDAC 查询也要应答，队列才不会被超时顶住。
            assertTrue(harness.link.awaitWritten(LDAC_QUERY));
            harness.link.inbound(LDAC_ON_RESPONSE);

            assertTrue("固件必须随聚合回包进入快照",
                    observer.awaitFirmware(FIRMWARE_TEXT));
            assertEquals(FIRMWARE_TEXT, harness.session.snapshot().deviceInfo().firmware());

            // 同一会话重复 READY：不得再补查。用一条业务命令落地当屏障。
            harness.link.ready();
            RecordingCallback callback = new RecordingCallback();
            harness.session.write(HsDevice.Capability.EQ_MODE, new HsControl.Value.Choice("1"), callback);
            assertTrue(harness.link.awaitWritten(EQ_MODE_QUERY));
            assertEquals("每条会话只补查一次聚合状态", 1,
                    harness.link.countWrittenWithPrefix("99ECC7"));
            harness.link.inbound(EQ_MODE_RESPONSE);
            assertTrue(callback.awaitResult());
        } finally {
            harness.close();
        }
    }

    /** 能力集合跟随连接目标机型：HS01 不声明 CUSTOM_EQ，但 EQ_MODE 照常可写。 */
    @Test
    public void hs01DoesNotDeclareCustomEq() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady("HS01", MAC);

            RecordingCallback customEq = new RecordingCallback();
            harness.session.write(HsDevice.Capability.CUSTOM_EQ,
                    new HsControl.Value.Curve(neutralPoints()), customEq);
            assertTrue(customEq.awaitResult());
            assertEquals("HS01 不声明 CUSTOM_EQ",
                    HsControl.Failure.UNSUPPORTED_CAPABILITY, customEq.failure());

            RecordingCallback eqMode = new RecordingCallback();
            harness.session.write(HsDevice.Capability.EQ_MODE, new HsControl.Value.Choice("1"), eqMode);
            assertTrue("HS01 声明 EQ_MODE：必须真的下发",
                    harness.link.awaitWritten(EQ_MODE_QUERY));
            harness.link.inbound(EQ_MODE_RESPONSE);
            assertTrue(eqMode.awaitResult());
            assertTrue(eqMode.result() instanceof HsControl.Outcome.Ok);
        } finally {
            harness.close();
        }
    }

    /** HS02 声明 CUSTOM_EQ：同一能力可写并走协议通道。 */
    @Test
    public void hs02DeclaresCustomEq() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady(HS02_MODEL, MAC);

            RecordingCallback callback = new RecordingCallback();
            harness.session.write(HsDevice.Capability.CUSTOM_EQ,
                    new HsControl.Value.Curve(neutralPoints()), callback);
            String payload = customEqRealtimePayload(neutralGains());
            assertTrue("HS02 声明 CUSTOM_EQ：必须真的下发", harness.link.awaitWritten(payload));
            harness.link.inbound(payload);
            assertTrue(callback.awaitResult());
            assertTrue(callback.result() instanceof HsControl.Outcome.Ok);
        } finally {
            harness.close();
        }
    }
    /** 目录未登记机型：链路照常连接，但没有任何声明能力可写。 */
    @Test
    public void unknownModelConnectsWithoutDeclaringCapabilities() throws Exception {
        Harness harness = new Harness();
        try {
            harness.session.connect("HS99", new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            harness.link.ready();
            awaitReady(harness);
            answerAutoQueries(harness);
            assertTrue("未知机型仍必须建立链路", harness.session.isReady());

            RecordingCallback callback = new RecordingCallback();
            harness.session.write(HsDevice.Capability.EQ_MODE, new HsControl.Value.Choice("1"), callback);
            assertTrue(callback.awaitResult());
            assertEquals(HsControl.Failure.UNSUPPORTED_CAPABILITY, callback.failure());
            assertEquals("读同一能力同样不支持", HsControl.Failure.UNSUPPORTED_CAPABILITY,
                    failureOf(harness.session.read(HsDevice.Capability.EQ_MODE)));
        } finally {
            harness.close();
        }
    }

    /** 连接目标的必填校验：缺地址当场抛错，不进入引擎。 */
    @Test
    public void invalidTargetIsRejected() {
        Harness harness = new Harness();
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> harness.session.connect("HS02", new HsDevice.Radios("  ", "", "", "")));
            assertThrows(IllegalArgumentException.class,
                    () -> harness.session.connect("HS02", new HsDevice.Radios(null, "", "", "")));
            assertThrows(IllegalArgumentException.class,
                    () -> harness.session.connect("HS02", new HsDevice.Radios(MAC, "", "", "")));
            assertThrows(NullPointerException.class,
                    () -> harness.session.connect(null, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", "")));
            assertFalse("被拒绝的连接不得进入就绪态", harness.session.isReady());
        } finally {
            harness.close();
        }
    }

    @Test
    public void gattOpensOnlyAfterClassicAudioIsUp() {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        HsClassicRadio.Callback[] held = new HsClassicRadio.Callback[1];
        HsClassicRadio radio = new HsClassicRadio() {
            @Override
            public void open(String classicAddress, Callback callback) {
                held[0] = callback;
            }

            @Override
            public void cancel() {
                held[0] = null;
            }

            @Override
            public void release() {
            }
        };
        HsSessionImpl session = new HsSessionImpl(
                engine, new HsCatalogImpl(), new FakeMainThread(), radio);
        try {
            session.connect(HS02_MODEL, new HsDevice.Radios(MAC, "", "E8:00:00:00:00:01", ""));
            assertNull("经典音频未就绪时不得开 GATT", link.openedAddress());
            held[0].onReady();
            assertEquals(MAC, link.openedAddress());
        } finally {
            session.close();
        }
    }

    /** 重构前 app 已有的重置/重启入口：动作通道必须打到对应命令。 */
    @Test
    public void resetControlAndRebootReachTheirCommands() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady("HS01", MAC);

            RecordingCallback reset = new RecordingCallback();
            harness.session.perform(new HsControl.Action.ResetControl(), reset);
            assertTrue("重置触控必须下发", harness.link.awaitWritten(RESET_CONTROL_QUERY));
            harness.link.inbound(RESET_CONTROL_QUERY);
            assertTrue(reset.awaitResult());
            assertTrue(reset.result() instanceof HsControl.Outcome.Ok);

            RecordingCallback reboot = new RecordingCallback();
            harness.session.perform(new HsControl.Action.Reboot(), reboot);
            assertTrue("重启必须下发", harness.link.awaitWritten(REBOOT_QUERY));
            harness.link.inbound(REBOOT_QUERY);
            assertTrue(reboot.awaitResult());
            assertTrue(reboot.result() instanceof HsControl.Outcome.Ok);
        } finally {
            harness.close();
        }
    }

    /** 未登记机型不得下发动作：RESET_CONTROL 同样按目录判定。 */
    @Test
    public void unsupportedModelRejectsActions() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady("HS99", MAC);
            RecordingCallback reset = new RecordingCallback();
            harness.session.perform(new HsControl.Action.ResetControl(), reset);
            assertTrue(reset.awaitResult());
            assertEquals(HsControl.Failure.UNSUPPORTED_CAPABILITY, reset.failure());
        } finally {
            harness.close();
        }
    }

    /** LDAC 状态由查询回包落地：写入前读快照必须给出设备真实值。 */
    @Test
    public void ldacStateComesFromTheQueryResponse() throws Exception {
        Harness harness = new Harness();
        try {
            RecordingObserver observer = new RecordingObserver();
            harness.session.observe(observer);
            harness.connectReady(HS02_MODEL, MAC);
            assertTrue("LDAC 开关状态必须进入快照",
                    observer.awaitSnapshotWithValue(HsDevice.Capability.LDAC));
            assertEquals(new HsControl.Value.Toggle(true),
                    harness.session.snapshot().values().get(HsDevice.Capability.LDAC));
            assertEquals(new HsControl.Outcome.Ok<>(new HsControl.Value.Toggle(true)),
                    harness.session.read(HsDevice.Capability.LDAC));
        } finally {
            harness.close();
        }
    }

    /** 新手模式开关：进入/退出成功即状态，read 与快照都反映。 */
    @Test
    public void beginnerModeToggleReflectsCommandAck() throws Exception {
        Harness harness = new Harness();
        try {
            RecordingObserver observer = new RecordingObserver();
            harness.session.observe(observer);
            harness.connectReady(HS02_MODEL, MAC);

            assertEquals("未进入过新手模式时不得编默认值", HsControl.Failure.NOT_READY,
                    failureOf(harness.session.read(HsDevice.Capability.BEGINNER_MODE)));

            RecordingCallback enter = new RecordingCallback();
            harness.session.write(HsDevice.Capability.BEGINNER_MODE,
                    new HsControl.Value.Toggle(true), enter);
            assertTrue("进入新手模式必须下发 CC", harness.link.awaitWritten(BEGINNER_MODE_ENTER));
            harness.link.inbound(BEGINNER_MODE_ENTER);
            assertTrue(enter.awaitResult());
            assertTrue(enter.result() instanceof HsControl.Outcome.Ok);

            assertTrue("新手模式开关必须进入快照",
                    observer.awaitSnapshotWithValue(HsDevice.Capability.BEGINNER_MODE));
            assertEquals("读必须反映进入",
                    new HsControl.Outcome.Ok<>(new HsControl.Value.Toggle(true)),
                    harness.session.read(HsDevice.Capability.BEGINNER_MODE));
            assertEquals(new HsControl.Value.Toggle(true),
                    harness.session.snapshot().values().get(HsDevice.Capability.BEGINNER_MODE));

            RecordingCallback exit = new RecordingCallback();
            harness.session.write(HsDevice.Capability.BEGINNER_MODE,
                    new HsControl.Value.Toggle(false), exit);
            assertTrue("退出新手模式必须下发 CD", harness.link.awaitWritten(BEGINNER_MODE_EXIT));
            harness.link.inbound(BEGINNER_MODE_EXIT);
            assertTrue(exit.awaitResult());
            assertTrue(exit.result() instanceof HsControl.Outcome.Ok);

            assertEquals("读必须反映退出",
                    new HsControl.Outcome.Ok<>(new HsControl.Value.Toggle(false)),
                    harness.session.read(HsDevice.Capability.BEGINNER_MODE));
        } finally {
            harness.close();
        }
    }

    /** 门控能力不得出现在快照控制值里：展示由 app 既有界面承担，biz 不给第二份状态。 */
    @Test
    public void gateCapabilitiesNeverAppearInSnapshotValues() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady(HS02_MODEL, MAC);
            HsDevice.Snapshot snapshot = harness.session.snapshot();
            for (HsDevice.Capability capability : new HsDevice.Capability[]{
                    HsDevice.Capability.CONNECTION, HsDevice.Capability.BATTERY,
                    HsDevice.Capability.DEVICE_INFO, HsDevice.Capability.SERVICE_STATUS,
                    HsDevice.Capability.AI_ASSISTANT, HsDevice.Capability.OTA,
                    HsDevice.Capability.OFFLINE_LOG, HsDevice.Capability.CLASSIC_BT}) {
                assertFalse("GATE 不得进入快照控制值: " + capability,
                        snapshot.values().containsKey(capability));
            }
        } finally {
            harness.close();
        }
    }

    /** 已配对设备列表是 HS02 的能力：HS01 上该动作必须被目录判定挡掉。 */
    @Test
    public void hs01RejectsPairedDeviceReads() throws Exception {
        Harness harness = new Harness();
        try {
            harness.connectReady("HS01", MAC);
            RecordingCallback callback = new RecordingCallback();
            harness.session.perform(new HsControl.Action.ReadPairedDevices(), callback);
            assertTrue(callback.awaitResult());
            assertEquals(HsControl.Failure.UNSUPPORTED_CAPABILITY, callback.failure());
        } finally {
            harness.close();
        }
    }

    private static int[] neutralGains() {
        int[] gains = new int[CustomEQRealtimeCommand.BAND_COUNT];
        for (int index = 0; index < gains.length; index++) {
            gains[index] = CustomEQRealtimeCommand.GAIN_NEUTRAL;
        }
        return gains;
    }

    private static List<Integer> neutralPoints() {
        List<Integer> points = new ArrayList<>();
        for (int gain : neutralGains()) {
            points.add(gain);
        }
        return points;
    }

    /** {@code CustomEQRealtimeCommand} 的回显帧：设备原样回显请求。 */
    private static String customEqRealtimePayload(int[] gains) {
        StringBuilder builder = new StringBuilder("99ECB0000A");
        for (int gain : gains) {
            builder.append(String.format("%02X", gain));
        }
        return builder.append("1234").toString();
    }

    /** 应答 READY 后的两条自动查询：耳机颜色与首页聚合状态。 */    private static void answerAutoQueries(Harness harness) throws InterruptedException {
        if (harness.link.awaitWrittenWithin(EAR_COLOR_QUERY, 3_000L)) {
            harness.link.inbound(EAR_COLOR_RESPONSE);
        }
        if (harness.link.awaitWrittenWithin(HOME_STATUS_QUERY, 3_000L)) {
            harness.link.inbound(HOME_STATUS_RESPONSE);
        }
        if (harness.link.awaitWrittenWithin(LDAC_QUERY, 3_000L)) {
            harness.link.inbound(LDAC_ON_RESPONSE);
        }
    }

    /** 等会话真正 READY：link.ready() 之后引擎还要在自己的轮次里建立会话。 */
    private static void awaitReady(Harness harness) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        harness.session.observe(new HsControl.Listener() {
            @Override
            public void onConnectionChanged(HsControl.State state, HsControl.Failure failure) {
                if (state == HsControl.State.READY) {
                    ready.countDown();
                }
            }
        });
        assertTrue("会话未在预算内就绪", ready.await(3, TimeUnit.SECONDS));
    }

    private static HsControl.Failure failureOf(HsControl.Outcome<?> result) {
        assertTrue("期望失败回执: " + result, result instanceof HsControl.Outcome.Err<?>);
        return ((HsControl.Outcome.Err<?>) result).failure();
    }

    private static HsControl.Dual dualOf(RecordingObserver observer) {
        HsControl.Event event = observer.lastEventOf(HsControl.Event.DualConnectionChanged.class);
        return event == null ? null : ((HsControl.Event.DualConnectionChanged) event).state();
    }

    /** 会话 + 假链路 + 就地执行的主线程。 */
    private static final class Harness {
        private final FakeHsCommandLink link = new FakeHsCommandLink();
        private final HsProtocolEngine engine = new HsProtocolEngine(link);
        private final HsSessionImpl session =
                new HsSessionImpl(engine, new HsCatalogImpl(), new FakeMainThread(), new ReadyClassicRadio());

        /** 连接 + 等 READY + 应答两条自动查询。 */
        private void connectReady(String modelId, String bleAddress) throws Exception {
            session.connect(modelId, new HsDevice.Radios(bleAddress, "", "E8:00:00:00:00:01", ""));
            link.ready();
            awaitReady(this);
            answerAutoQueries(this);
        }

        private void close() {
            session.close();
        }
    }

    /** 记录连接态、快照与设备事件。 */
    private static final class RecordingObserver implements HsControl.Listener {
        private static final long AWAIT_TIMEOUT_MILLIS = 3_000L;

        private final Object lock = new Object();
        private final List<HsControl.State> connections = new ArrayList<>();
        private final List<HsDevice.Snapshot> snapshots = new ArrayList<>();
        private final List<HsControl.Event> events = new CopyOnWriteArrayList<>();

        @Override
        public void onConnectionChanged(HsControl.State state, HsControl.Failure failure) {
            synchronized (lock) {
                connections.add(state);
                lock.notifyAll();
            }
        }

        @Override
        public void onSnapshotChanged(HsDevice.Snapshot snapshot) {
            synchronized (lock) {
                snapshots.add(snapshot);
                lock.notifyAll();
            }
        }

        @Override
        public void onDeviceEvent(HsControl.Event event) {
            synchronized (lock) {
                events.add(event);
                lock.notifyAll();
            }
        }

        HsControl.State lastConnection() {
            synchronized (lock) {
                return connections.isEmpty() ? null : connections.get(connections.size() - 1);
            }
        }

        boolean awaitConnection(HsControl.State expected) throws InterruptedException {
            long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
            synchronized (lock) {
                while (!connections.contains(expected)) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        return false;
                    }
                    lock.wait(remaining);
                }
                return true;
            }
        }

        boolean awaitSnapshotWithEarInCase(HsDevice.Ear expected) throws InterruptedException {
            long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
            synchronized (lock) {
                while (true) {
                    for (HsDevice.Snapshot snapshot : snapshots) {
                        if (snapshot.deviceInfo().earInCase() == expected) {
                            return true;
                        }
                    }
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        return false;
                    }
                    lock.wait(remaining);
                }
            }
        }

        boolean awaitSnapshotWithValue(HsDevice.Capability capability) throws InterruptedException {
            long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
            synchronized (lock) {
                while (true) {
                    for (HsDevice.Snapshot snapshot : snapshots) {
                        if (snapshot.values().containsKey(capability)) {
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

        boolean awaitFirmware(String expected) throws InterruptedException {
            long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
            synchronized (lock) {
                while (true) {
                    for (HsDevice.Snapshot snapshot : snapshots) {
                        if (expected.equals(snapshot.deviceInfo().firmware())) {
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

        boolean awaitDeviceEvent(Class<? extends HsControl.Event> type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
            synchronized (lock) {
                while (lastEventOf(type) == null) {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining <= 0L) {
                        return false;
                    }
                    lock.wait(remaining);
                }
                return true;
            }
        }

        HsControl.Event lastEventOf(Class<? extends HsControl.Event> type) {
            synchronized (lock) {
                HsControl.Event found = null;
                for (HsControl.Event event : events) {
                    if (type.isInstance(event)) {
                        found = event;
                    }
                }
                return found;
            }
        }
    }

    /** 记录写入/动作回执。 */
    private static final class RecordingCallback implements HsControl.Callback {
        private static final long AWAIT_TIMEOUT_MILLIS = 3_000L;

        private final Object lock = new Object();
        private final CountDownLatch done = new CountDownLatch(1);
        private volatile HsControl.Outcome<Void> result;

        @Override
        public void onResult(HsControl.Outcome<Void> value) {
            synchronized (lock) {
                result = value;
                lock.notifyAll();
            }
            done.countDown();
        }

        boolean awaitResult() throws InterruptedException {
            return done.await(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }

        HsControl.Outcome<Void> result() {
            return result;
        }

        HsControl.Failure failure() {
            HsControl.Outcome<Void> current = result;
            assertNotNull("回执尚未到达", current);
            assertTrue("期望失败回执: " + current, current instanceof HsControl.Outcome.Err<?>);
            return ((HsControl.Outcome.Err<Void>) current).failure();
        }
    }
}
