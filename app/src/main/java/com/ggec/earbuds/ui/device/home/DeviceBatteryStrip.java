package com.ggec.earbuds.ui.device.home;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.BatterySnapshot;
import com.ggec.earbuds.ui.device.DeviceFormats;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.component.BatteryGlyphView;

/**
 * 定制组件：概览区三段电量（左耳 / 右耳 / 充电盒）。复用公共 {@link BatteryGlyphView} 画电池，
 * 本类只负责三段布局与配色语义。
 */
final class DeviceBatteryStrip {

    private DeviceBatteryStrip() {
    }

    @NonNull
    static View create(@NonNull Context context, @NonNull DeviceTheme theme,
                       @NonNull BatterySnapshot battery) {
        LinearLayout strip = new LinearLayout(context);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.addView(cell(context, theme, R.string.device_battery_left,
                battery.left(), false), weight());
        strip.addView(cell(context, theme, R.string.device_battery_right,
                battery.right(), false), weight());
        strip.addView(cell(context, theme, R.string.device_battery_case,
                battery.caseLevel(), battery.caseCharging()), weight());
        return strip;
    }

    private static LinearLayout cell(@NonNull Context context, @NonNull DeviceTheme theme,
                                     @StringRes int labelRes, int level, boolean charging) {
        LinearLayout cell = new LinearLayout(context);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView label = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        label.setText(labelRes);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        cell.addView(row, rowParams);

        BatteryGlyphView glyph = new BatteryGlyphView(context);
        glyph.setState(level, charging, tintFor(theme, level));
        row.addView(glyph, new LinearLayout.LayoutParams(
                theme.dp(DeviceTheme.BATTERY_GLYPH_WIDTH),
                theme.dp(DeviceTheme.BATTERY_GLYPH_HEIGHT)));

        TextView value = theme.text(18f, level < 0 ? theme.text3 : theme.text1,
                DeviceTheme.Weight.MEDIUM);
        value.setText(DeviceFormats.battery(level));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = theme.dp(7f);
        row.addView(value, valueParams);

        if (charging) {
            ImageView bolt = theme.icon(R.drawable.ic_ai_bolt, theme.brand);
            LinearLayout.LayoutParams boltParams = new LinearLayout.LayoutParams(
                    theme.dp(14f), theme.dp(14f));
            boltParams.leftMargin = theme.dp(3f);
            row.addView(bolt, boltParams);
        }
        return cell;
    }

    @ColorInt
    private static int tintFor(@NonNull DeviceTheme theme, int level) {
        if (level < 0) {
            return theme.hint;
        }
        if (level <= 20) {
            return theme.alert;
        }
        return theme.confirm;
    }

    private static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }
}
