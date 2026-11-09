package com.ggec.earbuds.data.device;

import com.ggec.earbuds.domain.device.port.DevicePreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/** 纯 java.io 的本地偏好存储，避免数据层依赖 Android。 */
public final class FileDevicePreferences implements DevicePreferences {

    private final File file;
    private final Properties properties = new Properties();

    public FileDevicePreferences(File directory) {
        this.file = new File(directory, "device-prefs.properties");
        load();
    }

    private synchronized void load() {
        if (!file.isFile()) {
            return;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
        } catch (IOException ignored) {
            // 读取失败按空配置处理，不阻塞页面。
        }
    }

    private synchronized void save() {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            properties.store(output, "device");
        } catch (IOException ignored) {
            // 写入失败不阻断交互，下次修改会重试。
        }
    }

    @Override
    public String lastMac() {
        return properties.getProperty("mac");
    }

    @Override
    public void rememberDevice(String mac, String modelId) {
        if (mac == null) {
            return;
        }
        properties.setProperty("mac", mac);
        save();
    }
}
