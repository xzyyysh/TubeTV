package io.tubetvlol.tubetv.activities;

import androidx.media3.common.util.UnstableApi;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import java.util.HashMap;
import java.util.Map;
import io.tubetvlol.tubetv.R;
import io.tubetvlol.tubetv.utils.AntenaLatinaExtractor;
import io.tubetvlol.tubetv.utils.TeleantillasExtractor;
import io.tubetvlol.tubetv.utils.TelemicroExtractor;
import io.tubetvlol.tubetv.utils.TelesistemaExtractor;
import io.tubetvlol.tubetv.utils.RtvdExtractor;
import io.tubetvlol.tubetv.utils.CdnExtractor;
import io.tubetvlol.tubetv.utils.PreferencesManager;

@UnstableApi
public class PlayerActivity extends Activity {

    private static final String TAG = "PlayerActivity";
    private static final int CONTROLS_FADE_DURATION = 300;
    private static final int LOADING_DOT_INTERVAL = 500;
    private static final int MAX_LOADING_DOTS = 3;
    private static final String TELEANTILLAS_PREFIX = "teleantillas:";
    private static final String TELEMICRO_PREFIX = "telemicro:";
    private static final String ANTENA7_PREFIX = "antena7:";
    private static final String TELESISTEMA_PREFIX = "telesistema:";
    private static final String RTVD_PREFIX = "rtvd:";
    private static final String CDN_PREFIX = "cdn:";
    
    private ExoPlayer player;
    private PlayerView playerView;
    private boolean isTelemicroStream = false;
    private FrameLayout controlsContainer;
    private ImageButton playPauseButton;
    private Handler hideControlsHandler;
    private Runnable hideControlsRunnable;
    private static final int CONTROLS_HIDE_DELAY = 3000;
    private WebView hiddenWebView;
    private TextView loadingText;
    private Handler loadingHandler;
    private Runnable loadingRunnable;
    private int loadingDotCount = 0;
    private boolean isActivityActive = false;
    private LinearLayout channelHeader;
    private ImageView channelHeaderLogo;
    private TextView channelHeaderName;
    private PreferencesManager prefsManager;
    private FirebaseAnalytics analytics;
    private FirebaseCrashlytics crashlytics;
    private long playbackStartTime;
    private String currentChannelName;
    private String currentChannelNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        isActivityActive = true;
        prefsManager = new PreferencesManager(this);
        analytics = FirebaseAnalytics.getInstance(this);
        crashlytics = FirebaseCrashlytics.getInstance();
        playbackStartTime = System.currentTimeMillis();
        
        if (prefsManager.getKeepScreenOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        
        hideSystemUI();

        playerView = findViewById(R.id.player_view);
        controlsContainer = findViewById(R.id.controls_container);
        playPauseButton = findViewById(R.id.play_pause_button);
        loadingText = findViewById(R.id.loading_text);
        channelHeader = findViewById(R.id.channel_header);
        channelHeaderLogo = findViewById(R.id.channel_header_logo);
        channelHeaderName = findViewById(R.id.channel_header_name);

        loadingHandler = new Handler(Looper.getMainLooper());
        loadingRunnable = new Runnable() {
            @Override
            public void run() {
                if (loadingText.getVisibility() == View.VISIBLE) {
                    loadingDotCount = (loadingDotCount % MAX_LOADING_DOTS) + 1;
                    String dots = "";
                    for (int i = 0; i < loadingDotCount; i++) {
                        dots += ".";
                    }
                    loadingText.setText(dots);
                    loadingHandler.postDelayed(this, LOADING_DOT_INTERVAL);
                }
            }
        };

        hideControlsHandler = new Handler(Looper.getMainLooper());
        hideControlsRunnable = this::hideControls;

        setupControlListeners();

        String channelName = getIntent().getStringExtra("channel_name");
        String channelNumber = getIntent().getStringExtra("channel_number");
        String channelLogo = getIntent().getStringExtra("channel_logo");
        String streamUrl = getIntent().getStringExtra("stream_url");
        
        currentChannelName = channelName;
        currentChannelNumber = channelNumber;

        setupChannelHeader(channelName, channelLogo);
        
        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.SCREEN_NAME, "player_screen");
        params.putString(FirebaseAnalytics.Param.SCREEN_CLASS, getClass().getSimpleName());
        params.putString("channel_name", channelName);
        params.putString("channel_number", channelNumber);
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params);

        if (streamUrl == null) {
            Toast.makeText(this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (streamUrl.startsWith(TELEANTILLAS_PREFIX)) {
            String videoId = streamUrl.substring(TELEANTILLAS_PREFIX.length());
            loadTeleantillasStream(videoId);
        } else if (streamUrl.startsWith(TELEMICRO_PREFIX)) {
            isTelemicroStream = true;
            loadTelemicroStream();
        } else if (streamUrl.startsWith(ANTENA7_PREFIX)) {
            loadAntena7Stream();
        } else if (streamUrl.startsWith(TELESISTEMA_PREFIX)) {
            String videoId = streamUrl.substring(TELESISTEMA_PREFIX.length());
            loadTelesistemaStream(videoId);
        } else if (streamUrl.startsWith(RTVD_PREFIX)) {
            loadRtvdStream();
        } else if (streamUrl.startsWith(CDN_PREFIX)) {
            loadCdnStream();
        } else {
            initializePlayer(streamUrl);
        }
    }

    private void setupControlListeners() {
        View rootContainer = findViewById(R.id.root_container);
        if (rootContainer != null) {
            rootContainer.setOnClickListener(v -> toggleControls());
        }

        if (playPauseButton != null) {
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
    }

    private void setupChannelHeader(String channelName, String channelLogo) {
        if (channelName != null) {
            channelHeaderName.setText(channelName);
        }
        
        if (channelLogo != null && !channelLogo.isEmpty()) {
            int logoResId = getResources().getIdentifier(channelLogo, "drawable", getPackageName());
            if (logoResId != 0) {
                channelHeaderLogo.setImageResource(logoResId);
            }
        }
    }

    private void toggleControls() {
        if (controlsContainer == null) return;
        
        if (controlsContainer.getVisibility() == View.VISIBLE) {
            hideControls();
        } else {
            showControls();
        }
    }

    private void showControls() {
        if (controlsContainer == null || channelHeader == null) return;
        
        controlsContainer.setVisibility(View.VISIBLE);
        channelHeader.setVisibility(View.VISIBLE);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(CONTROLS_FADE_DURATION);
        controlsContainer.startAnimation(fadeIn);
        channelHeader.startAnimation(fadeIn);
        
        if (playPauseButton != null) {
            playPauseButton.requestFocus();
        }
        resetHideControlsTimer();
    }

    private void hideControls() {
        if (controlsContainer == null || channelHeader == null) return;
        
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(CONTROLS_FADE_DURATION);
        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                controlsContainer.setVisibility(View.GONE);
                channelHeader.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        controlsContainer.startAnimation(fadeOut);
        channelHeader.startAnimation(fadeOut);
        
        if (hideControlsHandler != null) {
            hideControlsHandler.removeCallbacks(hideControlsRunnable);
        }
    }

    private void resetHideControlsTimer() {
        if (hideControlsHandler == null || hideControlsRunnable == null || prefsManager == null) return;
        
        hideControlsHandler.removeCallbacks(hideControlsRunnable);
        int timeout = prefsManager.getControlsTimeout();
        if (timeout > 0) {
            hideControlsHandler.postDelayed(hideControlsRunnable, timeout);
        }
    }

    private void loadTeleantillasStream(String videoId) {
        showLoading();
        TeleantillasExtractor.getStreamUrl(videoId, new TeleantillasExtractor.StreamCallback() {
            @Override
            public void onSuccess(String streamUrl) {
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        initializePlayer(streamUrl);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        hideLoading();
                        String userMessage = getUserFriendlyError(error);
                        Toast.makeText(PlayerActivity.this, userMessage, Toast.LENGTH_LONG).show();
                        
                        Bundle params = new Bundle();
                        params.putString("channel_name", currentChannelName);
                        params.putString("error_type", "stream_load_error");
                        params.putString("error_message", error);
                        analytics.logEvent("playback_error", params);
                        crashlytics.recordException(new Exception("Stream load error: " + error));
                        
                        finish();
                    }
                });
            }
        });
    }

    private void loadTelemicroStream() {
        String streamUrl = getIntent().getStringExtra("stream_url");
        if (streamUrl == null || !streamUrl.startsWith(TELEMICRO_PREFIX)) {
            Toast.makeText(this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        String telemicroUrl = streamUrl.substring(TELEMICRO_PREFIX.length());
        
        showLoading();
        new Thread(() -> {
            String extractedUrl = TelemicroExtractor.extractStreamUrl(telemicroUrl);
            
            runOnUiThread(() -> {
                if (isActivityActive) {
                    if (extractedUrl != null) {
                        initializePlayer(extractedUrl);
                    } else {
                        hideLoading();
                        Toast.makeText(PlayerActivity.this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            });
        }).start();
    }

    private void loadAntena7Stream() {
        String streamUrl = getIntent().getStringExtra("stream_url");
        if (streamUrl == null || !streamUrl.startsWith(ANTENA7_PREFIX)) {
            Toast.makeText(this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        String antena7Url = streamUrl.substring(ANTENA7_PREFIX.length());
        Log.d(TAG, "Loading Antena 7 stream from: " + antena7Url);
        
        showLoading();
        
        AntenaLatinaExtractor.extractStreamUrl(antena7Url, new AntenaLatinaExtractor.ExtractionCallback() {
            @Override
            public void onSuccess(String extractedUrl) {
                Log.d(TAG, "Antena Latina stream URL extracted: " + extractedUrl);
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        initializePlayer(extractedUrl);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Antena Latina extraction error: " + error);
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        hideLoading();
                        Toast.makeText(PlayerActivity.this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    private void loadTelesistemaStream(String videoId) {
        Log.d(TAG, "Loading Telesistema stream for video: " + videoId);
        
        showLoading();
        
        TelesistemaExtractor.getStreamUrl(videoId, new TelesistemaExtractor.StreamCallback() {
            @Override
            public void onSuccess(String streamUrl) {
                Log.d(TAG, "Telesistema stream URL extracted: " + streamUrl);
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        initializePlayer(streamUrl);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Telesistema extraction error: " + error);
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        hideLoading();
                        Toast.makeText(PlayerActivity.this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    private void loadRtvdStream() {
        Log.d(TAG, "Loading RTVD stream");
        
        showLoading();
        
        RtvdExtractor.extractStreamUrl(new RtvdExtractor.ExtractionCallback() {
            @Override
            public void onSuccess(String streamUrl) {
                Log.d(TAG, "RTVD stream URL extracted: " + streamUrl);
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        initializePlayer(streamUrl);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "RTVD extraction error: " + error);
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        hideLoading();
                        Toast.makeText(PlayerActivity.this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    private void loadCdnStream() {
        Log.d(TAG, "Loading CDN stream");
        
        showLoading();
        
        CdnExtractor.getStreamUrl(new CdnExtractor.StreamCallback() {
            @Override
            public void onSuccess(String streamUrl) {
                Log.d(TAG, "CDN stream URL extracted: " + streamUrl);
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        initializePlayer(streamUrl);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "CDN extraction error: " + error);
                runOnUiThread(() -> {
                    if (isActivityActive) {
                        hideLoading();
                        Toast.makeText(PlayerActivity.this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
                        finish();
                    }
                });
            }
        });
    }

    private void cleanupWebView() {
        if (hiddenWebView != null) {
            hiddenWebView.destroy();
            hiddenWebView = null;
        }
    }

    private void showLoading() {
        if (loadingText != null) {
            loadingText.setVisibility(View.VISIBLE);
            loadingDotCount = 0;
            loadingHandler.post(loadingRunnable);
        }
    }

    private void hideLoading() {
        if (loadingText != null) {
            loadingText.setVisibility(View.GONE);
            loadingHandler.removeCallbacks(loadingRunnable);
        }
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

    private void initializePlayer(String streamUrl) {
        if (streamUrl == null || streamUrl.isEmpty()) {
            Toast.makeText(this, "Canal no disponible por el momento.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        player = new ExoPlayer.Builder(this).build();
        
        if (playerView != null) {
            playerView.setPlayer(player);
        }

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    hideLoading();
                    if (playPauseButton != null) {
                        playPauseButton.setImageResource(android.R.drawable.ic_media_pause);
                    }
                    
                    if (analytics != null) {
                        Bundle params = new Bundle();
                        params.putString("channel_name", currentChannelName);
                        params.putString("channel_number", currentChannelNumber);
                        analytics.logEvent("playback_started", params);
                    }
                } else {
                    if (playPauseButton != null) {
                        playPauseButton.setImageResource(android.R.drawable.ic_media_play);
                    }
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_BUFFERING) {
                    showLoading();
                } else if (playbackState == Player.STATE_READY) {
                    hideLoading();
                } else if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                    hideLoading();
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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (controlsContainer.getVisibility() == View.VISIBLE) {
                return super.onKeyDown(keyCode, event);
            } else {
                showControls();
                return true;
            }
        }
        
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (player != null) {
                if (player.isPlaying()) {
                    player.pause();
                } else {
                    player.play();
                }
            }
            return true;
        }
        
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isActivityActive = false;
        
        if (analytics != null) {
            long watchDuration = (System.currentTimeMillis() - playbackStartTime) / 1000;
            Bundle params = new Bundle();
            params.putString("channel_name", currentChannelName);
            params.putString("channel_number", currentChannelNumber);
            params.putLong("watch_duration_seconds", watchDuration);
            analytics.logEvent("playback_ended", params);
        }
        
        if (hideControlsHandler != null && hideControlsRunnable != null) {
            hideControlsHandler.removeCallbacks(hideControlsRunnable);
        }
        
        if (loadingHandler != null && loadingRunnable != null) {
            loadingHandler.removeCallbacks(loadingRunnable);
        }
        
        cleanupWebView();
        
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