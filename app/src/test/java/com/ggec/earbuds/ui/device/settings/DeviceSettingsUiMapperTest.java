package com.ggec.earbuds.ui.device.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DualConnectionView;
import com.ggec.earbuds.domain.device.model.PairedDeviceView;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.ggec.earbuds.ui.device.DeviceProfileFixtures;
import com.ggec.earbuds.ui.device.WriteState;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 快照 + 描述 → 设置页状态：一页一分组、事实行与能力行分区、写入槽随状态传递。 */
public final class DeviceSettingsUiMapperTest {

    private final DeviceSettingsUiMapper mapper =
            new DeviceSettingsUiMapper(new SettingsSectionFactory());

    @Test
    public void missingDescriptorIsAnExplicitEmptyStateNotASilentBlankPage() {
        for (DeviceCapabilityGroup group : DeviceCapabilityGroup.values()) {
            DeviceSettingsUiState state = mapper.map(group, Optional.empty(),
                    DeviceProfileFixtures.ready("HS01", emptyState()), Map.of());

            assertTrue("已连接就是已连接", state.connected());
            assertTrue(state.interactive());
            assertEquals(group, state.group());
            assertEquals(1, state.rows().size());
            DeviceSettingRow.Notice notice = (DeviceSettingRow.Notice) state.rows().get(0);
            assertEquals("缺描述必须显式说出来",
                    com.ggec.earbuds.R.string.device_settings_descriptor_missing,
                    notice.textRes());
        }
    }

    @Test
    public void notConnectedMapsToTheUnconnectedState() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS01", "HS01");

        for (DeviceCapabilityGroup group : DeviceCapabilityGroup.values()) {
            DeviceSettingsUiState idle = mapper.map(group, Optional.of(profile),
                    DeviceProfileFixtures.idle(), Map.of());
            assertFalse(idle.connected());
            assertFalse(idle.interactive());
            assertTrue(idle.rows().isEmpty());
            assertEquals(group, idle.group());
        }
    }

    @Test
    public void aboutGroupCarriesDeviceFactsBeforeCapabilities() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("CLEAR_PAIRING", "清除配对",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.ACTION));

        DeviceSettingsUiState state = mapper.map(DeviceCapabilityGroup.ABOUT,
                Optional.of(profile), DeviceProfileFixtures.ready("HS02", emptyState()), Map.of());

        assertTrue(state.connected());
        assertTrue(state.interactive());
        assertEquals(DeviceCapabilityGroup.ABOUT, state.group());
        assertTrue(state.rows().get(0) instanceof DeviceSettingRow.Info);
        assertTrue(state.rows().get(state.rows().size() - 1) instanceof DeviceSettingRow.Action);
    }

    @Test
    public void otherGroupsCarryOnlyCapabilityRows() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CHOICE),
                DeviceProfileFixtures.capability("CLEAR_PAIRING", "清除配对",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.ACTION));

        DeviceSettingsUiState state = mapper.map(DeviceCapabilityGroup.MUSIC,
                Optional.of(profile), DeviceProfileFixtures.ready("HS02", emptyState()), Map.of());

        assertEquals(1, state.rows().size());
        assertTrue(state.rows().get(0) instanceof DeviceSettingRow.Choice);
    }

    @Test
    public void listDataFollowsTheSnapshot() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability(DeviceCapabilityIds.DUAL_CONNECTION, "双连接",
                        DeviceCapabilityGroup.DUAL_CONNECTION, DeviceCapabilityKind.TOGGLE),
                DeviceProfileFixtures.capability(DeviceCapabilityIds.DUAL_CONNECTION_DEVICES,
                        "已配对设备", DeviceCapabilityGroup.DUAL_CONNECTION,
                        DeviceCapabilityKind.LIST));

        DualConnectionView dual = new DualConnectionView(true, "AA:BB",
                List.of(new PairedDeviceView("手机", "AA:BB",
                        PairedDeviceView.PairedDeviceType.PHONE, true,
                        PairedDeviceView.PairedBusiness.MUSIC, true, true)));
        List<RecordFileView> files = List.of(new RecordFileView("f1", "录音 1", 12, 2048L));

        DeviceSettingsUiState state = mapper.map(DeviceCapabilityGroup.DUAL_CONNECTION,
                Optional.of(profile),
                DeviceProfileFixtures.ready("HS02", emptyState(), Optional.of(dual), files),
                Map.of());

        assertEquals(Optional.of(dual), state.dualConnection());
        assertEquals(files, state.recordFiles());
        // 开关行在前（DUAL_CONNECTION），列表行在后（DUAL_CONNECTION_DEVICES）。
        assertTrue(state.rows().get(0) instanceof DeviceSettingRow.Toggle);
        DeviceSettingRow.CapabilityList list =
                (DeviceSettingRow.CapabilityList) state.rows().get(1);
        assertEquals(DeviceSettingRow.ListKind.DUAL_CONNECTION, list.kind());
    }

    @Test
    public void writeSlotsAreCarriedThroughUntouched() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("ANC", "降噪",
                        DeviceCapabilityGroup.CONTROL, DeviceCapabilityKind.TOGGLE));
        DeviceSettingsUiState.WriteSlot slot = new DeviceSettingsUiState.WriteSlot(
                WriteState.CONFIRMED,
                Optional.of(new ControlValue.Toggle(true)));

        DeviceSettingsUiState state = mapper.map(DeviceCapabilityGroup.CONTROL,
                Optional.of(profile), DeviceProfileFixtures.ready("HS02", emptyState()),
                Map.of("ANC", slot));

        assertSame(slot, state.writeOf("ANC"));
        assertEquals(Optional.of(Boolean.TRUE), state.writeOf("ANC").toggleValue());
        assertSame(WriteState.IDLE, state.writeOf("unknown").state());
    }

    @Test
    public void notReadyConnectionIsReadOnly() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CHOICE));

        DeviceSettingsUiState state = mapper.map(DeviceCapabilityGroup.MUSIC,
                Optional.of(profile), DeviceProfileFixtures.connecting("HS02"), Map.of());

        assertTrue(state.connected());
        assertFalse(state.interactive());
    }

    private static DeviceState emptyState() {
        return new DeviceState(Map.of());
    }
}
