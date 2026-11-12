package com.ggec.earbuds.ui.device.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceSnapshot;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.model.WearState;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceProfileFixtures;

import org.junit.Test;

import java.util.List;

/** 描述符 → 首页分区与行：分组决定分区、kind 决定控件、缺失能力不渲染。 */
public final class DeviceHomeUiMapperTest {

    private final DeviceHomeUiMapper mapper = new DeviceHomeUiMapper();

    @Test
    public void mapsConnectedSnapshot() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02 耳机");
        DeviceHomeUiState state = mapper.map(profile,
                DeviceProfileFixtures.ready("HS02", DeviceProfileFixtures.state("GAME_MODE",
                        new ControlValue.Toggle(true))));

        assertTrue(state.connected());
        assertTrue(state.interactive());
        assertEquals("HS02-display", state.title());
        assertEquals("HS02", state.subtitle());
        assertEquals("80%", state.leftText());
        assertEquals("70%", state.rightText());
        assertEquals(DeviceFormats.UNKNOWN, state.caseText());
        assertEquals(R.string.device_state_ready, state.connectionTextRes());
        assertEquals(R.string.device_wear_none, state.wearTextRes());
        assertEquals(R.string.device_charging_on, state.chargingTextRes());
    }

    @Test
    public void rowsFollowCapabilityDeclarationsAndGroupOrder() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("WEAR_DETECT", "佩戴检测",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE),
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CHOICE,
                        DeviceProfileFixtures.option("MUSIC", "音乐"),
                        DeviceProfileFixtures.option("MOVIE", "电影")),
                DeviceProfileFixtures.capability("GAME_MODE", "游戏模式",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE));

        DeviceHomeUiState.Section[] sections = mapper.map(profile,
                        DeviceProfileFixtures.ready("HS02", DeviceProfileFixtures.state("EQ_MODE",
                                new ControlValue.Choice("MOVIE"))))
                .sections().toArray(DeviceHomeUiState.Section[]::new);

        // 分区按枚举声明顺序（MUSIC 先于 FUNCTION），分区内保持声明顺序。
        assertEquals(2, sections.length);
        assertEquals(DeviceCapabilityGroup.MUSIC, sections[0].group());
        assertEquals(R.string.device_entry_music, sections[0].titleRes());
        assertEquals(List.of("EQ_MODE"), capabilityIds(sections[0]));
        assertEquals(DeviceCapabilityGroup.FUNCTION, sections[1].group());
        assertEquals(List.of("WEAR_DETECT", "GAME_MODE"), capabilityIds(sections[1]));
    }

    @Test
    public void gateCapabilitiesRenderNoRows() {
        // CONNECTION / BATTERY / DEVICE_INFO / SERVICE_STATUS 是纯门控：首页不为它们造行，
        // 电量与连接态由摘要卡承担。
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("CONNECTION", "连接状态",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.GATE),
                DeviceProfileFixtures.capability("BATTERY", "电量",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.GATE),
                DeviceProfileFixtures.capability("DEVICE_INFO", "设备信息",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.GATE),
                DeviceProfileFixtures.capability("SERVICE_STATUS", "服务状态",
                        DeviceCapabilityGroup.ABOUT, DeviceCapabilityKind.GATE));

        List<DeviceHomeUiState.Row> rows = allRows(mapper.map(profile,
                DeviceProfileFixtures.ready("HS02", DeviceProfileFixtures.state("BATTERY",
                        new ControlValue.Level(80)))));

        assertTrue("GATE 不生成任何行", rows.isEmpty());
    }

    @Test
    public void kindDecidesTheControl() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("GAME_MODE", "游戏模式",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE),
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CHOICE,
                        DeviceProfileFixtures.option("MUSIC", "音乐")),
                DeviceProfileFixtures.capability("FIND_EAR", "查找耳机",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.ACTION),
                DeviceProfileFixtures.capability("CUSTOM_EQ", "自定义均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CURVE));

        List<DeviceHomeUiState.Row> rows = allRows(mapper.map(profile,
                DeviceProfileFixtures.ready("HS02", DeviceProfileFixtures.state("GAME_MODE",
                        new ControlValue.Toggle(true)))));

        assertTrue(rows.get(0) instanceof DeviceHomeUiState.Row.Choice);
        assertTrue(rows.get(1) instanceof DeviceHomeUiState.Row.Link);
        assertTrue(rows.get(2) instanceof DeviceHomeUiState.Row.Toggle);
        assertTrue(rows.get(3) instanceof DeviceHomeUiState.Row.Link);
    }

    @Test
    public void missingCapabilitiesAreNotRendered() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS01", "HS01",
                DeviceProfileFixtures.capability("GAME_MODE", "游戏模式",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE));

        List<DeviceHomeUiState.Row> rows = allRows(mapper.map(profile,
                DeviceProfileFixtures.ready("HS01", new DeviceState(java.util.Map.of()))));

        assertEquals(1, rows.size());
        assertEquals("GAME_MODE", rows.get(0).capabilityId());
    }

    @Test
    public void unreportedValuesRenderAsAbsentInsteadOfSentinel() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("GAME_MODE", "游戏模式",
                        DeviceCapabilityGroup.FUNCTION, DeviceCapabilityKind.TOGGLE),
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CHOICE,
                        DeviceProfileFixtures.option("MUSIC", "音乐")));

        List<DeviceHomeUiState.Row> rows = allRows(mapper.map(profile,
                DeviceProfileFixtures.ready("HS02", new DeviceState(java.util.Map.of()))));

        assertTrue(((DeviceHomeUiState.Row.Toggle) rows.get(1)).checked().isEmpty());
        assertTrue(((DeviceHomeUiState.Row.Choice) rows.get(0)).value().isEmpty());
    }

    @Test
    public void unknownChoiceIdRendersAsAbsent() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS02", "HS02",
                DeviceProfileFixtures.capability("EQ_MODE", "均衡器",
                        DeviceCapabilityGroup.MUSIC, DeviceCapabilityKind.CHOICE,
                        DeviceProfileFixtures.option("MUSIC", "音乐")));

        DeviceHomeUiState state = mapper.map(profile,
                DeviceProfileFixtures.ready("HS02", DeviceProfileFixtures.state("EQ_MODE",
                        new ControlValue.Choice("NOT_DECLARED"))));

        DeviceHomeUiState.Row.Choice choice =
                (DeviceHomeUiState.Row.Choice) allRows(state).get(0);
        assertTrue(choice.value().isEmpty());
    }

    @Test
    public void connectingSnapshotShowsConnectingCardButIsNotInteractive() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS01", "HS01");
        DeviceHomeUiState state = mapper.map(profile, DeviceProfileFixtures.connecting("HS01"));

        assertTrue(state.connected());
        assertFalse(state.interactive());
        assertEquals(R.string.device_state_connecting, state.connectionTextRes());
    }

    @Test
    public void unknownWearStateIsNotReportedAsNotWorn() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS01", "HS01");
        DeviceSnapshot connecting = DeviceProfileFixtures.connecting("HS01",
                new DeviceState(java.util.Map.of()), WearState.UNKNOWN);

        assertEquals(R.string.device_wear_unknown, mapper.map(profile, connecting).wearTextRes());
        assertEquals(R.string.device_wear_none,
                mapper.map(profile, DeviceProfileFixtures.ready("HS01",
                        new DeviceState(java.util.Map.of()))).wearTextRes());
    }

    @Test
    public void disconnectedSnapshotMapsToEmptyState() {
        DeviceProfileView profile = DeviceProfileFixtures.profile("HS01", "HS01");
        DeviceHomeUiState state = mapper.map(profile, DeviceProfileFixtures.idle());

        assertFalse(state.connected());
        assertTrue(state.sections().isEmpty());
        assertEquals(R.string.device_state_idle, state.connectionTextRes());
    }

    @Test
    public void missingProfileMapsToEmptyState() {
        DeviceSnapshot snapshot = DeviceProfileFixtures.ready("HS01", new DeviceState(java.util.Map.of()));
        DeviceHomeUiState state = mapper.map(null, snapshot);

        assertFalse(state.connected());
        assertTrue(state.sections().isEmpty());
    }

    private static List<String> capabilityIds(DeviceHomeUiState.Section section) {
        return section.rows().stream().map(DeviceHomeUiState.Row::capabilityId).toList();
    }

    private static List<DeviceHomeUiState.Row> allRows(DeviceHomeUiState state) {
        return state.sections().stream().flatMap(section -> section.rows().stream()).toList();
    }
}
