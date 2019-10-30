package com.oasisfeng.island.firebase

import android.util.Log
import androidx.annotation.WorkerThread
import com.oasisfeng.island.util.Hacks
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.*
import kotlin.concurrent.thread

/**
 * Inject reverse proxy for Firebase service hosts.
 *
 * Created by Oasis on 2019-7-28.
 */
object FirebaseServiceProxy {

    private val CRASHLYTICS_HOSTS = arrayOf(
            "api.crashlytics.com", "settings.crashlytics.com", "reports.crashlytics.com",   // Crashlytics
            "firebaseremoteconfig.googleapis.com"                                           // Remote Config
    )

    @JvmStatic fun initialize(proxyHost: String) {
        thread(start = true) { try { doInitialize(proxyHost) } catch (e: Exception) { Log.e(TAG, "Error initializing", e) }}
    }

    @WorkerThread private fun doInitialize(proxyHost: String) {
        val proxy = try { InetAddress.getByName(proxyHost) } catch (e: UnknownHostException) {
            Log.w(TAG, "Failed to resolve host, skip initialization.")
            return
        }

        for (host in CRASHLYTICS_HOSTS) try {
            var addresses = InetAddress.getAllByName(host)
            addresses = Arrays.copyOf(addresses, addresses.size + 1)
            addresses[addresses.size - 1] = proxy
            DnsCacheInjector.inject(host, addresses)
        } catch (e: UnknownHostException) {
            Log.w(TAG, "Failed to resolve $host")
            DnsCacheInjector.inject(host, arrayOf(proxy))
        }
    }

    private const val TAG = "Island.FSP"

    object DnsCacheInjector {

        @WorkerThread fun inject(host: String, addresses: Array<InetAddress>): String? {
            if (Hacks.InetAddress_addressCache.isAbsent) return "InetAddress.addressCache"
            if (Hacks.AddressCache_put == null) return "AddressCache.put()"

            val addressCache = Hacks.InetAddress_addressCache.get() ?: return "InetAddress.addressCache == null"
            Hacks.AddressCache_put.invoke(host, 0, addresses).on(addressCache)

            /* Extend the expiry, as default expiry 2s is too short for retries of failed connection. */
            if (Hacks.AddressCache_cache.isAbsent) return "AddressCache.cache"
            if (Hacks.BasicLruCache_map.isAbsent) return "BasicLruCache.map"
            if (Hacks.AddressCacheKey_mHostname.isAbsent) return "AddressCacheKey.mHostname"
            if (Hacks.AddressCacheEntry_expiryNanos.isAbsent) return "AddressCacheEntry.expiryNanos"
            val cache = Hacks.AddressCache_cache.get(addressCache) ?: return "AddressCache.cache == null"
            val map = Hacks.BasicLruCache_map.get(cache) ?: return "AddressCache.cache.map == null"
            for (entry in map.entries) {
                val hostname = Hacks.AddressCacheKey_mHostname.get(entry.key ?: continue)
                if (host != hostname) continue
                Hacks.AddressCacheEntry_expiryNanos.set(entry.value ?: continue, java.lang.Long.MAX_VALUE)
                break
            }
            return null
        }
    }
}
