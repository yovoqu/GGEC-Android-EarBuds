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
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.YoSheet;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.controls.YoSlider;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 自定义音效模态面板（参考 HarmonyOS 智慧生活）。
 * 结构自底而上重排：音效名称（YoList + YoDialog 改名）、对齐的 10 段 EQ 画布、提示、底部保存。
 */
public final class CustomEqSheet {

    public interface Listener {
        void onSaved(@NonNull String name, @NonNull float[] gains);

        void onDismissed();
    }

    private static final String[] FREQUENCIES = {
            "60", "125", "250", "500", "1k", "2k", "4k", "8k", "12k", "16k"
    };

    /** 画布高度：卡片的调节区总高。 */
    private static final float CANVAS_HEIGHT_DP = 270f;
    /** 滑轨高度：实际滑动行程，居中放在画布里。 */
    private static final float TRACK_HEIGHT_DP = 250f;

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final List<Runnable> releases = new ArrayList<>();
    private final List<YoSlider> sliders = new ArrayList<>();

    private String name;
    private FrameLayout nameHost;
    private YoSheet sheet;

    private CustomEqSheet(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme) {
        this.activity = activity;
        this.theme = theme;
    }

    public static CustomEqSheet show(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                                     @NonNull String initialName, @NonNull float[] initialGains,
                                     @NonNull Listener listener) {
        CustomEqSheet panel = new CustomEqSheet(activity, theme);
        panel.name = initialName;
        panel.present(initialGains, listener);
        return panel;
    }

    private void present(float[] initialGains, Listener listener) {
        float[] gains = initialGains.clone();
        LinearLayout content = SettingsChrome.vertical(activity);
        content.setPadding(0, theme.dp(DeviceTheme.SPACE_S), 0, theme.dp(DeviceTheme.SPACE_L));

        addNameList(content);
        content.addView(bandCard(gains), cardParams());

        TextView hint = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        hint.setText(R.string.device_eq_hint);
        hint.setPadding(theme.dp(DeviceTheme.LIST_EDGE), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.LIST_EDGE), 0);
        content.addView(hint, matchWrap());

        // YoSheet 的 SheetPageHost 以「内容视口高度 EXACTLY」测量页面，
        // 所以用权重占位把保存按钮压到面板底部，而不是固定高度 spacer。
        View spacer = new View(activity);
        content.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(activity);
        footer.setPadding(theme.dp(DeviceTheme.LIST_EDGE), 0,
                theme.dp(DeviceTheme.LIST_EDGE), theme.dp(DeviceTheme.SPACE_L));
        YoButton save = YoButton.create(activity).build();
        save.setText(activity.getString(R.string.device_eq_save));
        save.setOnClickListener(v -> {
            listener.onSaved(name, gains.clone());
            dismiss();
        });
        releases.add(save::destroy);
        footer.addView(save.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(48)));
        content.addView(footer, matchWrap());

        sheet = YoSheet.create(activity)
                .setTitle(activity.getString(R.string.device_eq_custom_title))
                .setContent(content)
                .setHeight(YoSheet.Height.large())
                .setScrollSizeMode(YoSheet.ScrollSizeMode.FOLLOW_DETENT)
                .setCloseActionVisible(true)
                .setDismissOnBack(true)
                .setDismissOnDrag(true)
                .setDismissOnOutsideTap(true)
                .setListener(new YoSheet.Listener() {
                    @Override
                    public void onDismissed(@NonNull YoSheet.DismissReason reason) {
                        release();
                        listener.onDismissed();
                    }
                })
                .build();
        sheet.show();
    }

    private void addNameList(LinearLayout content) {
        nameHost = new FrameLayout(activity);
        nameHost.setOnClickListener(v -> openRenameDialog());
        // 不额外加左右边距：YoList 默认自带 16dp 外边距，与屏幕/卡片边界一致。
        content.addView(nameHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildNameList();
    }

    private void rebuildNameList() {
        nameHost.removeAllViews();
        YoList.Builder builder = YoList.create(activity);
        builder.addItem(ListPrefixSpec.none(),
                ListTextSpec.of(activity.getString(R.string.device_eq_name_label)),
                ListSuffixSpec.textAndArrow(name));
        YoList list = builder.build();
        releases.add(list::destroy);
        list.setOnItemClickListener(index -> openRenameDialog());
        nameHost.addView(list.getView(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    private void openRenameDialog() {
        YoTextInput field = YoTextInput.create(activity)
                .setText(name)
                .setPlaceholder(activity.getString(R.string.device_eq_name_label))
                .setMaxLength(20)
                .setShowCounter(true)
                .setShowErrorEnabled(true)
                .build();
        YoDialog.create(activity)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setTitle(activity.getString(R.string.device_eq_name_label))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.device_eq_cancel), null),
                        YoDialog.Action.positive(activity.getString(R.string.device_eq_save),
                                () -> {
                                    String next = String.valueOf(field.getText()).trim();
                                    if (!next.isEmpty()) {
                                        name = next;
                                        rebuildNameList();
                                    }
                                }))
                .show();
    }

    private View bandCard(float[] gains) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_SHEET,
                theme.cardStroke, 1f));
        card.setPadding(theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M),
                theme.dp(DeviceTheme.SPACE_M), theme.dp(DeviceTheme.SPACE_M));

        LinearLayout resetRow = new LinearLayout(activity);
        resetRow.setOrientation(LinearLayout.HORIZONTAL);
        resetRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        View resetIcon = theme.icon(R.drawable.ic_ai_refresh, theme.text3);
        resetRow.addView(resetIcon, new LinearLayout.LayoutParams(theme.dp(14), theme.dp(14)));
        TextView reset = theme.text(DeviceTheme.TEXT_CAPTION, theme.text2);
        reset.setText(R.string.device_eq_reset);
        reset.setPadding(theme.dp(DeviceTheme.SPACE_XS), theme.dp(DeviceTheme.SPACE_XS),
                theme.dp(DeviceTheme.SPACE_S), theme.dp(DeviceTheme.SPACE_XS));
        reset.setOnClickListener(v -> resetGains(gains));
        resetRow.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(resetRow, matchWrap());

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.BOTTOM);
        body.addView(scaleColumn(), new LinearLayout.LayoutParams(
                theme.dp(24), theme.dp(CANVAS_HEIGHT_DP)));

        LinearLayout bands = new LinearLayout(activity);
        bands.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < FREQUENCIES.length; index++) {
            bands.addView(sliderColumn(index, gains), new LinearLayout.LayoutParams(
                    0, theme.dp(CANVAS_HEIGHT_DP), 1f));
        }
        body.addView(bands, new LinearLayout.LayoutParams(
                0, theme.dp(CANVAS_HEIGHT_DP), 1f));
        card.addView(body, matchWrap());

        LinearLayout frequencies = new LinearLayout(activity);
        frequencies.setOrientation(LinearLayout.HORIZONTAL);
        frequencies.addView(new View(activity), new LinearLayout.LayoutParams(theme.dp(24), 0));
        for (String frequency : FREQUENCIES) {
            TextView label = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
            label.setText(frequency);
            label.setGravity(Gravity.CENTER);
            frequencies.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.addView(frequencies, matchWrap());
        return card;
    }

    /** 左侧刻度列：+6 / 0 / -6 与滑轨上下端对齐（滑轨居中于画布）。 */
    private View scaleColumn() {
        FrameLayout outer = new FrameLayout(activity);
        FrameLayout frame = new FrameLayout(activity);
        outer.addView(frame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(TRACK_HEIGHT_DP),
                Gravity.CENTER_VERTICAL));
        EqTickScaleView ticks = new EqTickScaleView(activity, theme.text3);
        frame.addView(ticks, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView top = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        top.setText("+6");
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        frame.addView(top, topParams);

        TextView middle = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        middle.setText("0");
        FrameLayout.LayoutParams middleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.START);
        frame.addView(middle, middleParams);

        TextView bottom = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        bottom.setText("-6");
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        frame.addView(bottom, bottomParams);
        return outer;
    }

    private View sliderColumn(int index, float[] gains) {
        LinearLayout column = SettingsChrome.vertical(activity);
        column.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        YoSlider slider = YoSlider.createOutSet(activity)
                .setOrientation(YoSlider.Orientation.VERTICAL)
                .setMin(-6f)
                .setMax(6f)
                .setStep(0.5f)
                .setValue(gains[index])
                // YoUI OutSet 滑杆默认 block 为 18vp、两侧预留 9–10vp；
                // thumb 放大到 >18vp 会被组件自身边界裁剪，因此保持官方尺寸。
                .setThumbSize(18f)
                .setThumbColor(0xFFFFFFFF)
                .setShowShadow(true)
                .setActiveTrackColor(theme.brand)
                .setInactiveTrackColor(theme.fill)
                .setOnProgressChangeListener((value, fromUser) -> gains[index] = value)
                .build();
        // YoSlider 的动效在视图脱离窗口后再 destroy 会抛 "motion is closed"，
        // 因此这里只保留引用用于重置，不在面板关闭时主动 destroy 滑杆。
        sliders.add(slider);
        column.addView(slider.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(TRACK_HEIGHT_DP)));
        return column;
    }

    private void resetGains(float[] gains) {
        for (int index = 0; index < gains.length; index++) {
            gains[index] = 0f;
        }
        for (YoSlider slider : sliders) {
            slider.setValue(0f, false);
        }
    }

    public void destroy() {
        if (sheet != null) {
            YoSheet current = sheet;
            sheet = null;
            current.destroy();
        }
        release();
    }

    private void dismiss() {
        if (sheet != null) {
            sheet.dismiss();
        }
    }

    private void release() {
        for (Runnable release : releases) {
            release.run();
        }
        releases.clear();
        sliders.clear();
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = theme.dp(DeviceTheme.LIST_EDGE);
        params.rightMargin = theme.dp(DeviceTheme.LIST_EDGE);
        params.topMargin = theme.dp(DeviceTheme.SPACE_M);
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
