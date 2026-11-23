package com.ggec.earbuds.ui.device.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
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

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设备首页：标题/电量/状态；正文按 HarmonyOS 版块分流成卡片（整列 / 半列），开关卡与入口卡按性质归位。
 */
public final class DeviceHomeUiMapperTest {

    private static final long NOW = 10_000_000L;
    private static final DeviceId ID = new DeviceId("HS02", "AA:BB:CC:DD:EE:02");

    private static final DeviceHomeUiMapper MAPPER = new DeviceHomeUiMapper(new DeviceCapabilityCatalog() {
        @Override
        public List<DeviceProfileView> models() {
            return List.of(new DeviceProfileView("HS02", "HS 系列耳机", capabilities()));
        }

        @Override
        public Optional<DeviceProfileView> model(String modelId) {
            return models().stream().filter(p -> p.modelId().equals(modelId)).findFirst();
        }
    });

    @Test
    public void connectedDeviceGetsTitleBatteryAndStatus() {
        DeviceHomeUiState state = MAPPER.map(
                new DeviceRoster(List.of(live()), Optional.of(ID)), entries(), Map.of());

        assertEquals("Vidda V12", state.title());
        assertEquals(80, state.battery().left());
        assertTrue(state.battery().caseCharging());
        assertEquals(R.string.device_state_ready, state.statusTextRes());
        assertTrue(state.statusOnline());
        assertTrue(state.hasContent());
    }

    /** 五个固定版块，卡片按性质与宽度归位（含半列入口卡）。 */
    @Test
    public void sectionsCarryTheHarmonyOsGrouping() {
        DeviceHomeUiState state = MAPPER.map(
                new DeviceRoster(List.of(live()), Optional.of(ID)), entries(), Map.of());
        assertEquals(5, state.sections().size());

        // 0 设备连接中心：双连接（整列入口卡）
        assertEquals(R.string.device_section_connection, state.sections().get(0).titleRes());
        assertEquals(List.of(DeviceCapabilityGroup.DUAL_CONNECTION), entryGroups(state.sections().get(0)));
        // 1 声音：仅音乐入口（音量自适应归「本机设置」，不在首页）
        assertEquals(R.string.device_section_sound, state.sections().get(1).titleRes());
        assertEquals(List.of(), toggleIds(state.sections().get(1)));
        assertEquals(List.of(DeviceCapabilityGroup.MUSIC), entryGroups(state.sections().get(1)));
        // 2 交互：游戏模式已移到「本机设置」，这里只剩控制入口
        assertEquals(R.string.device_section_interaction, state.sections().get(2).titleRes());
        assertEquals(List.of(), toggleIds(state.sections().get(2)));
        assertEquals(List.of(DeviceCapabilityGroup.CONTROL), entryGroups(state.sections().get(2)));
        // 3 佩戴：独立「佩戴设置」入口（不再内联开关）
        assertEquals(R.string.device_section_wear, state.sections().get(3).titleRes());
        assertEquals(List.of(), toggleIds(state.sections().get(3)));
        assertEquals(1, countPageCard(state.sections().get(3)));
        // 4 更多：查找设备页面卡 + 本机设置/录音/关于设备（共 4 张 → 偶数全两列）
        assertEquals(R.string.device_section_more, state.sections().get(4).titleRes());
        assertEquals(1, countPageCard(state.sections().get(4)));
        List<DeviceHomeUiState.EntryCard> moreEntries = entryCards(state.sections().get(4));
        assertEquals(3, moreEntries.size());
        assertEquals(DeviceHomeUiState.CardSize.HALF, moreEntries.get(0).size());
        assertEquals(DeviceHomeUiState.CardSize.HALF, moreEntries.get(1).size());
        assertEquals(DeviceHomeUiState.CardSize.HALF, moreEntries.get(2).size());
    }

    @Test
    public void noInlineTogglesOnHome() {
        Map<String, DeviceHomeUiState.WriteSlot> writes = Map.of("WEAR_DETECT",
                new DeviceHomeUiState.WriteSlot(
                        DeviceHomeUiState.WriteState.PENDING, Optional.of(true)));

        DeviceHomeUiState state = MAPPER.map(
                new DeviceRoster(List.of(live()), Optional.of(ID)), entries(), writes);

        for (DeviceHomeUiState.Section section : state.sections()) {
            assertTrue(toggleIds(section).isEmpty());
        }
    }

    @Test
    public void disconnectedRosterKeepsTheDropdownButNoContent() {
        DeviceRecord archived = new DeviceRecord(ID, "", identity(),
                DeviceStatus.archived(NOW - 60_000L), DeviceFacts.unknown(),
                new DeviceState(Map.of()));

        DeviceHomeUiState state = MAPPER.map(
                new DeviceRoster(List.of(archived), Optional.empty()), List.of(), Map.of());

        assertTrue(state.title().isEmpty());
        assertFalse(state.hasContent());
        assertEquals(R.string.device_home_disconnected_body, state.emptyBodyRes());
    }

    @Test
    public void anEmptyRosterUsesTheFirstRunEmptyCopy() {
        DeviceHomeUiState state = MAPPER.map(DeviceRoster.empty(), List.of(), Map.of());
        assertEquals(R.string.device_home_empty_body, state.emptyBodyRes());
        assertTrue(state.devices().isEmpty());
    }

    private static int countPageCard(DeviceHomeUiState.Section section) {
        return (int) section.cards().stream()
                .filter(c -> c instanceof DeviceHomeUiState.PageCard).count();
    }

    private static List<DeviceHomeUiState.EntryCard> entryCards(DeviceHomeUiState.Section section) {        return section.cards().stream()
                .filter(c -> c instanceof DeviceHomeUiState.EntryCard)
                .map(c -> (DeviceHomeUiState.EntryCard) c).toList();
    }

    private static List<DeviceCapabilityGroup> entryGroups(DeviceHomeUiState.Section section) {
        return entryCards(section).stream().map(DeviceHomeUiState.EntryCard::group).toList();
    }

    private static List<String> toggleIds(DeviceHomeUiState.Section section) {
        return section.cards().stream()
                .filter(c -> c instanceof DeviceHomeUiState.ToggleCard)
                .map(c -> ((DeviceHomeUiState.ToggleCard) c).capabilityId()).toList();
    }

    private static List<DeviceEntry> entries() {
        return List.of(
                new DeviceEntry(DeviceCapabilityGroup.DUAL_CONNECTION, 2),
                new DeviceEntry(DeviceCapabilityGroup.MUSIC, 3),
                new DeviceEntry(DeviceCapabilityGroup.CONTROL, 9),
                new DeviceEntry(DeviceCapabilityGroup.FUNCTION, 4),
                new DeviceEntry(DeviceCapabilityGroup.RECORD, 4),
                new DeviceEntry(DeviceCapabilityGroup.ABOUT, 3));
    }

    private static List<DeviceCapabilityView> capabilities() {
        return List.of(
                new DeviceCapabilityView("GAME_MODE", "游戏模式", DeviceCapabilityGroup.CONTROL,
                        DeviceCapabilityKind.TOGGLE, List.of(), Optional.empty()),
                new DeviceCapabilityView("WEAR_DETECT", "佩戴检测", DeviceCapabilityGroup.CONTROL,
                        DeviceCapabilityKind.TOGGLE, List.of(), Optional.empty()),
                new DeviceCapabilityView("VOLUME_ADAPTIVE", "音量自适应", DeviceCapabilityGroup.MUSIC,
                        DeviceCapabilityKind.TOGGLE, List.of(), Optional.empty()));
    }

    private static DeviceRecord live() {
        return new DeviceRecord(ID, "", identity(),
                new DeviceStatus(RecordOrigin.LIVE, ConnectionState.READY, NOW),
                new DeviceFacts(new BatterySnapshot(80, 70, 60, true), "1.0.3",
                        WearState.UNKNOWN, 2),
                new DeviceState(Map.of("GAME_MODE", new ControlValue.Toggle(true))));
    }

    private static DeviceIdentity identity() {
        return new DeviceIdentity("Vidda V12", "11:22:33:44:55:66", "");
    }
}
