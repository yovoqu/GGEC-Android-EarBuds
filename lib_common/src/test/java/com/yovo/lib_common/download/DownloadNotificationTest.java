package com.yovo.lib_common.download;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class DownloadNotificationTest {

    @Test
    public void attachShowProgressCancel_noCrash() {
        Context context = RuntimeEnvironment.getApplication();

        YoDownload.notification().attach(context);
        YoDownload.notification().showStarted(context, "task-1", "Auto_Tools_1.5.0.apk");
        YoDownload.notification().showProgress(context, "task-1", "Auto_Tools_1.5.0.apk", 50);
        YoDownload.notification().cancel(context, "task-1");
    }
}
