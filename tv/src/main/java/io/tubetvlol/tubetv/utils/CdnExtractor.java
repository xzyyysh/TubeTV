package io.tubetvlol.tubetv.utils;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

public class CdnExtractor {

    private static final String TAG = "CdnExtractor";
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 30000;
    private static final int MAX_RETRIES = 3;
    private static final String VIDEO_ID = "x9lincs";

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
                    Log.d(TAG, "Attempting to fetch CDN stream URL (attempt " + (retries + 1) + ")");
                    
                    String dmV1st = generateSessionId();
                    long dmTs = System.currentTimeMillis() / 1000;
                    String viewId = generateViewId();
                    
                    String apiUrl = "https://geo.dailymotion.com/video/" + VIDEO_ID + ".json" +
                            "?legacy=true" +
                            "&embedder=https%3A%2F%2Fcdn.com.do%2F" +
                            "&geo=1" +
                            "&player-id=x1b7bk" +
                            "&publisher-id=x3jtcrw" +
                            "&locale=en-US" +
                            "&dmV1st=" + dmV1st +
                            "&dmTs=" + dmTs +
                            "&is_native_app=0" +
                            "&dmViewId=" + viewId +
                            "&parallelCalls=1";

                    URL url = new URL(apiUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36");
                    conn.setRequestProperty("Referer", "https://geo.dailymotion.com/player/x1b7bk.html?video=" + VIDEO_ID);
                    conn.setRequestProperty("Accept", "*/*");
                    conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                    conn.setRequestProperty("Origin", "https://geo.dailymotion.com");
                    conn.setRequestProperty("Sec-Fetch-Dest", "empty");
                    conn.setRequestProperty("Sec-Fetch-Mode", "cors");
                    conn.setRequestProperty("Sec-Fetch-Site", "same-origin");
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
                            throw new Exception("Empty response from CDN API");
                        }

                        JSONObject json = new JSONObject(jsonString);
                        
                        if (json.has("error")) {
                            JSONObject error = json.getJSONObject("error");
                            String errorMsg = error.optString("message", "Unknown error");
                            String errorCode = error.optString("code", "");
                            String fullError = "CDN API error [" + errorCode + "]: " + errorMsg;
                            Log.e(TAG, fullError);
                            throw new Exception(fullError);
                        }

                        if (!json.has("qualities")) {
                            throw new Exception("No qualities field in CDN response");
                        }

                        JSONObject qualities = json.getJSONObject("qualities");
                        if (!qualities.has("auto")) {
                            throw new Exception("No auto quality in CDN response");
                        }

                        JSONArray autoArray = qualities.getJSONArray("auto");
                        if (autoArray.length() == 0) {
                            throw new Exception("Empty auto quality array in CDN response");
                        }

                        JSONObject auto = autoArray.getJSONObject(0);
                        if (!auto.has("url")) {
                            throw new Exception("No URL in CDN auto quality object");
                        }

                        String streamUrl = auto.getString("url");
                        if (streamUrl == null || streamUrl.isEmpty()) {
                            throw new Exception("CDN stream URL is empty");
                        }

                        if (!streamUrl.startsWith("http")) {
                            throw new Exception("Invalid CDN stream URL format: " + streamUrl);
                        }

                        String mode = json.optString("mode", "");
                        if (!"live".equals(mode)) {
                            Log.w(TAG, "Warning: CDN stream mode is not 'live', got: " + mode);
                        }

                        Log.d(TAG, "Successfully extracted CDN stream URL");
                        callback.onSuccess(streamUrl);
                        return;

                    } else if (responseCode == 403) {
                        lastError = "CDN stream bloqueado en tu región (403)";
                        Log.e(TAG, lastError);
                        break;
                    } else if (responseCode == 404) {
                        lastError = "CDN stream no encontrado (404)";
                        Log.e(TAG, lastError);
                        break;
                    } else {
                        lastError = "Error HTTP en CDN: " + responseCode;
                        Log.e(TAG, lastError);
                    }
                    
                    conn.disconnect();

                } catch (java.net.SocketTimeoutException e) {
                    lastError = "Tiempo de espera agotado conectando a CDN (intento " + (retries + 1) + ")";
                    Log.e(TAG, lastError, e);
                } catch (java.net.UnknownHostException e) {
                    lastError = "Sin conexión a internet para CDN";
                    Log.e(TAG, lastError, e);
                    break;
                } catch (org.json.JSONException e) {
                    lastError = "Respuesta JSON inválida de CDN: " + e.getMessage();
                    Log.e(TAG, lastError, e);
                    break;
                } catch (Exception e) {
                    lastError = e.getMessage();
                    Log.e(TAG, "Error extrayendo stream de CDN: " + lastError, e);
                    if (lastError != null && (lastError.contains("API error") || lastError.contains("private") || lastError.contains("DM020"))) {
                        Log.e(TAG, "CDN stream privado o restringido, deteniendo reintentos");
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

            callback.onError(lastError != null ? lastError : "Error desconocido en CDN después de " + MAX_RETRIES + " intentos");
        }).start();
    }

    private static String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String generateViewId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder viewId = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            viewId.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return viewId.toString();
    }
}