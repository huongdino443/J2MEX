# Native UI provenance

Upstream: https://github.com/nikita36078/J2ME-Loader

Pinned source: `261c216c017e77391774ca10748c1229884cecd2`

License: Apache License 2.0. The complete license is shipped in
`app/src/main/assets/licenses/J2ME-LOADER-APACHE-2.0.txt`; the app's component
notices identify the source and changes. The upstream tree examined contained
no separate NOTICE file.

## Modified ports

- `nativeui/NativeChoiceAdapter.java`: from
  `javax/microedition/lcdui/list/CompoundListAdapter.java` and
  `CompoundAdapter.java`. Retains framework row layout/checked-state approach;
  replaces MIDP Image/Font/ContextHolder and AndroidX resource dependencies with
  detached wire entries, native icons and framework theme attributes.
- `nativeui/NativeFormContent.java`: from the ScrollView/LinearLayout and item
  view management in `javax/microedition/lcdui/Form.java`. Replaces live Item
  objects with wire bindings, adds atomic state collection and in-place updates.
- `PhysicalKeyMapping.java` and `KeyMappingActivity.java`: adapt the per-profile
  Android-to-MIDP mapping and capture workflow from `KeyMapper.java` and
  `KeyMapperActivity.java`. They use J2MEX per-game settings, this host's MIDP
  key codes, and platform Views instead of J2ME Loader's profile/AppCompat stack.

These are adapted source portions, **not unchanged upstream files or a complete
J2ME Loader fork**. Original copyright notices are kept in these source files.

## New integration code

NativeLcdUiView, NativeInputTypes, the shared NativeScreen codec, engine NativeUi
adapter and transport integration are host-specific code. No J2ME Loader DEX
converter, MIDlet class loader, Canvas implementation or native libraries are
included by this port. No new AndroidX dependency is added.

FreeJ2ME and the combined project still retain their existing GPLv3 obligations.
This Apache notice does not relicense FreeJ2ME or the bundled Java runtime.
