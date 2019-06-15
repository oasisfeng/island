package com.oasisfeng.island

import android.app.admin.DevicePolicyManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.O
import android.os.Bundle
import android.os.UserHandle
import androidx.annotation.WorkerThread
import com.oasisfeng.island.util.DevicePolicies
import java.util.concurrent.ConcurrentHashMap

/**
 * Manage the state of authorized delegation
 *
 * TODO: Support authorization granularity by user
 *
 * Created by Oasis on 2019-6-11.
 */
class DelegationManager(policies: DevicePolicies) {

    @WorkerThread fun isDelegationAuthorized(pkg: String, user: UserHandle, delegation: String): Boolean {
        if (mCacheIndex < sChangeIndex) mAppDelegationState.clear().also { mCacheIndex = sChangeIndex }
        val key = "$pkg:$delegation"
        return mAppDelegationState[key] ?: isDelegationAuthorized(mPolicies, pkg, user, delegation).also { mAppDelegationState[key] = it }
    }

    private val mPolicies = policies
    private val mAppDelegationState = ConcurrentHashMap<String, Boolean>()
    private var mCacheIndex = sChangeIndex

    companion object {

        @WorkerThread fun addAuthorizedDelegation(policies: DevicePolicies, pkg: String, user: UserHandle, delegation: String) {
            if (isDelegationSupportedByAndroid(delegation)) {
                val existentScopes = policies.invoke(DevicePolicyManager::getDelegatedScopes, pkg)
                if (existentScopes.contains(delegation)) return
                policies.invoke(DevicePolicyManager::setDelegatedScopes, pkg, existentScopes.plus(delegation))
            } else {
                val restrictions = policies.getApplicationRestrictions(pkg)
                val existentDelegations = getDelegationsFromRestrictions(restrictions)
                if (existentDelegations?.contains(delegation) == true) return
                restrictions.putStringArray(ApiConstants.TYPE_DELEGATION, existentDelegations?.plus(delegation) ?: arrayOf(delegation))
                policies.execute(DevicePolicyManager::setApplicationRestrictions, pkg, restrictions)
            }
            sChangeIndex ++
        }

        fun removeAuthorizedDelegation(policies: DevicePolicies, pkg: String, user: UserHandle?, delegation: String) {
            if (isDelegationSupportedByAndroid(delegation)) {
                val existentScopes = policies.invoke(DevicePolicyManager::getDelegatedScopes, pkg)
                if (! existentScopes.contains(delegation)) return
                policies.invoke(DevicePolicyManager::setDelegatedScopes, pkg, existentScopes.minus(delegation))
            } else {
                val restrictions = policies.getApplicationRestrictions(pkg)
                val existentDelegations = getDelegationsFromRestrictions(restrictions)?.toList() ?: emptyList()
                if (! existentDelegations.contains(delegation)) return
                val updatedDelegations = existentDelegations.minus(delegation)
                if (updatedDelegations.isEmpty()) restrictions.remove(ApiConstants.TYPE_DELEGATION)
                else restrictions.putStringArray(ApiConstants.TYPE_DELEGATION, updatedDelegations.toTypedArray())
                policies.execute(DevicePolicyManager::setApplicationRestrictions, pkg, restrictions)
            }
            sChangeIndex ++
        }

        @WorkerThread fun isDelegationAuthorized(policies: DevicePolicies, pkg: String, user: UserHandle, delegation: String): Boolean {
            if (isDelegationSupportedByAndroid(delegation))
                return policies.invoke(DevicePolicyManager::getDelegatedScopes, pkg).contains(delegation)
            return getDelegationsFromRestrictions(policies.getApplicationRestrictions(pkg))?.contains(delegation) ?: false
        }

        private var sChangeIndex = 0   // Simple in-classloader cache invalidation mechanism, effective enough for infrequent changes.
    }
}

private fun DevicePolicies.getApplicationRestrictions(pkg: String) = invoke(DevicePolicyManager::getApplicationRestrictions, pkg)
private fun getDelegationsFromRestrictions(restrictions: Bundle) = restrictions.getStringArray(ApiConstants.TYPE_DELEGATION)
private fun isDelegationSupportedByAndroid(delegation: String): Boolean = SDK_INT >= O && delegation == ApiConstants.DELEGATION_PACKAGE_ACCESS
