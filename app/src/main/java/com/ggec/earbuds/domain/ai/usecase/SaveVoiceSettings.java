package com.ggec.earbuds.domain.ai.usecase;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiMode;
import com.ggec.earbuds.domain.ai.model.AiValidationError;
import com.ggec.earbuds.domain.ai.model.AiVoiceSettings;
import com.ggec.earbuds.domain.ai.port.AiSettingsRepository;

import java.util.Objects;

/**
 * 保存语音配置（模式、转写模型、音色、实时模型、静音阈值）。
 *
 * <p>为什么单独一个用例而不是让界面直接调仓库的 {@code update}：这里要跑一次校验。
 * 选了全双工却没有实时模型，保存下去的结果是"用户以为切过去了，通话页一进去就报错"。
 * 校验结果以 {@link AiValidationError} 返回而不是抛异常——领域不该写中文，
 * 也不该替界面决定"这是一句提示还是一个崩溃"。</p>
 */
public final class SaveVoiceSettings {

    private final AiSettingsRepository settings;

    public SaveVoiceSettings(@NonNull AiSettingsRepository settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * @return null 表示已经存下；非空表示没存，值是缺什么
     */
    @Nullable
    public AiValidationError save(@NonNull AiVoiceSettings voice) {
        Objects.requireNonNull(voice, "voice");
        AiValidationError invalid = voice.validate();
        if (invalid != null) {
            return invalid;
        }
        settings.update(current -> current.withVoice(voice));
        return null;
    }

    /**
     * 只改模式，其余保持不变。切换模式是最常做的动作，单独给一个方法，
     * 免得调用方为了改一个字段要把整份配置读出来再拼回去。
     *
     * <p>切到全双工但还没选实时模型时**仍然会存**：这是用户正在配的过程，
     * 拦住它等于逼人先跳去选模型再回来点模式。能不能真的拨通由 {@link #save} 与通话页判定。</p>
     */
    @NonNull
    public AiVoiceSettings withMode(@NonNull AiMode mode) {
        Objects.requireNonNull(mode, "mode");
        AiVoiceSettings[] result = new AiVoiceSettings[1];
        settings.update(current -> {
            result[0] = current.voice().withMode(mode);
            return current.withVoice(result[0]);
        });
        return result[0];
    }
}
