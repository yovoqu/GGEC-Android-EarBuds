package com.yovo.biz_appupdate.internal.contract;

/**
 * 安装执行结果（统一出口，避免 interactor 类型泄漏到上层）。
 */
public enum InstallOutcome {
    SUCCESS,
    INVALID_FILE,
    NO_INSTALLER,
    VERIFY_FAILED
}
