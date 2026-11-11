#!/usr/bin/env python3
"""Exercise the real FreeJ2ME engine and binary framebuffer/input protocol locally.

Uses the Linux host JDK, not the Android ARM64 runtime. A physical device test
is still required for JNI bootstrap and Android graphics.
"""
import os
import pathlib
import socket
import struct
import subprocess
import time
import uuid
import zlib

root = pathlib.Path(__file__).resolve().parents[1]
assets = root / "app/src/main/assets"
for required in ["engine.jar", "bridge.jar", "sample.jar"]:
    assert (assets / required).is_file(), "Build the JAR assets first: missing " + required
out = root / "build/protocol-test"
out.mkdir(parents=True, exist_ok=True)
java = pathlib.Path(os.environ["JAVA_HOME"]) / "bin/java"
server = socket.socket()
server.bind(("127.0.0.1", 0))
server.listen(1)
server.settimeout(20)
token = str(uuid.uuid4())
process = None
connection = None
log = open(out / "engine.log", "wb")
def exact(reader, count):
    data = reader.read(count)
    if len(data) != count:
        raise AssertionError(f"Engine disconnected: expected {count} bytes, got {len(data)}")
    return data

def integer(reader):
    return struct.unpack(">i", exact(reader, 4))[0]

def utf(reader):
    length = struct.unpack(">H", exact(reader, 2))[0]
    return exact(reader, length).decode("utf-8", errors="replace")

def next_frame(reader):
    while True:
        kind = integer(reader)
        if kind == 1:
            width, height = integer(reader), integer(reader)
            assert width == 240 and height == 320
            return width, height, exact(reader, width * height * 4)
        if kind == 6:
            continue
        if kind == 7:
            exact(reader, integer(reader))
            continue
        assert kind in (2, 3), f"Unexpected type {kind}"
        message = utf(reader)
        print("ENGINE:", message)
        assert kind != 3, message

def png(path, width, height, argb):
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            i = (y * width + x) * 4
            rows.extend(argb[i + 1:i + 4])
    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xffffffff)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))

try:
    process = subprocess.Popen([
        str(java), "-Djava.awt.headless=true", "-cp",
        str(assets / "engine.jar") + ":" + str(assets / "bridge.jar"),
        "org.recompile.android.HeadlessMain", (assets / "sample.jar").as_uri(),
        "240", "320", str(server.getsockname()[1]), token, str(out), str(out / "audio"),
        "144", "vi-VN", "0",
    ], stdout=log, stderr=subprocess.STDOUT, cwd=out)
    connection, _ = server.accept()
    connection.settimeout(20)
    reader = connection.makefile("rb")
    assert utf(reader) == token, "Authentication token mismatch"
    width, height, first = next_frame(reader)
    colors = set(struct.unpack(">" + "I" * (width * height), first))
    assert len(colors) >= 6, f"Unexpected blank/flat framebuffer: {len(colors)} colors"
    png(out / "first-frame.png", width, height, first)
    connection.sendall(struct.pack(">BiBi", 1, -4, 2, -4))
    deadline = time.monotonic() + 10
    after_key = first
    while after_key == first:
        _, _, after_key = next_frame(reader)
        assert time.monotonic() < deadline, "Key press did not update the framebuffer"
    connection.sendall(struct.pack(">Bii", 3, 40, 250))
    # The key-up may itself have queued an extra frame; skip it if necessary.
    deadline = time.monotonic() + 10
    while True:
        width, height, pointer = next_frame(reader)
        pixel = struct.unpack_from(">I", pointer, (250 * width + 40) * 4)[0] & 0xffffff
        if pixel == 0xffcc44:
            break
        assert time.monotonic() < deadline, "Pointer did not move the diagnostic marker"
    png(out / "pointer-frame.png", width, height, pointer)
    connection.sendall(struct.pack(">BiiB", 5, 40, 250, 9))
    process.wait(timeout=10)
    assert process.returncode == 0, f"Engine exit code {process.returncode}"
    print(f"PASS: authenticated handshake; {width}x{height} framebuffer ({len(colors)} colors); keys; pointer; clean exit.")
finally:
    if connection is not None:
        connection.close()
    server.close()
    if process is not None and process.poll() is None:
        process.kill()
        process.wait()
    log.close()
