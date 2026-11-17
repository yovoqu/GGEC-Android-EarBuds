package com.yovo.lib_common.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNotificationManager;

/**
 * 下载通知链路：渠道绑定 → 首个进度 → 进度更新 → 取消。
 *
 * <p>只断言可观测事实（通知是否真的挂到系统通知管理器、内容与进度是否更新、
 * 取消后是否摘除），不靠「不抛异常」当通过条件。使用 {@link Shadow#extract} 而非
 * {@code Shadows.shadowOf}，避免 Robolectric 的静态重载引用到 Android SDK 37 已移除的
 * {@code FingerprintManager} 而在编译期解析失败。</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class DownloadNotificationTest {

    private static final String TASK_ID = "task-1";
    private static final String FILE_NAME = "Auto_Tools_1.5.0.apk";

    @Test
    public void attachShowProgressCancel_updatesSystemNotifications() {
        Context context = RuntimeEnvironment.getApplication();
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ShadowNotificationManager shadow = Shadow.extract(manager);

        YoDownload.notification().attach(context);
        assertEquals("attach must only create the channel, not post a notification",
                0, shadow.getAllNotifications().size());

        YoDownload.notification().showStarted(context, TASK_ID, FILE_NAME);
        assertEquals("starting a download must post exactly one notification",
                1, shadow.getAllNotifications().size());
        Notification started = shadow.getAllNotifications().iterator().next();
        assertNotNull(started);
        assertEquals("下载 " + FILE_NAME,
                String.valueOf(started.extras.getCharSequence(Notification.EXTRA_TITLE)));
        assertTrue(String.valueOf(started.extras.getCharSequence(Notification.EXTRA_TEXT))
                .contains("0%"));

        YoDownload.notification().showProgress(context, TASK_ID, FILE_NAME, 50);
        assertEquals("one task must keep exactly one notification (id reused)",
                1, shadow.getAllNotifications().size());
        Notification progress = shadow.getAllNotifications().iterator().next();
        assertTrue("progress must be written into the notification text",
                String.valueOf(progress.extras.getCharSequence(Notification.EXTRA_TEXT))
                        .contains("50%"));

        YoDownload.notification().cancel(context, TASK_ID);
        assertEquals("after cancel the notification must be removed",
                0, shadow.getAllNotifications().size());
    }
}
