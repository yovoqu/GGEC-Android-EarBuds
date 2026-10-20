package com.ggec.earbuds.data.ai.store;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * 把密钥加密后存进一个文档。文档里只有密文，所以文档本身可以放在普通文件目录；
 * 解密用的密钥由 {@link SecretCipher} 的实现保管，不随文档走。
 */
public final class EncryptedSecretStore implements SecretStore {

    private static final String DOCUMENT = "secrets.json";
    private static final int VERSION = 1;

    private final AiDocumentStore documents;
    private final SecretCipher cipher;
    private final Object lock = new Object();

    public EncryptedSecretStore(AiDocumentStore documents, SecretCipher cipher) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    @Nullable
    @Override
    public String get(String alias) {
        synchronized (lock) {
            JSONObject items = items();
            String encoded = items.optString(alias, "");
            if (encoded.isEmpty()) {
                return null;
            }
            try {
                return cipher.decrypt(encoded);
            } catch (AiStorageException undecryptable) {
                return null;
            }
        }
    }

    @Override
    public void put(String alias, String secret) {
        synchronized (lock) {
            JSONObject items = items();
            try {
                items.put(alias, cipher.encrypt(secret));
            } catch (JSONException impossible) {
                throw new AiStorageException("Failed to encode secrets", impossible);
            }
            write(items);
        }
    }

    @Override
    public void remove(String alias) {
        synchronized (lock) {
            JSONObject items = items();
            if (items.has(alias)) {
                items.remove(alias);
                write(items);
            }
        }
    }

    private JSONObject items() {
        String text = documents.read(DOCUMENT);
        if (text == null || text.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject items = new JSONObject(text).optJSONObject("items");
            return items == null ? new JSONObject() : items;
        } catch (JSONException corrupt) {
            return new JSONObject();
        }
    }

    private void write(JSONObject items) {
        try {
            documents.write(DOCUMENT, new JSONObject().put("v", VERSION).put("items", items).toString());
        } catch (JSONException impossible) {
            throw new AiStorageException("Failed to encode secrets", impossible);
        }
    }
}
