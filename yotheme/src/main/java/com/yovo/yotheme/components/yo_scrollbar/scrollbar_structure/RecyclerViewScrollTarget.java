package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RecyclerView} 的 {@link ScrollTarget} 适配。
 */
public final class RecyclerViewScrollTarget implements ScrollTarget {

    private final RecyclerView recyclerView;
    private final List<Runnable> scrollListeners = new ArrayList<>();
    private final List<Runnable> contentSizeListeners = new ArrayList<>();

    private final RecyclerView.OnScrollListener scrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    notifyScrollListeners();
                }
            };

    private final Runnable contentSizeRunnable = this::notifyContentSizeListeners;

    public RecyclerViewScrollTarget(@NonNull RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    public void attach() {
        recyclerView.addOnScrollListener(scrollListener);
        recyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                notifyContentSizeListeners());
        recyclerView.post(contentSizeRunnable);
    }

    public void detach() {
        recyclerView.removeOnScrollListener(scrollListener);
        scrollListeners.clear();
        contentSizeListeners.clear();
    }

    @Override
    public int getScrollOffset() {
        return recyclerView.computeVerticalScrollOffset();
    }

    @Override
    public int getScrollRange() {
        return Math.max(0, recyclerView.computeVerticalScrollRange()
                - recyclerView.computeVerticalScrollExtent());
    }

    @Override
    public int getViewportSize() {
        return recyclerView.computeVerticalScrollExtent();
    }

    @Override
    public int getContentSize() {
        return recyclerView.computeVerticalScrollRange();
    }

    @Override
    public void scrollTo(int offset) {
        int range = getScrollRange();
        int clamped = Math.max(0, Math.min(range, offset));
        recyclerView.scrollTo(0, clamped);
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
