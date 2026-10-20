package com.ggec.earbuds.platform.ai;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.ggec.earbuds.data.ai.store.AiStorageException;
import com.ggec.earbuds.data.ai.store.SecretCipher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * 用 Android Keystore 里的 AES-256-GCM 密钥加解密。密钥材料留在系统的密钥库里，应用拿不到明文，
 * 所以密文即使随文件被拷走也解不开。输出格式是 Base64(随机 IV ‖ 密文 ‖ 认证标签)。
 */
public final class KeystoreSecretCipher implements SecretCipher {

    private static final String PROVIDER = "AndroidKeyStore";
    private static final String ALIAS = "ggec.ai.secrets.v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    @Override
    public String encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(sealed, 0, packed, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (GeneralSecurityException failure) {
            throw new AiStorageException("Failed to encrypt a secret", failure);
        }
    }

    @Override
    public String decrypt(String encoded) {
        try {
            byte[] packed = Base64.getDecoder().decode(encoded);
            if (packed.length <= IV_BYTES) {
                throw new AiStorageException("Secret is truncated");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(packed, 0, IV_BYTES)));
            return new String(cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new AiStorageException("Failed to decrypt a secret", failure);
        }
    }

    private synchronized SecretKey key() throws GeneralSecurityException {
        try {
            KeyStore store = KeyStore.getInstance(PROVIDER);
            store.load(null);
            Key existing = store.getKey(ALIAS, null);
            if (existing instanceof SecretKey secret) {
                return secret;
            }
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
            generator.init(new KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return generator.generateKey();
        } catch (IOException failure) {
            throw new KeyStoreException("Cannot open the Android Keystore", failure);
        }
    }
}
