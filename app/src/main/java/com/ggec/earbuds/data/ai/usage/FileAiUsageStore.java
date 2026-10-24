package com.ggec.earbuds.data.ai.usage;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.ai.store.AiDocumentStore;
import com.ggec.earbuds.domain.ai.model.AiUsageRecord;
import com.ggec.earbuds.domain.ai.usage.AiUsageStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 把用量流水按 JSON Lines 追加到 {@code usage/requests.jsonl}。
 *
 * <p>构造时把文档存储的根目录指向 {@code filesDir/ai}，落盘位置就是
 * {@code <filesDir>/ai/usage/requests.jsonl}；根目录由装配处决定，
 * 这个类不碰文件系统，只认文档名。</p>
 *
 * <p>为什么复用 {@link AiDocumentStore} 而不自己开关文件：它已经保证写入是
 * “临时文件 + 原子改名”，进程在写一半被杀也不会留下半截账；再造一套文件 API
 * 只会多出一份需要各自验证的原子性实现。代价是追加要先读出全文再整体覆盖，
 * 但一次请求只多一行、上限几千行，这点开销换原子性划算。</p>
 *
 * <p>超过 {@link #MAX_RECORDS} 行时丢弃最旧的：这是本机的诊断账本，
 * 不是计费凭证，不能让它在用户手机上无限长大。</p>
 */
public final class FileAiUsageStore implements AiUsageStore {

    /**
     * 账本文档名。目录 {@code ai/} 与其它 AI 数据同级，便于整个功能一起迁移或清理。
     */
    public static final String DOCUMENT = "usage/requests.jsonl";

    /** 最多保留的记录条数，约等于几百 KB。 */
    public static final int MAX_RECORDS = 5000;

    private final AiDocumentStore documents;
    private final AiUsageRecordJson json = new AiUsageRecordJson();

    public FileAiUsageStore(AiDocumentStore documents) {
        this.documents = Objects.requireNonNull(documents, "documents");
    }

    @Override
    public synchronized void append(AiUsageRecord record) {
        Objects.requireNonNull(record, "record");
        List<String> lines = lines(documents.read(DOCUMENT));
        lines.add(json.encode(record));
        while (lines.size() > MAX_RECORDS) {
            lines.remove(0);
        }
        // 末尾补换行：下次追加和前一行之间不会粘成一行坏数据。
        documents.write(DOCUMENT, String.join("\n", lines) + "\n");
    }

    @Override
    public synchronized List<AiUsageRecord> readAll() {
        List<AiUsageRecord> records = new ArrayList<>();
        for (String line : lines(documents.read(DOCUMENT))) {
            AiUsageRecord record = json.decode(line);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    @Override
    public synchronized void clear() {
        documents.write(DOCUMENT, "");
    }

    private static List<String> lines(@Nullable String text) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }
}
