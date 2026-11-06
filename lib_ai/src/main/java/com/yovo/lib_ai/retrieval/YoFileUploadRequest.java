package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * 一次文件上传请求：文件字节 + 文件名 + 用途。
 *
 * <p>SDK 只负责把内存里的字节按 multipart 送出去，不读本地磁盘、不管理缓存。
 * 上传后可用返回的 {@link YoFileRef#id()} 在聊天、Responses 或 Batch 里引用。</p>
 */
public final class YoFileUploadRequest {

    /** 未指定时的通用二进制类型。 */
    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final byte[] data;
    private final String filename;
    @Nullable
    private final String purpose;
    private final String contentType;

    private YoFileUploadRequest(Builder builder) {
        this.data = builder.data;
        this.filename = builder.filename;
        this.purpose = builder.purpose;
        this.contentType = builder.contentType;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public byte[] data() {
        return data.clone();
    }

    /** 文件字节数。 */
    public int dataLength() {
        return data.length;
    }

    /** 供 internal 传输层直接取底层数组，避免大文件再复制一份。调用方不得修改。 */
    @NonNull
    public byte[] dataUnsafe() {
        return data;
    }

    @NonNull
    public String filename() {
        return filename;
    }

    /** 用途，例如 {@code batch}、{@code vision}；部分厂商可省略。 */
    @Nullable
    public String purpose() {
        return purpose;
    }

    @NonNull
    public String contentType() {
        return contentType;
    }

    public static final class Builder {
        private byte[] data;
        private String filename;
        @Nullable
        private String purpose;
        private String contentType = DEFAULT_CONTENT_TYPE;

        private Builder() {
        }

        @NonNull
        public Builder data(@Nullable byte[] data) {
            this.data = data == null ? null : data.clone();
            return this;
        }

        @NonNull
        public Builder filename(@Nullable String filename) {
            this.filename = filename == null ? null : filename.trim();
            return this;
        }

        @NonNull
        public Builder purpose(@Nullable String purpose) {
            this.purpose = purpose == null || purpose.isBlank() ? null : purpose.trim();
            return this;
        }

        @NonNull
        public Builder contentType(@Nullable String contentType) {
            this.contentType = contentType == null || contentType.isBlank()
                    ? DEFAULT_CONTENT_TYPE : contentType.trim();
            return this;
        }

        @NonNull
        public YoFileUploadRequest build() {
            if (data == null || data.length == 0) {
                throw new IllegalArgumentException("file data is required");
            }
            if (filename == null || filename.isBlank()) {
                throw new IllegalArgumentException("filename is required");
            }
            Objects.requireNonNull(contentType, "contentType");
            return new YoFileUploadRequest(this);
        }
    }
}
