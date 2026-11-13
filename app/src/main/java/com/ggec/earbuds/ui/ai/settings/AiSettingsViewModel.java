package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsChanges;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.voice.VoiceDuplex;
import com.ggec.earbuds.domain.ai.port.AiUiPreferences;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.FetchVoiceModels;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettingsPersistence;
import com.ggec.earbuds.domain.ai.usecase.SaveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SaveVoiceApiKey;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * 设置页的 MVVM 视图模型。
 *
 * <p><b>唯一数据源</b>：所有配置都来自应用级的 {@code AiSettingsRepository}——设置页不维护
 * 第二份草稿，也不自己排队落盘。一次操作就是 {@code SaveAiSettings.execute(change)}：
 * 仓库在内存里同步提交并通知，界面状态从这条通知重算；磁盘写由仓库自己的串行执行器合并完成。</p>
 *
 * <p><b>视图偏好分离</b>：当前分段只存 {@code AiUiPreferences}，切换不写配置文档。</p>
 *
 * <p><b>单一状态流</b>：持久化状态（保存中/失败/已恢复）与「可添加的平台模板」都并入
 * {@link AiSettingsUiState}，页面只观察一条 LiveData；一次性提示仍走独立事件流。</p>
 */
public final class AiSettingsViewModel extends ViewModel {

    private final LoadAiSettings loadSettings;
    private final ObserveAiSettings observeSettings;
    private final ObserveAiSettingsPersistence observePersistence;
    private final FetchVoiceModels fetchVoiceModels;
    private final SaveAiSettings saveSettings;
    private final Executor io;
    private final LoadPlatformTemplates loadTemplates;
    private final AiUiPreferences uiPreferences;
    private final SaveVoiceApiKey saveVoiceKey;
    private final AiSettingsUiMapper mapper;

    private final MutableLiveData<AiSettingsUiState> uiState =
            new MutableLiveData<>(AiSettingsUiState.empty());
    private final OneShotLiveData<AiSettingsEvent> events = new OneShotLiveData<>();

    private volatile AiSettingsPersistenceStatus persistence = AiSettingsPersistenceStatus.SAVED;
    private volatile List<AiPlatformTemplate> templates = List.of();

    private volatile AiSettings latest = AiSettings.EMPTY;
    private AiInteractionMode displayMode = AiInteractionMode.defaultMode();
    private boolean started;
    private boolean loaded;
    private boolean loadFailed;
    private volatile boolean fetchingVoice;
    private Subscription subscription;
    private Subscription persistenceSubscription;

    public AiSettingsViewModel(@NonNull LoadAiSettings loadSettings,
                               @NonNull ObserveAiSettings observeSettings,
                               @NonNull ObserveAiSettingsPersistence observePersistence,
                               @NonNull FetchVoiceModels fetchVoiceModels,
                               @NonNull SaveAiSettings saveSettings,
                               @NonNull Executor io,
                               @NonNull LoadPlatformTemplates loadTemplates,
                               @NonNull AiUiPreferences uiPreferences,
                               @NonNull SaveVoiceApiKey saveVoiceKey,
                               @NonNull AiSettingsUiMapper mapper) {
        this.loadSettings = Objects.requireNonNull(loadSettings, "loadSettings");
        this.observeSettings = Objects.requireNonNull(observeSettings, "observeSettings");
        this.observePersistence = Objects.requireNonNull(observePersistence, "observePersistence");
        this.fetchVoiceModels = Objects.requireNonNull(fetchVoiceModels, "fetchVoiceModels");
        this.saveSettings = Objects.requireNonNull(saveSettings, "saveSettings");
        this.io = Objects.requireNonNull(io, "io");
        this.loadTemplates = Objects.requireNonNull(loadTemplates, "loadTemplates");
        this.uiPreferences = Objects.requireNonNull(uiPreferences, "uiPreferences");
        this.saveVoiceKey = Objects.requireNonNull(saveVoiceKey, "saveVoiceKey");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @NonNull
    public LiveData<AiSettingsUiState> uiState() {
        return uiState;
    }

    @NonNull
    public LiveData<AiSettingsEvent> events() {
        return events;
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        subscription = observeSettings.execute(this::onSettings);
        persistenceSubscription = observePersistence.execute(status -> {
            if (status == AiSettingsPersistenceStatus.FAILED
                    || status == AiSettingsPersistenceStatus.SAVED) {
                persistence = status;
                publish();
            }
        });
        loadSettings.executeAsync().whenComplete((settings, failure) -> {
            if (failure != null) {
                loadFailed = true;
                loaded = false;
                AiSettingsUiState current = uiState.getValue();
                uiState.postValue(current == null
                        ? AiSettingsUiState.empty().failedLoading()
                        : current.failedLoading());
                events.postValue(AiSettingsEvent.toast(AiMessages.failure(AiFailureKind.STORAGE)));
                return;
            }
            onSettings(settings);
            displayMode = uiPreferences.lastSettingsMode(AiInteractionMode.defaultMode());
            publish();
        });
        io.execute(this::loadTemplates);
    }

    private void loadTemplates() {
        try {
            templates = loadTemplates.execute();
        } catch (RuntimeException unreadable) {
            templates = List.of();
        }
        publish();
    }

    /** 切分段只改视图偏好：不碰设置文档、不触发保存。 */
    public void selectMode(@NonNull AiInteractionMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (displayMode == mode) {
            return;
        }
        displayMode = mode;
        uiPreferences.setLastSettingsMode(mode);
        AiSettingsUiState current = uiState.getValue();
        if (current != null && current.loaded()) {
            AiSettingsUiState next = current.withMode(mode);
            if (!Objects.equals(current, next)) {
                uiState.postValue(next);
            }
        }
    }

    public void saveSystemPrompt(@NonNull String systemPrompt) {
        apply(AiSettingsChanges.systemPrompt(systemPrompt));
    }

    public void selectVoiceModel(@NonNull String modelId) {
        AiVoiceModel model = AiVoiceModel.fromId(modelId);
        if (model == null) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
            return;
        }
        apply(AiSettingsChanges.voiceModel(model));
    }

    public void saveVoiceApiKey(@NonNull String apiKey) {
        saveVoiceKey.execute(latest.voice().model(), apiKey);
    }

    public void saveVoiceBaseUrl(@NonNull String baseUrl) {
        apply(AiSettingsChanges.voiceBaseUrl(baseUrl));
    }

    public void selectRealtimeModel(@NonNull String modelId) {
        apply(AiSettingsChanges.voiceRealtimeModel(modelId));
    }

    public void selectDefaultDuplex(@NonNull String duplexId) {
        VoiceDuplex duplex = VoiceDuplex.fromId(duplexId);
        if (duplex == null) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
            return;
        }
        apply(AiSettingsChanges.voiceDefaultDuplex(duplex));
    }

    public void fetchVoiceModels() {
        if (fetchingVoice) {
            return;
        }
        fetchingVoice = true;
        publish();
        events.postValue(AiSettingsEvent.toast(R.string.ai_settings_fetching));
        io.execute(() -> {
            try {
                fetchVoiceModels.execute();
            } catch (AiFailure failure) {
                events.postValue(AiSettingsEvent.toast(AiMessages.failure(failure.kind())));
            } catch (IllegalArgumentException invalid) {
                events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
            } finally {
                fetchingVoice = false;
                publish();
            }
        });
    }

    /** 唯一写入口：仓库在内存里提交并同步通知，界面状态由 {@link #onSettings} 统一重算。 */
    private void apply(@NonNull UnaryOperator<AiSettings> change) {
        if (!loaded) {
            return;
        }
        try {
            saveSettings.execute(change);
        } catch (AiValidationException invalid) {
            events.postValue(AiSettingsEvent.toast(AiMessages.validation(invalid.error())));
        } catch (AiFailure failure) {
            events.postValue(AiSettingsEvent.toast(AiMessages.failure(failure.kind())));
        } catch (IllegalArgumentException invalid) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
        }
    }

    private void onSettings(@NonNull AiSettings settings) {
        latest = settings;
        if (!loaded) {
            loaded = true;
            loadFailed = false;
        }
        if (fetchingVoice) {
            return;
        }
        publish();
    }

    private void publish() {
        AiSettingsUiState mapped = mapper.map(latest, displayMode, loaded)
                .withPersistence(persistence)
                .withTemplates(templates);
        if (fetchingVoice) {
            mapped = mapped.withFetchingVoice(true);
        }
        if (!Objects.equals(uiState.getValue(), mapped)) {
            uiState.postValue(mapped);
        }
    }

    @Override
    protected void onCleared() {
        if (subscription != null) {
            subscription.cancel();
        }
        if (persistenceSubscription != null) {
            persistenceSubscription.cancel();
        }
    }
}
