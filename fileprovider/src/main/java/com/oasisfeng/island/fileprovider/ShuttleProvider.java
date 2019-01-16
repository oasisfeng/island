package com.oasisfeng.island.fileprovider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.oasisfeng.island.util.Users;

import java.io.FileNotFoundException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import static android.os.Build.VERSION_CODES.O;

/**
 * This single-user provider serves as a proxy provider, since it can be accessed from any user.
 *
 * Created by Oasis on 2017/8/29.
 */
public class ShuttleProvider extends ContentProvider {

	private static final String EXTRA_URI = "uri";			// Disclosed from DocumentsContract

	@Override public void attachInfo(final Context context, final ProviderInfo info) {
		mShuttleAuthority = info.authority;
		final String host = info.authority.substring(0, info.authority.lastIndexOf(".shuttle"));	// Authority without trailing ".shuttle"
		Users.refreshUsers(context);	// Users may not be ready at this point, due to the parallel nature of provider initialization.
		mTargetAuthority = Users.toId(Users.isOwner() && Users.profile != null ? Users.profile : Users.owner) + "@" + host;		// Add user ID.
		Log.d(TAG, "Target authority: " + mTargetAuthority);
		super.attachInfo(context, info);
	}

	@Override public boolean onCreate() { //noinspection ConstantConditions
		mResolver = getContext().getContentResolver();
		return true;
	}

	@Nullable @Override public Cursor query(@NonNull final Uri uri, @Nullable final String[] projection, @Nullable final String selection, @Nullable final String[] selectionArgs, @Nullable final String sortOrder) {
		return forward(() -> mResolver.query(toTargetUri(uri), projection, selection, selectionArgs, sortOrder));
	}

	@RequiresApi(O) @Override public Cursor query(final Uri uri, final String[] projection, final Bundle queryArgs, final CancellationSignal cancellationSignal) {
		return forward(() -> mResolver.query(toTargetUri(uri), projection, queryArgs, cancellationSignal));
	}

	@Nullable @Override public Cursor query(@NonNull final Uri uri, @Nullable final String[] projection, @Nullable final String selection, @Nullable final String[] selectionArgs, @Nullable final String sortOrder, @Nullable final CancellationSignal cancellationSignal) {
		return forward(() -> mResolver.query(toTargetUri(uri), projection, selection, selectionArgs, sortOrder, cancellationSignal));
	}

	@Nullable @Override public String getType(@NonNull final Uri uri) {
		return forward(() -> mResolver.getType(toTargetUri(uri)));
	}

	@Nullable @Override public Uri insert(@NonNull final Uri uri, @Nullable final ContentValues values) {
		return forward(() -> mResolver.insert(toTargetUri(uri), values));
	}

	@Override public int bulkInsert(@NonNull final Uri uri, @NonNull final ContentValues[] values) {
		return forward(() -> mResolver.bulkInsert(toTargetUri(uri), values));
	}

	@Override public int delete(@NonNull final Uri uri, @Nullable final String selection, @Nullable final String[] selectionArgs) {
		return forward(() -> mResolver.delete(toTargetUri(uri), selection, selectionArgs));
	}

	@Override public int update(@NonNull final Uri uri, @Nullable final ContentValues values, @Nullable final String selection, @Nullable final String[] selectionArgs) {
		return forward(() -> mResolver.update(toTargetUri(uri), values, selection, selectionArgs));
	}

//	@RequiresApi(M) @NonNull @Override public ContentProviderResult[] applyBatch(@NonNull final ArrayList<ContentProviderOperation> operations) throws OperationApplicationException {
//		final ArrayList<Object> target_operations = new ArrayList<>(operations.size());
//		for (final ContentProviderOperation operation : operations) {
//			final ContentProviderOperation.Builder builder;
//			if (operation.isUpdate()) builder = ContentProviderOperation.newUpdate(operation.getUri());
//			else if (operation.isInsert()) builder = ContentProviderOperation.newInsert(operation.getUri());
//			else if (operation.isDelete()) builder = ContentProviderOperation.newDelete(operation.getUri());
//			if (operation. builder.withValues();
//			target_operations.add( ? );
//		}
//		return mResolver.applyBatch(operations);
//	}

	@Override public @Nullable ParcelFileDescriptor openFile(@NonNull final Uri uri, @NonNull final String mode) throws FileNotFoundException {
		return forwardFd(() -> mResolver.openFileDescriptor(toTargetUri(uri), mode));
	}

	@Override public @Nullable ParcelFileDescriptor openFile(@NonNull final Uri uri, @NonNull final String mode, @Nullable final CancellationSignal signal) throws FileNotFoundException {
		return forwardFd(() -> mResolver.openFileDescriptor(toTargetUri(uri), mode, signal));
	}

	@Override public @Nullable AssetFileDescriptor openAssetFile(@NonNull final Uri uri, @NonNull final String mode) throws FileNotFoundException {
		return forwardFd(() -> mResolver.openAssetFileDescriptor(toTargetUri(uri), mode));
	}

	@Override public @Nullable AssetFileDescriptor openAssetFile(@NonNull final Uri uri, @NonNull final String mode, @Nullable final CancellationSignal signal) throws FileNotFoundException {
		return forwardFd(() -> mResolver.openAssetFileDescriptor(toTargetUri(uri), mode, signal));
	}

	@Nullable @Override public AssetFileDescriptor openTypedAssetFile(@NonNull final Uri uri, @NonNull final String mimeTypeFilter, @Nullable final Bundle opts) throws FileNotFoundException {
		return forwardFd(() -> mResolver.openTypedAssetFileDescriptor(toTargetUri(uri), mimeTypeFilter, opts));
	}

	@Nullable @Override public AssetFileDescriptor openTypedAssetFile(@NonNull final Uri uri, @NonNull final String mimeTypeFilter, @Nullable final Bundle opts, @Nullable final CancellationSignal signal) throws FileNotFoundException {
		return forwardFd(() -> mResolver.openTypedAssetFileDescriptor(toTargetUri(uri), mimeTypeFilter, opts, signal));
	}

	@Nullable @Override public Bundle call(@NonNull final String method, @Nullable final String arg, @Nullable final Bundle extras) {
		return forward(() -> {
			final Bundle result = mResolver.call(new Uri.Builder().scheme("content").authority(mTargetAuthority).build(), method, arg, extras);
			if (result != null) {
				final Uri uri = result.getParcelable(EXTRA_URI);
				if (uri != null) result.putParcelable(EXTRA_URI, toProxyUri(uri));
			}
			return result;
		});
	}

	private Uri toTargetUri(final Uri uri) {
		return mTargetAuthority != null ? replaceAuthorityInUri(uri, mTargetAuthority) : uri;
	}

	private Uri toProxyUri(final Uri uri) {
		return replaceAuthorityInUri(uri, mShuttleAuthority);
	}

	private static Uri replaceAuthorityInUri(final Uri uri, final String authority) {
		final Uri.Builder builder = new Uri.Builder().scheme(uri.getScheme()).authority(authority).encodedPath(uri.getEncodedPath());
		final String query = uri.getEncodedQuery();
		if (query != null) builder.encodedQuery(query);
		final String fragment = uri.getEncodedFragment();
		if (fragment != null) builder.encodedFragment(fragment);
		return builder.build();
	}

	private static final String TAG = "ShuttleProvider";

	private interface Procedure<T, E extends Exception> {
		T execute() throws E;
	}

	/** Reset calling identity to eliminate possible SecurityException thrown from the target provider. */
	private static <T> T forward(final Procedure<T, RuntimeException> procedure) {
		final long caller = Binder.clearCallingIdentity();
		try { return procedure.execute(); }
		finally { Binder.restoreCallingIdentity(caller); }
	}

	private static <T> T forwardFd(final Procedure<T, FileNotFoundException> procedure) throws FileNotFoundException {
		final long caller = Binder.clearCallingIdentity();
		try { return procedure.execute(); }
		finally { Binder.restoreCallingIdentity(caller); }
	}

	private ContentResolver mResolver;
	private String mShuttleAuthority;
	private @Nullable String mTargetAuthority;
}
