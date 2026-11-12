package com.ggec.earbuds.ui.device.settings.section;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.ControlValue;
import com.ggec.earbuds.ui.device.DeviceControlOutcome;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.LevelPresentation;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.yohu.youi.api.callback.OnProgressChangeListener;
import com.yohu.youi.api.controls.YoSlider;

import java.util.OptionalInt;

/**
 * 档位行组件：标题 + 当前值 + 滑杆。
 *
 * <p>值域完全来自描述符的 {@code range}（min / max / step），界面不做任何推导。与开关同一条裁定：
 * **值未知只把值显示成「未上报」，滑杆仍然可写**——写入是绝对设定语义，不需要已知当前值；
 * 位置先取上报值，否则取用户本次的未确认选择，再否则取下界。拖动只更新读数，抬手写一次；
 * 写入失败时丢弃未确认值并回到未上报呈现。</p>
 */
final class SliderCardView implements ControlOutcomeSink {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;
    private final String capabilityId;

    private final LinearLayout view;
    private final TextView title;
    private final TextView value;
    private final FrameLayout sliderHost;

    private YoSlider slider;
    private OptionalInt unconfirmed = OptionalInt.empty();
    private int dragValue;

    SliderCardView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                   @NonNull DeviceSettingsViewModel viewModel, @NonNull String capabilityId) {
        this.activity = activity;
        this.theme = theme;
        this.viewModel = viewModel;
        this.capabilityId = capabilityId;

        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = SettingsChrome.horizontal(activity);
        title = theme.text(DeviceTheme.TEXT_BODY, theme.text1, DeviceTheme.Weight.MEDIUM);
        value = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sliderHost = new FrameLayout(activity);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sliderParams.topMargin = theme.dp(DeviceTheme.SPACE_XS);
        view.addView(sliderHost, sliderParams);
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull DeviceSettingRow.Level row, boolean interactive) {
        title.setText(row.title());
        sliderHost.removeAllViews();
        releaseSlider();

        LevelPresentation presentation = LevelPresentation.of(row.value(), unconfirmed,
                row.range().min(), interactive);
        dragValue = presentation.value();
        value.setText(presentation.unknown()
                ? activity.getString(R.string.device_value_unknown)
                : String.valueOf(presentation.value()));

        slider = YoSlider.create(activity)
                .setMin(row.range().min())
                .setMax(row.range().max())
                .setStep(row.range().step())
                .setValue(presentation.value())
                .setEnabled(presentation.enabled())
                .setActiveTrackColor(theme.brand)
                .setInactiveTrackColor(theme.fill)
                .setOnProgressChangeListener(new OnProgressChangeListener() {
                    @Override
                    public void onProgressChanged(float current, boolean fromUser) {
                        dragValue = Math.round(current);
                        value.setText(String.valueOf(dragValue));
                    }

                    @Override
                    public void onStopTrackingTouch() {
                        if (!presentation.enabled()) {
                            return;
                        }
                        unconfirmed = OptionalInt.of(dragValue);
                        viewModel.write(capabilityId, new ControlValue.Level(dragValue));
                    }
                })
                .build();
        sliderHost.addView(slider.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, theme.dp(DeviceTheme.TOUCH_TARGET),
                Gravity.CENTER_VERTICAL));
    }

    void destroy() {
        releaseSlider();
    }

    @Override
    public boolean onControlOutcome(@NonNull DeviceControlOutcome outcome) {
        if (outcome.succeeded() || !outcome.capabilityId().equals(capabilityId)) {
            return false;
        }
        if (unconfirmed.isEmpty()) {
            return false;
        }
        unconfirmed = OptionalInt.empty();
        return true;
    }

    private void releaseSlider() {
        if (slider != null) {
            YoSlider current = slider;
            slider = null;
            current.destroy();
        }
    }
}
