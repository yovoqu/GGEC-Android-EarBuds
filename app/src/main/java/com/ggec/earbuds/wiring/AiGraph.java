package com.ggec.earbuds.wiring;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.ggec.earbuds.provider.lib_ai.LibAiProvider;
import com.ggec.earbuds.data.ai.conversation.AiConversationRepositoryImpl;
import com.ggec.earbuds.provider.lib_ai.LibAiChatGateway;
import com.ggec.earbuds.provider.lib_ai.LibAiClientPool;
import com.ggec.earbuds.provider.lib_ai.LibAiModelGateway;
import com.ggec.earbuds.provider.lib_ai.LibAiPlatformCatalog;
import com.ggec.earbuds.provider.lib_ai.LibAiSpeedTestGateway;
import com.ggec.earbuds.provider.lib_ai.LibAiVoiceModelGateway;
import com.ggec.earbuds.data.ai.json.AiConversationJson;
import com.ggec.earbuds.data.ai.json.AiSettingsJson;
import com.ggec.earbuds.data.ai.settings.AiSettingsRepositoryImpl;
import com.ggec.earbuds.data.ai.store.AiDocumentStore;
import com.ggec.earbuds.data.ai.store.EncryptedCredentialStore;
import com.ggec.earbuds.data.ai.store.EncryptedSecretStore;
import com.ggec.earbuds.data.ai.store.FileAiDocumentStore;
import com.ggec.earbuds.data.ai.store.SecretStore;
import com.ggec.earbuds.provider.lib_ai.RealtimeLinkAdapter;
import com.ggec.earbuds.data.ai.voice.RealtimeVoiceSession;
import com.ggec.earbuds.domain.ai.chat.AiChatSession;
import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.policy.AiModelIdPolicy;
import com.ggec.earbuds.domain.ai.port.AiAudioSourceCatalog;
import com.ggec.earbuds.domain.ai.port.AiClock;
import com.ggec.earbuds.domain.ai.port.AiConversationRepository;
import com.ggec.earbuds.domain.ai.port.AiCredentialStore;
import com.ggec.earbuds.domain.ai.port.AiIdGenerator;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;
import com.ggec.earbuds.domain.ai.usecase.AddManualModel;
import com.ggec.earbuds.domain.ai.usecase.FetchPlatformModels;
import com.ggec.earbuds.domain.ai.usecase.FetchVoiceModels;
import com.ggec.earbuds.domain.ai.usecase.ListConversations;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettingsPersistence;
import com.ggec.earbuds.domain.ai.usecase.RemoveModel;
import com.ggec.earbuds.domain.ai.usecase.RemovePlatform;
import com.ggec.earbuds.domain.ai.usecase.RunSpeedTest;
import com.ggec.earbuds.domain.ai.usecase.SaveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SavePlatform;
import com.ggec.earbuds.domain.ai.usecase.SelectModel;
import com.ggec.earbuds.platform.ai.KeystoreSecretCipher;
import com.ggec.earbuds.platform.ai.prefs.SharedPreferencesAiUiPreferences;
import com.ggec.earbuds.platform.ai.voice.AndroidAudioPlayback;
import com.ggec.earbuds.platform.ai.voice.AndroidAudioSourceCatalog;
import com.ggec.earbuds.ui.ai.call.AiCallViewModel;
import com.ggec.earbuds.ui.ai.call.AiVoiceSessionFactory;
import com.ggec.earbuds.ui.ai.chat.AiChatUiMapper;
import com.ggec.earbuds.ui.ai.chat.AiChatViewModel;
import com.ggec.earbuds.ui.ai.history.AiHistoryUiMapper;
import com.ggec.earbuds.ui.ai.history.AiHistoryViewModel;
import com.ggec.earbuds.ui.ai.settings.AiPlatformUiMapper;
import com.ggec.earbuds.ui.ai.settings.AiPlatformViewModel;
import com.ggec.earbuds.ui.ai.settings.AiSettingsUiMapper;
import com.ggec.earbuds.ui.ai.settings.AiSettingsViewModel;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * AI 功能的装配根：应用里只有一份，创建存储、网关和对话会话，再按需造出对话页的 ViewModel。
 * 对话会话和设置仓库必须是应用级单例——生成过程不能因为界面销毁而中断。
 *
 * <p>整个 AI 功能里只有这里知道各层的具体实现：音频输入源目录、播放、实时链路都在此装配并注入端口。</p>
 */
public final class AiGraph {

    private final AiSettingsRepository settings;
    private final AiCredentialStore credentials;
    private final AiConversationRepository conversations;
    private final AiChatSession session;
    private final Context context;
    private final AiClock clock;
    private final Executor voiceState;
    /** 历史列表读取单独一条串行执行器；不与会话写盘、网络或音频线程共享。 */
    private final Executor historyIo;
    private final Executor settingsOperation;
    private final Executor realtimeFetch;
    private final AiDocumentStore documents;
    private final LibAiClientPool clients;
    private final LibAiPlatformCatalog catalog = new LibAiPlatformCatalog();
    private final AndroidAudioSourceCatalog audioSourceCatalog;
    private final AiIdGenerator ids;
    private final ViewModelProvider.Factory factory = new Factory();

    public AiGraph(@NonNull Context context, @NonNull AppExecutors executors) {
        this.context = context;
        LibAiProvider.install(context);
        AiClock clock = System::currentTimeMillis;
        this.clock = clock;
        this.voiceState = executors.voiceState();
        this.historyIo = executors.historyIo();
        this.documents = new FileAiDocumentStore(new File(context.getFilesDir(), "ai"));
        AiDocumentStore secretDocuments = new FileAiDocumentStore(new File(context.getNoBackupFilesDir(), "ai"));
        SecretStore secrets = new EncryptedSecretStore(secretDocuments, new KeystoreSecretCipher());
        this.credentials = new EncryptedCredentialStore(secrets);
        settings = new AiSettingsRepositoryImpl(
                documents, secrets, new AiSettingsJson(), executors.settingsPersistence());
        conversations = new AiConversationRepositoryImpl(documents, new AiConversationJson());

        clients = new LibAiClientPool(executors.aiCallbacks());
        ids = () -> UUID.randomUUID().toString();
        session = new AiChatSession(settings, conversations, new LibAiChatGateway(clients, credentials), clock, ids,
                executors.chatState());
        this.settingsOperation = executors.settingsOperation();
        this.realtimeFetch = executors.realtimeFetch();
        this.audioSourceCatalog = new AndroidAudioSourceCatalog(context);
    }

    /** 创建对话页的 ViewModel。 */
    @NonNull
    public ViewModelProvider.Factory factory() {
        return factory;
    }

    /** 音频输入源目录：供外部模块（如耳机 BLE/SPP 自定义语音通道）注册/注销自定义输入源。 */
    @NonNull
    public AiAudioSourceCatalog audioSourceCatalog() {
        return audioSourceCatalog;
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

    /** 每次打开历史面板创建一份页面级 ViewModel；面板关闭时由面板释放。 */
    @NonNull
    public AiHistoryViewModel historyViewModel() {
        return new AiHistoryViewModel(session, listConversations(), historyIo,
                clock, new AiHistoryUiMapper());
    }

    private AiChatViewModel newChatViewModel() {
        return new AiChatViewModel(session, new AiChatUiMapper());
    }

    /**
     * 通话页的会话工厂。音频输入通过共享 {@link #audioSourceCatalog} 按源热插拔；
     * 播放与链路按次新建并由通话执行器串行调度。
     */
    @NonNull
    public AiVoiceSessionFactory voiceSessions() {
        return () -> new RealtimeVoiceSession(settings, credentials,
                audioSourceCatalog, new AndroidAudioPlayback(context),
                new RealtimeLinkAdapter(), clock, voiceState);
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
                        new ObserveAiSettingsPersistence(settings),
                        new FetchVoiceModels(settings, new LibAiVoiceModelGateway(), credentials),
                        new SaveAiSettings(settings),
                        realtimeFetch,
                        new LoadPlatformTemplates(catalog),
                        new SharedPreferencesAiUiPreferences(context),
                        credentials,
                        new AiSettingsUiMapper()));
            }
            if (AiPlatformViewModel.class.equals(modelClass)) {
                return modelClass.cast(new AiPlatformViewModel(
                        new LoadAiSettings(settings), new ObserveAiSettings(settings),
                        new LoadPlatformTemplates(catalog),
                        new SavePlatform(settings, catalog, new AiApiKeyPolicy(),
                                new AiBaseUrlPolicy(catalog), ids, credentials),
                        new FetchPlatformModels(settings, new LibAiModelGateway(clients, credentials), clock),
                        new AddManualModel(settings, new AiModelIdPolicy()),
                        new RemoveModel(settings), new SelectModel(settings),
                        new RunSpeedTest(settings, new LibAiSpeedTestGateway(clients, credentials), clock),
                        new RemovePlatform(settings),
                        settingsOperation, new AiPlatformUiMapper()));
            }
            throw new IllegalArgumentException("Unknown model: " + modelClass.getName());
        }
    }
}
