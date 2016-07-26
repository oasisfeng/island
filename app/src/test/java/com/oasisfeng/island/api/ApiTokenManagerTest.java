package com.oasisfeng.island.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressLint("CommitPrefEdits")
public class ApiTokenManagerTest {

	private static final String TEST_PKG = "com.oasisfeng.island.test";
	private static final String TEST_TOKEN = "123abc";

	@Test public void createNewTokenAndReuse() {
		final SharedPreferences prefs = mock(SharedPreferences.class);
		when(prefs.getString(TEST_PKG, null)).thenReturn(null);
		final SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
		when(prefs.edit()).thenReturn(editor);
		when(editor.putString(any(), any())).thenReturn(editor);

		final ApiTokenManager atm = mockApiTokenManager(prefs);
		final String token = atm.getToken(TEST_PKG);

		assertNotNull(token);
		assertEquals(16, token.length());
		for (int i = 0; i < token.length(); i ++) {
			final char c = token.charAt(i);
			assertTrue(c >= '0' && c <= '9' || c >= 'A' && c <= 'F');
		}

		verify(prefs).getString(TEST_PKG, null);
		verify(prefs).edit();
		verify(editor).putString(TEST_PKG, token);
		verify(editor).apply();

		// Reuse existent token

		when(prefs.getString(TEST_PKG, null)).thenReturn(token);

		final String token2 = atm.getToken(TEST_PKG);

		assertEquals(token, token2);
		verify(prefs, times(2)).getString(TEST_PKG, null);
		verify(prefs).edit();		// Ensure not called again
	}

	@Test public void verifyToken() {
		final SharedPreferences prefs = mock(SharedPreferences.class);
		when(prefs.contains(TEST_TOKEN)).thenReturn(true);

		final ApiTokenManager atm = mockApiTokenManager(prefs);
		assertTrue(atm.verifyToken(TEST_TOKEN));

		final String bad_token = TEST_TOKEN + "321";
		assertFalse(atm.verifyToken(bad_token));

		verify(prefs).contains(TEST_TOKEN);
		verify(prefs).contains(bad_token);
	}

	private ApiTokenManager mockApiTokenManager(final SharedPreferences prefs) {
		final Context context = mock(Context.class);
		when(context.getSharedPreferences(any(), eq(Context.MODE_PRIVATE))).thenReturn(prefs);
		return new ApiTokenManager(context);
	}
}
