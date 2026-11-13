package com.ggec.earbuds.ui.device.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceEntry;
import com.ggec.earbuds.domain.device.model.DeviceFacts;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceIdentity;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.DeviceStatus;
import com.ggec.earbuds.domain.device.model.RecordOrigin;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.ui.device.DeviceFormats;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 设备页：连接中才有标题/副标题/能力入口；下拉三项；未连接走断开空态。 */
public final class DeviceHomeUiMapperTest {

    private static final long NOW = 10_000_000L;
    private static final DeviceId ID = new DeviceId("HS02", "AA:BB:CC:DD:EE:02");
    private static final DeviceHomeUiMapper MAPPER = new DeviceHomeUiMapper(new DeviceCapabilityCatalog() {
        @Override
        public List<DeviceProfileView> models() {
            return List.of(new DeviceProfileView("HS02", "HS 系列耳机", List.of()));
        }

        @Override
        public Optional<DeviceProfileView> model(String modelId) {
            return models().stream().filter(p -> p.modelId().equals(modelId)).findFirst();
        }
    });

    @Test
    public void connectedDeviceGetsTitleSubtitleAndEntries() {
        DeviceRecord live = live();
        List<DeviceEntry> entries = List.of(new DeviceEntry(DeviceCapabilityGroup.MUSIC, 3),
                new DeviceEntry(DeviceCapabilityGroup.ABOUT, 1));

        DeviceHomeUiState state = MAPPER.map(
                new DeviceRoster(List.of(live), Optional.of(ID)), entries);

        assertEquals("Vidda V12", state.title());
        assertEquals("AA:BB:CC:DD:EE:02" + DeviceFormats.META_SEPARATOR + "HS 系列耳机", state.subtitle());
        assertEquals(2, state.entries().size());
        assertEquals(R.string.device_entry_music, state.entries().get(0).titleRes());
        assertEquals(3, state.entries().get(0).count());
        assertEquals(R.drawable.ic_ai_bolt, state.entries().get(0).iconRes());
        assertTrue(state.devices().get(0).connected());
    }

    @Test
    public void disconnectedRosterKeepsTheDropdownButNoEntries() {
        DeviceRecord archived = new DeviceRecord(ID, "", identity(),
                DeviceStatus.archived(NOW - 60_000L), DeviceFacts.unknown(),
                new DeviceState(Map.of()));

        DeviceHomeUiState state = MAPPER.map(
                new DeviceRoster(List.of(archived), Optional.empty()), List.of());

        assertTrue("没有连接中的设备就没有标题", state.title().isEmpty());
        assertTrue(state.subtitle().isEmpty());
        assertTrue(state.entries().isEmpty());
        assertEquals(1, state.devices().size());
        assertFalse(state.devices().get(0).connected());
        assertEquals(R.string.device_home_disconnected_body, state.emptyBodyRes());
    }

    @Test
    public void anEmptyRosterUsesTheFirstRunEmptyCopy() {
        DeviceHomeUiState state = MAPPER.map(DeviceRoster.empty(), List.of());

        assertEquals(R.string.device_home_empty_body, state.emptyBodyRes());
        assertTrue(state.devices().isEmpty());
    }

    private static DeviceRecord live() {
        return new DeviceRecord(ID, "", identity(),
                new DeviceStatus(RecordOrigin.LIVE, ConnectionState.READY, NOW),
                new DeviceFacts(new BatterySnapshot(80, 70, 60, true), "1.0.3",
                        WearState.UNKNOWN, 2),
                new DeviceState(Map.of()));
    }

    private static DeviceIdentity identity() {
        return new DeviceIdentity("Vidda V12", "11:22:33:44:55:66", "");
    }
}
