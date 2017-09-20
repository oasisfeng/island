package com.oasisfeng.island.api;

import android.app.Activity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The API protocol
 *
 * <ul>
 * <li>Freeze specified app(s)</li>
 * {@link latest#ACTION_FREEZE} with app package name in data of "package" or "packages" (comma-separated) scheme.
 * <p>Result: {@link Activity#RESULT_OK} for success, {@link Activity#RESULT_CANCELED} for failure or {@link latest#RESULT_UNVERIFIED_IDENTITY}.
 *
 * <li>Unfreeze specified app(s)</li>
 * {@link latest#ACTION_UNFREEZE} with app package name in data of "package" or "packages" (comma-separated) scheme.
 * <p>Result: {@link Activity#RESULT_OK} for success, {@link Activity#RESULT_CANCELED} for failure or {@link latest#RESULT_UNVERIFIED_IDENTITY}.
 * </ol>
 *
 * <p>API revisions:
 *
 * <ul>
 * <li>v1.0 released in Island v2.0.
 * <li>v1.1 planned for Island v2.4.1.
 * </ul>
 *
 * Created by Oasis on 2017/9/19.
 */
public class Api {

	public interface latest extends v1 {}

	public interface v1 {
		@Since(.0) String ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE";		// data: "package:<package>" or "packages:<package1>,<package2>..."
		@Since(.1) String ACTION_UNFREEZE = "com.oasisfeng.island.action.UNFREEZE";	// data: same as above

		@Since(.0) String EXTRA_CALLER_ID = "caller";	// PendingIntent whose creator package is considered the caller of API

		/** Result code for unverified identity. */
		@Since(.0) int RESULT_UNVERIFIED_IDENTITY = Activity.RESULT_FIRST_USER;
	}

	@Retention(RetentionPolicy.SOURCE) private @interface Since { @SuppressWarnings("unused") double value(); }
}
