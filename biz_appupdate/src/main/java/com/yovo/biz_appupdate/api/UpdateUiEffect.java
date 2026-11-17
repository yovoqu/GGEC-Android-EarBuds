package com.yovo.biz_appupdate.api;

/**
 * 更新 UI 需要宿主执行的系统级动作。
 *
 * <p>只表达「需要宿主做一件事」，不含做法：怎么要权限、怎么开设置页由宿主决定。</p>
 *
 * <p>动作集合只保留本模块真会发出的那些：下载目录由宿主配置（{@code APP_PRIVATE} 恒可访问），
 * 引擎不会要求用户选目录或补目录权限，所以这里没有「选下载目录 / 请求下载目录权限」两种动作。</p>
 */
public record UpdateUiEffect(Kind kind) {

    public enum Kind {
        /** 请求「安装未知来源应用」权限。 */
        REQUEST_INSTALL_UNKNOWN_SOURCES,
        /** 打开本应用的系统设置页（权限被永久拒绝后的兜底）。 */
        OPEN_APP_SETTINGS
    }

    public UpdateUiEffect {
        if (kind == null) {
            throw new IllegalArgumentException("effect kind 不能为空");
        }
    }

    public static UpdateUiEffect of(Kind kind) {
        return new UpdateUiEffect(kind);
    }
}
