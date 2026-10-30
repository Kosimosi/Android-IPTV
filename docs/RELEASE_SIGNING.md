# RETROTV release signing setup

RETROTV uses a persistent Android signing key for release APKs. Keep a secure offline backup of the keystore and its passwords. If the key is lost or changed, Android will not accept future APKs as updates to the signed app.

## Create the key once

Run this on a trusted computer with Java's `keytool` installed:

```bash
keytool -genkeypair -v -keystore retrotv-release.jks -alias retrotv -keyalg RSA -keysize 2048 -validity 10000
```

Choose a strong keystore password and key password. Keep `retrotv-release.jks` and those passwords backed up securely. Never commit the keystore to Git.

## Add GitHub Actions secrets

In the repository, open **Settings → Secrets and variables → Actions → New repository secret** and create these four secrets:

- `RETROTV_KEYSTORE_BASE64`: the keystore file encoded as one Base64 string.
- `RETROTV_KEYSTORE_PASSWORD`: the keystore password.
- `RETROTV_KEY_ALIAS`: `retrotv` (or the alias you chose above).
- `RETROTV_KEY_PASSWORD`: the key password.

On Linux/macOS, generate the Base64 value with:

```bash
base64 -w 0 retrotv-release.jks
```

On macOS, if `-w 0` is unsupported, use:

```bash
base64 < retrotv-release.jks | tr -d '\\n'
```

On Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("retrotv-release.jks"))
```

Paste the entire Base64 output into the `RETROTV_KEYSTORE_BASE64` secret. Do not paste passwords into chat, issues, commits, or workflow logs.

## Release behavior

- Every push builds a debug APK artifact for testing.
- A signed release APK is published only after all four signing secrets are configured.
- Release APKs use the `v2.1.x` version series so the app version code stays higher than earlier `v2.0.x` builds.
- The key must remain the same for every future release.

## One-time migration from older APKs

Earlier RETROTV APKs were debug-signed by CI. Their signing key was not persisted reliably, so a new release signing key cannot be assumed to update those installations in place. For the first stable-signed release, you may need to uninstall the old app and install the new signed APK. Uninstalling normally clears the app's local settings and favourites. After that one-time migration, future releases signed with the same key can be installed as updates.
