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

        public String getGreetingAudioUrl() {
            if (greetingHash == null || greetingHash.isEmpty()) return null;
            String base = audioBase;
            int idx = base.indexOf("/released/");
            if (idx != -1) {
                int slash2 = base.indexOf('/', idx + 10);
                if (slash2 != -1) {
                    String voicePrefix = base.substring(0, slash2);
                    return voicePrefix + "/en-US/" + greetingHash + ".mp3";
                }
            }
            return base + greetingHash + ".mp3";
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
                TorchEngine.log("[COACH SYNC] Đang nạp HLV: " + coach.getDisplayName() + " (id=" + coach.id + ")");
                TorchEngine.getInstance(context).loadCoachById(coach.id, coach.lang);
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
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Hi, I'm Coach Mae."));
        list.add(new CoachInfo(13, "Mae", "Français", "fr_FR", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/fr-FR/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Bonjour, je suis Mae."));
        list.add(new CoachInfo(14, "Mae", "Español", "es_ES", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/es-ES/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Hola, soy la entrenadora Mae."));
        list.add(new CoachInfo(15, "Mae", "Português", "pt_PT", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/pt-PT/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Olá, sou a treinadora Mae."));
        list.add(new CoachInfo(16, "Mae", "Deutsch", "de_DE", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/de-DE/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Hallo, ich bin Mae."));
        list.add(new CoachInfo(17, "Mae", "Italiano", "it_IT", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/it-IT/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Ciao, sono l'allenatrice Mae."));
        list.add(new CoachInfo(18, "Mae", "Polski", "pl_PL", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/pl-PL/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Cześć, jestem Mae."));
        list.add(new CoachInfo(19, "Mae", "Русский", "ru_RU", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/ru-RU/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Привет, я тренер Мэй."));
        list.add(new CoachInfo(20, "Mae", "Türkçe", "tr_TR", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/tr-TR/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Merhaba, ben Koç Mae."));
        list.add(new CoachInfo(21, "Mae", "Bahasa Indonesia", "id_ID", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/id-ID/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Halo, saya Mae."));
        list.add(new CoachInfo(22, "Mae", "Filipino", "fil_PH", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/fil-PH/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Kumusta, ako si Mae."));
        list.add(new CoachInfo(23, "Mae", "Українська", "uk_UA", "https://assets-coaches.chess.com/image/coachmae.png",
                "https://text-and-audio.chess.com/prod/released/Mae_coach/uk-UA/",
                "1f37eb5ce41f900a3a771920313d70625477b43a33020674fc282bf301017b39", "Привіт, я тренерка Мей."));

        // Standard Coach: Dante (24..35)
        list.add(new CoachInfo(24, "Dante", "English", "en_US", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/en-US/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Hey there, I'm Dante."));
        list.add(new CoachInfo(25, "Dante", "Français", "fr_FR", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/fr-FR/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Salut, je m'appelle Dante."));
        list.add(new CoachInfo(26, "Dante", "Español", "es_ES", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/es-ES/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Hola, me llamo Dante."));
        list.add(new CoachInfo(27, "Dante", "Português", "pt_PT", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/pt-PT/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Olá, sou o Dante."));
        list.add(new CoachInfo(28, "Dante", "Deutsch", "de_DE", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/de-DE/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Hallo, ich bin Dante."));
        list.add(new CoachInfo(29, "Dante", "Italiano", "it_IT", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/it-IT/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Ciao a tutti, sono Dante."));
        list.add(new CoachInfo(30, "Dante", "Polski", "pl_PL", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/pl-PL/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Hej, tu Dante."));
        list.add(new CoachInfo(31, "Dante", "Русский", "ru_RU", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/ru-RU/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Привет, я Данте."));
        list.add(new CoachInfo(32, "Dante", "Türkçe", "tr_TR", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/tr-TR/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Selam, ben Dante."));
        list.add(new CoachInfo(33, "Dante", "Bahasa Indonesia", "id_ID", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/id-ID/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Halo, saya Dante."));
        list.add(new CoachInfo(34, "Dante", "Filipino", "fil_PH", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/fil-PH/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Kamusta, ako si Dante."));
        list.add(new CoachInfo(35, "Dante", "Українська", "uk_UA", "https://assets-coaches.chess.com/image/coachdante.png",
                "https://text-and-audio.chess.com/prod/released/Dante_coach/uk-UA/",
                "24384ec55eff42b3f8a6db7c059740fdd8f47121947efd4f8cfadf92167bd6e0", "Привіт, я Данте."));

        // Standard Coach: Nadia (36..47)
        list.add(new CoachInfo(36, "Nadia", "English", "en_US", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/en-US/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Hello, I'm Coach Nadia."));
        list.add(new CoachInfo(37, "Nadia", "Français", "fr_FR", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/fr-FR/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Bonjour, je suis Nadia."));
        list.add(new CoachInfo(38, "Nadia", "Español", "es_ES", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/es-ES/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Hola, soy Nadia."));
        list.add(new CoachInfo(39, "Nadia", "Português", "pt_PT", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/pt-PT/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Olá, sou a Nadia."));
        list.add(new CoachInfo(40, "Nadia", "Deutsch", "de_DE", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/de-DE/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Guten Tag, ich bin Nadia."));
        list.add(new CoachInfo(41, "Nadia", "Italiano", "it_IT", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/it-IT/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Ciao, sono Nadia."));
        list.add(new CoachInfo(42, "Nadia", "Polski", "pl_PL", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/pl-PL/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Dzień dobry, jestem Nadia."));
        list.add(new CoachInfo(43, "Nadia", "Русский", "ru_RU", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/ru-RU/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Здравствуйте, я Надя."));
        list.add(new CoachInfo(44, "Nadia", "Türkçe", "tr_TR", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/tr-TR/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Merhaba, ben Nadia."));
        list.add(new CoachInfo(45, "Nadia", "Bahasa Indonesia", "id_ID", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/id-ID/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Halo, saya Nadia."));
        list.add(new CoachInfo(46, "Nadia", "Filipino", "fil_PH", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/fil-PH/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Kumusta, ako si Nadia."));
        list.add(new CoachInfo(47, "Nadia", "Українська", "uk_UA", "https://assets-coaches.chess.com/image/coachnadia.png",
                "https://text-and-audio.chess.com/prod/released/Nadia_coach/uk-UA/",
                "383d01808be1075cca567646909c86bbcc7ef3cd6501f30024923cfabb9c0fe2", "Привіт, я Надія."));

        // Celebrities / Grandmasters (48..57)
        list.add(new CoachInfo(48, "Levy Rozman", "English", "en_US", "https://assets-coaches.chess.com/image/coachlevy.png",
                "https://text-and-audio.chess.com/prod/released/Levy_coach/en-US/",
                "", "Hey everyone, Levy Rozman here!"));
        list.add(new CoachInfo(49, "Magnus Carlsen", "English", "en_US", "https://assets-coaches.chess.com/image/coachmagnus.png",
                "https://text-and-audio.chess.com/prod/released/Magnus_coach/en-US/",
                "", "Hey, I'm Magnus. Let's play some chess."));
        list.add(new CoachInfo(50, "Hikaru Nakamura", "English", "en_US", "https://assets-coaches.chess.com/image/coachhikaru.png",
                "https://text-and-audio.chess.com/prod/released/Hikaru_coach/en-US/",
                "", "What's up guys, Hikaru here."));
        list.add(new CoachInfo(51, "Anna Cramling", "English", "en_US", "https://assets-coaches.chess.com/image/coachanna.png",
                "https://text-and-audio.chess.com/prod/released/Anna_coach/en-US/",
                "", "Hi, I'm Anna Cramling! Let's explore this game."));
        list.add(new CoachInfo(52, "James Canty III", "English", "en_US", "https://assets-coaches.chess.com/image/coachcanty.png",
                "https://text-and-audio.chess.com/prod/released/Canty_coach/en-US/",
                "", "What's good everybody! James Canty here."));
        list.add(new CoachInfo(53, "Vishy Anand", "English", "en_US", "https://assets-coaches.chess.com/image/coachanand.png",
                "https://text-and-audio.chess.com/prod/released/Anand_coach/en-US/",
                "", "Hello, I'm Vishy Anand. Let's study the position."));
        list.add(new CoachInfo(54, "Tania Sachdev", "English", "en_US", "https://assets-coaches.chess.com/image/coachtania.png",
                "https://text-and-audio.chess.com/prod/released/Tania_coach/en-US/",
                "", "Hi, I'm Tania Sachdev. Ready for some chess?"));
        list.add(new CoachInfo(55, "Danny Rensch", "English", "en_US", "https://assets-coaches.chess.com/image/coachdanny.png",
                "https://text-and-audio.chess.com/prod/released/Danny_coach/en-US/",
                "", "Hey chess fans, Danny Rensch here!"));
        list.add(new CoachInfo(56, "Botez Sisters", "English", "en_US", "https://assets-coaches.chess.com/image/coachbotez.png",
                "https://text-and-audio.chess.com/prod/released/Botez_coach/en-US/",
                "", "Hey guys, Botez sisters here! Let's play."));
        list.add(new CoachInfo(57, "Ben Finegold", "English", "en_US", "https://assets-coaches.chess.com/image/coachfinegold.png",
                "https://text-and-audio.chess.com/prod/released/Ben_coach/en-US/",
                "", "Terrible. Very terrible. Let's see what you played."));

        return list;
    }
}
