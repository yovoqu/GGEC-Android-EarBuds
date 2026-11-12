package com.ggec.earbuds.ui.device.settings.section;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.device.model.DeviceAction;
import com.ggec.earbuds.domain.device.model.RecordFileView;
import com.ggec.earbuds.ui.device.DeviceTheme;
import com.ggec.earbuds.ui.device.settings.DeviceSettingRow;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsViewModel;
import com.yohu.youi.api.controls.YoButton;
import com.yohu.youi.api.controls.YoTextInput;
import com.yohu.youi.api.feedback.YoDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 动作行组件：一个能力 = 一组按钮。
 *
 * <p>按钮与动作的对应关系由 {@link DeviceSettingRow.ActionKind} 决定（能力 id → kind 的路由在
 * {@code SettingsSectionFactory}，本类不再判断 id）。动作本身是类型化的
 * {@link DeviceAction}，界面不拼协议字节。需要二次确认或输入的动作（重启、清除配对、改名）
 * 走对话框。</p>
 */
final class ActionCardView {

    private final AppCompatActivity activity;
    private final DeviceTheme theme;
    private final DeviceSettingsViewModel viewModel;
    private final List<RecordFileView> recordFiles;
    private final List<Runnable> releases = new ArrayList<>();

    private final LinearLayout view;
    private final TextView title;
    private final LinearLayout buttons;

    ActionCardView(@NonNull AppCompatActivity activity, @NonNull DeviceTheme theme,
                   @NonNull DeviceSettingsViewModel viewModel,
                   @NonNull List<RecordFileView> recordFiles) {
        this.activity = activity;
        this.theme = theme;
        this.viewModel = viewModel;
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
        buttons.removeAllViews();
        releaseButtons();
        switch (row.kind()) {
            case FIND_EAR -> bindFindEar(interactive);
            case RENAME -> bindRename(interactive);
            case REBOOT -> bindReboot(interactive);
            case CLEAR_PAIRING -> bindClearPairing(interactive);
            case RESET_CONTROL -> bindResetControl(interactive);
            case RECORD_LOCAL -> bindRecord(DeviceAction.RecordMode.LOCAL, interactive,
                    R.string.device_row_record_start, R.string.device_row_record_stop);
            case RECORD_STREAM -> bindRecord(DeviceAction.RecordMode.STREAM, interactive,
                    R.string.device_action_record_stream_start,
                    R.string.device_action_record_stream_stop);
            case RECORD_TRANSFER -> bindTransfer(interactive);
        }
    }

    void destroy() {
        releaseButtons();
    }

    private void bindFindEar(boolean interactive) {
        addButton(R.string.device_row_find_left, interactive, false,
                () -> viewModel.run(new DeviceAction.FindEar(DeviceAction.FindEar.Side.LEFT)));
        addButton(R.string.device_row_find_right, interactive, false,
                () -> viewModel.run(new DeviceAction.FindEar(DeviceAction.FindEar.Side.RIGHT)));
        addButton(R.string.device_row_find_stop, interactive, false,
                () -> viewModel.run(new DeviceAction.StopFindAlert()));
    }

    private void bindRename(boolean interactive) {
        addButton(R.string.device_action_rename, interactive, false, this::openRenameDialog);
    }

    private void bindReboot(boolean interactive) {
        addButton(R.string.device_action_reboot, interactive, false,
                () -> confirm(R.string.device_action_reboot_confirm,
                        () -> viewModel.run(new DeviceAction.Reboot())));
    }

    private void bindClearPairing(boolean interactive) {
        addButton(R.string.device_row_clear_pairing, interactive, true,
                () -> confirm(R.string.device_action_clear_pairing_confirm,
                        () -> viewModel.run(new DeviceAction.ClearPairing())));
    }

    private void bindResetControl(boolean interactive) {
        addButton(R.string.device_action_reset_control, interactive, false,
                () -> confirm(R.string.device_action_reset_control_confirm,
                        () -> viewModel.run(new DeviceAction.ResetControl())));
    }

    private void bindRecord(DeviceAction.RecordMode mode, boolean interactive,
                            @StringRes int startLabel, @StringRes int stopLabel) {
        addButton(startLabel, interactive, false,
                () -> viewModel.run(new DeviceAction.StartRecord(mode)));
        addButton(stopLabel, interactive, false,
                () -> viewModel.run(new DeviceAction.StopRecord(mode)));
    }

    private void bindTransfer(boolean interactive) {
        boolean hasFile = !recordFiles.isEmpty();
        addButton(R.string.device_row_record_sync, interactive && hasFile, false, () -> {
            RecordFileView newest = recordFiles.get(0);
            viewModel.run(new DeviceAction.TransferRecord(newest.fileId(), true));
        });
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
                                        viewModel.run(new DeviceAction.Rename(name));
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
