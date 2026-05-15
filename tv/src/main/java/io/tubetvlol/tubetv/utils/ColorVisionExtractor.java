package io.tubetvlol.tubetv.utils;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorVisionExtractor {

    private static final String TAG = "ColorVisionExtractor";
    private static final String PAGE_URL = "https://colorvision.com.do/en-vivo/";
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 30000;
    private static final int MAX_RETRIES = 3;

    public interface StreamCallback {
        void onSuccess(String streamUrl);
        void onError(String error);
    }

    public static void getStreamUrl(StreamCallback callback) {
        new Thread(() -> {
            int retries = 0;
            String lastError = null;

            while (retries < MAX_RETRIES) {
                try {
                    String videoId = extractVideoId();
                    if (videoId == null) throw new Exception("Could not extract Dailymotion video ID from page");

                    Log.d(TAG, "Extracted video ID: " + videoId);

                    String apiUrl = "https://geo.dailymotion.com/video/" + videoId + ".json" +
                            "?legacy=true&embedder=https%3A%2F%2Fcolorvision.com.do%2F&geo=1";

                    URL url = new URL(apiUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
                    conn.setRequestProperty("Referer", "https://colorvision.com.do/");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setConnectTimeout(CONNECT_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);

                    int responseCode = conn.getResponseCode();

                    if (responseCode == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                        reader.close();
                        conn.disconnect();

                        JSONObject json = new JSONObject(response.toString());

                        if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            throw new Exception("API error [" + error.optString("code") + "]: " + error.optString("message"));
                        }

                        JSONArray autoArray = json.getJSONObject("qualities").getJSONArray("auto");
                        String streamUrl = autoArray.getJSONObject(0).getString("url");

                        if (streamUrl == null || streamUrl.isEmpty() || !streamUrl.startsWith("http")) {
                            throw new Exception("Invalid stream URL: " + streamUrl);
                        }

                        Log.d(TAG, "Stream URL extracted successfully");
                        callback.onSuccess(streamUrl);
                        return;

                    } else if (responseCode == 403) {
                        lastError = "Video bloqueado en tu región (403)";
                        break;
                    } else if (responseCode == 404) {
                        lastError = "Video no encontrado (404)";
                        break;
                    } else {
                        lastError = "Error HTTP: " + responseCode;
                    }

                    conn.disconnect();

                } catch (java.net.SocketTimeoutException e) {
                    lastError = "Tiempo de espera agotado (intento " + (retries + 1) + ")";
                } catch (java.net.UnknownHostException e) {
                    lastError = "Sin conexión a internet";
                    break;
                } catch (org.json.JSONException e) {
                    lastError = "Respuesta JSON inválida: " + e.getMessage();
                    break;
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.e(TAG, "Error: " + lastError, e);
                    if (lastError != null && (lastError.contains("API error") || lastError.contains("private") || lastError.contains("DM020"))) break;
                }

                retries++;
                if (retries < MAX_RETRIES) {
                    try { Thread.sleep(1000L * retries); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                }
            }

            callback.onError(lastError != null ? lastError : "Error desconocido después de " + MAX_RETRIES + " intentos");
        }).start();
    }

    private static String extractVideoId() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(PAGE_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder html = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) html.append(line);
                reader.close();

                Matcher matcher = Pattern.compile("geo\\.dailymotion\\.com/player/[^\"']+\\?video=([a-zA-Z0-9]+)").matcher(html);
                return matcher.find() ? matcher.group(1) : null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting video ID", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }
}