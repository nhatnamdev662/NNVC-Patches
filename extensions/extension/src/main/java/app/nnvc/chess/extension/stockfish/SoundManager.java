/*
 * Copyright 2026 NNVC
 */

package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;

/**
 * SoundManager - Quản lý phát âm thanh chất lượng cao cho NNVC Extension.
 * Sử dụng SoundPool được cache sẵn để phát âm thanh Brilliant chính hãng Chess.com với độ trễ cực thấp.
 */
public class SoundManager {
    private static final String TAG = "SoundManager";

    private static volatile SoundManager instance;
    private final Context context;
    private SoundPool soundPool;
    private int brilliantSoundId = -1;
    private boolean isLoaded = false;

    // Đường dẫn ứng viên cho file âm thanh Brilliant trong APK Chess.com
    private static final String[] BRILLIANT_SOUND_PATHS = {
            "sounds/brilliant.mp3",
            "sounds/delight/brilliant.mp3"
    };

    private SoundManager(Context context) {
        this.context = context.getApplicationContext();
        initSoundPool();
    }

    public static SoundManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SoundManager.class) {
                if (instance == null) {
                    instance = new SoundManager(context);
                }
            }
        }
        return instance;
    }

    private void initSoundPool() {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(attrs)
                    .build();

            soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
                if (status == 0 && sampleId == brilliantSoundId) {
                    isLoaded = true;
                    TorchEngine.log("[SOUND] Brilliant sound asset loaded successfully (id=" + sampleId + ")");
                }
            });

            AssetManager am = context.getAssets();
            for (String path : BRILLIANT_SOUND_PATHS) {
                try (AssetFileDescriptor afd = am.openFd(path)) {
                    brilliantSoundId = soundPool.load(afd, 1);
                    TorchEngine.log("[SOUND] Loaded sound candidate: " + path + " -> id=" + brilliantSoundId);
                    break;
                } catch (Throwable ignored) {
                    // Thử đường dẫn ứng viên tiếp theo
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize SoundPool: " + t.getMessage(), t);
        }
    }

    /**
     * Phát âm thanh Brilliant khi có nước đi thiên tài (Brilliant Move).
     */
    public void playBrilliantSound() {
        try {
            if (soundPool != null && brilliantSoundId > 0) {
                int streamId = soundPool.play(brilliantSoundId, 1.0f, 1.0f, 1, 0, 1.0f);
                if (streamId == 0) {
                    // Nếu soundPool chưa kịp load xong (chưa trigger onLoadComplete), phát qua MediaPlayer an toàn
                    playBrilliantFallback();
                } else {
                    TorchEngine.log("[SOUND] Played Brilliant sound via SoundPool (stream=" + streamId + ")");
                }
            } else {
                playBrilliantFallback();
            }
        } catch (Throwable t) {
            Log.w(TAG, "playBrilliantSound error: " + t.getMessage());
            playBrilliantFallback();
        }
    }

    private void playBrilliantFallback() {
        try {
            AssetManager am = context.getAssets();
            for (String path : BRILLIANT_SOUND_PATHS) {
                try {
                    AssetFileDescriptor afd = am.openFd(path);
                    android.media.MediaPlayer mp = new android.media.MediaPlayer();
                    if (Build.VERSION.SDK_INT >= 21) {
                        AudioAttributes attrs = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build();
                        mp.setAudioAttributes(attrs);
                    }
                    mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    afd.close();
                    mp.prepare();
                    mp.setOnCompletionListener(android.media.MediaPlayer::release);
                    mp.start();
                    TorchEngine.log("[SOUND] Played Brilliant sound via MediaPlayer fallback (" + path + ")");
                    return;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            TorchEngine.log("[SOUND FALLBACK ERROR] " + t.getMessage());
        }
    }

    public void release() {
        if (soundPool != null) {
            try {
                soundPool.release();
            } catch (Throwable ignored) {}
            soundPool = null;
            isLoaded = false;
        }
    }
}
