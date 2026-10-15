package com.yovo.lib_common.log.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FileNamePolicyTest {

    @Test
    public void fixed_nameWithProcessSuffixInsertsBeforeExtension() {
        FileNamePolicy policy = FileNamePolicy.fixed("app.log");

        assertEquals("app-abc.log", policy.nameFor(0L, "abc"));
        assertEquals("app.log", policy.nameFor(0L, ""));
    }

    @Test
    public void fixed_matchesActiveAndBackupsOnly() {
        FileNamePolicy policy = FileNamePolicy.fixed("app.log");

        assertTrue(policy.matches("app.log", ""));
        assertTrue(policy.matches("app.log.1", ""));
        assertTrue(policy.matches("app.log.9", ""));
        assertTrue(policy.matches("app-abc.log.2", "abc"));
        assertFalse(policy.matches("other.log", ""));
        assertFalse(policy.matches("app.bak", ""));
        assertFalse(policy.matches("app.log.bak", ""));
        assertFalse(policy.matches("app.log.txt", ""));
    }

    @Test
    public void daily_nameUsesDateFormatAndSuffix() {
        FileNamePolicy policy = FileNamePolicy.daily("yyyy-MM-dd");
        long millis = 1_720_000_000_000L;

        assertEquals("2024-07-03.log", policy.nameFor(millis, ""));
        assertTrue(policy.matches("2024-07-03.log", ""));
        assertTrue(policy.matches("2024-07-03-abc.log.3", "abc"));
        assertFalse(policy.matches("2024-7-03.log", ""));
        assertFalse(policy.matches("other.log", ""));
        assertFalse(policy.matches("2024-07-03-.log", ""));
    }

    @Test
    public void daily_customDatePattern_usesPatternRegex() {
        FileNamePolicy policy = FileNamePolicy.daily("yyyy/MM/dd");

        assertTrue(policy.matches("2024/07/03.log", ""));
        assertTrue(policy.matches("2024/07/03-abc.log.2", "abc"));
        assertFalse(policy.matches("2024-07-03.log", ""));
    }
}
