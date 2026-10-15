package com.yovo.lib_common.log.internal.access;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.yovo.lib_common.log.model.FileLogReason;
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
public final class LogTargetSelectorTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void select_primaryCustomDirUsed() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File root = folder.newFolder("logs");

        LogTargetSelector.Selection selection = LogTargetSelector.select(
                context,
                LogConfig.builder()
                        .enableFile(true)
                        .fileTarget(PathTarget.custom(root, ""))
                        .build());

        assertNotNull(selection.target());
        assertFalse(selection.usingFallback());
    }

    @Test
    public void select_fallbackUsedWhenPrimaryDenied() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File root = folder.newFolder("fallback");
        File blocker = folder.newFile("blocker");

        LogTargetSelector.Selection selection = LogTargetSelector.select(
                context,
                LogConfig.builder()
                        .enableFile(true)
                        .fileTarget(PathTarget.custom(blocker, ""))
                        .fallbackFileTarget(PathTarget.custom(root, ""))
                        .build());

        assertNotNull(selection.target());
        assertEquals(PathTarget.custom(root, ""), selection.target());
        assertTrue(selection.usingFallback());
    }

    @Test
    public void select_disabledWhenEnableFileFalse() {
        Context context = RuntimeEnvironment.getApplication();

        LogTargetSelector.Selection selection = LogTargetSelector.select(
                context, LogConfig.builder().enableFile(false).build());

        assertNull(selection.target());
        assertEquals(FileLogReason.NOT_REQUESTED, selection.reason());
    }
}
