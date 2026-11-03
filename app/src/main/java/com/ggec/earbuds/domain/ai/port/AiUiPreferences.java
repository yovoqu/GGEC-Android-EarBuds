package com.ggec.earbuds.domain.ai.port;

import androidx.annotation.NonNull;

import com.ggec.earbuds.domain.ai.model.AiInteractionMode;

/**
 * 设置页的"轻量界面偏好"，和 {@code AiSettings} 的配置文档分开。
 *
 * <p>"上次停在哪个分段"是视图偏好，不是 AI 配置：它不参与请求、不影响任何模型选择。
 * 把它塞进设置文档会让每次切 tab 都触发一次整份 JSON 落盘（还要 fsync），既慢又会闪
 * "正在保存"。这里只存一个枚举名，用 SharedPreferences.apply() 异步落盘，切换是瞬时的。</p>
 */
public interface AiUiPreferences {

    @NonNull
    AiInteractionMode lastSettingsMode(@NonNull AiInteractionMode fallback);

    void setLastSettingsMode(@NonNull AiInteractionMode mode);
}
