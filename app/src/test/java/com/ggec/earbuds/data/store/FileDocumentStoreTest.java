package com.ggec.earbuds.data.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** 唯一 IO 原语的契约：缺失 = empty、原子写、只列成品文件、拒绝越界名字。 */
public final class FileDocumentStoreTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void writesReadBackAndListOnlyFinishedDocuments() throws Exception {
        FileDocumentStore store = new FileDocumentStore(folder.getRoot());

        store.write("settings.json", "{\"a\":1}");
        store.write("conversations/b.json", "B");
        store.write("conversations/a.json", "中文 content");

        assertEquals("{\"a\":1}", store.read("settings.json").orElseThrow());
        assertEquals("中文 content", store.read("conversations/a.json").orElseThrow());
        assertEquals(List.of("a.json", "b.json"), store.list("conversations"));
        assertTrue(store.list("nothing-here").isEmpty());
        assertTrue(store.read("conversations/missing.json").isEmpty());
        assertEquals("only the finished documents are listed", 0,
                new File(folder.getRoot(), "conversations")
                        .list((dir, name) -> name.endsWith(".tmp")).length);

        store.write("settings.json", "replaced");
        assertEquals("replaced", store.read("settings.json").orElseThrow());
        store.delete("settings.json");
        store.delete("settings.json");
        assertTrue(store.read("settings.json").isEmpty());
    }

    @Test
    public void leftoverTempFilesFromACrashAreIgnoredByListings() throws Exception {
        File directory = folder.newFolder("conversations");
        Files.write(new File(directory, "good.json").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(directory, "half.json.tmp").toPath(), "x".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of("good.json"), new FileDocumentStore(folder.getRoot()).list("conversations"));
    }

    @Test
    public void unsafeNamesAreRefused() {
        FileDocumentStore store = new FileDocumentStore(folder.getRoot());
        for (String name : new String[] {"../escape.json", "a/../../b.json", "/absolute.json",
                "a b.json", "", "a//b.json"}) {
            try {
                store.write(name, "x");
                fail("expected the name to be refused: " + name);
            } catch (IllegalArgumentException expected) {
                // 期望的拒绝。
            }
        }
        assertEquals(0, folder.getRoot().list().length);
    }
}
