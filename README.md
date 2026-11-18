# Wallet

An Android app for loyalty cards, vouchers and tickets. Cards are stored on
the phone and open in one tap, with the code at full screen brightness.
Sync through a hidden folder in your Google Drive keeps a second phone or a
tablet up to date. The design and the plan for later versions are in
`docs/design-options.html`.

## Using it

The grid shows every card in its brand colour, sorted by how often you use
it. Tap a card to show its code; swipe sideways for the next card. Search
and the add button sit at the bottom of the screen.

The add button opens the camera, which reads the code as soon as it is in
view. You can also pick a screenshot or photo, share an image to the app
from another app, or type the number. The app suggests a colour for common
Dutch and European shops and checks the check digit of EAN and UPC numbers.

Long-press a card to keep it at the top, pin it to the home screen, edit
it, require a fingerprint or screen lock before its code shows, or delete
it. Long-pressing the app icon lists the three most used cards.

Settings turns on Google Drive sync and exports or imports all cards as one
file encrypted with a password.

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
cards, and uses the network only for Google Drive sync.

```bash
make check     # build, unit tests and Android lint
make install   # build bin/wallet.apk and install it over adb
make logs      # sync and error log lines
```

## Google Drive sync setup

Drive access needs a Google Cloud project that identifies the app. This is
a one-off step:

1. Create a project in the Google Cloud console and enable the Google Drive API.
2. Configure the OAuth consent screen as External, with only the
   `https://www.googleapis.com/auth/drive.appdata` scope.
3. Create an Android OAuth client with the package name
   `io.github.jsnjack.wallet` and the SHA-1 fingerprint of the signing key.
   For the debug key: `keytool -list -v -keystore ~/.android/debug.keystore -storepass android`.
4. Set the consent screen's publishing status to "In production". In
   "Testing" status Google drops the app's access seven days after you
   approve it, and sync stops.

The `drive.appdata` scope is non-sensitive, so publishing should not need
Google's app review.

## Releases

Release publishing through the shared F-Droid repository is not set up yet.
The app first needs a GitHub repository, a dedicated signing key and an
entry in the publisher's configuration in `jsnjack/android-apps`. After
that, commit and push `master`, then run `make release`; it runs the checks,
uploads the APK to the app's GitHub release and starts F-Droid publication
for that exact commit. If the GitHub release succeeds but the F-Droid trigger
fails, retry with `make fdroid-publish` and follow the deployment in
https://github.com/jsnjack/android-apps/actions/workflows/publish.yml.
