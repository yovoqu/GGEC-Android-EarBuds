package com.yovo.lib_common.system.file.internal.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public final class FileCoreLowDiskTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void ensureMinUsableSpace_whenBelowThreshold_returnsLowDisk() throws IOException {
        File file = folder.newFile("x.bin");

        FileResult<Void> result = FileCore.ensureMinUsableSpace(
                file, Long.MAX_VALUE);

        assertNotNull(result);
        assertFalse(result.success());
        assertEquals(FileFailureCode.LOW_DISK, result.requireFailure().code());
    }

    @Test
    public void ensureMinUsableSpace_whenAboveThreshold_returnsNull() throws IOException {
        File file = folder.newFile("x.bin");

        assertNull(FileCore.ensureMinUsableSpace(file, 1L));
    }

    @Test
    public void ensureMinUsableSpace_zeroThreshold_skipsCheck() throws IOException {
        File file = folder.newFile("x.bin");

        assertNull(FileCore.ensureMinUsableSpace(file, 0L));
        assertTrue(file.exists());
    }
}
