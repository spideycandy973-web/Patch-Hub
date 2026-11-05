# Patch Hub

An early Android utility for replacing **one existing file under `assets/`** in an APK you own or are authorized to modify. It uses Android's document picker and runs locally; it does not require root or network access.

## What it does (and doesn't)

Choose a source APK and a replacement file, enter the exact asset path (for example `assets/config.json`), then save a new APK. It copies the ZIP entries, replaces the specified asset, and removes old v1 signature files. The original APK is not intentionally modified; always keep a backup and choose a different output document.

The result is **unsigned and not installable as-is**. Android APK signatures are invalidated by modification. Use Android SDK Build Tools `zipalign` and `apksigner` on a computer to align and sign it with an appropriate key. Updating an existing installation normally requires the original app's signing key; a different key generally requires uninstalling the original first (which may delete its data). Split APKs, integrity checks, compressed asset expectations, and other app-specific constraints can prevent the modified app from working. Only files in `assets/` are supported, not compiled resources or executable code.

Patch Hub does not create unlimited currencies, change server-side data, bypass protections, patch arbitrary apps, or include an AI agent. No free unlimited AI service is promised. Use only on software you have permission to modify.

## Build

Requires JDK 17, Android SDK platform 35, and Gradle 8.9. Run `gradle assembleDebug`. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions also builds a downloadable debug APK artifact. A debug build is for testing, not a production-signed release.

To prepare an edited APK for testing (replace paths and use your own signing key):

```sh
zipalign -f -p 4 patched-unsigned.apk patched-aligned.apk
apksigner sign --ks your-key.jks --out patched-signed.apk patched-aligned.apk
apksigner verify --verbose patched-signed.apk
```

Do not share your signing key or passwords. Newer native libraries can require additional alignment; consult the Android SDK Build Tools documentation for the APK being modified.
