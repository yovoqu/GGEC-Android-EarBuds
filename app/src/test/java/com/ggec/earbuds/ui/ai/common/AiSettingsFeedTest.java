package com.ggec.earbuds.ui.ai.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.testing.ai.QueueExecutor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class AiSettingsFeedTest {

    private static final AiSettings STALE = AiSettings.EMPTY;
    private static final AiSettings FRESH = AiSettings.EMPTY.withPreferences(AiChatPreferences.DEFAULT.withShowReasoning(false));

    /** 读到的永远是 {@link #STALE}，通知里推送的是别的值：模拟"读盘读到一半、设置已经变了"。 */
    private static final class FrozenRepository implements AiSettingsRepository {
        Consumer<AiSettings> observer;
        boolean cancelled;
        boolean failLoad;

        @Override
        public AiSettings load() {
            if (failLoad) {
                throw new AiFailure(AiFailureKind.STORAGE, "disk unreadable");
            }
            return STALE;
        }

        @Override
        public AiSettings update(UnaryOperator<AiSettings> change) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Subscription observe(Consumer<AiSettings> observer) {
            this.observer = observer;
            return () -> cancelled = true;
        }
    }

    private final FrozenRepository repository = new FrozenRepository();
    private final QueueExecutor io = new QueueExecutor();
    private final List<AiSettings> seen = new ArrayList<>();
    private final List<AiFailure> failures = new ArrayList<>();

    private AiSettingsFeed feed() {
        return new AiSettingsFeed(new LoadAiSettings(repository), new ObserveAiSettings(repository), io,
                seen::add, failures::add);
    }

    @Test
    public void theInitialReadIsDeliveredWhenNothingNewerArrivedFirst() {
        feed();

        io.drain();
        repository.observer.accept(FRESH);

        assertEquals(List.of(STALE, FRESH), seen);
    }

    @Test
    public void aNotificationThatBeatsTheInitialReadReplacesItInsteadOfBeingOverwritten() {
        feed();

        repository.observer.accept(FRESH);
        io.drain();

        assertEquals("the stale read must not land after the fresh value", List.of(FRESH), seen);
    }

    @Test
    public void aFailedInitialReadIsReportedAndLaterChangesStillArrive() {
        repository.failLoad = true;
        feed();

        io.drain();
        repository.observer.accept(FRESH);

        assertEquals(1, failures.size());
        assertEquals(AiFailureKind.STORAGE, failures.get(0).kind());
        assertEquals(List.of(FRESH), seen);
    }

    @Test
    public void aFailedReadThatLostTheRaceIsNotReported() {
        repository.failLoad = true;
        feed();

        repository.observer.accept(FRESH);
        io.drain();

        assertTrue("a newer value already made the failure irrelevant", failures.isEmpty());
        assertEquals(List.of(FRESH), seen);
    }

    @Test
    public void closingStopsEverythingAndCancelsTheSubscription() {
        AiSettingsFeed feed = feed();

        feed.close();
        io.drain();
        repository.observer.accept(FRESH);

        assertTrue(seen.isEmpty());
        assertTrue(repository.cancelled);
    }
}
