package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.ui.device.CurveEditorSeed;
import com.ggec.earbuds.ui.device.DeviceControlOutcome;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.TogglePresentation;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoSwitch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 设置页行渲染宿主：按行的两族分派。
 *
 * <p>{@link DeviceSettingRow.Entry} 聚成一个 YoList 卡片；{@link DeviceSettingRow.Component}
 * 交给自己的专门组件（滑杆 / 动作卡 / 录音列表 / 双连接设备列表）。本类不判断能力 id，
 * 路由已在 {@code SettingsSectionFactory} 完成。</p>
 *
 * <p>与「值未知」相关的裁定统一在此落地：开关、开关位置、曲线入口都**不因值未知而消失或禁用**；
 * 用户本次的选择/编辑结果作为视图层「未确认」状态保存，写入失败时丢弃并重建呈现。</p>
 */
public final class SettingsRowsView implements SettingsSectionView {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;
    private final SettingsChrome chrome;
    private final LinearLayout content;
    private final List<Runnable> releases = new ArrayList<>();
    /** 设备未上报时用户在视图层的未确认开关位置；不写回设备状态。 */
    private final Map<String, Boolean> pendingToggles = new HashMap<>();
    /** 设备未上报时用户在视图层的未确认曲线；仅用于「已保存」呈现与编辑器回填。 */
    private final Map<String, List<Integer>> curveDrafts = new HashMap<>();
    private final List<ControlOutcomeSink> outcomeSinks = new ArrayList<>();

    private YoList.Builder pendingBuilder;
    private List<DeviceSettingRow.Entry> pendingRows = new ArrayList<>();
    private CustomEqSheet customEqSheet;
    private DeviceSettingsUiState lastState;

    public SettingsRowsView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                            @NonNull DeviceSettingsViewModel viewModel) {
        this.activity = activity;
        this.theme = theme;
        this.viewModel = viewModel;
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
        lastState = state;
        content.removeAllViews();
        release();
        for (DeviceSettingRow row : state.rows()) {
            switch (row) {
                case DeviceSettingRow.Entry entry -> appendEntry(entry, state.interactive());
                case DeviceSettingRow.Component component -> {
                    flushPending(state.interactive());
                    addComponent(component, state);
                }
            }
        }
        flushPending(state.interactive());
    }

    /** 写入落地结果：失败时丢弃未确认状态并重建，控件回到「未上报」呈现。 */
    public void onControlOutcome(@NonNull DeviceControlOutcome outcome) {
        if (outcome.succeeded()) {
            return;
        }
        boolean touched = pendingToggles.remove(outcome.capabilityId()) != null;
        touched |= curveDrafts.remove(outcome.capabilityId()) != null;
        for (ControlOutcomeSink sink : outcomeSinks) {
            touched |= sink.onControlOutcome(outcome);
        }
        if (touched && lastState != null) {
            render(lastState);
        }
    }

    @Override
    public void destroy() {
        if (customEqSheet != null) {
            customEqSheet.destroy();
            customEqSheet = null;
        }
        content.removeAllViews();
        release();
    }

    private void release() {
        chrome.releaseAll(releases);
        outcomeSinks.clear();
        pendingBuilder = null;
        pendingRows = new ArrayList<>();
    }

    // ---- 轻行 ----

    private void appendEntry(@NonNull DeviceSettingRow.Entry row, boolean interactive) {
        if (pendingBuilder == null) {
            pendingBuilder = YoList.create(activity);
            pendingRows = new ArrayList<>();
        }
        switch (row) {
            case DeviceSettingRow.Toggle toggle -> addToggle(toggle, interactive);
            case DeviceSettingRow.Choice choice -> addChoice(choice, interactive);
            case DeviceSettingRow.Curve curve -> addCurve(curve, interactive);
            case DeviceSettingRow.Info info -> pendingBuilder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(activity.getString(info.titleRes())),
                    ListSuffixSpec.text(info.value()));
            case DeviceSettingRow.Unsupported unsupported -> pendingBuilder.addItem(
                    ListPrefixSpec.none(),
                    ListTextSpec.colored(unsupported.title(), theme.text3),
                    ListSuffixSpec.text(
                            activity.getString(R.string.device_capability_unsupported),
                            theme.text3));
        }
        pendingRows.add(row);
    }

    private void addToggle(@NonNull DeviceSettingRow.Toggle row, boolean interactive) {
        TogglePresentation presentation = TogglePresentation.of(row.checked(),
                Optional.ofNullable(pendingToggles.get(row.capabilityId())), interactive);
        ListTextSpec text = presentation.unknown()
                ? ListTextSpec.of(row.title(), activity.getString(R.string.device_value_unknown))
                : ListTextSpec.of(row.title());
        if (presentation.enabled()) {
            pendingBuilder.addItem(ListPrefixSpec.none(), text,
                    ListSuffixSpec.switch_(presentation.position(), next -> {
                        pendingToggles.put(row.capabilityId(), next);
                        viewModel.write(row.capabilityId(), new ControlValue.Toggle(next));
                    }));
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

    /** 曲线入口恒定可点：编辑器自带给初值，不依赖设备是否上报过曲线。 */
    private void addCurve(@NonNull DeviceSettingRow.Curve row, boolean interactive) {
        boolean saved = row.points().isPresent() || curveDrafts.containsKey(row.capabilityId());
        String value = saved
                ? activity.getString(R.string.device_eq_custom)
                : activity.getString(R.string.device_value_unknown);
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
        new AlertDialog.Builder(activity)
                .setTitle(row.title())
                .setItems(labels, (dialog, which) -> viewModel.write(row.capabilityId(),
                        new ControlValue.Choice(options.get(which).id())))
                .show();
    }

    private void openCustomEq(@NonNull DeviceSettingRow.Curve row) {
        if (customEqSheet != null) {
            return;
        }
        List<Integer> seed = CurveEditorSeed.of(row.points(),
                Optional.ofNullable(curveDrafts.get(row.capabilityId())));
        customEqSheet = CustomEqSheet.show(activity, theme, seed, new CustomEqSheet.Listener() {
            @Override
            public void onSaved(@NonNull List<Integer> edited) {
                curveDrafts.put(row.capabilityId(), edited);
                viewModel.write(row.capabilityId(), new ControlValue.Curve(edited));
            }

            @Override
            public void onDismissed() {
                customEqSheet = null;
            }
        });
    }

    // ---- 专门组件 ----

    private void addComponent(@NonNull DeviceSettingRow.Component row,
                              @NonNull DeviceSettingsUiState state) {
        switch (row) {
            case DeviceSettingRow.Level level -> {
                SliderCardView card = new SliderCardView(activity, theme, viewModel,
                        level.capabilityId());
                releases.add(card::destroy);
                outcomeSinks.add(card);
                card.bind(level, state.interactive());
                chrome.addCard(content, card.view());
            }
            case DeviceSettingRow.Action action -> {
                ActionCardView card = new ActionCardView(activity, theme, viewModel,
                        state.recordFiles());
                releases.add(card::destroy);
                card.bind(action, state.interactive());
                chrome.addCard(content, card.view());
            }
            case DeviceSettingRow.CapabilityList list -> {
                switch (list.kind()) {
                    case DUAL_CONNECTION -> {
                        DualConnectionCard card = new DualConnectionCard(activity, theme, viewModel);
                        releases.add(card::destroy);
                        card.bind(list.title(), state, state.interactive());
                        chrome.addCard(content, card.view());
                    }
                    case RECORD_FILES -> {
                        RecordFilesCard card = new RecordFilesCard(activity, theme, viewModel);
                        releases.add(card::destroy);
                        card.bind(list.title(), state, state.interactive());
                        chrome.addCard(content, card.view());
                    }
                }
            }
        }
    }
}
