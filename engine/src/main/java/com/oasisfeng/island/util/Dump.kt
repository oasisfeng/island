package com.oasisfeng.island.util

import android.os.Debug
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Helper for dumping system service
 *
 * Created by Oasis on 2019-6-24.
 */
object Dump {

    suspend fun <T> systemService(service: String, vararg args: String, timeout: Long = 5_000, processor: (Sequence<String>) -> T): T? {
        val pipe = ParcelFileDescriptor.createPipe()
        pipe[1].use {
            if (! Debug.dumpService(service, it.fileDescriptor, if (args.isEmpty()) null else args)) return null
            return withContext(Dispatchers.IO) {   // IO thread to receive dump to avoid infinite blocking in large dump.
                withTimeout(timeout) {
                    ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).bufferedReader().useLines(processor)
                }
            }
        }
    }
}