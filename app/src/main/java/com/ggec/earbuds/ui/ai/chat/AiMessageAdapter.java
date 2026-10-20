package com.ggec.earbuds.ui.ai.chat;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.ggec.earbuds.ui.ai.AiTheme;

/**
 * 消息列表。条目靠 id 判断是不是同一条，靠记录的值相等判断画面有没有变，
 * 所以流式输出时只有正在生成的那一条会重新绑定。
 */
final class AiMessageAdapter extends ListAdapter<AiMessageUi, RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 1;
    private static final int TYPE_ASSISTANT = 2;

    private final AiTheme theme;
    private final AiMessageActions actions;

    AiMessageAdapter(@NonNull AiTheme theme, @NonNull AiMessageActions actions) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull AiMessageUi oldItem, @NonNull AiMessageUi newItem) {
                return oldItem.id().equals(newItem.id());
            }

            @Override
            public boolean areContentsTheSame(@NonNull AiMessageUi oldItem, @NonNull AiMessageUi newItem) {
                return oldItem.equals(newItem);
            }
        });
        this.theme = theme;
        this.actions = actions;
        setHasStableIds(false);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).fromUser() ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_USER) {
            return new UserHolder(new AiUserMessageView(theme, actions));
        }
        return new AssistantHolder(new AiAssistantMessageView(theme, actions));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AiMessageUi message = getItem(position);
        if (holder instanceof UserHolder user) {
            user.view.bind(message);
        } else if (holder instanceof AssistantHolder assistant) {
            assistant.view.bind(message);
        }
    }

    private static final class UserHolder extends RecyclerView.ViewHolder {
        final AiUserMessageView view;

        UserHolder(AiUserMessageView view) {
            super(view);
            this.view = view;
        }
    }

    private static final class AssistantHolder extends RecyclerView.ViewHolder {
        final AiAssistantMessageView view;

        AssistantHolder(AiAssistantMessageView view) {
            super(view);
            this.view = view;
        }
    }
}
