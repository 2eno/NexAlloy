# NexAlloy Keygen

A small Android app that creates the release signing key for NexAlloy and backs it up to
Bitwarden, as a phone-only alternative to running `keytool` on a computer.

It generates the same key as
`keytool -genkeypair -keyalg RSA -keysize 4096 -validity 36500`: an RSA 4096 key with a
self-signed certificate in a PKCS12 keystore. The keystore and key share one random
32-character password made of letters and digits, since the workflows write it with an
unquoted shell `echo`.

## Get the app

Download `nexalloy-keygen-apk` from the latest run of the **Keygen app** workflow
(Actions tab), or build it:

```
./gradlew -p keygen assembleDebug
```

It is debug-signed, which is fine: it is a tool, not something to update.

## Use it

1. Tap **Generate keystore**.
2. **Back up to Bitwarden**: pick a file password and tap **Save Bitwarden export…**. This saves
   a password-protected Bitwarden JSON export with one secure note:
   - notes: the keystore as base64, which is exactly the `KEYSTORE` secret
   - fields: `KEYSTORE_PASSWORD`, `KEYSTORE_ALIAS`, `KEYSTORE_ALIAS_PASSWORD` and the
     certificate's SHA-256 fingerprint

   Import it at vault.bitwarden.com → Tools → Import data, format **Bitwarden (json)**, enter
   the file password, check the item is there, then delete the file. Bitwarden's mobile apps
   can't import, so use the web vault (a phone browser works).
3. **Add the GitHub secrets**: the copy buttons put each value on the clipboard, marked
   sensitive so keyboards don't remember it. Paste them as repository secrets under
   Settings → Secrets and variables → Actions.
4. Optional: **Save keystore file…** for signed builds on a computer with `signing.properties`.

The key lives only in the app's memory. Closing the app loses it, so finish the backup first.
The screen is excluded from screenshots and the recent-apps preview.

## Restoring from Bitwarden

The notes field is the base64 keystore. To get the file back:

```
base64 -d notes.txt > nexalloynext.jks
```
