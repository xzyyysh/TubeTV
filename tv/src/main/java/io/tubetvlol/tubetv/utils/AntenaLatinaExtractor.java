package io.tubetvlol.tubetv.utils;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AntenaLatinaExtractor {
    private static final String TAG = "AntenaLatinaExtractor";
    private static final String REFERER = "https://www.antena7.com.do/";
    private static final int TIMEOUT_MS = 8000;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface ExtractionCallback {
        void onSuccess(String streamUrl);
        void onError(String error);
    }

    public static void extractStreamUrl(String pageUrl, ExtractionCallback callback) {
        executor.execute(() -> {
            try {
                String htmlContent = fetchPageContent(pageUrl);
                if (htmlContent == null) {
                    callback.onError("Failed to fetch page content");
                    return;
                }

                List<String> streamUrls = extractStreamUrlsFromHtml(htmlContent);
                if (streamUrls.isEmpty()) {
                    callback.onError("No stream URLs found in page");
                    return;
                }

                String workingUrl = findWorkingStreamUrl(streamUrls);
                if (workingUrl != null) {
                    String finalUrl = resolveStreamUrl(workingUrl);
                    Log.d(TAG, "Stream URL extracted: " + finalUrl);
                    callback.onSuccess(finalUrl);
                } else {
                    Log.e(TAG, "All stream URLs failed validation");
                    callback.onError("Stream not available (all URLs failed)");
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

    private static List<String> extractStreamUrlsFromHtml(String html) {
        List<String> urls = new ArrayList<>();
        
        Pattern pattern = Pattern.compile("\"streamUrl\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(html);
        
        if (matcher.find()) {
            String streamUrl = matcher.group(1);
            streamUrl = streamUrl.replace("\\/", "/");
            
            if (streamUrl.contains("#")) {
                String[] parts = streamUrl.split("#");
                for (String part : parts) {
                    if (part.contains(".m3u8")) {
                        urls.add(part.trim());
                    }
                }
            } else if (streamUrl.contains(".m3u8")) {
                urls.add(streamUrl.trim());
            }
        }
        
        return urls;
    }

    private static String findWorkingStreamUrl(List<String> urls) {
        if (urls.isEmpty()) {
            return null;
        }
        
        for (String url : urls) {
            Log.d(TAG, "Testing stream URL: " + url);
            if (testStreamUrl(url)) {
                Log.d(TAG, "Working stream URL found: " + url);
                return url;
            }
        }
        
        Log.w(TAG, "All URLs failed validation");
        return null;
    }

    private static boolean testStreamUrl(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("Origin", "https://www.antena7.com.do");
            connection.setRequestProperty("Referer", REFERER);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK || 
                   responseCode == HttpURLConnection.HTTP_PARTIAL || 
                   responseCode == HttpURLConnection.HTTP_NOT_MODIFIED;
        } catch (Exception e) {
            Log.e(TAG, "Error testing stream URL: " + urlString, e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String resolveStreamUrl(String indexUrl) {
        String manifest = fetchManifest(indexUrl);
        if (manifest != null && manifest.contains("original.m3u8")) {
            String baseUrl = indexUrl.substring(0, indexUrl.lastIndexOf("/"));
            return baseUrl + "/original.m3u8";
        }
        return indexUrl;
    }

    private static String fetchManifest(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Origin", "https://www.antena7.com.do");
            connection.setRequestProperty("Referer", REFERER);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
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
            Log.e(TAG, "Error fetching manifest", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return null;
    }
}