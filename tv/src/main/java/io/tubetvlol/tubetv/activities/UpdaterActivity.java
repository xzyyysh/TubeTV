package io.tubetvlol.tubetv.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;
import io.tubetvlol.tubetv.R;
import io.tubetvlol.tubetv.utils.UpdateChecker;
import io.tubetvlol.tubetv.utils.UpdateDownloader;
import io.tubetvlol.tubetv.utils.UpdateInstaller;

public class UpdaterActivity extends Activity {

    private TextView dotsText;
    private TextView checkingText;
    private LinearLayout progressContainer;
    private ProgressBar downloadProgress;
    private TextView progressSize;
    private TextView progressSpeed;
    private Button cancelButton;
    private Handler handler;
    private Runnable dotsRunnable;
    private int dotCount;
    private UpdateDownloader downloader;
    private File apkFile;
    private String currentDownloadUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_updater);
        hideSystemUI();

        checkingText = findViewById(R.id.checking_text);
        dotsText = findViewById(R.id.dots_text);
        progressContainer = findViewById(R.id.progress_container);
        downloadProgress = findViewById(R.id.download_progress);
        progressSize = findViewById(R.id.progress_size);
        progressSpeed = findViewById(R.id.progress_speed);
        cancelButton = findViewById(R.id.cancel_button);

        handler = new Handler(Looper.getMainLooper());

        startDotsAnimation();
        checkForUpdate();
    }

    private void hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void startDotsAnimation() {
        dotsRunnable = new Runnable() {
            @Override
            public void run() {
                dotCount = (dotCount % 4) + 1;
                StringBuilder dots = new StringBuilder();
                for (int i = 0; i < dotCount; i++) {
                    dots.append(".");
                }
                dotsText.setText(dots.toString());
                handler.postDelayed(this, 500);
            }
        };
        handler.post(dotsRunnable);
    }

    private void stopDotsAnimation() {
        if (handler != null && dotsRunnable != null) {
            handler.removeCallbacks(dotsRunnable);
        }
    }

    private void checkForUpdate() {
        UpdateChecker.checkForUpdate(this, result -> {
            handler.postDelayed(() -> {
                stopDotsAnimation();
                if (result.hasUpdate) {
                    currentDownloadUrl = result.downloadUrl;
                    showUpdateDialog(result);
                } else {
                    if (result.error != null) {
                        Log.d("UpdateChecker", "Error checking update: " + result.error);
                    } else {
                        Log.d("UpdateChecker", "No new releases found or same version.");
                    }
                    goToSplash();
                }
            }, 1500);
        });
    }

    private void showUpdateDialog(UpdateChecker.UpdateResult result) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK);
            builder.setTitle("Actualizacion disponible")
                .setMessage(result.latestVersion + "\n\n" + result.changelog)
                .setCancelable(false)
                .setPositiveButton("Actualizar", (dialog, which) -> {
                    startDownload();
                })
                .setNegativeButton("Actualizar despues", (dialog, which) -> goToSplash());
            AlertDialog dialog = builder.create();
            dialog.show();
        });
    }

    private void startDownload() {
        stopDotsAnimation();
        checkingText.setText(R.string.downloading_update);
        progressContainer.setVisibility(View.VISIBLE);
        dotsText.setVisibility(View.GONE);
        downloadProgress.setProgress(0);
        progressSize.setText(R.string.progress_size_default);
        progressSpeed.setText(R.string.progress_speed_default);

        File updatesDir = new File(getCacheDir(), "updates");
        updatesDir.mkdirs();
        apkFile = new File(updatesDir, "update.apk");

        downloader = new UpdateDownloader();
        cancelButton.setOnClickListener(v -> {
            downloader.cancel();
            goToSplash();
        });

        downloader.download(currentDownloadUrl, apkFile, new UpdateDownloader.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total, double speedMBps) {
                int progress = total > 0 ? (int) (downloaded * 100 / total) : 0;
                downloadProgress.setProgress(progress);
                progressSize.setText(String.format(Locale.ROOT, "%.1f MB / %.1f MB",
                    downloaded / 1024.0 / 1024.0, total / 1024.0 / 1024.0));
                progressSpeed.setText(String.format(Locale.ROOT, "Velocidad: %.1f MB/s", speedMBps));
            }

            @Override
            public void onComplete(String filePath) {
                stopDotsAnimation();
                checkingText.setText(R.string.installing);
                progressContainer.setVisibility(View.GONE);
                dotsText.setVisibility(View.GONE);
                handler.postDelayed(() -> {
                    UpdateInstaller.install(UpdaterActivity.this, apkFile);
                    handler.postDelayed(() -> UpdateInstaller.cleanup(UpdaterActivity.this, apkFile), 5000);
                    finish();
                }, 500);
            }

            @Override
            public void onError(String error) {
                stopDotsAnimation();
                checkingText.setText(R.string.download_error);
                progressContainer.setVisibility(View.GONE);
                handler.postDelayed(() -> goToSplash(), 2000);
            }
        });
    }

    private void goToSplash() {
        startActivity(new Intent(this, SplashActivity.class));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopDotsAnimation();
        if (downloader != null) {
            downloader.cancel();
        }
    }
}
