package com.oasisfeng.island.settings;

import android.app.ActionBar;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.TwoStatePreference;
import android.view.MenuItem;

import com.oasisfeng.android.app.Activities;
import com.oasisfeng.island.mobile.R;
import com.oasisfeng.island.shared.BuildConfig;
import com.oasisfeng.island.util.DevicePolicies;
import com.oasisfeng.island.util.Modules;

import java.util.List;

import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;
import androidx.core.app.NavUtils;

import static android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK;
import static android.content.res.Configuration.SCREENLAYOUT_SIZE_XLARGE;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.N;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On handset devices, settings are presented as a single list.
 * On tablets, settings are split by category, with category headers shown to the left of the list of settings.
 */
public class SettingsActivity extends PreferenceActivity {

	public static void startWithPreference(final Context context, final Class<? extends PreferenceFragment> fragment) {
		final Intent intent = new Intent(context, SettingsActivity.class).putExtra(PreferenceActivity.EXTRA_SHOW_FRAGMENT, fragment.getName());
		Activities.startActivity(context, intent);
	}

	@Override protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		final ActionBar actionBar = getActionBar();
		if (actionBar != null) actionBar.setDisplayHomeAsUpEnabled(true);		// Show the Up button in the action bar.
	}

	@Override public boolean onMenuItemSelected(final int featureId, final MenuItem item) {
		final int id = item.getItemId();
		if (id == android.R.id.home) {
			if (! super.onMenuItemSelected(featureId, item)) NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	/**
	 * Binds a preference's summary to its value. More specifically, when the preference's value is changed, its summary (line of text
	 * below the preference title) is updated to reflect the value. The summary is also immediately updated upon calling this method.
	 * The exact display format is dependent on the type of preference.
	 */
	private static void bindPreferenceSummaryToValue(final Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);
		// Trigger the listener immediately with the preference's current value.
		sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
				PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
	}

	/** A preference value change listener that updates the preference's summary to reflect its new value. */
	private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = (preference, value) -> {
		String stringValue = value.toString();

		if (preference instanceof ListPreference) {
			// For list preferences, look up the correct display value in
			// the preference's 'entries' list.
			ListPreference listPreference = (ListPreference) preference;
			int index = listPreference.findIndexOfValue(stringValue);

			// Set the summary to reflect the new value.
			preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);
		} else {
			// For all other preferences, set the summary to the value's simple string representation.
			preference.setSummary(stringValue);
		}
		return true;
	};

	@Override public boolean onIsMultiPane() {
		return (getResources().getConfiguration().screenLayout & SCREENLAYOUT_SIZE_MASK) >= SCREENLAYOUT_SIZE_XLARGE;
	}

	@Override public void onBuildHeaders(final List<Header> target) { loadHeadersFromResource(R.xml.pref_headers, target); }

	/** This method stops fragment injection in malicious applications. Make sure to deny any unknown fragments here. */
	protected boolean isValidFragment(final String fragmentName) { return fragmentName.startsWith(getClass().getPackage().getName()); }

	static abstract class SubPreferenceFragment extends PreferenceFragment {

		protected SubPreferenceFragment(final @XmlRes int preference_xml, final int... keys_to_bind_summary) {
			mPreferenceXml = preference_xml;
			mKeysToBindSummary = keys_to_bind_summary;
		}

		@Override public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(mPreferenceXml);
			setHasOptionsMenu(true);
			// Bind the summaries of EditText/List/Dialog/Ringtone preferences to their values.
			// When their values change, their summaries are updated to reflect the new value, per the Android Design guidelines.
			for (final int key : mKeysToBindSummary) bindPreferenceSummaryToValue(findPreference(getString(key)));
		}

		@Override public boolean onOptionsItemSelected(final MenuItem item) {
			final int id = item.getItemId();
			if (id == android.R.id.home) {
				startActivity(new Intent(getActivity(), SettingsActivity.class));
				return true;
			} else return super.onOptionsItemSelected(item);
		}

		protected static boolean removeLeafPreference(final PreferenceGroup root, final Preference preference) {
			if (root.removePreference(preference)) return true;
			for (int i = 0; i < root.getPreferenceCount(); i ++) {
				final Preference child = root.getPreference(i);
				if (child instanceof PreferenceGroup && removeLeafPreference((PreferenceGroup) child, preference)) return true;
			}
			return false;
		}

		private final int mPreferenceXml;
		private final @StringRes int[] mKeysToBindSummary;
	}


	/** This fragment shows general preferences only. It is used when the activity is showing a two-pane settings UI. */
	public static class GeneralPreferenceFragment extends SubPreferenceFragment {
		public GeneralPreferenceFragment() { super(R.xml.pref_general, R.string.key_launch_shortcut_prefix); }

		@Override public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			final TwoStatePreference pref_show_admin_message = (TwoStatePreference) findPreference(getString(R.string.key_show_admin_message));
			final DevicePolicies policies;
			if (SDK_INT >= N && (policies = new DevicePolicies(getActivity())).isActiveDeviceOwner()) {
				pref_show_admin_message.setChecked(policies.invoke(DevicePolicyManager::getShortSupportMessage) != null);
				pref_show_admin_message.setOnPreferenceChangeListener((pref, value) -> {
					final boolean enabled = value == Boolean.TRUE;
					policies.execute(DevicePolicyManager::setShortSupportMessage, enabled ? getText(R.string.device_admin_support_message_short) : null);
					policies.execute(DevicePolicyManager::setLongSupportMessage, enabled ? getText(R.string.device_admin_support_message_long) : null);
					return true;
				});
			} else if (pref_show_admin_message != null) removeLeafPreference(getPreferenceScreen(), pref_show_admin_message);
		}
	}

	public static class PrivacyPreferenceFragment extends SubPreferenceFragment {
		public PrivacyPreferenceFragment() { super(R.xml.pref_privacy); }
	}

	public static class AboutFragment extends SubPreferenceFragment {

		@Override public void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			try {
				final PackageInfo pkg_info = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
				String summary;
				if (BuildConfig.DEBUG) {
					summary = pkg_info.versionName + " (" + pkg_info.versionCode + ")";
					if (! Modules.MODULE_ENGINE.equals(getActivity().getPackageName())) try {
						final PackageInfo engine_info = getActivity().getPackageManager().getPackageInfo(Modules.MODULE_ENGINE, 0);
						summary += ", Engine: " + engine_info.versionName + " (" + engine_info.versionCode + ")";
					} catch (final PackageManager.NameNotFoundException ignored) {}
				} else summary = pkg_info.versionName;
				findPreference(getString(R.string.key_version)).setSummary(summary);
			} catch (final PackageManager.NameNotFoundException ignored) {}		// Should never happen
		}

		public AboutFragment() { super(R.xml.pref_about); }
	}
}
