#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Sign and verify the RCBBoard release APK. Used by .github/workflows/release.yml.

The keystore and passwords come from environment variables and are passed to the
signing tools only as a file path and through ``env:`` references, never on the
command line or in the output:

  RCB_KEYSTORE           path to the existing release keystore (decoded by the workflow)
  RCB_KEYSTORE_PASSWORD  keystore password
  RCB_KEY_ALIAS          key alias
  RCB_KEY_PASSWORD       key password

Steps: zipalign + apksigner. Afterwards the APK is verified: valid v1 and v2
signatures, exactly the expected signing certificate (SHA-256), and the package name,
version, minimum SDK and the four ABIs of the universal build, so that it installs as
an update over existing RCBBoard installations. Nothing is published if any check fails.

With --aab, the Android App Bundle for Google Play is signed too, with the same key
(jarsigner, passwords again only through ``:env`` references) and verified: a valid
JAR signature from exactly the expected certificate, ``bundletool validate``, and the
package name, version and four ABIs from the bundle's manifest.
"""
from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import sys
import tempfile
import zipfile

SECRET_VARS = ("RCB_KEYSTORE", "RCB_KEYSTORE_PASSWORD", "RCB_KEY_ALIAS", "RCB_KEY_PASSWORD")
EXPECTED_ABIS = {"armeabi-v7a", "arm64-v8a", "x86", "x86_64"}


class CheckError(Exception):
    pass


def run(cmd: list[str], what: str, stdout_only: bool = False) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True)
    output = result.stdout + result.stderr
    if result.returncode != 0:
        raise CheckError(f"{what} failed (exit {result.returncode}):\n{output.strip()}")
    # stdout_only: values printed by a tool, without warnings the JVM may write to stderr
    return result.stdout if stdout_only else output


def tool(build_tools: str, name: str) -> str:
    path = os.path.join(build_tools, name)
    if not os.access(path, os.X_OK):
        raise CheckError(f"{name} not found in {build_tools}")
    return path


def normalise_fingerprint(value: str) -> str:
    return value.replace(":", "").strip().lower()


def sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def check_zip(path: str) -> None:
    try:
        with zipfile.ZipFile(path) as z:
            bad = z.testzip()
    except zipfile.BadZipFile as error:
        raise CheckError(f"{os.path.basename(path)} is not a valid ZIP file: {error}")
    if bad:
        raise CheckError(f"{os.path.basename(path)}: corrupt entry {bad}")


def check_secrets() -> None:
    missing = [name for name in SECRET_VARS if not os.environ.get(name)]
    if missing:
        raise CheckError("signing credentials are missing: " + ", ".join(missing)
                         + ". Configure the secrets of the 'release' environment (docs/ci-cd.md).")
    if not os.path.isfile(os.environ["RCB_KEYSTORE"]) or os.path.getsize(os.environ["RCB_KEYSTORE"]) == 0:
        raise CheckError("the decoded keystore file is missing or empty; check ANDROID_KEYSTORE_BASE64")


def sign_apk(bt: str, unsigned: str, out: str) -> None:
    with tempfile.TemporaryDirectory() as tmp:
        aligned = os.path.join(tmp, "aligned.apk")
        run([tool(bt, "zipalign"), "-p", "-f", "4", unsigned, aligned], "zipalign")
        run([tool(bt, "apksigner"), "sign",
             "--ks", os.environ["RCB_KEYSTORE"],
             "--ks-key-alias", os.environ["RCB_KEY_ALIAS"],
             "--ks-pass", "env:RCB_KEYSTORE_PASSWORD",
             "--key-pass", "env:RCB_KEY_PASSWORD",
             "--out", out, aligned], "apksigner sign")


def sign_aab(jdk_bin: str, unsigned: str, out: str) -> None:
    run([tool(jdk_bin, "jarsigner"),
         "-keystore", os.environ["RCB_KEYSTORE"],
         "-storepass:env", "RCB_KEYSTORE_PASSWORD",
         "-keypass:env", "RCB_KEY_PASSWORD",
         "-signedjar", out, unsigned, os.environ["RCB_KEY_ALIAS"]], "jarsigner sign")


def verify_aab(jdk_bin: str, bundletool_jar: str, aab: str, expected_cert: str, package: str,
               version_code: int, version_name: str) -> None:
    check_zip(aab)
    out = run([tool(jdk_bin, "jarsigner"), "-verify", aab], "jarsigner verify")
    if "jar verified." not in out:
        raise CheckError("jarsigner did not report 'jar verified.' for the App Bundle")
    certs = run([tool(jdk_bin, "keytool"), "-printcert", "-jarfile", aab], "keytool printcert")
    signers = [normalise_fingerprint(f) for f in re.findall(r"SHA256:\s*([0-9A-Fa-f:]+)", certs)]
    if signers != [expected_cert]:
        raise CheckError(f"App Bundle signing certificate is {signers}, expected [{expected_cert}]")
    java = tool(jdk_bin, "java")
    run([java, "-jar", bundletool_jar, "validate", "--bundle", aab], "bundletool validate")
    found = []
    for xpath in ("/manifest/@package", "/manifest/@android:versionCode", "/manifest/@android:versionName"):
        found.append(run([java, "-jar", bundletool_jar, "dump", "manifest", "--bundle", aab, "--xpath", xpath],
                         f"bundletool dump {xpath}", stdout_only=True).strip())
    if tuple(found) != (package, str(version_code), version_name):
        raise CheckError(f"App Bundle identity is {tuple(found)}, expected {(package, str(version_code), version_name)}")
    with zipfile.ZipFile(aab) as z:
        abis = {n.split("/")[2] for n in z.namelist() if n.startswith("base/lib/") and n.count("/") >= 3}
    if abis != EXPECTED_ABIS:
        raise CheckError(f"App Bundle ABIs {sorted(abis)}, expected {sorted(EXPECTED_ABIS)}")


def verify_apk(bt: str, apk: str, expected_cert: str, package: str, version_code: int,
               version_name: str, min_sdk: int) -> None:
    check_zip(apk)
    run([tool(bt, "zipalign"), "-c", "-p", "4", apk], "zipalign check")
    out = run([tool(bt, "apksigner"), "verify", "--verbose", "--print-certs", apk], "apksigner verify")
    if not re.search(r"^Verifies$", out, re.M):
        raise CheckError("apksigner did not report 'Verifies'")
    for scheme in ("v1", "v2"):
        if not re.search(rf"Verified using {scheme} scheme.*: true", out):
            raise CheckError(f"APK is not signed with the {scheme} scheme (needed for Android 5.0+)")
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$", out, re.M)
    if signers != [expected_cert]:
        raise CheckError(f"APK signing certificate is {signers}, expected [{expected_cert}]")

    badging = run([tool(bt, "aapt2"), "dump", "badging", apk], "aapt2 dump badging")
    pkg = re.search(r"^package: name='([^']*)' versionCode='([^']*)' versionName='([^']*)'", badging, re.M)
    if not pkg:
        raise CheckError("could not read the package line from the APK")
    found = (pkg.group(1), pkg.group(2), pkg.group(3))
    if found != (package, str(version_code), version_name):
        raise CheckError(f"APK identity is {found}, expected {(package, str(version_code), version_name)}")
    sdk = re.search(r"^(?:minSdkVersion|sdkVersion):'(\d+)'", badging, re.M)
    if not sdk or int(sdk.group(1)) != min_sdk:
        raise CheckError(f"APK minimum SDK is {sdk.group(1) if sdk else 'unknown'}, expected {min_sdk}")
    abis = re.search(r"^native-code: (.*)$", badging, re.M)
    found_abis = set(re.findall(r"'([^']+)'", abis.group(1))) if abis else set()
    if found_abis != EXPECTED_ABIS:
        raise CheckError(f"APK is not universal: ABIs {sorted(found_abis)}, expected {sorted(EXPECTED_ABIS)}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Sign and verify the RCBBoard release APK.")
    parser.add_argument("--apk", required=True, help="unsigned release APK from Gradle")
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--name", required=True, help="file name without extension, e.g. RCBBoard-4.3.1")
    parser.add_argument("--build-tools", required=True, help="Android SDK build-tools folder")
    parser.add_argument("--expected-cert-sha256", required=True)
    parser.add_argument("--package", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--min-sdk", required=True, type=int)
    parser.add_argument("--aab", help="unsigned release App Bundle from Gradle (optional)")
    parser.add_argument("--bundletool", help="bundletool jar, needed with --aab")
    parser.add_argument("--jdk-bin", default=os.path.join(os.environ.get("JAVA_HOME", ""), "bin"),
                        help="JDK bin folder with jarsigner, keytool and java (default: $JAVA_HOME/bin)")
    args = parser.parse_args()
    if args.aab and not args.bundletool:
        parser.error("--aab needs --bundletool")

    expected = normalise_fingerprint(args.expected_cert_sha256)
    os.makedirs(args.out_dir, exist_ok=True)
    apk_out = os.path.join(args.out_dir, args.name + ".apk")
    aab_out = os.path.join(args.out_dir, args.name + ".aab") if args.aab else None
    created = False  # only clean up files that this run created
    try:
        check_secrets()
        if not os.path.isfile(args.apk) or os.path.getsize(args.apk) == 0:
            raise CheckError(f"build output {args.apk} is missing or empty")
        if args.aab and (not os.path.isfile(args.aab) or os.path.getsize(args.aab) == 0):
            raise CheckError(f"build output {args.aab} is missing or empty")
        if os.path.exists(apk_out) or os.path.exists(apk_out + ".idsig") or (aab_out and os.path.exists(aab_out)):
            raise CheckError("output file already exists; refusing to overwrite")
        created = True
        sign_apk(args.build_tools, args.apk, apk_out)
        verify_apk(args.build_tools, apk_out, expected, args.package, args.version_code,
                   args.version_name, args.min_sdk)
        print(f"APK signed and verified: {os.path.basename(apk_out)}")
        if aab_out:
            sign_aab(args.jdk_bin, args.aab, aab_out)
            verify_aab(args.jdk_bin, args.bundletool, aab_out, expected, args.package, args.version_code,
                       args.version_name)
            print(f"App Bundle signed and verified: {os.path.basename(aab_out)}")
    except CheckError as error:
        for path in (apk_out, apk_out + ".idsig") + ((aab_out,) if aab_out else ()):
            if created and os.path.exists(path):
                os.remove(path)
        print(f"::error title=Signing::{error}")
        return 1

    lines = [f"{sha256_file(path)}  {os.path.basename(path)}" for path in (apk_out, aab_out) if path]
    with open(os.path.join(args.out_dir, "SHA256SUMS.txt"), "w", encoding="utf-8") as sums:
        sums.write("\n".join(lines) + "\n")
    print("\n".join(lines))
    print(f"signing certificate SHA-256: {expected}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
