package com.yovo.lib_common.log.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LogLevelTest {

    @Test
    public void isEnabled_usesRankOrder() {
        assertFalse(LogLevel.VERBOSE.isEnabled(LogLevel.DEBUG));
        assertTrue(LogLevel.DEBUG.isEnabled(LogLevel.DEBUG));
        assertTrue(LogLevel.ERROR.isEnabled(LogLevel.INFO));
        assertTrue(LogLevel.ASSERT.isEnabled(LogLevel.ASSERT));
    }

    @Test
    public void androidPriority_mapsToLogConstants() {
        assertTrue(LogLevel.DEBUG.androidPriority() == android.util.Log.DEBUG);
        assertTrue(LogLevel.ERROR.androidPriority() == android.util.Log.ERROR);
    }
}
