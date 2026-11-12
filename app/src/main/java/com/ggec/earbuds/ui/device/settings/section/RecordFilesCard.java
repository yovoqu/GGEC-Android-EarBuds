package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 录音文件列表组件：文件来自快照投影 {@code recordFiles}（未上报为空列表）。
 * 点一条即发起该文件的传输，动作是类型化的 {@link DeviceAction.TransferRecord}。
 */
final class RecordFilesCard {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;
    private final List<Runnable> releases = new ArrayList<>();

    private final LinearLayout view;
    private final TextView title;
    private final LinearLayout listHost;

    RecordFilesCard(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                    @NonNull DeviceSettingsViewModel viewModel) {
        this.activity = activity;
        this.theme = theme;
        this.viewModel = viewModel;

        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        title = theme.text(DeviceTheme.TEXT_BODY, theme.text1, DeviceTheme.Weight.MEDIUM);
        view.addView(title, matchWrap());

        listHost = new LinearLayout(activity);
        listHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hostParams = matchWrap();
        hostParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        view.addView(listHost, hostParams);
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull String rowTitle, @NonNull DeviceSettingsUiState state, boolean interactive) {
        title.setText(rowTitle);
        listHost.removeAllViews();
        releaseAll();
        List<RecordFileView> files = state.recordFiles();
        if (files.isEmpty()) {
            TextView empty = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
            empty.setText(R.string.device_record_empty);
            listHost.addView(empty, matchWrap());
            return;
        }
        YoList.Builder builder = YoList.create(activity);
        List<RecordFileView> rows = new ArrayList<>(files);
        for (RecordFileView file : rows) {
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(file.name(), meta(file)),
                    interactive ? ListSuffixSpec.arrow() : ListSuffixSpec.none());
        }
        YoList list = builder.build();
        releases.add(list::destroy);
        if (interactive) {
            list.setOnItemClickListener(index -> {
                if (index >= 0 && index < rows.size()) {
                    viewModel.run(new DeviceAction.TransferRecord(rows.get(index).fileId(), true));
                }
            });
        }
        listHost.addView(list.getView(), matchWrap());
    }

    void destroy() {
        releaseAll();
    }

    private CharSequence meta(@NonNull RecordFileView file) {
        return activity.getString(R.string.device_record_meta, file.durationSeconds(),
                DeviceFormats.size(file.sizeBytes()));
    }

    private void releaseAll() {
        for (Runnable release : releases) {
            release.run();
        }
        releases.clear();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
