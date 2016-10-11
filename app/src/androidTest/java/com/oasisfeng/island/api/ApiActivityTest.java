package com.oasisfeng.island.api;

import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test cases for {@link ApiActivity}
 *
 * Created by Oasis on 2016/7/27.
 */
@MediumTest @RunWith(AndroidJUnit4.class)
public class ApiActivityTest {

	@Test public void testFreeze() {
		final Intent intent = new Intent(ApiActivity.ACTION_FREEZE).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.setSelector(new Intent("com.oasisfeng.island.action.FORWARD_ACTIVITY"));
		InstrumentationRegistry.getContext().startActivity(intent);
	}
}
