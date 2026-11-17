package com.ggec.earbuds.data.ai.store;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.data.store.DocumentFormatException;
import com.ggec.earbuds.data.store.FileDocumentStore.DocumentWriteException;
import com.ggec.earbuds.testing.ai.MemoryDocumentStore;

import org.junit.Test;

public final class EncryptedSecretStoreTest {

    private static final String DOCUMENT = EncryptedSecretStore.DOCUMENT;

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
                throw new SecretCipherException("cannot decrypt");
            }
            return new StringBuilder(encoded.substring(4)).reverse().toString();
        }
    }

    private static EncryptedSecretStore store(MemoryDocumentStore documents, SecretCipher cipher) {
        return new EncryptedSecretStore(documents, cipher);
    }

    @Test
    public void secretsRoundTripAndAreNotStoredInTheClear() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        EncryptedSecretStore store = store(documents, new ReverseCipher());

        store.put("apikey.a", "sk-plain-secret");
        store.put("apikey.b", "other");

        assertEquals("sk-plain-secret", store.get("apikey.a"));
        assertEquals("other", store.get("apikey.b"));
        assertFalse(documents.documents.get(DOCUMENT).contains("sk-plain-secret"));
        assertEquals("a second store over the same document sees them",
                "other", store(documents, new ReverseCipher()).get("apikey.b"));
    }

    @Test
    public void undecryptableSecretReadsAsMissingNotAsError() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        ReverseCipher cipher = new ReverseCipher();
        EncryptedSecretStore store = store(documents, cipher);
        store.put("apikey.a", "secret");

        cipher.broken = true;
        assertNull(store.get("apikey.a"));
        assertNull(store.get("never-stored"));
        cipher.broken = false;
        store.put("apikey.c", "fresh");
        assertEquals("fresh", store.get("apikey.c"));
    }

    @Test
    public void corruptSecretsDocumentIsReportedAndNeverOverwritten() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        EncryptedSecretStore store = store(documents, new ReverseCipher());
        documents.documents.put(DOCUMENT, "corrupt");

        try {
            store.get("apikey.a");
            fail("a corrupt secrets document must not read as empty");
        } catch (DocumentFormatException expected) {
            assertEquals("corrupt", documents.documents.get(DOCUMENT));
        }
        try {
            store.put("apikey.a", "new-secret");
            fail("writing over a corrupt document would drop the other keys");
        } catch (DocumentFormatException expected) {
            assertEquals("corrupt", documents.documents.get(DOCUMENT));
        }
    }

    @Test
    public void unsupportedVersionIsReported() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        documents.documents.put(DOCUMENT, "{\"v\":2,\"items\":{}}");

        try {
            store(documents, new ReverseCipher()).get("apikey.a");
            fail("an unknown secret document version must not read as empty");
        } catch (DocumentFormatException expected) {
            assertEquals("v", expected.field());
        }
    }

    @Test
    public void removingASecretLeavesTheOthers() {
        EncryptedSecretStore store = store(new MemoryDocumentStore(), new ReverseCipher());
        store.put("a", "1");
        store.put("b", "2");

        store.remove("a");
        store.remove("never-stored");

        assertNull(store.get("a"));
        assertEquals("2", store.get("b"));
    }

    @Test
    public void aWriteFailureSurfacesAsDocumentWriteException() {
        MemoryDocumentStore documents = new MemoryDocumentStore();
        EncryptedSecretStore store = store(documents, new ReverseCipher());
        store.put("a", "1");
        documents.failWrites = true;

        try {
            store.put("b", "2");
            fail("a failing document write must not be swallowed");
        } catch (DocumentWriteException expected) {
            assertEquals(DOCUMENT, expected.name());
            assertTrue(store.get("a").equals("1"));
        }
    }
}
