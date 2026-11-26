# Lockify v0.2 security milestone

This patch adds a SecureVault storage engine:
- PIN-derived wrapping key using PBKDF2-HMAC-SHA256 (310,000 iterations)
- Random 256-bit vault key
- AES-256-GCM authenticated encryption with a fresh 96-bit nonce for each save
- Encrypted data file in app-private internal storage

This file is an integration patch, not a complete replacement APK. The current MainActivity must be updated to use SecureVault for setup, unlock, load, save and lock. Fingerprint/Android Keystore, lockout policy, encrypted backup and independent security review remain future work. Do not store real credentials until the integration is built and tested. Losing the PIN means losing access to the vault.
