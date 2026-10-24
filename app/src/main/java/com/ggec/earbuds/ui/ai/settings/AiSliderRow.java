package com.ggec.earbuds.ui.ai.settings;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 一行内联滑杆：左边标签、右边当前值、下面滑杆，改完立刻生效。
 *
 * <p>为什么不再用"点行 → 弹窗 → 输入 → 保存"：温度和核采样是连续量，滑杆是它们的自然形态；
 * 弹窗要求用户先知道合法区间、再手打一个数字，还要多三次点击。参数值同时写在右侧，
 * 拖动过程中就能看到结果。</p>
 *
 * <p>"平台默认"是 {@code null}，不是某个数字。滑杆停在默认位置并显示「平台默认」，
 * 用户一动就变成具体值；点右侧的值可以退回默认——这个出口必须留着，
 * 否则一旦拖动就再也回不到"交给平台"。</p>
 */
final class AiSliderRow extends LinearLayout {

    private final AiTheme theme;
    private final TextView valueView;
    private final SeekBar seekBar;
    private Drawable thumbActive;
    private final float min;
    private final float max;
    private final int steps;
    private final int defaultStep;
    private final Formatter formatter;

    @Nullable
    private Listener listener;
    private boolean binding;

    interface Formatter {
        /** @param step 0..steps 的位置 */
        @NonNull
        CharSequence format(int step);
    }

    interface Listener {
        /** @param value 具体值；{@code null} 表示回到平台默认 */
        void onValue(@Nullable Double value);
    }

    private AiSliderRow(@NonNull AiTheme theme, int title, float min, float max, int steps,
                        int defaultStep, @NonNull Formatter formatter) {
        super(theme.context);
        this.theme = theme;
        this.min = min;
        this.max = max;
        this.steps = steps;
        this.defaultStep = defaultStep;
        this.formatter = formatter;
        setOrientation(VERTICAL);
        // 行高落在 56dp 网格上：上一版上下留白各 12 与 8，加上滑杆自身约 40dp 就到了 65dp。
        setPadding(theme.dp(AiTheme.SPACE_L), theme.dp(AiTheme.SPACE_S),
                theme.dp(AiTheme.SPACE_L), 0);

        LinearLayout head = new LinearLayout(theme.context);
        head.setOrientation(HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = theme.text(AiTheme.TEXT_BODY, theme.text1);
        label.setText(title);
        head.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        valueView = theme.text(AiTheme.TEXT_SUBHEAD, theme.text2, AiTheme.Weight.MEDIUM);
        valueView.setGravity(Gravity.END);
        // 点数值 = 交回平台默认，这是唯一不需要拖动就能撤销的出口。
        valueView.setOnClickListener(view -> {
            if (listener != null) {
                listener.onValue(null);
            }
        });
        head.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        seekBar = new SeekBar(theme.context);
        thumbActive = seekBar.getThumb();
        seekBar.setMax(steps);
        // 系统默认的灰蓝进度色和全站品牌色不是一个蓝，这里统一到主题色。
        seekBar.getProgressDrawable().setColorFilter(theme.brand, android.graphics.PorterDuff.Mode.SRC_IN);
        seekBar.getThumb().setColorFilter(theme.brand, android.graphics.PorterDuff.Mode.SRC_IN);
        seekBar.setPadding(0, theme.dp(2), 0, 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueView.setText(AiSliderRow.this.formatter.format(progress));
                if (fromUser && listener != null) {
                    listener.onValue(valueOf(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
        addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** 温度 / top_p：一位小数。 */
    static AiSliderRow decimal(@NonNull AiTheme theme, int title, float min, float max, int steps) {
        return new AiSliderRow(theme, title, min, max, steps, 0, step ->
                String.format(java.util.Locale.ROOT, "%.1f",
                        min + (max - min) * step / steps));
    }

    /** 整数档：最大输出、上下文条数。 */
    static AiSliderRow integer(@NonNull AiTheme theme, int title, int min, int max, int stepSize) {
        int steps = (max - min) / stepSize;
        return new AiSliderRow(theme, title, min, max, steps, 0, step ->
                String.valueOf(min + step * stepSize));
    }

    void setListener(@Nullable Listener next) {
        listener = next;
    }

    /**
     * @param value 具体值；{@code null} 表示平台默认（滑杆停在默认位置）
     */
    void bind(@Nullable Double value, @NonNull CharSequence defaultValueText) {
        binding = true;
        int step;
        if (value == null) {
            step = defaultStep;
            valueView.setText(defaultValueText);
            // 未设置时不画拇指：画一个停在一端的拇指会被轨道切出缺口，
            // 既像渲染坏了，也像"被设成了最小值"。干脆透明，只留一条压在灰里的轨道。
            seekBar.getThumb().setAlpha(0);
        } else {
            step = Math.round((float) ((value - min) / (max - min) * steps));
            valueView.setText(formatter.format(step));
            seekBar.getThumb().setAlpha(255);
        }
        seekBar.setProgress(Math.max(0, Math.min(steps, step)));
        binding = false;
    }

    @Nullable
    private Double valueOf(int step) {
        return (double) (min + (max - min) * step / steps);
    }

    /** 只用来让外层知道当前是不是"平台默认"，避免重复写同一个值。 */
    boolean isBinding() {
        return binding;
    }
}
