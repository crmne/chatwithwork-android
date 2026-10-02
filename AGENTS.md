# Agent guide

Chat with Work for Android: a Hotwire Native shell around the Rails app at
`crmne/chatwithwork` (private), whose `AGENTS.md` describes the product, the
Live Wire design system and the chat UI. Read the README here first, then
`docs/server-contract.md` and the iOS app's contract it builds on
(`crmne/chatwithwork-ios`, `docs/server-contract.md`).

## Working style

- Work on the default branch for maintainer-directed work. Do not create a
  branch or pull request unless asked. Pull requests remain required for
  outside contributions.
- Keep history linear: one focused commit per topic, no merge commits.
- Keep changes within the requested scope and preserve existing behavior
  unless the task changes it.
- Never commit secrets, keystores, `google-services.json` or API keys: the
  repository is public. Signing comes from environment variables or Gradle
  properties (README, "Signing").
- Never use em dashes in user-facing text (UI strings, docs, commit
  messages): commas, colons, parentheses or full stops.

## Architecture rules

- One activity (`MainActivity`) with a navigator per tab. Changes of
  session or organization reset the navigators rather than patching
  screens.
- Screens are `WebFragment` (in a tab) and `WebModalFragment` (modal),
  chosen by the path configuration's `uri`. Modals stay full-screen
  fragments: Hotwire Native's bottom-sheet fragments have no top app bar,
  and bridge components that put actions there crash in them.
- Top app bar actions go through `ToolbarComponent`, one menu group per
  component, so components never remove each other's items.
- A bridge component's name, events and payload keys are a contract with
  the Rails app and the iOS app. Change one only together with
  `docs/server-contract.md`, its test in `BridgeMessagesTest`, and a note
  for the iOS app (`crmne/chatwithwork-ios`), whose `web/controllers/bridge/`
  holds the Stimulus controllers both apps share. Where Joe Masilotti's
  bridge-components has the component, keep his messages and only add
  optional keys.
- Never key native behavior off a title or label: they're translated.
  Use where something is (a tab's first page), an index, a URL, or a
  stable `nativeAction` from the payload.
- Path configuration rules live in
  `app/src/main/assets/json/path-configuration.json`; the server serves a
  copy as `/configurations/android_v1.json`, which replaces the bundled one
  at launch. Change both, keep the rules in step with iOS's, and keep
  `PathConfigurationTest` passing.
- Colors come from Live Wire's tokens in `res/values/colors.xml` (and
  `values-night`), type is Geist, icons are Material Symbols. No dynamic
  color. Check UI changes in light and dark mode.
- The launcher icons stay in `mipmap-anydpi-v26`: bridge-components ships
  its demo app's icons there, and only the same folder overrides them.
- Pin dependencies to exact releases in `gradle/libs.versions.toml`, never
  to a branch or a range.

## Building and testing

- `./gradlew testDebugUnitTest lintDebug spotlessCheck assembleDebug
  assembleStaging assembleRelease` is what CI runs. Lint fails the build on
  any error.
- Try changes on an emulator against the iOS repository's playground
  (README, "The playground") or a local Rails app, and against staging for
  signed-out flows. Development builds simulate pushes from adb
  (`docs/push-notifications.md`).
- Never claim a platform or flow was tested unless it was actually run, and
  say whether it ran against the playground, a local server or staging.
- Run emulators headless when working remotely (`emulator -no-window`), and
  wrap GPU-heavy runs in `gpu-lock` where it exists.

<!-- github-automation: release-notes -->
## Releases

This section is maintained account-wide by
[crmne/github-automation](https://github.com/crmne/github-automation) and is
replaced when that policy changes. Do not edit it here. If it does not fit this
repository, say so in a review or issue, and put repository-specific release
steps in a separate section, which takes precedence.

Never use em dashes in new or edited user-facing writing, including release
titles, release notes, and agent responses. Use commas, colons, parentheses,
or full stops. Existing text does not need to change just to follow this.

The rest of this section applies only when this repository publishes GitHub
releases. If it has none, skip it, and do not add tags, release workflows, or
release-notes files just to follow it.

Do not cut a release for every fix. Work accumulates on the default branch
until there is something substantial to announce: a feature, or a batch of
fixes worth a changelog entry. The exception is a regression in something just
released, which goes out as soon as it is fixed.

Before writing release notes, read the previous two stable releases and match
their style. If there are fewer, read the most recent releases that exist,
including prereleases, and follow their format.

- Start with a short plain-language summary, followed by a download line when
  the project ships binaries.
- Include screenshots or short videos of the main user-visible changes.
  Capture only synthetic demo content, never real user data. Host the media
  where earlier releases do, such as release assets or files beside the notes.
- Use `New` and `Fixed` sections as applicable, and `Known limitations` when
  there are any. Lead each item with a bold user-facing result and credit who
  did what with issue or pull request numbers ("By @x; thanks @y"),
  acknowledging reporters separately from implementers.
- Include a `Thanks` section listing contributors and reporters, and end with
  `**Full changelog**:` and a link comparing the previous tag.
- Write about what changed for the user, not the commit history. Describe
  known limitations honestly.

Every release description is these hand-written notes, never a list generated
by GitHub, a changelog tool, or commit subjects. Commit the notes before
tagging, in the repository's existing release-notes location, or as
`packaging/release-notes/vX.Y.Z.md` when it has none. Any publishing path that
uses the committed file works, for example `softprops/action-gh-release` with
`body_path` and `generate_release_notes: false`, `gh release create
--notes-file`, GoReleaser's `--release-notes`, or `gh release edit
--notes-file` when another step creates the release.

If the release path still generates its notes, switching it to the committed
file is part of preparing the next release. Make a missing notes file stop the
release before any tag or release is created.

A release is not finished until every image, video, and download link in its
notes loads. Upload the release media right after the release is published and
before announcing it, then open the published release and check every image
and link.
<!-- /github-automation: release-notes -->
