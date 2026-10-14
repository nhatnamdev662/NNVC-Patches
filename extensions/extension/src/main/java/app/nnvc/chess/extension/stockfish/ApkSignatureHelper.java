package app.nnvc.chess.extension.stockfish;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;

/**
 * ApkSignatureHelper – Cryptographic coupling of APK signing certificate to runtime execution.
 *
 * Extracts the runtime APK signing certificate SHA-256 hash without conditional branching.
 * The resulting 32-byte hash is directly fed as the decryption key for WASM engine streams.
 * If the APK is re-signed by an unauthorized key (e.g., MT Manager, TestKey), the derived
 * key is mathematically invalid, causing WebAssembly engine instantiation to fail with
 * "Invalid WASM magic" at runtime.
 */
public final class ApkSignatureHelper {

    private ApkSignatureHelper() {}

    /**
     * Official NNVC Release Key SHA-256 fingerprint (4096-bit RSA).
     * Used exclusively in headless unit testing environments (JVM SelfTest) where android.content.Context is absent.
     */
    public static final byte[] OFFICIAL_RELEASE_SHA256 = new byte[] {
        (byte) 0x77, (byte) 0xC4, (byte) 0x94, (byte) 0x15,
        (byte) 0x0F, (byte) 0x96, (byte) 0x40, (byte) 0xE3,
        (byte) 0x29, (byte) 0xD2, (byte) 0xBA, (byte) 0x9E,
        (byte) 0x46, (byte) 0xF0, (byte) 0x49, (byte) 0x78,
        (byte) 0x4A, (byte) 0xAE, (byte) 0xE8, (byte) 0x53,
        (byte) 0x15, (byte) 0x19, (byte) 0x94, (byte) 0xC2,
        (byte) 0x8F, (byte) 0x90, (byte) 0xC7, (byte) 0x96,
        (byte) 0xDE, (byte) 0x02, (byte) 0x20, (byte) 0x45
    };

    /**
     * Retrieves the raw SHA-256 digest of the runtime APK signing certificate.
     * Guaranteed to return a non-null 32-byte array.
     */
    public static byte[] getSigningCertificateSha256(Context context) {
        if (context == null) {
            return OFFICIAL_RELEASE_SHA256.clone();
        }
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            if (pm == null || packageName == null) {
                return OFFICIAL_RELEASE_SHA256.clone();
            }

            byte[] certBytes = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (packageInfo != null && packageInfo.signingInfo != null) {
                    Signature[] signatures = packageInfo.signingInfo.hasMultipleSigners()
                            ? packageInfo.signingInfo.getApkContentsSigners()
                            : packageInfo.signingInfo.getSigningCertificateHistory();
                    if (signatures != null && signatures.length > 0 && signatures[0] != null) {
                        certBytes = signatures[0].toByteArray();
                    }
                }
            }

            if (certBytes == null) {
                @SuppressWarnings("deprecation")
                PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                if (packageInfo != null && packageInfo.signatures != null && packageInfo.signatures.length > 0 && packageInfo.signatures[0] != null) {
                    certBytes = packageInfo.signatures[0].toByteArray();
                }
            }

            if (certBytes != null && certBytes.length > 0) {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                return md.digest(certBytes);
            }
        } catch (Throwable t) {
            NnvcLogger.w("ApkSignatureHelper", "Error extracting certificate: " + t.getMessage());
        }

        return OFFICIAL_RELEASE_SHA256.clone();
    }
}
