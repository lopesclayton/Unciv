package com.unciv.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.URI

class WigoOnlineSafetyTest {
    private lateinit var previous: () -> Boolean
    @Before fun setUp() { previous = WigoOnlineSafety.permission; WigoOnlineSafety.permission = { false } }
    @After fun tearDown() { WigoOnlineSafety.permission = previous }

    @Test fun blocksDirectConnectionBeforeOpeningSocket() {
        try {
            WigoOnlineSafety.openConnection(URI("https://example.invalid").toURL())
            fail("Locked connection was accepted")
        } catch (error: IOException) { assertTrue(error.message!!.contains("locked")) }
    }

    @Test fun blocksExternalLinksWithoutGdxOrBrowser() {
        assertFalse(WigoOnlineSafety.openUri("https://example.invalid"))
    }

    @Test fun checksPermissionAgainAfterRevocation() {
        WigoOnlineSafety.permission = { true }
        WigoOnlineSafety.requireOnline()
        WigoOnlineSafety.permission = { false }
        try { WigoOnlineSafety.requireOnline(); fail("Revocation ignored") }
        catch (error: IOException) { assertTrue(error.message!!.contains("locked")) }
    }

    @Test fun ktorRejectsLockedRequest() = runBlocking {
        val client = HttpClient(CIO) { install(WigoOnlineGate) }
        try {
            client.get("https://example.invalid")
            fail("Locked Ktor request was accepted")
        } catch (error: IOException) { assertTrue(error.message!!.contains("locked")) }
        finally { client.close() }
    }

    @Test fun clientCanBeUsedAgainAfterParentRevokesAndReapproves() = runBlocking {
        // Local fixture only; no third-party game server or user account is contacted.
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val client = HttpClient(CIO) { install(WigoOnlineGate) }
        try {
            withTimeout(5000) {
                val url = "http://127.0.0.1:${server.address.port}/"
                WigoOnlineSafety.permission = { true }
                assertEquals("ok", client.get(url).bodyAsText())
                WigoOnlineSafety.permission = { false }
                WigoOnlineSafety.lockConnections()
                WigoOnlineSafety.permission = { true }
                assertEquals("ok", client.get(url).bodyAsText())
            }
        } finally { client.close(); server.stop(0) }
    }
}
