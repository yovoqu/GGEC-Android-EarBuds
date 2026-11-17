package com.yovo.lib_common.download;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DownloadCoreLifecycleTest {

    @Test
    public void invalidRequest_failsSynchronously() {
        Context context = RuntimeEnvironment.getApplication();
        YoDownload.init(context, YoDownload.Config.defaults(context));

        YoDownload.Task task = YoDownload.enqueue(
                YoDownload.Request.builder("not-a-url").build(), null);

        assertNotNull(task);
        assertEquals(YoDownload.State.FAILED, task.state());
        assertEquals(YoDownload.Error.INVALID_REQUEST, task.error());
    }

    @Test
    public void shutdownThenReinstall_works() {
        Context context = RuntimeEnvironment.getApplication();
        YoDownload.init(context, YoDownload.Config.defaults(context));
        YoDownload.shutdown();

        try {
            YoDownload.queryAll();
            fail("关停后仍可访问下载引擎");
        } catch (IllegalStateException expected) {
            assertEquals("YoDownload is not initialized. Call YoDownload.init() first.",
                    expected.getMessage());
        }

        YoDownload.init(context, YoDownload.Config.defaults(context));
        assertNotNull(YoDownload.queryAll());
        YoDownload.shutdown();
    }
}
