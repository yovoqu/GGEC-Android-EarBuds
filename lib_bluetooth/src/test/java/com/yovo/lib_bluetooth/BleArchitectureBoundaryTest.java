package com.yovo.lib_bluetooth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * BLE-001 架构与边界静态隔离校验：
 * <ul>
 *   <li>根包只含 YoBluetoothSDK.java；api 目录只含 YoBleClient.java 与 YoCbtClient.java；</li>
 *   <li>api 与 model.ble 零 android.bluetooth / Context / BroadcastReceiver / Intent / internal 导入；</li>
 *   <li>yoble 与 yocbt 零交叉导入；非 provider/lib_common 代码零 com.yovo.lib_common 导入；</li>
 *   <li>consumer-rules.pro 与 proguard-rules.pro 保留 YoBluetoothSDK / api / model.ble。</li>
 * </ul>
 */
public class BleArchitectureBoundaryTest {

    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([^;\\s]+)\\s*;");

    @Test
    public void apiAndRootPackage_containOnlyApprovedFacades() throws IOException {
        File moduleRoot = locateModuleRoot();
        File srcJava = new File(moduleRoot, "src/main/java/com/yovo/lib_bluetooth");

        List<String> rootFiles = listJavaNames(srcJava);
        assertEquals(Collections.singletonList("YoBluetoothSDK.java"), rootFiles);

        List<String> apiFiles = listJavaNames(new File(srcJava, "api"));
        List<String> expectedApi = new ArrayList<>();
        expectedApi.add("YoBleClient.java");
        expectedApi.add("YoCbtClient.java");
        Collections.sort(expectedApi);
        assertEquals(expectedApi, apiFiles);
    }

    @Test
    public void apiAndBleModels_haveZeroAndroidFrameworkOrInternalImports() throws IOException {
        File moduleRoot = locateModuleRoot();
        File srcJava = new File(moduleRoot, "src/main/java/com/yovo/lib_bluetooth");
        List<Path> targets = new ArrayList<>();
        collectJavaFiles(new File(srcJava, "api"), targets);
        collectJavaFiles(new File(srcJava, "model/ble"), targets);
        assertTrue(!targets.isEmpty());

        List<String> violations = new ArrayList<>();
        for (Path p : targets) {
            for (String imp : extractImports(p)) {
                if (imp.startsWith("android.bluetooth.")
                        || imp.equals("android.content.Context")
                        || imp.equals("android.content.BroadcastReceiver")
                        || imp.equals("android.content.Intent")
                        || imp.startsWith("com.yovo.lib_bluetooth.internal.")) {
                    violations.add(p.getFileName() + " imports " + imp);
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("Public BLE/API contract boundary violation:\n" + String.join("\n", violations));
        }
    }

    @Test
    public void yobleAndYocbt_haveZeroCrossImports_andProviderBoundaryRespected() throws IOException {
        File moduleRoot = locateModuleRoot();
        File srcJava = new File(moduleRoot, "src/main/java/com/yovo/lib_bluetooth");

        List<Path> bleFiles = new ArrayList<>();
        collectJavaFiles(new File(srcJava, "internal/ble"), bleFiles);
        collectJavaFiles(new File(srcJava, "model/ble"), bleFiles);

        List<Path> classicFiles = new ArrayList<>();
        collectJavaFiles(new File(srcJava, "internal/classic"), classicFiles);
        collectJavaFiles(new File(srcJava, "model/classic"), classicFiles);

        List<String> violations = new ArrayList<>();
        for (Path p : bleFiles) {
            for (String imp : extractImports(p)) {
                if (imp.contains(".classic.")) {
                    violations.add("BLE imports Classic: " + p + " -> " + imp);
                }
            }
        }
        for (Path p : classicFiles) {
            for (String imp : extractImports(p)) {
                if (imp.contains(".internal.ble.") || imp.contains(".model.ble.")) {
                    violations.add("Classic imports BLE: " + p + " -> " + imp);
                }
            }
        }

        List<Path> allMain = new ArrayList<>();
        collectJavaFiles(srcJava, allMain);
        for (Path p : allMain) {
            String normalized = p.toString().replace('\\', '/');
            if (normalized.contains("/provider/lib_common/")) {
                continue;
            }
            for (String imp : extractImports(p)) {
                if (imp.startsWith("com.yovo.lib_common.")) {
                    violations.add("Non-provider imports lib_common: " + p + " -> " + imp);
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Cross-domain or provider boundary violations:\n" + String.join("\n", violations));
        }
    }

    @Test
    public void proguardRules_keepSdkApiAndBleModels() throws IOException {
        File moduleRoot = locateModuleRoot();
        String consumer = Files.readString(
                new File(moduleRoot, "consumer-rules.pro").toPath(), StandardCharsets.UTF_8);
        String proguard = Files.readString(
                new File(moduleRoot, "proguard-rules.pro").toPath(), StandardCharsets.UTF_8);

        for (String content : new String[]{consumer, proguard}) {
            assertTrue(content.contains("com.yovo.lib_bluetooth.YoBluetoothSDK"));
            assertTrue(content.contains("com.yovo.lib_bluetooth.api.**"));
            assertTrue(content.contains("com.yovo.lib_bluetooth.model.ble.**"));
        }
    }

    @Test
    public void androidPlatformAdapters_flattenedWithoutSingleFileSubpackages() {
        File moduleRoot = locateModuleRoot();
        File androidDir = new File(moduleRoot,
                "src/main/java/com/yovo/lib_bluetooth/internal/ble/internal/android");
        assertTrue(new File(androidDir, "YoBleAndroidScanPlatform.java").isFile());
        assertTrue(new File(androidDir, "YoBleAndroidBondPlatform.java").isFile());
        assertTrue(new File(androidDir, "YoBleAndroidL2capPlatform.java").isFile());
        assertTrue(new File(androidDir, "YoBleAndroidPeripheralPlatform.java").isFile());
        assertTrue(new File(androidDir, "YoBleAndroidModuleGraph.java").isFile());

        for (String obsoleteSubdir : new String[]{"scanner", "pairing", "l2cap", "peripheral"}) {
            File dir = new File(androidDir, obsoleteSubdir);
            assertTrue("Obsolete single-file subpackage must not exist: " + dir, !dir.exists());
        }
    }

    private static File locateModuleRoot() {
        File direct = new File("src/main/java");
        if (direct.isDirectory()) {
            return new File(".");
        }
        File nested = new File("lib_bluetooth");
        if (new File(nested, "src/main/java").isDirectory()) {
            return nested;
        }
        throw new IllegalStateException("Cannot locate lib_bluetooth module root from "
                + new File(".").getAbsolutePath());
    }

    private static List<String> listJavaNames(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                names.add(f.getName());
            }
        }
        Collections.sort(names);
        return names;
    }

    private static void collectJavaFiles(File dir, List<Path> out) throws IOException {
        if (!dir.exists()) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir.toPath())) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .forEach(out::add);
        }
    }

    private static List<String> extractImports(Path path) throws IOException {
        List<String> imports = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            Matcher m = IMPORT_PATTERN.matcher(line);
            if (m.find()) {
                imports.add(m.group(1));
            }
        }
        return imports;
    }
}
