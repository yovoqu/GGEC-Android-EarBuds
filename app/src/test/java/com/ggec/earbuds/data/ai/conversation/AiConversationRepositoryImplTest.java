package com.ggec.earbuds.data.ai.conversation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.data.ai.json.AiConversationJson;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.testing.ai.MemoryDocumentStore;

import org.junit.Test;

import java.util.List;

public final class AiConversationRepositoryImplTest {

    private static final String INDEX = "conversations/index.json";

    private final MemoryDocumentStore documents = new MemoryDocumentStore();

    private AiConversationRepositoryImpl repository() {
        return new AiConversationRepositoryImpl(documents, new AiConversationJson());
    }

    @Test
    public void savedConversationsComeBackNewestFirst() {
        AiConversationRepositoryImpl repository = repository();
        repository.save(conversation("old", "旧对话", 100, "first words"));
        repository.save(conversation("new", "新对话", 900, "latest words"));
        repository.save(conversation("mid", "", 500, "middle words"));

        List<AiConversationSummary> summaries = repository.summaries();

        assertEquals(List.of("new", "mid", "old"), summaries.stream().map(AiConversationSummary::id).toList());
        assertEquals("latest words", summaries.get(0).preview());
        assertEquals(1, summaries.get(0).messageCount());
        assertEquals("新对话", repository.find("new").orElseThrow().title());
        assertTrue(repository.find("nobody").isEmpty());
    }

    @Test
    public void savingAgainReplacesTheEntryAndMovesItToTheTop() {
        AiConversationRepositoryImpl repository = repository();
        repository.save(conversation("a", "A", 100, "one"));
        repository.save(conversation("b", "B", 200, "two"));

        repository.save(conversation("a", "A", 300, "three"));

        assertEquals(List.of("a", "b"), repository.summaries().stream().map(AiConversationSummary::id).toList());
        assertEquals("three", repository.summaries().get(0).preview());
    }

    @Test
    public void deletingRemovesTheDocumentAndTheIndexEntry() {
        AiConversationRepositoryImpl repository = repository();
        repository.save(conversation("a", "A", 100, "one"));
        repository.save(conversation("b", "B", 200, "two"));

        repository.delete("a");

        assertEquals(List.of("b"), repository.summaries().stream().map(AiConversationSummary::id).toList());
        assertFalse(documents.documents.containsKey("conversations/a.json"));
        repository.delete("never-existed");
        assertEquals(1, repository.summaries().size());
    }

    @Test
    public void deleteAllEmptiesTheDirectoryAndTheIndex() {
        AiConversationRepositoryImpl repository = repository();
        repository.save(conversation("a", "A", 100, "one"));
        repository.save(conversation("b", "B", 200, "two"));

        repository.deleteAll();

        assertTrue(repository.summaries().isEmpty());
        assertEquals(List.of("index.json"), documents.list("conversations"));
        assertTrue(repository().summaries().isEmpty());
    }

    @Test
    public void historySurvivesARestart() {
        repository().save(conversation("a", "A", 100, "one"));

        AiConversationRepositoryImpl restarted = repository();

        assertEquals(List.of("a"), restarted.summaries().stream().map(AiConversationSummary::id).toList());
        assertEquals("one", restarted.find("a").orElseThrow().messages().get(0).text());
    }

    @Test
    public void aMissingOrCorruptIndexIsRebuiltFromTheConversationDocuments() {
        repository().save(conversation("a", "A", 100, "one"));
        repository().save(conversation("b", "B", 200, "two"));

        documents.documents.remove(INDEX);
        assertEquals(List.of("b", "a"), repository().summaries().stream().map(AiConversationSummary::id).toList());

        documents.documents.put(INDEX, "{\"v\":1,\"items\":[]}");
        assertEquals(List.of("b", "a"), repository().summaries().stream().map(AiConversationSummary::id).toList());
        assertTrue("the rebuilt index is written back",
                documents.documents.get(INDEX).contains("\"b\""));
        assertTrue("重建后写回的是当前版本",
                documents.documents.get(INDEX).contains("\"v\":2"));
    }

    @Test
    public void anIndexWriteFailureDoesNotFailTheAlreadySavedConversation() {
        AiConversationRepositoryImpl repository = repository();
        documents.failFor.add(INDEX);

        repository.save(conversation("a", "A", 100, "one"));

        assertTrue("会话文档仍然落盘", documents.documents.containsKey("conversations/a.json"));
        assertFalse("索引写不进去", documents.documents.containsKey(INDEX));
        documents.failFor.clear();
        assertEquals("缓存作废后按会话文件重建", List.of("a"),
                repository.summaries().stream().map(AiConversationSummary::id).toList());
    }

    @Test
    public void aCorruptConversationDocumentIsReportedNotSwallowed() {
        documents.documents.put("conversations/x.json", "{{ broken");

        try {
            repository().find("x");
            fail("a corrupt conversation must not read as missing");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.STORAGE, failure.kind());
            assertEquals("conversations/x.json", failure.location().orElseThrow().document());
            assertEquals("x", failure.location().orElseThrow().entityId());
        }
        assertEquals("损坏的会话文档保持原样", "{{ broken", documents.documents.get("conversations/x.json"));
    }

    @Test
    public void rebuildingReportsACorruptConversationDocument() {
        repository().save(conversation("a", "A", 100, "one"));
        documents.documents.put("conversations/c.json", "{{ broken");
        documents.documents.remove(INDEX);

        try {
            repository().summaries();
            fail("an unreadable conversation must not be skipped silently");
        } catch (AiFailure failure) {
            assertEquals(AiFailureKind.STORAGE, failure.kind());
            assertEquals("conversations/c.json", failure.location().orElseThrow().document());
        }
    }

    private static AiConversation conversation(String id, String title, long updatedAt, String text) {
        return new AiConversation(id, title, 1, updatedAt, List.of(AiMessage.user(id + "-m", text, 1)));
    }
}
