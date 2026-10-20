package com.ggec.earbuds.domain.ai.chat;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.policy.AiContextPolicy;
import com.ggec.earbuds.domain.ai.policy.AiMessageRecovery;
import com.ggec.earbuds.domain.ai.policy.AiTitlePolicy;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiChatGateway;
import com.ggec.earbuds.domain.ai.port.AiChatListener;
import com.ggec.earbuds.domain.ai.port.AiChatRequest;
import com.ggec.earbuds.domain.ai.port.AiChatResult;
import com.ggec.earbuds.domain.ai.port.AiChatTurn;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;
import com.ggec.earbuds.domain.ai.port.AiIdGenerator;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * 当前对话的状态机：收发消息、流式拼接、停止、重试、切换与删除。
 *
 * <p>所有命令都投递到同一个串行执行器上，状态只在那里修改；网关的回调线程不做保证，
 * 回调也先投回执行器再处理。每次变化发布一个不可变的 {@link AiChatState}。
 * 会话由装配根持有为应用级单例，所以生成过程不依赖界面是否存活。</p>
 */
public final class AiChatSession {

    public interface Observer {
        void onState(AiChatState state);
    }

    private final AiSettingsRepository settings;
    private final AiConversationRepository conversations;
    private final AiChatGateway gateway;
    private final AiClock clock;
    private final AiIdGenerator ids;
    private final Executor executor;
    private final AiContextPolicy contextPolicy = new AiContextPolicy();
    private final AiTitlePolicy titlePolicy = new AiTitlePolicy();
    private final AiMessageRecovery recovery = new AiMessageRecovery();
    private final CopyOnWriteArrayList<Observer> observers = new CopyOnWriteArrayList<>();

    private volatile AiChatState state;

    // 以下字段只在执行器线程上读写。
    private boolean started;
    private AiConversation conversation;
    private AiSettings snapshot = AiSettings.EMPTY;
    private boolean storageFailed;
    private int historyRevision;
    private long generation;
    private long activeToken;
    private long runStartedAt;
    @Nullable
    private AiCancellable running;

    public AiChatSession(
            AiSettingsRepository settings,
            AiConversationRepository conversations,
            AiChatGateway gateway,
            AiClock clock,
            AiIdGenerator ids,
            Executor executor) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.conversation = AiConversation.empty(ids.next(), clock.now());
        this.state = AiChatState.initial(conversation);
    }

    public AiChatState state() {
        return state;
    }

    /**
     * 订阅状态变化，订阅后会马上收到一次当前状态。回调在会话的执行器线程上，要尽快返回。
     */
    public Subscription observe(Observer observer) {
        Objects.requireNonNull(observer, "observer");
        observers.add(observer);
        executor.execute(() -> notifyOne(observer, state));
        return () -> observers.remove(observer);
    }

    /** 读取设置和最近一次对话。可重复调用，只有第一次生效。 */
    public void start() {
        executor.execute(this::ensureStarted);
    }

    public void send(String text) {
        executor.execute(() -> doSend(text));
    }

    public void stop() {
        executor.execute(this::doStop);
    }

    /** 重新生成最后一条助手回复；最后一条是用户消息时（例如上次中途被杀）直接补发。 */
    public void regenerate() {
        executor.execute(this::doRegenerate);
    }

    public void deleteMessage(String messageId) {
        executor.execute(() -> doDeleteMessage(messageId));
    }

    public void newConversation() {
        executor.execute(() -> {
            ensureStarted();
            cancelRun();
            persist();
            conversation = AiConversation.empty(ids.next(), clock.now());
            publish();
        });
    }

    public void open(String conversationId) {
        executor.execute(() -> doOpen(conversationId));
    }

    public void deleteConversation(String conversationId) {
        executor.execute(() -> {
            ensureStarted();
            boolean current = conversation.id().equals(conversationId);
            if (current) {
                cancelRun();
            }
            guarded(() -> conversations.delete(conversationId));
            if (current) {
                conversation = AiConversation.empty(ids.next(), clock.now());
            }
            publish();
        });
    }

    public void clearAll() {
        executor.execute(() -> {
            ensureStarted();
            cancelRun();
            guarded(conversations::deleteAll);
            conversation = AiConversation.empty(ids.next(), clock.now());
            publish();
        });
    }

    public void rename(String conversationId, String title) {
        executor.execute(() -> doRename(conversationId, title));
    }

    private void ensureStarted() {
        if (started) {
            return;
        }
        started = true;
        try {
            snapshot = settings.load();
        } catch (RuntimeException unreadable) {
            storageFailed = true;
        }
        settings.observe(next -> executor.execute(() -> {
            snapshot = next;
            publish();
        }));
        List<AiConversationSummary> summaries = List.of();
        try {
            summaries = conversations.summaries();
        } catch (RuntimeException unreadable) {
            storageFailed = true;
        }
        if (!summaries.isEmpty()) {
            loadConversation(summaries.get(0).id());
        }
        publish();
    }

    private void loadConversation(String id) {
        try {
            Optional<AiConversation> found = conversations.find(id);
            if (found.isPresent()) {
                AiConversation recovered = recovery.recover(found.get());
                conversation = recovered;
                if (recovered != found.get()) {
                    persist();
                }
            }
        } catch (RuntimeException unreadable) {
            storageFailed = true;
        }
    }

    private void doSend(String raw) {
        ensureStarted();
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty() || activeToken != 0) {
            return;
        }
        snapshot = settings.load();
        Optional<AiActiveModel> active = snapshot.activeModel();
        if (active.isEmpty()) {
            publish();
            return;
        }
        long now = clock.now();
        List<AiMessage> messages = new ArrayList<>(conversation.messages());
        messages.add(AiMessage.user(ids.next(), text, now));
        messages.add(AiMessage.assistantPlaceholder(ids.next(), active.get().model().id(), now));
        conversation = conversation.withMessages(messages, now);
        if (conversation.untitled()) {
            conversation = conversation.withTitle(titlePolicy.titleFor(text));
        }
        persist();
        beginRun(active.get());
    }

    private void doRegenerate() {
        ensureStarted();
        if (activeToken != 0) {
            return;
        }
        List<AiMessage> messages = new ArrayList<>(conversation.messages());
        if (!messages.isEmpty() && !messages.get(messages.size() - 1).user()) {
            messages.remove(messages.size() - 1);
        }
        if (messages.isEmpty() || !messages.get(messages.size() - 1).user()) {
            return;
        }
        snapshot = settings.load();
        Optional<AiActiveModel> active = snapshot.activeModel();
        if (active.isEmpty()) {
            publish();
            return;
        }
        long now = clock.now();
        messages.add(AiMessage.assistantPlaceholder(ids.next(), active.get().model().id(), now));
        conversation = conversation.withMessages(messages, now);
        persist();
        beginRun(active.get());
    }

    private void beginRun(AiActiveModel active) {
        AiChatPreferences preferences = snapshot.preferences();
        List<AiMessage> all = conversation.messages();
        String messageId = all.get(all.size() - 1).id();
        AiChatRequest request;
        try {
            List<AiChatTurn> turns = contextPolicy.window(all.subList(0, all.size() - 1),
                    preferences.contextMessages());
            String system = preferences.systemPrompt().isBlank() ? null : preferences.systemPrompt();
            request = new AiChatRequest(active.platform(), active.model().id(), system, turns,
                    preferences.temperature(), preferences.maxOutputTokens());
        } catch (IllegalStateException unusable) {
            failRun(messageId, AiFailureKind.UNKNOWN, unusable.getMessage());
            return;
        }
        long token = ++generation;
        activeToken = token;
        runStartedAt = clock.now();
        AiCancellable handle;
        try {
            handle = gateway.stream(request, new AiChatListener() {
                @Override
                public void onReasoning(String delta) {
                    executor.execute(() -> onDelta(token, messageId, delta, true));
                }

                @Override
                public void onText(String delta) {
                    executor.execute(() -> onDelta(token, messageId, delta, false));
                }

                @Override
                public void onComplete(AiChatResult result) {
                    executor.execute(() -> onFinished(token, messageId, result));
                }

                @Override
                public void onFailure(AiFailure failure) {
                    executor.execute(() -> onFailed(token, messageId, failure));
                }
            });
        } catch (AiFailure failure) {
            activeToken = 0;
            failRun(messageId, failure.kind(), failure.getMessage());
            return;
        } catch (RuntimeException unexpected) {
            activeToken = 0;
            failRun(messageId, AiFailureKind.UNKNOWN, unexpected.getMessage());
            return;
        }
        if (activeToken == token) {
            running = handle;
        }
        publish();
    }

    private void failRun(String messageId, AiFailureKind kind, @Nullable String detail) {
        replace(messageId, message -> message.fail(kind, detail, 0L));
        persist();
        publish();
    }

    private void onDelta(long token, String messageId, String delta, boolean reasoning) {
        if (token != activeToken || delta.isEmpty()) {
            return;
        }
        replace(messageId, message -> reasoning ? message.appendReasoning(delta) : message.appendText(delta));
        publish();
    }

    private void onFinished(long token, String messageId, AiChatResult result) {
        if (token != activeToken) {
            return;
        }
        activeToken = 0;
        running = null;
        long elapsed = clock.now() - runStartedAt;
        replace(messageId, message -> {
            AiMessage merged = message;
            if (merged.text().isBlank() && !result.text().isBlank()) {
                merged = merged.withText(result.text());
            }
            if (merged.reasoning() == null && result.reasoning() != null) {
                merged = merged.appendReasoning(result.reasoning());
            }
            return merged.complete(result.finishReason(), result.usage(), elapsed);
        });
        touch();
        persist();
        publish();
    }

    private void onFailed(long token, String messageId, AiFailure failure) {
        if (token != activeToken) {
            return;
        }
        activeToken = 0;
        running = null;
        long elapsed = clock.now() - runStartedAt;
        replace(messageId, message -> message.fail(failure.kind(), failure.getMessage(), elapsed));
        touch();
        persist();
        publish();
    }

    private void doStop() {
        if (activeToken == 0) {
            return;
        }
        cancelRun();
        persist();
        publish();
    }

    /**
     * 中断进行中的生成并收尾那条助手消息：已经收到内容的标为"已停止"，一个字都没收到的直接移除。
     */
    private void cancelRun() {
        if (activeToken == 0) {
            return;
        }
        activeToken = 0;
        AiCancellable handle = running;
        running = null;
        if (handle != null) {
            handle.cancel();
        }
        long elapsed = clock.now() - runStartedAt;
        List<AiMessage> next = new ArrayList<>();
        for (AiMessage message : conversation.messages()) {
            if (!message.streaming()) {
                next.add(message);
            } else if (!message.blank()) {
                next.add(message.stop(elapsed));
            }
        }
        conversation = conversation.withMessages(next, clock.now());
    }

    private void doDeleteMessage(String messageId) {
        ensureStarted();
        if (activeToken != 0) {
            return;
        }
        List<AiMessage> messages = new ArrayList<>(conversation.messages());
        int index = indexOf(messages, messageId);
        if (index < 0) {
            return;
        }
        boolean userMessage = messages.get(index).user();
        messages.remove(index);
        if (userMessage && index < messages.size() && !messages.get(index).user()) {
            messages.remove(index);
        }
        conversation = conversation.withMessages(messages, clock.now());
        persist();
        publish();
    }

    private void doOpen(String conversationId) {
        ensureStarted();
        if (conversation.id().equals(conversationId)) {
            return;
        }
        cancelRun();
        persist();
        loadConversation(conversationId);
        publish();
    }

    private void doRename(String conversationId, String typed) {
        ensureStarted();
        String title = titlePolicy.renamed(typed);
        if (title.isEmpty()) {
            return;
        }
        if (conversation.id().equals(conversationId)) {
            conversation = conversation.withTitle(title);
            persist();
            publish();
            return;
        }
        historyRevision++;
        try {
            Optional<AiConversation> found = conversations.find(conversationId);
            if (found.isPresent()) {
                conversations.save(found.get().withTitle(title));
            }
        } catch (RuntimeException unwritable) {
            storageFailed = true;
        }
        publish();
    }

    private void replace(String messageId, UnaryOperator<AiMessage> change) {
        for (AiMessage message : conversation.messages()) {
            if (message.id().equals(messageId)) {
                conversation = conversation.withMessageReplaced(change.apply(message));
                return;
            }
        }
    }

    private void touch() {
        conversation = conversation.withMessages(conversation.messages(), clock.now());
    }

    private static int indexOf(List<AiMessage> messages, String id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void persist() {
        historyRevision++;
        try {
            if (conversation.empty()) {
                conversations.delete(conversation.id());
            } else {
                conversations.save(conversation);
            }
            storageFailed = false;
        } catch (RuntimeException unwritable) {
            storageFailed = true;
        }
    }

    private void guarded(Runnable action) {
        historyRevision++;
        try {
            action.run();
        } catch (RuntimeException unwritable) {
            storageFailed = true;
        }
    }

    private void publish() {
        Optional<AiActiveModel> active = snapshot.activeModel();
        AiChatState.Readiness readiness = active.isPresent()
                ? AiChatState.Readiness.READY
                : snapshot.hasConfiguredPlatform() ? AiChatState.Readiness.NO_MODEL : AiChatState.Readiness.NO_PLATFORM;
        AiChatState next = new AiChatState(started, conversation, activeToken != 0, readiness,
                active.orElse(null), snapshot.preferences(), storageFailed, historyRevision);
        state = next;
        for (Observer observer : observers) {
            notifyOne(observer, next);
        }
    }

    private static void notifyOne(Observer observer, AiChatState next) {
        try {
            observer.onState(next);
        } catch (RuntimeException ignored) {
            // 一个观察者出错不能拖垮会话，也不能让其他观察者收不到状态。
        }
    }
}
