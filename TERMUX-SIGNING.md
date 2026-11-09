# Sign TsukiPort locally in Termux

This source and CI workflow do not contain a keystore or signing password. GitHub Actions
produces an unsigned release APK. Create and keep your own signing key on your phone.

## 1. Install signing tools

In Termux:

```sh
pkg update
pkg upgrade
pkg install openjdk-21 apksigner aapt
```

Check that the commands exist:

```sh
java -version
keytool -help
apksigner --version
zipalign -h
```

## 2. Create your private key (once)

Keep it inside Termux's private home directory, not `/sdcard` or a Git repository:

```sh
mkdir -p "$HOME/.keys"
chmod 700 "$HOME/.keys"
keytool -genkeypair -v \
  -keystore "$HOME/.keys/tsukiport-release.jks" \
  -storetype JKS \
  -keyalg RSA -keysize 3072 -validity 10000 \
  -alias tsukiport
chmod 600 "$HOME/.keys/tsukiport-release.jks"
```

`keytool` prompts for the keystore password and key password. Do not put either password in
the command line, a script, a chat, or GitHub. Store them in your own password manager. Make
an encrypted offline backup of the JKS file. Every future update of this application ID must
use the same signing key.

## 3. Sign a downloaded unsigned APK

Copy the CI artifact to your Termux home directory. For example, if it is in Downloads:

```sh
cp /sdcard/Download/TsukiPort-0.3.0-dev-arm64-unsigned.apk "$HOME/"
```

Align first, then sign:

```sh
zipalign -v -P 16 -f 4 \
  "$HOME/TsukiPort-0.3.0-dev-arm64-unsigned.apk" \
  "$HOME/TsukiPort-0.3.0-dev-aligned.apk"

apksigner sign \
  --ks "$HOME/.keys/tsukiport-release.jks" \
  --ks-key-alias tsukiport \
  --out "$HOME/TsukiPort-0.3.0-dev-signed.apk" \
  "$HOME/TsukiPort-0.3.0-dev-aligned.apk"
```

`apksigner` prompts for the keystore password. Then verify the signature:

```sh
apksigner verify --verbose --print-certs "$HOME/TsukiPort-0.3.0-dev-signed.apk"
```

Install the signed APK from your file manager. The app ID is
`com.dev.tsukihimeandroid.dev`, so it installs separately from the original
`com.dev.tsukihimeandroid` app.

## Security

- Never upload the JKS or passwords to GitHub or ChatGPT.
- Do not reuse the previously generated development key from the earlier signing kit.
- Do not delete the only copy of your keystore.
- `zipalign` must run before `apksigner`; changing the APK after signing invalidates its
  signature.
