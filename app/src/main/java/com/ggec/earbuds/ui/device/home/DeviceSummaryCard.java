package com.ggec.earbuds.ui.device.home;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.DeviceTheme;

/** 设备摘要卡：机型名、连接状态、三路电量、佩戴与充电。 */
public final class DeviceSummaryCard {

    private final DeviceTheme theme;
    private final LinearLayout view;
    private final TextView subtitle;
    private final TextView status;
    private final View statusDot;
    private final TextView leftValue;
    private final TextView rightValue;
    private final TextView caseValue;
    private final TextView wear;
    private final TextView charging;

    public DeviceSummaryCard(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme) {
        this.theme = theme;
        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_SHEET,
                theme.cardStroke, 1f));
        view.setPadding(theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_L),
                theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_L));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout names = new LinearLayout(activity);
        names.setOrientation(LinearLayout.VERTICAL);
        TextView name = theme.text(DeviceTheme.TEXT_TITLE, theme.text1, DeviceTheme.Weight.MEDIUM);
        name.setText(R.string.device_connected_title);
        subtitle = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        names.addView(name, wrap());
        names.addView(subtitle, wrap());
        top.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout stateRow = new LinearLayout(activity);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(activity);
        statusDot.setBackground(theme.circle(theme.confirm));
        stateRow.addView(statusDot, new LinearLayout.LayoutParams(theme.dp(8), theme.dp(8)));
        status = theme.text(DeviceTheme.TEXT_CAPTION, theme.text2);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMarginStart(theme.dp(DeviceTheme.SPACE_XS));
        stateRow.addView(status, statusParams);
        top.addView(stateRow, wrap());
        view.addView(top, match());

        LinearLayout batteries = new LinearLayout(activity);
        batteries.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams batteryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        batteryParams.topMargin = theme.dp(DeviceTheme.SPACE_L);
        leftValue = batteryColumn(batteries, R.string.device_battery_left);
        rightValue = batteryColumn(batteries, R.string.device_battery_right);
        caseValue = batteryColumn(batteries, R.string.device_battery_case);
        view.addView(batteries, batteryParams);

        LinearLayout meta = new LinearLayout(activity);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        wear = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        charging = theme.text(DeviceTheme.TEXT_CAPTION, theme.brand);
        LinearLayout.LayoutParams chargingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chargingParams.setMarginStart(theme.dp(DeviceTheme.SPACE_M));
        meta.addView(wear, wrap());
        meta.addView(charging, chargingParams);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = theme.dp(DeviceTheme.SPACE_M);
        view.addView(meta, metaParams);
    }

    @NonNull
    public View view() {
        return view;
    }

    public void render(@NonNull DeviceHomeUiState state) {
        subtitle.setText(state.modelId());
        status.setText(state.connectionTextRes());
        status.setTextColor(state.connected() ? theme.confirm : theme.text2);
        statusDot.setBackground(theme.circle(state.connected() ? theme.confirm : theme.text3));
        leftValue.setText(state.leftText());
        rightValue.setText(state.rightText());
        caseValue.setText(state.caseText());
        wear.setText(state.wearTextRes());
        if (state.chargingTextRes() != 0) {
            charging.setText(state.chargingTextRes());
        } else {
            charging.setText("");
        }
    }

    private TextView batteryColumn(LinearLayout parent, int labelRes) {
        LinearLayout column = new LinearLayout(parent.getContext());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        TextView value = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.BOLD);
        value.setText("--");
        TextView label = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        label.setText(labelRes);
        column.addView(value, wrap());
        column.addView(label, wrap());
        parent.addView(column, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return value;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
