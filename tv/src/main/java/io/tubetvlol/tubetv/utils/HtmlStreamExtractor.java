package io.tubetvlol.tubetv.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlStreamExtractor {
    private static final String TAG = "HtmlStreamExtractor";
    private static final int TIMEOUT_MS = 10000;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static final String DEFAULT_PATTERN = "https?://[^\"'\\s]*\\.m3u8[^\"'\\s]*";

    public interface ExtractionCallback {
        void onSuccess(String streamUrl);
        void onError(String error);
    }

    public static void extractStreamUrl(String pageUrl, String regexPattern, ExtractionCallback callback) {
        executor.execute(() -> {
            try {
                String html = fetchPageContent(pageUrl);
                if (html == null) {
                    callback.onError("Failed to fetch page content");
                    return;
                }

                String pattern = (regexPattern != null && !regexPattern.isEmpty()) ? regexPattern : DEFAULT_PATTERN;
                Matcher matcher = Pattern.compile(pattern).matcher(html);

                if (matcher.find()) {
                    String streamUrl = matcher.groupCount() > 0 ? matcher.group(1) : matcher.group(0);
                    Log.d(TAG, "Stream URL extracted: " + streamUrl);
                    callback.onSuccess(streamUrl);
                } else {
                    callback.onError("No stream URL found in page");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting stream URL", e);
                callback.onError("Error: " + e.getMessage());
            }
        });
    }

    private static String fetchPageContent(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                reader.close();
                return response.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching page content", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
}