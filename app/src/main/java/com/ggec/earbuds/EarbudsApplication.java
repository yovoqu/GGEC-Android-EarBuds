package com.ggec.earbuds;

import android.app.Application;

import androidx.annotation.NonNull;

import com.ggec.earbuds.wiring.AppContainer;

/**
 * 进程入口：只创建装配根，不含任何能力实现。
 *
 * <p>日志初始化、刷新率安装、设备/AI/壳子图都由 {@link AppContainer} 在装配点完成，
 * 进程入口不认识任何 provider。</p>
 */
public final class EarbudsApplication extends Application implements AppContainer.ContainerOwner {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
    }

    @NonNull
    @Override
    public AppContainer container() {
        return container;
    }

    @Override
    public void onTerminate() {
        // best-effort：Android 生产环境不保证对 Application.onTerminate 回调（仅测试/模拟器语义），
        // 因此这里只尝试释放一次底层资源；进程被杀时由系统回收，不视为缺陷。
        if (container != null) {
            container.release();
        }
        super.onTerminate();
    }
}
