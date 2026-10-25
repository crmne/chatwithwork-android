# Server contract (Android)

What the Rails app (`crmne/chatwithwork`) does so the Android app feels
native. The app shares one contract with the iOS app: the iOS repository's
[`docs/server-contract.md`](https://github.com/crmne/chatwithwork-ios/blob/main/docs/server-contract.md)
is the full version, with the Ruby, ERB and Stimulus code for every step,
and its `web/` folder holds the bridge controllers and `native.css` to copy
into the Rails app. Implement that once and both apps work.

This document follows the same sections and says, for each, what Android
needs on top or does differently, plus the three things the iOS contract
leaves to Android: the path configuration file (`android_v1.json`), App
Links (`assetlinks.json`), and sending pushes through Firebase Cloud
Messaging.

Every name here (user agent tokens, paths, bridge component names, events,
payload keys) is part of the contract: change it in both apps at once, or
version it.

## Order of work

| Step | What | Android needs |
|---|---|---|
| 1 | [Recognize the apps](#1-recognize-the-apps), [layout](#2-the-layout-in-the-apps), [native.css](#3-the-stylesheet) | the iOS steps, plus `overflow-x: clip` |
| 2 | [Authentication](#4-authentication) | nothing beyond iOS |
| 3 | [Path configuration](#5-path-configuration) | `public/configurations/android_v1.json` |
| 4 | [Bridge components](#6-bridge-components), [page by page](#7-page-by-page) | nothing beyond iOS: the same controllers drive both apps |
| 5 | [App Links](#8-app-links) | `/.well-known/assetlinks.json` |
| 6 | [Push notifications](#9-push-notifications) | FCM HTTP v1 sending |
| 7 | [Browser sign-in and connecting services](#10-browser-sign-in-and-connecting-services) | nothing beyond iOS |
| 8 | [The sidebar](#11-the-sidebar) | its JSON and the `sidebar` component's `changed` |
| 9 | [The composer](#12-the-composer) | the `composer` component on the web composer, and dictation's URL |

Steps 1 to 4 are enough for a first internal test track on Google Play.

---

## 1. Recognize the apps

The Android app's user agent, as the emulator sends it (one line):

```
Chat with Work; platform=android; version=0.1.0; build=100; Hotwire Native Android; Turbo Native Android;
  bridge-components: [alert auth-session button context-menu form haptic menu notification-token review-prompt search share theme toast];
  Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/145.0.0.0 Mobile Safari/537.36
```

- The app's tokens come first and the web view's own user agent last (iOS
  has them the other way round). The iOS contract's `NativeApp::PATTERN`
  and `COMPONENTS` are unanchored, so they read both.
- `version` is the release's `MAJOR.MINOR.PATCH` and `build` its version
  code (`MAJOR*10000 + MINOR*100 + PATCH`), both set from the release tag.
  Development builds send `0.1.0` and `100`.
- `bridge-components` lists the 13 components every build registers. The
  bridge also writes them on the page as
  `<html data-bridge-platform="android" data-bridge-components="...">`.

For request tests:

```ruby
ANDROID_UA = "Chat with Work; platform=android; version=1.0.0; build=10000; Hotwire Native Android; Turbo Native Android; " \
  "bridge-components: [alert button menu toast]; Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Mobile Safari/537.36"
```

## 2. The layout in the apps

Everything in the iOS contract's section 2 applies (`data-native-app` on
`<html>`, titles, no header, footer, support launcher or drawer, the
document scrolls, no environment label, `bridge--confirm` on `<body>`,
flashes as toasts). What differs on Android:

- **No tabs: one chat screen and a sidebar.** The app opens on a chat (a
  new one, or one the person left answering under ten minutes ago), and
  every chat replaces that one screen (the path configuration's
  `replace_root`), so chats never stack. Bots, projects and the other pages
  are pushed over it; Settings and forms are modals. The sidebar, Material
  3's modal navigation drawer, opens from the menu button on the chat or a
  swipe from the edge, and the app draws it from the server's JSON
  (section 11). Every screen but a modal has New chat in its top app bar.
  Earlier builds had tabs (Chats, Bots and Settings in a floating
  toolbar) and read `android_v1.json`, the `native-tabs` meta tag and
  `data-native-tab-root`; keep serving those for them.
- **The page sits under the top app bar, above the gesture bar.** The app
  puts the web view below its top app bar and above the gesture bar, and
  moves it above the keyboard. `env(safe-area-inset-*)` is 0 in the Android
  web view: don't pad for it. `viewport-fit=cover` makes no difference
  here.
- **Titles.** The app shows `<title>` in its top app bar. Until the server
  sends bare titles, the app strips a leading `Staging · ` or
  `Development · ` and a trailing ` | Chat with Work` itself, but the
  per-page titles in the iOS contract's table are what it should get.
- **The document must scroll** (the iOS contract's "No drawer"). Pull to
  refresh on Android is the web view's own: it only starts when the page is
  at its top. The app also stops it while a finger is inside an inner
  scroller that isn't at its top, so a drawer that scrolls doesn't refresh
  by accident, but a document that scrolls is what makes it right.
- **No sideways scrolling.** A page wider than the screen pans sideways in
  the Android web view. `/chats/new` does today: `.new-chat__backdrop`
  (`inset: -2rem -30vw`) relies on its container to clip it, and without
  the drawer nothing does. See the stylesheet below.

## 3. The stylesheet

Copy the iOS repository's `web/native.css` as the iOS contract says. It
hides, in the apps, `.native-hidden`, the drawer toggle
(`.app-main__toggle`), the staging band (`.environment-label`), tooltips
(`.tooltip`'s bubbles), and keyboard-shortcut hints (`.kbd-hint`, such as
the composer's "⌘ /" or "Ctrl /"), which mean nothing on a touch screen.
Add, for Android:

```css
@layer components {
  /* Pages never pan sideways (the new chat page's backdrop is wider than
     the screen on purpose). */
  [data-native-app] body {
    overflow-x: clip;
  }
}
```

The tab builds also gave a tab's first page room under its last row for
the floating toolbar (`html[data-bridge-platform="android"]
body[data-native-tab-root] .native-main { padding-block-end: 6.5rem }`);
sidebar builds have no toolbar and no tab roots.

In the apps, only a chat opens at its end (the scroll controller's
`opensAtEnd`, for `body.chat-view`); every other page opens at its top.

## 4. Authentication

Exactly the iOS contract's section 4: Devise answers the apps with `401`,
every sign-in ends at `/recede_historical_location`, sign-in forms always
remember the person, signing out redirects to `/users/sign_in`, and the
Devise pages render without their heading strip.

How the Android app reads it:

| The server... | The app... |
|---|---|
| answers the chat or a page with `401` | shows its welcome screen; if someone was signed in, opens the sign-in form over it |
| redirects a page to `/users/sign_in` instead | the same, so the app works before the `401` ships |
| sends `/recede_historical_location` while signing in | starts again on a new chat (now signed in), then opens any link that waited |
| moves the sign-in form on to a page inside an organization, or `/accounts` | the same as a recede (a chat list it lands on isn't opened: the sidebar is the list) |
| redirects the logout form to `/users/sign_in` | shows the welcome screen without opening the sign-in form |
| renders a Devise page again (wrong password) | keeps the sign-in form open |

Sessions live in the web view's cookie store, which Android keeps across
launches. The app keeps no backup of it (`allowBackup="false"`): a session
never moves to another phone.

## 5. Path configuration

Serve `public/configurations/android_v2.json` as a static file, an exact
copy of [`app/src/main/assets/json/path-configuration.json`](../app/src/main/assets/json/path-configuration.json)
in this repository (the server's test compares them). The app fetches it on
every launch from `https://chatwithwork.com/configurations/android_v2.json`
(staging and development builds from their own server), caches it, and the
server's copy **replaces** the bundled one entirely. Change both together;
bump the name only when old builds must not read the new rules, and keep
serving the old file for them: the tab builds read `android_v1.json`.

The rules, in order (later matches override earlier ones; patterns are
regular expressions searched for in the path and query, unanchored unless
written with `^`):

| Pattern | Context | Presentation | Pull to refresh | Notes |
|---|---|---|---|---|
| `.*` | default | push | on | `uri: hotwire://fragment/web` |
| `/new(\?\|$)`, `/edit(\?\|$)` | modal | | off | every new and edit form; `uri: hotwire://fragment/web/modal` |
| `^/users/sign_in`, `^/users/sign_up`, `^/users(\?\|$)`, `^/users/password...`, `^/users/confirmation...`, `^/users/unlock...` | modal | | off | the sign-in pages |
| `/chats/new(\?\|$)`, `/chats/\d+(\?\|#\|$)` | default | `replace_root` | off | a chat is always the one screen |
| `^/shared/` | default | push | off | |
| `/settings(\?\|$)` | modal | | on | a section (`?tab=`) pushes inside the modal |
| `/chats(\?\|$)`, `/projects(\?\|$)`, `/bots(\?\|$)` | default | push | on | pushed over the chat |
| files and the marketing site | | `none` | | `open_in_browser: true`: a Chrome Custom Tab |

Compared with `ios_v2.json`:

- The patterns and the order are the same, so a page is pushed, modal, or
  refreshable alike on both phones.
- `uri` picks the Android screen: `hotwire://fragment/web` or
  `hotwire://fragment/web/modal`. Modals are full-screen on Android, not
  Hotwire Native's bottom sheets: Joe Masilotti's form, menu, share and
  button components crash in those (Cluster Headache Tracker's app found
  out), and this app's put their actions in a top app bar, which a sheet
  doesn't have.
- `modal_style` and `modal_dismiss_gesture_enabled` are iOS's and Android
  ignores them; `uri` and `open_in_browser` are Android's and iOS ignores
  them.
- The last rule is Android's own: files (`/rails/active_storage/...`) and
  the marketing site open in a Chrome Custom Tab over the app.

Hotwire Native adds its own rules for `/recede_historical_location`,
`/resume_historical_location` and `/refresh_historical_location` after
these. A recede on the chat itself (deleting it from its menu) has nothing
to go back to: the app starts a new chat instead.

## 6. Bridge components

Install the bridge and the iOS repository's `web/controllers/bridge/*.js`
as the iOS contract says: the same Stimulus controllers drive both apps,
with the same names, events and payloads (iOS contract, section 6). Android
draws each one its own way:

| Component | On Android |
|---|---|
| `button` | An icon in the top app bar (its `title` is the tooltip and accessibility label), or a text action without an image. `left` buttons join the right side, since Android keeps the left for back, close and the sidebar. A button with `nativeAction: "new-chat"` is left out: every screen's bar has the app's own New chat. |
| `menu` | The page's actions in the **overflow menu** (the three dots): `destructive` items in red, `checked` items with a check. A menu with `side: "left"` or a `header` is a chooser, as on iOS (the organization switcher): a button in the top app bar showing its `label` and a chevron, which opens a sheet titled with its `header`, the `checked` item marked. `iosImage` is ignored. |
| `search` | Joe Masilotti's component: a search action in the top app bar that opens a search field there, replying `{query}` on every change. |
| `form` | The submit as a text action in the top app bar ("Save", "Create"), faded and disabled between `disableSubmit` and `enableSubmit`. |
| `share` | `connect` adds a share icon to the top app bar; `share` opens Android's share sheet at once. The reply is `{completed, activityType}`: `completed` is whether an app was picked, `activityType` that app's package name (`com.google.android.gm`). |
| `toast` | A snackbar; `alert` and `error` stay up longer. |
| `haptic` | `success` confirms, `warning` and `error` reject, `selection` ticks, `light` and `soft` tick lightly, `medium` and `rigid` tap, `heavy` presses long. Android's touch feedback setting applies. |
| `alert` | A Material dialog: `title`, `description` as its message, `dismiss` and `confirm` buttons, the confirm in red when `destructive`. |
| `theme` | Accepted: `light` or `dark` would set the whole app's appearance (remembered across launches), `null` follows the system. Not used yet, as on iOS. |
| `review-prompt` | Joe Masilotti's component: Google Play's in-app review. Not used yet. |
| `context-menu` | A popup menu at `rect` (CSS pixels, converted with the page's zoom), with icons; without a `rect`, a sheet titled with `title`. Items with `copy` text are copied to the clipboard by the app, with no reply; with `copyHtml` too (an answer as HTML, left out when the reader copies Markdown), the clip carries both (`ClipData.newHtmlText`), else plain text (Android 13 and later confirm a copy themselves; older versions get a "Copied" snackbar). |
| `auth-session` | An **Auth Tab** (Chrome's tab for sign-ins, which hands the `chatwithwork://` redirect straight back), or a Custom Tab where the browser has none, with `ephemeral` as ephemeral browsing where supported. The `url` must be on this app's server, and https outside development, or the reply is `{error: "invalid_url"}`; `{error: "unavailable"}` when no browser can open it; `{error: "canceled"}` when the person comes back without finishing. Otherwise `{url}`, the callback URL. |
| `notification-token` | `status` is `authorized`, `denied` or `not_determined` (Android has no provisional or ephemeral). `get` shows Android 13's notification permission prompt the first time, `openSettings` opens the app's notification settings. `offer` `{reason}` (`approval` or `answer`) shows, once per install and only while nobody has decided, the app's "Want a nudge when it's ready?" explanation worded for the reason, then Android's prompt on Continue (on Android 12 and earlier, Continue turns push on); it replies like `get`, at once when there's nothing to offer. When the person allows notifications (`offer`, or `get`), every `notification-token` on screen gets its latest `connect` answered again with the token. `token` is the FCM registration token, `platform` is `android`, `environment` is always `production`, `appId` is the package name. |

Material Symbols names go in `androidImage` (`data-bridge-android-image`):
`edit_square`, `keep`, `keep_off`, `link`, `edit`, `drive_file_move`,
`delete`, `content_copy`, `refresh`, `call_split`, `add`, the names the iOS
contract's page-by-page notes use. A `.fill` suffix (`keep.fill`) draws the
filled symbol.

Two rules both apps rely on (Cluster Headache Tracker learned them the hard
way):

- **Render each bridge component once per page.** A component keeps the
  last `connect` (or `left`/`right`) it received, so a second element with
  the same controller replaces the first, and only the last one gets
  replies. One `button`, one `menu`, one `form`, one `search` per page;
  several items go in the one `menu`. `toast`, `haptic` and
  `context-menu` send one-off events and may appear on as many elements as
  needed. One case to watch: `notification-token` is both the Settings ›
  Notifications row and the hidden element in the layout, so on that page
  render only the row (`unless` the Notifications tab is showing).
- **Native code never goes by titles.** Titles are what the person reads
  and may be translated. Where native code must recognize an action, the
  payload names it in `nativeAction` (`data-bridge-native-action`), a
  stable kebab-case name such as `new-chat`, listed in the contract. The
  Android app reads one today: `new-chat` on a page's `button` (the
  Chats list's, a chat's, a project's), which it leaves out for its own
  New chat in the bar. Menus and alerts answer by index,
  and sign-out is recognized by its redirect to `/users/sign_in`, not by a
  button's label.

## 7. Page by page

The iOS contract's section 7 is the source for what each page renders in
the apps. On Android:

- **New chat and a chat** (`chats/new`, `chats/show`): the app's root.
  The top app bar has the sidebar's menu button, the page's title, New
  chat, and the chat's own actions: Pin, Share link, Rename, Move to
  project and Delete in the overflow menu, Delete in red, behind the native
  confirm. After the first message the server redirects to the new chat,
  which replaces the new chat page. A message's "More actions" button opens
  the context menu as a popup at the button. The approval and composer
  haptics vibrate as in the table above. The composer's keyboard-shortcut
  hint (`.kbd-hint`) stays in the markup: native.css hides it in the apps.
- **The chat list** (`chats/index`) and **projects** (`projects/index`,
  from the sidebar's "All projects..."): pushed over the chat, when a link
  leads there. The sidebar is the app's list, so the app opens neither by
  itself.
- **Bots** (`bots/index`, from the sidebar): pushed over the chat, New bot
  (`add`) in the top app bar. A bot, its triggers and its conversations
  push on top.
- **Settings** (from the person at the sidebar's foot): a modal. `/settings`
  is the list of sections and `/settings?tab=...` pushes inside it with a
  back arrow. Its organization switcher still works (a link into another
  organization starts the app over there), but the sidebar's person menu
  is the usual way to switch.
- **Forms in modals** (every `new` and `edit`): the `form` component puts
  the submit in the top app bar. When the form recedes
  (`recede_or_redirect_to ..., notice: "Chat renamed."`), the app closes
  the modal, shows the notice as a snackbar, and loads the page under the
  modal again. (Hotwire Native Android would also go back from that page;
  the app stops it, to match iOS.) A create that redirects to the new
  record closes the modal and pushes it.
- **Deleting** a chat from its menu: `recede_or_redirect_to chats_path,
  notice: "Chat deleted."` shows the notice and starts a new chat.
- **Billing**: Google Play requires its own billing for digital
  subscriptions sold in an app in most countries. Until that's decided,
  render Settings › Billing in the Android app without checkout, top-ups or
  links to them: the plan, the credits, and "Billing is managed on
  chatwithwork.com" as plain text. (`native_app&.android?`.)

- **Sharing into the app**: text and links shared from another app
  (Android's share sheet, `text/plain`) open a new chat in the sidebar's
  list as `/chats/new?title=<subject>&text=<text>` (`title` only when the
  other app gave a subject), which the server puts in the composer
  (`ChatsHelper#shared_prompt`, as for the installed web app). Files
  (`ACTION_SEND` or `ACTION_SEND_MULTIPLE` with images, documents, PDFs,
  text files) are read at once, a new chat opens (with any shared words as
  above), and the files go to its composer as `attach` when it connects
  (section 12). Nothing is sent until the person sends it.
- **The home screen widget and the Quick Settings tile** open a new chat in
  the sidebar's list; the widget's microphone also starts dictation in its
  composer once it connects. They carry no content and need nothing from
  the server.

Links the app handles itself:

- Links to other sites open in a Chrome Custom Tab, in the app's colors;
  `mailto:`, `tel:`, `sms:`, `geo:` and Play Store links go to their apps.
- Files and the marketing site open in a Custom Tab (the path
  configuration's last rule).
- A link to a chat replaces the one screen; a link to any other page
  pushes it over the chat.
- A link into another organization starts the app over there: on that
  chat, or on its new chat with the page on top.
- `notice` and `alert` on `/recede_historical_location`,
  `/resume_historical_location` and `/refresh_historical_location` show as
  snackbars.

## 8. App Links

Links to chatwithwork.com in emails and messages open the app once the site
says the app may handle them. Serve `GET /.well-known/assetlinks.json` over
HTTPS with `Content-Type: application/json`, status 200, no redirect and no
authentication (Google fetches it when the app is installed):

```json
[
  {
    "relation": [
      "delegate_permission/common.handle_all_urls",
      "delegate_permission/common.get_login_creds"
    ],
    "target": {
      "namespace": "android_app",
      "package_name": "com.chatwithwork.app",
      "sha256_cert_fingerprints": [
        "AB:CD:...:EF"
      ]
    }
  }
]
```

- `sha256_cert_fingerprints`: the **app signing key's** SHA-256, from Play
  Console's app signing page (under App integrity), and the upload key's
  too if builds signed with it are installed directly. Keep
  them in configuration (`Rails.application.credentials.dig(:android,
  :cert_fingerprints)`), like the iOS team ID. They aren't secret, but they
  are per environment.
- Staging serves the same file for `com.chatwithwork.app.staging` (signed
  with the release key once it exists). Debug builds can't be verified; on
  a test phone, `adb shell pm set-app-links --package com.chatwithwork.app.debug 2 all`
  makes them open links anyway.
- `get_login_creds` lets Android's password managers offer the person's
  saved chatwithwork.com passwords in the app's sign-in form, as
  `webcredentials` does on iOS.
- A controller outside any account, like the iOS association file's
  (`disallow_account_scope`, no CSRF), and a route:
  `get "/.well-known/assetlinks.json", to: "well_known#assetlinks"`.

The app claims these paths (`AndroidManifest.xml`), the same set as the
iOS association file's includes. Android has no excludes, so only these are
listed:

```
/chats  /chats/*  /projects  /projects/*  /settings  /settings/*
/*/chats  /*/chats/*  /*/projects  /*/projects/*  /*/settings  /*/settings/*
/*/join/*  /accounts  /accounts/*  /device  /*/device
/invitations/*  /shared/*  /users/password/edit*  /users/confirmation*
```

Everything else, OAuth callbacks included, keeps opening in the browser.
Opened while signed out, a link waits behind the sign-in form and opens
once the person is in; a password reset or confirmation link opens in the
sign-in form itself.

## 9. Push notifications

Registration is the iOS contract's section 9 as it is: the page asks the
app for a token through `notification-token` and posts it to
`POST /native/push_registrations`. From Android:

```json
{ "push_registration": { "token": "fGh3...:APA91b...", "platform": "android", "environment": "production", "app_id": "com.chatwithwork.app" } }
```

`token` is an FCM registration token (about 160 characters, any of
`[A-Za-z0-9_:-]`); `app_id` is `com.chatwithwork.app`,
`com.chatwithwork.app.staging` or `com.chatwithwork.app.debug`. The app
registers with Firebase only after the person turns notifications on, and
unregisters when it sees a sign-out.

The app no longer asks after signing in. It offers notifications once per
install when a page sends `notification-token`'s `offer` (section 6): the
server sends it from the layout's hidden element the first time a chat page
shows a change or question waiting for the person, or an answer still
working after 15 seconds. A short native explanation ("Want a nudge when
it's ready?") comes before the system prompt (Android 13 and later; on
earlier versions Continue turns push on). When the person allows
notifications, it answers the latest `connect` of every
`notification-token` on screen again with the new state, so the page
registers the token without waiting for another page. Turn on in Settings ›
Notifications still asks, for someone who said "Not now".

### Sending

Firebase Cloud Messaging's HTTP v1 API, with a service account of the
Firebase project that holds the three Android apps:

```
POST https://fcm.googleapis.com/v1/projects/<project_id>/messages:send
Authorization: Bearer <OAuth 2 token, scope https://www.googleapis.com/auth/firebase.messaging>
Content-Type: application/json
```

The `googleauth` gem makes the token from the service account's JSON key
(`Google::Auth::ServiceAccountCredentials.make_creds(json_key_io:, scope:)`,
cached until it expires); credentials `firebase.project_id` and
`firebase.service_account`. The same `PushDeliveryJob` as for APNs, picking
FCM for `android` registrations.

### The payload

**Data only**: no `notification` block, so the app writes the notification
itself (its channel, one per chat, the tap opening the chat as the app's
one screen), and,
as on iOS, **no content**: never a chat title, a question, an answer, a
tool's arguments or a file name. The organization's and the service's
names are fine.

```json
{
  "message": {
    "token": "fGh3...:APA91b...",
    "android": {
      "priority": "high",
      "ttl": "86400s",
      "collapse_key": "chat-482139075-42"
    },
    "data": {
      "kind": "approval_waiting",
      "path": "/482139075/chats/42",
      "thread": "chat-482139075-42",
      "title": "Acme",
      "body": "A change is waiting for your approval in Slack."
    }
  }
}
```

- Every `data` value is a string.
- `kind`: `approval_waiting`, `input_requested` (body "A question is
  waiting for your answer in Notion."), `answered`, `failed`, `handed_off`
  (send `answered` with `"priority": "normal"`), or `resolved`, Android's own: the
  change was decided or the question answered somewhere else, so the app
  removes that chat's notification. Send `resolved` with `"priority":
  "normal"` and only `kind`, `path` and `thread`. (Unknown kinds are
  ignored, so iOS can adopt `resolved` later.)
- `path` (required): the page to open, starting with `/` on this server.
  Anything else is dropped.
- `thread`: one notification per chat; a newer one replaces it. Defaults
  to `path`.
- `title` and `body`: what the notification says. Without them the app
  shows "Chat with Work" and "A change is waiting for your approval." (or
  "A question is waiting for your answer.", "Your answer is ready.", "A
  chat couldn't finish its answer.", "A bot handed a conversation to your
  team.").
- High priority wakes the phone from Doze. Android lowers the priority of
  apps whose high-priority messages don't end in a notification, which is
  why `resolved` goes out as normal.

FCM answers `404` with `UNREGISTERED`, or `400` with `INVALID_ARGUMENT`
naming the token, for a token that's gone: destroy the registration. Retry
`429` and `5xx` with backoff, honoring `Retry-After`.

### Live Updates

When the person leaves the app while the chat on screen is answering (its
composer says `working`), the app posts an ongoing notification for that
chat: Android 16's progress style, promoted where the system allows, the
organization's name, "Your answer is on its way" and a timer, never the
chat's title or content. It uses the chat's notification slot (tag
`chat-<organization>-<number>`, the `thread` the server sends), so the
push that ends the wait (`answered`, `approval_waiting`,
`input_requested`, `failed`) takes its place and `resolved` clears it.
It's taken away when the person comes back, and after 30 minutes if no
push arrives. It only shows for someone who turned push on. No server
change: the pushes must keep using that `thread`.

When to send is the iOS contract's "When", unchanged. The app files
approvals and questions under the "Approvals and questions" channel, and
answers, failures and hand-offs under "Answers", which the person can
silence in Android's settings apart.

## 10. Browser sign-in and connecting services

The iOS contract's section 10 as it is: `/native/sign_ins/new`, the
`chatwithwork://sign-in?token=...` hand-back redeemed with the PKCE
verifier at `POST /native/sign_ins`, and `/native/handoffs` for connecting
services, ending at `chatwithwork://handoff?status=...&return_to=...`.

On Android:

- The flow runs in an Auth Tab, which returns the `chatwithwork://`
  redirect to the app directly, or, in a browser without Auth Tabs, a
  Custom Tab, whose redirect reaches the app through its
  `chatwithwork://` intent filter. Either way the server's redirect is the
  same.
- If Android ended the app while the browser was in front (it does, on
  phones short of memory), the page that held the verifier is gone: the
  app shows "Signing in didn't finish. Try again." for a sign-in, and for a
  connection opens `return_to` with the `message`, since the connection
  itself went through.
- Until this ships, hide the "Sign in with ..." buttons and the
  connectors' Connect buttons in the apps, as the iOS contract says:
  staging's Google sign-in in a Custom Tab today stops at Google's
  "Missing required parameter: client_id" error.

## Testing the contract

- **The playground**: the iOS repository's `Playground/server.py` implements
  this contract with fixture pages in the web app's markup. Run it, forward
  the port, and point a debug build at it:

  ```sh
  python3 ../chatwithwork-ios/Playground/server.py --port 8765
  adb reverse tcp:8765 tcp:8765
  ./gradlew installDebug -PbaseUrl=http://localhost:8765
  ```

- **The app against your Rails app**: `bin/dev`, then `adb reverse tcp:3000
  tcp:3000` and `-PbaseUrl=http://localhost:3000`, or the default
  `http://10.0.2.2:3000` (the emulator's view of your computer; Android 17
  asks for the local network permission first).
- **Rails tests**: the iOS contract's request tests, once more with
  `ANDROID_UA` (section 1), and a test that `/configurations/android_v1.json`
  is the Android repository's file and `/.well-known/assetlinks.json`
  answers JSON without a redirect.

## 11. The sidebar

Builds that draw the sidebar themselves register the `sidebar` bridge
component, so the user agent's `bridge-components` lists `sidebar`
(`native_app.supports?(:sidebar)`). The server shows those builds Connect,
Reconnect and "Sign in with ..." (section 10), puts the chat's New chat
under `nativeAction: "new-chat"`, and adds two things:

### Its JSON

`GET /:account/native/sidebar`, with the web view's own session (its
cookies and user agent, no token of its own) and `Accept:
application/json`. `list=<key>` picks the list (`recent`, `all`, or a
project's number; the app remembers the person's choice per organization),
`all=1` sends every chat (for searching), and `next` is the URL of the
following page. A 401 means the session is gone.

```json
{
  "organization": {"id": "482139075", "name": "Northwind Studio", "initials": "NS"},
  "organizations": [{"id": "482139075", "name": "Northwind Studio", "initials": "NS", "current": true, "role": "admin", "url": "/482139075/chats/new"}],
  "person": {"name": "Maya Hart", "email": "maya@northwind.example", "initials": "MH", "avatar_url": null},
  "list": {"key": "recent", "name": "Private chats", "kind": "recent", "hq": false},
  "lists": [{"key": "recent", "name": "Private chats", "kind": "recent", "hq": false, "pinned": false, "current": true}],
  "links": {"new_chat": "/482139075/chats/new", "bots": "/482139075/bots", "settings": "/482139075/settings",
            "projects": "/482139075/projects", "new_project": "/482139075/projects/new",
            "organizations": "/accounts", "new_organization": "/accounts/new"},
  "waiting": {"count": 1, "items": [{"title": "Launch date in #launch", "url": "/482139075/chats/42", "kind": "approval", "project": "Website relaunch"}]},
  "bots": {"count": 0, "attention": false, "next_run": "08:30"},
  "pins": [CHAT],
  "chats": [CHAT],
  "next": "/482139075/native/sidebar?list=recent&before=41&asked=2026-10-24T09:00:00.000000Z"
}
CHAT = {"number": 42, "title": "Launch date in #launch", "url": "/482139075/chats/42", "state": "idle",
        "waiting": false, "project": "Website relaunch", "asked_at": "2026-10-25T08:12:00Z", "group": "Today"}
```

Links that don't apply are `null` (a guest's `bots`, `projects` and
`new_project`), and `bots` is `null` for guests. A list's `kind` is
`recent`, `all` or `project`; a waiting item's `approval`, `question` or
`bot`; a chat's `state` `idle`, `processing` or `error`. Every URL is a
path on this server. The app ignores keys it doesn't know.

What the app draws from it, top to bottom: the list switcher (`list`; a
chooser of `lists`, then "All projects..." and "New project"), a search
that filters titles on the phone, New chat (`links.new_chat`, also where
the bar's New chat and a cold start go), Waiting for you (`waiting`, a
sheet of its items), Bots (`bots`: the count with an attention dot, else
the next run), Pinned, then the chats by `group`, a live dot on those
answering and an attention dot on those waiting, loading `next` near the
end. The person at the foot opens a sheet of `organizations` (each `url`
starts the app over there), All organizations, New organization and
Settings. Without the JSON (offline, an older server), the sidebar still
offers New chat, Bots and Settings.

It fetches the JSON as the sidebar opens, when the app comes back to the
foreground, after a page in another organization, every 5 seconds while
it's open and something answers, and on `changed`.

### The `sidebar` component

Every signed-in page in these builds has one hidden `bridge--sidebar`
element, subscribed to the person's own chat-list streams. It sends:

| Event | Data | The app |
|---|---|---|
| `connect` | `{}` | nothing |
| `changed` | `{}` | fetches the JSON again (debounced, 500 ms) if the sidebar is open, else on its next opening |

No replies.

## 12. The composer

Builds that register the `composer` component draw the composer
themselves, docked above the keyboard and the gesture bar, under the page:
"+", the field, the model, and Send or Stop, on the composer's rainbow
edge. The web composer (`#new_message`, with `bridge--composer`) stays in
the page out of sight (native.css, once `data-bridge-components` lists
`composer`), and the app drives it, so every rule it carries holds: locks,
who may steer, the queue, files waiting for their scan, the model, and web
search. The conversation then ends 1.5rem above the page's bottom; the app
lays the web view out above its composer, never under it.

| Event | Direction | Data |
|---|---|---|
| `connect`, then `state` | web to app | `{placeholder, locked, lockReason, working, stopping, models: [{name, rate, selected, searchesWeb}], webSearch: {on, disabled, note} \| null, dictation: {url} \| null, csrfToken, attachments: [{id, name, size, contentType, uploading, progress}], acceptsFiles}` |
| reply to `connect` | app to web | `{action: "send", text}`, `{action: "stop"}`, `{action: "model", index}`, `{action: "webSearch", on}`, `{action: "attach", files: [{name, type, data}]}` (data base64), `{action: "remove", id}`, `{action: "text", text}` |

A chat draws its composer again as an answer starts and ends; each new one
connects again, and the app always replies to the latest `connect`. A page
without a composer never connects, and the native one stays hidden there.

What the app does with it:

- **The field** keeps what's typed until Send, which sends `send {text}`
  (with a confirming haptic) and clears it. Send needs words or a file and
  waits while a file uploads (the web would refuse). While `working`, an
  empty field turns the button into Stop (disabled while `stopping`);
  words typed then are sent as `send`, and the web decides whether they
  steer the answer or wait for it.
- **Locked**: the field and "+" are disabled and `lockReason` shows above
  the composer.
- **The model**: a chip with the selected model's name, hidden while locked
  or working, which opens a sheet of `models` (name and rate, the selected
  one checked) and answers `model {index}`.
- **"+"** opens a sheet: Camera, Photos (Android's photo picker, no
  permission needed), Files, Scan (ML Kit's document scanner, one PDF),
  Dictate when `dictation` is set, and Web search as a switch with its
  `note` when `webSearch` is set (`webSearch {on}`). Files go to the page
  as `attach`, base64, at most 25 MB each (larger ones are named in a
  snackbar and left out). Each attachment shows as a chip with its name, its
  size or upload progress, and remove (`remove {id}`).
- **Dictation**: Android 10 and later record OGG/Opus (at most five
  minutes, with a level meter, Stop and Cancel), never Android's speech
  recognizer, and post it as multipart field `audio` to `{base}{dictation.url}`
  with the web view's cookies, `X-CSRF-Token: csrfToken` and `Accept:
  application/json`. `200 {text}` puts the words in the field and sends
  `text {text}`; an empty text says nothing was heard; `{error}` (403 for no
  credits, 413, 415, 429, 502) shows the server's words. Earlier versions
  leave Dictate out.
- Files shared into the app from elsewhere (section 7, "Sharing into the
  app") wait for the next composer that connects and go to it as `attach`.
