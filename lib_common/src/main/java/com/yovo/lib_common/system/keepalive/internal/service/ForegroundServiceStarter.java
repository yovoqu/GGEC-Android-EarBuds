package com.yovo.lib_common.system.keepalive.internal.service;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * 前台服务启动：启动入口的版本差异只经 {@link ForegroundServiceRules} 判定。
 */
public final class ForegroundServiceStarter {

    private ForegroundServiceStarter() {
    }

    public static void start(@NonNull Context context, @NonNull Intent intent) {
        if (ForegroundServiceRules.startForegroundServiceRequired()) {
            Api26.startForegroundService(context, intent);
            return;
        }
        context.startService(intent);
    }

    /** API 26+：startForegroundService 入口。 */
    @RequiresApi(Build.VERSION_CODES.O)
    private static final class Api26 {

        private Api26() {
        }

        static void startForegroundService(@NonNull Context context, @NonNull Intent intent) {
            context.startForegroundService(intent);
        }
    }
}
