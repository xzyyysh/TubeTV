package io.tubetvlol.tubetv.utils;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class DailymotionExtractor {

    private static final String TAG = "DailymotionExtractor";
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 30000;
    private static final int MAX_RETRIES = 3;

    public interface StreamCallback {
        void onSuccess(String streamUrl);
        void onError(String error);
    }

    public static void getStreamUrl(String videoId, StreamCallback callback) {
        new Thread(() -> {
            int retries = 0;
            String lastError = null;

            while (retries < MAX_RETRIES) {
                try {
                    Log.d(TAG, "Attempting to fetch stream URL for video: " + videoId + " (attempt " + (retries + 1) + ")");
                    
                    String embedder = URLEncoder.encode("https://www.dailymotion.com/video/" + videoId, "UTF-8");
                    String apiUrl = "https://geo.dailymotion.com/video/" + videoId + ".json" +
                            "?legacy=true&geo=1&embedder=" + embedder;

                    URL url = new URL(apiUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
                    conn.setRequestProperty("Referer", "https://geo.dailymotion.com/");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(CONNECT_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);

                    int responseCode = conn.getResponseCode();
                    Log.d(TAG, "Response code: " + responseCode);

                    if (responseCode == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                        reader.close();
                        conn.disconnect();

                        String jsonString = response.toString();
                        if (jsonString.isEmpty()) {
                            throw new Exception("Empty response from API");
                        }

                        JSONObject json = new JSONObject(jsonString);
                        
                        if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            String errorMsg = error.optString("message", "Unknown error");
                            String errorCode = error.optString("code", "");
                            String fullError = "API error [" + errorCode + "]: " + errorMsg;
                            Log.e(TAG, fullError);
                            throw new Exception(fullError);
                        }

                        if (!json.has("qualities")) {
                            throw new Exception("No qualities field in response");
                        }

                        JSONObject qualities = json.getJSONObject("qualities");
                        if (!qualities.has("auto")) {
                            throw new Exception("No auto quality in response");
                        }

                        JSONArray autoArray = qualities.getJSONArray("auto");
                        if (autoArray.length() == 0) {
                            throw new Exception("Empty auto quality array");
                        }

                        JSONObject auto = autoArray.getJSONObject(0);
                        if (!auto.has("url")) {
                            throw new Exception("No URL in auto quality object");
                        }

                        String streamUrl = auto.getString("url");
                        if (streamUrl == null || streamUrl.isEmpty()) {
                            throw new Exception("Stream URL is empty");
                        }

                        if (!streamUrl.startsWith("http")) {
                            throw new Exception("Invalid stream URL format: " + streamUrl);
                        }

                        Log.d(TAG, "Successfully extracted stream URL");
                        callback.onSuccess(streamUrl);
                        return;

                    } else if (responseCode == 403) {
                        lastError = "Video blocked in your region (403)";
                        Log.e(TAG, lastError);
                        break;
                    } else if (responseCode == 404) {
                        lastError = "Video not found (404)";
                        Log.e(TAG, lastError);
                        break;
                    } else {
                        lastError = "HTTP error: " + responseCode;
                        Log.e(TAG, lastError);
                    }
                    
                    conn.disconnect();

                } catch (java.net.SocketTimeoutException e) {
                    lastError = "Connection timeout (attempt " + (retries + 1) + ")";
                    Log.e(TAG, lastError, e);
                } catch (java.net.UnknownHostException e) {
                    lastError = "No internet connection";
                    Log.e(TAG, lastError, e);
                    break;
                } catch (org.json.JSONException e) {
                    lastError = "Invalid JSON response: " + e.getMessage();
                    Log.e(TAG, lastError, e);
                    break;
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.e(TAG, "Error extracting stream: " + lastError, e);
                    if (lastError != null && (lastError.contains("API error") || lastError.contains("private") || lastError.contains("DM020"))) {
                        Log.e(TAG, "Video is private or restricted, stopping retries");
                        break;
                    }
                }

                retries++;
                if (retries < MAX_RETRIES) {
                    try {
                        Thread.sleep(1000 * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            callback.onError(lastError != null ? lastError : "Unknown error after " + MAX_RETRIES + " retries");
        }).start();
    }
}