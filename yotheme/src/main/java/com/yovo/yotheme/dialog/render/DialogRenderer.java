package com.yovo.yotheme.dialog.render;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.performance.DialogPerformancePolicy;
import com.yovo.yotheme.dialog.core.DialogStateStore;
import com.yovo.yotheme.dialog.widget.DialogMetrics;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 协调 frame、background、header、body 与 button bar 渲染器
 */
public final class DialogRenderer {

    private DialogRenderer() {}

    @NonNull
    public static View buildContentView(@NonNull Context context,
                                        @NonNull YoDialogSpec spec,
                                        @NonNull DialogStateStore stateStore,
                                        @NonNull DialogActionCallback callback,
                                        @Nullable BodyRenderer.CustomContentLifecycle customContentLifecycle) {
        LinearLayout contentRoot = new LinearLayout(context);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = DialogMetrics.dp(
                context,
                spec.getStyle().getContentPaddingHorizontalDp());
        contentRoot.setPadding(
                horizontalPadding,
                DialogMetrics.dp(context, DialogConstants.PADDING_TOP.getValue()),
                horizontalPadding,
                DialogMetrics.dp(context, DialogConstants.PADDING_BOTTOM.getValue()));

        HeaderRenderer.render(context, contentRoot, spec, stateStore);
        BodyRenderer.render(context, contentRoot, spec, stateStore, callback, customContentLifecycle);
        stateStore.notifyStateChanged();
        ButtonBarRenderer.render(
                context,
                contentRoot,
                spec.getActions(),
                spec.getBehavior(),
                callback,
                stateStore);

        DialogPerformancePolicy policy = spec.getPerformancePolicy();
        View withBackground = BackgroundRenderer.wrap(
                context,
                contentRoot,
                spec,
                policy);
        return DialogFrameRenderer.wrap(
                context,
                withBackground,
                spec,
                policy);
    }
}
