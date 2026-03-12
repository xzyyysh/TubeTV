package io.tubetvlol.tubetv.activities;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import java.util.HashMap;
import java.util.Map;
import io.tubetvlol.tubetv.R;
import io.tubetvlol.tubetv.utils.DailymotionExtractor;
import io.tubetvlol.tubetv.utils.TelemicroExtractor;

public class PlayerActivity extends Activity {

    private ExoPlayer player;
    private PlayerView playerView;
    private boolean isTelemicroStream = false;
    private FrameLayout controlsContainer;
    private ImageButton playPauseButton;
    private Handler hideControlsHandler;
    private Runnable hideControlsRunnable;
    private static final int CONTROLS_HIDE_DELAY = 3000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        hideSystemUI();

        playerView = findViewById(R.id.player_view);
        controlsContainer = findViewById(R.id.controls_container);
        playPauseButton = findViewById(R.id.play_pause_button);

        hideControlsHandler = new Handler(Looper.getMainLooper());
        hideControlsRunnable = this::hideControls;

        setupControlListeners();

        String channelName = getIntent().getStringExtra("channel_name");
        String channelNumber = getIntent().getStringExtra("channel_number");
        String streamUrl = getIntent().getStringExtra("stream_url");

        if (streamUrl.startsWith("dailymotion:")) {
            String videoId = streamUrl.substring(12);
            loadDailymotionStream(videoId);
        } else if (streamUrl.startsWith("telemicro:")) {
            isTelemicroStream = true;
            loadTelemicroStream();
        } else {
            initializePlayer(streamUrl);
        }
    }

    private void setupControlListeners() {
        View rootContainer = findViewById(R.id.root_container);
        rootContainer.setOnClickListener(v -> toggleControls());

        playPauseButton.setOnClickListener(v -> {
            if (player != null) {
                if (player.isPlaying()) {
                    player.pause();
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                } else {
                    if (player.isCurrentMediaItemLive()) {
                        player.seekToDefaultPosition();
                    }
                    player.play();
                    playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                }
            }
            resetHideControlsTimer();
        });
    }

    private void toggleControls() {
        if (controlsContainer.getVisibility() == View.VISIBLE) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        controlsContainer.setVisibility(View.VISIBLE);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(300);
        controlsContainer.startAnimation(fadeIn);
        resetHideControlsTimer();
    }

    private void hideControls() {
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(300);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                controlsContainer.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        controlsContainer.startAnimation(fadeOut);
        hideControlsHandler.removeCallbacks(hideControlsRunnable);
    }

    private void resetHideControlsTimer() {
        hideControlsHandler.removeCallbacks(hideControlsRunnable);
        hideControlsHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY);
    }

    private void loadDailymotionStream(String videoId) {
        DailymotionExtractor.getStreamUrl(videoId, new DailymotionExtractor.StreamCallback() {
            @Override
            public void onSuccess(String streamUrl) {
                runOnUiThread(() -> {
                    initializePlayer(streamUrl);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    String userMessage = getUserFriendlyError(error);
                    Toast.makeText(PlayerActivity.this, userMessage, Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        });
    }

    private void loadTelemicroStream() {
        String telemicroUrl = getIntent().getStringExtra("stream_url").substring(10);
        
        new Thread(() -> {
            String streamUrl = TelemicroExtractor.extractStreamUrl(telemicroUrl);
            
            runOnUiThread(() -> {
                if (streamUrl != null) {
                    initializePlayer(streamUrl);
                } else {
                    Toast.makeText(PlayerActivity.this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }).start();
    }

    private String getUserFriendlyError(String error) {
        if (error == null) {
            return "Canal no disponible por el momento.";
        }
        
        if (error.contains("private") || error.contains("DM020")) {
            return "Canal no disponible por el momento.";
        }
        
        if (error.contains("403") || error.contains("blocked")) {
            return "Canal no disponible por el momento.";
        }
        
        if (error.contains("404") || error.contains("not found")) {
            return "Canal no disponible por el momento.";
        }
        
        if (error.contains("timeout") || error.contains("Connection")) {
            return "Error de conexión. Intenta de nuevo.";
        }
        
        if (error.contains("No internet")) {
            return "Sin conexión a internet.";
        }
        
        return "Canal no disponible por el momento.";
    }

    private void hideSystemUI() {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void initializePlayer(String streamUrl) {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                } else {
                    playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                }
            }
        });

        if (isTelemicroStream) {
            Map<String, String> headers = new HashMap<>();
            headers.put("Referer", "https://telemicro.com.do/");
            headers.put("Origin", "https://telemicro.com.do");
            headers.put("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36");

            DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
            dataSourceFactory.setDefaultRequestProperties(headers);

            MediaItem mediaItem = MediaItem.fromUri(streamUrl);
            MediaSource mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem);

            player.setMediaSource(mediaSource);
            player.prepare();
            player.play();
        } else {
            MediaItem mediaItem = MediaItem.fromUri(streamUrl);
            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideControlsHandler.removeCallbacks(hideControlsRunnable);
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (player != null) {
            if (player.isCurrentMediaItemLive()) {
                player.seekToDefaultPosition();
            }
            player.play();
        }
    }
}