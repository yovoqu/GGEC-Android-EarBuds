package com.yovo.yotheme.dialog.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.behavior.DialogBehavior;
import com.yovo.yotheme.dialog.api.content.DialogContentSpec;
import com.yovo.yotheme.dialog.api.option.HeaderLeadingSpec;
import com.yovo.yotheme.dialog.api.option.HeaderTrailingSpec;
import com.yovo.yotheme.dialog.api.style.DialogAnimationSpec;
import com.yovo.yotheme.dialog.api.style.DialogStyle;
import com.yovo.yotheme.dialog.performance.DialogPerformancePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 描述 YoDialog 实例的不可变配置
 */
public final class YoDialogSpec {

    @Nullable
    private final String title;

    @Nullable
    private final String subtitle;

    @Nullable
    private final DialogContentSpec contentSpec;

    @NonNull
    private final List<DialogAction> actions;

    @NonNull
    private final DialogBehavior behavior;

    @NonNull
    private final DialogStyle style;

    @NonNull
    private final DialogAnimationSpec animation;

    @NonNull
    private final DialogPerformancePolicy performancePolicy;

    @Nullable
    private final HeaderLeadingSpec headerLeading;

    @Nullable
    private final HeaderTrailingSpec headerTrailing;

    private YoDialogSpec(@NonNull Builder builder) {
        this.title = builder.title;
        this.subtitle = builder.subtitle;
        this.contentSpec = builder.contentSpec;
        this.actions = Collections.unmodifiableList(new ArrayList<>(builder.actions));
        this.behavior = builder.behavior;
        this.style = builder.style;
        this.animation = builder.animation;
        this.performancePolicy = builder.performancePolicy;
        this.headerLeading = builder.headerLeading;
        this.headerTrailing = builder.headerTrailing;
    }

    @Nullable
    public String getTitle() {
        return title;
    }

    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    @Nullable
    public DialogContentSpec getContentSpec() {
        return contentSpec;
    }

    @NonNull
    public List<DialogAction> getActions() {
        return actions;
    }

    @NonNull
    public DialogBehavior getBehavior() {
        return behavior;
    }

    @NonNull
    public DialogStyle getStyle() {
        return style;
    }

    @NonNull
    public DialogAnimationSpec getAnimation() {
        return animation;
    }

    @NonNull
    public DialogPerformancePolicy getPerformancePolicy() {
        return performancePolicy;
    }

    @Nullable
    public HeaderLeadingSpec getHeaderLeading() {
        return headerLeading;
    }

    @Nullable
    public HeaderTrailingSpec getHeaderTrailing() {
        return headerTrailing;
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        @Nullable
        private String title;

        @Nullable
        private String subtitle;

        @Nullable
        private DialogContentSpec contentSpec;

        @NonNull
        private List<DialogAction> actions = new ArrayList<>();

        @NonNull
        private DialogBehavior behavior = DialogBehavior.defaults();

        @NonNull
        private DialogStyle style = DialogStyle.defaults();

        @NonNull
        private DialogAnimationSpec animation = DialogAnimationSpec.defaults();

        @NonNull
        private DialogPerformancePolicy performancePolicy = DialogPerformancePolicy.defaults();

        @Nullable
        private HeaderLeadingSpec headerLeading;

        @Nullable
        private HeaderTrailingSpec headerTrailing;

        public Builder() {}

        private Builder(@NonNull YoDialogSpec source) {
            this.title = source.title;
            this.subtitle = source.subtitle;
            this.contentSpec = source.contentSpec;
            this.actions = new ArrayList<>(source.actions);
            this.behavior = source.behavior;
            this.style = source.style;
            this.animation = source.animation;
            this.performancePolicy = source.performancePolicy;
            this.headerLeading = source.headerLeading;
            this.headerTrailing = source.headerTrailing;
        }

        @NonNull
        public Builder title(@Nullable String title) {
            this.title = title;
            return this;
        }

        @NonNull
        public Builder subtitle(@Nullable String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        @NonNull
        public Builder contentSpec(@Nullable DialogContentSpec contentSpec) {
            this.contentSpec = contentSpec;
            return this;
        }

        @NonNull
        public Builder content(@Nullable DialogContentSpec contentSpec) {
            return contentSpec(contentSpec);
        }

        @NonNull
        public Builder actions(@NonNull List<DialogAction> actions) {
            this.actions = new ArrayList<>(actions);
            return this;
        }

        @NonNull
        public Builder addAction(@NonNull DialogAction action) {
            this.actions.add(action);
            return this;
        }

        @NonNull
        public Builder behavior(@NonNull DialogBehavior behavior) {
            this.behavior = behavior;
            return this;
        }

        @NonNull
        public Builder style(@NonNull DialogStyle style) {
            this.style = style;
            return this;
        }

        @NonNull
        public Builder animation(@NonNull DialogAnimationSpec animation) {
            this.animation = animation;
            return this;
        }

        @NonNull
        public Builder performancePolicy(@NonNull DialogPerformancePolicy performancePolicy) {
            this.performancePolicy = performancePolicy;
            return this;
        }

        @NonNull
        public Builder headerLeading(@Nullable HeaderLeadingSpec headerLeading) {
            this.headerLeading = headerLeading;
            return this;
        }

        @NonNull
        public Builder headerTrailing(@Nullable HeaderTrailingSpec headerTrailing) {
            this.headerTrailing = headerTrailing;
            return this;
        }

        @NonNull
        public YoDialogSpec build() {
            return new YoDialogSpec(this);
        }
    }
}

