package com.ggec.earbuds.provider.biz_appupdate;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.update.port.UpdateSystemActions;
import com.ggec.earbuds.domain.update.port.UpdateUiPort;
import com.yovo.biz_appupdate.YoAppUpdate;

import java.util.Locale;

/**
 * biz_appupdate 的 Provider：进程内唯一的 {@link YoAppUpdate} 实例 + 每个宿主一个接缝。
 *
 * <p>SDK 没有 close：实例与进程同寿命，所以 {@code AppContainer.release()} 不为它新增关闭语义。
 * 宿主只拿到 {@link UpdateUiPort}，SDK 类型不出这个目录。</p>
 *
 * <p>初始化配置全部由本应用事实推导，Provider 内没有 URL/路径字面量：下载地址与落盘目录名来自
 * {@code res/values/strings_update.xml}，其余来自 {@link Context}（包名、应用名）与 Android 的
 * 应用私有目录能力（{@code DownloadDir.appPrivate} 落到 {@code getExternalFilesDir(null)/<name>}，
 * 不需要任何存储权限）。</p>
 */
public final class AppUpdateProviders {

    /** 与 AndroidManifest 的 {@code ${applicationId}.fileprovider} 一一对应。 */
    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";

    /**
     * 启动检查的兜底超时：检查未在预算内返回就按「无更新」继续，不拖住宿主。
     * 宿主不是 Splash，所以最短展示时长为 0。
     */
    private static final long LAUNCH_CHECK_TIMEOUT_MS = 5000L;

    private final YoAppUpdate appUpdate;

    public AppUpdateProviders(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        appUpdate = YoAppUpdate.create(appContext, config(appContext));
    }

    /**
     * 为一次宿主生命周期开一个接缝：状态/事件/系统动作都经它进出，解绑即丢。
     *
     * @param hostActivity    宿主窗口：SDK 的启动检查与安装许可都要它承载
     * @param systemActions   只由平台层能做的系统页面动作（装配根注入）
     */
    @NonNull
    public UpdateUiPort openHost(@NonNull Activity hostActivity,
                                 @NonNull UpdateSystemActions systemActions) {
        return new AppUpdateUiHostAdapter(hostActivity, systemActions, appUpdate);
    }

    @NonNull
    private static YoAppUpdate.Config config(@NonNull Context appContext) {
        return new YoAppUpdate.Config.Builder()
                .fileProviderAuthority(appContext.getPackageName() + FILE_PROVIDER_SUFFIX)
                .apkFilePrefix(apkFilePrefix(appContext))
                .downloadUrl(fallbackDownloadUrl(appContext))
                .downloadDir(YoAppUpdate.DownloadDir.appPrivate(downloadDirName(appContext)))
                .autoInstallOnLaunch(true)
                // 产品默认：任何网络都允许检查；WiFi 限制不是本机型的默认策略。
                .wifiOnly(false)
                .splashMinDelayMs(0L)
                .splashCheckTimeoutMs(LAUNCH_CHECK_TIMEOUT_MS)
                .storagePolicy(YoAppUpdate.StoragePolicy.SINGLE_LATEST)
                .build();
    }

    /**
     * 安装包文件名前缀：{@code {prefix}_{versionName}_{versionCode}.apk}。
     * 取应用显示名并去掉不安全字符，避免把产品名再写一遍字面量。
     */
    @NonNull
    private static String apkFilePrefix(@NonNull Context appContext) {
        String sanitized = appLabel(appContext).replaceAll("[^0-9A-Za-z]", "");
        if (!sanitized.isEmpty()) {
            return sanitized;
        }
        String packageName = appContext.getPackageName();
        int dot = packageName.lastIndexOf('.');
        return dot >= 0 && dot < packageName.length() - 1
                ? packageName.substring(dot + 1)
                : packageName;
    }

    @NonNull
    private static String appLabel(@NonNull Context appContext) {
        PackageManager packageManager = appContext.getPackageManager();
        ApplicationInfo info = appContext.getApplicationInfo();
        CharSequence label = info.loadLabel(packageManager);
        return (label != null ? label : appContext.getPackageName()).toString();
    }

    /**
     * 兜底下载地址：只在检查接口返回的地址不可用时才会被用到。
     * 值必须来自资源（应用常量），provider 内不出现 URL 字面量。
     */
    @NonNull
    private static String fallbackDownloadUrl(@NonNull Context appContext) {
        String url = appContext.getString(R.string.update_fallback_download_url).trim();
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            throw new IllegalStateException(
                    "R.string.update_fallback_download_url 必须是 http(s) 地址，当前：" + url);
        }
        return url;
    }

    /**
     * 更新包落盘目录名（相对应用私有外部目录）。与 {@code res/xml/filepaths_update.xml}
     * 暴露的根互不依赖：FileProvider 只暴露应用私有外部目录本身。
     */
    @NonNull
    private static String downloadDirName(@NonNull Context appContext) {
        return appContext.getString(R.string.update_download_dir_name).trim();
    }
}
