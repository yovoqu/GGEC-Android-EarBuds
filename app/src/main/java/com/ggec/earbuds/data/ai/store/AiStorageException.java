package com.ggec.earbuds.data.ai.store;

/**
 * 存储层的读写失败。仓库让它向上传播，调用方决定是提示用户还是忽略。
 */
public final class AiStorageException extends RuntimeException {

    public AiStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiStorageException(String message) {
        super(message);
    }
}
