package com.oasisfeng.island.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.UserHandle
import android.os.UserManager
import android.util.ArrayMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer

class LiveProfileStates(private val context: Context) {

	enum class ProfileState { UNAVAILABLE, AVAILABLE, UNLOCKED }

	inner class LiveProfileState(private val profile: UserHandle): LiveData<ProfileState>() {

		internal fun updateFromBroadcast(intent: Intent) {
			value = when (intent.action) {
				Intent.ACTION_MANAGED_PROFILE_AVAILABLE   -> ProfileState.AVAILABLE
				Intent.ACTION_MANAGED_PROFILE_UNLOCKED    -> ProfileState.UNLOCKED
				Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE -> ProfileState.UNAVAILABLE
				else -> throw IllegalStateException("Unexpected broadcast: $intent") }
		}

		override fun onActive() {
			mLiveBroadcastReceiver.observeForever(mDummyObserver)
			value = context.getSystemService(UserManager::class.java)!!.run {
				try { when {
					isUserUnlocked(profile) -> ProfileState.UNLOCKED
					isUserRunning(profile) -> ProfileState.AVAILABLE
					else -> ProfileState.UNAVAILABLE
				}} catch (e: SecurityException) { ProfileState.UNAVAILABLE }}    // "SecurityException: You need INTERACT_ACROSS_USERS or MANAGE_USERS permission to ...", probably due to Island being destroyed.
		}

		override fun onInactive() = mLiveBroadcastReceiver.removeObserver(mDummyObserver)

		private val mDummyObserver = Observer<Unit> {}
	}

	fun get(profile: UserHandle): LiveProfileState = states.getOrPut(profile) { LiveProfileState(profile) }

	val states = ArrayMap<UserHandle, LiveProfileState>()

	val mLiveBroadcastReceiver = object: LiveData<Unit>() {

		val ACTIONS = arrayOf(Intent.ACTION_MANAGED_PROFILE_AVAILABLE, Intent.ACTION_MANAGED_PROFILE_UNLOCKED, Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)

		override fun onActive() { context.registerReceiver(mReceiver, IntentFilter().apply { ACTIONS.forEach { addAction(it) }}) }
		override fun onInactive() = context.unregisterReceiver(mReceiver)

		private val mReceiver = object: BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) {
			states[intent.getParcelableExtra(Intent.EXTRA_USER) ?: return]?.updateFromBroadcast(intent) }}
	}
}
