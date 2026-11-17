package com.yovo.biz_appupdate.api;

/**
 * 更新包的下载方式：三个选项由协议能力决定，宿主只负责让用户选。
 */
public enum UpdateDownloadMode {
    /** 应用内下载（走本模块的下载器，需要可写下载目录）。 */
    INTERNAL,
    /** 交给系统下载器。 */
    SYSTEM,
    /** 跳浏览器下载。 */
    BROWSER
}
