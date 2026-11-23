package com.yovo.biz_ggec_hs.internal.center.command;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;

/**
 * 将具体协议命令解析为调度语义。
 */
public interface CommandPolicyResolver {
    CommandPolicy resolve(Command command, String payload);
}
