#!/usr/bin/env python3
"""
build_clean_v1.py - Builds clean patched Chess.com APK directly from pristine original APK.

Keeps 100% original:
- classes.dex, classes2.dex, classes3.dex, classes4.dex, classes6.dex, classes7.dex,
  classes9.dex (Signup / Google Login), classes10.dex, classes11.dex
- Original Signup flow, Lessons tab, Bots, Session, Quotas.

Applies ONLY:
- classes5.dex: MainApplication.onCreate() hook to load NNVC extension
- classes8.dex: Ad-Free patch in LoginData & UserData (returns show_ads=false)
- classes12.dex: NNVC Extension bytecode (extension.mpe)
- Assets: WASM engines (Stockfish 18, Komodo 3.3, Torch) + 58 coaches + libstockfish.so
"""

import os
import sys
import zipfile
import shutil
import subprocess

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
ROOT_DIR = os.path.dirname(SCRIPT_DIR)

ORIG_APK = os.path.join(ROOT_DIR, "apk", "original", "chess_4.10.20.apk")
PATCHED_C5 = os.path.join(SCRIPT_DIR, "patches", "patched_classes5.dex")
PATCHED_C8 = os.path.join(SCRIPT_DIR, "patches", "patched_classes8_clean.dex")
PATCHED_C9 = os.path.join(SCRIPT_DIR, "patches", "patched_classes9_clean.dex")
EXT_MPE = os.path.join(SCRIPT_DIR, "extensions", "extension.mpe")
KEYSTORE = os.path.join(SCRIPT_DIR, "nnvc-release.jks")

PREV_SIGNED_APK = os.path.join(ROOT_DIR, "apk", "patched", "v1.0.0", "chess_4.10.20_nnvc_signed.apk")
OUT_DIR = os.path.join(ROOT_DIR, "apk", "patched", "v1.0.0")
TEMP_UNALIGNED = os.path.join(OUT_DIR, "temp_clean_unaligned.apk")
TARGET_SIGNED = os.path.join(OUT_DIR, "chess_4.10.20_nnvc_signed.apk")
TARGET_MOD = os.path.join(OUT_DIR, "chess_4.10.20_nnvc_mod.apk")

def find_tool(name, default_path):
    if default_path and os.path.exists(default_path):
        return default_path
    which_path = shutil.which(name)
    if which_path:
        return which_path
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

def main():
    print("=" * 60)
    print("Building Clean 100% Original-Base NNVC Modded APK")
    print("=" * 60)

    assert os.path.exists(ORIG_APK), f"Original APK missing: {ORIG_APK}"
    assert os.path.exists(PATCHED_C5), f"Patched classes5.dex missing: {PATCHED_C5}"
    assert os.path.exists(PATCHED_C8), f"Patched classes8.dex missing: {PATCHED_C8}"
    assert os.path.exists(PATCHED_C9), f"Patched classes9.dex missing: {PATCHED_C9}"
    assert os.path.exists(EXT_MPE), f"extension.mpe missing: {EXT_MPE}"
    assert os.path.exists(KEYSTORE), f"Keystore missing: {KEYSTORE}"

    with open(PATCHED_C5, "rb") as f:
        c5_bytes = f.read()
    with open(PATCHED_C8, "rb") as f:
        c8_bytes = f.read()
    with open(PATCHED_C9, "rb") as f:
        c9_bytes = f.read()
    with open(EXT_MPE, "rb") as f:
        c12_bytes = f.read()

    # Read extra 70 files from previous signed APK
    extra_files = {}
    with zipfile.ZipFile(PREV_SIGNED_APK, "r") as zs:
        for item in zs.namelist():
            if item.startswith("assets/coaches/") or \
               item.startswith("assets/komodo/") or \
               item.startswith("assets/stockfish18/") or \
               item.startswith("assets/torch/") or \
               item in ("lib/arm64-v8a/libstockfish.so", "lib/armeabi-v7a/libstockfish.so"):
                extra_files[item] = zs.read(item)

    print(f"Loaded {len(extra_files)} extra NNVC asset and native library files.")

    # Build unaligned APK
    print(f"Creating unaligned clean APK at {TEMP_UNALIGNED}...")
    if os.path.exists(TEMP_UNALIGNED):
        os.remove(TEMP_UNALIGNED)

    with zipfile.ZipFile(ORIG_APK, "r") as zin:
        with zipfile.ZipFile(TEMP_UNALIGNED, "w", compression=zipfile.ZIP_DEFLATED) as zout:
            for item in zin.infolist():
                fname = item.filename
                # Skip original signature files
                if fname.startswith("META-INF/") and (fname.endswith(".SF") or fname.endswith(".RSA") or fname.endswith(".MF")):
                    continue

                if fname == "classes5.dex":
                    print("  Replacing classes5.dex with clean patched entrypoint...")
                    zout.writestr(item, c5_bytes)
                elif fname == "classes8.dex":
                    print("  Replacing classes8.dex with clean Ad-Free & anti-logout patched version...")
                    zout.writestr(item, c8_bytes)
                elif fname == "classes9.dex":
                    print("  Replacing classes9.dex with clean Signup fix...")
                    zout.writestr(item, c9_bytes)
                else:
                    zout.writestr(item, zin.read(fname))

            # Add classes12.dex
            print("  Adding classes12.dex (NNVC Extension)...")
            zout.writestr("classes12.dex", c12_bytes)

            # Add extra assets
            print("  Adding engine assets and native libraries...")
            for fname, fbytes in extra_files.items():
                zout.writestr(fname, fbytes)

    print("Unaligned APK successfully packaged!")

    # Zipalign
    zipalign_cmd = find_tool("zipalign", r"C:\Android\build-tools\android-37.0\zipalign.exe")
    assert zipalign_cmd, "zipalign not found!"

    temp_aligned = TEMP_UNALIGNED + ".aligned.apk"
    if os.path.exists(temp_aligned):
        os.remove(temp_aligned)

    print(f"Running zipalign: {zipalign_cmd} -p -f 4 ...")
    cmd_za = [zipalign_cmd, "-p", "-f", "4", TEMP_UNALIGNED, temp_aligned]
    res_za = subprocess.run(cmd_za, capture_output=True, text=True)
    if res_za.returncode != 0:
        print("Zipalign error:", res_za.stderr)
        sys.exit(1)

    # Sign with apksigner
    apksigner_cmd = find_tool("apksigner", r"C:\Android\build-tools\android-37.0\apksigner.bat")
    assert apksigner_cmd, "apksigner not found!"

    print(f"Signing APK with {KEYSTORE}...")
    cmd_sign = [
        apksigner_cmd, "sign",
        "--ks", KEYSTORE,
        "--ks-pass", "pass:nnvc2026",
        "--ks-key-alias", "nnvc",
        "--key-pass", "pass:nnvc2026",
        "--v1-signing-enabled", "true",
        "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "true",
        "--out", TARGET_SIGNED,
        temp_aligned
    ]
    res_sign = subprocess.run(cmd_sign, capture_output=True, text=True)
    if res_sign.returncode != 0:
        print("Signing error:", res_sign.stderr)
        sys.exit(1)

    print("Verifying signed APK signature...")
    cmd_verify = [apksigner_cmd, "verify", "--print-certs", TARGET_SIGNED]
    res_ver = subprocess.run(cmd_verify, capture_output=True, text=True)
    print(res_ver.stdout)
    assert "77c494150f9640e329d2ba9e46f049784aaee853151994c28f90c796de022045" in res_ver.stdout.lower()

    # Clean up temporary files
    if os.path.exists(TEMP_UNALIGNED):
        os.remove(TEMP_UNALIGNED)
    if os.path.exists(temp_aligned):
        os.remove(temp_aligned)

    # Copy to TARGET_MOD
    shutil.copy2(TARGET_SIGNED, TARGET_MOD)
    print(f"[OK] Clean signed APK ready at: {TARGET_SIGNED}")
    print(f"[OK] Copied to: {TARGET_MOD}")
    print(f"[OK] Size: {os.path.getsize(TARGET_SIGNED)} bytes")

if __name__ == "__main__":
    main()
