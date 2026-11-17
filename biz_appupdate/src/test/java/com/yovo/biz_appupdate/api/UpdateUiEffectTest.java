package com.yovo.biz_appupdate.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.EnumSet;

/**
 * UI 动作集合收口（R-10）：只有「安装未知来源权限」与「打开应用设置页」两种宿主动作。
 *
 * <p>下载目录由宿主配置（本应用装配 {@code DownloadDir.appPrivate}），引擎的目录可访问判定对
 * {@code APP_PRIVATE} 恒为 true，因此「选下载目录 / 请求下载目录权限」两条动作永远不会被发出，
 * 已从契约里删除。这条断言把删除后的集合钉死，防止它们被顺手加回来。</p>
 */
public final class UpdateUiEffectTest {

    @Test
    public void effectVocabularyHasNoDownloadDirPicking() {
        assertEquals(EnumSet.of(UpdateUiEffect.Kind.REQUEST_INSTALL_UNKNOWN_SOURCES,
                        UpdateUiEffect.Kind.OPEN_APP_SETTINGS),
                EnumSet.allOf(UpdateUiEffect.Kind.class));
    }

    @Test
    public void effectRejectsMissingKind() {
        try {
            new UpdateUiEffect(null);
        } catch (IllegalArgumentException expected) {
            assertEquals("effect kind 不能为空", expected.getMessage());
            return;
        }
        throw new AssertionError("kind 为空必须抛 IllegalArgumentException");
    }
}
