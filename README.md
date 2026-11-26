# Lockify

Android-only offline password manager project.

## Current milestone
This is the first UI scaffold. It is **not secure for real passwords yet**:
- PIN screen is only a visual prototype; it does not authenticate.
- Fingerprint button is a placeholder.
- The sample account rows are demo data.
- The add-login dialog currently keeps data only in memory and does not persist it.
- Do not enter real passwords in this build.

## Build
The GitHub Actions workflow builds a debug APK on push to `main` or via Actions > Build Lockify APK > Run workflow.

## Security implementation planned next
1. Generate a random vault data-encryption key.
2. Encrypt records with AES-256-GCM and a fresh random nonce per record.
3. Derive a PIN wrapping key with PBKDF2-HMAC-SHA256 and a unique random salt; never store the PIN.
4. Wrap the vault key with Android Keystore for biometric unlock.
5. Add authenticated encrypted backup/export and recovery flow.
6. Add tests for tampering, wrong PIN, lockout and process death.
