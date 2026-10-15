package com.yovo.biz_appupdate.internal.infrastructure.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_appupdate.internal.model.ApkNamingSpec;
import com.yovo.biz_appupdate.internal.model.LocalApk;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

public final class LocalApkScannerTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    private final ApkNamingSpec spec = new ApkNamingSpec("Auto_Tools");

    @Test
    public void strictNaming_onlyManagedApk() {
        assertTrue(spec.matches("Auto_Tools_1.5.0_150.apk"));
        assertTrue(spec.matches("auto_tools_1.5.0_150.APK"));
        assertFalse(spec.matches("Auto_Tools_1.5.0.apk"));
        assertFalse(spec.matches("other_1.5.0_150.apk"));
        assertFalse(spec.matches("Auto_Tools_1.5.0_150.txt"));
    }

    @Test
    public void list_returnsOnlyStrictApks() throws IOException {
        File dir = tempFolder.newFolder("apks");
        File older = new File(dir, "Auto_Tools_1.4.0_140.apk");
        File newer = new File(dir, "Auto_Tools_1.5.0_150.apk");
        assertTrue(older.createNewFile());
        assertTrue(newer.createNewFile());
        assertTrue(new File(dir, "Auto_Tools_1.5.0.apk").createNewFile());
        assertTrue(new File(dir, "notes.txt").createNewFile());

        List<LocalApk> result = LocalApkScanner.list(dir, spec);

        assertEquals(2, result.size());
        assertEquals("1.4.0", result.get(0).versionName());
        assertEquals(140, result.get(0).versionCode());
        assertEquals("1.5.0", result.get(1).versionName());
        assertEquals(150, result.get(1).versionCode());
    }

    @Test
    public void list_emptyOrMissingDir_returnsEmpty() throws IOException {
        assertTrue(LocalApkScanner.list(tempFolder.newFolder("empty"), spec).isEmpty());
        assertTrue(LocalApkScanner.list(new File(tempFolder.getRoot(), "missing"), spec).isEmpty());
    }
}
