package com.oasisfeng.island.firebase;

import com.google.firebase.iid.FirebaseInstanceIdService;

/**
 * Wrapper of {@link FirebaseInstanceIdService} to ensure Firebase is lazily initialized first.
 *
 * Created by Oasis on 2017/7/22.
 */
public class LazyFirebaseInstanceIdService extends FirebaseInstanceIdService {

	@Override public void onCreate() {
		super.onCreate();
		FirebaseWrapper.init(this);
	}
}
