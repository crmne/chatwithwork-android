# Security policy

Chat with Work for Android holds a person's session with their Chat with
Work server and shows their work data. We treat any way to reach another
person's data, to run a page outside the app's own server with the app's
powers, or to steal the session or a sign-in as a serious vulnerability.

## Reporting a vulnerability

Please report vulnerabilities privately:

- Use GitHub's **"Report a vulnerability"** button on this repository
  (Security ▸ Advisories), or
- Email **hello@chatwithwork.com** (the contact on
  [chatwithwork.com/security](https://chatwithwork.com/security)) with
  "Android app vulnerability" in the subject.

Please don't open a public issue. Include the app's version (in Android's
App info, or on its Google Play page), your Android version and device, and
steps to reproduce. A proof of concept against the iOS repository's
playground (`Playground/server.py`) and a debug build is ideal.

We will acknowledge your report within 3 working days, keep you updated,
and credit you in the advisory unless you prefer otherwise. We ask that you
give us a reasonable chance to release a fix before disclosing the issue.

## In scope

- A page from another site, or a link, driving the app's bridge components
  (the share sheet, the Auth Tab, push registration, the clipboard).
- Opening a sign-in or connection handoff anywhere but the app's own
  server, or redeeming one somewhere else.
- An App Link, a `chatwithwork://` link, an intent from another app, or a
  notification opening a page from another site inside the app, or with the
  app's session.
- Leaking the session cookie, a push token, or notification contents, for
  example through backups, logs, or another app on the phone.

## Out of scope

- Vulnerabilities in the Chat with Work web app itself. Report those to the
  same address; they are handled separately.
- Attacks that need an unlocked, rooted, or compromised device, or a
  development build (which allows plain http to a local server and web view
  debugging on purpose).

## Supported versions

Only the latest release gets security fixes.
