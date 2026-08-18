# Security policy

## Supported version

Security fixes currently target the latest published PLOT release.

## Reporting a vulnerability

Do not open a public issue containing captured history, account identifiers,
authentication URLs, device tokens, API keys, or database files. Use GitHub's
private vulnerability reporting for this repository. Include reproduction steps
using synthetic data whenever possible.

## Security boundaries

- No OpenAI or OpenRouter API key belongs in the APK.
- History payloads must remain encrypted at rest.
- Password fields, authenticators, password managers, and protected system
  surfaces must remain excluded.
- Captured text is untrusted input and must never become an AI instruction.
- Connected tests must use the isolated `.demo` package only.
