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

public class RnnExtractor {
    private static final String TAG = "RnnExtractor";
    private static final String PAGE_URL = "https://rnn.com.do/rnn-en-vivo/";
    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_RETRIES = 3;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface ExtractionCallback {
        void onSuccess(String streamUrl);
        void onError(String error);
    }

    public static void extractStreamUrl(ExtractionCallback callback) {
        executor.execute(() -> {
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    String pageHtml = fetchContent(PAGE_URL, "https://rnn.com.do/");
                    if (pageHtml == null) throw new Exception("Failed to fetch main page");

                    String iframeUrl = extractPattern(pageHtml, "iframe[^>]+src=[\"']([^\"']*streamhoster\\.com/embed[^\"']+)[\"']");
                    if (iframeUrl == null) throw new Exception("No streamhoster iframe found");

                    String playerHtml = fetchContent(iframeUrl, PAGE_URL);
                    if (playerHtml == null) throw new Exception("Failed to fetch player page");

                    String streamUrl = extractPattern(playerHtml, "\"hlsAdaptiveUrl\"\\s*:\\s*\\{\"url\"\\s*:\\s*\"(https?://[^\"]+\\.m3u8[^\"]*)\"");
                    if (streamUrl == null) throw new Exception("No HLS adaptive URL found in player config");

                    Log.d(TAG, "Stream URL extracted: " + streamUrl);
                    callback.onSuccess(streamUrl);
                    return;

                } catch (Exception e) {
                    Log.e(TAG, "Attempt " + attempt + " failed: " + e.getMessage());
                    if (attempt < MAX_RETRIES) {
                        try { Thread.sleep(attempt * 2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                    }
                }
            }
            callback.onError("Failed to extract stream after " + MAX_RETRIES + " attempts");
        });
    }

    private static String fetchContent(String urlString, String referer) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
            connection.setRequestProperty("Referer", referer);
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line).append("\n");
                reader.close();
                return response.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching: " + urlString, e);
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private static String extractPattern(String html, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(html);
        if (!matcher.find()) return null;
        return matcher.groupCount() > 0 ? matcher.group(1) : matcher.group(0);
    }
}