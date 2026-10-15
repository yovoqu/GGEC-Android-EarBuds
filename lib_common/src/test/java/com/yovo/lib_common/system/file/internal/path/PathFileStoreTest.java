package com.yovo.lib_common.system.file.internal.path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.system.file.model.ConflictPolicy;
import com.yovo.lib_common.system.file.model.FileEntry;
import com.yovo.lib_common.system.file.model.FileFailureCode;
import com.yovo.lib_common.system.file.model.FileResult;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.io.OutputStream;

public final class PathFileStoreTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writeReadRoundTrip() throws IOException {
        File file = folder.newFile("data.txt");
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

        assertTrue(PathFileStore.writeBytes(
                file, bytes, ConflictPolicy.OVERWRITE).success());

        FileResult<byte[]> read = PathFileStore.readBytes(file, 1024);
        assertTrue(read.success());
        assertArrayEquals(bytes, read.value());
    }

    @Test
    public void write_existingFileWithFailConflictRejects() throws IOException {
        File file = folder.newFile("data.txt");

        FileResult<Void> result = PathFileStore.writeBytes(
                file, "x".getBytes(StandardCharsets.UTF_8), ConflictPolicy.FAIL);

        assertFalse(result.success());
        assertEquals(FileFailureCode.ALREADY_EXISTS, result.requireFailure().code());
    }

    @Test
    public void write_renameUniqueCreatesSibling() throws IOException {
        File file = folder.newFile("data.txt");

        FileResult<Void> result = PathFileStore.writeBytes(
                file, "x".getBytes(StandardCharsets.UTF_8), ConflictPolicy.RENAME_UNIQUE);

        assertTrue(result.success());
        assertTrue(new File(folder.getRoot(), "data(1).txt").isFile());
    }

    @Test
    public void readBytes_enforcesLimit() throws IOException {
        File file = folder.newFile("large.txt");
        java.nio.file.Files.write(file.toPath(), "0123456789".getBytes());

        FileResult<byte[]> result = PathFileStore.readBytes(file, 5);

        assertFalse(result.success());
        assertEquals(FileFailureCode.SIZE_LIMIT_EXCEEDED, result.requireFailure().code());
    }

    @Test
    public void atomicWrite_replacesContent() throws IOException {
        File file = folder.newFile("atomic.bin");
        PathFileStore.writeBytes(
                file, "old".getBytes(StandardCharsets.UTF_8), ConflictPolicy.OVERWRITE);

        FileResult<Void> result = AtomicPathWriter.writeAtomic(
                file, "new".getBytes(StandardCharsets.UTF_8));

        assertTrue(result.success());
        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8),
                PathFileStore.readBytes(file, 1024).value());
        assertFalse(new File(folder.getRoot(), "atomic.bin.new").exists());
    }

    @Test
    public void atomicWrite_failureKeepsOldContent() throws IOException {
        File file = folder.newFile("atomic.bin");
        PathFileStore.writeBytes(
                file, "old".getBytes(StandardCharsets.UTF_8), ConflictPolicy.OVERWRITE);

        FileResult<Void> result = AtomicPathWriter.writeAtomic(file, out -> {
            out.write("partial".getBytes(StandardCharsets.UTF_8));
            throw new java.io.IOException("boom");
        });

        assertFalse(result.success());
        assertArrayEquals("old".getBytes(StandardCharsets.UTF_8),
                PathFileStore.readBytes(file, 1024).value());
        assertFalse(new File(folder.getRoot(), "atomic.bin.new").exists());
    }

    @Test
    public void listRecursive_walksDirectories() throws IOException {
        File root = folder.newFolder("tree");
        File sub = new File(root, "sub");
        assertTrue(sub.mkdirs());
        PathFileStore.writeBytes(
                new File(root, "a.txt"), new byte[]{1}, ConflictPolicy.OVERWRITE);
        PathFileStore.writeBytes(
                new File(sub, "b.txt"), new byte[]{2}, ConflictPolicy.OVERWRITE);

        FileResult<List<FileEntry>> result =
                PathFileStore.listRecursive(root, null);

        assertTrue(result.success());
        assertEquals(3, result.value().size());
    }

    @Test
    public void delete_recursiveRemovesTree() throws IOException {
        File root = folder.newFolder("tree");
        File sub = new File(root, "sub");
        assertTrue(sub.mkdirs());
        assertTrue(new File(sub, "x.txt").createNewFile());

        FileResult<Void> result = PathFileStore.delete(root, true);

        assertTrue(result.success());
        assertFalse(root.exists());
    }

    @Test
    public void copyAndMove_respectConflict() throws IOException {
        File source = folder.newFile("src.bin");
        PathFileStore.writeBytes(
                source, new byte[]{1, 2, 3}, ConflictPolicy.OVERWRITE);
        File dst = new File(folder.getRoot(), "dst.bin");

        assertTrue(PathFileStore.copy(
                source, dst, ConflictPolicy.OVERWRITE, -1).success());
        assertTrue(dst.isFile());

        File moved = new File(folder.getRoot(), "moved.bin");
        assertTrue(PathFileStore.move(
                source, moved, ConflictPolicy.OVERWRITE).success());
        assertFalse(source.exists());
        assertTrue(moved.isFile());
    }

    @Test
    public void openOutputStream_requiresExplicitAppendOrTruncate() throws IOException {
        File file = folder.newFile("opts.txt");

        FileResult<OutputStream> result = PathFileStore.openOutputStream(
                file,
                EnumSet.of(
                        com.yovo.lib_common.system.file.model.OpenOptions.WRITE,
                        com.yovo.lib_common.system.file.model.OpenOptions.CREATE));

        assertFalse(result.success());
        assertEquals(
                FileFailureCode.INVALID_TARGET, result.requireFailure().code());
    }

    @Test
    public void openOutputStream_appendAndTruncateAreExclusive() throws IOException {
        File file = folder.newFile("opts.txt");

        FileResult<OutputStream> result = PathFileStore.openOutputStream(
                file,
                EnumSet.of(
                        com.yovo.lib_common.system.file.model.OpenOptions.WRITE,
                        com.yovo.lib_common.system.file.model.OpenOptions.APPEND,
                        com.yovo.lib_common.system.file.model.OpenOptions.TRUNCATE));

        assertFalse(result.success());
        assertEquals(
                FileFailureCode.INVALID_TARGET, result.requireFailure().code());
    }

    @Test
    public void openOutputStream_truncateReplacesContent() throws IOException {
        File file = folder.newFile("truncate.txt");
        PathFileStore.writeBytes(
                file, "old".getBytes(StandardCharsets.UTF_8), ConflictPolicy.OVERWRITE);

        FileResult<OutputStream> result = PathFileStore.openOutputStream(
                file,
                EnumSet.of(
                        com.yovo.lib_common.system.file.model.OpenOptions.WRITE,
                        com.yovo.lib_common.system.file.model.OpenOptions.TRUNCATE));
        assertTrue(result.success());
        try (OutputStream out = result.value()) {
            out.write("new".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals("new", new String(
                PathFileStore.readBytes(file, 1024).value(), StandardCharsets.UTF_8));
    }

    @Test
    public void openOutputStream_appendKeepsContent() throws IOException {
        File file = folder.newFile("append.txt");
        PathFileStore.writeBytes(
                file, "a".getBytes(StandardCharsets.UTF_8), ConflictPolicy.OVERWRITE);

        FileResult<OutputStream> result = PathFileStore.openOutputStream(
                file,
                EnumSet.of(
                        com.yovo.lib_common.system.file.model.OpenOptions.WRITE,
                        com.yovo.lib_common.system.file.model.OpenOptions.APPEND));
        assertTrue(result.success());
        try (OutputStream out = result.value()) {
            out.write("b".getBytes(StandardCharsets.UTF_8));
        }

        assertEquals("ab", new String(
                PathFileStore.readBytes(file, 1024).value(), StandardCharsets.UTF_8));
    }
}
