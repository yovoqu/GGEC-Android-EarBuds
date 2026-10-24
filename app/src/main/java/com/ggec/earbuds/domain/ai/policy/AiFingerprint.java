package com.ggec.earbuds.domain.ai.policy;

import androidx.annotation.Nullable;

import com.ggec.earbuds.domain.ai.model.AiPlatformConfig;
import com.ggec.earbuds.domain.ai.model.AiSpeedResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 配置指纹：判断"测试/测速结果是不是在当前配置下得出的"。API Key 只取摘要，指纹里不出现明文。
 */
public final class AiFingerprint {

    private AiFingerprint() {
    }

    /** 测速结果用：接口地址＋协议＋API Key。 */
    public static String speed(AiPlatformConfig platform) {
        return digest(nullToEmpty(platform.baseUrl()) + "\n" + platform.protocol().id() + "\n" + platform.apiKey());
    }

    /** 连接测试用：测速指纹＋测试模型。 */
    public static String connection(AiPlatformConfig platform, @Nullable String testModelId) {
        return speed(platform) + ":" + nullToEmpty(testModelId);
    }

    /** 测速结果是否过期（SPD-B-07）：旧数据没有指纹，不判过期。 */
    public static boolean stale(AiSpeedResult result, AiPlatformConfig platform) {
        return result.fingerprint() != null && !result.fingerprint().equals(speed(platform));
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    private static String digest(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
