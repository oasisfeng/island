package android.app;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Keep;

/**
 * Class holding the information about one unique operation of an application.
 *
 * (Stub class for AppOpsManager.OpEntry)
 */
@Keep public class AppOpsManager$OpEntry implements Parcelable {
	private final int mOp;
	private final int mMode;
	private final long[] mTimes;
	private final long[] mRejectTimes;
	private final int mDuration;
	private final int mProxyUid;
	private final boolean mRunning;
	private final String mProxyPackageName;

	public AppOpsManager$OpEntry(int op, int mode, long time, long rejectTime, int duration,
				   int proxyUid, String proxyPackage) {
		throw new UnsupportedOperationException("stub");
	}

	private void stub() {
		throw new UnsupportedOperationException("stub");
	}

	public AppOpsManager$OpEntry(int op, int mode, long[] times, long[] rejectTimes, int duration,
				   boolean running, int proxyUid, String proxyPackage) {
		throw new UnsupportedOperationException("stub");
	}

	public AppOpsManager$OpEntry(int op, int mode, long[] times, long[] rejectTimes, int duration,
				   int proxyUid, String proxyPackage) {
		throw new UnsupportedOperationException("stub");
	}

	public int getOp() {
		throw new UnsupportedOperationException("stub");
	}

	public int getMode() {
		throw new UnsupportedOperationException("stub");
	}

	public long getTime() {
		throw new UnsupportedOperationException("stub");
	}

	public long getLastAccessTime() {
		throw new UnsupportedOperationException("stub");
	}

	public long getLastAccessForegroundTime() {
		throw new UnsupportedOperationException("stub");
	}

	public long getLastAccessBackgroundTime() {
		throw new UnsupportedOperationException("stub");
	}

	public long getLastTimeFor(int uidState) {
		throw new UnsupportedOperationException("stub");
	}

	public long getRejectTime() {
		throw new UnsupportedOperationException("stub");
	}

	public long getLastRejectTime() {
		throw new UnsupportedOperationException("stub");
	}

	public long getLastRejectForegroundTime() {
		throw new UnsupportedOperationException("stub");
	}

	public long getLastRejectBackgroundTime() {
		throw new UnsupportedOperationException("stub");
	}

	public long getLastRejectTimeFor(int uidState) {
		throw new UnsupportedOperationException("stub");
	}

	public boolean isRunning() {
		throw new UnsupportedOperationException("stub");
	}

	public int getDuration() {
		throw new UnsupportedOperationException("stub");
	}

	public int getProxyUid() {
		throw new UnsupportedOperationException("stub");
	}

	public String getProxyPackageName() {
		throw new UnsupportedOperationException("stub");
	}

	@Override
	public int describeContents() {
		throw new UnsupportedOperationException("stub");
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		throw new UnsupportedOperationException("stub");
	}

	AppOpsManager$OpEntry(Parcel source) {
		throw new UnsupportedOperationException("stub");
	}

	public static final Creator<AppOpsManager$OpEntry> CREATOR = new Creator<AppOpsManager$OpEntry>() {
		@Override public AppOpsManager$OpEntry createFromParcel(Parcel source) {
			throw new UnsupportedOperationException("stub");
		}

		@Override public AppOpsManager$OpEntry[] newArray(int size) {
			throw new UnsupportedOperationException("stub");
		}
	};
}