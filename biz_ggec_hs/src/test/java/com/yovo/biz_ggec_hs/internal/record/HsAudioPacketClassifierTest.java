package com.yovo.biz_ggec_hs.internal.record;

import com.yovo.lib_ggec_audio.AudioFrameAssembler;
import com.yovo.lib_ggec_audio.AudioFrameContract;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 820003 数据通道：控制上报包与音频切片的边界。
 *
 * <p>帧装配本身由 {@code lib_ggec_audio} 的单测覆盖；这里只钉住 HS 自己的控制命令表，
 * 以及「控制包不得进入音频装配器」这一条产品侧约定。</p>
 */
public class HsAudioPacketClassifierTest {

    @Test
    public void classifiesRemoteControlReports() {
        byte[] startAck = new byte[]{0x04, (byte) 0x80, 0x01, 0x00, 0x00};
        byte[] stopNotify = new byte[]{0x05, (byte) 0x80, 0x01, 0x00, 0x01};
        byte[] delayReport = new byte[]{0x12, (byte) 0x80, 0x02, 0x00, 0x00, 0x00};

        assertTrue(HsAudioPacketClassifier.isControlPacket(startAck));
        assertTrue(HsAudioPacketClassifier.isControlPacket(stopNotify));
        assertTrue(HsAudioPacketClassifier.isControlPacket(delayReport));
        assertEquals(HsAudioPacketClassifier.CMD_TYPE_STOP,
                HsAudioPacketClassifier.resolveControlCmdType(stopNotify));
    }

    @Test
    public void audioSliceIsNotAControlReport() {
        byte[] audioSlice = new byte[AudioFrameContract.SUBFRAME_44];
        audioSlice[1] = (byte) 0x80;

        assertTrue(!HsAudioPacketClassifier.isControlPacket(audioSlice));
        assertEquals(0, HsAudioPacketClassifier.resolveControlCmdType(audioSlice));
    }

    @Test
    public void controlReportNeverPollutesAudioAssembler() {
        AudioFrameAssembler assembler = new AudioFrameAssembler();
        byte[] stopNotify = new byte[]{0x05, (byte) 0x80, 0x01, 0x00, 0x01};

        List<byte[]> frames = assembler.assemble(stopNotify);

        assertTrue(frames.isEmpty());
        assertEquals(0, assembler.pendingBytes());
        assertNull(assembler.getActiveFormat());
    }
}
