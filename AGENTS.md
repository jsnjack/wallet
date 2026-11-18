# AGENTS.md

> See [AGENTS.universal.md](./AGENTS.universal.md) and [AGENTS.android.md](./AGENTS.android.md) for shared conventions.
> Refresh: `make standards`

---

## Overview

`wallet` is an Android app (Kotlin, Jetpack Compose, personal sideload) for
loyalty cards, vouchers and tickets. Cards live in a local SQLite database
and open in one tap at full brightness; Google Drive's hidden app folder
keeps other devices in step. It is built for a Galaxy phone on One UI 9
(Android 17). The design, the decisions behind it and the v2/v3 plan
(boarding passes, .pkpass and PDF import, "Up next" suggestions, widgets)
are in `docs/design-options.html`.

---

## Architecture

```
app/src/main/java/io/github/jsnjack/wallet/
  core/                     Plain Kotlin, no Android types; everything here has JVM tests
    Card.kt                 Card, CardKind, CodeFormat, Usage
    CardDraft.kt            The add/edit form's state, validation and conversion to a Card
    CodeNumbers.kt          GS1 check digits, format guess for typed numbers, display grouping
    CodeMatrix.kt           ZXing: code → BitMatrix with no quiet zone
    CardLooks.kt            Text contrast, palette, colour for a name, monograms, hex
    Brands.kt               Bundled shop list for name suggestions and colours
    CardList.kt             Grid order, search matching, most-used cards for shortcuts
    SyncPlan.kt             SyncRules: file names, newer-wins plan, duplicate files
    CardJson.kt             Card file format v1 (sync files and export bundle)
    ExportCrypto.kt         Export file encryption: PBKDF2-HMAC-SHA256 + AES-256-GCM
  store/
    CardDatabase.kt         SQLiteOpenHelper: cards (synced) and usage (this device only)
    CardStore.kt            In-memory cards with write-through, StateFlows for the UI
  drive/
    DriveAuth.kt            Play services AuthorizationClient for the drive.appdata scope
    DriveClient.kt          Drive REST v3 over HttpURLConnection: list, download, create, update
    SyncController.kt       Sync runs, status, WorkManager scheduling, sync preferences
    SyncWorker.kt           Background run; silent authorization only
  platform/
    CodeReader.kt           zxing-cpp: camera frames and images → ReadCode
    CodeBitmaps.kt          BitMatrix → 1-pixel-per-module Bitmap, LRU cached
    Shortcuts.kt            Dynamic (top 3) and pinned launcher shortcuts
    Biometrics.kt           Framework BiometricPrompt (strong biometric or screen lock)
  ui/
    WalletViewModel.kt      Screen stack, actions, snackbar events, export/import
    WalletRoot.kt           Shows the top screen, snackbar, Google consent launcher
    GridScreen.kt, CodeScreen.kt, ScanScreen.kt, CardFormScreen.kt, SettingsScreen.kt, FirstRunScreen.kt
    CardParts.kt            CardTile, pixel-exact CodeImage, kind labels
    Theme.kt                Material 3 scheme from res/values(-night)/colors.xml
  MainActivity.kt           Compose host; routes shortcut and share intents; holds the first frame until cards load
  WalletApplication.kt      Lazy CardStore and SyncController; on-demand WorkManager
docs/design-options.html    Design, decisions and roadmap (bunk-gui page)
```

External services:
- Google Play services (`AuthorizationClient`) for Drive access.
- `www.googleapis.com/drive/v3` and `/upload/drive/v3`, app data folder only.

---

## Key Flows

1. **Open a card.** `WalletViewModel` loads `CardStore` on start; the first
   frame waits for it. A tap pushes `Screen.Code`; `CodeScreen` fixes the
   page order when it opens, sets full brightness and keeps the screen on,
   and counts a use after one second on a page.
2. **Add a card.** ＋ opens `ScanScreen` (CameraX + zxing-cpp). The first
   drawable code replaces the scanner with the form (`CardDraft.scanned`).
   Picked or shared images go through `CodeReader.readImage`; "Type number"
   opens an empty form whose format follows `CodeNumbers.guessFormat` until
   the user picks one. Saving a new card opens its code screen.
3. **Sync.** `CardStore` calls `SyncController.onLocalChange` after user
   changes, which schedules `SyncWorker` 5 s later (unique work, replaced on
   each change); the app also asks for a run at start and every 6 hours.
   A run lists the app folder, compares each card's `updatedAt`
   (stored in the file's `appProperties`) with the local one, downloads
   newer remote cards and uploads newer local ones.
4. **Delete.** A deleted card becomes a blank tombstone (`Card.tombstone`)
   that syncs like any card; undo saves the old card again with a new time.

---

## Build & Run

```bash
make check        # debug APK + JVM tests + Android lint
make build        # debug APK → bin/wallet.apk, version from monova
make install      # build + adb install -r
make test         # JVM unit tests (core/)
make logs         # adb logcat -s Wallet
make release      # check, upload GitHub release, then queue F-Droid publication (not set up yet)
make fdroid-publish  # retry F-Droid publication for the current commit
```

The Makefile uses `JAVA_HOME`, else a JDK 21 in `~/.jdks` (README.md). The
SDK path is in `local.properties`; the build pins platform 37.0 and build
tools 37.0.0 and the Makefile checks for them. Toolchain: Gradle 9.8.1,
AGP 9.4.1 with its built-in Kotlin 2.4.21, Compose BOM 2026.09.00.

Version: `make build` passes `-PappVersion` from `monova`;
`versionCode = major * 1,000,000 + minor * 1,000 + patch`. Without commits or
without the property the version is 0.0.1, code 1, for local use only.

Supported: Android 13+ (`minSdk` 33), target 37. Permissions: camera,
internet (Drive only), biometric. Not yet tested on a device.

Signing: local builds use the debug keystore. A dedicated app key is still
to be created before the first F-Droid release (AGENTS.android.md); the
Google OAuth client must list the SHA-1 of every key that signs an
installed build.

Logging: tag `Wallet`. Log sync counts, HTTP status codes and error
messages only; card names, numbers, notes and account addresses are
personal data and are never logged. `CodeMatrix` errors name the format,
never the code.

---

## Design Decisions

- **Local first.** The SQLite database is the main copy and all cards are
  held in memory, so nothing on the path from icon to code waits for disk
  scans or the network. Sync runs after the first frame.
- **Framework SQLite, not Room.** One table of a few hundred rows read once
  at start; Room's annotation processor would add build complexity with no
  query to gain from. Usage counts are a separate table and never synced,
  because they change on every view.
- **One Drive file per card, newer wins.** `appProperties.updatedAt` lets a
  sync decide from the listing alone. Same-card edits on two devices keep
  the later one; a merge screen would be overkill for one person.
- **No passphrase for sync** (decided 2026-11-19). Drive files are plain
  JSON protected by the Google account. Per-card files keep client-side
  encryption possible later without changing the sync design. Export files
  are always password-encrypted because they can end up anywhere.
- **Only the `drive.appdata` scope.** Non-sensitive, so no Google review;
  the consent screen must be "In production" or access expires after 7 days.
- **No Credential Manager or Google Drive client library.**
  `AuthorizationClient` picks the account and grants the scope in one step,
  and four REST calls do not justify the Drive client's size.
- **Pixel-exact codes.** `CodeMatrix` encodes with no margin;
  `CodeImage` scales by whole pixels with no filtering, on a white panel in
  both themes. QR codes get a UTF-8 charset hint only for non-Latin-1 text,
  because the ECI marker it adds confuses some shop scanners.
- **Raw scan text.** `CodeReader` uses zxing-cpp's `PLAIN` text mode so GS1
  and similar codes can be drawn again exactly as read.
- **Text colour by WCAG contrast.** White when it reaches 3:1 (large or bold
  text), otherwise near-black; Albert Heijn's light blue gets dark text.
- **Brand list is approximate.** `Brands.ALL` holds names and approximate
  colours only, never number prefixes, so nothing guesses a shop from a
  number.

---

## Gotchas

- `ProcessCameraProvider.awaitInstance` is an extension function; it needs
  `import androidx.camera.lifecycle.awaitInstance`.
- zxing-cpp builds its Kotlin result classes from native code;
  `proguard-rules.pro` keeps `zxingcpp.**` for minified builds.
- Shortcut updates are rate-limited for background apps; `Shortcuts.update`
  runs from the ViewModel and logs and skips when the limit is hit.
- HttpURLConnection has no PATCH; Drive updates use POST with
  `X-HTTP-Method-Override: PATCH`.
