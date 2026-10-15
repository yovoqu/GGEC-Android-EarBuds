package com.yovo.yotheme.dialog.render;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.api.content.CustomContentBinder;
import com.yovo.yotheme.dialog.api.content.CustomViewContent;
import com.yovo.yotheme.dialog.api.content.DialogContentSpec;
import com.yovo.yotheme.dialog.api.content.MessageContent;
import com.yovo.yotheme.dialog.api.content.OptionListContent;
import com.yovo.yotheme.dialog.api.content.ProgressContent;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.core.DialogStateStore;
import com.yovo.yotheme.dialog.widget.DialogMetrics;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 根据 {@link DialogContentSpec} 渲染对话框主体内容
 */
public final class BodyRenderer {

    private BodyRenderer() {}

    public static void render(@NonNull Context context,
                              @NonNull LinearLayout rootLayout,
                              @NonNull YoDialogSpec spec,
                              @NonNull DialogStateStore stateStore,
                              @NonNull DialogActionCallback callback,
                              @Nullable CustomContentLifecycle customContentLifecycle) {
        DialogContentSpec contentSpec = spec.getContentSpec();
        if (contentSpec == null) {
            return;
        }

        View bodyView = createBodyView(context, contentSpec, spec, stateStore, callback,
                customContentLifecycle);
        if (bodyView != null) {
            rootLayout.addView(bodyView, matchParentWrap());
        }
    }

    @NonNull
    private static View createBodyView(@NonNull Context context,
                                       @NonNull DialogContentSpec contentSpec,
                                       @NonNull YoDialogSpec spec,
                                       @NonNull DialogStateStore stateStore,
                                       @NonNull DialogActionCallback callback,
                                       @Nullable CustomContentLifecycle customContentLifecycle) {
        if (contentSpec instanceof MessageContent) {
            return createMessageView(context, ((MessageContent) contentSpec).getMessage());
        }
        if (contentSpec instanceof CustomViewContent) {
            return resolveCustomView(context, (CustomViewContent) contentSpec,
                    customContentLifecycle);
        }
        if (contentSpec instanceof OptionListContent) {
            return com.yovo.yotheme.dialog.render.OptionListRenderer.render(
                    context,
                    (OptionListContent) contentSpec,
                    spec,
                    stateStore,
                    callback);
        }
        if (contentSpec instanceof ProgressContent) {
            TextView placeholder = new TextView(context);
            placeholder.setVisibility(View.GONE);
            return placeholder;
        }
        TextView fallback = new TextView(context);
        fallback.setVisibility(View.GONE);
        return fallback;
    }

    @NonNull
    private static View createMessageView(@NonNull Context context, @NonNull String message) {
        TextView messageView = new TextView(context);
        messageView.setText(message);
        messageView.setTextSize(DialogConstants.MESSAGE_TEXT_SIZE.getValue());
        messageView.setLineSpacing(
                DialogMetrics.dp(context, DialogConstants.TEXT_LINE_SPACING.getValue()),
                DialogConstants.TEXT_LINE_SPACING_MULTIPLIER.getValue());
        messageView.setTextColor(ColorMapper.map(YoThemeColor.FONT_SECONDARY));
        messageView.setLinkTextColor(ColorMapper.map(YoThemeColor.BRAND));
        messageView.setPadding(
                0,
                0,
                0,
                DialogMetrics.dp(context, DialogConstants.CONTENT_BOTTOM_MARGIN.getValue()));
        messageView.setGravity(Gravity.START);
        messageView.setSingleLine(false);
        messageView.setMaxLines(Integer.MAX_VALUE);
        // 识别文案中的 URL，点击后通过系统浏览器打开
        Linkify.addLinks(messageView, Linkify.WEB_URLS);
        messageView.setMovementMethod(LinkMovementMethod.getInstance());
        return messageView;
    }

    @NonNull
    private static View resolveCustomView(@NonNull Context context,
                                          @NonNull CustomViewContent content,
                                          @Nullable CustomContentLifecycle customContentLifecycle) {
        if (content.getView() != null) {
            return detachFromParent(content.getView());
        }
        CustomContentBinder binder = content.getBinder();
        if (binder != null) {
            View view = binder.createView(context);
            detachFromParent(view);
            binder.bind(view);
            if (customContentLifecycle != null) {
                customContentLifecycle.onBound(binder, view);
            }
            return view;
        }
        TextView empty = new TextView(context);
        empty.setVisibility(View.GONE);
        return empty;
    }

    @NonNull
    private static LinearLayout.LayoutParams matchParentWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @NonNull
    private static View detachFromParent(@NonNull View view) {
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
        return view;
    }

    public interface CustomContentLifecycle {
        void onBound(@NonNull CustomContentBinder binder, @NonNull View contentView);
    }
}
