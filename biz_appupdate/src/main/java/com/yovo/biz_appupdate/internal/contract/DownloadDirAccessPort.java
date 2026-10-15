package com.yovo.biz_appupdate.internal.contract;

/**
 * 下载目录可访问性端口：权限/SAF 授权判定统一收敛到这里。
 */
public interface DownloadDirAccessPort {

    boolean isAccessible();
}
