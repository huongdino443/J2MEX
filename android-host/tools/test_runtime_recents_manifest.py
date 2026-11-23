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
not_hidden = [
    activity.get(ANDROID + "name")
    for activity in runtime_activities
    if activity.get(ANDROID + "excludeFromRecents", "false").lower() != "true"
]
assert not not_hidden, (
    f"runtime tasks must stay out of Recents so only the launcher task appears: {not_hidden}"
)
assert all(activity.get(ANDROID + "taskAffinity") for activity in runtime_activities), (
    "runtime activities must keep their per-session task affinities"
)
assert all(activity.get(ANDROID + "process") for activity in runtime_activities), (
    "runtime activities must keep their isolated Android processes"
)

visibility_source = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/org/recompile/freej2me/lite/RecentsTaskVisibility.java"
).read_text()
main_source = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/org/recompile/freej2me/lite/MainActivity.java"
).read_text()
runtime_source = (
    Path(__file__).resolve().parents[1]
    / "app/src/main/java/org/recompile/freej2me/lite/RuntimeActivity.java"
).read_text()
assert "task.setExcludeFromRecents(" in visibility_source, (
    "the active task must be switched into Recents dynamically"
)
assert "RecentsTaskVisibility.showOnlyTask(this, getTaskId())" in main_source, (
    "the launcher task must become the sole Recents task while the launcher is active"
)
assert "RecentsTaskVisibility.showOnlyTask(this, getTaskId())" in runtime_source, (
    "the game task must become the sole Recents task while the game is active"
)

print("RuntimeRecentsManifestSmoke: per-session isolation and single visible-task routing passed")
