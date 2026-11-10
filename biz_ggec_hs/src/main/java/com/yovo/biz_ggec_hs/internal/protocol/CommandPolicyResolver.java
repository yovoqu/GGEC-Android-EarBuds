package com.yovo.biz_ggec_hs.internal.protocol;

/**
 * 将具体协议命令解析为调度语义。
 */
interface CommandPolicyResolver {
    CommandPolicy resolve(Command command, String payload);
}
