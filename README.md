# D-Mail

A kosher-friendly, dpad-navigable email client for Android.

## Four apps


| App | Package | Attachments | Images |
| --- | --- | --- | --- |
| **D-Mail Kosher** | `io.github.theonionsarewatching.shtigletz` | never downloaded | never |
| **D-Mail Plus** | `io.github.theonionsarewatching.honeymustard` | save / open / send | never |
| **D-Mail Pro** | `io.github.theonionsarewatching.onegshabbos` | save / open / send | on demand, per message |
| **D-Mail Max** | `io.github.theonionsarewatching.gefilte` | save / open / send | on demand, loaded by the browser engine |

**Rendering in Kosher and Plus is identical**: pure text, no images embedded
or otherwise. Plus only adds file handling — attachments are saved or handed
off to other apps, never displayed inside D-Mail.

**Pro vs Max**: both show images only when you ask. Pro downloads each image
itself and embeds it in the page; Max lets the built-in browser engine load
approved images directly — try Max if images don't appear on your device in
Pro. Nothing ever loads without a tap in either app.


## Kosher Features

In **D-Mail Kosher** (and, for rendering, in Plus):

1. **Attachments are never downloaded** (Kosher only). The IMAP layer fetches
   only `text/plain` and `text/html` parts. Every other MIME part is counted
   and skipped — its bytes are never requested from the server. A `⊘📎`
   indicator shows that attachments exist; they cannot be opened, saved, or
   previewed. In Plus/Pro, attachment bytes are fetched **only** when you
   explicitly open or save one.
2. **Remote content never loads.** The message renderer (`SafeWebView`) has
   JavaScript off, network loads blocked, and intercepts **every** resource
   request with an empty response. Remote images, CSS, fonts, and tracking
   pixels are all dead. In Pro, images load **only** after an explicit
   "load images" action; nothing ever loads automatically in any flavor.

## Plus / Pro / Max features

- **Attachments line at the top of the message** (no scrolling to the
  bottom): `📎 2 attachments — tap to open or save`. Pick one → **Open with
  another app** (handed off via FileProvider) or **Save to Downloads**.
- **Attach files when composing.** Attach button opens the system document
  picker (no storage permissions needed, dpad-friendly, multiple files,
  20 MB total). Tap the attached line to remove one.
- **Share target.** In a file manager, Share → D-Mail Plus/Pro opens a
  compose screen with the file attached.
- **Pro & Max: three view modes.** The right soft key jumps between HTML
  and text; tapping the date line cycles all three. Default mode set in
  Settings.
  - **Text only** — exactly the Kosher rendering.
  - **Text + images** (default) — pure text with `[image]` placeholders;
    tap one to load just that image, or use the top line
    (`🖼 5 images — tap to load all`). Embedded (cid:) images come over
    IMAP; remote ones over HTTPS — each only when you ask.
  - **Original HTML** — sanitized HTML (scripts/handlers stripped), images
    blocked until you press **Load images**.

## Features (all flavors)

- **Multiple accounts.** Add as many IMAP/SMTP accounts as you like. With
  more than one account, the app opens to an account picker showing each
  account's unread count (cached count shown instantly, live count fetched
  in the background). One account goes straight to the inbox.
- **Folders.** The Folders button lists every folder on the server; pick one
  to browse it. Move messages between folders from the long-press menu.
- **Offline cache.** Message lists and any opened (or prefetched) message
  bodies are stored in a local SQLite database and shown instantly, with or
  without a connection. When offline you'll see
  "Offline — showing saved messages." Bodies cached are the same text-only
  content — nothing non-kosher can enter the cache.
- **Pagination.** Loads a page of newest messages (size configurable);
  "Load older messages" pages further back.
- **Obvious read/unread.** Unread = accent dot + bold + full brightness.
  Read = dimmed. Starred messages show a gold ★.
- **Pure-text rendering.** Emails are never rendered as HTML: bodies are
  converted to plain text in the app's own light/dark theme — no colors, no
  backgrounds, no buttons, no layout tricks, ever. The only interactive
  elements are ones the app itself creates:
- **Link policy.** Raw URLs never appear in a message body. Depending on
  Settings, links show as a tappable **[link]** — tapping shows the URL as
  text with a Copy button (never navigates, never loads anything) — or are
  stripped entirely. Named links keep their text label with [link] after it.
- **Tap-to-copy contacts (optional, on by default).** Email addresses and
  phone numbers in a message are tappable: a dialog shows the value with
  Copy, plus Compose (for emails) or Dial (for phone numbers — opens the
  dialer, never places a call).
- **Long-press actions.** Long-press a message (touch, or hold SELECT on a
  dpad) for: mark read/unread, star/unstar, reply, forward, move to
  folder…, move to Trash, delete permanently. Long-press an account to edit
  or remove it.
- **Settings.** Theme, text size (Extra small–Huge), sort order, page size
  (25–200), auto refresh (off / on-open-if-stale / 1 min–hourly / once a
  day), link display mode, and how many newest messages to save for offline
  (off–50). Offline prefetch uses IMAP PEEK, so saving a message does
  **not** mark it read.
- **Dates follow the system.** 12/24-hour format matches the phone's
  setting; messages older than a year show the year instead of the time.
- **Material 3 UI**, light/dark/system theme, no wasted title bar. Dpad focus
  is always visible: focused rows get a rounded accent outline, focused
  buttons get an accent stroke.
- **Provider presets.** Add account → pick Gmail, Outlook/Hotmail/Office 365,
  Yahoo, iCloud, or AOL and only display name, email, and app password are
  asked; servers are filled in automatically (with per-provider app-password
  instructions). "Custom" exposes the full IMAP/SMTP form.
- **Text size** setting (Small–Huge) scales every screen and message bodies.
- **Sort** newest-first or unread-first.
- Reply, forward, compose (plain text only), delete (moves to Trash when the
  server has one, otherwise asks before deleting permanently).
- Contact photo or monogram avatars from the local address book.
- **Small-screen friendly.** Compact bars, buttons, and rows sized for
  240–320dp feature-phone screens.
- **Scroll position.** Lists get a scrollbar + draggable fast-scroll slider;
  the reader shows a thin progress bar that fills as you scroll the message.
- **Account order.** Long-press an account → Move up / Move down to set the
  order on the picker screen.
- **New-mail notifications (optional, off by default).** A 15-minute
  background check (envelope metadata only — same kosher IMAP layer, no
  bodies or attachments). Notify for all accounts or selected ones; content
  can show sender & subject or just a count. Tapping opens that account's
  inbox. Enabling never floods old mail — the first pass sets a baseline
  silently.
- **Soft keys (optional).** Small action labels above the phone's left/right
  soft keys, different per screen (list: Compose/Folders, reader: Reply/Mark read-unread,
  accounts: Add/Settings, compose: Send). Off unless the device model is in
  `res/xml/softkey_profiles.xml` or the user teaches their keys via Settings → Soft keys →
  Custom. The learn screen refuses every standard key (dpad, numbers,
  letters, volume, Back…) so normal keys can never be hijacked.

## Navigation

Standard Android keys throughout — dpad/arrow keys move focus, SELECT/ENTER
opens, long-press SELECT opens the actions menu, BACK goes up. There is no
key-remapping layer (the v0.1 calibration screen is gone). Works with touch,
dpad, or both.


## Setup on the phone

1. Enter IMAP/SMTP details. For Gmail-hosted mail use an **app password**
   (`imap.gmail.com:993 SSL`, `smtp.gmail.com:465 SSL`). Any standard
   IMAP/SMTP provider works.
2. Add more accounts anytime: Accounts screen → **Add account** (from the
   inbox: More → Accounts).

## Storage & privacy

- Credentials live only in Android's `EncryptedSharedPreferences`
  (Keystore-backed). Never logged, never shown in errors.
- The offline cache (`dmail.db`) holds envelope data and text bodies only.
  Removing an account deletes its cached mail.
- No analytics, no telemetry, no network connections except your own mail
  servers.

## Collecting soft-key profiles

To get your phone added to the built-in soft-key list:

1. The device owner installs D-Mail and learns their keys once:
   **Settings → Soft keys → Custom** (press left key, press right key, Save).
2. A **Soft-key report for this device** entry then appears at the bottom of
   Settings — open it.
3. Tap **Copy** (or **Share**) and post the report, which contains the
   exact model string, both key codes, and a ready-to-paste line like:
   `<profile model="Nokia 2720 Flip" left="1" right="2" />`
4. Paste that line into `softkey_profiles.xml`, replacing the dummy entries,
   and ship a new release. That device then gets soft keys automatically
   (Settings → Soft keys → Automatic).

The `model` attribute must be the device's exact `Build.MODEL` — the report
provides it verbatim, so no guessing.

## License

Copyright (c) 2026 theOnionsAreWatching.

D-Mail is source-available under the **PolyForm Noncommercial License 1.0.0**
(see `LICENSE.md`). In plain terms: you may use, build, modify, and share it
for **noncommercial** purposes — personal use, hobby projects, religious
observance, and nonprofit/educational organizations are all explicitly
permitted. **Selling it or any commercial use requires written permission
from the copyright holder.**

Required Notice: Copyright (c) 2026 theOnionsAreWatching (https://github.com/theOnionsAreWatching)

Bundled open-source libraries keep their own licenses — see
`THIRD-PARTY-NOTICES.md`.
