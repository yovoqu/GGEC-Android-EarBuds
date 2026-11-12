package com.yovo.biz_ggec_hs.api;


import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsScan;
import com.yovo.biz_ggec_hs.api.HsControl;
import com.yovo.biz_ggec_hs.YoHsSdk;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.Optional;

/**
 * 契约形状：api 包只提供类型与常量，能力/动作/事件/失败分类的取值集合与设计一致。
 *
 * <p>这些断言是给实现方的护栏：api 增删成员会在这里先亮红灯，而不是等到 app 编译失败。</p>
 */
public final class HsApiContractTest {

    @Test
    public void controlValuesCoverEveryControlShape() {
        List<HsControl.Value> values = List.of(
                new HsControl.Value.Toggle(true),
                new HsControl.Value.Level(42),
                new HsControl.Value.Choice("option"),
                new HsControl.Value.Curve(List.of(1, 2, 3)));
        assertEquals(4, values.size());
        assertTrue(values.get(0) instanceof HsControl.Value.Toggle);
        assertEquals(42, ((HsControl.Value.Level) values.get(1)).value());
        assertEquals("option", ((HsControl.Value.Choice) values.get(2)).optionId());
        assertEquals(List.of(1, 2, 3), ((HsControl.Value.Curve) values.get(3)).points());
    }

    @Test
    public void resultCarriesValueOrTypedFailure() {
        HsControl.Outcome<String> success = new HsControl.Outcome.Ok<>("ok");
        HsControl.Outcome<String> failure = new HsControl.Outcome.Err<>(HsControl.Failure.TIMEOUT);
        assertEquals("ok", ((HsControl.Outcome.Ok<String>) success).value());
        assertEquals(HsControl.Failure.TIMEOUT, ((HsControl.Outcome.Err<String>) failure).failure());
    }

    @Test
    public void actionsShareOneRecordMode() {
        HsControl.Action start = new HsControl.Action.StartRecord(HsControl.Action.RecordMode.LOCAL);
        HsControl.Action stop = new HsControl.Action.StopRecord(HsControl.Action.RecordMode.STREAM);
        assertEquals(HsControl.Action.RecordMode.LOCAL, ((HsControl.Action.StartRecord) start).mode());
        assertEquals(HsControl.Action.RecordMode.STREAM, ((HsControl.Action.StopRecord) stop).mode());
        assertEquals(HsControl.Action.FindEar.Side.BOTH,
                new HsControl.Action.FindEar(HsControl.Action.FindEar.Side.BOTH).side());
        assertTrue(new HsControl.Action.ResetControl() instanceof HsControl.Action);
    }

    @Test
    public void eventsCoverEveryReportedChannel() {
        List<HsControl.Event> events = List.of(
                new HsControl.Event.PairedDevicesChanged(List.of()),
                new HsControl.Event.DualConnectionChanged(
                        new HsControl.Dual(true, "", List.of())),
                new HsControl.Event.RecordFilesChanged(List.of()),
                new HsControl.Event.DeviceInfoChanged(
                        new HsDevice.Info("V1", 1, HsDevice.Ear.BOTH)),
                new HsControl.Event.ServiceStatusChanged(java.util.Map.of()),
                new HsControl.Event.RemoteStreamStop(),
                new HsControl.Event.FindAlertChanged(0, 3),
                new HsControl.Event.BeginnerModeChanged(1, 2),
                new HsControl.Event.EarExchangeChanged(1),
                new HsControl.Event.VoiceAssistantChanged(0, 1));
        assertEquals(10, events.size());
    }

    @Test
    public void earInCaseIsASemanticEnumNotProtocolNumbers() {
        assertEquals(5, HsDevice.Ear.values().length);
        assertEquals(HsDevice.Ear.UNKNOWN, HsDevice.Ear.valueOf("UNKNOWN"));
        assertEquals(HsDevice.Ear.BOTH, HsDevice.Ear.valueOf("BOTH"));
        assertNotEquals(HsDevice.Ear.UNKNOWN, HsDevice.Ear.NONE);
    }

    @Test
    public void deviceInfoExpressesUnknownWithDedicatedMarkers() {
        HsDevice.Info unknown = new HsDevice.Info(HsDevice.Info.UNKNOWN_FIRMWARE,
                HsDevice.Info.UNKNOWN_EAR_COLOR, HsDevice.Ear.UNKNOWN);
        assertTrue(unknown.firmware().isEmpty());
        assertEquals(-1, unknown.earColor());
        assertEquals(HsDevice.Ear.UNKNOWN, unknown.earInCase());
        assertEquals("协议真实颜色都是非负数", true, unknown.earColor() < 0);
    }

    @Test
    public void batteryKeepsAStructuredUnknownLevel() {
        assertEquals(-1, HsDevice.Battery.UNKNOWN_LEVEL);
        HsDevice.Battery battery = new HsDevice.Battery(HsDevice.Battery.UNKNOWN_LEVEL, 80, 100, true, 3);
        assertEquals(HsDevice.Battery.UNKNOWN_LEVEL, battery.leftLevel());
        assertTrue(battery.caseCharging());
        assertEquals(3, battery.earInCaseStatus());
    }

    @Test
    public void descriptorCarriesOptionalRange() {
        HsDevice.Feature level = new HsDevice.Feature(HsDevice.Capability.BATTERY,
                "电量", HsDevice.Group.ABOUT, HsDevice.Form.LEVEL, List.of(),
                Optional.of(new HsDevice.Range(0, 100, 1)));
        assertEquals(Optional.of(new HsDevice.Range(0, 100, 1)), level.range());

        HsDevice.Feature toggle = new HsDevice.Feature(HsDevice.Capability.GAME_MODE,
                "游戏模式", HsDevice.Group.CONTROL, HsDevice.Form.TOGGLE, List.of(),
                Optional.empty());
        assertTrue(toggle.range().isEmpty());
        assertFalse(toggle.kind() == HsDevice.Form.LEVEL);
    }

    @Test
    public void capabilityVocabularyIsClosed() {
        assertEquals(35, HsDevice.Capability.values().length);
        assertEquals(6, HsDevice.Group.values().length);
        assertEquals(7, HsDevice.Form.values().length);
        assertEquals(16, HsControl.Failure.values().length);
        assertEquals(4, HsControl.State.values().length);
    }

    @Test
    public void taxonomyTypesDescribeIdentity() {
        assertEquals(3, HsDevice.Type.values().length);
        assertEquals(HsDevice.Type.EARBUDS, HsDevice.Type.valueOf("EARBUDS"));
        assertEquals(new HsDevice.Project("HS", "HS 系列"), new HsDevice.Project("HS", "HS 系列"));
        HsDevice.Product model = new HsDevice.Product("HS01", "HS01", "HS",
                HsDevice.Type.EARBUDS, Set.of(HsDevice.Capability.BATTERY));
        assertEquals("HS", model.projectId());
        assertEquals(HsDevice.Type.EARBUDS, model.deviceType());
    }

    @Test
    public void filterRuleDefaultsToUnrestricted() {
        HsScan.Rule none = HsScan.Rule.none();
        assertTrue(none.deviceTypes().isEmpty());
        assertTrue(none.projectIds().isEmpty());
        assertTrue(none.modelIds().isEmpty());
        assertFalse(none.onlyConnectable());
        assertEquals(HsScan.Rule.MIN_RSSI_UNRESTRICTED, none.minRssi());
    }

    @Test
    public void viewAndHealthCarryTheFacets() {
        HsScan.View view = new HsScan.View(
                List.of(new HsScan.Found("HS01", new HsDevice.Radios("11:22:33:44:55:66", "buds", "", ""), -50)),
                java.util.Map.of(HsDevice.Type.EARBUDS, 1),
                java.util.Map.of("HS", 1),
                java.util.Map.of("HS01", 1),
                1, true);
        assertEquals(1, view.totalInRange());
        assertEquals(Integer.valueOf(1), view.countsByType().get(HsDevice.Type.EARBUDS));

        HsScan.Health health = new HsScan.Health(true, false, true, 123L);
        assertTrue(health.scanning());
        assertFalse(health.silent());
        assertTrue(health.throttled());
        assertEquals(123L, health.lastFrameAtMillis());
    }

    @Test
    public void sdkConnectsByModelAndAddress() throws Exception {
        java.lang.reflect.Method connect = YoHsSdk.class.getDeclaredMethod(
                "connect", String.class, HsDevice.Radios.class);
        assertEquals(String.class, connect.getParameterTypes()[0]);
        assertEquals(HsDevice.Radios.class, connect.getParameterTypes()[1]);
    }

    @Test
    public void nearbyDeviceAndPairedDeviceExposeSemanticFields() {
        HsScan.Found nearby = new HsScan.Found("HS02",
                new HsDevice.Radios("11:22:33:44:55:66", "Vidda V11", "AA:BB:CC:DD:EE:FF", ""),
                -48);
        assertEquals("HS02", nearby.modelId());
        assertEquals("Vidda V11", nearby.radios().bleName());
        assertEquals("11:22:33:44:55:66", nearby.radios().bleAddress());
        assertEquals("AA:BB:CC:DD:EE:FF", nearby.radios().classicAddress());
        assertEquals("", nearby.radios().classicName());
        assertEquals(-48, nearby.rssi());

        HsControl.Paired paired = new HsControl.Paired("Phone", "AA:BB:CC:DD:EE:FF",
                HsControl.Paired.Kind.PHONE, true,
                HsControl.Paired.Activity.MUSIC, true, false);
        assertEquals(HsControl.Paired.Kind.PHONE, paired.type());
        assertEquals(HsControl.Paired.Activity.MUSIC, paired.business());
        assertTrue(paired.connected());
        assertTrue(paired.currentDevice());
        assertFalse(paired.priority());
    }
}
