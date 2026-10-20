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
    private int moveSelfSoundId = -1;
    private int captureSoundId = -1;
    private int castleSoundId = -1;
    private int promoteSoundId = -1;

    // Đường dẫn ứng viên cho file âm thanh Brilliant trong APK Chess.com
    private static final String[] BRILLIANT_SOUND_PATHS = {
            "sounds/brilliant.mp3",
            "sounds/delight/brilliant.mp3"
    };

    private static final String[] MOVE_SELF_PATHS = {
            "sounds/game-actions/move-self.mp3",
            "sounds/move-self.mp3",
            "sounds/click.mp3"
    };

    private static final String[] CAPTURE_PATHS = {
            "sounds/game-actions/capture.mp3",
            "sounds/capture.mp3",
            "sounds/click.mp3"
    };

    private static final String[] CASTLE_PATHS = {
            "sounds/game-actions/castle.mp3",
            "sounds/castle.mp3",
            "sounds/game-actions/move-self.mp3"
    };

    private static final String[] PROMOTE_PATHS = {
            "sounds/game-actions/promote.mp3",
            "sounds/game-actions/move-self.mp3"
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

    private final java.util.Set<Integer> loadedSoundIds = java.util.Collections.synchronizedSet(new java.util.HashSet<Integer>());

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

            soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool sp, int sampleId, int status) {
                    if (status == 0) {
                        loadedSoundIds.add(sampleId);
                    }
                }
            });

            AssetManager am = context.getAssets();
            brilliantSoundId = loadSoundCandidate(am, BRILLIANT_SOUND_PATHS, "Brilliant");
            moveSelfSoundId = loadSoundCandidate(am, MOVE_SELF_PATHS, "MoveSelf");
            captureSoundId = loadSoundCandidate(am, CAPTURE_PATHS, "Capture");
            castleSoundId = loadSoundCandidate(am, CASTLE_PATHS, "Castle");
            promoteSoundId = loadSoundCandidate(am, PROMOTE_PATHS, "Promote");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize SoundPool: " + t.getMessage(), t);
        }
    }

    private void playSoundDirect(int soundId, String soundLabel) {
        try {
            if (soundPool != null && soundId > 0 && loadedSoundIds.contains(soundId)) {
                int streamId = soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
                if (streamId != 0) {
                    TorchEngine.log("[SOUND] Played " + soundLabel + " via SoundPool (stream=" + streamId + ")");
                } else {
                    TorchEngine.log("[SOUND WARNING] Could not play stream for " + soundLabel + " (SoundPool returned 0)");
                }
            } else {
                TorchEngine.log("[SOUND WARNING] " + soundLabel + " sound is not loaded in SoundPool");
            }
        } catch (Throwable t) {
            Log.w(TAG, "playSoundDirect error (" + soundLabel + "): " + t.getMessage());
        }
    }

    /** Phát âm thanh Brilliant khi có nước đi thiên tài */
    public void playBrilliantSound() {
        if (!StockfishSettings.isBrilliantSoundEnabled(context)) return;
        playSoundDirect(brilliantSoundId, "Brilliant");
    }

    /**
     * Phát âm thanh nước đi của quân cờ chính hãng Chess.com với độ trễ 0ms qua SoundPool.
     */
    public void playMoveSound(boolean isCapture, boolean isCastle, boolean isPromo) {
        int soundId;
        String label;

        if (isPromo) {
            soundId = (promoteSoundId > 0) ? promoteSoundId : moveSelfSoundId;
            label = "Promote";
        } else if (isCastle) {
            soundId = (castleSoundId > 0) ? castleSoundId : moveSelfSoundId;
            label = "Castle";
        } else if (isCapture) {
            soundId = (captureSoundId > 0) ? captureSoundId : moveSelfSoundId;
            label = "Capture";
        } else {
            soundId = moveSelfSoundId;
            label = "MoveSelf";
        }

        playSoundDirect(soundId, label);
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
