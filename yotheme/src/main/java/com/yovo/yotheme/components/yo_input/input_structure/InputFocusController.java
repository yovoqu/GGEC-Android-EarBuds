package com.yovo.yotheme.components.yo_input.input_structure;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.yovo.yotheme.constants.InputConstants;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author tingfeng.yang
 * @date 2026-06-13
 * @description 统一管理 YoInput 的键盘收起失焦与窗口级生命周期
 */
public final class InputFocusController {

    public interface Host {
        View asView();

        InputTextEditor getEditor();

        void onFocusStateChanged();
    }

    private static final CopyOnWriteArrayList<WeakReference<Host>> HOSTS = new CopyOnWriteArrayList<>();
    private static final Map<View, RootHook> ROOT_HOOKS = new ConcurrentHashMap<>();

    private InputFocusController() {
    }

    public static void register(Host host) {
        purgeStaleHosts();
        HOSTS.add(new WeakReference<>(host));
        View root = host.asView().getRootView();
        if (root != null) {
            ensureRootHook(root, host.asView().getContext());
        }
    }

    public static void unregister(Host host) {
        unregister(host, host.asView().getRootView());
    }

    public static void unregister(Host host, View root) {
        HOSTS.removeIf(ref -> {
            Host registered = ref.get();
            return registered == null || registered == host;
        });
        purgeStaleHosts();

        if (root != null && !hasHostOnRoot(root)) {
            releaseRootHook(root);
        }
    }

    public static void onWindowFocusChanged(Host host, boolean hasWindowFocus) {
        if (!hasWindowFocus && host.getEditor().isFocused()) {
            dismissEditor(host);
        } else {
            host.onFocusStateChanged();
        }
    }

    public static void dismissEditor(Host host) {
        if (host == null) {
            return;
        }
        InputTextEditor editor = host.getEditor();
        editor.hideImeAndClearFocus();
        View container = host.asView();
        container.requestFocus();
        host.onFocusStateChanged();
    }

    private static void ensureRootHook(View root, Context context) {
        ROOT_HOOKS.computeIfAbsent(root, key -> new RootHook(key, context));
    }

    private static void releaseRootHook(View root) {
        RootHook hook = ROOT_HOOKS.remove(root);
        if (hook != null) {
            hook.release();
        }
    }

    private static boolean hasHostOnRoot(View root) {
        for (WeakReference<Host> reference : HOSTS) {
            Host host = reference.get();
            if (host == null) {
                continue;
            }
            View hostRoot = host.asView().getRootView();
            if (hostRoot == root) {
                return true;
            }
        }
        return false;
    }

    private static void purgeStaleHosts() {
        HOSTS.removeIf(ref -> ref.get() == null);
    }

    private static void dismissAllFocusedEditorsOnRoot(View root) {
        for (WeakReference<Host> reference : HOSTS) {
            Host host = reference.get();
            if (host == null) {
                continue;
            }
            if (host.asView().getRootView() != root) {
                continue;
            }
            if (host.getEditor().isFocused()) {
                dismissEditor(host);
            }
        }
    }

    private static int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density + InputConstants.ROUNDING_FLOAT.getFloatValue());
    }

    private static final class RootHook {
        private final View root;
        private final ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
        private final int keyboardVisibleHeightThresholdPx;
        private boolean keyboardVisibleByLayout;
        private boolean imeVisible;

        private RootHook(View root, Context context) {
            this.root = root;
            keyboardVisibleHeightThresholdPx = dpToPx(context, 100);
            keyboardLayoutListener = () -> {
                Rect visibleFrame = new Rect();
                root.getWindowVisibleDisplayFrame(visibleFrame);
                int heightDiff = root.getHeight() - visibleFrame.height();
                boolean nowVisible = heightDiff > keyboardVisibleHeightThresholdPx;
                if (keyboardVisibleByLayout && !nowVisible) {
                    dismissAllFocusedEditorsOnRoot(root);
                }
                keyboardVisibleByLayout = nowVisible;
            };
            root.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);

            ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                boolean nowVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
                if (imeVisible && !nowVisible) {
                    dismissAllFocusedEditorsOnRoot(root);
                }
                imeVisible = nowVisible;
                return ViewCompat.onApplyWindowInsets(view, insets);
            });
            ViewCompat.requestApplyInsets(root);
        }

        private void release() {
            ViewCompat.setOnApplyWindowInsetsListener(root, null);
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(keyboardLayoutListener);
            }
        }
    }
}
