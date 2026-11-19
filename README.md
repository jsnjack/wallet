# Wallet

An Android app for loyalty cards, vouchers, tickets and boarding passes.
Cards are stored on the phone and open in one tap, with the code at full
screen brightness. Your phone's Google backup keeps a copy, with nothing to
set up. The design and the plan for later versions are in
`docs/design-options.html`.

## Using it

The grid shows every card in its brand colour, A to Z from the top-left;
Settings → "Card order" can put the most used cards first instead, starting
bottom-right by your thumb. Cards with a date sit above the rest under
"Coming up", soonest first: tickets and flights, and cards that start or end
within 30 days. Each carries a chip saying when, such as "Today 20:30",
"Fri 07:05", "Ends Sun" or "From 1 Dec". Tap a card to show its code; swipe
down to close it. Search and the add button sit at the bottom of the screen.

The add button opens the camera, which reads the code as soon as it is in
view. You can also pick a screenshot or photo, share an image to the app
from another app, or type the number. The app suggests a colour for common
Dutch and European shops and checks the check digit of EAN and UPC numbers.
A typed number gets a guessed code type you can change; a scanned code keeps
the type it was read with unless you tap "Change code type".

Long-press a card to keep it at the top, pin it to the home screen, edit
it, share it, require a fingerprint or screen lock before its code shows, or
delete it. Long-pressing the app icon lists the three most used cards.

Share (in that menu or at the bottom of a code) sends a picture of the code
with the card's name. Anyone can show the picture at a till; Wallet on
another phone imports it like a screenshot, with the name filled in.

Vouchers can have a start date ("Valid from"), an expiry date and a PIN,
gift cards and memberships the same dates, gift cards a balance, and tickets
a date, a start date and a seat. A card that becomes valid today shows in Up
next. "Mark as used" moves a voucher to the archive;
expired cards and past tickets and flights move there by themselves and are
deleted after the period chosen in Settings (one year by default).

Boarding passes fill themselves in from the barcode (IATA BCBP): flight,
route with city names, seat and booking reference. Passes for the same
flight, such as yours and a travel companion's, share one tile ("2
passengers"); on the code, swipe sideways from one passenger to the next.
Tickets with the same name and time share a tile the same way. Apple Wallet
`.pkpass` files and e-ticket PDFs can be shared to the app or picked with
"PDF or pass" in the scanner. On Android 15 and newer the text of a PDF
ticket fills in the form: the event name, date and time, and doors, hall,
row, seat, venue and address; the buyer's name, numbers and price are left
out. A PDF with several tickets, one per page, offers "Add both" (or "Add
all"): one form per ticket, each filled from its own page, and the tickets
then share one tile. When a ticket prints its code as a QR code and as a
barcode, Wallet shows the QR code with a "Show as barcode" switch under it,
for a scanner that only reads barcodes. A card with a venue, an address or
an Apple Wallet location shows it under its name; a tap opens Google Maps.

A boarding pass also shows its gate, terminal, departure time and status
from flightstats.com, updated through the day of the flight, and after
landing the baggage belt (see "Live flight info" below). The "Up
next" row and the notification show them too, and a gate change, a new
departure time or a cancellation sends an alert. On Android 16 and newer the
notification also shows the flight as a progress bar, from four hours before
departure through boarding to landing, in One UI's Now Bar.

An "Up next" row on top of the grid shows a boarding pass from the day
before the flight, a ticket on the day of the event, or a voucher that
expires today or tomorrow. The same card appears as an ongoing notification
on the day (a Live Update on Android 16 QPR2 and newer, shown in One UI's
Now Bar), in the "Up next" home screen widget and in the Quick Settings
tile. A reminder arrives at 09:00 the day before a card expires. The
"Favourite cards" widget shows the four most used cards.

Google Wallet has no export, so cards move over as screenshots: open each
card in Google Wallet, take a screenshot of its barcode, then use Settings →
"Import Google Wallet screenshots" (or share the screenshots to Wallet). The
form opens for each card in turn so you can name it.

Settings exports or imports all cards as one file encrypted with a password.

## Backup

Your phone's Google backup includes Wallet with no setup: the cards and
settings go to your Google account about once a day (when the phone is idle,
charging and on Wi-Fi) and come back by themselves when you set up a new
phone or reinstall Wallet. `adb shell bmgr backupnow io.github.jsnjack.wallet`
runs it at once. A backup only restores into a Wallet signed with the same
key, so keep a copy of the signing key (`~/.android/debug.keystore` for local
builds). An export gives an extra copy you can keep anywhere, and moves cards
to a build signed with another key: export, uninstall, install, import.

## Building

The build needs JDK 21. The Makefile uses `JAVA_HOME` when it is set, and
otherwise looks in `~/.jdks`. To unpack Temurin 21 there:

```bash
mkdir -p ~/.jdks && curl -fL \
    https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse \
    | tar xz -C ~/.jdks
```

The Android SDK path goes in `local.properties` (`sdk.dir=…`) or
`ANDROID_HOME`. With only the command-line tools installed, add the pinned
components with:

```bash
sdkmanager 'platform-tools' 'platforms;android-37.0' 'build-tools;37.0.0'
```

The app supports Android 13 and newer (`minSdk` 33) and is built for a
Galaxy phone on One UI 9 (Android 17). It asks for the camera, to scan
cards, and uses the network only for live flight info (Settings can turn it off).

```bash
make check     # build, unit tests and Android lint
make install   # build bin/wallet.apk and install it over adb
make logs      # the app's log lines (counts and errors, no card data)
make airports  # refresh the bundled airport table from OurAirports
```

The app asks for the notification permission the first time you save a card
with a date, for the "today" notification and reminders.

## Live flight info

Live flight info needs no account or key. It reads the data that
flightstats.com's own flight tracker pages load. That is not an official
service: it can change or stop without notice, and the pass then simply
shows no live information. It covers about yesterday to tomorrow, so a
flight is looked up the day before and then more often on the day: every 30
minutes from four hours before departure, every 15 minutes in the last hour,
and once after landing, about a dozen lookups per flight. One lookup serves
every passenger on the flight, and opening the pass on the day asks again
when the information is more than ten minutes old. Only the airline, flight
number and date are sent. FlightStats has no boarding status; Wallet shows
scheduled, in the air, landed, cancelled or diverted, and the delay from
the times. Settings → Live flight info turns it off.

## Releases

Wallet is published in the personal F-Droid repository at
https://jsnjack.github.io/android-apps/ (configured in
`jsnjack/android-apps`). Commit and push `master`, then run `make release`;
it runs the checks, uploads the APK to the app's GitHub release and starts
F-Droid publication for that exact commit. The publisher builds an optimised
release APK and signs it with the same personal key as local builds, so a
repository update installs over a local build and keeps the cards.
Repository builds get a version code of at least 15001; a local build
installed over one must not be lower. If the GitHub release succeeds but the F-Droid trigger
fails, retry with `make fdroid-publish` and follow the deployment in
https://github.com/jsnjack/android-apps/actions/workflows/publish.yml.
