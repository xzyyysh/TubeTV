package io.tubetvlol.tubetv.activities;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowInsetsController;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        hideSystemUI();

        playerView = findViewById(R.id.player_view);

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
                    Toast.makeText(PlayerActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
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
                    Toast.makeText(PlayerActivity.this, "Error: No se pudo obtener el stream", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }).start();
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
            player.play();
        }
    }
}