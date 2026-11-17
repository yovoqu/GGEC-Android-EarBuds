package com.ggec.earbuds.ui.update;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.update.port.UpdateUiPort;
import com.yohu.youi.api.feedback.YoDialog;
import com.yohu.youi.api.feedback.YoToast;

import java.util.List;

/**
 * 更新弹层的渲染编排：把端口的「状态 / 一次性呈现」落到具体视图上。
 *
 * <p>视图只有它和 {@link UpdateDialogs}：{@code ui} 层不做流程判断，也不碰文件与网络；
 * 用户动作一律经 {@link UpdateUiPort.Actions} 回灌适配层。本类实现
 * {@link UpdateUiPort.Renderer}，因此只依赖领域端口，不 import 任何 SDK 或 provider 类型。</p>
 */
public final class UpdateOverlay implements UpdateUiPort.Renderer {

    private final Context context;
    private final UpdateUiPort.Actions actions;

    @Nullable
    private YoDialog checkingDialog;
    @Nullable
    private UpdateDialogs.ProgressHandle progressHandle;
    @Nullable
    private YoDialog promptDialog;
    @Nullable
    private YoDialog installDialog;
    @Nullable
    private YoDialog pickerDialog;

    public UpdateOverlay(@NonNull Context context, @NonNull UpdateUiPort.Actions actions) {
        this.context = context;
        this.actions = actions;
    }

    // ------------------------------------------------------------------ 端口

    /** 状态整体替换：检查中给占位弹窗，下载中给进度。 */
    @Override
    public void onState(@NonNull UpdateUiPort.State state) {
        if (state.checking()) {
            if (checkingDialog == null) {
                checkingDialog = UpdateDialogs.showChecking(context);
            }
        } else {
            dismissChecking();
        }

        if (state.downloadInProgress()) {
            if (progressHandle == null) {
                progressHandle = UpdateDialogs.showProgress(context);
            }
            progressHandle.setPercent(state.downloadPercent());
        } else {
            dismissProgress();
        }
    }

    /** 一次性呈现：按类型分给对应的弹层或轻提示。 */
    @Override
    public void onEvent(@NonNull UpdateUiPort.Event event) {
        switch (event) {
            case UpdateUiPort.Event.Notify notify -> onNotice(notify.notice(), notify.detail());
            case UpdateUiPort.Event.ShowUpdatePrompt prompt -> onPrompt(prompt.prompt());
            case UpdateUiPort.Event.ShowLocalApkPicker picker ->
                    onLocalApkPicker(picker.fileNames());
            case UpdateUiPort.Event.ConfirmInstall confirm ->
                    onConfirmInstall(confirm.apkFileName());
        }
    }

    /** 解绑：关掉自己开过的所有弹窗，之后不再被调用。 */
    @Override
    public void dismissAll() {
        dismissChecking();
        dismissProgress();
        dismissPrompt();
        dismissInstall();
        dismissPicker();
    }

    // ------------------------------------------------------------------ 渲染

    /** 一次性轻提示。文案按 kind 选资源，{@code detail} 只作为附加说明填进去。 */
    private void onNotice(@NonNull UpdateUiPort.Notice notice, @NonNull String detail) {
        switch (notice) {
            case ALREADY_LATEST -> toast(context.getString(R.string.update_toast_already_latest));
            case CHECK_FAILED -> toast(context.getString(R.string.update_toast_check_failed, detail));
            case DOWNLOAD_STARTED_SYSTEM ->
                    toast(context.getString(R.string.update_toast_system_started));
            case DOWNLOAD_STARTED_BROWSER ->
                    toast(context.getString(R.string.update_toast_browser_started));
            case DOWNLOAD_START_FAILED_INTERNAL, DOWNLOAD_START_FAILED_SYSTEM ->
                    toast(context.getString(R.string.update_toast_system_failed));
            case DOWNLOAD_START_FAILED_BROWSER ->
                    toast(context.getString(R.string.update_toast_browser_failed));
            case DOWNLOAD_COMPLETED ->
                    toast(context.getString(R.string.update_toast_download_complete, detail));
            case DOWNLOAD_FAILED -> toast(detail.isEmpty()
                    ? context.getString(R.string.update_toast_download_failed)
                    : context.getString(R.string.update_toast_download_failed_detail, detail));
            case INSTALL_INVALID_FILE ->
                    toast(context.getString(R.string.update_toast_download_failed));
            case INSTALL_VERIFY_FAILED ->
                    toast(context.getString(R.string.update_toast_install_verify_failed));
            case INSTALL_NO_INSTALLER ->
                    toast(context.getString(R.string.update_toast_no_installer));
            case INSTALL_PERMISSION_DENIED ->
                    toast(context.getString(R.string.update_toast_install_permission_denied));
        }
    }

    /** 有新版本：弹更新弹窗，用户选下载方式或稍后。 */
    private void onPrompt(@NonNull UpdateUiPort.Prompt prompt) {
        dismissPrompt();
        promptDialog = UpdateDialogs.showUpdatePrompt(
                context,
                prompt,
                choice -> {
                    dismissPrompt();
                    actions.onDownloadChosen(choice);
                },
                () -> {
                    dismissPrompt();
                    actions.onUpdateDeferred();
                });
    }

    /** 手机里有多份本地安装包。 */
    private void onLocalApkPicker(@NonNull List<String> fileNames) {
        dismissPicker();
        pickerDialog = UpdateDialogs.showLocalApkPicker(context, fileNames, index -> {
            dismissPicker();
            actions.onLocalApkChosen(index);
        });
    }

    /** 安装前确认。 */
    private void onConfirmInstall(@NonNull String apkFileName) {
        dismissInstall();
        installDialog = UpdateDialogs.showInstallConfirm(context, apkFileName, () -> {
            dismissInstall();
            actions.onInstallConfirmed();
        });
    }

    private void toast(@NonNull String text) {
        YoToast.show(context, text);
    }

    private void dismissChecking() {
        if (checkingDialog != null) {
            checkingDialog.dismiss();
            checkingDialog = null;
        }
    }

    private void dismissProgress() {
        if (progressHandle != null) {
            progressHandle.dismiss();
            progressHandle = null;
        }
    }

    private void dismissPrompt() {
        if (promptDialog != null) {
            promptDialog.dismiss();
            promptDialog = null;
        }
    }

    private void dismissInstall() {
        if (installDialog != null) {
            installDialog.dismiss();
            installDialog = null;
        }
    }

    private void dismissPicker() {
        if (pickerDialog != null) {
            pickerDialog.dismiss();
            pickerDialog = null;
        }
    }
}
