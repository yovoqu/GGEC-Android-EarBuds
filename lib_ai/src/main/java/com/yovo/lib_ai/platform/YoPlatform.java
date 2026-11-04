package com.yovo.lib_ai.platform;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.yovo.lib_ai.api.YoProtocol;
import com.yovo.lib_ai.chat.YoChatPartEncoder;
import com.yovo.lib_ai.chat.YoChatRequestPolicy;
import com.yovo.lib_ai.messages.YoMessagesPolicy;
import com.yovo.lib_ai.models.YoModelListing;
import com.yovo.lib_ai.responses.YoResponsesPolicy;

import java.util.Set;

/**
 * 一个 AI 平台提供哪些标准协议，以及它和标准报文不一致的编码器与策略。
 *
 * <p>{@link #protocols()} 是唯一的协议清单。装配只挂清单里的协议；
 * 清单里的每一项都必须有对应的 {@link YoServiceTarget}，缺了就在装配时拒绝，
 * 避免目录写一套、目标地址又写一套。</p>
 */
public interface YoPlatform {

    @NonNull
    String id();

    /**
     * 这台平台真正提供的对话协议。顺序没有意义。
     */
    @NonNull
    Set<YoProtocol> protocols();

    @Nullable
    YoServiceTarget chatTarget();

    @Nullable
    YoServiceTarget responsesTarget();

    @Nullable
    YoServiceTarget messagesTarget();

    @NonNull
    YoChatPartEncoder chatPartEncoder();

    @NonNull
    YoChatRequestPolicy chatPolicy();

    @NonNull
    YoResponsesPolicy responsesPolicy();

    @NonNull
    YoMessagesPolicy messagesPolicy();

    /**
     * 平台怎样列出自己的模型。不支持列表的平台返回空，调用方改用手动输入模型名。
     */
    @Nullable
    default YoModelListing modelListing() {
        return null;
    }
}
