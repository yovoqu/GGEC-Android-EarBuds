package com.yovo.lib_common.log.internal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.yovo.lib_common.log.model.FileLogReason;
import com.yovo.lib_common.log.model.InitResult;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.system.file.model.PathTarget;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class LogCoreLifecycleTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** 每个用例持有独立核心实例：日志管线状态属于实例，不再跨用例共享。 */
    private final LogCore core = new LogCore();

    @Test
    public void initShutdownReinit_statusTransitionsCorrectly() throws Exception {
        Application app = RuntimeEnvironment.getApplication();
        File root = folder.newFolder("logs");
        LogConfig config = LogConfig.builder()
                .enableFile(true)
                .fileTarget(PathTarget.custom(root, ""))
                .processNameSuffix(false)
                .flushOnBackground(false)
                .build();

        InitResult first = core.init(app, config);

        assertTrue(first.fileEnabled());
        assertTrue(core.isInitialized());
        assertSame(first, core.lastResult());
        assertTrue(core.fileStatus().enabled());

        core.shutdown();

        assertFalse(core.isInitialized());
        assertFalse(core.fileStatus().enabled());
        assertEquals(FileLogReason.SHUTDOWN, core.fileStatus().reason());

        InitResult second = core.init(app, config);

        assertTrue(second.fileEnabled());
        assertTrue(core.fileStatus().enabled());

        core.shutdown();
    }
}
