package com.oasisfeng.island.shuttle;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The detail of method invocation to be shuttled.
 *
 * Created by Oasis on 2017/4/2.
 */
class MethodInvocation<Result> implements Parcelable {

	String clazz;
	Object[] args;
	Result result;

	MethodInvocation() {}

	private MethodInvocation(final Parcel in) {
		clazz = in.readString();
		args = in.readArray(getClass().getClassLoader());
	}

	void readFromParcel(final Parcel parcel) { //noinspection unchecked
		result = (Result) parcel.readValue(getClass().getClassLoader());
	}

	@Override public void writeToParcel(final Parcel dest, final int flags) {
		if ((flags & PARCELABLE_WRITE_RETURN_VALUE) == 0) {
			dest.writeString(clazz);
			dest.writeArray(args);
		} else dest.writeValue(result);
	}

	@Override public int describeContents() { return 0; }

	static final Creator<MethodInvocation> CREATOR = new Creator<MethodInvocation>() {
		@Override public MethodInvocation createFromParcel(final Parcel in) { return new MethodInvocation(in); }

		@Override public MethodInvocation[] newArray(final int size) { return new MethodInvocation[size]; }
	};
}
