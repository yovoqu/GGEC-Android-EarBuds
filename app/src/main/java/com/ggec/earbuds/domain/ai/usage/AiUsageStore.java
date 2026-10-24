package com.ggec.earbuds.domain.ai.usage;

import com.ggec.earbuds.domain.ai.model.AiUsageRecord;

import java.util.List;

/**
 * 用量流水的存取端口。
 *
 * <p>只暴露“追加”和“读回”，不提供修改或删除单条——流水一旦写下就是事实，
 * 允许改账本就没法用它核对费用。要清理只按时间整段裁剪。</p>
 *
 * <p>读回必须是宽容的：一行坏掉只丢那一行，不能让整份账打不开。
 * 写入失败用运行时异常表达，由调用方（{@code RecordUsage}）决定吞掉还是上报——
 * 端口自己不假装成功。</p>
 */
public interface AiUsageStore {

    void append(AiUsageRecord record);

    List<AiUsageRecord> readAll();

    /**
     * 整段清空。只在用户主动清账时调用。
     */
    void clear();
}
