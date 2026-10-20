package com.ggec.earbuds.data.ai.conversation;

import com.ggec.earbuds.data.ai.json.AiConversationJson;
import com.ggec.earbuds.data.ai.store.AiDocumentStore;
import com.ggec.earbuds.data.ai.store.StorageGuard;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 一段对话一个文档，外加一份历史索引，列表页只读索引不必打开每段对话。
 * 索引丢失或损坏时扫描对话文档重建，所以索引永远只是缓存，不是数据的唯一来源。
 * 存储读写失败统一以领域的存储失败（{@link com.ggec.earbuds.domain.ai.model.AiFailure}）抛出。
 */
public final class AiConversationRepositoryImpl implements AiConversationRepository {

    private static final String DIRECTORY = "conversations";
    private static final String INDEX_NAME = "index.json";
    private static final String INDEX = DIRECTORY + "/" + INDEX_NAME;

    private final AiDocumentStore documents;
    private final AiConversationJson json;
    private List<AiConversationSummary> index;

    public AiConversationRepositoryImpl(AiDocumentStore documents, AiConversationJson json) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public synchronized List<AiConversationSummary> summaries() {
        return StorageGuard.call(() -> List.copyOf(loadedIndex()));
    }

    @Override
    public synchronized Optional<AiConversation> find(String id) {
        return StorageGuard.call(() -> {
            String text = documents.read(path(id));
            if (text == null || text.isBlank()) {
                return Optional.empty();
            }
            try {
                return Optional.of(json.decode(text));
            } catch (JSONException corrupt) {
                return Optional.empty();
            }
        });
    }

    @Override
    public synchronized void save(AiConversation conversation) {
        StorageGuard.run(() -> {
            documents.write(path(conversation.id()), json.encode(conversation));
            List<AiConversationSummary> next = new ArrayList<>(loadedIndex());
            next.removeIf(summary -> summary.id().equals(conversation.id()));
            next.add(AiConversationSummary.of(conversation));
            writeIndex(sorted(next));
        });
    }

    @Override
    public synchronized void delete(String id) {
        StorageGuard.run(() -> {
            documents.delete(path(id));
            List<AiConversationSummary> next = new ArrayList<>(loadedIndex());
            if (next.removeIf(summary -> summary.id().equals(id))) {
                writeIndex(next);
            }
        });
    }

    @Override
    public synchronized void deleteAll() {
        StorageGuard.run(() -> {
            for (String name : documents.list(DIRECTORY)) {
                documents.delete(DIRECTORY + "/" + name);
            }
            index = List.of();
        });
    }

    private List<AiConversationSummary> loadedIndex() {
        if (index == null) {
            index = readIndex();
        }
        return index;
    }

    private List<AiConversationSummary> readIndex() {
        String text = documents.read(INDEX);
        if (text != null && !text.isBlank()) {
            try {
                return sorted(json.decodeIndex(text));
            } catch (JSONException corrupt) {
                // 索引坏了不影响数据，下面重建。
            }
        }
        return rebuild();
    }

    private List<AiConversationSummary> rebuild() {
        List<AiConversationSummary> rebuilt = new ArrayList<>();
        for (String name : documents.list(DIRECTORY)) {
            if (!name.endsWith(".json") || name.equals(INDEX_NAME)) {
                continue;
            }
            String text = documents.read(DIRECTORY + "/" + name);
            if (text == null) {
                continue;
            }
            try {
                rebuilt.add(AiConversationSummary.of(json.decode(text)));
            } catch (JSONException corrupt) {
                // 单个坏文档跳过。
            }
        }
        List<AiConversationSummary> result = sorted(rebuilt);
        if (!result.isEmpty()) {
            writeIndex(result);
        }
        return result;
    }

    private void writeIndex(List<AiConversationSummary> next) {
        documents.write(INDEX, json.encodeIndex(next));
        index = next;
    }

    private static List<AiConversationSummary> sorted(List<AiConversationSummary> summaries) {
        List<AiConversationSummary> copy = new ArrayList<>(summaries);
        copy.sort(Comparator.comparingLong(AiConversationSummary::updatedAtMillis).reversed()
                .thenComparing(AiConversationSummary::id));
        return copy;
    }

    private static String path(String id) {
        return DIRECTORY + "/" + id + ".json";
    }
}
