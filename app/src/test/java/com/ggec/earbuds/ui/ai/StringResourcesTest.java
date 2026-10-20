package com.ggec.earbuds.ui.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * 字符串资源的格式出错只会在运行到那一句时崩溃，单元测试读不到资源，所以直接解析 strings.xml 检查。
 */
public class StringResourcesTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:(\\d+)\\$)?([a-zA-Z])");

    /** 事件里带参数的提示：参数一律以 String 传入，占位符只能是 %s，写成 %d 会在显示时抛异常。 */
    private static final Set<String> STRING_ARGUMENT = Set.of("ai_model_switched", "ai_fetch_done", "ai_speed_capped");

    private Map<String, String> load() throws Exception {
        File file = new File("src/main/res/values/strings.xml");
        assertTrue("run from the app module: " + file.getAbsolutePath(), file.exists());
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        NodeList nodes = document.getElementsByTagName("string");
        Map<String, String> strings = new HashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            String name = element.getAttribute("name");
            assertFalse("duplicate string " + name, strings.containsKey(name));
            strings.put(name, element.getTextContent());
        }
        return strings;
    }

    @Test
    public void everyStringWithSeveralPlaceholdersNumbersThem() throws Exception {
        for (Map.Entry<String, String> entry : load().entrySet()) {
            Matcher matcher = PLACEHOLDER.matcher(entry.getValue());
            int count = 0;
            boolean positional = true;
            while (matcher.find()) {
                count++;
                positional &= matcher.group(1) != null;
            }
            assertTrue(entry.getKey() + " mixes unnumbered placeholders", count <= 1 || positional);
        }
    }

    @Test
    public void stringsFedWithStringArgumentsUseOnlyStringPlaceholders() throws Exception {
        Map<String, String> strings = load();
        for (String name : STRING_ARGUMENT) {
            String value = strings.get(name);
            assertTrue(name + " is missing", value != null);
            Matcher matcher = PLACEHOLDER.matcher(value);
            int count = 0;
            while (matcher.find()) {
                count++;
                assertEquals(name + " must take a String", "s", matcher.group(2));
            }
            assertEquals(name + " takes exactly one argument", 1, count);
        }
    }

    @Test
    public void failureAndValidationMessagesNeedNoArguments() throws Exception {
        for (Map.Entry<String, String> entry : load().entrySet()) {
            String name = entry.getKey();
            if (name.startsWith("ai_failure_") || name.startsWith("ai_error_")) {
                assertFalse(name + " is shown without arguments", PLACEHOLDER.matcher(entry.getValue()).find());
            }
        }
    }
}
