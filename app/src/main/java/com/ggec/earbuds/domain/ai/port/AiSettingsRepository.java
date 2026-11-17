package com.ggec.earbuds.domain.ai.port;

import com.ggec.earbuds.domain.ai.model.AiFailure;
import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSettings;
import com.ggec.earbuds.domain.ai.model.AiSettingsPersistenceStatus;
import com.ggec.earbuds.domain.ai.model.AiVoiceModel;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * AI 设置的唯一持有者与唯一写入口。
 *
 * <p>每个成员都是必需的，没有 {@code default} 方法：默认实现会让某个实现悄悄少做一步
 * （例如永不 flush、订阅后收不到失败），调用方却以为契约成立。测试要用的内存仓库
 * 自己补齐全部方法，端口不提供"这段可以不做"的形态。</p>
 *
 * <p>一次业务提交只经一个方法：跨文档的提交（设置文档 + 密钥文档）在
 * {@link #savePlatform} / {@link #saveVoiceKey} 内部成对完成，失败时两者都不落。
 * 调用方不得再自己先写密钥、再提交设置。</p>
 */
public interface AiSettingsRepository {

    /** 当前快照；首次调用会同步读盘。读不出来抛 {@link AiFailure}（种类为 STORAGE，带文档坐标）。 */
    AiSettings load();

    /**
     * 异步读取当前设置。磁盘实现把读取放到后台执行；并发调用共享同一次读取。
     * 首次读取完成前 {@link #ready()} 为 false。
     */
    CompletableFuture<AiSettings> loadAsync();

    /** 首次读取是否已从存储完成。 */
    boolean ready();

    /**
     * 原子地读-改-写。测速回调和用户编辑可能同时到达，修改函数在仓库的锁内执行，
     * 所以它必须是快速且无副作用的纯函数。
     */
    AiSettings update(UnaryOperator<AiSettings> change);

    /**
     * 提交一个平台的连接参数<b>和</b>它的密钥：同一提交点，任一失败两者都不落。
     *
     * @param platform 已由用例校验并归一过的平台配置（不含密钥）
     * @param apiKey   明文密钥；空串表示清除该平台的密钥
     * @return 提交后的平台配置（{@code configured} 由密钥的真实存在情况决定）
     */
    AiPlatformConfig savePlatform(AiPlatformConfig platform, String apiKey);

    /**
     * 提交一个语音服务商的密钥与"已配置"标记：同一提交点，失败时两者都不落。
     *
     * @param model  语音预设
     * @param apiKey 明文密钥；空串表示清除
     * @return 提交后的设置快照
     */
    AiSettings saveVoiceKey(AiVoiceModel model, String apiKey);

    /** 每次设置变化后在调用 {@link #update} 的线程上通知，不会在锁内回调。 */
    Subscription observe(Consumer<AiSettings> observer);

    /** 持久化状态通知。磁盘实现可能从后台线程回调；观察者必须立即返回。 */
    Subscription observePersistenceStatus(Consumer<AiSettingsPersistenceStatus> observer);

    /** 持久化失败通知，载荷是分类加文档坐标。实现可能在后台线程回调；观察者必须立即返回。 */
    Subscription observePersistenceFailures(Consumer<AiFailure> observer);

    /** 等待截至调用时刻的修改写入完成。生产环境只在退出、诊断或显式保存时使用。 */
    CompletableFuture<Void> flush();
}
