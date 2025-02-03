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
import androidx.palette.graphics.Palette;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Color;
import android.view.View;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.MediaStore;
import android.content.ClipData;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

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
    private static final String PREFS_NAME = "MediaPlayerPrefs";
    private static final String LAST_SONGS_LIST = "LastSongsList";
    private static final String LAST_SONG_INDEX = "LastSongIndex";
    private static final String LAST_POSITION = "LastPosition";
    private static final String LAST_FOLDER_PATH = "LastFolderPath";
    private String lastFolderPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Load last folder path
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        lastFolderPath = prefs.getString(LAST_FOLDER_PATH, null);

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

        // If we have a saved folder path, open it automatically
        if (lastFolderPath != null) {
            openLastFolder();
        }

        // Load last session state
        loadLastSessionState();
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
                
                // Generate palette from album art
                Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
                    @Override
                    public void onGenerated(@Nullable Palette palette) {
                        if (palette != null) {
                            // Get the vibrant color (usually looks better than dominant)
                            int backgroundColor = palette.getDominantColor(
                                getResources().getColor(android.R.color.white)
                            );
                            
                            // Create a gradient background
                            GradientDrawable gradientDrawable = new GradientDrawable(
                                GradientDrawable.Orientation.TOP_BOTTOM,
                                new int[] {
                                    adjustAlpha(backgroundColor, 0.8f),
                                    adjustAlpha(backgroundColor, 0.3f)
                                }
                            );
                            
                            // Get the root layout
                            View rootLayout = findViewById(R.id.root_layout);
                            
                            // Apply the gradient with animation
                            runOnUiThread(() -> {
                                rootLayout.setBackground(gradientDrawable);
                                
                                // Adjust text colors for better visibility
                                int textColor = isColorDark(backgroundColor) ? 
                                    Color.WHITE : Color.BLACK;
                                songInfoText.setTextColor(textColor);
                                durationText.setTextColor(textColor);
                            });
                        }
                    }
                });
            } else {
                // Reset to default white background if no album art
                albumArt.setImageResource(R.drawable.ic_music_note);
                View rootLayout = findViewById(R.id.root_layout);
                rootLayout.setBackgroundColor(Color.WHITE);
                songInfoText.setTextColor(Color.BLACK);
                durationText.setTextColor(Color.BLACK);
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
            e.printStackTrace();
            // Reset to default if there's an error
            songInfoText.setText("File: " + songUri.getLastPathSegment() + "\n" +
                               "Song " + (currentSongIndex + 1) + " of " + songsList.size());
            albumArt.setImageResource(R.drawable.ic_music_note);
            View rootLayout = findViewById(R.id.root_layout);
            rootLayout.setBackgroundColor(Color.WHITE);
            songInfoText.setTextColor(Color.BLACK);
            durationText.setTextColor(Color.BLACK);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null) {
            songsList.clear();

            // Handle multiple selection
            if (data.getClipData() != null) {
                ClipData clipData = data.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri uri = clipData.getItemAt(i).getUri();
                    // Take permission for each URI
                    try {
                        getContentResolver().takePersistableUriPermission(
                            uri, 
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                    } catch (SecurityException se) {
                        // If we can't take persistent permission, we'll handle it during playback
                    }
                    songsList.add(uri);
                }
            } else if (data.getData() != null) {
                Uri uri = data.getData();
                // Take permission for single URI
                try {
                    getContentResolver().takePersistableUriPermission(
                        uri, 
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );
                } catch (SecurityException se) {
                    // If we can't take persistent permission, we'll handle it during playback
                }
                songsList.add(uri);
            }

            if (!songsList.isEmpty()) {
                currentSongIndex = 0;
                playSong(songsList.get(currentSongIndex));
                saveCurrentState();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            saveCurrentState();
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
            
            // Set up the MediaPlayer with the URI
            try {
                mediaPlayer.setDataSource(getApplicationContext(), songUri);
            } catch (SecurityException se) {
                // If we can't access the URI directly, try to get a file path
                String filePath = getRealPathFromURI(songUri);
                if (filePath != null) {
                    mediaPlayer.setDataSource(filePath);
                } else {
                    throw se;
                }
            }
            
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
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error playing this file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Audio.Media.DATA };
        try (Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                return cursor.getString(column_index);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private boolean isColorDark(int color) {
        return Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 < 128;
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    private void openLastFolder() {
        Uri folderUri = Uri.parse(lastFolderPath);
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setDataAndType(folderUri, "audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }

    private String getPathFromUri(Uri uri) {
        try {
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                String[] projection = { MediaStore.MediaColumns.DATA };
                Cursor cursor = null;

                try {
                    cursor = getContentResolver().query(uri, projection, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                        String path = cursor.getString(columnIndex);
                        return new File(path).getParent();
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                return new File(uri.getPath()).getParent();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void loadLastSessionState() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        // Load last songs list
        Set<String> savedSongs = prefs.getStringSet(LAST_SONGS_LIST, null);
        if (savedSongs != null && !savedSongs.isEmpty()) {
            songsList.clear();
            for (String uriString : savedSongs) {
                try {
                    Uri uri = Uri.parse(uriString);
                    songsList.add(uri);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            // Load last song index and position
            currentSongIndex = prefs.getInt(LAST_SONG_INDEX, 0);
            int lastPosition = prefs.getInt(LAST_POSITION, 0);
            
            // Ensure index is within bounds
            if (currentSongIndex >= songsList.size()) {
                currentSongIndex = 0;
            }
            
            // Play the last song from its last position
            if (!songsList.isEmpty()) {
                initializeMediaPlayer(songsList.get(currentSongIndex), lastPosition, false);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentState();
    }

    private void saveCurrentState() {
        if (mediaPlayer != null && !songsList.isEmpty()) {
            SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
            
            // Save songs list
            Set<String> songsUriStrings = new HashSet<>();
            for (Uri uri : songsList) {
                songsUriStrings.add(uri.toString());
            }
            editor.putStringSet(LAST_SONGS_LIST, songsUriStrings);
            
            // Save current song index and position
            editor.putInt(LAST_SONG_INDEX, currentSongIndex);
            editor.putInt(LAST_POSITION, mediaPlayer.getCurrentPosition());
            
            // Save current folder path if available
            if (lastFolderPath != null) {
                editor.putString(LAST_FOLDER_PATH, lastFolderPath);
            }
            
            editor.apply();
        }
    }
} 