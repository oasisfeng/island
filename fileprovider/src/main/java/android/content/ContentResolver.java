package android.content;

import android.content.res.AssetFileDescriptor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Keep;
import androidx.annotation.RequiresApi;

import static android.os.Build.VERSION_CODES.KITKAT;
import static android.os.Build.VERSION_CODES.N;

/**
 * Stub class of real ContentResolver, only for compilation purpose.
 *
 * Created by Oasis on 2017/4/11.
 */
@Keep public abstract class ContentResolver {

	/* Critical hidden APIs implemented by ContextImpl.ApplicationContentResolver. They must be overridden by ContentResolver forwarder. */

	public/* protected */abstract IContentProvider acquireProvider(Context c, String name);
	public/* protected */IContentProvider acquireExistingProvider(final Context c, final String name) { return acquireProvider(c, name); }
	public abstract boolean releaseProvider(IContentProvider icp);
	public/* protected */abstract IContentProvider acquireUnstableProvider(Context c, String name);
	public abstract boolean releaseUnstableProvider(IContentProvider icp);
	public abstract void unstableProviderDied(IContentProvider icp);
	public void appNotRespondingViaProvider(final IContentProvider icp) { throw new UnsupportedOperationException("appNotRespondingViaProvider"); }

	/* Pure stubs without final and static methods */

	public ContentResolver(Context context) {
		throw new RuntimeException("Stub!");
	}

	public String[] getStreamTypes(Uri url, String mimeTypeFilter) {
		throw new RuntimeException("Stub!");
	}

	public ContentProviderResult[] applyBatch(String authority, ArrayList<ContentProviderOperation> operations) throws RemoteException, OperationApplicationException {
		throw new RuntimeException("Stub!");
	}

	public void notifyChange(Uri uri, ContentObserver observer) {
		throw new RuntimeException("Stub!");
	}

	public void notifyChange(Uri uri, ContentObserver observer, boolean syncToNetwork) {
		throw new RuntimeException("Stub!");
	}

	@RequiresApi(N) public void notifyChange(Uri uri, ContentObserver observer, int flags) {
		throw new RuntimeException("Stub!");
	}

	@RequiresApi(KITKAT) public void takePersistableUriPermission(Uri uri, int modeFlags) {
		throw new RuntimeException("Stub!");
	}

	@RequiresApi(KITKAT) public void releasePersistableUriPermission(Uri uri, int modeFlags) {
		throw new RuntimeException("Stub!");
	}

	@RequiresApi(KITKAT) public List<UriPermission> getPersistedUriPermissions() {
		throw new RuntimeException("Stub!");
	}

	@RequiresApi(KITKAT) public List<UriPermission> getOutgoingPersistedUriPermissions() {
		throw new RuntimeException("Stub!");
	}

	@Deprecated public void startSync(Uri uri, Bundle extras) {
		throw new RuntimeException("Stub!");
	}

	@Deprecated public void cancelSync(Uri uri) {
		throw new RuntimeException("Stub!");
	}

	public final String getType(Uri url) {
		throw new RuntimeException("Stub!");
	}

	public final Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		throw new RuntimeException("Stub!");
	}

	public final Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
		throw new RuntimeException("Stub!");
	}

	public final Cursor query(Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal) {
		throw new RuntimeException("Stub!");
	}

	public final Uri canonicalize(Uri url) {
		throw new RuntimeException("Stub!");
	}

	public final Uri uncanonicalize(Uri url) {
		throw new RuntimeException("Stub!");
	}

	public final boolean refresh(Uri url, Bundle args, CancellationSignal cancellationSignal) {
		throw new RuntimeException("Stub!");
	}

	public final InputStream openInputStream(Uri uri) throws FileNotFoundException {
		throw new RuntimeException("Stub!");
	}

	public final OutputStream openOutputStream(Uri uri) throws FileNotFoundException {
		throw new RuntimeException("Stub!");
	}

	public final OutputStream openOutputStream(Uri uri, String mode) throws FileNotFoundException {
		throw new RuntimeException("Stub!");
	}

	public final ParcelFileDescriptor openFileDescriptor(Uri uri, String mode) throws FileNotFoundException {
		throw new RuntimeException("Stub!");
	}

	public final ParcelFileDescriptor openFileDescriptor(Uri uri, String mode, CancellationSignal cancellationSignal) throws FileNotFoundException {
		throw new RuntimeException("Stub!");
	}

	public final AssetFileDescriptor openAssetFileDescriptor(Uri uri, String mode) throws FileNotFoundException {
		throw new RuntimeException("Stub!");
	}

	public final AssetFileDescriptor openAssetFileDescriptor(Uri uri, String mode, CancellationSignal cancellationSignal) throws FileNotFoundException {
		throw new RuntimeException("Stub!");
	}

	public final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType, Bundle opts) throws FileNotFoundException {
		throw new RuntimeException("Stub!");
	}

	public final AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType, Bundle opts, CancellationSignal cancellationSignal) throws FileNotFoundException {
		throw new RuntimeException("Stub!");
	}

	public final Uri insert(Uri url, ContentValues values) {
		throw new RuntimeException("Stub!");
	}

	public final int bulkInsert(Uri url, ContentValues[] values) {
		throw new RuntimeException("Stub!");
	}

	public final int delete(Uri url, String where, String[] selectionArgs) {
		throw new RuntimeException("Stub!");
	}

	public final int update(Uri uri, ContentValues values, String where, String[] selectionArgs) {
		throw new RuntimeException("Stub!");
	}

	public final Bundle call(Uri uri, String method, String arg, Bundle extras) {
		throw new RuntimeException("Stub!");
	}

	public final ContentProviderClient acquireContentProviderClient(Uri uri) {
		throw new RuntimeException("Stub!");
	}

	public final ContentProviderClient acquireContentProviderClient(String name) {
		throw new RuntimeException("Stub!");
	}

	public final ContentProviderClient acquireUnstableContentProviderClient(Uri uri) {
		throw new RuntimeException("Stub!");
	}

	public final ContentProviderClient acquireUnstableContentProviderClient(String name) {
		throw new RuntimeException("Stub!");
	}

	public final void registerContentObserver(Uri uri, boolean notifyForDescendants, ContentObserver observer) {
		throw new RuntimeException("Stub!");
	}

	public final void unregisterContentObserver(ContentObserver observer) {
		throw new RuntimeException("Stub!");
	}

	/* The static fields & methods used in this project */

	public static List<SyncInfo> getCurrentSyncs() {
		throw new RuntimeException("Stub!");
	}
}
