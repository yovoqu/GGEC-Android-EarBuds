package com.ggec.earbuds.provider.lib_common.download;

import android.content.Context;

import androidx.annotation.NonNull;

import com.yovo.lib_common.download.YoDownload;

/**
 * App 侧 YoDownload 的装配落点：只被装配根（{@code wiring/AppContainer}）在建图时调用一次。
 *
 * <p>下载引擎是进程级单例持有者（{@link YoDownload} 内部只有一份安装状态），biz_appupdate 的
 * 断点续传（{@code YoDownloadResumerProvider.resume → YoDownload.queryAll}）在启动检查阶段就会用到它，
 * 所以必须在装配期显式 init，否则启动即抛
 * {@code IllegalStateException: YoDownload is not initialized}。</p>
 *
 * <p>本类不收 Config：下载域的默认配置由库自己给（{@code Config.defaults}），App 没有额外偏好，
 * 也不在 provider 里攒「是否已安装」的状态——装配根只调用一次。</p>
 */
public final class YoDownloadProvider {

    private YoDownloadProvider() {
    }

    /** 用库默认配置初始化下载引擎：装配根构造期、任何下载使用之前调用。 */
    public static void install(@NonNull Context context) {
        YoDownload.init(context.getApplicationContext(), null);
    }
}
