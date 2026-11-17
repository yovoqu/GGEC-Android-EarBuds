package com.yovo.lib_common.system.refresh_rate.internal.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.yovo.lib_common.system.refresh_rate.model.RefreshRatePolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * 刷新率生命周期装配是实例状态（R-8）：两个核心实例互不影响，
 * 卸载只对持有该 Application 的实例生效，且重复卸载幂等。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public final class RefreshRateCoreInstallTest {

    @Test
    public void installAndUninstall_areOwnedByInstance() {
        Application app = RuntimeEnvironment.getApplication();
        RefreshRateCore first = new RefreshRateCore();
        RefreshRateCore second = new RefreshRateCore();

        assertFalse(first.isInstalled());
        first.install(app, RefreshRatePolicy.highest());
        assertTrue(first.isInstalled());
        assertFalse("装配状态不得跨实例共享", second.isInstalled());

        second.uninstall(app);
        assertTrue("未持有该 Application 的实例不得改动他人装配", first.isInstalled());

        first.install(app, RefreshRatePolicy.target(90f));
        assertTrue("同一实例重复安装保持已安装", first.isInstalled());

        first.uninstall(app);
        assertFalse(first.isInstalled());

        first.uninstall(app);
        assertFalse("重复卸载幂等", first.isInstalled());
    }
}
