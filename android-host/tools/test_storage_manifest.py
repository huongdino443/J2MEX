import pathlib
import re
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
manifest_path = pathlib.Path("app/src/main/AndroidManifest.xml")
manifest = ET.parse(manifest_path).getroot()
permissions = {
    element.attrib.get(ANDROID + "name"): element
    for element in manifest.findall("uses-permission")
}

write = permissions.get("android.permission.WRITE_EXTERNAL_STORAGE")
assert write is not None, "Legacy write permission must be declared"
assert write.attrib.get(ANDROID + "maxSdkVersion") == "29", (
    "Legacy write permission must remain available through Android 10/API 29"
)
assert "android.permission.READ_EXTERNAL_STORAGE" in permissions
assert "android.permission.MANAGE_EXTERNAL_STORAGE" in permissions
application = manifest.find("application")
assert application is not None
assert application.attrib.get(ANDROID + "requestLegacyExternalStorage") == "true"

gradle = pathlib.Path("app/build.gradle").read_text(encoding="utf-8")
assert re.search(r"\bminSdk\s+21\b", gradle), "Minimum supported API should remain 21"
print("Storage manifest smoke: shared-storage permissions and API range passed")
