/*
 * Copyright 2026 PrathxmOp
 * https://github.com/PrathxmOp/Prathxm-Patches
 */

package app.prathxm.chess.patches.stockfish

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprint 1 – CBViewModelStateImpl.m() (position setter)
//
// We match on the setter method in CBViewModelStateImpl that accepts the
// generic POSITION parameter (erased to Lcom/chess/chessboard/variants/d;)
// and returns void.
// ─────────────────────────────────────────────────────────────────────────────
object PositionSetterFingerprint : Fingerprint(
    custom = { method, classDef ->
        if (classDef.type == "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;") {
            val positionType = classDef.methods.find { it.name == "getPosition" }?.returnType
            positionType != null &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == positionType &&
                method.returnType == "V" &&
                method.name != "<init>"
        } else {
            false
        }
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprint 1b – ChessBoardView.setPosition / CBViewModelStateImpl.<init>
//
// Matches initial position setup (Move 0 / New Game) so extension is notified
// immediately without having to make a move.
// ─────────────────────────────────────────────────────────────────────────────
object ChessBoardViewSetPositionFingerprint : Fingerprint(
    custom = { method, classDef ->
        if (classDef.type == "Lcom/chess/chessboard/view/ChessBoardView;" || classDef.type == "Lcom/chess/chessboard/v2/ChessBoardView;") {
            (method.name == "setPosition" || method.name == "setPositionWithPremoves") &&
                method.parameterTypes.isNotEmpty() &&
                method.returnType == "V"
        } else if (classDef.type == "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;") {
            method.name == "<init>" && method.parameterTypes.any { paramType ->
                val posType = classDef.methods.find { it.name == "getPosition" }?.returnType
                posType != null && posType == paramType
            }
        } else {
            false
        }
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprint 2 – CBViewModelStateImpl setMoveArrows(List<HintArrow>)
//
// Matched by body rather than by obfuscated name (G2 in 4.10.17): the (List)V setter that
// writes delegated property slot 0xb. moveArrows is the 12th (index 0xb) delegated property
// of CBViewModelStateImpl; the only other slot-0xb accessor is the getter, which has a
// different signature.
// ─────────────────────────────────────────────────────────────────────────────
object SetMoveArrowsFingerprint : Fingerprint(
    definingClass = "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;",
    returnType = "V",
    parameters = listOf("Ljava/util/List;"),
    custom = { method, _ ->
        val insns = method.implementation?.instructions?.toList() ?: emptyList()
        // Body: iget-object q; sget-object t; const/16 v2, 0xb; aget-object; invoke-interface setValue; return-void
        insns.any { it.opcode.name == "const/16" && (it as? com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction)?.narrowLiteral == 0xb } &&
            insns.any { i ->
                val ref = (i as? com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction)?.reference
                ref is com.android.tools.smali.dexlib2.iface.reference.MethodReference && ref.name == "setValue"
            }
    }
)

// ─────────────────────────────────────────────────────────────────────────────
// Fingerprint 3 – CBViewModelStateImpl.getPosition()
//
// Used so our extension can read the current board position / state.
// ─────────────────────────────────────────────────────────────────────────────
object GetPositionFingerprint : Fingerprint(
    definingClass = "Lcom/chess/chessboard/vm/movesinput/CBViewModelStateImpl;",
    name = "getPosition",
    returnType = "L",  // returns com.chess.chessboard.variants.Position (obfuscated)
    parameters = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
object OptionalPaintersCompanionBFingerprint : Fingerprint(
    custom = { method, classDef ->
        classDef.type.contains("ChessBoardViewOptionalPainterType") &&
            method.name == "b" &&
            method.parameterTypes.size == 7 &&
            method.parameterTypes[3] == "[Lcom/chess/internal/utils/chessboard/ChessBoardViewOptionalPainterType;"
    }
)

object MainApplicationOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/chess/MainApplication;",
    name = "onCreate",
    parameters = listOf(),
    returnType = "V"
)

