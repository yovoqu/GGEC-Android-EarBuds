package com.ggec.earbuds.provider.biz_ggec_hs;


import com.yovo.biz_ggec_hs.api.HsDevice;
import com.yovo.biz_ggec_hs.api.HsControl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceFailure;
import com.ggec.earbuds.domain.device.model.DeviceWriteResult;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 映射穷尽性：每个 biz 取值都有确定落点，新增取值时这里必须跟着改。 */
public final class HsValueMapperTest {

    @Test
    public void everyCapabilityGroupAndControlKindMapsOneToOne() {
        for (HsDevice.Group group : HsDevice.Group.values()) {
            assertEquals(DeviceCapabilityGroup.valueOf(group.name()), HsValueMapper.toGroup(group));
        }
        for (HsDevice.Form kind : HsDevice.Form.values()) {
            assertEquals(DeviceCapabilityKind.valueOf(kind.name()), HsValueMapper.toKind(kind));
        }
    }

    @Test
    public void gateKindKeepsItsOwnAppVocabulary() {
        // GATE = 纯门控（不生成通用行）：不是可写控件，也不会被折叠进 TOGGLE/ACTION，
        // 界面据 kind 决定渲染而不是靠能力名推断。
        assertEquals(DeviceCapabilityKind.GATE, HsValueMapper.toKind(HsDevice.Form.GATE));
    }

    @Test
    public void everyFailureCategoryHasAnExplicitTarget() {
        Map<HsControl.Failure, DeviceFailure> expected = new EnumMap<>(HsControl.Failure.class);
        expected.put(HsControl.Failure.BLUETOOTH_DISABLED, DeviceFailure.BLUETOOTH_DISABLED);
        expected.put(HsControl.Failure.PERMISSION_DENIED, DeviceFailure.PERMISSION_DENIED);
        expected.put(HsControl.Failure.LOCATION_DISABLED, DeviceFailure.LOCATION_DISABLED);
        expected.put(HsControl.Failure.BLE_UNSUPPORTED, DeviceFailure.BLE_UNSUPPORTED);
        expected.put(HsControl.Failure.SCAN_THROTTLED, DeviceFailure.SCAN_THROTTLED);
        expected.put(HsControl.Failure.SCAN_START_FAILED, DeviceFailure.SCAN_FAILED);
        expected.put(HsControl.Failure.SCAN_FEATURE_UNSUPPORTED, DeviceFailure.SCAN_FAILED);
        expected.put(HsControl.Failure.SCAN_OUT_OF_RESOURCES, DeviceFailure.SCAN_FAILED);
        expected.put(HsControl.Failure.SCAN_INTERNAL_ERROR, DeviceFailure.SCAN_FAILED);
        expected.put(HsControl.Failure.RELEASED, DeviceFailure.NOT_READY);
        expected.put(HsControl.Failure.NOT_READY, DeviceFailure.NOT_READY);
        expected.put(HsControl.Failure.TIMEOUT, DeviceFailure.TIMEOUT);
        expected.put(HsControl.Failure.REJECTED, DeviceFailure.REJECTED);
        expected.put(HsControl.Failure.UNSUPPORTED_CAPABILITY, DeviceFailure.UNSUPPORTED_CAPABILITY);
        expected.put(HsControl.Failure.DEVICE_ERROR, DeviceFailure.DEVICE_ERROR);
        expected.put(HsControl.Failure.INTERNAL_ERROR, DeviceFailure.INTERNAL_ERROR);

        assertEquals("每个 biz 失败分类都必须有落点",
                HsControl.Failure.values().length, expected.size());
        for (HsControl.Failure failure : HsControl.Failure.values()) {
            assertEquals(failure.name(), expected.get(failure), HsValueMapper.toFailure(failure));
        }
    }

    @Test
    public void controlValuesRoundTripInBothDirections() {
        List<ControlValue> presented = List.of(
                new ControlValue.Toggle(true),
                new ControlValue.Level(7),
                new ControlValue.Choice("movie"),
                new ControlValue.Curve(List.of(0, 1, 2)));

        for (ControlValue value : presented) {
            HsControl.Value toBiz = HsValueMapper.toHsControlValue(value);
            assertEquals(value, HsValueMapper.toControlValue(toBiz));
        }
    }

    @Test
    public void descriptorProjectionKeepsTitleGroupKindOptionsAndRange() {
        HsDevice.Feature raw = new HsDevice.Feature(
                HsDevice.Capability.EQ_MODE, "音效模式", HsDevice.Group.MUSIC, HsDevice.Form.CHOICE,
                List.of(new HsDevice.Option("music", "音乐"),
                        new HsDevice.Option("movie", "影院")),
                Optional.empty());

        DeviceCapabilityView view = HsValueMapper.toCapabilityView(raw);

        assertEquals("EQ_MODE", view.id());
        assertEquals("音效模式", view.title());
        assertEquals(DeviceCapabilityGroup.MUSIC, view.group());
        assertEquals(DeviceCapabilityKind.CHOICE, view.kind());
        assertEquals(List.of(new CapabilityOption("music", "音乐"),
                new CapabilityOption("movie", "影院")), view.options());
        assertTrue(view.range().isEmpty());
    }

    @Test
    public void levelDescriptorKeepsDeclaredRange() {
        HsDevice.Feature raw = new HsDevice.Feature(
                HsDevice.Capability.VOLUME_REMIND, "音量", HsDevice.Group.FUNCTION, HsDevice.Form.LEVEL,
                List.of(), Optional.of(new HsDevice.Range(0, 10, 1)));

        DeviceCapabilityView view = HsValueMapper.toCapabilityView(raw);

        assertEquals(Optional.of(new ControlRange(0, 10, 1)), view.range());
    }

    @Test
    public void capabilityIdParsingIsStrict() {
        assertEquals(HsDevice.Capability.GAME_MODE, HsValueMapper.capabilityOf("GAME_MODE"));
        assertThrows(IllegalArgumentException.class, () -> HsValueMapper.capabilityOf("GAME_MODE_X"));
        assertThrows(IllegalArgumentException.class, () -> HsValueMapper.capabilityOf(null));
    }

    @Test
    public void everyBizCapabilityIdIsAcceptedWithoutAnAppSideWhitelist() {
        // 能力 id 只经 HsDevice.Capability.valueOf 解析：biz 新增能力时 app 侧无需改代码即可透传。
        for (HsDevice.Capability capability : HsDevice.Capability.values()) {
            assertEquals(capability, HsValueMapper.capabilityOf(capability.name()));
        }
        // 重构前 app 有 UI 消费者的四项能力（原 DeviceSwitch.LDAC/VOLUME_ADAPTIVE、
        // SettingId.RESET_CONTROL、YoHsClient.reboot）必须仍然可达；双连接设备列表是独立能力。
        assertEquals(HsDevice.Capability.LDAC, HsValueMapper.capabilityOf("LDAC"));
        assertEquals(HsDevice.Capability.VOLUME_ADAPTIVE, HsValueMapper.capabilityOf("VOLUME_ADAPTIVE"));
        assertEquals(HsDevice.Capability.RESET_CONTROL, HsValueMapper.capabilityOf("RESET_CONTROL"));
        assertEquals(HsDevice.Capability.REBOOT, HsValueMapper.capabilityOf("REBOOT"));
        assertEquals(HsDevice.Capability.DUAL_CONNECTION_DEVICES,
                HsValueMapper.capabilityOf("DUAL_CONNECTION_DEVICES"));
    }

    @Test
    public void everyPresentedActionMapsToItsBizCounterpart() {
        Map<DeviceAction, HsControl.Action> expected = new java.util.LinkedHashMap<>();
        expected.put(new DeviceAction.FindEar(DeviceAction.FindEar.Side.LEFT),
                new HsControl.Action.FindEar(HsControl.Action.FindEar.Side.LEFT));
        expected.put(new DeviceAction.FindEar(DeviceAction.FindEar.Side.BOTH),
                new HsControl.Action.FindEar(HsControl.Action.FindEar.Side.BOTH));
        expected.put(new DeviceAction.StopFindAlert(), new HsControl.Action.StopFindAlert());
        expected.put(new DeviceAction.Reboot(), new HsControl.Action.Reboot());
        expected.put(new DeviceAction.Rename("n"), new HsControl.Action.Rename("n"));
        expected.put(new DeviceAction.ClearPairing(), new HsControl.Action.ClearPairing());
        expected.put(new DeviceAction.ResetControl(), new HsControl.Action.ResetControl());
        expected.put(new DeviceAction.StartRecord(DeviceAction.RecordMode.LOCAL),
                new HsControl.Action.StartRecord(HsControl.Action.RecordMode.LOCAL));
        expected.put(new DeviceAction.StartRecord(DeviceAction.RecordMode.STREAM),
                new HsControl.Action.StartRecord(HsControl.Action.RecordMode.STREAM));
        expected.put(new DeviceAction.StopRecord(DeviceAction.RecordMode.STREAM),
                new HsControl.Action.StopRecord(HsControl.Action.RecordMode.STREAM));
        expected.put(new DeviceAction.ReadRecordList(), new HsControl.Action.ReadRecordList());
        expected.put(new DeviceAction.TransferRecord("f", true), new HsControl.Action.TransferRecord("f", true));
        expected.put(new DeviceAction.ReadPairedDevices(), new HsControl.Action.ReadPairedDevices());
        expected.put(new DeviceAction.ForgetPairedDevice("a"), new HsControl.Action.ForgetPairedDevice("a"));
        expected.put(new DeviceAction.SetPriorityPairedDevice("a"),
                new HsControl.Action.SetPriorityPairedDevice("a"));
        expected.put(new DeviceAction.ConnectPairedDevice("a"), new HsControl.Action.ConnectPairedDevice("a"));
        expected.put(new DeviceAction.DisconnectPairedDevice("a"),
                new HsControl.Action.DisconnectPairedDevice("a"));

        for (Map.Entry<DeviceAction, HsControl.Action> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), HsValueMapper.toHsAction(entry.getKey()));
        }
    }

    @Test
    public void resultMappingSeparatesSuccessFromFailure() {
        assertEquals(new DeviceWriteResult.Ok(),
                HsValueMapper.toWriteResult(new HsControl.Outcome.Ok<Void>(null)));
        assertEquals(new DeviceWriteResult.Failed(DeviceFailure.TIMEOUT),
                HsValueMapper.toWriteResult(new HsControl.Outcome.Err<Void>(HsControl.Failure.TIMEOUT)));
    }

    @Test
    public void recordFilesMapFieldByField() {
        RecordFileView view = HsValueMapper.toRecordFileView(
                new HsControl.Recording("f1", "rec", 5, 1024L));

        assertEquals("f1", view.fileId());
        assertEquals("rec", view.name());
        assertEquals(5, view.durationSeconds());
        assertEquals(1024L, view.sizeBytes());
    }

    @Test
    public void recordFileListsAreCopiedNotAliased() {
        List<HsControl.Recording> source = new ArrayList<>();
        source.add(new HsControl.Recording("f1", "rec", 5, 1024L));

        List<RecordFileView> mapped = HsValueMapper.toRecordFileViews(source);
        source.add(new HsControl.Recording("f2", "rec2", 6, 2048L));

        assertEquals(1, mapped.size());
    }
}
