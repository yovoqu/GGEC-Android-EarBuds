package com.yovo.lib_common.system.file.internal.saf.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class PrefsSafTreeStoreTest {

    @Test
    public void saveThenLoad_roundTrip() {
        PrefsSafTreeStore store = new PrefsSafTreeStore(RuntimeEnvironment.getApplication());
        Uri uri = Uri.parse("content://tree/root");

        store.save(uri);

        assertEquals(uri, store.load());
        assertTrue(store.has());
        assertFalse(store.isDismissed());
    }

    @Test
    public void markDismissed_thenSave_clearsDismissed() {
        PrefsSafTreeStore store = new PrefsSafTreeStore(RuntimeEnvironment.getApplication());

        store.markDismissed();
        assertTrue(store.isDismissed());

        store.save(Uri.parse("content://tree/root"));
        assertFalse(store.isDismissed());
    }

    @Test
    public void clearDismissed_resetsState() {
        PrefsSafTreeStore store = new PrefsSafTreeStore(RuntimeEnvironment.getApplication());

        store.markDismissed();
        store.clearDismissed();

        assertFalse(store.isDismissed());
        assertNull(store.load());
        assertFalse(store.has());
    }
}
