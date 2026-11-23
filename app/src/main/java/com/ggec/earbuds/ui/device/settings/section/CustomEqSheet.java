package com.ggec.earbuds.ui.device.settings.section;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.CapabilityOption;
import com.ggec.earbuds.domain.device.model.ControlRange;
import com.ggec.earbuds.ui.device.CurveEditorSeed;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.yohu.youi.api.containers.YoSheet;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.controls.YoSlider;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 自定义音效（{@code CURVE}）模态面板：N 段可调曲线，数值即 {@code ControlValue.Curve} 的 points。
 *
 * <p>面板只渲染描述符：段数、每段的频率标签来自能力描述符的频段 {@code options}，滑杆的取值域与步进
 * 来自描述符的 {@code range}。APP 这里没有频段表、没有增益上下界；<b>缺 options 或 range 就表示这条
 * 能力对编辑器不可用</b>——{@link #show} 返回空，调用方不打开面板、也不猜默认。</p>
 *
 * <p>整条曲线只在保存时写一次。</p>
 */
public final class CustomEqSheet {

    public interface Listener {
        void onSaved(@NonNull String name, @NonNull List<Integer> points);

        void onDismissed();
    }

    /** 画布高度：卡片的调节区总高。 */
    private static final float CANVAS_HEIGHT_DP = 270f;
    /** 滑轨高度：实际滑动行程，居中放在画布里。 */
    private static final float TRACK_HEIGHT_DP = 250f;
    /** 左侧刻度列宽度（与频段列左偏移同宽，标签才对得上滑杆）。 */
    private static final int SCALE_COLUMN_WIDTH_DP = 24;

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    /** 频段表：段数与标签全部来自描述符。 */
    private final List<CapabilityOption> bands;
    /** 增益值域：滑杆上下界与步进全部来自描述符。 */
    private final ControlRange range;
    /**
     * 复位后的曲线：与草稿默认值同源——{@link CurveEditorSeed#flat} 给出的平直曲线，即描述符声明的
     * 中立档（缺省时是值域下界这一显式产品默认）。这里不另取默认，也不带本地常量。
     */
    private final List<Integer> rest;
    private final List<Runnable> releases = new ArrayList<>();
    private final List<YoSlider> sliders = new ArrayList<>();

    private int[] gains;
    private String name;
    private TextView nameValue;
    private YoSheet sheet;

    private CustomEqSheet(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                          @NonNull List<CapabilityOption> bands, @NonNull ControlRange range,
                          @NonNull List<Integer> rest) {
        this.activity = activity;
        this.theme = theme;
        this.bands = bands;
        this.range = range;
        this.rest = rest;
    }

    /**
     * 打开面板；描述符没给出曲线契约（频段表为空或缺增益值域）时返回空，调用方不得渲染编辑器。
     *
     * @param reported 设备上报的曲线，可为空
     * @param draft    本次未确认的编辑结果，可为空
     */
    @NonNull
    public static Optional<CustomEqSheet> show(@NonNull AppCompatActivity activity,
                                               @NonNull DeviceTheme theme,
                                               @NonNull List<CapabilityOption> bands,
                                               @NonNull Optional<ControlRange> range,
                                               @NonNull Optional<List<Integer>> reported,
                                               @NonNull Optional<List<Integer>> draft,
                                               @NonNull Optional<String> initialName,
                                               @NonNull Listener listener) {
        Optional<List<Integer>> seed = CurveEditorSeed.of(bands, range, reported, draft);
        Optional<List<Integer>> rest = CurveEditorSeed.flat(bands, range);
        if (seed.isEmpty() || rest.isEmpty()) {
            return Optional.empty();
        }
        CustomEqSheet panel = new CustomEqSheet(activity, theme, bands, range.get(), rest.get());
        panel.present(seed.get(), initialName, listener);
        return Optional.of(panel);
    }

    private void present(List<Integer> seed, Optional<String> initialName, Listener listener) {
        gains = new int[seed.size()];
        for (int index = 0; index < gains.length; index++) {
            gains[index] = seed.get(index);
        }
        name = initialName.filter(value -> !value.trim().isEmpty())
                .orElseGet(() -> activity.getString(R.string.device_eq_name_default));

        LinearLayout content = SettingsChrome.vertical(activity);
        content.setPadding(0, theme.dp(DeviceTheme.SPACE_S), 0, theme.dp(DeviceTheme.SPACE_L));
        content.addView(nameCard(), cardParams());
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
            listener.onSaved(name, points());
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

    private List<Integer> points() {
        List<Integer> points = new ArrayList<>(gains.length);
        for (int gain : gains) {
            points.add(gain);
        }
        return List.copyOf(points);
    }

    /** 音效名称行：左「音效名称」、右当前名 + 右箭头，点开输入框改名。 */
    private View nameCard() {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(theme.roundedStroke(theme.card, DeviceTheme.RADIUS_SHEET,
                theme.cardStroke, 1f));
        int padH = theme.dp(DeviceTheme.SPACE_L);
        int padV = theme.dp(DeviceTheme.SPACE_L);
        card.setPadding(padH, padV, padH, padV);
        card.setMinimumHeight(theme.dp(DeviceTheme.ROW_MIN_HEIGHT));

        TextView label = theme.text(DeviceTheme.TEXT_BODY, theme.text1, DeviceTheme.Weight.MEDIUM);
        label.setText(R.string.device_eq_name_label);
        card.addView(label, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        nameValue = theme.text(DeviceTheme.TEXT_BODY, theme.text3);
        nameValue.setText(name);
        nameValue.setSingleLine(true);
        nameValue.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(nameValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView chevron = theme.icon(R.drawable.ic_dev_chevron_right, theme.hint);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(
                theme.dp(DeviceTheme.SCAN_CHEVRON), theme.dp(DeviceTheme.SCAN_CHEVRON));
        chevronParams.leftMargin = theme.dp(DeviceTheme.SPACE_XS);
        card.addView(chevron, chevronParams);

        card.setOnClickListener(v -> openNameDialog());
        return card;
    }

    private void openNameDialog() {
        YoTextInput field = YoTextInput.create(activity)
                .setText(name)
                .setPlaceholder(activity.getString(R.string.device_eq_name_placeholder))
                .setMaxLength(20)
                .setShowCounter(true)
                .setShowErrorEnabled(true)
                .build();
        YoDialog.create(activity)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setTitle(activity.getString(R.string.device_eq_name_label))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.device_dialog_cancel),
                                null),
                        YoDialog.Action.positive(activity.getString(R.string.device_dialog_confirm),
                                () -> {
                                    String edited = String.valueOf(field.getText()).trim();
                                    if (!edited.isEmpty()) {
                                        name = edited;
                                        if (nameValue != null) {
                                            nameValue.setText(name);
                                        }
                                    }
                                }))
                .show();
    }

    private View bandCard(int[] gains) {        LinearLayout card = new LinearLayout(activity);
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
                theme.dp(SCALE_COLUMN_WIDTH_DP), theme.dp(CANVAS_HEIGHT_DP)));

        LinearLayout bandColumns = new LinearLayout(activity);
        bandColumns.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < gains.length; index++) {
            bandColumns.addView(sliderColumn(index, gains), new LinearLayout.LayoutParams(
                    0, theme.dp(CANVAS_HEIGHT_DP), 1f));
        }
        body.addView(bandColumns, new LinearLayout.LayoutParams(
                0, theme.dp(CANVAS_HEIGHT_DP), 1f));
        card.addView(body, matchWrap());

        // 频率标签与滑杆列一一对应，标签文案来自描述符的频段 options。
        LinearLayout frequencies = new LinearLayout(activity);
        frequencies.setOrientation(LinearLayout.HORIZONTAL);
        frequencies.addView(new View(activity), new LinearLayout.LayoutParams(
                theme.dp(SCALE_COLUMN_WIDTH_DP), 0));
        for (CapabilityOption band : bands) {
            TextView label = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
            label.setText(band.title());
            label.setGravity(Gravity.CENTER);
            frequencies.addView(label, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        card.addView(frequencies, matchWrap());
        return card;
    }

    /** 左侧刻度列：值域上界 / 等分点 / 下界，与滑轨上下端对齐（滑轨居中于画布）。 */
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
        top.setText(String.valueOf(range.max()));
        frame.addView(top, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));

        TextView middle = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        middle.setText(String.valueOf(middleGain()));
        frame.addView(middle, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.START));

        TextView bottom = theme.text(DeviceTheme.TEXT_CAPTION, theme.text3);
        bottom.setText(String.valueOf(range.min()));
        frame.addView(bottom, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START));
        return outer;
    }

    /** 刻度列正中的取值：值域按 step 等分后的中间点（落在协议值域内、也落在步进网格上）。 */
    private int middleGain() {
        int steps = (range.max() - range.min()) / range.step();
        return range.min() + (steps / 2) * range.step();
    }

    private View sliderColumn(int index, int[] gains) {
        LinearLayout column = SettingsChrome.vertical(activity);
        column.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        YoSlider slider = YoSlider.createOutSet(activity)
                .setOrientation(YoSlider.Orientation.VERTICAL)
                .setMin(range.min())
                .setMax(range.max())
                .setStep(range.step())
                .setValue(gains[index])
                // YoUI OutSet 滑杆默认 block 为 18vp、两侧预留 9–10vp；
                // thumb 放大到 >18vp 会被组件自身边界裁剪，因此保持官方尺寸。
                .setThumbSize(18f)
                .setThumbColor(0xFFFFFFFF)
                .setShowShadow(true)
                .setActiveTrackColor(theme.brand)
                .setInactiveTrackColor(theme.fill)
                .setOnProgressChangeListener((value, fromUser) -> gains[index] = Math.round(value))
                .build();
        // YoSlider 的动效在视图脱离窗口后再 destroy 会抛 "motion is closed"，
        // 因此这里只保留引用用于重置，不在面板关闭时主动 destroy 滑杆。
        sliders.add(slider);
        column.addView(slider.getView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(TRACK_HEIGHT_DP)));
        return column;
    }

    /** 复位到描述符派生的平直曲线（与草稿默认值同源，不是本地常量）。 */
    private void resetGains(int[] gains) {
        for (int index = 0; index < gains.length; index++) {
            gains[index] = rest.get(index);
        }
        for (int index = 0; index < sliders.size(); index++) {
            sliders.get(index).setValue(rest.get(index).floatValue(), false);
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
