package com.yovo.lib_ggec_opus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yovo.lib_ggec_opus.api.PcmWriterStatus;
import com.yovo.lib_ggec_opus.api.YoOpusAudioContract;
import com.yovo.lib_ggec_opus.api.YoPcmWriter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/**
 * PCM 写入器：失败必须按分类上报，时长必须由契约常量推导（不许出现魔数）。
 */
public final class YoPcmWriterStatusTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void openOnDirectoryTarget_reportsOpenFailedWithoutSwallowing() throws IOException {
        File directoryTarget = folder.newFolder("not-a-file");

        YoPcmWriter writer = YoOpusSdk.openPcmWriter(directoryTarget);

        PcmWriterStatus status = writer.status();
        assertEquals(PcmWriterStatus.Code.OPEN_FAILED, status.code());
        assertTrue(status.isFailure());
        assertFalse(status.isWritable());
        assertNotNull("失败必须带可诊断详情，不能是空分类", status.detail());

        writer.writePcm(new byte[YoOpusAudioContract.PCM_FRAME_BYTES]);
        assertEquals("失败粘滞：写入不得把状态改回 OPEN",
                PcmWriterStatus.Code.OPEN_FAILED, writer.status().code());
        assertEquals(0L, writer.getDurationMs());

        writer.close();
        assertEquals("失败粘滞：关闭不得覆盖首次失败分类",
                PcmWriterStatus.Code.OPEN_FAILED, writer.status().code());
    }

    @Test
    public void writtenFrames_durationFollowsContractGeometry() throws IOException {
        File target = folder.newFile("capture.pcm");

        YoPcmWriter writer = YoOpusSdk.openPcmWriter(target);
        assertEquals(PcmWriterStatus.Code.OPEN, writer.status().code());

        byte[] frame = new byte[YoOpusAudioContract.PCM_FRAME_BYTES];
        writer.writePcm(frame);
        writer.writePcm(frame);
        writer.writePcm(frame);

        assertEquals("3 帧 PCM 的时长必须是 3 * 帧周期",
                3L * YoOpusAudioContract.FRAME_PERIOD_MS, writer.getDurationMs());
        assertEquals(3L * YoOpusAudioContract.PCM_FRAME_BYTES, writer.getFileSizeBytes());

        writer.close();
        assertEquals(PcmWriterStatus.Code.CLOSED, writer.status().code());
        assertFalse(writer.status().isFailure());
    }
}
