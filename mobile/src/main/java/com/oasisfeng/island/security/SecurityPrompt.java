package com.oasisfeng.island.security;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;

import com.oasisfeng.island.mobile.R;

/**
 * Security protection based on biometric or lock-screen credentials.
 *
 * Created by Oasis on 2019-1-17.
 */
public class SecurityPrompt {

	public static void showBiometricPrompt(final FragmentActivity activity, final @StringRes int title,
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
