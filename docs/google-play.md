# Publishing on Google Play

Everything Play Console asks for, and where it lives. The listing is in
`fastlane/metadata/android/en-US/` in the layout
[fastlane supply](https://docs.fastlane.tools/actions/supply/) reads, so it
can be pasted by hand or uploaded with `fastlane supply` (not set up here:
it needs a Play service account key, which never goes in this repository).

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
   1Password (item "Android upload key", vault chatwithwork). Play App
   Signing is on by default for new apps: Play signs what it distributes,
   and this key only proves an upload is yours.
4. **The app.** Play Console > Create app: name "Chat with Work", default
   language English (United States), App, Free.
5. **The demo login** for review, on production: the 1Password item "App
   Review demo login" holds it, and the Rails app's `script/store/seed
   production review app-review@chatwithwork.com <password file>` creates it
   with synthetic content, once the release that serves the apps is
   deployed.

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

Screenshots come from the app on an emulator, signed in to staging as the
synthetic screenshot account (`script/store/seed staging screenshots` in
the Rails app; Northwind Studio and everything in it is made up), framed
with the iOS repository's `script/frame-screenshots.mjs` and its captions.

## App content

- **Privacy policy:** https://chatwithwork.com/privacy-policy
- **Ads:** no ads.
- **App access:** all or some functionality is restricted. Add the demo
  login: email `app-review@chatwithwork.com`, password from 1Password, and
  the instructions: "Sign in on the welcome screen. The account belongs to
  a made-up organization with synthetic chats. Open 'Morning Acme brief'
  for an answer with sources and 'Acme launch update' for a change waiting
  for approval (no Slack is connected, so approving posts nothing). Services
  are connected on chatwithwork.com from a computer."
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
- **Account deletion:** yes, in the app (Settings > Account > Delete
  account), and the web link https://chatwithwork.com/account-deletion, which
  gives the steps, what's deleted at once and after 30 days, what stays, and
  how to ask by email without signing in.

## Data safety

True to what the app and the server keep (the privacy policy is the
reference). Data is encrypted in transit (HTTPS only). People can delete
their account and its data in the app. Nothing is shared with third parties
in Play's sense: the model providers and other processors act for us, and
services a person connects receive only what they approve.

| Category > type | Collected | Optional | Purposes |
|---|---|---|---|
| Personal info > Name | Yes | No | App functionality, Account management |
| Personal info > Email address | Yes | No | App functionality, Account management |
| Personal info > User IDs | Yes | No | App functionality, Account management |
| Messages > Other in-app messages (chats with the assistant) | Yes | No | App functionality |
| Photos and videos > Photos, Videos (attachments) | Yes | Yes | App functionality |
| Audio > Voice or sound recordings (dictation; transcribed in the EU, the recording isn't kept) | Yes | Yes | App functionality |
| Files and docs (attachments) | Yes | Yes | App functionality |
| App info and performance > Diagnostics (server error reports, by user ID only) | Yes | No | App functionality |
| Device or other IDs (the push token, once notifications are on) | Yes | Yes | App functionality |
| Location, financial info, health, contacts, calendar, web browsing, app activity, installed apps | No | | |

Data is not used for advertising, analytics, or tracking. Payment details
are never collected in the app: the app sells nothing (Settings > Billing
shows the plan without checkout), which also keeps it within Google Play's
payments policy.

## Permissions

`INTERNET`; `POST_NOTIFICATIONS`, asked for once after signing in (with a
short explanation first) or from Settings > Notifications;
`RECORD_AUDIO` (with `MODIFY_AUDIO_SETTINGS`, which the web view needs to
record), asked for the first time someone taps the composer's microphone,
and granted to the web view for the app's own server only. The microphone
is optional hardware, so devices without one still see the app.
