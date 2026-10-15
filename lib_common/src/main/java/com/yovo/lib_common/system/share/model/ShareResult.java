package com.yovo.lib_common.system.share.model;

/**
 * @author tingfeng.yang
 * @date 2026-07-13
 * @description 出站分享启动结果。成功仅表示系统分享界面已拉起，不代表用户完成分享。
 */
public enum ShareResult {
    LAUNCHED,
    NO_FILES,
    PROVIDER_ERROR,
    ARCHIVE_ERROR,
    NO_APP;

    public boolean isLaunched() {
        return this == LAUNCHED;
    }
}
