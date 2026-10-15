package com.yovo.biz_appupdate.internal.ui;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.R;
import com.yovo.yotheme.dialog.YoDialog;
import com.yovo.yotheme.dialog.preset.MessageDialogPreset;

import java.io.File;

/**
 * 安装确认弹窗。
 */
public final class InstallConfirmDialog {

    public interface OnInstallListener {
        void onInstall(@NonNull File apkFile);
    }

    private final Context context;

    public InstallConfirmDialog(@NonNull Context context) {
        this.context = context;
    }

    public void show(@NonNull File apkFile, @NonNull OnInstallListener listener) {
        YoDialog.show(context,
                MessageDialogPreset.create()
                        .title(context.getString(R.string.install_confirm_title))
                        .message(context.getString(R.string.install_confirm_message, apkFile.getName()))
                        .positive(context.getString(R.string.install_confirm_positive),
                                result -> listener.onInstall(apkFile))
                        .negative(context.getString(R.string.install_confirm_negative), null)
                        .build());
    }
}
