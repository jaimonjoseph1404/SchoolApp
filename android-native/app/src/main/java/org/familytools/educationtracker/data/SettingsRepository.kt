package org.familytools.educationtracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val THEME_DARK = booleanPreferencesKey("theme_dark")
        val PIN_ENABLED = booleanPreferencesKey("pin_lock_enabled")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { it[Keys.THEME_DARK] ?: false }
    val isPinLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.PIN_ENABLED] ?: false }
    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.THEME_DARK] = enabled }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = deriveHash(pin, salt)
        context.dataStore.edit {
            it[Keys.PIN_SALT] = salt.joinToString("") { b -> "%02x".format(b) }
            it[Keys.PIN_HASH] = hash
            it[Keys.PIN_ENABLED] = true
        }
    }

    suspend fun disablePin() {
        context.dataStore.edit {
            it[Keys.PIN_ENABLED] = false
            it[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.dataStore.data.first()
        val saltHex = prefs[Keys.PIN_SALT] ?: return false
        val storedHash = prefs[Keys.PIN_HASH] ?: return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return deriveHash(pin, salt) == storedHash
    }

    private fun deriveHash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 200_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return key.encoded.joinToString("") { "%02x".format(it) }
    }
}
