package com.unciv.logic

import com.badlogic.gdx.Gdx
import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.SendingRequest
import io.ktor.client.plugins.api.createClientPlugin
import kotlinx.coroutines.cancelChildren
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.CopyOnWriteArrayList

/** Android game networking only. Ads/UMP have their separate child-safe policy. */
object WigoOnlineSafety {
    @Volatile var requestParent: () -> Unit = {}
    @Volatile var permission: () -> Boolean = {
        // Background workers may run before Gdx exists. Android must fail closed.
        System.getProperty("java.vm.name") != "Dalvik"
    }
    private val clients = CopyOnWriteArrayList<WeakReference<HttpClient>>()
    private val connections = CopyOnWriteArrayList<WeakReference<HttpURLConnection>>()

    fun allowed() = permission()
    fun requireOnline() {
        if (!allowed()) throw IOException("Online features are locked. Ask an adult to use Privacy > Parental controls.")
    }
    fun register(client: HttpClient) { clients.add(WeakReference(client)) }
    fun lockConnections() {
        clients.forEach { it.get()?.coroutineContext?.cancelChildren() }
        connections.forEach { it.get()?.disconnect() }
        clients.removeAll { it.get() == null }
        connections.clear()
    }
    fun openConnection(url: URL): URLConnection {
        requireOnline()
        val connection = url.openConnection()
        if (connection is HttpURLConnection) connections.add(WeakReference(connection))
        requireOnline()
        return connection
    }
    fun openUri(uri: String): Boolean {
        if (!allowed()) { requestParent(); return false }
        return Gdx.net.openURI(uri)
    }
}

val WigoOnlineGate = createClientPlugin("WigoOnlineGate") {
    WigoOnlineSafety.register(client)
    onRequest { _, _ -> WigoOnlineSafety.requireOnline() }
    on(SendingRequest) { _, _ -> WigoOnlineSafety.requireOnline() }
}
