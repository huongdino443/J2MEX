# J2MEX Changelog

## 0.1.1

Changes since 0.1.0:

### Game compatibility and rendering

- Improve M3G and Micro3D models, animation, transforms, terrain textures, fog, transparency, depth ordering, and water surfaces.
- Honor M3G polygon depth offsets to prevent overlapping road and terrain surfaces from flickering.
- Fix terrain, fog, and water rendering in Tank Battle and translucent mist/water effects in Battle Boats.
- Correct 3D attachment/depth rendering in City Bloxx and spawn-platform ordering in Tankzor, while preserving Need for Speed rendering.
- Improve MIDP sprite/tile dimensions and layer ordering, plus class-loading and legacy timing compatibility.
- Validate compatibility with Mod Army II, Tankzor, City Bloxx, Battle Boats, Tank Battle, Need for Speed, Medal of Honor: Airborne, Bounce Tales, and Ninja School.

### Android storage and usability

- Improve shared-storage access and game-folder handling across Android versions.
- Raise the supported game archive import limit from 128 MiB to 512 MiB.
- Refine file-manager search and JAR/ZIP selection; keep LCDUI list focus and scroll position stable.
- Open JAR/ZIP files from Android file managers; import them and launch new games into first-run settings.
- Add GitHub update checking, APK download verification, and Android installer handoff.
- Reduce repeated runtime logs; omit normal key events and keep frame summaries only when performance slows.

### Emulation speed

- Extend the selectable emulation speed range from 10x to 20x.
