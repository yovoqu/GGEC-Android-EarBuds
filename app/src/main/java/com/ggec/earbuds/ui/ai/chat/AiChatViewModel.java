package com.ggec.earbuds.ui.ai.chat;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.chat.AiChatState;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话页的 ViewModel。真正的对话状态在应用级的 {@link AiChatSession} 里，它不随界面销毁；
 * 这里只负责把状态翻译成界面状态、保存只属于界面的临时状态（思考块的展开、输入草稿）。
 *
 * <p>设置页、平台页、历史面板、模型面板各有自己的 ViewModel；这一层只管对话本身，
 * 不读盘、不跑后台任务，也不碰存储——那些由领域层与数据层负责。</p>
 */
public final class AiChatViewModel extends ViewModel {

    private final AiChatSession session;
    private final AiChatUiMapper mapper;

    private final MutableLiveData<AiChatUiState> uiState = new MutableLiveData<>(AiChatUiState.empty());
    private final OneShotLiveData<AiChatEvent> events = new OneShotLiveData<>();
    private final Map<String, Boolean> reasoningOverrides = new ConcurrentHashMap<>();
    private final Subscription subscription;

    private volatile AiChatState latest;
    private String draft = "";

    public AiChatViewModel(@NonNull AiChatSession session, @NonNull AiChatUiMapper mapper) {
        this.session = Objects.requireNonNull(session, "session");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.latest = session.state();
        this.subscription = session.observe(this::onState);
        session.start();
    }

    @NonNull
    public LiveData<AiChatUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<AiChatEvent> events() {
        return events;
    }

    @NonNull
    public String draft() {
        return draft;
    }

    /**
     * 语音交流模式是否已经可用：预设模型选好、Key 已填。为假时通话入口隐藏。
     * 页面只拿这个布尔值，不需要知道用的是哪家模型——那是通话页与会话的事。
     */
    public boolean voiceReady() {
        return latest.voiceReady();
    }

    public void setDraft(@NonNull String draft) {
        this.draft = draft;
    }

    public void send(@NonNull String text) {
        session.send(text);
    }

    public void stop() {
        session.stop();
    }

    public void regenerate() {
        session.regenerate();
    }

    public void deleteMessage(@NonNull String messageId) {
        session.deleteMessage(messageId);
    }

    public void newConversation() {
        draft = "";
        session.newConversation();
    }

    public void openConversation(@NonNull String conversationId) {
        session.open(conversationId);
    }

    public void deleteConversation(@NonNull String conversationId) {
        session.deleteConversation(conversationId);
    }

    public void renameConversation(@NonNull String conversationId, @NonNull String title) {
        session.rename(conversationId, title);
    }

    public void clearHistory() {
        session.clearAll();
    }

    public void toggleReasoning(@NonNull String messageId) {
        AiChatUiState current = uiState.getValue();
        if (current == null) {
            return;
        }
        for (AiMessageUi message : current.messages()) {
            if (message.id().equals(messageId)) {
                reasoningOverrides.put(messageId, !message.reasoningExpanded());
                uiState.setValue(mapper.map(latest, reasoningOverrides));
                return;
            }
        }
    }

    private void onState(AiChatState state) {
        latest = state;
        uiState.postValue(mapper.map(state, reasoningOverrides));
    }

    @Override
    protected void onCleared() {
        subscription.cancel();
    }
}
