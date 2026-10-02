# Push notifications

Status: built in the app; needs a Firebase project and the server's half
(`docs/server-contract.md`, section 9) before a phone can receive one.

The first notification is **"A change is waiting for your approval"**: the
assistant parked a reply at a change (a Slack message, a calendar event)
that only its driver can approve. Then **"A question is waiting for your
answer"**, when a connected service asks the person something while its
tool runs. Tapping one opens that chat, in Chats, inside the right
organization.

## What the app does

- **Nothing until the person asks for it.** Firebase's token auto-init and
  analytics are off in the manifest. The app registers with Firebase only
  when a page asks for a token (`notification-token`'s `get`, from the
  "Turn on" button in Settings › Notifications), after Android 13's
  permission prompt where there is one.
- **The page registers the token, not the app.** The bridge component
  replies with the token; the page posts it to `/native/push_registrations`
  with its own session and CSRF token, and posts it again at most once a
  day while push is on, so a token Firebase rotated reaches the server.
- **Signing out forgets the device.** When the app sees a sign-out (or a
  session that ended), it drops the token, unregisters from Firebase, and
  turns push off until someone turns it on again. The server deletes the
  registration when it signs the person out.
- **Data-only messages.** The server sends `kind`, `path`, `thread`,
  `title` and `body` as FCM data, never content from the chat. The app
  checks them (`Push.from`: a known kind, a path on this app's server) and
  writes the notification itself:
  - on the "Approvals and questions" channel (high importance), which the
    person can silence in Android's settings on its own;
  - one per chat (`thread` is the notification's tag), so a newer one
    replaces it;
  - tapping it opens the page in the app, as an App Link would;
  - `kind: resolved` removes the chat's notification, for a change approved
    or a question answered somewhere else.

The code: `push/PushNotifications.kt` (permission, opt-in, token, showing
notifications), `push/PushMessagingService.kt` (Firebase's callbacks),
`push/Push.kt` (the payload), and `bridge/NotificationTokenComponent.kt`.

## Setting up Firebase

1. Create a Firebase project (it can be dedicated to the apps), and add
   three Android apps to it: `com.chatwithwork.app`,
   `com.chatwithwork.app.staging` and `com.chatwithwork.app.debug`.
2. Download `google-services.json` (one file covers all three) into
   `app/`. It's git-ignored: this repository is public. The build applies
   the Google Services plugin only when the file is there; without it,
   `BuildConfig.FIREBASE_CONFIGURED` is false, Firebase never starts, and
   the app reports push as on without a token, so pages register nothing.
3. For release builds in CI, keep the file as a secret (for example
   base64-encoded in `GOOGLE_SERVICES_JSON`) and write it to
   `app/google-services.json` before building.
4. For the server, create a service account with the Firebase Cloud
   Messaging API Admin role and give its JSON key to the Rails app
   (`firebase.service_account`, with `firebase.project_id`). The server
   contract says how to send.

## Trying it

Without Firebase, development builds show a push from adb, as if FCM had
delivered it (`app/src/debug/.../DebugPushReceiver.kt`; only the shell can
send it):

```sh
adb shell am broadcast -n com.chatwithwork.app.debug/com.chatwithwork.app.push.DebugPushReceiver \
  --es kind approval_waiting --es path /1000001/chats/42 --es thread chat-1000001-42 \
  --es title Acme --es body "'A change is waiting for your approval in Slack.'"

adb shell am broadcast -n com.chatwithwork.app.debug/com.chatwithwork.app.push.DebugPushReceiver \
  --es kind resolved --es path /1000001/chats/42 --es thread chat-1000001-42
```

Grant the permission first if the app hasn't asked yet:
`adb shell pm grant com.chatwithwork.app.debug android.permission.POST_NOTIFICATIONS`.
Against the iOS repository's playground (`server.py`), `/1000001/chats/42`
is a chat waiting for an approval.

With Firebase configured, turn push on in Settings › Notifications, copy
the token the page registered (the server's `PushRegistration`), and send a
data message with the HTTP v1 API (`docs/server-contract.md`), or from the
Firebase console's messaging test with the same data keys.

## Not yet

- **Actions on the notification** (Approve, Deny): an approval needs the
  preview of what will be written, so it stays in the chat.
- **Other kinds of notification.** Add a setting for each in Settings ›
  Notifications first, as the server contract says, and a channel for each
  in `PushNotifications.createChannels`, so people can silence them apart.
