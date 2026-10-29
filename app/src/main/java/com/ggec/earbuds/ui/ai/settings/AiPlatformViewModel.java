package com.ggec.earbuds.ui.ai.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ggec.earbuds.R;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiInteractionMode;
import com.ggec.earbuds.domain.ai.model.AiModel;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiPlatformTemplate;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;
import com.ggec.earbuds.domain.ai.model.AiValidationException;
import com.ggec.earbuds.domain.ai.port.AiCancellable;
import com.ggec.earbuds.domain.ai.port.AiSpeedTestListener;
import com.ggec.earbuds.domain.ai.port.Subscription;
import com.ggec.earbuds.domain.ai.usecase.AddManualModel;
import com.ggec.earbuds.domain.ai.usecase.FetchPlatformModels;
import com.ggec.earbuds.domain.ai.usecase.LoadAiSettings;
import com.ggec.earbuds.domain.ai.usecase.LoadPlatformTemplates;
import com.ggec.earbuds.domain.ai.usecase.ObserveAiSettings;
import com.ggec.earbuds.domain.ai.usecase.RemoveModel;
import com.ggec.earbuds.domain.ai.usecase.RemovePlatform;
import com.ggec.earbuds.domain.ai.usecase.RunSpeedTest;
import com.ggec.earbuds.domain.ai.usecase.SavePlatform;
import com.ggec.earbuds.domain.ai.usecase.SelectModel;
import com.ggec.earbuds.ui.ai.common.AiMessages;
import com.yovo.lib_common.event.OneShotLiveData;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * 平台详情页的 ViewModel（MVVM 的 VM）。
 *
 * <p>职责边界：这里是<b>唯一</b>可变状态的地方。它把用例的结果压成
 * {@link AiPlatformUiState}，页面只订阅、只画。页面不持有任何业务判断，
 * 因此"通不通""哪个快""能不能点"这类规则不会漏到 View 里。</p>
 *
 * <p>测速的生命周期由这里持有（不是在页面里）：离开页面再回来、转屏，
 * 结果都在；已测完的写回配置，未测完的随取消结束。</p>
 */
public final class AiPlatformViewModel extends ViewModel {

    private final LoadAiSettings loadSettings;
    private final ObserveAiSettings observeSettings;
    private final LoadPlatformTemplates loadTemplates;
    private final SavePlatform savePlatform;
    private final FetchPlatformModels fetchModels;
    private final AddManualModel addManualModel;
    private final RemoveModel removeModel;
    private final SelectModel selectModel;
    private final RunSpeedTest runSpeedTest;
    private final RemovePlatform removePlatform;
    private final Executor io;
    private final AiPlatformUiMapper mapper;

    private final MutableLiveData<AiPlatformUiState> state =
            new MutableLiveData<>(AiPlatformUiState.empty());
    private final OneShotLiveData<AiPlatformEvent> events = new OneShotLiveData<>();
    private final Set<String> testing = ConcurrentHashMap.newKeySet();

    @Nullable
    private String platformId;
    @Nullable
    private String creatingTemplateId;
    /** 从设置页带进来：决定模型列表里哪些模型可选，以及选中项写到哪一份。 */
    private AiInteractionMode mode = AiInteractionMode.defaultMode();
    private AiSettings settings = AiSettings.EMPTY;
    private List<AiPlatformTemplate> templates = List.of();
    private boolean advancedExpanded;
    private boolean speedRunning;
    private boolean keyRevealed;
    /** 搜索词：只影响"哪些行可见"，不进存储、不发网络。 */
    private volatile String searchQuery = "";
    @Nullable
    private AiCancellable speedTest;
    @Nullable
    private Subscription subscription;
    private boolean started;

    public AiPlatformViewModel(@NonNull LoadAiSettings loadSettings,
                               @NonNull ObserveAiSettings observeSettings,
                               @NonNull LoadPlatformTemplates loadTemplates,
                               @NonNull SavePlatform savePlatform,
                               @NonNull FetchPlatformModels fetchPlatformModels,
                               @NonNull AddManualModel addManualModel,
                               @NonNull RemoveModel removeModel,
                               @NonNull SelectModel selectModel,
                               @NonNull RunSpeedTest runSpeedTest,
                               @NonNull RemovePlatform removePlatform,
                               @NonNull Executor io,
                               @NonNull AiPlatformUiMapper mapper) {
        this.loadSettings = Objects.requireNonNull(loadSettings, "loadSettings");
        this.observeSettings = Objects.requireNonNull(observeSettings, "observeSettings");
        this.loadTemplates = Objects.requireNonNull(loadTemplates, "loadTemplates");
        this.savePlatform = Objects.requireNonNull(savePlatform, "savePlatform");
        this.fetchModels = Objects.requireNonNull(fetchPlatformModels, "fetchPlatformModels");
        this.addManualModel = Objects.requireNonNull(addManualModel, "addManualModel");
        this.removeModel = Objects.requireNonNull(removeModel, "removeModel");
        this.selectModel = Objects.requireNonNull(selectModel, "selectModel");
        this.runSpeedTest = Objects.requireNonNull(runSpeedTest, "runSpeedTest");
        this.removePlatform = Objects.requireNonNull(removePlatform, "removePlatform");
        this.io = Objects.requireNonNull(io, "io");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @NonNull
    public LiveData<AiPlatformUiState> state() {
        return state;
    }

    @NonNull
    public LiveData<AiPlatformEvent> events() {
        return events;
    }

    /** 绑定一个已有平台，并在指定模式下配置它的模型。 */
    public void open(@NonNull String platformId, @NonNull AiInteractionMode mode) {
        this.platformId = platformId;
        this.creatingTemplateId = null;
        this.mode = mode;
        start();
    }

    /** 新建：还没落盘，只有模板与一份空配置。 */
    public void create(@NonNull String templateId, @NonNull AiInteractionMode mode) {
        this.creatingTemplateId = templateId;
        this.platformId = null;
        this.mode = mode;
        start();
    }

    private void start() {
        if (started) {
            publish();
            return;
        }
        started = true;
        io.execute(() -> {
            settings = loadSettings.execute();
            templates = loadTemplates.execute();
            publish();
            subscription = observeSettings.execute(next -> {
                settings = next;
                publish();
            });
        });
    }

    private void publish() {
        AiPlatformConfig platform = platform();
        if (platform == null && creatingTemplateId == null) {
            state.postValue(AiPlatformUiState.empty());
            return;
        }
        AiPlatformUiState next = mapper.map(settings, platform, template(), mode,
                testing, speedRunning, advancedExpanded, searchQuery);
        state.postValue(next);
    }

    @Nullable
    private AiPlatformConfig platform() {
        String id = platformId;
        return id == null ? null : settings.platform(id).orElse(null);
    }

    @Nullable
    private AiPlatformTemplate template() {
        String templateId = creatingTemplateId;
        if (templateId == null) {
            AiPlatformConfig platform = platform();
            templateId = platform == null ? null : platform.templateId();
        }
        if (templateId == null) {
            return null;
        }
        for (AiPlatformTemplate candidate : templates) {
            if (candidate.id().equals(templateId)) {
                return candidate;
            }
        }
        return null;
    }

    // ---- 连接 ----

    /** 测试连接 = 只测当前选中模型，等价于一次真实的最小请求。 */
    @androidx.annotation.Nullable
    private String selectedModelOf(@NonNull AiPlatformConfig platform) {
        com.ggec.earbuds.domain.ai.model.AiSelection selection = settings.selection(mode);
        return selection != null && platform.id().equals(selection.platformId())
                ? selection.modelId() : null;
    }

    public void testConnection() {
        AiPlatformConfig platform = platform();
        if (platform == null || speedRunning) {
            return;
        }
        String selected = selectedModelOf(platform);
        if (selected == null) {
            events.postValue(AiPlatformEvent.toast(AiMessages.validation(
                    com.ggec.earbuds.domain.ai.model.AiValidationError.MODEL_UNKNOWN)));
            return;
        }
        runSpeed(List.of(selected));
    }

    public void toggleKeyRevealed() {
        keyRevealed = !keyRevealed;
        publish();
    }

    public boolean keyRevealed() {
        return keyRevealed;
    }

    // ---- 模型 ----

    public void selectModel(@NonNull String modelId) {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        io.execute(() -> {
            selectModel.execute(mode, platform.id(), modelId);
            publish();
        });
    }

    public void testModel(@NonNull String modelId) {
        runSpeed(List.of(modelId));
    }

    /**
     * 搜索词。过滤是纯展示逻辑（匹配名称或 id），不重读磁盘、不发请求；
     * 具体匹配规则在 {@link AiPlatformUiMapper#filter} 里，可单测。
     */
    public void setSearchQuery(@NonNull String query) {
        String next = query.strip();
        if (next.equals(searchQuery)) {
            return;
        }
        searchQuery = next;
        publish();
    }

    public void runAllSpeed() {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        // 只测这一模式能选的模型：语音模式不必去测一堆对话模型的延迟。
        List<String> ids = platform.modelsFor(mode).stream().map(AiModel::id).toList();
        if (ids.isEmpty()) {
            events.postValue(AiPlatformEvent.toast(com.ggec.earbuds.R.string.ai_settings_models_empty));
            return;
        }
        runSpeed(ids);
    }

    public void cancelSpeed() {
        AiCancellable running = speedTest;
        speedTest = null;
        if (running != null) {
            running.cancel();
        }
        testing.clear();
        speedRunning = false;
        publish();
    }

    private void runSpeed(@NonNull List<String> modelIds) {
        AiPlatformConfig platform = platform();
        if (platform == null || speedRunning) {
            return;
        }
        speedRunning = true;
        testing.clear();
        testing.addAll(modelIds);
        publish();
        try {
            speedTest = runSpeedTest.execute(platform.id(), modelIds, new Listener());
        } catch (AiFailure failure) {
            fail(failure);
        } catch (AiValidationException invalid) {
            speedRunning = false;
            testing.clear();
            publish();
            events.postValue(AiPlatformEvent.toast(AiMessages.validation(invalid.error())));
        }
    }

    private void fail(@NonNull AiFailure failure) {
        speedRunning = false;
        testing.clear();
        publish();
        events.postValue(AiPlatformEvent.toast(AiMessages.failure(failure.kind())));
    }

    public void addManualModel(@NonNull String rawId) {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        io.execute(() -> {
            try {
                AiModel added = addManualModel.execute(platform.id(), rawId);
                // 手填的模型也要和当前模式对得上，否则用户加完却在列表里看不到它。
                if (mode == AiInteractionMode.VOICE && !added.realtime()) {
                    events.postValue(AiPlatformEvent.toast(AiMessages.validation(
                            com.ggec.earbuds.domain.ai.model.AiValidationError.VOICE_NEEDS_REALTIME_MODEL)));
                } else if (mode == AiInteractionMode.TEXT && added.realtime()) {
                    events.postValue(AiPlatformEvent.toast(R.string.ai_settings_model_realtime_only));
                }
            } catch (AiValidationException invalid) {
                events.postValue(AiPlatformEvent.toast(AiMessages.validation(invalid.error())));
            }
            publish();
        });
    }

    public void fetchModels() {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        events.postValue(AiPlatformEvent.toast(com.ggec.earbuds.R.string.ai_settings_fetching));
        io.execute(() -> {
            try {
                fetchModels.execute(platform.id());
            } catch (AiFailure failure) {
                events.postValue(AiPlatformEvent.toast(AiMessages.failure(failure.kind())));
            }
            publish();
        });
    }

    public void deleteModel(@NonNull String modelId) {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        io.execute(() -> {
            removeModel.execute(platform.id(), modelId);
            publish();
        });
    }

    // ---- 高级 / 危险区 ----

    public void toggleAdvanced() {
        advancedExpanded = !advancedExpanded;
        publish();
    }

    public void saveKey(@NonNull String key) {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        io.execute(() -> {
            try {
                savePlatform.execute(new SavePlatform.Command(platform.id(), platform.templateId(),
                        null, key, null, null));
            } catch (AiValidationException invalid) {
                events.postValue(AiPlatformEvent.toast(AiMessages.validation(invalid.error())));
            }
            publish();
        });
    }

    public void saveBaseUrl(@NonNull String baseUrl) {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        io.execute(() -> {
            try {
                savePlatform.execute(new SavePlatform.Command(platform.id(), platform.templateId(),
                        null, platform.apiKey(), baseUrl, null));
            } catch (AiValidationException invalid) {
                events.postValue(AiPlatformEvent.toast(AiMessages.validation(invalid.error())));
            }
            publish();
        });
    }

    public void saveName(@NonNull String name) {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        io.execute(() -> {
            savePlatform.execute(new SavePlatform.Command(platform.id(), platform.templateId(),
                    name, platform.apiKey(), null, null));
            publish();
        });
    }

    /** 新建平台：唯一一次"整体提交"，因为没密钥就落不了盘。 */
    public void createWithKey(@NonNull String apiKey) {
        String templateId = creatingTemplateId;
        if (templateId == null) {
            return;
        }
        io.execute(() -> {
            try {
                AiPlatformConfig created = savePlatform.execute(new SavePlatform.Command(
                        null, templateId, null, apiKey, null, null));
                platformId = created.id();
                creatingTemplateId = null;
                publish();
                events.postValue(AiPlatformEvent.toast(com.ggec.earbuds.R.string.ai_settings_saved));
            } catch (AiValidationException invalid) {
                events.postValue(AiPlatformEvent.toast(AiMessages.validation(invalid.error())));
            }
        });
    }

    public void deletePlatform() {
        AiPlatformConfig platform = platform();
        if (platform == null) {
            return;
        }
        io.execute(() -> {
            removePlatform.execute(platform.id());
            events.postValue(AiPlatformEvent.closed(com.ggec.earbuds.R.string.ai_settings_platform_removed));
        });
    }

    @Override
    protected void onCleared() {
        Subscription current = subscription;
        subscription = null;
        if (current != null) {
            current.cancel();
        }
        cancelSpeed();
    }

    private final class Listener implements AiSpeedTestListener {
        @Override
        public void onStarted(@NonNull String modelId) {
            // 已提前置为 TESTING，无需再刷新。
        }

        @Override
        public void onResult(@NonNull AiSpeedResult result) {
            testing.remove(result.modelId());
            publish();
        }

        @Override
        public void onComplete(boolean cancelled) {
            speedTest = null;
            speedRunning = false;
            testing.clear();
            publish();
            if (cancelled) {
                events.postValue(AiPlatformEvent.toast(com.ggec.earbuds.R.string.ai_settings_speed_cancelled));
            }
        }
    }
}