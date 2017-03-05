package com.oasisfeng.common.databinding;

import android.databinding.BindingAdapter;
import android.support.v4.widget.DrawerLayout;
import android.view.View;

/**
 * Binding adapter for {@link DrawerLayout}
 *
 * Created by heruoxin on 2017/3/01.
 */
@SuppressWarnings("unused")
public class DrawerLayoutBindingAdapter {

	@BindingAdapter("drawerListener")
	public static void bindBottomSheetStateChange(final DrawerLayout drawer, final DrawerListener listener) {
        listener.setDrawer(drawer);
		drawer.addDrawerListener(listener);
	}

	public abstract static class DrawerListener implements DrawerLayout.DrawerListener {

		private DrawerLayout mDrawerLayout;

		private void setDrawer(DrawerLayout drawerLayout) {mDrawerLayout = drawerLayout;}

		protected final DrawerLayout getDrawer() {return mDrawerLayout;}

		@Override
		public void onDrawerSlide(View drawerView, float slideOffset) {}

		@Override
		public void onDrawerOpened(View drawerView) {}

		@Override
		public void onDrawerClosed(View drawerView) {}

		@Override
		public void onDrawerStateChanged(int newState) {}

	}

}