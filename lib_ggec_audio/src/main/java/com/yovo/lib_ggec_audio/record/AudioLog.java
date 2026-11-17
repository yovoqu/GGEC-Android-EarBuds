package com.yovo.lib_ggec_audio.record;

/**
 * 录音管线的日志缝：lib 只产生消息，落到哪个日志系统由产品侧一行转发决定。
 *
 * <p>lib 层不认识任何产品日志门面，因此管线只持有这个接口；HS / WY 各自把
 * {@code HsLog} / {@code WyLog} 接上来，实现体只有三行转发，不再复制管线实现。</p>
 */
public interface AudioLog {

    void debug(String message);

    void info(String message);

    void warn(String message);
}
