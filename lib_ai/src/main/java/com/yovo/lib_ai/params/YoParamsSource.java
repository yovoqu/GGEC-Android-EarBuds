package com.yovo.lib_ai.params;

/**
 * 某个参数最终取自哪里。
 *
 * <p>把来源记下来，界面上才能说清"这个值是你在这个模型上设的，还是全局设的，还是平台默认"——
 * 只显示数值的话，用户改全局却发现某个模型没变，会以为是 bug。
 */
public enum YoParamsSource {
    /** 该模型自己的设定。优先级最高。 */
    MODEL,
    /** 全局设定，且调用方判定它对这个模型生效。 */
    GLOBAL,
    /** 都没有，不发送该字段，由平台决定。 */
    PLATFORM_DEFAULT
}
