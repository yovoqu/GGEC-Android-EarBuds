package com.yovo.yotheme.dialog.render;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.api.option.HeaderLeadingSpec;
import com.yovo.yotheme.dialog.api.option.HeaderTrailingSpec;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.core.DialogStateStore;
import com.yovo.yotheme.dialog.widget.DialogMetrics;
import com.yovo.yotheme.small_widget.YoCheckbox;

import java.util.ArrayList;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 渲染对话框标题、副标题及可选的标题栏首尾插槽
 */
public final class HeaderRenderer {

    private static final float LEADING_DISABLED_ALPHA = 0.38f;

    private HeaderRenderer() {}

    public static void render(@NonNull Context context,
                              @NonNull LinearLayout rootLayout,
                              @NonNull YoDialogSpec spec,
                              @NonNull DialogStateStore stateStore) {
        String title = spec.getTitle();
        if (TextUtils.isEmpty(title)) {
            return;
        }

        LinearLayout titleSection = new LinearLayout(context);
        titleSection.setOrientation(LinearLayout.VERTICAL);

        View leadingView = createLeadingView(context, spec.getHeaderLeading(), stateStore);
        View trailingView = createTrailingView(context, spec.getHeaderTrailing(), stateStore);
        if (leadingView == null && trailingView == null) {
            titleSection.addView(createTitleView(context, title), matchParentWrap());
        } else {
            FrameLayout titleRow = new FrameLayout(context);
            titleRow.setPadding(0, 0, 0,
                    DialogMetrics.dp(context, DialogConstants.TITLE_BOTTOM_PADDING.getValue()));

            int sidePad = DialogMetrics.dp(context, DialogConstants.OPTION_RADIO_SIZE.getValue() + 8);
            TextView titleView = createTitleView(context, title);
            titleView.setPadding(
                    leadingView != null ? sidePad : 0,
                    0,
                    trailingView != null ? sidePad : 0,
                    0);
            titleRow.addView(titleView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));

            if (leadingView != null) {
                titleRow.addView(leadingView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.START | Gravity.CENTER_VERTICAL));
            }
            if (trailingView != null) {
                titleRow.addView(trailingView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.END | Gravity.CENTER_VERTICAL));
            }
            titleSection.addView(titleRow, matchParentWrap());
        }

        addSubtitleIfPresent(context, titleSection, spec.getSubtitle());
        rootLayout.addView(titleSection, matchParentWrap());
    }

    @Nullable
    private static View createLeadingView(@NonNull Context context,
                                          @Nullable HeaderLeadingSpec leadingSpec,
                                          @NonNull DialogStateStore stateStore) {
        if (leadingSpec == null) {
            return null;
        }

        ImageView iconView = new ImageView(context);
        int size = DialogMetrics.dp(context, DialogConstants.OPTION_RADIO_SIZE.getValue());
        iconView.setLayoutParams(new FrameLayout.LayoutParams(size, size));
        iconView.setImageResource(leadingSpec.getIconResId());
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconView.setClickable(true);
        iconView.setFocusable(true);

        Runnable syncEnabled = () -> {
            boolean enabled = !leadingSpec.isEnableWhenHasSelection()
                    || !stateStore.getSelectedIds().isEmpty();
            iconView.setEnabled(enabled);
            iconView.setAlpha(enabled ? 1f : LEADING_DISABLED_ALPHA);
            int tint = enabled
                    ? ColorMapper.map(YoThemeColor.ICON_EMPHASIZE)
                    : ColorMapper.map(YoThemeColor.ICON_TERTIARY);
            iconView.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        };
        syncEnabled.run();
        stateStore.addListener(syncEnabled::run);

        HeaderLeadingSpec.OnClickListener listener = leadingSpec.getListener();
        if (listener != null) {
            iconView.setOnClickListener(v -> {
                if (!iconView.isEnabled()) {
                    return;
                }
                listener.onClick(new ArrayList<>(stateStore.getSelectedIds()));
            });
        }
        return iconView;
    }

    @Nullable
    private static View createTrailingView(@NonNull Context context,
                                           @Nullable HeaderTrailingSpec trailingSpec,
                                           @NonNull DialogStateStore stateStore) {
        if (trailingSpec == null) {
            return null;
        }

        switch (trailingSpec.getType()) {
            case SELECT_ALL_CHECKBOX:
                return createSelectAllCheckbox(context, stateStore);
            case SELECT_ALL_TEXT:
                return createSelectAllText(context, stateStore);
            default:
                return null;
        }
    }

    @NonNull
    private static TextView createSelectAllText(@NonNull Context context,
                                                @NonNull DialogStateStore stateStore) {
        TextView selectAllView = new TextView(context);
        selectAllView.setTextSize(DialogConstants.OPTION_TEXT_SIZE.getValue());
        selectAllView.setTextColor(ColorMapper.map(YoThemeColor.BRAND));
        updateSelectAllText(selectAllView, stateStore.isAllSelected());
        selectAllView.setOnClickListener(v -> {
            stateStore.toggleSelectAll();
            updateSelectAllText(selectAllView, stateStore.isAllSelected());
        });
        stateStore.addListener(() -> updateSelectAllText(selectAllView, stateStore.isAllSelected()));
        return selectAllView;
    }

    @NonNull
    private static YoCheckbox createSelectAllCheckbox(@NonNull Context context,
                                                      @NonNull DialogStateStore stateStore) {
        YoCheckbox selectAllCheckbox = new YoCheckbox(context);
        int size = DialogMetrics.dp(context, DialogConstants.OPTION_RADIO_SIZE.getValue());
        selectAllCheckbox.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        selectAllCheckbox.setChecked(stateStore.isAllSelected());
        selectAllCheckbox.setOnClickListener(v -> stateStore.toggleSelectAll());
        stateStore.addListener(() -> selectAllCheckbox.setChecked(stateStore.isAllSelected()));
        return selectAllCheckbox;
    }

    private static void updateSelectAllText(@NonNull TextView selectAllView, boolean allSelected) {
        selectAllView.setText(allSelected ? "取消全选" : "全选");
    }

    @NonNull
    private static TextView createTitleView(@NonNull Context context, @NonNull String title) {
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(DialogConstants.TITLE_TEXT_SIZE.getValue());
        titleView.setTypeface(titleView.getTypeface(), Typeface.BOLD);
        titleView.setTextColor(ColorMapper.map(YoThemeColor.FONT_PRIMARY));
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(
                0,
                0,
                0,
                DialogMetrics.dp(context, DialogConstants.TITLE_BOTTOM_PADDING.getValue()));
        return titleView;
    }

    private static void addSubtitleIfPresent(@NonNull Context context,
                                             @NonNull LinearLayout titleSection,
                                             @Nullable String subtitle) {
        if (TextUtils.isEmpty(subtitle)) {
            return;
        }
        TextView subtitleView = new TextView(context);
        subtitleView.setText(subtitle);
        subtitleView.setTextSize(DialogConstants.MESSAGE_TEXT_SIZE.getValue());
        subtitleView.setTextColor(ColorMapper.map(YoThemeColor.FONT_SECONDARY));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(
                0,
                0,
                0,
                DialogMetrics.dp(context, DialogConstants.CONTENT_BOTTOM_MARGIN.getValue()));
        titleSection.addView(subtitleView, matchParentWrap());
    }

    @NonNull
    private static LinearLayout.LayoutParams matchParentWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
