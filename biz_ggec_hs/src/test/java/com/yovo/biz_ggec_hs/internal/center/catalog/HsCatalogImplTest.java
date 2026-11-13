package com.yovo.biz_ggec_hs.internal.center.catalog;

import com.yovo.biz_ggec_hs.internal.interact.HsCapabilityRouter;


import com.yovo.biz_ggec_hs.api.HsDevice;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 设备目录：机型 → 变种 → 能力，以及描述符不变量。
 *
 * <p>这些是「能力知识只有一处」的可见证据：数量、归属、LEVEL 值域、选项完整性。</p>
 */
public final class HsCatalogImplTest {

    private final HsCatalogImpl catalog = new HsCatalogImpl();

    @Test
    public void declaredFormsMatchReachableCommands() {
        HsCapabilityRouter.verify(catalog);
    }

    @Test
    public void exposesTwoModelsWithoutVariants() {
        assertEquals(2, catalog.models().size());
        HsDevice.Product hs01 = catalog.model("HS01").orElseThrow();
        HsDevice.Product hs02 = catalog.model("HS02").orElseThrow();
        assertEquals(HsDevice.Type.EARBUDS, hs01.deviceType());
        assertEquals(HsDevice.Type.EARBUDS, hs02.deviceType());
        assertEquals("HS", hs01.projectId());
        assertEquals("HS", hs02.projectId());
        assertTrue(catalog.model("HS03").isEmpty());
    }

    @Test
    public void hs01DeclaresTwentyEightCapabilitiesAndHs02AddsSeven() {
        Set<HsDevice.Capability> hs01 = catalog.model("HS01").orElseThrow().capabilities();
        Set<HsDevice.Capability> hs02 = catalog.model("HS02").orElseThrow().capabilities();
        assertEquals(28, hs01.size());
        assertEquals(35, hs02.size());
        assertEquals("HS02 必须是 HS01 的超集", true, hs02.containsAll(hs01));
        Set<HsDevice.Capability> extra = new HashSet<>(hs02);
        extra.removeAll(hs01);
        assertEquals(Set.of(HsDevice.Capability.DUAL_CONNECTION, HsDevice.Capability.DUAL_CONNECTION_DEVICES,
                HsDevice.Capability.CUSTOM_EQ, HsDevice.Capability.TOUCH_DOUBLE,
                HsDevice.Capability.SERVICE_STATUS, HsDevice.Capability.BEGINNER_MODE,
                HsDevice.Capability.INTERPRET), extra);
        assertTrue(hs01.contains(HsDevice.Capability.TOUCH_TAP));
        assertFalse(hs01.contains(HsDevice.Capability.TOUCH_DOUBLE));
        // 重构前 app 已有的四个入口不得缺失（旧 device_row_ldac / device_row_volume_adaptive /
        // SETTING_RESET_CONTROL / reboot()）。
        for (HsDevice.Capability capability : new HsDevice.Capability[]{
                HsDevice.Capability.LDAC, HsDevice.Capability.VOLUME_ADAPTIVE,
                HsDevice.Capability.RESET_CONTROL, HsDevice.Capability.REBOOT}) {
            assertTrue("HS01 必须声明 " + capability, hs01.contains(capability));
            assertTrue("HS02 必须声明 " + capability, hs02.contains(capability));
        }
    }

    @Test
    public void everyCapabilityHasExactlyOneDescriptor() {
        assertEquals(HsDevice.Capability.values().length, catalog.descriptors().size());
        for (HsDevice.Capability capability : HsDevice.Capability.values()) {
            HsDevice.Feature descriptor = catalog.descriptor(capability);
            assertEquals(capability, descriptor.capability());
            assertNotNull(descriptor.title());
            assertFalse("标题不能为空: " + capability, descriptor.title().trim().isEmpty());
            assertNotNull(descriptor.group());
            assertNotNull(descriptor.kind());
        }
    }

    @Test
    public void levelCapabilitiesCarryRangeAndOthersDoNot() {
        for (HsDevice.Feature descriptor : catalog.descriptors()) {
            if (descriptor.kind() == HsDevice.Form.LEVEL) {
                assertTrue("LEVEL 必须给值域: " + descriptor.capability(),
                        descriptor.range().isPresent());
                HsDevice.Range range = descriptor.range().get();
                assertTrue(range.min() <= range.max());
                assertTrue(range.step() > 0);
                continue;
            }
            assertTrue("非 LEVEL 不得带值域: " + descriptor.capability(),
                    descriptor.range().isEmpty());
        }
        assertEquals("电量是纯门控项，不得渲染成可写滑杆",
                HsDevice.Form.GATE, catalog.descriptor(HsDevice.Capability.BATTERY).kind());
        assertTrue(catalog.descriptor(HsDevice.Capability.BATTERY).range().isEmpty());
    }

    @Test
    public void choiceCapabilitiesEnumerateTheirOptions() {
        for (HsDevice.Feature descriptor : catalog.descriptors()) {
            if (descriptor.kind() != HsDevice.Form.CHOICE) {
                assertTrue("非 CHOICE 不带选项: " + descriptor.capability(),
                        descriptor.options().isEmpty());
                continue;
            }
            assertFalse("CHOICE 必须给选项: " + descriptor.capability(),
                    descriptor.options().isEmpty());
            Set<String> ids = new HashSet<>();
            for (HsDevice.Option option : descriptor.options()) {
                assertTrue("选项 id 不能重复: " + descriptor.capability(),
                        ids.add(option.optionId()));
                assertFalse("选项标题不能为空: " + descriptor.capability(),
                        option.title().trim().isEmpty());
            }
        }
        assertEquals(6, catalog.descriptor(HsDevice.Capability.EQ_MODE).options().size());
        assertEquals(2, catalog.descriptor(HsDevice.Capability.SOUND_QUALITY).options().size());
        // 三档耳类型 × 五种长触操作
        assertEquals(15, catalog.descriptor(HsDevice.Capability.TOUCH_LONG).options().size());
        // 两耳 × （双击 6 + 三击 9）
        assertEquals(30, catalog.descriptor(HsDevice.Capability.TOUCH_TAP).options().size());
    }

    /** 声明态能力一律 GATE：不产生通用控件行（展示由调用方既有界面承担）。 */
    @Test
    public void declarationOnlyCapabilitiesAreGates() {
        for (HsDevice.Capability capability : new HsDevice.Capability[]{
                HsDevice.Capability.CONNECTION, HsDevice.Capability.BATTERY,
                HsDevice.Capability.DEVICE_INFO, HsDevice.Capability.SERVICE_STATUS,
                HsDevice.Capability.AI_ASSISTANT, HsDevice.Capability.OTA,
                HsDevice.Capability.OFFLINE_LOG, HsDevice.Capability.CLASSIC_BT}) {
            assertEquals("声明态能力必须是 GATE: " + capability,
                    HsDevice.Form.GATE, catalog.descriptor(capability).kind());
        }
    }

    /** 双连接拆成两个面：开关（TOGGLE）与设备列表（LIST）。 */
    @Test
    public void dualConnectionHasASeparateDevicesCapability() {
        HsDevice.Feature devices = catalog.descriptor(HsDevice.Capability.DUAL_CONNECTION_DEVICES);
        assertEquals(HsDevice.Form.LIST, devices.kind());
        assertEquals(HsDevice.Group.DUAL_CONNECTION, devices.group());
        assertEquals("已配对设备", devices.title());
        assertEquals(HsDevice.Form.TOGGLE, catalog.descriptor(HsDevice.Capability.DUAL_CONNECTION).kind());
        assertTrue("HS02 才有双连接设备列表",
                catalog.model("HS02").orElseThrow().capabilities()
                        .contains(HsDevice.Capability.DUAL_CONNECTION_DEVICES));
        assertFalse("HS01 没有双连接设备列表",
                catalog.model("HS01").orElseThrow().capabilities()
                        .contains(HsDevice.Capability.DUAL_CONNECTION_DEVICES));
    }

    /**
     * 目录不变量逐条核对：ACTION 必须可达、LEVEL 必须带值域、INFO/GATE 不得可写也不得带值域、
     * 其余形态不得带值域。遍历全量能力，新加能力漏了不变量这里会红。
     */
    @Test
    public void everyCapabilitySatisfiesItsKindInvariant() {
        for (HsDevice.Capability capability : HsDevice.Capability.values()) {
            HsDevice.Feature descriptor = catalog.descriptor(capability);
            boolean writable = HsCapabilityRouter.supportsWrite(capability);
            boolean actionable = HsCapabilityRouter.supportsAction(capability);
            switch (descriptor.kind()) {
                case ACTION -> assertTrue("ACTION 必须有可达命令: " + capability,
                        writable || actionable);
                case LEVEL -> assertTrue("LEVEL 必须带值域: " + capability,
                        descriptor.range().isPresent());
                case GATE -> {
                    assertTrue("GATE 不得带值域: " + capability,
                            descriptor.range().isEmpty());
                    assertFalse("GATE 不得可写: " + capability, writable);
                }
                case TOGGLE, CHOICE, CURVE, LIST -> assertTrue(
                        "非 LEVEL 不得带值域: " + capability, descriptor.range().isEmpty());
            }
        }
    }

    /** 分类学：型号逐条声明项目与设备类型；分类列表由 models 聚合，不维护第二份表。 */
    @Test
    public void taxonomyIsDeclaredPerModelAndAggregated() {
        for (HsDevice.Product model : catalog.models()) {
            assertNotNull("型号必须声明项目: " + model.modelId(), model.projectId());
            assertNotNull("型号必须声明设备类型: " + model.modelId(), model.deviceType());
        }
        assertEquals(List.of(HsDevice.Type.EARBUDS), catalog.deviceTypes());
        assertEquals(List.of(new HsDevice.Project("HS", "HS 系列")), catalog.projects());
        assertEquals("目录只登记已确认型号，不预编造其它项目",
                List.of("HS01", "HS02"),
                catalog.models().stream().map(HsDevice.Product::modelId).toList());
    }

    @Test
    public void unknownCapabilityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> catalog.descriptor(null));
    }
}
