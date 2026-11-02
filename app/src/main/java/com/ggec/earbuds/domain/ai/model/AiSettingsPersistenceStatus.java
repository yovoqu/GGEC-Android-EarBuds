package com.ggec.earbuds.domain.ai.model;

/** 设置的磁盘持久化状态；内存提交成功不等于已经安全落盘。 */
public enum AiSettingsPersistenceStatus {
    SAVED,
    SAVING,
    FAILED
}
