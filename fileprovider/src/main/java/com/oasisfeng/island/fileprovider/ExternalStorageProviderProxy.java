package com.oasisfeng.island.fileprovider;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.DocumentsContract;
import android.util.Log;

import com.oasisfeng.android.util.Supplier;
import com.oasisfeng.island.analytics.Analytics;
import com.oasisfeng.island.util.Users;
import com.oasisfeng.pattern.PseudoContentProvider;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import static android.Manifest.permission.MANAGE_DOCUMENTS;
import static android.content.Context.CONTEXT_IGNORE_SECURITY;
import static android.content.Context.CONTEXT_INCLUDE_CODE;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.O;
import static android.provider.DocumentsContract.PROVIDER_INTERFACE;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_CATEGORY;
import static com.oasisfeng.island.analytics.Analytics.Param.ITEM_ID;

/**
 * Documents provider for accessing files in / outside Island.
 *
 * Created by Oasis on 2017/8/29.
 */
public class ExternalStorageProviderProxy extends ContentProvider {

	private static final String PREFIX_SHUTTLE_NAME = "⇌ ";
	private static final String TARGET_AUTHORITY = "com.android.externalstorage.documents";
	private static final String TARGET_PACKAGE = "com.android.externalstorage";

	private static final int FLAG_ADVANCED = 1 << 17;		// Disclosed from DocumentsContract.Root
	private static final int FLAG_REMOVABLE_SD = 1 << 19;

	private static final String EXTRA_URI = "uri";			// Disclosed from DocumentsContract
	private static final String EXTRA_PARENT_URI = "parentUri";
	private static final String EXTRA_TARGET_URI = "android.content.extra.TARGET_URI";

	@Override public @Nullable Cursor query(final @NonNull Uri uri, final @Nullable String[] projection, final @Nullable String selection, final @Nullable String[] selectionArgs, final @Nullable String sortOrder) {
		return processQuery(uri, () -> mDelegate.query(toTargetUri(uri), projection, selection, selectionArgs, sortOrder));
	}

	@Override public @Nullable Cursor query(final @NonNull Uri uri, final @Nullable String[] projection, final @Nullable String selection, final @Nullable String[] selectionArgs, final @Nullable String sortOrder, final @Nullable CancellationSignal cancellationSignal) {
		return processQuery(uri, () -> mDelegate.query(toTargetUri(uri), projection, selection, selectionArgs, sortOrder, cancellationSignal));
	}

	@RequiresApi(O) @Override public Cursor query(final Uri uri, final String[] projection, final Bundle queryArgs, final CancellationSignal cancellationSignal) {
		return processQuery(uri, () -> mDelegate.query(toTargetUri(uri), projection, queryArgs, cancellationSignal));
	}

	private @Nullable Cursor processQuery(final Uri uri, final Supplier<Cursor> procedure) {
		final Cursor cursor;
		try {
			cursor = procedure.get();
		} catch (final RuntimeException e) {
			final String message = "Error during query (in user " + Users.toId(Process.myUserHandle()) + "): " + uri;
			try {
				Analytics.$().logAndReport(TAG, message, e);
			} catch (final RuntimeException ignored) {}		// IllegalStateException will be thrown if GlobalContextProvider is not initialized yet.
			throw new IllegalStateException(message, e);
		}
		if (cursor == null) return null;
		if ("/root".equals(uri.getPath()))
			return new TweakedRootCursor(cursor, Objects.requireNonNull(getContext()).getString(R.string.file_shuttle_summary));
		return cursor;
	}

	@Override public @Nullable String getType(final @NonNull Uri uri) {
		return mDelegate.getType(toTargetUri(uri));
	}

	@Override public @Nullable Uri insert(final @NonNull Uri uri, final @Nullable ContentValues values) {
		return mDelegate.insert(toTargetUri(uri), values);
	}

	@Override public int bulkInsert(final @NonNull Uri uri, final @NonNull ContentValues[] values) {
		return mDelegate.bulkInsert(toTargetUri(uri), values);
	}

	@Override public int delete(final @NonNull Uri uri, final @Nullable String selection, final @Nullable String[] selectionArgs) {
		return mDelegate.delete(toTargetUri(uri), selection, selectionArgs);
	}

	@Override public int update(final @NonNull Uri uri, final @Nullable ContentValues values, final @Nullable String selection, final @Nullable String[] selectionArgs) {
		return mDelegate.update(toTargetUri(uri), values, selection, selectionArgs);
	}

	@Override public @NonNull ContentProviderResult[] applyBatch(final @NonNull ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
		return mDelegate.applyBatch(operations);	// FIXME: toTargetUri(uri)
	}

	@Override public @Nullable ParcelFileDescriptor openFile(final @NonNull Uri uri, final @NonNull String mode) throws FileNotFoundException {
		return mDelegate.openFile(toTargetUri(uri), mode);
	}

	@Override public @Nullable ParcelFileDescriptor openFile(final @NonNull Uri uri, final @NonNull String mode, final @Nullable CancellationSignal signal) throws FileNotFoundException {
		return mDelegate.openFile(toTargetUri(uri), mode, signal);
	}

	@Override public @Nullable AssetFileDescriptor openAssetFile(final @NonNull Uri uri, final @NonNull String mode) throws FileNotFoundException {
		return mDelegate.openAssetFile(toTargetUri(uri), mode);
	}

	@Override public @Nullable AssetFileDescriptor openAssetFile(final @NonNull Uri uri, final @NonNull String mode, @Nullable final CancellationSignal signal) throws FileNotFoundException {
		return mDelegate.openAssetFile(toTargetUri(uri), mode, signal);
	}

	@Override public @Nullable AssetFileDescriptor openTypedAssetFile(final @NonNull Uri uri, final @NonNull String mimeTypeFilter, final @Nullable Bundle opts) throws FileNotFoundException {
		return mDelegate.openTypedAssetFile(toTargetUri(uri), mimeTypeFilter, opts);
	}

	@Override public @Nullable AssetFileDescriptor openTypedAssetFile(final @NonNull Uri uri, final @NonNull String mimeTypeFilter, final @Nullable Bundle opts, final @Nullable CancellationSignal signal) throws FileNotFoundException {
		return mDelegate.openTypedAssetFile(toTargetUri(uri), mimeTypeFilter, opts, signal);
	}

	@Override public @Nullable Bundle call(final @NonNull String method, final @Nullable String arg, final @Nullable Bundle extras) {
		if (extras != null) {
			final Uri uri = extras.getParcelable(EXTRA_URI);
			if (uri != null) extras.putParcelable(EXTRA_URI, toTargetUri(uri));
			final Uri parent_uri = extras.getParcelable(EXTRA_PARENT_URI);
			if (parent_uri != null) extras.putParcelable(EXTRA_PARENT_URI, toTargetUri(parent_uri));
			final Uri target_uri = extras.getParcelable(EXTRA_TARGET_URI);
			if (target_uri != null) extras.putParcelable(EXTRA_TARGET_URI, toTargetUri(target_uri));
		}
		final Bundle result = mDelegate.call(method, arg, extras);
		if (result != null) {
			final Uri uri = result.getParcelable(EXTRA_URI);
			if (uri != null) result.putParcelable(EXTRA_URI, toProxyUri(uri));
		}
		return result;
	}

	private Uri toTargetUri(final Uri uri) {
		return mTargetAuthority != null ? replaceAuthorityInUri(uri, mTargetAuthority) : uri;
	}

	private Uri toProxyUri(final Uri uri) {
		return replaceAuthorityInUri(uri, mProviderInfo.authority);
	}

	private static Uri replaceAuthorityInUri(final Uri uri, final String authority) {
		final Uri.Builder builder = new Uri.Builder().scheme(uri.getScheme()).authority(authority).encodedPath(uri.getEncodedPath());
		final String query = uri.getEncodedQuery();
		if (query != null) builder.encodedQuery(query);
		final String fragment = uri.getEncodedFragment();
		if (fragment != null) builder.encodedFragment(fragment);
		return builder.build();
	}

	@Override public void attachInfo(final Context context, final ProviderInfo info) {
		mProviderInfo = info;
		super.attachInfo(context, info);
	}

	private Context wrapWithResolverWrapper(final Context context) {
		final ContentResolver resolver = context.getContentResolver();
		if (resolver instanceof ContentResolverWrapper) return context;		// Already wrapped
		return new ContextWrapper(context) {

			@Override public ContentResolver getContentResolver() {

				return new ContentResolverWrapper(context, super.getContentResolver()) {
					@Override public void notifyChange(final @NonNull Uri uri, final @Nullable ContentObserver observer) {
						resolver.notifyChange(toProxyUri(uri), observer);
					}

					@RequiresApi(N) @Override public void notifyChange(final @NonNull Uri uri, final @Nullable ContentObserver observer, final int flags) {
						resolver.notifyChange(toProxyUri(uri), observer, flags);
					}

					@Override public void notifyChange(final @NonNull Uri uri, final @Nullable ContentObserver observer, final boolean syncToNetwork) {
						resolver.notifyChange(toProxyUri(uri), observer, syncToNetwork);
					}
				};
			}
		};
	}

	public Context context() { return getContext(); }

	@Override public boolean onCreate() {	// As lazy as possible
		Log.v(TAG, "onCreate");

		final ProviderInfo target_provider = findTargetProvider(context());
		if (target_provider == null) {		// Logging is done in findTargetProvider().
			mDelegate = new PseudoContentProvider();
			return false;
		}
		mTargetAuthority = target_provider.authority;

		// Install IContentService interceptor to transform the URI of content changes notification sent by ExternalStorageProvider.
		try {	// Ensure ContentResolver.sContentService is initialized with the actual IContentService instance.
			@SuppressWarnings("unused") @SuppressLint("MissingPermission") final List<?> syncs = ContentResolver.getCurrentSyncs();
		} catch (final RuntimeException ignored) {}		// SecurityException is expected here.
		try {
			final Field ContentResolver_sContentService = ContentResolver.class.getDeclaredField("sContentService");
			ContentResolver_sContentService.setAccessible(true);
			final Object content_service = ContentResolver_sContentService.get(null);
			if (! Proxy.isProxyClass(content_service.getClass())) {		// There should be no other interceptor in this process.
				final Class<?> IContentService = ContentResolver_sContentService.getType();
				final Object proxy = Proxy.newProxyInstance(IContentService.getClassLoader(), new Class[] { IContentService }, (o, method, args) -> {
					if ("registerContentObserver".equals(method.getName())) args[0] = toProxyUri((Uri) args[0]);
					return method.invoke(content_service, args);
				});
				ContentResolver_sContentService.set(null, proxy);
			} else Analytics.$().event("esp_proxy_error").with(ITEM_CATEGORY, "content_service_proxy")
					.with(ITEM_ID, Proxy.getInvocationHandler(content_service).getClass().getCanonicalName()).send();

			final Context context = context().createPackageContext(target_provider.packageName, CONTEXT_INCLUDE_CODE | CONTEXT_IGNORE_SECURITY);
			@SuppressLint("PrivateApi") final Class<?> ExternalStorageProvider = context.getClassLoader().loadClass(target_provider.name);
			mDelegate = (ContentProvider) ExternalStorageProvider.newInstance();
			mDelegate.attachInfo(wrapWithResolverWrapper(context), target_provider);
			// onCreate() is indirectly invoked by attachInfo().
			return true;
		} catch (PackageManager.NameNotFoundException/* Should not happen */| ReflectiveOperationException e) {
			Analytics.$().logAndReport(TAG, "Failed to init due to incompatibility.", e);
		} catch (final Throwable t) {	// No just RuntimeException, but also Error expected. (e.g. NoSuchFieldError thrown by ExternalStorageProvider)
			Analytics.$().logAndReport(TAG, "Failed to init.", t);
		}
		mDelegate = new PseudoContentProvider();
		return false;
	}

	static @Nullable ProviderInfo findTargetProvider(final Context context) {
		final PackageManager pm = context.getPackageManager();
		final ProviderInfo provider = pm.resolveContentProvider(TARGET_AUTHORITY, 0);
		final String error_event = "esp_error";
		if (provider != null) {
			if (! provider.exported) Analytics.$().event(error_event).with(ITEM_CATEGORY, "not exported").send();
			else if (! MANAGE_DOCUMENTS.equals(provider.readPermission))
				Analytics.$().event(error_event).with(ITEM_CATEGORY, "permission").with(ITEM_ID, provider.readPermission).send();
			else return provider;
		}

		// In case authority cannot be found, try enumerating documents providers in package of its well-known name. (Fixed authority name is unnecessary for documents provider)
		final List<ResolveInfo> resolves = pm.queryIntentContentProviders(new Intent(PROVIDER_INTERFACE).setPackage(TARGET_PACKAGE), 0);
		if (resolves == null || resolves.isEmpty()) {
			Log.e(TAG, "Documents provider for external storage is missing.");
			return null;
		}
		final ResolveInfo first_resolve = resolves.get(0);
		if (resolves.size() > 1) {
			Log.w(TAG, "Potential providers: " + resolves);
			for (final ResolveInfo resolve : resolves) {
				final String authority = resolve.providerInfo.authority;
				final String info = resolve.toString();
				Analytics.$().event(error_event).with(ITEM_CATEGORY, "indistinguishable").with(ITEM_ID, authority + ":" + info).send();
				Log.w(TAG, ">> " + authority + ": " + info);
			}
		} else Analytics.$().event(error_event).with(ITEM_CATEGORY, "authority").with(ITEM_ID, first_resolve.providerInfo.authority).send();

		return first_resolve.providerInfo;
	}

	private ProviderInfo mProviderInfo;
	private @Nullable String mTargetAuthority;
	private ContentProvider mDelegate;

	private static final String TAG = "FileShuttle";

	private static class TweakedRootCursor extends CursorWrapper {

		TweakedRootCursor(final Cursor cursor, final String summary) {
			super(cursor);
			mTitleColumnIndex = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_TITLE);
			mFlagsColumnIndex = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_FLAGS);
			if (BuildConfig.DEBUG) dumpCursor(cursor);
			mAppendSummaryColumn = cursor.getColumnIndex(DocumentsContract.Root.COLUMN_SUMMARY) < 0;
			mSummary = summary;
			Log.d(TAG, "AppendSummaryColumn: " + mAppendSummaryColumn);
		}

		/* Append our COLUMN_SUMMARY to this cursor if absent in original cursor */

		@Override public int getColumnCount() {
			return mAppendSummaryColumn ? super.getColumnCount() + 1 : super.getColumnCount();
		}
		@Override public int getColumnIndex(final String name) {
			return mAppendSummaryColumn && DocumentsContract.Root.COLUMN_SUMMARY.equals(name) ? super.getColumnCount() : super.getColumnIndex(name);
		}
		@Override public int getColumnIndexOrThrow(final String name) throws IllegalArgumentException {
			return mAppendSummaryColumn && DocumentsContract.Root.COLUMN_SUMMARY.equals(name) ? super.getColumnCount() : super.getColumnIndexOrThrow(name);
		}
		@Override public String getColumnName(final int index) {
			return mAppendSummaryColumn && index == super.getColumnCount() ? DocumentsContract.Root.COLUMN_SUMMARY : super.getColumnName(index);
		}
		@Override public String[] getColumnNames() {
			String[] names = super.getColumnNames();
			if (mAppendSummaryColumn) {
				names = Arrays.copyOf(names, names.length + 1);
				names[names.length - 1] = DocumentsContract.Root.COLUMN_SUMMARY;
			}
			return names;
		}
		@Override public int getType(final int index) {
			return mAppendSummaryColumn && index == super.getColumnCount() ? FIELD_TYPE_STRING : super.getType(index);
		}

		@Override public String getString(final int column_index) {
			if (mAppendSummaryColumn && column_index == super.getColumnCount()) return mSummary;
			final String value = super.getString(column_index);
			Log.d(TAG, "Cursor.getString(" + getColumnName(column_index) + ") = " + value);
			return column_index == mTitleColumnIndex ? PREFIX_SHUTTLE_NAME + value : value;
		}

		@Override public long getLong(final int column_index) {		// TYPE_INTEGER is actually retrieved by getLong() for cross-process cursor.
			final int value = super.getInt(column_index);
			if (column_index != mFlagsColumnIndex) return value;
			if ((value & FLAG_REMOVABLE_SD) != 0) return value | FLAG_ADVANCED;		// TODO: Test removable storage in real device
			return value & ~ FLAG_ADVANCED;		// Make it always visible to user in document picker.
		}

		synchronized void dumpCursor(final Cursor cursor) {		// Synchronized to avoid interleaving by parallel dumping.
			final int position = cursor.getPosition();
			final String[] columns = cursor.getColumnNames();
			if (! cursor.moveToFirst()) {
				Log.d(TAG, "Empty cursor: " + Arrays.deepToString(columns));
				return;
			}
			Log.d(TAG, "===== CURSOR DUMP =====");
			do {
				for (int i = 0; i < columns.length; i ++) {
					final String column = cursor.getColumnName(i);
					switch (cursor.getType(i)) {
					case FIELD_TYPE_STRING: Log.d(TAG, column + ": " + cursor.getString(i)); break;
					case FIELD_TYPE_INTEGER: Log.d(TAG, column + ": " + cursor.getInt(i) + " (0x" + Integer.toHexString(cursor.getInt(i)) + ")"); break;
					case FIELD_TYPE_FLOAT: Log.d(TAG, column + ": " + cursor.getFloat(i)); break;
					case FIELD_TYPE_NULL: Log.d(TAG, column + ": null"); break;
					case FIELD_TYPE_BLOB: Log.d(TAG, column + ": blob, size=" + cursor.getBlob(i).length); break;
					}
				}
				Log.d(TAG, "=======================");
			} while (cursor.moveToNext());
			cursor.moveToPosition(position);
		}

		private final int mTitleColumnIndex;
		private final int mFlagsColumnIndex;
		private final boolean mAppendSummaryColumn;
		private final String mSummary;
	}
}
