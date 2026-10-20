package com.ggec.earbuds.data.ai.conversation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.data.ai.json.AiConversationJson;
import com.ggec.earbuds.domain.ai.model.AiConversation;
import com.ggec.earbuds.domain.ai.model.AiConversationSummary;
import com.ggec.earbuds.domain.ai.model.AiMessage;
import com.ggec.earbuds.testing.ai.MemoryDocumentStore;

import org.junit.Test;

import java.util.List;

public class AiConversationRepositoryImplTest {

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
    public void deleteAllEmptiesTheDirectory() {
        AiConversationRepositoryImpl repository = repository();
        repository.save(conversation("a", "A", 100, "one"));
        repository.save(conversation("b", "B", 200, "two"));

        repository.deleteAll();

        assertTrue(repository.summaries().isEmpty());
        assertTrue(documents.documents.isEmpty());
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
        documents.documents.put("conversations/c.json", "{{ broken");

        documents.documents.remove("conversations/index.json");
        assertEquals(List.of("b", "a"), repository().summaries().stream().map(AiConversationSummary::id).toList());

        documents.documents.put("conversations/index.json", "not json at all");
        assertEquals(List.of("b", "a"), repository().summaries().stream().map(AiConversationSummary::id).toList());
        assertTrue("the rebuilt index is written back", documents.documents.get("conversations/index.json").contains("\"b\""));
    }

    @Test
    public void aCorruptConversationDocumentReadsAsMissing() {
        documents.documents.put("conversations/x.json", "{{ broken");

        assertTrue(repository().find("x").isEmpty());
    }

    private static AiConversation conversation(String id, String title, long updatedAt, String text) {
        return new AiConversation(id, title, 1, updatedAt, List.of(AiMessage.user(id + "-m", text, 1)));
    }
}
