#!/usr/bin/env python3
"""Pack the existing Android ARM64 OpenJDK, preserving actual headless libraries."""
import pathlib
import sys
import tarfile

root = pathlib.Path(sys.argv[1])
destination = pathlib.Path(sys.argv[2])
required = [
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
]
excluded_files = {
    # These are not used by JNI_CreateJavaVM or the headless Android renderer.
    "lib/aarch64/libawt_xawt.so",
    "lib/aarch64/libjawt.so",
    "lib/aarch64/jli/libjli.so",
    "lib/aarch64/server/Xusage.txt",
    # Optional Java SE features outside the MIDP/CLDC host's requirements.
    "lib/ext/nashorn.jar",
    # JFR is optional on this embedded host; the JDK archive places it at lib/jfr.jar.
    "lib/jfr.jar",
}
excluded_directories = ("bin/", "man/", "lib/aarch64/jli/", "lib/jfr/")
for name in required:
    if not (root / name).is_file():
        raise SystemExit("Missing runtime file: " + name)
destination.parent.mkdir(parents=True, exist_ok=True)
with tarfile.open(destination, "w:xz", format=tarfile.USTAR_FORMAT, dereference=True, preset=7) as archive:
    for file in sorted(root.rglob("*")):
        relative = file.relative_to(root)
        relative_name = relative.as_posix()
        excluded = (
            any(relative_name.startswith(prefix) for prefix in excluded_directories)
            or relative_name in excluded_files
        )
        if file.is_file() and not excluded and relative.name != ".complete":
            # Only normalized regular files; no symlinks/PAX/GNU extension headers.
            info = archive.gettarinfo(str(file), relative.as_posix())
            info.uid = info.gid = 0
            info.uname = info.gname = ""
            with file.open("rb") as content:
                archive.addfile(info, content)
print(f"runtime.tar.xz: {destination.stat().st_size / 1048576:.2f} MiB")
