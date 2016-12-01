package com.oasisfeng.island.model;

import android.os.Parcel;
import android.os.UserHandle;
import android.support.annotation.Nullable;

import com.oasisfeng.island.util.OwnerUser;
import com.oasisfeng.island.util.Users;

/**
 * View-model for global status
 *
 * Created by Oasis on 2016/5/4.
 */
public class GlobalStatus {

	public static final UserHandle OWNER;
	static {
		final Parcel p = Parcel.obtain();
		try {
			p.writeInt(0);
			p.setDataPosition(0);
			OWNER = new UserHandle(p);
		} finally {
			p.recycle();
		}
	}

	public static @Nullable UserHandle profile;		// Semi-immutable (until profile is created or destroyed)
	@OwnerUser public static boolean device_owner;

	public static final UserHandle current_user = Users.current();
	public static final boolean running_in_owner = Users.isOwner();
}
