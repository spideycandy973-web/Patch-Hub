# Patch Hub

An early Android utility for inspecting an APK and replacing **multiple existing files inside it** in an APK you own or are authorized to modify. It uses Android's document picker and runs locally; APK editing does not require root or network access. Optional Shizuku connection diagnostics can report shell or root identity after the user grants permission; no patch operation currently uses Shizuku.

## What it does (and doesn't)

Inspect a standalone document or an APK entry using streaming (no fixed file-size limit for inspection): view its SHA-256, first 4 KiB as hex/ASCII, and up to 100 printable ASCII strings. This is a bounded file viewer, not Ghidra: it does not disassemble or decompile code. Choose a source APK, browse and search its file entries, then queue replacement files and export a new APK. You can apply exact, same-length hex byte replacements to an entry up to 16 MiB, previewing the match count and first five offsets before staging. Search replaces all non-overlapping matches and works on the queued version if one exists. Arbitrary binary edits can invalidate DEX checksums, corrupt files, or break apps; the tool does not repair these. You can also extract original entries or edit UTF-8 text entries up to 256 KiB inside the app; edits are staged in temporary app cache until export. The editor does not decode Android binary XML, DEX, or compiled resources. It copies the ZIP entries, replaces the queued files, and removes old v1 signature files. Other limits still apply: binary patching is limited to 16 MiB per entry, text editing to 256 KiB, and APK scanning to 50,000 entries. Available time, memory, storage and Android permissions also limit operation. The glass-inspired interface is an approximation rather than native iOS Liquid Glass. The original APK is not intentionally modified; always keep a backup and choose a different output document.

The result is **unsigned and not installable as-is**. Android APK signatures are invalidated by modification. Use Android SDK Build Tools `zipalign` and `apksigner` on a computer to align and sign it with an appropriate key. Updating an existing installation normally requires the original app's signing key; a different key generally requires uninstalling the original first (which may delete its data). Split APKs, integrity checks, compressed asset expectations, and other app-specific constraints can prevent the modified app from working. Existing file entries can be replaced except old signature metadata; scanning is limited to 50,000 entries and search displays up to 300 matches at once. This is a raw file replacement, not a code or compiled-resource editor: you must supply a compatible replacement yourself, and edits to executable code, manifests, or resources can easily make the APK unusable.

Patch Hub does not create unlimited currencies, change server-side data, bypass protections, guarantee successful changes to arbitrary apps, or include an AI agent. No free unlimited AI service is promised. Use only on software you have permission to modify.

## Build

Requires JDK 17, Android SDK platform 35, and Gradle 8.9. Run `gradle assembleDebug`. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also builds a downloadable debug APK artifact. A debug build is for testing, not a production-signed release.

To prepare an edited APK for testing (replace paths and use your own signing key):

```sh
zipalign -f -p 4 patched-unsigned.apk patched-aligned.apk
apksigner sign --ks your-key.jks --out patched-signed.apk patched-aligned.apk
apksigner verify --verbose patched-signed.apk
```

Do not share your signing key or passwords. Newer native libraries can require additional alignment; consult the Android SDK Build Tools documentation for the APK being modified.

Debug builds may be signed with different keys on different CI runs, so upgrading between them can require uninstalling the earlier version first (and may erase its data). This prototype has not been verified on every device.

## Optional Shizuku connection

Install and start Shizuku separately, then tap **Connect Shizuku** and grant access. On unrooted Android 11 or newer, Shizuku can be started with wireless debugging; older Android versions usually require a computer. After reboot it may need to be started again. This integration currently only requests permission and displays connection/identity diagnostics; it does not inspect or modify other apps through Shizuku. ADB shell is not root and generally cannot access other apps' private data or memory.

## Community patch format (experimental)

A creator can share a UTF-8 JSON file in Patch Hub format version 1. The user selects the exact source APK first and then imports the file. Patch Hub verifies the full APK SHA-256 and all target paths, shows the creator and changes, and asks for confirmation before staging. Multiple patches can be combined when they target the same APK and do not touch the same entries. The exported APK is still unsigned and requires separate alignment and signing. This is a simple file-replacement format, not a universal importer for ReVanced, Morphe, or arbitrary patch scripts; it does not automatically generate new features or verify that a patch is safe. Never import patches from untrusted creators.

```json
{
  "format": 1,
  "name": "Example replacement",
  "author": "Example creator",
  "apk_sha256": "replace-with-64-character-sha256-of-exact-source-apk",
  "replacements": [
    {"path": "assets/example.txt", "data_base64": "SGVsbG8="}
  ]
}
```

Replace the hash and contents with those for the authorized APK and desired replacement. The example encodes `Hello`; it does not add features to a third-party app. All paths must already exist and cannot be signature files. Imports are capped at 4 MiB of JSON and 50 replacement entries, and conflicting queued paths are rejected. The patch file and its replacement bytes can contain malicious content: hash matching checks compatibility, not authenticity or safety.
