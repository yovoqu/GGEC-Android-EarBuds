package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

import androidx.annotation.NonNull;

/**
 * 可滚动目标抽象，对齐 HarmonyOS {@code Scroller} 与 Scroll 组件的绑定关系。
 */
public interface ScrollTarget {

    /** 当前滚动偏移（主轴方向）。 */
    int getScrollOffset();

    /** 最大可滚动距离。 */
    int getScrollRange();

    /** 视口尺寸（主轴方向）。 */
    int getViewportSize();

    /** 内容尺寸（主轴方向）。 */
    int getContentSize();

    /** 是否可滚动。 */
    default boolean isScrollable() {
        return getContentSize() > getViewportSize() && getScrollRange() > 0;
    }

    void scrollTo(int offset);

    void addOnScrollChangedListener(@NonNull Runnable listener);

    void removeOnScrollChangedListener(@NonNull Runnable listener);

    void addOnContentSizeChangedListener(@NonNull Runnable listener);

    void removeOnContentSizeChangedListener(@NonNull Runnable listener);
}
