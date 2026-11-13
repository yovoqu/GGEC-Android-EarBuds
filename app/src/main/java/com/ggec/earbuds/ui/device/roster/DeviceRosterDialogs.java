package com.ggec.earbuds.ui.device.roster;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.common.YoComponents;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;

/**
 * 设备台账的行菜单与确认框。
 *
 * <p>所有 YoUI 组件都登记到页面的 {@link YoComponents}，页面销毁时统一释放；
 * 输入框在读取文本时取当前值，不在点击时缓存旧值。</p>
 */
public final class DeviceRosterDialogs {

    private DeviceRosterDialogs() {
    }

    public static void showMenu(@NonNull AppCompatActivity activity, @NonNull YoComponents components,
                                @NonNull CharSequence name, @NonNull Runnable onRename,
                                @NonNull Runnable onForget) {
        YoDialog dialog = YoDialog.create(activity)
                .kind(YoDialog.Kind.STANDARD)
                .setTitle(name)
                .setNeutralButton(activity.getString(R.string.device_roster_rename), onRename)
                .setNegativeButton(activity.getString(R.string.device_dialog_cancel), () -> {
                })
                .setPositiveButton(activity.getString(R.string.device_roster_forget), onForget)
                .build();
        components.own(dialog::destroy);
        dialog.show();
    }

    public static void showRename(@NonNull AppCompatActivity activity,
                                  @NonNull YoComponents components,
                                  @NonNull CharSequence currentName,
                                  @NonNull java.util.function.Consumer<String> onConfirm) {
        YoTextInput input = YoTextInput.create(activity)
                .setText(currentName)
                .setMaxLength(20)
                .build();
        YoDialog dialog = YoDialog.create(activity)
                .kind(YoDialog.Kind.INPUT)
                .setTitle(activity.getString(R.string.device_roster_rename_title))
                .hosted(input)
                .setNegativeButton(activity.getString(R.string.device_dialog_cancel), () -> {
                })
                .setPositiveButton(activity.getString(R.string.device_dialog_confirm), () -> {
                    CharSequence text = input.getText();
                    onConfirm.accept(text == null ? "" : text.toString().trim());
                })
                .build();
        components.own(input::destroy);
        components.own(dialog::destroy);
        dialog.show();
    }

    public static void showForget(@NonNull AppCompatActivity activity,
                                  @NonNull YoComponents components,
                                  @NonNull CharSequence name, @NonNull Runnable onConfirm) {
        YoDialog dialog = YoDialog.create(activity)
                .kind(YoDialog.Kind.STANDARD)
                .setTitle(activity.getString(R.string.device_roster_forget_title))
                .setMessage(activity.getString(R.string.device_roster_forget_body, name))
                .setNegativeButton(activity.getString(R.string.device_dialog_cancel), () -> {
                })
                .setPositiveButton(activity.getString(R.string.device_dialog_confirm), onConfirm)
                .build();
        components.own(dialog::destroy);
        dialog.show();
    }
}
