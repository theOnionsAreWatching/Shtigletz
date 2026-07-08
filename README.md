# D-Mail

A kosher-friendly, dpad-navigable email client for Android.

## Kosher Features

1. **Attachments are never downloaded.** The IMAP layer fetches only
   `text/plain` and `text/html` parts. Every other MIME part is counted and
   skipped — its bytes are never requested from the server. A `⊘📎` indicator
   shows that attachments exist; they cannot be opened, saved, or previewed.
2. **Remote content never loads.** The message renderer (`SafeWebView`) has
   JavaScript off, network loads blocked, and intercepts **every** resource
   request with an empty response. Remote images, CSS, fonts, and tracking
   pixels are all dead. The only images anywhere in the app are contact
   photos already stored on the phone.

## Other Features (v0.6)

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
- **Settings.** Theme, text size, sort order, page size (25–200), in-app
  auto refresh (off–30 min), link display mode, and how many newest messages
  to save for offline (off–50).
  Offline prefetch uses IMAP PEEK, so saving a message does **not** mark it
  read.
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
  `res/xml/softkey_profiles.xml` (dummy entries for now — add real models as
  collected) or the user teaches their keys via Settings → Soft keys →
  Custom. The learn screen refuses every standard key (dpad, numbers,
  letters, volume, Back…) so normal keys can never be hijacked.

## Navigation

Standard Android keys throughout — dpad/arrow keys move focus, SELECT/ENTER
opens, long-press SELECT opens the actions menu, BACK goes up. Works with touch,
dpad, or both.


## Setup on the phone

1. Sideload the APK (enable "Install unknown apps" for your file manager).
2. Enter IMAP/SMTP details. For Gmail-hosted mail use an **app password**
   (`imap.gmail.com:993 SSL`, `smtp.gmail.com:465 SSL`). Any standard
   IMAP/SMTP provider works.
3. Add more accounts anytime: Accounts screen → **Add account** (from the
   inbox: More → Accounts).

## Storage & privacy

- Credentials live only in Android's `EncryptedSharedPreferences`
  (Keystore-backed). Never logged, never shown in errors.
- The offline cache (`dmail.db`) holds envelope data and text bodies only.
  Removing an account deletes its cached mail.
- No analytics, no telemetry, no network connections except your own mail
  servers.
