package com.oasisfeng.island.fileprovider;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IContentProvider;
import android.content.OperationApplicationException;
import android.content.UriPermission;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Delegation wrapper of {@link ContentResolver}
 *
 * Created by Oasis on 2017/4/11.
 */
class ContentResolverWrapper extends ContentResolver {

	@Override public IContentProvider acquireProvider(final Context c, final String name) { return mBase.acquireProvider(c, name); }
	@Override public IContentProvider acquireExistingProvider(final Context c, final String name) { return mBase.acquireExistingProvider(c, name); }
	@Override public boolean releaseProvider(final IContentProvider icp) { return mBase.releaseProvider(icp); }
	@Override public IContentProvider acquireUnstableProvider(final Context c, final String name)	{ return mBase.acquireUnstableProvider(c, name); }
	@Override public boolean releaseUnstableProvider(IContentProvider icp) { return mBase.releaseUnstableProvider(icp); }
	@Override public void unstableProviderDied(IContentProvider icp) { mBase.unstableProviderDied(icp); }

	/* Pure forwarding */

	@Override @Nullable public String[] getStreamTypes(@NonNull Uri url, @NonNull String mimeTypeFilter) {
		return mBase.getStreamTypes(url, mimeTypeFilter);
	}

	@Override @NonNull public ContentProviderResult[] applyBatch(@NonNull String authority, @NonNull ArrayList<ContentProviderOperation> operations) throws RemoteException, OperationApplicationException {
		return mBase.applyBatch(authority, operations);
	}

	@Override public void notifyChange(@NonNull Uri uri, @Nullable ContentObserver observer) {
		mBase.notifyChange(uri, observer);
	}

	@Override public void notifyChange(@NonNull Uri uri, @Nullable ContentObserver observer, boolean syncToNetwork) {
		mBase.notifyChange(uri, observer, syncToNetwork);
	}

	@Override public void notifyChange(@NonNull Uri uri, @Nullable ContentObserver observer, int flags) {
		mBase.notifyChange(uri, observer, flags);
	}

	@Override public void takePersistableUriPermission(@NonNull Uri uri, int modeFlags) {
		mBase.takePersistableUriPermission(uri, modeFlags);
	}

	@Override public void releasePersistableUriPermission(@NonNull Uri uri, int modeFlags) {
		mBase.releasePersistableUriPermission(uri, modeFlags);
	}

	@Override @NonNull public List<UriPermission> getPersistedUriPermissions() {
		return mBase.getPersistedUriPermissions();
	}

	@Override @NonNull public List<UriPermission> getOutgoingPersistedUriPermissions() {
		return mBase.getOutgoingPersistedUriPermissions();
	}

	@Override public void startSync(Uri uri, Bundle extras) {
		mBase.startSync(uri, extras);
	}

	@Override public void cancelSync(Uri uri) {
		mBase.cancelSync(uri);
	}

	ContentResolverWrapper(final Context context, final ContentResolver base) { super(context); mBase = base; }

	private final ContentResolver mBase;
}
