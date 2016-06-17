package com.oasisfeng.island.model;

import android.os.Parcel;
import android.os.Process;
import android.os.UserHandle;

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

	public static final UserHandle CURRENT_USER = Process.myUserHandle();
	public static final boolean running_in_owner = OWNER.equals(CURRENT_USER);
}
