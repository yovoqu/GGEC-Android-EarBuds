package com.yovo.lib_common.log.internal.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.log.model.RetentionPolicy;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RetentionControllerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void maxDays_deletesOldFiles() throws Exception {
        List<File> files = new ArrayList<>();
        long now = System.currentTimeMillis();
        files.add(file("old.log", now - 10L * 86_400_000L));
        files.add(file("new.log", now - 1_000L));

        List<File> toDelete = RetentionController.selectForDeletion(
                files, new RetentionPolicy(0, 7, 0), now);

        assertEquals(1, toDelete.size());
        assertEquals("old.log", toDelete.get(0).getName());
    }

    @Test
    public void maxFiles_keepsNewest() throws Exception {
        List<File> files = Arrays.asList(
                file("a.log", 100L),
                file("b.log", 200L),
                file("c.log", 300L));

        List<File> toDelete = RetentionController.selectForDeletion(
                files, new RetentionPolicy(2, 0, 0), 1000L);

        assertEquals(1, toDelete.size());
        assertEquals("a.log", toDelete.get(0).getName());
    }

    @Test
    public void maxTotalBytes_removesOldestUntilUnderLimit() throws Exception {
        List<File> files = Arrays.asList(
                file("a.log", 100L, 100L),
                file("b.log", 200L, 100L),
                file("c.log", 300L, 100L));

        List<File> toDelete = RetentionController.selectForDeletion(
                files, new RetentionPolicy(0, 0, 150L), 1000L);

        assertTrue(toDelete.contains(files.get(0)));
        assertTrue(toDelete.contains(files.get(1)));
        assertEquals(2, toDelete.size());
    }

    private File file(String name, long lastModified) throws Exception {
        File file = folder.newFile(name);
        assertTrue(file.setLastModified(lastModified));
        return file;
    }

    private File file(String name, long lastModified, long size) throws Exception {
        File file = folder.newFile(name);
        Files.write(file.toPath(), new byte[(int) size]);
        assertTrue(file.setLastModified(lastModified));
        return file;
    }
}
