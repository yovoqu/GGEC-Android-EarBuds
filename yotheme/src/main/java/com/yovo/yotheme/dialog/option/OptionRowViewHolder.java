package com.yovo.yotheme.dialog.option;

import com.yovo.yotheme.dialog.api.option.OptionSelectionMode;

import com.yovo.yotheme.dialog.api.option.OptionItemSpec;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.constants.ListConstants;
import com.yovo.yotheme.dialog.api.style.OptionRowStyle;
import com.yovo.yotheme.dialog.api.option.TrailingActionSpec;
import com.yovo.yotheme.dialog.widget.DialogMetrics;
import com.yovo.yotheme.dialog.widget.DialogRippleDrawable;
import com.yovo.yotheme.small_widget.YoCheckbox;
import com.yovo.yotheme.small_widget.YoRadio;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 选项行 ViewHolder：行首图标、标题/副标题、YoRadio/YoCheckbox、行尾动作
 */
public final class OptionRowViewHolder {

    public static final Object PAYLOAD_CHECKED = new Object();

    private final LinearLayout rowContainer;
    private final ImageView leadingIcon;
    private final TextView titleView;
    private final LinearLayout subtitleContainer;
    private final TextView subtitleView;
    private final TextView leftSubtitleView;
    private final TextView rightSubtitleView;
    private final YoRadio radio;
    private final YoCheckbox checkbox;
    private final ImageView trailingActionIcon;
    private final View divider;
    private final boolean showSelectionControl;

    private OptionRowViewHolder(@NonNull LinearLayout rowContainer,
                                @Nullable ImageView leadingIcon,
                                @NonNull TextView titleView,
                                @NonNull LinearLayout subtitleContainer,
                                @Nullable TextView subtitleView,
                                @Nullable TextView leftSubtitleView,
                                @Nullable TextView rightSubtitleView,
                                @Nullable YoRadio radio,
                                @Nullable YoCheckbox checkbox,
                                @Nullable ImageView trailingActionIcon,
                                @NonNull View divider,
                                boolean showSelectionControl) {
        this.rowContainer = rowContainer;
        this.leadingIcon = leadingIcon;
        this.titleView = titleView;
        this.subtitleContainer = subtitleContainer;
        this.subtitleView = subtitleView;
        this.leftSubtitleView = leftSubtitleView;
        this.rightSubtitleView = rightSubtitleView;
        this.radio = radio;
        this.checkbox = checkbox;
        this.trailingActionIcon = trailingActionIcon;
        this.divider = divider;
        this.showSelectionControl = showSelectionControl;
    }

    @NonNull
    public static OptionRowViewHolder create(@NonNull Context context,
                                             @NonNull OptionSelectionMode selectionMode,
                                             @NonNull OptionRowStyle rowStyle,
                                             int dialogCornerRadiusDp,
                                             boolean showDivider) {
        int verticalPadding = DialogMetrics.dp(context, DialogConstants.OPTION_ITEM_VERTICAL_PADDING.getValue());
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout optionRow = new LinearLayout(context);
        optionRow.setOrientation(LinearLayout.HORIZONTAL);
        optionRow.setGravity(Gravity.CENTER_VERTICAL);
        optionRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        optionRow.setPadding(
                DialogMetrics.dp(context, DialogConstants.OPTION_PADDING_START.getValue()),
                verticalPadding,
                DialogMetrics.dp(context, DialogConstants.OPTION_PADDING_END.getValue()),
                verticalPadding);
        optionRow.setBackground(DialogRippleDrawable.listItem(
                context, dialogCornerRadiusDp, rowStyle.isRippleEnabled()));
        optionRow.setClickable(true);
        optionRow.setFocusable(true);

        ImageView leadingIcon = new ImageView(context);
        int leadingSizePx = DialogMetrics.dp(context, ListConstants.LEADING_SIZE_20_DP.getIntValue());
        leadingIcon.setLayoutParams(new LinearLayout.LayoutParams(leadingSizePx, leadingSizePx));
        leadingIcon.setVisibility(View.GONE);

        LinearLayout contentColumn = new LinearLayout(context);
        contentColumn.setOrientation(LinearLayout.VERTICAL);
        contentColumn.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, DialogConstants.BUTTON_LAYOUT_WEIGHT.getValue()));

        TextView titleView = new TextView(context);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextSize(DialogConstants.OPTION_TEXT_SIZE.getValue());
        titleView.setTextColor(ColorMapper.map(YoThemeColor.FONT_PRIMARY));
        contentColumn.addView(titleView);

        LinearLayout subtitleContainer = new LinearLayout(context);
        subtitleContainer.setOrientation(LinearLayout.VERTICAL);
        subtitleContainer.setVisibility(View.GONE);

        TextView subtitleView = new TextView(context);
        subtitleView.setVisibility(View.GONE);

        TextView leftSubtitleView = new TextView(context);
        leftSubtitleView.setVisibility(View.GONE);
        TextView rightSubtitleView = new TextView(context);
        rightSubtitleView.setVisibility(View.GONE);

        LinearLayout splitSubtitleRow = new LinearLayout(context);
        splitSubtitleRow.setOrientation(LinearLayout.HORIZONTAL);
        splitSubtitleRow.setVisibility(View.GONE);
        splitSubtitleRow.addView(leftSubtitleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        splitSubtitleRow.addView(rightSubtitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        subtitleContainer.addView(subtitleView);
        subtitleContainer.addView(splitSubtitleRow);
        contentColumn.addView(subtitleContainer);

        LinearLayout trailingContainer = new LinearLayout(context);
        trailingContainer.setOrientation(LinearLayout.HORIZONTAL);
        trailingContainer.setGravity(Gravity.CENTER_VERTICAL);

        int controlSize = DialogMetrics.dp(context, DialogConstants.OPTION_RADIO_SIZE.getValue());
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(controlSize, controlSize);

        boolean multi = selectionMode == OptionSelectionMode.MULTI_CONFIRM;
        boolean showSelection = selectionMode != OptionSelectionMode.NONE;
        YoRadio radio = null;
        YoCheckbox checkbox = null;
        if (showSelection) {
            if (multi) {
                checkbox = new YoCheckbox(context);
                checkbox.setClickable(false);
                checkbox.setFocusable(false);
                trailingContainer.addView(checkbox, controlParams);
            } else if (selectionMode != OptionSelectionMode.NONE) {
                radio = new YoRadio(context);
                radio.setUserInteractive(false);
                trailingContainer.addView(radio, controlParams);
            }
        }

        ImageView trailingActionIcon = new ImageView(context);
        int trailingSize = DialogMetrics.dp(context, ListConstants.TRAILING_ICON_SIZE_DP.getIntValue());
        trailingActionIcon.setLayoutParams(new LinearLayout.LayoutParams(trailingSize, trailingSize));
        trailingActionIcon.setVisibility(View.GONE);
        trailingContainer.addView(trailingActionIcon);

        View leadingSpacer = createSpacer(context, ListConstants.LEADING_CONTENT_GAP_DP.getIntValue());
        optionRow.addView(leadingIcon);
        optionRow.addView(leadingSpacer);
        optionRow.addView(contentColumn);
        optionRow.addView(createSpacer(context, rowStyle.isCompactSpacing()
                ? ListConstants.CONTENT_TRAILING_GAP_TIGHT_DP.getIntValue()
                : ListConstants.CONTENT_TRAILING_GAP_DP.getIntValue()));
        optionRow.addView(trailingContainer);
        row.addView(optionRow);

        View divider = showDivider ? createDivider(context) : new View(context);
        if (!showDivider) {
            divider.setVisibility(View.GONE);
        }
        row.addView(divider);

        return new OptionRowViewHolder(
                row,
                leadingIcon,
                titleView,
                subtitleContainer,
                subtitleView,
                leftSubtitleView,
                rightSubtitleView,
                radio,
                checkbox,
                trailingActionIcon,
                divider,
                showSelection);
    }

    private static View createSpacer(@NonNull Context context, int widthDp) {
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                DialogMetrics.dp(context, widthDp), 1));
        return spacer;
    }

    @NonNull
    public View getItemView() {
        return rowContainer;
    }

    @NonNull
    public LinearLayout getRowLayout() {
        return (LinearLayout) rowContainer.getChildAt(0);
    }

    public void setDividerVisible(boolean visible) {
        divider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void bindFull(@NonNull OptionItemSpec item,
                         int index,
                         boolean checked,
                         @NonNull OptionRowStyle rowStyle,
                         @Nullable View.OnClickListener rowClickListener) {
        getRowLayout().setOnClickListener(rowClickListener);
        getRowLayout().setEnabled(item.isEnabled());
        getRowLayout().setAlpha(item.isEnabled() ? 1f : 0.4f);

        titleView.setText(item.getTitle());

        LinearLayout rowLayout = getRowLayout();
        View leadingSpacer = rowLayout.getChildAt(1);
        if (item.getLeadingIconRes() != 0) {
            leadingIcon.setVisibility(View.VISIBLE);
            leadingIcon.setImageResource(item.getLeadingIconRes());
            leadingSpacer.setVisibility(View.VISIBLE);
        } else {
            leadingIcon.setVisibility(View.GONE);
            leadingSpacer.setVisibility(View.GONE);
        }

        bindSubtitles(item, rowStyle);
        bindChecked(checked);
        bindTrailingAction(item, index);
    }

    public void bindChecked(boolean checked) {
        if (radio != null) {
            radio.setChecked(checked);
        }
        if (checkbox != null) {
            checkbox.setChecked(checked);
        }
    }

    private void bindSubtitles(@NonNull OptionItemSpec item, @NonNull OptionRowStyle rowStyle) {
        float subtitleSp = rowStyle.getSubtitleTextSizeSp();
        int textColor = ColorMapper.map(YoThemeColor.FONT_SECONDARY);

        if (item.hasSplitSubtitle()) {
            subtitleView.setVisibility(View.GONE);
            subtitleContainer.setVisibility(View.VISIBLE);
            leftSubtitleView.setVisibility(View.VISIBLE);
            rightSubtitleView.setVisibility(View.VISIBLE);
            leftSubtitleView.setText(item.getLeftSubtitle() != null ? item.getLeftSubtitle() : "");
            rightSubtitleView.setText(item.getRightSubtitle());
            leftSubtitleView.setTextSize(subtitleSp);
            rightSubtitleView.setTextSize(subtitleSp);
            leftSubtitleView.setTextColor(textColor);
            rightSubtitleView.setTextColor(textColor);
            ((View) leftSubtitleView.getParent()).setVisibility(View.VISIBLE);
            return;
        }

        String subtitle = item.getSubtitle();
        if (subtitle != null && !subtitle.isEmpty()) {
            subtitleContainer.setVisibility(View.VISIBLE);
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setText(subtitle);
            subtitleView.setTextSize(subtitleSp);
            subtitleView.setTextColor(textColor);
            if (leftSubtitleView.getParent() instanceof View) {
                ((View) leftSubtitleView.getParent()).setVisibility(View.GONE);
            }
            return;
        }

        subtitleContainer.setVisibility(View.GONE);
    }

    private void bindTrailingAction(@NonNull OptionItemSpec item, int index) {
        TrailingActionSpec action = item.getTrailingAction();
        if (action == null) {
            trailingActionIcon.setVisibility(View.GONE);
            trailingActionIcon.setOnClickListener(null);
            return;
        }
        trailingActionIcon.setVisibility(View.VISIBLE);
        trailingActionIcon.setImageResource(action.getIconResId());
        trailingActionIcon.setOnClickListener(v ->
                action.getListener().onClick(index, item.getId()));
    }

    @NonNull
    private static View createDivider(@NonNull Context context) {
        View divider = new View(context);
        divider.setBackgroundColor(ColorMapper.map(YoThemeColor.COMP_DIVIDER));
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DialogMetrics.hairline(context)));
        return divider;
    }
}

