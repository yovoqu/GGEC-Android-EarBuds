package com.ggec.earbuds.ui.device.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DualConnectionView;
import com.ggec.earbuds.domain.device.model.PairedDeviceView;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.ggec.earbuds.ui.device.DeviceProfileFixtures;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 快照 + 描述 → 设置页状态：一页一分组、事实行与能力行分区、列表数据随快照传递。 */
public final class DeviceSettingsUiMapperTest {

    private final DeviceSettingsUiMapper mapper =
            new DeviceSettingsUiMapper(new SettingsSectionFactory());

    @Test
    public void disconnectedOrMissingProfileMapsToUnconnectedStateWithoutNpe() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS01", "HS01");

        for (DeviceCapabilityGroup group : DeviceCapabilityGroup.values()) {
            DeviceSettingsUiState missingProfile = mapper.map(group, null,
                    DeviceProfileFixtures.ready("HS01", emptyState()));
            assertFalse(missingProfile.connected());
            assertFalse(missingProfile.interactive());
            assertTrue(missingProfile.rows().isEmpty());
            assertEquals(group, missingProfile.group());

            DeviceSettingsUiState idle = mapper.map(group, profile, DeviceProfileFixtures.idle());
            assertFalse(idle.connected());
            assertTrue(idle.rows().isEmpty());
        }
    }

    @Test
    public void aboutGroupCarriesDeviceFactsBeforeCapabilities() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("CLEAR_PAIRING", "清除配对",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.ACTION));

        DeviceSettingsUiState state = mapper.map(DeviceCapabilityGroup.ABOUT, profile,
                DeviceProfileFixtures.ready("HS02", emptyState()));

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

        DeviceSettingsUiState state = mapper.map(DeviceCapabilityGroup.MUSIC, profile,
                DeviceProfileFixtures.ready("HS02", emptyState()));

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

        DeviceSettingsUiState state = mapper.map(DeviceCapabilityGroup.DUAL_CONNECTION, profile,
                DeviceProfileFixtures.ready("HS02", emptyState(), Optional.of(dual), files));

        assertEquals(Optional.of(dual), state.dualConnection());
        assertEquals(files, state.recordFiles());
        // 开关行在前（DUAL_CONNECTION），列表行在后（DUAL_CONNECTION_DEVICES）。
        assertTrue(state.rows().get(0) instanceof DeviceSettingRow.Toggle);
        DeviceSettingRow.CapabilityList list =
                (DeviceSettingRow.CapabilityList) state.rows().get(1);
        assertEquals(DeviceSettingRow.ListKind.DUAL_CONNECTION, list.kind());
    }

    @Test
    public void notReadyConnectionIsReadOnly() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CHOICE));

        DeviceSettingsUiState state = mapper.map(DeviceCapabilityGroup.MUSIC, profile,
                DeviceProfileFixtures.connecting("HS02"));

        assertTrue(state.connected());
        assertFalse(state.interactive());
    }

    private static DeviceState emptyState() {
        return new DeviceState(Map.of());
    }
}
