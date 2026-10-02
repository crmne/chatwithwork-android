# Chat with Work for Android

The Android app for [Chat with Work](https://chatwithwork.com), the private
AI assistant for your work: one place to ask about your documents, mail and
chats wherever they live, hosted in the EU or on your own servers.

The app is built with [Hotwire Native](https://native.hotwired.dev). Its
screens are the web app's own pages, so every feature the web app gains
reaches the app without a release, and it goes native wherever that makes it
feel like an Android app: a bottom bar with a navigator per tab, a floating
New chat button, Material top app bars with the page's actions and overflow
menu, full-screen modals, a welcome screen, native dialogs, snackbars,
haptics, search, the share sheet, sign-in through Chrome's Auth Tab, push
notifications, App Links, predictive back, edge to edge, and Live Wire's
colors in light and dark.

What the server has to do for all of that is written down in
[docs/server-contract.md](docs/server-contract.md), next to the iOS app's
contract it shares.

## Requirements

- Android 9 (API 28) or later; built for Android 17 (API 37)
- JDK 21 or later to build (CI uses 21; Android Studio's bundled JDK works), Android SDK 37
- Pinned dependencies, in `gradle/libs.versions.toml`:
  [Hotwire Native Android](https://github.com/hotwired/hotwire-native-android)
  1.3.1, Joe Masilotti's [bridge-components](https://github.com/joemasilotti/bridge-components)
  v0.14.0 (from JitPack), Material Components 1.14, AndroidX, Firebase
  Cloud Messaging (BoM 34.19)

## Building

```sh
./gradlew assembleDebug                 # debug build, for the emulator and a local server
./gradlew installDebug -PbaseUrl=http://localhost:3000
./gradlew testDebugUnitTest lintDebug spotlessCheck
./gradlew assembleStaging assembleRelease
```

Or open the folder in Android Studio and run the `app` configuration.
`./gradlew spotlessApply` formats Kotlin (ktlint) and the bundled JSON.

## Configuration

Each build type points at one server and installs as its own app, so all
three can sit on one phone:

| Build type | Server | Application id | Name on the home screen |
|---|---|---|---|
| debug | `http://10.0.2.2:3000`, or `-PbaseUrl=...` | `com.chatwithwork.app.debug` | CWW Dev |
| staging | `https://staging.chatwithwork.com` | `com.chatwithwork.app.staging` | CWW Staging |
| release | `https://chatwithwork.com` | `com.chatwithwork.app` | Chat with Work |

`10.0.2.2` is your computer as the emulator sees it, so the default debug
build talks to `bin/dev`; Android 17 asks for the local network permission
before it can. For a phone, or to skip that, forward the port and point at
`localhost`:

```sh
adb reverse tcp:3000 tcp:3000
./gradlew installDebug -PbaseUrl=http://localhost:3000
```

Debug builds allow plain http to `10.0.2.2`, `localhost` and `127.0.0.1`
only, enable web view debugging (`chrome://inspect`), and show the server's
host on the welcome screen, as staging builds do.

Push notifications need `app/google-services.json` from the Firebase
project (git-ignored; see [docs/push-notifications.md](docs/push-notifications.md)).
Without it the app builds and runs, with push off.

## Signing

This repository is public: keystores, `google-services.json` and keys never
go in it. Release builds read their signing key from Gradle properties or
environment variables:

| Variable | What |
|---|---|
| `RELEASE_KEYSTORE_PATH` | the upload keystore file |
| `RELEASE_KEYSTORE_PASSWORD` | its password |
| `RELEASE_KEY_ALIAS` | the key's alias |
| `RELEASE_KEY_PASSWORD` | the key's password |

Without them, `assembleRelease` makes an unsigned APK and staging builds
are signed with the debug key. Google Play signs what it distributes with
the app signing key it holds (Play App Signing); the key here is the upload
key, and the app signing key's fingerprint is what
`/.well-known/assetlinks.json` names.

## How it's put together

```
app/src/main/kotlin/com/chatwithwork/app/
  ChatWithWorkApplication.kt  Hotwire configuration, bridge components, path configuration
  main/        MainActivity (tabs, welcome screen, sign-in, links), MainTab, view model
  fragments/   the web screen (top app bar over the shared web view), page titles, error states
  bridge/      the bridge components (native halves) and the action sheet they share
  routing/     the app's URLs, Custom Tabs, route decisions for other sites and apps
  push/        Firebase Cloud Messaging, the notification channel, the payload
app/src/main/assets/json/path-configuration.json   the bundled path configuration
app/src/main/res/        Live Wire's colors, Geist, Material Symbols icons, layouts
app/src/debug/           plain-http config and the push simulator for development
app/src/test/            unit tests (JUnit, Truth)
docs/                    the server contract and design notes
```

- **MainActivity** owns the bottom bar (Chats, Projects, Settings, each with
  its own navigator, loaded lazily), the floating button, and the welcome
  screen. It watches the navigators' traffic for what the server says: a
  401 or the sign-in page means no session, a recede while signed out means
  signed in, a link into another organization rebuilds the tabs there.
- **Web screens** are `HotwireWebFragment`s with the app's own layout: a
  Material top app bar (the page's `<title>`, its bridge buttons and
  overflow menu) over the web view. Modals are full-screen fragments, not
  Hotwire's bottom sheets, where bridge components that need a top app bar
  crash.
- **Bridge components**: the app's own `button`, `menu`, `form`, `share`,
  `alert`, `toast`, `haptic`, `theme`, `context-menu`, `auth-session` and
  `notification-token`, with Joe Masilotti's messages where his component
  exists, plus his `search` and `review-prompt` as they are. All 13 are the
  same names and messages as the iOS app's.
- **Path configuration**: bundled, and replaced at launch by the server's
  `/configurations/android_v1.json`.

## The playground

The iOS repository's `Playground/server.py` stands in for the Rails app as
it will be once it implements the server contract: fixture pages in the web
app's own markup, styled by its real stylesheet, with the bridge
controllers and the contract's authentication. With
[crmne/chatwithwork-ios](https://github.com/crmne/chatwithwork-ios) checked
out next to this repository:

```sh
python3 ../chatwithwork-ios/Playground/server.py --port 8765
adb reverse tcp:8765 tcp:8765
./gradlew installDebug -PbaseUrl=http://localhost:8765
```

Sign in with the prefilled form; everything on its pages is made up.

## Releasing

Versions come from tags: for `v1.2.3`, build with
`-PversionName=1.2.3 -PversionCode=10203` (MAJOR·10000 + MINOR·100 + PATCH),
signing variables set, and upload `app/build/outputs/bundle/release/app-release.aab`
to Google Play:

```sh
./gradlew bundleRelease -PversionName=1.2.3 -PversionCode=10203
```

## Third-party assets

- [Geist](https://vercel.com/font) by Vercel, under the SIL Open Font
  License 1.1 (`app/src/main/assets/licenses/geist-OFL.txt`), subset to
  Latin.
- [Material Symbols](https://fonts.google.com/icons) by Google, under the
  Apache License 2.0: the icon drawables and, through bridge-components,
  the symbol font the bridge buttons draw from.

## License

Licensed under either of

- Apache License, Version 2.0 ([LICENSE-APACHE](LICENSE-APACHE))
- MIT license ([LICENSE-MIT](LICENSE-MIT))

at your option. Unless you explicitly state otherwise, any contribution you
intentionally submit for inclusion in this project, as defined in the
Apache-2.0 license, is dual licensed as above, without any additional terms
or conditions.

"Chat with Work" and its logo are trademarks of Plenty UG. The license
covers the code, not the name or the logo in `app/src/main/res`.

See [SECURITY.md](SECURITY.md) for how to report a vulnerability.
