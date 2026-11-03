package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiChatPreferences;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiFailureKind;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiRealtimeDialect;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.AiUiPreferences;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.FetchVoiceModels;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettingsPersistence;
import com.ggec.earbuds.domain.ai.usecase.SaveAiSettings;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * 设置页的展示层状态机：本地优先（local-first）+ 节流落盘 + 视图偏好与配置分离。
 *
 * <p>一次操作只有一条同步路径：<b>先改内存草稿 → 立刻发布一份新 UiState</b>；
 * 仓库写入被推迟到 {@link AiDebouncer} 的静默窗口之后，多个连续操作合并成一次。
 * 因此每个开关/下拉都不会等一次磁盘往返，也不会出现"保存中"闪一下再把页面重画三遍。</p>
 *
 * <p>三条解耦：</p>
 * <ul>
 *   <li><b>模式 ⊥ 配置</b>：当前分段是 {@link AiUiPreferences} 里的视图偏好，切换不写设置文档；</li>
 *   <li><b>状态 ⊥ 持久化</b>：持久化状态不再进 {@link AiSettingsUiState}，只用独立的
 *       {@link #saveStatus()} 通知页头，状态发布次数从每次三遍降到一遍；</li>
 *   <li><b>草稿 ⊥ 仓库回显</b>：自己写入后的仓库回调与草稿相同，直接忽略，不重算界面。</li>
 * </ul>
 */
public final class AiSettingsViewModel extends ViewModel {

    /** 连续操作的合并窗口：最后一次改动后 350ms 才真正落盘。 */
    static final long SAVE_DEBOUNCE_MILLIS = 350L;

    private final LoadAiSettings loadSettings;
    private final ObserveAiSettings observeSettings;
    private final ObserveAiSettingsPersistence observePersistence;
    private final FetchVoiceModels fetchVoiceModels;
    private final SaveAiSettings saveSettings;
    private final Executor io;
    private final LoadPlatformTemplates loadTemplates;
    private final AiUiPreferences uiPreferences;
    private final AiDebouncer debouncer;
    private final AiSettingsUiMapper mapper;

    private final MutableLiveData<AiSettingsUiState> uiState =
            new MutableLiveData<>(AiSettingsUiState.empty());
    private final OneShotLiveData<AiSettingsEvent> events = new OneShotLiveData<>();
    private final MutableLiveData<AiSettingsPersistenceStatus> saveStatus =
            new MutableLiveData<>(AiSettingsPersistenceStatus.SAVED);
    private final MutableLiveData<List<AiPlatformTemplate>> templates =
            new MutableLiveData<>(List.of());

    /** 界面看到的唯一真相（草稿）。持久化只是把它延后写进仓库。 */
    private volatile AiSettings latest = AiSettings.EMPTY;
    private AiInteractionMode displayMode = AiInteractionMode.defaultMode();
    private boolean started;
    private boolean loaded;
    private boolean loadFailed;
    private volatile boolean fetchingVoice;
    /** 尚未落盘的变更，按到达顺序组合成一个纯函数。 */
    private volatile UnaryOperator<AiSettings> pending;
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
                               @NonNull AiDebouncer debouncer,
                               @NonNull AiSettingsUiMapper mapper) {
        this.loadSettings = Objects.requireNonNull(loadSettings, "loadSettings");
        this.observeSettings = Objects.requireNonNull(observeSettings, "observeSettings");
        this.observePersistence = Objects.requireNonNull(observePersistence, "observePersistence");
        this.fetchVoiceModels = Objects.requireNonNull(fetchVoiceModels, "fetchVoiceModels");
        this.saveSettings = Objects.requireNonNull(saveSettings, "saveSettings");
        this.io = Objects.requireNonNull(io, "io");
        this.loadTemplates = Objects.requireNonNull(loadTemplates, "loadTemplates");
        this.uiPreferences = Objects.requireNonNull(uiPreferences, "uiPreferences");
        this.debouncer = Objects.requireNonNull(debouncer, "debouncer");
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

    /** 只有"保存失败"和"已恢复"需要页头展示；正常保存不闪任何提示。 */
    @NonNull
    public LiveData<AiSettingsPersistenceStatus> saveStatus() {
        return saveStatus;
    }

    @NonNull
    public LiveData<List<AiPlatformTemplate>> templates() {
        return templates;
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        subscription = observeSettings.execute(this::onExternalSettings);
        persistenceSubscription = observePersistence.execute(status -> {
            if (status == AiSettingsPersistenceStatus.FAILED
                    || status == AiSettingsPersistenceStatus.SAVED) {
                saveStatus.postValue(status);
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
            latest = settings;
            loaded = true;
            loadFailed = false;
            displayMode = uiPreferences.lastSettingsMode(settings.mode());
            publish();
        });
        io.execute(this::loadTemplates);
    }

    private void loadTemplates() {
        try {
            templates.postValue(loadTemplates.execute());
        } catch (RuntimeException unreadable) {
            templates.postValue(List.of());
        }
    }

    /**
     * 切分段只改视图偏好：不碰设置文档、不触发保存、不重建另一块面板。
     */
    public void selectMode(@NonNull AiInteractionMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (displayMode == mode) {
            return;
        }
        displayMode = mode;
        uiPreferences.setLastSettingsMode(mode);
        // 切片更新：只换 mode 与说明行，不重算平台/身份/语音分区。
        AiSettingsUiState current = uiState.getValue();
        if (current == null || !current.loaded()) {
            publish();
            return;
        }
        AiSettingsUiState next = current.withMode(mode);
        if (!Objects.equals(current, next)) {
            uiState.postValue(next);
        }
    }

    public void saveSystemPrompt(@NonNull String systemPrompt) {
        mutate(settings -> {
            AiChatPreferences next = settings.preferences().withSystemPrompt(systemPrompt);
            if (next.systemPrompt().length() > AiChatPreferences.MAX_SYSTEM_PROMPT_LENGTH) {
                throw new AiValidationException(AiValidationError.PROMPT_TOO_LONG);
            }
            return settings.withPreferences(next);
        });
    }

    public void selectVoiceModel(@NonNull String modelId) {
        AiVoiceModel model = AiVoiceModel.fromId(modelId);
        if (model == null) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
            return;
        }
        mutate(settings -> settings.withVoice(settings.voice().withModel(model)));
    }

    public void saveVoiceApiKey(@NonNull String apiKey) {
        mutate(settings -> settings.withVoice(settings.voice().withApiKey(apiKey)));
    }

    public void saveVoiceBaseUrl(@NonNull String baseUrl) {
        mutate(settings -> settings.withVoice(settings.voice().withBaseUrl(baseUrl)));
    }

    public void selectChatGptModel(@NonNull String modelId) {
        mutate(settings -> settings.withVoice(settings.voice().withRealtimeModel(modelId)));
    }

    public void selectChatGptDialect(@NonNull String dialectId) {
        AiRealtimeDialect dialect = AiRealtimeDialect.fromId(dialectId);
        if (dialect == null) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
            return;
        }
        mutate(settings -> settings.withVoice(settings.voice().withDialect(dialect)));
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

    /** 页面离开/销毁前把节流窗口内没写完的改动立即写掉。 */
    public void flushNow() {
        debouncer.cancel();
        flushPending();
    }

    /** 本地先落地一次变更，再排进节流窗口。 */
    private void mutate(@NonNull UnaryOperator<AiSettings> change) {
        if (!loaded) {
            return;
        }
        AiSettings next;
        try {
            next = change.apply(latest);
        } catch (AiValidationException invalid) {
            events.postValue(AiSettingsEvent.toast(AiMessages.validation(invalid.error())));
            return;
        } catch (IllegalArgumentException invalid) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
            return;
        }
        if (next.equals(latest)) {
            return;
        }
        latest = next;
        publish();
        enqueue(change);
    }

    private void enqueue(@NonNull UnaryOperator<AiSettings> change) {
        UnaryOperator<AiSettings> previous = pending;
        pending = previous == null ? change : settings -> change.apply(previous.apply(settings));
        debouncer.schedule(SAVE_DEBOUNCE_MILLIS, this::flushPending);
    }

    private void flushPending() {
        UnaryOperator<AiSettings> change = pending;
        pending = null;
        if (change == null) {
            return;
        }
        io.execute(() -> {
            try {
                saveSettings.execute(change);
            } catch (AiFailure failure) {
                events.postValue(AiSettingsEvent.toast(AiMessages.failure(failure.kind())));
            } catch (RuntimeException unexpected) {
                events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
            }
        });
    }

    /**
     * 仓库变化 = 外部写入（平台页等）。与自己草稿相同说明只是自己的落盘回显，直接忽略；
     * 不同才覆盖草稿，避免把用户正在编辑的内容冲掉。
     */
    private void onExternalSettings(@NonNull AiSettings settings) {
        if (loaded && settings.equals(latest)) {
            return;
        }
        // 外部写入（平台页等）进入时，把还没落盘的本地变更叠上去，避免覆盖用户刚点下的改动。
        UnaryOperator<AiSettings> undelivered = pending;
        latest = undelivered == null ? settings : undelivered.apply(settings);
        if (!loaded) {
            loaded = true;
        }
        if (fetchingVoice) {
            return;
        }
        publish();
    }

    private void publish() {
        AiSettingsUiState mapped = mapper.map(latest, displayMode);
        if (fetchingVoice) {
            mapped = mapped.withFetchingVoice(true);
        }
        if (!Objects.equals(uiState.getValue(), mapped)) {
            uiState.postValue(mapped);
        }
    }

    @Override
    protected void onCleared() {
        debouncer.cancel();
        if (subscription != null) {
            subscription.cancel();
        }
        if (persistenceSubscription != null) {
            persistenceSubscription.cancel();
        }
        flushPending();
    }
}
