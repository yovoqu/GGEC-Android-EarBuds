package com.ggec.earbuds.ui.ai.chat;

import android.view.View;

import androidx.annotation.NonNull;

import com.ggec.earbuds.R;
import com.ggec.earbuds.ui.ai.AiTheme;
import com.ggec.earbuds.ui.ai.common.AiTexts;
import com.yohu.youi.api.containers.YoList;
import com.yohu.youi.api.containers.YoSheet;
import com.yohu.youi.api.containers.list.ListPrefixSpec;
import com.yohu.youi.api.containers.list.ListSuffixSpec;
import com.yohu.youi.api.containers.list.ListTextSpec;
import com.yohu.youi.api.controls.YoButton;

/**
 * 历史对话面板：列出所有对话，点一下回到那段对话，右侧菜单重命名或删除，右上角一键清空。
 * 面板内容用 {@link YoList} 的默认行，“更多”和“清空”用默认 {@link YoButton}。
 */
final class AiHistorySheet {

    interface Listener {
        void onNewChat();

        void onOpen(@NonNull AiHistoryItemUi item);

        void onMore(@NonNull View anchor, @NonNull AiHistoryItemUi item);

        void onClearAll();

        void onClosed();
    }

    private final AiTheme theme;
    private final Listener listener;
    private final YoButton clear;
    private final AiListSheet sheet;

    AiHistorySheet(@NonNull AiTheme theme, @NonNull Listener listener) {
        this.theme = theme;
        this.listener = listener;
        clear = YoButton.create(theme.context)
                .setIcon(R.drawable.ic_ai_delete)
                .setStyle(YoButton.Style.TEXT)
                .setShape(YoButton.Shape.CIRCULAR)
                .setRole(YoButton.Role.WARNING)
                .setSize(YoButton.Size.SMALL)
                .setOnClickListener(view -> listener.onClearAll())
                .build();
        clear.getView().setContentDescription(theme.context.getString(R.string.ai_history_clear));
        clear.getView().setVisibility(View.GONE);
        sheet = new AiListSheet(theme, theme.context.getString(R.string.ai_history_title),
                YoSheet.Height.large(), clear.getView(), listener::onClosed);
    }

    void show() {
        sheet.show();
    }

    void dismiss() {
        sheet.dismiss();
    }

    boolean isShowing() {
        return sheet.isShowing();
    }

    void destroy() {
        sheet.destroy();
        clear.destroy();
    }

    void update(@NonNull java.util.List<AiHistoryItemUi> items) {
        sheet.resetRows();

        sheet.addItem(ListPrefixSpec.icon(R.drawable.ic_ai_new_chat),
                ListTextSpec.of(theme.context.getString(R.string.ai_history_new)),
                ListSuffixSpec.none(), listener::onNewChat);

        if (items.isEmpty()) {
            sheet.addGroupFooter(theme.context.getString(R.string.ai_history_empty));
        }
        for (AiHistoryItemUi item : items) {
            sheet.addItem(ListPrefixSpec.icon(R.drawable.ic_ai_chat), text(item), moreSuffix(item),
                    () -> listener.onOpen(item));
        }
        clear.getView().setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private ListTextSpec text(@NonNull AiHistoryItemUi item) {
        String title = item.title().isBlank()
                ? theme.context.getString(R.string.ai_history_untitled) : item.title();
        String meta = theme.context.getString(R.string.ai_history_item_meta,
                AiTexts.relativeTime(theme.context, System.currentTimeMillis(), item.updatedAtMillis()),
                item.messageCount());
        if (item.current()) {
            meta = meta + " · " + theme.context.getString(R.string.ai_history_current);
        }
        return item.preview().isBlank()
                ? ListTextSpec.of(title, null, meta)
                : ListTextSpec.of(title, item.preview(), meta);
    }

    private ListSuffixSpec moreSuffix(@NonNull AiHistoryItemUi item) {
        YoButton more = YoButton.create(theme.context)
                .setIcon(R.drawable.ic_ai_more)
                .setStyle(YoButton.Style.TEXT)
                .setShape(YoButton.Shape.CIRCULAR)
                .setSize(YoButton.Size.SMALL)
                .setOnClickListener(view -> listener.onMore(view, item))
                .build();
        more.getView().setContentDescription(theme.context.getString(R.string.ai_rename));
        sheet.ownRowComponent(more::destroy);
        return ListSuffixSpec.custom(more.getView());
    }
}
