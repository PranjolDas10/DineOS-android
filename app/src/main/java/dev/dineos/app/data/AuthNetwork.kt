package dev.dineos.app.data

import dev.dineos.api.model.RefreshIn
import dev.dineos.api.model.TokenPair
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

private fun Request.isAuthCall() = url.encodedPath.startsWith("/v1/auth/")
private fun Request.withToken(access: String) = newBuilder().header("Authorization", "Bearer $access").build()

/** Adds the access token to every API call except sign-in and refresh themselves. */
class BearerInterceptor(private val session: Session) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val access = session.tokens.value?.access
        return chain.proceed(if (access == null || request.isAuthCall()) request else request.withToken(access))
    }
}

/** OkHttp calls this when a request comes back 401: trade the refresh token for a new pair
 * and retry once.
 *
 * The server rotates refresh tokens and treats a second use of one as theft, revoking the
 * whole session. So two requests that fail at the same moment must not both refresh: the
 * lock lets the first one do it, and the second finds the tokens already changed and just
 * retries with them. */
class TokenRefresher(
    private val session: Session,
    private val client: OkHttpClient, // a bare client: no interceptors, so no recursion
    private val baseUrl: HttpUrl,
    private val json: Json,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        // A 401 from sign-in is a wrong password; a 401 after a retry means the new token
        // failed too. Neither gets better by refreshing again.
        if (request.isAuthCall() || response.priorResponse != null) return null
        val sent = request.header("Authorization")?.removePrefix("Bearer ")
        synchronized(this) {
            val current = session.tokens.value ?: return null
            if (current.access != sent) return request.withToken(current.access) // someone else refreshed
            val fresh = refresh(current.refresh) ?: return null
            return request.withToken(fresh.access)
        }
    }

    /** The new pair, or null when this request can't be retried. Only a 401 ends the session
     * (the server refused the refresh token). Server trouble (5xx, 429) fails this one call and
     * a network failure throws: being offline is not being signed out. */
    private fun refresh(refreshToken: String): Tokens? {
        val body = json.encodeToString(RefreshIn.serializer(), RefreshIn(refreshToken))
            .toRequestBody("application/json".toMediaType())
        val call = Request.Builder().url(baseUrl.resolve("v1/auth/refresh")!!).post(body).build()
        client.newCall(call).execute().use { reply ->
            if (!reply.isSuccessful && reply.code != 401) return null
            val tokens = if (reply.isSuccessful) {
                json.decodeFromString(TokenPair.serializer(), reply.body.string()).let { Tokens(it.accessToken, it.refreshToken) }
            } else {
                null
            }
            // OkHttp runs this on its own thread, never the main one, so blocking here is fine.
            runBlocking { session.set(tokens) }
            return tokens
        }
    }
}
