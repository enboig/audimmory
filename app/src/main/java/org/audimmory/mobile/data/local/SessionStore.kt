package org.audimmory.mobile.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.audimmory.mobile.BuildConfig
import org.audimmory.mobile.core.DateTimeFormat
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "session")

/**
 * Persists the Grimmory session: access and refresh tokens, server base URL,
 * and the signed-in account's username, display name and download permission.
 *
 * Grimmory access tokens live two hours and are renewed with the refresh token
 * (see [org.audimmory.mobile.data.remote.TokenAuthenticator]). On a rooted
 * device DataStore is not a hardware-backed secret store, but it is
 * app-private. Encrypting at rest is a future hardening step.
 */
@Singleton
class SessionStore
    @Inject
    constructor(
        private val context: Context,
    ) {
        private object Keys {
            val TOKEN = stringPreferencesKey("token")
            val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
            val SERVER_URL = stringPreferencesKey("server_url")
            val USERNAME = stringPreferencesKey("username")
            val DISPLAY_NAME = stringPreferencesKey("display_name")
            val CAN_DOWNLOAD = booleanPreferencesKey("can_download")
        }

        val token: Flow<String?> = context.dataStore.data.map { it[Keys.TOKEN] }

        val serverUrl: Flow<String> =
            context.dataStore.data.map { it[Keys.SERVER_URL] ?: BuildConfig.DEFAULT_SERVER_URL }

        val username: Flow<String?> = context.dataStore.data.map { it[Keys.USERNAME] }
        val displayName: Flow<String?> = context.dataStore.data.map { it[Keys.DISPLAY_NAME] }
        val canDownload: Flow<Boolean> = context.dataStore.data.map { it[Keys.CAN_DOWNLOAD] ?: false }

        // Grimmory has no per-user sort-prefix or date/time format settings the
        // app can read, so these keep the app defaults.
        val ignorePrefixesWhenSorting: Flow<Boolean> = context.dataStore.data.map { false }
        val dateFormat: Flow<String> = context.dataStore.data.map { DateTimeFormat.DEFAULT_DATE_FORMAT }
        val timeFormat: Flow<String> = context.dataStore.data.map { DateTimeFormat.DEFAULT_TIME_FORMAT }

        suspend fun currentToken(): String? = token.first()

        suspend fun currentRefreshToken(): String? = context.dataStore.data.first()[Keys.REFRESH_TOKEN]

        suspend fun currentServerUrl(): String = serverUrl.first()

        suspend fun saveSession(
            token: String,
            refreshToken: String?,
            serverUrl: String,
        ) {
            context.dataStore.edit {
                it[Keys.TOKEN] = token
                if (refreshToken == null) it.remove(Keys.REFRESH_TOKEN) else it[Keys.REFRESH_TOKEN] = refreshToken
                it[Keys.SERVER_URL] = serverUrl
            }
        }

        /** Stores renewed tokens; called from OkHttp's authenticator thread. */
        suspend fun saveTokens(
            token: String,
            refreshToken: String,
        ) {
            context.dataStore.edit {
                // Signed out while the refresh was in flight: do not resurrect the session.
                if (it[Keys.TOKEN] == null) return@edit
                it[Keys.TOKEN] = token
                it[Keys.REFRESH_TOKEN] = refreshToken
            }
        }

        suspend fun setAccount(
            username: String,
            displayName: String,
            canDownload: Boolean,
        ) {
            context.dataStore.edit {
                it[Keys.USERNAME] = username
                it[Keys.DISPLAY_NAME] = displayName
                it[Keys.CAN_DOWNLOAD] = canDownload
            }
        }

        suspend fun setServerUrl(serverUrl: String) {
            context.dataStore.edit { it[Keys.SERVER_URL] = serverUrl }
        }

        suspend fun clear() {
            context.dataStore.edit {
                it.remove(Keys.TOKEN)
                it.remove(Keys.REFRESH_TOKEN)
                it.remove(Keys.USERNAME)
                it.remove(Keys.DISPLAY_NAME)
                it.remove(Keys.CAN_DOWNLOAD)
            }
        }
    }
