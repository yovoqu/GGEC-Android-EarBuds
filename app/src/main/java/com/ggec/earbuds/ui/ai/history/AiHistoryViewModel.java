package com.ggec.earbuds.ui.ai.history;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.chat.AiChatState;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.ListConversations;
import com.yovo.lib_common.event.OneShotLiveData;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 历史面板的 ViewModel：只做两件事——把落盘的会话摘要翻成 {@link AiHistoryItemUi}，
 * 把用户操作原样转交给 {@link AiChatSession}。
 *
 * <p>它不落盘、不改状态：改名/删除/清空全部由会话自己执行并写盘，会话每次写完都会让
 * {@code AiChatState.historyRevision} 递增，这里据此重新读一次列表。读列表走
 * {@link ListConversations}，不直接碰仓库。</p>
 *
 * <p>刷新时机只有两个：第一次打开（{@link #start()}），以及 {@code historyRevision} 变化。
 * 流式输出期间会话会不停发布状态，但 revision 不变，所以面板不会跟着重读磁盘——
 * 那既没必要，也会让正在滑动的列表一直重建。</p>
 *
 * <p>线程用构造时注入的 {@link Executor}：读列表是磁盘操作，不能占主线程；单测传
 * {@code Runnable::run} 就变成同步执行。LiveData 一律 {@code postValue}，因此两种线程上都安全。</p>
 */
public final class AiHistoryViewModel extends ViewModel {

    private final AiChatSession session;
    private final ListConversations listConversations;
    private final Executor io;
    private final AiClock clock;
    private final AiHistoryUiMapper mapper;
    private final ZoneId zone;

    private final MutableLiveData<AiHistoryUiState> uiState =
            new MutableLiveData<>(AiHistoryUiState.from(false, false, List.of()));
    private final OneShotLiveData<AiHistoryEvent> events = new OneShotLiveData<>();
    private final Subscription subscription;

    /** 对话页此刻打开的是哪一条；由会话状态给出，面板不自己猜。 */
    private volatile String currentId = "";
    /** 上次读列表时的 revision；-1 表示还没同步过，第一次变化一定算"变了"。 */
    private volatile int revision = -1;
    private volatile boolean started;
    private volatile boolean closed;

    public AiHistoryViewModel(@NonNull AiChatSession session,
                              @NonNull ListConversations listConversations,
                              @NonNull Executor io,
                              @NonNull AiClock clock,
                              @NonNull AiHistoryUiMapper mapper) {
        this(session, listConversations, io, clock, mapper, ZoneId.systemDefault());
    }

    /** 测试用：时区固定下来，「今天」「昨天」的边界才不会随机器设置漂。 */
    AiHistoryViewModel(@NonNull AiChatSession session,
                       @NonNull ListConversations listConversations,
                       @NonNull Executor io,
                       @NonNull AiClock clock,
                       @NonNull AiHistoryUiMapper mapper,
                       @NonNull ZoneId zone) {
        this.session = Objects.requireNonNull(session, "session");
        this.listConversations = Objects.requireNonNull(listConversations, "listConversations");
        this.io = Objects.requireNonNull(io, "io");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.zone = Objects.requireNonNull(zone, "zone");
        // 订阅放在最后：observe 会立刻投递一次当前状态，那时上面的字段必须已经就位。
        this.subscription = session.observe(this::onState);
    }

    @NonNull
    public LiveData<AiHistoryUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<AiHistoryEvent> events() {
        return events;
    }

    /** 打开面板时调一次。幂等：重复调用不会再读一次盘。 */
    public void start() {
        if (started || closed) {
            return;
        }
        started = true;
        refresh();
    }

    /** 重新读一次列表。除了 {@link #start()} 与会话的 revision 变化，别处不需要调用。 */
    public void refresh() {
        if (closed) {
            return;
        }
        io.execute(() -> {
            if (closed) {
                return;
            }
            List<AiConversationSummary> summaries;
            try {
                summaries = listConversations.execute();
            } catch (RuntimeException unreadable) {
                // 读不出来时保留上一次的行。列表本来就是空的时候不弹 toast：
                // 面板会就地写「没能读取对话记录」+「重试」，状态由界面承载，不由 toast 承载。
                AiHistoryUiState current = uiState.getValue();
                publishState(AiHistoryUiState.from(
                        true, true, current == null ? List.of() : current.items()));
                if (!itemsAreEmpty()) {
                    events.postValue(AiHistoryEvent.toast(R.string.ai_history_load_failed));
                }
                return;
            }
            publishState(AiHistoryUiState.from(
                    true, false, mapper.map(summaries, currentId, clock.now(), zone)));
        });
    }

    // ---- 会话操作：全部交给会话，它自己落盘 ----

    public void newChat() {
        if (closed) {
            return;
        }
        session.newConversation();
    }

    public void open(@NonNull String conversationId) {
        if (closed) {
            return;
        }
        session.open(conversationId);
    }

    /**
     * 改名。空标题在这里就挡下：会话会把空串当成"没改名"静默返回，
     * 用户按了保存却什么都没发生，比报一句错更糟。
     */
    public void rename(@NonNull String conversationId, @Nullable String title) {
        if (closed) {
            return;
        }
        String next = title == null ? "" : title.strip();
        if (next.isEmpty()) {
            events.postValue(AiHistoryEvent.toast(R.string.ai_history_rename_empty));
            return;
        }
        session.rename(conversationId, next);
    }

    public void delete(@NonNull String conversationId) {
        if (closed) {
            return;
        }
        session.deleteConversation(conversationId);
    }

    public void clearAll() {
        if (closed) {
            return;
        }
        session.clearAll();
    }

    /** 面板关闭时调；之后所有操作都变成空操作，晚到的磁盘结果也不会再发出去。 */
    @Override
    protected void onCleared() {
        release();
    }

    /** 面板关闭时释放订阅。幂等，并与 {@link #onCleared()} 共用同一释放路径。 */
    public void close() {
        release();
    }

    // ---- 内部 ----

    private void onState(@NonNull AiChatState state) {
        if (closed) {
            return;
        }
        String nextId = state.conversation().id();
        int nextRevision = state.historyRevision();
        boolean changed = nextRevision != revision || !nextId.equals(currentId);
        revision = nextRevision;
        currentId = nextId;
        if (started && changed) {
            refresh();
        }
    }

    private boolean itemsAreEmpty() {
        AiHistoryUiState current = uiState.getValue();
        return current == null || current.items().isEmpty();
    }

    private void publishState(@NonNull AiHistoryUiState next) {
        if (Objects.equals(uiState.getValue(), next)) {
            return;
        }
        uiState.postValue(next);
    }

    private void release() {
        if (closed) {
            return;
        }
        closed = true;
        subscription.cancel();
    }
}
