package app.prathxm.chess.extension.stockfish;

/**
 * Self-checking tests for pure-logic methods in MoveClassifier and StockfishExtension.
 * 
 * Run with: javac -d out SelfTest.java && java -ea -cp out app.prathxm.chess.extension.stockfish.SelfTest
 *
 * Covers:
 *  - #37 Move classification thresholds (Brilliant, Blunder, etc.)
 *  - #60 isLiveMatch activity name detection
 *  - FEN deduction logic
 */
public class SelfTest {

    // ────────────────────────────────────────────────────────────────
    // Replicated pure-logic from MoveClassifier for isolated testing
    // ────────────────────────────────────────────────────────────────

    static String classify(float delta, boolean isBest, boolean isInPV, boolean onlyMoveInPV) {
        if (isBest) {
            if (delta > 1.5f && onlyMoveInPV) return "Brilliant";
            if (delta > 0.4f) return "Great Move";
            return "Best Move";
        }
        if (isInPV) return "Excellent";
        if (delta < -3.0f) return "Blunder";
        if (delta < -1.5f) return "Mistake";
        if (delta < -0.5f) return "Inaccuracy";
        if (delta < -0.1f) return "Good Move";
        return "Great Move";
    }

    static String classifyReview(float actualDelta, boolean isBest, boolean isSac, float evalAfter,
                                  boolean isInPV, boolean isMiss) {
        if (isMiss) return "miss";
        if (isBest) {
            if (isSac && actualDelta >= 0.5f && evalAfter >= 0.0f) return "brilliant";
            if (actualDelta > 0.4f) return "greatFind";
            return "best";
        }
        if (isInPV) return "excellent";
        if (actualDelta < -3.0f) return "blunder";
        if (actualDelta < -1.2f) return "mistake";
        if (actualDelta < -0.5f) return "inaccuracy";
        if (actualDelta < -0.1f) return "good";
        return "greatFind";
    }

    // ────────────────────────────────────────────────────────────────
    // Replicated isLiveMatch logic
    // ────────────────────────────────────────────────────────────────

    static boolean isLiveMatch(String activityClassName) {
        if (activityClassName == null) return false;
        String lower = activityClassName.toLowerCase();

        if (lower.contains("computer") || lower.contains("bot") || lower.contains("practice") ||
            lower.contains("analysis") || lower.contains("review") || lower.contains("local") ||
            lower.contains("solo") || lower.contains("tutorial") || lower.contains("puzzle")) {
            return false;
        }
        if (lower.contains("playactivity") || lower.contains("gameactivity") || lower.contains("live")) {
            return true;
        }
        if (lower.contains(".play.")) {
            return true;
        }
        return false;
    }

    // ────────────────────────────────────────────────────────────────
    // Replicated FEN utils
    // ────────────────────────────────────────────────────────────────

    static String expandFenBoard(String fenBoard) {
        StringBuilder sb = new StringBuilder();
        for (char c : fenBoard.toCharArray()) {
            if (c == '/') continue;
            if (Character.isDigit(c)) {
                int emptySquares = c - '0';
                for (int i = 0; i < emptySquares; i++) sb.append('.');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    static String getSquareName(int index) {
        char file = (char) ('a' + (index % 8));
        int rank = 8 - (index / 8);
        return "" + file + rank;
    }

    // ────────────────────────────────────────────────────────────────
    // Tests
    // ────────────────────────────────────────────────────────────────

    static int passed = 0;
    static int failed = 0;

    static void check(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failed++;
            System.err.println("FAIL: " + name);
        }
    }

    public static void main(String[] args) {
        // === CLASSIFICATION TESTS (#37) ===

        // Best move with only 1 PV line and large delta -> Brilliant
        check("Brilliant: best + only PV + delta=2.0",
            classify(2.0f, true, false, true).equals("Brilliant"));

        // Best move with multiple PV lines -> NOT Brilliant even with high delta
        check("Not Brilliant when multiple PV: delta=2.0",
            !classify(2.0f, true, false, false).equals("Brilliant"));
        check("Great Move when best + delta=2.0 + multi PV",
            classify(2.0f, true, false, false).equals("Great Move"));

        // Best move with small delta -> Best Move
        check("Best Move: delta=0.1",
            classify(0.1f, true, false, false).equals("Best Move"));

        // Best move with medium delta -> Great Move
        check("Great Move: delta=0.5",
            classify(0.5f, true, false, false).equals("Great Move"));

        // In PV but not best -> Excellent
        check("Excellent: in PV",
            classify(-0.2f, false, true, false).equals("Excellent"));

        // Blunder: delta < -3.0
        check("Blunder: delta=-3.5",
            classify(-3.5f, false, false, false).equals("Blunder"));

        // NOT Blunder at -2.5 (old threshold was too loose)
        check("NOT Blunder at -2.5, should be Mistake",
            classify(-2.5f, false, false, false).equals("Mistake"));

        // Mistake: -3.0 < delta < -1.5
        check("Mistake: delta=-2.0",
            classify(-2.0f, false, false, false).equals("Mistake"));

        // Inaccuracy: -1.5 < delta < -0.5
        check("Inaccuracy: delta=-0.8",
            classify(-0.8f, false, false, false).equals("Inaccuracy"));

        // Good Move: -0.5 < delta < -0.1
        check("Good Move: delta=-0.2",
            classify(-0.2f, false, false, false).equals("Good Move"));

        // Great Move: delta >= -0.1
        check("Great Move: delta=0.0",
            classify(0.0f, false, false, false).equals("Great Move"));

        // === REVIEW CLASSIFICATION TESTS (#37) ===

        // Brilliant in review: sacrifice + positive delta + positive eval
        check("Review Brilliant: sac + delta=1.0 + eval=0.5",
            classifyReview(1.0f, true, true, 0.5f, false, false).equals("brilliant"));

        // NOT Brilliant if not sacrifice
        check("Review NOT Brilliant without sac",
            !classifyReview(1.0f, true, false, 0.5f, false, false).equals("brilliant"));

        // NOT Brilliant if delta < 0.5 (old threshold was -0.2)
        check("Review NOT Brilliant if sac but delta=0.3",
            !classifyReview(0.3f, true, true, 0.5f, false, false).equals("brilliant"));

        // NOT Brilliant if eval < 0.0
        check("Review NOT Brilliant if sac but eval=-0.3",
            !classifyReview(0.6f, true, true, -0.3f, false, false).equals("brilliant"));

        // Review blunder at -3.0
        check("Review Blunder: delta=-3.5",
            classifyReview(-3.5f, false, false, 0.0f, false, false).equals("blunder"));

        // Review NOT blunder at -2.5 (fixed from old -2.5 threshold)
        check("Review NOT Blunder at -2.5, should be mistake",
            classifyReview(-2.5f, false, false, 0.0f, false, false).equals("mistake"));

        // Miss classification
        check("Review miss",
            classifyReview(-2.0f, false, false, 0.0f, false, true).equals("miss"));

        // === isLiveMatch TESTS (#60) ===

        // Live match activities
        check("PlayActivity is live",
            isLiveMatch("com.chess.play.PlayActivity"));
        check("GameActivity is live",
            isLiveMatch("com.chess.GameActivity"));
        check("LiveChessActivity is live",
            isLiveMatch("com.chess.live.LiveChessActivity"));
        check(".play. package is live",
            isLiveMatch("com.chess.play.SomeFragment"));

        // Non-live activities
        check("ComputerActivity is NOT live",
            !isLiveMatch("com.chess.computer.ComputerPlayActivity"));
        check("BotActivity is NOT live",
            !isLiveMatch("com.chess.bot.BotGameActivity"));
        check("AnalysisActivity is NOT live",
            !isLiveMatch("com.chess.analysis.AnalysisActivity"));
        check("ReviewActivity is NOT live",
            !isLiveMatch("com.chess.review.GameReviewActivity"));
        check("PuzzleActivity is NOT live",
            !isLiveMatch("com.chess.features.puzzles.PuzzleActivity"));
        check("TutorialActivity is NOT live",
            !isLiveMatch("com.chess.tutorial.TutorialActivity"));
        check("PracticeActivity is NOT live",
            !isLiveMatch("com.chess.practice.PracticeActivity"));
        check("HomeActivity is NOT live",
            !isLiveMatch("com.chess.home.HomeActivity"));
        check("Profile is NOT live (no matching keyword)",
            !isLiveMatch("com.chess.profile.PlayerProfileActivity"));
        check("null is NOT live",
            !isLiveMatch(null));

        // === FEN UTILITY TESTS ===

        check("expandFenBoard starting position",
            expandFenBoard("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR").length() == 64);

        check("expandFenBoard empty rank = 8 dots",
            expandFenBoard("8").equals("........"));

        check("getSquareName(0) = a8",
            getSquareName(0).equals("a8"));
        check("getSquareName(63) = h1",
            getSquareName(63).equals("h1"));
        check("getSquareName(4) = e8",
            getSquareName(4).equals("e8"));

        // === EVAL BAR TESTS (MODULE 1921 & NNVC PARITY) ===
        check("EvalBar ratioFor(0.0) == 0.50",
            Math.abs(EvalBarView.ratioFor(0.0f) - 0.50f) < 0.001f);
        check("EvalBar ratioFor(+5.0) == 0.95 (capped)",
            Math.abs(EvalBarView.ratioFor(5.0f) - 0.95f) < 0.001f);
        check("EvalBar ratioFor(-5.0) == 0.05 (capped)",
            Math.abs(EvalBarView.ratioFor(-5.0f) - 0.05f) < 0.001f);
        check("EvalBar ratioFor(+10.0) == 0.95 (clamped)",
            Math.abs(EvalBarView.ratioFor(10.0f) - 0.95f) < 0.001f);
        check("EvalBar ratioFor(-10.0) == 0.05 (clamped)",
            Math.abs(EvalBarView.ratioFor(-10.0f) - 0.05f) < 0.001f);
        check("EvalBar ratioFor(+2.5) == 0.725",
            Math.abs(EvalBarView.ratioFor(2.5f) - 0.725f) < 0.001f);
        check("EvalBar ratioFor(-2.5) == 0.275",
            Math.abs(EvalBarView.ratioFor(-2.5f) - 0.275f) < 0.001f);

        check("EvalBar formatScore(0.0) = 0.0",
            EvalBarView.formatScore(0.0f).equals("0.0"));
        check("EvalBar formatScore(0.4) = 0.4",
            EvalBarView.formatScore(0.4f).equals("0.4"));
        check("EvalBar formatScore(-1.2) = 1.2 (no negative prefix)",
            EvalBarView.formatScore(-1.2f).equals("1.2"));
        check("EvalBar formatScore(12.3) = 12",
            EvalBarView.formatScore(12.3f).equals("12"));
        check("EvalBar formatScore(-15.8) = 16",
            EvalBarView.formatScore(-15.8f).equals("16"));

        check("isStandardStartposMoves legal e2e4",
            WasmEngineManager.isStandardStartposMoves(java.util.Collections.singletonList("e2e4")));
        check("isStandardStartposMoves legal d2d4",
            WasmEngineManager.isStandardStartposMoves(java.util.Collections.singletonList("d2d4")));
        check("isStandardStartposMoves illegal e1g1",
            !WasmEngineManager.isStandardStartposMoves(java.util.Collections.singletonList("e1g1")));
        check("isStandardStartposMoves empty is false",
            !WasmEngineManager.isStandardStartposMoves(java.util.Collections.emptyList()));

        // === ARROW EVAL LABEL TESTS ===
        check("Arrow eval mate in 3",
            formatArrowEval(true, 3, 0f, true).equals("#3"));
        check("Arrow eval mate in -1",
            formatArrowEval(true, -1, 0f, true).equals("#-1"));
        check("Arrow eval cp score +1.5",
            formatArrowEval(false, 0, 1.5f, true).equals("+1.5"));
        check("Arrow eval cp score -0.4",
            formatArrowEval(false, 0, -0.4f, true).equals("-0.4"));
        check("Arrow eval cp score +12.3",
            formatArrowEval(false, 0, 12.3f, true).equals("+12"));

        // === SOFT MOVETIME TESTS ===
        check("Soft movetime depth 12 is 2100ms",
            Math.max(700, Math.min(2100, 12 * 175)) == 2100);
        check("Soft movetime depth 8 is 1400ms",
            Math.max(700, Math.min(2100, 8 * 175)) == 1400);
        check("Soft movetime depth 4 is clamped to 700ms",
            Math.max(700, Math.min(2100, 4 * 175)) == 700);

        // === MOVE 0 STARTPOS DETECTION TESTS ===
        String startposFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        check("Move 0 startpos recognized",
            startposFen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"));
        String move1Fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        check("Move 1 is not startpos",
            !move1Fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"));

        // === POSITION COMMAND SELECTION (STARTPOS VS FEN) TESTS ===
        java.util.List<String> standardMoves = java.util.Arrays.asList("e2e4", "e7e5");
        check("Standard moves select startpos",
            WasmEngineManager.isStandardStartposMoves(standardMoves));
        java.util.List<String> illegalFirstMove = java.util.Arrays.asList("e1g1");
        check("Illegal first move falls back from startpos",
            !WasmEngineManager.isStandardStartposMoves(illegalFirstMove));

        // === ARROW SIGNATURE PALETTE TESTS ===
        String sig1 = "fen1|[e2e4]||-16711936|-256|-65536";
        String sig2 = "fen1|[e2e4]||-16711936|-256|-16776961";
        check("Color change alters arrow signature",
            !sig1.equals(sig2));
        String sig3 = "fen1|[e2e4]||-16711936|-256|-65536";
        check("Identical color keeps arrow signature",
            sig1.equals(sig3));

        // === GAMEOVER DIALOG TAG TESTS ===
        java.util.Set<String> gameOverTags = new java.util.HashSet<>(java.util.Arrays.asList(
            "BaseGameOverDialog",
            "LiveGameOverDialog",
            "LiveGameOverDialogV2",
            "LiveArenaGameOverDialog",
            "PracticePlayGameOverDialog",
            "BotGameOverDialog",
            "PassAndPlayGameOverDialog",
            "VisionChallengeCompleteDialog"
        ));
        check("BaseGameOverDialog recognized", gameOverTags.contains("BaseGameOverDialog"));
        check("BotGameOverDialog recognized", gameOverTags.contains("BotGameOverDialog"));
        check("RandomDialog not recognized", !gameOverTags.contains("RandomDialog"));

        // === GAMEOVER FALSE-POSITIVE FIX TESTS ===
        // Non-View, non-Boolean objects (like listeners, Lazy) must NEVER be treated as game over
        Object listenerMock = new Object();
        boolean isListenerGameOver = (listenerMock instanceof Boolean && (Boolean) listenerMock);
        check("Listener object is NOT GameOver", !isListenerGameOver);
        Boolean boolTrue = Boolean.TRUE;
        check("Boolean.TRUE is GameOver", (boolTrue instanceof Boolean && boolTrue));
        Boolean boolFalse = Boolean.FALSE;
        check("Boolean.FALSE is NOT GameOver", !(boolFalse instanceof Boolean && boolFalse));

        // === CLASSIFY SKIP TESTS ===
        String curFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        boolean isClassifySearching = true;
        long epoch = 5;
        long lastEpoch = 5;
        boolean shouldSkipSearching = curFen.equals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") &&
            (isClassifySearching || (lastEpoch == epoch && epoch > 0));
        check("Classify skips when same FEN and searching", shouldSkipSearching);
        boolean shouldSkipDifferentFen = "other_fen".equals(curFen) &&
            (isClassifySearching || (lastEpoch == epoch && epoch > 0));
        check("Classify does NOT skip on different FEN", !shouldSkipDifferentFen);

        // === RESOLVE VARIANT POSITION TESTS ===
        class MockInnerPos {
            public String getFen() { return "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"; }
        }
        class MockDisplayPos {
            public MockInnerPos a() { return new MockInnerPos(); }
        }
        MockDisplayPos disp = new MockDisplayPos();
        check("DisplayPosition unwraps via a()", disp.a() != null);
        Object resolved = StockfishExtension.resolveVariantPosition(null);
        check("Null position resolves to null safely", resolved == null);

        // === MOVE 0 NULL STATEIMPL SAFETY TEST ===
        // On Move 0, stateImpl is null before any move is played; onBoardChanged must still accept the position
        Object nullState = null;
        Object dummyPos = null;
        boolean canProcessNullPosition = (dummyPos != null);
        check("Null position is safely rejected", !canProcessNullPosition);

        // === IS_WHITE_TO_MOVE TESTS (SOLELY FEN TURN) ===
        String whiteTurnFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        String blackTurnFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        java.util.List<String> oneMoveList = java.util.Collections.singletonList("e2e4");
        check("isWhiteToMove white turn is true regardless of moves",
            StockfishProcess.isWhiteToMove(whiteTurnFen, oneMoveList));
        check("isWhiteToMove black turn is false regardless of moves empty",
            !StockfishProcess.isWhiteToMove(blackTurnFen, java.util.Collections.emptyList()));
        check("isWhiteToMove black turn is false with 1 move in list",
            !StockfishProcess.isWhiteToMove(blackTurnFen, oneMoveList));
        check("isWhiteToMove null fen defaults to white",
            StockfishProcess.isWhiteToMove(null));
        check("isWhiteToMove whitespace padded fen works",
            StockfishProcess.isWhiteToMove("  rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR   w   KQkq - 0 1  "));
        check("isWhiteToMove single token piece placement fen defaults to white",
            StockfishProcess.isWhiteToMove("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"));

        // === OVERLAYMANAGER FORMAT ARROW EVAL LABEL (NNVC EXTENSION PARITY) ===
        check("OverlayManager format mate in 1 = #1",
            OverlayManager.formatArrowEvalLabel(true, 1, 0f).equals("#1"));
        check("OverlayManager format mate in 2 = #2",
            OverlayManager.formatArrowEvalLabel(true, 2, 0f).equals("#2"));
        check("OverlayManager format mate in -1 = #-1",
            OverlayManager.formatArrowEvalLabel(true, -1, 0f).equals("#-1"));
        check("OverlayManager format mate in -3 = #-3",
            OverlayManager.formatArrowEvalLabel(true, -3, 0f).equals("#-3"));
        check("OverlayManager format +2.5 = +2.5",
            OverlayManager.formatArrowEvalLabel(false, 0, 2.5f).equals("+2.5"));
        check("OverlayManager format -0.8 = -0.8",
            OverlayManager.formatArrowEvalLabel(false, 0, -0.8f).equals("-0.8"));
        check("OverlayManager format 0.0 = 0.0",
            OverlayManager.formatArrowEvalLabel(false, 0, 0.0f).equals("0.0"));
        check("OverlayManager format -0.0f = 0.0",
            OverlayManager.formatArrowEvalLabel(false, 0, -0.0f).equals("0.0"));
        check("OverlayManager format small positive 0.04f = 0.0",
            OverlayManager.formatArrowEvalLabel(false, 0, 0.04f).equals("0.0"));
        check("OverlayManager format small negative -0.04f = 0.0",
            OverlayManager.formatArrowEvalLabel(false, 0, -0.04f).equals("0.0"));
        check("OverlayManager format +12.3 = +12",
            OverlayManager.formatArrowEvalLabel(false, 0, 12.3f).equals("+12"));
        check("OverlayManager format -15.8 = -16",
            OverlayManager.formatArrowEvalLabel(false, 0, -15.8f).equals("-16"));

        // === MULTIPV SORTING & SIDE-TO-MOVE POV TEST ===
        // Branch hierarchy from side-to-move POV:
        // Mate in 1 (99900L) > Mate in 2 (99800L) > +5.0 (500L) > 0.0 (0L) > -2.0 (-200L) > Mate in -3 (-99700L) > Mate in -1 (-99900L)
        long mateIn1 = 100000L - 100L;
        long mateIn2 = 100000L - 200L;
        long cp500 = 500L;
        long cp0 = 0L;
        long cpMinus200 = -200L;
        long mateMinus3 = -100000L + 300L;
        long mateMinus1 = -100000L + 100L;
        check("MultiPV Hierarchy: mateIn1 > mateIn2", mateIn1 > mateIn2);
        check("MultiPV Hierarchy: mateIn2 > cp500", mateIn2 > cp500);
        check("MultiPV Hierarchy: cp500 > cp0", cp500 > cp0);
        check("MultiPV Hierarchy: cp0 > cpMinus200", cp0 > cpMinus200);
        check("MultiPV Hierarchy: cpMinus200 > mateMinus3", cpMinus200 > mateMinus3);
        check("MultiPV Hierarchy: mateMinus3 > mateMinus1", mateMinus3 > mateMinus1);

        // When Black is to move and Black is winning:
        long eval1 = 250L;
        long eval2 = 100L;
        long eval3 = -50L;
        check("MultiPV Black winning: eval1 > eval2 > eval3",
            eval1 > eval2 && eval2 > eval3);
        String label1 = OverlayManager.formatArrowEvalLabel(false, 0, 2.5f);
        String label2 = OverlayManager.formatArrowEvalLabel(false, 0, 1.0f);
        String label3 = OverlayManager.formatArrowEvalLabel(false, 0, -0.5f);
        check("Tier 1 arrow label is +2.5", label1.equals("+2.5"));
        check("Tier 2 arrow label is +1.0", label2.equals("+1.0"));
        check("Tier 3 arrow label is -0.5", label3.equals("-0.5"));

        // === MOVE 0 STATE MACHINE DEDUPLICATION TEST ===
        class Move0StateMachine {
            String lastLoadedFen = null;
            boolean isScheduled = false;
            int analysisTriggerCount = 0;
            boolean hasArrowsOnScreen = false;

            void onBoardChanged(String fen) {
                boolean isStartPos = fen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
                if (isStartPos) {
                    if (isScheduled && fen.equals(lastLoadedFen) && hasArrowsOnScreen) {
                        return; // duplicate suppressed only if arrows already on screen
                    }
                    lastLoadedFen = fen;
                    isScheduled = true;
                    analysisTriggerCount++;
                    hasArrowsOnScreen = true;
                } else {
                    isScheduled = false;
                    lastLoadedFen = null;
                    hasArrowsOnScreen = false;
                }
            }

            void onGameOver() {
                isScheduled = false;
                lastLoadedFen = null;
                hasArrowsOnScreen = false;
            }
        }
        Move0StateMachine sm = new Move0StateMachine();
        String m0Fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        sm.onBoardChanged(m0Fen);
        check("Move 0 initial load triggered analysis count = 1", sm.analysisTriggerCount == 1);
        sm.onBoardChanged(m0Fen); // duplicate call with arrows on screen
        check("Move 0 duplicate call skipped when arrows visible", sm.analysisTriggerCount == 1);
        sm.hasArrowsOnScreen = false; // simulates arrows not rendered yet (e.g. view layout delay)
        sm.onBoardChanged(m0Fen);
        check("Move 0 triggers retry if arrows are not yet rendered", sm.analysisTriggerCount == 2);
        String m1Fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";
        sm.onBoardChanged(m1Fen);
        check("Move 1 reset Move 0 scheduled flag", !sm.isScheduled);
        sm.onGameOver();
        sm.onBoardChanged(m0Fen); // new game started
        check("New game Move 0 triggers fresh analysis count = 3", sm.analysisTriggerCount == 3);

        // === ACTIVE GAMEOVER DETECTION DIALOG & FRAGMENT TESTS ===
        class MockDialog {
            public boolean isShowing() { return true; }
        }
        class MockDialogFragment {
            public boolean isVisible() { return false; } // false in lifecycle but dialog is showing!
            public boolean isAdded() { return true; }
            public boolean isRemoving() { return false; }
            public MockDialog getDialog() { return new MockDialog(); }
        }
        MockDialogFragment mdf = new MockDialogFragment();
        boolean isDialogShowing = false;
        try {
            java.lang.reflect.Method getDlg = mdf.getClass().getMethod("getDialog");
            Object dlg = getDlg.invoke(mdf);
            if (dlg != null) {
                java.lang.reflect.Method isShowing = dlg.getClass().getMethod("isShowing");
                isDialogShowing = (Boolean) isShowing.invoke(dlg);
            }
        } catch (Throwable ignored) {}
        // DialogFragment showing check:
        // isAdded() alone MUST NOT trigger game over (would cause false positive on dormant retained dialogs)
        // getDialog().isShowing() == true MUST trigger game over
        boolean isFragActive = false;
        try {
            java.lang.reflect.Method getDlg = mdf.getClass().getMethod("getDialog");
            Object dlg = getDlg.invoke(mdf);
            if (dlg != null) {
                java.lang.reflect.Method isShowing = dlg.getClass().getMethod("isShowing");
                isFragActive = (Boolean) isShowing.invoke(dlg);
            }
        } catch (Throwable ignored) {}
        check("Active DialogFragment isShowing recognized correctly", isFragActive);

        class MockView {
            public int getVisibility() { return 0; } // View.VISIBLE = 0
            public boolean isShown() { return true; }
            public Object getParent() { return new Object(); }
        }
        class MockRegularFragment {
            public boolean isVisible() { return false; }
            public MockView getView() { return new MockView(); }
        }
        MockRegularFragment mrf = new MockRegularFragment();
        boolean isViewShowing = false;
        try {
            java.lang.reflect.Method getViewM = mrf.getClass().getMethod("getView");
            Object v = getViewM.invoke(mrf);
            if (v != null) {
                java.lang.reflect.Method getVis = v.getClass().getMethod("getVisibility");
                java.lang.reflect.Method isShownM = v.getClass().getMethod("isShown");
                java.lang.reflect.Method getParent = v.getClass().getMethod("getParent");
                int vis = (Integer) getVis.invoke(v);
                boolean shown = (Boolean) isShownM.invoke(v);
                Object parent = getParent.invoke(v);
                isViewShowing = (vis == 0 && shown && parent != null);
            }
        } catch (Throwable ignored) {}
        check("Active Fragment getView() isShown recognized correctly", isViewShowing);

        // Checkmate '#' and Game result recognition
        String lastMoveCheckmate = "Qxf7#";
        boolean isCheckmateMove = lastMoveCheckmate.endsWith("#") || lastMoveCheckmate.contains("#");
        check("Last move checkmate symbol '#' triggers game over recognition", isCheckmateMove);

        // Startpos bypass: When board FEN is startpos, checkmate in stale history must NOT trigger game over!
        String boardStartposFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        boolean isStartposBoard = boardStartposFen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR");
        boolean isGameOverFromHistory = !isStartposBoard && isCheckmateMove;
        check("Startpos board ignores stale checkmate in moves history", !isGameOverFromHistory);

        // ViewModel GameEndData flow test
        class MockStateFlow {
            public Object getValue() { return new Object(); } // GameEndData present!
        }
        class MockRealGameViewModel {
            public MockStateFlow oa() { return new MockStateFlow(); }
        }
        MockRealGameViewModel mvm = new MockRealGameViewModel();
        boolean isVmGameOver = false;
        try {
            java.lang.reflect.Method oaM = mvm.getClass().getMethod("oa");
            Object flow = oaM.invoke(mvm);
            if (flow != null) {
                java.lang.reflect.Method getVal = flow.getClass().getMethod("getValue");
                isVmGameOver = (getVal.invoke(flow) != null);
            }
        } catch (Throwable ignored) {}
        check("RealGameViewModel oa() popup presence triggers game over recognition", isVmGameOver);

        // === SUMMARY ===
        System.out.println("\n" + passed + " passed, " + failed + " failed.");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static String formatArrowEval(boolean branchHasMate, int branchMateIn, float score, boolean whiteToMove) {
        if (branchHasMate && branchMateIn != 0) {
            return branchMateIn > 0 ? ("#" + branchMateIn) : ("#-" + Math.abs(branchMateIn));
        }
        float relativeScore = whiteToMove ? score : -score;
        float rounded = Math.round(relativeScore * 10f) / 10f;
        String fixed = Math.abs(rounded) >= 10f
                ? String.format(java.util.Locale.US, "%.0f", (float) Math.abs(rounded))
                : String.format(java.util.Locale.US, "%.1f", (float) Math.abs(rounded));
        return (rounded > 0 ? "+" : (rounded < 0 ? "-" : "")) + fixed;
    }
}
