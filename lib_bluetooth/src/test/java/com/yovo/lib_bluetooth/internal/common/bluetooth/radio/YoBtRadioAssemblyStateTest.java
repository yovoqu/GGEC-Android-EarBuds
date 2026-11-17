package com.yovo.lib_bluetooth.internal.common.bluetooth.radio;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.yovo.lib_bluetooth.internal.ble.platform.YoBleAndroidModuleGraph;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 射频协调器装配归属基线。
 *
 * <p>模块内不存在“静态装配态”：协调器既不自己找平台，也不由任何静态持有者按需构造，
 * 只能由客户端装配点显式构造后沿构造参数逐层注入。</p>
 */
public final class YoBtRadioAssemblyStateTest {

    @Test
    public void coordinatorHasNoStaticWritableFields() {
        List<String> offenders = new ArrayList<>();
        for (Field field : YoBtRadioCoordinator.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                offenders.add(field.getName());
            }
        }
        assertTrue("协调器静态可写字段必须为 0：" + offenders, offenders.isEmpty());
    }

    @Test
    public void staticAssemblyHolderIsGone() {
        try {
            Class.forName("com.yovo.lib_bluetooth.internal.common.bluetooth.radio.YoBtRadioSharing");
            fail("静态装配态持有者必须不存在");
        } catch (ClassNotFoundException expected) {
            // 期望：进程级静态持有者已删除，协调器只由装配点构造。
        }
    }

    @Test
    public void coordinatorRequiresExplicitPlatform() {
        for (Constructor<?> constructor : YoBtRadioCoordinator.class.getDeclaredConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            assertTrue("协调器必须是 construct 注入平台： " + constructor,
                    parameters.length > 0 && parameters[0] == YoBtRadioPlatform.class);
        }
    }

    @Test
    public void bleGraphRequiresInjectedRadioCoordinator() {
        List<String> offenders = new ArrayList<>();
        for (Method method : YoBleAndroidModuleGraph.class.getDeclaredMethods()) {
            if (!Modifier.isStatic(method.getModifiers())
                    || !method.getName().equals("create")) {
                continue;
            }
            boolean injectsCoordinator = false;
            for (Class<?> parameter : method.getParameterTypes()) {
                if (parameter == YoBtRadioCoordinator.class) {
                    injectsCoordinator = true;
                }
            }
            if (!injectsCoordinator) {
                offenders.add(method.toString());
            }
        }
        assertTrue("BLE 装配图只能接受注入的协调器，不得自带默认来源：" + offenders,
                offenders.isEmpty());
    }
}
