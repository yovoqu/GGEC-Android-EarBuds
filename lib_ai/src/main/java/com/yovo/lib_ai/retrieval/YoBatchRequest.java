package com.yovo.lib_ai.retrieval;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 一次批量任务提交。两种形态共存：
 *
 * <ul>
 *   <li>OpenAI / Qwen 兼容：引用已上传的 JSONL 输入文件（{@link #inputFileId()}），
 *       指定逐行请求要打的端点（{@link #endpoint()}）与完成窗口；</li>
 *   <li>Anthropic：直接内联一组请求（{@link #requests()}），每条带 {@code custom_id} 与 {@code params}。</li>
 * </ul>
 */
public final class YoBatchRequest {

    @Nullable
    private final String inputFileId;
    @Nullable
    private final String endpoint;
    @Nullable
    private final String completionWindow;
    @Nullable
    private final JSONObject metadata;
    private final List<JSONObject> requests;

    private YoBatchRequest(Builder builder) {
        this.inputFileId = builder.inputFileId;
        this.endpoint = builder.endpoint;
        this.completionWindow = builder.completionWindow;
        this.metadata = builder.metadata;
        this.requests = Collections.unmodifiableList(new ArrayList<>(builder.requests));
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @Nullable
    public String inputFileId() {
        return inputFileId;
    }

    @Nullable
    public String endpoint() {
        return endpoint;
    }

    @Nullable
    public String completionWindow() {
        return completionWindow;
    }

    @Nullable
    public JSONObject metadata() {
        return metadata;
    }

    /** Anthropic 形态的内联请求，每条形如 {@code {"custom_id":..., "params":{...}}}。 */
    @NonNull
    public List<JSONObject> requests() {
        return requests;
    }

    public static final class Builder {
        @Nullable
        private String inputFileId;
        @Nullable
        private String endpoint;
        @Nullable
        private String completionWindow;
        @Nullable
        private JSONObject metadata;
        private final List<JSONObject> requests = new ArrayList<>();

        private Builder() {
        }

        @NonNull
        public Builder inputFileId(@Nullable String inputFileId) {
            this.inputFileId = inputFileId == null || inputFileId.isBlank() ? null : inputFileId.trim();
            return this;
        }

        @NonNull
        public Builder endpoint(@Nullable String endpoint) {
            this.endpoint = endpoint == null || endpoint.isBlank() ? null : endpoint.trim();
            return this;
        }

        @NonNull
        public Builder completionWindow(@Nullable String completionWindow) {
            this.completionWindow = completionWindow == null || completionWindow.isBlank()
                    ? null : completionWindow.trim();
            return this;
        }

        @NonNull
        public Builder metadata(@Nullable JSONObject metadata) {
            this.metadata = metadata;
            return this;
        }

        @NonNull
        public Builder addRequest(@Nullable JSONObject request) {
            if (request != null) {
                requests.add(request);
            }
            return this;
        }

        @NonNull
        public YoBatchRequest build() {
            if (requests.isEmpty() && (inputFileId == null || inputFileId.isBlank())) {
                throw new IllegalArgumentException("inputFileId or at least one inline request is required");
            }
            return new YoBatchRequest(this);
        }
    }
}
