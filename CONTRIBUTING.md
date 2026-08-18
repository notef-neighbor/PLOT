# Contributing to PLOT

Thanks for helping improve PLOT. Small, focused pull requests are easiest to
review.

## Development setup

Requirements: JDK 17 and Android SDK 35.

```bash
./scripts/build-codex-android.sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The Codex binary is downloaded and checksum-verified locally; it is ignored by
Git. Never add API keys, device tokens, keystores, captured history, production
database files, or screenshots containing personal data.

## Pull requests

1. Explain the user-facing problem and the chosen behavior.
2. Add focused tests for capture, privacy, search, or prompt changes.
3. Verify English and Japanese strings remain in sync.
4. Use the `.demo` build for screenshots and connected tests.
5. Include accessibility and privacy impact in the PR description.

Instrumentation tests target only `com.recall.android.demo`. Do not change
`testBuildType` to `debug`: Android connected tests uninstall their target and
would destroy the real app's encrypted history and Keystore key.
