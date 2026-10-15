package com.yovo.yotheme.dialog.api.option;

import com.yovo.yotheme.dialog.api.style.OptionRowStyle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.constants.DialogConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 选项列表的公共配置
 */
public final class OptionListSpec {

    @NonNull
    private final List<OptionItemSpec> items;
    @NonNull
    private final OptionSelectionMode selectionMode;
    private final int maxHeightDp;
    @NonNull
    private final OptionRowStyle rowStyle;
    @Nullable
    private final String emptyStateText;

    private OptionListSpec(@NonNull Builder builder) {
        this.items = Collections.unmodifiableList(new ArrayList<>(builder.items));
        this.selectionMode = builder.selectionMode;
        this.maxHeightDp = builder.maxHeightDp;
        this.rowStyle = builder.rowStyle != null ? builder.rowStyle : OptionRowStyle.defaults();
        this.emptyStateText = builder.emptyStateText;
    }

    @NonNull
    public List<OptionItemSpec> getItems() {
        return items;
    }

    @NonNull
    public OptionSelectionMode getSelectionMode() {
        return selectionMode;
    }

    public int getMaxHeightDp() {
        return maxHeightDp;
    }

    @NonNull
    public OptionRowStyle getRowStyle() {
        return rowStyle;
    }

    @Nullable
    public String getEmptyStateText() {
        return emptyStateText;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        @NonNull
        private final List<OptionItemSpec> items = new ArrayList<>();
        @NonNull
        private OptionSelectionMode selectionMode = OptionSelectionMode.NONE;
        private int maxHeightDp = DialogConstants.SELECT_CONTENT_MAX_HEIGHT_DP.getValue();
        @Nullable
        private OptionRowStyle rowStyle;
        @Nullable
        private String emptyStateText;

        @NonNull
        public Builder items(@NonNull List<OptionItemSpec> items) {
            this.items.clear();
            this.items.addAll(items);
            return this;
        }

        @NonNull
        public Builder addItem(@NonNull OptionItemSpec item) {
            this.items.add(item);
            return this;
        }

        @NonNull
        public Builder selectionMode(@NonNull OptionSelectionMode selectionMode) {
            this.selectionMode = selectionMode;
            return this;
        }

        @NonNull
        public Builder maxHeightDp(int maxHeightDp) {
            this.maxHeightDp = maxHeightDp;
            return this;
        }

        @NonNull
        public Builder rowStyle(@Nullable OptionRowStyle rowStyle) {
            this.rowStyle = rowStyle;
            return this;
        }

        @NonNull
        public Builder emptyStateText(@Nullable String emptyStateText) {
            this.emptyStateText = emptyStateText;
            return this;
        }

        @NonNull
        public OptionListSpec build() {
            return new OptionListSpec(this);
        }
    }
}


