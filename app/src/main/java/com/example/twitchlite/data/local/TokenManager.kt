package com.example.twitchlite.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "twitch_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

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
        private const val KEY_ACCESS_TOKEN = "oauth_access_token"
        private const val KEY_REFRESH_TOKEN = "oauth_refresh_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "user_login"
        private const val KEY_CLIENT_ID = "custom_client_id"
    }
}