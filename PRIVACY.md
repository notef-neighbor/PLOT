# PLOT privacy policy

Last updated: August 18, 2026

PLOT is a local-first Android history application. It records data only after
the person using the device accepts the in-app disclosure and enables the
required Android access.

## Data PLOT can store

For apps explicitly enabled in PLOT, Android may expose visible text, page
titles, URLs, input changes, view identifiers, and interaction events through
AccessibilityService. Optional Android permissions can add notification text
and Google Calendar events.

PLOT never intentionally records password fields, authenticator or password
manager apps, protected Android permission surfaces, screenshots, camera,
microphone, calls, system audio, or clipboard history.

## Storage and deletion

Captured payloads are encrypted on the device with an Android Keystore-backed
AES-GCM key. Raw interaction events are deleted after 48 hours. Generated
memories remain on the device until the user deletes an item or clears all
history. Uninstalling PLOT deletes its local data and encryption key.

## AI processing

PLOT does not include a provider API key. When a user connects ChatGPT through
the on-device Codex App Server, only history selected for a requested search,
report, or ChapiChapi conversation is sent for that request. An optional
self-hosted HTTPS gateway can be configured instead; its operator controls that
gateway's retention and provider terms.

## Network services

The optional ChatGPT/Codex and gateway features communicate with their
respective providers. PLOT itself has no developer-operated analytics,
advertising, crash-reporting, or history collection service.

## Control

Users can pause collection, enable or disable all eligible apps at once, change
apps individually, disconnect ChatGPT, disable daily reports, delete individual
memories, or delete all local history.

Security issues should be reported using the private process in
[SECURITY.md](SECURITY.md), not a public issue containing personal history.
