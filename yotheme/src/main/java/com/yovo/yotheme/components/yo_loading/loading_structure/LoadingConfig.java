package com.yovo.yotheme.components.yo_loading.loading_structure;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.NonNull;

import com.yovo.yotheme.color.ColorMapper;
import com.yovo.yotheme.color.YoThemeColor;
import com.yovo.yotheme.constants.LoadingConstants;

/**
 * @author tingfeng.yang
 * @date 2026-06-15
 * @description Loading 视觉与尺寸配置
 */
public final class LoadingConfig {

    public final int sizePx;
    public final int panelColor;
    public final int ringTrackColor;
    public final int ringColor;
    public final int dotColor;

    private LoadingConfig(int sizePx,
                          int panelColor,
                          int ringTrackColor,
                          int ringColor,
                          int dotColor) {
        this.sizePx = sizePx;
        this.panelColor = panelColor;
        this.ringTrackColor = ringTrackColor;
        this.ringColor = ringColor;
        this.dotColor = dotColor;
    }

    @NonNull
    public static LoadingConfig from(@NonNull Context context) {
        int white = ColorMapper.map(YoThemeColor.WHITE);
        return new LoadingConfig(
                dp(context, LoadingConstants.WINDOW_SIZE_DP.getIntValue()),
                Color.argb(LoadingConstants.PANEL_ALPHA.getIntValue(), 18, 24, 33),
                withAlpha(white, LoadingConstants.RING_TRACK_ALPHA.getIntValue()),
                withAlpha(white, LoadingConstants.RING_ALPHA.getIntValue()),
                withAlpha(white, LoadingConstants.DOT_ALPHA_BASE.getIntValue()));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
