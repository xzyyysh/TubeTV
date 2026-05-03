package io.tubetvlol.tubetv.utils;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelemicroExtractor {
    private static final String TAG = "TelemicroExtractor";
    private static final int TIMEOUT_MS = 30000;
    private static final int MAX_RETRIES = 3;
    private static final String MAIN_PAGE_URL = "https://telemicro.com.do/telemicro-en-vivo/";
    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    public static final String REFERER = "https://telemicro.com.do/";
    public static final String ORIGIN = "https://telemicro.com.do";

    public static String extractStreamUrl(String mainPageUrl) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Log.d(TAG, "Attempt " + attempt + " to extract stream from: " + mainPageUrl);
                
                String playerUrl = getPlayerIframeUrl(mainPageUrl);
                if (playerUrl == null) {
                    throw new Exception("Failed to find player iframe URL");
                }
                
                Log.d(TAG, "Found player URL: " + playerUrl);
                
                String streamUrl = extractStreamFromPlayer(playerUrl, mainPageUrl);
                if (streamUrl == null) {
                    throw new Exception("Failed to extract stream URL from player");
                }
                
                Log.d(TAG, "Successfully extracted stream URL: " + streamUrl);
                return streamUrl;
                
            } catch (Exception e) {
                Log.e(TAG, "Attempt " + attempt + " failed: " + e.getMessage());
                
                if (attempt < MAX_RETRIES) {
                    try {
                        int delay = attempt * 2000;
                        Log.d(TAG, "Retrying in " + delay + "ms...");
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        Log.e(TAG, "All extraction attempts failed");
        return null;
    }

    private static String getPlayerIframeUrl(String mainPageUrl) throws Exception {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        
        try {
            URL url = new URL(mainPageUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP error: " + responseCode);
            }
            
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                html.append(line);
            }
            
            Pattern pattern1 = Pattern.compile("<iframe[^>]+src=[\"']([^\"']*players/[^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher matcher1 = pattern1.matcher(html);
            
            if (matcher1.find()) {
                String iframeUrl = matcher1.group(1);
                if (!iframeUrl.startsWith("http")) {
                    iframeUrl = "https://telemicro.com.do" + iframeUrl;
                }
                return iframeUrl;
            }
            
            Pattern pattern2 = Pattern.compile("https://telemicro\\.com\\.do/players/[^\"'\\s]+");
            Matcher matcher2 = pattern2.matcher(html);
            
            if (matcher2.find()) {
                return matcher2.group(0);
            }
            
            throw new Exception("No player iframe found in HTML");
            
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing reader: " + e.getMessage());
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String extractStreamFromPlayer(String playerUrl, String refererUrl) throws Exception {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        
        try {
            URL url = new URL(playerUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Referer", refererUrl);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("HTTP error: " + responseCode);
            }
            
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                html.append(line);
            }
            
            Pattern pattern = Pattern.compile("https://[^\"']+\\.m3u8[^\"']*");
            Matcher matcher = pattern.matcher(html);
            
            if (matcher.find()) {
                return matcher.group(0);
            }
            
            throw new Exception("No m3u8 URL found in player HTML");
            
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing reader: " + e.getMessage());
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}