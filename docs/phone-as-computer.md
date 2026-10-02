# The phone as a computer

Status: design note, not built. 2026-10-02.

Chat with Work already lets the assistant search a person's computer through
the Local Agent (`cww`, crmne/chatwithwork-local-agent): the computer pairs
with the device flow, dials out to `/local_agent`, and answers read-only MCP
tools that appear to the model as `local_<device id>_<tool>`. An Android
phone can be one more such computer, answering for what lives only on the
phone, each kind of data turned on by its owner: calendars, contacts,
photos, and folders the person picks.

The server side is the Rails app's `docs/local-agent.md` and
`docs/security/local-agent.md`; the iOS repository's
`docs/phone-as-computer.md` is the same design for iPhones. This note
covers what's different on Android.

## What the assistant could ask

Read-only tools, named like the computer's (`roots`, `search`, `list`,
`read`) so `LocalAgent::Device` treats them alike:

| Data | Android API | Tools | Permission |
|---|---|---|---|
| Calendars | `CalendarContract` (every account synced to the phone) | search events by text and date range; list a day | `READ_CALENDAR` |
| Contacts | `ContactsContract` | search by name, company, email, phone | `READ_CONTACTS` |
| Photos | `MediaStore`, with on-device text recognition (ML Kit's bundled model, nothing leaves the phone to recognize) | search by date, place and the text in a photo | `READ_MEDIA_IMAGES`, or only the photos the person selects (Android 14's partial access) |
| Files | Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`, persisted URI permissions) | the same four tools as `cww`, inside folders the person picked | none beyond the pick |

Android has no shared store for reminders or tasks (Google Tasks lives in
the cloud, behind its own connector), so there's no reminders row.
Messages and call logs stay out: Google Play allows those permissions only
for default SMS and phone apps, and they're the wrong thing to hand an
assistant anyway.

Nothing writes, as with `cww`. Text that leaves the phone is what a tool
returns for one call, cached by the server like any computer's
(`RemoteResource`, `provider: "local"`, 30 days unread).

## Pairing

The device flow (RFC 8628) with DPoP proofs, as `cww` pairs, but the person
is already signed in inside the app, so it can be one tap:

1. Settings › Connectors › Computers shows "Use this phone" in the app (a
   bridge component, say `computer`, so the page knows the app can be one).
2. The app makes its key, posts `POST /local_agent/device_authorizations`
   (`name`: the device name the person sees in Settings, `platform`:
   `android`, `client_version`), and opens `verification_uri_complete` in
   the web view, where `/device` asks the person to confirm who they are, as
   it does for a computer.
3. The app polls `/local_agent/token` and keeps the refresh token encrypted
   with a key in the Android Keystore that never leaves it.

The one server change pairing needs is iOS's too: proofs are `alg: EdDSA`
with Ed25519 keys today. The Android Keystore holds Ed25519 keys only from
Android 13, and only where the hardware supports it; P-256 keys it holds
everywhere, in the secure hardware where there is some. Accepting `ES256`
proofs from phones serves both platforms with keys that never leave the
phone.

## Staying reachable

A computer keeps its socket open; a phone can't for long. Android freezes
apps that sit in the background, and Doze cuts network access for idle
phones, so a socket doesn't outlive the app leaving the screen by much. So:

- While the app is open, it holds the `/local_agent` socket like `cww`
  (OkHttp's WebSocket, the `mcp` subprotocol, which needs no Action Cable
  framing) and answers at once.
- When a tool call targets a phone that isn't connected, the server sends a
  high-priority FCM data message (`kind: "local_agent_wake"`), and the app
  starts expedited work (WorkManager) to connect, answer what's waiting,
  and disconnect. Android gives a high-priority message a short window and
  may defer the work for a phone in battery saver or a rarely used app, and
  a force-stopped app gets no messages at all, so this is best effort.
- Otherwise the call answers as an offline computer's does ("Carmine's Pixel
  is offline"), and the model says so. Calls don't queue: by the time the
  phone comes back, the person has moved on.

## Privacy and control

- Each kind of data is off until the person turns it on in the app (a
  native screen, since it asks Android for the permission), and can be
  turned off there or in Android's settings. The phone answers only for
  kinds that are on: like `cww`, the device enforces what may be read; the
  server's checks are defense in depth.
- Limits on the phone: results per call, characters per read, calls per
  minute, and a local log of every call, viewable in the app.
- Settings › Computers lists the phone with the activity log
  (`local_agent.*` AuditEvents, never contents), Pause, and Disconnect, as
  for a computer. The taint rule applies: once a chat has read phone data,
  every change in it asks again.
- Google Play's Data safety form has to declare each kind of data when it
  ships, as shared with the person's own Chat with Work server for the
  feature they turned on.

## Where it fits in this app

Nothing in the app depends on it yet; these are the seams it would use:

- **A package of its own** (`com.chatwithwork.app.localagent`): the key and
  token store, the pairing flow, the socket, and one tool provider per kind
  of data, behind a small interface so each is tested alone.
- **`PushMessagingService`** hands `kind: "local_agent_wake"` to it instead
  of showing a notification.
- **`MainActivity`** connects the socket when the app comes to the front and
  the phone is paired, and disconnects on sign-out or when the organization
  changes: one key pairs into one organization, as with `cww`.
- **`BridgeComponents`** gains the `computer` component for the Settings
  row.
- **The manifest** gains `READ_CALENDAR`, `READ_CONTACTS` and
  `READ_MEDIA_IMAGES` (with `READ_MEDIA_VISUAL_USER_SELECTED`) only when
  the feature ships, each asked for in context, when its kind is turned on.
