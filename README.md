# D-Mail

A privacy-hardened, D-pad-first Android email client (IMAP/SMTP) built for sideloading on kosher/filtered devices.

Package: `io.github.theonionsarewatching.shtigletz`

## The two guarantees

**1. Kosher content policy — enforced at the network layer, compiled in, no toggle.**

- **Attachments are never downloaded.** The MIME walker (`mail/BodyExtractor.kt`) reads only `text/plain` and `text/html` parts. Jakarta Mail over IMAP fetches lazily, so parts whose content is never read are never requested from the server — attachment bytes (including inline `cid:` images) never reach the device. The 📎 indicator in the list comes from BODYSTRUCTURE metadata only.
- **Remote content never loads.** `ui/SafeWebView.kt` disables JavaScript, blocks network loads at the settings level, and returns an empty response from `shouldInterceptRequest` for *every* resource request — images, CSS, fonts, favicons. Content is rendered with a null base URL, a `default-src 'none'` CSP, and active-content stripping. Blocking remote images also kills read-tracking pixels.
- The **only image source in the entire app** is local contact photos (sender avatars), which involve no network access.

**2. First-class D-pad navigation with per-device key calibration.**

Physical keys on non-standard hardware emit inconsistent key/scan codes. Every activity extends `ui/BaseActivity.kt`, which resolves each hardware key against the active `KeyProfile`:

- Navigation actions (up/down/left/right/select/back) are re-dispatched as canonical `KEYCODE_DPAD_*` events, so Android's built-in focus system works even with vendor keycodes.
- App actions (COMPOSE, REPLY, DELETE, NEXT/PREV_MESSAGE, MENU=refresh, ...) fire on the current screen.

### Calibration ("learn keys")

Inbox → **Keys** → **Start calibration**. For each action, press the physical key you want (on-screen **Skip** for actions the phone has no key for — the buttons are touch targets while capture is active, since all raw keys are being captured). The profile records `keyCode` + `scanCode`, is saved keyed by `MANUFACTURER_MODEL`, and survives app restarts.

- **Export** shares the profile JSON (email it to yourself) so identical units can **Import** it by pasting.
- Presets can also be bundled at `app/src/main/assets/keyprofiles/{MANUFACTURER}_{MODEL}.json`.
- Load order: saved calibration → bundled preset for this model → `default.json` → hard-coded standard mapping.

## Building via GitHub Actions

1. Push this repository to GitHub.
2. Actions runs `.github/workflows/build.yml` on every push: it builds and uploads **D-Mail-debug** (`app-debug.apk`), installable immediately for testing.
3. For a **signed release APK**, add these repository secrets (Settings → Secrets and variables → Actions):
   - `KEYSTORE_BASE64` — your keystore file base64-encoded (`base64 -w0 release.keystore`)
   - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

   Generate a keystore once with:
   ```
   keytool -genkeypair -v -keystore release.keystore -alias dmail -keyalg RSA -keysize 2048 -validity 10000
   ```
   When the secrets exist, CI also uploads **D-Mail-release**. Never commit the keystore.
4. Download the APK from the workflow run's Artifacts and sideload it.

## Setup in the app

First launch opens account setup: email, password (use an **app password** for Gmail/Workspace — regular Gmail passwords won't work over IMAP), IMAP host/port/security, SMTP host/port/security. The app validates both connections before saving. Credentials are stored only in Keystore-backed `EncryptedSharedPreferences`.

## Project layout

```
app/src/main/java/io/github/theonionsarewatching/shtigletz/
├── LauncherActivity.kt        # routes to Setup or Inbox
├── input/                     # Action, KeyProfile, KeyProfileStore
├── mail/                      # ImapService, SmtpService, BodyExtractor (kosher network layer), MailAccount
├── security/CredentialStore.kt
├── contacts/ContactPhotos.kt  # local-only avatars
└── ui/                        # BaseActivity (key dispatch), SafeWebView (kosher render layer),
                               # Setup/Inbox/Read/Compose/Calibration activities, MessageAdapter
```

minSdk 23 (required by encrypted credential storage) · target/compile SDK 34 · Kotlin, Android Views (chosen over Compose for predictable D-pad focus).

## Roadmap

- **M2** — Room offline cache + sync, WorkManager background polling, new-mail notifications.
- **M3** — Drafts/Outbox with retry, save-to-Sent, archive/move/flag, folder list.
- **M4** — Server-side search, soft-key labels, bundled device presets, update-check hook, OAuth2 + multi-account scaffolding.

## Do not weaken

`BodyExtractor.kt` and `SafeWebView.kt` are the enforcement layers. Any change that reads a non-text MIME part's content, or lets any WebView resource request through, breaks the app's core guarantee. Both files carry comments explaining each lock.
