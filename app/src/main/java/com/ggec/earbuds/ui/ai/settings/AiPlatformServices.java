package com.ggec.earbuds.ui.ai.settings;

import com.ggec.earbuds.domain.ai.policy.AiApiKeyPolicy;
import com.ggec.earbuds.domain.ai.policy.AiBaseUrlPolicy;
import com.ggec.earbuds.domain.ai.policy.AiModelIdPolicy;
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

/**
 * 平台详情页要用的全部用例和输入校验。打包成一个对象，ViewModel 的构造参数就不会无限增长。
 */
public record AiPlatformServices(
        LoadAiSettings loadSettings,
        ObserveAiSettings observeSettings,
        LoadPlatformTemplates loadTemplates,
        SavePlatform savePlatform,
        FetchPlatformModels fetchModels,
        AddManualModel addManualModel,
        RemoveModel removeModel,
        RemovePlatform removePlatform,
        SelectModel selectModel,
        RunSpeedTest runSpeedTest,
        AiApiKeyPolicy keyPolicy,
        AiBaseUrlPolicy urlPolicy,
        AiModelIdPolicy modelIdPolicy) {
}
