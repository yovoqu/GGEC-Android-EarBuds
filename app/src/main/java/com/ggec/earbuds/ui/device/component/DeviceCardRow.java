package com.ggec.earbuds.ui.device.component;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.ggec.earbuds.ui.device.DeviceTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * 公共卡片排布（一行一列 / 一行两列）：把若干卡片按宽度排成版块内容。
 *
 * <p>整列卡独占一行；半列卡两两并排，奇数尾张占半列、右侧留白。行距 / 列距统一取
 * {@link DeviceInsets#gap}。</p>
 */
public final class DeviceCardRow {

    /** 一个待排布项：卡片视图 + 是否半列。 */
    public record Item(@NonNull View card, boolean half) {
    }

    private DeviceCardRow() {
    }

    @NonNull
    public static View build(@NonNull Context context, @NonNull DeviceTheme theme,
                             @NonNull List<Item> items) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);

        List<View> halfBuffer = new ArrayList<>(2);
        for (Item item : items) {
            if (item.half()) {
                halfBuffer.add(item.card());
                if (halfBuffer.size() == 2) {
                    column.addView(halfRow(context, theme, halfBuffer), rowParams(theme, column));
                    halfBuffer.clear();
                }
            } else {
                flush(column, context, theme, halfBuffer);
                column.addView(item.card(), params(theme, column, false));
            }
        }
        flush(column, context, theme, halfBuffer);
        return column;
    }

    private static void flush(@NonNull LinearLayout column, @NonNull Context context,
                              @NonNull DeviceTheme theme, @NonNull List<View> halfBuffer) {
        if (halfBuffer.isEmpty()) {
            return;
        }
        column.addView(halfRow(context, theme, halfBuffer), rowParams(theme, column));
        halfBuffer.clear();
    }

    @NonNull
    private static View halfRow(@NonNull Context context, @NonNull DeviceTheme theme,
                                @NonNull List<View> cards) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < cards.size(); index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (index < cards.size() - 1) {
                params.rightMargin = DeviceInsets.gap(theme);
            }
            row.addView(cards.get(index), params);
        }
        if (cards.size() == 1) {
            row.addView(new View(context), new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return row;
    }

    private static LinearLayout.LayoutParams params(@NonNull DeviceTheme theme,
                                                     @NonNull LinearLayout column,
                                                     boolean wrapWidth) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                wrapWidth ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (column.getChildCount() > 0) {
            p.topMargin = DeviceInsets.gap(theme);
        }
        return p;
    }

    private static LinearLayout.LayoutParams rowParams(@NonNull DeviceTheme theme,
                                                       @NonNull LinearLayout column) {
        return params(theme, column, false);
    }
}
