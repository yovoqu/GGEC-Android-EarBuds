package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceCapabilityIds;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.component.DeviceInsets;
import com.ggec.earbuds.ui.device.TogglePresentation;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 「本机设置（FUNCTION）/ 音效设置（MUSIC）」二级页渲染：全部用 YoUI 原生 {@link YoList} 组合。
 *
 * <p>口径（本轮）：分区标题（灰小字）+ 原生 {@link YoList} 卡片；选择型能力（官方预置音效 / 音质）
 * 直接用 {@code YoList} 的<b>单选模式</b>（{@code setSelectable(true)} +
 * {@link YoList.SelectionIndicator#RADIO}），不再自绘单选圈；开关型能力用
 * {@link ListSuffixSpec#switch_}；入口型（自定义均衡器）用 {@link ListSuffixSpec#textAndArrow}。
 * 本类只做「分组 + 选控件」，不自造任何控件。</p>
 */
public final class AlignedSettingsView implements SettingsSectionView {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final SettingsInteractionListener listener;
    private final DeviceCapabilityGroup group;

    private final LinearLayout content;
    private YoComponents components = new YoComponents();
    private final List<Runnable> releases = new ArrayList<>();
    private CustomEqSheet customEqSheet;
    private DeviceSettingsUiState rendered;
    private boolean firstSection;

    public AlignedSettingsView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                               @NonNull SettingsInteractionListener listener,
                               @NonNull DeviceCapabilityGroup group) {
        this.activity = activity;
        this.theme = theme;
        this.listener = listener;
        this.group = group;
        this.content = new LinearLayout(activity);
        this.content.setOrientation(LinearLayout.VERTICAL);
    }

    @NonNull
    @Override
    public View view() {
        return content;
    }

    @Override
    public void render(@NonNull DeviceSettingsUiState state) {
        if (state.equals(rendered)) {
            return;
        }
        rendered = state;
        content.removeAllViews();
        release();
        components.close();
        components = new YoComponents();
        firstSection = true;

        if (group == DeviceCapabilityGroup.MUSIC) {
            renderMusic(state);
        } else {
            renderFunction(state);
        }
    }

    @Override
    public void destroy() {
        if (customEqSheet != null) {
            customEqSheet.destroy();
            customEqSheet = null;
        }
        components.close();
        content.removeAllViews();
        release();
        rendered = null;
    }

    private void release() {
        for (Runnable release : releases) {
            release.run();
        }
        releases.clear();
    }

    // ---- 音效设置 ----

    private void renderMusic(@NonNull DeviceSettingsUiState state) {
        addChoiceSection(state, R.string.device_settings_section_preset_effect,
                DeviceCapabilityIds.EQ_MODE);
        addCurveSection(state, R.string.device_settings_section_custom_effect,
                DeviceCapabilityIds.CUSTOM_EQ);
        addChoiceSection(state, R.string.device_settings_section_quality,
                DeviceCapabilityIds.SOUND_QUALITY);
        addToggleSection(state, R.string.device_settings_section_advanced,
                List.of(DeviceCapabilityIds.LDAC, DeviceCapabilityIds.VOLUME_ADAPTIVE));
        addLeftovers(state, List.of(DeviceCapabilityIds.EQ_MODE,
                DeviceCapabilityIds.CUSTOM_EQ, DeviceCapabilityIds.SOUND_QUALITY,
                DeviceCapabilityIds.LDAC, DeviceCapabilityIds.VOLUME_ADAPTIVE));
    }

    // ---- 本机设置 ----

    private void renderFunction(@NonNull DeviceSettingsUiState state) {
        addToggleSection(state, R.string.device_settings_section_functions,
                List.of(DeviceCapabilityIds.GAME_MODE, DeviceCapabilityIds.FALL_ALERT,
                        DeviceCapabilityIds.BEGINNER_MODE, DeviceCapabilityIds.INTERPRET));
        addLeftovers(state, List.of(DeviceCapabilityIds.GAME_MODE,
                DeviceCapabilityIds.FALL_ALERT, DeviceCapabilityIds.BEGINNER_MODE,
                DeviceCapabilityIds.INTERPRET));
    }

    // ---- 选择型：YoList 单选模式 ----

    private void addChoiceSection(@NonNull DeviceSettingsUiState state, @StringRes int headerRes,
                                  @NonNull String capabilityId) {
        DeviceSettingRow.Choice row = choiceOf(state, capabilityId);
        if (row == null || row.options().isEmpty()) {
            return;
        }
        addHeader(headerRes);
        List<CapabilityOption> options = row.options();
        YoList list = YoList.create(activity)
                .setSelectable(state.interactive())
                .setSelectionIndicator(YoList.SelectionIndicator.RADIO)
                .build();
        components.own(list::destroy);
        for (CapabilityOption option : options) {
            list.addItem(ListTextSpec.of(option.title(), optionSubtitle(option)));
        }
        // 先写选中态、再挂回调，避免绑定阶段触发一次伪写入。
        int selectedIndex = selectedIndex(options, row, state);
        if (selectedIndex >= 0 && selectedIndex < list.getItemCount()) {
            list.setSelectedStableId(list.getStableIdAt(selectedIndex));
        } else {
            list.clearSelection();
        }
        list.setOnSelectionChangeListener((oldId, newId) -> {
            if (!state.interactive()) {
                return;
            }
            int index = list.indexOfId(newId);
            if (index >= 0 && index < options.size()) {
                listener.onChoice(capabilityId, options.get(index).id());
            }
        });
        addList(list);
    }

    // ---- 入口型：自定义均衡器 ----

    private void addCurveSection(@NonNull DeviceSettingsUiState state, @StringRes int headerRes,
                                 @NonNull String capabilityId) {
        DeviceSettingRow.Curve row = curveOf(state, capabilityId);
        if (row == null) {
            return;
        }
        addHeader(headerRes);
        boolean saved = row.points().isPresent()
                || state.writeOf(capabilityId).curveValue().isPresent();
        String value = displayName(row, state, saved);
        YoList list = YoList.create(activity).build();
        components.own(list::destroy);
        list.addItem(ListPrefixSpec.none(), ListTextSpec.of(row.title()),
                ListSuffixSpec.textAndArrow(value));
        if (state.interactive()) {
            list.setOnItemClickListener(index -> openCustomEq(row));
        }
        addList(list);
    }

    // ---- 开关型：YoList + 原生开关尾槽 ----

    private void addToggleSection(@NonNull DeviceSettingsUiState state, @StringRes int headerRes,
                                  @NonNull List<String> capabilityIds) {
        List<DeviceSettingRow.Toggle> rows = new ArrayList<>();
        for (String id : capabilityIds) {
            DeviceSettingRow.Toggle toggle = toggleOf(state, id);
            if (toggle != null) {
                rows.add(toggle);
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        addHeader(headerRes);
        YoList list = YoList.create(activity).build();
        components.own(list::destroy);
        for (DeviceSettingRow.Toggle row : rows) {
            addToggleItem(list, row, state);
        }
        addList(list);
    }

    private void addToggleItem(@NonNull YoList list, @NonNull DeviceSettingRow.Toggle row,
                               @NonNull DeviceSettingsUiState state) {
        DeviceSettingsUiState.WriteSlot slot = state.writeOf(row.capabilityId());
        TogglePresentation presentation = TogglePresentation.of(slot.state(), row.checked(),
                slot.toggleValue(), state.interactive());
        String subtitle = subtitleFor(row.capabilityId());
        ListSuffixSpec suffix = ListSuffixSpec.switch_(presentation.position(),
                presentation.enabled()
                        ? next -> listener.onToggle(row.capabilityId(), next)
                        : null);
        list.addItem(ListPrefixSpec.none(), ListTextSpec.of(row.title(), subtitle), suffix);
    }

    // ---- 兜底：未被命中的行仍可见 ----

    private void addLeftovers(@NonNull DeviceSettingsUiState state,
                              @NonNull List<String> consumedIds) {
        List<DeviceSettingRow> leftovers = new ArrayList<>();
        for (DeviceSettingRow row : state.rows()) {
            if (row instanceof DeviceSettingRow.Entry entry) {
                String id = entryId(entry);
                if (id == null || !consumedIds.contains(id)) {
                    leftovers.add(row);
                }
            }
        }
        if (leftovers.isEmpty()) {
            return;
        }
        addHeader(R.string.device_settings_section_other);
        YoList list = YoList.create(activity).build();
        components.own(list::destroy);
        for (DeviceSettingRow row : leftovers) {
            if (row instanceof DeviceSettingRow.Toggle toggle) {
                addToggleItem(list, toggle, state);
            } else if (row instanceof DeviceSettingRow.Notice notice) {
                list.addItem(ListPrefixSpec.none(),
                        ListTextSpec.colored(activity.getString(notice.textRes()), theme.text3),
                        ListSuffixSpec.none());
            } else if (row instanceof DeviceSettingRow.Info info) {
                list.addItem(ListPrefixSpec.none(),
                        ListTextSpec.of(activity.getString(info.titleRes())),
                        ListSuffixSpec.text(info.value()));
            } else if (row instanceof DeviceSettingRow.Unsupported unsupported) {
                list.addItem(ListPrefixSpec.none(),
                        ListTextSpec.colored(unsupported.title(), theme.text3),
                        ListSuffixSpec.text(
                                activity.getString(R.string.device_capability_unsupported),
                                theme.text3));
            } else if (row instanceof DeviceSettingRow.Choice choice) {
                list.addItem(ListPrefixSpec.none(), ListTextSpec.of(choice.title()),
                        ListSuffixSpec.text(choice.value().orElse("")));
            } else if (row instanceof DeviceSettingRow.Curve curve) {
                list.addItem(ListPrefixSpec.none(), ListTextSpec.of(curve.title()),
                        ListSuffixSpec.text(""));
            }
        }
        addList(list);
    }

    // ---- 版式 ----

    private void addHeader(@StringRes int headerRes) {
        TextView header = theme.text(DeviceTheme.TEXT_SUBHEAD, theme.text3);
        header.setText(headerRes);
        int side = DeviceInsets.page(theme);
        header.setPadding(side, theme.dp(firstSection ? DeviceTheme.SPACE_S : DeviceTheme.SPACE_XL),
                side, theme.dp(DeviceTheme.SPACE_XS));
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        firstSection = false;
    }

    private void addList(@NonNull YoList list) {
        content.addView(list.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // ---- 行数据检索 ----

    private int selectedIndex(@NonNull List<CapabilityOption> options,
                              @NonNull DeviceSettingRow.Choice row,
                              @NonNull DeviceSettingsUiState state) {
        Optional<String> pendingId = state.writeOf(row.capabilityId()).choiceValue();
        if (pendingId.isPresent()) {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).id().equals(pendingId.get())) {
                    return i;
                }
            }
        }
        String title = row.value().orElse(null);
        if (title != null) {
            for (int i = 0; i < options.size(); i++) {
                if (options.get(i).title().equals(title)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Nullable
    private static DeviceSettingRow.Choice choiceOf(@NonNull DeviceSettingsUiState state,
                                                    @NonNull String capabilityId) {
        for (DeviceSettingRow row : state.rows()) {
            if (row instanceof DeviceSettingRow.Choice choice
                    && capabilityId.equals(choice.capabilityId())) {
                return choice;
            }
        }
        return null;
    }

    @Nullable
    private static DeviceSettingRow.Curve curveOf(@NonNull DeviceSettingsUiState state,
                                                  @NonNull String capabilityId) {
        for (DeviceSettingRow row : state.rows()) {
            if (row instanceof DeviceSettingRow.Curve curve
                    && capabilityId.equals(curve.capabilityId())) {
                return curve;
            }
        }
        return null;
    }

    @Nullable
    private static DeviceSettingRow.Toggle toggleOf(@NonNull DeviceSettingsUiState state,
                                                    @NonNull String capabilityId) {
        for (DeviceSettingRow row : state.rows()) {
            if (row instanceof DeviceSettingRow.Toggle toggle
                    && capabilityId.equals(toggle.capabilityId())) {
                return toggle;
            }
        }
        return null;
    }

    @Nullable
    private static DeviceSettingRow.Action actionOf(@NonNull DeviceSettingsUiState state,
                                                    @NonNull String capabilityId) {
        for (DeviceSettingRow row : state.rows()) {
            if (row instanceof DeviceSettingRow.Action action
                    && capabilityId.equals(action.capabilityId())) {
                return action;
            }
        }
        return null;
    }

    @Nullable
    private static String entryId(@NonNull DeviceSettingRow.Entry entry) {
        if (entry instanceof DeviceSettingRow.Toggle toggle) {
            return toggle.capabilityId();
        }
        if (entry instanceof DeviceSettingRow.Choice choice) {
            return choice.capabilityId();
        }
        if (entry instanceof DeviceSettingRow.Curve curve) {
            return curve.capabilityId();
        }
        if (entry instanceof DeviceSettingRow.Unsupported unsupported) {
            return unsupported.capabilityId();
        }
        return null;
    }

    @Nullable
    private String optionSubtitle(@NonNull CapabilityOption option) {
        return switch (option.id()) {
            case "high_quality" -> activity.getString(R.string.device_settings_subtitle_high_quality);
            case "high_endurance" ->
                    activity.getString(R.string.device_settings_subtitle_endurance);
            default -> null;
        };
    }

    @Nullable
    private String subtitleFor(@NonNull String capabilityId) {
        return switch (capabilityId) {
            case DeviceCapabilityIds.GAME_MODE ->
                    activity.getString(R.string.device_settings_subtitle_game_mode);
            case DeviceCapabilityIds.FALL_ALERT ->
                    activity.getString(R.string.device_settings_subtitle_fall_alert);
            case DeviceCapabilityIds.BEGINNER_MODE ->
                    activity.getString(R.string.device_settings_subtitle_beginner_mode);
            case DeviceCapabilityIds.INTERPRET ->
                    activity.getString(R.string.device_settings_subtitle_interpret);
            case DeviceCapabilityIds.LDAC ->
                    activity.getString(R.string.device_settings_subtitle_ldac);
            case DeviceCapabilityIds.VOLUME_ADAPTIVE ->
                    activity.getString(R.string.device_settings_subtitle_volume_adaptive);
            default -> null;
        };
    }

    private String displayName(@NonNull DeviceSettingRow.Curve row,
                               @NonNull DeviceSettingsUiState state, boolean saved) {
        Optional<String> name = state.writeOf(row.capabilityId()).curveName();
        if (name.isEmpty()) {
            name = row.name();
        }
        return name.orElse(saved ? activity.getString(R.string.device_eq_custom) : "");
    }

    private void openCustomEq(@NonNull DeviceSettingRow.Curve row) {
        if (customEqSheet != null) {
            return;
        }
        DeviceSettingsUiState state = rendered;
        Optional<List<Integer>> pending = state == null
                ? Optional.empty()
                : state.writeOf(row.capabilityId()).curveValue();
        Optional<String> initialName = state == null
                ? row.name()
                : state.writeOf(row.capabilityId()).curveName().or(() -> row.name());
        customEqSheet = CustomEqSheet.show(activity, theme, row.options(), row.range(),
                row.points(), pending, initialName, new CustomEqSheet.Listener() {
                    @Override
                    public void onSaved(@NonNull String name, @NonNull List<Integer> edited) {
                        listener.onCurve(row.capabilityId(), name, edited);
                    }

                    @Override
                    public void onDismissed() {
                        customEqSheet = null;
                    }
                }).orElse(null);
    }
}
