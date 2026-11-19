# AGENTS.md

> See [AGENTS.universal.md](./AGENTS.universal.md) and [AGENTS.android.md](./AGENTS.android.md) for shared conventions.
> Refresh: `make standards`

---

## Overview

`wallet` is an Android app (Kotlin, Jetpack Compose, personal sideload) for
loyalty cards, vouchers, gift cards, tickets and boarding passes. Cards live
in a local SQLite database and open in one tap at full brightness; Google
Drive's hidden app folder keeps other devices in step. Time-based rules
archive used and past cards and surface the card needed next in the grid,
a notification, two widgets and a Quick Settings tile. It is built for a
Galaxy A56 on One UI 9 (Android 17). The design, decisions and plan are in
`docs/design-options.html`.

---

## Architecture

```
app/src/main/java/io/github/jsnjack/wallet/
  core/                     Plain Kotlin, no Android types; everything here has JVM tests
    Card.kt                 Card, CardKind, CodeFormat, Detail, Flight, Usage
    CardDraft.kt            The add/edit form's state, validation, boarding-pass fill-in, conversion to a Card
    DayTime.kt, Money.kt    Date with optional time (no zone); euro balances
    Bcbp.kt                 IATA boarding pass barcode text → passenger and legs
    Pkpass.kt               Apple Wallet .pkpass zip → CardDraft
    Airports.kt             IATA code → city table parsing
    Agenda.kt               Archive, retention, "Up next", reminders, next change
    CardGroups.kt           Passes for one flight and tickets for one show: one tile, swipe between them
    CodeNumbers.kt          GS1 check digits, format guess for typed numbers, display grouping
    CodeMatrix.kt           ZXing: code → BitMatrix with no quiet zone
    CardLooks.kt            Text contrast, palette, colour for a name, monograms, hex
    Brands.kt               Bundled shop list for name suggestions and colours
    CardList.kt             Grid order, search, favourites, quick access (favourites then most used)
    ConnectProblem.kt       Google status codes → why turning on sync failed
    SyncPlan.kt             SyncRules: card and settings file names, newer-wins plan, duplicate files
    SyncedSettings.kt       Settings that sync through Drive (the flight-status key), one file
    LiveFlight.kt           FlightRef (one flight on one day), LiveFlight, FlightCheck, LookupProblem
    AeroDataBox.kt          AeroDataBox flight status JSON → LiveFlight
    FlightLookups.kt        When to look a flight up, monthly allowance, changes worth an alert
    FlightProgress.kt       A flight's day as a 0–100 progress bar, and when to redraw it
    CardJson.kt             Card file format v2 (sync files, export bundle, database JSON columns)
    ExportCrypto.kt         Export file encryption: PBKDF2-HMAC-SHA256 + AES-256-GCM
  store/
    CardDatabase.kt         SQLiteOpenHelper v2: cards (synced) and usage (this device only)
    CardStore.kt            In-memory cards with write-through, StateFlows for the UI
    AppSettings.kt          Archive retention, notifications, reminders already posted, flight-status key
    FlightStore.kt          Latest lookup per flight and this month's lookup count (this device only)
  drive/
    DriveAuth.kt            Play services AuthorizationClient for the drive.appdata scope
    DriveClient.kt          Drive REST v3 over HttpURLConnection: list, download, create, update
    SyncController.kt       Sync runs, status, WorkManager scheduling, sync preferences
    SyncWorker.kt           Background run; silent authorization only
  flight/
    AeroDataBoxClient.kt    RapidAPI GET for a flight or a key check; LookupRefused for bad key or used-up quota
    FlightUpdater.kt        Due lookups (FlightWorker), lookup on opening a pass, key check, scheduling
    FlightText.kt           Status labels shared by the code screen, Now Bar and notifications
  platform/
    CodeReader.kt           zxing-cpp: camera frames, images and PDF pages → ReadCode
    AirportTable.kt         Loads assets/airports.tsv once
    CodeBitmaps.kt          BitMatrix → 1-pixel-per-module Bitmap, LRU cached
    Shortcuts.kt            Dynamic (top 3) and pinned launcher shortcuts
    Biometrics.kt           Framework BiometricPrompt (strong biometric or screen lock)
    CardShare.kt            A card as a picture (name, pixel-exact code, number) for the share sheet
  surface/
    Surfaces.kt             Refresh of everything outside the app; AgendaWorker runs it at the next change
    Notifier.kt             "Today" ongoing notification (Live Update on 36.1+, flight progress bar on 36+),
                            expiry reminders, flight change alerts
    Widgets.kt              RemoteViews widgets: Up next (4×1) and Favourite cards (4×2)
    WalletTileService.kt    Quick Settings tile
  ui/
    WalletViewModel.kt      Screen stack, actions, imports, snackbar events, export/import
    WalletRoot.kt           Shows the top screen, snackbar, Google consent launcher
    GridScreen.kt           Bottom-up grid, favourites rail, Now Bar, ⋯ and long-press sheets
    CodeScreen.kt, ScanScreen.kt, CardFormScreen.kt, FirstRunScreen.kt
    ArchiveScreen.kt, SettingsScreen.kt   Full-height bottom sheets (ArchiveSheet, SettingsSheet)
    CardParts.kt            Big-type CardTile and MiniTile, pixel-exact CodeImage, DetailGrid, dates, usage text
    OneUi.kt                One UI parts: TitleBar, Group/GroupRow, WalletSwitch, WalletChip, WalletField, ToastSnackbar,
                            WalletSheet, SheetRow, ConfirmSheet, BottomButtons
    Theme.kt                Material 3 scheme and shapes from res/values(-night)/colors.xml (One UI teal)
  MainActivity.kt           Compose host; routes shortcut and share intents; holds the first frame until cards load
  WalletApplication.kt      Lazy CardStore and SyncController; on-demand WorkManager
app/src/main/assets/airports.tsv   Bundled airport table (make airports)
scripts/airports.sh                Builds it from OurAirports (public domain)
docs/design-options.html           Design, decisions and roadmap (bunk-gui page)
docs/style-storybook.html          Five visual styles compared; One UI 9 was chosen
docs/one-hand.html                 One-handed layouts and tile styles compared
docs/favourites.html               Favourites ideas compared
docs/flight-info.html              Live flight info: services compared, AeroDataBox chosen
```

External services:
- Google Play services (`AuthorizationClient`) for Drive access.
- `www.googleapis.com/drive/v3` and `/upload/drive/v3`, app data folder only.
- `aerodatabox.p.rapidapi.com` (AeroDataBox through RapidAPI) with the
  user's own key: flight status by number and date, and an airport lookup
  to check the key.

---

## Key Flows

1. **Open a card.** `WalletViewModel` loads `CardStore` on start; the first
   frame waits for it. A tap pushes `Screen.Code`; `CodeScreen` fixes its
   pages when it opens (the card's group from `CardGroups`, or the card
   alone), sets full brightness and keeps the screen on, and counts a use
   after one second on a page.
2. **Add a card.** ＋ opens `ScanScreen` (CameraX + zxing-cpp). The first
   drawable code replaces the scanner with the form (`CardDraft.scanned`).
   Picked or shared images go through `CodeReader.readImage`; "Type number"
   opens an empty form whose format follows `CodeNumbers.guessFormat` until
   the user picks one; only then does the form show the code type picker.
   A scanned, imported or saved code keeps its type behind "Change code
   type", because changing it can make the card unreadable at the till.
   Saving a new card opens its code screen.
3. **Sync.** `CardStore` calls `SyncController.onLocalChange` after user
   changes, which schedules `SyncWorker` 5 s later (unique work, replaced on
   each change); the app also asks for a run at start and every 6 hours.
   A run lists the app folder, compares each card's `updatedAt`
   (stored in the file's `appProperties`) with the local one, downloads
   newer remote cards and uploads newer local ones. `settings.json`
   (`SyncedSettings`, the flight-status key) follows the same rule.
4. **Delete.** A deleted card becomes a blank tombstone (`Card.tombstone`)
   that syncs like any card; undo saves the old card again with a new time.
5. **Import a file.** Shared or picked files go to `WalletViewModel.importFile`,
   which sniffs the bytes: a zip is read by `Pkpass`, `%PDF` pages are
   rendered and read by `CodeReader.readPdf`, anything else is read as an
   image. Several codes open a chooser. `importScreenshots` reads many
   images (the Google Wallet route) and queues one form per card found.
6. **Archive and agenda.** `Agenda` decides from `usedAt`, `expires` and,
   for tickets and boarding passes, `event` whether a card is archived; the
   ViewModel ticks a clock every minute. `Surfaces.refresh` runs after card
   changes (debounced), after a sync that downloaded cards, from widget
   updates and from `AgendaWorker`, which it schedules for
   `Agenda.nextChange` (at least next midnight). It deletes cards past the
   retention, posts or cancels notifications, redraws widgets and asks the
   tile to refresh, then has `FlightUpdater` schedule the next lookup.
7. **Live flight info.** With a key in Settings, `FlightUpdater.schedule`
   finds the earliest lookup `FlightLookups.next` asks for among current
   boarding passes (grouped by `FlightRef`) and enqueues `FlightWorker`
   (unique work "flights", network required). `runDue` looks up what is
   due, one request a second, keeping 10 of the 200 monthly lookups for
   opening a pass; `CodeScreen` calls `refreshOnOpen` for each page. A
   result goes to `FlightStore`, fills in a pass's missing departure time,
   posts an alert for a gate, time or cancellation change, and refreshes
   the surfaces. A refused key or used-up quota stops lookups
   (`LookupProblem`); other failures wait 10 minutes. On Android 16 and
   newer the "today" notification for a flight carries a
   `Notification.ProgressStyle` bar from `FlightProgress`; `Surfaces`
   schedules `AgendaWorker` every 10 minutes while the bar moves.
8. **Share a card.** The long-press menu and the code screen call
   `WalletViewModel.shareCard`: `CardShare` draws the name, the code at
   whole pixels with a quiet zone, and the number into a PNG in
   `cache/shared/`, served by the FileProvider `<applicationId>.files`, and
   sends it with the name as `EXTRA_TEXT`. A shared image arriving with text
   becomes the form's name (`importFile(nameHint)`) when the code has none.

---

## Build & Run

```bash
make check        # debug APK + JVM tests + Android lint
make build        # debug APK → bin/wallet.apk, version from monova
make install      # build + adb install -r
make test         # JVM unit tests (core/)
make logs         # adb logcat -s Wallet
make airports     # refresh assets/airports.tsv from OurAirports
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
internet (Drive and AeroDataBox), biometric, notifications, promoted notifications
(Live Updates). Tested over adb on a Galaxy A56 (SM-A566B, One UI 9,
Android 17): the screens in light and dark mode, the v1 → v2 database
upgrade, image, multi-screenshot, PDF and .pkpass import, boarding pass
reading, "Up next", the promoted "today" notification, voucher "Mark as
used" and the archive. Not yet tested on a device: camera scanning of a
real card, biometric unlock, Drive sync (needs the Google Cloud setup)
and the widgets. The Quick Settings tile was tested with
`cmd statusbar add-tile` / `click-tile` and removed again.

Signing: local builds use the debug keystore. A dedicated app key is still
to be created before the first F-Droid release (AGENTS.android.md); the
Google OAuth client must list the SHA-1 of every key that signs an
installed build.

Logging: tag `Wallet`. Log sync counts, HTTP status codes and error
messages only; card names, numbers, notes and account addresses are
personal data and are never logged. `CodeMatrix` errors name the format,
never the code. Flight lookups log the flight's state and HTTP statuses,
never the flight number, date or API key, and parser errors only by type,
because their messages quote the response.

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
- **One UI 9 style, compact** (decided 2026-11-19 from
  `docs/style-storybook.html`). Teal accent, grouped rounded containers,
  glossy tiles with a soft shadow, a dark "Now Bar" capsule for Up next,
  One UI switches, chips and toasts, and Material Symbols Rounded weight
  300 line icons (Apache 2.0, converted to vector drawables). One UI's tall
  title area is replaced by a 60 dp title bar so content uses the screen;
  square codes fill the panel width.
- **One-handed by default, no mode to switch on** (decided 2026-11-19 from
  `docs/one-hand.html`, options A + D + C's swipe-down). The grid is laid
  out right-to-left and bottom-up (`reverseLayout`, RTL with LTR content),
  so the most-used card sits bottom-right; Up next is the grid's first item,
  just above the bottom bar. Nothing interactive sits at the top: the ⋯
  button opens a sheet with Archive, Settings and imports; long-press menus,
  confirmations, the code chooser, Settings and Archive are bottom sheets;
  the code screen closes with a swipe down (nested-scroll overscroll) or
  Done; the form has Cancel/Skip and Save at the bottom; the scanner's close
  and torch are at the bottom.
- **Groups instead of swiping through everything** (decided 2026-11-19).
  Boarding passes with the same airline, flight number, departure airport
  and date form one tile ("2 passengers"), whatever their booking
  reference; tickets with the same name and event date and time form one
  tile too. The code screen swipes only within a group, so a loyalty card
  opens alone and nobody slides to the wrong card at a till. Other flights
  of a booking are separate tiles. Up next, the notification, widgets and
  tile show one card per group; deleting a grouped tile from the grid
  deletes the whole group, while Edit → Delete removes one card.
- **Big-type tiles** (decided 2026-11-19): brand colour, the name at the
  top, a giant monogram or plane bleeding off the bottom-right. Usage
  ("Used 3×, last 2 days ago") shows on the code screen, measured before the
  current visit.
- **Favourites rail plus favourites outside the app** (decided 2026-11-19
  from `docs/favourites.html`, ideas 2 + 4). Favourites reuse the `pinned`
  field, so the file format and database are unchanged. The rail sits above
  the bottom bar, fills right to left like the grid, and hides during a
  search; favourites also stay in the grid (the user prefers duplicates to
  missing cards). Shortcuts, the favourites widget and the Quick Settings
  tile (when nothing is up next) use `CardList.quickAccess`: favourites,
  then the most used cards.
- **Turning on sync explains itself.** `WalletViewModel.connect` tracks the
  attempt; Settings and the first-run screen show "Connecting to Google…"
  or the reason Google refused (`ConnectProblem`: not registered, Drive API
  off, no network, other with Google's status code). Cancelling shows
  nothing.
- **Google Wallet import is by screenshot.** Google Wallet and Samsung
  Wallet offer no export to other apps; a screenshot of each card's barcode
  is read like any image, several at once.
- **Archive is derived, not stored.** Only "used at" is stored; expiry and
  past events are computed from dates each time, so changing a date moves a
  card in or out of the archive. Retention deletes create tombstones that
  sync.
- **Widgets use RemoteViews, not Glance.** Two small fixed layouts do not
  justify the Glance dependency; tiles are tinted with
  `setBackgroundTintList`.
- **File format v2 is additive.** New fields have defaults, so v1 files read
  fine; a v1 app ignores the new fields and drops them when it re-uploads a
  card, so update all devices together.
- **Brand list is approximate.** `Brands.ALL` holds names and approximate
  colours only, never number prefixes, so nothing guesses a shop from a
  number.

---

- **Live flight info from AeroDataBox with the user's key** (decided
  2026-11-19 from `docs/flight-info.html`). Free RapidAPI Basic plan: 400
  units a month, 2 per flight lookup, so 200 lookups; gate, terminal,
  check-in desk, belt, revised and predicted times and status in one call,
  for every airport. The key is pasted in Settings and syncs through Drive
  in `settings.json` (the user's choice over one key per phone); a key
  built into the APK would be public and missing from F-Droid builds. Live
  results stay on the device and never sync: they change every few minutes.
  Each device counts its own lookups.
- **A shared card is a picture, not a file format** (decided 2026-11-19).
  Anyone can show the picture at a till without the app, Wallet imports it
  with the existing image route, and no import format has to stay stable.
  Name and colour travel as the share text and the brand list; dates,
  notes and PIN do not.
- **Lookups follow the flight's day** (`FlightLookups`): once 24 hours
  before (or 08:00 the day before when the pass has no time), every 30
  minutes from 4 hours before departure, every 15 minutes in the last hour,
  once 10 minutes after the expected landing; a flight the service does not
  know is tried once more at 06:00 on the day. About 12–15 lookups per
  flight, shared by every passenger on it.

## Gotchas

- `adb shell am start … --grant-read-uri-permission` cannot grant MediaStore
  URIs, so a shared image from adb arrives without access; the app reports
  "could not be read". Test imports through the photo picker or a real share.
- The phone locks during long adb sessions; a blind tap at the bottom right
  of the lock screen opens the camera. Check `topResumedActivity` before
  tapping or taking screenshots.
- `AgendaWorker` reschedules itself with `ExistingWorkPolicy.REPLACE`, which
  cancels the running run right after it enqueued the next one. Doze can
  delay the run, so a morning reminder may arrive late.
- `Surfaces.refresh` schedules `AgendaWorker` with REPLACE last, after
  `FlightUpdater.schedule`: when AgendaWorker runs the refresh, the REPLACE
  cancels it, and any later suspending call would not run.
- Google's authorization API reports an unregistered app (no OAuth client
  for the package and signing SHA-1) as status 8, INTERNAL_ERROR; the real
  reason is only in the Play services log (tag `Auth`, "not registered to
  use OAuth2.0"). `ConnectProblem` treats 8 like 10 as not registered.
- `Intent.setClipData` returns nothing, so it cannot be chained; the share
  intent sets the clip separately so the read grant reaches the app picked
  in the chooser.
- RapidAPI answers HTTP 429 both for the monthly quota and for more than
  one request a second; `AeroDataBoxClient` treats only a body that
  mentions "quota" as a used-up allowance.
- AeroDataBox's spec does not pin down its time format: responses have
  been seen as "2026-11-20 14:10+01:00", the spec says ISO date-time;
  `AeroDataBox.localTime` reads the wall-clock part of either form.
- `Notification.Builder.setRequestPromotedOngoing` is API 36.1: guard with
  `SDK_INT_FULL >= VERSION_CODES_FULL.BAKLAVA_1`, not `SDK_INT >= 36`.

- `ProcessCameraProvider.awaitInstance` is an extension function; it needs
  `import androidx.camera.lifecycle.awaitInstance`.
- zxing-cpp builds its Kotlin result classes from native code;
  `proguard-rules.pro` keeps `zxingcpp.**` for minified builds.
- Shortcut updates are rate-limited for background apps; `Shortcuts.update`
  runs from the ViewModel and logs and skips when the limit is hit.
- HttpURLConnection has no PATCH; Drive updates use POST with
  `X-HTTP-Method-Override: PATCH`.
- A Material snackbar with an action defaults to `Indefinite`; Undo
  snackbars pass `SnackbarDuration.Long`.
- A `ModalBottomSheet` is its own window and hides the screen's snackbar;
  `WalletSheet` shows `LocalSnackbar` inside the sheet.
- The phone used for testing holds the user's real cards: install with
  `adb install -r` only, never uninstall or clear data.
