package com.oasisfeng.island.util;

import android.content.Context;
import android.security.KeyPairGeneratorSpec;
import android.util.Log;

import com.oasisfeng.android.util.Supplier;
import com.oasisfeng.android.util.Suppliers;
import com.oasisfeng.island.analytics.Analytics;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import androidx.annotation.Nullable;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * Utility for signing and verification with Android Keystore.
 *
 * Created by Oasis on 2017/9/22.
 */
public class Cryptography {

	public static String sign(final Context context, final String data) throws GeneralSecurityException, IOException {
		final KeyStore keystore = getAndroidKeyStore();
		final KeyStore.Entry entry = keystore.getEntry(KEYPAIR_ALIAS, null);
		final PrivateKey private_key = (entry != null && entry instanceof KeyStore.PrivateKeyEntry) ? ((KeyStore.PrivateKeyEntry) entry).getPrivateKey()
				: generateCertificate(context).getPrivate();
		final Signature signer = Signature.getInstance("SHA512withRSA");
		signer.initSign(private_key);
		signer.update(data.getBytes(ISO_8859_1));
		return new String(signer.sign(), ISO_8859_1);
	}

	public static boolean verify(final String data, final @Nullable String signature) throws GeneralSecurityException {
		final KeyStore keystore = getAndroidKeyStore();
		final Certificate certificate = keystore.getCertificate(KEYPAIR_ALIAS);
		if (certificate == null) {
			Log.w(TAG, "Cannot verify due to certificate not found.");
			return false;
		}
		final Signature verifier = Signature.getInstance("SHA512withRSA");
		verifier.initVerify(certificate);			// Even if signature is null, we init the verification first and throw GeneralSecurityException if failed.
		verifier.update(data.getBytes(ISO_8859_1));	// So we could skip the verification when initialization failed, but reject the null signature otherwise.
		return signature != null && verifier.verify(signature.getBytes(ISO_8859_1));
	}

	private static KeyPair generateCertificate(final Context context) throws GeneralSecurityException, IOException {
		final KeyPairGenerator keypair_generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
		keypair_generator.initialize(new KeyPairGeneratorSpec.Builder(context).setAlias(KEYPAIR_ALIAS)//.setKeySize(1024)
				.setSubject(new X500Principal("CN=Island")).setSerialNumber(new BigInteger("123456789"))
				.setStartDate(new Date()).setEndDate(new Date(System.currentTimeMillis() + 25/* years */* 365 * 24 * 3600_000L)).build());
		return keypair_generator.generateKeyPair();
	}

	private static KeyStore getAndroidKeyStore() throws KeyStoreException {
		final KeyStore keystore = mAndroidKeystore.get();
		if (keystore == null) throw new KeyStoreException("Android keystore initialization error");
		return keystore;
	}

	private static final String TAG = "Crypto";
	private static final Supplier<KeyStore> mAndroidKeystore = Suppliers.memoize(() -> {
		try {
			final KeyStore keystore = KeyStore.getInstance("AndroidKeyStore");
			keystore.load(null);
			return keystore;
		} catch (GeneralSecurityException | IOException e) {
			Analytics.$().logAndReport(TAG, "Error loading Android keystore.", e);
			return null;
		}
	});
	private static final String KEYPAIR_ALIAS = "Island.Crypto";
}
