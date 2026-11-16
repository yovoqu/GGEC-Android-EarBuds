package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 动作行组件：一个能力 = 一组按钮。
 *
 * <p>按钮与动作的对应关系由 {@link DeviceSettingRow.ActionKind} 决定（能力 id → kind 的路由在
 * {@code SettingsSectionFactory}，本类不再判断 id）。通过回调派发意图，界面不拼协议字节、
 * 不构造领域动作模型。需要二次确认或输入的动作（重启、清除配对、改名）走对话框。</p>
 */
final class ActionCardView {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final SettingsInteractionListener listener;
    private final List<RecordFileView> recordFiles;
    private final List<Runnable> releases = new ArrayList<>();

    private final LinearLayout view;
    private final TextView title;
    private final LinearLayout buttons;

    ActionCardView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                   @NonNull SettingsInteractionListener listener,
                   @NonNull List<RecordFileView> recordFiles) {
        this.activity = activity;
        this.theme = theme;
        this.listener = listener;
        this.recordFiles = recordFiles;

        view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);

        title = theme.text(DeviceTheme.TEXT_BODY, theme.text1, DeviceTheme.Weight.MEDIUM);
        view.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buttons = SettingsChrome.horizontal(activity);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = theme.dp(DeviceTheme.SPACE_S);
        view.addView(buttons, buttonsParams);
    }

    @NonNull
    View view() {
        return view;
    }

    void bind(@NonNull DeviceSettingRow.Action row, boolean interactive) {
        title.setText(row.title());
        if (row.kind() == DeviceSettingRow.ActionKind.DEVICE_MANAGEMENT) {
            title.setText(R.string.about_device_management);
        }
        buttons.removeAllViews();
        releaseButtons();
        switch (row.kind()) {
            case FIND_EAR -> bindFindEar(interactive);
            case RENAME -> bindRename(interactive);
            case REBOOT -> bindReboot(interactive);
            case CLEAR_PAIRING -> bindClearPairing(interactive);
            case RESET_CONTROL -> bindResetControl(interactive);
            case RECORD_LOCAL -> bindRecord(interactive,
                    R.string.device_row_record_start, listener::onStartRecordLocal,
                    R.string.device_row_record_stop, listener::onStopRecordLocal);
            case RECORD_STREAM -> bindRecord(interactive,
                    R.string.device_action_record_stream_start, listener::onStartRecordStream,
                    R.string.device_action_record_stream_stop, listener::onStopRecordStream);
            case RECORD_TRANSFER -> bindTransfer(interactive);
            case DEVICE_MANAGEMENT -> bindDeviceManagement(interactive);
        }
    }

    void destroy() {
        releaseButtons();
    }

    private void bindFindEar(boolean interactive) {
        addButton(R.string.device_row_find_left, interactive, false, listener::onFindEarLeft);
        addButton(R.string.device_row_find_right, interactive, false, listener::onFindEarRight);
        addButton(R.string.device_row_find_stop, interactive, false, listener::onStopFindAlert);
    }

    private void bindRename(boolean interactive) {
        addButton(R.string.device_action_rename, interactive, false, this::openRenameDialog);
    }

    private void bindReboot(boolean interactive) {
        addButton(R.string.device_action_reboot, interactive, false,
                () -> confirm(R.string.device_action_reboot_confirm, listener::onReboot));
    }

    private void bindClearPairing(boolean interactive) {
        addButton(R.string.device_row_clear_pairing, interactive, true,
                () -> confirm(R.string.device_action_clear_pairing_confirm,
                        listener::onClearPairing));
    }

    private void bindResetControl(boolean interactive) {
        addButton(R.string.device_action_reset_control, interactive, false,
                () -> confirm(R.string.device_action_reset_control_confirm,
                        listener::onResetControl));
    }

    private void bindRecord(boolean interactive,
                            @StringRes int startLabel, @NonNull Runnable onStart,
                            @StringRes int stopLabel, @NonNull Runnable onStop) {
        addButton(startLabel, interactive, false, onStart);
        addButton(stopLabel, interactive, false, onStop);
    }

    private void bindTransfer(boolean interactive) {
        boolean hasFile = !recordFiles.isEmpty();
        addButton(R.string.device_row_record_sync, interactive && hasFile, false, () -> {
            RecordFileView newest = recordFiles.get(0);
            listener.onFileTransfer(newest.fileId());
        });
    }

    /**
     * 设备管理：忘记设备（先断活动会话、再从台账移除，成功后关闭本页）。
     *
     * <p>改名不在这里重复提供：设备名由 biz 的 {@code RENAME} 能力行负责，
     * 两个同名按钮只会让用户分不清改的是设备还是台账。</p>
     */
    private void bindDeviceManagement(boolean interactive) {
        addButton(R.string.device_forget, interactive, true, () -> confirm(
                R.string.device_forget_confirm, listener::onForgetActive));
    }

    private void addButton(@StringRes int labelRes, boolean enabled, boolean destructive,
                           @NonNull Runnable onClick) {
        YoButton button = YoButton.create(activity).build();
        button.setText(activity.getString(labelRes));
        button.setEnabled(enabled);
        if (destructive) {
            button.setTextColor(theme.alert);
        }
        if (enabled) {
            button.setOnClickListener(v -> onClick.run());
        }
        releases.add(button::destroy);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, theme.dp(DeviceTheme.TOUCH_TARGET), 1f);
        params.setMarginEnd(theme.dp(DeviceTheme.SPACE_XS));
        buttons.addView(button.getView(), params);
    }

    private void confirm(@StringRes int messageRes, @NonNull Runnable onConfirm) {
        YoDialog.create(activity)
                .setTitle(activity.getString(R.string.device_action_confirm_title))
                .setMessage(activity.getString(messageRes))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.device_dialog_cancel),
                                null),
                        YoDialog.Action.positive(activity.getString(R.string.device_dialog_confirm),
                                onConfirm))
                .show();
    }

    private void openRenameDialog() {
        YoTextInput field = YoTextInput.create(activity)
                .setPlaceholder(activity.getString(R.string.device_action_rename))
                .setMaxLength(20)
                .setShowCounter(true)
                .setShowErrorEnabled(true)
                .build();
        YoDialog.create(activity)
                .kind(YoDialog.Kind.INPUT)
                .hosted(field)
                .setTitle(activity.getString(R.string.device_action_rename))
                .setActions(
                        YoDialog.Action.negative(activity.getString(R.string.device_dialog_cancel),
                                null),
                        YoDialog.Action.positive(activity.getString(R.string.device_dialog_confirm),
                                () -> {
                                    String name = String.valueOf(field.getText()).trim();
                                    if (!name.isEmpty()) {
                                        listener.onRename(name);
                                    }
                                }))
                .show();
    }

    private void releaseButtons() {
        for (Runnable release : releases) {
            release.run();
        }
        releases.clear();
    }
}
