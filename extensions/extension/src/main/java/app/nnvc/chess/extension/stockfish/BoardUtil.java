/*
 * Copyright 2026 NNVC
 * https://github.com/NNVC/NNVC-Patches
 */

package app.nnvc.chess.extension.stockfish;

import java.util.List;

/**
 * Minimal, allocation-light board model used by the game review.
 *
 * It does NOT generate moves or check legality – it only applies moves that are already
 * known to be legal (moves from the game or from Stockfish's principal variation). That is
 * enough to follow the engine's line to detect real sacrifices, detect recaptures,
 * normalise castling notation before it is sent to Stockfish, and classify the game phase.
 *
 * Squares are indexed 0..63 with 0 = a8 and 63 = h1 (FEN order).
 */
public final class BoardUtil {

    private BoardUtil() {}

    /** Expand the piece-placement field of a FEN into a 64 char array ('.' = empty). */
    public static char[] parseBoard(String fen) {
        char[] b = new char[64];
        java.util.Arrays.fill(b, '.');
        if (fen == null) return b;
        int sp = fen.indexOf(' ');
        String placement = sp >= 0 ? fen.substring(0, sp) : fen;
        int idx = 0;
        for (int i = 0; i < placement.length() && idx < 64; i++) {
            char c = placement.charAt(i);
            if (c == '/') continue;
            if (c >= '1' && c <= '8') {
                idx += c - '0';
            } else {
                b[idx++] = c;
            }
        }
        return b;
    }

    /** Piece placement field only. */
    public static String placement(String fen) {
        if (fen == null) return null;
        int sp = fen.indexOf(' ');
        return sp >= 0 ? fen.substring(0, sp) : fen;
    }

    public static String placement(char[] b) {
        StringBuilder sb = new StringBuilder(72);
        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                char c = b[r * 8 + f];
                if (c == '.') {
                    empty++;
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(c);
                }
            }
            if (empty > 0) sb.append(empty);
            if (r < 7) sb.append('/');
        }
        return sb.toString();
    }

    public static int square(String uci, int offset) {
        if (uci == null || uci.length() < offset + 2) return -1;
        int file = uci.charAt(offset) - 'a';
        int rank = uci.charAt(offset + 1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) return -1;
        return (7 - rank) * 8 + file;
    }

    public static String squareName(int sq) {
        return "" + (char) ('a' + (sq % 8)) + (char) ('1' + (7 - sq / 8));
    }

    /**
     * Convert "king takes own rook" castling notation (e1h1 / e1a1) into standard notation
     * (e1g1 / e1c1). Stockfish 19 terminates on illegal moves, so this must be done before a
     * move list is sent to the engine.
     */
    public static String normalizeCastling(char[] b, String uci) {
        if (uci == null || uci.length() < 4) return uci;
        int from = square(uci, 0);
        int to = square(uci, 2);
        if (from < 0 || to < 0) return uci;
        char p = b[from];
        if (p != 'K' && p != 'k') return uci;
        char ownRook = p == 'K' ? 'R' : 'r';
        if (b[to] == ownRook && from / 8 == to / 8) {
            int fromFile = from % 8;
            int toFile = to % 8;
            int dest = (from / 8) * 8 + (toFile > fromFile ? 6 : 2);
            return squareName(from) + squareName(dest);
        }
        return uci;
    }

    /**
     * Apply a legal UCI move in place.
     *
     * @return the captured piece ('.' if none)
     */
    public static char apply(char[] b, String uci) {
        int from = square(uci, 0);
        int to = square(uci, 2);
        if (from < 0 || to < 0) return '.';
        char p = b[from];
        if (p == '.') return '.';
        char captured = b[to];

        boolean white = Character.isUpperCase(p);
        char lower = Character.toLowerCase(p);

        // Castling written as king-takes-rook
        if (lower == 'k' && captured == (white ? 'R' : 'r')) {
            int row = from / 8;
            boolean kingSide = (to % 8) > (from % 8);
            b[from] = '.';
            b[to] = '.';
            b[row * 8 + (kingSide ? 6 : 2)] = p;
            b[row * 8 + (kingSide ? 5 : 3)] = white ? 'R' : 'r';
            return '.';
        }

        // Standard castling (king moves two files)
        if (lower == 'k' && Math.abs((to % 8) - (from % 8)) == 2 && from / 8 == to / 8) {
            int row = from / 8;
            boolean kingSide = (to % 8) > (from % 8);
            int rookFrom = row * 8 + (kingSide ? 7 : 0);
            int rookTo = row * 8 + (kingSide ? 5 : 3);
            b[rookTo] = b[rookFrom];
            b[rookFrom] = '.';
        }

        // En passant: pawn moves diagonally onto an empty square
        if (lower == 'p' && (from % 8) != (to % 8) && captured == '.') {
            int capSq = (from / 8) * 8 + (to % 8);
            captured = b[capSq];
            b[capSq] = '.';
        }

        b[to] = p;
        b[from] = '.';

        // Promotion
        if (uci.length() >= 5 && lower == 'p') {
            char promo = uci.charAt(4);
            b[to] = white ? Character.toUpperCase(promo) : Character.toLowerCase(promo);
        }
        return captured;
    }

    public static int pieceValue(char c) {
        switch (Character.toLowerCase(c)) {
            case 'p': return 1;
            case 'n':
            case 'b': return 3;
            case 'r': return 5;
            case 'q': return 9;
            default: return 0;
        }
    }

    /** Material of one side (pawn = 1). */
    public static int material(char[] b, boolean white) {
        int s = 0;
        for (char c : b) {
            if (c != '.' && Character.isUpperCase(c) == white) s += pieceValue(c);
        }
        return s;
    }

    /** Non-pawn material of one side. */
    public static int nonPawnMaterial(char[] b, boolean white) {
        int s = 0;
        for (char c : b) {
            if (c != '.' && c != 'p' && c != 'P' && Character.isUpperCase(c) == white) s += pieceValue(c);
        }
        return s;
    }

    /** 0 = opening, 1 = middlegame, 2 = endgame. */
    public static int phase(char[] b, int ply) {
        int npm = nonPawnMaterial(b, true) + nonPawnMaterial(b, false);
        boolean queens = false;
        for (char c : b) if (c == 'Q' || c == 'q') { queens = true; break; }
        if (npm <= 26 || (!queens && npm <= 32)) return 2;
        if (ply < 24) return 0;
        return 1;
    }

    /** Does the move land on the square where the opponent just captured? */
    public static boolean isRecapture(String prevMove, boolean prevWasCapture, String move) {
        if (!prevWasCapture || prevMove == null || move == null) return false;
        if (prevMove.length() < 4 || move.length() < 4) return false;
        return prevMove.regionMatches(2, move, 2, 2);
    }

    /**
     * Detect a genuine sacrifice by following Stockfish's best line after the played move.
     *
     * The move is a sacrifice if, after the last opponent move inside the engine line window,
     * the mover is down at least two points compared with before the move and has lost
     * non-pawn material. Ordinary trades (material comes back) are therefore ignored.
     *
     * @param before     board before the played move (not modified)
     * @param moverWhite side that played the move
     * @param played     the played move
     * @param replyPv    engine PV from the position after the played move (opponent to move)
     */
    public static boolean isSacrifice(char[] before, boolean moverWhite, String played,
                                      List<String> replyPv, int window) {
        if (before == null || played == null || replyPv == null || replyPv.isEmpty()) return false;
        char[] b = before.clone();
        int diff0 = material(b, moverWhite) - material(b, !moverWhite);
        int pieces0 = nonPawnMaterial(b, moverWhite);

        apply(b, played);

        boolean endDown = false;
        int plies = Math.min(window, replyPv.size());
        for (int i = 0; i < plies; i++) {
            apply(b, replyPv.get(i));
            if ((i & 1) == 0) { // after an opponent move
                int diff = material(b, moverWhite) - material(b, !moverWhite);
                endDown = diff <= diff0 - 2 && nonPawnMaterial(b, moverWhite) < pieces0;
            }
        }
        return endDown;
    }

    /**
     * Checks if a square is attacked by pieces of the specified color.
     */
    public static boolean isSquareAttacked(char[] b, int sq, boolean byWhite) {
        if (b == null || sq < 0 || sq >= 64) return false;
        int r = sq / 8;
        int f = sq % 8;

        // 1. Pawns
        if (byWhite) {
            int pr = r + 1;
            if (pr < 8) {
                if (f > 0 && b[pr * 8 + (f - 1)] == 'P') return true;
                if (f < 7 && b[pr * 8 + (f + 1)] == 'P') return true;
            }
        } else {
            int pr = r - 1;
            if (pr >= 0) {
                if (f > 0 && b[pr * 8 + (f - 1)] == 'p') return true;
                if (f < 7 && b[pr * 8 + (f + 1)] == 'p') return true;
            }
        }

        // 2. Knights
        int[] knightOffsets = {-17, -15, -10, -6, 6, 10, 15, 17};
        char targetKnight = byWhite ? 'N' : 'n';
        for (int off : knightOffsets) {
            int nsq = sq + off;
            if (nsq >= 0 && nsq < 64) {
                int nr = nsq / 8;
                int nf = nsq % 8;
                if (Math.abs(nr - r) <= 2 && Math.abs(nf - f) <= 2 && b[nsq] == targetKnight) {
                    return true;
                }
            }
        }

        // 3. Diagonal rays (Bishop, Queen)
        int[][] diagDirs = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        char targetBishop = byWhite ? 'B' : 'b';
        char targetQueen = byWhite ? 'Q' : 'q';
        for (int[] dir : diagDirs) {
            int dr = dir[0], df = dir[1];
            for (int step = 1; step < 8; step++) {
                int nr = r + dr * step;
                int nf = f + df * step;
                if (nr < 0 || nr >= 8 || nf < 0 || nf >= 8) break;
                char p = b[nr * 8 + nf];
                if (p != '.') {
                    if (p == targetBishop || p == targetQueen) return true;
                    break;
                }
            }
        }

        // 4. Straight rays (Rook, Queen)
        int[][] straightDirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        char targetRook = byWhite ? 'R' : 'r';
        for (int[] dir : straightDirs) {
            int dr = dir[0], df = dir[1];
            for (int step = 1; step < 8; step++) {
                int nr = r + dr * step;
                int nf = f + df * step;
                if (nr < 0 || nr >= 8 || nf < 0 || nf >= 8) break;
                char p = b[nr * 8 + nf];
                if (p != '.') {
                    if (p == targetRook || p == targetQueen) return true;
                    break;
                }
            }
        }

        // 5. King
        char targetKing = byWhite ? 'K' : 'k';
        for (int dr = -1; dr <= 1; dr++) {
            for (int df = -1; df <= 1; df++) {
                if (dr == 0 && df == 0) continue;
                int nr = r + dr;
                int nf = f + df;
                if (nr >= 0 && nr < 8 && nf >= 0 && nf < 8 && b[nr * 8 + nf] == targetKing) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks if the King of the specified color is currently in check.
     */
    public static boolean isKingInCheck(char[] b, boolean white) {
        if (b == null) return false;
        char target = white ? 'K' : 'k';
        int kingSq = -1;
        for (int i = 0; i < 64; i++) {
            if (b[i] == target) {
                kingSq = i;
                break;
            }
        }
        if (kingSq < 0) return false;
        return isSquareAttacked(b, kingSq, !white);
    }

    /**
     * Counts legal moves for the specified side, short-circuiting once maxCount is reached.
     */
    public static int countLegalMoves(char[] b, boolean white, int maxCount) {
        if (b == null) return 0;
        int count = 0;
        int[] targets = new int[64];

        for (int sq = 0; sq < 64; sq++) {
            char p = b[sq];
            if (p == '.' || Character.isUpperCase(p) != white) continue;
            int r = sq / 8;
            int f = sq % 8;
            char lp = Character.toLowerCase(p);
            int targetCount = 0;

            if (lp == 'p') {
                int dir = white ? -1 : 1;
                int startRow = white ? 6 : 1;
                int nr = r + dir;
                if (nr >= 0 && nr < 8 && b[nr * 8 + f] == '.') {
                    targets[targetCount++] = nr * 8 + f;
                    int nnr = r + 2 * dir;
                    if (r == startRow && b[nnr * 8 + f] == '.') {
                        targets[targetCount++] = nnr * 8 + f;
                    }
                }
                for (int df = -1; df <= 1; df += 2) {
                    int nf = f + df;
                    if (nr >= 0 && nr < 8 && nf >= 0 && nf < 8) {
                        char cap = b[nr * 8 + nf];
                        if (cap != '.' && Character.isUpperCase(cap) != white) {
                            targets[targetCount++] = nr * 8 + nf;
                        }
                    }
                }
            } else if (lp == 'n') {
                int[] knightOffsets = {-17, -15, -10, -6, 6, 10, 15, 17};
                for (int off : knightOffsets) {
                    int nsq = sq + off;
                    if (nsq >= 0 && nsq < 64) {
                        int nr = nsq / 8, nf = nsq % 8;
                        if (Math.abs(nr - r) <= 2 && Math.abs(nf - f) <= 2) {
                            char dest = b[nsq];
                            if (dest == '.' || Character.isUpperCase(dest) != white) {
                                targets[targetCount++] = nsq;
                            }
                        }
                    }
                }
            } else if (lp == 'b' || lp == 'q') {
                int[][] diagDirs = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
                for (int[] dir : diagDirs) {
                    int dr = dir[0], df = dir[1];
                    for (int step = 1; step < 8; step++) {
                        int nr = r + dr * step, nf = f + df * step;
                        if (nr < 0 || nr >= 8 || nf < 0 || nf >= 8) break;
                        int nsq = nr * 8 + nf;
                        char dest = b[nsq];
                        if (dest == '.') {
                            targets[targetCount++] = nsq;
                        } else {
                            if (Character.isUpperCase(dest) != white) targets[targetCount++] = nsq;
                            break;
                        }
                    }
                }
            }
            if (lp == 'r' || lp == 'q') {
                int[][] straightDirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                for (int[] dir : straightDirs) {
                    int dr = dir[0], df = dir[1];
                    for (int step = 1; step < 8; step++) {
                        int nr = r + dr * step, nf = f + df * step;
                        if (nr < 0 || nr >= 8 || nf < 0 || nf >= 8) break;
                        int nsq = nr * 8 + nf;
                        char dest = b[nsq];
                        if (dest == '.') {
                            targets[targetCount++] = nsq;
                        } else {
                            if (Character.isUpperCase(dest) != white) targets[targetCount++] = nsq;
                            break;
                        }
                    }
                }
            } else if (lp == 'k') {
                for (int dr = -1; dr <= 1; dr++) {
                    for (int df = -1; df <= 1; df++) {
                        if (dr == 0 && df == 0) continue;
                        int nr = r + dr, nf = f + df;
                        if (nr >= 0 && nr < 8 && nf >= 0 && nf < 8) {
                            int nsq = nr * 8 + nf;
                            char dest = b[nsq];
                            if (dest == '.' || Character.isUpperCase(dest) != white) {
                                targets[targetCount++] = nsq;
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < targetCount; i++) {
                int tsq = targets[i];
                char[] cloned = b.clone();
                cloned[tsq] = cloned[sq];
                cloned[sq] = '.';
                if (!isKingInCheck(cloned, white)) {
                    count++;
                    if (count >= maxCount) return count;
                }
            }
        }
        return count;
    }

    /**
     * Returns true if and only if the specified side has EXACTLY 1 legal move in total.
     */
    public static boolean isOnlyLegalMove(char[] b, boolean white) {
        return countLegalMoves(b, white, 2) == 1;
    }
}
