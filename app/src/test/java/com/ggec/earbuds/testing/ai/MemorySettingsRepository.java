package com.ggec.earbuds.testing.ai;

import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.port.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** 领域测试用的设置仓库：没有存储，只保证"读-改-写-通知"的语义。 */
public final class MemorySettingsRepository implements AiSettingsRepository {

    private AiSettings current;
    private final List<Consumer<AiSettings>> observers = new ArrayList<>();
    public int updates;

    public MemorySettingsRepository() {
        this(AiSettings.EMPTY);
    }

    public MemorySettingsRepository(AiSettings initial) {
        this.current = initial;
    }

    @Override
    public AiSettings load() {
        return current;
    }

    @Override
    public AiSettings update(UnaryOperator<AiSettings> change) {
        AiSettings next = change.apply(current);
        if (!next.equals(current)) {
            current = next;
            updates++;
            for (Consumer<AiSettings> observer : new ArrayList<>(observers)) {
                observer.accept(next);
            }
        }
        return current;
    }

    @Override
    public Subscription observe(Consumer<AiSettings> observer) {
        observers.add(observer);
        return () -> observers.remove(observer);
    }
}
