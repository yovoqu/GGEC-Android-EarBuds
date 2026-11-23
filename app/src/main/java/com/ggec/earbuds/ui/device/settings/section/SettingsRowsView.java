package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.TogglePresentation;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoSwitch;
import com.yohu.youi.api.navigation.YoMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 设置页行渲染宿主：按行的两族分派。
 *
 * <p>{@link DeviceSettingRow.Entry} 聚成一个 YoList 卡片；{@link DeviceSettingRow.Component}
 * 交给自己的专门组件（滑杆 / 动作卡 / 录音列表 / 双连接设备列表）。本类不判断能力 id，
 * 路由已在 {@code SettingsSectionFactory} 完成。</p>
 *
 * <p>本类<b>不持有任何业务状态</b>：开关位置、滑杆位置、曲线是否已保存全部来自
 * {@link DeviceSettingsUiState#writeOf(String)} 与行上的设备事实；「未确认」值、写入中、失败回退
 * 都由 ViewModel 的状态驱动，视图只做投影。重建判据是整份状态的<b>结构等价</b>
 * （{@link DeviceSettingsUiState} 是记录，含行、写入槽与列表事实），不再拼字符串签名。</p>
 */
public final class SettingsRowsView implements SettingsSectionView {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final SettingsInteractionListener listener;
    private final SettingsChrome chrome;
    private final LinearLayout content;
    private final List<Runnable> releases = new ArrayList<>();
    /** 本视图自己创建、需要随视图释放的 YoUI 组件（选项菜单）。 */
    private final YoComponents components = new YoComponents();

    /** 上次真正渲染的输入：结构等价即不重建控件树。 */
    private DeviceSettingsUiState rendered;

    private YoList.Builder pendingBuilder;
    private List<DeviceSettingRow.Entry> pendingRows = new ArrayList<>();
    private CustomEqSheet customEqSheet;

    public SettingsRowsView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                            @NonNull SettingsInteractionListener listener) {
        this.activity = activity;
        this.theme = theme;
        this.listener = listener;
        this.chrome = new SettingsChrome(activity, theme);
        this.content = SettingsChrome.vertical(activity);
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
        for (DeviceSettingRow row : state.rows()) {
            switch (row) {
                case DeviceSettingRow.Entry entry -> appendEntry(entry, state);
                case DeviceSettingRow.Component component -> {
                    flushPending(state.interactive());
                    addComponent(component, state);
                }
            }
        }
        flushPending(state.interactive());
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
        chrome.releaseAll(releases);
        pendingBuilder = null;
        pendingRows = new ArrayList<>();
    }

    // ---- 轻行 ----

    private void appendEntry(@NonNull DeviceSettingRow.Entry row,
                             @NonNull DeviceSettingsUiState state) {
        if (pendingBuilder == null) {
            pendingBuilder = YoList.create(activity);
            pendingRows = new ArrayList<>();
        }
        boolean interactive = state.interactive();
        switch (row) {
            case DeviceSettingRow.Toggle toggle -> addToggle(toggle, state);
            case DeviceSettingRow.Choice choice -> addChoice(choice, interactive);
            case DeviceSettingRow.Curve curve -> addCurve(curve, state);
            case DeviceSettingRow.Info info -> pendingBuilder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(activity.getString(info.titleRes())),
                    ListSuffixSpec.text(info.value()));
            case DeviceSettingRow.Notice notice -> pendingBuilder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.colored(activity.getString(notice.textRes()), theme.text3),
                    ListSuffixSpec.none());
            case DeviceSettingRow.Unsupported unsupported -> pendingBuilder.addItem(
                    ListPrefixSpec.none(),
                    ListTextSpec.colored(unsupported.title(), theme.text3),
                    ListSuffixSpec.text(
                            activity.getString(R.string.device_capability_unsupported),
                            theme.text3));
        }
        pendingRows.add(row);
    }

    private void addToggle(@NonNull DeviceSettingRow.Toggle row,
                           @NonNull DeviceSettingsUiState state) {
        DeviceSettingsUiState.WriteSlot slot = state.writeOf(row.capabilityId());
        TogglePresentation presentation = TogglePresentation.of(slot.state(), row.checked(),
                slot.toggleValue(), state.interactive());
        ListTextSpec text = presentation.unknown()
                ? ListTextSpec.of(row.title(), activity.getString(R.string.device_value_unknown))
                : ListTextSpec.of(row.title());
        if (presentation.enabled()) {
            pendingBuilder.addItem(ListPrefixSpec.none(), text,
                    ListSuffixSpec.switch_(presentation.position(),
                            next -> listener.onToggle(row.capabilityId(), next)));
            return;
        }
        YoSwitch readOnly = YoSwitch.create(activity)
                .setChecked(presentation.position())
                .setEnabled(false)
                .build();
        releases.add(readOnly::destroy);
        pendingBuilder.addItem(ListPrefixSpec.none(),
                ListTextSpec.colored(row.title(), theme.text3),
                ListSuffixSpec.custom(readOnly.getView()));
    }

    private void addChoice(@NonNull DeviceSettingRow.Choice row, boolean interactive) {
        String value = row.value().orElse(activity.getString(R.string.device_value_unknown));
        if (interactive) {
            pendingBuilder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(row.title()),
                    ListSuffixSpec.textAndArrow(value));
            return;
        }
        pendingBuilder.addItem(ListPrefixSpec.none(),
                ListTextSpec.colored(row.title(), theme.text3),
                ListSuffixSpec.text(value, theme.text3));
    }

    /**
     * 曲线入口恒定可点：编辑器初值由描述符派生（段数 = 频段 options，平直值 = range 下界），
     * 不依赖设备是否上报过曲线。描述符缺频段表或值域时工厂根本不会生成该行。
     */
    private void addCurve(@NonNull DeviceSettingRow.Curve row,
                          @NonNull DeviceSettingsUiState state) {
        DeviceSettingsUiState.WriteSlot slot = state.writeOf(row.capabilityId());
        boolean saved = row.points().isPresent() || slot.curveValue().isPresent();
        Optional<String> name = slot.curveName();
        if (name.isEmpty()) {
            name = row.name();
        }
        String value = name.orElse(saved
                ? activity.getString(R.string.device_eq_custom)
                : activity.getString(R.string.device_value_unknown));
        if (state.interactive()) {
            pendingBuilder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(row.title()),
                    ListSuffixSpec.textAndArrow(value));
            return;
        }
        pendingBuilder.addItem(ListPrefixSpec.none(),
                ListTextSpec.colored(row.title(), theme.text3),
                ListSuffixSpec.text(value, theme.text3));
    }

    private void flushPending(boolean interactive) {
        if (pendingBuilder == null) {
            return;
        }
        YoList list = pendingBuilder.build();
        releases.add(list::destroy);
        List<DeviceSettingRow.Entry> rows = List.copyOf(pendingRows);
        list.setOnItemClickListener(index -> {
            if (index < 0 || index >= rows.size()) {
                return;
            }
            onEntryClick(rows.get(index), interactive);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        content.addView(list.getView(), params);
        pendingBuilder = null;
        pendingRows = new ArrayList<>();
    }

    private void onEntryClick(@NonNull DeviceSettingRow.Entry row, boolean interactive) {
        if (!interactive) {
            return;
        }
        switch (row) {
            case DeviceSettingRow.Choice choice -> pickOption(choice);
            case DeviceSettingRow.Curve curve -> openCustomEq(curve);
            case DeviceSettingRow.Toggle toggle -> {
            }
            case DeviceSettingRow.Info info -> {
            }
            case DeviceSettingRow.Notice notice -> {
            }
            case DeviceSettingRow.Unsupported unsupported -> {
            }
        }
    }

    private void pickOption(@NonNull DeviceSettingRow.Choice row) {
        List<CapabilityOption> options = row.options();
        if (options.isEmpty()) {
            return;
        }
        String[] labels = new String[options.size()];
        for (int index = 0; index < options.size(); index++) {
            labels[index] = options.get(index).title();
        }
        YoMenu.Builder builder = YoMenu.create(activity)
                .setAnchorView(activity.findViewById(android.R.id.content));
        for (int index = 0; index < labels.length; index++) {
            builder.addItem(index, labels[index]);
        }
        YoMenu menu = builder
                .setOnItemClickListener(id -> {
                    if (id < 0 || id >= options.size()) {
                        return;
                    }
                    listener.onChoice(row.capabilityId(), options.get(id).id());
                })
                .build();
        components.own(menu::destroy);
        menu.show();
    }

    private void openCustomEq(@NonNull DeviceSettingRow.Curve row) {
        if (customEqSheet != null) {
            return;
        }
        Optional<List<Integer>> pending = rendered == null
                ? Optional.empty()
                : rendered.writeOf(row.capabilityId()).curveValue();
        Optional<String> initialName = rendered == null
                ? row.name()
                : rendered.writeOf(row.capabilityId()).curveName().or(() -> row.name());
        // 段数/值域/编辑起点全部由描述符决定：描述符缺频段表或增益值域时面板不打开（不可用）。
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

    // ---- 专门组件 ----

    private void addComponent(@NonNull DeviceSettingRow.Component row,
                              @NonNull DeviceSettingsUiState state) {
        switch (row) {
            case DeviceSettingRow.Level level -> {
                SliderCardView card = new SliderCardView(activity, theme, listener,
                        level.capabilityId());
                releases.add(card::destroy);
                card.bind(level, state.writeOf(level.capabilityId()), state.interactive());
                chrome.addCard(content, card.view());
            }
            case DeviceSettingRow.Action action -> {
                ActionCardView card = new ActionCardView(activity, theme, listener,
                        state.recordFiles());
                releases.add(card::destroy);
                card.bind(action, state.interactive());
                chrome.addCard(content, card.view());
            }
            case DeviceSettingRow.CapabilityList list -> {
                switch (list.kind()) {
                    case DUAL_CONNECTION -> {
                        DualConnectionCard card = new DualConnectionCard(activity, theme, listener);
                        releases.add(card::destroy);
                        card.bind(list.title(), state, state.interactive());
                        chrome.addCard(content, card.view());
                    }
                    case RECORD_FILES -> {
                        RecordFilesCard card = new RecordFilesCard(activity, theme, listener);
                        releases.add(card::destroy);
                        card.bind(list.title(), state, state.interactive());
                        chrome.addCard(content, card.view());
                    }
                }
            }
        }
    }
}
