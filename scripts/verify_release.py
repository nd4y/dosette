#!/usr/bin/env python3
"""Check the actual phone/Wear release artifacts, without exposing signing secrets."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import shutil
import ssl
import struct
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "icu.nd4y.dosette"
MAX_CODE = 2_100_000_000
# Public certificate from the existing v0.13.1 release, independently checked
# with apksigner. Keep Obtainium upgrades and mixed Play/GitHub device pairs working.
RELEASE_CERTIFICATE = "678b5ad85a8cdea2b74654aef067f8266aa699e3caf44ca2e6b616c5b613f649"


def run(*args):
    return subprocess.check_output(
        [str(part) for arg in args for part in (arg if isinstance(arg, tuple) else (arg,))], text=True, encoding="utf-8", errors="replace", stderr=subprocess.STDOUT
    )


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sdk_tool(name):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    require(sdk, "Set ANDROID_HOME or ANDROID_SDK_ROOT")
    suffix = ".exe" if os.name == "nt" else ""
    if os.name == "nt" and name == "apksigner":
        suffix = ".bat"
    candidates = list((Path(sdk) / "build-tools").glob(f"*/{name}{suffix}"))
    require(candidates, f"Android build tool not found: {name}")
    path = max(candidates, key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)))
    if os.name == "nt" and name == "apksigner":
        return ("java", "-jar", path.parent / "lib/apksigner.jar")
    return path


def version_codes(version):
    match = re.fullmatch(r"v?(0|[1-9]\d*)\.(0|[1-9]\d?)\.(0|[1-9]\d?)", version)
    require(match, "Expected X.Y.Z, minor/patch 0..99, without leading zeroes")
    major, minor, patch = map(int, match.groups())
    base = major * 10_000 + minor * 100 + patch
    phone, wear = base * 10, base * 10 + 1
    require(base > 0 and wear <= MAX_CODE, "Version code outside Google Play range")
    return phone, wear


def proto_fields(data):
    # AAB manifests are aapt.pb.XmlNode, not binary APK XML.
    # Schema: AOSP frameworks/base/tools/aapt2/Resources.proto.
    offset = 0

    def varint():
        nonlocal offset
        value = 0
        for shift in range(0, 70, 7):
            require(offset < len(data), "Truncated manifest protobuf")
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value
        raise ValueError("Invalid manifest protobuf varint")

    while offset < len(data):
        key = varint()
        field, wire = key >> 3, key & 7
        require(field > 0, "Invalid manifest protobuf field")
        if wire == 0:
            yield field, varint()
        elif wire in (1, 2, 5):
            size = varint() if wire == 2 else (8 if wire == 1 else 4)
            require(offset + size <= len(data), "Truncated manifest protobuf value")
            yield field, data[offset:offset + size]
            offset += size
        else:
            raise ValueError(f"Unsupported manifest protobuf wire type: {wire}")


def bundle_identity(path):
    with zipfile.ZipFile(path) as archive:
        node = dict(proto_fields(archive.read("base/manifest/AndroidManifest.xml")))
    require(1 in node, "Bundle manifest has no XML root")
    element = list(proto_fields(node[1]))
    require(dict(element).get(3) == b"manifest", "Bundle root is not a manifest")
    attributes = {}
    for field, value in element:
        if field != 4:
            continue
        attribute = dict(proto_fields(value))
        name = attribute.get(2, b"").decode("utf-8")
        namespace = attribute.get(1, b"").decode("utf-8")
        if namespace not in ("", "http://schemas.android.com/apk/res/android"):
            continue
        raw = attribute.get(3)
        if raw is not None:
            attributes[name] = raw.decode("utf-8")
        elif 6 in attribute:  # XmlAttribute.compiled_item -> Item
            item = dict(proto_fields(attribute[6]))
            if 7 in item:  # Item.prim -> Primitive.int_decimal_value / int_hexadecimal_value
                primitive = dict(proto_fields(item[7]))
                attributes[name] = primitive.get(6, primitive.get(7))
            elif 2 in item:  # Item.str -> String.value
                attributes[name] = dict(proto_fields(item[2])).get(1, b"").decode("utf-8")
    require(all(key in attributes for key in ("package", "versionCode", "versionName")),
            "Bundle is missing package or version metadata")
    code = attributes["versionCode"]
    code = int(code, 0) if isinstance(code, str) and code.startswith("0x") else int(code)
    return attributes["package"], code, attributes["versionName"]

def jar_certificate(path):
    # Unlike APKs, AABs use JAR signing. Verify payload signatures, then compare
    # the certificate to both APKs. No passwords are needed for either operation.
    output = run("jarsigner", "-verify", "-J-Duser.language=en", path)
    require("jar verified." in output and "unsigned entries" not in output.lower(), f"Bundle is not fully JAR-signed: {path.name}")
    certificate = run("keytool", "-printcert", "-jarfile", path, "-rfc")
    blocks = re.findall(
        r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----",
        certificate, re.DOTALL,
    )
    require(blocks, f"No signing certificate in {path.name}")
    der = ssl.PEM_cert_to_DER_cert(blocks[0])
    return hashlib.sha256(der).hexdigest()


def require_phone_capability(resources):
    # Play services looks up this resource by name; no code reference protects
    # it from R8/resource shrinking. Also require the advertised value, rather
    # than merely accepting an empty array with the expected resource name.
    block = re.search(
        r"(?ms)^[ \t]*resource 0x[0-9a-fA-F]+ array/android_wear_capabilities[ \t]*\r?\n"
        r"(.*?)(?=^[ \t]*(?:resource 0x|type )|\Z)",
        resources,
    )
    require(block is not None, "Phone APK lost android_wear_capabilities during resource shrinking")
    require('"dosette_phone"' in block[1], "Phone APK does not advertise the dosette_phone capability")

def check_native_libraries(path):
    with zipfile.ZipFile(path) as archive:
        libraries = {}
        for name in archive.namelist():
            match = re.search(r"(?:^|/)lib/([^/]+)/([^/]+\.so)$", name)
            if not match:
                continue
            abi, filename = match.groups()
            libraries.setdefault(abi, set()).add(filename)
            if abi not in ("arm64-v8a", "x86_64"):
                continue
            data = archive.read(name)
            require(data[:4] == b"\x7fELF" and data[4] == 2, f"Invalid 64-bit ELF: {name}")
            endian = "<" if data[5] == 1 else ">"
            offset = struct.unpack_from(endian + "Q", data, 32)[0]
            entry_size, count = struct.unpack_from(endian + "HH", data, 54)
            for index in range(count):
                header = offset + index * entry_size
                segment_type = struct.unpack_from(endian + "I", data, header)[0]
                if segment_type == 1:  # PT_LOAD
                    alignment = struct.unpack_from(endian + "Q", data, header + 48)[0]
                    require(alignment >= 16_384, f"{name} needs 16 KB ELF page alignment")
        for narrow, wide in (("armeabi-v7a", "arm64-v8a"), ("x86", "x86_64")):
            missing = libraries.get(narrow, set()) - libraries.get(wide, set())
            require(not missing, f"{path.name}: native libraries missing {wide}: {sorted(missing)}")
        # Pure Java/Kotlin packages work on 32-bit and 64-bit devices.
        return sorted(libraries)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", help="Required for a distributable, non-debug release")
    parser.add_argument("--smoke", action="store_true", help="Allow unversioned debug-signed CI artifacts")
    args = parser.parse_args()
    require(bool(args.version) != args.smoke, "Choose exactly one of --version or --smoke")
    expected_codes = version_codes(args.version) if args.version else (1, 2)
    expected_name = args.version.removeprefix("v") if args.version else "0.0.0-dev"
    for tool in ("jarsigner", "keytool"):
        require(shutil.which(tool), f"{tool} is not in PATH")
    aapt2, apksigner, zipalign = (sdk_tool(name) for name in ("aapt2", "apksigner", "zipalign"))
    certificates = set()
    for module, code in zip(("app", "wear"), expected_codes):
        apk = ROOT / module / "build/outputs/apk/release" / f"{module}-release.apk"
        bundle = ROOT / module / "build/outputs/bundle/release" / f"{module}-release.aab"
        require(apk.is_file() and bundle.is_file(), f"Build {module} release APK and AAB first")
        require(bundle_identity(bundle) == (PACKAGE, code, expected_name), f"{module}: APK/AAB identity mismatch")
        badging = run(aapt2, "dump", "badging", apk)
        if module == "app":
            require_phone_capability(run(aapt2, "dump", "resources", apk))
        require(f"name='{PACKAGE}'" in badging.splitlines()[0], f"Wrong package in {module}")
        require(f"versionCode='{code}'" in badging.splitlines()[0], f"Wrong versionCode in {module}")
        require(f"versionName='{expected_name}'" in badging.splitlines()[0], f"Wrong versionName in {module}")
        require("application-debuggable" not in badging, f"{module} release is debuggable")
        require("android.permission.USE_EXACT_ALARM" not in badging, "Restricted USE_EXACT_ALARM remains")
        target = re.search(r"targetSdkVersion:'(\d+)'", badging)
        require(target and int(target[1]) >= (36 if module == "app" else 35), f"{module}: outdated target API")
        if module == "wear":
            require("uses-feature: name='android.hardware.type.watch'" in badging, "Watch feature must be required")
        signature = run(apksigner, "verify", "--verbose", "--print-certs", apk)
        fingerprints = {
            value.lower().replace(":", "")
            for value in re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", signature)
        }
        require(len(fingerprints) == 1, f"Expected one APK signing identity in {module}")
        fingerprint = fingerprints.pop()
        if not args.smoke:
            require("CN=Android Debug" not in signature, f"{module}: debug key cannot be released")
            require(fingerprint == RELEASE_CERTIFICATE, f"{module}: existing release key has changed")
        certificates.add(fingerprint)
        certificates.add(jar_certificate(bundle))
        run(zipalign, "-c", "-P", "16", "4", apk)
        abis = check_native_libraries(apk)
        require(abis == check_native_libraries(bundle), f"{module}: APK/AAB native ABIs differ")
        print(f"{module}: package={PACKAGE}, version={expected_name}, code={code}, native ABIs={abis or 'none'}")
    require(len(certificates) == 1, "Phone/Wear APKs and AABs must all share one signing certificate")
    print(f"Verified four artifacts; signing certificate SHA-256: {certificates.pop()}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError, zipfile.BadZipFile) as error:
        print(f"Release verification failed: {error}", file=sys.stderr)
        sys.exit(1)
