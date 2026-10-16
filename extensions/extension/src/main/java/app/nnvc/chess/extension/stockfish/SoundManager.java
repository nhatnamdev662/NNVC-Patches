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
 * Sử dụng SoundPool được cache sẵn để phát âm thanh chính hãng Chess.com với độ trễ cực thấp.
 */
public class SoundManager {
    private static final String TAG = "NNVC";

    private static volatile SoundManager instance;
    private final Context context;
    private SoundPool soundPool;

    private int brilliantSoundId = -1;
    private int threatSoundId = -1;
    private int goodSoundId = -1;
    private int badSoundId = -1;
    private int checkSoundId = -1;
    private int gameWinSoundId = -1;
    private int gameLoseSoundId = -1;
    private int gameDrawSoundId = -1;

    // Đường dẫn ứng viên cho các file âm thanh trong APK Chess.com
    private static final String[] BRILLIANT_SOUND_PATHS = {
            "sounds/brilliant.mp3",
            "sounds/delight/brilliant.mp3"
    };

    private static final String[] THREAT_SOUND_PATHS = {
            "sounds/event-warning.mp3",
            "sounds/event/event-warning.mp3",
            "sounds/event-warning-60s.mp3"
    };

    private static final String[] GOOD_SOUND_PATHS = {
            "sounds/results-and-feedback/training-result-good.mp3",
            "sounds/training-result-good.mp3",
            "sounds/game-actions/correct.mp3"
    };

    private static final String[] BAD_SOUND_PATHS = {
            "sounds/results-and-feedback/training-result-bad.mp3",
            "sounds/training-result-bad.mp3",
            "sounds/game-actions/incorrect.mp3"
    };

    private static final String[] CHECK_SOUND_PATHS = {
            "sounds/game-actions/move-check.mp3",
            "sounds/move-check.mp3"
    };

    private static final String[] GAME_WIN_SOUND_PATHS = {
            "sounds/game-events/game-win.mp3",
            "sounds/game-win.mp3"
    };

    private static final String[] GAME_LOSE_SOUND_PATHS = {
            "sounds/game-events/game-lose.mp3",
            "sounds/game-lose.mp3"
    };

    private static final String[] GAME_DRAW_SOUND_PATHS = {
            "sounds/game-events/game-draw.mp3",
            "sounds/game-draw.mp3"
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

    private int loadSoundCandidate(AssetManager am, String[] paths, String soundName) {
        for (String path : paths) {
            try (AssetFileDescriptor afd = am.openFd(path)) {
                int id = soundPool.load(afd, 1);
                TorchEngine.log("[SOUND] Loaded " + soundName + " sound candidate: " + path + " -> id=" + id);
                return id;
            } catch (Throwable ignored) {}
        }
        return -1;
    }

    private void initSoundPool() {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(6)
                    .setAudioAttributes(attrs)
                    .build();

            AssetManager am = context.getAssets();
            brilliantSoundId = loadSoundCandidate(am, BRILLIANT_SOUND_PATHS, "Brilliant");
            threatSoundId = loadSoundCandidate(am, THREAT_SOUND_PATHS, "Threat");
            goodSoundId = loadSoundCandidate(am, GOOD_SOUND_PATHS, "Good/Best");
            badSoundId = loadSoundCandidate(am, BAD_SOUND_PATHS, "Bad/Mistake");
            checkSoundId = loadSoundCandidate(am, CHECK_SOUND_PATHS, "Check");
            gameWinSoundId = loadSoundCandidate(am, GAME_WIN_SOUND_PATHS, "GameWin");
            gameLoseSoundId = loadSoundCandidate(am, GAME_LOSE_SOUND_PATHS, "GameLose");
            gameDrawSoundId = loadSoundCandidate(am, GAME_DRAW_SOUND_PATHS, "GameDraw");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize SoundPool: " + t.getMessage(), t);
        }
    }

    private void playSoundWithFallback(int soundId, String[] fallbackPaths, String soundLabel) {
        try {
            if (soundPool != null && soundId > 0) {
                int streamId = soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
                if (streamId == 0) {
                    playFallback(fallbackPaths, soundLabel);
                } else {
                    TorchEngine.log("[SOUND] Played " + soundLabel + " via SoundPool (stream=" + streamId + ")");
                }
            } else {
                playFallback(fallbackPaths, soundLabel);
            }
        } catch (Throwable t) {
            Log.w(TAG, "playSoundWithFallback error (" + soundLabel + "): " + t.getMessage());
            playFallback(fallbackPaths, soundLabel);
        }
    }

    private void playFallback(String[] paths, String soundLabel) {
        if (paths == null) return;
        try {
            AssetManager am = context.getAssets();
            for (String path : paths) {
                try (AssetFileDescriptor afd = am.openFd(path)) {
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
                    TorchEngine.log("[SOUND] Played " + soundLabel + " via MediaPlayer fallback (" + path + ")");
                    return;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            TorchEngine.log("[SOUND FALLBACK ERROR] " + soundLabel + ": " + t.getMessage());
        }
    }

    /** Phát âm thanh Brilliant khi có nước đi thiên tài */
    public void playBrilliantSound() {
        if (!StockfishSettings.isBrilliantSoundEnabled(context)) return;
        playSoundWithFallback(brilliantSoundId, BRILLIANT_SOUND_PATHS, "Brilliant");
    }

    /** Phát âm thanh Cảnh báo nguy hiểm khi xuất hiện đòn đe dọa từ đối thủ (Disabled - Brilliant only) */
    public void playThreatSound() {
    }

    /** Phát âm thanh Nước đi xuất sắc / tốt nhất (Disabled - Brilliant only) */
    public void playGoodMoveSound() {
    }

    /** Phát âm thanh Sai lầm / Đại sai lầm (Disabled - Brilliant only) */
    public void playBadMoveSound() {
    }

    /** Phát âm thanh Chiếu tướng (Disabled - Brilliant only) */
    public void playCheckSound() {
    }

    /** Phát âm thanh Kết thúc ván đấu (Disabled - Brilliant only) */
    public void playGameEndSound(boolean isWin, boolean isDraw) {
    }

    public void release() {
        if (soundPool != null) {
            try {
                soundPool.release();
            } catch (Throwable ignored) {}
            soundPool = null;
        }
    }
}
