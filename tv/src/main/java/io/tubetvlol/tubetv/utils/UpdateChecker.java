package io.tubetvlol.tubetv.utils;

import android.content.Context;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/xzyyysh/TubeTV/releases/latest";

    public interface UpdateCallback {
        void onResult(UpdateResult result);
    }

    public static class UpdateResult {
        public final boolean hasUpdate;
        public final String latestVersion;
        public final String changelog;
        public final String downloadUrl;
        public final String error;

        public UpdateResult(boolean hasUpdate, String latestVersion, String changelog, String downloadUrl, String error) {
            this.hasUpdate = hasUpdate;
            this.latestVersion = latestVersion;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.error = error;
        }
    }

    public static void checkForUpdate(Context context, UpdateCallback callback) {
        new Thread(() -> {
            try {
                String currentVersion = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0)
                    .versionName;

                String jsonString = fetchFromUrl(GITHUB_API_URL);
                JSONObject json = new JSONObject(jsonString);
                String latestTag = json.optString("tag_name", "");
                String latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
                String changelog = json.optString("body", "");
                String downloadUrl = "";
                if (json.has("assets") && json.getJSONArray("assets").length() > 0) {
                    downloadUrl = json.getJSONArray("assets").getJSONObject(0)
                        .optString("browser_download_url", "");
                }

                boolean hasUpdate = compareVersions(latestVersion, currentVersion) > 0;
                callback.onResult(new UpdateResult(hasUpdate, latestTag, changelog, downloadUrl, null));

            } catch (Exception e) {
                callback.onResult(new UpdateResult(false, null, null, null, e.getMessage()));
            }
        }).start();
    }

    private static String fetchFromUrl(String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent", "TubeTV");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.connect();

        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new Exception("GitHub API returned " + code);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();
        return response.toString();
    }

    private static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int n2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (n1 != n2) return n1 - n2;
        }
        return 0;
    }
}
