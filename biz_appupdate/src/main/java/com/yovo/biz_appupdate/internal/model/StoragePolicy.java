package com.yovo.biz_appupdate.internal.model;

/**
 * 安装包目录保留策略。
 *
 * <p>{@link #SINGLE_LATEST}：只保留最新一个安装包；
 * {@link #MULTI_VERSION}：保留多个版本供选择。</p>
 */
public enum StoragePolicy {

    SINGLE_LATEST,
    MULTI_VERSION
}
