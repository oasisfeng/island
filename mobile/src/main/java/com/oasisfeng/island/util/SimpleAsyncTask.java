package com.oasisfeng.island.util;

import android.os.AsyncTask;
import androidx.annotation.WorkerThread;

/**
 * Simplified {@link AsyncTask} without parameters and result.
 *
 * Created by Oasis on 2016/11/6.
 */
public abstract class SimpleAsyncTask extends AsyncTask<Void, Void, Void> {

	public static void execute(final Runnable do_in_background, final Runnable on_post_execute) {
		new SimpleAsyncTask() {
			@Override protected void doInBackground() {
				do_in_background.run();
			}

			@Override protected void onPostExecute() {
				on_post_execute.run();
			}
		}.execute();
	}

	protected abstract void doInBackground();
	protected abstract void onPostExecute();

	@Override @WorkerThread protected final Void doInBackground(final Void... params) { doInBackground(); return null; }
	@Override protected final void onPostExecute(final Void aVoid) { onPostExecute(); }
}
