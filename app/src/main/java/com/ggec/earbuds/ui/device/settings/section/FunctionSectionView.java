package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.controls.YoSwitch;

import java.util.ArrayList;
import java.util.List;

/** 功能设置：开关用 YoList，查找耳机用按钮卡。 */
public final class FunctionSectionView implements SettingsSectionView {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;
    private final SettingsChrome chrome;
    private final LinearLayout content;
    private final List<Runnable> releases = new ArrayList<>();

    public FunctionSectionView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
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
        List<DeviceSettingRow.Toggle> switches = new ArrayList<>();
        List<DeviceSettingRow.Action> findButtons = new ArrayList<>();
        for (DeviceSettingRow row : state.rows()) {
            if (row instanceof DeviceSettingRow.Toggle toggle) {
                switches.add(toggle);
            } else if (row instanceof DeviceSettingRow.Action action) {
                findButtons.add(action);
            }
        }
        if (!switches.isEmpty()) {
            addSwitchList(switches, interactive);
        }
        if (!findButtons.isEmpty()) {
            content.addView(chrome.label(R.string.device_section_find), chrome.matchWrap());
            LinearLayout card = chrome.card();
            LinearLayout row = SettingsChrome.horizontal(activity);
            for (DeviceSettingRow.Action item : findButtons) {
                YoButton button = chrome.button(item.titleRes(), () -> viewModel.onAction(item.id()));
                button.setEnabled(interactive);
                if (!interactive) {
                    button.setOnClickListener(null);
                }
                releases.add(button::destroy);
                row.addView(button.getView(), chrome.weight());
            }
            card.addView(row, chrome.matchWrap());
            chrome.addCard(content, card);
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

    private void addSwitchList(List<DeviceSettingRow.Toggle> switches, boolean interactive) {
        YoList.Builder builder = YoList.create(activity);
        for (DeviceSettingRow.Toggle row : switches) {
            String title = activity.getString(row.titleRes());
            if (interactive) {
                builder.addItem(ListPrefixSpec.none(),
                        ListTextSpec.of(title),
                        ListSuffixSpec.switch_(row.checked(),
                                checked -> viewModel.onToggle(row.id(), checked)));
            } else {
                YoSwitch sw = YoSwitch.create(activity)
                        .setChecked(row.checked())
                        .setEnabled(false)
                        .build();
                releases.add(sw::destroy);
                builder.addItem(ListPrefixSpec.none(),
                        ListTextSpec.colored(title, theme.text3),
                        ListSuffixSpec.custom(sw.getView()));
            }
        }
        YoList list = builder.build();
        releases.add(list::destroy);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        content.addView(list.getView(), params);
    }
}
