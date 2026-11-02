package com.ggec.earbuds.data.ai.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.store.AiStorageException;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiRealtimeDialect;
import com.ggec.earbuds.domain.ai.model.AiVoiceConfig;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.MemoryDocumentStore;
import com.ggec.earbuds.testing.ai.MemorySecretStore;
import com.ggec.earbuds.testing.ai.QueueExecutor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

public class AiSettingsRepositoryImplTest {

    private final MemoryDocumentStore documents = new MemoryDocumentStore();
    private final MemorySecretStore secrets = new MemorySecretStore();

    private AiSettingsRepositoryImpl repository() {
        return new AiSettingsRepositoryImpl(
                documents, secrets, new AiSettingsJson(), Runnable::run);
    }

    private AiSettingsRepositoryImpl queuedRepository(QueueExecutor executor) {
        return new AiSettingsRepositoryImpl(
                documents, secrets, new AiSettingsJson(), executor);
    }

    @Test
    public void startsEmptyWhenNothingWasSaved() {
        assertEquals(AiSettings.EMPTY, repository().load());
        assertEquals(0, documents.writes);
    }

    @Test
    public void updatesPersistAndSurviveARestart() {
        AiSettingsRepositoryImpl first = repository();
        first.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "sk-one", "m1", "m2"))
                .withTextSelection(new AiSelection("deepseek", "m2")));

        AiSettings reloaded = repository().load();

        assertEquals("sk-one", reloaded.platform("deepseek").orElseThrow().apiKey());
        assertEquals(2, reloaded.platform("deepseek").orElseThrow().models().size());
        assertEquals(new AiSelection("deepseek", "m2"), reloaded.textSelection());
        assertTrue(reloaded.textModel().isPresent());
    }

    @Test
    public void keysLiveOnlyInTheSecretStore() {
        repository().update(s -> s.withPlatform(AiFixtures.platform("deepseek", "sk-very-secret", "m1")));

        assertEquals("sk-very-secret", secrets.secrets.get("apikey.deepseek"));
        assertFalse(documents.documents.get("settings.json").contains("sk-very-secret"));
    }

    @Test
    public void changingAKeyUpdatesItAndBlankingItRemovesIt() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "k1", "m1")));

        repository.update(s -> s.withPlatform(s.platform("deepseek").orElseThrow()
                .withCredentials("deepseek", "k2", null, com.ggec.earbuds.domain.ai.model.AiProtocol.CHAT_COMPLETIONS)));
        assertEquals("k2", secrets.secrets.get("apikey.deepseek"));

        repository.update(s -> s.withPlatform(s.platform("deepseek").orElseThrow().withoutKey()));
        assertNull(secrets.secrets.get("apikey.deepseek"));
        assertFalse(repository.load().platform("deepseek").orElseThrow().configured());
    }

    @Test
    public void removingAPlatformRemovesItsSecret() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "ka", "m"))
                .withPlatform(AiFixtures.platform("b", "kb", "m")));

        repository.update(s -> s.withoutPlatform("a"));

        assertNull(secrets.secrets.get("apikey.a"));
        assertEquals("kb", secrets.secrets.get("apikey.b"));
    }

    @Test
    public void voiceKeysLiveOnlyInTheSecretStoreAndSurviveARestart() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withVoice(AiFixtures.voice(AiVoiceModel.QWEN, "sk-qwen-secret")));

        assertEquals("sk-qwen-secret", secrets.secrets.get("voicekey.qwen"));
        assertFalse(documents.documents.get("settings.json").contains("sk-qwen-secret"));
        assertTrue(repository().load().voiceReady());
        assertEquals(AiVoiceModel.QWEN, repository().load().voice().model());

        repository.update(s -> s.withVoice(s.voice().withApiKey("")));
        assertNull(secrets.secrets.get("voicekey.qwen"));
        assertFalse(repository().load().voiceReady());
    }

    @Test
    public void chatGptRealtimeChoicesSurviveARestartBesideTheKey() {
        repository().update(s -> s.withVoice(AiFixtures.voice(AiVoiceModel.CHATGPT, "sk-gpt")
                .withBaseUrl("https://gw.example.com")
                .withDialect(AiRealtimeDialect.GA)
                .withRealtimeModels(List.of("gpt-realtime"))));

        AiVoiceConfig voice = repository().load().voice();

        assertEquals("sk-gpt", voice.apiKey());
        assertEquals("https://gw.example.com", voice.baseUrl());
        assertEquals(AiRealtimeDialect.GA, voice.dialect());
        assertEquals(List.of("gpt-realtime"), voice.realtimeModels());
        assertEquals("gpt-realtime", voice.resolvedRealtimeModel());
        assertFalse(documents.documents.get("settings.json").contains("sk-gpt"));
    }

    @Test
    public void aLostVoiceKeyLeavesTheModelSelectedButUnready() {
        repository().update(s -> s.withVoice(AiFixtures.voice(AiVoiceModel.CHATGPT, "sk-gpt")));
        secrets.secrets.clear();

        AiSettings reloaded = repository().load();

        assertEquals(AiVoiceModel.CHATGPT, reloaded.voice().model());
        assertFalse(reloaded.voiceReady());
    }

    @Test
    public void removingAPlatformKeepsTheVoiceKey() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "sk-one", "m"))
                .withVoice(AiFixtures.voice(AiVoiceModel.CHATGPT, "sk-gpt")));

        repository.update(s -> s.withoutPlatform("deepseek"));

        assertNull(secrets.secrets.get("apikey.deepseek"));
        assertEquals("sk-gpt", secrets.secrets.get("voicekey.chatgpt"));
        assertTrue(repository.load().voiceReady());
    }

    @Test
    public void aLostSecretLeavesThePlatformUnconfiguredInsteadOfFailing() {
        repository().update(s -> s.withPlatform(AiFixtures.platform("deepseek", "k", "m1")));
        secrets.secrets.clear();

        AiPlatformConfig reloaded = repository().load().platform("deepseek").orElseThrow();

        assertFalse(reloaded.configured());
        assertEquals(1, reloaded.models().size());
    }

    @Test
    public void aCorruptDocumentFallsBackToEmptySettings() {
        documents.documents.put("settings.json", "{{{ not json");

        assertEquals(AiSettings.EMPTY, repository().load());
    }

    @Test
    public void unchangedUpdatesDoNotWriteOrNotify() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "k", "m1")));
        int writes = documents.writes;
        List<AiSettings> seen = new ArrayList<>();
        repository.observe(seen::add);

        AiSettings result = repository.update(s -> s);

        assertEquals(writes, documents.writes);
        assertTrue(seen.isEmpty());
        assertSame(repository.load(), result);
    }

    @Test
    public void observersSeeEachChangeUntilTheyUnsubscribe() {
        AiSettingsRepositoryImpl repository = repository();
        List<AiSettings> seen = new ArrayList<>();
        Subscription subscription = repository.observe(seen::add);

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        repository.update(s -> s.withTextSelection(new AiSelection("a", "m")));
        subscription.cancel();
        repository.update(s -> s.withTextSelection(null));

        assertEquals(2, seen.size());
        assertEquals(new AiSelection("a", "m"), seen.get(1).textSelection());
    }

    @Test
    public void aFailingObserverDoesNotUndoTheSave() {
        AiSettingsRepositoryImpl repository = repository();
        repository.observe(s -> {
            throw new IllegalStateException("boom");
        });

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));

        assertEquals(1, repository().load().platforms().size());
    }

    @Test
    public void aRejectedChangeLeavesStateAndDiskUntouched() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        int writes = documents.writes;

        try {
            repository.update(s -> {
                throw new IllegalArgumentException("rejected");
            });
            fail("the exception from the change must propagate");
        } catch (IllegalArgumentException expected) {
            assertEquals(writes, documents.writes);
            assertEquals(1, repository.load().platforms().size());
        }
    }

    @Test
    public void aFailedWriteLeavesTheInMemoryStateAndObserversUntouched() {
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        List<AiSettings> seen = new ArrayList<>();
        repository.observe(seen::add);
        List<Throwable> failures = new ArrayList<>();
        repository.observePersistenceFailures(failures::add);
        documents.failWrites = true;

        try {
            repository.update(s -> s.withTextSelection(new AiSelection("a", "m")));
            repository.flush().join();
            fail("a storage failure must reach flush");
        } catch (CompletionException wrapped) {
            assertTrue(wrapped.getCause() instanceof AiFailure);
            AiFailure expected = (AiFailure) wrapped.getCause();
            assertEquals(AiFailureKind.STORAGE, expected.kind());
            assertTrue(expected.getCause() instanceof AiStorageException);
            assertEquals(new AiSelection("a", "m"), repository.load().textSelection());
            assertEquals(1, seen.size());
            assertEquals(1, failures.size());
        }
    }

    @Test
    public void commitsPublishMemoryBeforeTheDiskWriteRuns() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        List<AiSettings> seen = new ArrayList<>();
        repository.observe(seen::add);

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));

        assertEquals(1, seen.size());
        assertEquals("a", repository.load().platforms().get(0).id());
        assertEquals(0, documents.writes);
        assertEquals(1, executor.pending());
        executor.drain();
        assertEquals(1, documents.writes);
    }

    @Test
    public void queuedWritesCoalesceToTheLatestSnapshot() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        List<AiSettings> seen = new ArrayList<>();
        repository.observe(seen::add);

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        repository.update(s -> s.withTextSelection(new AiSelection("a", "m")));
        repository.update(s -> s.withVoice(AiFixtures.voice(AiVoiceModel.QWEN, "sk-qwen")));

        assertEquals(3, seen.size());
        assertEquals(0, documents.writes);
        assertEquals(1, executor.pending());
        executor.drain();
        assertEquals(1, documents.writes);
        assertEquals(AiVoiceModel.QWEN, repository().load().voice().model());
        assertEquals(new AiSelection("a", "m"), repository().load().textSelection());
    }

    @Test
    public void flushWaitsForTheQueuedRevision() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));

        java.util.concurrent.CompletableFuture<Void> flushed = repository.flush();

        assertFalse(flushed.isDone());
        executor.drain();
        assertTrue(flushed.isDone());
        assertFalse(flushed.isCompletedExceptionally());
    }

    @Test
    public void loadAsyncReadsOnTheInjectedExecutorAndSharesOneRead() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        assertFalse(repository.ready());

        java.util.concurrent.CompletableFuture<AiSettings> first = repository.loadAsync();
        java.util.concurrent.CompletableFuture<AiSettings> second = repository.loadAsync();

        assertFalse(first.isDone());
        assertFalse(second.isDone());
        assertEquals(1, executor.pending());
        executor.drain();
        assertEquals(AiSettings.EMPTY, first.join());
        assertEquals(AiSettings.EMPTY, second.join());
        assertTrue(repository.ready());
    }

    @Test
    public void persistenceStatusReportsSavingThenSaved() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        List<com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus> statuses =
                new ArrayList<>();
        repository.observePersistenceStatus(statuses::add);

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        assertEquals(List.of(com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus.SAVING),
                statuses);
        executor.drain();
        assertEquals(List.of(
                com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus.SAVING,
                com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus.SAVED),
                statuses);
    }

    @Test
    public void persistenceStatusReportsFailure() {
        QueueExecutor executor = new QueueExecutor();
        AiSettingsRepositoryImpl repository = queuedRepository(executor);
        List<com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus> statuses =
                new ArrayList<>();
        repository.observePersistenceStatus(statuses::add);
        documents.failWrites = true;

        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "k", "m")));
        executor.drain();

        assertEquals(com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus.FAILED,
                statuses.get(statuses.size() - 1));
    }
}
