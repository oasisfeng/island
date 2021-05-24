package com.oasisfeng.island.shuttle

import android.app.Activity
import android.content.*
import android.content.ContentResolver.SCHEME_CONTENT
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.database.Cursor
import android.net.Uri
import android.os.*
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.toId
import java.io.Serializable
import java.util.*

class ShuttleProvider: ContentProvider() {

	companion object {

		fun <R> call(context: Context, profile: UserHandle, function: ContextFun<R>): ShuttleResult<R> {
			val bundle = Bundle(1).apply { putParcelable(null, Closure(function)) }
			val uri = buildCrossProfileUri(profile.toId())
			try { return ShuttleResult(context.contentResolver.call(uri, function.javaClass.name, null, bundle)) }
			catch (e: RuntimeException) { // "SecurityException" or "IllegalArgumentException: Unknown authority 0@..." if shuttle is not ready
				if ((e is SecurityException || e is IllegalArgumentException) && ! isReady(context, profile)) @Suppress("UNCHECKED_CAST")
					return ShuttleResult.NOT_READY as ShuttleResult<R>
				throw e }
		}

		fun isReady(c: Context, profile: UserHandle) = c.isUriPermissionGranted(buildCrossProfileUri(profile.toId()))

		private fun Context.isUriPermissionGranted(uri: Uri, uid: Int = Process.myUid()) =
				checkUriPermission(uri, 0, uid, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == PERMISSION_GRANTED

		fun initialize(context: Context) {
			Log.v(TAG, "Initializing...")
			if (Users.isParentProfile())
				return Users.getProfilesManagedByIsland().forEach {
					if (isReady(context, it)) Log.d(TAG, "Shuttle to profile ${it.toId()}: ready")
					else Log.i(TAG, "Shuttle to profile ${it.toId()}: not ready") }

			if (isReady(context, Users.getParentProfile())) Log.d(TAG, "Shuttle to parent profile: ready")
			else Log.i(TAG, "Shuttle to parent profile: not ready")

			initializeInIsland(context)
		}

		private fun initializeInIsland(context: Context) {
			val uri = Uri.parse(CONTENT_URI)
			if (context.isUriPermissionGranted(uri, uid = UserHandles.getAppId(Process.myUid())))
				return Unit.also { Log.d(TAG, "Shuttle in ${Users.current().toId()}: ready") }

			Log.d(TAG, "Shuttle in ${Users.current().toId()}: establishing...")
			ShuttleCarrierActivity.sendToParentProfileQuietlyIfPossible(context) {
				addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
				clipData = ClipData(TAG, emptyArray(), ClipData.Item(uri)) }
		}

		@OwnerUser @ProfileUser fun collect(context: Context, intent: Intent) {
			(intent.data ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri)?.also {    // data or clipData
				Log.d(TAG, "[${Users.currentId()}] Received: $it")
				context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
		}

		private fun buildCrossProfileUri(profileId: Int) =
				Uri.Builder().scheme(SCHEME_CONTENT).encodedAuthority("$profileId@$AUTHORITY").build()

		private const val AUTHORITY = "com.oasisfeng.island.shuttle"
		const val CONTENT_URI = "content://$AUTHORITY"
	}

	override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
		val closure = requireNotNull(extras?.apply { classLoader = Closure::class.java.classLoader }?.getParcelable<Closure>(null)) { "Missing extra" }
		val result = closure.invoke(context).also { Log.i(TAG, "Call: $method()=$it") }
		return if (result == null || result == Unit) null else Bundle().apply { put(null, result) }
	}

	override fun onCreate() = true.also { initialize(context) }

	override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
	override fun getType(uri: Uri): String? = null
	override fun insert(uri: Uri, values: ContentValues?): Uri? = null
	override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
	override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

	val context: Context; @JvmName("context") get() = getContext()!!

	class ReceiverActivity: Activity() {

		override fun onCreate(savedInstanceState: Bundle?) {
			super.onCreate(savedInstanceState)
			finish()
			intent.data?.also { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
		}
	}
}

@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class ShuttleResult<R>(private val bundle: Bundle?) {

	companion object { internal val NOT_READY = ShuttleResult<Any>(Bundle()) }

	fun isNotReady() = bundle === NOT_READY.bundle
	@Suppress("UNCHECKED_CAST") fun get(): R = bundle?.get(null) as R
	override fun toString() = when(this) {
		NOT_READY -> "ShuttleResult{NOT_READY}"
		else -> "ShuttleResult{" + bundle.toString() + "}" }
}

//private fun Bundle?.toStr() = this?.toString()?.substring(6) ?: "null"

private typealias ContextFun<R> = Context.() -> R?

private fun Bundle.put(key: String?, value: Any?) {
	when (value) {
		null -> putString(key, null)
		is Boolean -> putBoolean(key, value)
		is Int -> putInt(key, value)
		is Long -> putLong(key, value)
		is String -> putString(key, value)
		is CharSequence -> putCharSequence(key, value)
		is Parcelable -> putParcelable(key, value)

		is Array<*> -> when {
			value.isArrayOf<Parcelable>() -> @Suppress("UNCHECKED_CAST") putParcelableArray(key, value as Array<Parcelable?>)
			value.isArrayOf<CharSequence>() -> @Suppress("UNCHECKED_CAST") putCharSequenceArray(key, value as Array<CharSequence?>)
			value.isArrayOf<String>() -> @Suppress("UNCHECKED_CAST") putStringArray(key, value as Array<String?>)
			else -> throw IllegalArgumentException("Unsupported array type: " + value.javaClass) }
		is List<*> -> @Suppress("UNCHECKED_CAST") putParcelableArrayList(key,
				if (value is ArrayList<*>) (value as ArrayList<Parcelable>) else ArrayList(value as List<Parcelable>))
		is SparseArray<*> -> @Suppress("UNCHECKED_CAST") putSparseParcelableArray(key, value as SparseArray<Parcelable>)
		is Bundle -> putBundle(key, value)
		is Serializable -> putSerializable(key, value)

		is Byte -> putByte(key, value)
		is Char -> putChar(key, value)
		is Short -> putShort(key, value)
		is Float -> putFloat(key, value)
		is Double -> putDouble(key, value)
		is Size -> putSize(key, value)
		is SizeF -> putSizeF(key, value)
		is BooleanArray -> putBooleanArray(key, value)
		is IntArray -> putIntArray(key, value)
		is LongArray -> putLongArray(key, value)
		is ByteArray -> putByteArray(key, value)
		is CharArray -> putCharArray(key, value)
		is ShortArray -> putShortArray(key, value)
		is FloatArray -> putFloatArray(key, value)
		is DoubleArray -> putDoubleArray(key, value)

		is IBinder -> putBinder(key, value)
		else -> throw IllegalArgumentException("Unsupported type: " + value.javaClass)
	}
}

private const val TAG = "Island.SP"