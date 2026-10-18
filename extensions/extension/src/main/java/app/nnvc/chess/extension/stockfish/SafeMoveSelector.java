package app.nnvc.chess.extension.stockfish;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * SafeMoveSelector – 100% Parity with NNVC Desktop Extension Safe Mode
 * (NNVC/nnvc_main_bundle.js:5395-5620 selectMoveFromCandidates).
 *
 * Implements human-like natural move candidate selection constrained strictly
 * by Arrow Count (MultiPV 1-5), Emergency Override (In Check, Mate Threat, Heavy Loss <= -2.0),
 * Dirty Flagging scramble prioritization (<= 6.0s), and Human Imperfection simulation.
 */
public class SafeMoveSelector {

    public static class MoveDecision {
        public final String chosenMove;
        public final int chosenIndex; // 0-based index in candidate list
        public final String reason;
        public final int cpGap;

        public MoveDecision(String chosenMove, int chosenIndex, String reason, int cpGap) {
            this.chosenMove = chosenMove;
            this.chosenIndex = chosenIndex;
            this.reason = reason;
            this.cpGap = cpGap;
        }
    }

    private static final Random RNG = new Random();

    /**
     * Compute centipawn gap between top engine move and a candidate move.
     * Matches nnvc_main_bundle.js computeCpGap: lines 5152-5165.
     */
    public static int computeCpGap(float topScore, boolean topHasMate, int topMateIn,
                                  float candScore, boolean candHasMate, int candMateIn) {
        if (topHasMate) {
            if (candHasMate) {
                if (topMateIn > 0 && candMateIn > 0) {
                    return Math.max(0, Math.abs(topMateIn) - Math.abs(candMateIn)) * 12;
                }
                if (topMateIn < 0 && candMateIn < 0) {
                    return Math.max(0, Math.abs(candMateIn) - Math.abs(topMateIn)) * 12;
                }
            }
            return 9999;
        }

        if (candHasMate) {
            return 9999;
        }

        int topCp = Math.round(topScore * 100f);
        int curCp = Math.round(candScore * 100f);
        return Math.max(0, topCp - curCp);
    }

    /**
     * Select a move from engine candidates adhering 100% to Desktop Extension Safe Mode logic.
     */
    public static MoveDecision selectMove(List<String> moves,
                                          float[] lineScores,
                                          boolean[] lineMates,
                                          int[] lineMateIn,
                                          int arrowLimit,
                                          long playerClockMs,
                                          long oppClockMs,
                                          String currentFen,
                                          boolean isSafeMode,
                                          boolean userWhite) {
        if (moves == null || moves.isEmpty()) {
            return new MoveDecision(null, 0, "no_candidates", 0);
        }

        // Arrow Count Constraint: candidate moves are restricted to arrowLimit (1-5)
        int effectiveLimit = Math.max(1, arrowLimit);
        int totalCandidates = Math.min(moves.size(), effectiveLimit);
        List<String> candidates = new ArrayList<>(moves.subList(0, totalCandidates));

        String topMove = candidates.get(0);
        float topScore = (lineScores != null && lineScores.length > 0) ? lineScores[0] : 0f;
        boolean topMate = (lineMates != null && lineMates.length > 0) && lineMates[0];
        int topMateIn = (lineMateIn != null && lineMateIn.length > 0) ? lineMateIn[0] : 0;

        // When Safe Mode is OFF or only 1 candidate available: 100% pure Top 1
        if (!isSafeMode || candidates.size() <= 1) {
            return new MoveDecision(topMove, 0, !isSafeMode ? "bullet_mode_off_pure_top1" : "single_candidate", 0);
        }

        // Eval and advantage classification
        int evalCp = Math.round(topScore * 100f);
        boolean hasMateForMe = topMate && topMateIn > 0;
        boolean hasMateAgainstMe = topMate && topMateIn < 0;

        // Check if user is in check from current FEN
        boolean isCheck = false;
        if (currentFen != null && !currentFen.isEmpty()) {
            try {
                char[] board = BoardUtil.parseBoard(currentFen);
                isCheck = BoardUtil.isKingInCheck(board, userWhite);
            } catch (Throwable ignored) {}
        }

        boolean tacticalCrisis = hasMateAgainstMe || isCheck || (topMate && Math.abs(topMateIn) <= 3);
        boolean winningHard = hasMateForMe || evalCp >= 280;
        boolean winning = winningHard || evalCp >= 120;
        boolean losingHard = hasMateAgainstMe || evalCp <= -180;
        boolean losing = losingHard || evalCp <= -80;

        String advantageState = losingHard ? "losing_hard" : losing ? "losing" : winningHard ? "winning_hard" : winning ? "winning" : "balanced";

        // Emergency Override: forced mate for me, tactical crisis, or losing hard
        if (hasMateForMe) {
            return new MoveDecision(topMove, 0, "forced_mate_top1", 0);
        }
        if (tacticalCrisis || losingHard) {
            return new MoveDecision(topMove, 0, "tactical_crisis_top1", 0);
        }

        int maxAllowedGap = losing ? 25 : advantageState.equals("balanced") ? 45 : 65;

        // Candidate filtering & gap calculation
        class CandidateInfo {
            final String move;
            final int originalIndex;
            final int cpGap;
            final boolean isCheckMove;
            final boolean isPromoMove;

            CandidateInfo(String move, int originalIndex, int cpGap, boolean isCheckMove, boolean isPromoMove) {
                this.move = move;
                this.originalIndex = originalIndex;
                this.cpGap = cpGap;
                this.isCheckMove = isCheckMove;
                this.isPromoMove = isPromoMove;
            }
        }

        List<CandidateInfo> safeShortlist = new ArrayList<>();
        // Top 1 candidate is always in safe shortlist
        boolean topIsCheck = isMoveCheck(currentFen, topMove, userWhite);
        boolean topIsPromo = isMovePromotion(currentFen, topMove);
        safeShortlist.add(new CandidateInfo(topMove, 0, 0, topIsCheck, topIsPromo));

        for (int i = 1; i < candidates.size(); i++) {
            String candMove = candidates.get(i);
            float cScore = (lineScores != null && lineScores.length > i) ? lineScores[i] : topScore;
            boolean cMate = (lineMates != null && lineMates.length > i) && lineMates[i];
            int cMateIn = (lineMateIn != null && lineMateIn.length > i) ? lineMateIn[i] : 0;

            int gap = computeCpGap(topScore, topMate, topMateIn, cScore, cMate, cMateIn);
            if (gap > maxAllowedGap) {
                continue;
            }
            if (cMate && !hasMateForMe) {
                continue;
            }

            boolean cIsCheck = isMoveCheck(currentFen, candMove, userWhite);
            boolean cIsPromo = isMovePromotion(currentFen, candMove);
            safeShortlist.add(new CandidateInfo(candMove, i, gap, cIsCheck, cIsPromo));
        }

        if (safeShortlist.size() <= 1) {
            return new MoveDecision(topMove, 0, "only_top1_safe", 0);
        }

        // ─── DIRTY FLAGGING: Clock scramble <= 6.0s for opp or <= 6.5s for player ───
        boolean isDirtyFlaggingScramble = (oppClockMs >= 0 && oppClockMs <= 6000L) ||
                                          (playerClockMs >= 0 && playerClockMs <= 6500L);
        if (isDirtyFlaggingScramble) {
            // Prioritize check candidate within 35 cp gap
            for (CandidateInfo c : safeShortlist) {
                if (c.isCheckMove && c.cpGap <= 35) {
                    return new MoveDecision(c.move, c.originalIndex, "dirty_flagging_check", c.cpGap);
                }
            }
            // Prioritize promotion candidate within 30 cp gap
            for (CandidateInfo c : safeShortlist) {
                if (c.isPromoMove && c.cpGap <= 30) {
                    return new MoveDecision(c.move, c.originalIndex, "dirty_flagging_promo", c.cpGap);
                }
            }
        }

        // ─── HUMAN IMPERFECTION: When winning decisively (evalCp >= 400 ~ +4.0) ───
        if (evalCp >= 400 && !tacticalCrisis && !isCheck && !hasMateAgainstMe && !isDirtyFlaggingScramble && safeShortlist.size() > 1) {
            float imperfectionRoll = RNG.nextFloat();
            if (imperfectionRoll < 0.045f) { // 4.5% chance
                for (int i = 1; i < safeShortlist.size(); i++) {
                    CandidateInfo cand = safeShortlist.get(i);
                    int gap = cand.cpGap;
                    // Inaccuracy gap: 24 - 42 cp, preserving decisive winning advantage (evalCp - gap >= 320)
                    if (gap >= 24 && gap <= 42 && (evalCp - gap) >= 320) {
                        return new MoveDecision(cand.move, cand.originalIndex, "human_imperfection", gap);
                    }
                }
            }
        }

        // ─── WEIGHTED PROBABILITY PICK ───
        // Calculate weights matching Desktop Extension lines 5597-5616
        double[] weights = new double[safeShortlist.size()];
        double totalWeight = 0;
        for (int i = 0; i < safeShortlist.size(); i++) {
            CandidateInfo cand = safeShortlist.get(i);
            int gap = cand.cpGap;
            double gapPenalty = Math.max(0.2, 1.0 - (double) gap / (maxAllowedGap * 1.2));
            double baseWeight;
            if (i == 0) {
                baseWeight = isDirtyFlaggingScramble ? 5.0 : losing ? 2.5 : advantageState.equals("winning_hard") ? 0.45 : advantageState.equals("balanced") ? 0.9 : 0.6;
            } else if (i == 1) {
                baseWeight = isDirtyFlaggingScramble ? 0.2 : losing ? 0.5 : advantageState.equals("winning_hard") ? 1.25 : advantageState.equals("balanced") ? 1.0 : 1.0;
            } else if (i == 2) {
                baseWeight = isDirtyFlaggingScramble ? 0.05 : losing ? 0.2 : advantageState.equals("winning_hard") ? 0.85 : advantageState.equals("balanced") ? 0.7 : 0.8;
            } else if (i == 3) {
                baseWeight = isDirtyFlaggingScramble ? 0.01 : losing ? 0.05 : 0.45;
            } else {
                baseWeight = isDirtyFlaggingScramble ? 0.005 : losing ? 0.02 : 0.25;
            }
            double w = Math.max(0.01, baseWeight * gapPenalty);
            weights[i] = w;
            totalWeight += w;
        }

        double roll = RNG.nextDouble() * totalWeight;
        CandidateInfo chosen = safeShortlist.get(0);
        for (int i = 0; i < safeShortlist.size(); i++) {
            roll -= weights[i];
            if (roll <= 0) {
                chosen = safeShortlist.get(i);
                break;
            }
        }

        String reason = (chosen.originalIndex == 0) ? "natural_top1" : ("natural_top" + (chosen.originalIndex + 1));
        return new MoveDecision(chosen.move, chosen.originalIndex, reason, chosen.cpGap);
    }

    /**
     * Check if a move delivers check to the opponent king.
     */
    private static boolean isMoveCheck(String fen, String uciMove, boolean userWhite) {
        if (fen == null || uciMove == null || uciMove.length() < 4) return false;
        try {
            char[] board = BoardUtil.parseBoard(fen);
            BoardUtil.apply(board, uciMove);
            boolean oppWhite = !userWhite;
            return BoardUtil.isKingInCheck(board, oppWhite);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Check if a move is a pawn promotion.
     */
    private static boolean isMovePromotion(String fen, String uciMove) {
        if (uciMove == null || uciMove.length() < 4) return false;
        if (uciMove.length() >= 5) {
            char promo = Character.toLowerCase(uciMove.charAt(4));
            if (promo == 'q' || promo == 'r' || promo == 'b' || promo == 'n') return true;
        }
        if (fen == null) return false;
        try {
            String fromSq = uciMove.substring(0, 2);
            char piece = StockfishExtension.getPieceAtSquare(fen, fromSq);
            if (piece == 'P' && uciMove.charAt(3) == '8') return true;
            if (piece == 'p' && uciMove.charAt(3) == '1') return true;
        } catch (Throwable t) {
            return false;
        }
        return false;
    }
}
