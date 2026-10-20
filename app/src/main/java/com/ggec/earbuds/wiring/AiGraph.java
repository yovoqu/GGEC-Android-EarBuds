package com.ggec.earbuds.wiring;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.SavedStateHandleSupport;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.ggec.earbuds.data.ai.conversation.AiConversationRepositoryImpl;
import com.ggec.earbuds.data.ai.gateway.LibAiChatGateway;
import com.ggec.earbuds.data.ai.gateway.LibAiClientPool;
import com.ggec.earbuds.data.ai.gateway.LibAiModelGateway;
import com.ggec.earbuds.data.ai.gateway.LibAiPlatformCatalog;
import com.ggec.earbuds.data.ai.gateway.LibAiSpeedTestGateway;
import com.ggec.earbuds.data.ai.json.AiConversationJson;
import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.settings.AiSettingsRepositoryImpl;
import com.ggec.earbuds.data.ai.store.AiDocumentStore;
import com.ggec.earbuds.data.ai.store.EncryptedSecretStore;
import com.ggec.earbuds.data.ai.store.FileAiDocumentStore;
import com.ggec.earbuds.data.ai.store.SecretStore;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.policy.AiModelIdPolicy;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;
import com.ggec.earbuds.domain.ai.port.AiIdGenerator;
import com.ggec.earbuds.domain.ai.port.AiPlatformCatalog;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.usecase.AddManualModel;
import com.ggec.earbuds.domain.ai.usecase.FetchPlatformModels;
import com.ggec.earbuds.domain.ai.usecase.ListConversations;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.RemoveModel;
import com.ggec.earbuds.domain.ai.usecase.RemovePlatform;
import com.ggec.earbuds.domain.ai.usecase.RunSpeedTest;
import com.ggec.earbuds.domain.ai.usecase.SaveChatPreferences;
import com.ggec.earbuds.domain.ai.usecase.SavePlatform;
import com.ggec.earbuds.domain.ai.usecase.SelectModel;
import com.ggec.earbuds.platform.ai.KeystoreSecretCipher;
import com.ggec.earbuds.ui.ai.chat.AiChatUiMapper;
import com.ggec.earbuds.ui.ai.chat.AiChatViewModel;
import com.ggec.earbuds.ui.ai.chat.AiModelSheetMapper;
import com.ggec.earbuds.ui.ai.settings.AiPlatformServices;
import com.ggec.earbuds.ui.ai.settings.AiPlatformUiMapper;
import com.ggec.earbuds.ui.ai.settings.AiPlatformViewModel;
import com.ggec.earbuds.ui.ai.settings.AiSettingsUiMapper;
import com.ggec.earbuds.ui.ai.settings.AiSettingsViewModel;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 功能的装配根：应用里只有一份，创建全部的存储、网关、用例和对话会话，再按需造出各页的 ViewModel。
 * 对话会话和设置仓库必须是应用级单例——生成过程不能因为界面销毁而中断，几个页面看到的设置也必须是同一份。
 * 整个 AI 功能里只有这里知道各层的具体实现。
 */
public final class AiGraph {

    /** 平台详情页的启动参数：要打开的平台标识，内置平台就是它的模板标识。 */
    public static final String ARG_PLATFORM_ID = "com.ggec.earbuds.ai.platform_id";

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

    private final Executor io = pool("ai-io", 4);
    private final AiSettingsRepository settings;
    private final AiConversationRepository conversations;
    private final AiChatSession session;
    private final AiPlatformCatalog catalog = new LibAiPlatformCatalog();
    private final AiApiKeyPolicy keyPolicy = new AiApiKeyPolicy();
    private final AiBaseUrlPolicy urlPolicy = new AiBaseUrlPolicy(catalog);
    private final AiModelIdPolicy modelIdPolicy = new AiModelIdPolicy();
    private final AiPlatformServices platformServices;
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

        platformServices = new AiPlatformServices(
                new LoadAiSettings(settings),
                new ObserveAiSettings(settings),
                new LoadPlatformTemplates(catalog),
                new SavePlatform(settings, catalog, keyPolicy, urlPolicy, ids),
                new FetchPlatformModels(settings, new LibAiModelGateway(clients), clock),
                new AddManualModel(settings, modelIdPolicy),
                new RemoveModel(settings),
                new RemovePlatform(settings),
                new SelectModel(settings),
                new RunSpeedTest(settings, new LibAiSpeedTestGateway(clients), clock),
                keyPolicy,
                urlPolicy,
                modelIdPolicy);
    }

    /** 创建对话页、设置总览页、平台详情页的 ViewModel；平台详情页从启动参数里取平台标识。 */
    @NonNull
    public ViewModelProvider.Factory factory() {
        return factory;
    }

    private AiChatViewModel newChatViewModel() {
        return new AiChatViewModel(
                session,
                new ListConversations(conversations),
                platformServices.loadSettings(),
                platformServices.selectModel(),
                new AiChatUiMapper(),
                new AiModelSheetMapper(),
                io);
    }

    private AiSettingsViewModel newSettingsViewModel() {
        return new AiSettingsViewModel(
                platformServices.loadSettings(),
                platformServices.observeSettings(),
                platformServices.loadTemplates(),
                platformServices.savePlatform(),
                new SaveChatPreferences(settings),
                session,
                keyPolicy,
                urlPolicy,
                new AiSettingsUiMapper(),
                io);
    }

    private AiPlatformViewModel newPlatformViewModel(String platformId) {
        return new AiPlatformViewModel(platformId, platformServices, new AiPlatformUiMapper(), io);
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
            if (AiSettingsViewModel.class.equals(modelClass)) {
                return modelClass.cast(newSettingsViewModel());
            }
            if (AiPlatformViewModel.class.equals(modelClass)) {
                SavedStateHandle savedState = SavedStateHandleSupport.createSavedStateHandle(extras);
                String platformId = savedState.get(ARG_PLATFORM_ID);
                if (platformId == null || platformId.isEmpty()) {
                    throw new IllegalArgumentException("Missing launch argument " + ARG_PLATFORM_ID);
                }
                return modelClass.cast(newPlatformViewModel(platformId));
            }
            throw new IllegalArgumentException("Unknown model: " + modelClass.getName());
        }
    }
}
