package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;
import com.ggec.earbuds.domain.ai.model.AiActiveModel;
import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiModelParams;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiUsageSummary;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.policy.AiParamsPolicy;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.ApplyModelParams;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.LoadUsageSummary;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.SaveChatPreferences;
import com.ggec.earbuds.domain.ai.usecase.SaveVoiceSettings;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 设置总览页的 ViewModel：只经用例读写设置，把结果交给 {@link AiSettingsUiMapper} 变成界面状态。
 *
 * <p>它不认识 Context、不认识视图、也不认识仓库实现——线程用构造时注入的 {@link Executor}，
 * 单测里传 {@code Runnable::run} 就能同步跑完。</p>
 *
 * <p>为什么每次改动都重新读一遍磁盘：设置里有"用量"这一块来自另一个存储（JSONL 账本），
 * 它不随设置变化而变；只订阅设置在用量上是听不到的，所以进页面时单独读一次。</p>
 */
public final class AiSettingsViewModel extends ViewModel {

    private final LoadAiSettings loadSettings;
    private final ObserveAiSettings observeSettings;
    private final SaveChatPreferences savePreferences;
    private final SaveVoiceSettings saveVoice;
    private final LoadUsageSummary loadUsage;
    private final LoadPlatformTemplates loadTemplates;
    private final ApplyModelParams applyModelParams;
    private final Executor io;
    private final AiSettingsUiMapper mapper;

    private final MutableLiveData<AiSettingsUiState> uiState = new MutableLiveData<>(AiSettingsUiState.empty());
    private final OneShotLiveData<AiSettingsEvent> events = new OneShotLiveData<>();

    private volatile AiSettings latest = AiSettings.EMPTY;
    private volatile AiUsageSummary usage = AiUsageSummary.empty();
    private volatile List<AiPlatformTemplate> templates = List.of();
    private Subscription subscription;
    private boolean started;

    public AiSettingsViewModel(@NonNull LoadAiSettings loadSettings,
                               @NonNull ObserveAiSettings observeSettings,
                               @NonNull SaveChatPreferences savePreferences,
                               @NonNull SaveVoiceSettings saveVoice,
                               @NonNull LoadUsageSummary loadUsage,
                               @NonNull LoadPlatformTemplates loadTemplates,
                               @NonNull ApplyModelParams applyModelParams,
                               @NonNull Executor io,
                               @NonNull AiSettingsUiMapper mapper) {
        this.loadSettings = Objects.requireNonNull(loadSettings, "loadSettings");
        this.observeSettings = Objects.requireNonNull(observeSettings, "observeSettings");
        this.savePreferences = Objects.requireNonNull(savePreferences, "savePreferences");
        this.saveVoice = Objects.requireNonNull(saveVoice, "saveVoice");
        this.loadUsage = Objects.requireNonNull(loadUsage, "loadUsage");
        this.loadTemplates = Objects.requireNonNull(loadTemplates, "loadTemplates");
        this.applyModelParams = Objects.requireNonNull(applyModelParams, "applyModelParams");
        this.io = Objects.requireNonNull(io, "io");
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
        reloadUsage();
    }

    /** 从别处切回来时刷新账本（设置没变，用量可能变了）。 */
    public void reloadUsage() {
        io.execute(() -> {
            AiUsageSummary loaded = loadUsage.load();
            usage = loaded;
            publish();
        });
    }

    public void selectMode(@NonNull AiMode mode) {
        guard(() -> saveVoice.withMode(mode));
    }

    public void saveSystemPrompt(@NonNull String systemPrompt) {
        guard(() -> savePreferences.update(preferences -> preferences.withSystemPrompt(systemPrompt)));
    }

    /** null 表示"用平台默认"，界面上的"恢复默认"走这条路径。 */
    /** 全局参数开关。关掉不等于清空数值——用户下次打开还应该看到自己调过的值。 */
    public void saveGlobalParamsEnabled(boolean enabled) {
        guard(() -> savePreferences.update(preferences -> preferences.withGlobalParamsEnabled(enabled)));
    }

    public void saveTemperature(@Nullable Double temperature) {
        guard(() -> savePreferences.update(preferences -> preferences.withTemperature(temperature)));
    }

    public void saveTopP(@Nullable Double topP) {
        guard(() -> savePreferences.update(preferences -> preferences.withTopP(topP)));
    }

    public void saveMaxOutputTokens(@Nullable Integer maxOutputTokens) {
        guard(() -> savePreferences.update(preferences -> preferences.withMaxOutputTokens(maxOutputTokens)));
    }

    public void saveContextMessages(int contextMessages) {
        guard(() -> savePreferences.update(preferences -> preferences.withContextMessages(contextMessages)));
    }

    /**
     * 把"当前生效的那套生成参数"写进本机每个模型。
     *
     * <p>这一版没有可写的"作用范围"字段，所以"让这套值真的对全部模型生效"只能靠写覆盖表：
     * 写完每个模型都有自己的一份，不再跟随全局。值取自
     * {@link AiParamsPolicy#snapshot}——也就是界面上滑杆所代表的那套值，不是重新猜一遍。</p>
     */
    public void applyParamsToAllModels() {
        AiSettings snapshot = latest;
        AiModelParams params = paramsToApply(snapshot);
        if (params.isEmpty()) {
            events.postValue(AiSettingsEvent.toast(R.string.ai_settings_apply_all_none));
            return;
        }
        io.execute(() -> {
            int written = applyModelParams.execute(params, null);
            events.postValue(AiSettingsEvent.toast(written > 0
                    ? R.string.ai_settings_apply_all_done
                    : R.string.ai_settings_apply_all_none,
                    written > 0 ? String.valueOf(written) : null));
            publish();
        });
    }

    @NonNull
    private static AiModelParams paramsToApply(@NonNull AiSettings settings) {
        AiActiveModel active = settings.activeModel().orElse(null);
        return active == null
                ? AiParamsPolicy.snapshot(null, null, settings.preferences())
                : AiParamsPolicy.snapshot(active.model(), active.platform(), settings.preferences());
    }

    private void onSettings(AiSettings settings) {
        latest = settings;
        publish();
    }

    private void publish() {
        uiState.postValue(mapper.map(latest, usage));
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
