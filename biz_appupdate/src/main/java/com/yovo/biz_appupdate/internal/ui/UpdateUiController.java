package com.yovo.biz_appupdate.internal.ui;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.yovo.biz_appupdate.R;
import com.yovo.biz_appupdate.YoAppUpdate;
import com.yovo.biz_appupdate.internal.model.DownloadMode;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;
import com.yovo.yotheme.components.yo_loading.YoLoading;
import com.yovo.yotheme.components.yo_toast.YoToast;
import com.yovo.yotheme.dialog.YoDialog;

import java.io.File;
import java.util.List;

/**
 * SDK 内聚的更新 UI 控制器：只负责订阅 ViewModel 并渲染。
 */
public final class UpdateUiController {

    private final Activity activity;
    private final LifecycleOwner lifecycleOwner;
    private final UpdateViewModel viewModel;
    private final YoAppUpdate.UpdateUiHost host;
    private final UpdateModeDialog updateModeDialog;
    private final InstallConfirmDialog installConfirmDialog;
    private final LocalApkPickerDialog localApkPickerDialog;
    @Nullable
    private DownloadProgressDialog downloadProgressDialog;
    @Nullable
    private YoLoading loadingDialog;
    private boolean detached;

    public UpdateUiController(@NonNull Activity activity,
                              @NonNull UpdateViewModel viewModel,
                              @NonNull YoAppUpdate.UpdateUiHost host) {
        if (!(activity instanceof LifecycleOwner)) {
            throw new IllegalArgumentException("attachUpdateUi requires a LifecycleOwner Activity");
        }
        this.activity = activity;
        this.lifecycleOwner = (LifecycleOwner) activity;
        this.viewModel = viewModel;
        this.host = host;
        this.updateModeDialog = new UpdateModeDialog(activity);
        this.installConfirmDialog = new InstallConfirmDialog(activity);
        this.localApkPickerDialog = new LocalApkPickerDialog(activity);
        observe();
    }

    public void checkUpdate() {
        viewModel.checkUpdate();
    }

    public void onSafTreeResult(boolean granted) {
        viewModel.onSafTreeResult(granted);
    }

    public void onDownloadDirPermissionResult(boolean granted) {
        viewModel.onDownloadDirPermissionResult(granted);
    }

    public void onDownloadDirPermissionResume() {
        viewModel.onDownloadDirPermissionResume();
    }

    public void onDownloadDirPermissionPermanentlyDenied() {
        viewModel.onDownloadDirPermissionPermanentlyDenied();
    }

    public void onInstallPermissionResult() {
        viewModel.onInstallPermissionResult();
    }

    public void detach() {
        if (detached) {
            return;
        }
        detached = true;
        viewModel.state().removeObservers(lifecycleOwner);
        viewModel.events().removeObservers(lifecycleOwner);
        viewModel.effects().removeObservers(lifecycleOwner);
        YoDialog.dismissAll(activity);
        hideDownloadProgressDialog();
        hideLoading();
    }

    private void observe() {
        viewModel.state().observe(lifecycleOwner, state -> {
            if (state == null || detached) {
                return;
            }
            if (state.checking()) {
                showLoading();
            } else {
                hideLoading();
            }
            if (state.downloadInProgress()) {
                showDownloadProgressDialog();
                updateDownloadProgress(state.downloadPercent());
            } else {
                hideDownloadProgressDialog();
            }
        });
        viewModel.events().observe(lifecycleOwner, event -> {
            if (event != null && !detached) {
                handleEvent(event);
            }
        });
        viewModel.effects().observe(lifecycleOwner, effect -> {
            if (effect != null && !detached) {
                dispatchEffect(effect);
            }
        });
    }

    private void handleEvent(@NonNull UpdateUiEvent event) {
        switch (event.kind()) {
            case CHECK_ALREADY_LATEST:
                YoToast.show(activity, R.string.toast_already_latest);
                break;
            case CHECK_FAILED:
                YoToast.show(activity, activity.getString(
                        R.string.toast_check_update_failed,
                        event.message() != null ? event.message() : ""));
                break;
            case SHOW_UPDATE_DIALOG:
                if (event.update() != null) {
                    showUpdateDialog(event.update(), event.autoPrompt());
                }
                break;
            case SHOW_LOCAL_APK_PICKER:
                showLocalApkPicker(event.apkFiles());
                break;
            case INSTALL_APK:
                if (event.update() != null && event.apkFile() != null) {
                    viewModel.onUpdateDialogShown(event.update(), true);
                    showInstallConfirmDialog(event.apkFile(), event.update());
                }
                break;
            case INSTALL_NOW:
                if (event.apkFile() != null) {
                    viewModel.install(activity, event.apkFile(), event.update());
                }
                break;
            case INSTALL_PERMISSION_DENIED:
                YoToast.show(activity, R.string.toast_install_permission_denied);
                break;
            case INSTALL_VERIFY_FAILED:
                YoToast.show(activity, R.string.toast_install_verify_failed);
                break;
            case DOWNLOAD_STARTED_BROWSER:
                YoToast.show(activity, R.string.toast_browser_download_started);
                break;
            case DOWNLOAD_STARTED_SYSTEM:
                YoToast.show(activity, R.string.toast_system_download_started);
                break;
            case DOWNLOAD_START_FAILED:
                if (event.mode() == DownloadMode.BROWSER) {
                    YoToast.show(activity, R.string.toast_browser_download_failed);
                } else if (event.mode() == DownloadMode.SYSTEM) {
                    YoToast.show(activity, R.string.toast_system_download_failed);
                } else {
                    YoToast.show(activity, R.string.toast_download_failed);
                }
                break;
            case DOWNLOAD_SUCCESS:
                File file = event.apkFile();
                YoToast.show(activity, activity.getString(
                        R.string.toast_download_complete,
                        file != null ? file.getName() : ""));
                if (file != null) {
                    showInstallConfirmDialog(file, event.update());
                } else {
                    YoToast.show(activity, R.string.toast_download_failed);
                }
                break;
            case DOWNLOAD_FAILED:
                if (event.message() != null && !event.message().isEmpty()) {
                    YoToast.show(activity, activity.getString(
                            R.string.toast_download_failed_detail, event.message()));
                } else {
                    YoToast.show(activity, R.string.toast_download_failed);
                }
                break;
            case INSTALL_INVALID_FILE:
                YoToast.show(activity, R.string.toast_download_failed);
                break;
            case INSTALL_NO_HANDLER:
                YoToast.show(activity, R.string.toast_no_installer);
                break;
            case DOWNLOAD_STARTED_INTERNAL:
            default:
                break;
        }
    }

    private void showUpdateDialog(@NonNull RemoteUpdate update, boolean autoPrompt) {
        viewModel.onUpdateDialogShown(update, autoPrompt);
        updateModeDialog.show(
                update,
                mode -> viewModel.download(mode, update),
                () -> viewModel.ignoreVersion(update));
    }

    private void showLocalApkPicker(@NonNull List<File> apkFiles) {
        if (apkFiles.isEmpty()) {
            return;
        }
        localApkPickerDialog.show(apkFiles, file -> viewModel.requestInstall(file, null));
    }

    private void showDownloadProgressDialog() {
        if (downloadProgressDialog == null) {
            downloadProgressDialog = new DownloadProgressDialog(activity);
            downloadProgressDialog.show();
        }
    }

    private void updateDownloadProgress(int percent) {
        if (downloadProgressDialog != null) {
            downloadProgressDialog.updatePercent(percent);
        }
    }

    private void hideDownloadProgressDialog() {
        if (downloadProgressDialog != null) {
            downloadProgressDialog.dismiss();
        }
        downloadProgressDialog = null;
    }

    private void showInstallConfirmDialog(@NonNull File apkFile,
                                          @Nullable RemoteUpdate update) {
        installConfirmDialog.show(apkFile,
                file -> viewModel.requestInstall(file, update));
    }

    private void dispatchEffect(@NonNull UpdateUiEffect effect) {
        switch (effect.kind()) {
            case SAF_PICKER:
                host.openSafTreePicker();
                break;
            case REQUEST_DOWNLOAD_DIR_PERMISSION:
                host.requestDownloadDirPermission();
                break;
            case REQUEST_INSTALL_UNKNOWN_SOURCES:
                host.requestInstallUnknownSources();
                break;
            case OPEN_APP_SETTINGS:
                host.openAppSettings();
                break;
            default:
                break;
        }
    }

    private void showLoading() {
        if (loadingDialog == null) {
            loadingDialog = YoLoading.create(activity);
        }
        loadingDialog.showLoading();
    }

    private void hideLoading() {
        if (loadingDialog != null) {
            loadingDialog.hideLoading();
        }
        loadingDialog = null;
    }

}
