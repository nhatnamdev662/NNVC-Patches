/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.stockfish

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.prathxm.chess.patches.shared.Constants.COMPATIBILITY_CHESS

private const val EXTENSION_CLASS = "Lapp/prathxm/chess/extension/stockfish/StockfishExtension;"

private val stockfishResourcePatch = resourcePatch {
    execute {
        val arm64Dest = this@execute["lib/arm64-v8a/libstockfish.so"]
        arm64Dest.parentFile.mkdirs()
        object {}.javaClass.classLoader?.getResourceAsStream("stockfish/arm64-v8a/stockfish")?.use { input ->
            arm64Dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not find bundled arm64-v8a stockfish binary")

        val armv7Dest = this@execute["lib/armeabi-v7a/libstockfish.so"]
        armv7Dest.parentFile.mkdirs()
        object {}.javaClass.classLoader?.getResourceAsStream("stockfish/armeabi-v7a/stockfish")?.use { input ->
            armv7Dest.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not find bundled armeabi-v7a stockfish binary")
    }
}

val stockfishPatch = bytecodePatch(
    name = "Local Stockfish Analysis",
    description = "Enables local Stockfish engine for live match assistance & analysis.",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    dependsOn(stockfishResourcePatch)

    extendWith("extensions/extension.mpe")

    execute {
        // ─────────────────────────────────────────────────────────────────
        // Hook 1 – After applyMove, trigger engine analysis on the new FEN
        // ─────────────────────────────────────────────────────────────────
        PositionSetterFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                move-object/from16 v1, p1
                invoke-static {v0, v1}, $EXTENSION_CLASS->onBoardChanged(Ljava/lang/Object;Ljava/lang/Object;)V
            """
        )

        // ─────────────────────────────────────────────────────────────────
        // Hook 1b – Initial position setup (Move 0 / New Game)
        // ─────────────────────────────────────────────────────────────────
        ChessBoardViewSetPositionFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                invoke-static {v0}, $EXTENSION_CLASS->onInitialPositionLoaded(Ljava/lang/Object;)V
            """
        )

        // ─────────────────────────────────────────────────────────────────
        // Hook 2 – Expose the setMoveArrows method to our extension
        // ─────────────────────────────────────────────────────────────────
        SetMoveArrowsFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p0
                move-object/from16 v1, p1
                invoke-static {v0, v1}, $EXTENSION_CLASS->onArrowsChanged(Ljava/lang/Object;Ljava/util/List;)V
            """
        )

        // Hook 4 – Inject KEY_MOVE_HINTS into optional painters
        OptionalPaintersCompanionBFingerprint.method.addInstructions(
            0,
            """
                move-object/from16 v0, p4
                invoke-static {v0}, $EXTENSION_CLASS->ensureHintArrowsEnabled([Ljava/lang/Object;)[Ljava/lang/Object;
                move-result-object v0
                check-cast v0, [Lcom/chess/internal/utils/chessboard/ChessBoardViewOptionalPainterType;
                move-object/from16 p4, v0
            """
        )

        // ─────────────────────────────────────────────────────────────────
        // Hook 5 – Early initialization at Application startup
        // ─────────────────────────────────────────────────────────────────
        MainApplicationOnCreateFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->ensureEngineReady()V
            """
        )
    }
}

