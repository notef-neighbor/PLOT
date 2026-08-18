# PLOT product specification

## Purpose

PLOT helps a person resume work, find recently viewed information, produce a
daily recap, and recognize repeated workflows. Collection begins only after a
prominent disclosure is accepted; all eligible apps then start enabled and can
be changed individually or with one-tap all-on/all-off controls.

## Data lifecycle

1. Android emits an accessibility event.
2. `PrivacyGuard` rejects password fields, authenticator and password-manager
   apps, protected system surfaces, PLOT itself, and events without useful
   context. Browser content is not treated specially.
3. Visible text, page titles, URLs, input changes, view identifiers, and the
   visible accessibility-node tree are retained up to bounded event limits,
   then encrypted before Room persistence.
4. Events are grouped into sessions locally.
5. Recent events appear immediately in a live feed. After roughly one minute,
   local session cards are generated; an authenticated gateway may replace
   them with structured AI summaries.
6. Raw events expire after 48 hours. Memories remain until deleted by the user.

## AI runtime

The primary AI experience is an on-device Codex App Server process connected
to the user's ChatGPT account through managed browser login with an automatic
local callback. Device-code login is kept only as a fallback. PLOT communicates
with it only through `ws://127.0.0.1`, and the foreground service keeps the
process lifecycle visible. A remote HTTPS gateway is an optional fallback.

Captured history is supplied only to ephemeral threads configured with no
approvals and a read-only sandbox. The model receives explicit instructions to
treat every history field as untrusted evidence and never as an instruction.

## Daily reports

Daily reports default to on at 21:00 local time. A one-time WorkManager job is
scheduled for the next selected wall-clock time and schedules its successor
after completion. The report is generated from that day's encrypted session
memories with Codex when authenticated, or from a local statistical recap when
AI is unavailable. It is stored as an encrypted timeline memory and surfaced
through a private notification. Users can disable reports, change the time, or
run one immediately.

## Threat model

- A copied app-data directory must not reveal captured text.
- A malicious page may place prompt-injection text into the event stream; all
  captured content is treated as quoted, untrusted evidence and never as model
  instructions.
- Backend credentials must be revocable and scoped to one device. OpenAI or
  OpenRouter API keys must never be embedded in the Android package.
- History collection must be visible and immediately pausable.
- Browser private-mode detection is not reliable across Android browsers.
  PLOT therefore discloses that browser content, including private-mode
  content exposed through accessibility, can be captured when the browser is
  enabled.

## Non-goals

- Remote control or autonomous operation of other applications.
- Capturing password fields or bypassing Android secure-surface protections.
- Screen, camera, microphone, call, or system-audio recording.
- Hidden monitoring of another person or device.
