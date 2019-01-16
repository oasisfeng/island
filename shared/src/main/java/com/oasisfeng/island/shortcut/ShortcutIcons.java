package com.oasisfeng.island.shortcut;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import com.oasisfeng.android.ui.IconResizer;
import com.oasisfeng.island.analytics.Analytics;

import java.util.Objects;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import static android.os.Build.VERSION_CODES.O;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID;

/**
 * Helper for shortcut icon.
 *
 * Created by Oasis on 2017/9/18.
 */
public class ShortcutIcons {

	public static @Nullable Bitmap createLargeIconBitmap(final Context context, final Drawable drawable, final String pkg) {
		final int icon_size = Objects.requireNonNull((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getLauncherLargeIconSize();
		final Drawable icon = new IconResizer(icon_size).createIconThumbnail(drawable);	// Resize the app icon in case it's too large. (also avoid TransactionTooLargeException)
		icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
		final Bitmap bitmap = drawableToBitmap(icon);
		if (bitmap == null) Analytics.$().event("invalid_app_icon").with(ITEM_ID, pkg).with(ITEM_CATEGORY, drawable.getClass().getName()).send();
		return bitmap;
	}

	@RequiresApi(O) static Icon createAdaptiveIcon(final AdaptiveIconDrawable drawable) {
		final int width = drawable.getIntrinsicWidth() * 3 / 2, height = drawable.getIntrinsicHeight() * 3 / 2,
				start = drawable.getIntrinsicWidth() / 4, top = drawable.getIntrinsicHeight() / 4;
		drawable.setBounds(start, top, width - start, height - top);
		final Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(bitmap);
		drawable.draw(canvas);
		return Icon.createWithAdaptiveBitmap(bitmap);
	}

	private static Bitmap drawableToBitmap(final Drawable drawable) {
		if (drawable instanceof BitmapDrawable)
			return ((BitmapDrawable) drawable).getBitmap();
		if (drawable instanceof ColorDrawable) {
			return null;    //TODO: Support color drawable
		}
		final Bitmap bitmap = Bitmap.createBitmap(drawable.getBounds().width(), drawable.getBounds().height(), Bitmap.Config.ARGB_8888);
		final Canvas canvas = new Canvas(bitmap);
		drawable.draw(canvas);
		return bitmap;
	}
}
