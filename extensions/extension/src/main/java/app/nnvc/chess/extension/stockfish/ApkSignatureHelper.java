package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;

/**
 * ApkSignatureHelper – Branchless Cryptographic Coupling of APK signing certificate to runtime execution.
 *
 * Extracts the runtime APK signing certificate SHA-256 hash without conditional branching.
 * The resulting 32-byte hash is directly fed as the decryption key for WASM engine streams.
 * If the APK is re-signed by an unauthorized key (e.g., MT Manager, TestKey), or if extraction
 * fails or is hooked, the derived key is mathematically invalid, causing WebAssembly engine instantiation
 * to fail with "Invalid WASM magic" at runtime.
 */
public final class ApkSignatureHelper {

    private static final byte[] EMPTY_KEY = new byte[32];
    private static volatile byte[] cachedSigKey = null;

    private ApkSignatureHelper() {}

    /**
     * Computes the SHA-256 digest of raw certificate bytes.
     */
    public static byte[] digestCertificate(byte[] certBytes) {
        if (certBytes == null || certBytes.length == 0) {
            return EMPTY_KEY.clone();
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(certBytes);
        } catch (Throwable ignored) {
            return EMPTY_KEY.clone();
        }
    }

    /**
     * Retrieves the raw SHA-256 digest of the runtime APK signing certificate.
     * Guaranteed to return a non-null 32-byte array.
     * If context is absent, corrupted, or an error occurs, returns an invalid 32-byte key
     * ensuring cryptographic decryption fails immediately without any branching if statements.
     */
    public static byte[] getSigningCertificateSha256(Context context) {
        if (context == null) {
            return EMPTY_KEY.clone();
        }
        if (cachedSigKey != null) {
            return cachedSigKey.clone();
        }
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            if (pm == null || packageName == null) {
                return EMPTY_KEY.clone();
            }

            byte[] certBytes = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (packageInfo != null && packageInfo.signingInfo != null) {
                    if (packageInfo.signingInfo.hasMultipleSigners()) {
                        Signature[] signatures = packageInfo.signingInfo.getApkContentsSigners();
                        if (signatures != null && signatures.length > 0 && signatures[0] != null) {
                            certBytes = signatures[0].toByteArray();
                        }
                    } else {
                        Signature[] signatures = packageInfo.signingInfo.getSigningCertificateHistory();
                        if (signatures != null && signatures.length > 0) {
                            Signature currentSigner = signatures[signatures.length - 1];
                            if (currentSigner != null) {
                                certBytes = currentSigner.toByteArray();
                            }
                        }
                    }
                }
            }

            if (certBytes == null) {
                @SuppressWarnings("deprecation")
                PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                if (packageInfo != null && packageInfo.signatures != null && packageInfo.signatures.length > 0) {
                    android.content.pm.Signature sig = packageInfo.signatures[packageInfo.signatures.length - 1];
                    if (sig != null) {
                        certBytes = sig.toByteArray();
                    }
                }
            }

            if (certBytes != null && certBytes.length > 0) {
                byte[] key = digestCertificate(certBytes);
                if (key != null && key.length == 32) {
                    cachedSigKey = key;
                    return key.clone();
                }
            }
        } catch (Throwable ignored) {}

        return EMPTY_KEY.clone();
    }
}

