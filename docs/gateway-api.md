# AI gateway API

The production gateway is expected to authenticate every request with a
short-lived bearer token issued to the signed-in user and device. TLS is
mandatory.

## `POST /v1/history/summarize`

Request:

```json
{
  "sessionId": "uuid",
  "locale": "ja-JP",
  "events": [
    {
      "at": "2026-08-17T08:00:00Z",
      "application": "Browser",
      "kind": "window_changed",
      "text": "Project documentation"
    }
  ]
}
```

Response:

```json
{
  "title": "プロジェクト資料を確認",
  "summary": "資料を開き、実装に関係する項目を確認した。",
  "keywords": ["project", "documentation"],
  "status": "completed"
}
```

## `POST /v1/history/ask`

Request includes a question and the minimal locally retrieved memories. The
gateway must not have a tool capable of executing captured instructions.

## Security requirements

- Validate Structured Outputs against a closed JSON Schema.
- Treat every event and memory field as untrusted quoted data.
- Do not log request bodies.
- Apply per-user and per-device rate limits.
- Rotate and revoke device credentials.
- Keep OpenAI or OpenRouter credentials only in server-side secret storage.
