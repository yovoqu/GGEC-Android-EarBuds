package com.ggec.earbuds.ui.device.home;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.common.YoComponents;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.component.DeviceLayouts;
import com.yohu.youi.api.controls.YoButton;

/**
 * 设备首页最底部的 APP 版本信息与「检查更新」入口。
 * 点击回调交给 {@link DeviceHomePage.Listener#onCheckAppUpdate()}，由宿主接入更新会话。
 */
final class DeviceHomeFooterView {

    private DeviceHomeFooterView() {
    }

    @NonNull
    static View create(@NonNull Context context,
                       @NonNull DeviceTheme theme,
                       @NonNull YoComponents components,
                       @NonNull Runnable onCheckUpdate) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.CENTER_HORIZONTAL);
        container.setPadding(
                theme.dp(DeviceTheme.PAGE_INSET),
                theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.PAGE_INSET),
                theme.dp(DeviceTheme.SPACE_S));

        TextView versionText = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        versionText.setGravity(Gravity.CENTER);
        versionText.setText(context.getString(R.string.device_home_app_version, resolveVersionName(context)));
        container.addView(versionText, DeviceLayouts.matchWrap());

        YoButton checkUpdateButton = YoButton.create(context)
                .setText(context.getString(R.string.device_home_check_update))
                .setStyle(YoButton.Style.TEXT)
                .setSize(YoButton.Size.SMALL)
                .setOnClickListener(v -> onCheckUpdate.run())
                .build();
        components.own(checkUpdateButton::destroy);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        container.addView(checkUpdateButton.getView(), buttonParams);

        return container;
    }

    @NonNull
    private static String resolveVersionName(@NonNull Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            if (info != null && info.versionName != null && !info.versionName.isEmpty()) {
                return info.versionName;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Fall through to placeholder.
        }
        return "—";
    }
}
