package io.tubetvlol.tubetv.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Antena7Extractor {
    private static final String TAG = "Antena7Extractor";
    private static final String ANTENA7_URL = "https://www.antena7.com.do/envivo-canal-7/";
    private static final int INITIAL_WAIT = 3000;
    private static final int PLAY_WAIT = 2000;
    private static final int EXTENDED_WAIT = 8000;
    private static final int MAX_RETRIES = 2;

    public interface ExtractionCallback {
        void onSuccess(String streamUrl);
        void onError(String error);
    }

    public static void extractStreamUrl(WebView webView, ExtractionCallback callback) {
        extractWithRetry(webView, callback, 0);
    }

    private static void extractWithRetry(WebView webView, ExtractionCallback callback, int retryCount) {
        if (retryCount >= MAX_RETRIES) {
            callback.onError("Failed to extract stream URL after " + MAX_RETRIES + " attempts");
            return;
        }

        Log.d(TAG, "Attempt " + (retryCount + 1) + " - Loading page: " + ANTENA7_URL);

        Handler handler = new Handler(Looper.getMainLooper());

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.getSettings().setUserAgentString("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page loaded, waiting for player initialization...");

                handler.postDelayed(() -> {
                    checkVideoElement(view, handler, callback, retryCount);
                }, INITIAL_WAIT);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e(TAG, "WebView error: " + description);
                callback.onError("WebView error: " + description);
            }
        });

        webView.loadUrl(ANTENA7_URL);
    }

    private static void checkVideoElement(WebView webView, Handler handler, ExtractionCallback callback, int retryCount) {
        Log.d(TAG, "Method 1: Checking for video element in main DOM...");
        String jsCode = "(function() {" +
                "  const video = document.querySelector('video');" +
                "  if (video && video.src && video.src.includes('.m3u8')) {" +
                "    return video.src;" +
                "  }" +
                "  if (video && video.currentSrc && video.currentSrc.includes('.m3u8')) {" +
                "    return video.currentSrc;" +
                "  }" +
                "  return null;" +
                "})();";

        webView.evaluateJavascript(jsCode, result -> {
            if (result != null && !result.equals("null") && result.contains(".m3u8")) {
                String cleanUrl = cleanStreamUrl(result.replace("\"", ""));
                Log.d(TAG, "Found stream URL from video element: " + cleanUrl);
                callback.onSuccess(cleanUrl);
            } else {
                Log.d(TAG, "Method 2: Clicking play and waiting longer...");
                handler.postDelayed(() -> {
                    tryClickPlayButton(webView, handler, callback, retryCount);
                }, 1000);
            }
        });
    }

    private static void tryClickPlayButton(WebView webView, Handler handler, ExtractionCallback callback, int retryCount) {
        String clickJs = "(function() {" +
                "  const playButton = document.querySelector('button[aria-label*=\"play\"], media-play-button');" +
                "  if (playButton) {" +
                "    playButton.click();" +
                "    return 'clicked';" +
                "  }" +
                "  return 'not_found';" +
                "})();";

        webView.evaluateJavascript(clickJs, result -> {
            if (result != null && result.contains("clicked")) {
                Log.d(TAG, "Clicked play button");
            } else {
                Log.d(TAG, "Could not find play button, continuing...");
            }

            handler.postDelayed(() -> {
                checkShadowDOM(webView, handler, callback, retryCount);
            }, PLAY_WAIT);
        });
    }

    private static void checkShadowDOM(WebView webView, Handler handler, ExtractionCallback callback, int retryCount) {
        Log.d(TAG, "Method 3: Checking video element in shadow DOM...");
        
        handler.postDelayed(() -> {
            String jsCode = "(function() {" +
                    "  const mediaChrome = document.querySelector('adjacent-media-chrome-mux');" +
                    "  if (mediaChrome && mediaChrome.shadowRoot) {" +
                    "    const mediaController = mediaChrome.shadowRoot.querySelector('media-controller');" +
                    "    if (mediaController && mediaController.shadowRoot) {" +
                    "      const video = mediaController.shadowRoot.querySelector('video');" +
                    "      if (video && video.src && !video.src.startsWith('blob:')) {" +
                    "        return video.src;" +
                    "      }" +
                    "      if (video && video.currentSrc && !video.currentSrc.startsWith('blob:')) {" +
                    "        return video.currentSrc;" +
                    "      }" +
                    "    }" +
                    "    const video = mediaChrome.shadowRoot.querySelector('video');" +
                    "    if (video && video.src && !video.src.startsWith('blob:')) {" +
                    "      return video.src;" +
                    "    }" +
                    "    if (video && video.currentSrc && !video.currentSrc.startsWith('blob:')) {" +
                    "      return video.currentSrc;" +
                    "    }" +
                    "  }" +
                    "  const allElements = document.querySelectorAll('*');" +
                    "  for (let el of allElements) {" +
                    "    if (el.shadowRoot) {" +
                    "      const vid = el.shadowRoot.querySelector('video');" +
                    "      if (vid && vid.src && !vid.src.startsWith('blob:')) {" +
                    "        return vid.src;" +
                    "      }" +
                    "      if (vid && vid.currentSrc && !vid.currentSrc.startsWith('blob:')) {" +
                    "        return vid.currentSrc;" +
                    "      }" +
                    "    }" +
                    "  }" +
                    "  return null;" +
                    "})();";

            webView.evaluateJavascript(jsCode, result -> {
                if (result != null && !result.equals("null") && result.contains(".m3u8")) {
                    String cleanUrl = cleanStreamUrl(result.replace("\"", ""));
                    Log.d(TAG, "Found stream URL from shadow DOM: " + cleanUrl);
                    callback.onSuccess(cleanUrl);
                } else {
                    Log.d(TAG, "Method 4: Checking window.Adjacent object...");
                    checkAdjacentObject(webView, callback, retryCount);
                }
            });
        }, EXTENDED_WAIT);
    }

    private static void checkAdjacentObject(WebView webView, ExtractionCallback callback, int retryCount) {
        String jsCode = "(function() {" +
                "  const adjacent = window.Adjacent;" +
                "  if (!adjacent) return null;" +
                "  const findM3u8 = function(obj, path) {" +
                "    if (typeof obj === 'string' && obj.includes('.m3u8') && obj.includes('cloudfront')) {" +
                "      return obj;" +
                "    }" +
                "    if (typeof obj === 'object' && obj !== null) {" +
                "      for (const key in obj) {" +
                "        const result = findM3u8(obj[key], (path || '') + '.' + key);" +
                "        if (result) return result;" +
                "      }" +
                "    }" +
                "    return null;" +
                "  };" +
                "  if (adjacent.adjacentResponse) {" +
                "    const url = findM3u8(adjacent.adjacentResponse);" +
                "    if (url) return url;" +
                "  }" +
                "  if (adjacent.publisherResponse) {" +
                "    const url = findM3u8(adjacent.publisherResponse);" +
                "    if (url) return url;" +
                "  }" +
                "  if (adjacent.publisherObject) {" +
                "    const url = findM3u8(adjacent.publisherObject);" +
                "    if (url) return url;" +
                "  }" +
                "  return null;" +
                "})();";

        webView.evaluateJavascript(jsCode, result -> {
            if (result != null && !result.equals("null") && result.contains(".m3u8")) {
                String cleanUrl = cleanStreamUrl(result.replace("\"", ""));
                Log.d(TAG, "Found stream URL from Adjacent object: " + cleanUrl);
                callback.onSuccess(cleanUrl);
            } else {
                Log.d(TAG, "Method 5: Checking page source with regex...");
                extractFromPageSource(webView, callback, retryCount);
            }
        });
    }

    private static void extractFromPageSource(WebView webView, ExtractionCallback callback, int retryCount) {
        String jsCode = "document.documentElement.outerHTML;";

        webView.evaluateJavascript(jsCode, html -> {
            if (html != null) {
                String cleanHtml = html.replace("\\u003C", "<")
                        .replace("\\u003E", ">")
                        .replace("\\\"", "\"")
                        .replace("\\/", "/")
                        .replace("\\\\", "");

                Log.d(TAG, "HTML length: " + cleanHtml.length());

                Pattern pattern = Pattern.compile("https://d2qsan2ut81n2k\\.cloudfront\\.net/live/[a-f0-9\\-]+/[^\\s\"'<>]+\\.m3u8");
                Matcher matcher = pattern.matcher(cleanHtml);

                if (matcher.find()) {
                    String streamUrl = cleanStreamUrl(matcher.group());
                    Log.d(TAG, "Found stream URL in page source: " + streamUrl);
                    callback.onSuccess(streamUrl);
                } else {
                    Pattern simplePattern = Pattern.compile("d2qsan2ut81n2k\\.cloudfront\\.net[^\\s\"'<>]+m3u8");
                    Matcher simpleMatcher = simplePattern.matcher(cleanHtml);
                    
                    if (simpleMatcher.find()) {
                        String partialUrl = simpleMatcher.group();
                        String streamUrl = cleanStreamUrl("https://" + partialUrl);
                        Log.d(TAG, "Found stream URL with simple pattern: " + streamUrl);
                        callback.onSuccess(streamUrl);
                    } else {
                        Log.e(TAG, "No stream URL found in page source");
                        Log.d(TAG, "HTML sample: " + cleanHtml.substring(0, Math.min(500, cleanHtml.length())));
                        extractWithRetry(webView, callback, retryCount + 1);
                    }
                }
            } else {
                Log.e(TAG, "Failed to get page source");
                extractWithRetry(webView, callback, retryCount + 1);
            }
        });
    }

    private static String cleanStreamUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        url = url.replace("\\/", "/");
        
        if (url.contains("#")) {
            url = url.substring(0, url.indexOf("#"));
        }
        
        return url;
    }
}