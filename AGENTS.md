# AGENTS.md

> See [AGENTS.universal.md](./AGENTS.universal.md) and [AGENTS.android.md](./AGENTS.android.md) for shared conventions.
> Refresh: `make standards`

---

## Overview

`wallet` is an Android app (Kotlin, Jetpack Compose, personal sideload) for
loyalty cards, vouchers, gift cards, tickets and boarding passes. Cards live
in a local SQLite database and open in one tap, with full brightness one
more tap away; the phone's Google backup keeps a copy. Time-based rules
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
    CardList.kt             CardOrder (A–Z or most used), search, favourites, quick access (favourites then most used)
    CardVersions.kt         "Last changed" times on save, edit and delete; which imported copies are newer
    CodeChoice.kt           One code per text when a file repeats it (a ticket's QR and barcode), square codes first;
                            the other format becomes Card.altFormat
    TicketText.kt           E-ticket text (English, Dutch) → event name, date and time, door details, venue and address
    CardPlace.kt            A card's venue, address or coordinates as one maps search
    CardDates.kt            "Coming up" cards and the date chip: event ahead, start or end within 30 days
    LiveFlight.kt           FlightRef (one flight on one day), LiveFlight, FlightCheck
    FlightStats.kt          flightstats.com flight tracker JSON → LiveFlight
    FlightLookups.kt        When to look a flight up, changes worth an alert
    FlightProgress.kt       A flight's day as a 0–100 progress bar, and when to redraw it
    CardJson.kt             Card format v4 (export bundle, database JSON columns)
    DraftJson.kt            Form drafts as JSON for the app's saved state (never in files)
    ExportCrypto.kt         Export file encryption: PBKDF2-HMAC-SHA256 + AES-256-GCM
  store/
    CardDatabase.kt         SQLiteOpenHelper v4: cards and usage counts; upgrades only add columns
    CardStore.kt            In-memory cards with write-through, StateFlows for the UI
    AppSettings.kt          Card order, archive retention, notifications, reminders already posted, live flights switch
    FlightStore.kt          Latest lookup per flight and the retry time after a failure
  flight/
    FlightStatsClient.kt    GET of one flight from flightstats.com's tracker data, no key
    FlightUpdater.kt        Due lookups (FlightWorker), lookup on opening a pass, scheduling
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
    WalletRoot.kt           Shows the top screen, snackbar, code chooser, notification permission request
    ImportPickers.kt        Screenshot and pass/PDF pickers, remembered by a screen that stays open
    GridScreen.kt           Bottom-up grid, favourites rail, Now Bar, ⋯ and long-press sheets
    CodeScreen.kt, ScanScreen.kt, CardFormScreen.kt
    ArchiveScreen.kt, SettingsScreen.kt   Full-height bottom sheets (ArchiveSheet, SettingsSheet)
    CardParts.kt            Big-type CardTile and MiniTile, pixel-exact CodeImage, DetailGrid, dates, usage text
    OneUi.kt                One UI parts: TitleBar, Group/GroupRow, WalletSwitch, WalletChip, WalletField, ToastSnackbar,
                            WalletSheet, SheetRow, ConfirmSheet, BottomButtons
    Theme.kt                Material 3 scheme and shapes from res/values(-night)/colors.xml (One UI teal)
  MainActivity.kt           Compose host; routes shortcut and share intents; holds the first frame until cards load
  WalletApplication.kt      Lazy CardStore, settings, flight lookups and surfaces; on-demand WorkManager
app/src/main/assets/airports.tsv   Bundled airport table (make airports)
scripts/airports.sh                Builds it from OurAirports (public domain)
docs/design-options.html           Design, decisions and roadmap (bunk-gui page)
docs/style-storybook.html          Five visual styles compared; One UI 9 was chosen
docs/one-hand.html                 One-handed layouts and tile styles compared
docs/favourites.html               Favourites ideas compared
docs/flight-info.html              Live flight info: services compared, FlightStats (no key) chosen
docs/icons.html                    Ten app icon ideas; Big-type W chosen
```

External services:
- Android's Google backup (Auto Backup, `data_extraction_rules.xml`): the
  card database and settings, about once a day; no code of our own.
- `www.flightstats.com/v2/api-next/flight-tracker/{airline}/{number}/{y}/{m}/{d}`:
  the JSON behind flightstats.com's tracker pages, no key; unofficial.

---

## Key Flows

1. **Open a card.** `WalletViewModel` loads `CardStore` on start; the first
   frame waits for it. A tap pushes `Screen.Code`; `CodeScreen` fixes its
   pages when it opens (the card's group from `CardGroups`, or the card
   alone), keeps the screen on, and counts a use after one second on a
   page. The "Full brightness" pill at the top switches full brightness on
   for every page until the screen closes.
2. **Add a card.** ＋ opens `ScanScreen` (CameraX + zxing-cpp). The first
   drawable code replaces the scanner with the form (`CardDraft.scanned`).
   Picked or shared images go through `CodeReader.readImage`; "Type number"
   opens an empty form whose format follows `CodeNumbers.guessFormat` until
   the user picks one; only then does the form show the code type picker.
   A scanned, imported or saved code keeps its type behind "Change code
   type", because changing it can make the card unreadable at the till.
   Saving a new card opens its code screen.
3. **Delete.** A deleted card becomes a blank tombstone (`Card.tombstone`),
   hidden everywhere; undo saves the old card again with a new time.
4. **Import a file.** Shared or picked files go to `WalletViewModel.importFile`,
   which sniffs the bytes: a zip is read by `Pkpass`, `%PDF` pages are
   rendered and read by `CodeReader.readPdf`, anything else is read as an
   image. Codes with the same text count once (`CodeChoice`), keeping the
   other format as `altFormat`. Each `ReadCode` knows its page; on Android
   15+ `readPdf` also returns each page's text, which `TicketText.readPage`
   turns into the form's name, date and details (`CardDraft.withTicket`).
   Several codes open a chooser with a picture of each; when every code is
   on its own page it offers "Add both/all", which queues one form per code
   (`addAllChoices`). `importScreenshots` reads many
   images (the Google Wallet route) and queues one form per card found.
5. **Archive and agenda.** `Agenda` decides from `usedAt`, `expires` and,
   for tickets and boarding passes, `event` whether a card is archived; the
   ViewModel ticks a clock every minute. `Surfaces.refresh` runs after card
   changes (debounced), after flight lookups, from widget updates and from
   `AgendaWorker`, which it schedules for
   `Agenda.nextChange` (at least next midnight). It deletes cards past the
   retention, posts or cancels notifications and redraws widgets, then has
   `FlightUpdater` schedule the next lookup. The tile redraws itself each
   time the Quick Settings panel opens.
6. **Live flight info.** Unless Settings turns it off, `FlightUpdater.schedule`
   finds the earliest lookup `FlightLookups.next` asks for among current
   boarding passes (grouped by `FlightRef`) and enqueues `FlightWorker`
   (unique work "flights", network required). `runDue` looks up what is
   due, a second apart; `CodeScreen` calls `refreshOnOpen` for each page. A
   result goes to `FlightStore`, fills in a pass's missing departure time,
   posts an alert for a gate, time or cancellation change, and refreshes
   the surfaces. A failed lookup, or a response that is not the expected
   JSON, keeps the last result and makes background lookups wait 10 minutes.
   On Android 16 and
   newer the "today" notification for a flight carries a
   `Notification.ProgressStyle` bar from `FlightProgress`; `Surfaces`
   schedules `AgendaWorker` every 10 minutes while the bar moves.
7. **Share a card.** The long-press menu and the code screen call
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
make release-apk  # minified, non-debuggable APK signed with the personal key → bin/release/wallet.apk
make release      # check, upload the release APK to GitHub, then queue F-Droid publication (jsnjack/android-apps)
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
internet (flightstats.com), biometric, notifications, promoted notifications
(Live Updates). Tested over adb on a Galaxy A56 (SM-A566B, One UI 9,
Android 17): the screens in light and dark mode, the v1 → v2 database
upgrade, image, multi-screenshot, PDF and .pkpass import, boarding pass
reading, "Up next", the promoted "today" notification, voucher "Mark as
used", the archive, search, the Settings sections and a Google backup run
(`bmgr backupnow`). Not yet tested on a device: camera scanning of a real
card, biometric unlock, the widgets, sharing, the flight progress bar and
live flight info. The Quick Settings tile was tested with
`cmd statusbar add-tile` / `click-tile` and removed again.

Signing: local builds use the debug keystore, which is also the personal app
key that `jsnjack/android-apps` signs every published app with (alias
`androiddebugkey`, certificate SHA-256 `53d59ccc…13ebf`). So repository
builds update a locally installed build in place and keep its cards, and
Google backup restores into either. Android refuses an update signed with
another key; moving to a new key means export, uninstall, install, import.

Releases: the publisher builds `assembleRelease` (R8 minify and resource
shrinking) at the released commit and verifies package, version, certificate
and that it is not debuggable. The first release build (0.15.0) was signed
locally the same way, installed over the phone's debug build and started
with no crash. The published version code is the higher of `version_floor`
(15000 in `apps.json`) plus one and the `make release` mapping; local
builds installed afterwards must not be lower. The GitHub release carries
the same kind of APK (`make release-apk`: `assembleRelease`, signed with
`apksigner` and the debug keystore); the debug APK stays local, because
installed over the real app it would let `run-as` read the card database.

Logging: tag `Wallet`. Log counts, HTTP status codes and error
messages only; card names, numbers, notes and account addresses are
personal data and are never logged. `CodeMatrix` errors name the format,
never the code. Flight lookups log the flight's state and HTTP statuses,
never the flight number or date, and parser errors only by type,
because their messages quote the response.

---

## Design Decisions

- **Local first.** The SQLite database is the main copy and all cards are
  held in memory, so nothing on the path from icon to code waits for disk
  scans or the network.
- **Framework SQLite, not Room.** One table of a few hundred rows read once
  at start; Room's annotation processor would add build complexity with no
  query to gain from. Usage counts are a separate table because they change
  on every view.
- **Google backup instead of Drive sync** (decided 2026-11-19). Drive sync
  needed a Google Cloud project with an OAuth client before it could work,
  and the user wants no setup. `data_extraction_rules.xml` puts `wallet.db`
  and all shared preferences into Android's cloud backup and device
  transfer, which restore on a new phone or a reinstall signed with the
  same key; a first `bmgr backupnow` run succeeded on the phone. The sync
  code, the first-run "Restore from Google Drive" screen and Play services
  were removed; one phone needs no live sync. Export files are always
  password-encrypted because they can end up anywhere.
- **Pixel-exact codes.** `CodeMatrix` encodes with no margin;
  `CodeImage` scales by whole pixels with no filtering, on a white panel in
  both themes. QR codes get a UTF-8 charset hint only for non-Latin-1 text,
  because the ECI marker it adds confuses some shop scanners.
- **Small square codes, full width on a tap** (decided 2026-11-19 after
  a Bringmeboxes.com scanner struggled with a full-width QR code). QR,
  Aztec and Data Matrix codes fit a 168 dp square, the size Google Wallet
  draws them (a 29-module QR code at 14 px per module on the A56, 2.7 cm),
  because a scanner held close to the phone must see the whole code; the
  full-width code was 5.7 cm. A tap on the code or "Larger code" fills the
  width on every page until the code screen closes. Barcodes and PDF417
  keep the full width, which is already close to Google Wallet's.
- **Normal brightness, full brightness on a tap** (decided 2026-11-20).
  Every card used to open at full brightness, which glared while the user
  looked through tickets days ahead. The code screen now opens at the
  phone's brightness; the "Full brightness" pill (a switch: outlined off,
  filled on) turns it on for every page until the screen closes, like
  "Larger code". A rule that brightened only cards due now (tills always,
  tickets on the day, passes from the Up next window) was offered and
  declined. The screen still stays awake on every code. The override is
  a window attribute, so it ends when the app leaves the screen.
- **Raw scan text.** `CodeReader` uses zxing-cpp's `PLAIN` text mode so GS1
  and similar codes can be drawn again exactly as read.
- **Text colour by WCAG contrast.** White when it reaches 3:1 (large or bold
  text), otherwise near-black; Albert Heijn's light blue gets dark text.
- **One UI 9 style, compact** (decided 2026-11-19 from
  `docs/style-storybook.html`). Teal accent, grouped rounded containers,
  glossy tiles with a soft shadow, a dark "Now Bar" capsule for Up next,
  One UI switches, chips and toasts, and Material Symbols Rounded weight
  300 line icons (Apache 2.0, converted to vector drawables). One UI's tall
  title area is replaced by a 60 dp title bar so content uses the screen.
- **One-handed by default, no mode to switch on** (decided 2026-11-19 from
  `docs/one-hand.html`, options A + D + C's swipe-down). Short grids sit at
  the bottom; in "Most used" order the grid is right-to-left and bottom-up
  (`reverseLayout`, RTL with LTR content), so the most-used card sits
  bottom-right. Up next is fixed just above the favourites rail, in reach
  and visible however far the grid scrolls. Nothing interactive sits at the
  top except the code screen's "Full brightness" switch, which the user
  chose to keep there (2026-11-20): the ⋯
  button opens a sheet with Archive, Settings and imports; long-press menus,
  confirmations, the code chooser, Settings and Archive are bottom sheets;
  the code screen closes with a swipe down (nested-scroll overscroll) or
  Done; the form has Cancel/Skip and Save at the bottom; the scanner's close
  and torch are at the bottom.
- **A–Z by default** (decided 2026-11-19). Cards keep their place, so the
  grid can be learned; frequent cards are already one tap away in the rail,
  Up next, shortcuts and the widget. A–Z reads like a page from the
  top-left (the user chose it over A–Z from the thumb); favourites keep
  their alphabetical place. Settings → "Card order" switches to most used.
- **Dated cards get their own section** (decided 2026-11-19 from
  `docs/dates.html`, layout B). "Coming up" sits above the grid with
  full-size tiles, soonest first, and those cards are not repeated below;
  it hides during a search, where every match shows. Every tile with a date
  worth knowing carries a chip from `CardDates.next`: events from today on,
  start dates ahead, end dates within 30 days (amber within a week). The Now
  Bar keeps today and tomorrow at the bottom.
- **Cards can start later** (decided 2026-11-19): `Card.validFrom` for
  vouchers, gift cards, memberships and tickets, from the form or from
  ticket text ("Valid from", "Geldig vanaf"); `.pkpass` has no such field.
  The code still shows before then, with "Valid from …" under it; Up next
  says "Valid from today" on the day.
- **Several tickets in one file become one form each** (decided
  2026-11-19 from a real Ticketmaster PDF: two pages, one ticket each, every
  code printed four times). Each form is filled from its own page, so seats
  stay right; a single shared form was rejected because edits would apply
  to every ticket. The saved tickets group into one tile.
- **The other code format is a switch, not a second code** (decided
  2026-11-19). A ticket's QR code and barcode hold the same text, so only
  the format is stored; QR is the default because camera scanners read it
  more reliably from a screen, and "Show as barcode" covers scanners that
  read only barcodes.
- **Maps through Google's documented search link.** The Map button in the
  code screen's bottom bar (in place of Copy) opens
  `https://www.google.com/maps/search/?api=1&query=…`, which the Maps app
  handles and a browser falls back to; no Maps SDK or key. The venue shows
  under the card's name as plain text, so nothing to tap sits at the top.
- **Ticket details from PDF text, not image recognition** (decided
  2026-11-19). Android 15's `PdfRenderer.Page.getTextContents` reads
  e-ticket PDFs with no new library; text recognition for screenshots would
  need ML Kit (closed source, several MB). Screenshots still need typing.
- **Groups instead of swiping through everything** (decided 2026-11-19).
  Boarding passes with the same airline, flight number, departure airport
  and date form one tile ("2 passengers"), whatever their booking
  reference; tickets with the same name and event date and time form one
  tile too. The code screen swipes only within a group, so a loyalty card
  opens alone and nobody slides to the wrong card at a till. Other flights
  of a booking are separate tiles. Up next, the notification, widgets and
  tile show one card per group; deleting a grouped tile from the grid
  deletes the whole group, while Edit → Delete removes one card.
- **App icon: Big-type W** (decided 2026-11-19 from `docs/icons.html`,
  idea 1, replacing the ticket icon). The launcher icon is the app's own
  card tile: teal gradient, a name line at the top left and a giant W
  cropped by the launcher's mask at the bottom right. The monochrome layer
  has the same shapes for themed icons.
- **Big-type tiles** (decided 2026-11-19): brand colour, the name at the
  top, a giant monogram or plane bleeding off the bottom-right. Usage
  ("Used 3×, last 2 days ago") shows on the code screen, measured before the
  current visit.
- **Favourites rail plus favourites outside the app** (decided 2026-11-19
  from `docs/favourites.html`, ideas 2 + 4). Favourites reuse the `pinned`
  field, so the file format and database are unchanged. The rail sits above
  the bottom bar, fills right to left like the grid, and hides during a
  search. Favourites never move by use (the user disliked that): they keep
  the order the user dragged them into (long-press and drag in the rail;
  long-press and let go opens the menu; TalkBack has "Move left/right"),
  stored as card ids in `AppSettings.favouriteOrder`, and favourites not
  arranged yet follow A–Z; favourites also stay in the grid (the user prefers duplicates to
  missing cards). Shortcuts, the favourites widget and the Quick Settings
  tile (when nothing is up next) use `CardList.quickAccess`: favourites
  in the rail's order, then the most used cards.
- **Google Wallet import is by screenshot.** Google Wallet and Samsung
  Wallet offer no export to other apps; a screenshot of each card's barcode
  is read like any image, several at once.
- **Archive is derived, not stored.** Only "used at" is stored; expiry and
  past events are computed from dates each time, so changing a date moves a
  card in or out of the archive. Retention deletes create tombstones. A
  boarding pass with a live lookup stays until two hours after the flight
  lands (or leaves, without a landing time), so a flight delayed past
  midnight keeps its alerts and its after-landing lookup.
- **Widgets use RemoteViews, not Glance.** Two small fixed layouts do not
  justify the Glance dependency; tiles are tinted with
  `setBackgroundTintList`.
- **Card format and database grow additively.** New fields have defaults
  and new columns are added with `ALTER TABLE`, so older export files read
  fine and an upgrade keeps every stored card (v3 added `altFormat`, v4
  `validFrom`).
- **Brand list is approximate.** `Brands.ALL` holds names and approximate
  colours only, never number prefixes, so nothing guesses a shop from a
  number.
- **Live flight info from FlightStats, free and without a key** (decided
  2026-11-19; it replaced AeroDataBox, which needed a RapidAPI key and a
  monthly allowance and which the user rejected). Of the no-key sources
  tested on real flights, flightstats.com's tracker JSON was the only one
  with gates, terminals, belts and times in one small response (FlightAware
  needs a 500 KB page, Flightradar24 lacked gates, adsb.lol has positions
  only, Schiphol's site answered 403). It is unofficial and may break; a
  failure just means no live information. Settings has an on/off switch
  (on); the old key and allowance entries are erased from the phone.
- **A shared card is a picture, not a file format** (decided 2026-11-19).
  Anyone can show the picture at a till without the app, Wallet imports it
  with the existing image route, and no import format has to stay stable.
  Name and colour travel as the share text and the brand list; dates,
  notes and PIN do not.
- **Lookups follow the flight's day** (`FlightLookups`): once 24 hours
  before (or 08:00 the day before when the pass has no time), every 30
  minutes from 4 hours before departure, every 15 minutes in the last hour,
  once 10 minutes after the expected landing; a flight the service does not
  know is tried once more at 06:00 on the day, or 4 hours before the
  departure on the pass if that is earlier. About 12–15 lookups per
  flight, shared by every passenger on it. The schedule runs on instants
  (`LiveFlight.departureAt`/`arrivalAt`), because departure and landing
  are local times at different airports.

---

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
- A picker registered with `rememberLauncherForActivityResult` inside a
  sheet that closes as the picker opens never gets its result: the sheet's
  callback is gone when the picker returns. `ImportPickers` are remembered
  by the screen under the sheet.
- `PdfRenderer` needs a seekable descriptor; a PDF shared as a stream is
  not one. `CodeReader.readPdf` writes the bytes already read to the cache
  and renders one page at a time.
- A deleted card's pinned shortcut is disabled, and `requestPinShortcut`
  throws for a disabled ID; `Shortcuts` enables restored cards' shortcuts
  before updating them and before a pin request.
- `WalletViewModel` keeps the screen stack, the form and the import queue
  in its `SavedStateHandle` (`DraftJson`), restored after the cards load so
  a restored code screen finds its card; the code chooser is not kept.
- `Intent.setClipData` returns nothing, so it cannot be chained; the share
  intent sets the clip separately so the read grant reaches the app picked
  in the chooser.
- A PDF's stored text order follows its columns, not the page: on the
  Ticketmaster ticket the buyer's name shares a line with the venue's
  postcode. `TicketText` takes only the postcode and one-word city from such
  a line, never the rest of it.
- `.pkpass` dates with a venue offset ("+01:00") keep their clock time; a
  UTC date ("Z") says nothing about the venue and is read in the phone's
  zone, or a show at 00:30 would be stored a day early.
- A number typed in digit groups ("6012 3456 7890 1234") is stored without
  the separators whatever its format (`CardDraft.codeTyped`); scanned text
  is kept exactly as read.
- ZXing's Code 39 writer switches to extended mode for lowercase, so
  `canEncode` accepts lowercase text; only non-ASCII text fails.
- FlightStats answers an unknown flight, and one more than about a day
  ahead, with HTTP 200 and `{"data":{}}`; its times are local at each
  airport ("2026-11-19T07:20:00.000"), with the same moments in UTC in the
  `…UTC` fields ("2026-11-19T06:20:00.000Z"), and its status codes are S,
  A, L, C, D, R, NO, U and DN. It has no boarding or gate-closed status.
- `FlightUpdater.schedule` does nothing while a lookup holds its lock:
  REPLACE would cancel a running `FlightWorker` mid-request, and the
  lookup schedules the next run itself when it is done.
- `Notification.Builder.setRequestPromotedOngoing` is API 36.1: guard with
  `SDK_INT_FULL >= VERSION_CODES_FULL.BAKLAVA_1`, not `SDK_INT >= 36`.
- `ProcessCameraProvider.awaitInstance` is an extension function; it needs
  `import androidx.camera.lifecycle.awaitInstance`.
- zxing-cpp builds its Kotlin result classes from native code;
  `proguard-rules.pro` keeps `zxingcpp.**` for minified builds.
- Shortcut updates are rate-limited for background apps; `Shortcuts.update`
  runs from the ViewModel and logs and skips when the limit is hit.
- A Material snackbar with an action defaults to `Indefinite`; Undo
  snackbars pass `SnackbarDuration.Long`.
- A `ModalBottomSheet` is its own window and hides the screen's snackbar;
  `WalletSheet` shows `LocalSnackbar` inside the sheet.
- The phone used for testing holds the user's real cards: install with
  `adb install -r` only, never uninstall or clear data.
