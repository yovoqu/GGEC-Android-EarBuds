package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.yohu.youi.api.controls.YoButton;

import java.util.ArrayList;
import java.util.List;

/** 控制设置：按手势分组的左右耳卡片 + 恢复默认。 */
public final class ControlSectionView implements SettingsSectionView {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;
    private final SettingsChrome chrome;
    private final LinearLayout content;
    private final List<Runnable> releases = new ArrayList<>();

    public ControlSectionView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
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
        List<DeviceSettingRow.Choice> group = new ArrayList<>();
        for (DeviceSettingRow row : state.rows()) {
            if (row instanceof DeviceSettingRow.Section section) {
                flushGroup(group, interactive);
                content.addView(chrome.label(section.titleRes()), chrome.matchWrap());
            } else if (row instanceof DeviceSettingRow.Choice choice) {
                group.add(choice);
            } else if (row instanceof DeviceSettingRow.Action action) {
                flushGroup(group, interactive);
                addActionButton(action, interactive);
            }
        }
        flushGroup(group, interactive);
    }

    @Override
    public void destroy() {
        content.removeAllViews();
        release();
    }

    private void release() {
        chrome.releaseAll(releases);
    }

    private void flushGroup(List<DeviceSettingRow.Choice> group, boolean interactive) {
        if (group.isEmpty()) {
            return;
        }
        LinearLayout card = chrome.card();
        LinearLayout row = SettingsChrome.horizontal(activity);
        for (DeviceSettingRow.Choice item : group) {
            row.addView(gestureCell(item, interactive), new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.addView(row, chrome.matchWrap());
        chrome.addCard(content, card);
        group.clear();
    }

    private View gestureCell(DeviceSettingRow.Choice item, boolean interactive) {
        LinearLayout cell = SettingsChrome.vertical(activity);
        cell.setPadding(theme.dp(DeviceTheme.SPACE_XS), theme.dp(DeviceTheme.SPACE_S),
                theme.dp(DeviceTheme.SPACE_XS), theme.dp(DeviceTheme.SPACE_S));
        if (interactive) {
            cell.setBackground(theme.pressable(DeviceTheme.RADIUS_ROW));
            cell.setOnClickListener(v -> pick(item));
        } else {
            cell.setEnabled(false);
            cell.setClickable(false);
        }

        TextView name = theme.text(DeviceTheme.TEXT_CAPTION, interactive ? theme.text3 : theme.hint);
        name.setText(item.titleRes());
        TextView value = theme.text(DeviceTheme.TEXT_BODY, interactive ? theme.text1 : theme.text3,
                DeviceTheme.Weight.MEDIUM);
        value.setText(item.valueRes() == 0
                ? DeviceFormats.UNKNOWN : activity.getString(item.valueRes()));
        cell.addView(name, chrome.matchWrap());
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        cell.addView(value, valueParams);
        return cell;
    }

    private void addActionButton(DeviceSettingRow.Action row, boolean interactive) {
        YoButton button = chrome.button(row.titleRes(), () -> viewModel.onAction(row.id()));
        button.setEnabled(interactive);
        if (!interactive) {
            button.setOnClickListener(null);
        }
        releases.add(button::destroy);
        LinearLayout wrapper = new LinearLayout(activity);
        wrapper.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XS));
        wrapper.addView(button.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(44)));
        content.addView(wrapper, chrome.matchWrap());
    }

    private void pick(DeviceSettingRow.Choice item) {
        List<Integer> options = item.optionRes();
        if (options.isEmpty()) {
            return;
        }
        String[] labels = new String[options.size()];
        for (int index = 0; index < options.size(); index++) {
            labels[index] = activity.getString(options.get(index));
        }
        new AlertDialog.Builder(activity)
                .setItems(labels, (dialog, which) -> viewModel.onChoice(item.id(), which))
                .show();
    }
}
