package com.oasisfeng.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;

import com.oasisfeng.island.mobile.R;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

/** A preference with a clickable action icon on the side */
public class ActionButtonPreference extends AdvancedPreference implements View.OnClickListener {

	public void setSummaryAndActionButton(final @StringRes int summary, final @DrawableRes int icon, final OnPreferenceClickListener listener) {
		setSummaryAndNotSelectable(summary);
		mActionIcon = icon;
		mOnActionClickListener = listener;
		notifyChanged();
	}

	@Override protected void onBindView(final View view) {
		super.onBindView(view);
		final ImageView icon = (ImageView) view.findViewById(R.id.preference_action_button);
		if (mActionIcon != 0) {
			icon.setImageDrawable(getContext().getDrawable(mActionIcon));
			icon.setOnClickListener(this);
			icon.setEnabled(true);		// Make icon available even if the preference itself is disabled.
			icon.setVisibility(View.VISIBLE);
		} else icon.setVisibility(View.GONE);
	}

	@Override public void onClick(final View v) {
		if (v.getId() == R.id.preference_action_button)
			if (mOnActionClickListener != null)
				mOnActionClickListener.onPreferenceClick(this);
	}

	public ActionButtonPreference(final Context context, final AttributeSet attrs) {
		super(context, attrs);
		mActionIcon = attrs.getAttributeResourceValue("http://schemas.android.com/apk/res-auto", "icon", 0);
		setWidgetLayoutResource(R.layout.preference_widget_action_button);
	}

	private @DrawableRes int mActionIcon;
	private OnPreferenceClickListener mOnActionClickListener;
}
