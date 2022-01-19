package com.oasisfeng.island.clone

import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.oasisfeng.android.os.UserHandles
import com.oasisfeng.island.controller.IslandAppClones
import com.oasisfeng.island.controller.IslandAppClones.Companion.AppCloneMode
import com.oasisfeng.island.mobile.R
import com.oasisfeng.island.ui.IslandTheme
import com.oasisfeng.island.ui.MutexChipGroup

class AppClonesBottomSheet(
    private val targets: Map<UserHandle, String>, private val icons: Map<UserHandle, Drawable>?,
    private val isCloned: Function1<UserHandle, Boolean>, private val clone: Function2<UserHandle, @AppCloneMode Int, Unit>) {

    @Suppress("unused") // For Compose Preview
    constructor(): this(mapOf(UserHandles.SYSTEM to "Mainland", UserHandles.of(10) to "Island"), null, { false }, { _,_ -> })

    @Composable @Preview @OptIn(ExperimentalMaterialApi::class)
    fun compose(showShizuku: Boolean, showPlayStore: Boolean, mode: MutableState<Int?>) {
        val selectedMode: MutableState<Int?> = rememberSaveable { mode }

        IslandTheme { Column(modifier = Modifier.padding(16.dp)) {
            if (showShizuku || showPlayStore) {
                MutexChipGroup(ArrayList<Pair<String, Int>>(2).apply {
                    if (showShizuku) {
                        add(stringResource(R.string.option_clone_via_shizuku) to IslandAppClones.MODE_SHIZUKU) }
                    if (showPlayStore) {
                        add(stringResource(R.string.option_clone_via_play_store) to IslandAppClones.MODE_PLAY_STORE) }
                }, selectedMode, arrangement = Arrangement.Center) }

            ListItem(text = { Text(stringResource(R.string.prompt_clone_app_to), color = MaterialTheme.colors.onBackground) })

            for ((user, label) in targets) {
                val icon = icons?.get(user)?.toBitmap()?.asImageBitmap()
                ListItem(text = { Text(label, color = MaterialTheme.colors.onBackground) },
                    icon = { if (icon != null) Image(icon, null) },
                    modifier = if (isCloned(user)) Modifier.alpha(ContentAlpha.disabled)
                    else Modifier.clickable { clone(user, selectedMode.value ?: IslandAppClones.MODE_INSTALLER) }) }}
    }}
}
