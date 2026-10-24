package com.ggec.earbuds.testing.ai;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.usage.AiUsageStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 内存账本。测试里只想回答两个问题：记了几笔、最后一笔是什么。
 * 故意不做去重和排序——真实实现也不做，多一层"体贴"会让测试替实现擦屁股。
 */
public final class MemoryUsageStore implements AiUsageStore {

    private final List<AiUsageRecord> records = new ArrayList<>();

    @Override
    public void append(@NonNull AiUsageRecord record) {
        records.add(record);
    }

    @NonNull
    @Override
    public List<AiUsageRecord> readAll() {
        return List.copyOf(records);
    }

    @Override
    public void clear() {
        records.clear();
    }

    public int size() {
        return records.size();
    }

    @NonNull
    public AiUsageRecord last() {
        if (records.isEmpty()) {
            throw new IllegalStateException("no usage recorded");
        }
        return records.get(records.size() - 1);
    }
}
