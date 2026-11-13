package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.DualConnectionView;
import com.ggec.earbuds.domain.device.model.PairedDeviceView;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsUiState;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.ggec.earbuds.ui.device.settings.PairedDeviceLabels;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.navigation.YoMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * 双连接「已配对设备列表」组件：只负责列表与设备操作，不含开关。
 *
 * <p>双连接开关是独立能力（{@code DUAL_CONNECTION}，TOGGLE），由通用开关行渲染；本组件对应
 * {@code DUAL_CONNECTION_DEVICES}（LIST）。数据来自快照投影 {@link DualConnectionView}，
 * 未上报时显示「未上报」。四类设备操作（连接 / 断开 / 设为优先 / 忘记）都在设备项上。</p>
 */
final class DualConnectionCard {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;
    /** 绑定周期内创建的控件：每次 bind 重建。 */
    private final List<Runnable> releases = new ArrayList<>();
    /** 页面周期内创建、必须活到页面销毁的弹层（选项菜单）。 */
    private final YoComponents components = new YoComponents();

    private final LinearLayout view;
    private final TextView title;
    private final TextView priority;
    private final LinearLayout listHost;

    DualConnectionCard(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                       @NonNull DeviceSettingsViewModel viewModel) {
        this.activity = activity;
        this.theme = theme;
        this.viewModel = viewModel;

        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);

        title = theme.text(DeviceTheme.TEXT_BODY, theme.text1, DeviceTheme.Weight.MEDIUM);
        view.addView(title, matchWrap());

        priority = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        LinearLayout.LayoutParams priorityParams = matchWrap();
        priorityParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        view.addView(priority, priorityParams);

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

    void bind(@NonNull String rowTitle, @NonNull DeviceSettingsUiState state,
              boolean interactive) {
        title.setText(rowTitle);
        listHost.removeAllViews();
        releaseAll();

        DualConnectionView dual = state.dualConnection().orElse(null);
        if (dual == null) {
            priority.setText(R.string.device_value_unknown);
            listHost.addView(message(R.string.device_value_unknown), matchWrap());
            return;
        }
        priority.setText(priorityText(dual));
        listHost.addView(deviceList(dual, interactive), matchWrap());
    }

    void destroy() {
        components.close();
        releaseAll();
    }

    private CharSequence priorityText(@NonNull DualConnectionView dual) {
        for (PairedDeviceView device : dual.devices()) {
            if (device.address().equals(dual.priorityAddress())) {
                return activity.getString(R.string.device_paired_priority_value, device.name());
            }
        }
        return activity.getString(R.string.device_paired_priority_none);
    }

    private View deviceList(@NonNull DualConnectionView dual, boolean interactive) {
        if (dual.devices().isEmpty()) {
            return message(R.string.device_paired_empty);
        }
        YoList.Builder builder = YoList.create(activity);
        List<PairedDeviceView> devices = List.copyOf(dual.devices());
        for (PairedDeviceView device : devices) {
            String titleText = device.name().isEmpty() ? device.address() : device.name();
            String subtitle = activity.getString(PairedDeviceLabels.type(device.type()))
                    + DeviceFormats.META_SEPARATOR
                    + activity.getString(PairedDeviceLabels.business(device.business()));
            builder.addItem(ListPrefixSpec.none(),
                    ListTextSpec.of(titleText, subtitle),
                    interactive ? ListSuffixSpec.arrow() : ListSuffixSpec.none());
        }
        YoList list = builder.build();
        releases.add(list::destroy);
        if (interactive) {
            list.setOnItemClickListener(index -> {
                if (index >= 0 && index < devices.size()) {
                    openDeviceActions(devices.get(index));
                }
            });
        }
        return list.getView();
    }

    private void openDeviceActions(@NonNull PairedDeviceView device) {
        List<Integer> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(device.connected()
                ? R.string.device_paired_disconnect : R.string.device_paired_connect);
        actions.add(device.connected()
                ? () -> viewModel.run(new DeviceAction.DisconnectPairedDevice(device.address()))
                : () -> viewModel.run(new DeviceAction.ConnectPairedDevice(device.address())));
        if (!device.priority()) {
            labels.add(R.string.device_paired_set_priority);
            actions.add(() ->
                    viewModel.run(new DeviceAction.SetPriorityPairedDevice(device.address())));
        }
        labels.add(R.string.device_paired_forget);
        actions.add(() -> viewModel.run(new DeviceAction.ForgetPairedDevice(device.address())));

        String[] items = new String[labels.size()];
        for (int index = 0; index < labels.size(); index++) {
            items[index] = activity.getString(labels.get(index));
        }
        YoMenu.Builder builder = YoMenu.create(activity)
                .setAnchorView(activity.findViewById(android.R.id.content));
        for (int index = 0; index < items.length; index++) {
            builder.addItem(index, items[index]);
        }
        YoMenu menu = builder
                .setOnItemClickListener(id -> {
                    if (id >= 0 && id < actions.size()) {
                        actions.get(id).run();
                    }
                })
                .build();
        components.own(menu::destroy);
        menu.show();
    }

    private View message(int messageRes) {
        TextView text = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        text.setText(messageRes);
        return text;
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
