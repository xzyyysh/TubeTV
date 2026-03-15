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

public class RtvdExtractor {
    private static final String TAG = "RtvdExtractor";
    private static final String PAGE_URL = "https://rtvd.gob.do/";
    private static final int TIMEOUT_MS = 10000;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface ExtractionCallback {
        void onSuccess(String streamUrl);
        void onError(String error);
    }

    public static void extractStreamUrl(ExtractionCallback callback) {
        executor.execute(() -> {
            try {
                String htmlContent = fetchPageContent(PAGE_URL);
                if (htmlContent == null) {
                    callback.onError("Failed to fetch page content");
                    return;
                }

                String streamUrl = extractM3u8Url(htmlContent);
                if (streamUrl != null) {
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

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
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

    private static String extractM3u8Url(String html) {
        Pattern pattern = Pattern.compile("https?://[^\"'\\s]*\\.m3u8[^\"'\\s]*");
        Matcher matcher = pattern.matcher(html);
        
        if (matcher.find()) {
            return matcher.group(0);
        }
        
        return null;
    }
}
