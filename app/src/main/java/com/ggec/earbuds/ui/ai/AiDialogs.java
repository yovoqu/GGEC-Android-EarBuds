package com.ggec.earbuds.ui.ai;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoSlider;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * AI 页面用到的几种对话框：确认、单行输入、多行编辑、滑块。
 *
 * <p>所有对话框都用 {@code YoDialog.Builder.show()}（库内部的 {@code showThenDestroy} 会在关闭时
 * 销毁对话框和 hosted 组件）。输入类对话框把“提交”交给调用方，调用方返回校验错误时对话框保持打开
 * 并把错误显示在输入框上，输入内容也不会丢。</p>
 */
public final class AiDialogs {

    /** 提交输入的内容。返回校验错误表示不接受，对话框保持打开；返回空表示接受。 */
    public interface Submit {
        @Nullable
        AiValidationError submit(@NonNull String text);
    }

    /** 动作列表里的一项。 */
    public record Choice(@NonNull CharSequence label, boolean destructive, @NonNull Runnable action) {
    }

    private AiDialogs() {
    }

    /**
     * 底部动作列表：每项一个按钮，最后固定一个取消。用来替代锚定在某一行上的弹出菜单——
     * 面板里的行点开后菜单会被面板盖住，需要先关面板再用这个。
     */
    public static void choices(@NonNull Context context, @NonNull CharSequence title,
                               @NonNull java.util.List<Choice> choices) {
        YoDialog.Action[] actions = new YoDialog.Action[choices.size() + 1];
        for (int i = 0; i < choices.size(); i++) {
            Choice choice = choices.get(i);
            YoDialog.Action action = YoDialog.Action.of(100 + i, choice.label(), choice.action());
            actions[i] = choice.destructive() ? action.alert() : action;
        }
        actions[choices.size()] = YoDialog.Action.negative(context.getString(R.string.ai_cancel), null);
        YoDialog.create(context)
                .kind(YoDialog.Kind.ACTION_SHEET)
                .setTitle(title)
                .setActions(actions)
                .show();
    }

    public static void confirm(@NonNull Context context, @NonNull CharSequence title, @NonNull CharSequence message,
                               @NonNull CharSequence positive, boolean destructive, @NonNull Runnable onConfirm) {
        YoDialog.Action ok = YoDialog.Action.positive(positive, onConfirm);
        YoDialog.create(context)
                .setTitle(title)
                .setMessage(message)
                .setActions(
                        YoDialog.Action.negative(context.getString(R.string.ai_cancel), null),
                        destructive ? ok.alert() : ok)
                .show();
    }

    /**
     * 单行输入。
     *
     * @param inputType 密钥用 PASSWORD（带显示开关），其余用 TEXT
     */
    public static void input(@NonNull Context context, @NonNull CharSequence title, @NonNull CharSequence hint,
                             @NonNull CharSequence initial, @NonNull YoTextInput.InputType inputType, int maxLength,
                             @NonNull Submit submit) {
        YoTextInput field = YoTextInput.create(context)
                .setText(initial)
                .setPlaceholder(hint)
                .setInputType(inputType)
                .setMaxLength(maxLength)
                .setShowCancel(true)
                .setShowErrorEnabled(true)
                .build();
        input(context, title, field, null, submit, () -> field.getText().toString());
    }

    /**
     * 带一个“恢复默认”次要按钮的单行输入，用于接口地址这类有默认值的设置。
     */
    public static void inputWithReset(@NonNull Context context, @NonNull CharSequence title,
                                      @NonNull CharSequence hint, @NonNull CharSequence initial,
                                      @NonNull CharSequence resetLabel, @NonNull Submit submit) {
        YoTextInput field = YoTextInput.create(context)
                .setText(initial)
                .setPlaceholder(hint)
                .setInputType(YoTextInput.InputType.TEXT)
                .setMaxLength(300)
                .setShowCancel(true)
                .setShowErrorEnabled(true)
                .build();
        input(context, title, field, resetLabel, submit, () -> field.getText().toString());
    }

    private static void input(Context context, CharSequence title, YoTextInput field,
                              @Nullable CharSequence resetLabel, Submit submit,
                              java.util.function.Supplier<String> value) {
        boolean[] keepOpen = {false};
        YoDialog.Builder builder = YoDialog.create(context)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setTitle(title)
                .setNegativeButton(context.getString(R.string.ai_cancel), null)
                .setPositiveButton(context.getString(R.string.ai_save), () -> {
                    AiValidationError error = submit.submit(value.get());
                    if (error != null) {
                        field.showError(context.getString(AiMessages.validation(error)));
                        keepOpen[0] = true;
                    }
                })
                .setOnWillDismissListener(cause -> {
                    if (cause == YoDialog.DismissCause.ACTION && keepOpen[0]) {
                        keepOpen[0] = false;
                        return false;
                    }
                    return true;
                })
                .setOnDismissListener(() -> AiKeyboard.hide(context));
        if (resetLabel != null) {
            builder.setNeutralButton(resetLabel, () -> submit.submit(""));
        }
        builder.show();
    }

    /**
     * 多行文本编辑，用于系统提示词。{@code maxLength} 之内才能保存，计数与错误提示由 {@link YoTextInput} 自己画。
     */
    public static void editText(@NonNull Context context, @NonNull CharSequence title, @NonNull CharSequence hint,
                                @NonNull CharSequence initial, int maxLength, @Nullable CharSequence resetLabel,
                                @Nullable Runnable onReset, @NonNull Submit submit) {
        YoTextInput field = YoTextInput.create(context)
                .setText(initial)
                .setPlaceholder(hint)
                .setMaxLength(maxLength)
                .setShowCounter(true)
                .setShowErrorEnabled(true)
                .setMultiline(true, 5, 10)
                .build();
        boolean[] keepOpen = {false};
        YoDialog.Builder builder = YoDialog.create(context)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setTitle(title)
                .setNegativeButton(context.getString(R.string.ai_cancel), null)
                .setPositiveButton(context.getString(R.string.ai_save), () -> {
                    AiValidationError failure = submit.submit(field.getText().toString());
                    if (failure != null) {
                        field.showError(context.getString(AiMessages.validation(failure)));
                        keepOpen[0] = true;
                    }
                })
                .setOnWillDismissListener(cause -> {
                    if (cause == YoDialog.DismissCause.ACTION && keepOpen[0]) {
                        keepOpen[0] = false;
                        return false;
                    }
                    return true;
                })
                .setOnDismissListener(() -> AiKeyboard.hide(context));
        if (resetLabel != null && onReset != null) {
            builder.setNeutralButton(resetLabel, onReset);
        }
        builder.show();
    }

    /**
     * 滑块对话框，用于温度。{@code initial} 为空表示当前用平台默认值；“默认”按钮把值清回默认。
     */
    public static void slider(@NonNull Context context, @NonNull YoComponents owner,
                              @NonNull CharSequence title, @NonNull CharSequence summary,
                              float min, float max, float step, @Nullable Float initial, float fallback,
                              @NonNull CharSequence defaultLabel, @NonNull DoubleConsumer onPick,
                              @NonNull Runnable onDefault) {
        AiTheme theme = AiTheme.of(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView value = theme.text(AiTheme.TEXT_HEADLINE, theme.text1, AiTheme.Weight.MEDIUM);
        value.setGravity(Gravity.CENTER);
        content.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        float[] current = {initial != null ? initial : fallback};
        value.setText(String.format(Locale.ROOT, "%.1f", current[0]));
        YoSlider slider = YoSlider.create(context)
                .setMin(min)
                .setMax(max)
                .setStep(step)
                .setValue(current[0])
                .setOnProgressChangeListener((progress, fromUser) -> {
                    current[0] = Math.round(progress / step) * step;
                    value.setText(String.format(Locale.ROOT, "%.1f", current[0]));
                })
                .build();
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sliderParams.topMargin = theme.dp(AiTheme.SPACE_M);
        content.addView(slider.getView(), sliderParams);
        owner.own(slider::destroy);
        TextView hint = theme.text(AiTheme.TEXT_CAPTION, theme.text2);
        hint.setText(summary);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = theme.dp(AiTheme.SPACE_S);
        content.addView(hint, hintParams);
        YoDialog.create(context)
                .setTitle(title)
                .setContentView(content)
                .setNegativeButton(context.getString(R.string.ai_cancel), null)
                .setNeutralButton(defaultLabel, onDefault)
                .setPositiveButton(context.getString(R.string.ai_save), () -> onPick.accept(current[0]))
                .setOnDismissListener(slider::destroy)
                .show();
    }
}
