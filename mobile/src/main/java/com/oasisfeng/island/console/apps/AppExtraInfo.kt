package com.oasisfeng.island.console.apps

import android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
import android.Manifest.permission.QUERY_ALL_PACKAGES
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_PERMISSIONS
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import com.oasisfeng.android.base.Versions
import com.oasisfeng.common.app.hasRequestedLegacyExternalStorage
import com.oasisfeng.island.data.IslandAppInfo
import com.oasisfeng.island.data.IslandAppListProvider
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.model.AppViewModel

object AppExtraInfo {

	@JvmStatic fun bind(extraInfo: ComposeView, selection: MutableLiveData<AppViewModel>) {
		extraInfo.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
		extraInfo.setContent {
			val selected = selection.observeAsState()
			selected.value?.info()?.also { addExtraInfo(extraInfo.context, it) }
		}
	}

	@Composable private fun addExtraInfo(context: Context, info: ApplicationInfo = buildPreviewAppInfo()) {
		val theme = context.resources.newTheme().apply { applyStyle(R.style.AppTheme_Dark, true) }
		val textColor = Color(context.resources.getColor(R.color.textSecondary, theme))

		val canQueryAllPackages = SDK_INT <= Q || info.targetSdkVersion > Q && try {
			context.packageManager.getPackageInfo(info.packageName, GET_PERMISSIONS or MATCH_UNINSTALLED_PACKAGES)
			.requestedPermissions.contains(QUERY_ALL_PACKAGES)
		} catch (e: PackageManager.NameNotFoundException) { false }     // Should not happen

		val canManageExternalStorage = SDK_INT > Q && info.targetSdkVersion > Q
				&& context.checkPermission(MANAGE_EXTERNAL_STORAGE, 0, info.uid) == PERMISSION_GRANTED
		addExtraInfo(textColor, info, canQueryAllPackages, canManageExternalStorage)
	}
}

@Composable @Preview(backgroundColor = 0x3F51B5, showBackground = true)
private fun addExtraInfo(textColor: Color = Color(0xffaaaaaa), info: ApplicationInfo = buildPreviewAppInfo(),
                         canQueryAllPackages: Boolean = true, canManageExternalStorage: Boolean = true) {
	MaterialTheme { Surface(contentColor = textColor, color = Color.Transparent) {
		Column(Modifier.padding(vertical = 10.dp, horizontal = 16.dp)) {
			val targetSdk = info.targetSdkVersion
			Text(stringResource(R.string.info_target_sdk, Versions.getAndroidVersionNumber(targetSdk)))
			Spacer(Modifier.padding(vertical = 4.dp))

			Text(buildAnnotatedString {
				if (canQueryAllPackages)
					Bold(stringResource(R.string.filter_can_query_all_apps))
				if (canManageExternalStorage) {
					Bold(stringResource(R.string.filter_can_manage_external_storage))
				} else if (targetSdk > Q || (targetSdk == Q && !info.hasRequestedLegacyExternalStorage()))
					append(stringResource(R.string.filter_scoped_storage))
			})
			Spacer(Modifier.padding(vertical = 8.dp))

			Divider()
			Text(info.packageName, fontSize = 13.sp)
		}}
	}
}

@Composable private fun AnnotatedString.Builder.Bold(string: String) {
	withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
		withStyle(style = ParagraphStyle(textIndent = TextIndent(restLine = 20.sp))) { append(string) }}
}

private fun buildPreviewAppInfo(): IslandAppInfo {
	return IslandAppListProvider().createEntryWithLabel(ApplicationInfo().apply {
		packageName = "com.oasisfeng.island"; targetSdkVersion = TIRAMISU; uid = 10021
	}, null, "Island")
}
