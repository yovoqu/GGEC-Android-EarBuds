package com.yovo.yotheme.dialog.render;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;

import com.yovo.yotheme.dialog.api.style.DialogStyle;
import com.yovo.yotheme.dialog.api.YoDialogSpec;
import com.yovo.yotheme.dialog.performance.DialogPerformancePolicy;
import com.yovo.yotheme.dialog.widget.DialogMetrics;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 为面板内容添加水平屏幕边距与阴影处理
 */
public final class DialogFrameRenderer {

    private DialogFrameRenderer() {}

    @NonNull
    public static View wrap(@NonNull Context context,
                            @NonNull View contentView,
                            @NonNull YoDialogSpec spec,
                            @NonNull DialogPerformancePolicy policy) {
        DialogStyle style = spec.getStyle();
        int horizontalMarginPx = DialogMetrics.dp(context, style.getHorizontalScreenMarginDp());
        return ShadowRenderer.wrap(
                context,
                contentView,
                style,
                policy.getShadowMode(),
                horizontalMarginPx);
    }
}
