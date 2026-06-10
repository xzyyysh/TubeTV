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

    // fetch page content as string, returns null on failure
    private static String fetchPageContent(String urlString, String refererUrl) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            if (refererUrl != null) {
                connection.setRequestProperty("Referer", refererUrl);
            }
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error: " + responseCode + " for " + urlString);
                return null;
            }
            
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                html.append(line);
            }
            
            return html.toString();
            
        } catch (Exception e) {
            Log.e(TAG, "Error fetching " + urlString + ": " + e.getMessage());
            return null;
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

    private static String getPlayerIframeUrl(String mainPageUrl) throws Exception {
        String html = fetchPageContent(mainPageUrl, null);
        if (html == null) {
            throw new Exception("Failed to fetch main page");
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
    }

    private static String extractStreamFromPlayer(String playerUrl, String refererUrl) throws Exception {
        String html = fetchPageContent(playerUrl, refererUrl);
        if (html == null) {
            throw new Exception("Failed to fetch player page");
        }
        
        // follow metarefresh redirect (index.php -> index_mob.php)
        Pattern refreshPattern = Pattern.compile(
            "<meta\\s+http-equiv\\s*=\\s*[\"']refresh[\"']\\s+content\\s*=\\s*[\"']\\d+;\\s*url\\s*=\\s*([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        );
        Matcher refreshMatcher = refreshPattern.matcher(html);
        if (refreshMatcher.find()) {
            String redirectUrl = refreshMatcher.group(1);
            if (!redirectUrl.startsWith("http")) {
                int lastSlash = playerUrl.lastIndexOf('/');
                String baseUrl = playerUrl.substring(0, lastSlash + 1);
                redirectUrl = baseUrl + redirectUrl;
            }
            Log.d(TAG, "following meta-refresh to: " + redirectUrl);
            html = fetchPageContent(redirectUrl, refererUrl);
            if (html == null) {
                throw new Exception("Failed to fetch redirected player page");
            }
        }
        
        // try specific hls pattern first (radiant media player settings)
        Pattern hlsPattern = Pattern.compile("hls\\s*:\\s*\"([^\"]+)\"");
        Matcher hlsMatcher = hlsPattern.matcher(html);
        if (hlsMatcher.find()) {
            String url = hlsMatcher.group(1).replace("http://", "https://");
            return url;
        }
        
        // fallback: generic m3u8 url pattern
        Pattern m3u8Pattern = Pattern.compile("https?://[^\"']+\\.m3u8[^\"']*");
        Matcher m3u8Matcher = m3u8Pattern.matcher(html);
        if (m3u8Matcher.find()) {
            String url = m3u8Matcher.group(0).replace("http://", "https://");
            return url;
        }
        
        throw new Exception("No m3u8 URL found in player HTML");
    }
}