package io.tubetvlol.tubetv.activities;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import io.tubetvlol.tubetv.R;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SplashActivity extends Activity {

    private TextView loadingText;
    private TextView errorText;
    private Handler mainHandler;
    private ExecutorService executor;
    private boolean hasInternet = false;

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
                        showOpeningMessage();
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

    private void showOpeningMessage() {
        updateLoadingText("Opening...");
        mainHandler.postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 1200);
    }

    private void showNoInternetError() {
        mainHandler.post(() -> {
            loadingText.setText("Connection Failed");
            errorText.setText("Error: No internet connection detected\n\nPlease check your network and try again later.");
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
    }
}