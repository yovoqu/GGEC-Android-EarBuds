package com.ggec.earbuds.wiring;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.ggec.earbuds.data.ai.conversation.AiConversationRepositoryImpl;
import com.ggec.earbuds.data.ai.gateway.LibAiChatGateway;
import com.ggec.earbuds.data.ai.gateway.LibAiChatGptRealtimeGateway;
import com.ggec.earbuds.data.ai.gateway.LibAiModelGateway;
import com.ggec.earbuds.data.ai.gateway.LibAiPlatformCatalog;
import com.ggec.earbuds.data.ai.gateway.LibAiSpeedTestGateway;
import com.ggec.earbuds.data.ai.gateway.LibAiClientPool;
import com.ggec.earbuds.data.ai.json.AiConversationJson;
import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.settings.AiSettingsRepositoryImpl;
import com.ggec.earbuds.data.ai.store.AiDocumentStore;
import com.ggec.earbuds.data.ai.store.EncryptedSecretStore;
import com.ggec.earbuds.data.ai.store.FileAiDocumentStore;
import com.ggec.earbuds.data.ai.store.SecretStore;
import com.ggec.earbuds.data.ai.voice.RealtimeLinkAdapter;
import com.ggec.earbuds.data.ai.voice.RealtimeVoiceSession;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;
import com.ggec.earbuds.domain.ai.port.AiIdGenerator;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.policy.AiModelIdPolicy;
import com.ggec.earbuds.domain.ai.usecase.AddManualModel;
import com.ggec.earbuds.domain.ai.usecase.FetchChatGptRealtimeModels;
import com.ggec.earbuds.domain.ai.usecase.FetchPlatformModels;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.ListConversations;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.RemoveModel;
import com.ggec.earbuds.domain.ai.usecase.RemovePlatform;
import com.ggec.earbuds.domain.ai.usecase.RunSpeedTest;
import com.ggec.earbuds.domain.ai.usecase.SaveChatPreferences;
import com.ggec.earbuds.domain.ai.usecase.SavePlatform;
import com.ggec.earbuds.domain.ai.usecase.SaveVoiceApiKey;
import com.ggec.earbuds.domain.ai.usecase.SelectInteractionMode;
import com.ggec.earbuds.domain.ai.usecase.SelectModel;
import com.ggec.earbuds.domain.ai.usecase.SelectVoiceModel;
import com.ggec.earbuds.domain.ai.usecase.UpdateChatGptRealtime;
import com.ggec.earbuds.platform.ai.KeystoreSecretCipher;
import com.ggec.earbuds.platform.ai.voice.AndroidAudioCapture;
import com.ggec.earbuds.platform.ai.voice.AndroidAudioPlayback;
import com.ggec.earbuds.ui.ai.call.AiCallViewModel;
import com.ggec.earbuds.ui.ai.call.AiVoiceSessionFactory;
import com.ggec.earbuds.ui.ai.chat.AiChatUiMapper;
import com.ggec.earbuds.ui.ai.settings.AiPlatformUiMapper;
import com.ggec.earbuds.ui.ai.settings.AiPlatformViewModel;
import com.ggec.earbuds.ui.ai.settings.AiSettingsUiMapper;
import com.ggec.earbuds.ui.ai.settings.AiSettingsViewModel;
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
 * <p>配置界面（设置页、平台页）与历史面板各有自己的装配；整个 AI 功能里只有这里知道各层的具体实现，
 * 语音的采集、播放与实时链路也都在这里选定——界面层只拿端口。</p>
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
    private final Context context;
    private final AiClock clock;
    private final Executor voiceState;
    /** 设置页与平台页共用的几个依赖：存储、网关池、平台目录、id 生成器。 */
    private final AiDocumentStore documents;
    private final LibAiClientPool clients;
    private final LibAiPlatformCatalog catalog = new LibAiPlatformCatalog();
    private final AiIdGenerator ids;
    private final ViewModelProvider.Factory factory = new Factory();

    private AiGraph(Context context) {
        this.context = context;
        AiClock clock = System::currentTimeMillis;
        this.clock = clock;
        this.voiceState = pool("ai-voice", 1);
        this.documents = new FileAiDocumentStore(new File(context.getFilesDir(), "ai"));
        // 密文放进不参与备份的目录：它只有在这台设备的密钥库里才解得开，恢复到别的设备上也是废的。
        AiDocumentStore secretDocuments = new FileAiDocumentStore(new File(context.getNoBackupFilesDir(), "ai"));
        SecretStore secrets = new EncryptedSecretStore(secretDocuments, new KeystoreSecretCipher());
        settings = new AiSettingsRepositoryImpl(documents, secrets, new AiSettingsJson());
        conversations = new AiConversationRepositoryImpl(documents, new AiConversationJson());

        // 网关回调用单线程，保证同一次调用的增量按顺序送达；会话自己再投回它的串行执行器处理。
        clients = new LibAiClientPool(pool("ai-callbacks", 1));
        ids = () -> UUID.randomUUID().toString();
        session = new AiChatSession(settings, conversations, new LibAiChatGateway(clients), clock, ids,
                pool("ai-chat", 1));
    }

    /** 创建对话页的 ViewModel。 */
    @NonNull
    public ViewModelProvider.Factory factory() {
        return factory;
    }

    /** 历史面板要用的两个东西：会话本身（所有会话操作在它上面）与列表读取用例。 */
    @NonNull
    public AiChatSession chatSession() {
        return session;
    }

    @NonNull
    public ListConversations listConversations() {
        return new ListConversations(conversations);
    }

    private AiChatViewModel newChatViewModel() {
        return new AiChatViewModel(session, new AiChatUiMapper());
    }

    /**
     * 通话页的会话工厂。每次通话新建采集与播放（它们持有音频设备）；
     * 所有状态合并都投到同一条串行线程，否则一次朗读会把"打断"排在它后面才生效。
     */
    @NonNull
    public AiVoiceSessionFactory voiceSessions() {
        return () -> new RealtimeVoiceSession(settings,
                new AndroidAudioCapture(context), new AndroidAudioPlayback(context),
                new RealtimeLinkAdapter(), clock, voiceState);
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
            if (AiCallViewModel.class.equals(modelClass)) {
                return modelClass.cast(new AiCallViewModel(voiceSessions()));
            }
            if (AiSettingsViewModel.class.equals(modelClass)) {
                return modelClass.cast(new AiSettingsViewModel(
                        new LoadAiSettings(settings), new ObserveAiSettings(settings),
                        new SaveChatPreferences(settings), new SelectInteractionMode(settings),
                        new SelectVoiceModel(settings), new SaveVoiceApiKey(settings),
                        new UpdateChatGptRealtime(settings),
                        new FetchChatGptRealtimeModels(settings, new LibAiChatGptRealtimeGateway()),
                        pool("ai-chatgpt-realtime", 1),
                        new LoadPlatformTemplates(catalog),
                        new AiSettingsUiMapper()));
            }
            if (AiPlatformViewModel.class.equals(modelClass)) {
                return modelClass.cast(new AiPlatformViewModel(
                        new LoadAiSettings(settings), new ObserveAiSettings(settings),
                        new LoadPlatformTemplates(catalog),
                        new SavePlatform(settings, catalog, new AiApiKeyPolicy(),
                                new AiBaseUrlPolicy(catalog), ids),
                        new FetchPlatformModels(settings, new LibAiModelGateway(clients), clock),
                        new AddManualModel(settings, new AiModelIdPolicy()),
                        new RemoveModel(settings), new SelectModel(settings),
                        new RunSpeedTest(settings, new LibAiSpeedTestGateway(clients), clock),
                        new RemovePlatform(settings),
                        pool("ai-settings", 2), new AiPlatformUiMapper()));
            }
            throw new IllegalArgumentException("Unknown model: " + modelClass.getName());
        }
    }
}
