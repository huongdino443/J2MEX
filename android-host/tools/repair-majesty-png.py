#!/usr/bin/env python3
"""Losslessly normalize indexed PNG scanlines in Majesty HGIM v9 packs.

Only resource bytes are changed, never classes/manifest/palette/alpha/dimensions.
Original JAR remains untouched. Reject signed JARs rather than breaking signatures.
"""
import hashlib
import json
from pathlib import Path
import struct
import sys
import zipfile
import zlib


class Reader:
    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, count):
        if count < 0 or self.pos + count > len(self.data):
            raise ValueError("truncated pack")
        result = self.data[self.pos:self.pos + count]
        self.pos += count
        return result

    def varint(self):
        value = 0
        for shift in range(0, 35, 7):
            byte = self.take(1)[0]
            value |= (byte & 127) << shift
            if byte < 128:
                return value
        raise ValueError("invalid variable integer")

    def block(self):
        return self.take(self.varint())


def varint(value):
    result = bytearray()
    while value >= 128:
        result.append((value & 127) | 128)
        value >>= 7
    result.append(value)
    return bytes(result)


def paeth(left, up, corner):
    estimate = left + up - corner
    a, b, c = abs(estimate - left), abs(estimate - up), abs(estimate - corner)
    return left if a <= b and a <= c else up if b <= c else corner


def unfilter(raw, width, height):
    if width <= 0 or height <= 0 or len(raw) != (width + 1) * height:
        raise ValueError("unexpected indexed PNG dimensions/scanline length")
    previous = bytearray(width)
    result = bytearray()
    for y in range(height):
        start = y * (width + 1)
        mode = raw[start]
        if mode > 4:
            raise ValueError("invalid PNG filter")
        row = bytearray(raw[start + 1:start + width + 1])
        for x in range(width):
            left = row[x - 1] if x else 0
            up = previous[x]
            corner = previous[x - 1] if x else 0
            prediction = (0, left, up, (left + up) // 2, paeth(left, up, corner))[mode]
            row[x] = (row[x] + prediction) & 255
        result.extend(row)
        previous = row
    return bytes(result)


def check_crc(tag, payload):
    if len(payload) < 4 or zlib.crc32(tag + payload[:-4]) != struct.unpack(">I", payload[-4:])[0]:
        raise ValueError("invalid original chunk CRC: " + tag.decode())


def repair_pack(data):
    if len(data) < 8 or data[:4] != b"HGIM" or data[7] != 9:
        return data, 0, 0
    reader = Reader(data)
    reader.take(8)
    palettes, alphas = [], []
    for _ in range(reader.varint()):
        palette, alpha = reader.block(), reader.block()
        # Truecolor-only packs may retain empty, unused palette slots.
        if palette:
            check_crc(b"PLTE", palette)
        if alpha:
            check_crc(b"tRNS", alpha)
        palettes.append(palette)
        alphas.append(alpha)
    count = reader.varint()
    indices = reader.take(count)
    dimensions = [(reader.varint(), reader.varint()) for _ in range(count)]
    output = bytearray(data[:reader.pos])
    changed = indexed = 0
    for index in range(count):
        block_start = reader.pos
        payload = reader.block()
        if indices[index] >= 128:  # truecolor RGBA; game delegates these to native PNG
            output.extend(data[block_start:reader.pos])
            continue
        indexed += 1
        palette = palettes[indices[index]]
        check_crc(b"IDAT", payload)
        width, height = dimensions[index]
        raw = zlib.decompress(payload[:-4])
        pixels = unfilter(raw, width, height)
        if max(pixels) >= (len(palette) - 4) // 3:
            raise ValueError("decoded pixel outside palette")
        if all(raw[y * (width + 1)] == 0 for y in range(height)):
            output.extend(data[block_start:reader.pos])
            continue
        normalized = b"".join(b"\0" + pixels[y * width:(y + 1) * width] for y in range(height))
        compressed = zlib.compress(normalized, 9)
        new_payload = compressed + struct.pack(">I", zlib.crc32(b"IDAT" + compressed))
        assert unfilter(zlib.decompress(new_payload[:-4]), width, height) == pixels
        check_crc(b"IDAT", new_payload)
        output.extend(varint(len(new_payload)))
        output.extend(new_payload)
        changed += 1
    output.extend(reader.take(len(data) - reader.pos))
    return bytes(output), changed, indexed


def main():
    source, destination = map(Path, sys.argv[1:3])
    if source.resolve() == destination.resolve():
        raise ValueError("refuse to overwrite input")
    report = {"sourceSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
              "changedPacks": [], "changedImages": 0, "checkedIndexedImages": 0,
              "unchangedClasses": 0}
    with zipfile.ZipFile(source) as jar:
        names = jar.namelist()
        if len(set(names)) != len(names):
            raise ValueError("duplicate archive entries")
        if any(name.upper().startswith("META-INF/") and name.upper().endswith(
                (".SF", ".RSA", ".DSA", ".EC")) for name in names):
            raise ValueError("signed JAR requires a separate signing decision")
        with zipfile.ZipFile(destination, "x") as output:
            output.comment = jar.comment
            for entry in jar.infolist():
                data = jar.read(entry)
                if entry.filename.endswith(".pak"):
                    data, changed, indexed = repair_pack(data)
                    report["checkedIndexedImages"] += indexed
                    report["changedImages"] += changed
                    if changed:
                        report["changedPacks"].append({"path": entry.filename, "images": changed})
                if entry.filename.endswith(".class"):
                    report["unchangedClasses"] += 1
                output.writestr(entry, data)
    assert report["checkedIndexedImages"] > 0, "no supported indexed images were checked"
    assert report["changedImages"] > 0, "no PNG filter changes were made"
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(destination) as fixed:
        assert original.namelist() == fixed.namelist()
        changed_names = {entry["path"] for entry in report["changedPacks"]}
        for name in original.namelist():
            if name not in changed_names:
                assert original.read(name) == fixed.read(name), name
        assert fixed.testzip() is None
    report["outputSha256"] = hashlib.sha256(destination.read_bytes()).hexdigest()
    destination.with_suffix(".verification.json").write_text(json.dumps(report, indent=2))
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
