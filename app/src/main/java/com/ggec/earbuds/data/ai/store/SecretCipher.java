package com.ggec.earbuds.data.ai.store;

/**
 * 对称加解密一小段文本，密文是可以直接写进文档的字符串。实现负责密钥的保管。
 */
public interface SecretCipher {

    /**
     * @throws SecretCipherException 密钥库不可用（例如系统密钥被清除）
     */
    String encrypt(String plain);

    /**
     * @throws SecretCipherException 密文损坏，或保管的密钥已经失效
     */
    String decrypt(String encoded);
}
