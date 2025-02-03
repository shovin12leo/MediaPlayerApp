package com.example.mediaplayerapp;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;

public class mediaplayerapp extends AppCompatActivity {

    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize MediaPlayer with your audio file from raw folder
        mediaPlayer = MediaPlayer.create(this, R.raw.sample_audio);

        // Find views using the correct IDs from layout
        Button playButton = findViewById(R.id.btnPlay);
        Button pauseButton = findViewById(R.id.btnPause);
        Button loopButton = findViewById(R.id.btnLoop);  // Changed from Switch to Button since layout has a Button

        // Set click listeners
        playButton.setOnClickListener(v -> {
            if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
            }
        });

        pauseButton.setOnClickListener(v -> {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
            }
        });

        boolean[] isLooping = {false};  // Using array to hold mutable state
        loopButton.setOnClickListener(v -> {
            isLooping[0] = !isLooping[0];  // Toggle loop state
            if (isLooping[0]) {
                mediaPlayer.setLooping(true);
                loopButton.setText("Loop On");
            } else {
                mediaPlayer.setLooping(false);
                loopButton.setText("Loop Off");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}