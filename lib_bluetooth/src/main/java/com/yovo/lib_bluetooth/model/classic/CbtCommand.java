package com.yovo.lib_bluetooth.model.classic;

import androidx.annotation.NonNull;

/**
 * SPP 命令协议命令。
 */
public interface CbtCommand {

    @NonNull
    byte[] payload();
}
