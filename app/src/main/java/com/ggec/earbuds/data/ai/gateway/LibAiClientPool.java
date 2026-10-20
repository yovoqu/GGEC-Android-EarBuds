package com.ggec.earbuds.data.ai.gateway;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.yovo.lib_ai.api.YoAiClient;
import com.yovo.lib_ai.catalog.YoPlatformCatalog;
import com.yovo.lib_ai.catalog.YoPlatformSettings;
import com.yovo.lib_ai.platform.YoPlatform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 按平台配置缓存 {@link YoAiClient}，复用连接池。配置里任何影响连接的字段（密钥、地址）变了，
 * 指纹就变，自然换一个新客户端；同一平台的旧客户端在没有人使用时立刻关闭。
 * 客户端用租约借出：关闭客户端会中断它上面所有进行中的调用，所以有人借着就绝不关。
 */
public final class LibAiClientPool {

    private static final int CAPACITY = 4;
    private static final long CONNECT_TIMEOUT_MILLIS = 15_000L;
    private static final long READ_TIMEOUT_MILLIS = 120_000L;
    private static final long WRITE_TIMEOUT_MILLIS = 30_000L;

    /** 一次借用。关闭是幂等的。 */
    public interface Lease extends AutoCloseable {
        YoAiClient client();

        @Override
        void close();
    }

    private final Executor callbackExecutor;
    private final Object lock = new Object();
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(8, 0.75f, true);

    public LibAiClientPool(Executor callbackExecutor) {
        this.callbackExecutor = Objects.requireNonNull(callbackExecutor, "callbackExecutor");
    }

    public Lease acquire(AiPlatformConfig config) {
        String fingerprint = fingerprint(config);
        List<YoAiClient> closing = new ArrayList<>();
        Entry entry;
        synchronized (lock) {
            entry = entries.get(fingerprint);
            if (entry == null) {
                entry = new Entry(config.id(), fingerprint, create(config));
                entries.put(fingerprint, entry);
                retireOtherClientsOf(entry, closing);
            }
            entry.leases++;
            evictIdleBeyondCapacity(closing);
        }
        closeAll(closing);
        return new LeaseImpl(entry);
    }

    /** 当前被借出的租约总数，测试用来确认每条路径都归还了租约。 */
    int leasesInUse() {
        synchronized (lock) {
            int total = 0;
            for (Entry entry : entries.values()) {
                total += entry.leases;
            }
            return total;
        }
    }

    private void release(Entry entry) {
        List<YoAiClient> closing = new ArrayList<>();
        synchronized (lock) {
            entry.leases--;
            if (entry.retired && entry.leases == 0 && entries.remove(entry.fingerprint, entry)) {
                closing.add(entry.client);
            }
            evictIdleBeyondCapacity(closing);
        }
        closeAll(closing);
    }

    private YoAiClient create(AiPlatformConfig config) {
        YoPlatform platform = YoPlatformCatalog.standard().create(
                YoPlatformSettings.builder(config.templateId())
                        .instanceId(config.id())
                        .apiKey(config.apiKey())
                        .baseUrl(config.baseUrl())
                        .build());
        return YoAiClient.builder()
                .platform(platform)
                .maxRetries(2)
                .connectTimeoutMillis(CONNECT_TIMEOUT_MILLIS)
                .readTimeoutMillis(READ_TIMEOUT_MILLIS)
                .writeTimeoutMillis(WRITE_TIMEOUT_MILLIS)
                .callbackExecutor(callbackExecutor)
                .build();
    }

    private void retireOtherClientsOf(Entry fresh, List<YoAiClient> closing) {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry other = iterator.next().getValue();
            if (other == fresh || !other.platformId.equals(fresh.platformId)) {
                continue;
            }
            other.retired = true;
            if (other.leases == 0) {
                iterator.remove();
                closing.add(other.client);
            }
        }
    }

    private void evictIdleBeyondCapacity(List<YoAiClient> closing) {
        Iterator<Map.Entry<String, Entry>> iterator = entries.entrySet().iterator();
        while (entries.size() > CAPACITY && iterator.hasNext()) {
            Entry candidate = iterator.next().getValue();
            if (candidate.leases == 0) {
                iterator.remove();
                closing.add(candidate.client);
            }
        }
    }

    private static void closeAll(List<YoAiClient> clients) {
        for (YoAiClient client : clients) {
            client.close();
        }
    }

    /** 密钥只参与哈希，不会作为缓存键的明文留在内存里。 */
    static String fingerprint(AiPlatformConfig config) {
        String source = config.id() + '\n' + config.templateId() + '\n' + config.baseUrl() + '\n' + sha256(config.apiKey());
        return source;
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class Entry {
        final String platformId;
        final String fingerprint;
        final YoAiClient client;
        int leases;
        boolean retired;

        Entry(String platformId, String fingerprint, YoAiClient client) {
            this.platformId = platformId;
            this.fingerprint = fingerprint;
            this.client = client;
        }
    }

    private final class LeaseImpl implements Lease {
        private final Entry entry;
        private final AtomicBoolean released = new AtomicBoolean();

        LeaseImpl(Entry entry) {
            this.entry = entry;
        }

        @Override
        public YoAiClient client() {
            return entry.client;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                release(entry);
            }
        }
    }
}
