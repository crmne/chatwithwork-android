# Server contract (Android)

What the Rails app (`crmne/chatwithwork`) does so the Android app feels
native. The app shares one contract with the iOS app: the iOS repository's
[`docs/server-contract.md`](https://github.com/crmne/chatwithwork-ios/blob/main/docs/server-contract.md)
is the full version, with the Ruby, ERB and Stimulus code for every step,
and its `web/` folder holds the bridge controllers and `native.css` to copy
into the Rails app. Implement that once and both apps work.

This document follows the same sections and says, for each, what Android
needs on top or does differently, plus the three things the iOS contract
leaves to Android: the path configuration file (`android_v4.json`), App
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
| 3 | [Path configuration](#5-path-configuration) | `public/configurations/android_v4.json` (and v1, v2, v3 for older builds) |
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
Chat with Work; platform=android; version=0.1.0; build=100; new-chat=page; Hotwire Native Android; Turbo Native Android;
  bridge-components: [alert auth-session button context-menu form haptic menu notification-token review-prompt search share theme toast];
  Mozilla/5.0 (Linux; Android 10; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/145.0.0.0 Mobile Safari/537.36
```

- The app's tokens come first and the web view's own user agent last (iOS
  has them the other way round). The iOS contract's `NativeApp::PATTERN`
  and `COMPONENTS` are unanchored, so they read both.
- `version` is the release's `MAJOR.MINOR.PATCH` and `build` its version
  code (`MAJOR*10000 + MINOR*100 + PATCH`), both set from the release tag.
  Development builds send `0.1.0` and `100`.
- `new-chat=page;` (from path configuration v4, right after `build=`) says
  New chat and New bot are pages in this build, so the server draws what
  they turn into in place (`NativeApp#new_chat_page?`, section 5). Builds
  that read v3 or earlier don't send it.
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

- **No tabs: one root screen and a sidebar.** The app opens on the
  organization's home, which the server sends on to Decisions, or on a chat
  (one it was sent to, or one the person left answering under ten minutes
  ago). Everything reached from the sidebar is a place, never a step: a
  chat, Decisions, Bots, a bot, Projects, a project, Settings,
  Instructions, and New chat and New bot (section 5, "New chat and New bot
  are pages"). A place replaces the one root screen, which has the
  sidebar's menu button and never back, so places never stack
  (Carmine, 2026-11-05). Only a
  drill-down is pushed over a place, with back to it (a bot from Bots, a
  Settings tab from the Settings list, New chat from the bar's button);
  forms and pickers are modals
  (section 5). The system back at a place works as a browser's back
  (Carmine, 2026-11-27): it opens the place the person was at before, as
  a place (the sidebar's button, no back arrow, moving as a pop does), or,
  with nothing before it, the home (Decisions, `links.decisions` or
  `waiting.url` in the sidebar's JSON), and at the home with nothing
  before it, it leaves the app. The places are the app's own short list
  (`PlaceHistory`, the newest five, in memory and the saved state, nothing
  from the server): going back adds nothing to it, so there's no
  ping-pong between two places; a place already on it moves to the top;
  back reaching the home clears it, so back at the home always leaves; an
  empty New chat (no words waiting, `NewChatDrafts`) isn't worth going
  back to, so the chat it started stands in its place; a chat deleted
  from its own menu or from the sidebar (the page's rows, or the native
  sidebar's Delete, which the page on screen sends) leaves the list, as the
  app learns from the page (`PageScripts.DELETES` notes each confirmed
  delete's path, and the app takes them after every visit and as the
  sidebar closes), so back from Decisions never opens it; a place that
  answers a client error is skipped for the one before it, or the home
  (a chat that's gone or no longer the person's redirects to the chat
  list instead, so only the list above keeps back off one); and a new
  organization or session starts it over. A chat opened from a notification or a link has nothing
  before it, so back goes home, then out. Back with the sidebar open
  closes it, and in a drill-down or a modal pops one step, as before. The
  app enables its back callback only when it goes to a place
  (`GoingBack.step`), so the predictive back gesture shows the system's
  own animation on the way out. Back on a modal closes it, never the app.
  The
  sidebar, Material 3's modal navigation drawer, opens from the menu
  button on the root or a swipe from the edge, and the app draws it from
  the server's JSON (section 11). Every screen but a modal has New chat in
  its top app bar.
  Earlier builds had tabs (Chats, Bots and Settings in a floating
  toolbar) and read `android_v1.json`, the `native-tabs` meta tag and
  `data-native-tab-root`; keep serving those for them.
- **Every page runs under the top app bar.** The app puts the web view
  above the gesture bar, and moves it above the keyboard.
  `env(safe-area-inset-*)` is 0 in the Android web view: don't pad for it.
  `viewport-fit=cover` makes no difference here. Every screen, a chat, a
  page, or a modal, has the same bar (`TopBar`): no surface of its own,
  one 56dp row floating over the page, its navigation button (the
  sidebar, back, or close) a round surface at the start, its actions on
  one pill at the end (`BarPill`), both 48dp, Live Wire's surface with its
  hairline and a soft lift, and the title between them. Where the page runs under it, the web view reaches the top
  of the screen and the app sets `--native-top-bar-height` on `<html>` to
  how much of the page the bar covers (status bar included, in CSS pixels;
  `0px` on a page that starts below it), as each visit starts, as a page
  finishes loading whole, and each time the screen starts again (back in
  the foreground, or on top again), once the web view is laid out: told
  before it was, the page never heard and ran under the bar. A chat always runs under it; any
  other page only where the path configuration's `page_under_top_bar` is
  `true`, which `android_v4.json` (and v3 and v2 before it) sets for every page once the server's
  `native.css` keeps every page's start, and anything sticky at its top,
  clear of the bar. A server whose configuration doesn't say so gets
  pages that start below the bar, with the fade's last 20dp coming in over
  them as they scroll. Pages don't draw a header of their own: `.app-bar`
  and `.native-hidden` stay hidden, so the bar and its fade are the only
  ones. `scroll_controller.js` and the panel sheet keep a chat's new
  question and the sheet clear of the bar too.
- **What's behind the bar (2026-11-27).** The page draws it (Android
  couldn't blur a web view from outside it anyway: a RenderNode recording
  of the web view comes out empty): where the path configuration's
  `page_draws_top_bar_backdrop` is `true` (`android_v4.json` sets it on
  every page) and the page runs under the bar, the app also sets
  `data-native-top-bar="floating"` on `<html>` (and removes it elsewhere),
  and `native.css` puts a fixed `body::before` over the page's top, as
  tall as `--native-top-bar-height` plus 2px: a fade of the canvas,
  translucent even at the top (88% under the status bar), easing through
  the title's row, where what's behind still shows faintly, to nothing
  just below the buttons, never taking a touch. The app then draws
  nothing there. A server without the flag (an older
  self-hosted install) gets the app's own fade instead, the canvas down
  past the title and an ease into the page.
- **Titles.** The app shows `<title>` in its top app bar, and follows it
  when the page changes it later (`onReceivedTitle`): a chat's `<title>`
  changes as its automatic title arrives, or someone renames it. Until the server
  sends bare titles, the app strips a leading `Staging · ` or
  `Development · ` and a trailing ` | Chat with Work` itself, but the
  per-page titles in the iOS contract's table are what it should get.
- **The document must scroll** (the iOS contract's "No drawer"). Pull to
  refresh on Android is the web view's own: it only starts when the page is
  at its top. The app also stops it while a finger is inside an inner
  scroller that isn't at its top, so a drawer that scrolls doesn't refresh
  by accident, but a document that scrolls is what makes it right.
- **Pull to refresh brings the page up to date in place** (2026-11-18).
  The pull calls the page's `window.chatWithWork.refresh()`
  (`PullToRefresh`), which morphs a fresh copy of a chat's frame in
  (what's typed, the focus and the scroll kept) or refreshes any other page
  by morphing it, and keeps the spinner until `window.chatWithWork.refreshing`
  is false, at most 10 seconds. A page without `chatWithWork` (an older
  server) gets Hotwire Native's refresh, a cold reload. Hotwire Native sets
  its listener each time it prepares the screen, so `WebFragment` takes it
  over on every start. The page's touchstart listener (`PageScripts`) tells
  Hotwire Native the pull may start through `window.TurboSessionChannel`
  (Hotwire Native 1.4 has no `window.TurboSession`).
- **Coming back from the background needs nothing from the app.** The web
  view fires `visibilitychange` as the app returns, and the page reopens its
  connection and brings a chat up to date in place itself (the server's
  docs/ui/chat.md, "Coming back").
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
| sends `/recede_historical_location` while signing in | starts again on the organization's home (now signed in), then opens any link that waited, New chat among them |
| moves the sign-in form on to a page inside an organization, or `/accounts` | the same as a recede (a chat list it lands on isn't opened: the sidebar is the list) |
| redirects the logout form to `/users/sign_in` | shows the welcome screen without opening the sign-in form |
| renders a Devise page again (wrong password) | keeps the sign-in form open |

Sessions live in the web view's cookie store, which Android keeps across
launches. The app keeps no backup of it (`allowBackup="false"`): a session
never moves to another phone.

## 5. Path configuration

Serve `public/configurations/android_v4.json` as a static file, an exact
copy of [`app/src/main/assets/json/path-configuration.json`](../app/src/main/assets/json/path-configuration.json)
in this repository (the server's test reads which version `AppConfig.kt`
names and compares the bundled copy with it). The app fetches it on every
launch from `https://chatwithwork.com/configurations/android_v4.json`
(staging and development builds from their own server), caches it, and the
server's copy **replaces** the bundled one entirely. Change both together;
bump the name only when old builds must not read the new rules, and keep
serving the old file for them: 1.2.11 and 1.2.12, which show New chat
as a modal, read `android_v3.json`; the sidebar builds before them read
`android_v2.json` (v3 without the modal: `/chats/new` is in the chats'
`replace_root` rule there), and the tab builds read `android_v1.json`. v2
can't become v3 in place: those builds start the navigator on New chat,
and a modal can't be the first screen. v3 can't become v4 in place either:
those builds close their New chat as a modal and send no `new-chat=page`,
so the server draws no in-place turn for them.

The rules, in order (later matches override earlier ones; patterns are
regular expressions searched for in the path and query, unanchored unless
written with `^`; from v4 every rule after the sign-in pages' is anchored
as `^(/[0-9]+)?/...`, so a `return_to` in a query can't match it):

| Pattern | Context | Presentation | Pull to refresh | Notes |
|---|---|---|---|---|
| `.*` | default | push | on | `uri: hotwire://fragment/web`; `query_string_presentation: replace`, so a visit to the page on screen with another query (a bot's tabs, a form redirecting back) replaces it rather than stacking a copy |
| `/new(\?\|$)`, `/edit(\?\|$)` | modal | | off | every new and edit form; `uri: hotwire://fragment/web/modal` |
| `^/users/sign_in`, `^/users/sign_up`, `^/users(\?\|$)`, `^/users/password...`, `^/users/confirmation...`, `^/users/unlock...` | modal | | off | the sign-in pages |
| `/chats/\d+(\?\|#\|$)` | default | `replace_root` | on | a chat is always the one screen; the pull brings it up to date in place, keeping what's typed. The turn from New chat replaces the screen it was sent from instead (section 5, "New chat and New bot are pages") |
| `/chats/new(\?\|$)`, `/bots/new(\?\|$)` | default | push (`presentation: default`) | off | New chat (with `?project_id=`, `?text=&title=`) and New bot's sentence: pages, pushed over the page they're opened from; the sidebar and the ways in from outside open them as the place. `skeleton: new_chat`, `uri: hotwire://fragment/web` |
| `/chats/\d+\?(.*&)?from=` | default | push (`presentation: default`) | on | a chat a page opens about something on it (a bot's "Change it in a chat", its runs, Decisions' "Open chat", a side chat), pushed with back to that page |
| `^/shared/` | default | push | off | |
| `/settings\?` | default | push | on | a Settings tab, pushed over the Settings list (`query_string_presentation: default`, since the list and its tabs share `/settings`) |
| `^(/[0-9]+)?/home(\?\|$)` (anchored: a `return_to=/home` in another page's query isn't the home), `/decisions(\?\|$)`, `/bots(\?\|$)`, `/chats(\?\|$)`, `/projects(\?\|$)`, `/settings$` | default | `replace_root` | on | the places: the organization's home (where the app opens, which the server sends on to Decisions; a server without it answers 404, and the app opens the chat list instead, below), Decisions, Bots, the chat lists, Projects, the Settings list |
| `/chats/\d+/files/` | modal | | off | a chat's file in its viewer, a full-screen modal over the chat (iOS contract, "A file") |
| files and the marketing site | | `none` | | `open_in_browser: true`: a Chrome Custom Tab |

Every rule but the browser's also says which **skeleton** the app draws
from the moment the screen appears until its page first paints (the
`skeleton` property, merged like every other: the later match wins):

| `skeleton` | Where | What the app draws |
|---|---|---|
| `chat` | `/chats/<n>`, a `?from=` chat too | the chat's: two questions and answers, the composer at rest |
| `new_chat` | `/chats/new`, `/bots/new` | a greeting over three suggestions, the composer at rest |
| `list` | `/decisions`, `/bots`, `/chats`, `/projects` | rows of a round mark and two lines, as many as fit |
| `settings` | `/settings` and `/settings?tab=...` | two sections: a heading, a muted line, a group of rows |
| `form` | every other `new` and `edit` sheet | four labelled fields |
| `page` | everything else (`.*`): a bot and its tabs, a project, a trigger, the panel beside a chat, Settings' own pages | a heading, two lines of text, a group of four rows |
| `none` | the sign-in pages, a chat's file viewer | nothing, as before |

Every kind is the same machinery (`PageSkeleton`, drawn by `SkeletonView`):
bones in Live Wire's `--skeleton-bone` with one `--skeleton-shine` band
sweeping across them every 1.4 seconds, still under Remove animations; the
title's bone in the bar until the page names itself (never the page's
address, which Hotwire Native titles a screen with until then, `PageTitles`); groups and fields as
boxes on the surface with a hairline; read as "Loading" by screen readers
("Loading chat" on a chat), with the page below hidden from them. It
crossfades to the page in 200 ms at its first paint, goes at once for an
error, and after 10 seconds whatever happens. It shows only the first time
a screen appears, never when coming back to one, and a skeleton stands in
for Hotwire Native's loading view. An unknown value draws `page`. A server
that sends no `skeleton` (one from before 2026-11-07) gets `chat` for a
chat and no skeleton elsewhere, as earlier builds do with every
server. The numbers are the same as iOS's.

Compared with `ios_v4.json`:

- The patterns and the order are the same, so a page is pushed, modal, or
  refreshable alike on both phones.
- `uri` picks the Android screen: `hotwire://fragment/web` or
  `hotwire://fragment/web/modal`. Modals are full-screen fragments on
  Android, not Hotwire Native's bottom sheets: Joe Masilotti's form, menu,
  share and button components crash in those (Cluster Headache Tracker's
  app found out), and this app's put their actions in a top app bar, which
  a sheet doesn't have. Inside its fragment a modal is Material 3's
  full-screen dialog (below, "How a modal looks"), where iOS has its page
  sheet.
- `modal_style` and `modal_dismiss_gesture_enabled` are iOS's and Android
  ignores them; `uri` and `open_in_browser` are Android's and iOS ignores
  them.
- The last rule is Android's own: files (`/rails/active_storage/...`) and
  the marketing site open in a Chrome Custom Tab over the app. A chat's files
  open in their viewer instead (`/chats/\d+/files/`), so a deck shows as its
  slides in the app, never as a download Chrome hands to another app; only a
  viewer's Download in a build without `file` still opens a tab. The viewer
  is the one page whose web view zooms with two fingers (`WebFragment`
  turns the web view's built-in zoom on for it and off for every other page).

Hotwire Native adds its own rules for `/recede_historical_location`,
`/resume_historical_location` and `/refresh_historical_location` after
these. A recede on the chat itself (deleting it from its menu, on a
server that recedes rather than sending Decisions) has nothing to go back
to: the app opens the organization's home instead, never New chat.

**New chat and New bot are pages (v4, 2026-11-20).** Carmine: "On the
phones, we should switch from sheets to pages for sure." New chat and New
bot's sentence are pages again, so the web's turn into the chat in place
works in the app as on the web: Send, and the question is on screen at
once (the server's docs/ui/chat.md, "Sending a question", and
docs/ui/native-apps.md, "Places"). Rename, Move to project and every other
`/new` and `/edit` stay modals. The app says so in its user agent,
`new-chat=page;` right after `build=N;` (section 1), and only for such
builds does the server draw what New chat, New bot and Decisions' All
clear turn into. So:

- **Where it's opened decides how it's presented.** What the sidebar
  opens (its New chat and New bot, the server's page or the native one) is
  a place: the navigator clears to its root and the page replaces it, as
  every sidebar row does, while the drawer slides shut (2026-11-20; before,
  the visit waited for the drawer to shut and be drawn, `AfterDrawerShut`,
  which now holds only the modals, Rename and Move to project, and New
  chat's keyboard, which the drawer's slide would put away). So do the ways in from outside:
  the icon's shortcuts, the widget, the tile, the assistant and a share,
  which start the navigator on New chat (or New bot) on a cold start, as
  builds before v3 did, and replace the root otherwise. Opened from a page
  (the bar's New chat over a chat or any place, Bots' New bot, a project's
  New chat, a bot's, Decisions'), the configuration's `default` pushes it
  with Back to that page. That push turns the page into New chat in place
  (2026-11-20, `OpensInPlace`): no animation in and no skeleton, the web
  view going on showing the page until New chat draws, with Back sliding as
  from any page. A replace with a remembered way back would do the same
  for the eye, but Back would then be the app's own bookkeeping rather than
  the stack's, so the push stays and only its motion goes. The path
  configuration doesn't change. The bar's New chat while New chat is already on
  top focuses its field instead of stacking another.
- **The turn into the chat replaces that screen** (`NewChatHandOff`). The
  app's composer sends through the page's hidden form, the page draws the
  question and Thinking in the shared web view at once, and the server's
  redirect to `/chats/<n>` arrives as a visit proposed with the form's
  response. When that visit goes to a chat without `from=`, and the screen
  on top shows New chat, New bot, the home or Decisions, the app replaces
  that screen in its place in the stack (Hotwire Native's `replace`): a
  page pushed over a chat stays pushed over it, with Back to that chat; a
  root stays the root. The chat's rule says `replace_root`, so for this
  one visit the app routes it with `replace` (it overrides the
  presentation Hotwire Native caches for that address while it routes, and
  puts it back). The new screen renders the response the visit carries,
  so there's no second request; it comes in without an animation, the
  screen it replaces goes at once, and neither a skeleton nor a loading
  view covers the web view, which already shows the question. Until the
  chat's page connects its own composer, the new screen draws the
  composer as the old one last showed it (answering, with Stop), with the
  keyboard where it was. The bar's title is the page's, "New chat" while
  it sends, then the chat's ("Chat #12" until it's titled). Back from the
  chat goes where New chat was opened from, never to an empty New chat.
- On a server older than v4 (no `android_v4.json`, so the bundled copy
  holds, and no in-place template), the same replace happens; New chat
  simply shows until the chat's page renders.
- A refusal, an error or no connection puts the page back as it was, and
  the composer bridge's `text` puts the words back in the app's field.
- **What's typed in New chat and not sent** is kept per organization when
  the page is left without sending (Back, a predictive back gesture,
  another place), and comes back in the next New chat there, a project's
  too. It survives a rotation, a theme change and the process ending (the
  activity's saved state; a force stop forgets it). Sending clears it, and
  signing out forgets every organization's. A share's words join it rather
  than replacing it. The web keeps no draft of a chat that doesn't exist
  yet, so this is the app's (`NewChatDrafts`).
- **The keyboard** comes up when the person opened New chat by a tap (the
  bar's New chat, the sidebar's) or a shortcut, the widget, the tile or the
  assistant, never on a page that arrived by itself (the home on a cold
  start, whose All clear carries a composer too).

v3 (1.2.11 and 1.2.12) showed New chat as a modal, as New bot was: the
navigator never started on it, and sending closed the modal and made the
chat the root. Those builds keep doing so with `android_v3.json`.

**How a modal looks.** Material 3's full-screen dialog (`ModalFrame`,
`ModalMotion`): on a phone, and on any window under 600dp wide or 480dp
tall (a phone on its side), the modal fills the window, edge to edge, with
a close icon at the start of its top app bar and the page's title beside
it. It slides up over a still screen in 500 ms with Material's emphasized
decelerate easing and slides away in 200 ms with emphasized accelerate.
Back, the close icon and a predictive back gesture close it; during the
gesture it shrinks to 90%, leans away from the edge, rounds its corners and
shows the screen it came over behind a scrim (a still picture taken as it
opened, since the navigator has no view of that screen any more), and
springs back if the gesture is let go. On a medium or expanded window (a
tablet, an unfolded phone) it's a dialog instead: centred, at most 560dp
wide and 840dp tall, with 28dp corners, over a scrim and the screen it came
over, fading and growing in; a tap on the scrim closes it. A step inside a
modal (sign-up after sign-in, a recipe's proposal from New chat) slides in
from the side with back to the step before; the same page again (New chat
with shared words) swaps in place. The keyboard pushes the composer up and
the page stays edge to edge, as on any screen. A modal the sidebar opens
(a row's Rename or Move to project) rises only once
the drawer has slid shut and the page has been drawn without it
(`AfterDrawerShut`; a place or a link chosen meanwhile, signing out, or
turning the drawer back open drops it), so the drawer slides away, then the modal rises over
the page alone: the pictures taken as a modal opens (the frame's backdrop,
and Hotwire Native's own, which the screen under it shows until its page
draws again after the modal closes) never have the drawer in them, which
used to vanish, come back behind the rising card, and come back again on
every close (Android 1.2.11 on a phone, 2026-11-20). New chat and New bot,
places from the sidebar from v4, wait for the drawer the same way.

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
| `file` | In a file's viewer: `connect` `{files}` adds a share icon to the top app bar and "Save to device" to the overflow menu; with more than one file (an Office file, then the PDF of its pages) each first asks which in a dialog named for the action, listing each `title` (what the file is: "PowerPoint", "PDF"). Both download the file with the web view's cookies, only from this app's server, into the cache's `shared/` folder: Share hands it to the share sheet through the FileProvider (`ACTION_SEND` with the file itself), Save writes it where the person picks (`ACTION_CREATE_DOCUMENT`). No reply. |
| `toast` | A snackbar; `alert` and `error` stay up longer. |
| `haptic` | `success` confirms, `warning` and `error` reject, `selection` ticks, `light` and `soft` tick lightly, `medium` and `rigid` tap, `heavy` presses long. Android's touch feedback setting applies. |
| `alert` | A Material dialog: `title`, `description` as its message, `dismiss` and `confirm` buttons, the confirm in red when `destructive`. |
| `theme` | Accepted: `light` or `dark` would set the whole app's appearance (remembered across launches), `null` follows the system. Not used yet, as on iOS. |
| `review-prompt` | Joe Masilotti's component: Google Play's in-app review. Not used yet. |
| `context-menu` | A popup menu at `rect` (CSS pixels, converted with the page's zoom), with icons; without a `rect`, a sheet titled with `title`. Items with `copy` text are copied to the clipboard by the app, with no reply; with `copyHtml` too (an answer as HTML, left out when the reader copies Markdown), the clip carries both (`ClipData.newHtmlText`), else plain text (Android 13 and later confirm a copy themselves; older versions get a "Copied" snackbar). |
| `auth-session` | An **Auth Tab** (Chrome's tab for sign-ins, which hands the `chatwithwork://` redirect straight back), or a Custom Tab where the browser has none, with `ephemeral` as ephemeral browsing where supported. The `url` must be on this app's server, and https outside development, or the reply is `{error: "invalid_url"}`; `{error: "unavailable"}` when no browser can open it; `{error: "canceled"}` when the person comes back without finishing. Otherwise `{url}`, the callback URL. |
| `notification-token` | `status` is `authorized`, `denied` or `not_determined` (Android has no provisional or ephemeral). `get` shows Android 13's notification permission prompt the first time, `openSettings` opens the app's notification settings. `offer` `{reason}` (`approval` or `answer`) shows, once per install and only while nobody has decided, the app's "Want a nudge when it's ready?" explanation worded for the reason, then Android's prompt on Continue (on Android 12 and earlier, Continue turns push on); it replies like `get`, at once when there's nothing to offer. When the person allows notifications (`offer`, or `get`), every `notification-token` on screen gets its latest `connect` answered again with the token. `token` is the FCM registration token, `platform` is `android`, `environment` is always `production`, `appId` is the package name, `needsKey` that the app has no notification key for the token yet (section 9), so the page registers at once. `key` `{key, token}` hands the app the key the registration answered. |

Material Symbols names go in `androidImage` (`data-bridge-android-image`):
`edit_square`, `keep`, `keep_off`, `link`, `edit`, `drive_file_move`,
`delete`, `content_copy`, `refresh`, `call_split`, `add`, the names the iOS
contract's page-by-page notes use. A `.fill` suffix (`keep.fill`) draws the
filled symbol. A new symbol for a `button` in the top app bar also gets a
vector in `BarSymbol`, so it matches the bar's own icons (Rounded, weight 300,
22dp). Until it has one it draws from the Outlined font, at the same size and
thickness but with square corners.

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

- **A chat** (`chats/show`): the app's root. The top app bar has the
  sidebar's menu button, the page's title, New chat, and the chat's own
  actions: Pin, Share link, Rename, Move to project and Delete in the
  overflow menu, Delete in red, behind the native confirm. **New chat**
  (`chats/new`) is a page, pushed over the page it's opened from or the
  place the sidebar opens (section 5). After the first message it turns
  into the new chat in place, and the chat's screen replaces it. A message's "More actions" button opens
  the context menu as a popup at the button. The approval and composer
  haptics vibrate as in the table above. The composer's keyboard-shortcut
  hint (`.kbd-hint`) stays in the markup: native.css hides it in the apps.
  The answer's menu also holds Good answer (`thumb_up`) and Bad answer
  (`thumb_down`), the `.fill` symbols and "Remove your rating" once
  pressed (2026-10-27): Bad answer opens its reasons under the answer,
  including "Offensive or harmful", the in-app report Play's AI-Generated
  Content policy asks for.
- **Beside the chat** (`chats/panels/show`, `/chats/<n>/beside`, server
  2026-11-05): the web's side panel as a page of its own, since the chat
  screen has no room beside it. The `.*` rule pushes it over the chat with
  back to it, a drill-down. It shows the panel's tabs (Details, Agents
  once the chat has some, Side chats), all drawn on the page and switched
  in place, never as a visit (server 2026-11-07: a visit per tab was a new
  screen each, and lagged); each tab keeps its `?tab=` address through
  `replaceState`, and the page restores the tab chosen last when Back
  brings it again. It's kept live by the chat's broadcasts, never for
  guests. The `composer` shows on Side chats only: the bridge sends
  `visible: false` on the other tabs. Its rows open
  their chats with `?from=chat`, pushed over it with back to it; New side
  chat is a form that goes to the new side chat. In sidebar builds the
  chat's bar `button` opens it (the app draws New chat itself). Bar
  buttons carry no badge, so the icon is the count of agents and side
  chats: `counter_N` to 9, then `filter_9_plus`, `.fill` while one works
  or waits for an approval, and `right_panel_open` at none, titled "Side
  chats and more, N" for TalkBack. The server replaces that button over
  the chat's stream as the count changes. Where the bar's one button is
  something else (a bot's Setup or Make bot, a try's), "Side chats and
  more" is the chat menu's first item instead. Nothing in the app changes
  for it.
- **The chat list** (`chats/index`) and **projects** (`projects/index`,
  from the sidebar's "All projects..."): places, replacing the root. The
  sidebar is the app's list, so the app opens neither by itself. A project
  from the list is pushed with back to it.
- **Decisions** (`decisions/index`, the sidebar's Decisions and its items):
  a place. Deciding never leaves it: approvals answer with Turbo Streams,
  and a form that leaves the board's frame redirects back to the page's
  own address, which the board keeps in Turbo's history, so Hotwire
  Native's adapter refreshes it in place (a morph) instead of proposing a
  screen. A decision's "Open chat" links to `/chats/<n>?from=decisions`,
  which the `from=` rule pushes over Decisions with back to it; any other
  link to a chat replaces the root.
- **Bots** (`bots/index`, from the sidebar): a place, New bot (`add`) in
  the top app bar. A bot, its triggers and its conversations push on top,
  with back to Bots; a bot opened from the sidebar's Bots view is a place
  itself.
- **Settings** (from the person at the sidebar's foot): a place. `/settings`
  is the list of sections and `/settings?tab=...` pushes over it with a
  back arrow (from the sidebar's Instructions, `/settings?tab=assistant` is
  the place itself). Its organization switcher still works (a link into another
  organization starts the app over there), but the sidebar's person menu
  is the usual way to switch.
- **Forms in modals** (every `new` and `edit`): the `form` component puts
  the submit in the top app bar. When the form recedes
  (`recede_or_redirect_to ..., notice: "Chat renamed."`), the app closes
  the modal, shows the notice as a snackbar, and loads the page under the
  modal again. (Hotwire Native Android would also go back from that page;
  the app stops it, to match iOS.) A create that redirects to the new
  record closes the modal and pushes it.
- **Deleting** a chat from its menu: the server answers sidebar builds with
  Decisions (`/decisions`, which replaces the root) and "Chat deleted." as a
  snackbar, so nothing of the deleted chat, its title included, stays; the
  sidebar page's own "chat on screen deleted" visit goes to Decisions too.
  A recede that still reaches the root (an older server) opens the
  organization's home. Deleting another chat leaves the screen as it is.
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
- **The icon's shortcuts** (a long press, `xml/shortcuts.xml`): New chat, New
  bot and Decisions, the same three names as on iOS. They open what the
  sidebar's own do: the new chat in its list, `bots.new_url` (both places,
  section 5) and `waiting.url` (the root, by the `/decisions` rule), or,
  before the sidebar's JSON has answered, `/<organization>/chats/new`,
  `/<organization>/bots/new` and `/<organization>/decisions` in the
  organization used last. Signed out, the app remembers the page and opens it
  once the person is in. The server's rules stand: a guest's New bot opens
  nothing, and leaves the page on screen. They need nothing new from the
  server.
- **The default digital assistant** (Android's Settings, Default apps, Digital
  assistant app): `MainActivity` handles `android.intent.action.ASSIST`, which
  is all that lists the app there. Invoking it (a long press of the power
  button, or the corner swipe, where the phone offers them) opens a new chat,
  as the tile does, with the app's composer focused and the keyboard up, and
  dictation in its usual place. It never records by itself, and has no wake
  word and no live voice. The app has no `VoiceInteractionService`, and never
  reads what Android attaches to the intent about the screen, so nothing about
  the screen reaches the app or the server. Words reach a model only from the
  composer, so the yes before the first question (section 12) stands as it
  is: until the yes there's no microphone, and Send brings up the dialog. A
  guest, who has no new chat, gets the Projects page the server sends them
  to. It needs nothing from the server. Android's own confirmation, when the
  person picks the app, says an assistant "will be able to access info"; that
  is its wording for every assistant, and the screen's "Use screen and app
  data" switch changes nothing here, since the app reads none of it.

Links the app handles itself:

- Links to other sites open in a Chrome Custom Tab, in the app's colors;
  `mailto:`, `tel:`, `sms:`, `geo:` and Play Store links go to their apps.
- Files and the marketing site open in a Custom Tab (the path
  configuration's last rule).
- A link to a place replaces the root screen; a link to any other page
  pushes it over the screen it's on, with back.
- A link into another organization starts the app over there: on that
  chat, or on its home with the page opened once it has (New chat and New
  bot as the place in its stead).
- `notice` and `alert` on `/recede_historical_location`,
  `/resume_historical_location` and `/refresh_historical_location` show as
  snackbars, `alert` for longer. Account-scoped controllers send them under
  the organization's prefix (`/482139075/recede_historical_location?notice=Chat+renamed.`),
  which counts the same (from 1.2.9; earlier builds missed the prefixed
  ones, so "Chat renamed." and Move to project's notice never showed).
  Turbo's
  `recede_or_redirect_to` passes `notice:` only; an `alert` needs
  `native_params: { alert: }`.
- Snackbars sit above the native composer where a screen has one, never
  over it (1.2.9).
- **Where the app opens (1.2.9).** `/<organization>/home` (`/home` before
  the app knows the organization), which the server sends on to Decisions
  (its All clear carries New chat's composer, suggestions and recipes when
  nothing waits). A server from before 2026-11-17, every self-hosted release
  up to v0.15.0 among them, answers it 404: the app then opens the
  organization's chat list, a place, as the builds that showed New chat as
  a modal did. Such a server serves no `android_v4.json` either, so the
  bundled configuration holds. A 404
  on `/<organization>/home` (or `/home`) does this whenever it comes, as
  long as that page is still the one on screen and someone is signed in
  (signed out, the next sign-in starts on the home again). A 401 there
  shows the welcome screen, as anywhere; other failures show the error
  screen.

**Chats readable offline (server, 2026-11-04).** The pages register the
server's service worker in the app too, and it keeps the chats someone
opens, encrypted, in the web view's storage (the Rails app's
docs/offline-chats.md). Without a connection a visit gets the worker's
copy, or its "isn't available offline" page, as in a browser, and the
composer locks through `bridge--composer`. Offline, a cold boot (a launch,
or a reload) first reports the page's request as failed
(`ERROR_HOST_LOOKUP` and the like) and commits the web view's error page,
and only then renders what the worker answered. From 1.2.1 the app
(`fragments/OfflineCopies.kt`) holds that error back until a page finishes:
a kept copy (`<meta name="offline-copy">`) drops it, so the cold boot goes
on and the copy shows with the bridge and Turbo; the worker's own offline
page (`main[data-offline]`), or nothing within 3 seconds, hands it to
Hotwire Native, which shows the app's offline screen as before. A launch
with no connection opens the chat last shown in the organization (only its
address is kept, forgotten on signing out) in place of New chat, which
can't load offline. The worker's requests carry the web view's own user
agent, so the server reads the app's from its `app_user_agent` cookie:
never clear it. The web view's storage, and with it the copies, is
excluded from backups and device transfers
(`xml/data_extraction_rules.xml`). The sidebar's JSON doesn't go through
the worker: offline it keeps what it last showed while the app runs, and
after a launch with no connection it has only New chat, Bots and Settings
until it's back online.

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
/*/bots  /*/bots/*  /*/decisions  /*/decisions/*
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

The answer carries the phone's notification key (`notification_key`, as in
the iOS contract), which the page hands the app with `notification-token`'s
`key` `{key, token}`. The app keeps it with the token it belongs to, in its
private preferences, and forgets it when it sees a sign-out; until it holds a
key for the current token its replies say `needsKey: true`, and the page
registers at once.

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
  ignored, so iOS can adopt `resolved` later.) `idea` (2026-11-26): a
  bot to try, sent at most once a day to someone who wasn't in the app
  that day, with `"priority": "normal"`, `title` "A bot to try", `body`
  the recipe's name and what it's worth (catalog words, the same for
  everyone, so no `notification_id`), `path` the bot's card
  (`/<account>/bots/proposals/new?recipe=...`), and `thread`
  `idea-<account>`. It shows on the answers channel, like `answered`.
- `path` (required): the page to open, starting with `/` on this server.
  Anything else is dropped.
- `thread`: one notification per chat; a newer one replaces it. Defaults
  to `path`.
- `title` and `body`: what the notification says. Without them the app
  shows "Chat with Work" and "A change is waiting for your approval." (or
  "A question is waiting for your answer.", "Your answer is ready.", "A
  chat couldn't finish its answer.", "A bot handed a conversation to your
  team.").
- `notification_id` (2026-10-26): an id the app can't read, sent to
  someone whose "Show the chat and its answer" is on, and for failures even
  when that setting is off (to validate that the failure still applies). Before showing the
  notification, the app fetches its words from the server with it, as in
  the iOS contract's "The words": `GET /native/notifications/<id>` with
  `Authorization: Bearer <notification key>`, answering
  `{title, subtitle?, body}`: the chat's title, what waits ("Waiting for
  your approval in Slack"), and the answer's start or what's to decide, at
  most 180 characters. The app shows `title` and `body`, `subtitle` as the
  notification's sub text, privately: its public version, which a lock
  screen set to hide sensitive content shows, says only the push's own
  `title` and `body`. It waits at most 5 seconds, and shows the push's own
  words when there's no key, the server answers anything but `200`, or
  nothing comes in time. So those words never pass through Google.
  An authorized failure that has since resolved answers `200` with
  `{suppressed: true, title, body}`; this app drops it without replacing a
  newer notification or Live Update. The title and body are neutral,
  content-free fallback words for older clients. Authentication failures
  and network failures keep the usual fallback behavior.
- High priority wakes the phone from Doze. Android lowers the priority of
  apps whose high-priority messages don't end in a notification, which is
  why `resolved` goes out as normal.

**Decisions digest (server, 2026-10-26).** Once 3 or more decisions wait for
someone, a new one's push becomes `decisions_waiting` instead of
`approval_waiting` or `input_requested`, at most every 30 minutes: title the
organization, body "4 decisions are waiting for you.", `path` the
organization's `/decisions`, `thread` `decisions-<account>`. The server keeps
sending Android each wait's own push until this app says it knows
`decisions_waiting`; iOS gets the digest now, and opens its `path` like any
other.

FCM answers `404` with `UNREGISTERED`, or `400` with `INVALID_ARGUMENT`
naming the token, for a token that's gone: destroy the registration. Retry
`429` and `5xx` with backoff, honoring `Retry-After`.

### Live Updates

When the person leaves the app while the chat on screen is answering (its
composer says `working`), the app posts an ongoing notification for that
chat: the Open Road card, as on iOS (`docs/push-notifications.md`), with the
chat's title as its headline (below), the current step under it, the organization's name and an elapsed timer, never the chat's content. No push carries the title. It uses the chat's notification
slot (tag `chat-<organization>-<number>`, the `thread` the server sends), so
the push for its end (`answered`, `failed`) takes its place.
It's taken away when the person comes back, and after 30 minutes if
nothing ends it. It only shows for someone who turned push on.

**Its steps (2026-10-26).** Leaving the chat, the app also asks the server
to follow it, with the web view's cookies and the composer's `csrfToken`,
as the iOS app registers a Live Activity:

```
POST /:account/chats/:number/live_activity
{ "live_activity": { "token": "<FCM token>", "app_id": "com.chatwithwork.app", "environment": "production", "platform": "android" } }
```

While the answer runs, the server then sends FCM data of kind `progress`
for that chat, at most every 3 seconds, `normal` priority, collapsed per
chat (`collapse_key` `progress-<thread>`) and kept two minutes:

```json
{ "kind": "progress", "path": "/482139075/chats/42", "thread": "chat-482139075-42",
  "phase": "answering", "message": "Searching Todoist", "step": "search", "steps": "3",
  "recent_steps": "[\"read\",\"search\"]",
  "sent_at": "1793039262987",
  "run": "6f1c0a4e9d2b4c7183a0e5d9b7c21f46", "revision": "12", "started_at": "1793039201" }
```

**Runs (2026-10-31).** `run`, `revision` and `started_at` are optional and
come on every `progress` message, and the composer's state carries them as
`run`, `revision` and `startedAt` (numbers), and `mark` (below), while it says
`working` or `waiting`, null otherwise. A run is one turn: a question until it's answered, through
steering and waits for the person. A new question or a retry is a new run.
`run` is opaque and content-free, `revision` orders everything within it,
and `started_at` (unix seconds) never changes within it. `follow` sends
`run` as `live_activity.run`, and a `409` means that run is over: the app
takes its card away, since nothing would end it. A `201` answers with
`id`, `run`, `revision` and `started_at`.

Only `answered`, `failed` and `stopped` end a run. `approval_waiting` and
`input_requested` are `progress` messages at high priority with
`waiting_since`: the card stays ongoing, with the push for the wait beside it
(tag `<thread>-wait`, which `resolved` clears), and the next step takes over.
The server repeats the state every 4 minutes with `heard_at`, also while
waiting, which renews the card's 30-minute timeout. Also optional, and
content-free, for the card's road (below): `service`, `services` (JSON string),
`trail`, `pace`, `agents`, `heard_at`, and `paper`.

The app keeps one record per thread (`LiveRuns`, kept apart from the card):
the run, its start, the newest revision heard and whether it was dismissed.
A message that names a run is taken only if it's that run's and a newer
revision, or at the same revision a newer `sent_at` with no end there yet, so
a late end for answer A can't touch B's card, even before B's first step. A
person clearing a working card (`setDeleteIntent`) suppresses that run: it
isn't shown or followed again, and a later run is. The app taking its own card
away on return sends no delete intent, and a timeout (which does) is told
apart by the card's age. Sign-out ends every card, clears the records and
stops requests already under way. A server that sends no `run` gets
the `sent_at` rules below, and a card from before runs adopts the first run it
hears.

`message` is the step in the words of the chat's activity line ("Searching
Todoist", "Reading 3 files in Drive", "Running code", "Writing the answer",
"Answering" between steps), `step` its kind (`search`, `list`, `read`, `use`,
`plan`, `run`, `ask`, `write`, `answer`), `steps` how many it took, never a
query, file name, title or text. The app shows `message` under the chat's
title, or as the headline when it doesn't know the title, with the
organization in the header. When the
answer ends, the same kind comes with `phase` `answered`,
`failed` or `stopped` and generic
words for it, never collapsed and kept a day, and the Live Update becomes a
quiet notification saying so, until the push for that end (if the person
gets one) takes its place. `sent_at` is the server's clock in milliseconds:
the app drops a message older than the latest one received for that chat,
including after a follow ends or is cancelled. This server timestamp is
kept across following the same chat again and cleared on sign-out, so an
old end cannot replace new progress. A failed end can also carry
`notification_id`: the app validates it using the same endpoint before
ending the card, preserving current progress if the failure is suppressed.

Optional `mark` says who's at work on the answer: `chat`, a person's chat, which
draws Chat with Work's logo (at the road's head below Android 16) and its
silhouette in the status bar (`LivePresentation.isChat`); `bot`, a bot's chat or run, or nothing from an
older server, which draws the bot and its faces.

Optional `paper` (2026-11-15), in a bot's chat or run, is the bot's colour by
name from `Bot::Colours::PALETTE` (`chalk`, `peach`, `sand`, `sage`, `mist`,
`lilac`, `rose`, `stone`), the paper of its mark: Android 16's template draws
the bot on it, and the app drops any other value. The app remembers the
last one per chat, so the card it posts on leaving starts on the right
paper, and a message without it keeps that paper: chalk, the white sticker,
only while none was ever heard. The server sends it on every message of a
bot's run, so a changed colour (chalk included) replaces the remembered one.

**The chat's title (2026-11-18).** Every `progress` message carries
optional `title_ref`, an id the app can't read. As the message arrives, the
app asks for the chat's title with it and the phone's notification key, as
it fetches a notification's words:

```
GET /native/live_titles/<title_ref>
Authorization: Bearer <notification key>

{ "title": "Q3 renewal plan", "bot": "Morning plan", "titled": true }
```

`title` is the chat's title on one line, at most 120 characters; `bot` the
bot's name in a bot's chat or run; `titled` false while the chat has none yet.
An empty object means the person turned off Settings ▸ Notifications'
"Show the chat and its answer", and the card shows the step's words as its headline,
as before. A `404` (not this phone's, the follow ended, an id too old or
tampered with) or no answer within 3 seconds changes nothing. The headline is
the title, the bot's name and then the title (` · `, which the system cuts
short where there's no room), the bot's name alone, or "New chat"; the line
under it is the step's words and the steps ("Searching Drive · 3 steps", or
"Approve a change in Slack · 4 steps" while it waits). The app keeps the
answer per chat (`LiveTitles`), shows it on the next card for that chat at
once, and asks again once per run and while `titled` is false, so a new
chat's summarized title replaces "New chat" with the next message. Sign-out
forgets them. The status bar chip keeps the step's word (Search, Review): a
title doesn't fit its seven letters. Below Android 16 the drawn card puts
the title on its top line, above the step's words, in place of the
organization's name, which stays in the header.

Optional `recent_steps`, which older app versions show, is ignored now: the
road says the same thing.

The card is Open Road. `service` is the built-in key of the step now, `mcp` or
`computer`, and shows as a logo only if the app bundles it (`LiveServices`);
it's never a name a person gave. `services` is the same keys, oldest first,
and the newest four are the road's stops. `trail` is the last 48 steps, one
letter each (`s l r u p x a w`, in the order of `step`'s kinds with `x` for
`run`), upper case when one failed or was denied; the app drops anything else.
`pace` is one digit per `trail` letter, how long that step took: 1 under 2
seconds, 2 under 10, 3 under 60, 4 a minute or more; without exactly that, every
tick is the same height. `agents`, from 2, forks the road into two lanes or three
bots and a count. Below Android 16 the app draws the iOS card as a custom
notification, and from 16 the road is the template's segments
(`docs/push-notifications.md`): one per step, as long as its `pace`, with the
bot two thirds along and the open road ahead; upper-case letters show faded or grey.

An `end` message carries only `phase`, `message`, `run`, `revision`,
`started_at` and `heard_at`: the card keeps its last steps, trail and services
in its extras, so its road stays. `heard_at` minus `started_at` is the fixed
time a finished or stopped card shows. Without `service`, `trail` or `services`
(an older server), the road is counted from `steps`, with no stops.

The current action is the headline. Organization and the native timer remain
secondary. The timer counts from the run's `started_at`, the same across
leaving and returning and across process restarts; an older server sends none,
and it follows time in the background.
A blank or missing
message uses a localized fallback for `step`; `answer` means generic
answering/thinking, while `write` means streaming the answer. The active
card itself opens the chat. Ended states retain the last real step count
when the end payload omits it, stop the timer, and use Review or Reply when
waiting and Open when failed; finished and stopped cards have no button.
Every action opens the
authenticated chat; none approves a change from the notification. There is
no percentage or ETA because the amount of work is not known. From Android 16 it's
Android's own [Live Update](https://developer.android.com/develop/ui/views/notifications/live-update)
(`ProgressStyle`, promoted, with a status bar chip); below, a custom
RemoteViews card. The payload is the same for both.

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
  `ANDROID_UA` (section 1), and a test that `/configurations/android_v4.json`
  is the Android repository's file and `/.well-known/assetlinks.json`
  answers JSON without a redirect.

## 11. The sidebar

**The sidebar page (2026-11-17).** The server draws the sidebar, so a change
to it needs no app release: builds from `server-sidebar` keep the server's
own sidebar, `GET /<organization>/sidebar` (the server's `SidebarsController`),
loaded in a web view of their own in the drawer (`SidebarWebView`), and show
it in place of the native sidebar once it has drawn. Everything below this
section, the JSON and the native sidebar, stays as the fallback for a server
without the page, and for older builds, which the server keeps serving.

- **Which page.** Every signed-in page's hidden `bridge--sidebar` element
  sends `connect` `{page, belongsTo}`: `page` is this organization's sidebar
  page (`"/482139075/sidebar"`), `belongsTo` the path the page on screen
  belongs under (its `data-sidebar-belongs-to`: a bot's chat raises its bot's
  row), or null. An older server sends `{}`: no `page`, so the native
  sidebar. The app loads the page once and again only when `page` changes
  (another organization), remembers it per organization (`SessionMemory`) to
  load it at launch before the first page connects, and forgets it when a
  `connect` comes without one or the page answers 404.
- **Its web view.** A plain `WebView`, never Hotwire's, kept for the
  activity's life over the native sidebar, inside the system bars and cutout,
  on the sunken canvas, JavaScript and DOM storage on, no zoom, one window.
  Cookies are the app's own `CookieManager`, so it's signed in as the pages
  are. Its user agent is the app's tokens and the WebView's own,
  `Chat with Work; platform=android; version=...; build=...; Mozilla/5.0 ...`,
  with no `Hotwire Native`, `Turbo Native` or `bridge-components`: the server
  draws it for a phone, and none of the page's bridge controllers load there,
  where no bridge answers them. The page never writes the session, shows only
  its own requests' flashes, and never becomes the kept `app_user_agent`.
- **Page to app**, through `window.ChatWithWorkSidebar.postMessage(json)`
  (`addJavascriptInterface`), taken only while the web view shows this
  server's page:

  | Event | Data | The app |
  |---|---|---|
  | `ready` | | shows the page in place of the native sidebar, and answers with `current` |
  | `visit` | `url` (absolute) | this server: opens it as a place, as a native row does (Places), and closes the drawer (sliding shut first and then opening it, when it's a modal such as New bot, Rename or Move to project: "How a modal looks"); the sign-in form (signing out from the person's menu) shows the welcome screen; another site (Help, which the page sends as an absolute https URL): a Chrome Custom Tab, never the drawer or the navigator. A link of this server the page marks `target="_blank"` is a page like any other: the navigator |
  | `close` | | closes the drawer (the page's header button) |
  | `confirm` | `id`, `title`, `description`, `confirm`, `dismiss`, `destructive` | a Material alert worded as `bridge--confirm`'s, the confirm button in the error colour when `destructive`, then `answer` |

- **App to page**, on `window.chatWithWorkSidebar` with `evaluateJavascript`:
  `current(url, belongsTo)` after every visit on the navigator, each
  `connect` and `ready`, which marks the row of the page on screen (the
  page keeps it on its column as `data-native-current-url`, so a row drawn
  again by a stream is marked too, with nothing more from the app);
  `refresh()` back from the background (`onStart` after `onStop`), since the
  page's streams may have missed something; `answer(id, confirmed)`; and
  `closed()` (2026-11-20) whenever the drawer closes, however it closes (a
  back gesture, a swipe, a tap on the scrim, a visit), so the page ends
  picking rows and closes its menus. A page from before it has no `closed`,
  and the call does nothing there.
- **Navigation.** Nothing but the page loads in the drawer:
  `shouldOverrideUrlLoading` lets through only the page's own path, hands
  any other page of this server to the navigator (`visit`) and other sites to
  the browser. A redirect to the sign-in form on the page's own load (no
  session) is dropped: the navigator learns about the session on its own.
  Turbo's visits inside the page never reach the WebView: the page turns
  each into `visit`. Requests that answer in place (pins, sections, the
  filter, Delete in a row's menu) stay in the page, and deleting the chat on
  screen sends `visit` to New chat.
- **When it can't load.** A 404 means a server without the page: the native
  sidebar, nothing remembered, and that page isn't asked for again while
  the app runs, even when a `connect` names it. Any other failure, on the
  first load or on a reload once it drew (`refresh` coming back from the
  background), shows the native sidebar (fetched again if the drawer is
  open) and tries the page again as the drawer next opens or the next
  `connect` names it. Offline, the page has an offline copy in the service
  worker (`docs/offline-chats.md` on the server), which the WebView may
  draw after its own `onReceivedError`: `ready` then shows it. `ready`
  counts only from the page the app last asked for, in that load: one from
  a page since replaced (another organization) or dropped (signed out) is
  ignored. A renderer that dies gets a new web view, and the page again as
  the drawer next opens.
- **While it shows,** the app fetches no JSON (on opening, polling, or
  `changed`), and the app's own New chat (the top bar, a chat deleted on
  screen, the icon's shortcuts, the widget and tile, shared text) opens
  `/<organization>/chats/new`, never the list the native sidebar last
  chose. That holds from the moment a `connect` names the page, before it
  draws.
- **Phone proportions** are the page's (the server's `native_sidebar.css`):
  48dp rows and buttons, names and the search at 16px.
- **What it shows (server, 2026-11-18).** The web sidebar's own partials:
  New bot and New chat, Decisions with how many wait, the search, Pinned
  (chats and bots), Recent filtered All, Chats or Bots with the person's
  sections, each row's three-dot menu (Pin, Rename, Move to section, Move
  to project, Delete behind the app's alert; a bot's own), Projects, and the
  person's menu with the usage ring, whose panel shows what was used and
  sells nothing. No credits row and no "Get the apps".
- **Known limitations.** While the page shows, every page in the navigator
  still subscribes to the person's chat-list streams for `changed`, which
  the app then ignores, so the app holds two cable connections (the
  navigator's and the drawer's). `belongsTo` is read when the bridge
  connects, so after a morph (which doesn't connect again), or for the
  moment between a visit and the new page's `connect`, the row marked can be
  the previous page's. `refresh()` reloads the whole page (only online),
  where a Turbo refresh would be lighter. The bridge interface is on every
  frame of the page's web view; the app takes messages only while its top
  frame is this server's page.
- **Retiring the JSON.** The server keeps `/native/sidebar` until every
  store build in use has the sidebar page; this app keeps the native sidebar
  until self-hosted servers older than the page have updated, then drops
  `SidebarDrawer`, `SidebarClient`, `Sidebar` and their layouts.

**Same head in both views (2026-11-01).** Chats and Bots open the same way,
as the web's (`.sidebar__head`, `.sidebar__search`), so switching moves
nothing: Decisions, the Chats | Bots switch, the head (one row: the list
selector and a square `+`, starting a chat or opening `new_url`, raised on
that page, and no `+` for a guest), and the search. Then Pinned and the
chats by latest question in one list, with no day headings, or the bots.
There are no New chat or Instructions rows; `links.instructions` stays for
older builds, and Instructions stays in Settings. New, all optional:

- `bots.list`, the list the web's Bots view last chose, and `bots.lists`,
  the same choices as `lists`, each also with `new_url`. `bots.new_url` is
  the chosen list's.
- Each `BOT`'s `description`, which the Bots search looks in with the name,
  every word, ignoring case, and `lists`, the keys of the lists holding it.
  `items` are always every bot the person reads, so the app narrows them at
  once, and remembers its choice per organization, starting from `bots.list`.
- A list's `url`, a project's page, for "Open <project>" in the selector's
  menu (My and All, "Projects you're on", then Open, All projects… and New
  project), and `links.chats`, the chosen list's page, the last row once the
  list holds everything ("See all, by date", or "Open <project>"), only
  while it has a chat and not while searching.

A server without `bots.lists` keeps the card and New bot in the Bots view
(described below), with no search there.

**New chat and New bot's card, and chats that make a bot (2026-11-06).** As on
the web (`.sidebar__new`), the head's `+` is a quiet card beside the list
selector, exactly as tall, on the same surface with the same hairline, never
solid, holding what it makes with a small + disc of ink on its corner:
`ic_forum` (Rounded, weight 300, like the bar's vectors, the web's
`chats-circle`) for New chat, the bot mark for New bot, holding still. On its
own page its edge is stronger. The top bar's New chat keeps `edit_square`,
Android's compose button. Each `CHAT` has `builds_bot`, that it's a chat
making a bot: its row wears the bot mark before its title, or in a pin's
icon's place, and says "makes a bot" to screen readers. A server without the
key means false. Tries with such a chat, or a bot made from one, aren't in
`chats` or `sections` (the server's `Chat.listed`), so the app does nothing for
them; a pin stays until its person takes it off, as on the web.

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
  "bots": {"count": 0, "attention": false, "next_run": "08:30",
           "url": "/482139075/bots", "new_url": "/482139075/bots/new", "items": [BOT]},
  "pins": [CHAT],
  "sections": [SECTION],
  "chats": [CHAT],
  "next": "/482139075/native/sidebar?list=recent&before=41&asked=2026-10-24T09:00:00.000000Z"
}
CHAT = {"number": 42, "title": "Launch date in #launch", "url": "/482139075/chats/42", "state": "idle",
        "waiting": false, "builds_bot": false, "project": "Website relaunch", "asked_at": "2026-10-25T08:12:00Z", "group": "Today",
        "actions": [ACTION]}
SECTION = {"id": 7, "name": "Customers", "collapsed": false, "chats": [CHAT],
           "edit_url": "/482139075/sidebar/sections/7/edit", "collapse_url": "/482139075/sidebar/sections/7/collapse",
           "position_url": "/482139075/sidebar/sections/7/position"}
BOT = {"id": 5, "name": "Front desk", "description": "Answers refunds", "url": "/482139075/bots/5", "lists": ["all", "3"],
       "group": "needs_you", "project": "Website relaunch",
       "state": "waiting", "state_label": "Waiting for you", "tone": "attention", "waiting": 1, "working": false,
       "next_run": null, "line": "Reply to Ana about her refund", "line_tone": "attention", "line_when": "12 min",
       "colour": "#F7C8B2", "face": "waiting"}
ACTION = {"key": "delete", "title": "Delete", "method": "delete", "url": "/482139075/chats/42",
          "iosImage": "trash", "androidImage": "delete", "confirm": "Delete this chat?", "destructive": true}
```

Links that don't apply are `null` (a guest's `bots`, `projects` and
`new_project`), and `bots` is `null` for guests. A list's `kind` is
`recent`, `all` or `project`; a waiting item's `approval`, `question` or
`bot`; a chat's `state` `idle`, `processing` or `error`. Every URL is a
path on this server. The app ignores keys it doesn't know.

**Decisions (server, 2026-10-26).** `waiting` now lists decisions, not chats:
everything waiting for the person across their chats, agents, bots' runs
and the bots they handle (the server's `Decision`, `docs/decisions.md`).
`waiting.count` is what waits now, `waiting.snoozed` how many they put off,
and `waiting.url` the web Decisions page (`/:account/decisions`), also in
`links.decisions`. Each of the first 50 `items` keeps `title` (its chat's),
`kind` and `project` as before, and its `url` now opens the Decisions page
on it (`/:account/decisions?decision=<key>`), one decision at a time with its
card, "Why you" and the context below it; `chat_url` is its chat. New keys:
`key`, `decision` (`approval`, `question` (a server's), `ask` (the
assistant's own question with options, `ask_decision`), `draft`, `editor`,
`handoff`), `summary` (one line saying what's to decide) and `since`.
Opening `waiting.url` or an item's `url` in the web view is all a first
version needs: the page decides through the chat's own cards, swiping left
or right on it moves between decisions, and the sidebar's `changed` follows
its count.

**Who asks (server, 2026-10-27).** Each of `waiting.items` also carries
`asker`, `{"kind": "bot", "name": "Front desk", "project": "Website
relaunch"}` (`kind` `bot`, `agent` or `chat`, `project` null outside one),
the server's `Decision#asker`. The sheet of `waiting.items` (shown only from
a server without `waiting.url`) leads each with `asker.name`, then `summary`
and the project, as the web's decision row does. Without `asker` it's as
before.

**Question or Permission (server, 2026-11-04).** Each of `waiting.items`
also carries `family`, `question` or `permission` (the server's
`Decision#family`), and `label`, the web tag's words for it ("Question",
"Permission · runs a command", "Permission · deletes",
`DecisionsHelper#decision_kind_label`). The sheet of `waiting.items` shows
`label` under each item's summary, as its subtitle, as the web's decision row
and queue show their tag; without `label` it spells the family ("Question",
"Permission"), and without either the item is as before. Like the rest of the
sheet, it shows only from a server without `waiting.url`: with one, Decisions
opens the web page, which draws the tag itself. Never branch on `label`'s
words, which may be translated: `family` is the stable key. The iOS app draws
the same under each row of its Decisions list.

**Chats | Bots (server, 2026-10-27).** `bots` also carries `url` (the Bots
page), `new_url` (New bot) and `items`, the person's bots and the shared ones
they read, as the web sidebar's Bots view lists them (`bots/_sidebar_list`),
those that need them first. In `BOT`, `group` is `needs_you` or `quiet`,
already in order; `project` a shared bot's, null for a personal one; `state`
one of `waiting`, `failed`, `working`, `paused`, `next`, `listening` and
`idle` (`Bot::Overview#state`), with the web's words in `state_label` and its
`tone` (`attention`, `negative`, `live`, `positive`, or null); `waiting` how
many decisions wait for the person in it; `working` whether one of its chats
is answering now; `next_run` its next timed run, if any.

With `items`, the sidebar draws a segmented Chats | Bots switch right
under Decisions, in place of the Bots row (the rest of this paragraph is
what a server without `bots.lists` gets, see "Same head in both views"). Its Bots half shows an attention
dot, never a number, while `bots.attention`: Decisions carries the one
count.
Chats shows the sidebar as before. Bots keeps the same skeleton: a card in
the list switcher's place ("Bots in <organization>", how many bots, and how
many are working or waiting for the person), opening `url`; New bot where New
chat sits, opening `new_url`; then the bots, under "Needs you" and "Quiet"
only when both have some. Each row is the bot mark (holding still while
`paused`, with a live dot while `working`), the name, a shared bot's project,
and at its end `waiting` with an attention dot when it's more than 0, else
`state_label` with a dot in its `tone` (a neutral one without). A tap opens
`url` like any sidebar link, and the row shows as current on the bot's pages.
With no bots it says "Your bots, and the ones shared with you, show up here."
and offers "Start from a recipe" (`new_url`). The search shows only with the
chats, under the switch and the list switcher. The app remembers the chosen half per install. Guests get no `bots`,
so no switch; a server that sends no `items` keeps the Bots row. The sidebar
keeps checking every 5 seconds while it's open and a bot is `working`, as it
does for a chat.

**The chosen half, tapped again (2026-11-07).** A tap on the half of the
switch that's already chosen opens its page, as the web's switch does: Chats
the chats page on the chosen list (`/:account/chats?list=<list.key>`), Bots
the Bots page on the list the Bots view shows (`bots.url`, else `links.bots`,
with `?list=<key>`, this install's choice, else the server's). Both pages
take `list=`, since the app keeps its own choices, and both are places, so
they replace the root. A tap on the other half only switches, as before.

**Bots' colours and faces (server, 2026-11-04).** Each `BOT` also carries
the line the web's row shows under its name (`bots/_sidebar_bot`,
`BotsHelper#bot_sidebar_line`): `line`, its `line_tone` (`attention`,
`negative`, `live`, or null), and `line_when`, what the web shows at the end
of the name ("now", "12 min", "08:00", "Fri"); and its mark as the web draws
it (the Rails app's docs/ui/native-apps.md, "Bots' colours and faces"):
`colour`, the paper of its mark as `#RRGGBB`, and `face`, `waiting`,
`thinking`, `flat`, `happy`, or null for the blinking mark. From 1.2.1 each
row is two lines beside a 28 dp mark: a sticker filled with `colour`, its
outline and eyes black in both themes, with `face`'s eyes (thinking swaps
its two frames every 1.6 seconds; the others hold still) or else the blink,
held still while `paused`, with the live dot while `working`. The first
line is the name, a shared bot's project icon and `line_when` in mono at
its end; the second is `line` in its tone's ink, with `waiting` at its end
as a count on the attention wash when it's more than 0. A server without
these keys gets the same two lines from `state_label`, `tone` and, for a
`next` state, `next_run`, with the mark in the text colour.

**Long press (2026-10-26).** Each chat's `actions` are what the web row's
menu offers for it, in order: `rename`, `pin` or `unpin`, `section`,
`project` and `delete`, the creator's own only where the web's are;
`confirm` and `destructive` only where they apply. A long press on a chat's
row opens them as a popup menu at the row (`androidImage` icons,
`destructive` in red). A `get` opens its `url` like any link (Rename and
the Move pages are modals); any other goes to the page showing as a reply
to its `sidebar` component's latest `connect`, `{action: "perform", url,
method, confirm, destructive}`, which the page sends as the web's link
would, behind the native alert where there's a `confirm`. Without
`actions` (an older server) a long press does nothing.

**Sections (2026-10-26).** `sections` are the person's own sidebar
sections (the server's `ChatSection`), in their order, the same in every
list, each with this list's chats in it, newest question first, all of
them (not paged); the list's `chats` (and `all=1`'s) are then only those
in no section. Each draws as a heading after Pinned (a caret, its name and
how many chats it holds) with its chats below it unless `collapsed`. A tap
on a heading opens or closes it at once and sends `collapse_url` to the
page showing like a chat's action (`post` closes it, `delete` opens it),
so every device shows it the same; a long press offers Rename (`edit_url`,
a modal). A search lists the matches in sections too. Without `sections`
(an older server) the list is as before.

What the app draws from it, top to bottom: Decisions (below), the Chats |
Bots switch (from servers that send `bots.items`), the head (the list
selector, `list` with a chooser of `lists`, and the square `+`, starting from
`links.new_chat`, also where the bar's New chat and a cold start go), the
search (chat titles, or a bot's name and description, every word, filtered
on the phone), from a server without `bots.items` the Bots row (`bots`' count
with an attention dot, else the next run), then Pinned, the person's own
sections (`sections`, below), and the chats by latest question (under one
"Other chats" heading when there are sections, as on the web), a live dot on
those answering and an attention dot on those waiting, loading `next` near
the end, or the bots. `group` is no longer drawn, nor New chat and
Instructions as rows.

**Decisions (2026-10-28).** As on the web (`decisions/_sidebar_button`),
Decisions is a button of its own at the top of the sidebar, just before
the Chats | Bots switch, so it shows in both halves, while searching, and
for guests, who have no switch. It's outlined like the list switcher, since
it's the one thing in the sidebar that asks for the person: the list-checks
icon, "Decisions" spelled out, and at its end `waiting.count` as a small
badge on the attention wash (the outline then takes the attention edge),
or the happy bot and a quiet "All clear" at 0. It opens the Decisions page (`waiting.url`, else
`links.decisions`), or, from a server with neither, a sheet of its items,
and shows as selected while that page is showing, on any decision. It
reads the same JSON, so no key changes. Earlier builds drew it as a pill
in the chat's top bar, before that as a row under New chat, and before
that as "Waiting for you".
The person at the foot opens a sheet of `organizations` (each `url`
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
| `connect` | `{page?, belongsTo?}` | shows the sidebar page `page` names (above, "The sidebar page"), or the native sidebar without one; keeps the component, to reply with a chat's action from a long press: `{action: "perform", url, method, confirm?, destructive?}` |
| `changed` | `{}` | fetches the JSON again (debounced, 500 ms) if the native sidebar is open, else on its next opening; nothing while the sidebar page shows |

No replies.

### Pages loaded ahead

**From 2026-11-20.** The server's `helpers/preload.js` has the main web view
load Decisions, New chat and New bot ahead once its page is idle, only on a
network that costs nothing. The app tells it what the network costs
(`NetworkCost`):

```js
window.chatWithWorkNetwork = { metered, constrained }
```

`metered` is a default network without `NET_CAPABILITY_NOT_METERED` (mobile
data, a hotspot), `constrained` is Data Saver on for this app
(`ConnectivityManager.restrictBackgroundStatus` is
`RESTRICT_BACKGROUND_STATUS_ENABLED`). With no network the app sets nothing,
and the page falls back to `navigator.connection`. The value is set at
document start in every page of the app's web views (a document-start
script on this server's origin, replaced on each change; where the WebView
lacks that, when the cold boot's page finishes), and in the pages already
open whenever it changes (and taken away there when the network is lost, so
the page falls back to `navigator.connection`); changing to a network that costs nothing also
calls `window.chatWithWork?.preload?.()`. As the drawer starts to open, the
app calls `preload()` in the main web view too, so a person who opens it
soon after a page appeared still finds the places ready. Nothing of it goes
to the server.

### Decisions in the bar

**From 2026-11-20.** Builds that register `decisions` show Decisions as the
top app bar's last button on every page but Decisions, where it stays New
chat. The server renders the component only for builds that list it: a
hidden element on every signed-in account page (`application/_native_decisions`,
`bridge/decisions_controller.js`, `data-bridge-native-action="decisions"`).

| Event | Direction | Data |
|---|---|---|
| `connect` | web to app | `{waiting, here, url}` |
| `count` | web to app | `{waiting}`, whenever it changes |

`waiting` is how many decisions wait for this person in this organization,
snoozed ones left out, as the sidebar's Decisions row says, live on their
`[user, :sidebar]` stream; the page loads it again as it comes back from the
background. `here` is whether this page is Decisions; `url` is Decisions,
absolute. Nothing goes back to the page.

- **The button** (`DecisionsComponent`, its own menu group, `ORDER_DECISIONS`,
  80, New chat's place): Phosphor's regular signpost (`ic_bar_decisions`) at
  the bar's 22dp in its ink. While some wait it takes `lw_attention_ink`,
  with the count at its top right on `lw_attention_wash` ringed in
  `lw_attention_edge`, as the sidebar's count: up to 9, then "9+", nothing at
  zero. TalkBack and the tooltip say "Decisions" or "Decisions, 3 waiting".
  The shell's New chat hides while it shows, and comes back on Decisions
  (`here`).
- **Other trailing buttons** (a chat's side panel, a bot's Setup, a form's
  Save, share) keep their places before it: Decisions is always the last
  button on the bar, with only the overflow menu after it.
- **The tap** goes back to Decisions when it's the place under the screen
  (every screen over it closes, `clearAll`), and otherwise makes Decisions
  the place, as the sidebar opens it. There's never a second Decisions on
  the stack.
- A server that doesn't render it, and builds without it, keep New chat.

## 12. The composer

**The page's own composer (2026-11-20).** Builds made with
`AppConfig.NATIVE_COMPOSER = false` (from 1.2.13) don't register `composer`,
so it's missing from the user agent's `bridge-components` and from
`data-bridge-components`, and native.css leaves the web composer showing. The
page's composer then does everything itself: typing, Send (the same form, so
New chat still turns into its chat in place and the app keeps the screen for
it), suggestions, the model, web search, the slash menu, files, and dictation
through `getUserMedia` (the microphone allowed for this server's pages only,
`AppWebChromeClient`; Hotwire Native asks for `RECORD_AUDIO` the first time).
The rest of this section after "The web composer" describes the native
composer, which turning the constant back on restores.

### The web composer

Those builds register `web-composer` instead (never both: `BridgeComponents`
registers it exactly when it leaves `composer` out). Its Stimulus controller,
the server's `bridge/web_composer_controller.js`, sits on `#new_message`
beside `bridge--composer`, and tells the app what it needs from a composer it
doesn't draw:

| Event | Direction | Data |
|---|---|---|
| `connect`, then `state` whenever it changes | web to app | `{working, waiting, run, revision, startedAt, mark, csrfToken, acceptsFiles, dictates, menu, confirmsAttach}` |
| `attached`, once per attach, after its uploads settle | web to app | `{id, taken, files: [{name, taken, reason}]}` |
| reply to `connect` | app to web | `{action: "attach", id?, files: [{name, type, data}]}` (data base64, `ComposerFile`; `id` only for a page with `confirmsAttach`), `{action: "dictate"}`, `{action: "closeMenus"}` |

`working`, `waiting`, `run`, `revision`, `startedAt` (unix seconds) and `mark`
are `bridge--composer`'s own. `acceptsFiles` is false while the composer is
locked; `dictates` is whether its microphone is offered now (an install that
transcribes, and the person's yes to models); `menu` is whether one of its
menus is open (+, the model picker, the mode picker, the slash menu). A chat draws its composer again
as an answer starts and ends, and New chat's composer connects again as it
turns into its chat (`address:replaced`): each connects again, and the app
replies to the latest `connect`.

- **Live Updates.** Each screen keeps the latest state (`WebFragment.webComposer`,
  forgotten as another visit starts on it). Leaving a chat while it's
  `working` starts its Live Update from that state, as from the native
  composer's: the chat's URL, `liveRun` from `run`, `revision` and
  `startedAt`, `mark`, and `csrfToken` for following it (section 9, "Live
  Updates"), and a launch soon after reopens that chat.
- **Files shared into a new chat** (section 7, "Sharing into the app") wait
  for New chat's composer: on its `connect` with `acceptsFiles`, or a later
  `state` with it once a locked composer unlocks, the app replies `attach`
  with them, which the page uploads as any file it takes. Files the reply
  can't carry wait for the next New chat. A page whose state says
  `confirmsAttach: true` answers each attach once with `attached {id,
  taken, files: [{name, taken, reason}]}` after every upload has settled, in
  the attach's order; the app sends an `id` with the attach and keeps the
  files in flight until then (`PendingShares`). A file taken, `refused` or
  `removed` leaves; `locked` and `unavailable` wait to be handed again, to
  this composer once a `state` says it takes files (an answer that comes
  after that state hands them again at once, but only once per state, so a
  page that answers `unavailable` straight away isn't sent them in a loop);
  `failed` and any reason
  the app doesn't know wait for a New chat after another visit, so a failing
  upload isn't retried on the same page. Files in flight go back when the
  screen that sent them starts another visit or is destroyed, or any web
  composer's `connect` or `state` comes once ten minutes have passed since
  the attach with no answer (each one checks; there's no timer, so until a
  message, a visit or the screen going, they stay in flight), for a New
  chat after it. Each shared file keeps an
  identity of its own, so an answer that comes late still takes out what the
  page took, even after the file was sent again, and a new share joins the
  files still waiting rather than replacing them. That closes the gap where a lock between
  the reply and the page acting on it lost the files. A page without
  `confirmsAttach` (a server before 2026-11-20) is trusted with them as the
  reply goes, as before. Shared words still arrive through `?text=`.
- **Pictures from the keyboard, and drops (2026-11-27).** Gboard's
  clipboard, its suggestion to paste what was just copied, GIFs, stickers
  and image keyboards insert a picture as rich content (`commitContent`),
  never as a paste, and Chromium's web view takes none: the keyboard said
  "Chat with Work does not support image pasting here". The app's web view
  (`AppWebView`) takes rich content the platform's way
  (`ViewCompat.setOnReceiveContentListener`, `PastedContent`): while the
  screen's latest `web-composer` state, for the page now showing, has
  `acceptsFiles`, the field tells the keyboard it takes `image/*`, and a
  picture inserted is read (under the keyboard's grant) and replied as
  `attach {files}` without an `id`, so it uploads like any file the page
  takes, with its limits and its dialog for one it refuses; the chip shows
  how the upload went, and the `attached` that follows settles nothing in
  the app. A drop from another app (tablets, split screen) that holds files
  goes the same way; a drop of words is the page's. Where no composer takes
  files now, nothing is offered to the keyboard, and a picture that comes
  anyway is turned down in a snackbar. Long-press Paste needs none of this:
  Chromium offers Paste for a picture and gives the page an ordinary paste
  with it, which the page attaches, or pastes as words when the clip has
  words of its own (a spreadsheet range with a picture of it beside).
  The native composer's field takes the same content by the same rule.
  Nothing changes in the contract.
- **Dictate from the widget or a shortcut** waits the same way: on New chat's
  `connect` with `dictates`, the app asks for the microphone if Android
  hasn't allowed it yet, then replies `dictate`, which clicks the page's
  microphone, so everything it checks holds. Without the permission it says
  so in a snackbar.
- **The focus.** The page focuses New chat's composer as it loads; the app
  gives the web view the focus and asks for the keyboard (`WebFragment`).
  New chat opened again while it's already on top (the bar, a shortcut)
  focuses the page's composer from the app. After Send the page decides
  the keyboard: in a chat it keeps the focus through the composer's redraw,
  so the keyboard stays up; New chat, New bot's sentence and Decisions' All
  clear let it go down once as they turn into the chat (the shared web view
  also loses the focus as it moves to the chat's screen), and the app never
  brings it back.
- **Back closes a menu.** While `menu` is true the app takes Back (a callback
  added after the navigator's, on the screen's view) and replies
  `closeMenus`, which closes the composer's dropdowns and slash menu; Back
  then goes back as before. While the sidebar covers the page, Back is the
  drawer's, and the menu waits for the next one. Without it, Back with the + menu open left the
  app from a place, the menu still drawn. A page without `menu` sends none,
  and Back is the navigator's.
- **Files from the + menu.** Its Attach files is the page's own file input:
  on this server's pages, for an input that takes several files of any kind
  (the composer's), the app answers it with a sheet of Camera, Photos
  (Android's photo picker), Files and Scan (ML Kit's document scanner, one
  PDF). Anything narrower (a type, or a single file, which the sheet's
  pickers can't keep to), and other sites, get Hotwire Native's chooser
  (`WebFileChooser`).
- **The keyboard.** The app pads the pages for the system bars and the
  keyboard, and follows the keyboard's slide frame by frame
  (`WindowInsetsAnimationCompat`), so the composer rides on its top edge as
  it opens and closes. The web view gets those insets already used up, so
  `env(safe-area-inset-bottom)` is 0 in the app and the page never pads for
  the gesture bar a second time. The VirtualKeyboard API is no use here: the
  WebView (149) has `navigator.virtualKeyboard` but never fires
  `geometrychange`.
- **Haptics.** Send's `turbo:submit-start->bridge--haptic#vibrate` (`light`)
  ticks as on the web.
- **Not in these builds:** New chat's kept draft (the page keeps its own
  draft per chat), and the skeletons' composer bone.

**Composer parity (2026-10-30).** Each model may also send `iconName`
(the model maker’s key), `icon` (the web asset’s absolute URL), and
`invertIcon`. Both apps bundle the exact web maker artwork for `anthropic`,
`openai`, `gemini`, `qwen`, `deepseek`, `mistral` and `xai`; unknown or absent
keys simply omit artwork. Only the web’s monochrome logos invert in dark
mode. An inference host never supplies the displayed brand.

`permissionModes` is absent or null when the viewer cannot choose a mode,
or while their lazy frame loads. Otherwise it is
`{selected, options: [{value, label, description, selected, disabled}]}`.
The native + sheet draws these labels and restrictions unchanged. Its
`permissionMode` reply `{value}` clicks the existing enabled web radio;
new chats submit that choice with the first question. A returned choice
is rechecked against the latest state, so closing access while a sheet is
open cannot retain the older choice. The page and server remain the policy
authority for defaults, organization caps, drivers, guests and bot runs.
The selected mode shows on the bar as a pill unless it's `ask` (see "The
mode pill" below). Existing chats keep the saved choice until the server returns its updated state.
Older servers omit these optional fields and retain the existing controls.

The native composer uses the web’s exact surface/ink tokens and all five
rainbow stops, with 70% opacity at rest and full opacity while editing.

**The rainbow (2026-11-01).** The composer is the app's clearest mark, and it
follows the web's `.composer` from `state` alone: `locked`, `working` and
`waiting` need nothing new. A 2dp edge at 70% (full with focus, 60% locked),
flowing to the right, one loop in 5 seconds, while `working` and from Send
until the state says it is (the web form's `aria-busy`; at most 10 seconds,
and never once it's `locked` or hidden); a blurred glow around the box at 16%
(30% with focus, 5% locked); and both amber while `waiting`, the glow at 14%,
which outranks `working` and the glow's lock. Both opacities ease over 220ms.

**The glow is the edge blurred (2026-11-19).** As the web's composer since
that day, the glow is the box itself (no inset, its 20dp corners) blurred by
22dp, painted with the edge's own shader: under any point of the edge the glow
is that point's colour, and while the edge flows the glow flows with it, in
the same phase, since one animator moves the one shader both use. The blur is
an alpha-only mask made once for a width and cached (`ComposerGlow`), so
typing, the keyboard and each frame of the flow only draw a texture. With
animations off in the system's settings both stand still on the loop's first
frame, and the flow stops while the composer is off screen or the app is in
the background. Colours are `--rainbow`, `--rainbow-loop`, `--attention` and
`--shadow-float`: change them on the web first.


**Overlay layout (2026-10-29).** The server advertises `overlay: true` in
composer state when it supports scroll clearance. Only then does the app
float the composer over the page and reply `layout {height, width}` after
every `connect` and whenever its size changes. If the flag is absent or
false, the app keeps the page above the composer for older servers. `height` is the
whole native composer, including its own padding and lock or notice line,
but excluding the keyboard and system safe area. `width` is the full web
viewport width, not the narrower composer column on a tablet. Both use
the same native units (points on iOS, pixels on Android). The bridge
converts the height to CSS pixels with `height * innerWidth / width`,
sets `--native-composer-height`, and clears it when the composer leaves.
The conversation has that height plus 1.5rem of bottom padding; older apps
leave the variable unset and keep their previous clearance.

An optional `visible` state flag defaults to true. While a web modal
dialog or a popover is open, or the web composer is in a hidden tab (the
side chats tab of `/chats/<n>/beside`), the bridge sends false: native hides the composer, releases
its inset and reports layout height 0, while keeping the draft. Closing it
restores the composer without taking focus from the page. This
applies to connector search, model consent and any other web modal.

Builds that register the `composer` component draw the composer
themselves, floating over the page above the keyboard and the gesture bar:
"+", the microphone and web search's compass, the mode pill unless the mode
is `ask`, the field, the model, Stop while an answer runs, and Send, on the composer's rainbow
edge. The web composer (`#new_message`, with `bridge--composer`) stays in
the page out of sight (native.css, once `data-bridge-components` lists
`composer`), and the app drives it, so every rule it carries holds: locks,
who may steer, the queue, files waiting for their scan, the model, and web
search. The conversation scrolls behind the composer, showing through its clear
gutters. The page leaves room to scroll its last answer above the box.

| Event | Direction | Data |
|---|---|---|
| `connect`, then `state` | web to app | `{overlay?, visible?, placeholder, text?, fill?, locked, lockReason, working, stopping, models: [{name, rate, selected, searchesWeb, iconName?, icon?, invertIcon?}], permissionModes?, webSearch: {on, disabled, note} \| null, narrowing?: {title, note} \| null, dictation: {url} \| null, csrfToken, attachments: [{id, name, size, contentType, uploading, progress}], acceptsFiles}` |
| reply to `connect` | app to web | `{action: "send", text}`, `{action: "stop"}`, `{action: "model", index}`, `{action: "permissionMode", value}`, `{action: "webSearch", on}`, `{action: "narrow"}`, `{action: "attach", files: [{name, type, data}]}` (data base64), `{action: "remove", id}`, `{action: "text", text}`, `{action: "layout", height, width}` |

A chat draws its composer again as an answer starts and ends; each new one
connects again, and the app always replies to the latest `connect`. A page
without a composer never connects, and the native one stays hidden there.

What the app does with it:

- **The field** starts with `text` on `connect` when it's empty (words
  shared in, or a draft the web restored). A suggestion pressed under the
  composer (New chat's, Decisions' All clear; the server's
  `prompt_suggestions_controller.js`) connects again with its words as
  `text` and `fill: true`: the field takes them in place of whatever it
  holds, never joined to it or to New chat's kept draft, with the caret at
  their end, the focus and the keyboard, as the web's composer takes them
  (from 2026-11-20; builds before it take them as any connect's, into an
  empty field, with no keyboard). The field keeps what's typed until Send, which sends `send {text}`
  (with a confirming haptic) and clears it. Send needs words or a file and
  waits while a file uploads (the web would refuse). While `working`, an
  empty field shows Stop alone (`stop`, disabled while `stopping`); once
  there are words or a file, Send shows beside it, so either can be
  pressed: Stop sends `stop`, and Send sends `send` and the web decides
  whether the words steer the answer or wait for it. Emptying the field
  leaves Stop alone again. Not working, Send stands alone. As on the web,
  Stop is the solid ink square at the bar's end, Send on its left the subtle
  one, and Stop
  wears `.chat-prompt__ring`: a turning 2dp rainbow, grey at half strength
  and slower while `stopping`, standing still with animations off.
- **Locked**: the field, "+", the microphone and the mode pill are disabled
  and dimmed, and `lockReason` shows above the composer.
- **The model**: a chip with the selected model's name, hidden while locked
  or working, which opens a sheet of `models` (name and rate, the selected
  one checked) and answers `model {index}`.
- **The bar's own controls (2026-10-28)**, one tap away while there's
  room, as on the web's folding bar: the microphone when `dictation` is
  set, and web search's compass when `webSearch` is set (sending
  `webSearch {on}`; ink at 20% while `disabled`, when a tap shows its `note`).
  The compass is the web's `.chat-prompt__web-search-toggle` (2026-11-20, the
  globe before): Phosphor's bold compass in the ghost buttons' 34dp square, in
  the neighbouring buttons' ink, the same glyph on and off; while `on` it sits on
  a quiet wash (ink at 8%, no edge or shadow), the pressed square, and TalkBack
  says on or off. They fold into "+" as
  the bar narrows: web search on a screen under 408dp, where the web's bar
  (the box, the screen less 24px) drops below 24rem, and dictation when the model chip would lose its room for a short
  name, or its logo and chevron while the mode pill shows. While web search is on and its switch is in "+", "+" carries the
  web's small compass (`.chat-prompt__attach-badge`), never while `disabled`.
  Nothing new comes from the server.
- **"+"** opens a sheet: Camera, Photos (Android's photo picker, no
  permission needed), Files, Scan (ML Kit's document scanner, one PDF), and
  whatever the bar had no room for: Dictate, and Web search as a switch with
  its `note`, then a "Changes" heading with `permissionModes`' options
  (the selected one checked, disabled ones dimmed with the description saying
  why; Allow everything's icon in `--negative-ink`). It's always there at the
  bar's left and always the plain add icon, never the mode's icon or a
  paperclip, disabled while locked or holding nothing. Files go to the page
  as `attach`, base64, at most 25 MB each (larger ones are named in a
  snackbar and left out). Each attachment shows as a chip with its name, its
  size or upload progress, and remove (`remove {id}`).
- **Narrow services (2026-11-09)**: `narrowing` is `{title, note}` only
  where the web's + menu has its Narrow services item (the person has a
  service that can be kept to some of its accounts or projects in this
  chat), and null or absent otherwise, older servers included, which show
  no row. `title` is the item's ("Narrow services") and `note` what it's
  kept to now ("Everything connected", "Google Drive ·
  alex@northwind.example"), possibly null. "+" shows it as one row after
  the tiles and web search and before "Changes", styled as the modes' rows,
  with the web item's funnel (the `filter_alt` symbol) and the note under
  the title; it counts toward "+" holding something. A tap dismisses the
  sheet and sends `narrow`, which clicks the web's item and opens its
  dialog; while that's open `visible` is false, so the native composer
  hides (see above). The pill's Changes-only sheet leaves it out.
- **The mode pill (2026-11-06)**, after the globe, only when the selected
  option's `value` isn't `ask`, hidden while dictating: a 34dp fully rounded
  pill in a 44dp target, read from `permissionModes` alone. `edits` is the
  `edit` symbol and "Edits" in `--ink-muted` with a 1dp `--line-strong`
  outline and no fill; `everything` is the `report` symbol and "Everything" in
  `--negative-ink` on `--negative-wash` with a `--negative-edge` outline; any
  other value shows the server's full `label` in the quiet look. TalkBack reads
  "Changes: " and the server's `label`. A tap opens the "+" sheet with only
  Changes. The bar measures the pill and keeps its room: the globe still folds
  at the same width, dictation folds if it must, and the model chip's name gives
  way first, down to its logo and chevron (56dp, only while the pill shows), so
  Send and the pill stay on the bar. Nothing new comes from the server.
  With the system's text larger than standard, a name that would show fewer
  than six letters beside the bar's buttons moves the model to a row of its
  own above the bar instead, where it reads whole; at the standard size the
  bar is as above. The pill, the chip and a file's chip grow with large text
  rather than clip, and the bar's 34dp buttons take touches 48dp around them.
- **Dictation**: Android 10 and later record OGG/Opus (at most five
  minutes), never Android's speech recognizer. While it listens, and while
  it transcribes, its line takes the field's place, as the web composer's
  does (`[data-dictating]`): "Listening" with a live dot, the waveform
  across the composer (newest on the right; a still meter with animations
  off), and the time, with Cancel where "+" is and Stop where Send is; then
  "Transcribing", the waveform faded and a spinner on Stop. The field comes
  back with the words. The app posts the recording as multipart field
  `audio` to `{base}{dictation.url}`
  with the web view's cookies, `X-CSRF-Token: csrfToken` and `Accept:
  application/json`. `200 {text}` puts the words in the field and sends
  `text {text}`; an empty text says nothing was heard. A recording the
  server refuses for itself (400, 413, 415) is dropped and its `{error}`
  shown. Anything else (no connection, the proxy's own 504 after 30
  seconds, 403, 429, 502) keeps the recording on the phone: the line says
  "Couldn't transcribe." (or the server's `{error}`) in negative ink, with
  Discard where "+" is and Try again where Send is, until one is pressed.
  Going to the background or a lock leaves it; leaving the page drops it. Earlier versions
  leave Dictate out. Recordings shorter than half a second or without a
  measured signal above 0.02 for 300 ms are deleted without uploading.
  The gate uses Android's peak meter, conservatively admitting quieter
  speech; if metering is unavailable, transcription remains available.
- Files shared into the app from elsewhere (section 7, "Sharing into the
  app") wait for the next composer that connects and go to it as `attach`.
- **The yes before the first question (2026-10-27).** Before anything a
  person sends goes to a model, the page asks once in a dialog of its own,
  over the chat (the server's `docs/model-consent.md`). The app does
  nothing new: after `send`, the web composer waits for the dialog. Agree
  sends the words; Not now sends nothing and the page connects again, with
  the words as `text`, which the empty field takes back. Until the person
  agrees, `dictation` is `null`, so Dictate stays out of "+", and a `state`
  brings it back once they do.
