package com.oasisfeng.ui.card;

import android.content.Context;
import android.view.View;

import com.oasisfeng.island.mobile.R;

import androidx.annotation.ColorInt;
import androidx.annotation.StringRes;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

/**
 * View-model for card
 *
 * Created by Oasis on 2017/9/7.
 */
public class CardViewModel {

	public CharSequence title;
	public CharSequence text;
	public CharSequence button_start;
	public CharSequence button_end;
	public @ColorInt int color;
	public boolean dismissible = true;

	protected CardViewModel(final Context context, final @StringRes int title, final @StringRes int text,
							final @StringRes int button_start, final @StringRes int button_end) {
		this.title = title == 0 ? null : context.getText(title);
		this.text = text == 0 ? null : context.getText(text);
		this.button_start = button_start == 0 ? null : context.getText(button_start);
		this.button_end = button_end == 0 ? null : context.getText(button_end);
		color = ContextCompat.getColor(context, R.color.card_attention);
	}

	@SuppressWarnings("MethodMayBeStatic") protected void dismiss(final CardView card) {
		card.setVisibility(View.GONE);
	}

	public void onButtonStartClick(final Context context, final CardView card) {}
	public void onButtonEndClick(final Context context, final CardView card) {}
}
