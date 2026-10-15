package com.yovo.biz_appupdate.internal.infrastructure.local;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class PromptStoreTest {

    private PromptStore store;

    @Before
    public void setUp() {
        store = new PromptStore(RuntimeEnvironment.getApplication());
    }

    @Test
    public void shouldPrompt_firstTimeForVersion() {
        assertTrue(store.shouldPrompt("1.5.0"));
    }

    @Test
    public void markPrompted_suppressesSameVersionOnly() {
        store.markPrompted("1.5.0");

        assertFalse(store.shouldPrompt("1.5.0"));
        assertTrue(store.shouldPrompt("1.6.0"));
    }

    @Test
    public void ignoreVersion_tracksIgnoredVersion() {
        store.ignore("1.5.0");

        assertTrue(store.isIgnored("1.5.0"));
        assertFalse(store.isIgnored("1.6.0"));
    }
}
