package com.yovo.yotheme.components.yo_scrollbar.scrollbar_structure;

/**
 * 滚动条显示策略，对齐 HarmonyOS ArkUI {@code BarState}。
 * <ul>
 *   <li>{@link #AUTO} — 可滚动时按需显示，滚动结束后自动隐藏（List/Grid/Scroll 默认）</li>
 *   <li>{@link #ON} — 可滚动时常驻显示</li>
 *   <li>{@link #OFF} — 永不显示</li>
 * </ul>
 */
public enum BarState {
    AUTO,
    ON,
    OFF
}
