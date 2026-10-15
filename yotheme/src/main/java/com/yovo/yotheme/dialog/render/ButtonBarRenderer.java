package com.yovo.yotheme.dialog.render;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.api.action.DialogAction;
import com.yovo.yotheme.dialog.api.action.DialogActionRole;
import com.yovo.yotheme.dialog.api.behavior.DialogBehavior;
import com.yovo.yotheme.dialog.api.lifecycle.DialogResult;
import com.yovo.yotheme.dialog.core.DialogStateStore;
import com.yovo.yotheme.dialog.widget.DialogMetrics;
import com.yovo.yotheme.dialog.widget.DialogRippleDrawable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 根据 {@link DialogAction} 列表渲染对话框底部操作按钮
 */
public final class ButtonBarRenderer {

    private ButtonBarRenderer() {}

    public static void render(@NonNull Context context,
                              @NonNull LinearLayout rootLayout,
                              @NonNull List<DialogAction> actions,
                              @NonNull DialogBehavior behavior,
                              @NonNull DialogActionCallback callback,
                              @NonNull DialogStateStore stateStore) {
        List<DialogAction> visibleActions = orderForDisplay(filterVisibleActions(actions));
        if (visibleActions.isEmpty()) {
            return;
        }

        LinearLayout buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams buttonLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DialogMetrics.dp(context, DialogConstants.BUTTON_HEIGHT.getValue()));
        buttonLayoutParams.setMargins(
                0,
                0,
                0,
                DialogMetrics.dp(context, DialogConstants.BUTTON_BOTTOM_MARGIN.getValue()));

        if (visibleActions.size() == 1) {
            addSingleButton(buttonLayout, visibleActions.get(0), behavior, callback, stateStore);
        } else {
            addDualButtons(buttonLayout, visibleActions.get(0), visibleActions.get(1), behavior, callback, stateStore);
        }

        rootLayout.addView(buttonLayout, buttonLayoutParams);
    }

    @NonNull
    private static List<DialogAction> filterVisibleActions(@NonNull List<DialogAction> actions) {
        List<DialogAction> visible = new ArrayList<>();
        for (DialogAction action : actions) {
            if (!TextUtils.isEmpty(action.getText())) {
                visible.add(action);
            }
        }
        return visible;
    }

    /** 取消/次要按钮在左，确认/主要按钮在右。 */
    @NonNull
    private static List<DialogAction> orderForDisplay(@NonNull List<DialogAction> actions) {
        if (actions.size() <= 1) {
            return actions;
        }
        List<DialogAction> ordered = new ArrayList<>(actions);
        ordered.sort((left, right) -> Integer.compare(layoutOrder(left), layoutOrder(right)));
        return ordered;
    }

    private static int layoutOrder(@NonNull DialogAction action) {
        switch (action.getRole()) {
            case SECONDARY:
            case NEUTRAL:
                return 0;
            case PRIMARY:
            case DANGER:
                return 1;
            default:
                if (DialogAction.ID_NEGATIVE.equals(action.getId())) {
                    return 0;
                }
                if (DialogAction.ID_POSITIVE.equals(action.getId())) {
                    return 1;
                }
                return 0;
        }
    }

    private static void addSingleButton(@NonNull LinearLayout buttonLayout,
                                      @NonNull DialogAction action,
                                      @NonNull DialogBehavior behavior,
                                      @NonNull DialogActionCallback callback,
                                      @NonNull DialogStateStore stateStore) {
        Context context = buttonLayout.getContext();
        TextView button = createButton(context, action, stateStore);
        button.setOnClickListener(v -> handleActionClick(action, behavior, callback, stateStore));
        buttonLayout.addView(button, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static void addDualButtons(@NonNull LinearLayout buttonLayout,
                                     @NonNull DialogAction leftAction,
                                     @NonNull DialogAction rightAction,
                                     @NonNull DialogBehavior behavior,
                                     @NonNull DialogActionCallback callback,
                                     @NonNull DialogStateStore stateStore) {
        Context context = buttonLayout.getContext();

        TextView leftButton = createButton(context, leftAction, stateStore);
        leftButton.setOnClickListener(v -> handleActionClick(leftAction, behavior, callback, stateStore));
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                DialogConstants.BUTTON_LAYOUT_WEIGHT.getValue());
        leftParams.setMargins(
                0,
                0,
                DialogMetrics.dp(context, DialogConstants.BUTTON_HORIZONTAL_MARGIN.getValue()),
                0);
        buttonLayout.addView(leftButton, leftParams);

        View divider = new View(context);
        divider.setBackgroundColor(ColorMapper.map(YoThemeColor.COMP_DIVIDER));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                DialogMetrics.dp(context, DialogConstants.DIVIDER_WIDTH.getValue()),
                DialogMetrics.dp(context, DialogConstants.DIVIDER_HEIGHT.getValue()));
        dividerParams.gravity = Gravity.CENTER_VERTICAL;
        buttonLayout.addView(divider, dividerParams);

        TextView rightButton = createButton(context, rightAction, stateStore);
        rightButton.setOnClickListener(v -> handleActionClick(rightAction, behavior, callback, stateStore));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                DialogConstants.BUTTON_LAYOUT_WEIGHT.getValue());
        rightParams.setMargins(
                DialogMetrics.dp(context, DialogConstants.BUTTON_HORIZONTAL_MARGIN.getValue()),
                0,
                0,
                0);
        buttonLayout.addView(rightButton, rightParams);
    }

    private static void handleActionClick(@NonNull DialogAction action,
                                          @NonNull DialogBehavior behavior,
                                          @NonNull DialogActionCallback callback,
                                          @NonNull DialogStateStore stateStore) {
        if (!stateStore.isActionEnabled(action.getId())) {
            return;
        }

        DialogResult result = new DialogResult(
                DialogResult.Reason.ACTION,
                action.getId(),
                new ArrayList<>(stateStore.getSelectedIds()));
        if (action.getOnClick() != null) {
            action.getOnClick().onClick(result);
        }
        callback.onAction(result);
        if (action.isAutoDismiss() && behavior.isDismissOnAction()) {
            callback.requestDismiss(result);
        }
    }

    @NonNull
    private static TextView createButton(@NonNull Context context,
                                         @NonNull DialogAction action,
                                         @NonNull DialogStateStore stateStore) {
        TextView button = new TextView(context);
        button.setText(action.getText());
        button.setTextSize(DialogConstants.BUTTON_TEXT_SIZE.getValue());
        button.setTypeface(button.getTypeface(), Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(resolveActionColor(action.getRole()));
        button.setAllCaps(false);
        updateButtonEnabled(button, stateStore.isActionEnabled(action.getId()));
        stateStore.addListener(() -> updateButtonEnabled(
                button, stateStore.isActionEnabled(action.getId())));
        button.setBackground(DialogRippleDrawable.button(context));
        return button;
    }

    private static void updateButtonEnabled(@NonNull TextView button, boolean enabled) {
        button.setClickable(enabled);
        button.setFocusable(enabled);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.4f);
    }

    private static int resolveActionColor(@NonNull DialogActionRole role) {
        switch (role) {
            case DANGER:
                return ColorMapper.map(YoThemeColor.ERROR_RED);
            case SECONDARY:
            case NEUTRAL:
            case PRIMARY:
            default:
                return ColorMapper.map(YoThemeColor.BRAND);
        }
    }
}
