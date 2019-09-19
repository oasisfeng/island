package com.oasisfeng.island.security;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.oasisfeng.android.app.LifecycleActivity;
import com.oasisfeng.androidx.biometric.BiometricPrompt;
import com.oasisfeng.island.mobile.R;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;

import static android.os.Build.VERSION_CODES.M;

/**
 * Security protection based on biometric or lock-screen credentials.
 *
 * Created by Oasis on 2019-1-17.
 */
public class SecurityPrompt {

	@RequiresApi(M) public static void showBiometricPrompt(final LifecycleActivity activity, final @StringRes int title,
														   final @StringRes int description, final Runnable on_authenticated) {
		final Handler handler = new Handler(Looper.getMainLooper());
		final Context app_context = activity.getApplication();
		new BiometricPrompt(activity, handler::post, new BiometricPrompt.AuthenticationCallback() {

			@Override public void onAuthenticationSucceeded(@NonNull final BiometricPrompt.AuthenticationResult result) {
				on_authenticated.run();
			}

			@Override public void onAuthenticationError(final int error, @NonNull final CharSequence error_message) {
				if (error == BiometricPrompt.ERROR_CANCELED | error == BiometricPrompt.ERROR_NEGATIVE_BUTTON) return;
				Toast.makeText(app_context, error_message, Toast.LENGTH_LONG).show();
//				if (error == BiometricPrompt.ERROR_TIMEOUT) return;
//
//				TODO: Security confirmation with UserManager.createUserCreationIntent()
			}

			@Override public void onAuthenticationFailed() {
				Toast.makeText(app_context, R.string.toast_security_confirmation_failure, Toast.LENGTH_LONG).show();
			}
		}).authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle(activity.getText(title))
				.setDescription(activity.getText(description)).setNegativeButtonText(activity.getText(android.R.string.cancel)).build());
	}
}
