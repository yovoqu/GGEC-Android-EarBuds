package com.ggec.earbuds.device;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.ggec.earbuds.domain.device.model.DeviceCapabilityGroup;
import com.ggec.earbuds.ui.device.settings.DeviceSettingsActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 真机渲染冒烟：按能力分组逐个打开设备设置页，确认页面进入 RESUMED 并落地截图。
 *
 * <p>用 {@link ActivityScenario} 驱动而不是 adb 触摸注入：本机系统会抢占前台，
 * 触摸注入不可复现；ActivityScenario 直接走真实 Activity 生命周期，结论确定。</p>
 */
@RunWith(AndroidJUnit4.class)
public final class DeviceSettingsScreenTest {

    private static final Map<DeviceCapabilityGroup, String> GROUPS = new LinkedHashMap<>();

    static {
        GROUPS.put(DeviceCapabilityGroup.MUSIC, "music");
        GROUPS.put(DeviceCapabilityGroup.CONTROL, "control");
        GROUPS.put(DeviceCapabilityGroup.FUNCTION, "function");
        GROUPS.put(DeviceCapabilityGroup.DUAL_CONNECTION, "dual");
        GROUPS.put(DeviceCapabilityGroup.RECORD, "record");
        GROUPS.put(DeviceCapabilityGroup.ABOUT, "about");
    }

    @Test
    public void everyCapabilityGroupOpensItsSettingsPage() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        for (Map.Entry<DeviceCapabilityGroup, String> entry : GROUPS.entrySet()) {
            Intent intent = DeviceSettingsActivity.intent(context, entry.getKey());
            try (ActivityScenario<DeviceSettingsActivity> scenario =
                         ActivityScenario.launch(intent)) {
                Lifecycle.State state = scenario.getState();
                assertTrue("设置页未进入 RESUMED: " + entry.getKey() + " 实际=" + state,
                        state == Lifecycle.State.RESUMED);
                scenario.onActivity(activity -> assertTrue(
                        "设置页窗口不可见: " + entry.getKey(),
                        activity.getWindow().getDecorView().isShown()));
                capture(entry.getValue());
            }
        }
    }

    private void capture(String name) throws Exception {
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .takeScreenshot();
        assertNotNull("截图失败: " + name, bitmap);
        File dir = new File(
                InstrumentationRegistry.getInstrumentation().getTargetContext()
                        .getExternalFilesDir(null), "device-screens");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File out = new File(dir, name + ".png");
        try (OutputStream stream = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        }
        bitmap.recycle();
    }
}
