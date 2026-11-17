package com.ggec.earbuds.platform.update;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.update.port.UpdateSystemActions;

import java.util.Objects;

/**
 * 更新流程里宿主窗口的动作：唯一拼 {@code Intent}、唯一拉起系统页面的地方。
 *
 * <p>由装配根按「一次宿主生命周期」新建并注入更新适配层，适配层因此只认
 * {@link UpdateSystemActions} 这类领域端口，不出现 {@code Intent}/{@code Settings}。</p>
 */
public final class AndroidUpdateInteractions implements UpdateSystemActions {

    private final Activity hostActivity;

    public AndroidUpdateInteractions(@NonNull Activity hostActivity) {
        this.hostActivity = Objects.requireNonNull(hostActivity, "hostActivity");
    }

    @Override
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", hostActivity.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        hostActivity.getApplicationContext().startActivity(intent);
    }
}
