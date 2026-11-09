#!/usr/bin/env python3
"""Check every arm64 shared library in an APK for 16 KiB ELF PT_LOAD alignment."""
import argparse
import pathlib
import subprocess
import tempfile
import zipfile

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("apk")
    parser.add_argument("--readelf", required=True)
    args = parser.parse_args()
    apk = pathlib.Path(args.apk)
    readelf = args.readelf

    with zipfile.ZipFile(apk) as archive:
        libs = [n for n in archive.namelist()
                if n.startswith("lib/arm64-v8a/") and n.endswith(".so")]
        if not libs:
            raise SystemExit("FAIL: APK contains no arm64-v8a shared libraries")
        with tempfile.TemporaryDirectory() as tmp:
            for name in libs:
                path = pathlib.Path(tmp) / pathlib.Path(name).name
                path.write_bytes(archive.read(name))
                result = subprocess.run(
                    [readelf, "-lW", str(path)],
                    check=True, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT
                )
                loads = []
                for line in result.stdout.splitlines():
                    fields = line.split()
                    if fields and fields[0] == "LOAD":
                        try:
                            loads.append(int(fields[-1], 16))
                        except ValueError:
                            raise SystemExit(f"FAIL: cannot parse PT_LOAD alignment in {name}: {line}")
                if not loads:
                    raise SystemExit(f"FAIL: no PT_LOAD segments found in {name}")
                bad = [value for value in loads if value < 0x4000 or value & (value - 1)]
                if bad:
                    raise SystemExit(
                        f"FAIL: {name} has PT_LOAD alignment(s) below 16 KiB: "
                        + ", ".join(hex(x) for x in loads)
                    )
                print(f"PASS: {name}: " + ", ".join(hex(x) for x in loads))
    print(f"PASS: all {len(libs)} arm64 libraries are 16 KiB ELF-aligned")

if __name__ == "__main__":
    main()
