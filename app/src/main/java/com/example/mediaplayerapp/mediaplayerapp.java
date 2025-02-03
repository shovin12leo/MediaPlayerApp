package com.example.mediaplayerapp;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import com.example.mediaplayerapp.R;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class mediaplayerapp extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private static final int PICK_AUDIO_REQUEST = 1;
    private ArrayList<Uri> songsList = new ArrayList<>();
    private int currentSongIndex = 0;
    private TextView songInfoText;
    private TextView durationText;
    private SeekBar seekBar;
    private SeekBar volumeBar;
    private Handler handler = new Handler();
    private boolean isShuffleOn = false;
    private AudioManager audioManager;
    private ImageButton playButton;
    private ImageView albumArt;
    private int currentPosition = 0;
    private boolean wasPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize AudioManager
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Request permissions at startup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 123);
        }

        // Initialize views
        initializeViews();
        setupVolumeControl();
        setupClickListeners();
    }

    private void initializeViews() {
        songInfoText = findViewById(R.id.song_info_text);
        durationText = findViewById(R.id.duration_text);
        seekBar = findViewById(R.id.seek_bar);
        volumeBar = findViewById(R.id.volumeBar);
        playButton = findViewById(R.id.btn_play);
        albumArt = findViewById(R.id.albumArt);
    }

    private void setupVolumeControl() {
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        
        volumeBar.setMax(maxVolume);
        volumeBar.setProgress(currentVolume);
        
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        findViewById(R.id.btnVolumeUp).setOnClickListener(v -> {
            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
            volumeBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        });

        findViewById(R.id.btnVolumeDown).setOnClickListener(v -> {
            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_PLAY_SOUND);
            volumeBar.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        });
    }

    private void setupClickListeners() {
        ImageButton selectButton = findViewById(R.id.btn_select);
        ImageButton prevButton = findViewById(R.id.btn_prev);
        ImageButton nextButton = findViewById(R.id.btn_next);
        ImageButton shuffleButton = findViewById(R.id.btn_shuffle);
        ImageButton loopButton = findViewById(R.id.btn_loop);

        selectButton.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select Audio Files"), PICK_AUDIO_REQUEST);
        });

        playButton.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    playButton.setImageResource(R.drawable.ic_play);
                } else {
                    mediaPlayer.start();
                    playButton.setImageResource(R.drawable.ic_pause);
                    startSeekBarUpdate();
                }
            }
        });

        prevButton.setOnClickListener(v -> playPreviousSong());
        nextButton.setOnClickListener(v -> playNextSong());

        shuffleButton.setOnClickListener(v -> {
            isShuffleOn = !isShuffleOn;
            shuffleButton.setImageResource(isShuffleOn ? R.drawable.ic_shuffle : R.drawable.ic_shuffle);
            Toast.makeText(this, "Shuffle " + (isShuffleOn ? "On" : "Off"), Toast.LENGTH_SHORT).show();
        });

        loopButton.setOnClickListener(v -> {
            if (mediaPlayer != null) {
                boolean isLooping = !mediaPlayer.isLooping();
                mediaPlayer.setLooping(isLooping);
                loopButton.setImageResource(isLooping ? R.drawable.ic_repeat : R.drawable.ic_repeat);
                Toast.makeText(this, "Loop " + (isLooping ? "On" : "Off"), Toast.LENGTH_SHORT).show();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    updateDurationText();
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void startSeekBarUpdate() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    seekBar.setProgress(mediaPlayer.getCurrentPosition());
                    updateDurationText();
                    handler.postDelayed(this, 1000);
                }
            }
        }, 0);
    }

    private void updateDurationText() {
        if (mediaPlayer != null) {
            int currentPosition = mediaPlayer.getCurrentPosition();
            int duration = mediaPlayer.getDuration();
            String currentTime = formatTime(currentPosition);
            String totalTime = formatTime(duration);
            durationText.setText(currentTime + " / " + totalTime);
        }
    }

    private String formatTime(int milliseconds) {
        int seconds = (milliseconds / 1000) % 60;
        int minutes = (milliseconds / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private void playNextSong() {
        if (songsList.size() > 0) {
            if (isShuffleOn) {
                currentSongIndex = new Random().nextInt(songsList.size());
            } else {
                currentSongIndex = (currentSongIndex + 1) % songsList.size();
            }
            playSong(songsList.get(currentSongIndex));
        }
    }

    private void playPreviousSong() {
        if (songsList.size() > 0) {
            if (isShuffleOn) {
                currentSongIndex = new Random().nextInt(songsList.size());
            } else {
                currentSongIndex = (currentSongIndex - 1 + songsList.size()) % songsList.size();
            }
            playSong(songsList.get(currentSongIndex));
        }
    }

    private void playSong(Uri songUri) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getApplicationContext(), songUri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            playButton.setImageResource(R.drawable.ic_pause);
            updateSongInfo(songUri);
            seekBar.setMax(mediaPlayer.getDuration());
            startSeekBarUpdate();
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!mediaPlayer.isLooping()) {
                    playNextSong();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error playing audio file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSongInfo(Uri songUri) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(this, songUri);
            
            // Get metadata
            String title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            String artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            String album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            
            // Get and set album art
            byte[] albumArtBytes = retriever.getEmbeddedPicture();
            if (albumArtBytes != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(albumArtBytes, 0, albumArtBytes.length);
                albumArt.setImageBitmap(bitmap);
            } else {
                // Set default music icon if no album art
                albumArt.setImageResource(R.drawable.ic_music_note);
            }
            
            // Update song info text
            StringBuilder info = new StringBuilder();
            info.append("Title: ").append(title != null ? title : "Unknown").append("\n");
            info.append("Artist: ").append(artist != null ? artist : "Unknown").append("\n");
            info.append("Album: ").append(album != null ? album : "Unknown").append("\n");
            info.append("Song ").append(currentSongIndex + 1).append(" of ").append(songsList.size());
            
            songInfoText.setText(info.toString());
            
            retriever.release();
        } catch (Exception e) {
            songInfoText.setText("File: " + songUri.getLastPathSegment() + "\n" +
                               "Song " + (currentSongIndex + 1) + " of " + songsList.size());
            albumArt.setImageResource(R.drawable.ic_music_note);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null) {
            songsList.clear();
            
            if (data.getClipData() != null) {
                // Multiple files selected
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    Uri audioUri = data.getClipData().getItemAt(i).getUri();
                    songsList.add(audioUri);
                }
                Toast.makeText(this, count + " files selected", Toast.LENGTH_SHORT).show();
            } else if (data.getData() != null) {
                // Single file selected
                Uri audioUri = data.getData();
                songsList.add(audioUri);
                Toast.makeText(this, "1 file selected", Toast.LENGTH_SHORT).show();
            }
            
            if (!songsList.isEmpty()) {
                currentSongIndex = 0;
                playSong(songsList.get(currentSongIndex));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mediaPlayer != null) {
            outState.putInt("currentPosition", mediaPlayer.getCurrentPosition());
            outState.putBoolean("wasPlaying", mediaPlayer.isPlaying());
            outState.putInt("currentSongIndex", currentSongIndex);
            outState.putParcelableArrayList("songsList", songsList);
            outState.putBoolean("isShuffleOn", isShuffleOn);
            if (mediaPlayer.isLooping()) {
                outState.putBoolean("isLooping", true);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            currentPosition = savedInstanceState.getInt("currentPosition");
            wasPlaying = savedInstanceState.getBoolean("wasPlaying");
            currentSongIndex = savedInstanceState.getInt("currentSongIndex");
            songsList = savedInstanceState.getParcelableArrayList("songsList");
            isShuffleOn = savedInstanceState.getBoolean("isShuffleOn");
            
            if (!songsList.isEmpty()) {
                Uri songUri = songsList.get(currentSongIndex);
                initializeMediaPlayer(songUri, currentPosition, wasPlaying);
                if (savedInstanceState.getBoolean("isLooping", false)) {
                    mediaPlayer.setLooping(true);
                }
            }
        }
    }

    private void initializeMediaPlayer(Uri songUri, int position, boolean shouldPlay) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(getApplicationContext(), songUri);
            mediaPlayer.prepare();
            mediaPlayer.seekTo(position);
            
            if (shouldPlay) {
                mediaPlayer.start();
                playButton.setImageResource(R.drawable.ic_pause);
            } else {
                playButton.setImageResource(R.drawable.ic_play);
            }
            
            updateSongInfo(songUri);
            seekBar.setMax(mediaPlayer.getDuration());
            startSeekBarUpdate();
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!mediaPlayer.isLooping()) {
                    playNextSong();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error restoring playback: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
} 