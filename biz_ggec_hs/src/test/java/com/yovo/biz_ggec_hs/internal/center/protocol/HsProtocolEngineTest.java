package com.yovo.biz_ggec_hs.internal.center.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.EQModeCommand;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 引擎的命令往返：提交走链路端口，完成结果以 {@link CommandCompletion} 回到提交方。
 */
public class HsProtocolEngineTest {

    @Test
    public void eqCommandRoundTripsThroughTheLinkPort() throws Exception {
        FakeHsCommandLink link = new FakeHsCommandLink();
        HsProtocolEngine engine = new HsProtocolEngine(link);
        try {
            engine.connect("00:11:22:33:44:55");
            link.ready();

            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<CommandCompletion> result = new AtomicReference<>();
            engine.submit(new EQModeCommand(EQModeCommand.MUSIC_MODE), (command, completion) -> {
                result.set(completion);
                done.countDown();
            });
            assertTrue(link.awaitWritten("99EC830001001234"));
            link.inbound("99EC83000200001234");
            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertTrue(result.get().isSuccess());
            assertEquals(EQModeCommand.MUSIC_MODE, result.get().getResultValue());
        } finally {
            engine.release();
        }
    }

    @Test
    public void standardFrameAcceptsLengthAndTrailer() {
        assertTrue(ProtocolValues.isProtocolFrame("99EC83000200001234"));
        assertTrue(ProtocolValues.isCompleteFrame("99EC830001001234", "99EC83"));
    }
}
