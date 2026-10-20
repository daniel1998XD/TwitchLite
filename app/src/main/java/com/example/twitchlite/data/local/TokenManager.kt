package com.example.twitchlite.data.local

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

class TokenManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createSafeEncryptedSharedPreferences(appContext)

    private fun createSafeEncryptedSharedPreferences(context: Context): SharedPreferences {
        return try {
            buildEncryptedPrefs(context)
        } catch (e: Exception) {
            Log.w(
                TAG,
                "Falha ao abrir EncryptedSharedPreferences (${e.javaClass.simpleName}). " +
                        "Limpando arquivo corrompido e recriando chaves...",
                e
            )
            purgeCorruptedPreferencesAndKey(context)

            // Segunda tentativa após limpar o XML corrompido e o alias do KeyStore
            try {
                buildEncryptedPrefs(context)
            } catch (retryException: Exception) {
                Log.e(
                    TAG,
                    "Keystore indisponível neste dispositivo após reset. Usando fallback seguro.",
                    retryException
                )
                context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    private fun buildEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun purgeCorruptedPreferencesAndKey(context: Context) {
        // 1. Limpa o conteúdo do SharedPreferences de forma síncrona (commit)
        runCatching {
            context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }

        // 2. Deleta o arquivo físico .xml e remove a instância em cache do ContextImpl
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.deleteSharedPreferences(PREFS_FILE_NAME)
            } else {
                val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$PREFS_FILE_NAME.xml")
                if (prefsFile.exists()) {
                    prefsFile.delete()
                }
            }
        }

        // 3. Remove o alias da MasterKey no AndroidKeyStore caso ela também tenha sido invalidada
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }
    }

    fun saveAuthData(
        accessToken: String,
        refreshToken: String? = null,
        userId: String,
        username: String
    ) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .putString(KEY_REFRESH_TOKEN, refreshToken?.trim())
            .putString(KEY_USER_ID, userId.trim())
            .putString(KEY_USERNAME, username.trim())
            .apply()
    }

    fun updateTokens(accessToken: String, refreshToken: String?) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken.trim())
            if (!refreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, refreshToken.trim())
            }
            apply()
        }
    }

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    fun getUserId(): String? = prefs.getString(KEY_USER_ID, null)
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)

    fun saveClientId(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId.trim()).apply()
    }

    fun getClientId(): String? = prefs.getString(KEY_CLIENT_ID, null)

    fun isLoggedIn(): Boolean {
        return !getAccessToken().isNullOrBlank() && !getUserId().isNullOrBlank()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "TokenManager"
        private const val PREFS_FILE_NAME = "twitch_secure_prefs"

        private const val KEY_ACCESS_TOKEN = "oauth_access_token"
        private const val KEY_REFRESH_TOKEN = "oauth_refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "user_login"
        private const val KEY_CLIENT_ID = "custom_client_id"
    }
}