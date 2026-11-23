package com.ggec.earbuds.ui.device;

/** 一次控件写入的生命周期：空闲/发送中/处理中/成功/失败。 */
public enum WriteState { IDLE, SENDING, PROCESSING, CONFIRMED, FAILED;
    public boolean inFlight() { return this == SENDING || this == PROCESSING; } }
