package org.audimmory.mobile.data.remote

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import org.audimmory.mobile.data.local.SessionStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renews Grimmory's short-lived access token when a request comes back 401.
 *
 * Grimmory access tokens expire after two hours, far shorter than an
 * offline-first app's session, so a 401 is routine rather than a sign-out. The
 * refresh call goes through a bare [OkHttpClient] without this authenticator or
 * the auth interceptor, so a failing refresh cannot recurse. Refreshes are
 * serialised: when several requests fail at once, the first renews the token
 * and the rest just retry with it.
 *
 * Covers every caller of the shared client — Retrofit, Coil covers, ExoPlayer
 * streaming and downloads — because they all use the same [OkHttpClient].
 */
@Singleton
class TokenAuthenticator
    @Inject
    constructor(
        private val sessionStore: SessionStore,
        private val json: Json,
    ) : Authenticator {
        private val lock = Any()
        private val refreshClient = OkHttpClient()

        override fun authenticate(
            route: Route?,
            response: Response,
        ): Request? {
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")
            // Never loop: one renewal attempt per original request.
            if (failedToken == null || response.priorResponse != null) return null

            synchronized(lock) {
                val current = runBlocking { sessionStore.currentToken() } ?: return null
                val token =
                    if (current != failedToken) {
                        current
                    } else {
                        refresh() ?: return null
                    }
                return response.request
                    .newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
        }

        private fun refresh(): String? {
            val refreshToken = runBlocking { sessionStore.currentRefreshToken() } ?: return null
            val baseUrl = runBlocking { sessionStore.currentServerUrl() }.trimEnd('/')
            val body =
                json
                    .encodeToString(GrimmoryRefreshRequest.serializer(), GrimmoryRefreshRequest(refreshToken))
                    .toRequestBody("application/json".toMediaType())
            val request =
                Request
                    .Builder()
                    .url("$baseUrl/api/v1/auth/refresh")
                    .post(body)
                    .build()
            return runCatching {
                refreshClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val tokens = json.decodeFromString(GrimmoryTokens.serializer(), response.body?.string() ?: return@use null)
                    runBlocking { sessionStore.saveTokens(tokens.accessToken, tokens.refreshToken ?: refreshToken) }
                    tokens.accessToken
                }
            }.getOrNull()
        }
    }
