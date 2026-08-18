# Releasing the Android APK

GitHub Releases publishes a signed `PLOT.apk` and its SHA-256 checksum.
The same signing key must be retained for every update; Android will reject an
update signed with a different key.

## One-time signing setup

Create and securely back up a release keystore outside the repository:

```bash
keytool -genkeypair -v \
  -keystore plot-release.jks \
  -alias chapichapi \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Add these encrypted GitHub Actions secrets in the repository settings:

- `RELEASE_KEYSTORE_BASE64`: Base64 of the complete keystore file
- `RELEASE_STORE_PASSWORD`: Keystore password
- `RELEASE_KEY_ALIAS`: Key alias, such as `chapichapi`
- `RELEASE_KEY_PASSWORD`: Private-key password

On macOS, create the single-line Base64 value without changing the keystore:

```bash
base64 -i plot-release.jks | tr -d '\n'
```

Never commit the keystore, its passwords, or the Base64 value. Keep at least two
encrypted backups in separate locations.

## Publish

Merge the release workflow into the default branch before creating the first
tag. Then push a semantic version tag:

```bash
git tag -a v0.1 -m "PLOT v0.1"
git push origin v0.1
```

The workflow downloads the pinned Codex App Server archive, verifies its
checksum, runs Android and gateway tests, builds the minified release APK,
verifies its signature with `apksigner`, and publishes both files:

- `PLOT.apk`
- `PLOT.apk.sha256`

The workflow can also be started manually from GitHub Actions with a semantic
version tag. A failed signing or test step prevents publication.
