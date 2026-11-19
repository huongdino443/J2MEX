# J2MEX Changelog

## J2MEX 0.1.4

#### Game sessions

- Add the Tasks screen for up to five active games, with swipeable previews that preserve each game's orientation and session order.
- Show the selected game's live Java heap usage and indicate active games in the launcher.
- Keep game audio playing while viewing Tasks or returning to the launcher, and pause canvas drawing whenever a game is not visible.
- Open a running game from its launcher icon, stop it from the launcher, and return to the correct screen from Android notifications.
- Show separate ongoing notifications for background games only while background activity is enabled.

#### Controls and interface

- Redesign the edge-swipe menu as two swipeable pages, with log export on the second page and an updated Tasks label.
- Use J2MEX-styled dialogs and place primary actions on the right.
- Improve gamepad editor behavior when the Android keyboard opens, including on Android 10.

#### Settings and compatibility

- Default the status bar setting to off and background activity to on.
- Improve session preview orientation, cutout handling, and lifecycle behavior when returning to J2MEX.

**Install this update over 0.1.3 without uninstalling; existing app and game data will be retained.**

## 0.1.3

#### Display and controls

- Add a per-game orientation choice for system, portrait, or landscape and apply it before the game starts.
- Respect display cutout safe areas across the launcher, settings, file manager, gamepad, and in-game overlays.
- Improve virtual gamepad layout positioning and landscape LCDUI form behavior while the Android keyboard is open.
- Keep launcher search navigation and file-manager actions visible above the Android keyboard.

#### Runtime and graphics

- Improve returning to an active game from its foreground notification or the Android launcher.
- Improve runtime logging and session lifecycle handling.
- Add conservative M3G frustum culling and an optional GLES2 rendering path with the Java renderer as fallback.
- Improve M3G rendering compatibility while retaining the validated Java rendering path.

#### Updates

- Check for new releases automatically when the app opens online, while retaining manual checks.
- Style update prompts and progress screens to match J2MEX, with scrollable release notes and a two-hour automatic-check interval.
- Keep “Remind me later” available; a postponed update is shown again after 12 hours when the app is opened.

**Install this update over 0.1.2 without uninstalling; existing app and game data will be retained.**

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
