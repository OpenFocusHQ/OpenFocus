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
Without signing secrets, releases are **debug-signed testing builds** (the release notes say so). Each runner has a different debug key, so phones can't update from one of these builds to the next; you have to uninstall first. To publish properly signed releases:

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
