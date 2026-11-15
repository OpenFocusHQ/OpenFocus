# Releasing

OpenFocus is released on **Google Play** as a signed Android App Bundle (`.aab`).
[`.github/workflows/play-bundle.yml`](../.github/workflows/play-bundle.yml) builds it when you run the workflow from the **Actions** tab, or when you push a tag such as `v0.3`. The workflow:
1. Runs all unit, migration, content and screenshot tests.
2. Builds a minified bundle signed with your **upload key**.
3. Attaches `app-release.aab` and the R8 `mapping.txt` to the run as an artifact named `openfocus-<run number>`.

The workflow doesn't run on pushes or pull requests.

## Version numbers
- `openfocus.version` in `gradle.properties` is the human version (e.g. `0.2`). Bump it for notable releases.
- CI sets `OPENFOCUS_BUILD_NUMBER` to the workflow run number. It becomes both the `versionCode`, so every bundle is an upgrade, and the last part of `versionName` (e.g. `0.2.12`).

## Upload key (one-time setup)
Play uses **Play App Signing**. Google keeps the key that signs what users install, and you sign uploads with your own *upload key*. If you lose the upload key, Google can reset it. Create the key:

```bash
keytool -genkeypair -v -keystore openfocus-upload.jks -alias openfocus \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 openfocus-upload.jks   # copy the output
```

Then add these **repository secrets** (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `OPENFOCUS_KEYSTORE_BASE64` | the base64 output above |
| `OPENFOCUS_KEYSTORE_PASSWORD` | keystore password |
| `OPENFOCUS_KEY_ALIAS` | `openfocus` (or the alias you chose) |
| `OPENFOCUS_KEY_PASSWORD` | key password |

Without these secrets, the workflow fails before it builds anything. Play rejects debug-signed bundles.
Keep the `.jks` file and its passwords safe and **never commit them**.

To build locally, set the same variables (`OPENFOCUS_KEYSTORE` = path to the `.jks`) and run `./gradlew :app:bundleRelease`. The bundle is written to `app/build/outputs/bundle/release/`. Without the variables, release builds fall back to the debug key: that's fine for testing on your own phone, but Play won't accept the result.

## Publishing a build
1. Run **Play Store bundle** from the Actions tab (or push a `v*` tag) and download the artifact.
2. In Play Console, open **Testing → Internal testing → Create new release**, upload `app-release.aab`, and roll it out. Promote it to closed, open or production testing when it's ready.
3. Under **App bundle explorer → Downloads**, upload `mapping.txt` for that version so crash reports are deobfuscated.

The first upload must be done by hand in Play Console. Later uploads can be automated with a service account, but this isn't set up yet.

## Play Console requirements
Sideloaded apps with an accessibility service are restricted by Play Protect and by many payment apps (see the README). Installing from Play avoids that. Play requires:
1. The **Accessibility API declaration**, describing the core feature (blocking apps and short-video feeds that the user chooses) and confirming that data stays on the device.
2. A **prominent in-app disclosure** before the user turns on the service. That's `AccessibilityDisclosureDialog`, shown from onboarding and the Shield tab.
3. A **privacy policy URL**. Everything stays on the device, so the policy can be short.
4. The **Data safety** form: no data is collected or shared.

Don't set `android:isAccessibilityTool`. OpenFocus is not an accessibility tool, and declaring that would be misleading.
