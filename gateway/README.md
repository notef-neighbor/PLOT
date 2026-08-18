# PLOT AI Gateway

Server-side boundary between the Android app and an OpenAI-compatible model
provider. It validates and rate
limits every request, authenticates revocable device tokens, does not log body
content, treats captured history as untrusted data, and returns schema-validated
Structured Outputs.

## Configure

Generate a device token and its hash:

```bash
token=$(openssl rand -hex 32)
hash=$(printf %s "$token" | shasum -a 256 | awk '{print $1}')
```

Store only `hash` in `RECALL_DEVICE_TOKEN_HASHES`. Enter `token` in the Android
app. Keep the model-provider API key only in the gateway's secret manager.

OpenRouter-compatible endpoints are supported. However, free routes may send
personal history to providers that log prompts or use them for training, so
they are blocked by default. For non-sensitive development fixtures only:

```bash
AI_API_KEY=sk-or-v1-...
AI_BASE_URL=https://openrouter.ai/api/v1
AI_MODEL=openrouter/free
ALLOW_OPENROUTER_FREE_ROUTING=true
```

For OpenAI directly, use `OPENAI_API_KEY` and `OPENAI_MODEL`; the default model
is `gpt-5.6-terra`. For production OpenRouter usage, choose a fixed endpoint
whose retention/training policy meets your requirements and enforce ZDR where
available. PLOT falls back to its local summary if the remote model is
rate-limited or temporarily unavailable.

## Run

```bash
npm ci
npm test
npm run build
npm start
```

Terminate TLS at a trusted reverse proxy or managed HTTPS service. The Android
client rejects non-HTTPS gateway URLs.
