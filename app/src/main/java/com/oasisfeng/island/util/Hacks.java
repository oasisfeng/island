package com.oasisfeng.island.util;

import android.content.pm.ApplicationInfo;

import com.oasisfeng.hack.Hack;

/**
 * All reflection-based hacks should be defined here
 *
 * Created by Oasis on 2016/8/10.
 */
public class Hacks {

	public static final Hack.HackedField<ApplicationInfo, Integer> ApplicationInfo_privateFlags;

	static {
		ApplicationInfo_privateFlags = Hack.into(ApplicationInfo.class).field("privateFlags").fallbackTo(null);
	}
}
