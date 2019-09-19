package com.oasisfeng.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.preference.Preference;
import android.util.AttributeSet;

import androidx.annotation.StringRes;

/**
 * Improved {@link Preference}
 *
 * Created by Oasis on 2017/3/7.
 */
public class AdvancedPreference extends Preference {

	private static final String PACKAGE_PLACEHOLDER_SELF = "self";

	@Override public void setIntent(final Intent intent) {
		final ComponentName component = intent.getComponent();
		if (component != null && PACKAGE_PLACEHOLDER_SELF.equals(component.getPackageName())) {
			if (component.getClassName().isEmpty()) intent.setPackage(getContext().getPackageName()).setComponent(null);
			else intent.setComponent(new ComponentName(getContext().getPackageName(), component.getClassName()));
		}
		super.setIntent(intent);
	}

	public void setSummaryAndNotSelectable(final @StringRes int summary) {
		setSummary(summary);
		setSelectable(false);
	}

	public void setSummaryAndClickListener(final @StringRes int summary, final OnPreferenceClickListener listener) {
		setSummary(summary);
		setSelectable(true);
		setOnPreferenceClickListener(listener);
	}

	public AdvancedPreference(final Context context, final AttributeSet attrs) {
		super(context, attrs);
	}
}
