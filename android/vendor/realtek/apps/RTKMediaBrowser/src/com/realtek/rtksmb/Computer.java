package com.realtek.rtksmb;

import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by arpitkh996 on 16-01-2016.
 */public class Computer implements Parcelable {

    public String addr;
    public String name;

    public Computer(String name, String addr) {
        this.name = name;
        this.addr = addr;
    }

    public static final Creator<Computer> CREATOR = new Creator<Computer>() {
        @Override
        public Computer createFromParcel(Parcel in) {
            return new Computer(in);
        }

        @Override
        public Computer[] newArray(int size) {
            return new Computer[size];
        }
    };

    public String toString() {
        return String.format("%s [%s]", new Object[]{this.name, this.addr});
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(this.name);
        parcel.writeString(this.addr);
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Computer)) {
            return false;
        }
        if (this == obj || (this.name.equals(((Computer) obj).name) && this.addr.equals(((Computer) obj).addr))) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        return this.name.hashCode() + this.addr.hashCode();
    }

    private Computer(Parcel parcel) {
        this.name = parcel.readString();
        this.addr = parcel.readString();
    }

}
