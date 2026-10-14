/*
 * Copyright 2026 NNVC
 * https://github.com/NNVC/NNVC-Patches
 */

package app.nnvc.chess.patches.adfree

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.nnvc.chess.patches.shared.Constants.COMPATIBILITY_CHESS

private const val EXTENSION_CLASS = "Lapp/nnvc/chess/extension/stockfish/StockfishExtension;"

val adFreePatch = bytecodePatch(
    name = "Ad-Free",
    description = "Removes advertisements",
    default = true
) {
    compatibleWith(COMPATIBILITY_CHESS)

    extendWith("extensions/extension.mpe")

    execute {
        // LoginData.getShow_ads() → boolean (primitive)
        LoginDataGetShowAdsFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0
                return v0
            """
        )

        // LoginData.getShow_interstitial_ads() → Boolean (boxed)
        LoginDataGetShowInterstitialAdsFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // UserData.getShow_ads() → Boolean (boxed)
        UserDataGetShowAdsFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // UserData.getShow_interstitial_ads() → Boolean (boxed)
        UserDataGetShowInterstitialAdsFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
                return-object v0
            """
        )

        // SharedPreferencesSessionStore.i() → boolean (isGuest = false when user has a session)
        // Fixes: Sign Up button showing in settings and missing chat button in games
        SessionStoreIsGuestFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0}, Lcom/chess/apputils/SharedPreferencesSessionStore;->getSession()Lcom/chess/net/model/LoginData;
                move-result-object v0
                if-eqz v0, :no_session
                invoke-virtual {v0}, Lcom/chess/net/model/LoginData;->getUsername()Ljava/lang/String;
                move-result-object v0
                if-eqz v0, :no_session
                invoke-virtual {v0}, Ljava/lang/String;->length()I
                move-result v0
                if-eqz v0, :no_session
                const/4 v0, 0
                return v0
                :no_session
                const/4 v0, 1
                return v0
            """
        )

        // LoginData.getPremium_status() → int (preserve original or return 0 for basic, never force Diamond badge)
        // UserData.getPremium_status() → PremiumStatus (preserve original, never force Diamond badge)


        // UserMembershipLevel.Companion.ofCode() -> fallback to BASIC if code unknown (e.g., "silver")
        UserMembershipLevelOfCodeFingerprint.method.addInstructions(
            0,
            """
                invoke-virtual {p0, p1}, Lcom/chess/entities/UserMembershipLevel${'$'}Companion;->ofCodeOrNull(Ljava/lang/String;)Lcom/chess/entities/UserMembershipLevel;
                move-result-object v0
                if-eqz v0, :cond_safe
                return-object v0
                :cond_safe
                sget-object v0, Lcom/chess/entities/UserMembershipLevel;->BASIC:Lcom/chess/entities/UserMembershipLevel;
                return-object v0
            """
        )
    }
}

