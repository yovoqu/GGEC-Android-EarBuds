package com.ggec.earbuds.ui.device.home;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.domain.device.model.ConnectionState;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityKind;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityView;
import com.ggec.earbuds.domain.device.model.DeviceEntry;
import com.ggec.earbuds.domain.device.model.DeviceId;
import com.ggec.earbuds.domain.device.model.DeviceProfileView;
import com.ggec.earbuds.domain.device.model.DeviceRecord;
import com.ggec.earbuds.domain.device.model.DeviceRoster;
import com.ggec.earbuds.domain.device.model.DeviceState;
import com.ggec.earbuds.domain.device.port.DeviceCapabilityCatalog;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceGroupLabels;
import com.ggec.earbuds.ui.device.TogglePresentation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 台账 + 能力入口 → 设备首页。纯映射：不碰 View、Context、存储、时钟。
 *
 * <p>版块结构（对齐 HarmonyOS 智慧生活）：设备连接中心 / 声音 / 交互 / 佩戴 / 更多。
 * 每个版块是一组卡片：开关卡（整列）+ 入口卡（整列或半列）。版块顺序、分组归属、卡片宽度
 * 都只在这里定义，视图只按 {@code sections} 渲染。</p>
 */
public final class DeviceHomeUiMapper {

    /** 版块定义：标题 + 归属分区 + 归属开关能力 id。 */
    private record Spec(@StringRes int titleRes,
                        List<DeviceCapabilityGroup> groups,
                        List<String> toggleIds) {
    }

    private static final List<Spec> SPECS = List.of(
            new Spec(R.string.device_section_connection,
                    List.of(DeviceCapabilityGroup.DUAL_CONNECTION), List.of()),
            new Spec(R.string.device_section_sound,
                    List.of(DeviceCapabilityGroup.MUSIC), List.of()),
            new Spec(R.string.device_section_interaction,
                    List.of(DeviceCapabilityGroup.CONTROL),
                    List.of()),
            new Spec(R.string.device_section_wear,
                    List.of(),
                    List.of()),
            new Spec(R.string.device_section_more,
                    List.of(DeviceCapabilityGroup.FUNCTION, DeviceCapabilityGroup.RECORD,
                            DeviceCapabilityGroup.ABOUT),
                    List.of()));

    /** 独立页 id：首页「页面卡」据此路由到对应 Activity。 */
    public static final String PAGE_FIND_DEVICE = "find_device";
    public static final String PAGE_WEAR_SETTINGS = "wear_settings";

    /** 首页不再内联任何快捷开关（游戏模式/佩戴检测均已下沉到二级页）。 */
    private static final List<String> QUICK_TOGGLE_IDS = List.of();

    private final DeviceCapabilityCatalog catalog;

    public DeviceHomeUiMapper(@NonNull DeviceCapabilityCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @NonNull
    public DeviceHomeUiState map(@NonNull DeviceRoster roster,
                                 @NonNull List<DeviceEntry> entries,
                                 @NonNull Map<String, DeviceHomeUiState.WriteSlot> writes) {
        Objects.requireNonNull(writes, "writes");
        DeviceRecord active = liveActive(roster);
        List<DeviceHomeUiState.Choice> choices = new ArrayList<>(roster.devices().size());
        for (DeviceRecord record : roster.sorted()) {
            choices.add(choice(record, roster.activeId()));
        }
        if (active == null) {
            boolean known = !choices.isEmpty();
            return new DeviceHomeUiState("", "", choices, List.of(),
                    BatterySnapshot.unknown(), R.string.device_state_idle, false,
                    R.string.device_home_empty_title,
                    known ? R.string.device_home_disconnected_body
                            : R.string.device_home_empty_body);
        }

        Map<DeviceCapabilityGroup, DeviceEntry> entryByGroup = new LinkedHashMap<>();
        for (DeviceEntry entry : entries) {
            entryByGroup.put(entry.group(), entry);
        }
        Optional<DeviceProfileView> profile = catalog.model(active.id().modelId());
        boolean wearSupported = profile.map(p -> p.supports(DeviceCapabilityIds.WEAR_DETECT))
                .orElse(false);
        Map<String, DeviceHomeUiState.ToggleCard> toggleById = new LinkedHashMap<>();
        for (DeviceHomeUiState.ToggleCard toggle : profile
                .map(view -> quickToggles(view, active.state(), writes, active.status().ready()))
                .orElseGet(List::of)) {
            toggleById.put(toggle.capabilityId(), toggle);
        }

        List<DeviceHomeUiState.Section> sections = new ArrayList<>(SPECS.size());
        for (Spec spec : SPECS) {
            List<DeviceHomeUiState.Card> cards = new ArrayList<>();
            if (spec.titleRes() == R.string.device_section_more) {
                // 「更多」固定「查找设备」页面卡（对齐音频管家：查找设备是设备页的功能入口）。
                cards.add(new DeviceHomeUiState.PageCard(PAGE_FIND_DEVICE,
                        R.string.find_device_title, R.drawable.ic_hw_find,
                        DeviceHomeUiState.CardSize.HALF));
            } else if (spec.titleRes() == R.string.device_section_wear && wearSupported) {
                // 「佩戴」改为独立「佩戴设置」入口（不再在首页内联开关）。
                cards.add(new DeviceHomeUiState.PageCard(PAGE_WEAR_SETTINGS,
                        R.string.wear_settings_title, R.drawable.ic_hw_wear,
                        DeviceHomeUiState.CardSize.HALF));
            }
            for (String id : spec.toggleIds()) {
                DeviceHomeUiState.ToggleCard toggle = toggleById.get(id);
                if (toggle != null) {
                    cards.add(toggle);
                }
            }
            for (DeviceCapabilityGroup group : spec.groups()) {
                DeviceEntry entry = entryByGroup.get(group);
                if (entry != null) {
                    cards.add(new DeviceHomeUiState.EntryCard(group,
                            DeviceGroupLabels.title(group), DeviceGroupLabels.icon(group),
                            DeviceHomeUiState.CardSize.HALF));
                }
            }
            sections.add(new DeviceHomeUiState.Section(spec.titleRes(), reflow(cards)));
        }

        BatterySnapshot battery = active.status().ready()
                ? active.facts().battery()
                : BatterySnapshot.unknown();

        return new DeviceHomeUiState(nameOf(active), subtitle(active), choices, sections,
                battery, statusText(active), active.status().ready(),
                R.string.device_home_empty_title, R.string.device_home_empty_body);
    }

    /**
     * 版块卡片排布（单双数策略）：
     * <ul>
     *   <li>偶数张：全部两列排（一行两张）；</li>
     *   <li>奇数张：最上方单独一张整列卡，其余两列排。</li>
     * </ul>
     * 尺寸只在这里统一决定，构造期给的大小一律被覆盖。
     */
    @NonNull
    private static List<DeviceHomeUiState.Card> reflow(
            @NonNull List<DeviceHomeUiState.Card> cards) {
        int count = cards.size();
        if (count == 0) {
            return cards;
        }
        boolean odd = count % 2 == 1;
        List<DeviceHomeUiState.Card> laid = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            DeviceHomeUiState.CardSize size = odd && index == 0
                    ? DeviceHomeUiState.CardSize.FULL
                    : DeviceHomeUiState.CardSize.HALF;
            laid.add(withSize(cards.get(index), size));
        }
        return laid;
    }

    @NonNull
    private static DeviceHomeUiState.Card withSize(@NonNull DeviceHomeUiState.Card card,
                                                   @NonNull DeviceHomeUiState.CardSize size) {
        if (card instanceof DeviceHomeUiState.EntryCard entry) {
            return new DeviceHomeUiState.EntryCard(entry.group(), entry.titleRes(),
                    entry.iconRes(), size);
        }
        if (card instanceof DeviceHomeUiState.ToggleCard toggle) {
            return new DeviceHomeUiState.ToggleCard(toggle.capabilityId(), toggle.title(),
                    toggle.iconRes(), toggle.checked(), toggle.enabled(), toggle.pending(), size);
        }
        if (card instanceof DeviceHomeUiState.PageCard page) {
            return new DeviceHomeUiState.PageCard(page.pageId(), page.titleRes(),
                    page.iconRes(), size);
        }
        throw new IllegalStateException("未知卡片类型: " + card);
    }

    @NonNull
    private static List<DeviceHomeUiState.ToggleCard> quickToggles(
            @NonNull DeviceProfileView profile, @NonNull DeviceState state,
            @NonNull Map<String, DeviceHomeUiState.WriteSlot> writes, boolean interactive) {
        List<DeviceHomeUiState.ToggleCard> out = new ArrayList<>();
        for (String id : QUICK_TOGGLE_IDS) {
            if (!profile.supports(id)) {
                continue;
            }
            DeviceCapabilityView capability = profile.capability(id);
            if (capability.kind() != DeviceCapabilityKind.TOGGLE) {
                continue;
            }
            // 与设置页同一套呈现裁定：reported=设备上报值，pending=写入槽里的本次目标值，
            // interactive=连接就绪。位置优先用户目标（SENDING/PROCESSING/CONFIRMED），
            // in-flight 时 enabled=false（禁用重复点击）。
            Optional<Boolean> reported = toggle(state, id);
            DeviceHomeUiState.WriteSlot slot =
                    writes.getOrDefault(id, DeviceHomeUiState.WriteSlot.idle());
            TogglePresentation presentation = TogglePresentation.of(
                    slot.state(), reported, slot.toggleValue(), interactive);
            out.add(new DeviceHomeUiState.ToggleCard(id, capability.title(),
                    DeviceGroupLabels.toggleIcon(id), presentation.position(),
                    presentation.enabled(), presentation.writeState().inFlight(),
                    DeviceHomeUiState.CardSize.FULL));
        }
        return out;
    }

    @NonNull
    private static Optional<Boolean> toggle(@NonNull DeviceState state, @NonNull String id) {
        return state.value(id).flatMap(value -> value instanceof ControlValue.Toggle toggle
                ? Optional.of(toggle.enabled()) : Optional.empty());
    }

    @NonNull
    private static DeviceRecord liveActive(@NonNull DeviceRoster roster) {
        Optional<DeviceId> activeId = roster.activeId();
        if (activeId.isEmpty()) {
            return null;
        }
        DeviceRecord record = roster.find(activeId.get()).orElse(null);
        return record != null && record.live() ? record : null;
    }

    @StringRes
    private static int statusText(@NonNull DeviceRecord record) {
        ConnectionState connection = record.status().connection();
        return switch (connection) {
            case READY -> R.string.device_state_ready;
            case CONNECTING -> R.string.device_state_connecting;
            case DISCONNECTED -> R.string.device_state_disconnected;
            case IDLE -> R.string.device_state_idle;
        };
    }

    @NonNull
    private static String nameOf(@NonNull DeviceRecord record) {
        String classic = record.identity().classicName();
        if (!classic.isEmpty()) {
            return classic;
        }
        String ble = record.identity().bleName();
        return ble.isEmpty() ? record.displayName() : ble;
    }

    @NonNull
    private String subtitle(@NonNull DeviceRecord record) {
        return record.id().displayAddress() + DeviceFormats.META_SEPARATOR
                + modelName(record.id().modelId());
    }

    @NonNull
    private DeviceHomeUiState.Choice choice(@NonNull DeviceRecord record,
                                            @NonNull Optional<DeviceId> activeId) {
        boolean selected = activeId.map(id -> id.equals(record.id())).orElse(false);
        return new DeviceHomeUiState.Choice(
                record.id().key(),
                nameOf(record),
                record.id().displayAddress(),
                modelName(record.id().modelId()),
                selected && record.live());
    }

    @NonNull
    private String modelName(@NonNull String modelId) {
        for (DeviceProfileView profile : catalog.models()) {
            if (profile.modelId().equals(modelId)) {
                return profile.displayName();
            }
        }
        return modelId;
    }
}
