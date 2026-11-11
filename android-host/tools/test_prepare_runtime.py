#!/usr/bin/env python3
"""Verify the trimmed runtime packer keeps the host and omits optional payloads."""
import pathlib
import subprocess
import sys
import tarfile
import tempfile


PACKER = pathlib.Path(__file__).with_name("prepare_runtime.py")
REQUIRED = [
    "LICENSE",
    "lib/rt.jar",
    "lib/charsets.jar",
    "lib/jsse.jar",
    "lib/aarch64/server/libjvm.so",
    "lib/aarch64/libverify.so",
    "lib/aarch64/libjava.so",
    "lib/aarch64/libnet.so",
    "lib/aarch64/libnio.so",
    "lib/aarch64/libawt.so",
    "lib/aarch64/libawt_headless.so",
    "lib/aarch64/libfontmanager.so",
    "lib/aarch64/libfreetype.so",
    "lib/fonts/LucidaSansRegular.ttf",
    "lib/ext/sunjce_provider.jar",
    "lib/ext/sunec.jar",
    "lib/ext/cldrdata.jar",
]
EXCLUDED = [
    "bin/java",
    "man/java.1",
    "lib/aarch64/jli/libjli.so",
    "lib/aarch64/libawt_xawt.so",
    "lib/aarch64/libjawt.so",
    "lib/aarch64/server/Xusage.txt",
    "lib/ext/nashorn.jar",
    "lib/jfr.jar",
    "lib/jfr/profile.jfc",
]


def main():
    with tempfile.TemporaryDirectory(prefix="freej2me-runtime-pack-") as temporary:
        root = pathlib.Path(temporary) / "runtime"
        archive = pathlib.Path(temporary) / "runtime.tar.xz"
        for name in REQUIRED + EXCLUDED:
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(b"fixture")

        subprocess.run(
            [sys.executable, str(PACKER), str(root), str(archive)],
            check=True,
            capture_output=True,
            text=True,
        )
        with tarfile.open(archive, "r:xz") as tar:
            packaged = set(tar.getnames())

        missing = set(REQUIRED) - packaged
        retained_optional = set(EXCLUDED) & packaged
        if missing or retained_optional:
            raise SystemExit(
                f"FAIL: missing required={sorted(missing)}; "
                f"optional payloads retained={sorted(retained_optional)}"
            )
        print("PASS: runtime packer retains host dependencies and removes optional payloads")


if __name__ == "__main__":
    main()
