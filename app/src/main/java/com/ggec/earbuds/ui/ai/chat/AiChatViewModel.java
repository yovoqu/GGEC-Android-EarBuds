package com.ggec.earbuds.ui.ai.chat;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.chat.AiChatState;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.ListConversations;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SelectModel;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 对话页的 ViewModel。真正的对话状态在应用级的 {@link AiChatSession} 里，它不随界面销毁；
 * 这里只负责把状态翻译成界面状态、保存只属于界面的临时状态（思考块的展开、输入草稿），
 * 并把需要读盘的操作放到后台线程。
 */
public final class AiChatViewModel extends ViewModel {

    private final AiChatSession session;
    private final ListConversations listConversations;
    private final LoadAiSettings loadSettings;
    private final SelectModel selectModel;
    private final AiChatUiMapper mapper;
    private final AiModelSheetMapper sheetMapper;
    private final Executor io;

    private final MutableLiveData<AiChatUiState> uiState = new MutableLiveData<>(AiChatUiState.empty());
    private final MutableLiveData<List<AiHistoryItemUi>> history = new MutableLiveData<>(List.of());
    private final MutableLiveData<AiModelSheetUi> modelSheet = new MutableLiveData<>();
    private final OneShotLiveData<AiChatEvent> events = new OneShotLiveData<>();
    private final Map<String, Boolean> reasoningOverrides = new ConcurrentHashMap<>();
    private final Subscription subscription;

    private volatile AiChatState latest;
    private volatile int knownRevision = -1;
    private volatile boolean historyWatched;
    private String draft = "";

    public AiChatViewModel(
            @NonNull AiChatSession session,
            @NonNull ListConversations listConversations,
            @NonNull LoadAiSettings loadSettings,
            @NonNull SelectModel selectModel,
            @NonNull AiChatUiMapper mapper,
            @NonNull AiModelSheetMapper sheetMapper,
            @NonNull Executor io) {
        this.session = Objects.requireNonNull(session, "session");
        this.listConversations = Objects.requireNonNull(listConversations, "listConversations");
        this.loadSettings = Objects.requireNonNull(loadSettings, "loadSettings");
        this.selectModel = Objects.requireNonNull(selectModel, "selectModel");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sheetMapper = Objects.requireNonNull(sheetMapper, "sheetMapper");
        this.io = Objects.requireNonNull(io, "io");
        this.latest = session.state();
        this.subscription = session.observe(this::onState);
        session.start();
    }

    @NonNull
    public LiveData<AiChatUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<List<AiHistoryItemUi>> history() {
        return history;
    }

    /** 模型选择面板的数据，{@link #prepareModelSheet()} 之后才有值。 */
    @NonNull
    public LiveData<AiModelSheetUi> modelSheet() {
        return modelSheet;
    }

    @NonNull
    public LiveData<AiChatEvent> events() {
        return events;
    }

    @NonNull
    public String draft() {
        return draft;
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

    /** 历史面板打开时调用：读一次列表，并在之后随历史变化自动刷新。 */
    public void watchHistory() {
        historyWatched = true;
        reloadHistory();
    }

    public void unwatchHistory() {
        historyWatched = false;
    }

    public void prepareModelSheet() {
        io.execute(() -> {
            try {
                modelSheet.postValue(sheetMapper.map(loadSettings.execute()));
            } catch (AiFailure failure) {
                events.postValue(AiChatEvent.toast(AiMessages.failure(failure.kind())));
            }
        });
    }

    public void chooseModel(@NonNull String platformId, @NonNull String modelId, @NonNull String displayName) {
        io.execute(() -> {
            try {
                selectModel.execute(platformId, modelId);
                events.postValue(AiChatEvent.toast(R.string.ai_model_switched, displayName));
            } catch (AiValidationException rejected) {
                events.postValue(AiChatEvent.toast(AiMessages.validation(rejected.error())));
            } catch (AiFailure failure) {
                events.postValue(AiChatEvent.toast(AiMessages.failure(failure.kind())));
            }
        });
    }

    private void onState(AiChatState state) {
        latest = state;
        uiState.postValue(mapper.map(state, reasoningOverrides));
        if (state.historyRevision() != knownRevision) {
            knownRevision = state.historyRevision();
            if (historyWatched) {
                reloadHistory();
            }
        }
    }

    private void reloadHistory() {
        io.execute(() -> {
            String currentId = latest.conversation().id();
            List<AiHistoryItemUi> items = new ArrayList<>();
            try {
                for (AiConversationSummary summary : listConversations.execute()) {
                    items.add(new AiHistoryItemUi(summary.id(), summary.title(), summary.preview(),
                            summary.updatedAtMillis(), summary.messageCount(), summary.id().equals(currentId)));
                }
            } catch (AiFailure failure) {
                events.postValue(AiChatEvent.toast(AiMessages.failure(failure.kind())));
                return;
            }
            history.postValue(items);
        });
    }

    @Override
    protected void onCleared() {
        subscription.cancel();
    }
}
