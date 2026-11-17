package com.ggec.earbuds.data.ai.store;

/**
 * 密文加解密失败：密钥库不可用，或密文本身已经坏了。
 *
 * <p>它只描述"这段密文解不开"，不描述文件读写：文件层的失败是
 * {@code DocumentReadException} / {@code DocumentWriteException}。</p>
 */
public final class SecretCipherException extends RuntimeException {

    public SecretCipherException(String message) {
        super(message);
    }

    public SecretCipherException(String message, Throwable cause) {
        super(message, cause);
    }
}
