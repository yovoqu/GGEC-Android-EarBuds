package com.ggec.earbuds.domain.ai.usecase;

import static com.ggec.earbuds.testing.ai.AiFixtures.model;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.ai.model.AiConnectionResult;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiModelKind;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiProtocol;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiUsageSource;
import com.ggec.earbuds.domain.ai.policy.AiConnectionPolicy;
import com.ggec.earbuds.domain.ai.policy.AiFingerprint;
import com.ggec.earbuds.testing.ai.AiFixtures;
import com.ggec.earbuds.testing.ai.ManualClock;
import com.ggec.earbuds.testing.ai.MemorySettingsRepository;
import com.ggec.earbuds.testing.ai.MemoryUsageStore;
import com.ggec.earbuds.testing.ai.ScriptedSpeedGateway;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 连接测试与测速的后台协调（CON-02/06/07/08，SPD-B-03…08）。 */
public class AiPlatformJobsTest {

    private final ManualClock clock = new ManualClock();
    private final ScriptedSpeedGateway gateway = new ScriptedSpeedGateway();
    private final MemoryUsageStore usage = new MemoryUsageStore();
    private MemorySettingsRepository repository = new MemorySettingsRepository(settings(
            AiFixtures.platform("p", "sk-one", "a", "b", "c").withModels(List.of(model("a"), model("b"), model("c"),
                    embedding("e")), 0L)));
    private AiPlatformJobs jobs = new AiPlatformJobs(repository, gateway, clock, usage);

    // ------------------------------------------------------------------ 连接测试

    @Test
    public void aConnectionTestSendsOneRequestAndStoresTheResultWithItsFingerprint() {
        List<String> events = new ArrayList<>();
        jobs.observe(() -> events.add("changed"));

        jobs.testConnection("p");
        jobs.testConnection("p");
        assertEquals("测试中再点不会再发", 1, gateway.runs.size());
        assertEquals(List.of("a"), gateway.lastModelIds());
        assertTrue(jobs.state("p").testingConnection());

        clock.advance(10);
        gateway.lastListener().onResult(ok("a", 420));
        gateway.lastListener().onComplete(false);

        AiConnectionResult result = platform().connection();
        assertNotNull(result);
        assertTrue(result.ok());
        assertEquals(420L, result.firstTokenMillis());
        assertEquals("a", result.modelId());
        assertEquals(AiFingerprint.connection(platform(), "a"), result.fingerprint());
        assertSame(AiPlatformJobs.PlatformJobs.IDLE, jobs.state("p"));
        assertEquals(AiConnectionPolicy.Status.OK, AiConnectionPolicy.status(platform(), null));
        assertEquals(1, usage.size());
        assertEquals(AiUsageSource.CONNECTION_TEST, usage.last().source());
        assertFalse(events.isEmpty());
    }

    @Test
    public void failuresAreStoredWithTheirKind() {
        jobs.testConnection("p");
        gateway.lastListener().onResult(new AiSpeedResult("a", AiSpeedResult.Status.FAILED, -1, 30,
                AiFailureKind.AUTHENTICATION, "bad key", 0));
        gateway.lastListener().onComplete(false);

        assertEquals(AiFailureKind.AUTHENTICATION, platform().connection().failureKind());
        assertEquals(AiConnectionPolicy.Status.FAILED, AiConnectionPolicy.status(platform(), null));
        assertEquals(AiConnectionPolicy.Target.API_KEY,
                AiConnectionPolicy.target(platform().connection().failureKind()));
    }

    @Test
    public void timeoutsCountAsTimeoutFailures() {
        jobs.testConnection("p");
        gateway.lastListener().onResult(new AiSpeedResult("a", AiSpeedResult.Status.TIMEOUT, -1, 15_000, null,
                null, 0));
        assertEquals(AiFailureKind.TIMEOUT, platform().connection().failureKind());
    }

    @Test
    public void gatewayErrorsBecomeAFailedResult() {
        gateway.failOnRun = new AiFailure(AiFailureKind.NETWORK, "offline");
        jobs.testConnection("p");
        assertEquals(AiFailureKind.NETWORK, platform().connection().failureKind());
        assertFalse(jobs.state("p").testingConnection());
    }

    @Test
    public void cancellingRestoresThePreviousStateWithoutWritingOrRecording() {
        AiSettings before = repository.load();
        jobs.testConnection("p");
        jobs.cancelConnection("p");

        assertTrue(gateway.lastHandle().cancelled);
        assertSame(AiPlatformJobs.PlatformJobs.IDLE, jobs.state("p"));
        gateway.lastListener().onResult(ok("a", 100));
        gateway.lastListener().onComplete(true);
        assertEquals(before, repository.load());
        assertEquals(0, usage.size());
    }

    @Test
    public void changingTheKeyMakesTheResultStaleAndChangingItBackRestoresIt() {
        jobs.testConnection("p");
        gateway.lastListener().onResult(ok("a", 300));
        gateway.lastListener().onComplete(false);
        assertEquals(AiConnectionPolicy.Status.OK, AiConnectionPolicy.status(platform(), null));

        repository.update(s -> s.withPlatform(platform().withApiKey("sk-two")));
        assertEquals(AiConnectionPolicy.Status.STALE, AiConnectionPolicy.status(platform(), null));
        repository.update(s -> s.withPlatform(platform().withApiKey("sk-one")));
        assertEquals(AiConnectionPolicy.Status.OK, AiConnectionPolicy.status(platform(), null));

        repository.update(s -> s.withPlatform(platform().withTestModel("b")));
        assertEquals("换测试模型也要重测", AiConnectionPolicy.Status.STALE,
                AiConnectionPolicy.status(platform(), null));
    }

    // ------------------------------------------------------------------ 测速

    @Test
    public void onlyChatModelsAreTestedAndRunningOnesAreNotRepeated() {
        assertEquals(2, jobs.speedTest("p", List.of("a", "b", "e"), true));
        assertEquals(List.of("a", "b"), gateway.lastModelIds());
        assertEquals(Set.of("a", "b"), jobs.state("p").speedTesting());

        assertEquals("同一模型重复触发只产生 1 个请求", 0, jobs.speedTest("p", List.of("a"), false));
        assertEquals(1, gateway.runs.size());
        assertEquals(1, jobs.speedTest("p", List.of("a", "c"), false));
        assertEquals(List.of("c"), gateway.lastModelIds());
    }

    @Test
    public void resultsAreStampedStoredRecordedAndCollectedIntoTheBatch() {
        jobs.speedTest("p", List.of("a", "b", "c"), true);
        clock.advance(50);
        gateway.lastListener().onResult(ok("a", 300));
        gateway.lastListener().onResult(ok("b", 200));

        AiPlatformJobs.Batch batch = jobs.state("p").batch();
        assertEquals(3, batch.total());
        assertEquals(2, batch.done());
        assertFalse(batch.finished());
        assertEquals(Set.of("c"), jobs.state("p").speedTesting());

        gateway.lastListener().onResult(failed("c"));
        gateway.lastListener().onComplete(false);

        AiSpeedResult stored = platform().speedResults().get("b");
        assertEquals(clock.now(), stored.testedAtMillis());
        assertEquals(AiFingerprint.speed(platform()), stored.fingerprint());
        batch = jobs.state("p").batch();
        assertTrue(batch.finished());
        assertFalse(batch.cancelled());
        assertEquals(3, batch.done());
        assertEquals(3, usage.readAll().stream().filter(r -> r.source() == AiUsageSource.SPEED_TEST).count());

        jobs.clearFinishedBatch("p");
        assertNull(jobs.state("p").batch());
    }

    @Test
    public void cancellingKeepsFinishedResultsAndLeavesTheRestAlone() {
        AiSpeedResult old = ok("c", 999);
        repository.update(s -> s.withPlatform(platform().withSpeedResult(old)));
        jobs.speedTest("p", List.of("a", "b", "c"), true);
        gateway.lastListener().onResult(ok("a", 100));

        jobs.cancelSpeed("p");
        gateway.lastListener().onResult(ok("b", 100));
        gateway.lastListener().onComplete(true);

        assertTrue(gateway.lastHandle().cancelled);
        assertNotNull(platform().speedResults().get("a"));
        assertNull(platform().speedResults().get("b"));
        assertEquals(old, platform().speedResults().get("c"));
        AiPlatformJobs.Batch batch = jobs.state("p").batch();
        assertTrue(batch.finished());
        assertTrue(batch.cancelled());
        assertEquals(1, batch.done());
        assertTrue(jobs.state("p").speedTesting().isEmpty());
        assertEquals(1, usage.size());
    }

    @Test
    public void resultsForAModelRemovedMeanwhileAreDropped() {
        jobs.speedTest("p", List.of("a"), false);
        repository.update(s -> s.withPlatform(platform().withoutModels(Set.of("a"))));
        gateway.lastListener().onResult(ok("a", 100));
        assertFalse(platform().speedResults().containsKey("a"));
    }

    @Test
    public void changingTheProtocolMakesSpeedResultsStaleAndChangingBackRestoresThem() {
        jobs.speedTest("p", List.of("a"), false);
        gateway.lastListener().onResult(ok("a", 100));
        AiSpeedResult result = platform().speedResults().get("a");
        assertFalse(AiFingerprint.stale(result, platform()));

        repository.update(s -> s.withPlatform(platform().withProtocol(AiProtocol.MESSAGES)));
        assertTrue(AiFingerprint.stale(platform().speedResults().get("a"), platform()));
        repository.update(s -> s.withPlatform(platform().withProtocol(AiProtocol.CHAT_COMPLETIONS)));
        assertFalse(AiFingerprint.stale(platform().speedResults().get("a"), platform()));
        assertFalse("旧数据没有指纹，不判过期", AiFingerprint.stale(ok("x", 1), platform()));
    }

    @Test
    public void aGatewayFailureFailsEveryRequestedModel() {
        gateway.failOnRun = new AiFailure(AiFailureKind.NETWORK, "offline");
        assertEquals(2, jobs.speedTest("p", List.of("a", "b"), true));
        assertFalse(platform().speedResults().get("a").ok());
        assertFalse(platform().speedResults().get("b").ok());
        assertTrue(jobs.state("p").batch().finished());
        assertTrue(jobs.state("p").speedTesting().isEmpty());
    }

    @Test
    public void jobsArePerPlatform() {
        repository = new MemorySettingsRepository(settings(AiFixtures.platform("p", "k", "a"), AiFixtures.platform("q", "k", "a")));
        jobs = new AiPlatformJobs(repository, gateway, clock, usage);
        jobs.speedTest("p", List.of("a"), false);
        assertEquals(1, jobs.speedTest("q", List.of("a"), false));
        jobs.cancelSpeed("p");
        assertEquals(Set.of("a"), jobs.state("q").speedTesting());
        assertTrue(gateway.handles.get(0).cancelled);
        assertFalse(gateway.handles.get(1).cancelled);
    }

    // ------------------------------------------------------------------ 工具

    private static AiSettings settings(AiPlatformConfig... platforms) {
        return AiFixtures.settings(null, platforms);
    }

    private AiPlatformConfig platform() {
        return repository.load().platform("p").orElseThrow();
    }

    private static AiModel embedding(String id) {
        return new AiModel(id, id, null, null, Set.of(), false, AiModelKind.EMBEDDING, false);
    }

    private static AiSpeedResult ok(String id, long firstToken) {
        return new AiSpeedResult(id, AiSpeedResult.Status.OK, firstToken, firstToken, null, null, 0);
    }

    private static AiSpeedResult failed(String id) {
        return new AiSpeedResult(id, AiSpeedResult.Status.FAILED, -1, 0, AiFailureKind.SERVER, "500", 0);
    }
}
