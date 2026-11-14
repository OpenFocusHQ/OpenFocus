# Releasing

Every push to `main` runs [`.github/workflows/build.yml`](../.github/workflows/build.yml):
1. **Test & build:** all unit, migration, content and screenshot tests, then a minified release APK.
2. **Publish GitHub release** (only if step 1 passes):
   - The tag is `v<version>.<run number>` (e.g. `v0.2.17`).
   - Release notes are generated from the commits since the last release.
   - The release includes `OpenFocus-<version>.apk` and its SHA-256.

Pull requests run step 1 only.

## Version numbers
- `openfocus.version` in `gradle.properties` is the human version (e.g. `0.2`). Bump it for notable releases.
- CI sets `OPENFOCUS_BUILD_NUMBER` to the workflow run number. It becomes both the APK `versionCode` (so every release is an upgrade) and the last part of `versionName`.

## Signing with your own key (recommended)
**Without signing secrets, nothing is published.** CI still tests and builds every push. Debug-signed APKs are flagged by Play Protect, and phones can't update from one to the next, so they're no longer released. To publish signed releases:

```bash
keytool -genkeypair -v -keystore openfocus-release.jks -alias openfocus \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 openfocus-release.jks   # copy the output
```

Then add these **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `OPENFOCUS_KEYSTORE_BASE64` | the base64 output above |
| `OPENFOCUS_KEYSTORE_PASSWORD` | keystore password |
| `OPENFOCUS_KEY_ALIAS` | `openfocus` (or the alias you chose) |
| `OPENFOCUS_KEY_PASSWORD` | key password |

Keep the `.jks` file and its passwords safe and **never commit them**. If you lose the key, existing installs can't receive updates.

To build a signed APK locally, set the same environment variables (`OPENFOCUS_KEYSTORE` = path to the `.jks`) and run `./gradlew :app:assembleRelease`.

## Towards Google Play
Sideloaded apps with an accessibility service are restricted by Play Protect and by many payment apps (see the README). Distributing through Google Play, even just an **internal testing** track, avoids that. Play requires:
1. A signed **Android App Bundle**: `./gradlew :app:bundleRelease` with the signing variables above.
2. The **Accessibility API declaration** in Play Console, describing the core feature (blocking apps and short-video feeds chosen by the user) and confirming that data stays on the device.
3. A **prominent in-app disclosure** before the user enables the service. That's `AccessibilityDisclosureDialog`, shown from onboarding and the Shield tab.
4. A privacy policy URL. Everything stays on the device, which keeps the policy short.

Don't set `android:isAccessibilityTool`. OpenFocus is not an accessibility tool, and declaring that would be misleading.
