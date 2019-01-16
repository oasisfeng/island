package com.oasisfeng.island.util;

import android.content.Context;
import android.text.Html;
import android.text.SpannedString;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Utility class for text format.
 *
 * Created by Oasis on 2017/8/28.
 */
public class TextFormat {

	public static @Nullable CharSequence getText(final Context context, final @StringRes int text, final Object... args) {
		return text == 0 ? null : args == null || args.length == 0 ? context.getText(text)
				: Html.fromHtml(String.format(Html.toHtml(new SpannedString(context.getText(text))), args));
	}
}
