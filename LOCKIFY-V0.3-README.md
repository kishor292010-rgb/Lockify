# Lockify v0.3 source update

This is a source patch for the existing `com.lockify.app` Android project.

## Included
- Fingerprint unlock backed by Android Keystore AES-GCM key requiring strong biometric authentication.
- Immediate lock when app leaves foreground.
- Persistent PIN failed-attempt delay after five failures, with capped exponential backoff.
- Password generator (8–64 characters).
- Login edit/delete, search and user-defined categories.
- Encrypted backup export/import using a separate passphrase (PBKDF2-HMAC-SHA256 + AES-256-GCM).
- Existing `LoginEntry` data remains readable; missing category values default to `Other`.

## Important
- This is an integration source patch, not a verified APK. Run GitHub Actions before installing.
- Test on a spare/test vault first. Keep an independent copy of your current repository and APK.
- Biometric unlock depends on Android strong biometric hardware and enrolled fingerprint. If biometric enrollment changes, the Keystore key can become invalid; PIN remains the fallback.
- Backup passphrase is separate from the vault PIN. Losing it means the backup cannot be restored.
- PIN lockout is local-app protection, not a defense against a determined attacker with device/root access.
- Password generator uses Kotlin's platform random utility in this patch; for high-assurance password generation replace it with `SecureRandom`.
- Immediate foreground lock can be inconvenient if the app is interrupted; it is chosen to avoid retaining the vault key while backgrounded.
- Do not store production credentials until build, functional testing, backup/restore testing and independent security review are complete.

## Apply
Copy the included files into the matching paths in your `~/Lockify` project, preserving all other files (especially launcher resources, workflow, and branding). Then commit and push. The About attribution `Made by Shadowking` and launcher icon resources remain in your existing project and are not replaced by this patch.
