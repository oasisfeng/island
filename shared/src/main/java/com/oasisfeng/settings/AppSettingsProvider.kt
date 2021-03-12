package com.oasisfeng.settings

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.core.content.edit
import com.oasisfeng.island.shared.BuildConfig

/**
 * Encapsulate the complexity of building app settings.
 *
 * 1. Settings only need to be defined just once, anywhere in your code base (usually within its controller class).
 * 2. Settings storage layer (SharedPreferences) is read from and written to, only through this content provider.
 *
 * Created by Oasis on 2016/7/15.
 */
class AppSettingsProvider : ContentProvider() {

	override fun onCreate(): Boolean {    // Keep this method as fast as possible.
		if (BuildConfig.DEBUG) try {
			val info = context().packageManager.getProviderInfo(ComponentName(context(), javaClass), 0)
			check(!info.multiprocess) { "Multi-process mode is not supported." }
		} catch (e: PackageManager.NameNotFoundException) {
			throw IllegalStateException("Provider is not declared correctly in AndroidManifest.xml.", e)
		}
		return true
	}

	override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
		val key = validateOptionUri(uri) ?: return null
		if (key !in mSharedPrefs) return null
		val value = mSharedPrefs.all[key]           // TODO: Avoid calling getAll() every time.
		val cursor = MatrixCursor(arrayOf("value")) // TODO: Simpler Cursor implementation
		cursor.addRow(arrayOf(if (value is Boolean) if (value) 1 else 0 else value)) // Convert boolean to int, because boolean is not supported by Cursor
		return cursor
	}

	override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
		val key = validateOptionUri(uri) ?: return 0
		val value = (values ?: return 0)[null]
		val context = context()
		mSharedPrefs.edit {
			when (value) {
				null ->       remove(key)
				is Boolean -> putBoolean(key, value)
				is String ->  putString(key, value)
				is Int ->     putInt(key, value)
				is Long ->    putLong(key, value)
				is Float ->   putFloat(key, value)
				else -> { Log.e(TAG, "Unexpected value type: " + value.javaClass.name); return 0 }}}
		context.contentResolver.notifyChange(uri, null)
		broadcastSettingChange(key)         // For manifest receivers
		return 1
	}

	private fun broadcastSettingChange(key: String) {
		val intent = Intent(ACTION_SETTING_CHANGED, Uri.fromParts(SCHEME, key, null))
		val uid = Process.myUid(); val context = context()
		for (resolve in context.packageManager.queryBroadcastReceivers(intent, 0)) {
			val receiver = resolve.activityInfo
			if (receiver.applicationInfo.uid != uid) continue
			context.sendBroadcast(intent.setComponent(ComponentName(receiver.packageName, receiver.name))) }
	}

	private fun validateOptionUri(uri: Uri)
			=  uri.pathSegments?.takeIf { it.size == 1 }?.get(0) ?: null.also { Log.w(TAG, "Unsupported URI: $uri") }

	/** Must never be called in constructors  */
	private fun context() = super.getContext()!!

	override fun getType(uri: Uri): String? = null
	override fun insert(uri: Uri, values: ContentValues?): Uri? = null
	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0

	private val mSharedPrefs by lazy { @Suppress("DEPRECATION")
		android.preference.PreferenceManager.getDefaultSharedPreferences(context()) }
}

private const val ACTION_SETTING_CHANGED = "com.oasisfeng.action.SETTING_CHANGED"
private const val SCHEME = "setting"

private const val TAG = "SettingsProvider"
