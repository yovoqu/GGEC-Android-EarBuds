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
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSelection;
import com.ggec.earbuds.domain.ai.model.AiSettings;
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
        secrets.secrets.put("apikey.deepseek", "sk-one");
        AiSettingsRepositoryImpl first = repository();
        first.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "", "m1", "m2"))
                .withTextSelection(new AiSelection("deepseek", "m2")));

        AiSettings reloaded = repository().load();

        assertTrue(reloaded.platform("deepseek").orElseThrow().configured());
        assertEquals(2, reloaded.platform("deepseek").orElseThrow().models().size());
        assertEquals(new AiSelection("deepseek", "m2"), reloaded.textSelection());
        assertTrue(reloaded.textModel().isPresent());
    }

    @Test
    public void keysLiveOnlyInTheSecretStore() {
        secrets.secrets.put("apikey.deepseek", "sk-very-secret");
        repository().update(s -> s.withPlatform(AiFixtures.platform("deepseek", "", "m1")));

        assertEquals("sk-very-secret", secrets.secrets.get("apikey.deepseek"));
        assertFalse(documents.documents.get("settings.json").contains("sk-very-secret"));
    }

    @Test
    public void configuredFlagFollowsTheSecretStore() {
        secrets.secrets.put("apikey.deepseek", "k1");
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "", "m1")));
        assertTrue(repository.load().platform("deepseek").orElseThrow().configured());

        secrets.secrets.remove("apikey.deepseek");
        assertFalse(repository.load().platform("deepseek").orElseThrow().configured());
    }

    @Test
    public void removingAPlatformRemovesItsSecret() {
        secrets.secrets.put("apikey.a", "ka");
        secrets.secrets.put("apikey.b", "kb");
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("a", "", "m"))
                .withPlatform(AiFixtures.platform("b", "", "m")));

        repository.update(s -> s.withoutPlatform("a"));

        assertNull(secrets.secrets.get("apikey.a"));
        assertEquals("kb", secrets.secrets.get("apikey.b"));
    }

    @Test
    public void voiceKeysLiveOnlyInTheSecretStoreAndDriveConfiguredFlag() {
        secrets.secrets.put("voicekey.qwen", "sk-qwen-secret");
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withVoice(AiFixtures.voice(AiVoiceModel.QWEN, "")));

        assertFalse(documents.documents.get("settings.json").contains("sk-qwen-secret"));
        assertFalse("文档里连密钥键都不该有", documents.documents.get("settings.json").contains("voicekey"));
        assertTrue(repository().load().voiceReady());
        assertEquals(AiVoiceModel.QWEN, repository().load().voice().model());

        secrets.secrets.remove("voicekey.qwen");
        assertFalse(repository().load().voiceReady());
    }

    @Test
    public void chatGptRealtimeChoicesSurviveARestartBesideTheKey() {
        secrets.secrets.put("voicekey.chatgpt", "sk-gpt");
        repository().update(s -> s.withVoice(AiFixtures.voice(AiVoiceModel.CHATGPT, "")
                .withBaseUrl("https://gw.example.com")
                .withRealtimeModels(List.of(new AiModel("gpt-realtime", "gpt-realtime",
                        null, null, java.util.Set.of(), false,
                        com.ggec.earbuds.domain.ai.model.AiModelCategory.REALTIME)))));

        AiVoiceConfig voice = repository().load().voice();

        assertTrue("配置状态来自凭证存储", voice.configured());
        assertEquals("https://gw.example.com", voice.baseUrl());
        assertEquals(List.of(new AiModel("gpt-realtime", "gpt-realtime", null, null,
                java.util.Set.of(), false, com.ggec.earbuds.domain.ai.model.AiModelCategory.REALTIME)),
                voice.realtimeModels());
        assertEquals("gpt-realtime", voice.resolvedRealtimeModel());
        assertFalse(documents.documents.get("settings.json").contains("sk-gpt"));
    }

    @Test
    public void aLostVoiceKeyLeavesTheModelSelectedButUnready() {
        secrets.secrets.put("voicekey.chatgpt", "sk-gpt");
        repository().update(s -> s.withVoice(AiFixtures.voice(AiVoiceModel.CHATGPT, "")));
        secrets.secrets.clear();

        AiSettings reloaded = repository().load();

        assertEquals(AiVoiceModel.CHATGPT, reloaded.voice().model());
        assertFalse(reloaded.voiceReady());
    }

    @Test
    public void removingAPlatformKeepsTheVoiceKey() {
        secrets.secrets.put("voicekey.chatgpt", "sk-gpt");
        AiSettingsRepositoryImpl repository = repository();
        repository.update(s -> s.withPlatform(AiFixtures.platform("deepseek", "sk-one", "m"))
                .withVoice(AiFixtures.voice(AiVoiceModel.CHATGPT, "")));

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
