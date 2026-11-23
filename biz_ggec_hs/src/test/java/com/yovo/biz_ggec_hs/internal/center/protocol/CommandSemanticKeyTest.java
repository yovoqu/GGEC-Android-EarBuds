package com.yovo.biz_ggec_hs.internal.center.protocol;

import static org.junit.Assert.assertEquals;

import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ControlCommandV11;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.DoublePressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.LongPressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.controlcmd.ShortPressSetCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetRecordIdListCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.GetStorageCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.devicecmd.StorageCapacityResult;
import com.yovo.biz_ggec_hs.internal.center.protocol.filerecordcmd.SyncRecordFileIdOpCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.BeginnerModeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.functioncmd.WearDetectionPromptToneCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQCancelRealtimeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.musiccmd.CustomEQRealtimeCommand;
import com.yovo.biz_ggec_hs.internal.center.protocol.streamrecordcmd.SimultaneousInterpretationCommand;

import org.junit.Test;

/**
 * B4b-1：命令调度语义去“载荷下标魔法”的等价性证明。
 *
 * <p>原 {@code CommandPolicyRegistry.semanticKey(Class, payload)} 的 8 段 if 链与
 * {@code responseTimeoutMs}/{@code isRecordIdDelete} 的载荷下标解析已下沉到命令自身。
 * 本测试对每个覆写命令构造代表性实例，断言 {@code semanticKey(getCommandData())} 等于
 * legacy 公式手算出的硬编码期望串；并对 {@link SyncRecordFileIdOpCommand} 断言 delete/export
 * 两种载荷的 {@code responseTimeoutMs} 分别为 5000 与声明值。全部通过即“行为零变化”。</p>
 */
public class CommandSemanticKeyTest {

    @Test
    public void beginnerModeKeysByModeByte() {
        BeginnerModeCommand enter = new BeginnerModeCommand(true);
        assertEquals(BeginnerModeCommand.class.getName() + ":CC",
                enter.semanticKey(enter.getCommandData()));

        BeginnerModeCommand exit = new BeginnerModeCommand(false);
        assertEquals(BeginnerModeCommand.class.getName() + ":CD",
                exit.semanticKey(exit.getCommandData()));
    }

    @Test
    public void simultaneousInterpretationKeysByPayloadByte() {
        SimultaneousInterpretationCommand enter = new SimultaneousInterpretationCommand(true);
        assertEquals(SimultaneousInterpretationCommand.class.getName() + ":01",
                enter.semanticKey(enter.getCommandData()));

        SimultaneousInterpretationCommand exit = new SimultaneousInterpretationCommand(false);
        assertEquals(SimultaneousInterpretationCommand.class.getName() + ":00",
                exit.semanticKey(exit.getCommandData()));
    }

    @Test
    public void wearDetectionPromptToneSplitQueryAndSet() {
        WearDetectionPromptToneCommand on =
                new WearDetectionPromptToneCommand(WearDetectionPromptToneCommand.Action.ON);
        assertEquals(WearDetectionPromptToneCommand.class.getName() + ":set",
                on.semanticKey(on.getCommandData()));

        WearDetectionPromptToneCommand off =
                new WearDetectionPromptToneCommand(WearDetectionPromptToneCommand.Action.OFF);
        assertEquals(WearDetectionPromptToneCommand.class.getName() + ":set",
                off.semanticKey(off.getCommandData()));

        WearDetectionPromptToneCommand query =
                new WearDetectionPromptToneCommand(WearDetectionPromptToneCommand.Action.QUERY);
        assertEquals(WearDetectionPromptToneCommand.class.getName() + ":query",
                query.semanticKey(query.getCommandData()));
    }

    @Test
    public void getStorageKeysByEarByte() {
        GetStorageCommand left = new GetStorageCommand(StorageCapacityResult.EAR_LEFT);
        assertEquals(GetStorageCommand.class.getName() + ":00",
                left.semanticKey(left.getCommandData()));

        GetStorageCommand both = new GetStorageCommand(StorageCapacityResult.EAR_BOTH);
        assertEquals(GetStorageCommand.class.getName() + ":02",
                both.semanticKey(both.getCommandData()));
    }

    @Test
    public void getRecordIdListKeysByWholePayload() {
        GetRecordIdListCommand page = new GetRecordIdListCommand(1, 1);
        assertEquals("99ECC00002010C1234", page.getCommandData());
        assertEquals(GetRecordIdListCommand.class.getName() + ":99ECC00002010C1234",
                page.semanticKey(page.getCommandData()));

        GetRecordIdListCommand current = new GetRecordIdListCommand(0, 0);
        assertEquals("99ECC0000200001234", current.getCommandData());
        assertEquals(GetRecordIdListCommand.class.getName() + ":99ECC0000200001234",
                current.semanticKey(current.getCommandData()));
    }

    @Test
    public void controlCommandKeysByFirstDataByte() {
        ControlCommand v12 = new ControlCommand(
                ControlCommand.EAR_RIGHT, ControlCommand.TAP_TRIPLE, 3);
        assertEquals("99EC85000203031234", v12.getCommandData());
        assertEquals(ControlCommand.class.getName() + ":03",
                v12.semanticKey(v12.getCommandData()));

        ControlCommandV11 v11 = new ControlCommandV11(
                ControlCommandV11.TAP_TRIPLE, 2);
        assertEquals("99EC85000203021234", v11.getCommandData());
        assertEquals(ControlCommandV11.class.getName() + ":03",
                v11.semanticKey(v11.getCommandData()));
    }

    @Test
    public void pressSetCommandsKeyByEarByte() {
        LongPressSetCommand longPress = new LongPressSetCommand(2, 1);
        assertEquals("99ECB6000201021234", longPress.getCommandData());
        assertEquals(LongPressSetCommand.class.getName() + ":01",
                longPress.semanticKey(longPress.getCommandData()));

        ShortPressSetCommand shortPress = new ShortPressSetCommand(1, 2);
        assertEquals("99ECB8000202011234", shortPress.getCommandData());
        assertEquals(ShortPressSetCommand.class.getName() + ":02",
                shortPress.semanticKey(shortPress.getCommandData()));

        DoublePressSetCommand doublePress = new DoublePressSetCommand(3, 0);
        assertEquals("99ECC9000200031234", doublePress.getCommandData());
        assertEquals(DoublePressSetCommand.class.getName() + ":00",
                doublePress.semanticKey(doublePress.getCommandData()));
    }

    @Test
    public void customEqRealtimeCommandsShareFixedKey() {
        CustomEQRealtimeCommand realtime =
                new CustomEQRealtimeCommand(new int[CustomEQRealtimeCommand.BAND_COUNT]);
        assertEquals("custom-eq-realtime",
                realtime.semanticKey(realtime.getCommandData()));

        CustomEQCancelRealtimeCommand cancel = new CustomEQCancelRealtimeCommand();
        assertEquals("custom-eq-realtime",
                cancel.semanticKey(cancel.getCommandData()));
    }

    @Test
    public void syncRecordFileIdOpDeleteUsesLongerTimeoutExportKeepsDeclared() {
        SyncRecordFileIdOpCommand delete =
                SyncRecordFileIdOpCommand.forDelete(new long[]{1L}, 1);
        assertEquals(5_000L, delete.responseTimeoutMs(1234L));

        SyncRecordFileIdOpCommand export =
                SyncRecordFileIdOpCommand.forExport(1L, 1);
        assertEquals(1234L, export.responseTimeoutMs(1234L));
    }
}
