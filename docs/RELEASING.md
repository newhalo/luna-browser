# Releasing

Releases are built by GitHub Actions from a version tag (`.github/workflows/release.yml`).

## One-time setup: your signing key

The repository contains only a **public development key** (`app/dev.keystore`). Official releases must be
signed with a private key that you keep secret — and keep **forever**: Android refuses to install an update
signed with a different key over the existing app.

```bash
keytool -genkeypair -keystore release.keystore -alias luna -keyalg RSA -keysize 4096 -validity 36500
base64 -w0 release.keystore      # (macOS: base64 -i release.keystore)
```

Back the file up somewhere safe, then add these **repository secrets** (Settings → Secrets and variables →
Actions):

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the base64 text from above |
| `RELEASE_KEYSTORE_PASSWORD` | the keystore password |
| `RELEASE_KEY_ALIAS` | `luna` |
| `RELEASE_KEY_PASSWORD` | the key password |

For a local signed build, export `LUNA_KEYSTORE_FILE`, `LUNA_KEYSTORE_PASSWORD`, `LUNA_KEY_ALIAS` and
`LUNA_KEY_PASSWORD`, then run `./gradlew :app:assembleRelease`.

## Cutting a release

1. Update `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Move the *Unreleased* entries of `CHANGELOG.md` under the new version and date.
3. Commit, then tag and push:

   ```bash
   git tag v0.3.0
   git push origin v0.3.0
   ```

4. The workflow runs the tests, builds `luna-browser-v0.3.0.apk` and publishes a GitHub Release with generated
   notes. Edit the notes to taste.

If no signing secrets are configured, the workflow signs with the public dev key and prints a warning — fine for
testing, but do not publish such a build as an official release.
