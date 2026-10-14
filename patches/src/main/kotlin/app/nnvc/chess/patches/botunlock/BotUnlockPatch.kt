/*
 * Copyright 2026 NNVC
 * https://github.com/NNVC/NNVC-Patches
 */

package app.nnvc.chess.patches.botunlock

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.nnvc.chess.patches.shared.Constants.COMPATIBILITY_CHESS

private const val EXTENSION_CLASS = "Lapp/nnvc/chess/extension/stockfish/StockfishExtension;"

val botUnlockPatch = bytecodePatch(
    name = "Unlock All Bots",
    description = "Unlocks all premium and restricted bots in the Versus Bots feature.",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    extendWith("extensions/extension.mpe")

    execute {
        // Legacy Bot.PersonalityBot checks removed in 4.10.x (handled via ProtoBotPersonality & LockedBots)
        // BotPersonalityBotGetCanPlayFingerprint and BotPersonalityBotGetRequiresActivationFingerprint omitted

        // BotPersonality (Proto).getCan_play() → Boolean (boxed)
        ProtoBotPersonalityGetCanPlayFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->TRUE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // BotPersonality (Proto).getEnabled() → boolean
        ProtoBotPersonalityGetEnabledFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 1
                return v0
            """
        )

        // BotPersonality (Proto).getPremium() → Boolean (boxed)
        ProtoBotPersonalityGetPremiumFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // h9 (LockedBots).a(Bot) → boolean (isLocked)
        LockedBotsCheckFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0
                return v0
            """
        )
    }
}
