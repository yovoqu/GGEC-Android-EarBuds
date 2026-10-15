package com.yovo.biz_appupdate.internal.model;

import com.yovo.biz_appupdate.internal.model.DownloadDirSpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public final class DownloadDirSpecTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void appPrivate_keepsTypeAndRelativeName() {
        DownloadDirSpec spec = DownloadDirSpec.appPrivate("AppUpdate");

        assertEquals(DownloadDirSpec.Type.APP_PRIVATE, spec.type());
        assertEquals("AppUpdate", spec.relativeName());
    }

    @Test
    public void appPrivate_rejectsUnsafeNames() {
        assertThrows(IllegalArgumentException.class, () -> DownloadDirSpec.appPrivate("../x"));
        assertThrows(IllegalArgumentException.class, () -> DownloadDirSpec.appPrivate("a/b"));
        assertThrows(IllegalArgumentException.class, () -> DownloadDirSpec.appPrivate(""));
    }

    @Test
    public void absolute_keepsAbsoluteDir() throws Exception {
        File dir = new File(tempFolder.getRoot(), "Download/Auto_Tools").getAbsoluteFile();
        DownloadDirSpec spec = DownloadDirSpec.absolute(dir);

        assertEquals(dir, spec.absoluteDir());
        assertEquals(DownloadDirSpec.Type.ABSOLUTE, spec.type());
    }

    @Test
    public void absolute_rejectsRelativeDir() {
        assertThrows(IllegalArgumentException.class,
                () -> DownloadDirSpec.absolute(new File("relative/Download")));
    }

}
