package com.yovo.lib_ai.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 文/图生视频请求（Qwen Wan3.0）。
 *
 * <p>对应 {@code input.prompt} + {@code input.media[]}（{@code type/url}）+
 * {@code parameters.resolution/ratio/duration/prompt_extend}。
 */
public final class YoVideoRequest {

    private final String prompt;
    private final String model;
    @Nullable
    private final String resolution;
    @Nullable
    private final String ratio;
    @Nullable
    private final Integer duration;
    @NonNull
    private final List<MediaRef> media;
    @Nullable
    private final Boolean promptExtend;
    @Nullable
    private final String negativePrompt;
    @Nullable
    private final Integer seed;

    private YoVideoRequest(
            String prompt,
            String model,
            @Nullable String resolution,
            @Nullable String ratio,
            @Nullable Integer duration,
            @NonNull List<MediaRef> media,
            @Nullable Boolean promptExtend,
            @Nullable String negativePrompt,
            @Nullable Integer seed) {
        this.prompt = prompt;
        this.model = model;
        this.resolution = resolution;
        this.ratio = ratio;
        this.duration = duration;
        this.media = List.copyOf(media);
        this.promptExtend = promptExtend;
        this.negativePrompt = negativePrompt;
        this.seed = seed;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    @NonNull
    public String prompt() {
        return prompt;
    }

    @NonNull
    public String model() {
        return model;
    }

    @Nullable
    public String resolution() {
        return resolution;
    }

    @Nullable
    public String ratio() {
        return ratio;
    }

    @Nullable
    public Integer duration() {
        return duration;
    }

    @NonNull
    public List<MediaRef> media() {
        return media;
    }

    @Nullable
    public Boolean promptExtend() {
        return promptExtend;
    }

    @Nullable
    public String negativePrompt() {
        return negativePrompt;
    }

    @Nullable
    public Integer seed() {
        return seed;
    }

    /** 一条参考素材：{@code type} 如 {@code reference_image/first_frame/last_frame}。 */
    public static final class MediaRef {

        private final String type;
        private final String url;

        public MediaRef(@NonNull String type, @NonNull String url) {
            this.type = Objects.requireNonNull(type, "type");
            this.url = Objects.requireNonNull(url, "url");
        }

        @NonNull
        public String type() {
            return type;
        }

        @NonNull
        public String url() {
            return url;
        }
    }

    public static final class Builder {

        @Nullable
        private String prompt;
        @Nullable
        private String model;
        @Nullable
        private String resolution;
        @Nullable
        private String ratio;
        @Nullable
        private Integer duration;
        private final List<MediaRef> media = new ArrayList<>();
        @Nullable
        private Boolean promptExtend;
        @Nullable
        private String negativePrompt;
        @Nullable
        private Integer seed;

        private Builder() {
        }

        @NonNull
        public Builder prompt(@NonNull String prompt) {
            this.prompt = trimToNull(prompt);
            return this;
        }

        @NonNull
        public Builder model(@NonNull String model) {
            this.model = trimToNull(model);
            return this;
        }

        @NonNull
        public Builder resolution(@Nullable String resolution) {
            this.resolution = trimToNull(resolution);
            return this;
        }

        @NonNull
        public Builder ratio(@Nullable String ratio) {
            this.ratio = trimToNull(ratio);
            return this;
        }

        /** 时长（秒），必须 > 0。 */
        @NonNull
        public Builder duration(@Nullable Integer duration) {
            if (duration != null && duration <= 0) {
                throw new IllegalArgumentException("duration must be > 0");
            }
            this.duration = duration;
            return this;
        }

        @NonNull
        public Builder addMedia(@NonNull String type, @NonNull String url) {
            this.media.add(new MediaRef(type, url));
            return this;
        }

        @NonNull
        public Builder media(@Nullable List<MediaRef> media) {
            this.media.clear();
            if (media != null) {
                this.media.addAll(media);
            }
            return this;
        }

        @NonNull
        public Builder promptExtend(@Nullable Boolean promptExtend) {
            this.promptExtend = promptExtend;
            return this;
        }

        @NonNull
        public Builder negativePrompt(@Nullable String negativePrompt) {
            this.negativePrompt = trimToNull(negativePrompt);
            return this;
        }

        @NonNull
        public Builder seed(@Nullable Integer seed) {
            this.seed = seed;
            return this;
        }

        @NonNull
        public YoVideoRequest build() {
            if (prompt == null) {
                throw new IllegalArgumentException("prompt is required");
            }
            if (model == null) {
                throw new IllegalArgumentException("model is required");
            }
            return new YoVideoRequest(
                    prompt, model, resolution, ratio, duration, media,
                    promptExtend, negativePrompt, seed);
        }

        @Nullable
        private static String trimToNull(@Nullable String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
