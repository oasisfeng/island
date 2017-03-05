package com.oasisfeng.common.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.support.annotation.UiThread;

/**
 * Utility class to resize icons to match default icon size.
 *
 * Derived from {@link android.app.LauncherActivity.IconResizer}
 */
public class IconResizer {
	// Code is borrowed from com.android.launcher.Utilities.
	private final int mIconWidth, mIconHeight;
	private final Rect mOldBounds = new Rect();
	private final Canvas mCanvas = new Canvas();

	public IconResizer() {
		mCanvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG,
				Paint.FILTER_BITMAP_FLAG));

		final Resources resources = Resources.getSystem();
		mIconWidth = mIconHeight = (int) resources.getDimension(
				android.R.dimen.app_icon_size);
	}

	/**
	 * Returns a Drawable representing the thumbnail of the specified Drawable.
	 * The size of the thumbnail is defined by the dimension
	 * android.R.dimen.launcher_application_icon_size.
	 *
	 * This method is not thread-safe and should be invoked on the UI thread only.
	 *
	 * @param icon The icon to get a thumbnail of.
	 *
	 * @return A thumbnail for the specified icon or the icon itself if the
	 *         thumbnail could not be created.
	 */
	@UiThread public Drawable createIconThumbnail(Drawable icon, float scale) {
		int width = mIconWidth;
		int height = mIconHeight;

		final int iconWidth = icon.getIntrinsicWidth();
		final int iconHeight = icon.getIntrinsicHeight();

		if (icon instanceof PaintDrawable) {
			final PaintDrawable painter = (PaintDrawable) icon;
			painter.setIntrinsicWidth(width);
			painter.setIntrinsicHeight(height);
		}

		if (width > 0 && height > 0) {
			final Canvas canvas = mCanvas;
			final Bitmap.Config c = Bitmap.Config.ARGB_8888;
			final Bitmap thumb = Bitmap.createBitmap(mIconWidth, mIconHeight, c);
			canvas.setBitmap(thumb);
			if (width < iconWidth || height < iconHeight) {
				final float ratio = (float) iconWidth / iconHeight;
				if (iconWidth > iconHeight) {
					height = (int) (width / ratio);
				} else if (iconHeight > iconWidth) {
					width = (int) (height * ratio);
				}
				mOldBounds.set(icon.getBounds());
				final int x = (mIconWidth - width) / 2;
				final int y = (mIconHeight - height) / 2;
				icon.setBounds(x, y, x + width, y + height);
			} else if (iconWidth < width && iconHeight < height) {
				mOldBounds.set(icon.getBounds());
				final int x = (width - iconWidth) / 2;
				final int y = (height - iconHeight) / 2;
				icon.setBounds(x, y, x + iconWidth, y + iconHeight);
			}
			canvas.save(Canvas.MATRIX_SAVE_FLAG);
			canvas.scale(scale, scale, mIconWidth / 2, mIconHeight / 2);
			icon.draw(canvas);
			icon.setBounds(mOldBounds);
			icon = new BitmapDrawable(Resources.getSystem(), thumb);
			canvas.setBitmap(null);
		}

		return icon;
	}
}
