package com.oasisfeng.island.firebase;

import android.content.Context;

import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Wrapper of {@link FirebaseInstanceIdService} to ensure Firebase is lazily initialized first.
 *
 * Created by Oasis on 2017/7/22.
 */
public class LazyFirebaseInstanceIdService extends FirebaseInstanceIdService {

	@Override protected void attachBaseContext(final Context base) {
		super.attachBaseContext(FirebaseWrapper.init());
	}
}
