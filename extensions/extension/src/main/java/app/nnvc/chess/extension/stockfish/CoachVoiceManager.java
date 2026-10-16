/*
 * Copyright 2026 NNVC
 */

package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CoachVoiceManager - Quản lý phát âm thanh giọng nói của 58 Huấn Luyện Viên.
 * Tải stream mp3 từ CDN chính thức Chess.com kèm xác thực header tránh chặn 403,
 * lưu cache cục bộ và phát qua MediaPlayer mượt mà.
 * 
 * QUY TẮC BẮT BUỘC (User Rule):
 * - TUYỆT ĐỐI KHÔNG DÙNG TTS (Text-To-Speech)
 * - TUYỆT ĐỐI KHÔNG FALLBACK khi voice lỗi hoặc mất mạng (Giữ im lặng hoàn toàn).
 */
public class CoachVoiceManager {
    private static final String TAG = "NNVC";
    private static volatile CoachVoiceManager instance;

    private final Context context;
    private final Handler mainHandler;
    private final ExecutorService networkExecutor;

    private MediaPlayer mediaPlayer;
    private volatile String currentPlayingHash = null;

    public static CoachVoiceManager getInstance(Context context) {
        if (instance == null) {
            synchronized (CoachVoiceManager.class) {
                if (instance == null) {
                    instance = new CoachVoiceManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private CoachVoiceManager(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.networkExecutor = Executors.newSingleThreadExecutor();
        initMediaPlayer();
    }

    private void initMediaPlayer() {
        mainHandler.post(() -> {
            try {
                if (mediaPlayer != null) {
                    try { mediaPlayer.release(); } catch (Throwable ignored) {}
                }
                mediaPlayer = new MediaPlayer();
                AudioAttributes attr = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                mediaPlayer.setAudioAttributes(attr);
                mediaPlayer.setOnCompletionListener(mp -> {
                    currentPlayingHash = null;
                });
                mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                    currentPlayingHash = null;
                    return true; // Xử lý lỗi im lặng, không throw
                });
            } catch (Throwable t) {
                Log.e(TAG, "Lỗi khởi tạo MediaPlayer Voice: " + t.getMessage());
            }
        });
    }

    private File getVoiceCacheDir() {
        File dir = new File(context.getCacheDir(), "coach_voices");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Dừng phát âm thanh hiện tại.
     */
    public void stop() {
        mainHandler.post(() -> {
            try {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                currentPlayingHash = null;
            } catch (Throwable ignored) {}
        });
    }

    /**
     * Phát câu nhận xét nước cờ vừa đi từ Torch Engine.
     */
    public void speakMove(String audioUrlHash, String speechText, Context ctx) {
        Context c = (ctx != null) ? ctx : context;
        if (!StockfishSettings.isVoiceCoachEnabled(c)) return;
        if (audioUrlHash == null || audioUrlHash.trim().isEmpty()) return;

        CoachManager.CoachInfo coach = CoachManager.getInstance().getCurrentCoach(c);
        if (coach == null || coach.audioBase == null || coach.audioBase.isEmpty()) return;

        playAudioHash(coach, audioUrlHash.trim());
    }

    /**
     * Phát câu chào mừng mở đầu của HLV khi chọn trong menu hoặc bấm nghe thử.
     */
    public void playGreeting(int coachId, Context ctx) {
        Context c = (ctx != null) ? ctx : context;
        if (coachId < 0) return;

        CoachManager.CoachInfo coach = CoachManager.getInstance().getCoach(coachId);
        if (coach == null || coach.greetingHash == null || coach.greetingHash.isEmpty()) return;

        TorchEngine.log("[COACH GREETING] Nghe thử giọng chào của: " + coach.getDisplayName());
        playAudioHash(coach, coach.greetingHash);
    }

    private void playAudioHash(CoachManager.CoachInfo coach, String hash) {
        if (coach == null || hash == null || hash.isEmpty()) return;

        final File cacheFile = new File(getVoiceCacheDir(), coach.id + "_" + hash + ".mp3");

        // 1. Nếu đã có trong cache bộ nhớ tạm -> phát ngay tức thì
        if (cacheFile.exists() && cacheFile.length() > 500) {
            playLocalFile(cacheFile, hash);
            return;
        }

        // 2. Chưa có trong cache -> tải từ CDN Chess.com trên luồng nền
        final String downloadUrl = coach.audioBase + hash + ".mp3";
        networkExecutor.execute(() -> {
            boolean success = downloadMp3(downloadUrl, cacheFile);
            if (success && cacheFile.exists() && cacheFile.length() > 500) {
                playLocalFile(cacheFile, hash);
            } else {
                // TUYỆT ĐỐI KHÔNG FALLBACK KHI LỖI:
                // Nếu tải thất bại hoặc không có mạng, giữ im lặng hoàn toàn.
                TorchEngine.log("[COACH VOICE SILENT] Không thể tải voice CDN (" + hash + "). Giữ im lặng.");
            }
        });
    }

    private void playLocalFile(File file, String hash) {
        mainHandler.post(() -> {
            try {
                if (mediaPlayer == null) initMediaPlayer();
                if (mediaPlayer == null) return;

                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.reset();
                mediaPlayer.setDataSource(file.getAbsolutePath());
                currentPlayingHash = hash;
                mediaPlayer.prepareAsync();
                mediaPlayer.setOnPreparedListener(mp -> {
                    try {
                        mp.start();
                        TorchEngine.log("[COACH VOICE PLAYING] Đang phát voice: " + file.getName());
                    } catch (Throwable t) {
                        Log.e(TAG, "Lỗi start MediaPlayer: " + t.getMessage());
                    }
                });
            } catch (Throwable t) {
                TorchEngine.log("[COACH VOICE ERROR] Lỗi phát file mp3: " + t.getMessage());
                // Lỗi im lặng, không fallback
            }
        });
    }

    private boolean downloadMp3(String urlStr, File destFile) {
        HttpURLConnection conn = null;
        InputStream in = null;
        FileOutputStream out = null;
        File tmpFile = new File(destFile.getParentFile(), destFile.getName() + ".tmp");

        try {
            TorchEngine.log("[COACH VOICE DOWNLOAD] Tải âm thanh từ CDN: " + urlStr);
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            // Header chống Cloudflare 403 Forbidden
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36");
            conn.setRequestProperty("Referer", "https://www.chess.com/");
            conn.setRequestProperty("Origin", "https://www.chess.com");

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                TorchEngine.log("[COACH VOICE CDN HTTP " + responseCode + "] Lỗi CDN tải voice.");
                return false;
            }

            in = conn.getInputStream();
            out = new FileOutputStream(tmpFile);

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();

            if (tmpFile.exists() && tmpFile.length() > 500) {
                if (destFile.exists()) destFile.delete();
                return tmpFile.renameTo(destFile);
            }
            return false;
        } catch (Throwable t) {
            TorchEngine.log("[COACH VOICE DL FAILED] " + t.getMessage());
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (out != null) out.close(); } catch (Throwable ignored) {}
            try { if (conn != null) conn.disconnect(); } catch (Throwable ignored) {}
            if (tmpFile.exists()) tmpFile.delete();
        }
    }
}
