package io.tubetvlol.tubetv.utils;

import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateDownloader {

    public interface DownloadCallback {
        void onProgress(long downloaded, long total, double speedMBps);
        void onComplete(String filePath);
        void onError(String error);
    }

    private volatile boolean cancelled;

    public void download(String urlString, File outputFile, DownloadCallback callback) {
        cancelled = false;
        Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    mainHandler.post(() -> callback.onError("Server returned " + responseCode));
                    return;
                }

                long totalBytes = conn.getContentLengthLong();
                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(outputFile);

                byte[] buffer = new byte[8192];
                long downloaded = 0;
                long lastTime = System.nanoTime();
                long lastBytes = 0;
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    if (cancelled) {
                        output.close();
                        input.close();
                        outputFile.delete();
                        mainHandler.post(() -> callback.onError("Download cancelled"));
                        return;
                    }

                    output.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;

                    long now = System.nanoTime();
                    long elapsed = now - lastTime;
                    if (elapsed > 200_000_000) {
                        long bytesSinceLast = downloaded - lastBytes;
                        double speedMBps = (bytesSinceLast / 1024.0 / 1024.0) / (elapsed / 1_000_000_000.0);
                        final long d = downloaded;
                        mainHandler.post(() -> callback.onProgress(d, totalBytes, speedMBps));
                        lastTime = now;
                        lastBytes = downloaded;
                    }
                }

                output.close();
                input.close();

                mainHandler.post(() -> callback.onComplete(outputFile.getAbsolutePath()));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    public void cancel() {
        cancelled = true;
    }
}
