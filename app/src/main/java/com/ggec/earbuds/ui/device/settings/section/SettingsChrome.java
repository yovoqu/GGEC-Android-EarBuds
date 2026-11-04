package com.ggec.earbuds.ui.device.settings.section;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.controls.YoButton;

import java.util.List;

/** 设置页各区块共用的布局原语：分组标题、卡片、按钮。 */
final class SettingsChrome {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;

    SettingsChrome(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme) {
        this.activity = activity;
        this.theme = theme;
    }

    @NonNull
    TextView label(int titleRes) {
        TextView view = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        view.setText(titleRes);
        view.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_L),
                theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.SPACE_XS));
        return view;
    }

    @NonNull
    LinearLayout card() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_SHEET,
                theme.cardStroke, 1f));
        card.setPadding(theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.SPACE_L), theme.dp(DeviceTheme.SPACE_M));
        return card;
    }

    void addCard(@NonNull LinearLayout content, @NonNull View card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
        params.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
        params.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        content.addView(card, params);
    }

    @NonNull
    YoButton button(int titleRes, @NonNull Runnable onClick) {
        YoButton button = YoButton.create(activity).build();
        button.setText(activity.getString(titleRes));
        button.setOnClickListener(v -> onClick.run());
        return button;
    }

    @NonNull
    LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(theme.dp(DeviceTheme.SPACE_XS));
        return params;
    }

    @NonNull
    LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @NonNull
    LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @NonNull
    static LinearLayout vertical(@NonNull AppCompatActivity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    @NonNull
    static LinearLayout horizontal(@NonNull AppCompatActivity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    void releaseAll(@NonNull List<Runnable> releases) {
        for (Runnable release : releases) {
            release.run();
        }
        releases.clear();
    }
}
