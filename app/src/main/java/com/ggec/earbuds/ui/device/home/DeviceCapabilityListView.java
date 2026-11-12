package com.ggec.earbuds.ui.device.home;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceControlOutcome;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.TogglePresentation;
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
 * 设备首页的能力分区列表：把 {@link DeviceHomeUiState.Section} 按分组顺序渲染成分区标题 + 行。
 *
 * <p>行控件由行类型决定（开关 / 可选项 / 跳转），不再有"功能入口"与"快捷开关"两套写死清单。
 * 开关只要存在就必须可交互：值未知只把值显示成「未上报」，不取消控件——写入是绝对设定语义，
 * 不需要已知当前值。用户本次的选择作为「未确认」只留在视图层（{@link #pending}），
 * 写入失败时丢弃并重建回未确认呈现。</p>
 *
 * <p>结构或值变化时重建一次，避免每次快照刷新都重排视图。</p>
 */
public final class DeviceCapabilityListView {

    public interface Listener {
        void onToggle(@NonNull String capabilityId, boolean checked);

        void onSelectOption(@NonNull String capabilityId, @NonNull String optionId);

        void onOpenGroup(@NonNull DeviceCapabilityGroup group);
    }

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final YoComponents components;
    private final Listener listener;
    private final LinearLayout column;
    private final List<Runnable> releases = new ArrayList<>();
    /** 设备未上报时用户在视图层的未确认选择；不写回设备状态。 */
    private final Map<String, Boolean> pending = new HashMap<>();

    private String signature = "";
    private List<DeviceHomeUiState.Section> lastSections = List.of();
    private boolean lastInteractive;

    public DeviceCapabilityListView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                                    @NonNull YoComponents components, @NonNull Listener listener) {
        this.activity = activity;
        this.theme = theme;
        this.components = components;
        this.listener = listener;
        this.column = new LinearLayout(activity);
        this.column.setOrientation(LinearLayout.VERTICAL);
        components.own(this::releaseAll);
    }

    @NonNull
    public View view() {
        return column;
    }

    public void render(@NonNull List<DeviceHomeUiState.Section> sections, boolean interactive) {
        lastSections = sections;
        lastInteractive = interactive;
        String next = signatureOf(sections, interactive);
        if (next.equals(signature)) {
            return;
        }
        signature = next;
        column.removeAllViews();
        releaseAll();
        for (DeviceHomeUiState.Section section : sections) {
            column.addView(label(section.titleRes()), matchWrap());
            column.addView(sectionList(section, interactive), matchWrap());
        }
    }

    /** 写入落地结果：失败时丢弃未确认选择并重建，开关回到「未上报」呈现。 */
    public void onControlOutcome(@NonNull DeviceControlOutcome outcome) {
        if (outcome.succeeded()) {
            return;
        }
        if (pending.remove(outcome.capabilityId()) == null) {
            return;
        }
        signature = "";
        render(lastSections, lastInteractive);
    }

    private View sectionList(@NonNull DeviceHomeUiState.Section section, boolean interactive) {
        YoList.Builder builder = YoList.create(activity);
        List<DeviceHomeUiState.Row> rows = new ArrayList<>(section.rows());
        for (DeviceHomeUiState.Row row : rows) {
            addItem(builder, row, interactive);
        }
        YoList list = builder.build();
        releases.add(list::destroy);
        list.setOnItemClickListener(index -> {
            if (index < 0 || index >= rows.size()) {
                return;
            }
            openRow(rows.get(index), section.group(), interactive);
        });
        FrameLayout host = new FrameLayout(activity);
        host.addView(list.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return host;
    }

    private void addItem(@NonNull YoList.Builder builder, @NonNull DeviceHomeUiState.Row row,
                         boolean interactive) {
        switch (row) {
            case DeviceHomeUiState.Row.Toggle toggle -> addToggle(builder, toggle, interactive);
            case DeviceHomeUiState.Row.Choice choice -> addChoice(builder, choice, interactive);
            case DeviceHomeUiState.Row.Link link -> builder.addItem(
                    ListPrefixSpec.none(),
                    ListTextSpec.of(link.title()),
                    interactive ? ListSuffixSpec.arrow() : ListSuffixSpec.none());
        }
    }

    private void addToggle(@NonNull YoList.Builder builder,
                           @NonNull DeviceHomeUiState.Row.Toggle toggle, boolean interactive) {
        TogglePresentation presentation = TogglePresentation.of(toggle.checked(),
                Optional.ofNullable(pending.get(toggle.capabilityId())), interactive);
        ListTextSpec text = presentation.unknown()
                ? ListTextSpec.of(toggle.title(),
                        activity.getString(R.string.device_value_unknown))
                : ListTextSpec.of(toggle.title());
        if (presentation.enabled()) {
            builder.addItem(ListPrefixSpec.none(), text,
                    ListSuffixSpec.switch_(presentation.position(), next -> {
                        pending.put(toggle.capabilityId(), next);
                        listener.onToggle(toggle.capabilityId(), next);
                    }));
            return;
        }
        YoSwitch readOnly = YoSwitch.create(activity)
                .setChecked(presentation.position())
                .setEnabled(false)
                .build();
        releases.add(readOnly::destroy);
        builder.addItem(ListPrefixSpec.none(),
                ListTextSpec.colored(toggle.title(), theme.text3),
                ListSuffixSpec.custom(readOnly.getView()));
    }

    private void addChoice(@NonNull YoList.Builder builder,
                           @NonNull DeviceHomeUiState.Row.Choice choice, boolean interactive) {
        String value = choice.value().orElse(activity.getString(R.string.device_value_unknown));
        if (interactive) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(choice.title()),
                    ListSuffixSpec.textAndArrow(value));
            return;
        }
        builder.addItem(ListPrefixSpec.none(),
                ListTextSpec.colored(choice.title(), theme.text3),
                ListSuffixSpec.text(value, theme.text3));
    }

    private void openRow(@NonNull DeviceHomeUiState.Row row,
                         @NonNull DeviceCapabilityGroup group, boolean interactive) {
        switch (row) {
            case DeviceHomeUiState.Row.Toggle ignored -> {
                // 开关由开关自身回调，行点击不再重复写入。
            }
            case DeviceHomeUiState.Row.Choice choice -> {
                if (interactive) {
                    pickOption(choice);
                }
            }
            case DeviceHomeUiState.Row.Link ignored -> listener.onOpenGroup(group);
        }
    }

    private void pickOption(@NonNull DeviceHomeUiState.Row.Choice choice) {
        List<CapabilityOption> options = choice.options();
        if (options.isEmpty()) {
            return;
        }
        String[] labels = new String[options.size()];
        for (int index = 0; index < options.size(); index++) {
            labels[index] = options.get(index).title();
        }
        new AlertDialog.Builder(activity)
                .setTitle(choice.title())
                .setItems(labels, (dialog, which) -> {
                    CapabilityOption picked = options.get(which);
                    listener.onSelectOption(choice.capabilityId(), picked.id());
                })
                .show();
    }

    private View label(int titleRes) {
        TextView label = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        label.setText(titleRes);
        label.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_L),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XS));
        return label;
    }

    @NonNull
    private String signatureOf(@NonNull List<DeviceHomeUiState.Section> sections, boolean interactive) {
        StringBuilder builder = new StringBuilder().append(interactive)
                .append("|pending=").append(new HashMap<>(pending));
        for (DeviceHomeUiState.Section section : sections) {
            builder.append('|').append(section.group()).append(':');
            for (DeviceHomeUiState.Row row : section.rows()) {
                builder.append(row.getClass().getSimpleName())
                        .append('#').append(row.capabilityId())
                        .append('=');
                switch (row) {
                    case DeviceHomeUiState.Row.Toggle toggle ->
                            builder.append(toggle.checked().map(String::valueOf).orElse("-"));
                    case DeviceHomeUiState.Row.Choice choice ->
                            builder.append(choice.value().orElse("-")).append('/')
                                    .append(choice.options().size());
                    case DeviceHomeUiState.Row.Link link -> builder.append(link.title());
                }
                builder.append(';');
            }
        }
        return builder.toString();
    }

    private void releaseAll() {
        for (Runnable release : releases) {
            release.run();
        }
        releases.clear();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
