# Publishing on Google Play

Everything Play Console asks for, and where it lives. The listing is in
`fastlane/metadata/android/en-US/` in the layout
[fastlane supply](https://docs.fastlane.tools/actions/supply/) reads, so it
can be pasted by hand or uploaded with `fastlane supply` (not set up here:
it needs a Play service account key, which never goes in this repository).

## Release 1.1.5

Version 1.1.5 (10105) keeps the selected approval mode in the existing
composer control, without an extra warning row or color treatment. Its
light and dark playground checks select Allow everything, verify the
composer height stays unchanged, and restore Ask for changes. The native
app continues to draw the server’s saved mode and restrictions.

The Internal release is being prepared; it has not been uploaded yet.

## Release 1.1.4

Version 1.1.4 (10104) matches the web composer’s light and dark colors,
shows model-maker logos, and adds the web’s Changes menu. Its signed,
Firebase-enabled bundle became available to Internal testers on
2026-10-30 at 10:21 Europe/Berlin. Play Console confirmed that status;
updates can take an hour to reach Google Play. Open testing remains on
1.1.3 (10103), awaiting Google’s review, and Production remains unpublished.

The Internal tester link is
https://play.google.com/apps/internaltest/4700795205724875813. It requires
an account on the selected tester email list; it is separate from the
public beta’s opt-in link below.

Validation: 103 unit tests, lint, formatting, and Debug, Staging and
Release builds passed. The Android emulator playground was reviewed in
light and dark mode, including model logos, the Changes menu and enlarged
text. The release bundle was signed with the existing upload key and its
signature verified before upload. Play reported no changes to supported
devices and only a non-blocking warning about native dependency debug
symbols; the ReTrace mapping file is included.

## Before the first upload

1. **The server.** Release builds talk to `https://chatwithwork.com`, which
   must serve the server contract (`docs/server-contract.md`): the native
   layout, `/configurations/android_v1.json`, and
   `/.well-known/assetlinks.json`. In the Rails app's credentials, set
   `android.cert_fingerprints` to the **app signing key's** SHA-256 from
   Play Console > Test and release > App integrity (Play holds that key;
   add the upload key's too if you want builds you sideload to verify), and
   `firebase.project_id` and `firebase.service_account` for push. Then
   deploy.
2. **Push.** Put the Firebase project's `google-services.json` in `app/`
   (git-ignored; see `docs/push-notifications.md`) before building the
   release, or the app builds with push off.
3. **The upload key.** `script/upload-key` makes it once and keeps it in
   1Password (item "Android upload key", vault chatwithwork, the keystore base64-encoded in `keystore_base64`). Play App
   Signing is on by default for new apps: Play signs what it distributes,
   and this key only proves an upload is yours.
4. **The app.** Play Console > Create app: name "Chat with Work", default
   language English (United States), App, Free.
5. **The demo login** for review, on production: the 1Password item "App
   Review demo login" holds it, and the Rails app's `script/store/seed
   production review app-review@chatwithwork.com <password file>` makes it
   with synthetic content, past the first-run tour. It was first made on
   2026-10-27; run it again just before sending a release for review, so its
   morning brief reads as that day's, its change waits again, and an
   earlier reviewer's yes to AI models is taken back.

## Building and uploading

```sh
git tag v1.0.0
script/bundle-release v1.0.0     # version 1.0.0, versionCode 10000, signed with the upload key
```

Upload `app/build/outputs/bundle/release/app-release.aab` to Test and
release > Testing > Internal testing first: add yourself as a tester, open
the opt-in link on your phone, and install from Play. A new personal
developer account must also run a closed test with at least 12 testers for
14 days before it can publish to production; an organization account
doesn't have to.

## Open testing (the public beta)

Once Google approves the first Open release, anybody can join the beta from
https://play.google.com/apps/testing/com.chatwithwork.app (on Android, or on
the web with their Google account), which chatwithwork.com links as
"Android beta". The app is in Plenty Labs' organization account, so it
needs no closed test first. As of 2026-10-29 the first Open review is still
pending: there is no public build to install yet. Internal testing access
does not make the public opt-in link available.

- **The track.** Test and release > Testing > Open testing: unlimited
  testers, every country and region but Russia, Belarus, Iran and Cuba
  (managed apart from production), and feedback to support@chatwithwork.com.
- **Countries left out.** Russia and Belarus are under EU sanctions on
  software and IT services (Regulations 833/2014 and 765/2006), Iran and
  Cuba under sanctions the services the app runs on follow (AWS, Google,
  Stripe). North Korea, Syria and Crimea aren't in Play's list. "Rest of
  World" is off too, so no country Play adds later gets the app before
  someone checks it. Production has no countries yet: leave out the same
  ones when it gets them. These exclusions were submitted on 2026-10-29
  with version 1.1.1: the review now covers 173 countries and regions,
  without Rest of World.
- **A release.** Internal testing > Promote release > Open testing, or
  create one there. Every change goes through Play's review (Publishing
  overview > Send changes for review; usually hours, at most 7 days), and
  with managed publishing off it goes live once approved.
- **The first review** (sent 2026-10-26, release 10004 / 1.0.4) also
  carried the app's setup: the store listing, App content, and the store
  settings below. It was restarted on 2026-10-29 with release 10101 / 1.1.1
  and the country exclusions, then updated to 10103 / 1.1.3 with the
  current store description later that day. Until Play approves the app, internal testers see
  the temporary name "com.chatwithwork.app (unreviewed)".
- **Version 1.1.0** (10100) went to internal testing on 2026-10-27. It's
  superseded by 1.1.1 below. When the production launch is approved,
  promote the latest tested release (Internal testing > Promote release >
  Production) and update
  App content's App access instructions to the text below, which describe
  the first question's yes and reporting an answer.

## Release 1.1.3

Version 1.1.3 (10103) redesigns Live Updates with native status cards,
elapsed time only while working, and actions that open the chat to read or
review. It validates delayed failures against the server and preserves the
last progress timestamp when following the same chat again. The composer
hides while a web dialog is open and restores the draft when it closes.
Silent recordings and brief microphone taps stay on the phone.
`fastlane/metadata/android/en-US/changelogs/10103.txt` is its Play text.
Version 1.1.2 was prepared and tagged before the silence gate was added;
its bundle was not uploaded to Play.

The signed Firebase-enabled bundle from `v1.1.3` became available to
Internal testers on 2026-10-29 at 16:07 Europe/Berlin. The same bundle was
promoted to Open testing and submitted for review that afternoon, after
refreshing the synthetic App Review account. The submission also updates
the full store description, including connecting tools inside the app and
EU dictation. Publishing overview confirms 10103 and the existing 173
countries and regions under Changes in review, with no unsent changes.
Google's quick checks subsequently passed and Publishing overview confirmed
the changes are in review. Approval is still pending, and Production
remains unpublished. Managed publishing remains off for this public beta.

Validation: 99 unit tests, lint, formatting, and Debug, Staging and Release
builds passed. Android 17 on the Mac mini's Metal emulator confirmed the
system's `PROMOTED_ONGOING` flag; Android 16 also displayed the native
fallback without an indeterminate progress bar. Promotion remains the
system's decision and depends on the device and notification settings.
The emulator also verified modal composer hiding and draft restoration,
late failed progress preserving a newer Live Update, and a silent
recording returning to typing without an upload.

## Release 1.1.1

Version 1.1.1 (10101) includes the floating native composer and sidebar
alignment with the web app. `fastlane/metadata/android/en-US/changelogs/10101.txt`
is its Play release text. The signed bundle was built from tag `v1.1.1`
with `script/bundle-release v1.1.1`, with Firebase configured, and became
available to Internal testers on 2026-10-29. The same bundle was promoted
to Open testing and submitted for review that day, after refreshing the
synthetic App Review account. Publishing overview shows the release and
country restrictions in review, with no unsent changes; Google's quick
checks run before its review starts. Production remains unpublished.

Validation: 95 unit tests, lint, formatting, and Debug, Staging and Release
builds passed. The playground was checked on an Android 16 emulator using
the Mac mini's Metal GPU in light and dark mode: sidebar search, keyboard,
inline dictation and cancellation, and scrolling to the last chat item.
The floating layout is enabled only when the server advertises support;
older self-hosted servers keep their existing composer placement.

## The listing

| Field | Where |
|---|---|
| App name, short description, full description | `fastlane/metadata/android/en-US/title.txt`, `short_description.txt`, `full_description.txt` |
| What's new | `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` |
| App icon (512 x 512), feature graphic (1024 x 500) | `fastlane/metadata/android/en-US/images/icon.png`, `featureGraphic.png` |
| Phone, 7-inch, and 10-inch tablet screenshots | `fastlane/metadata/android/en-US/images/phoneScreenshots/`, `sevenInchScreenshots/`, `tenInchScreenshots/` |
| Category | Business |
| Contact | support@chatwithwork.com, website https://chatwithwork.com |
| Privacy policy | https://chatwithwork.com/privacy-policy |

Screenshots come from the debug build on headless emulators
(`cww-store-phone`, `cww-store-tablet7`, `cww-store-tablet10`), driven by
crmne/films' `chatwithwork/store-screenshots/capture/android` and pointed at
a server holding a synthetic organization (Northwind Studio, which crmne/films'
`chatwithwork/store-screenshots/capture/seed` makes in a checkout of the
Rails app, its clock set to a Monday at 9:41), with System UI's demo mode
for a 9:41 status bar. They're composed in crmne/films
(`chatwithwork/store-screenshots`): the phone's seven with one headline
each, in a band under a fifth of the image and without a device, so they're
eligible for promotion; the tablets' as the app shows them, with no text,
on a canvas that makes them 9:16 (1200 x 2133) and 16:9 (2844 x 1600), the
only shapes Play takes. The feature graphic comes from the same place. The
listing's words, and what backs each claim, are in the Rails app's
`docs/marketing/marketplaces.md`.

## App content

- **Privacy policy:** https://chatwithwork.com/privacy-policy
- **Ads:** no ads.
- **App access:** all or some functionality is restricted. Add the demo
  login: email `app-review@chatwithwork.com`, password from 1Password, and
  the instructions: "Sign in on the welcome screen. The account belongs to
  a made-up organization with synthetic chats. Your first question asks
  first: before anything goes to an AI model, the app names the companies
  that run the models and where, with Not now and Agree and continue. To
  report an answer, tap the '...' under it, then Bad answer, pick
  'Offensive or harmful', and Send. Open 'Morning Acme brief' for an answer
  with sources, and Decisions (from the sidebar) for a change waiting for
  approval (no Slack is connected, so approving posts nothing). Services
  are connected through the browser's sign-in sheet; the demo account has
  none."
- **AI-generated content:** Play's AI-Generated Content policy asks apps
  whose AI output is central for a way to report offensive output without
  leaving the app. An answer's menu (the "..." under it) has Good answer
  and Bad answer, and Bad answer's reasons include "Offensive or harmful",
  with a comment, sent to our staff (the Rails app's `docs/feedback.md`).
  Before the first question goes to a model, the app also asks once and
  names the companies that run the models and where (the Rails app's
  `docs/model-consent.md`). Both are drawn by the server's pages, so every
  build has them.
- **Content rating (IARC):** category "Productivity, communication, or
  other". No violence, sexuality, language, controlled substances, or
  gambling. Users interact or exchange content: yes (chats shared inside an
  organization). Shares location: no. Digital purchases: no. Unrestricted
  internet: no. AI-generated content: yes. Expect the lowest or a teen
  rating; the target audience below keeps it out of the Families program.
- **Target audience:** 18 and over (the privacy policy excludes under 16;
  it's a work tool). Not designed for children.
- **News app:** no. **Government app:** no. **Financial features:** none.
  **Health:** no.
- **Account deletion:** yes, in the app (Settings > Profile > Delete
  account), and the web link https://chatwithwork.com/account-deletion, which
  gives the steps, what's deleted at once and after 30 days, what stays, and
  how to ask by email without signing in.

## Data safety

The October 29, 2026 audit includes the native web view, server, connected
tools, support widget, and analytics, not only the Kotlin app. The corrected
20-type declaration was saved and submitted that evening with Open testing
1.1.3 (10103). This restarted the pending review. Quick checks subsequently
passed; Publishing overview confirms the changes are in review with no
unsent changes. Production remains unpublished.

Data is encrypted in transit (HTTPS only). People can delete their account
and its data in the app. Nothing is shared with third parties in Play's
sense: model providers and other processors act for us, and user-initiated
transfers to connected services fall under Play's exemptions. This does not
mean that no processor receives data. Follow [Google's definitions](https://support.google.com/googleplay/android-developer/answer/10787469)
and the privacy policy when updating the form.

| Category > type | Collected | Optional | Purposes |
|---|---|---|---|
| Personal info > Name | Yes | No | App functionality, Account management |
| Personal info > Email address | Yes | No | App functionality, Account management, Developer communications |
| Personal info > User IDs | Yes | No | App functionality, Account management, Analytics, Personalization, Fraud prevention, security, and compliance |
| Personal info > Address (billing details) | Yes | Yes | App functionality |
| Financial info > Purchase history (plan, seats and credits sent by the support widget) | Yes | Yes | App functionality |
| Financial info > Other financial info (connected accounting balances and transactions) | Yes | Yes | App functionality |
| Location > Approximate location (analytics country inferred from IP) | Yes | No | Analytics |
| Messages > Emails (connected accounts) | Yes | Yes | App functionality |
| Messages > Other in-app messages (assistant, connected chat, support) | Yes | No | App functionality |
| Photos and videos > Photos, Videos (attachments) | Yes | Yes | App functionality |
| Audio > Voice or sound recordings (dictation and audio attachments) | Yes | Yes | App functionality |
| Files and docs (attachments) | Yes | Yes | App functionality |
| Contacts (connected workspace directories and CRM) | Yes | Yes | App functionality |
| Calendar > Calendar events (connected calendars) | Yes | Yes | App functionality |
| App activity > App interactions (page views, feedback and feature counts) | Yes | No | App functionality, Analytics |
| App activity > In-app search history (retained search tool calls) | Yes | Yes | App functionality |
| App activity > Other user-generated content (instructions, memory and notes) | Yes | Yes | App functionality, Personalization |
| App info and performance > Diagnostics (server errors and latency traces) | Yes | No | App functionality, Analytics |
| Device or other IDs (push tokens and analytics visitor identifiers) | Yes | No | App functionality, Analytics |
| Precise location, payment-card details, credit score, health, sensitive personal info, SMS/MMS, web browsing history, installed apps, native crash logs | No | | |

Plausible is loaded by the production web view and records page paths,
country and daily visitor identifiers. The support widget sends the
identity's plan, seat and credit metadata. Editable billing details collect
an address even though native checkout is hidden. Connected Outlook
calendar, Slack directory and Odoo accounting tools also count: their
results are retained in chats or encrypted provider caches. Optional means
the person can avoid that feature or connection, not that the app has an
analytics opt-out. The push permission is optional, but the same device-ID
category also covers analytics, so the combined category is required.

All selected types are declared non-ephemeral. Dictation itself drops the
recording after transcription, but audio attachments can be kept in chats;
the category cannot claim every audio collection is memory-only. Release
emails use Google's Developer communications purpose. Apple groups that
use under Developer's Advertising or Marketing; the two taxonomies differ.

Two things that look new aren't types of their own: the words a
notification shows, which the app fetches from our server as the push
arrives (it sends only the phone's notification key), and the record of a
person's yes to AI models (when, which wording, from the Android app),
which is account data.

There are no advertisements, advertising identifiers, or cross-company
tracking. First-party analytics is declared above. Payment-card entry stays
on the external website; the native app has no checkout. This does not
exclude billing addresses, purchase history or connected accounting data.

## Permissions

`INTERNET`; `POST_NOTIFICATIONS`, asked for once after signing in (with a
short explanation first) or from Settings > Notifications;
`RECORD_AUDIO` (with `MODIFY_AUDIO_SETTINGS`, which the web view needs to
record), asked for the first time someone taps the composer's microphone,
and granted to the web view for the app's own server only. The microphone
is optional hardware, so devices without one still see the app. So is a
portrait screen: ML Kit's document scanner locks its activity to portrait,
which Play would otherwise read as a required feature
(`android.hardware.screen.portrait`) and hide the app from landscape-only
devices. Before an upload, check the release build's features:

```sh
./gradlew assembleRelease
aapt2 dump badging app/build/outputs/apk/release/app-release-unsigned.apk | grep feature
```

The microphone and `android.hardware.screen.portrait` should show as
`uses-feature-not-required`, and nothing beyond `android.hardware.faketouch`
as a required `uses-feature`.
