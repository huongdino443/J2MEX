# J2MEX Changelog

## 0.1.2

Changes since 0.1.1:

### Runtime and compatibility

- Improve high-speed emulation performance while preserving rendered output and the established speed presets.
- Add a MIDlet picker for game archives that contain multiple MIDlets.
- Improve compatibility with cloned MIDlets and older Java ME class/return behavior.
- Reduce repetitive diagnostic output, including temporary performance-profiling messages.

### Memory editor

- Search Int8, Int16, UInt16, Int32, Int64, Float32, and Float64 values in game memory.
- Add type filtering, paged results, multi-selection, and bulk editing with layouts adapted to portrait and landscape.
- Restrict memory editing to offline sessions; after editing starts, keep network access blocked for the rest of that session, including remote M3G resource downloads.

### Background activity

- Add an optional, default-off background activity setting in both the app settings and edge-swipe settings.
- Show an ongoing notification while a game runs in the background, and improve foreground-service handling when switching away from and back to a game.

These compatibility fixes affect games such as Avatar NST, Ngọc Rồng, and Ma thú viễn chinh.

**Install this update over 0.1.1 without uninstalling; existing app and game data will be retained.**
