 package com.oasisfeng.island.controller

import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.Intent.EXTRA_INSTALLER_PACKAGE_NAME
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager.*
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.os.Build.VERSION_CODES.O
import android.os.Build.VERSION_CODES.P
import android.util.Log
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import com.oasisfeng.android.app.Activities
import com.oasisfeng.android.content.pm.LauncherAppsCompat
import com.oasisfeng.android.google.GooglePlayStore
import com.oasisfeng.android.ui.Dialogs
import com.oasisfeng.android.ui.WebContent
import com.oasisfeng.android.util.Apps
import com.oasisfeng.common.app.BaseAndroidViewModel
import com.oasisfeng.island.Config
import com.oasisfeng.island.analytics.Analytics
import com.oasisfeng.island.analytics.analytics
import com.oasisfeng.island.clone.AppClonesBottomSheet
import com.oasisfeng.island.controller.IslandAppControl.launchSystemAppSettings
import com.oasisfeng.island.controller.IslandAppControl.unfreezeInitiallyFrozenSystemApp
import com.oasisfeng.island.data.IslandAppInfo
import com.oasisfeng.island.data.IslandAppListProvider
import com.oasisfeng.island.data.helper.hidden
import com.oasisfeng.island.data.helper.installed
import com.oasisfeng.island.data.helper.isSystem
import com.oasisfeng.island.data.helper.suspended
import com.oasisfeng.island.engine.IslandManager
import com.oasisfeng.island.engine.common.WellKnownPackages
import com.oasisfeng.island.installer.InstallerExtras
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.model.interactive
import com.oasisfeng.island.settings.IslandNameManager.getAllNames
import com.oasisfeng.island.shuttle.Shuttle
import com.oasisfeng.island.ui.ModelBottomSheetFragment
import com.oasisfeng.island.util.*
import eu.chainfire.libsuperuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import rikka.shizuku.Shizuku.removeRequestPermissionResultListener
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.stream.Collectors
import kotlin.annotation.AnnotationRetention.SOURCE
import kotlin.annotation.AnnotationTarget.TYPE

 /**
 * Controller for complex procedures of Island.
 *
 * Refactored by Oasis on 2018-9-30.
 */
class IslandAppClones(val activity: FragmentActivity, val vm: BaseAndroidViewModel, val app: IslandAppInfo) {

	fun request() {
		val names = getAllNames(context)
		check(names.isNotEmpty()) { "No Island" }
		val targets: MutableMap<UserHandle, String> = LinkedHashMap(names.size + 1)
		targets[Users.getParentProfile()] = context.getString(R.string.tab_mainland)
		targets.putAll(names)

		val shouldShowBadge: Boolean = targets.size > 2
		val icons: Map<UserHandle, Drawable> = targets.entries.stream().collect(Collectors.toMap({ obj: Map.Entry<UserHandle, String> -> obj.key }) { e: Map.Entry<UserHandle, String> ->
			val user = e.key
			val res = if (Users.isParentProfile(user)) R.drawable.ic_portrait_24dp else R.drawable.ic_island_black_24dp
			val drawable: Drawable = context.getDrawable(res)!!
			val dark = (context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES
			drawable.setTint(context.getColor(if (dark) android.R.color.white else android.R.color.black))		// TODO: Decouple
			if (shouldShowBadge) Users.getUserBadgedIcon(context, drawable, user) else drawable })

		val isShizukuAvailable = try { Shizuku.getVersion() >= 11 } catch (e: RuntimeException) { false }
		val isShizukuReady = isShizukuAvailable && Shizuku.checkSelfPermission() == PERMISSION_GRANTED
		val isPlayStoreAvailable = Apps.of(context).isAvailable(GooglePlayStore.PACKAGE_NAME) && isInstalledByPlayStore(context, pkg)
		val isPlayStoreReady = targets.size <= 2 && isPlayStoreAvailableInProfiles(targets.keys)

		val fragment = ModelBottomSheetFragment()
		val alp = IslandAppListProvider.getInstance(context)
		val dialog = AppClonesBottomSheet(targets, icons, { user -> alp.isInstalled(pkg, user) }) { target, mode ->
			makeAppAvailableInProfile(target, mode)
			fragment.dismiss() }

		fragment.show(activity) {
			val mode = mutableStateOf(if (isShizukuReady) MODE_SHIZUKU else if (isPlayStoreReady) MODE_PLAY_STORE else null)
			dialog.compose(showShizuku = isShizukuAvailable, showPlayStore = isPlayStoreAvailable, mode)

			snapshotFlow { mode.value }.onEach {
				if (it != MODE_SHIZUKU || Shizuku.checkSelfPermission() == PERMISSION_GRANTED) return@onEach
				Shizuku.addRequestPermissionResultListener(object: Shizuku.OnRequestPermissionResultListener { override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
					removeRequestPermissionResultListener(this)
					if (grantResult != PERMISSION_GRANTED) mode.value = MODE_INSTALLER }})	// Uncheck the chip if permission is denied
				Shizuku.requestPermission(1)	// Request permission when the chip is checked
			}.launchIn(vm.viewModelScope) }
	}

	/** Either by unfreezing initially frozen (system) app, enabling disabled system app, or clone user app. */
	private fun makeAppAvailableInProfile(profile: UserHandle, mode: Int) {
		val target = IslandAppListProvider.getInstance(context)[pkg, profile]
		if (target != null && target.isHiddenSysIslandAppTreatedAsDisabled) {    // Frozen system app shown as disabled, just unfreeze it.
			if (unfreezeInitiallyFrozenSystemApp(target))
				Toast.makeText(activity, context.getString(R.string.toast_successfully_cloned, app.label), Toast.LENGTH_SHORT).show()
		} else if (target != null && target.isInstalled && ! target.enabled) {    // Disabled system app is shown as "removed" (not cloned)
			launchSystemAppSettings(target)
			Toast.makeText(activity, R.string.toast_enable_disabled_system_app, Toast.LENGTH_SHORT).show()
		} else cloneUserApp(profile, mode)
	}

	/** Clone user app to Mainland or Island */
	private fun cloneUserApp(target: UserHandle, mode: @AppCloneMode Int = 0) {
		if (Users.isParentProfile(target))
			activity.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null)))
		else vm.interactive(context) { cloneAppToIsland(app, target, mode) }
	}

	private suspend fun cloneAppToIsland(source: IslandAppInfo, target: UserHandle, mode: @AppCloneMode Int) {
		val context = source.context(); val pkg = source.packageName
		if (source.isSystem) {
			analytics().event("clone_sys").with(Analytics.Param.ITEM_ID, pkg).send()

			val enabled = Shuttle(context, to = target).invoke(with = pkg) { DevicePolicies(this).enableSystemApp(it) }

			if (enabled) Toast.makeText(context, context.getString(R.string.toast_successfully_cloned, source.label), Toast.LENGTH_SHORT).show()
			else Toast.makeText(context, context.getString(R.string.toast_cannot_clone, source.label), Toast.LENGTH_LONG).show()
			return
		}

		if (SDK_INT >= O && cloneAppViaRoot(context, source, target)) return    // Prefer root routine to avoid overhead (it's instant)

		if (mode == MODE_SHIZUKU && Shizuku.checkSelfPermission() == PERMISSION_GRANTED) {
			val component = ComponentName(context, PrivilegedRemoteWorker::class.java)
			val args = UserServiceArgs(component).daemon(false).processNameSuffix(pkg)
			Shizuku.bindUserService(args, object: ServiceConnection {
				override fun onServiceConnected(name: ComponentName, service: IBinder) {
					val data = Parcel.obtain().apply { writeString(pkg); writeInt(target.toId()) }
					val reply = Parcel.obtain()
					try {
						service.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)
						val result = reply.readInt()
						if (result == 1)
							Toast.makeText(context, context.getString(R.string.toast_successfully_cloned, source.label), Toast.LENGTH_SHORT).show()
						else Toast.makeText(context, context.getString(R.string.toast_cannot_clone, source.label), Toast.LENGTH_LONG).show() }
					finally {
						data.recycle(); reply.recycle()
						Handler(Looper.getMainLooper()).post { Shizuku.unbindUserService(args, this, false) }}
				}

				override fun onServiceDisconnected(name: ComponentName?) {}
			})
			return
		}

		val viaPlayStore = mode == MODE_PLAY_STORE && isInstalledByPlayStore(context, pkg)

		val result = Shuttle(context, to = target).invoke(with = source as ApplicationInfo) {
			performAppCloningInProfile(this, it, viaPlayStore) }     // Cast to reduce the overhead
		Log.i(TAG, "Result of cloning $pkg to $target: $result")

		when (result) {
			CLONE_RESULT_OK_INSTALL -> {
				// As visual feedback, since installation may take some time. TODO: Track installing apps with PackageInstaller
				IslandAppListProvider.getInstance(context).addPlaceholder(pkg, target)
				analytics().event("clone_install").with(Analytics.Param.ITEM_ID, pkg).send() }

			CLONE_RESULT_OK_INSTALL_EXISTING -> analytics().event("clone_install_existing").with(Analytics.Param.ITEM_ID, pkg).send()
			CLONE_RESULT_OK_GOOGLE_PLAY ->      analytics().event("clone_via_play").with(Analytics.Param.ITEM_ID, pkg).send()
			CLONE_RESULT_ALREADY_CLONED ->      Toast.makeText(context, R.string.toast_already_cloned, Toast.LENGTH_SHORT).show()
			CLONE_RESULT_NO_SYS_MARKET -> {
				val activity = Activities.findActivityFrom(context)
				if (activity != null) Dialogs.buildAlert(activity, 0, R.string.dialog_clone_incapable_explanation)
						.setNeutralButton(R.string.action_learn_more) { _,_ -> WebContent.view(context, Config.URL_FAQ.get()) }
						.setPositiveButton(android.R.string.cancel, null).show()
				else Toast.makeText(context, R.string.dialog_clone_incapable_explanation, Toast.LENGTH_LONG).show() }

			CLONE_RESULT_UNKNOWN_SYS_MARKET -> {
				val info = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).resolveActivityInfo(context.packageManager, 0)
				if (info != null && info.applicationInfo.isSystem)
					analytics().event("clone_via_market").with(Analytics.Param.ITEM_ID, pkg).with(Analytics.Param.ITEM_CATEGORY, info.packageName).send() }}
	}

	@OwnerUser @RequiresApi(O) private suspend fun cloneAppViaRoot(context: Context, source: IslandAppInfo, profile: UserHandle): Boolean {
		val pkg = source.packageName
		val cmd = "cmd package install-existing --user ${profile.toId()} $pkg"  // Try root approach first
		val result = withContext(Dispatchers.Default) { Shell.SU.run(cmd) }
		try {
			val app = context.getSystemService<LauncherApps>()!!.getApplicationInfo(pkg, 0, profile)
			if (app != null && app.installed) {
				Toast.makeText(context, context.getString(R.string.toast_successfully_cloned, source.label), Toast.LENGTH_SHORT).show()
				return true }}
		catch (e: NameNotFoundException) {
			Log.i(TAG, "Cannot clone app via root: $pkg")
			if (result != null && result.isNotEmpty()) analytics().logAndReport(TAG, "Error executing: $cmd",
					ExecutionException("ROOT: " + cmd + ", result: " + java.lang.String.join(" \\n ", result), null)) }
		return false
	}

	private fun isPlayStoreAvailableInProfiles(targets: Collection<UserHandle>) =
		targets.all { LauncherAppsCompat(context).getApplicationInfoNoThrows(GooglePlayStore.PACKAGE_NAME, 0, it) != null }

	private fun isInstalledByPlayStore(context: Context, pkg: String) =
		if (SDK_INT >= VERSION_CODES.R) {
			context.packageManager.getInstallSourceInfo(pkg).run {
				GooglePlayStore.PACKAGE_NAME.let { it == initiatingPackageName && it == installingPackageName }}
		} else context.packageManager.getInstallerPackageName(pkg) == GooglePlayStore.PACKAGE_NAME

	companion object {
		private const val CLONE_RESULT_ALREADY_CLONED = 0
		private const val CLONE_RESULT_OK_INSTALL = 1
		private const val CLONE_RESULT_OK_INSTALL_EXISTING = 2
		private const val CLONE_RESULT_OK_GOOGLE_PLAY = 10
		private const val CLONE_RESULT_UNKNOWN_SYS_MARKET = 11
		private const val CLONE_RESULT_NO_SYS_MARKET = -1

		@IntDef(MODE_INSTALLER, MODE_PLAY_STORE, MODE_SHIZUKU) @Target(TYPE) @Retention(SOURCE)
		annotation class AppCloneMode
		const val MODE_INSTALLER = 0
		const val MODE_PLAY_STORE = 1
		const val MODE_SHIZUKU = 2

		@ProfileUser private fun performAppCloningInProfile(context: Context, app: ApplicationInfo, viaPlayStore: Boolean): Int {
			val policies = DevicePolicies(context)
			policies.clearUserRestrictionsIfNeeded(UserManager.DISALLOW_INSTALL_APPS)  // Blindly clear these restrictions

			val pkg = app.packageName
			if (SDK_INT >= P && policies.manager.isAffiliatedUser) try {
				if (policies.invoke(DevicePolicyManager::installExistingPackage, pkg))
					return CLONE_RESULT_OK_INSTALL_EXISTING
				Log.e(TAG, "Error cloning existent user app: $pkg")     // Fall-through
			} catch (e: RuntimeException) { analytics().logAndReport(TAG, "Error cloning existent user app: $pkg", e) } // Fall-through

			if (! IslandManager.ensureLegacyInstallNonMarketAppAllowed(context, policies)) {    // Fallback to install via app store
				val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(FLAG_ACTIVITY_NEW_TASK)
				policies.enableSystemAppByIntent(marketIntent)
				val marketApp = context.packageManager.resolveActivity(marketIntent, MATCH_SYSTEM_ONLY)?.activityInfo?.applicationInfo
				return when {
					marketApp == null || ! marketApp.isSystem -> CLONE_RESULT_NO_SYS_MARKET
					marketApp.packageName != WellKnownPackages.PACKAGE_GOOGLE_PLAY_STORE -> CLONE_RESULT_UNKNOWN_SYS_MARKET.also {
						context.startActivity(marketIntent) }
					else -> CLONE_RESULT_OK_GOOGLE_PLAY.also {
						policies.enableSystemApp(WellKnownPackages.PACKAGE_GOOGLE_PLAY_SERVICES)     // Special dependency
						context.startActivity(marketIntent) }}}

			if (viaPlayStore && ensurePlayStoreReady(context, policies)) try {
				context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
					.setPackage(GooglePlayStore.PACKAGE_NAME).addFlags(FLAG_ACTIVITY_NEW_TASK))
				return CLONE_RESULT_OK_GOOGLE_PLAY
			} catch (e: ActivityNotFoundException) { Log.e(TAG, "Error launching Google Play Store to clone $pkg", e) }

			@Suppress("DEPRECATION") val intent = Intent(Intent.ACTION_INSTALL_PACKAGE, Uri.fromParts("package", pkg, null))
					.putExtra(EXTRA_INSTALLER_PACKAGE_NAME, context.packageName).addFlags(FLAG_ACTIVITY_NEW_TASK)
					.putExtra(InstallerExtras.EXTRA_APP_INFO, app).addCategory(context.packageName) // Launch App Installer
			policies.enableSystemAppByIntent(intent)
			context.startActivity(intent)
			return CLONE_RESULT_OK_INSTALL
		}

		private fun ensurePlayStoreReady(context: Context, policies: DevicePolicies): Boolean {
			val pkg = GooglePlayStore.PACKAGE_NAME
			val info = try { context.packageManager.getApplicationInfo(pkg, MATCH_UNINSTALLED_PACKAGES or MATCH_DISABLED_COMPONENTS) }
				catch (e: NameNotFoundException) { return false }
			if (! info.enabled) return false    // We cannot enable a disabled system app
			if (! info.installed) return policies.enableSystemApp(pkg)
			if (info.hidden) policies.setApplicationHidden(pkg, false)
			if (info.suspended) policies.invoke(DPM::setPackagesSuspended, arrayOf(pkg), false).isEmpty()
			return true
		}
	}

	private val pkg = app.packageName
	private val context = app.context()
}

private const val TAG = "Island.AC"
