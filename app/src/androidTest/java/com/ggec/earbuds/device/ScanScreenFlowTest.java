package com.ggec.earbuds.device;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertNotNull;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.device.scan.ScanActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * 扫描链路真机交互：进入扫描页 → 停止/开始按钮可点且状态翻转。
 *
 * <p>用 Espresso 而不是 adb 触摸注入：本机同时运行其它应用，系统级输入会被抢占，
 * 结论不可复现；Espresso 直接操作本应用视图树。筛选项的目录来源由
 * {@code HsCapabilityCatalogAdapterRealCatalogTest} 与真机截图覆盖（本机 YoSelect 的
 * 几何点击在 Espresso 下不可靠，不再用它做断言）。</p>
 */
@RunWith(AndroidJUnit4.class)
public final class ScanScreenFlowTest {

    @Test
    public void scanPageTogglesBetweenStopAndStart() throws Exception {
        try (ActivityScenario<ScanActivity> scenario = ActivityScenario.launch(ScanActivity.class)) {
            scenario.onActivity(activity -> assertNotNull("扫描页未创建", activity));
            onView(withText(R.string.device_scan_stop)).check(matches(isDisplayed()));
            onView(withText(R.string.device_scan_stop)).perform(click());
            onView(withText(R.string.device_scan_start)).check(matches(isDisplayed()));
            onView(withText(R.string.device_scan_start)).perform(click());
            onView(withText(R.string.device_scan_stop)).check(matches(isDisplayed()));
        }
    }
}
