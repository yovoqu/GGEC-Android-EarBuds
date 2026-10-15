package com.yovo.lib_common.log.internal.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.yovo.lib_common.log.model.FileLogReason;
import com.yovo.lib_common.log.model.FileLogStatus;
import com.yovo.lib_common.log.model.LogConfig;

import org.junit.Test;

import java.util.Collections;

public final class LogPipelineTest {

    @Test
    public void fileStatus_withoutSink_returnsPersistedReason() {
        LogPipeline pipeline = new LogPipeline(
                LogConfig.builder().enableFile(false).build(),
                null,
                null,
                null,
                FileLogStatus.disabled(FileLogReason.DENIED, "storage denied"),
                Collections.emptyList());

        FileLogStatus status = pipeline.fileStatus();

        assertFalse(status.enabled());
        assertEquals(FileLogReason.DENIED, status.reason());
        assertEquals("storage denied", status.detail());
    }
}
