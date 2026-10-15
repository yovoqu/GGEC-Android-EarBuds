package com.yovo.biz_appupdate.internal.infrastructure.local;

import android.content.Intent;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.biz_appupdate.internal.contract.LaunchPromptBridge;
import com.yovo.biz_appupdate.internal.model.LaunchDecision;
import com.yovo.biz_appupdate.internal.model.LocalApk;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import java.io.File;
import java.util.Collections;

/**
 * Splash -> Main 的启动决策传递桥。
 */
public final class SplashIntentBridge implements LaunchPromptBridge {

    private static final String EXTRA_ACTION = "biz_appupdate_action";
    private static final String EXTRA_VERSION = "biz_appupdate_version";
    private static final String EXTRA_VERSION_CODE = "biz_appupdate_version_code";
    private static final String EXTRA_DESC = "biz_appupdate_desc";
    private static final String EXTRA_URL = "biz_appupdate_url";
    private static final String EXTRA_FORCE = "biz_appupdate_force";
    private static final String EXTRA_APK_PATH = "biz_appupdate_apk_path";

    public SplashIntentBridge() {
    }

    @Override
    public void attach(@NonNull Intent intent, @NonNull LaunchDecision decision) {
        intent.putExtra(EXTRA_ACTION, decision.action().name());
        RemoteUpdate update = decision.update();
        if (update != null) {
            intent.putExtra(EXTRA_VERSION, update.version());
            intent.putExtra(EXTRA_VERSION_CODE, update.versionCode());
            intent.putExtra(EXTRA_DESC, update.description());
            intent.putExtra(EXTRA_URL, update.downloadUrl());
            intent.putExtra(EXTRA_FORCE, update.forceUpdate());
        }
        LocalApk localApk = decision.localApk();
        if (localApk != null) {
            intent.putExtra(EXTRA_APK_PATH, localApk.file().getAbsolutePath());
        }
    }

    @Override
    @Nullable
    public LaunchDecision consume(@Nullable Intent intent) {
        if (intent == null) {
            return null;
        }
        String actionName = intent.getStringExtra(EXTRA_ACTION);
        if (TextUtils.isEmpty(actionName)) {
            return null;
        }
        LaunchDecision.Action action;
        try {
            action = LaunchDecision.Action.valueOf(actionName);
        } catch (IllegalArgumentException e) {
            return null;
        }
        intent.removeExtra(EXTRA_ACTION);

        if (action == LaunchDecision.Action.NONE) {
            removeUpdateExtras(intent);
            return LaunchDecision.none();
        }
        if (action == LaunchDecision.Action.SELECT_LOCAL_APK) {
            removeUpdateExtras(intent);
            return LaunchDecision.selectLocalApks(Collections.emptyList());
        }
        if (action == LaunchDecision.Action.NEED_DOWNLOAD_DIR) {
            removeUpdateExtras(intent);
            return LaunchDecision.needDownloadDir();
        }

        String version = intent.getStringExtra(EXTRA_VERSION);
        if (TextUtils.isEmpty(version)) {
            removeUpdateExtras(intent);
            return null;
        }
        RemoteUpdate update = new RemoteUpdate(
                true,
                version,
                intent.getIntExtra(EXTRA_VERSION_CODE, 0),
                intent.getStringExtra(EXTRA_DESC) != null
                        ? intent.getStringExtra(EXTRA_DESC)
                        : "",
                intent.getStringExtra(EXTRA_URL) != null
                        ? intent.getStringExtra(EXTRA_URL)
                        : "",
                intent.getBooleanExtra(EXTRA_FORCE, false));
        removeUpdateExtras(intent);

        if (action == LaunchDecision.Action.INSTALL_DIRECT) {
            String apkPath = intent.getStringExtra(EXTRA_APK_PATH);
            intent.removeExtra(EXTRA_APK_PATH);
            if (TextUtils.isEmpty(apkPath)) {
                return null;
            }
            File apkFile = new File(apkPath);
            if (!apkFile.isFile()) {
                return null;
            }
            return LaunchDecision.installDirect(
                    update,
                    new LocalApk(apkFile, apkFile.lastModified(), update.version()));
        }
        return LaunchDecision.showUpdate(update);
    }

    private static void removeUpdateExtras(@NonNull Intent intent) {
        intent.removeExtra(EXTRA_VERSION);
        intent.removeExtra(EXTRA_VERSION_CODE);
        intent.removeExtra(EXTRA_DESC);
        intent.removeExtra(EXTRA_URL);
        intent.removeExtra(EXTRA_FORCE);
        intent.removeExtra(EXTRA_APK_PATH);
    }
}
