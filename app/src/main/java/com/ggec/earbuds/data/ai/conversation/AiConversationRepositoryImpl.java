package com.ggec.earbuds.data.ai.conversation;

import androidx.annotation.Nullable;

import com.ggec.earbuds.data.ai.json.AiConversationJson;
import com.ggec.earbuds.data.store.DocumentFormatException;
import com.ggec.earbuds.data.store.DocumentStore;
import com.ggec.earbuds.data.store.FileDocumentStore.DocumentReadException;
import com.ggec.earbuds.data.store.FileDocumentStore.DocumentWriteException;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiFailureLocation;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 一段对话一个文档，外加一份历史索引，列表页只读索引不必打开每段对话。
 *
 * <p><b>真源是会话文档</b>：索引只是派生缓存。会话文档写成功之后，索引写失败不再把这次
 * {@link #save} 回报成失败——缓存作废，下次读取时按会话文件重建并写回。</p>
 *
 * <p>版本与结构都严格：会话文档读不出来就是失败（{@link AiFailureKind#STORAGE} +
 * 文档坐标），文件保持原样不删不改；只有索引损坏会触发重建，因为它本来就可以再生。</p>
 */
public final class AiConversationRepositoryImpl implements AiConversationRepository {

    private static final String DIRECTORY = "conversations";
    private static final String INDEX_NAME = "index.json";
    private static final String INDEX = DIRECTORY + "/" + INDEX_NAME;

    private final DocumentStore documents;
    private final AiConversationJson json;
    private List<AiConversationSummary> index;

    public AiConversationRepositoryImpl(DocumentStore documents, AiConversationJson json) {
        this.documents = Objects.requireNonNull(documents, "documents");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public synchronized List<AiConversationSummary> summaries() {
        return List.copyOf(loadedIndex());
    }

    @Override
    public synchronized Optional<AiConversation> find(String id) {
        Optional<String> text = read(path(id), id);
        if (text.isEmpty() || text.get().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.decode(text.get()));
        } catch (DocumentFormatException corrupt) {
            throw storageFailure(path(id), id, corrupt);
        }
    }

    @Override
    public synchronized void save(AiConversation conversation) {
        String path = path(conversation.id());
        try {
            documents.write(path, json.encode(conversation));
        } catch (DocumentWriteException unwritable) {
            throw storageFailure(path, conversation.id(), unwritable);
        }
        List<AiConversationSummary> next = new ArrayList<>(loadedIndex());
        next.removeIf(summary -> summary.id().equals(conversation.id()));
        next.add(AiConversationSummary.of(conversation));
        writeIndexBestEffort(sorted(next));
    }

    @Override
    public synchronized void delete(String id) {
        String path = path(id);
        try {
            documents.delete(path);
        } catch (DocumentWriteException unwritable) {
            throw storageFailure(path, id, unwritable);
        }
        List<AiConversationSummary> next = new ArrayList<>(loadedIndex());
        if (next.removeIf(summary -> summary.id().equals(id))) {
            writeIndexBestEffort(next);
        }
    }

    @Override
    public synchronized void deleteAll() {
        for (String name : documents.list(DIRECTORY)) {
            if (!name.endsWith(".json")) {
                continue;
            }
            String path = DIRECTORY + "/" + name;
            try {
                documents.delete(path);
            } catch (DocumentWriteException unwritable) {
                throw storageFailure(path, name, unwritable);
            }
        }
        writeIndexBestEffort(List.of());
    }

    private List<AiConversationSummary> loadedIndex() {
        if (index == null) {
            index = readIndex();
        }
        return index;
    }

    private List<AiConversationSummary> readIndex() {
        Optional<String> text = read(INDEX, null);
        if (text.isPresent() && !text.get().isBlank()) {
            try {
                return sorted(json.decodeIndex(text.get()));
            } catch (DocumentFormatException unusable) {
                // 索引是派生缓存：旧版本或损坏都按会话文件重建，重建结果立刻写回。
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
            String path = DIRECTORY + "/" + name;
            Optional<String> text = read(path, name);
            if (text.isEmpty() || text.get().isBlank()) {
                continue;
            }
            try {
                rebuilt.add(AiConversationSummary.of(json.decode(text.get())));
            } catch (DocumentFormatException corrupt) {
                // 会话文档是用户数据：读不出来就上报，不跳过、不删除、不隔离。
                throw storageFailure(path, name, corrupt);
            }
        }
        List<AiConversationSummary> result = sorted(rebuilt);
        writeIndexBestEffort(result);
        return result;
    }

    /**
     * 索引写失败不改变已落盘的会话：作废内存缓存，下次读取时按会话文件重建。
     * 这里只吞 {@link DocumentWriteException}（索引文件写不进去），其余失败仍然上抛。
     */
    private void writeIndexBestEffort(List<AiConversationSummary> next) {
        try {
            writeIndex(next);
        } catch (DocumentWriteException indexUnwritable) {
            index = null;
        }
    }

    private void writeIndex(List<AiConversationSummary> next) {
        documents.write(INDEX, json.encodeIndex(next));
        index = next;
    }

    private Optional<String> read(String path, @Nullable String entityId) {
        try {
            return documents.read(path);
        } catch (DocumentReadException unreadable) {
            throw storageFailure(path, entityId, unreadable);
        }
    }

    private static AiFailure storageFailure(String path, @Nullable String entityId, Throwable cause) {
        return new AiFailure(AiFailureKind.STORAGE, AiFailureLocation.of(path, entityId), cause);
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
