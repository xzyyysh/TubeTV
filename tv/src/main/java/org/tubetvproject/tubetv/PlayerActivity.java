package org.tubetvproject.tubetv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

public class PlayerActivity extends Activity {

    private ExoPlayer player;
    private PlayerView playerView;
    private TextView channelInfoTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        channelInfoTextView = findViewById(R.id.channel_info);

        String channelName = getIntent().getStringExtra("channel_name");
        String channelNumber = getIntent().getStringExtra("channel_number");
        String streamUrl = getIntent().getStringExtra("stream_url");

        channelInfoTextView.setText(channelName + " - Canal " + channelNumber);

        initializePlayer(streamUrl);
    }

    private void initializePlayer(String streamUrl) {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);

        MediaItem mediaItem = MediaItem.fromUri(streamUrl);
        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
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
        if (player != null) {
            player.play();
        }
    }
}