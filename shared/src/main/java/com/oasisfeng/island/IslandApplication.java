package com.oasisfeng.island;

import android.app.Application;

import com.oasisfeng.island.firebase.FirebaseLazyInit;
import com.oasisfeng.island.util.Users;

/**
 * Application class of Island
 *
 * Created by Oasis on 2017/3/21.
 */
public class IslandApplication extends Application {

	@Override public void onCreate() {
		super.onCreate();
		if (Users.isOwner()) FirebaseLazyInit.lazy(this);
	}
}
