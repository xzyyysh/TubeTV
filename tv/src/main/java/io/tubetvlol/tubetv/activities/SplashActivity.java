package io.tubetvlol.tubetv.activities;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.annotation.SuppressLint;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import io.tubetvlol.tubetv.R;
import io.tubetvlol.tubetv.utils.PreferencesManager;
import io.tubetvlol.tubetv.utils.UpdateChecker;
import io.tubetvlol.tubetv.utils.UpdateDownloader;
import io.tubetvlol.tubetv.utils.UpdateInstaller;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends Activity {

    private TextView loadingText;
    private TextView errorText;
    private Handler mainHandler;
    private ExecutorService executor;
    private boolean hasInternet = false;

    private LinearLayout progressContainer;
    private LinearProgressIndicator downloadProgress;
    private TextView progressSize;
    private TextView progressSpeed;
    private Button cancelButton;
    private PreferencesManager prefsManager;
    private UpdateDownloader downloader;
    private File apkFile;
    private String currentDownloadUrl;

    private final String[] checkUrls = {
        "https://telemicro.com.do",
        "https://geo.dailymotion.com",
        "https://www.antena7.com.do",
        "https://rtvd.gob.do",
        "https://rtvdclic.com",
        "https://canaldelsol.com",
        "https://fox.hostlagarto.com"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        hideSystemUI();

        ImageView logo = findViewById(R.id.logo);
        loadingText = findViewById(R.id.loading_text);
        errorText = findViewById(R.id.error_text);
        progressContainer = findViewById(R.id.progress_container);
        downloadProgress = findViewById(R.id.download_progress);
        progressSize = findViewById(R.id.progress_size);
        progressSpeed = findViewById(R.id.progress_speed);
        cancelButton = findViewById(R.id.cancel_button);

        prefsManager = new PreferencesManager(this);
        mainHandler = new Handler(Looper.getMainLooper());
        executor = Executors.newSingleThreadExecutor();

        animateLogo(logo);
        startConnectivityChecks();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void animateLogo(ImageView logo) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f);

        AnimatorSet popIn = new AnimatorSet();
        popIn.playTogether(scaleX, scaleY, alpha);
        popIn.setDuration(800);
        popIn.setInterpolator(new OvershootInterpolator(1.5f));
        popIn.setStartDelay(300);
        popIn.start();
    }

    private void startConnectivityChecks() {
        mainHandler.postDelayed(() -> {
            loadingText.setAlpha(1f);
            updateLoadingText("Checking connection...");
            executor.execute(() -> {
                for (String url : checkUrls) {
                    if (checkConnectivity(url)) {
                        hasInternet = true;
                        break;
                    }
                }
                mainHandler.post(() -> {
                    if (hasInternet) {
                        checkForUpdate();
                    } else {
                        showNoInternetError();
                    }
                });
            });
        }, 1000);
    }

    private boolean checkConnectivity(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.connect();
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkForUpdate() {
        updateLoadingText("Checking for updates...");

        if (!prefsManager.shouldCheckForUpdate()) {
            mainHandler.postDelayed(this::goToMain, 3000);
            return;
        }

        mainHandler.postDelayed(() -> {
            prefsManager.setLastUpdateCheckTime(System.currentTimeMillis());
            UpdateChecker.checkForUpdate(this, result -> {
                mainHandler.post(() -> {
                    if (result.hasUpdate) {
                        currentDownloadUrl = result.downloadUrl;
                        showUpdateDialog(result);
                    } else {
                        goToMain();
                    }
                });
            });
        }, 3000);
    }

    private void showUpdateDialog(UpdateChecker.UpdateResult result) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
            builder.setTitle("Actualizacion disponible")
                .setMessage(result.latestVersion + "\n\n" + result.changelog)
                .setCancelable(false)
                .setPositiveButton("Actualizar", (dialog, which) -> startDownload())
                .setNegativeButton("Actualizar despues", (dialog, which) -> goToMain());
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    private void startDownload() {
        updateLoadingText("Downloading update...");
        progressContainer.setVisibility(View.VISIBLE);
        loadingText.setVisibility(View.VISIBLE);
        errorText.setVisibility(View.GONE);
        downloadProgress.setProgress(0);
        progressSize.setText(R.string.progress_size_default);
        progressSpeed.setText(R.string.progress_speed_default);

        File updatesDir = new File(getCacheDir(), "updates");
        updatesDir.mkdirs();
        apkFile = new File(updatesDir, "update.apk");

        downloader = new UpdateDownloader();
        cancelButton.setOnClickListener(v -> {
            downloader.cancel();
            goToMain();
        });

        downloader.download(currentDownloadUrl, apkFile, new UpdateDownloader.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total, double speedMBps) {
                int progress = total > 0 ? (int) (downloaded * 100 / total) : 0;
                downloadProgress.setProgress(progress);
                progressSize.setText(String.format(Locale.ROOT, "%.1f MB / %.1f MB",
                    downloaded / 1024.0 / 1024.0, total / 1024.0 / 1024.0));
                progressSpeed.setText(String.format(Locale.ROOT, "Speed: %.1f MB/s", speedMBps));
            }

            @Override
            public void onComplete(String filePath) {
                progressContainer.setVisibility(View.GONE);
                updateLoadingText("Installing...");
                mainHandler.postDelayed(() -> {
                    UpdateInstaller.install(SplashActivity.this, apkFile);
                    mainHandler.postDelayed(() -> UpdateInstaller.cleanup(SplashActivity.this, apkFile), 5000);
                    finish();
                }, 500);
            }

            @Override
            public void onError(String error) {
                progressContainer.setVisibility(View.GONE);
                updateLoadingText("Update failed");
                mainHandler.postDelayed(() -> goToMain(), 2000);
            }
        });
    }

    private void updateLoadingText(String text) {
        mainHandler.post(() -> {
            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(loadingText, "alpha", 1f, 0f);
            fadeOut.setDuration(200);
            fadeOut.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    loadingText.setText(text);
                    ObjectAnimator fadeIn = ObjectAnimator.ofFloat(loadingText, "alpha", 0f, 1f);
                    fadeIn.setDuration(200);
                    fadeIn.start();
                }
                @Override public void onAnimationStart(Animator animation) {}
                @Override public void onAnimationCancel(Animator animation) {}
                @Override public void onAnimationRepeat(Animator animation) {}
            });
            fadeOut.start();
        });
    }

    private void goToMain() {
        updateLoadingText("Opening...");
        mainHandler.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 1200);
    }

    private void showNoInternetError() {
        mainHandler.post(() -> {
            loadingText.setText(R.string.connection_failed);
            errorText.setText(R.string.connection_error);
            errorText.setVisibility(View.VISIBLE);
            ObjectAnimator fadeIn = ObjectAnimator.ofFloat(errorText, "alpha", 0f, 1f);
            fadeIn.setDuration(400);
            fadeIn.start();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
        if (downloader != null) {
            downloader.cancel();
        }
    }
}