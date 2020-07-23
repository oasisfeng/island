package com.oasisfeng.island.model

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.oasisfeng.common.app.BaseAndroidViewModel
import com.oasisfeng.island.analytics.analytics
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

fun BaseAndroidViewModel.interactive(context: Context, block: suspend () -> Unit) {
	viewModelScope.launch(CoroutineExceptionHandler { _, e -> handleException(context, tag, e) }, block = { block() })
}

fun <R> BaseAndroidViewModel.interactiveFuture(context: Context, block: suspend CoroutineScope.() -> R): CompletableFuture<R> {
	return viewModelScope.future(CoroutineExceptionHandler { _, e -> handleException(context, tag, e) }, block = block)
}

private fun handleException(context: Context, tag: String, t: Throwable) {
	analytics().logAndReport(tag, "Unexpected internal error", t)
	Toast.makeText(context, "Internal error: " + t.message, Toast.LENGTH_LONG).show()
}
