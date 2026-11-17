package com.ggec.earbuds.provider.biz_appupdate;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.update.port.UpdateSystemActions;
import com.ggec.earbuds.domain.update.port.UpdateUiPort;
import com.yovo.biz_appupdate.YoAppUpdate;
import com.yovo.biz_appupdate.api.UpdateUiEffect;
import com.yovo.biz_appupdate.api.UpdateUiEvent;
import com.yovo.biz_appupdate.api.UpdateUiSession;
import com.yovo.biz_appupdate.api.UpdateUiState;

/**
 * biz_appupdate 与本应用更新端口之间的双向适配（一个宿主生命周期一个实例）。
 *
 * <p>正向：会话的 {@link UpdateUiState}/{@link UpdateUiEvent} 翻成本应用端口词汇交给渲染面；
 * 反向：端口的 {@link UpdateUiPort.Actions} 翻成 {@link UpdateUiSession} 调用。系统页面的动作
 * （打开应用设置页）不在这里拼 {@code Intent}——那是平台层
 * {@code platform/update/AndroidUpdateInteractions} 的职责，由装配根注入
 * {@link UpdateSystemActions} 传进来。</p>
 *
 * <p>本类是本应用里唯一 import {@code com.yovo.biz_appupdate.*} 的更新代码；
 * 渲染面（{@code ui/update}）与端口实现（本类）互不 import。</p>
 *
 * <p>启动顺序固定：先跑一次启动检查（SplashGate），把决策写回宿主 Intent，再挂 UI 会话——
 * 呈现器只消费一次启动 Intent，先挂会话会丢掉「有新版本/可直接安装」的决策。</p>
 */
public final class AppUpdateUiHostAdapter
        implements UpdateUiPort, UpdateUiPort.Actions, com.yovo.biz_appupdate.api.UpdateUiPort {

    private final Activity hostActivity;
    private final UpdateSystemActions systemActions;
    private final YoAppUpdate appUpdate;

    @Nullable
    private UpdateUiPort.Renderer renderer;
    @Nullable
    private UpdateUiSession session;
    @Nullable
    private YoAppUpdate.LaunchSession launchCheck;

    /** 安装未知来源设置页已拉起，等宿主回前台收口。 */
    private boolean installPermissionPending;
    private boolean detached;

    AppUpdateUiHostAdapter(@NonNull Activity hostActivity,
                           @NonNull UpdateSystemActions systemActions,
                           @NonNull YoAppUpdate appUpdate) {
        this.hostActivity = hostActivity;
        this.systemActions = systemActions;
        this.appUpdate = appUpdate;
    }

    // ------------------------------------------------------------------ 端口

    @NonNull
    @Override
    public Actions actions() {
        return this;
    }

    @Override
    public void attach(@NonNull UpdateUiPort.Renderer renderer) {
        if (detached || this.renderer != null) {
            return;
        }
        this.renderer = renderer;
        startLaunchCheck();
    }

    @Override
    public void onHostResume() {
        UpdateUiSession current = session;
        if (current == null || !installPermissionPending) {
            return;
        }
        installPermissionPending = false;
        current.onInstallPermissionResult();
    }

    @Override
    public void detach() {
        if (detached) {
            return;
        }
        detached = true;
        if (launchCheck != null) {
            launchCheck.cancel();
            launchCheck = null;
        }
        UpdateUiSession current = session;
        session = null;
        if (current != null) {
            current.detach();
        }
        UpdateUiPort.Renderer target = renderer;
        renderer = null;
        if (target != null) {
            target.dismissAll();
        }
        installPermissionPending = false;
    }

    // ------------------------------------------------------- 反向：用户动作 → 会话

    @Override
    public void onCheckUpdate() {
        UpdateUiSession current = session;
        if (current != null) {
            current.checkUpdate();
        }
    }

    @Override
    public void onDownloadChosen(@NonNull UpdateUiPort.DownloadChoice choice) {
        UpdateUiSession current = session;
        if (current != null) {
            current.download(AppUpdateUiMapper.toDownloadMode(choice));
        }
    }

    @Override
    public void onUpdateDeferred() {
        UpdateUiSession current = session;
        if (current != null) {
            current.deferUpdate();
        }
    }

    @Override
    public void onInstallConfirmed() {
        UpdateUiSession current = session;
        if (current != null) {
            current.confirmInstall();
        }
    }

    @Override
    public void onLocalApkChosen(int index) {
        UpdateUiSession current = session;
        if (current != null) {
            current.selectLocalApk(index);
        }
    }

    // ------------------------------------------------------- 正向：会话 → 渲染面

    @Override
    public void onState(@NonNull UpdateUiState state) {
        UpdateUiPort.Renderer target = renderer;
        if (target == null) {
            return;
        }
        target.onState(AppUpdateUiMapper.toState(state));
    }

    @Override
    public void onEvent(@NonNull UpdateUiEvent event) {
        UpdateUiPort.Renderer target = renderer;
        if (target == null) {
            return;
        }
        target.onEvent(AppUpdateUiMapper.toEvent(event));
    }

    @Override
    public void onEffect(@NonNull UpdateUiEffect effect) {
        dispatch(AppUpdateUiMapper.toEffect(effect.kind()));
    }

    // ------------------------------------------------------------------ 内部

    private void startLaunchCheck() {
        launchCheck = appUpdate.startLaunchCheck(hostActivity, prompt -> {
            launchCheck = null;
            if (detached) {
                return;
            }
            Intent hostIntent = hostActivity.getIntent();
            if (hostIntent != null) {
                appUpdate.attachLaunchPrompt(hostIntent, prompt);
            }
            session = appUpdate.attachUpdateUi(hostActivity, hostIntent, this);
        });
    }

    private void dispatch(@NonNull UpdateUiPort.Effect effect) {
        switch (effect) {
            case REQUEST_INSTALL_PERMISSION -> {
                if (!appUpdate.requestInstallUnknownSources(hostActivity)) {
                    installPermissionPending = true;
                }
            }
            case OPEN_APP_SETTINGS -> systemActions.openAppSettings();
        }
    }
}
