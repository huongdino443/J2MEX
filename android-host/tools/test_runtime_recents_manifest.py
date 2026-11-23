#!/usr/bin/env python3
import xml.etree.ElementTree as ET
from pathlib import Path


ANDROID = "{http://schemas.android.com/apk/res/android}"
manifest_path = Path(__file__).resolve().parents[1] / "app/src/main/AndroidManifest.xml"
root = ET.parse(manifest_path).getroot()

activities = root.findall("./application/activity")
main_activity = next(
    activity for activity in activities
    if activity.get(ANDROID + "name") == ".MainActivity"
)
runtime_activities = [
    activity
    for activity in activities
    if activity.get(ANDROID + "name", "").split(".")[-1].startswith("Runtime")
]

assert len(runtime_activities) == 9, (
    f"expected all 9 runtime activities, found {len(runtime_activities)}"
)
assert main_activity.get(ANDROID + "excludeFromRecents", "false").lower() != "true", (
    "the single launcher task must remain visible in Android Recents"
)
assert all(activity.get(ANDROID + "process") for activity in runtime_activities), (
    "runtime activities must keep their isolated Android processes"
)
assert all(not activity.get(ANDROID + "taskAffinity") for activity in runtime_activities), (
    "runtime activities must share the launcher task affinity"
)
assert all(activity.get(ANDROID + "excludeFromRecents", "false").lower() != "true"
           for activity in runtime_activities), (
    "the active runtime screen must remain part of the single visible Recents task"
)

main_source = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/org/recompile/freej2me/lite/MainActivity.java"
).read_text()
runtime_source = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/org/recompile/freej2me/lite/RuntimeActivity.java"
).read_text()
assert "RecentsTaskVisibility" not in main_source + runtime_source, (
    "runtime task visibility must not be toggled while sessions are running"
)
assert "returnToActiveRuntime()" in main_source, (
    "selecting the launcher task must still return to the foreground game"
)
assert "Intent.FLAG_ACTIVITY_CLEAR_TASK" not in main_source, (
    "launching a new game must not clear the shared launcher/runtime task"
)
assert "Intent.FLAG_ACTIVITY_CLEAR_TOP" not in main_source + runtime_source, (
    "session routing must not destroy activities stacked above the chosen game"
)
assert "Intent.FLAG_ACTIVITY_REORDER_TO_FRONT" in main_source, (
    "returning to an existing game must reorder its Activity instead of starting a new task"
)
assert "pendingRecentsRuntimeSessionId" not in main_source, (
    "the v495 Recents-return marker must not override the shared v485 task stack"
)
assert "RuntimeReturnPolicy" not in main_source, (
    "the v495 Recents-return fallback must not reopen a game from MainActivity"
)
assert "requestRecentsReturn" not in runtime_source, (
    "leaving a runtime must not create the v495 Recents-return marker"
)
assert "launcherRuntimeReturnPending" in main_source, (
    "preserve the launcher-intent return behavior that existed in v485"
)
overview_source = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/org/recompile/freej2me/lite/SessionOverviewActivity.java"
).read_text()
assert "Intent.FLAG_ACTIVITY_REORDER_TO_FRONT" in overview_source, (
    "the session overview must switch to existing runtime activities in the shared task"
)
assert "Intent.FLAG_ACTIVITY_CLEAR_TOP" not in overview_source, (
    "returning from session overview must not clear running games"
)
service_source = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/org/recompile/freej2me/lite/BackgroundGameService.java"
).read_text()
assert "FOREGROUND_SERVICE_IMMEDIATE" in service_source, (
    "keep the fast foreground notification behavior on Android 12 and newer"
)
assert "Intent.FLAG_ACTIVITY_CLEAR_TOP" not in service_source, (
    "tapping a game notification must not destroy a later running game"
)
session_store_source = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/org/recompile/freej2me/lite/RuntimeSessionStore.java"
).read_text()
assert "requestRecentsReturn" not in session_store_source, (
    "runtime leave events must not add a separate Recents redirection request"
)

print("RuntimeRecentsManifestSmoke: v485 task routing kept; v495 Recents fallback removed")
