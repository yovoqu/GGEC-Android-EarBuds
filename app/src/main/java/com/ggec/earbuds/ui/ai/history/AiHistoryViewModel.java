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

    private final MutableLiveData<List<AiHistoryItemUi>> items = new MutableLiveData<>(List.of());
    private final MutableLiveData<Boolean> loaded = new MutableLiveData<>(Boolean.FALSE);
    /** 这一轮读取失败了吗。首次失败时列表是空的，界面必须说"没读到"而不是"没有对话"。 */
    private final MutableLiveData<Boolean> loadFailed = new MutableLiveData<>(Boolean.FALSE);
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
    public LiveData<List<AiHistoryItemUi>> items() {
        return items;
    }

    /** 读取失败：界面据此把空态换成"没读到"+ 重试。 */
    @NonNull
    public LiveData<Boolean> loadFailed() {
        return loadFailed;
    }

    /** 第一次读完盘之前不要画空态：「还没有对话」和「还没读到」是两件事。 */
    @NonNull
    public LiveData<Boolean> loaded() {
        return loaded;
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
                loadFailed.postValue(Boolean.TRUE);
                if (!itemsAreEmpty()) {
                    events.postValue(AiHistoryEvent.toast(R.string.ai_history_load_failed));
                }
                postLoadedOnce();
                return;
            }
            loadFailed.postValue(Boolean.FALSE);
            items.postValue(mapper.map(summaries, currentId, clock.now(), zone));
            postLoadedOnce();
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
    public void destroy() {
        release();
    }

    @Override
    protected void onCleared() {
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

    /** 只在第一次成功读完时把 loaded 翻成 true；之后重复置位没有意义，也会多触发一次渲染。 */
    private void postLoadedOnce() {
        if (!Boolean.TRUE.equals(loaded.getValue())) {
            loaded.postValue(Boolean.TRUE);
        }
    }

    /** 当前列表里有没有行。读 {@link LiveData#getValue()} 是安全的：它只是一次字段读取。 */
    private boolean itemsAreEmpty() {
        List<AiHistoryItemUi> current = items.getValue();
        return current == null || current.isEmpty();
    }

    private void release() {
        if (closed) {
            return;
        }
        closed = true;
        subscription.cancel();
    }
}
