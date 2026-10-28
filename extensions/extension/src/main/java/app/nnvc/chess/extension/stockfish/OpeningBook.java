/*
 * Copyright 2026 NNVC
 * 100% Parity with NNVC Desktop Extension Opening Book (_OPENING_BOOK in nnvc_main_bundle.js)
 */

package app.nnvc.chess.extension.stockfish;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpeningBook - Bảng tra cứu sách khai cuộc trực tiếp trong bộ nhớ (In-Memory Opening Book).
 * Cung cấp kết quả nước đi khai cuộc tức thì (0ms) trên UI Thread y hệt Extension Chrome,
 * loại bỏ hoàn toàn độ trễ 300-800ms của WebAssembly IPC khi hiển thị mũi tên ở các nước đầu ván.
 */
public final class OpeningBook {
    private static final Map<String, List<String>> BOOK_ENTRIES = new HashMap<>();

    static {
        BOOK_ENTRIES.put("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR", Arrays.asList("e2e4", "d2d4", "c2c4", "g1f3"));
        BOOK_ENTRIES.put("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR", Arrays.asList("e7e5", "c7c5", "e7e6", "c7c6", "d7d5"));
        BOOK_ENTRIES.put("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR", Arrays.asList("g1f3", "f1c4", "f1b5", "d2d4"));
        BOOK_ENTRIES.put("rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", Arrays.asList("b8c6", "g8f6", "f8c5", "d7d6"));
        BOOK_ENTRIES.put("r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", Arrays.asList("f1b5", "f1c4", "d2d4", "b1c3"));
        BOOK_ENTRIES.put("r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", Arrays.asList("a7a6", "g8f6", "f8c5", "d7d6"));
        BOOK_ENTRIES.put("r1bqkbnr/1ppp1ppp/p1n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R", Arrays.asList("b1a4"));
        BOOK_ENTRIES.put("r1bqkb1r/1ppp1ppp/p1n2n2/4p3/B3P3/5N2/PPPP1PPP/RNBQK2R", Arrays.asList("e1g1"));
        BOOK_ENTRIES.put("r1bqk2r/1pppbppp/p1n2n2/4p3/B3P3/5N2/PPPP1PPP/RNBQ1RK1", Arrays.asList("f1e1", "d2d3"));
        BOOK_ENTRIES.put("r1bqkb1r/1pp2ppp/p1n2n2/1B1pp3/4P3/5N2/PPPP1PPP/RNBQK2R", Arrays.asList("e4d5"));
        BOOK_ENTRIES.put("r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", Arrays.asList("f8c5", "g8f6", "f8e7"));
        BOOK_ENTRIES.put("r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", Arrays.asList("c2c3", "b1c3"));
        BOOK_ENTRIES.put("r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R", Arrays.asList("f1g5", "d2d3", "b1c3"));
        BOOK_ENTRIES.put("r1bqkb1r/ppp2ppp/2n2n2/3pp1N1/2B1P3/8/PPPP1PPP/RNBQK2R", Arrays.asList("e4d5"));
        BOOK_ENTRIES.put("r1bqk1nr/pppp1ppp/2n2b2/4p3/2B1P3/2P2N2/PP1P1PPP/RNBQK2R", Arrays.asList("d2d4"));
        BOOK_ENTRIES.put("rnbqkbnr/pppp1ppp/8/4p3/4PP2/8/PPPP2PP/RNBQKBNR", Arrays.asList("e5f4", "d7d5", "f8c5"));
        BOOK_ENTRIES.put("rnbqkbnr/pppp1ppp/8/8/4Pp2/5N2/PPPP2PP/RNBQKB1R", Arrays.asList("g7g5", "d7d5", "g8f6"));
        BOOK_ENTRIES.put("rnbqkb1r/pppp1ppp/5n2/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R", Arrays.asList("g1e5", "d2d4", "b1c3"));
        BOOK_ENTRIES.put("rnbqkb1r/pppp1ppp/8/4N3/4n3/8/PPPP1PPP/RNBQKB1R", Arrays.asList("d2d4", "b1c3"));
        BOOK_ENTRIES.put("rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR", Arrays.asList("g1f3", "b1c3", "f2f4"));
        BOOK_ENTRIES.put("rnbqkbnr/pp2pppp/3p4/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", Arrays.asList("d2d4", "b1c3"));
        BOOK_ENTRIES.put("r1bqkbnr/pp1ppppp/2n5/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", Arrays.asList("d2d4", "f1b5", "b1c3"));
        BOOK_ENTRIES.put("rnbqkbnr/pp1p1ppp/4p3/2p5/4P3/5N2/PPPP1PPP/RNBQKB1R", Arrays.asList("d2d4", "b1c3"));
        BOOK_ENTRIES.put("rnbqkbnr/pp2pppp/3p4/8/3NP3/8/PPP2PPP/RNBQKB1R", Arrays.asList("g8f6", "a7a6", "b8c6"));
        BOOK_ENTRIES.put("rnbqkb1r/1p2pppp/p2p1n2/8/3NP3/2N5/PPP2PPP/R1BQKB1R", Arrays.asList("c1g5", "c1e3", "f2f3", "f1e2"));
        BOOK_ENTRIES.put("r1bqkb1r/1p2pppp/p1np1n2/6B1/3NP3/2N5/PPP2PPP/R2QKB1R", Arrays.asList("f2f4", "d1d2"));
        BOOK_ENTRIES.put("rnbqkb1r/pp2pp1p/3p1np1/8/3NP3/2N5/PPP2PPP/R1BQKB1R", Arrays.asList("c1e3", "f2f3"));
        BOOK_ENTRIES.put("rnbqkb1r/pp3ppp/3ppn2/8/3NP3/2N5/PPP2PPP/R1BQKB1R", Arrays.asList("f1e2", "g2g4", "c1e3"));
        BOOK_ENTRIES.put("rnbqkbnr/pp1ppppp/8/2p5/4P3/2P5/PP1P1PPP/RNBQKBNR", Arrays.asList("d7d5", "g8f6", "e7e6"));
        BOOK_ENTRIES.put("r1bqkbnr/pp1ppppp/2n5/2p5/4PP2/2N5/PPPP2PP/R1BQKBNR", Arrays.asList("g7g6", "e7e6", "d7d6"));
        BOOK_ENTRIES.put("rnbqkbnr/pppp1ppp/4p3/8/4P3/8/PPPP1PPP/RNBQKBNR", Arrays.asList("d2d4", "b1c3"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp2ppp/4p3/3p4/3PP3/8/PPP2PPP/RNBQKBNR", Arrays.asList("b1c3", "b1d2", "e4e5", "e4d5"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp2ppp/4p3/3pP3/3P4/8/PPP2PPP/RNBQKBNR", Arrays.asList("c7c5", "f7f6", "g8e7"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp2ppp/4p3/3p4/3PP3/8/PPPN1PPP/R1BQKBNR", Arrays.asList("g8f6", "c7c5", "c7c6"));
        BOOK_ENTRIES.put("rnbqkb1r/ppp2ppp/4pn2/3p4/3PP3/2N5/PPP2PPP/R1BQKBNR", Arrays.asList("c1g5", "e4e5", "e4d5"));
        BOOK_ENTRIES.put("rnbqk1nr/ppp2ppp/4p3/3p4/1b1PP3/2N5/PPP2PPP/R1BQKBNR", Arrays.asList("e4e5", "a2a3", "g1e2"));
        BOOK_ENTRIES.put("rnbqkbnr/pp1ppppp/2p5/8/4P3/8/PPPP1PPP/RNBQKBNR", Arrays.asList("d2d4", "b1c3"));
        BOOK_ENTRIES.put("rnbqkbnr/pp2pppp/2p5/3p4/3PP3/8/PPP2PPP/RNBQKBNR", Arrays.asList("b1c3", "b1d2", "e4e5", "e4d5"));
        BOOK_ENTRIES.put("rnbqkbnr/pp2pppp/2p5/8/3PN3/8/PPP2PPP/R1BQKBNR", Arrays.asList("c8f5", "g8f6", "b8d7"));
        BOOK_ENTRIES.put("rnbqkbnr/pp2pppp/2p5/3pP3/3P4/8/PPP2PPP/RNBQKBNR", Arrays.asList("c8f5", "g8e7"));
        BOOK_ENTRIES.put("rnbqkbnr/pp2pppp/8/3p4/3P4/8/PPP2PPP/RNBQKBNR", Arrays.asList("g8f6", "c8f5", "e7e6"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR", Arrays.asList("e4d5"));
        BOOK_ENTRIES.put("rnb1kbnr/ppp1pppp/8/3q4/8/2N5/PPPP1PPP/R1BQKBNR", Arrays.asList("d5a5", "d5d8", "d5d6"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp1pppp/8/8/4p3/8/PPPP1PPP/RNBQKBNR", Arrays.asList("d1e2", "b1c3"));
        BOOK_ENTRIES.put("rnbqkb1r/pppppppp/5n2/8/4P3/8/PPPP1PPP/RNBQKBNR", Arrays.asList("e4e5", "b1c3", "d2d4"));
        BOOK_ENTRIES.put("rnbqkb1r/pppppppp/8/3nP3/3P4/8/PPP2PPP/RNBQKBNR", Arrays.asList("d7d6", "c7c5", "g7g6"));
        BOOK_ENTRIES.put("rnbqkbnr/pppppp1p/6p1/8/4P3/8/PPPP1PPP/RNBQKBNR", Arrays.asList("d2d4"));
        BOOK_ENTRIES.put("rnbqk1nr/ppppppbp/6p1/8/3PP3/8/PPP2PPP/RNBQKBNR", Arrays.asList("b1c3", "g1f3"));
        BOOK_ENTRIES.put("rnbqkb1r/ppp1pppp/3p1n2/8/3PP3/8/PPP2PPP/RNBQKBNR", Arrays.asList("b1c3", "f2f3"));
        BOOK_ENTRIES.put("rnbqkbnr/pppppppp/8/8/3P4/8/PPP1PPPP/RNBQKBNR", Arrays.asList("d7d5", "g8f6", "f7f5", "e7e6", "c7c6"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp1pppp/8/3p4/3P4/8/PPP1PPPP/RNBQKBNR", Arrays.asList("c2c4", "g1f3", "e2e3", "c1f4"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp1pppp/8/3p4/2PP4/8/PP2PPPP/RNBQKBNR", Arrays.asList("e7e6", "c7c6", "d5c4", "g8f6"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp2ppp/4p3/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR", Arrays.asList("g8f6", "c7c5", "f8e7"));
        BOOK_ENTRIES.put("rnbqkb1r/ppp2ppp/4pn2/3p4/2PP4/2N3P1/PP2PP1P/R1BQKBNR", Arrays.asList("f8e7", "c7c6", "b8d7"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp1pppp/8/8/2pP4/8/PP2PPPP/RNBQKBNR", Arrays.asList("e2e4", "g1f3", "e2e3"));
        BOOK_ENTRIES.put("rnbqkbnr/pp2pppp/2p5/3p4/2PP4/8/PP2PPPP/RNBQKBNR", Arrays.asList("g1f3", "b1c3", "e2e3"));
        BOOK_ENTRIES.put("rnbqkb1r/pp2pppp/2p2n2/3p4/2PP4/2N2N2/PP2PPPP/R1BQKB1R", Arrays.asList("c8f5", "e7e6", "d5c4"));
        BOOK_ENTRIES.put("rnbqkb1r/pp3ppp/2p1pn2/3p4/2PP4/2N2N2/PP2PPPP/R1BQKB1R", Arrays.asList("e2e3", "c1g5"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp1pppp/8/3p4/3P1B2/8/PPP1PPPP/RN1QKBNR", Arrays.asList("g8f6", "e7e6", "c7c5"));
        BOOK_ENTRIES.put("rnbqkb1r/ppp1pppp/5n2/3p4/3P1B2/5N2/PPP1PPPP/RN1QKB1R", Arrays.asList("e7e6", "c7c5", "b8d7"));
        BOOK_ENTRIES.put("rnbqkb1r/pppppppp/5n2/8/3P4/8/PPP1PPPP/RNBQKBNR", Arrays.asList("c2c4", "g1f3", "c1f4", "e2e3"));
        BOOK_ENTRIES.put("rnbqkb1r/pppppppp/5n2/8/2PP4/8/PP2PPPP/RNBQKBNR", Arrays.asList("e7e6", "g7g6", "c7c5", "e7e5"));
        BOOK_ENTRIES.put("rnbqkb1r/pppppp1p/5np1/8/2PP4/2N5/PP2PPPP/R1BQKBNR", Arrays.asList("f8g7", "d7d5", "c7c5"));
        BOOK_ENTRIES.put("rnbqk2r/ppppppbp/5np1/8/2PPP3/2N5/PP3PPP/R1BQKBNR", Arrays.asList("d7d6", "e8g8", "c7c5"));
        BOOK_ENTRIES.put("rnbqk2r/ppp1ppbp/3p1np1/8/2PPP3/2N2N2/PP3PPP/R1BQKB1R", Arrays.asList("e8g8", "c7c5"));
        BOOK_ENTRIES.put("rnbq1rk1/ppp1ppbp/3p1np1/8/2PPP3/2N2N2/PP2BPPP/R1BQK2R", Arrays.asList("e7e5", "c7c5", "b8a6"));
        BOOK_ENTRIES.put("rnbqk2r/pppp1ppp/4pn2/8/1bPP4/2N5/PP2PPPP/R1BQKBNR", Arrays.asList("e2e3", "d1c2", "g1f3", "a2a3"));
        BOOK_ENTRIES.put("rnbqk2r/pppp1ppp/4pn2/8/1bPP4/2N1P3/PP3PPP/R1BQKBNR", Arrays.asList("e8g8", "d7d5", "c7c5"));
        BOOK_ENTRIES.put("rnbqkb1r/p1pp1ppp/1p2pn2/8/2PP4/5N2/PP2PPPP/RNBQKB1R", Arrays.asList("e2e3", "g2g3", "b1c3", "c1f4"));
        BOOK_ENTRIES.put("rnbqkb1r/ppp1pp1p/5np1/3p4/2PP4/2N5/PP2PPPP/R1BQKBNR", Arrays.asList("c4d5", "e2e4", "g1f3"));
        BOOK_ENTRIES.put("rnbqkb1r/ppp1pp1p/6p1/3n4/3PP3/2N5/PP3PPP/R1BQKBNR", Arrays.asList("g8c3", "d7d6"));
        BOOK_ENTRIES.put("rnbqkb1r/pp1ppppp/5n2/2pP4/2P5/8/PP2PPPP/RNBQKBNR", Arrays.asList("e7e6", "g7g6"));
        BOOK_ENTRIES.put("rnbqkbnr/ppppp1pp/8/5p2/3P4/8/PPP1PPPP/RNBQKBNR", Arrays.asList("g2g3", "c2c4", "b1c3"));
        BOOK_ENTRIES.put("rnbqkb1r/ppppp1pp/5n2/5p2/3P4/6P1/PPP1PP1P/RNBQKBNR", Arrays.asList("f8g7", "e7e6"));
        BOOK_ENTRIES.put("rnbqkb1r/pppppppp/5n2/6B1/3P4/8/PPP1PPPP/RN1QKBNR", Arrays.asList("d7d5", "c7c5", "e7e6", "g7g6"));
        BOOK_ENTRIES.put("rnbqkbnr/pppppppp/8/8/2P5/8/PP1PPPPP/RNBQKBNR", Arrays.asList("e7e5", "g8f6", "c7c5", "e7e6"));
        BOOK_ENTRIES.put("rnbqkbnr/pppp1ppp/8/4p3/2P5/2N5/PP1PPPPP/R1BQKBNR", Arrays.asList("g8f6", "b8c6", "f8c5"));
        BOOK_ENTRIES.put("rnbqkb1r/pppp1ppp/5n2/4p3/2P5/2N5/PP1PPPPP/R1BQKBNR", Arrays.asList("e2e4", "g2g3", "d2d4"));
        BOOK_ENTRIES.put("rnbqkb1r/pppppppp/5n2/2p5/2P5/2N5/PP1PPPPP/R1BQKBNR", Arrays.asList("g2g3", "e2e4", "g1f3"));
        BOOK_ENTRIES.put("rnbqkb1r/pppp1ppp/4pn2/8/2P5/2N5/PP1PPPPP/R1BQKBNR", Arrays.asList("e2e4", "g2g3", "d2d4"));
        BOOK_ENTRIES.put("rnbqkbnr/pppppppp/8/8/8/5N2/PPPPPPPP/RNBQKB1R", Arrays.asList("d7d5", "g8f6", "c7c5", "e7e6"));
        BOOK_ENTRIES.put("rnbqkbnr/ppp1pppp/8/3p4/3P4/5N2/PPP1PPPP/RNBQKB1R", Arrays.asList("c2c4", "e2e3", "g2g3"));
        BOOK_ENTRIES.put("rnbqkb1r/pppppppp/5n2/8/8/5NP1/PPPPPP1P/RNBQKB1R", Arrays.asList("g7g6", "d7d5", "e7e6"));
        BOOK_ENTRIES.put("r1bqkbnr/pp1ppppp/2n5/2p5/2P5/5N2/PP1PPPPP/RNBQKB1R", Arrays.asList("b1c3", "g2g3", "d2d4"));
        BOOK_ENTRIES.put("rnbqkbnr/pp1p1ppp/4p3/2p5/8/5NP1/PPPPPP1P/RNBQKB1R", Arrays.asList("d2d4", "c2c4"));
    }

    private OpeningBook() {}

    /**
     * Tra cứu nhanh nước đi khai cuộc từ FEN.
     * @param fen FEN đầy đủ hoặc chuỗi vị trí quân.
     * @return AnalysisResult với depth=0, score=0 nếu có trong sách; null nếu không tìm thấy.
     */
    public static StockfishProcess.AnalysisResult getBookResult(String fen) {
        if (fen == null || fen.trim().isEmpty()) return null;
        String trimmed = fen.trim();
        String boardKey = trimmed.contains(" ") ? trimmed.split(" ")[0] : trimmed;
        List<String> rawMoves = BOOK_ENTRIES.get(boardKey);
        if (rawMoves == null || rawMoves.isEmpty()) return null;

        List<String> validMoves = new ArrayList<>();
        for (String m : rawMoves) {
            if (m != null && m.length() >= 4 && AutoMoveManager.isLegalMoveOnFen(trimmed, m)) {
                validMoves.add(m);
            }
        }
        if (validMoves.isEmpty()) return null;

        // Tính toán lượt và xoay vòng theo chuẩn Extension (_getInternalOpeningBookResult)
        int turnCount = 0;
        String[] parts = trimmed.split(" ");
        if (parts.length >= 6) {
            try {
                turnCount = Math.max(0, Integer.parseInt(parts[5]) - 1);
            } catch (Throwable ignored) {}
        }
        int pickIndex = turnCount % validMoves.size();
        List<String> rotated = new ArrayList<>();
        for (int i = pickIndex; i < validMoves.size(); i++) rotated.add(validMoves.get(i));
        for (int i = 0; i < pickIndex; i++) rotated.add(validMoves.get(i));

        StockfishProcess.AnalysisResult result = new StockfishProcess.AnalysisResult(
                rotated,
                0.0f,
                false,
                0,
                0,
                0,
                0,
                null,
                new ArrayList<>(rotated),
                new float[rotated.size()],
                new float[rotated.size()],
                new boolean[rotated.size()],
                new int[rotated.size()],
                0,
                false
        );
        return result;
    }

    public static boolean hasBookMove(String fen) {
        if (fen == null || fen.trim().isEmpty()) return false;
        String trimmed = fen.trim();
        String boardKey = trimmed.contains(" ") ? trimmed.split(" ")[0] : trimmed;
        return BOOK_ENTRIES.containsKey(boardKey);
    }

    public static int getBookSize() {
        return BOOK_ENTRIES.size();
    }
}
