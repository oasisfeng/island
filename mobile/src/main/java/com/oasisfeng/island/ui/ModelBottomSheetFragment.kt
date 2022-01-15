package com.oasisfeng.island.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ModelBottomSheetFragment : BottomSheetDialogFragment() {

    fun show(activity: FragmentActivity, content: @Composable () -> Unit) {
        mContent = content
        super.show(activity.supportFragmentManager, tag)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        ComposeView(requireContext()).apply { setContent(mContent) }

    override fun onPause() = super.onPause().also { dismiss() }       // Dismiss on stop

    private lateinit var mContent: @Composable () -> Unit
}