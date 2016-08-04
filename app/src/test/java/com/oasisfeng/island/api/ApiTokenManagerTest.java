package com.oasisfeng.island.api;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Map;

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
	private static final String TEST_INTERNAL_TOKEN = "123abc";

	@Test public void createNewTokenAndReuse() {
		final SharedPreferences prefs = mock(SharedPreferences.class);
		when(prefs.getString(TEST_PKG, null)).thenReturn(null);
		final SharedPreferences.Editor editor = mock(SharedPreferences.Editor.class);
		when(prefs.edit()).thenReturn(editor);
		final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		when(editor.putString(any(), captor.capture())).thenReturn(editor);

		final ApiTokenManager atm = buildApiTokenManager(prefs);
		final String token = atm.getToken(TEST_PKG);

		assertNotNull(token);
		assertEquals(16 + 1 + TEST_PKG.length(), token.length());
		final String[] parts = token.split("@");
		assertEquals(2, parts.length);
		assertEquals(TEST_PKG, parts[1]);
		for (int i = 0; i < parts[0].length(); i ++) {
			final char c = parts[0].charAt(i);
			assertTrue(c >= '0' && c <= '9' || c >= 'A' && c <= 'F');
		}

		verify(prefs).getString(TEST_PKG, null);
		verify(prefs).edit();
		verify(editor).putString(TEST_PKG, parts[0]);
		verify(editor).apply();

		// Reuse existent token

		when(prefs.getString(TEST_PKG, null)).thenReturn(captor.getValue());

		final String token2 = atm.getToken(TEST_PKG);

		assertEquals(token, token2);
		verify(prefs, times(2)).getString(TEST_PKG, null);
		verify(prefs).edit();		// Ensure not called again
	}

	@Test public void verifyToken() {
		final SharedPreferences prefs = mock(SharedPreferences.class);
		when(prefs.getString(TEST_PKG, null)).thenReturn(TEST_INTERNAL_TOKEN);
		//noinspection unchecked
		when((Map<String, String>) prefs.getAll()).thenReturn(Collections.singletonMap(TEST_PKG, TEST_INTERNAL_TOKEN));

		final ApiTokenManager atm = buildApiTokenManager(prefs);
		assertTrue(atm.verifyToken(TEST_INTERNAL_TOKEN + "@" + TEST_PKG));
		assertTrue(atm.verifyToken(TEST_INTERNAL_TOKEN));	// Old style token

		final String bad_token = TEST_INTERNAL_TOKEN + "321";
		assertFalse(atm.verifyToken(bad_token));
	}

	private ApiTokenManager buildApiTokenManager(final SharedPreferences prefs) {
		final Context context = mock(Context.class);
		when(context.getSharedPreferences(any(), eq(Context.MODE_PRIVATE))).thenReturn(prefs);
		return new ApiTokenManager(context);
	}
}
