package com.yovo.biz_appupdate.api;

/**
 * 更新 UI 的动作面：宿主把用户操作与系统回调转进来。
 *
 * <p>方法名就是用户的动作，参数只有宿主确实掌握的值（下载方式、列表下标）；
 * 版本号、安装包路径等 biz 侧已知的上下文由呈现器自己记住，不回传给宿主。</p>
 *
 * <p>下载目录由宿主配置（{@code APP_PRIVATE} 恒可访问），本模块不请求目录权限、
 * 也不上报目录权限结果，因此契约里没有目录权限三个动作。</p>
 */
public interface UpdateUiSession {

    /** 用户点「检查更新」。 */
    void checkUpdate();

    /** 用户选了下载方式。 */
    void download(UpdateDownloadMode mode);

    /** 用户暂时不更新（忽略当前版本）。 */
    void deferUpdate();

    /** 用户确认安装当前待安装包。 */
    void confirmInstall();

    /** 用户在本地安装包列表里选了第 {@code index} 项（对应最近一次 {@code ShowLocalApkPicker}）。 */
    void selectLocalApk(int index);

    /** 安装未知来源权限的设置页返回。 */
    void onInstallPermissionResult();

    /** 解绑：不再回调宿主，也不改引擎状态。 */
    void detach();
}
