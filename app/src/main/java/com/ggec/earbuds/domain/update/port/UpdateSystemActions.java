package com.ggec.earbuds.domain.update.port;

/**
 * 更新流程里只能由宿主窗口执行的系统动作。
 *
 * <p>端口只有「要做哪件事」：拉起哪个系统页面、用哪个窗口承载，都由实现方（平台层）决定。
 * 更新适配层拿到的是本接口，因此不会自己去拼 {@code Intent}。</p>
 */
public interface UpdateSystemActions {

    /** 打开本应用的系统设置页（权限被永久拒绝后的收口路径）。 */
    void openAppSettings();
}
