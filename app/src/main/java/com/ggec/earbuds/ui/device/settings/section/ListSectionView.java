package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoSwitch;

import java.util.ArrayList;
import java.util.List;

/** 双连接 / 录音 / 关于设备：按分组用 YoList 呈现。 */
public final class ListSectionView implements SettingsSectionView {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;
    private final SettingsChrome chrome;
    private final LinearLayout content;
    private final List<Runnable> releases = new ArrayList<>();

    public ListSectionView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
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
        content.removeAllViews();
        release();
        boolean interactive = state.interactive();
        YoList.Builder builder = null;
        List<DeviceSettingRow> itemRows = new ArrayList<>();
        for (DeviceSettingRow row : state.rows()) {
            if (row instanceof DeviceSettingRow.Section section) {
                if (builder != null) {
                    flush(builder, itemRows, interactive);
                    builder = null;
                    itemRows = new ArrayList<>();
                }
                content.addView(chrome.label(section.titleRes()), chrome.matchWrap());
                continue;
            }
            if (builder == null) {
                builder = YoList.create(activity);
            }
            addItem(builder, row, interactive);
            itemRows.add(row);
        }
        if (builder != null) {
            flush(builder, itemRows, interactive);
        }
    }

    @Override
    public void destroy() {
        content.removeAllViews();
        release();
    }

    private void release() {
        chrome.releaseAll(releases);
    }

    private void addItem(YoList.Builder builder, DeviceSettingRow row, boolean interactive) {
        if (row instanceof DeviceSettingRow.Info info) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(activity.getString(info.titleRes())),
                    ListSuffixSpec.text(info.value()));
        } else if (row instanceof DeviceSettingRow.Choice choice) {
            String value = choice.valueRes() == 0
                    ? DeviceFormats.UNKNOWN : activity.getString(choice.valueRes());
            String title = activity.getString(choice.titleRes());
            if (interactive) {
                builder.addItem(ListPrefixSpec.none(),
                        ListTextSpec.of(title),
                        ListSuffixSpec.textAndArrow(value));
            } else {
                builder.addItem(ListPrefixSpec.none(),
                        ListTextSpec.colored(title, theme.text3),
                        ListSuffixSpec.text(value, theme.text3));
            }
        } else if (row instanceof DeviceSettingRow.Toggle toggle) {
            String title = activity.getString(toggle.titleRes());
            if (interactive) {
                builder.addItem(ListPrefixSpec.none(),
                        ListTextSpec.of(title),
                        ListSuffixSpec.switch_(toggle.checked(),
                                checked -> viewModel.onToggle(toggle.id(), checked)));
            } else {
                YoSwitch sw = YoSwitch.create(activity)
                        .setChecked(toggle.checked())
                        .setEnabled(false)
                        .build();
                releases.add(sw::destroy);
                builder.addItem(ListPrefixSpec.none(),
                        ListTextSpec.colored(title, theme.text3),
                        ListSuffixSpec.custom(sw.getView()));
            }
        } else if (row instanceof DeviceSettingRow.Action action) {
            String title = activity.getString(action.titleRes());
            if (!interactive) {
                builder.addItem(ListTextSpec.colored(title, theme.text3));
            } else {
                builder.addItem(action.destructive()
                        ? ListTextSpec.destructive(title)
                        : ListTextSpec.of(title));
            }
        }
    }

    private void flush(YoList.Builder builder, List<DeviceSettingRow> itemRows, boolean interactive) {
        YoList list = builder.build();
        releases.add(list::destroy);
        List<DeviceSettingRow> snapshot = new ArrayList<>(itemRows);
        if (interactive) {
            list.setOnItemClickListener(index -> {
                if (index < 0 || index >= snapshot.size()) {
                    return;
                }
                DeviceSettingRow row = snapshot.get(index);
                if (row instanceof DeviceSettingRow.Action action) {
                    viewModel.onAction(action.id());
                }
            });
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        content.addView(list.getView(), params);
    }
}
