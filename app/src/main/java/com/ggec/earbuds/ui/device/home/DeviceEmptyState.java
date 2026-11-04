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
import com.yohu.youi.api.controls.YoButton;

/** 未连接设备时的空态引导。 */
public final class DeviceEmptyState {

    private final DeviceTheme theme;
    private final View view;
    private final YoButton action;

    public DeviceEmptyState(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                            @NonNull Runnable onAdd) {
        this.theme = theme;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(theme.dp(DeviceTheme.PAGE_INSET), theme.dp(DeviceTheme.EMPTY_TOP),
                theme.dp(DeviceTheme.PAGE_INSET), 0);

        View icon = theme.icon(R.drawable.ic_ai_bolt, theme.text3);
        box.addView(icon, new LinearLayout.LayoutParams(theme.dp(40), theme.dp(40)));

        TextView title = theme.text(DeviceTheme.TEXT_HEADLINE, theme.text1, DeviceTheme.Weight.MEDIUM);
        title.setText(R.string.device_empty_title);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = theme.dp(DeviceTheme.SPACE_L);
        box.addView(title, titleParams);

        TextView body = theme.text(DeviceTheme.TEXT_CALLOUT, theme.text3);
        body.setText(R.string.device_empty_body);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        box.addView(body, bodyParams);

        action = YoButton.create(activity).build();
        action.setText(activity.getString(R.string.device_empty_action));
        action.setOnClickListener(v -> onAdd.run());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                theme.dp(180), theme.dp(44));
        actionParams.topMargin = theme.dp(DeviceTheme.SPACE_XL);
        box.addView(action.getView(), actionParams);
        view = box;
    }

    @NonNull
    public View view() {
        return view;
    }

    public void destroy() {
        action.destroy();
    }
}
