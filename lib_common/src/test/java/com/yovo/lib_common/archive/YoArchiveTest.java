package com.yovo.lib_common.archive;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_common.archive.model.ArchiveLimits;
import com.yovo.lib_common.archive.model.ArchivePackResult;
import com.yovo.lib_common.archive.model.ArchiveUnpackResult;
import com.yovo.lib_common.archive.model.UnpackLayout;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 */
public class YoArchiveTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void packAndUnpackRoundTrip() throws Exception {
        File a = writeFile("a.bin", "aaa");
        File b = writeFile("b.hex", "bbb");
        File zip = folder.newFile("out.zip");

        ArchivePackResult packed = YoArchive.pack()
                .sources(Arrays.asList(a, b))
                .destination(zip)
                .execute();
        assertTrue(packed.isSuccess());
        assertEquals(2, packed.entryCount());

        File dest = folder.newFolder("out");
        ArchiveUnpackResult unpacked = YoArchive.unpack()
                .archive(zip)
                .destination(dest)
                .layout(UnpackLayout.FLATTEN)
                .execute();
        assertTrue(unpacked.isSuccess());
        assertEquals(2, unpacked.extractedFiles().size());
    }

    @Test
    public void unpackFilterKeepsOnlyFirmwareNames() throws Exception {
        File zip = folder.newFile("mixed.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            put(zos, "fw.bin", "bin");
            put(zos, "readme.txt", "txt");
            put(zos, "nested/app.ota", "ota");
        }

        File dest = folder.newFolder("fw");
        ArchiveUnpackResult result = YoArchive.unpack()
                .archive(zip)
                .destination(dest)
                .layout(UnpackLayout.FLATTEN)
                .filter(entry -> {
                    String name = entry.name().toLowerCase();
                    int slash = name.lastIndexOf('/');
                    String base = slash >= 0 ? name.substring(slash + 1) : name;
                    return base.endsWith(".bin") || base.endsWith(".hex") || base.endsWith(".ota");
                })
                .execute();

        assertEquals(2, result.extractedFiles().size());
        assertTrue(result.skippedByFilter() >= 1);
        assertTrue(new File(dest, "fw.bin").exists() || existsEnding(dest, "fw.bin"));
        assertTrue(existsEnding(dest, "app.ota"));
        assertFalse(existsEnding(dest, "readme.txt"));
    }

    @Test
    public void unpackRejectsZipSlip() throws Exception {
        File zip = folder.newFile("slip.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            put(zos, "../evil.bin", "evil");
            put(zos, "safe.bin", "safe");
        }

        File dest = folder.newFolder("safe_dir");
        ArchiveUnpackResult result = YoArchive.unpack()
                .archive(zip)
                .destination(dest)
                .layout(UnpackLayout.PRESERVE_SAFE)
                .execute();

        assertTrue(result.rejectedUnsafe() >= 1);
        assertTrue(existsEnding(dest, "safe.bin"));
        File parentEvil = new File(dest.getParentFile(), "evil.bin");
        assertFalse(parentEvil.exists());
    }

    @Test
    public void unpackFlattenNeutralizesTraversalName() throws Exception {
        File zip = folder.newFile("flat_slip.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            put(zos, "../../outside.bin", "x");
        }
        File dest = folder.newFolder("flat");
        ArchiveUnpackResult result = YoArchive.unpack()
                .archive(zip)
                .destination(dest)
                .layout(UnpackLayout.FLATTEN)
                .execute();
        // basename outside.bin is allowed under dest; must not escape
        assertEquals(1, result.extractedFiles().size());
        assertTrue(new File(dest, "outside.bin").exists());
        assertFalse(new File(folder.getRoot(), "outside.bin").exists());
    }

    @Test
    public void unpackBombGuardStopsOversizedEntry() throws Exception {
        File zip = folder.newFile("bomb.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
            zos.putNextEntry(new ZipEntry("big.bin"));
            byte[] chunk = new byte[1024];
            for (int i = 0; i < 20; i++) {
                zos.write(chunk);
            }
            zos.closeEntry();
        }
        File dest = folder.newFolder("bomb_out");
        ArchiveUnpackResult result = YoArchive.unpack()
                .archive(zip)
                .destination(dest)
                .limits(ArchiveLimits.of(10, 8 * 1024, 8 * 1024))
                .execute();
        assertFalse(result.isSuccess() && result.failedCount() == 0);
        assertTrue(result.failedCount() >= 1 || result.status() == ArchiveUnpackResult.Status.FAILED
                || result.status() == ArchiveUnpackResult.Status.PARTIAL);
    }

    @Test
    public void packNoSources() throws Exception {
        File zip = folder.newFile("empty.zip");
        ArchivePackResult result = YoArchive.pack()
                .sources(Collections.emptyList())
                .destination(zip)
                .execute();
        assertEquals(ArchivePackResult.Status.NO_SOURCES, result.status());
    }

    private File writeFile(String name, String content) throws Exception {
        File file = folder.newFile(name);
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    private static void put(ZipOutputStream zos, String name, String content) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static boolean existsEnding(File dir, String name) {
        File[] children = dir.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (name.equals(child.getName())) {
                return true;
            }
        }
        return false;
    }
}
