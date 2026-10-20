package com.ggec.earbuds.data.ai.store;

/**
 * 对称加解密一小段文本，密文是可以直接写进文档的字符串。实现负责密钥的保管。
 */
public interface SecretCipher {

    String encrypt(String plain);

    /**
     * 密文损坏、或保管密钥已经失效时抛 {@link AiStorageException}。
     */
    String decrypt(String encoded);
}
