package com.yovo.lib_common.log.internal.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.log.model.FileNamePolicy;
import com.yovo.lib_common.log.model.RollPolicy;

import org.junit.Test;

import java.io.File;
import java.util.List;

public final class RollControllerTest {

    @Test
    public void shouldRoll_whenSizeReachesLimit() {
        RollController controller = new RollController(
                new RollPolicy(100, 3), FileNamePolicy.fixed("app.log"), "");

        assertFalse(controller.shouldRoll(99));
        assertTrue(controller.shouldRoll(100));
    }

    @Test
    public void shouldNotRoll_whenBackupIndexZero() {
        RollController controller = new RollController(
                new RollPolicy(100, 0), FileNamePolicy.fixed("app.log"), "");

        assertFalse(controller.shouldRoll(10_000));
    }

    @Test
    public void shouldSwitchDay_onlyForDailyPolicy() {
        RollController fixed = new RollController(
                new RollPolicy(100, 2), FileNamePolicy.fixed("app.log"), "");
        assertFalse(fixed.shouldSwitchDay("app.log", 0L));

        RollController daily = new RollController(
                new RollPolicy(100, 2), FileNamePolicy.daily("yyyy-MM-dd"), "");
        assertTrue(daily.shouldSwitchDay("2024-07-03.log", 1_752_000_000_000L));
        assertFalse(daily.shouldSwitchDay(
                dailyNameFor(1_752_000_000_000L), 1_752_000_000_000L));
    }

    @Test
    public void backupChain_producesDotIndexedFiles() {
        RollController controller = new RollController(
                new RollPolicy(100, 3), FileNamePolicy.fixed("app.log"), "");
        File active = new File("/logs/app.log");

        List<File> chain = controller.backupChain(active);

        assertEquals(3, chain.size());
        assertEquals(new File("/logs", "app.log.1").getPath(), chain.get(0).getPath());
        assertEquals(new File("/logs", "app.log.3").getPath(), chain.get(2).getPath());
    }

    private static String dailyNameFor(long millis) {
        return FileNamePolicy.daily("yyyy-MM-dd").nameFor(millis, "");
    }
}
