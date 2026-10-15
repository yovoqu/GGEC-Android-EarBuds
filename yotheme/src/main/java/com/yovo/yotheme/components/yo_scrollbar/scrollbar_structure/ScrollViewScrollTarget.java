package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/** {@link ScrollView} 的 {@link ScrollTarget} 适配。 */
public final class ScrollViewScrollTarget implements ScrollTarget {

    private final ScrollView scrollView;
    private final List<Runnable> scrollListeners = new ArrayList<>();
    private final List<Runnable> contentSizeListeners = new ArrayList<>();

    private ViewTreeObserver.OnScrollChangedListener scrollChangedListener;
    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener;

    public ScrollViewScrollTarget(@NonNull ScrollView scrollView) {
        this.scrollView = scrollView;
    }

    public void attach() {
        if (scrollChangedListener != null) {
            return;
        }
        scrollChangedListener = () -> notifyScrollListeners();
        globalLayoutListener = () -> {
            notifyScrollListeners();
            notifyContentSizeListeners();
        };
        scrollView.getViewTreeObserver().addOnScrollChangedListener(scrollChangedListener);
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
    }

    public void detach() {
        ViewTreeObserver observer = scrollView.getViewTreeObserver();
        if (scrollChangedListener != null && observer.isAlive()) {
            observer.removeOnScrollChangedListener(scrollChangedListener);
        }
        if (globalLayoutListener != null && observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(globalLayoutListener);
        }
        scrollChangedListener = null;
        globalLayoutListener = null;
        scrollListeners.clear();
        contentSizeListeners.clear();
    }

    @Override
    public int getScrollOffset() {
        return scrollView.getScrollY();
    }

    @Override
    public int getScrollRange() {
        View child = scrollView.getChildAt(0);
        if (child == null) {
            return 0;
        }
        return Math.max(0, child.getHeight() - scrollView.getHeight());
    }

    @Override
    public int getViewportSize() {
        return scrollView.getHeight();
    }

    @Override
    public int getContentSize() {
        View child = scrollView.getChildAt(0);
        return child != null ? child.getHeight() : 0;
    }

    @Override
    public void scrollTo(int offset) {
        scrollView.scrollTo(0, Math.max(0, Math.min(getScrollRange(), offset)));
    }

    @Override
    public void addOnScrollChangedListener(@NonNull Runnable listener) {
        if (!scrollListeners.contains(listener)) {
            scrollListeners.add(listener);
        }
    }

    @Override
    public void removeOnScrollChangedListener(@NonNull Runnable listener) {
        scrollListeners.remove(listener);
    }

    @Override
    public void addOnContentSizeChangedListener(@NonNull Runnable listener) {
        if (!contentSizeListeners.contains(listener)) {
            contentSizeListeners.add(listener);
        }
    }

    @Override
    public void removeOnContentSizeChangedListener(@NonNull Runnable listener) {
        contentSizeListeners.remove(listener);
    }

    private void notifyScrollListeners() {
        for (Runnable listener : new ArrayList<>(scrollListeners)) {
            listener.run();
        }
    }

    private void notifyContentSizeListeners() {
        for (Runnable listener : new ArrayList<>(contentSizeListeners)) {
            listener.run();
        }
    }
}
