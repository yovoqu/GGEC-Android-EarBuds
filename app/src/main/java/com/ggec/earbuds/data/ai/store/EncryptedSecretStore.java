package com.ggec.earbuds.data.ai.store;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.json.JsonValues;
import com.ggec.earbuds.data.store.DocumentFormatException;
import com.ggec.earbuds.data.store.DocumentStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;
import java.util.Optional;

/**
 * 把密钥加密后存进一份文档。文档里只有密文，所以文档本身放在 noBackup 目录即可；
 * 解密用的密钥由 {@link SecretCipher} 的实现保管，不随文档走。
 *
 * <p>IO 只经 {@link DocumentStore}：写失败抛 {@code DocumentWriteException}，
 * 由仓库归一成 {@code AiFailureKind.STORAGE}，本类不自己翻译成领域失败。
 * 文档损坏（不是 JSON、版本不符、缺 items）抛 {@link DocumentFormatException}，
 * 绝不把损坏的密钥文档当成"空"再写回去——那会把其余密钥一起抹掉。</p>
 */
public final class EncryptedSecretStore implements SecretStore {

    /** 密钥文档在装配根给出的 noBackup 目录里的固定名字。 */
    public static final String DOCUMENT = "secrets.json";

    private static final int VERSION = 1;

    private final DocumentStore documents;
    private final SecretCipher cipher;
    private final Object lock = new Object();

    public EncryptedSecretStore(DocumentStore documents, SecretCipher cipher) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.cipher = Objects.requireNonNull(cipher, "cipher");
    }

    @Nullable
    @Override
    public String get(String alias) {
        synchronized (lock) {
            JSONObject items = items();
            Optional<String> encoded = JsonValues.optionalString(items, alias);
            if (encoded.isEmpty()) {
                return null;
            }
            try {
                return cipher.decrypt(encoded.get());
            } catch (SecretCipherException undecryptable) {
                // 解不开 = 这份密钥没有了：界面上表现为"未配置"，用户重新输入即可。
                return null;
            }
        }
    }

    @Override
    public void put(String alias, String secret) {
        synchronized (lock) {
            JSONObject items = items();
            JsonValues.putOrRemove(items, alias, cipher.encrypt(secret));
            write(items);
        }
    }

    @Override
    public void remove(String alias) {
        synchronized (lock) {
            JSONObject items = items();
            if (!items.has(alias)) {
                return;
            }
            JsonValues.putOrRemove(items, alias, null);
            write(items);
        }
    }

    private JSONObject items() {
        Optional<String> text = documents.read(DOCUMENT);
        if (text.isEmpty() || text.get().isBlank()) {
            return new JSONObject();
        }
        JSONObject root;
        try {
            root = new JSONObject(text.get());
        } catch (JSONException corrupt) {
            throw new DocumentFormatException(DOCUMENT, DOCUMENT + " is not a JSON object", corrupt);
        }
        int version = JsonValues.requiredInt(root, "v");
        if (version != VERSION) {
            throw new DocumentFormatException("v",
                    DOCUMENT + " has unsupported version " + version);
        }
        JSONObject items = root.optJSONObject("items");
        if (items == null) {
            throw new DocumentFormatException("items", DOCUMENT + " has no items object");
        }
        return items;
    }

    private void write(JSONObject items) {
        JSONObject root = new JSONObject();
        JsonValues.put(root, "v", VERSION);
        JsonValues.put(root, "items", items);
        documents.write(DOCUMENT, root.toString());
    }
}
