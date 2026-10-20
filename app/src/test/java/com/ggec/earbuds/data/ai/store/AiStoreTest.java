package com.ggec.earbuds.data.ai.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.testing.ai.MemoryDocumentStore;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public class AiStoreTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    /** 可逆但不是明文的假密码器，足够验证存储逻辑；真正的加密在设备上的 Keystore 实现里。 */
    private static final class ReverseCipher implements SecretCipher {
        boolean broken;

        @Override
        public String encrypt(String plain) {
            return "enc:" + new StringBuilder(plain).reverse();
        }

        @Override
        public String decrypt(String encoded) {
            if (broken || !encoded.startsWith("enc:")) {
                throw new AiStorageException("cannot decrypt");
            }
            return new StringBuilder(encoded.substring(4)).reverse().toString();
        }
    }

    @Test
    public void secretsRoundTripAndAreNotStoredInTheClear() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        EncryptedSecretStore store = new EncryptedSecretStore(documents, new ReverseCipher());

        store.put("apikey.a", "sk-plain-secret");
        store.put("apikey.b", "other");

        assertEquals("sk-plain-secret", store.get("apikey.a"));
        assertEquals("other", store.get("apikey.b"));
        assertFalse(documents.documents.get("secrets.json").contains("sk-plain-secret"));
        assertEquals("a second store over the same document sees them",
                "other", new EncryptedSecretStore(documents, new ReverseCipher()).get("apikey.b"));
    }

    @Test
    public void unreadableSecretsReadAsMissingNotAsErrors() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        ReverseCipher cipher = new ReverseCipher();
        EncryptedSecretStore store = new EncryptedSecretStore(documents, cipher);
        store.put("apikey.a", "secret");

        cipher.broken = true;
        assertNull(store.get("apikey.a"));
        assertNull(store.get("never-stored"));
        documents.documents.put("secrets.json", "corrupt");
        assertNull(store.get("apikey.a"));
        cipher.broken = false;
        store.put("apikey.c", "fresh");
        assertEquals("fresh", store.get("apikey.c"));
    }

    @Test
    public void removingASecretLeavesTheOthers() {
        EncryptedSecretStore store = new EncryptedSecretStore(new MemoryDocumentStore(), new ReverseCipher());
        store.put("a", "1");
        store.put("b", "2");

        store.remove("a");
        store.remove("never-stored");

        assertNull(store.get("a"));
        assertEquals("2", store.get("b"));
    }

    @Test
    public void filesAreWrittenAtomicallyReadBackAndListed() throws Exception {
        FileAiDocumentStore store = new FileAiDocumentStore(folder.getRoot());

        store.write("settings.json", "{\"a\":1}");
        store.write("conversations/b.json", "B");
        store.write("conversations/a.json", "中文 content");

        assertEquals("{\"a\":1}", store.read("settings.json"));
        assertEquals("中文 content", store.read("conversations/a.json"));
        assertEquals(List.of("a.json", "b.json"), store.list("conversations"));
        assertTrue(store.list("nothing-here").isEmpty());
        assertNull(store.read("conversations/missing.json"));
        assertEquals("only the finished documents are listed", 0,
                new File(folder.getRoot(), "conversations").list((dir, name) -> name.endsWith(".tmp")).length);

        store.write("settings.json", "replaced");
        assertEquals("replaced", store.read("settings.json"));
        store.delete("settings.json");
        store.delete("settings.json");
        assertNull(store.read("settings.json"));
    }

    @Test
    public void leftoverTempFilesFromACrashAreIgnoredByListings() throws Exception {
        File directory = folder.newFolder("conversations");
        Files.write(new File(directory, "good.json").toPath(), "x".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(directory, "half.json.tmp").toPath(), "x".getBytes(StandardCharsets.UTF_8));

        assertEquals(List.of("good.json"), new FileAiDocumentStore(folder.getRoot()).list("conversations"));
    }

    @Test
    public void unsafeNamesAreRefused() {
        FileAiDocumentStore store = new FileAiDocumentStore(folder.getRoot());
        for (String name : new String[] {"../escape.json", "a/../../b.json", "/absolute.json", "a b.json", "", "a//b.json"}) {
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
