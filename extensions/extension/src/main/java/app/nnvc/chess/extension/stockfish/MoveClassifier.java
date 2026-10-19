/*
 * Copyright 2026 NNVC
 * https://github.com/NNVC/NNVC-Patches
 */

package app.nnvc.chess.extension.stockfish;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MoveClassifier {
    private static final String TAG = "NNVC";

    private static final List<String> fenHistory = new ArrayList<>();
    private static final Map<String, Float> fenToEvalMap = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> fenToBestMovesMap = new ConcurrentHashMap<>();
    private static final Map<String, String> keyToFullFenMap = new ConcurrentHashMap<>();
    /** Line evaluations (MultiPV) for calculating gap between top lines. */
    private static final Map<String, float[]> fenToLineScoresMap = new ConcurrentHashMap<>();
    /** Principal variation lines from this position. */
    private static final Map<String, List<String>> fenToPvMap = new ConcurrentHashMap<>();
    /** Whether this position has a known mate in N. */
    private static final Map<String, Boolean> fenToMateMap = new ConcurrentHashMap<>();
    /** Search depth behind each stored evaluation, so a shallower result never overwrites a deeper one. */
    private static final Map<String, Integer> fenToDepthMap = new ConcurrentHashMap<>();
    /** Moves ("prevKey|currentKey" or "seqLen:uci") that already produced a toast, so a move is rated only once. */
    private static final java.util.Set<String> classifiedMoves = ConcurrentHashMap.newKeySet();

    /** Move and loss tracking across plies for recapture and missed win / miss detection. */
    private static volatile String lastPlayedUci = null;
    private static volatile boolean lastWasCapture = false;
    private static volatile float lastOppLoss = -1f;

    /** Hooked moves from Chess.com positionObject (StandardPosition) */
    private static volatile Object lastPositionObject = null;
    private static volatile List<String> currentPositionMoves = null;
    private static volatile String lastDeduceUciMove = null;

    public static List<String> getCurrentPositionMoves() {
        return currentPositionMoves != null ? new ArrayList<>(currentPositionMoves) : null;
    }

    public static String getLastDeduceUciMove() {
        return lastDeduceUciMove;
    }

    public static boolean isStandardStartposFirstMove(String move) {
        return move != null && STARTPOS_LEGAL_FIRST_MOVES.contains(move.trim().toLowerCase(java.util.Locale.US));
    }

    private static volatile Class<?> cachedMoveConverterClass = null;
    private static volatile Method cachedConvertHistoryMethod = null;
    private static volatile Method cachedConvertMoveMethod = null;
    private static volatile Method cachedItemBMethod = null;
    private static volatile boolean converterResolved = false;

    /** Minimum depth for an interrupted / intermediate search to be trusted for a rating. */
    private static final int MIN_RATING_DEPTH = 8;

    /** 20 legal first moves from startpos for White. Used to verify move list validity for Torch WASM. */
    private static final java.util.Set<String> STARTPOS_LEGAL_FIRST_MOVES = new java.util.HashSet<>(java.util.Arrays.asList(
        "a2a3", "a2a4", "b2b3", "b2b4", "c2c3", "c2c4", "d2d3", "d2d4",
        "e2e3", "e2e4", "f2f3", "f2f4", "g2g3", "g2g4", "h2h3", "h2h4",
        "b1a3", "b1c3", "g1f3", "g1h3"
    ));

    public static void clearHistory() {
        synchronized (fenHistory) {
            fenHistory.clear();
            clearMaps();
        }
        currentPositionMoves = null;
        lastPositionObject = null;
    }

    private static void resolveConverter(ClassLoader cl) {
        if (converterResolved) return;
        try {
            Class<?> clazz = cl.loadClass("com.chess.chessboard.compengine.MoveConverterKt");
            cachedMoveConverterClass = clazz;
            for (Method m : clazz.getMethods()) {
                if (m.getParameterCount() == 1 && m.getReturnType() == String.class) {
                    if ("c".equals(m.getName())) {
                        cachedConvertHistoryMethod = m;
                    } else if ("b".equals(m.getName())) {
                        cachedConvertMoveMethod = m;
                    }
                }
            }
        } catch (Throwable t) {
            TorchEngine.log("[EXTRACT MOVES] Không load được MoveConverterKt: " + t.getMessage());
        } finally {
            converterResolved = true;
        }
    }

    private static volatile Method cachedHMethod = null;
    private static volatile boolean hMethodResolved = false;

    public static List<String> extractMovesFromPosition(Object positionObject) {
        if (positionObject == null) return null;
        try {
            Method hMethod = cachedHMethod;
            if (!hMethodResolved) {
                try {
                    hMethod = positionObject.getClass().getMethod("h");
                } catch (NoSuchMethodException e) {
                    for (Method m : positionObject.getClass().getMethods()) {
                        if (m.getName().equals("h") && m.getParameterCount() == 0 && List.class.isAssignableFrom(m.getReturnType())) {
                            hMethod = m;
                            break;
                        }
                    }
                }
                cachedHMethod = hMethod;
                hMethodResolved = true;
            }
            if (hMethod == null) return null;

            Object listObj = hMethod.invoke(positionObject);
            if (!(listObj instanceof List)) return null;

            List<?> historyList = (List<?>) listObj;
            if (historyList.isEmpty()) {
                return new ArrayList<>();
            }

            resolveConverter(positionObject.getClass().getClassLoader());

            List<String> moves = new ArrayList<>();
            for (Object item : historyList) {
                if (item == null) continue;
                String uci = null;
                if (cachedConvertHistoryMethod != null) {
                    try {
                        Object res = cachedConvertHistoryMethod.invoke(null, item);
                        if (res instanceof String) {
                            uci = (String) res;
                        }
                    } catch (Throwable ignored) {}
                }
                if (uci == null && cachedConvertMoveMethod != null) {
                    try {
                        if (cachedItemBMethod == null) {
                            try {
                                cachedItemBMethod = item.getClass().getMethod("b");
                            } catch (Throwable ignored) {}
                        }
                        if (cachedItemBMethod != null) {
                            Object moveObj = cachedItemBMethod.invoke(item);
                            if (moveObj != null) {
                                Object res = cachedConvertMoveMethod.invoke(null, moveObj);
                                if (res instanceof String) {
                                    uci = (String) res;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }
                if (uci != null && !uci.trim().isEmpty()) {
                    moves.add(uci.trim().toLowerCase(java.util.Locale.US));
                }
            }
            return moves;
        } catch (Throwable t) {
            TorchEngine.log("[EXTRACT MOVES ERROR] " + t.getMessage());
            return null;
        }
    }

    private static void clearMaps() {
        fenToEvalMap.clear();
        fenToBestMovesMap.clear();
        keyToFullFenMap.clear();
        fenToDepthMap.clear();
        fenToLineScoresMap.clear();
        fenToPvMap.clear();
        fenToMateMap.clear();
        classifiedMoves.clear();
        lastPlayedUci = null;
        lastWasCapture = false;
        lastOppLoss = -1f;
        EloScanner.reset();
    }

    /**
     * Stores the evaluation of a position (final, intermediate or interrupted search). Keeps the
     * deepest one. Recording intermediate results is what makes the move toasts reliable: the
     * opponent (e.g. a bot) often replies before the search after our move has reached full
     * depth, and that search is then cancelled. Previously its result was discarded, so neither
     * our move nor the reply could be rated and no toast appeared.
     */
    public static void recordResult(String fen, StockfishProcess.AnalysisResult r) {
        if (r == null || r.moves.isEmpty()) return;
        String key = getFenKey(fen);
        if (key == null) return;
        Integer stored = fenToDepthMap.get(key);
        if (stored != null && stored > r.depth) return;
        fenToEvalMap.put(key, r.score);
        fenToBestMovesMap.put(key, new ArrayList<>(r.moves));
        fenToDepthMap.put(key, r.depth);
        if (r.lineScores != null && r.lineScores.length > 0) {
            fenToLineScoresMap.put(key, r.lineScores.clone());
        }
        if (r.pv != null && !r.pv.isEmpty()) {
            fenToPvMap.put(key, new ArrayList<>(r.pv));
        }
        fenToMateMap.put(key, r.hasMate && r.mateIn != 0);
    }

    /** True if an interrupted/intermediate result is deep enough to rate a move with. */
    public static boolean isUsableForRating(StockfishProcess.AnalysisResult r) {
        if (r == null) return false;
        if (r.terminal) return true;
        return !r.moves.isEmpty() && r.depth >= MIN_RATING_DEPTH;
    }

    public static List<String> getFenHistory() {
        return fenHistory;
    }

    public static List<String> getPlayedMoves() {
        List<String> list = new ArrayList<>();
        synchronized (fenHistory) {
            for (int i = 0; i < fenHistory.size() - 1; i++) {
                String uci = deduceUciMove(fenHistory.get(i), fenHistory.get(i + 1));
                if (uci != null) list.add(uci);
            }
        }
        return list;
    }

    public static Map<String, Float> getFenToEvalMap() {
        return fenToEvalMap;
    }

    public static Map<String, List<String>> getFenToBestMovesMap() {
        return fenToBestMovesMap;
    }

    public static void updateHistory(String fen, Object positionObject) {
        lastPositionObject = positionObject;
        if (positionObject != null) {
            List<String> moves = extractMovesFromPosition(positionObject);
            if (moves != null) {
                currentPositionMoves = moves;
                if (!moves.isEmpty()) {
                    TorchEngine.log("[POSITION HOOK] Đã trích xuất " + moves.size() + " nước từ positionObject: " + moves);
                } else {
                    TorchEngine.log("[POSITION HOOK] Bàn cờ ở vị trí ban đầu (0 nước đi).");
                }
            }
        }
        updateHistory(fen);
    }

    public static void updateHistory(String fen) {
        String key = getFenKey(fen);
        if (key == null) return;
        if (fen != null) keyToFullFenMap.put(key, fen);
        synchronized (fenHistory) {
            int idx = fenHistory.indexOf(key);
            if (idx >= 0) {
                boolean truncated = false;
                while (fenHistory.size() > idx + 1) {
                    fenHistory.remove(fenHistory.size() - 1);
                    truncated = true;
                }
                // Stepped back (take-back / navigation): allow the next move to be rated again.
                if (truncated) classifiedMoves.clear();
            } else {
                if (fenHistory.isEmpty()) {
                    String startPosKey = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w";
                    if (!startPosKey.equals(key)) {
                        String fromStart = deduceUciMove(startPosKey, key);
                        if (fromStart != null) {
                            fenHistory.add(startPosKey);
                        }
                    }
                    fenHistory.add(key);
                } else {
                    String lastKey = fenHistory.get(fenHistory.size() - 1);
                    String deduced = deduceUciMove(lastKey, key);
                    if (deduced == null) {
                        boolean isStartPos = key.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
                        if (isStartPos) {
                            fenHistory.clear();
                            clearMaps();
                            StockfishExtension.isReviewMode = false;
                            fenHistory.add(key);
                            return;
                        }

                        // Check if same turn (intermediate state, castling animation step, or FEN correction)
                        String[] lastParts = lastKey.split("\\s+");
                        String[] currParts = key.split("\\s+");
                        if (lastParts.length >= 2 && currParts.length >= 2 && lastParts[1].equals(currParts[1])) {
                            // Update the last position snapshot without clearing history or appending a duplicate turn
                            fenHistory.set(fenHistory.size() - 1, key);
                            TorchEngine.log("[CLASSIFIER SYNC] Intermediate board state updated: " + key);
                            return;
                        }

                        // Different turn but deduceUciMove failed (edge-case / en-passant sync):
                        // Preserve full prior move history! Do NOT clear history mid-game!
                        TorchEngine.log("[CLASSIFIER SYNC] Step transition preserved (deduceUciMove null): " + key);
                        fenHistory.add(key);
                    } else {
                        fenHistory.add(key);
                    }
                }
            }
        }
    }

    public static String getFenKey(String fen) {
        if (fen == null) return null;
        String[] parts = fen.split("\\s+");
        if (parts.length >= 2) {
            return parts[0] + " " + parts[1];
        }
        return fen;
    }

    private static String expandFenBoard(String fenBoard) {
        StringBuilder sb = new StringBuilder();
        for (char c : fenBoard.toCharArray()) {
            if (c == '/') continue;
            if (Character.isDigit(c)) {
                int emptySquares = c - '0';
                for (int i = 0; i < emptySquares; i++) {
                    sb.append('.');
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String getSquareName(int index) {
        char file = (char) ('a' + (index % 8));
        int rank = 8 - (index / 8);
        return "" + file + rank;
    }

    /** UCI promotion letter ("q", "n", ...) when a pawn turned into another piece, else "". */
    private static String promotionSuffix(char before, char after) {
        if (Character.toLowerCase(before) != 'p') return "";
        char a = Character.toLowerCase(after);
        return (a == 'q' || a == 'r' || a == 'b' || a == 'n') ? String.valueOf(a) : "";
    }

    public static String deduceUciMove(String prevFen, String currFen) {
        try {
            String[] prevParts = prevFen.split("\\s+");
            String[] currParts = currFen.split("\\s+");
            if (prevParts.length < 2 || currParts.length < 2) return null;

            String prevBoard = expandFenBoard(prevParts[0]);
            String currBoard = expandFenBoard(currParts[0]);
            if (prevBoard.length() != 64 || currBoard.length() != 64) return null;

            boolean whiteMoved = prevParts[1].equals("w");

            List<Integer> fromCandidates = new ArrayList<>();
            List<Integer> toCandidates = new ArrayList<>();

            for (int i = 0; i < 64; i++) {
                char p = prevBoard.charAt(i);
                char c = currBoard.charAt(i);
                if (p != c) {
                    if (p != '.') {
                        boolean isWhitePiece = Character.isUpperCase(p);
                        if (isWhitePiece == whiteMoved) {
                            fromCandidates.add(i);
                        }
                    }
                    if (c != '.') {
                        boolean isWhitePiece = Character.isUpperCase(c);
                        if (isWhitePiece == whiteMoved) {
                            toCandidates.add(i);
                        }
                    }
                }
            }

            if (fromCandidates.size() == 1 && toCandidates.size() == 1) {
                int f = fromCandidates.get(0), t = toCandidates.get(0);
                return getSquareName(f) + getSquareName(t) + promotionSuffix(prevBoard.charAt(f), currBoard.charAt(t));
            }

            if (fromCandidates.size() >= 1 && toCandidates.size() >= 1) {
                char kingChar = whiteMoved ? 'K' : 'k';
                int kingFrom = -1;
                int kingTo = -1;
                for (int f : fromCandidates) {
                    if (prevBoard.charAt(f) == kingChar) {
                        kingFrom = f;
                        break;
                    }
                }
                for (int t : toCandidates) {
                    if (currBoard.charAt(t) == kingChar) {
                        kingTo = t;
                        break;
                    }
                }
                if (kingFrom != -1 && kingTo != -1) {
                    return getSquareName(kingFrom) + getSquareName(kingTo);
                }

                if (toCandidates.size() == 1) {
                    int toIdx = toCandidates.get(0);
                    char movedPiece = currBoard.charAt(toIdx);
                    for (int f : fromCandidates) {
                        char prevPiece = prevBoard.charAt(f);
                        if (Character.toLowerCase(prevPiece) == Character.toLowerCase(movedPiece) ||
                            (Character.toLowerCase(prevPiece) == 'p' && (movedPiece == 'Q' || movedPiece == 'q' || movedPiece == 'R' || movedPiece == 'r' || movedPiece == 'B' || movedPiece == 'b' || movedPiece == 'N' || movedPiece == 'n'))) {
                            return getSquareName(f) + getSquareName(toIdx) + promotionSuffix(prevPiece, movedPiece);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static volatile android.os.Handler classifyHandler = null;
    private static Runnable pendingClassifyRunnable = null;

    private static android.os.Handler getClassifyHandler() {
        if (classifyHandler == null) {
            synchronized (MoveClassifier.class) {
                if (classifyHandler == null) {
                    try {
                        classifyHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                    } catch (Throwable ignored) {}
                }
            }
        }
        return classifyHandler;
    }

    public static void classifyMoveDebounced(final Context context, final String currentFen) {
        if (context == null || currentFen == null) return;
        if (!StockfishSettings.isMoveClassificationEnabled(context)) return;

        final List<String> snapshotMoves = (currentPositionMoves != null) ? new ArrayList<>(currentPositionMoves) : null;
        final Context appContext = context.getApplicationContext();

        android.os.Handler handler = getClassifyHandler();
        if (handler != null && pendingClassifyRunnable != null) {
            handler.removeCallbacks(pendingClassifyRunnable);
        }

        pendingClassifyRunnable = () -> {
            pendingClassifyRunnable = null;
            classifyMoveIfPossible(appContext, currentFen, null, snapshotMoves);
        };
        if (handler != null) {
            handler.postDelayed(pendingClassifyRunnable, 100);
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    public static void classifyMoveIfPossible(Context context, String currentFen) {
        classifyMoveIfPossible(context, currentFen, null, null);
    }

    @android.annotation.SuppressLint("MissingPermission")
    public static void classifyMoveIfPossible(Context context, String currentFen, StockfishProcess.AnalysisResult currentResult) {
        classifyMoveIfPossible(context, currentFen, currentResult, null);
    }

    @android.annotation.SuppressLint("MissingPermission")
    public static void classifyMoveIfPossible(Context context, String currentFen, StockfishProcess.AnalysisResult currentResult, List<String> explicitMoves) {
        StockfishExtension.silenceIllegalMoveSound();
        if (context == null) return;
        
        Activity activity = StockfishExtension.getCurrentActivity();
        if (activity != null && StockfishExtension.isLiveMatch(activity) && !StockfishExtension.isReviewMode) {
            return;
        }

        if (!StockfishSettings.isMoveClassificationEnabled(context)) return;
        if (currentFen != null && currentFen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR") &&
                (explicitMoves == null || explicitMoves.isEmpty()) &&
                (currentPositionMoves == null || currentPositionMoves.isEmpty())) {
            return;
        }

        try {
            List<String> hookMoves = (explicitMoves != null && !explicitMoves.isEmpty()) ? explicitMoves : currentPositionMoves;
            String currentKey = getFenKey(currentFen);
            if (currentKey == null && (hookMoves == null || hookMoves.isEmpty())) return;
            if (currentFen != null && currentKey != null) {
                keyToFullFenMap.put(currentKey, currentFen);
            }

            String uciMove = null;
            boolean whiteMoved = false;
            String moveIdentifier = null;
            List<String> moves = null;

            if (hookMoves != null && !hookMoves.isEmpty()) {
                // Ưu tiên 100%: Dùng danh sách nước đi đầy đủ được hook trực tiếp từ positionObject
                moves = new ArrayList<>(hookMoves);
                uciMove = moves.get(moves.size() - 1);
                whiteMoved = (moves.size() % 2 == 1);
                moveIdentifier = moves.size() + ":" + uciMove;
                if (classifiedMoves.contains(moveIdentifier)) return;
            } else {
                // Fallback: dựa vào FEN transition nếu không hook được positionObject
                if (currentKey == null) return;
                String prevKey = null;
                synchronized (fenHistory) {
                    int idx = fenHistory.indexOf(currentKey);
                    if (idx >= 1) {
                        prevKey = fenHistory.get(idx - 1);
                    }
                }
                if (prevKey == null) return;
                final String transition = prevKey + "|" + currentKey;
                if (classifiedMoves.contains(transition)) return;

                whiteMoved = prevKey.endsWith(" w");
                uciMove = deduceUciMove(prevKey, currentKey);
                if (uciMove == null) {
                    TorchEngine.log("[CLASSIFIER DEDUCE NULL] prev=" + prevKey + ", curr=" + currentKey);
                    return;
                }
                lastDeduceUciMove = uciMove;
                moveIdentifier = transition;
                moves = getPlayedMoves();
                if (moves.isEmpty()) {
                    moves.add(uciMove);
                } else if (!moves.get(moves.size() - 1).equals(uciMove)) {
                    moves.add(uciMove);
                }
            }

            final Activity currentAct = activity;
            TorchEngine.log("[CLASSIFIER TRIGGER] Move=" + uciMove + ", whiteMoved=" + whiteMoved + ", totalMoves=" + (moves != null ? moves.size() : 0) + ", torchReady=" + TorchEngine.getInstance(context).isReady());

            // ── 1. 100% Real Torch WebAssembly Engine Execution ──
            TorchEngine torch = TorchEngine.getInstance(context);
            if (torch.isReady()) {
                Boolean userIsWhite = StockfishExtension.isUserWhite(StockfishExtension.getStateImpl());
                final boolean isMyMove;
                if (userIsWhite != null) {
                    isMyMove = (whiteMoved == userIsWhite.booleanValue());
                } else {
                    isMyMove = (whiteMoved == !OverlayManager.isBoardFlipped(StockfishExtension.getStateImpl()));
                }

                String userColor = (userIsWhite != null && !userIsWhite) ? "black" : "white";
                final String finalUci = uciMove;
                classifiedMoves.add(moveIdentifier);

                // Kiểm tra tính hợp lệ của chuỗi nước đi tính từ bàn cờ ban đầu (startpos)
                String firstMove = moves.get(0);
                if (!STARTPOS_LEGAL_FIRST_MOVES.contains(firstMove)) {
                    TorchEngine.log("[TORCH CLASSIFIER BLOCKED] Nước đầu tiên '" + firstMove + "' không thể đi từ startpos (Vào lại giữa ván và không có lịch sử). Chặn gửi lệnh để bảo vệ WebAssembly khỏi Abort.");
                    return;
                }

                final boolean finalWhiteMoved = whiteMoved;
                int coachDepth = StockfishSettings.getCoachDepth(context);
                final boolean finalIsMyMove = isMyMove;
                final boolean finalUserIsWhite = (userIsWhite != null) ? userIsWhite.booleanValue() : true;
                torch.analyze(moves, userColor, coachDepth, (classificationName, playedMoveLan, bestMoveLan, speechText, audioUrlHash, rawJson) -> {
                    TorchEngine.log("[CLASSIFIER CALLBACK] class=" + classificationName + ", act=" + (currentAct != null) + ", isMyMove=" + finalIsMyMove + ", audioHash=" + audioUrlHash);
                    
                    // Kích hoạt giọng nói Huấn Luyện Viên chính hãng (nếu có audioUrlHash)
                    if (currentAct != null && audioUrlHash != null && !audioUrlHash.isEmpty()) {
                        CoachVoiceManager.getInstance(currentAct).speakMove(audioUrlHash, speechText, currentAct);
                    }

                    // Trích xuất Accuracy (%) và Estimated Elo từ đối tượng JSON của Torch (chuẩn Extension)
                    if (rawJson != null && currentAct != null) {
                        try {
                            org.json.JSONObject root = new org.json.JSONObject(rawJson);
                            float whiteAcc = -1f;
                            float blackAcc = -1f;
                            int whiteElo = -1;
                            int blackElo = -1;

                            org.json.JSONObject caps = root.optJSONObject("CAPS");
                            if (caps == null) caps = root.optJSONObject("caps");
                            if (caps != null) {
                                org.json.JSONObject wCaps = caps.optJSONObject("white");
                                if (wCaps != null) whiteAcc = (float) wCaps.optDouble("all", -1.0);
                                org.json.JSONObject bCaps = caps.optJSONObject("black");
                                if (bCaps != null) blackAcc = (float) bCaps.optDouble("all", -1.0);
                            }

                            org.json.JSONObject reportCard = root.optJSONObject("reportCard");
                            if (reportCard == null) reportCard = root.optJSONObject("report_card");
                            if (reportCard == null) reportCard = root.optJSONObject("gameReview");
                            if (reportCard != null) {
                                org.json.JSONObject wRep = reportCard.optJSONObject("white");
                                if (wRep != null) {
                                    whiteElo = wRep.optInt("effectiveElo", wRep.optInt("estimatedElo", -1));
                                    if (whiteAcc < 0) whiteAcc = (float) wRep.optDouble("accuracy", -1.0);
                                }
                                org.json.JSONObject bRep = reportCard.optJSONObject("black");
                                if (bRep != null) {
                                    blackElo = bRep.optInt("effectiveElo", bRep.optInt("estimatedElo", -1));
                                    if (blackAcc < 0) blackAcc = (float) bRep.optDouble("accuracy", -1.0);
                                }
                            }

                            if (whiteAcc >= 0 || blackAcc >= 0 || whiteElo > 0 || blackElo > 0) {
                                final float fWhiteAcc = whiteAcc;
                                final float fBlackAcc = blackAcc;
                                final int fWhiteElo = whiteElo;
                                final int fBlackElo = blackElo;
                                currentAct.runOnUiThread(() -> {
                                    OverlayManager.updateAccuracyEloPills(fWhiteAcc, fBlackAcc, fWhiteElo, fBlackElo, finalUserIsWhite);
                                });
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Lỗi trích xuất Accuracy/Elo từ Torch: " + t.getMessage());
                        }
                    }

                    if (classificationName != null && !classificationName.isEmpty() && !"null".equalsIgnoreCase(classificationName)) {
                        if (currentAct != null) {
                            currentAct.runOnUiThread(() -> {
                                displayTorchClassification(currentAct, classificationName,
                                        finalUci,
                                        speechText,
                                        finalWhiteMoved,
                                        finalIsMyMove);
                            });
                        }
                    } else {
                        // 100% phụ thuộc vào Torch: Không chuyển sang Stockfish, chỉ ghi log
                        TorchEngine.log("[TORCH CLASSIFIER ERROR] Không thể phân loại: Torch CEE trả về null (Thiếu chuỗi startpos hoặc lỗi WebAssembly)");
                    }
                });
                return;
            }

            // Torch Engine chưa sẵn sàng: Ghi log, không hiện toast làm phiền người dùng
            TorchEngine.log("[TORCH CLASSIFIER ERROR] Torch Engine WASM chưa sẵn sàng!");
        } catch (Throwable t) {
            Log.e(TAG, "Error in classifyMoveIfPossible: " + t.getMessage());
        }
    }

    private static void displayTorchClassification(Activity activity, String torchName, String uciMove, String speechText, boolean isWhite, boolean isMyMove) {
        if (activity == null || torchName == null) return;
        if (activity.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed()) return;
        boolean isVi = "vi".equalsIgnoreCase(StockfishSettings.getLanguage(activity));
        String classification;
        String emoji;
        boolean isBlunderOrMistake = false;

        String lower = torchName.toLowerCase(java.util.Locale.US).replace(" ", "_");
        switch (lower) {
            case "brilliant":
                classification = isVi ? "Nước cờ thiên tài (Brilliant)" : "Brilliant Move";
                emoji = "!!";
                break;
            case "great":
            case "greatfind":
            case "great_find":
                classification = isVi ? "Nước cờ xuất sắc (Great)" : "Great Move";
                emoji = "!";
                break;
            case "best":
                classification = isVi ? "Nước cờ tốt nhất (Best)" : "Best Move";
                emoji = "★";
                break;
            case "excellent":
                classification = isVi ? "Nước cờ tuyệt vời (Excellent)" : "Excellent";
                emoji = "👍";
                break;
            case "good":
                classification = isVi ? "Nước cờ hay (Good)" : "Good Move";
                emoji = "✓";
                break;
            case "book":
                classification = isVi ? "Nước cờ khai cuộc (Book)" : "Book Move";
                emoji = "📖";
                break;
            case "inaccuracy":
                classification = isVi ? "Thiếu chính xác (Inaccuracy)" : "Inaccuracy";
                emoji = "?!";
                break;
            case "mistake":
                classification = isVi ? "Sai lầm (Mistake)" : "Mistake";
                emoji = "?";
                isBlunderOrMistake = true;
                break;
            case "blunder":
                classification = isVi ? "Sai lầm nghiêm trọng (Blunder)" : "Blunder";
                emoji = "??";
                isBlunderOrMistake = true;
                break;
            case "miss":
                classification = isVi ? "Bỏ lỡ cơ hội (Miss)" : "Miss";
                emoji = "✕";
                isBlunderOrMistake = true;
                break;
            case "missed":
            case "missed_win":
            case "missedwin":
                classification = isVi ? "Bỏ lỡ cơ hội thắng (Missed Win)" : "Missed Win";
                emoji = "✕";
                isBlunderOrMistake = true;
                break;
            case "forced":
                classification = isVi ? "Nước bắt buộc (Forced)" : "Forced Move";
                emoji = "➔";
                break;
            default:
                classification = isVi ? "Nước cờ hay (Good)" : "Good Move";
                emoji = "✓";
                break;
        }

        String comment = (speechText != null && !speechText.trim().isEmpty()) ? "\n\"" + speechText.trim() + "\"" : "";
        String playerPrefix = isMyMove ? (isVi ? "[Bạn]" : "[You]") : (isVi ? "[Đối thủ]" : "[Opponent]");
        TorchEngine.log("[CLASSIFIED] " + playerPrefix + " " + emoji + " " + classification + " (" + uciMove + ")" + (comment.isEmpty() ? "" : " " + comment));

        // 1. Hiển thị huy hiệu đồ họa Chess.com chính hãng trên ô cờ vừa đi kèm hiệu ứng tô màu ô cờ
        String fromSquare = null;
        String targetSquare = null;
        if (uciMove != null && uciMove.length() >= 4) {
            fromSquare = uciMove.substring(0, 2);
            targetSquare = uciMove.substring(2, 4);
            OverlayManager.setClassificationBadge(fromSquare, targetSquare, torchName, isWhite, isMyMove);
        }

        // 2. Phát âm thanh Chess.com chính hãng theo phân loại nước cờ (Chỉ giữ lại Brilliant)
        String lowerName = torchName.toLowerCase(java.util.Locale.US).replace(" ", "_");
        if ("brilliant".equals(lowerName)) {
            SoundManager.getInstance(activity).playBrilliantSound();
        }
    }
}


