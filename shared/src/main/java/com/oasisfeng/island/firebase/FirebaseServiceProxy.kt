package com.oasisfeng.island.firebase

import android.util.Log
import io.fabric.sdk.android.services.network.HttpRequest
import okhttp3.OkHttpClient
import okhttp3.internal.huc.OkHttpsURLConnection
import java.net.*

/**
 * Inject reverse proxy for Firebase service hosts.
 *
 * Created by Oasis on 2019-7-28.
 */
object FirebaseServiceProxy {

    private val FIREBASE_HOSTS = arrayOf(
            "api.crashlytics.com", "settings.crashlytics.com", "reports.crashlytics.com",   // Crashlytics
            "firebaseremoteconfig.googleapis.com"                                           // Remote Config
    )

    @JvmStatic fun initialize(altHost: String) = HttpRequest.setConnectionFactory(object: HttpRequest.ConnectionFactory {

        override fun create(url: URL): HttpURLConnection {
            Log.d(TAG, "Create connection: $url")
            if (url.host !in FIREBASE_HOSTS) return url.openConnection() as HttpURLConnection
            return OkHttpsURLConnection(url, OkHttpClient.Builder().dns { host ->
                Log.d(TAG, "Resolving: $host")
                InetAddress.getAllByName(host).toMutableList().apply {
                    if (host in FIREBASE_HOSTS) try {
                        val altIp = InetAddress.getByName(altHost)
                        add(InetAddress.getByAddress(host, altIp.address))
                        Log.d(TAG, "Attach alt address: ${altIp.hostAddress}") }
                    catch (e: UnknownHostException) { Log.w(TAG, "Failed to resolve alt host.") }}
            }.build())
        }

        override fun create(url: URL, proxy: Proxy): HttpURLConnection {
            Log.d(TAG, "Create connection with proxy: $url")
            return url.openConnection(proxy) as HttpURLConnection
        }
    })

    private const val TAG = "Island.FSP"
}
