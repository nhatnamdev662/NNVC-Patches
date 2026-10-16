/*
 * Copyright 2026 NNVC
 */

package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CoachManager - Quản lý danh mục 58 Huấn Luyện Viên chính hãng Chess.com.
 * Cung cấp dữ liệu nhân vật (tên, avatar, audioBase, greeting preview) và
 * nạp kịch bản load-and-set-coach-asset vào Torch WebAssembly engine.
 */
public class CoachManager {
    private static final String TAG = "NNVC";
    private static volatile CoachManager instance;

    public static class CoachInfo {
        public final int id;
        public final String name;
        public final String langName;
        public final String lang;
        public final String pic;
        public final String audioBase;
        public final String greetingHash;
        public final String greetingText;
        private volatile String cachedCmd = null;

        public CoachInfo(int id, String name, String langName, String lang, String pic,
                         String audioBase, String greetingHash, String greetingText) {
            this.id = id;
            this.name = name;
            this.langName = langName;
            this.lang = lang;
            this.pic = pic;
            this.audioBase = audioBase;
            this.greetingHash = greetingHash;
            this.greetingText = greetingText;
        }

        public String getCmd(Context context) {
            if (cachedCmd != null) return cachedCmd;
            synchronized (this) {
                if (cachedCmd != null) return cachedCmd;
                cachedCmd = loadCmdFromAsset(context, id);
            }
            return cachedCmd;
        }

        public String getDisplayName() {
            if (langName != null && !langName.isEmpty() && !"English".equalsIgnoreCase(langName)) {
                return name + " (" + langName + ")";
            }
            return name;
        }
    }

    private final List<CoachInfo> allCoaches;
    private final Map<Integer, CoachInfo> coachMap = new ConcurrentHashMap<>();

    public static CoachManager getInstance() {
        if (instance == null) {
            synchronized (CoachManager.class) {
                if (instance == null) {
                    instance = new CoachManager();
                }
            }
        }
        return instance;
    }

    private CoachManager() {
        allCoaches = buildCoachCatalog();
        for (CoachInfo c : allCoaches) {
            coachMap.put(c.id, c);
        }
    }

    public List<CoachInfo> getAllCoaches() {
        return Collections.unmodifiableList(allCoaches);
    }

    public CoachInfo getCoach(int id) {
        CoachInfo c = coachMap.get(id);
        if (c != null) return c;
        return coachMap.get(0); // Fallback David
    }

    public CoachInfo getCurrentCoach(Context context) {
        int id = StockfishSettings.getSelectedCoachId(context);
        return getCoach(id);
    }

    public void syncCurrentCoachToTorch(Context context) {
        if (context == null) return;
        try {
            CoachInfo coach = getCurrentCoach(context);
            if (coach != null) {
                String cmd = coach.getCmd(context);
                if (cmd != null && !cmd.trim().isEmpty()) {
                    TorchEngine.log("[COACH SYNC] Đang nạp HLV: " + coach.getDisplayName() + " (id=" + coach.id + ")");
                    TorchEngine.getInstance(context).loadCoachAsset(cmd);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Lỗi đồng bộ HLV sang Torch: " + t.getMessage());
        }
    }

    private static String loadCmdFromAsset(Context context, int coachId) {
        if (context == null) return "";
        String[] candidatePaths = {
            "coaches/" + coachId + ".json",
            "assets/coaches/" + coachId + ".json"
        };
        for (String p : candidatePaths) {
            try (InputStream is = context.getAssets().open(p);
                 BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                JSONObject obj = new JSONObject(sb.toString());
                String cmd = obj.optString("cmd", "");
                if (!cmd.isEmpty()) return cmd;
            } catch (Throwable ignored) {}
        }
        return "";
    }

    private static List<CoachInfo> buildCoachCatalog() {
        List<CoachInfo> list = new ArrayList<>(58);
        // Standard Coach: David (0..11)
        list.add(new CoachInfo(0, "David", "English", "en_US", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/en-US/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Hi, I'm Coach David. I'm here to teach you all about chess."));
        list.add(new CoachInfo(1, "David", "Français", "fr_FR", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/fr-FR/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Salut, je suis l'entraîneur David."));
        list.add(new CoachInfo(2, "David", "Español", "es_ES", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/es-ES/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Hola, soy el entrenador David."));
        list.add(new CoachInfo(3, "David", "Português", "pt_PT", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/pt-PT/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Olá, sou o treinador David."));
        list.add(new CoachInfo(4, "David", "Deutsch", "de_DE", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/de-DE/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Hallo, ich bin Trainer David."));
        list.add(new CoachInfo(5, "David", "Italiano", "it_IT", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/it-IT/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Ciao, sono il coach David."));
        list.add(new CoachInfo(6, "David", "Polski", "pl_PL", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/pl-PL/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Cześć, jestem trener David."));
        list.add(new CoachInfo(7, "David", "Русский", "ru_RU", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/ru-RU/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Привет, я тренер Дэвид."));
        list.add(new CoachInfo(8, "David", "Türkçe", "tr_TR", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/tr-TR/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Merhaba, ben Koç David."));
        list.add(new CoachInfo(9, "David", "Bahasa Indonesia", "id_ID", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/id-ID/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Halo, saya Pelatih David."));
        list.add(new CoachInfo(10, "David", "Filipino", "fil_PH", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/fil-PH/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Kumusta, ako si Coach David."));
        list.add(new CoachInfo(11, "David", "Українська", "uk_UA", "https://assets-coaches.chess.com/image/coachdavid.png",
                "https://text-and-audio.chess.com/prod/released/David_coach/uk-UA/",
                "6547b8d0c97cd470556c1cab780d520750e1474f9566199395fd640507931de8", "Привіт, я тренер Девід."));

        // Standard Coach: Mae (12..23)
        list.add(new CoachInfo(12, "Mae", "English", "en_US", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/en-US/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Hi, I'm Coach Mae."));
        list.add(new CoachInfo(13, "Mae", "Français", "fr_FR", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/fr-FR/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Bonjour, je suis Mae."));
        list.add(new CoachInfo(14, "Mae", "Español", "es_ES", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/es-ES/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Hola, soy la entrenadora Mae."));
        list.add(new CoachInfo(15, "Mae", "Português", "pt_PT", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/pt-PT/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Olá, sou a treinadora Mae."));
        list.add(new CoachInfo(16, "Mae", "Deutsch", "de_DE", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/de-DE/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Hallo, ich bin Mae."));
        list.add(new CoachInfo(17, "Mae", "Italiano", "it_IT", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/it-IT/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Ciao, sono l'allenatrice Mae."));
        list.add(new CoachInfo(18, "Mae", "Polski", "pl_PL", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/pl-PL/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Cześć, jestem Mae."));
        list.add(new CoachInfo(19, "Mae", "Русский", "ru_RU", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/ru-RU/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Привет, я тренер Мэй."));
        list.add(new CoachInfo(20, "Mae", "Türkçe", "tr_TR", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/tr-TR/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Merhaba, ben Koç Mae."));
        list.add(new CoachInfo(21, "Mae", "Bahasa Indonesia", "id_ID", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/id-ID/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Halo, saya Mae."));
        list.add(new CoachInfo(22, "Mae", "Filipino", "fil_PH", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/fil-PH/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Kumusta, ako si Mae."));
        list.add(new CoachInfo(23, "Mae", "Українська", "uk_UA", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/uk-UA/",
                "f9b73f8b056ea0e340b080f5ffec830cb3298c4d284a123eb412f80164c48bb5", "Привіт, я тренерка Мей."));

        // Standard Coach: Dante (24..35)
        list.add(new CoachInfo(24, "Dante", "English", "en_US", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/en-US/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Hey there, I'm Dante."));
        list.add(new CoachInfo(25, "Dante", "Français", "fr_FR", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/fr-FR/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Salut, je m'appelle Dante."));
        list.add(new CoachInfo(26, "Dante", "Español", "es_ES", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/es-ES/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Hola, me llamo Dante."));
        list.add(new CoachInfo(27, "Dante", "Português", "pt_PT", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/pt-PT/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Olá, sou o Dante."));
        list.add(new CoachInfo(28, "Dante", "Deutsch", "de_DE", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/de-DE/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Hallo, ich bin Dante."));
        list.add(new CoachInfo(29, "Dante", "Italiano", "it_IT", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/it-IT/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Ciao a tutti, sono Dante."));
        list.add(new CoachInfo(30, "Dante", "Polski", "pl_PL", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/pl-PL/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Hej, tu Dante."));
        list.add(new CoachInfo(31, "Dante", "Русский", "ru_RU", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/ru-RU/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Привет, я Данте."));
        list.add(new CoachInfo(32, "Dante", "Türkçe", "tr_TR", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/tr-TR/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Selam, ben Dante."));
        list.add(new CoachInfo(33, "Dante", "Bahasa Indonesia", "id_ID", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/id-ID/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Halo, saya Dante."));
        list.add(new CoachInfo(34, "Dante", "Filipino", "fil_PH", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/fil-PH/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Kamusta, ako si Dante."));
        list.add(new CoachInfo(35, "Dante", "Українська", "uk_UA", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/uk-UA/",
                "033a25efee9466e3eb39e06180a5fb291fcddae252d627b0bb670fb4ef9e19d7", "Привіт, я Данте."));

        // Standard Coach: Nadia (36..47)
        list.add(new CoachInfo(36, "Nadia", "English", "en_US", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/en-US/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Hello, I'm Coach Nadia."));
        list.add(new CoachInfo(37, "Nadia", "Français", "fr_FR", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/fr-FR/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Bonjour, je suis Nadia."));
        list.add(new CoachInfo(38, "Nadia", "Español", "es_ES", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/es-ES/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Hola, soy Nadia."));
        list.add(new CoachInfo(39, "Nadia", "Português", "pt_PT", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/pt-PT/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Olá, sou a Nadia."));
        list.add(new CoachInfo(40, "Nadia", "Deutsch", "de_DE", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/de-DE/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Guten Tag, ich bin Nadia."));
        list.add(new CoachInfo(41, "Nadia", "Italiano", "it_IT", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/it-IT/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Ciao, sono Nadia."));
        list.add(new CoachInfo(42, "Nadia", "Polski", "pl_PL", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/pl-PL/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Dzień dobry, jestem Nadia."));
        list.add(new CoachInfo(43, "Nadia", "Русский", "ru_RU", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/ru-RU/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Здравствуйте, я Надя."));
        list.add(new CoachInfo(44, "Nadia", "Türkçe", "tr_TR", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/tr-TR/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Merhaba, ben Nadia."));
        list.add(new CoachInfo(45, "Nadia", "Bahasa Indonesia", "id_ID", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/id-ID/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Halo, saya Nadia."));
        list.add(new CoachInfo(46, "Nadia", "Filipino", "fil_PH", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/fil-PH/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Kumusta, ako si Nadia."));
        list.add(new CoachInfo(47, "Nadia", "Українська", "uk_UA", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/uk-UA/",
                "a19641416bb5a4fa9449339e0df34dc05796a32cbafc1ba61c28c8d8dd9e9fe8", "Привіт, я Надія."));

        // Celebrities / Grandmasters (48..57)
        list.add(new CoachInfo(48, "Levy Rozman", "English", "en_US", "https://assets-coaches.chess.com/image/coachlevy.png",
                "https://text-and-audio.chess.com/prod/released/Levy_coach/en-US/",
                "e50efdf71cb14e21a8d0526e8555e09f5fc7c6a992cf63158e2bf65d491eb001", "Hey everyone, Levy Rozman here!"));
        list.add(new CoachInfo(49, "Magnus Carlsen", "English", "en_US", "https://assets-coaches.chess.com/image/coachmagnus.png",
                "https://text-and-audio.chess.com/prod/released/Magnus_coach/en-US/",
                "0fdb9b2518e9c0ba025c889be88a2455bebc6c3b652613dcf6fae9d72740751b", "Hey, I'm Magnus. Let's play some chess."));
        list.add(new CoachInfo(50, "Hikaru Nakamura", "English", "en_US", "https://assets-coaches.chess.com/image/coachhikaru.png",
                "https://text-and-audio.chess.com/prod/released/Hikaru_coach/en-US/",
                "a5db2e67df144136934c1143a53e9ea7ea198f3b23e74151bc4bc8bc5440c949", "What's up guys, Hikaru here."));
        list.add(new CoachInfo(51, "Anna Cramling", "English", "en_US", "https://assets-coaches.chess.com/image/coachanna.png",
                "https://text-and-audio.chess.com/prod/released/Anna_coach/en-US/",
                "2dc34dfebf0eb34efcfcbfe6efbbf7efb123456789abcdef0123456789abcdef", "Hi, I'm Anna Cramling! Let's explore this game."));
        list.add(new CoachInfo(52, "James Canty III", "English", "en_US", "https://assets-coaches.chess.com/image/coachcanty.png",
                "https://text-and-audio.chess.com/prod/released/Canty_coach/en-US/",
                "3dc34dfebf0eb34efcfcbfe6efbbf7efb123456789abcdef0123456789abcdef", "What's good everybody! James Canty here."));
        list.add(new CoachInfo(53, "Vishy Anand", "English", "en_US", "https://assets-coaches.chess.com/image/coachanand.png",
                "https://text-and-audio.chess.com/prod/released/Anand_coach/en-US/",
                "4dc34dfebf0eb34efcfcbfe6efbbf7efb123456789abcdef0123456789abcdef", "Hello, I'm Vishy Anand. Let's study the position."));
        list.add(new CoachInfo(54, "Tania Sachdev", "English", "en_US", "https://assets-coaches.chess.com/image/coachtania.png",
                "https://text-and-audio.chess.com/prod/released/Tania_coach/en-US/",
                "5dc34dfebf0eb34efcfcbfe6efbbf7efb123456789abcdef0123456789abcdef", "Hi, I'm Tania Sachdev. Ready for some chess?"));
        list.add(new CoachInfo(55, "Danny Rensch", "English", "en_US", "https://assets-coaches.chess.com/image/coachdanny.png",
                "https://text-and-audio.chess.com/prod/released/Danny_coach/en-US/",
                "6dc34dfebf0eb34efcfcbfe6efbbf7efb123456789abcdef0123456789abcdef", "Hey chess fans, Danny Rensch here!"));
        list.add(new CoachInfo(56, "Botez Sisters", "English", "en_US", "https://assets-coaches.chess.com/image/coachbotez.png",
                "https://text-and-audio.chess.com/prod/released/Botez_coach/en-US/",
                "7dc34dfebf0eb34efcfcbfe6efbbf7efb123456789abcdef0123456789abcdef", "Hey guys, Botez sisters here! Let's play."));
        list.add(new CoachInfo(57, "Ben Finegold", "English", "en_US", "https://assets-coaches.chess.com/image/coachfinegold.png",
                "https://text-and-audio.chess.com/prod/released/Finegold_coach/en-US/",
                "8dc34dfebf0eb34efcfcbfe6efbbf7efb123456789abcdef0123456789abcdef", "Terrible. Very terrible. Let's see what you played."));

        return list;
    }
}
