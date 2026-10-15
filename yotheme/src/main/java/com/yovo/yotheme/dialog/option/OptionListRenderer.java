package com.yovo.yotheme.dialog.option;

import com.yovo.yotheme.dialog.api.option.OptionSelectionMode;

import com.yovo.yotheme.dialog.api.option.OptionListSpec;

import com.yovo.yotheme.dialog.api.option.OptionItemSpec;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.yovo.yotheme.components.yo_scrollbar.YoScrollBar;
import com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure.RecyclerViewScrollTarget;
import com.yovo.yotheme.constants.DialogConstants;
import com.yovo.yotheme.dialog.widget.DialogMetrics;
import com.yovo.yotheme.dialog.widget.MaxHeightFrameLayout;

/**
 * @author tingfeng.yang
 * @date 2026-06-29
 * @description 选项列表渲染器：RecyclerView 限高 + YoScrollBar
 */
public final class OptionListRenderer {

    public interface ImmediateSelectListener {
        void onImmediateSelect(int index, @NonNull OptionItemSpec item);
    }

    private final MaxHeightFrameLayout scrollHost;
    private final RecyclerView recyclerView;
    private final FrameLayout scrollBarTouchSlot;
    private final YoScrollBar scrollBar;
    private final OptionListAdapter adapter;
    private final OptionSelectionStore selectionStore;
    @NonNull
    private final OptionSelectionStore.OnSelectionChangedListener selectionChangedListener;
    @Nullable
    private RecyclerViewScrollTarget scrollTarget;
    @Nullable
    private ViewTreeObserver.OnGlobalLayoutListener layoutListener;

    private OptionListRenderer(@NonNull MaxHeightFrameLayout scrollHost,
                               @NonNull RecyclerView recyclerView,
                               @NonNull FrameLayout scrollBarTouchSlot,
                               @NonNull YoScrollBar scrollBar,
                               @NonNull OptionListAdapter adapter,
                               @NonNull OptionSelectionStore selectionStore,
                               @NonNull OptionSelectionStore.OnSelectionChangedListener selectionChangedListener) {
        this.scrollHost = scrollHost;
        this.recyclerView = recyclerView;
        this.scrollBarTouchSlot = scrollBarTouchSlot;
        this.scrollBar = scrollBar;
        this.adapter = adapter;
        this.selectionStore = selectionStore;
        this.selectionChangedListener = selectionChangedListener;
        selectionStore.addListener(selectionChangedListener);
    }

    @NonNull
    public static OptionListRenderer create(@NonNull Context context,
                                            @NonNull OptionListSpec listSpec,
                                            @NonNull OptionSelectionStore selectionStore,
                                            int dialogCornerRadiusDp,
                                            @Nullable ImmediateSelectListener immediateSelectListener) {
        MaxHeightFrameLayout scrollHost = new MaxHeightFrameLayout(context);
        scrollHost.setClipChildren(false);
        scrollHost.setClipToPadding(false);
        scrollHost.setMaxHeightPx(DialogMetrics.dp(context, listSpec.getMaxHeightDp()));
        scrollHost.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        RecyclerView recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        recyclerView.setClipToPadding(false);
        recyclerView.setClipChildren(false);
        int bottomPadding = DialogMetrics.dp(context, DialogConstants.CONTENT_BOTTOM_MARGIN.getValue());
        int scrollTouchSlotWidth = DialogMetrics.dp(
                context, DialogConstants.OPTION_SCROLLBAR_TOUCH_SLOT_WIDTH.getValue());
        int contentEndPadding = DialogMetrics.dp(
                context, DialogConstants.OPTION_SCROLLBAR_CONTENT_END_PADDING.getValue());
        recyclerView.setPadding(0, 0, contentEndPadding, bottomPadding);

        YoScrollBar scrollBar = new YoScrollBar(context);
        scrollBar.setVisibility(View.GONE);
        FrameLayout scrollBarTouchSlot = new FrameLayout(context);
        scrollBarTouchSlot.setVisibility(View.GONE);
        scrollBarTouchSlot.addView(scrollBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        OptionListAdapter adapter = new OptionListAdapter(
                context,
                listSpec.getItems(),
                listSpec.getSelectionMode(),
                listSpec.getRowStyle(),
                dialogCornerRadiusDp,
                selectionStore,
                (index, item) -> handleRowClick(
                        listSpec.getSelectionMode(),
                        selectionStore,
                        immediateSelectListener,
                        index,
                        item));
        recyclerView.setAdapter(adapter);

        scrollHost.addView(recyclerView, new MaxHeightFrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        MaxHeightFrameLayout.LayoutParams scrollSlotParams = new MaxHeightFrameLayout.LayoutParams(
                scrollTouchSlotWidth,
                ViewGroup.LayoutParams.MATCH_PARENT);
        scrollSlotParams.gravity = Gravity.END;
        scrollHost.addView(scrollBarTouchSlot, scrollSlotParams);

        OptionListRenderer renderer = new OptionListRenderer(
                scrollHost, recyclerView, scrollBarTouchSlot, scrollBar, adapter, selectionStore,
                store -> adapter.refreshSelectionState());
        renderer.registerScrollStateObserver();
        renderer.showEmptyStateIfNeeded(context, listSpec);
        return renderer;
    }

    private static void handleRowClick(@NonNull OptionSelectionMode mode,
                                       @NonNull OptionSelectionStore store,
                                       @Nullable ImmediateSelectListener immediateSelectListener,
                                       int index,
                                       @NonNull OptionItemSpec item) {
        switch (mode) {
            case SINGLE_IMMEDIATE:
                store.select(item.getId());
                if (immediateSelectListener != null) {
                    immediateSelectListener.onImmediateSelect(index, item);
                }
                break;
            case SINGLE_CONFIRM:
                store.select(item.getId());
                break;
            case MULTI_CONFIRM:
                store.toggle(item.getId());
                break;
            default:
                break;
        }
    }

    private void showEmptyStateIfNeeded(@NonNull Context context, @NonNull OptionListSpec listSpec) {
        if (!listSpec.getItems().isEmpty() || listSpec.getEmptyStateText() == null) {
            return;
        }
        TextView emptyView = new TextView(context);
        emptyView.setText(listSpec.getEmptyStateText());
        emptyView.setTextSize(DialogConstants.MESSAGE_TEXT_SIZE.getValue());
        emptyView.setPadding(
                DialogMetrics.dp(context, DialogConstants.OPTION_PADDING_START.getValue()),
                DialogMetrics.dp(context, DialogConstants.CONTENT_BOTTOM_MARGIN.getValue()),
                DialogMetrics.dp(context, DialogConstants.OPTION_PADDING_END.getValue()),
                DialogMetrics.dp(context, DialogConstants.CONTENT_BOTTOM_MARGIN.getValue()));
        scrollHost.addView(emptyView);
        recyclerView.setVisibility(View.GONE);
    }

    private void registerScrollStateObserver() {
        scrollHost.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                updateScrollBarBinding();
                ViewTreeObserver observer = scrollHost.getViewTreeObserver();
                if (layoutListener != null) {
                    observer.removeOnGlobalLayoutListener(layoutListener);
                }
                layoutListener = () -> updateScrollBarBinding();
                observer.addOnGlobalLayoutListener(layoutListener);
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                ViewTreeObserver observer = scrollHost.getViewTreeObserver();
                if (layoutListener != null && observer.isAlive()) {
                    observer.removeOnGlobalLayoutListener(layoutListener);
                }
                layoutListener = null;
            }
        });
    }

    private void updateScrollBarBinding() {
        boolean needScroll = recyclerView.canScrollVertically(1)
                || recyclerView.canScrollVertically(-1);
        if (needScroll) {
            ensureScrollBarBound();
            scrollBarTouchSlot.setVisibility(View.VISIBLE);
            scrollBar.setVisibility(View.VISIBLE);
        } else {
            releaseScrollBar();
            scrollBarTouchSlot.setVisibility(View.GONE);
            scrollBar.setVisibility(View.GONE);
        }
    }

    private void ensureScrollBarBound() {
        if (scrollTarget == null) {
            scrollTarget = new RecyclerViewScrollTarget(recyclerView);
            scrollTarget.attach();
            scrollBar.bindTarget(scrollTarget);
        }
    }

    private void releaseScrollBar() {
        scrollBar.unbind();
        if (scrollTarget != null) {
            scrollTarget.detach();
            scrollTarget = null;
        }
    }

    @NonNull
    public View getView() {
        return scrollHost;
    }

    @NonNull
    public OptionSelectionStore getSelectionStore() {
        return selectionStore;
    }

    public void release() {
        releaseScrollBar();
        selectionStore.removeListener(selectionChangedListener);
    }
}
