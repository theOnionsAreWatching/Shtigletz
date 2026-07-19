# Building D-Mail

(Developer notes — users just download APKs from the Releases page.)

## CI

GitHub Actions (`.github/workflows/build.yml`) builds on every push:

- Always: unsigned **debug APKs** for all four flavors, as an Actions artifact.
- With signing secrets configured: signed **release APKs** for all four.
- Pushing a tag starting with `v` (e.g. `v0.7.1`) publishes a GitHub Release
  with `D-Mail-Kosher-…`, `D-Mail-Plus-…`, `D-Mail-Pro-…`, and
  `D-Mail-Max-…` APKs attached automatically.

Signing secrets (repo Settings → Secrets and variables → **Actions**):

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 your.keystore` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

## Local build

`./gradlew assembleDebug` with JDK 17 and the Android SDK (compileSdk 34).
Per-flavor: `./gradlew assembleKosherDebug`, `assembleProRelease`, etc.

## Flavors

`app/build.gradle.kts` defines four product flavors (dimension `policy`):
kosher, plus, pro, gefilte. Each has its own `applicationId`, display name
(via `resValue`), and a `FlavorConfig.kt` in `app/src/<flavor>/java/…`
declaring compile-time capabilities (`ATTACHMENTS`, `IMAGES`,
`WEBVIEW_IMAGES`). Kosher-critical enforcement lives in
`mail/BodyExtractor.kt` and `ui/SafeWebView.kt`, both gated on those
constants. **Do not weaken those two files.**

## Adding a soft-key device profile

Users generate a report via Settings → "Soft-key report for this device"
(appears after they learn keys via Settings → Soft keys → Custom). Paste the
`<profile … />` line from the report into
`app/src/main/res/xml/softkey_profiles.xml`.
