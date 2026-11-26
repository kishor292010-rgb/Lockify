package com.lockify.app

import android.content.Context
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class LoginEntry(
    val id: String,
    val title: String,
    val username: String,
    val password: String,
    val category: String = "Other"
)

class SecureVault(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("lockify_secure_meta", Context.MODE_PRIVATE)
    private val file = File(app.filesDir, "vault.enc")
    private val rng = SecureRandom()
    private var key: ByteArray? = null

    fun configured() = prefs.contains("salt") && prefs.contains("nonce") && prefs.contains("wrapped")
    fun isBiometricEnabled() = prefs.contains("bio_iv") && prefs.contains("bio_blob")
    fun lock() { key?.fill(0); key = null }
    fun isUnlocked() = key != null

    fun create(pin: CharArray) {
        require(pin.size in 6..12 && pin.all(Char::isDigit))
        check(!configured())
        val salt = bytes(16)
        val vaultKey = bytes(32)
        val wrappingKey = derive(pin, salt)
        val (nonce, ciphertext) = encrypt(wrappingKey, vaultKey)
        check(prefs.edit().putString("salt", b64(salt))
            .putString("nonce", b64(nonce)).putString("wrapped", b64(ciphertext)).commit())
        key = vaultKey
        save(emptyList())
        wrappingKey.fill(0)
    }

    @Synchronized fun unlock(pin: CharArray) {
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong("locked_until", 0L)
        check(now >= lockedUntil) { "LOCKOUT:${(lockedUntil - now).coerceAtLeast(1)}" }
        try {
            val salt = dec(prefs.getString("salt", null)!!)
            val nonce = dec(prefs.getString("nonce", null)!!)
            val wrapped = dec(prefs.getString("wrapped", null)!!)
            val derived = derive(pin, salt)
            val unwrapped = try { decrypt(derived, nonce, wrapped) } finally { derived.fill(0) }
            check(unwrapped.size == 32)
            key?.fill(0)
            key = unwrapped
            prefs.edit().putInt("failed_attempts", 0).putLong("locked_until", 0L).apply()
        } catch (e: Exception) {
            val attempts = prefs.getInt("failed_attempts", 0) + 1
            val delay = if (attempts < 5) 0L else (30_000L * (1L shl (attempts - 5).coerceAtMost(5))).coerceAtMost(30 * 60_000L)
            prefs.edit().putInt("failed_attempts", attempts)
                .putLong("locked_until", if (delay > 0) System.currentTimeMillis() + delay else 0L).apply()
            throw e
        }
    }

    fun entries(): List<LoginEntry> {
        val k = key ?: error("Vault locked")
        if (!file.exists()) return emptyList()
        val obj = JSONObject(file.readText())
        val raw = decrypt(k, dec(obj.getString("n")), dec(obj.getString("c")))
        val a = JSONArray(String(raw, Charsets.UTF_8))
        raw.fill(0)
        return (0 until a.length()).map { i ->
            a.getJSONObject(i).let {
                LoginEntry(it.getString("id"), it.getString("title"), it.getString("username"),
                    it.getString("password"), it.optString("category", "Other"))
            }
        }
    }

    fun save(items: List<LoginEntry>) {
        val k = key ?: error("Vault locked")
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().put("id", it.id).put("title", it.title).put("username", it.username)
                .put("password", it.password).put("category", it.category))
        }
        val plain = a.toString().toByteArray(Charsets.UTF_8)
        val (nonce, ciphertext) = encrypt(k, plain)
        plain.fill(0)
        val tmp = File(file.parentFile, "vault.tmp")
        tmp.writeText(JSONObject().put("n", b64(nonce)).put("c", b64(ciphertext)).toString())
        if (!tmp.renameTo(file)) { tmp.copyTo(file, true); tmp.delete() }
    }

    // Call with a Cipher configured for ENCRYPT_MODE and pass that same Cipher to BiometricPrompt.
    fun biometricEncryptCipher(): Cipher {
        val k = getOrCreateBiometricKey()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k) }
    }

    // Call with a Cipher configured for DECRYPT_MODE and pass that same Cipher to BiometricPrompt.
    fun biometricDecryptCipher(): Cipher {
        check(isBiometricEnabled()) { "Biometric unlock is not enabled" }
        val k = getBiometricKey()
        val iv = dec(prefs.getString("bio_iv", null)!!)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv)) }
    }

    fun finishBiometricEnrollment(cipher: Cipher) {
        val k = key ?: error("Unlock with PIN first")
        val encrypted = cipher.doFinal(k)
        check(prefs.edit().putString("bio_iv", b64(cipher.iv))
            .putString("bio_blob", b64(encrypted)).commit())
    }

    fun finishBiometricUnlock(cipher: Cipher) {
        val encrypted = dec(prefs.getString("bio_blob", null)!!)
        val unwrapped = cipher.doFinal(encrypted)
        check(unwrapped.size == 32)
        key?.fill(0)
        key = unwrapped
    }

    fun disableBiometric() {
        prefs.edit().remove("bio_iv").remove("bio_blob").apply()
        try { getKeyStore().deleteEntry(BIO_ALIAS) } catch (_: Exception) {}
    }

    fun exportBackup(passphrase: CharArray): ByteArray {
        require(passphrase.size >= 10) { "Backup passphrase must be at least 10 characters" }
        val data = entries()
        val arr = JSONArray()
        data.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title)
            .put("username", it.username).put("password", it.password).put("category", it.category)) }
        val salt = bytes(16)
        val derived = deriveBackup(passphrase, salt)
        val (iv, encrypted) = encrypt(derived, arr.toString().toByteArray(Charsets.UTF_8))
        derived.fill(0)
        return JSONObject().put("format", "LOCKIFY_BACKUP_1").put("salt", b64(salt))
            .put("iv", b64(iv)).put("data", b64(encrypted)).toString().toByteArray(Charsets.UTF_8)
    }

    fun importBackup(backup: ByteArray, passphrase: CharArray, replace: Boolean) {
        require(passphrase.size >= 10) { "Backup passphrase must be at least 10 characters" }
        val obj = JSONObject(String(backup, Charsets.UTF_8))
        require(obj.getString("format") == "LOCKIFY_BACKUP_1") { "Unsupported backup file" }
        val derived = deriveBackup(passphrase, dec(obj.getString("salt")))
        val plain = try { decrypt(derived, dec(obj.getString("iv")), dec(obj.getString("data"))) }
            finally { derived.fill(0) }
        val arr = JSONArray(String(plain, Charsets.UTF_8))
        plain.fill(0)
        val imported = (0 until arr.length()).map { i ->
            arr.getJSONObject(i).let { LoginEntry(it.getString("id"), it.getString("title"),
                it.getString("username"), it.getString("password"), it.optString("category", "Other")) }
        }
        val combined = if (replace) imported else (entries() + imported).distinctBy { it.id }
        save(combined)
    }

    private fun getOrCreateBiometricKey(): SecretKey {
        val ks = getKeyStore()
        if (ks.containsAlias(BIO_ALIAS)) return (ks.getEntry(BIO_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val builder = KeyGenParameterSpec.Builder(BIO_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun getBiometricKey(): SecretKey =
        (getKeyStore().getEntry(BIO_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey

    private fun getKeyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun derive(pin: CharArray, salt: ByteArray) = deriveKey(pin, salt, 310_000)
    private fun deriveBackup(pass: CharArray, salt: ByteArray) = deriveKey(pass, salt, 400_000)
    private fun deriveKey(password: CharArray, salt: ByteArray, rounds: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, rounds, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
    }
    private fun encrypt(k: ByteArray, p: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = bytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, nonce))
        return nonce to cipher.doFinal(p)
    }
    private fun decrypt(k: ByteArray, nonce: ByteArray, p: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(p)
    }
    private fun bytes(n: Int) = ByteArray(n).also(rng::nextBytes)
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun dec(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object { private const val BIO_ALIAS = "lockify_biometric_v1" }
}
