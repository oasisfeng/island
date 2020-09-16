package com.oasisfeng.settings

import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.annotation.StringRes

/**
 * Utility class to access options stored in settings provider.
 *
 * Created by Oasis on 2016/7/18.
 */
class AppSettings(context: Context) {

	interface AppSetting<T> {
		/**
		 * The preference key is the unique identifier for a preference option, which should be defined
		 * in a special resource XML besides the normal strings.xml. This ensures the centralized
		 * definition of preference keys, which can be shared between preferences XML and Java code.
		 *
		 * @return the resource ID of preference key string. (should never be an i18n string).
		 */
		@get:StringRes val prefKeyResId: Int

		/** Single-user setting is always stored in owner user  */
		val isSingleUser: Boolean
	}

	fun getString(option: AppSetting<String>): String? = query(option) { it.getString(0) }
	fun getBoolean(option: AppSetting<Boolean>) = query(option) { it.getInt(0) != 0 } !!
	fun getInt(option: AppSetting<Int>) = query(option) { it.getInt(0) } !!
	fun getLong(option: AppSetting<Long>) = query(option) { it.getLong(0) } !!
	fun getFloat(option: AppSetting<Float>) = query(option) { it.getFloat(0) } !!

	private fun <T> query(option: AppSetting<T>, getter: (Cursor) -> T?): T? {
		mAppContext.contentResolver.query(getOptionUri(option), null, null, null, null).use { cursor ->
			if (cursor != null && cursor.count > 0) return getter(cursor.apply { moveToNext() })
			return getter(EMPTY_CURSOR_1X1) }
	}

	/** @return whether this change is accepted */
	operator fun set(option: AppSetting<String>, value: String?) = set(option, 1, value, false, 0, 0, 0f)
	/** @return whether this change is accepted */
	operator fun set(option: AppSetting<Boolean>, value: Boolean) = set(option, 2, null, value, 0, 0, 0f)
	/** @return whether this change is accepted */
	operator fun set(option: AppSetting<Int>, value: Int) = set(option, 3, null, false, value, 0, 0f)
	/** @return whether this change is accepted */
	operator fun set(option: AppSetting<Long>, value: Long) = set(option, 4, null, false, 0, value, 0f)
	/** @return whether this change is accepted */
	operator fun set(option: AppSetting<Float>, value: Float) = set(option, 5, null, false, 0, 0, value)

	private operator fun <T> set(option: AppSetting<T>, type: Int, string: String?, bool: Boolean, integer: Int, long_value: Long, float_value: Float): Boolean {
		val values = ContentValues(3)
		when (type) {
			1 -> values.put(null, string)
			2 -> values.put(null, bool)
			3 -> values.put(null, integer)
			4 -> values.put(null, long_value)
			5 -> values.put(null, float_value) }
		return mAppContext.contentResolver.update(getOptionUri(option), values, null, null) > 0
	}

	fun registerObserver(option: AppSetting<*>, observer: ContentObserver) {
		mAppContext.contentResolver.registerContentObserver(getOptionUri(option), false, observer)
	}

	val singleUserRootUri; get(): Uri = Uri.parse("content://0@" + mAppContext.packageName + ".settings")

	private fun getOptionUri(option: AppSetting<*>) = getOptionUri(getKey(option), option.isSingleUser)
	private fun getOptionUri(pref_key: String, singleUser: Boolean) = Uri.parse("content://"
			+ (if (singleUser) "0@" else "") + mAppContext.packageName + ".settings/" + pref_key)
	private fun <T> getKey(option: AppSetting<T>) = mAppContext.getString(option.prefKeyResId)

	private val mAppContext: Context = context.applicationContext

	companion object {
		/** Empty cursor for automatic default value based on value type  */
		private val EMPTY_CURSOR_1X1: MatrixCursor = MatrixCursor(arrayOf(""), 1).apply {
			addRow(arrayOf<Any?>(null)); moveToNext() }     // Null or default value for primitive type
	}
}