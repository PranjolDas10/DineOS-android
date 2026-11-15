package dev.dineos.app

import dev.dineos.app.data.BearerInterceptor
import dev.dineos.app.data.Session
import dev.dineos.app.data.TokenRefresher
import dev.dineos.app.data.TokenVault
import dev.dineos.app.data.Tokens
import dev.dineos.app.di.ApiJson
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

private class MemoryVault(var saved: Tokens?) : TokenVault {
    override suspend fun read() = saved
    override suspend fun write(tokens: Tokens?) {
        saved = tokens
    }
}

/** A server that accepts only the access token "new", and whose refresh endpoint answers
 * with [refreshCode]. It counts refresh calls: the server treats a refresh token used twice
 * as stolen, so the count is the thing under test. */
class TokenRefresherTest {
    private val server = MockWebServer()
    private val refreshes = AtomicInteger()
    private var refreshCode = 200
    private val vault = MemoryVault(Tokens("old", "refresh-1"))
    private val session = Session(vault)
    private lateinit var client: OkHttpClient

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.url.encodedPath == "/v1/auth/refresh" -> {
                    refreshes.incrementAndGet()
                    Thread.sleep(50) // long enough for the other requests to pile up behind the lock
                    val pair = """{"access_token":"new","refresh_token":"refresh-2","token_type":"bearer","expires_in":900}"""
                    MockResponse(code = refreshCode, body = if (refreshCode == 200) pair else "{}")
                }
                request.headers["Authorization"] == "Bearer new" -> MockResponse(code = 200, body = "ok")
                else -> MockResponse(code = 401)
            }
        }
        server.start()
        runBlocking { session.load() }
        val bare = OkHttpClient()
        client = bare.newBuilder()
            .addInterceptor(BearerInterceptor(session))
            .authenticator(TokenRefresher(session, bare, server.url("/"), ApiJson))
            .build()
    }

    @After
    fun stop() = server.close()

    private fun get(path: String = "/v1/checklists/today") =
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.code }

    @Test
    fun `an expired access token is refreshed and the request retried`() {
        assertEquals(200, get())
        assertEquals(Tokens("new", "refresh-2"), session.tokens.value)
        assertEquals("the new pair is saved for the next launch", Tokens("new", "refresh-2"), vault.saved)
    }

    @Test
    fun `requests failing together refresh once`() {
        val codes = MutableList(8) { 0 }
        val go = CountDownLatch(1)
        val threads = List(8) { i -> thread { go.await(); codes[i] = get() } }
        go.countDown()
        threads.forEach { it.join() }
        assertEquals(List(8) { 200 }, codes)
        assertEquals(1, refreshes.get())
    }

    @Test
    fun `a refused refresh token signs out`() {
        refreshCode = 401
        assertEquals(401, get())
        assertNull(session.tokens.value)
        assertNull(vault.saved)
    }

    @Test
    fun `server trouble during refresh keeps the session`() {
        refreshCode = 503
        assertEquals(401, get())
        assertEquals(Tokens("old", "refresh-1"), session.tokens.value)
    }

    @Test
    fun `a wrong password is not a reason to refresh`() {
        assertEquals(401, get("/v1/auth/login"))
        assertEquals(0, refreshes.get())
    }
}
