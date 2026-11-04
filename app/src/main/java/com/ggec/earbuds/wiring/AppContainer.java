package com.ggec.earbuds.wiring;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** 进程装配根：只创建应用级图，不让各页面自行 new 一套依赖。 */
public final class AppContainer {

    private final AiGraph ai;
    private final ShellGraph shell;
    private final DeviceGraph device;
    private final AppExecutors executors;

    public AppContainer(@NonNull Context context) {
        executors = new AppExecutors();
        ai = new AiGraph(context.getApplicationContext(), executors);
        shell = new ShellGraph();
        device = new DeviceGraph(context.getApplicationContext());
    }

    @NonNull
    public AppExecutors executors() {
        return executors;
    }

    @NonNull
    public AiGraph ai() {
        return ai;
    }

    @NonNull
    public ShellGraph shell() {
        return shell;
    }

    @NonNull
    public DeviceGraph device() {
        return device;
    }

    @Nullable
    public static AppContainer from(@NonNull Context context) {
        Context application = context.getApplicationContext();
        if (application instanceof ContainerOwner owner) {
            return owner.container();
        }
        return null;
    }

    public interface ContainerOwner {
        @NonNull
        AppContainer container();
    }
}
