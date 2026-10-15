package com.yovo.biz_appupdate.internal.infrastructure.platform.pgy;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.yovo.biz_appupdate.BuildConfig;
import com.yovo.biz_appupdate.internal.contract.UpdateCheckProvider;
import com.yovo.biz_appupdate.internal.model.RemoteUpdate;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PgyUpdateCheckProvider implements UpdateCheckProvider {

    private static final String API_URL = "https://api.pgyer.com/apiv2/app/check";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public void check(@NonNull String currentVersion, @NonNull Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(API_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                LinkedHashMap<String, String> params = new LinkedHashMap<>();
                params.put("_api_key", BuildConfig.PGY_API_KEY);
                params.put("appKey", BuildConfig.PGY_APP_KEY);
                params.put("buildVersion", currentVersion);

                StringBuilder body = new StringBuilder();
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    if (body.length() > 0) {
                        body.append('&');
                    }
                    body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
                    body.append('=');
                    body.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.name()));
                }

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(payload.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    MAIN.post(() -> callback.onError("HTTP Error: " + responseCode));
                    return;
                }

                String response = readResponse(conn);

                JSONObject json = new JSONObject(response);
                if (json.optInt("code", -1) != 0) {
                    MAIN.post(() -> callback.onError(json.optString("message", "Unknown error")));
                    return;
                }

                JSONObject data = json.getJSONObject("data");
                RemoteUpdate update = new RemoteUpdate(
                        data.optBoolean("buildHaveNewVersion"),
                        data.optString("buildVersion"),
                        PgyerCheckMapper.parseVersionCode(data.optString("buildVersionNo")),
                        data.optString("buildUpdateDescription"),
                        PgyerCheckMapper.resolveDownloadUrl(
                                data.optString("downloadURL"),
                                data.optString("buildKey"),
                                BuildConfig.PGY_API_KEY),
                        data.optBoolean("needForceUpdate", false),
                        data.optString("buildMd5"),
                        data.optString("buildSha256"),
                        PgyerCheckMapper.parseFileSize(data.opt("buildFileSize")));
                MAIN.post(() -> callback.onSuccess(update));
            } catch (Exception e) {
                MAIN.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "check failed"));
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    @NonNull
    private static String readResponse(@NonNull HttpURLConnection conn) throws IOException {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }
}
