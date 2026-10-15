package com.yovo.lib_common.log.internal.sink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.yovo.lib_common.log.model.FileLogReason;
import com.yovo.lib_common.log.model.FileNamePolicy;
import com.yovo.lib_common.log.model.LogConfig;
import com.yovo.lib_common.log.model.LogLevel;
import com.yovo.lib_common.log.model.LogRecord;
import com.yovo.lib_common.log.model.RetentionPolicy;
import com.yovo.lib_common.log.model.RollPolicy;
import com.yovo.lib_common.system.file.model.PathTarget;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class FileSinkTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writesRollsAndRetains() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File root = folder.newFolder("logs");
        LogConfig config = LogConfig.builder()
                .enableFile(true)
                .fileTarget(PathTarget.custom(root, ""))
                .fileNamePolicy(FileNamePolicy.fixed("app.log"))
                .rollPolicy(new RollPolicy(200, 2))
                .retentionPolicy(new RetentionPolicy(2, 0, 0))
                .processNameSuffix(false)
                .build();
        FileSink sink = new FileSink(context, PathTarget.custom(root, ""), config, "", false);

        sink.init();

        assertTrue(sink.status().enabled());
        for (int i = 0; i < 40; i++) {
            sink.write(record("line-" + i));
        }
        sink.flush();
        sink.close();

        File active = new File(root, "app.log");
        assertTrue(active.isFile());
        assertTrue(active.length() < 300);
        File[] matches = root.listFiles((dir, name) ->
                config.fileNamePolicy().matches(name, ""));
        assertTrue(matches.length <= 2);
    }

    @Test
    public void init_onInvalidRootDegradesToLogcatOnly() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File blocker = folder.newFile("blocker");
        LogConfig config = LogConfig.builder()
                .enableFile(true)
                .fileTarget(PathTarget.custom(blocker, ""))
                .build();
        FileSink sink = new FileSink(
                context, PathTarget.custom(blocker, ""), config, "", false);

        sink.init();

        assertFalse(sink.status().enabled());
        assertTrue(sink.status().degraded());
        assertEquals(FileLogReason.IO_ERROR, sink.status().reason());
        sink.close();
    }

    @Test
    public void fallbackStatus_reportsEnabledDegraded() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File root = folder.newFolder("fallback-logs");
        LogConfig config = LogConfig.builder()
                .enableFile(true)
                .fileTarget(PathTarget.custom(root, ""))
                .build();
        FileSink sink = new FileSink(
                context, PathTarget.custom(root, ""), config, "", true);

        sink.init();

        assertTrue(sink.status().enabled());
        assertTrue(sink.status().degraded());
        assertEquals(
                FileLogReason.PRIMARY_UNAVAILABLE_USING_FALLBACK,
                sink.status().reason());
        sink.close();
    }

    @Test
    public void retention_neverDeletesActiveFile() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File root = folder.newFolder("retention-logs");
        File active = new File(root, "app.log");
        Files.write(active.toPath(), "old".getBytes(StandardCharsets.UTF_8));
        assertTrue(active.setLastModified(
                System.currentTimeMillis() - 10L * 86_400_000L));
        LogConfig config = LogConfig.builder()
                .enableFile(true)
                .fileTarget(PathTarget.custom(root, ""))
                .fileNamePolicy(FileNamePolicy.fixed("app.log"))
                .retentionPolicy(new RetentionPolicy(0, 7, 0))
                .processNameSuffix(false)
                .build();
        FileSink sink = new FileSink(
                context, PathTarget.custom(root, ""), config, "", false);

        sink.init();
        sink.write(record("hello"));
        sink.flush();
        sink.close();

        assertTrue(active.isFile());
        String content = new String(
                Files.readAllBytes(active.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains("hello"));
    }

    @Test
    public void write_includesThrowableStacktrace() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File root = folder.newFolder("stack-logs");
        LogConfig config = LogConfig.builder()
                .enableFile(true)
                .fileTarget(PathTarget.custom(root, ""))
                .processNameSuffix(false)
                .build();
        FileSink sink = new FileSink(
                context, PathTarget.custom(root, ""), config, "", false);

        sink.init();
        sink.write(new LogRecord(
                System.currentTimeMillis(),
                LogLevel.ERROR,
                "FileSinkTest",
                "boom",
                "test",
                1L,
                new IllegalStateException("stack-detail"),
                Collections.emptyMap()));
        sink.flush();
        sink.close();

        String content = new String(
                Files.readAllBytes(new File(root, "app.log").toPath()),
                StandardCharsets.UTF_8);
        assertTrue(content.contains("boom"));
        assertTrue(content.contains("IllegalStateException"));
        assertTrue(content.contains("stack-detail"));
    }

    private static LogRecord record(String message) {
        return new LogRecord(
                System.currentTimeMillis(),
                LogLevel.INFO,
                "FileSinkTest",
                message,
                "test",
                1L,
                null,
                Collections.emptyMap());
    }
}
