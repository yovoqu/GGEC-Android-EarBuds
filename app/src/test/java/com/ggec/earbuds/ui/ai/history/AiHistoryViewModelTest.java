package com.ggec.earbuds.ui.ai.history;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;
import com.ggec.earbuds.domain.ai.usecase.ListConversations;
import com.ggec.earbuds.domain.ai.usecase.RecordUsage;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemoryConversationRepository;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.MemoryUsageStore;
import com.ggec.earbuds.testing.ai.ScriptedChatGateway;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 历史面板的 ViewModel：读列表走用例，改列表走会话，刷新只看 {@code historyRevision}。
 *
 * <p>线程全部是 {@code Runnable::run}，所以每一步都是同步的；时区固定下来，
 * 「今天」「昨天」的断言才不会随机器漂。</p>
 */
public class AiHistoryViewModelTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Rule
    public final InstantTaskExecutorRule tasks = new InstantTaskExecutorRule();

    private final MemorySettingsRepository settings = new MemorySettingsRepository(AiFixtures.readySettings());
    private final MemoryConversationRepository conversations = new MemoryConversationRepository();
    private final MemoryUsageStore usageStore = new MemoryUsageStore();
    private final ScriptedChatGateway gateway = new ScriptedChatGateway();
    private final ManualClock clock = new ManualClock();
    private final List<AiHistoryEvent> events = new ArrayList<>();

    private AiChatSession session;
    private AiHistoryViewModel viewModel;

    @Before
    public void setUp() {
        clock.now = at(2026, 10, 22, 15, 0);
        session = new AiChatSession(settings, conversations, gateway, clock, clock,
                new RecordUsage(usageStore, clock), Runnable::run);
        seed("c1", "耳机连不上", clock.now - 2 * 60_000L, "耳机连不上手机怎么办？");
        seed("c2", "", clock.now - 3 * 24 * 60 * 60_000L, "怎么延长耳机的续航时间？");
        viewModel = new AiHistoryViewModel(session, new ListConversations(conversations),
                Runnable::run, clock, new AiHistoryUiMapper(), ZONE);
        viewModel.events().observeForever(events::add);
        viewModel.start();
    }

    @Test
    public void startShowsWhatIsOnDisk() {
        List<AiHistoryItemUi> rows = viewModel.items().getValue();

        assertTrue(viewModel.loaded().getValue());
        assertEquals(2, rows.size());
        assertEquals("c1", rows.get(0).id());
        assertEquals(AiHistoryText.of(R.string.ai_history_title_untitled), rows.get(1).title());
        assertEquals(AiHistoryText.raw("怎么延长耳机的续航时间？"), rows.get(1).preview());
    }

    @Test
    public void startingTwiceDoesNotReadTwice() {
        CountingRepository counting = new CountingRepository(conversations);
        AiHistoryViewModel other = new AiHistoryViewModel(session, new ListConversations(counting),
                Runnable::run, clock, new AiHistoryUiMapper(), ZONE);

        other.start();
        other.start();

        assertEquals(1, counting.reads);
        other.destroy();
    }

    @Test
    public void renamingWritesThroughTheSessionAndRefreshes() {
        viewModel.rename("c2", "  续航问题  ");

        assertEquals("续航问题", conversations.stored.get("c2").title());
        assertEquals(AiHistoryText.raw("续航问题"), viewModel.items().getValue().get(1).title());
    }

    /** 空标题由面板挡下并提示：会话会把空串当成"没改名"静默返回，报一句错比什么都不发生好。 */
    @Test
    public void aBlankRenameOnlyReports() {
        viewModel.rename("c2", "   ");

        assertEquals("", conversations.stored.get("c2").title());
        assertEquals(1, events.size());
        assertEquals(AiHistoryEvent.toast(R.string.ai_history_rename_empty), events.get(0));
    }

    @Test
    public void deletingDropsTheRowAndTheDocument() {
        viewModel.delete("c1");

        assertFalse(conversations.stored.containsKey("c1"));
        List<AiHistoryItemUi> rows = viewModel.items().getValue();
        assertEquals(1, rows.size());
        assertEquals("c2", rows.get(0).id());
    }

    @Test
    public void clearingEverythingLeavesAnEmptyList() {
        viewModel.clearAll();

        assertTrue(conversations.stored.isEmpty());
        assertTrue(viewModel.items().getValue().isEmpty());
        assertTrue(viewModel.loaded().getValue());
    }

    @Test
    public void openingMarksThatConversation() {
        viewModel.open("c2");

        List<AiHistoryItemUi> rows = viewModel.items().getValue();
        assertEquals("c2", rows.get(1).id());
        assertTrue(rows.get(1).current());
        assertFalse(rows.get(0).current());
    }

    /** 会话从别处被改动（例如对话页那边改名）时，也要靠 revision 把列表刷新出来。 */
    @Test
    public void aChangeFromOutsideRefreshesTheList() {
        session.rename("c2", "外部改名");

        assertEquals(AiHistoryText.raw("外部改名"), viewModel.items().getValue().get(1).title());
    }

    @Test
    public void destroyedPanelsStopFollowingTheSession() {
        viewModel.destroy();
        session.rename("c2", "改名之后");

        assertEquals(AiHistoryText.of(R.string.ai_history_title_untitled), viewModel.items().getValue().get(1).title());
    }

    /**
     * 首次读取失败：列表本来就是空的，界面就地写「没能读取对话记录 + 重试」，
     * 不再弹 toast——状态由界面承载（页面规格 §7）。
     */
    @Test
    public void aFirstLoadFailureIsAStateNotAToast() {
        AiHistoryViewModel broken = new AiHistoryViewModel(session,
                new ListConversations(new UnreadableRepository()), Runnable::run, clock,
                new AiHistoryUiMapper(), ZONE);
        List<AiHistoryEvent> captured = new ArrayList<>();
        broken.events().observeForever(captured::add);

        broken.start();

        assertTrue(captured.isEmpty());
        assertTrue(broken.items().getValue().isEmpty());
        assertTrue(broken.loaded().getValue());
        // 界面靠这个信号把空态换成"没能读取对话记录 + 重试"，而不是"还没有对话"。
        assertEquals(Boolean.TRUE, broken.loadFailed().getValue());
        broken.destroy();
    }

    /** 已有内容时刷新失败：旧行必须留着，只弹一次原因——此时没有别的可见反馈。 */
    @Test
    public void aLaterFailureKeepsTheRowsAndTostsOnce() {
        FlakyRepository flaky = new FlakyRepository(conversations);
        AiHistoryViewModel panel = new AiHistoryViewModel(session, new ListConversations(flaky),
                Runnable::run, clock, new AiHistoryUiMapper(), ZONE);
        List<AiHistoryEvent> captured = new ArrayList<>();
        panel.events().observeForever(captured::add);
        panel.start();
        assertEquals(2, panel.items().getValue().size());

        flaky.fail = true;
        panel.refresh();

        assertEquals(2, panel.items().getValue().size());
        assertEquals(1, captured.size());
        assertEquals(AiHistoryEvent.toast(R.string.ai_history_load_failed), captured.get(0));
        assertEquals(Boolean.TRUE, panel.loadFailed().getValue());
        panel.destroy();
    }

    // ---- 夹具 ----

    private void seed(String id, String title, long updatedAt, String lastText) {
        conversations.stored.put(id, new AiConversation(id, title, updatedAt, updatedAt,
                List.of(AiMessage.user("m-" + id, lastText, updatedAt))));
    }

    private static long at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant().toEpochMilli();
    }

    /** 数一数真正读了几次盘：{@code start()} 幂等这件事只能这么验。 */
    private static final class CountingRepository implements AiConversationRepository {

        private final MemoryConversationRepository delegate;
        private int reads;

        private CountingRepository(MemoryConversationRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<AiConversationSummary> summaries() {
            reads++;
            return delegate.summaries();
        }

        @Override
        public Optional<AiConversation> find(String id) {
            return delegate.find(id);
        }

        @Override
        public void save(AiConversation conversation) {
            delegate.save(conversation);
        }

        @Override
        public void delete(String id) {
            delegate.delete(id);
        }

        @Override
        public void deleteAll() {
            delegate.deleteAll();
        }
    }

    private static final class UnreadableRepository implements AiConversationRepository {

        @Override
        public List<AiConversationSummary> summaries() {
            throw new IllegalStateException("disk is unreadable");
        }

        @Override
        public Optional<AiConversation> find(String id) {
            return Optional.empty();
        }

        @Override
        public void save(AiConversation conversation) {
        }

        @Override
        public void delete(String id) {
        }

        @Override
        public void deleteAll() {
        }
    }

    /** 先能读、后读不出来：验"已有内容时刷新失败"那条路。 */
    private static final class FlakyRepository implements AiConversationRepository {

        private final MemoryConversationRepository delegate;
        private boolean fail;

        private FlakyRepository(MemoryConversationRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<AiConversationSummary> summaries() {
            if (fail) {
                throw new IllegalStateException("disk went away");
            }
            return delegate.summaries();
        }

        @Override
        public Optional<AiConversation> find(String id) {
            return delegate.find(id);
        }

        @Override
        public void save(AiConversation conversation) {
            delegate.save(conversation);
        }

        @Override
        public void delete(String id) {
            delegate.delete(id);
        }

        @Override
        public void deleteAll() {
            delegate.deleteAll();
        }
    }
}
