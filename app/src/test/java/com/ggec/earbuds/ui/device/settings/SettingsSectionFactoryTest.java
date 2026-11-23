package com.ggec.earbuds.ui.device.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.ggec.earbuds.ui.device.DeviceProfileFixtures;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** 描述符 → 行：分组顺序、kind → 行类型、能力缺失不渲染、未知能力 id 可见不崩。 */
public final class SettingsSectionFactoryTest {

    private final SettingsSectionFactory factory = new SettingsSectionFactory();

    /** 描述符频段表：测试输入，只声明段数与标签，不是产品的频段表。 */
    private static List<CapabilityOption> bands(int count) {
        List<CapabilityOption> options = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            options.add(new CapabilityOption("BAND_" + index, "band " + index));
        }
        return List.copyOf(options);
    }

    /** 可编辑的曲线描述符：频段表与增益值域都由描述符给出。 */
    private static DeviceCapabilityView curve(String id, String title, DeviceCapabilityGroup group,
                                              int bandCount, int min, int max, int step) {
        return new DeviceCapabilityView(id, title, group, DeviceCapabilityKind.CURVE,
                bands(bandCount), Optional.of(ControlRange.of(min, max, step)));
    }

    /** 声明了中立档的曲线描述符：中立档只声明在描述符里。 */
    private static DeviceCapabilityView curveWithNeutral(String id, String title,
                                                        DeviceCapabilityGroup group, int bandCount,
                                                        int min, int max, int step, int neutral) {
        return new DeviceCapabilityView(id, title, group, DeviceCapabilityKind.CURVE,
                bands(bandCount), Optional.of(new ControlRange(min, max, step,
                        OptionalInt.of(neutral))));
    }

    @Test
    public void setOnlyTogglesAlwaysProduceRowsEvenWithoutReportedValues() {
        // 这 6 项协议只有 Set、没有 Query：域里永远没有当前值，但行必须存在（开关不能消失）。
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                toggleOf(DeviceCapabilityIds.VOLUME_ADAPTIVE, "音量自适应"),
                toggleOf("WEAR_DETECT", "佩戴检测"),
                toggleOf("VOICE_WAKEUP", "语音唤醒"),
                toggleOf("FALL_ALERT", "掉落提醒"),
                toggleOf("VOLUME_REMIND", "听力保护"),
                toggleOf("INTERPRET", "同声传译"));

        List<DeviceSettingRow> rows =
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.FUNCTION);

        assertEquals(6, rows.size());
        for (DeviceSettingRow row : rows) {
            assertTrue(row instanceof DeviceSettingRow.Toggle);
            assertTrue("未上报时行仍存在，值留空", ((DeviceSettingRow.Toggle) row).checked().isEmpty());
        }
    }

    private static DeviceCapabilityView toggleOf(String capabilityId, String title) {
        return DeviceProfileFixtures.capability(capabilityId, title,
                DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE);
    }

    @Test
    public void gateCapabilitiesRenderNoRows() {
        // CONNECTION / BATTERY / DEVICE_INFO / SERVICE_STATUS 降为 GATE：不产生任何通用行。
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("CONNECTION", "连接状态",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.GATE),
                DeviceProfileFixtures.capability("BATTERY", "电量",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.GATE),
                DeviceProfileFixtures.capability("DEVICE_INFO", "设备信息",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.GATE),
                DeviceProfileFixtures.capability("SERVICE_STATUS", "服务状态",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.GATE));

        List<DeviceSettingRow> rows = factory.rows(profile,
                new DeviceState(Map.of("BATTERY", new ControlValue.Level(80))),
                DeviceCapabilityGroup.ABOUT);

        assertTrue(rows.isEmpty());
    }

    @Test
    public void dualConnectionSplitsIntoASwitchRowAndADeviceListRow() {
        // 真实形状：DUAL_CONNECTION 是 TOGGLE（只有开关），DUAL_CONNECTION_DEVICES 是 LIST（设备列表）。
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability(DeviceCapabilityIds.DUAL_CONNECTION, "双连接",
                        DeviceCapabilityGroup.DUAL_CONNECTION, DeviceCapabilityKind.TOGGLE),
                DeviceProfileFixtures.capability(DeviceCapabilityIds.DUAL_CONNECTION_DEVICES,
                        "已配对设备", DeviceCapabilityGroup.DUAL_CONNECTION,
                        DeviceCapabilityKind.LIST));

        List<DeviceSettingRow> rows = factory.rows(profile,
                new DeviceState(Map.of(DeviceCapabilityIds.DUAL_CONNECTION,
                        new ControlValue.Toggle(true))),
                DeviceCapabilityGroup.DUAL_CONNECTION);

        assertEquals(2, rows.size());
        DeviceSettingRow.Toggle toggle = (DeviceSettingRow.Toggle) rows.get(0);
        assertEquals(DeviceCapabilityIds.DUAL_CONNECTION, toggle.capabilityId());
        assertEquals(Boolean.TRUE, toggle.checked().get());

        DeviceSettingRow.CapabilityList list = (DeviceSettingRow.CapabilityList) rows.get(1);
        assertEquals(DeviceCapabilityIds.DUAL_CONNECTION_DEVICES, list.capabilityId());
        assertEquals(DeviceSettingRow.ListKind.DUAL_CONNECTION, list.kind());
    }

    @Test
    public void recordListRoutesToTheRecordFilesComponent() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability(DeviceCapabilityIds.RECORD_LIST, "录音文件",
                        DeviceCapabilityGroup.RECORD, DeviceCapabilityKind.LIST));

        DeviceSettingRow.CapabilityList list = (DeviceSettingRow.CapabilityList)
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.RECORD).get(0);

        assertEquals(DeviceSettingRow.ListKind.RECORD_FILES, list.kind());
    }

    @Test
    public void curveRowExistsWithoutReportedPoints() {
        // 值未上报不等于行不可用：编辑器初值由描述符派生（段数与值域都在描述符里）。
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                curve(DeviceCapabilityIds.CUSTOM_EQ, "自定义均衡器",
                        DeviceCapabilityGroup.MUSIC, 7, 2, 20, 2));

        DeviceSettingRow.Curve curveRow = (DeviceSettingRow.Curve)
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.MUSIC).get(0);

        assertEquals(DeviceCapabilityIds.CUSTOM_EQ, curveRow.capabilityId());
        assertTrue(curveRow.points().isEmpty());
        assertEquals("频段表照抄描述符", bands(7), curveRow.options());
        assertEquals("增益值域照抄描述符", Optional.of(ControlRange.of(2, 20, 2)),
                curveRow.range());
        assertTrue("描述符没声明中立档时行里也没有", curveRow.range().orElseThrow().neutral().isEmpty());
    }

    @Test
    public void curveRowKeepsTheDescriptorNeutral() {
        // 中立档是描述符的事实：行如实携带它，界面据此生成平直曲线，而不是自己推中点。
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                curveWithNeutral(DeviceCapabilityIds.CUSTOM_EQ, "自定义均衡器",
                        DeviceCapabilityGroup.MUSIC, 5, 0, 24, 1, 12));

        DeviceSettingRow.Curve curveRow = (DeviceSettingRow.Curve)
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.MUSIC).get(0);

        assertEquals(OptionalInt.of(12), curveRow.range().orElseThrow().neutral());
    }

    @Test
    public void curveWithoutDescriptorOptionsOrRangeIsNotRendered() {
        // 目录不变量被违反（CURVE 必须带频段 options 与 range）：不生成行，不猜默认。
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability(DeviceCapabilityIds.CUSTOM_EQ, "自定义均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CURVE),
                DeviceProfileFixtures.capability("LDAC", "LDAC",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.TOGGLE));

        List<DeviceSettingRow> rows =
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.MUSIC);

        assertEquals(1, rows.size());
        assertEquals("LDAC", ((DeviceSettingRow.Toggle) rows.get(0)).capabilityId());
    }

    @Test
    public void resetControlAndRebootRouteToTheirOwnActionComponents() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability(DeviceCapabilityIds.RESET_CONTROL, "重置手势",
                        DeviceCapabilityGroup.CONTROL, DeviceCapabilityKind.ACTION),
                DeviceProfileFixtures.capability(DeviceCapabilityIds.REBOOT, "重启耳机",
                        DeviceCapabilityGroup.CONTROL, DeviceCapabilityKind.ACTION));

        List<DeviceSettingRow> rows =
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.CONTROL);

        assertEquals(2, rows.size());
        assertEquals(DeviceSettingRow.ActionKind.RESET_CONTROL,
                ((DeviceSettingRow.Action) rows.get(0)).kind());
        assertEquals(DeviceSettingRow.ActionKind.REBOOT,
                ((DeviceSettingRow.Action) rows.get(1)).kind());
    }

    @Test
    public void audioQualityTogglesStayGenericSwitches() {
        // LDAC / VOLUME_ADAPTIVE 是 TOGGLE：必须走通用开关行，不允许出现专门组件分支。
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability(DeviceCapabilityIds.LDAC, "LDAC",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.TOGGLE),
                DeviceProfileFixtures.capability(DeviceCapabilityIds.VOLUME_ADAPTIVE, "音量自适应",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.TOGGLE));

        List<DeviceSettingRow> rows = factory.rows(profile,
                new DeviceState(Map.of(DeviceCapabilityIds.LDAC, new ControlValue.Toggle(true))),
                DeviceCapabilityGroup.MUSIC);

        assertEquals(2, rows.size());
        assertTrue(rows.get(0) instanceof DeviceSettingRow.Toggle);
        assertTrue(rows.get(1) instanceof DeviceSettingRow.Toggle);
        assertEquals(Boolean.TRUE,
                ((DeviceSettingRow.Toggle) rows.get(0)).checked().get());
        assertTrue(((DeviceSettingRow.Toggle) rows.get(1)).checked().isEmpty());
    }

    @Test
    public void rowsContainOnlyTheRequestedGroup() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CHOICE),
                DeviceProfileFixtures.capability("GAME_MODE", "游戏模式",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE));

        List<DeviceSettingRow> rows =
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.FUNCTION);

        assertEquals(1, rows.size());
        assertTrue(rows.get(0) instanceof DeviceSettingRow.Toggle);
    }

    @Test
    public void kindsMapToTheirRowTypes() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("GAME_MODE", "游戏模式",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE),
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.CHOICE,
                        DeviceProfileFixtures.option("MUSIC", "音乐")),
                DeviceProfileFixtures.level("VOLUME_REMIND", "音量提醒",
                        DeviceCapabilityGroup.FUNCTION, 0, 10, 1),
                curve("CUSTOM_EQ", "自定义均衡器",
                        DeviceCapabilityGroup.FUNCTION, 6, 4, 16, 2),
                DeviceProfileFixtures.capability("FIND_EAR", "查找耳机",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.ACTION),
                DeviceProfileFixtures.capability("RECORD_LIST", "录音列表",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.LIST));

        List<DeviceSettingRow> rows =
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.FUNCTION);

        assertTrue(rows.get(0) instanceof DeviceSettingRow.Toggle);
        assertTrue(rows.get(1) instanceof DeviceSettingRow.Choice);
        assertTrue(rows.get(2) instanceof DeviceSettingRow.Level);
        assertTrue(rows.get(3) instanceof DeviceSettingRow.Curve);
        assertTrue(rows.get(4) instanceof DeviceSettingRow.Action);
        assertTrue(rows.get(5) instanceof DeviceSettingRow.CapabilityList);
    }

    @Test
    public void specialisedKindsCarryTheirComponentRouting() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("FIND_EAR", "查找耳机",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.ACTION),
                DeviceProfileFixtures.capability("RECORD_LIST", "录音列表",
                        DeviceCapabilityGroup.RECORD, DeviceCapabilityKind.LIST),
                curve("CUSTOM_EQ", "自定义均衡器",
                        DeviceCapabilityGroup.MUSIC, 5, 1, 9, 1));

        DeviceState state = new DeviceState(Map.of());

        DeviceSettingRow.Action action = (DeviceSettingRow.Action)
                factory.rows(profile, state, DeviceCapabilityGroup.FUNCTION).get(0);
        assertEquals(DeviceSettingRow.ActionKind.FIND_EAR, action.kind());

        DeviceSettingRow.CapabilityList list = (DeviceSettingRow.CapabilityList)
                factory.rows(profile, state, DeviceCapabilityGroup.RECORD).get(0);
        assertEquals(DeviceSettingRow.ListKind.RECORD_FILES, list.kind());

        assertTrue(factory.rows(profile, state, DeviceCapabilityGroup.MUSIC).get(0)
                instanceof DeviceSettingRow.Curve);
    }

    @Test
    public void unknownCapabilityIdsStayVisibleInsteadOfBeingDropped() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("SOMETHING_NEW", "新能力",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.ACTION),
                DeviceProfileFixtures.capability("ANOTHER_CURVE", "新曲线",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.CURVE),
                DeviceProfileFixtures.capability("ANOTHER_LIST", "新列表",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.LIST));

        List<DeviceSettingRow> rows =
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.FUNCTION);

        assertEquals(3, rows.size());
        for (DeviceSettingRow row : rows) {
            assertTrue(row instanceof DeviceSettingRow.Unsupported);
        }
        assertEquals("SOMETHING_NEW",
                ((DeviceSettingRow.Unsupported) rows.get(0)).capabilityId());
    }

    @Test
    public void levelWithoutRangeIsContractViolationAndNotRendered() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.level("VOLUME_REMIND", "音量提醒",
                        DeviceCapabilityGroup.FUNCTION, null, null, null),
                DeviceProfileFixtures.capability("GAME_MODE", "游戏模式",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE));

        List<DeviceSettingRow> rows =
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.FUNCTION);

        assertEquals(1, rows.size());
        assertEquals("GAME_MODE", ((DeviceSettingRow.Toggle) rows.get(0)).capabilityId());
    }

    @Test
    public void reportedValuesAreResolvedFromDeviceState() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("GAME_MODE", "游戏模式",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE),
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.CHOICE,
                        DeviceProfileFixtures.option("MUSIC", "音乐"),
                        DeviceProfileFixtures.option("MOVIE", "电影")),
                DeviceProfileFixtures.level("VOLUME_REMIND", "音量提醒",
                        DeviceCapabilityGroup.FUNCTION, 0, 10, 1),
                curve("CUSTOM_EQ", "自定义均衡器",
                        DeviceCapabilityGroup.FUNCTION, 3, 2, 14, 2));

        DeviceState state = new DeviceState(Map.of(
                "GAME_MODE", new ControlValue.Toggle(true),
                "EQ_MODE", new ControlValue.Choice("MOVIE"),
                "VOLUME_REMIND", new ControlValue.Level(4),
                "CUSTOM_EQ", new ControlValue.Curve("", List.of(1, 2, 3))));

        List<DeviceSettingRow> rows =
                factory.rows(profile, state, DeviceCapabilityGroup.FUNCTION);

        assertEquals(Boolean.TRUE, ((DeviceSettingRow.Toggle) rows.get(0)).checked().get());
        assertEquals("电影", ((DeviceSettingRow.Choice) rows.get(1)).value().get());
        assertEquals(4, ((DeviceSettingRow.Level) rows.get(2)).value().getAsInt());
        assertEquals(List.of(1, 2, 3),
                ((DeviceSettingRow.Curve) rows.get(3)).points().get());
    }

    @Test
    public void unreportedValuesAreEmptyNotSentinel() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("GAME_MODE", "游戏模式",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE),
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.CHOICE,
                        DeviceProfileFixtures.option("MUSIC", "音乐")),
                DeviceProfileFixtures.level("VOLUME_REMIND", "音量提醒",
                        DeviceCapabilityGroup.FUNCTION, 0, 10, 1));

        List<DeviceSettingRow> rows =
                factory.rows(profile, new DeviceState(Map.of()), DeviceCapabilityGroup.FUNCTION);

        assertTrue(((DeviceSettingRow.Toggle) rows.get(0)).checked().isEmpty());
        assertTrue(((DeviceSettingRow.Choice) rows.get(1)).value().isEmpty());
        assertFalse(((DeviceSettingRow.Level) rows.get(2)).value().isPresent());
    }

    @Test
    public void choiceValueWithUnknownOptionIdIsEmpty() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CHOICE,
                        DeviceProfileFixtures.option("MUSIC", "音乐")));

        List<DeviceSettingRow> rows = factory.rows(profile,
                new DeviceState(Map.of("EQ_MODE", new ControlValue.Choice("NOT_DECLARED"))),
                DeviceCapabilityGroup.MUSIC);

        assertTrue(((DeviceSettingRow.Choice) rows.get(0)).value().isEmpty());
    }
}
