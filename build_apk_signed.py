#!/usr/bin/env python3
"""
build_apk_signed.py - One-command signing and verification tool for NNVC protected APKs.

Signs any patched APK using the official NNVC release keystore (nnvc-release.jks)
with apksigner (v1, v2, v3 schemes) so that runtime Level 2 Cryptographic Coupling
matches the mathematical decryption key for WebAssembly engines.

Usage:
    python build_apk_signed.py <path_to_unsigned_or_patched_apk> [output_signed_apk]
"""

import sys
import os
import subprocess
import shutil

# Ensure cross-platform terminal encoding compatibility (prevent cp1252 crash)
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
        sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
KEYSTORE_PATH = os.environ.get("NNVC_KEYSTORE", os.path.join(SCRIPT_DIR, "nnvc-release.jks"))
KEYSTORE_PASS = "nnvc2026"
KEY_ALIAS = "nnvc"
KEY_PASS = "nnvc2026"
OFFICIAL_SHA256 = "77C494150F9640E329D2BA9E46F049784AAEE853151994C28F90C796DE022045"

def find_tool(name, default_path):
    if default_path and os.path.exists(default_path):
        return default_path
    which_path = shutil.which(name)
    if which_path:
        return which_path
    # Search common Android SDK locations
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if android_home:
        bt_dir = os.path.join(android_home, "build-tools")
        if os.path.exists(bt_dir):
            versions = sorted(os.listdir(bt_dir), reverse=True)
            for v in versions:
                cand = os.path.join(bt_dir, v, f"{name}.bat" if os.name == "nt" else name)
                if os.path.exists(cand):
                    return cand
                cand_exe = os.path.join(bt_dir, v, f"{name}.exe")
                if os.path.exists(cand_exe):
                    return cand_exe
    return None

def inject_patched_dex_if_needed(apk_path):
    import zipfile
    patched_dex_path = os.path.join(SCRIPT_DIR, "patches", "patched_classes9.dex")
    if not os.path.exists(patched_dex_path):
        patched_dex_path = os.path.join(os.path.dirname(SCRIPT_DIR), "project", "patches", "patched_classes9.dex")

    ext_mpe_path = os.path.join(SCRIPT_DIR, "extensions", "extension.mpe")
    if not os.path.exists(ext_mpe_path):
        ext_mpe_path = os.path.join(os.path.dirname(SCRIPT_DIR), "project", "extensions", "extension.mpe")

    updates = {}
    if os.path.exists(patched_dex_path):
        with open(patched_dex_path, "rb") as pf:
            updates["classes9.dex"] = pf.read()
    if os.path.exists(ext_mpe_path):
        with open(ext_mpe_path, "rb") as ef:
            updates["classes12.dex"] = ef.read()

    need_update = {}
    with zipfile.ZipFile(apk_path, "r") as z:
        names = z.namelist()
        for dex_name, dex_bytes in updates.items():
            if dex_name in names:
                if z.read(dex_name) != dex_bytes:
                    need_update[dex_name] = dex_bytes
            else:
                need_update[dex_name] = dex_bytes

    if not need_update:
        print("[OK] All DEX files (classes9.dex, classes12.dex) are already up to date.")
        return

    for dex_name in need_update:
        print(f"[*] Injecting updated {dex_name} into APK...")
    temp_apk = apk_path + ".dex_patch.tmp"
    with zipfile.ZipFile(apk_path, "r") as zin:
        with zipfile.ZipFile(temp_apk, "w", compression=zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                if item.filename in need_update:
                    zout.writestr(item, need_update[item.filename])
                else:
                    zout.writestr(item, zin.read(item.filename))
            for dex_name, dex_bytes in need_update.items():
                if dex_name not in zin.namelist():
                    zout.writestr(dex_name, dex_bytes)

    os.remove(apk_path)
    os.rename(temp_apk, apk_path)
    print(f"[OK] {list(need_update.keys())} successfully injected into APK!")

def sign_apk(input_apk, output_apk=None):
    if not os.path.exists(input_apk):
        print(f"[ERROR] Input APK not found: {input_apk}")
        sys.exit(1)

    if not os.path.exists(KEYSTORE_PATH):
        print(f"[ERROR] Keystore not found: {KEYSTORE_PATH}")
        sys.exit(1)

    inject_patched_dex_if_needed(input_apk)

    apksigner_cmd = find_tool("apksigner", r"C:\Android\build-tools\android-37.0\apksigner.bat")
    if not apksigner_cmd:
        print("[ERROR] apksigner not found in build-tools or PATH.")
        sys.exit(1)

    zipalign_cmd = find_tool("zipalign", r"C:\Android\build-tools\android-37.0\zipalign.exe")

    if output_apk is None:
        base, ext = os.path.splitext(input_apk)
        output_apk = f"{base}-nnvc-signed{ext}"

    # Step 1: Zipalign if available
    aligned_apk = output_apk + ".aligned.tmp"
    target_to_sign = output_apk
    if zipalign_cmd:
        print(f"[1/4] Running zipalign 4-byte page alignment on {input_apk}...")
        za_cmd = [zipalign_cmd, "-p", "-f", "4", input_apk, aligned_apk]
        za_res = subprocess.run(za_cmd, capture_output=True, text=True)
        if za_res.returncode == 0 and os.path.exists(aligned_apk):
            print("[OK] Zipalign completed successfully!")
            if os.path.exists(output_apk):
                os.remove(output_apk)
            os.rename(aligned_apk, output_apk)
        else:
            if os.path.exists(aligned_apk):
                os.remove(aligned_apk)
            print(f"[WARNING] zipalign failed or skipped ({za_res.stderr.strip()}), copying raw APK...")
            shutil.copy2(input_apk, output_apk)
    else:
        print(f"[1/4] Copying {input_apk} -> {output_apk} (zipalign not found)...")
        shutil.copy2(input_apk, output_apk)

    # Step 2: Sign APK
    print(f"[2/4] Signing APK with official NNVC release key ({KEY_ALIAS})...")
    sign_cmd = [
        apksigner_cmd, "sign",
        "--ks", KEYSTORE_PATH,
        "--ks-pass", f"pass:{KEYSTORE_PASS}",
        "--ks-key-alias", KEY_ALIAS,
        "--key-pass", f"pass:{KEY_PASS}",
        "--min-sdk-version", "21",
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        output_apk
    ]
    res = subprocess.run(sign_cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"[ERROR] Signing failed:\n{res.stderr}")
        sys.exit(1)
    print("[OK] APK signed successfully (Schemes v1, v2, v3 active)!")

    # Step 3: Verify APK signature
    print("[3/4] Verifying APK signature and certificate SHA-256...")
    verify_cmd = [apksigner_cmd, "verify", "--verbose", "--print-certs", "--min-sdk-version", "21", output_apk]
    vres = subprocess.run(verify_cmd, capture_output=True, text=True)
    if vres.returncode != 0:
        print(f"[ERROR] Verification failed:\n{vres.stderr}")
        sys.exit(1)

    output_text = vres.stdout
    print(output_text.strip())

    # Step 4: Verify certificate fingerprint match
    clean_out = output_text.replace(":", "").replace(" ", "").upper()
    if OFFICIAL_SHA256 in clean_out:
        print("\n[4/4] Cryptographic Coupling Validation:")
        print("=================================================================")
        print("[SUCCESS] APK is signed with official NNVC release certificate!")
        print(f"[*] Certificate SHA-256: {OFFICIAL_SHA256}")
        print("[*] Level 2 Cryptographic Coupling: ACTIVE & MATHEMATICALLY BOUND.")
        print(f"[*] Protected APK ready for installation: {output_apk}")
        print("=================================================================")
    else:
        print("\n[!] WARNING: APK signature SHA-256 does NOT match official fingerprint!")
        print("WebAssembly engines will fail to decrypt at runtime until re-signed.")

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    inp = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else None
    sign_apk(inp, out)

