package com.rtk.debug;

import android.content.Context;
import android.os.SystemProperties;

/**
 * Created by archerho on 2015/10/7.
 */
public class DumpStream implements IDump{
    final String prop = "rtk.debug.media.DumpStream";
    private static DumpStream mIns;
    public static IDump getInstance(Context ctx) {
        if(mIns==null)
            mIns = new DumpStream();
        return mIns;
    }

    @Override
    public void on() {
        SystemProperties.set(prop, "true");
    }

    @Override
    public void off() {
        SystemProperties.set(prop, "false");
    }

    @Override
    public boolean isOn() {
        String s = SystemProperties.get(prop);
        return s.equals("true");
    }
}
