package com.yovo.yotheme.system_set;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * @author tingfeng.yang
 * @date 2025-11-26
 * @description 系统栏工具类
 */
public final class SystemBarUtils {

    private SystemBarUtils() {
    }

    public static void applyBottomInsetToView(@NonNull View rootView, @NonNull View bottomView) {
        final int basePaddingLeft = bottomView.getPaddingLeft();
        final int basePaddingTop = bottomView.getPaddingTop();
        final int basePaddingRight = bottomView.getPaddingRight();
        final int basePaddingBottom = bottomView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, 0, systemBars.right, 0);
            bottomView.setPadding(
                    basePaddingLeft,
                    basePaddingTop,
                    basePaddingRight,
                    basePaddingBottom + systemBars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(rootView);
    }

    public static void setupNavigationBarWithBackground(@NonNull Window window,
                                                        @NonNull Context context,
                                                        @Nullable Drawable background,
                                                        int defaultColorResId,
                                                        boolean lightNavigationBars) {
        WindowCompat.setDecorFitsSystemWindows(window, false);

        int panelColor = ContextCompat.getColor(context, defaultColorResId);
        if (background instanceof ColorDrawable) {
            panelColor = ((ColorDrawable) background).getColor();
        }

        window.setNavigationBarColor(panelColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(panelColor);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightNavigationBars(lightNavigationBars);
    }

    public static void setupTransparentNavigationBar(@NonNull Window window,
                                                     boolean lightNavigationBars) {
        WindowCompat.setDecorFitsSystemWindows(window, false);

        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightNavigationBars(lightNavigationBars);
    }

    /**
     * 沉浸式浅色状态栏：透明背景 + 深色图标/文字，适配浅色页面背景。
     */
    public static void applyLightStatusBar(@NonNull Window window) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
        }

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    /**
     * Floating windows such as YoDialog and YoLoading keep the host Activity's
     * status-bar icon appearance while preventing platform dim from repainting
     * the system-bar area.
     */
    public static void applyFloatingStatusBar(@NonNull Window window, @NonNull Context context) {
        applyFloatingStatusBar(window, resolveLightStatusBar(context));
    }

    /**
     * Edge-to-edge floating dialog window: transparent status/navigation bars,
     * host appearance preserved, custom backdrop rendered inside dialog content.
     */
    public static void applyFloatingDialogWindow(@NonNull Window window, @NonNull Context context) {
        boolean lightSystemBars = resolveLightStatusBar(context);
        applyFloatingStatusBar(window, lightSystemBars);
        setupTransparentNavigationBar(window, lightSystemBars);
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    private static void applyFloatingStatusBar(@NonNull Window window, boolean lightStatusBars) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        window.setAttributes(params);
        window.setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
        }

        View decorView = window.getDecorView();
        int flags = decorView.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (lightStatusBars) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }
        decorView.setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window.getInsetsController() != null) {
            window.getInsetsController().setSystemBarsAppearance(
                    lightStatusBars ? WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS : 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        }

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, decorView);
        controller.setAppearanceLightStatusBars(lightStatusBars);
    }

    private static boolean resolveLightStatusBar(@NonNull Context context) {
        Activity activity = findActivity(context);
        if (activity != null) {
            Window hostWindow = activity.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && hostWindow.getInsetsController() != null) {
                int appearance = hostWindow.getInsetsController().getSystemBarsAppearance();
                return (appearance & WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS) != 0;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int flags = hostWindow.getDecorView().getSystemUiVisibility();
                if ((flags & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0) {
                    return true;
                }
            }
        }
        return resolveThemeLightStatusBar(context);
    }

    private static boolean resolveThemeLightStatusBar(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        TypedValue outValue = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.windowLightStatusBar, outValue, true)) {
            if (outValue.type == TypedValue.TYPE_INT_BOOLEAN) {
                return outValue.data != 0;
            }
            if (outValue.resourceId != 0) {
                try {
                    return context.getResources().getBoolean(outValue.resourceId);
                } catch (Resources.NotFoundException ignored) {
                    return true;
                }
            }
        }
        return true;
    }

    @Nullable
    private static Activity findActivity(@NonNull Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity ? (Activity) current : null;
    }
}
