package com.oasisfeng.island.model

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.oasisfeng.common.app.BaseAndroidViewModel
import com.oasisfeng.island.analytics.Analytics
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

fun BaseAndroidViewModel.interactive(context: Context, block: suspend CoroutineScope.() -> Unit) {
	viewModelScope.launch(CoroutineExceptionHandler { _, e -> handleException(context, tag, e) }, block = block)
}

fun <R> BaseAndroidViewModel.interactiveFuture(context: Context, block: suspend CoroutineScope.() -> R): CompletableFuture<R?> {
	return viewModelScope.future(block = block).exceptionally { t -> null.also { handleException(context, tag, t) }}
}

private fun handleException(context: Context, tag: String, t: Throwable) {
	if (t is CancellationException) return Unit.also { Log.i(tag, "Interaction canceled: ${t.message}") }
	Analytics().logAndReport(tag, "Unexpected internal error", t)
	Toast.makeText(context, "Internal error: " + t.message, Toast.LENGTH_LONG).show()
}
