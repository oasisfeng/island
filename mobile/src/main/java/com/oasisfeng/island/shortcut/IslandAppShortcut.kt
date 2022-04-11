package com.oasisfeng.island.shortcut

import android.app.ActivityManager
import android.app.Service
import android.content.*
import android.content.Intent.*
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.ShortcutManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Binder
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.annotation.RequiresApi
import androidx.core.content.LocusIdCompat
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import com.oasisfeng.android.app.LifecycleActivity
import com.oasisfeng.android.content.pm.LauncherAppsCompat
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.android.util.Apps
import com.oasisfeng.android.widget.Toasts
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.island.data.helper.hidden
import com.oasisfeng.island.data.helper.installed
import com.oasisfeng.island.data.helper.user
import com.oasisfeng.island.data.helper.userId
import com.oasisfeng.island.engine.IslandManager
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.settings.IslandSettings
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.util.DevicePolicies
import com.oasisfeng.island.util.OwnerUser
import com.oasisfeng.island.util.ProfileUser
import com.oasisfeng.island.util.Users
import com.oasisfeng.island.util.Users.Companion.toId
import kotlinx.coroutines.launch
import java.net.URISyntaxException

object IslandAppShortcut {

	const val ACTION_LAUNCH_CLONE = "com.oasisfeng.island.action.LAUNCH_CLONE"
	private const val ACTION_LAUNCH_APP = "com.oasisfeng.island.action.LAUNCH_APP"
	private const val SCHEME_PACKAGE = "package"            // Introduced in Island 2.8 (deprecated)
	private const val SCHEME_ANDROID_APP = "android-app"    // Introduced in Island 5.0 (deprecated)
	private const val SCHEME_APP = "app"                    // Introduced in Island 5.3 (replacing "android-app" used before to avoid shortcut intent corruption after reboot)

	@OwnerUser @JvmStatic fun requestPin(context: Context, app: ApplicationInfo) {
		val dynamic = isDynamicLabelEnabled(context)
		val profile = app.user
		if (IslandManager.isReady(context, profile))
			Shuttle(context, profile).launchNoThrows { requestPinAsUser(this, app, dynamic) }
		else requestPinAsUser(context, app, dynamic)    // Create cross-profile shortcut in Mainland if Island is not ready (probably deactivated)
	}

	/** @return true if launcher supports shortcut pinning, false for failure, or null if legacy shortcut installation broadcast is sent. */
	@OwnerUser @ProfileUser @JvmStatic fun requestPinAsUser(context: Context, app: ApplicationInfo, dynamic: Boolean) {
		val shortcut = buildShortcutInfo(context, app, dynamic)

		try { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
		catch (e: RuntimeException) { Log.e(TAG, "Error adding dynamic shortcut", e) }

		try { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }       // FIXME: Deal with rate limit
		catch (e: RuntimeException) { showToastForShortcutFailure(context); analytics().report(e) }
	}

	@OwnerUser @ProfileUser private fun remove(context: Context, pkg: String) {
		val id = getShortcutId(pkg, Users.current().toId())
		ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(id))

		if (! Users.isParentProfile()) Shuttle(context, to = Users.parentProfile).launchNoThrows(with = Users.currentId()) {
			ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(getShortcutId(pkg, it, isCrossProfile = true))) }
	}

	@OwnerUser @ProfileUser @RequiresApi(O) fun updateIfNeeded(context: Context, app: ApplicationInfo, isCrossProfile: Boolean) {
		val sm: ShortcutManager = context.getSystemService() ?: return
		val id = getShortcutId(app.packageName, app.userId, isCrossProfile)
		sm.pinnedShortcuts.firstOrNull { it.id == id } ?: sm.dynamicShortcuts.firstOrNull { it.id == id } ?: return // Ensure existence
		update(context, app, dynamic = true)
	}

	@OwnerUser @RequiresApi(O) fun updateAllInActiveProfiles(context: Context) {
		val dynamic = isDynamicLabelEnabled(context)
		updateAll(context, dynamic)
		Users.getProfilesManagedByIsland().forEach {
			Shuttle(context, to = it).launchNoThrows { updateAll(context, dynamic) }}
	}

	@RequiresApi(O) fun updateAll(context: Context, dynamic: Boolean) {
		Log.i(TAG, "Updating all pinned shortcuts...")
		val sm: ShortcutManager = context.getSystemService() ?: return
		val la: LauncherApps = context.getSystemService() ?: return
		sm.pinnedShortcuts.plus(sm.dynamicShortcuts).distinctBy { it.id }.forEach { shortcut ->
			val parsed = parseShortcutId(shortcut.id)?.takeIf { it.size <= 2 } ?: return@forEach
			val pkg = parsed[0]
			val profileId = try { parsed.getOrNull(1)?.toInt() } catch (e: NumberFormatException) { return@forEach }
			val profile = profileId?.let { UserHandles.of(it) } ?: Users.current()
			val app = try { la.getApplicationInfo(pkg, MATCH_UNINSTALLED_PACKAGES, profile).takeIf { it.installed } }
				catch (e: NameNotFoundException) { null }
				?: return@forEach sm.removeDynamicShortcuts(listOf(getShortcutId(pkg, profile.toId())))
			update(context, app, dynamic) }
	}

	@OwnerUser private fun update(context: Context, app: ApplicationInfo, dynamic: Boolean) {
		Log.i(TAG, "Updating shortcut for ${app.packageName} in profile ${app.userId}")
		val shortcut = buildShortcutInfo(context, app, dynamic)
		ShortcutManagerCompat.updateShortcuts(context, listOf(shortcut))
	}

	private fun buildLabel(context: Context, app: ApplicationInfo, dynamic: Boolean)
			= app.loadLabel(context.packageManager).let { buildLabelPrefix(context, app, dynamic)?.plus(it) ?: it }

	private fun buildLabelPrefix(context: Context, app: ApplicationInfo, dynamic: Boolean) = when {
		SDK_INT < O -> @Suppress("DEPRECATION") android.preference.PreferenceManager.getDefaultSharedPreferences(context)
				.getString(context.getString(R.string.key_launch_shortcut_prefix), context.getString(R.string.default_launch_shortcut_prefix))
		dynamic -> getDynamicPrefix(context, app)
		else -> null }

	private fun getDynamicPrefix(context: Context, app: ApplicationInfo)
			= if (app.hidden) context.getString(R.string.default_launch_shortcut_prefix) else null

	private fun buildShortcutInfo(context: Context, app: ApplicationInfo, dynamic: Boolean): ShortcutInfoCompat {
		val pkg = app.packageName; val userId = app.userId; val isCrossProfile = isCrossProfile(userId)
		val shortcutId = getShortcutId(pkg, userId, isCrossProfile)
		val label = buildLabel(context, app, dynamic)
		val intent = buildShortcutIntent(context, pkg, userId)
		val drawable = getAppIconDrawable(context, context.getSystemService()!!, app)
		return ShortcutInfoCompat.Builder(context, shortcutId).setIntent(intent).setShortLabel(label).apply {
			setIcon(IconCompat.createWithAdaptiveBitmap(drawable.toBitmap(ShortcutManagerCompat.getIconMaxWidth(context), ShortcutManagerCompat.getIconMaxHeight(context))))
			if (SDK_INT >= Q) setLocusId(LocusIdCompat(shortcutId))
		}.build()
	}

	private fun buildShortcutIntent(context: Context, pkg: String, userId: Int) = Intent(ACTION_LAUNCH_APP, Uri.Builder()
			.scheme(SCHEME_APP).encodedAuthority(if (Users.isParentProfile(userId)) pkg else "$userId@$pkg").build())
			.addCategory(CATEGORY_LAUNCHER).setPackage(context.packageName)

	private const val SHORTCUT_ID_PREFIX = "launch:"    // launch:<pkg>[@<user ID>]
	private fun getShortcutId(pkg: String, userId: Int, isCrossProfile: Boolean = isCrossProfile(userId))
			= "$SHORTCUT_ID_PREFIX$pkg".let { if (isCrossProfile) it.plus("@$userId") else it }
	private fun parseShortcutId(id: String)
			= id.takeIf { it.startsWith(SHORTCUT_ID_PREFIX) }?.substring(SHORTCUT_ID_PREFIX.length)?.split('@')

	@RequiresApi(O) private fun Drawable.toBitmap(width: Int, height: Int): Bitmap {
		val eif = AdaptiveIconDrawable.getExtraInsetFraction()
		return Bitmap.createBitmap(((1 + 2 * eif) * width).toInt(), ((1 + 2 * eif) * height).toInt(), ARGB_8888).also { bitmap ->
			setBounds((width * eif).toInt(), (height * eif).toInt(), (width * (1 + eif)).toInt(), (height * (1 + eif)).toInt())
			draw(Canvas(bitmap).apply { drawColor(Color.WHITE) }) }
	}

	private fun requestLegacyPin(context: Context, app: ApplicationInfo) {
		val label = buildLabel(context, app, dynamic = false)
		val intent = buildShortcutIntent(context, app.packageName, app.userId)
		val am: ActivityManager = context.getSystemService()!!; val size = am.launcherLargeIconSize
		val bitmap = getAppIconDrawable(context, am, app).let { drawable ->
			Bitmap.createBitmap(size, size, ARGB_8888).also { bitmap -> drawable.draw(Canvas(bitmap)) }}
		val shortcut = ShortcutInfoCompat.Builder(context, ""/* unused */).setIntent(intent).setShortLabel(label)
				.setIcon(IconCompat.createWithBitmap(bitmap)).build()
		if (! ShortcutManagerCompat.requestPinShortcut(context, shortcut, null))
			showToastForShortcutFailure(context)
	}

	private fun getAppIconDrawable(context: Context, am: ActivityManager, app: ApplicationInfo): Drawable
			= getAppIconLargeDrawable(context, am, app) ?: app.loadIcon(context.packageManager)   // Fallback to default density icon

	private fun getAppIconLargeDrawable(context: Context, am: ActivityManager, app: ApplicationInfo): Drawable?
			= if (app.icon == 0) null else try { context.packageManager.getResourcesForApplication(app)
				.getDrawableForDensity(app.icon, am.launcherLargeIconDensity, null) }
			catch (_: NameNotFoundException) { null } catch (_: Resources.NotFoundException) { null }

	private fun showToastForShortcutFailure(context: Context)
			= Toast.makeText(context, R.string.toast_shortcut_failed, LENGTH_LONG).show()

	private fun isCrossProfile(userId: Int) = userId != Users.current().toId()

	@ProfileUser @RequiresApi(O) class ShortcutSyncService: Service() {

		private val mPackageObserver = object: BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) {
			val pkg = intent.data?.schemeSpecificPart ?: return
			if (intent.getBooleanExtra(EXTRA_REPLACING, false)) return  // Ignore package removal during replacing
			Log.d(TAG, "Package event: $intent")
			if (intent.`package` == context.packageName) return         // Skip duplicate broadcast directly sent to installer (that's us).

			val info = try { context.packageManager.getApplicationInfo(pkg, MATCH_UNINSTALLED_PACKAGES) }
			catch (e: NameNotFoundException) { return remove(context, pkg) }    // Actual package uninstall

			updateIfNeeded(context, info, false)

			if (! Users.isParentProfile()) Shuttle(context, to = Users.parentProfile).launchNoThrows {  // For cross-profile shortcut
				if (isDynamicLabelEnabled(this)) updateIfNeeded(this, info, true) }}
		}

		override fun onCreate() {
			registerReceiver(mPackageObserver, IntentFilter(ACTION_PACKAGE_REMOVED).apply {
				addAction(ACTION_PACKAGE_ADDED); addDataScheme("package") })
		}

		override fun onDestroy() = unregisterReceiver(mPackageObserver)
		override fun onBind(intent: Intent?) = Binder()
	}

	fun isDynamicLabelEnabled(context: Context) = IslandSettings(context).DynamicShortcutLabel().enabled

	class ShortcutLauncher: LifecycleActivity() {

		companion object {

			/** @return Whether to finish the launchpad activity */
			@OwnerUser private fun prepareAndLaunch(activity: LifecycleActivity, pkg: String, intent: Intent? = null,
													profile: UserHandle = Users.current()): Boolean =
				doPrepareAndLaunch(activity, pkg, intent, profile) ?: true.also {
					Toast.makeText(activity, activity.getString(R.string.toast_app_launch_failure, Apps.of(activity).getAppName(pkg)), LENGTH_LONG).show() }

			/** @return Whether to finish the launchpad activity, or null for failure. */
			@OwnerUser private fun doPrepareAndLaunch(activity: LifecycleActivity, pkg: String, intent: Intent?,
			                                          profile: UserHandle): Boolean? {
				val context = activity.applicationContext; val la = LauncherAppsCompat(context)
				val app = la.getApplicationInfoNoThrows(pkg, MATCH_UNINSTALLED_PACKAGES, profile) ?: return null

				val um = context.getSystemService<UserManager>()!!
				if (SDK_INT < P || um.isUserUnlocked(profile))      // Quiet mode was introduced in Android P
					return true.also { shuttleAndLaunch(context, pkg, intent, profile, app.hidden) }

				if (! app.hidden) la.get().run {    // Use LauncherApps to start non-frozen app within profile in Quiet Mode
					val component = getActivityList(pkg, profile).getOrNull(0)?.componentName ?: return true
					startMainActivity(component, profile, null, null)
					return true }

				// Island is currently locked (deactivated), unlock it before launching shortcut
				if (! DevicePolicies(context).isProfileOwner)   // Activating Island (to unfreeze app) requires managed Mainland.
					return true.also { Toasts.showLong(context, R.string.prompt_activate_island_first) }
				val toast = Toast.makeText(context, R.string.prompt_activating_island, LENGTH_LONG).apply { show() }
				return false.also { activity.lifecycleScope.launch {    // Do not finish the activity to keep coroutine running.
					when(Users.requestQuietModeDisabled(context, profile)) {
						true -> {
							toast.cancel()    // Cancel as soon as shortcut is ready to launch
							Log.i(TAG, "Launching shortcut...")
							shuttleAndLaunch(context, pkg, intent, profile, app.hidden) }
						false ->
							Toasts.showLong(context, R.string.prompt_activate_island_first) }
					activity.finish() }}
			}

			private fun shuttleAndLaunch(context: Context, pkg: String, intent: Intent?, profile: UserHandle, frozen: Boolean) {
				Shuttle(context, to = profile).launch {
					if (frozen) IslandManager.ensureAppFreeToLaunch(this, pkg)
					launch(this, pkg, intent) }
			}

			private fun launch(context: Context, pkg: String, intent: Intent?): Boolean {
				if (intent == null) return IslandManager.launchApp(context, pkg, Users.current())
				// Entrance activity may not contain CATEGORY_DEFAULT, component must be set in launch intent.
				val resolve = context.packageManager.resolveActivity(intent, 0)
					?: return false.also { Log.w(TAG, "Unable to launch $pkg in profile ${Users.currentId()}: $intent") }
				Log.i(TAG, "Launching $pkg in profile ${Users.currentId()}...")
				intent.component = ComponentName(resolve.activityInfo.packageName, resolve.activityInfo.name)
				return try { context.startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK)); true } catch (e: ActivityNotFoundException) {
					false.also { Log.e(TAG, "Error launching $pkg in profile ${Users.currentId()}: $intent") }}
			}
		}

		/** @return Whether to finish the launchpad activity */
		private fun launch(uri: Uri): Boolean = when(uri.scheme) {
			SCHEME_PACKAGE /* legacy */     -> prepareAndLaunch(this, uri.schemeSpecificPart)
			SCHEME_ANDROID_APP /* legacy */ -> launchForAndroidAppScheme(uri)
			SCHEME_APP -> launchForAndroidAppScheme(uri.buildUpon().scheme(SCHEME_ANDROID_APP).build())
			else -> true.also { showInvalidShortcutToast() }
		}

		private fun launchForAndroidAppScheme(uri: Uri): Boolean {
			val parsed = try { parseUri(uri.toString(), URI_ANDROID_APP_SCHEME) }
			catch (e: URISyntaxException) { showInvalidShortcutToast(); return false }
			val intent = if (! uri.encodedPath.isNullOrEmpty() || uri.encodedFragment != null) parsed else null // Null for pure app launch

			val authority = parsed.getPackage()!!   // Never null, ensured by scheme "android-app"
			if (! authority.contains('@'))
				return prepareAndLaunch(this, authority, intent)

			val pkg = uri.host!!.also { intent?.setPackage(it) }
			val user = try { uri.userInfo?.toInt()?.let { UserHandles.of(it) } ?: Users.current() }
			catch (e: NumberFormatException) { showInvalidShortcutToast(); return false }

			return prepareAndLaunch(this, pkg, intent, user)
		}

		private fun showInvalidShortcutToast() = Toast.makeText(this, R.string.prompt_invalid_shortcut, LENGTH_LONG).show()

		override fun onNewIntent(intent: Intent) {
			var finishNow = true
			try {
				val action = intent.action
				if (ACTION_LAUNCH_APP != action && ACTION_LAUNCH_CLONE != action) return
				finishNow = launch(intent.data ?: return)       // TODO: Handle failure and show toast
			} finally { if (finishNow) finish() }
		}

		override fun onCreate(savedInstanceState: Bundle?) = super.onCreate(savedInstanceState).also { onNewIntent(intent) }
	}
}

class ShortcutsUpdater: BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		if (SDK_INT >= O) try { // It's safe to not check the action, as the update is idempotent and does not depends on intent.
			val dynamic = Shuttle(context, to = Users.parentProfile).invokeNoThrows { IslandAppShortcut.isDynamicLabelEnabled(this) }
				?: return Unit.also { Log.w(TAG, "Failed to query setting DynamicShortcutLabel across profile.") }
			IslandAppShortcut.updateAll(context, dynamic)
		} catch (e: IllegalStateException) { return }   // User is locked
	}
}

private const val TAG = "Island.Shortcut"
