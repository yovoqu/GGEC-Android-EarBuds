package com.ggec.earbuds.wiring;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.ggec.earbuds.data.ai.conversation.AiConversationRepositoryImpl;
import com.ggec.earbuds.data.ai.gateway.LibAiChatGateway;
import com.ggec.earbuds.data.ai.gateway.LibAiClientPool;
import com.ggec.earbuds.data.ai.json.AiConversationJson;
import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.settings.AiSettingsRepositoryImpl;
import com.ggec.earbuds.data.ai.store.AiDocumentStore;
import com.ggec.earbuds.data.ai.store.EncryptedSecretStore;
import com.ggec.earbuds.data.ai.store.FileAiDocumentStore;
import com.ggec.earbuds.data.ai.store.SecretStore;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;
import com.ggec.earbuds.domain.ai.port.AiIdGenerator;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.platform.ai.KeystoreSecretCipher;
import com.ggec.earbuds.ui.ai.chat.AiChatUiMapper;
import com.ggec.earbuds.ui.ai.chat.AiChatViewModel;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 功能的装配根：应用里只有一份，创建存储、网关和对话会话，再按需造出对话页的 ViewModel。
 * 对话会话和设置仓库必须是应用级单例——生成过程不能因为界面销毁而中断。
 *
 * <p>配置界面（设置页、平台页）撤掉之后，这里不再装配平台目录、输入策略和各平台用例：
 * 它们的类还在领域层，只是当前没有界面去调用。整个 AI 功能里只有这里知道各层的具体实现。</p>
 */
public final class AiGraph {

    private static volatile AiGraph instance;

    @NonNull
    public static AiGraph get(@NonNull Context context) {
        AiGraph graph = instance;
        if (graph == null) {
            synchronized (AiGraph.class) {
                graph = instance;
                if (graph == null) {
                    graph = new AiGraph(context.getApplicationContext());
                    instance = graph;
                }
            }
        }
        return graph;
    }

    private final AiSettingsRepository settings;
    private final AiConversationRepository conversations;
    private final AiChatSession session;
    private final ViewModelProvider.Factory factory = new Factory();

    private AiGraph(Context context) {
        AiDocumentStore documents = new FileAiDocumentStore(new File(context.getFilesDir(), "ai"));
        // 密文放进不参与备份的目录：它只有在这台设备的密钥库里才解得开，恢复到别的设备上也是废的。
        AiDocumentStore secretDocuments = new FileAiDocumentStore(new File(context.getNoBackupFilesDir(), "ai"));
        SecretStore secrets = new EncryptedSecretStore(secretDocuments, new KeystoreSecretCipher());
        settings = new AiSettingsRepositoryImpl(documents, secrets, new AiSettingsJson());
        conversations = new AiConversationRepositoryImpl(documents, new AiConversationJson());

        // 网关回调用单线程，保证同一次调用的增量按顺序送达；会话自己再投回它的串行执行器处理。
        LibAiClientPool clients = new LibAiClientPool(pool("ai-callbacks", 1));
        AiClock clock = System::currentTimeMillis;
        AiIdGenerator ids = () -> UUID.randomUUID().toString();
        session = new AiChatSession(settings, conversations, new LibAiChatGateway(clients), clock, ids,
                pool("ai-chat", 1));
    }

    /** 创建对话页的 ViewModel。 */
    @NonNull
    public ViewModelProvider.Factory factory() {
        return factory;
    }

    private AiChatViewModel newChatViewModel() {
        return new AiChatViewModel(session, new AiChatUiMapper());
    }

    private static Executor pool(String name, int threads) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(task, name + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threads, threads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), threadFactory);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private final class Factory implements ViewModelProvider.Factory {

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
            if (AiChatViewModel.class.equals(modelClass)) {
                return modelClass.cast(newChatViewModel());
            }
            throw new IllegalArgumentException("Unknown model: " + modelClass.getName());
        }
    }
}
