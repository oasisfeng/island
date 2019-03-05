package android.app;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

import androidx.annotation.Keep;

/**
 * Created by Oasis on 2019-3-3.
 */
@Keep public class AppOpsManager$PackageOps implements Parcelable {
	private final String mPackageName;
	private final int mUid;
	private final List<AppOpsManager$OpEntry> mEntries;

	public AppOpsManager$PackageOps(String packageName, int uid, List<AppOpsManager$OpEntry> entries) {
		throw new UnsupportedOperationException("stub");
	}

	public String getPackageName() {
		throw new UnsupportedOperationException("stub");
	}

	public int getUid() {
		throw new UnsupportedOperationException("stub");
	}

	public List<AppOpsManager$OpEntry> getOps() {
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

	AppOpsManager$PackageOps(Parcel source) {
		throw new UnsupportedOperationException("stub");
	}

	public static final Creator<AppOpsManager$PackageOps> CREATOR = new Creator<AppOpsManager$PackageOps>() {
		@Override public AppOpsManager$PackageOps createFromParcel(Parcel source) {
			throw new UnsupportedOperationException("stub");
		}

		@Override public AppOpsManager$PackageOps[] newArray(int size) {
			throw new UnsupportedOperationException("stub");
		}
	};
}