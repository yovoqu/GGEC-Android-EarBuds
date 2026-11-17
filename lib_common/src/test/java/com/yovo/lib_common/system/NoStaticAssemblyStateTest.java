package com.yovo.lib_common.system;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 装配状态归属基线（R-8）。
 *
 * <p>1）日志/下载/刷新率/权限/保活的核心类型不得再持有静态可写字段（{@code static volatile} 等）；<br>
 * 2）装配实例由各自域门面私有 final 持有，平台实现经构造注入；<br>
 * 3）需要「可替换安装」的域（下载）把核心实例放在门面持有的装配持有者里，持有者自身也无静态可写状态；<br>
 * 4）保活会话表/回执表是核心实例的字段：不存在指向注册表的静态单例，
 * 整包也不存在可经静态单例到达的可变容器状态（P0-2）。</p>
 */
public final class NoStaticAssemblyStateTest {

    private static final List<String> CORE_TYPES = Arrays.asList(
            "com.yovo.lib_common.log.internal.core.LogCore",
            "com.yovo.lib_common.download.internal.core.DownloadCore",
            "com.yovo.lib_common.system.refresh_rate.internal.core.RefreshRateCore",
            "com.yovo.lib_common.system.permission.internal.core.PermissionCore",
            "com.yovo.lib_common.system.keepalive.internal.core.KeepAliveCore");

    /** {门面类型, 门面持有的核心类型} 对照。 */
    private static final String[][] FACADE_HOLDERS = {
            {
                    "com.yovo.lib_common.log.YoLog",
                    "com.yovo.lib_common.log.internal.core.LogCore"
            },
            {
                    "com.yovo.lib_common.system.refresh_rate.YoRefreshRate",
                    "com.yovo.lib_common.system.refresh_rate.internal.core.RefreshRateCore"
            },
            {
                    "com.yovo.lib_common.system.permission.YoPermission",
                    "com.yovo.lib_common.system.permission.internal.core.PermissionCore"
            },
            {
                    "com.yovo.lib_common.system.keepalive.YoKeepAlive",
                    "com.yovo.lib_common.system.keepalive.internal.core.KeepAliveCore"
            }
    };

    /** {门面类型, 门面持有的装配持有者, 持有者持有的核心类型} 对照。 */
    private static final String[][] INSTALLATION_HOLDERS = {
            {
                    "com.yovo.lib_common.download.YoDownload",
                    "com.yovo.lib_common.download.internal.core.DownloadInstallation",
                    "com.yovo.lib_common.download.internal.core.DownloadCore"
            }
    };

    private static final String KEEPALIVE_PACKAGE = "com.yovo.lib_common.system.keepalive";

    private static final String KEEPALIVE_SESSION_REGISTRY =
            KEEPALIVE_PACKAGE + ".internal.core.KeepAliveSessionRegistry";

    private static final String KEEPALIVE_ACK_REGISTRY =
            KEEPALIVE_PACKAGE + ".internal.ack.KeepAliveAckRegistry";

    private static final String KEEPALIVE_CORE =
            KEEPALIVE_PACKAGE + ".internal.core.KeepAliveCore";

    @Test
    public void coresHaveNoStaticWritableFields() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String typeName : CORE_TYPES) {
            Class<?> type = load(typeName);
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                    offenders.add(typeName + "#" + field.getName());
                }
            }
        }
        assertTrue("静态可写装配字段必须为 0：" + offenders, offenders.isEmpty());
    }

    @Test
    public void facadesHoldCoreInstances() throws Exception {
        for (String[] pair : FACADE_HOLDERS) {
            Class<?> facade = load(pair[0]);
            assertTrue("门面必须私有 final 持有核心实例：" + pair[0],
                    holdsFinalField(facade, pair[1], true));
        }
    }

    @Test
    public void installationsHoldCoresWithoutStaticState() throws Exception {
        for (String[] triple : INSTALLATION_HOLDERS) {
            Class<?> facade = load(triple[0]);
            Class<?> installation = load(triple[1]);
            assertTrue("门面必须私有 final 持有装配持有者：" + triple[0],
                    holdsFinalField(facade, triple[1], true));
            for (Field field : installation.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                assertTrue("装配持有者不得有静态可写字段：" + triple[1] + "#" + field.getName(),
                        !Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers));
            }
            assertTrue("装配持有者必须持有核心实例：" + triple[1],
                    holdsFinalField(installation, triple[2], false));
        }
    }

    /** 会话表/回执表必须是核心实例的字段（构造注入的来源）。 */
    @Test
    public void keepAliveRegistriesAreInstanceStateOfCore() throws Exception {
        Class<?> core = load(KEEPALIVE_CORE);
        assertTrue("KeepAliveCore 必须持有会话注册表实例字段：" + KEEPALIVE_SESSION_REGISTRY,
                holdsFinalField(core, KEEPALIVE_SESSION_REGISTRY, false));
        assertTrue("KeepAliveCore 必须持有回执注册表实例字段：" + KEEPALIVE_ACK_REGISTRY,
                holdsFinalField(core, KEEPALIVE_ACK_REGISTRY, false));
    }

    /** 注册表本身不能是「可达静态单例」：无指向自身类型的静态字段/静态工厂。 */
    @Test
    public void keepAliveRegistriesExposeNoStaticSingleton() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (String typeName : Arrays.asList(KEEPALIVE_SESSION_REGISTRY, KEEPALIVE_ACK_REGISTRY)) {
            Class<?> registry = load(typeName);
            for (Field field : registry.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        && field.getType().equals(registry)) {
                    offenders.add(typeName + "#" + field.getName());
                }
            }
            for (Method method : registry.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getReturnType().equals(registry)) {
                    offenders.add(typeName + "#" + method.getName() + "()");
                }
            }
        }
        assertTrue("注册表不得有静态单例入口/静态实例字段：" + offenders, offenders.isEmpty());
    }

    /**
     * 保活整包不得存在可经静态单例到达的可变容器状态。
     *
     * <p>判据是结构性的：静态非 final 字段，或静态 final 但类型本身可变
     * （Map / Collection / Atomic* / Handler / StringBuilder / 数组）。
     * 静态 final 的常量与不可变值对象树不受影响。</p>
     */
    @Test
    public void keepAlivePackageHasNoStaticMutableContainerState() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : loadPackage(KEEPALIVE_PACKAGE)) {
            for (Field field : type.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isStatic(modifiers)) {
                    continue;
                }
                String location = type.getName() + "#" + field.getName();
                if (!Modifier.isFinal(modifiers)) {
                    offenders.add(location + "（静态可写字段）");
                } else if (isMutableContainer(field.getType())) {
                    offenders.add(location + "（静态可变容器：" + field.getType().getName() + "）");
                }
            }
        }
        assertTrue("保活包内静态单例不得承载可变容器状态：" + offenders, offenders.isEmpty());
    }

    private static boolean isMutableContainer(Class<?> type) {
        if (type.isArray()
                || Map.class.isAssignableFrom(type)
                || Iterable.class.isAssignableFrom(type)
                || StringBuilder.class.equals(type)
                || StringBuffer.class.equals(type)
                || AtomicBoolean.class.equals(type)
                || AtomicInteger.class.equals(type)
                || AtomicLong.class.equals(type)
                || AtomicReference.class.equals(type)
                || ConcurrentHashMap.class.equals(type)) {
            return true;
        }
        // Handler / HandlerThread 等平台线程载体：按名字判定，避免测试期依赖 android.os 类。
        String name = type.getName();
        return name.startsWith("android.os.Handler")
                || name.startsWith("android.os.Looper")
                || name.startsWith("android.database.sqlite");
    }

    private static List<Class<?>> loadPackage(String packageName) throws Exception {
        ClassLoader loader = NoStaticAssemblyStateTest.class.getClassLoader();
        String resourcePath = packageName.replace('.', '/');
        URL url = loader.getResource(resourcePath);
        assertNotNull("测试类路径必须包含包目录：" + resourcePath, url);
        assertTrue("包目录必须是目录型资源（AGP 单测类目录）：" + url,
                "file".equals(url.getProtocol()));
        Path root = new File(url.toURI()).toPath();
        List<Class<?>> types = new ArrayList<>();
        List<Path> classFiles = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.filter(path -> path.toString().endsWith(".class")).forEach(classFiles::add);
        }
        for (Path classFile : classFiles) {
            String relative = root.relativize(classFile).toString()
                    .replace(File.separatorChar, '/');
            if (relative.contains("$")) {
                continue;
            }
            String simpleName = relative.substring(0, relative.length() - ".class".length())
                    .replace('/', '.');
            types.add(load(packageName + "." + simpleName));
        }
        assertTrue("保活包必须至少扫描到一个类型：" + types, !types.isEmpty());
        return types;
    }

    private static boolean holdsFinalField(Class<?> owner, String fieldType, boolean requireStatic) {
        for (Field field : owner.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            boolean staticOk = requireStatic
                    ? Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)
                    : !Modifier.isStatic(modifiers);
            if (staticOk
                    && Modifier.isPrivate(modifiers)
                    && field.getType().getName().equals(fieldType)) {
                return true;
            }
        }
        return false;
    }

    /** 只完成类加载，不触发静态初始化。 */
    private static Class<?> load(String typeName) throws ClassNotFoundException {
        return Class.forName(
                typeName, false, NoStaticAssemblyStateTest.class.getClassLoader());
    }
}
