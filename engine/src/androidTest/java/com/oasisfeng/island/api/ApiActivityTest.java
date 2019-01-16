package com.oasisfeng.island.api;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;

import androidx.test.filters.MediumTest;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.runner.AndroidJUnit4;
import java9.util.concurrent.CompletableFuture;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;

/**
 * Test cases for {@link ApiActivity}
 *
 * Created by Oasis on 2016/7/27.
 */
@MediumTest @RunWith(AndroidJUnit4.class)
public class ApiActivityTest {

	private static final String MY_PKG = getContext().getPackageName();
	private static final PendingIntent CALLER_ID = PendingIntent.getBroadcast(getContext(), 0, new Intent(), FLAG_UPDATE_CURRENT);

	public static class DummyActivity extends Activity {
		@Override protected void onCreate(final Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			finish();
		}
	}

	@Rule public GrantPermissionRule mPermissionRule = GrantPermissionRule.grant(Api.latest.PERMISSION_FREEZE_PACKAGE, Api.latest.PERMISSION_LAUNCH_PACKAGE);

	@Test public void testApiBasic() throws ExecutionException, InterruptedException {
		final Intent intent = new Intent(Api.latest.ACTION_FREEZE).setData(Uri.fromParts("package", "a.b.c", null));
		invokeApiActivityAndReceiver(Api.latest.RESULT_UNVERIFIED_IDENTITY, intent);	// Missing EXTRA_CALLER_ID

		invokeApiActivityAndReceiver(Activity.RESULT_OK, intent.putExtra(Api.latest.EXTRA_CALLER_ID, CALLER_ID));

		intent.setData(Uri.fromParts("packages", "a.b.c,x.y.z", null));
		invokeApiActivityAndReceiver(Activity.RESULT_OK, intent);
	}

	@Test public void testLaunch() throws ExecutionException, InterruptedException {
		final Intent intent = new Intent(Api.latest.ACTION_LAUNCH).setData(Uri.fromParts("package", MY_PKG, null));
		invokeApiActivityAndReceiver(Activity.RESULT_OK, intent.putExtra(Api.latest.EXTRA_CALLER_ID, CALLER_ID));
	}

	private static void invokeApiActivityAndReceiver(final int expected_result, Intent intent) throws ExecutionException, InterruptedException {
		final Context context = getContext();
		intent = new Intent(intent).setPackage("com.oasisfeng.island");

		final CompletableFuture<Integer> result_code = new CompletableFuture<>();
		final CompletableFuture<String> result_data = new CompletableFuture<>();
		context.sendOrderedBroadcast(intent, null, new BroadcastReceiver() { @Override public void onReceive(final Context context, final Intent intent) {
			result_code.complete(getResultCode());
			result_data.complete(getResultData());
		}}, null, 0, null, null);
		assertEquals(result_data.get(), expected_result, (int) result_code.get());

		context.startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
	}
}
