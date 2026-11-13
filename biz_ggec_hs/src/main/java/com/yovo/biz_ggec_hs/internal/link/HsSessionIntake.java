package com.yovo.biz_ggec_hs.internal.link;

import com.yovo.biz_ggec_hs.internal.center.protocol.Command;

import java.util.List;

/**
 * 会话就绪之后如何对待这台 HS 设备。
 *
 * <p>连接状态始终发布。本接口只决定三件与「设备信息」有关的事：
 * READY 后补发哪些查询、引擎要不要补查耳机颜色、主动上报进不进设备域。
 * 组合根安装其中一份实现，会话不在内部再写一套分支。</p>
 */
public interface HsSessionIntake {

    /** READY 后按型号补发的查询。空列表表示这次会话不取设备内容。 */
    List<Command> readyQueries(String modelId);

    /** 主动上报是否写入设备域。关闭时入站事件帧被丢掉，命令应答仍走原通道。 */
    boolean acceptDeviceReports();

    /** 引擎在会话就绪时是否补查一次耳机颜色。 */
    boolean probeEarColorOnReady();
}
