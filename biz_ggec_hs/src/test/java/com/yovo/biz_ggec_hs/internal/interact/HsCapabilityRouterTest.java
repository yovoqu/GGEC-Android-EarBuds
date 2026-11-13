package com.yovo.biz_ggec_hs.internal.interact;

import com.yovo.biz_ggec_hs.internal.center.catalog.HsCatalogImpl;


import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsControl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolDual;
import com.yovo.biz_ggec_hs.internal.center.protocol.ProtocolRecording;
import com.yovo.biz_ggec_hs.internal.center.protocol.Command;
import com.yovo.biz_ggec_hs.internal.center.protocol.CommandCompletion;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.DoublePressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.LongPressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ResetControlCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ShortPressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.ClearPairingCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.DeviceRebootCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.DeviceRenameCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetRecordIdListCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.ConnectPhoneCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.DeleteParingDeviceCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.DisconnectPhoneCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.GetDualConnectionPageCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.GetPairedDevicesCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.SetDualConnectionCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.dualconnectioncmd.SetPriorityEquipmentCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.CancelTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StartTransmitRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.StopLocalRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FallAlertCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertLeftCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertRightCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.FindAlertStopCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.GameModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.VoiceWakeupCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.WearDetectionCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.inbound.DeviceDomainStore;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQRealtimeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.EQModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.LDACCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.SoundQualityModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.VolumeAdaptiveCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.VolumeRemindCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.SimultaneousInterpretationCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StartStreamRecordCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.StopStreamRecordCommand;

import org.junit.Test;

import java.util.List;
import java.util.Optional;

/**
 * 能力/动作到协议命令的唯一映射：写入、动作、状态投影各自一条通路，没有第二处判断。
 *
 * <p>断言用「等价命令的载荷」而不是字面量协议串，映射改了就编译期可见。</p>
 */
public final class HsCapabilityRouterTest {

    private final HsCapabilityRouter router = new HsCapabilityRouter();
    private final DeviceDomainStore domain = new DeviceDomainStore();

    @Test
    public void writesMapToTheirProtocolCommands() {
        assertEquals(new EQModeCommand(1).getCommandData(),
                router.write("HS02", HsDevice.Capability.EQ_MODE, new HsControl.Value.Choice("1")).getCommandData());
        assertEquals(new CustomEQRealtimeCommand(neutralGains()).getCommandData(),
                router.write("HS02", HsDevice.Capability.CUSTOM_EQ, new HsControl.Value.Curve(neutralPoints()))
                        .getCommandData());
        assertEquals(new SoundQualityModeCommand(true).getCommandData(),
                router.write("HS02", HsDevice.Capability.SOUND_QUALITY,
                        new HsControl.Value.Choice(HsCapabilityRouter.SOUND_QUALITY_HIGH))
                        .getCommandData());
        assertEquals(new SoundQualityModeCommand(false).getCommandData(),
                router.write("HS02", HsDevice.Capability.SOUND_QUALITY,
                        new HsControl.Value.Choice(HsCapabilityRouter.SOUND_QUALITY_ENDURANCE))
                        .getCommandData());
        assertEquals(new GameModeCommand(true).getCommandData(),
                router.write("HS02", HsDevice.Capability.GAME_MODE, new HsControl.Value.Toggle(true)).getCommandData());
        assertEquals(new WearDetectionCommand(false).getCommandData(),
                router.write("HS02", HsDevice.Capability.WEAR_DETECT, new HsControl.Value.Toggle(false))
                        .getCommandData());
        assertEquals(new VoiceWakeupCommand(true).getCommandData(),
                router.write("HS02", HsDevice.Capability.VOICE_WAKEUP, new HsControl.Value.Toggle(true))
                        .getCommandData());
        assertEquals(new FallAlertCommand(true).getCommandData(),
                router.write("HS02", HsDevice.Capability.FALL_ALERT, new HsControl.Value.Toggle(true))
                        .getCommandData());
        assertEquals(new VolumeRemindCommand(false).getCommandData(),
                router.write("HS02", HsDevice.Capability.VOLUME_REMIND, new HsControl.Value.Toggle(false))
                        .getCommandData());
        assertEquals(new SimultaneousInterpretationCommand(true).getCommandData(),
                router.write("HS02", HsDevice.Capability.INTERPRET, new HsControl.Value.Toggle(true))
                        .getCommandData());
        assertEquals(new SetDualConnectionCommand(true).getCommandData(),
                router.write("HS02", HsDevice.Capability.DUAL_CONNECTION, new HsControl.Value.Toggle(true))
                        .getCommandData());
        assertEquals(new LDACCommand(true).getCommandData(),
                router.write("HS02", HsDevice.Capability.LDAC, new HsControl.Value.Toggle(true)).getCommandData());
        assertEquals(new LDACCommand(false).getCommandData(),
                router.write("HS02", HsDevice.Capability.LDAC, new HsControl.Value.Toggle(false)).getCommandData());
        assertEquals(new VolumeAdaptiveCommand(true).getCommandData(),
                router.write("HS02", HsDevice.Capability.VOLUME_ADAPTIVE, new HsControl.Value.Toggle(true))
                        .getCommandData());
    }

    @Test
    public void touchOptionsDecodeEarAndAction() {
        String longOption = HsCapabilityRouter.pressOptionId(2, 3);
        assertEquals("2:3", longOption);
        assertEquals(new LongPressSetCommand(3, 2).getCommandData(),
                router.write("HS02", HsDevice.Capability.TOUCH_LONG, new HsControl.Value.Choice(longOption))
                        .getCommandData());
        assertEquals(new ShortPressSetCommand(5, 1).getCommandData(),
                router.write("HS02", HsDevice.Capability.TOUCH_SHORT,
                        new HsControl.Value.Choice(HsCapabilityRouter.pressOptionId(1, 5)))
                        .getCommandData());
        assertEquals(new DoublePressSetCommand(8, 0).getCommandData(),
                router.write("HS02", HsDevice.Capability.TOUCH_DOUBLE,
                        new HsControl.Value.Choice(HsCapabilityRouter.pressOptionId(0, 8)))
                        .getCommandData());

        String tapOption = HsCapabilityRouter.tapOptionId(1, ControlCommand.TAP_TWICE, 4);
        assertEquals(new ControlCommand(1, ControlCommand.TAP_TWICE, 4).getCommandData(),
                router.write("HS02", HsDevice.Capability.TOUCH_TAP, new HsControl.Value.Choice(tapOption))
                        .getCommandData());
    }

    @Test
    public void malformedOptionIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> router.write("HS02", 
                HsDevice.Capability.TOUCH_LONG, new HsControl.Value.Choice("abc")));
        assertThrows(IllegalArgumentException.class, () -> router.write("HS02", 
                HsDevice.Capability.EQ_MODE, new HsControl.Value.Choice("music")));
        assertThrows(IllegalArgumentException.class, () -> router.write("HS02", 
                HsDevice.Capability.SOUND_QUALITY, new HsControl.Value.Choice("unknown")));
        assertThrows(IllegalArgumentException.class, () -> router.write("HS02", 
                HsDevice.Capability.CUSTOM_EQ, new HsControl.Value.Curve(List.of(0, 0))));
    }

    @Test
    public void valueKindMismatchIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> router.write("HS02", 
                HsDevice.Capability.EQ_MODE, new HsControl.Value.Toggle(true)));
        assertThrows(IllegalArgumentException.class, () -> router.write("HS02", 
                HsDevice.Capability.GAME_MODE, new HsControl.Value.Level(3)));
    }

    @Test
    public void nonWritableCapabilitiesAreRejected() {
        for (HsDevice.Capability capability : new HsDevice.Capability[]{
                HsDevice.Capability.CONNECTION, HsDevice.Capability.BATTERY, HsDevice.Capability.FIND_EAR,
                HsDevice.Capability.DEVICE_INFO, HsDevice.Capability.RENAME, HsDevice.Capability.CLEAR_PAIRING,
                HsDevice.Capability.RECORD_LIST, HsDevice.Capability.OTA, HsDevice.Capability.CLASSIC_BT}) {
            assertFalse("不应可写: " + capability, HsCapabilityRouter.supportsWrite(capability));
            assertThrows("必须拒绝写入: " + capability, IllegalArgumentException.class,
                    () -> router.write("HS02", capability, new HsControl.Value.Toggle(true)));
        }
    }

    @Test
    public void actionsMapToTheirCommandLists() {
        assertEquals(List.of(new FindAlertLeftCommand(true).getCommandData()),
                payloads(new HsControl.Action.FindEar(HsControl.Action.FindEar.Side.LEFT)));
        assertEquals(List.of(new FindAlertRightCommand(true).getCommandData()),
                payloads(new HsControl.Action.FindEar(HsControl.Action.FindEar.Side.RIGHT)));
        assertEquals(
                List.of(new FindAlertLeftCommand(true).getCommandData(),
                        new FindAlertRightCommand(true).getCommandData()),
                payloads(new HsControl.Action.FindEar(HsControl.Action.FindEar.Side.BOTH)));
        assertEquals(List.of(new FindAlertStopCommand().getCommandData()),
                payloads(new HsControl.Action.StopFindAlert()));
        assertEquals(List.of(new DeviceRebootCommand().getCommandData()),
                payloads(new HsControl.Action.Reboot()));
        assertEquals(List.of(new DeviceRenameCommand("Earbuds").getCommandData()),
                payloads(new HsControl.Action.Rename("Earbuds")));
        assertEquals(List.of(new ClearPairingCommand().getCommandData()),
                payloads(new HsControl.Action.ClearPairing()));
        assertEquals(List.of(new ResetControlCommand().getCommandData()),
                payloads(new HsControl.Action.ResetControl()));
        assertEquals(List.of(new StartLocalRecordCommand().getCommandData()),
                payloads(new HsControl.Action.StartRecord(HsControl.Action.RecordMode.LOCAL)));
        assertEquals(List.of(new StartStreamRecordCommand().getCommandData()),
                payloads(new HsControl.Action.StartRecord(HsControl.Action.RecordMode.STREAM)));
        assertEquals(List.of(new StopLocalRecordCommand().getCommandData()),
                payloads(new HsControl.Action.StopRecord(HsControl.Action.RecordMode.LOCAL)));
        assertEquals(List.of(new StopStreamRecordCommand().getCommandData()),
                payloads(new HsControl.Action.StopRecord(HsControl.Action.RecordMode.STREAM)));
        assertEquals(List.of(new GetRecordIdListCommand(0, 12).getCommandData()),
                payloads(new HsControl.Action.ReadRecordList()));
        assertEquals(List.of(new StartTransmitRecordCommand(7).getCommandData()),
                payloads(new HsControl.Action.TransferRecord("7", true)));
        assertEquals(List.of(new CancelTransmitRecordCommand().getCommandData()),
                payloads(new HsControl.Action.TransferRecord("7", false)));
        assertEquals(
                List.of(new GetPairedDevicesCommand().getCommandData(),
                        new GetDualConnectionPageCommand().getCommandData()),
                payloads(new HsControl.Action.ReadPairedDevices()));
        assertEquals(List.of(new DeleteParingDeviceCommand("AA:BB:CC:DD:EE:FF").getCommandData()),
                payloads(new HsControl.Action.ForgetPairedDevice("AA:BB:CC:DD:EE:FF")));
        assertEquals(List.of(new SetPriorityEquipmentCommand("AA:BB:CC:DD:EE:FF").getCommandData()),
                payloads(new HsControl.Action.SetPriorityPairedDevice("AA:BB:CC:DD:EE:FF")));
        assertEquals(List.of(new ConnectPhoneCommand("AA:BB:CC:DD:EE:FF").getCommandData()),
                payloads(new HsControl.Action.ConnectPairedDevice("AA:BB:CC:DD:EE:FF")));
        assertEquals(List.of(new DisconnectPhoneCommand("AA:BB:CC:DD:EE:FF").getCommandData()),
                payloads(new HsControl.Action.DisconnectPairedDevice("AA:BB:CC:DD:EE:FF")));
    }

    @Test
    public void actionsDeclareTheCapabilityTheyBelongTo() {
        assertEquals(HsDevice.Capability.FIND_EAR,
                router.capabilityOf(new HsControl.Action.FindEar(HsControl.Action.FindEar.Side.BOTH)));
        assertEquals(HsDevice.Capability.RECORD_LOCAL,
                router.capabilityOf(new HsControl.Action.StartRecord(HsControl.Action.RecordMode.LOCAL)));
        assertEquals(HsDevice.Capability.RECORD_STREAM,
                router.capabilityOf(new HsControl.Action.StopRecord(HsControl.Action.RecordMode.STREAM)));
        assertEquals("设备列表动作挂在 DUAL_CONNECTION_DEVICES 上",
                HsDevice.Capability.DUAL_CONNECTION_DEVICES,
                router.capabilityOf(new HsControl.Action.ReadPairedDevices()));
        assertEquals(HsDevice.Capability.DUAL_CONNECTION_DEVICES,
                router.capabilityOf(new HsControl.Action.ForgetPairedDevice("AA:BB:CC:DD:EE:FF")));
        assertEquals(HsDevice.Capability.DUAL_CONNECTION_DEVICES,
                router.capabilityOf(new HsControl.Action.SetPriorityPairedDevice("AA:BB:CC:DD:EE:FF")));
        assertEquals(HsDevice.Capability.DUAL_CONNECTION_DEVICES,
                router.capabilityOf(new HsControl.Action.ConnectPairedDevice("AA:BB:CC:DD:EE:FF")));
        assertEquals(HsDevice.Capability.DUAL_CONNECTION_DEVICES,
                router.capabilityOf(new HsControl.Action.DisconnectPairedDevice("AA:BB:CC:DD:EE:FF")));
        assertEquals(HsDevice.Capability.REBOOT, router.capabilityOf(new HsControl.Action.Reboot()));
        assertEquals(HsDevice.Capability.RESET_CONTROL,
                router.capabilityOf(new HsControl.Action.ResetControl()));
    }

    @Test
    public void malformedActionArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> router.commands(new HsControl.Action.Rename("   ")));
        assertThrows(IllegalArgumentException.class,
                () -> router.commands(new HsControl.Action.ForgetPairedDevice("")));
        assertThrows(IllegalArgumentException.class,
                () -> router.commands(new HsControl.Action.TransferRecord("file-a", true)));
    }

    @Test
    public void valuesComeOnlyFromKnownState() {
        HsControl.Dual dual =
                new HsControl.Dual(true, "AA:BB:CC:DD:EE:FF", List.of());
        assertEquals(Optional.empty(),
                Optional.ofNullable(router.value(HsDevice.Capability.EQ_MODE, domain, dual)));
        assertEquals(new HsControl.Value.Toggle(true),
                router.value(HsDevice.Capability.DUAL_CONNECTION, domain, dual));
        assertEquals("设备未上报过 EQ 时不得编默认值", null,
                router.value(HsDevice.Capability.EQ_MODE, domain, null));
        assertEquals("无状态来源的能力不出现在快照", null,
                router.value(HsDevice.Capability.SOUND_QUALITY, domain, dual));
        assertEquals("LDAC 状态未查询前不得编默认值", null,
                router.value(HsDevice.Capability.LDAC, domain, dual));
        assertEquals("新手模式状态未上报前不得编默认值", null,
                router.value(HsDevice.Capability.BEGINNER_MODE, domain, dual));
    }

    /** GATE 能力是纯门控：不得进入快照控制值（单一状态来源）。 */
    @Test
    public void gateCapabilitiesStayOutOfSnapshotValues() {
        HsControl.Dual dual = new HsControl.Dual(true, "AA:BB:CC:DD:EE:FF",
                List.of());
        HsCatalogImpl catalog = new HsCatalogImpl();
        for (HsDevice.Capability capability : HsDevice.Capability.values()) {
            if (catalog.descriptor(capability).kind() != HsDevice.Form.GATE) {
                continue;
            }
            assertEquals("GATE 不得有快照控制值: " + capability, null,
                    router.value(capability, domain, dual));
        }
    }

    /**
     * value() 对每个能力都有明确结果：穷尽 switch（无 default），未知状态返回 null。
     * 新增能力若漏了分支，这里会编译失败而不是静默返回 null。
     */
    @Test
    public void valueIsDefinedForEveryCapability() {
        HsControl.Dual dual = new HsControl.Dual(true, "", List.of());
        for (HsDevice.Capability capability : HsDevice.Capability.values()) {
            HsControl.Value value = router.value(capability, domain, dual);
            if (capability == HsDevice.Capability.DUAL_CONNECTION) {
                assertEquals(new HsControl.Value.Toggle(true), value);
                continue;
            }
            assertEquals("未上报状态的能力必须返回 null: " + capability, null, value);
        }
    }

    @Test
    public void dualPageCompletionBecomesDualConnectionEvent() {
        ProtocolDual state =
                new ProtocolDual(false, "", List.of());
        Optional<HsControl.Event> event = router.deviceEvent(
                new GetDualConnectionPageCommand(),
                CommandCompletion.objectResult(Command.ResultCode.SUCCESS, state, "ok"));
        assertTrue(event.isPresent());
        assertEquals(new HsControl.Event.DualConnectionChanged(HsModels.dual(state)), event.get());

        assertEquals("失败回执不产出事件", Optional.empty(), router.deviceEvent(
                new GetDualConnectionPageCommand(),
                CommandCompletion.stringResult(Command.ResultCode.FAILED, "bad")));
    }

    @Test
    public void recordListCompletionBecomesRecordFilesEvent() {
        List<ProtocolRecording> files = List.of(new ProtocolRecording("3", "3", 16, 4096L));
        Optional<HsControl.Event> event = router.deviceEvent(
                new GetRecordIdListCommand(0, 12),
                CommandCompletion.objectResult(Command.ResultCode.SUCCESS, files, "ok"));
        assertTrue(event.isPresent());
        assertEquals(new HsControl.Event.RecordFilesChanged(HsModels.recordings(files)), event.get());
    }

    private List<String> payloads(HsControl.Action action) {
        List<String> payloads = new java.util.ArrayList<>();
        for (Command command : router.commands(action)) {
            payloads.add(command.getCommandData());
        }
        return payloads;
    }

    private static int[] neutralGains() {
        int[] gains = new int[CustomEQRealtimeCommand.BAND_COUNT];
        for (int index = 0; index < gains.length; index++) {
            gains[index] = CustomEQRealtimeCommand.GAIN_NEUTRAL;
        }
        return gains;
    }

    private static List<Integer> neutralPoints() {
        List<Integer> points = new java.util.ArrayList<>();
        for (int gain : neutralGains()) {
            points.add(gain);
        }
        return points;
    }
}
