package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.FetchChatGptRealtimeModels;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SaveChatPreferences;
import com.ggec.earbuds.domain.ai.usecase.SaveVoiceApiKey;
import com.ggec.earbuds.domain.ai.usecase.SelectInteractionMode;
import com.ggec.earbuds.domain.ai.usecase.SelectVoiceModel;
import com.ggec.earbuds.domain.ai.usecase.UpdateChatGptRealtime;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 设置总览页的 ViewModel：只经用例读写设置，把结果交给 {@link AiSettingsUiMapper} 变成界面状态。
 *
 * <p>读设置是同步的。拉取 ChatGPT 实时模型是例外：它走网络，必须丢到 {@code io} 上。</p>
 */
public final class AiSettingsViewModel extends ViewModel {

    private final LoadAiSettings loadSettings;
    private final ObserveAiSettings observeSettings;
    private final SaveChatPreferences savePreferences;
    private final SelectInteractionMode selectMode;
    private final SelectVoiceModel selectVoiceModel;
    private final SaveVoiceApiKey saveVoiceApiKey;
    private final UpdateChatGptRealtime updateChatGpt;
    private final FetchChatGptRealtimeModels fetchRealtime;
    private final Executor io;
    private final LoadPlatformTemplates loadTemplates;
    private final AiSettingsUiMapper mapper;

    private final MutableLiveData<AiSettingsUiState> uiState = new MutableLiveData<>(AiSettingsUiState.empty());
    private final OneShotLiveData<AiSettingsEvent> events = new OneShotLiveData<>();

    private volatile AiSettings latest = AiSettings.EMPTY;
    private volatile List<AiPlatformTemplate> templates = List.of();
    private Subscription subscription;
    private boolean started;
    private volatile boolean fetchingRealtime;

    public AiSettingsViewModel(@NonNull LoadAiSettings loadSettings,
                               @NonNull ObserveAiSettings observeSettings,
                               @NonNull SaveChatPreferences savePreferences,
                               @NonNull SelectInteractionMode selectMode,
                               @NonNull SelectVoiceModel selectVoiceModel,
                               @NonNull SaveVoiceApiKey saveVoiceApiKey,
                               @NonNull UpdateChatGptRealtime updateChatGpt,
                               @NonNull FetchChatGptRealtimeModels fetchRealtime,
                               @NonNull Executor io,
                               @NonNull LoadPlatformTemplates loadTemplates,
                               @NonNull AiSettingsUiMapper mapper) {
        this.loadSettings = Objects.requireNonNull(loadSettings, "loadSettings");
        this.observeSettings = Objects.requireNonNull(observeSettings, "observeSettings");
        this.savePreferences = Objects.requireNonNull(savePreferences, "savePreferences");
        this.selectMode = Objects.requireNonNull(selectMode, "selectMode");
        this.selectVoiceModel = Objects.requireNonNull(selectVoiceModel, "selectVoiceModel");
        this.saveVoiceApiKey = Objects.requireNonNull(saveVoiceApiKey, "saveVoiceApiKey");
        this.updateChatGpt = Objects.requireNonNull(updateChatGpt, "updateChatGpt");
        this.fetchRealtime = Objects.requireNonNull(fetchRealtime, "fetchRealtime");
        this.io = Objects.requireNonNull(io, "io");
        this.loadTemplates = Objects.requireNonNull(loadTemplates, "loadTemplates");
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

    /** 可以添加的平台模板；「添加平台」对话框按它列候选。 */
    @NonNull
    public List<AiPlatformTemplate> templates() {
        return templates;
    }

    /**
     * 进页面时调一次。配置页在返回后可能被重建，所以这里幂等：已经开始了就什么都不做。
     */
    public void start() {
        if (started) {
            return;
        }
        started = true;
        try {
            templates = loadTemplates.execute();
        } catch (RuntimeException unreadable) {
            // 模板表是编译期固定的，读不出来就没有可添加的平台；设置本身照常显示。
            templates = List.of();
        }
        onSettings(loadSettings.execute());
        subscription = observeSettings.execute(this::onSettings);
    }

    /**
     * 切换分段只记住"下次进来停在哪个模式"，不要求另一模式已经配好：
     * 用户可以先切到语音模式，再去添加实时模型。
     */
    public void selectMode(@NonNull AiInteractionMode mode) {
        guard(() -> selectMode.execute(mode));
    }

    /** 回答风格现在只有这一项可改：提示词写什么，模型就按什么口径回答。 */
    public void saveSystemPrompt(@NonNull String systemPrompt) {
        guard(() -> savePreferences.update(preferences -> preferences.withSystemPrompt(systemPrompt)));
    }

    /**
     * 切换语音模型。只认预设表里的 id：界面传上来的是下拉项，不是用户手写的模型名，
     * 所以这里把未知 id 当成"界面状态过期"，提示一句而不是静默改掉用户的选择。
     */
    public void selectVoiceModel(@NonNull String modelId) {
        AiVoiceModel model = AiVoiceModel.fromId(modelId);
        if (model == null) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
            return;
        }
        guard(() -> selectVoiceModel.execute(model));
    }

    /** 保存当前语音模型的 Key；空串表示清掉这一家的 Key。 */
    public void saveVoiceApiKey(@NonNull String apiKey) {
        guard(() -> saveVoiceApiKey.execute(apiKey));
    }

    /** 改 ChatGPT 的接口地址。空串恢复官方主机。不合法的地址只提示，不写入。 */
    public void saveVoiceBaseUrl(@NonNull String baseUrl) {
        guard(() -> updateChatGpt.baseUrl(baseUrl));
    }

    /** 从已拉取的实时模型里选一个。列表里没有的 id 不写入。 */
    public void selectChatGptModel(@NonNull String modelId) {
        guard(() -> updateChatGpt.model(modelId));
    }

    /** 选择 ChatGPT 的线路方言。未知 id 不写入。 */
    public void selectChatGptDialect(@NonNull String dialectId) {
        guard(() -> updateChatGpt.dialect(dialectId));
    }

    /** 用当前地址和 Key 拉取 ChatGPT 实时模型。进行中再点一次会被忽略。 */
    public void fetchChatGptModels() {
        if (fetchingRealtime) {
            return;
        }
        fetchingRealtime = true;
        publish();
        events.postValue(AiSettingsEvent.toast(R.string.ai_settings_fetching));
        io.execute(() -> {
            try {
                fetchRealtime.execute();
            } catch (AiFailure failure) {
                events.postValue(AiSettingsEvent.toast(AiMessages.failure(failure.kind())));
            } catch (IllegalArgumentException invalid) {
                events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
            } finally {
                fetchingRealtime = false;
                publish();
            }
        });
    }

    private void onSettings(AiSettings settings) {
        latest = settings;
        publish();
    }

    private void publish() {
        AiSettingsUiState mapped = mapper.map(latest);
        if (fetchingRealtime) {
            mapped = new AiSettingsUiState(mapped.loaded(), mapped.mode(), mapped.modeHint(),
                    mapped.platforms(), mapped.identity(), mapped.voice().fetching(true), mapped.style());
        }
        uiState.postValue(mapped);
    }

    /**
     * 用例把"输入不合法"和"平台返回失败"分开报，界面也分开显示：
     * 前者是这个字段没填对，后者是网络或账号那边的事。
     */
    private void guard(@NonNull Runnable change) {
        try {
            change.run();
        } catch (AiValidationException invalid) {
            events.postValue(AiSettingsEvent.toast(AiMessages.validation(invalid.error())));
        } catch (AiFailure failure) {
            events.postValue(AiSettingsEvent.toast(AiMessages.failure(failure.kind())));
        } catch (IllegalArgumentException invalid) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_value_invalid));
        }
    }

    @Override
    protected void onCleared() {
        if (subscription != null) {
            subscription.cancel();
        }
    }
}
