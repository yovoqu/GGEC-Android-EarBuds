package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * 音乐与音效：音效模式（预置 + 自定义同一单选组）+ 添加自定义音效 + 音质模式 + 开关。
 *
 * <p>交互要点：单选切换只更新选中态，不重建任何列表；只有结构变化（新增自定义音效、
 * 名称变化、能力变化）才重建，避免整页抖动。</p>
 */
public final class MusicSectionView implements SettingsSectionView {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;
    private final SettingsChrome chrome;
    private final LinearLayout content;
    private final List<Runnable> releases = new ArrayList<>();

    private DeviceSettingsUiState.MusicPanel music = emptyMusic();
    private CustomEqSheet customEqSheet;

    private boolean built;
    private String signature = "";
    private boolean updatingSelection;
    private YoList presetList;
    private YoList qualityList;

    public MusicSectionView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
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
        music = state.music() == null ? emptyMusic() : state.music();
        String next = signatureOf(music);
        if (built && next.equals(signature)) {
            updateSelections();
            return;
        }
        signature = next;
        built = true;
        content.removeAllViews();
        release();
        build();
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
        presetList = null;
        qualityList = null;
    }

    private void build() {
        content.addView(chrome.label(R.string.device_eq_modes_section), chrome.matchWrap());
        addModeList();
        content.addView(chrome.label(R.string.device_eq_add_section), chrome.matchWrap());
        addAddCustomRow();
        content.addView(chrome.label(R.string.device_section_quality), chrome.matchWrap());
        addQualityList();
        addSwitchList();
    }

    /** 预置音效与自定义音效同一个单选组，保证同一时刻只有一个选中。 */
    private void addModeList() {
        List<DeviceSettingsUiState.MusicPanel.Preset> presets = music.presets();
        YoList.Builder builder = YoList.create(activity);
        for (DeviceSettingsUiState.MusicPanel.Preset preset : presets) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(activity.getString(preset.labelRes()),
                            activity.getString(preset.subtitleRes())),
                    ListSuffixSpec.none());
        }
        if (music.showCustom()) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(customName(),
                            activity.getString(R.string.device_eq_custom_section)),
                    ListSuffixSpec.none());
        }
        YoList list = builder.build();
        releases.add(list::destroy);
        presetList = list;
        singleSelect(list, selectedModeIndex(), index -> {
            if (index < presets.size()) {
                viewModel.choosePreset(index);
            } else {
                viewModel.selectCustomEq();
            }
        });
        content.addView(list.getView(), listParams());
    }

    private void addAddCustomRow() {
        YoList.Builder builder = YoList.create(activity);
        builder.addItem(ListTextSpec.colored(
                activity.getString(R.string.device_eq_add_custom), theme.brand));
        YoList list = builder.build();
        releases.add(list::destroy);
        list.setOnItemClickListener(index -> openCustomEq());
        content.addView(list.getView(), listParams());
    }

    private void addQualityList() {
        YoList.Builder builder = YoList.create(activity);
        for (int labelRes : music.qualityLabels()) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(activity.getString(labelRes)), ListSuffixSpec.none());
        }
        YoList list = builder.build();
        releases.add(list::destroy);
        qualityList = list;
        singleSelect(list, music.selectedQuality(), viewModel::chooseQuality);
        content.addView(list.getView(), listParams());
    }

    private void addSwitchList() {
        YoList.Builder builder = YoList.create(activity);
        builder.addItem(ListPrefixSpec.none(),
                ListTextSpec.of(activity.getString(R.string.device_row_ldac)),
                ListSuffixSpec.switch_(music.ldac(), viewModel::setLdac));
        builder.addItem(ListPrefixSpec.none(),
                ListTextSpec.of(activity.getString(R.string.device_row_volume_adaptive)),
                ListSuffixSpec.switch_(music.volumeAdaptive(), viewModel::setVolumeAdaptive));
        YoList list = builder.build();
        releases.add(list::destroy);
        content.addView(list.getView(), listParams());
    }

    private void singleSelect(YoList list, int selectedIndex, IntConsumer onSelected) {
        list.setSelectable(true);
        list.setSelectionIndicator(YoList.SelectionIndicator.RADIO);
        applySelection(list, selectedIndex);
        list.setOnSelectionChangeListener((oldId, newId) -> {
            if (updatingSelection) {
                return;
            }
            // YoUI 语义：onSelectionChanged(oldStableId, newStableId)。
            int index = list.indexOfId(newId);
            if (index >= 0) {
                onSelected.accept(index);
            }
        });
    }

    private void updateSelections() {
        updatingSelection = true;
        applySelection(presetList, selectedModeIndex());
        applySelection(qualityList, music.selectedQuality());
        updatingSelection = false;
    }

    private void applySelection(YoList list, int index) {
        if (list == null) {
            return;
        }
        if (index >= 0 && index < list.getItemCount()) {
            long stableId = list.getStableIdAt(index);
            if (list.getSelectedStableId() != stableId) {
                list.setSelectedStableId(stableId);
            }
        } else {
            list.clearSelection();
        }
    }

    private int selectedModeIndex() {
        if (music.customSelected() && music.showCustom()) {
            return music.presets().size();
        }
        return music.selectedPreset();
    }

    private void openCustomEq() {
        if (customEqSheet != null) {
            return;
        }
        customEqSheet = CustomEqSheet.show(activity, theme, customName(), music.customGains(),
                new CustomEqSheet.Listener() {
                    @Override
                    public void onSaved(@NonNull String name, @NonNull float[] gains) {
                        viewModel.saveCustomEq(name, gains);
                    }

                    @Override
                    public void onDismissed() {
                        customEqSheet = null;
                    }
                });
    }

    private String signatureOf(DeviceSettingsUiState.MusicPanel panel) {
        StringBuilder builder = new StringBuilder();
        for (DeviceSettingsUiState.MusicPanel.Preset preset : panel.presets()) {
            builder.append(preset.labelRes()).append(':')
                    .append(preset.subtitleRes()).append('|');
        }
        builder.append(panel.showCustom()).append(':').append(panel.customName()).append('|');
        builder.append(panel.qualityLabels());
        return builder.toString();
    }

    private String customName() {
        return music.customName().isEmpty()
                ? activity.getString(R.string.device_eq_custom_name) : music.customName();
    }

    private LinearLayout.LayoutParams listParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        return params;
    }

    private static DeviceSettingsUiState.MusicPanel emptyMusic() {
        return new DeviceSettingsUiState.MusicPanel(
                List.of(), -1, false, false, "", new float[10], List.of(), 0, false, false);
    }
}
