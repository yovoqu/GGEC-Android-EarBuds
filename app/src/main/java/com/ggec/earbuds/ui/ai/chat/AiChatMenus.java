package com.ggec.earbuds.ui.ai.chat;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ggec.earbuds.R;
import com.yohu.youi.api.navigation.YoMenu;

/** 对话页菜单：页头动作与消息长按菜单。 */
final class AiChatMenus {

    private static final int MENU_NEW_CHAT = 1;
    private static final int MENU_SETTINGS = 2;
    private static final int ACTION_COPY = 1;
    private static final int ACTION_REGENERATE = 2;
    private static final int ACTION_DELETE = 3;

    interface Listener {
        void onNewConversation();
        void onSettings();
        void onCopy(@NonNull String text);
        void onRegenerate();
        void onDelete(@NonNull String messageId);
    }

    private final AppCompatActivity activity;
    private final Listener listener;

    AiChatMenus(@NonNull AppCompatActivity activity, @NonNull Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    void showPageMenu(@NonNull View anchor) {
        YoMenu menu = YoMenu.create(activity)
                .addItem(MENU_NEW_CHAT, activity.getString(R.string.ai_menu_new_chat),
                        R.drawable.ic_ai_new_chat)
                .addItem(MENU_SETTINGS, activity.getString(R.string.ai_settings_entry),
                        R.drawable.ic_ai_settings)
                .setAnchorView(anchor)
                .setOnItemClickListener(id -> {
                    if (id == MENU_NEW_CHAT) {
                        listener.onNewConversation();
                    } else if (id == MENU_SETTINGS) {
                        listener.onSettings();
                    }
                })
                .build();
        menu.setOnDismissListener(menu::destroy);
        menu.show();
    }

    void showMessageMenu(@NonNull View anchor, @NonNull AiMessageUi message) {
        YoMenu menu = YoMenu.create(activity)
                .addItem(ACTION_COPY, activity.getString(R.string.ai_copy), R.drawable.ic_ai_copy)
                .setAnchorView(anchor)
                .setPlacement(YoMenu.Placement.BOTTOM_LEFT)
                .setOnItemClickListener(id -> {
                    if (id == ACTION_COPY) {
                        listener.onCopy(message.text());
                    } else if (id == ACTION_REGENERATE) {
                        listener.onRegenerate();
                    } else if (id == ACTION_DELETE) {
                        listener.onDelete(message.id());
                    }
                })
                .build();
        if (message.canRegenerate()) {
            menu.addItem(ACTION_REGENERATE, activity.getString(R.string.ai_action_regenerate),
                    R.drawable.ic_ai_refresh);
        }
        if (message.canDelete()) {
            menu.addDestructiveItem(ACTION_DELETE, activity.getString(R.string.ai_delete));
        }
        menu.setOnDismissListener(menu::destroy);
        menu.show();
    }
}
