package com.ggec.earbuds.provider.biz_appupdate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ggec.earbuds.domain.update.port.UpdateUiPort;
import com.yovo.biz_appupdate.api.UpdateDownloadMode;
import com.yovo.biz_appupdate.api.UpdateUiEffect;
import com.yovo.biz_appupdate.api.UpdateUiEvent;
import com.yovo.biz_appupdate.api.UpdateUiState;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 桥的翻译表必须一一对应：biz 侧每加一个枚举值，这里就漏一个 → 编译期就挂。
 *
 * <p>纯 Java（无 Android），因此跑在 {@code :app:testDebugUnitTest} 里。</p>
 */
public class AppUpdateUiMapperTest {

    @Test
    public void noticeKindsCoverEveryBizKindAndStayDistinct() {
        UpdateUiEvent.Notice.Kind[] kinds = UpdateUiEvent.Notice.Kind.values();
        UpdateUiPort.Notice[] mapped = new UpdateUiPort.Notice[kinds.length];
        for (int index = 0; index < kinds.length; index++) {
            mapped[index] = AppUpdateUiMapper.toNotice(kinds[index]);
            assertEquals("kind 名称必须可追溯：" + kinds[index],
                    kinds[index].name(), mapped[index].name());
        }
        assertEquals("biz 与端口的 notice 数量必须一致",
                kinds.length, UpdateUiPort.Notice.values().length);
        assertEquals("notice 映射必须是一一对应",
                kinds.length, Arrays.stream(mapped).distinct().count());
    }

    @Test
    public void effectKindsCoverEveryBizKindAndStayDistinct() {
        UpdateUiEffect.Kind[] kinds = UpdateUiEffect.Kind.values();
        UpdateUiPort.Effect[] mapped = new UpdateUiPort.Effect[kinds.length];
        for (int index = 0; index < kinds.length; index++) {
            mapped[index] = AppUpdateUiMapper.toEffect(kinds[index]);
        }
        assertEquals("biz 与端口的 effect 数量必须一致",
                kinds.length, UpdateUiPort.Effect.values().length);
        assertEquals("effect 映射必须是一一对应",
                kinds.length, Arrays.stream(mapped).distinct().count());
        assertEquals(UpdateUiPort.Effect.REQUEST_INSTALL_PERMISSION,
                AppUpdateUiMapper.toEffect(UpdateUiEffect.Kind.REQUEST_INSTALL_UNKNOWN_SOURCES));
        assertEquals(UpdateUiPort.Effect.OPEN_APP_SETTINGS,
                AppUpdateUiMapper.toEffect(UpdateUiEffect.Kind.OPEN_APP_SETTINGS));
    }

    @Test
    public void downloadChoiceMapsToBizMode() {
        assertEquals(UpdateDownloadMode.INTERNAL,
                AppUpdateUiMapper.toDownloadMode(UpdateUiPort.DownloadChoice.INTERNAL));
        assertEquals(UpdateDownloadMode.SYSTEM,
                AppUpdateUiMapper.toDownloadMode(UpdateUiPort.DownloadChoice.SYSTEM));
        assertEquals(UpdateDownloadMode.BROWSER,
                AppUpdateUiMapper.toDownloadMode(UpdateUiPort.DownloadChoice.BROWSER));
    }

    @Test
    public void stateKeepsCheckingAndDownloadProgress() {
        UpdateUiPort.State idle = AppUpdateUiMapper.toState(UpdateUiState.idle());
        assertFalse(idle.checking());
        assertFalse(idle.downloadInProgress());
        assertEquals(0, idle.downloadPercent());

        UpdateUiPort.State downloading = AppUpdateUiMapper.toState(
                new UpdateUiState(false, true, 42));
        assertFalse(downloading.checking());
        assertTrue(downloading.downloadInProgress());
        assertEquals(42, downloading.downloadPercent());
    }

    @Test
    public void promptKeepsVersionDescriptionAndForceFlag() {
        UpdateUiPort.Prompt prompt = AppUpdateUiMapper.toPrompt(
                new UpdateUiEvent.ShowUpdatePrompt("1.2.3", "修复若干问题", true));
        assertEquals("1.2.3", prompt.version());
        assertEquals("修复若干问题", prompt.description());
        assertTrue(prompt.forceUpdate());
    }

    @Test
    public void everyEventKeepsItsPayloadWithoutLeakingSdkTypes() {
        UpdateUiEvent.Notice notice =
                new UpdateUiEvent.Notice(UpdateUiEvent.Notice.Kind.CHECK_FAILED, "timeout");
        UpdateUiPort.Event mappedNotice = AppUpdateUiMapper.toEvent(notice);
        assertEquals(new UpdateUiPort.Event.Notify(UpdateUiPort.Notice.CHECK_FAILED, "timeout"),
                mappedNotice);

        UpdateUiPort.Event mappedPrompt = AppUpdateUiMapper.toEvent(
                new UpdateUiEvent.ShowUpdatePrompt("2.0.0", "", false));
        assertEquals(new UpdateUiPort.Event.ShowUpdatePrompt(
                new UpdateUiPort.Prompt("2.0.0", "", false)), mappedPrompt);

        UpdateUiPort.Event mappedPicker = AppUpdateUiMapper.toEvent(
                new UpdateUiEvent.ShowLocalApkPicker(List.of("a.apk", "b.apk")));
        assertEquals(new UpdateUiPort.Event.ShowLocalApkPicker(List.of("a.apk", "b.apk")),
                mappedPicker);

        UpdateUiPort.Event mappedConfirm =
                AppUpdateUiMapper.toEvent(new UpdateUiEvent.ConfirmInstall("a.apk"));
        assertEquals(new UpdateUiPort.Event.ConfirmInstall("a.apk"), mappedConfirm);
    }
}
