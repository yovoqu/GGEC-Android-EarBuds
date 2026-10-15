package com.yovo.lib_common.system.file.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public final class PathTargetTest {

    @Test
    public void internalFiles_factoryBuildsExpectedTarget() {
        PathTarget target = PathTarget.internalFiles("logs");

        assertEquals(FileScope.APP_INTERNAL, target.scope());
        assertEquals(RootKind.FILES, target.rootKind());
        assertEquals("logs", target.subPath());
        assertNull(target.absolutePath());
        assertTrue(target.isPath());
    }

    @Test
    public void absolute_factoryRejectsCombinedFields() {
        PathTarget target = PathTarget.absolute("D:/data/ota");

        assertEquals(FileScope.ABSOLUTE, target.scope());
        assertNull(target.rootKind());
        assertEquals("", target.subPath());
        assertEquals("D:/data/ota", target.absolutePath());
    }

    @Test
    public void absolute_withSubPath_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                PathTarget.absolute(""));
    }

    @Test
    public void customRoot_keepsAbsoluteRoot() {
        File root = new File("C:/tmp/root");
        PathTarget target = PathTarget.custom(root, "sub/dir");

        assertEquals(RootKind.CUSTOM_SUBDIR, target.rootKind());
        assertEquals("sub/dir", target.subPath());
        assertEquals(root.getAbsolutePath(), target.customRootPath());
    }

    @Test
    public void subPath_normalizesSlashes() {
        assertEquals("a/b", PathTarget.internalFiles("\\a\\b").subPath());
        assertEquals("a/b", PathTarget.internalFiles("/a/b/").subPath());
    }

    @Test
    public void fileTarget_dispatchWorks() {
        FileTarget path = FileTarget.internalFiles("logs");
        FileTarget uri = FileTarget.uri(UriTarget.document(android.net.Uri.parse("content://x/y")));

        assertTrue(path.isPath());
        assertFalse(path.isUri());
        assertTrue(uri.isUri());
        assertFalse(uri.isPath());
        assertEquals("logs", path.asPath().subPath());
        assertEquals(UriKind.DOCUMENT, uri.asUri().uriKind());
    }
}
