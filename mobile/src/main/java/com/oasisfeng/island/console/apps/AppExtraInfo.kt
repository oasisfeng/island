package com.oasisfeng.island.console.apps

import android.Manifest.permission.QUERY_ALL_PACKAGES
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build.VERSION_CODES.Q
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import com.oasisfeng.android.base.Versions
import com.oasisfeng.common.app.hasRequestedLegacyExternalStorage
import com.oasisfeng.island.data.IslandAppInfo
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.model.AppViewModel

class AppExtraInfo {

	companion object {

		@JvmStatic fun bind(extraInfo: ComposeView, selection: MutableLiveData<AppViewModel>) {
			extraInfo.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
			extraInfo.setContent {
				val selected = selection.observeAsState()
				val info = selected.value?.info() ?: return@setContent
				addExtraInfo(info)
			}
		}

		@Preview @Composable private fun addExtraInfo(info: ApplicationInfo = ApplicationInfo()) {
			MaterialTheme(colors = darkColors(onPrimary = colorResource(R.color.textPrimary))) {
				Column {
					val targetSdk = info.targetSdkVersion
					Line("APP ID: ${info.packageName}")
					Line("Target Android ${Versions.getAndroidVersionNumber(targetSdk)}")
					if (targetSdk >= Q) {
						if (targetSdk == Q && info.hasRequestedLegacyExternalStorage()) Line("Legacy storage")
						else Line("Scoped storage")
					}
					if (targetSdk > Q/* targetSdk >= R */) {
						val packageInfo = (info as IslandAppInfo).getPackageInfo(PackageManager.GET_PERMISSIONS)
						if (packageInfo?.requestedPermissions?.contains(QUERY_ALL_PACKAGES) == true)
							Line("QUERY_ALL_PACKAGES")
					}
				}
			}
		}

		@Composable fun Line(text: String) {
			Text(text, color = MaterialTheme.colors.primary, fontSize = 16.sp, modifier = Modifier.padding(start = 16.dp))
		}
	}
}