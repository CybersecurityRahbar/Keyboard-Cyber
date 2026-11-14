# Stable Android app signing

Every distributable update must use the same application ID, the same persistent signing key, and a higher versionCode.

- Application ID: `com.cybersecurityrahbar.keyboardcyber`
- Current code: `2` (version `0.2.0`)
- Keystore format: PKCS12
- Alias: `keyboard-cyber`

## GitHub Actions secrets

Create these four repository secrets under **Settings → Secrets and variables → Actions**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | The complete one-line Base64 content in the provided `KEYSTORE_BASE64.txt` file |
| `KEYSTORE_PASSWORD` | The password supplied with the keystore |
| `KEY_ALIAS` | `keyboard-cyber` |
| `KEY_PASSWORD` | Same as `KEYSTORE_PASSWORD` |

The private key is intentionally not committed to this public repository. Keep an offline backup. If it is lost, future updates cannot be signed with the same identity.

Once all four secrets are configured, run **Actions → Android build → Run workflow**. CI will upload an APK only when the persistent signing key was supplied. Without the secrets, CI may still compile but it will not publish a temporary-debug-signed artifact as an update.

## One-time migration from the first experimental APK

The first distributed v0.1.0 APK from CI run #18 was signed using a temporary GitHub Actions Android Debug certificate. Its private key was not saved. Its SHA-256 certificate fingerprint was:

`88:92:EA:86:50:F3:56:D0:00:FF:43:F7:9B:C1:39:47:02:5D:12:F7:93:20:E3:AE:B0:37:D7:9C:1E:85:CB:04`

The new persistent signing key's certificate fingerprint is:

`B5:4E:76:F7:56:D7:98:EA:F9:6D:E7:E8:91:61:20:7F:ED:0A:E4:1F:EE:FA:99:CF:B7:CD:A9:FB:B7:E0:C7:F0`

The private key for the old signature cannot be reconstructed from the APK. Therefore Android cannot accept the stable-key APK as an in-place update over the old test installation. A one-time uninstall/reinstall may be needed and uninstalling erases the old private app database and preferences. Do not uninstall the old build while it contains data you need; v0.1.0 did not yet include export/restore. After installing the first stable-signed APK, future builds signed with this same key and a greater versionCode will update in place and retain local data.
