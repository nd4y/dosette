"""Regression checks for release admission rules, without an Android SDK."""
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

from verify_release import bundle_identity, check_native_libraries, proto_fields, version_codes


class ReleaseVersionTests(unittest.TestCase):
    def test_phone_and_watch_are_unique_and_upgrade_from_historical_release(self):
        phone, watch = version_codes("v0.13.2")
        self.assertEqual((phone, watch), (13020, 13021))
        self.assertGreater(phone, 1301)
        self.assertLess(watch, version_codes("0.13.3")[0])
        self.assertLess(version_codes("0.99.99")[1], version_codes("1.0.0")[0])

    def test_rejects_collisions_and_overflow(self):
        for value in ("0.0.0", "0.1.100", "0.100.0", "0.01.0", "01.0.0", "21000.0.0", "1.2.3-dev", "v1.2.3\n"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                version_codes(value)
        self.assertLessEqual(version_codes("20999.99.99")[1], 2_100_000_000)


class NativeLibraryTests(unittest.TestCase):
    def package(self, libraries):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        path = Path(temp.name) / "artifact.zip"
        with zipfile.ZipFile(path, "w") as archive:
            for name, content in libraries.items():
                archive.writestr(name, content)
        return path

    def elf(self, alignment):
        data = bytearray(120)
        data[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 1)
        struct.pack_into("<I", data, 64, 1)
        struct.pack_into("<Q", data, 112, alignment)
        return data

    def test_pure_kotlin_has_no_abi_restriction(self):
        self.assertEqual(check_native_libraries(self.package({"classes.dex": b"dex"})), [])

    def test_rejects_32_bit_only_dependency(self):
        with self.assertRaisesRegex(ValueError, "missing arm64"):
            check_native_libraries(self.package({"lib/armeabi-v7a/libdependency.so": b"elf"}))

    def test_rejects_4kb_native_alignment(self):
        with self.assertRaisesRegex(ValueError, "16 KB"):
            check_native_libraries(self.package({"base/lib/arm64-v8a/libdependency.so": self.elf(4096)}))

    def test_accepts_matching_32_and_64_bit_bundle_libraries(self):
        path = self.package({
            "base/lib/armeabi-v7a/libdependency.so": b"elf",
            "base/lib/arm64-v8a/libdependency.so": self.elf(16384),
        })
        self.assertEqual(check_native_libraries(path), ["arm64-v8a", "armeabi-v7a"])



class BundleManifestTests(unittest.TestCase):
    @staticmethod
    def varint(value):
        data = bytearray()
        while value > 127:
            data.append((value & 127) | 128)
            value >>= 7
        return bytes(data + bytes([value]))

    def field(self, number, value):
        if isinstance(value, int):
            return self.varint(number << 3) + self.varint(value)
        if isinstance(value, str):
            value = value.encode()
        return self.varint(number << 3 | 2) + self.varint(len(value)) + value

    def test_reads_actual_proto_manifest_with_compiled_version_code(self):
        attr = lambda name, value: self.field(4, self.field(2, name) + self.field(3, value))
        code = self.field(4, self.field(2, "versionCode") + self.field(6, self.field(7, self.field(6, 13020))))
        element = self.field(3, "manifest") + attr("package", "icu.nd4y.dosette") + code + attr("versionName", "0.13.2")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "app.aab"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("base/manifest/AndroidManifest.xml", self.field(1, element))
            self.assertEqual(bundle_identity(path), ("icu.nd4y.dosette", 13020, "0.13.2"))

    def test_rejects_truncated_or_invalid_manifest(self):
        for data in (b"\x0a\xff", b"\x0a\x03x", b"\x00", b"\xff" * 11):
            with self.subTest(data=data), self.assertRaises(ValueError):
                list(proto_fields(data))
if __name__ == "__main__":
    unittest.main()
