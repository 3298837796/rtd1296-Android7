/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.NtpTrustedTime;
import android.util.TimeUtils;
import android.util.TrustedTime;
import android.os.SystemProperties;

import com.android.internal.telephony.TelephonyIntents;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import com.rtk.net.openwrt.UbusRpc;

/**
 * Monitors the network time and updates the system time if it is out of sync
 * and there hasn't been any NITZ update from the carrier recently.
 * If looking up the network time fails for some reason, it tries a few times with a short
 * interval and then resets to checking on longer intervals.
 * <p>
 * If the user enables AUTO_TIME, it will check immediately for the network time, if NITZ wasn't
 * available.
 * </p>
 */
public class NetworkTimeUpdateService extends Binder {

    private static final String TAG = "NetworkTimeUpdateService";
    private static final boolean DBG = false;

    private static final int EVENT_AUTO_TIME_CHANGED = 1;
    private static final int EVENT_POLL_NETWORK_TIME = 2;
    private static final int EVENT_NETWORK_CHANGED = 3;

    private static final String ACTION_POLL =
            "com.android.server.NetworkTimeUpdateService.action.POLL";

    private static final int NETWORK_CHANGE_EVENT_DELAY_MS = 1000;
    private static int POLL_REQUEST = 0;

    private static final long NOT_SET = -1;
    private long mNitzTimeSetTime = NOT_SET;
    // TODO: Have a way to look up the timezone we are in
    private long mNitzZoneSetTime = NOT_SET;

    private Context mContext;
    private TrustedTime mTime;

    // NTP lookup is done on this thread and handler
    private Handler mHandler;
    private AlarmManager mAlarmManager;
    private PendingIntent mPendingPollIntent;
    private SettingsObserver mSettingsObserver;
    // The last time that we successfully fetched the NTP time.
    private long mLastNtpFetchTime = NOT_SET;
    private final PowerManager.WakeLock mWakeLock;

    // Normal polling frequency
    private final long mPollingIntervalMs;
    // Try-again polling interval, in case the network request failed
    private final long mPollingIntervalShorterMs;
    // Number of times to try again
    private final int mTryAgainTimesMax;
    // If the time difference is greater than this threshold, then update the time.
    private final int mTimeErrorThresholdMs;
    // Keeps track of how many quick attempts were made to fetch NTP time.
    // During bootup, the network may not have been up yet, or it's taking time for the
    // connection to happen.
    private int mTryAgainCounter;
    private boolean mNeedRefreshTime;

    public NetworkTimeUpdateService(Context context) {
        mContext = context;
        mTime = NtpTrustedTime.getInstance(context);
        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        Intent pollIntent = new Intent(ACTION_POLL, null);
        mPendingPollIntent = PendingIntent.getBroadcast(mContext, POLL_REQUEST, pollIntent, 0);

        mPollingIntervalMs = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_ntpPollingInterval);
        /**
         * [BUG_FIX] DHCKYLIN-2131, ntp time sync too slow sometimes.
         * [ROOT_CAUSE] Default ntp servers may be unreliable to request ntp time.
         * [SOLUTION] In xen or 3in1 version, Android doesn't know if real network connection link up,
         * shorter ntp polling interval and increase retry times,
         * this configuration only effect before first success ntp polling.
         */
        if(UbusRpc.isXen() || (UbusRpc.isOpenWrt() && !UbusRpc.isOttWifi())){
            mPollingIntervalShorterMs = 10000;
            mTryAgainTimesMax = 1080; //1080 * 10000 = 3 hour
            if(DBG) Log.d(TAG, "In OpenWrt Network, shorter mPollingIntervalShorterMs:"+mPollingIntervalShorterMs);
        } else {
            mPollingIntervalShorterMs = mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_ntpPollingIntervalShorter);
            mTryAgainTimesMax = mContext.getResources().getInteger(
                    com.android.internal.R.integer.config_ntpRetry);
        }
        mTimeErrorThresholdMs = mContext.getResources().getInteger(
                com.android.internal.R.integer.config_ntpThreshold);

        mWakeLock = ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    /** Initialize the receivers and initiate the first NTP request */
    public void systemRunning() {
        registerForTelephonyIntents();
        registerForAlarms();
        registerForConnectivityIntents();

        HandlerThread thread = new HandlerThread(TAG);
        thread.start();
        mHandler = new MyHandler(thread.getLooper());
        // Check the network time on the new thread
        mHandler.obtainMessage(EVENT_POLL_NETWORK_TIME).sendToTarget();

        mSettingsObserver = new SettingsObserver(mHandler, EVENT_AUTO_TIME_CHANGED);
        mSettingsObserver.observe(mContext);
    }

    private void registerForTelephonyIntents() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TelephonyIntents.ACTION_NETWORK_SET_TIME);
        intentFilter.addAction(TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mContext.registerReceiver(mNitzReceiver, intentFilter);
    }

    private void registerForAlarms() {
        mContext.registerReceiver(
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mHandler.obtainMessage(EVENT_POLL_NETWORK_TIME).sendToTarget();
                }
            }, new IntentFilter(ACTION_POLL));
    }

    private void registerForConnectivityIntents() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        mContext.registerReceiver(mConnectivityReceiver, intentFilter);
    }

    private void onPollNetworkTime(int event) {
        //Resume from suspend, must force refresh time.
        if(mNeedRefreshTime && event==EVENT_NETWORK_CHANGED){
            if(mTime.forceRefresh()){
                Log.i(TAG, "Resume from suspend, forceRefresh time");
                mNeedRefreshTime = false;
                mWakeLock.acquire();
                try {
                    onPollNetworkTimeUnderWakeLock(EVENT_AUTO_TIME_CHANGED);
                } finally {
                    mWakeLock.release();
                }
            }
            return;
        }

        // If Automatic time is not set, don't bother.
        if (!isAutomaticTimeRequested()) return;
        mWakeLock.acquire();
        try {
            onPollNetworkTimeUnderWakeLock(event);
        } finally {
            mWakeLock.release();
        }
    }

    private void onPollNetworkTimeUnderWakeLock(int event) {
        final long refTime = SystemClock.elapsedRealtime();
        // If NITZ time was received less than mPollingIntervalMs time ago,
        // no need to sync to NTP.
        if (mNitzTimeSetTime != NOT_SET && refTime - mNitzTimeSetTime < mPollingIntervalMs) {
            resetAlarm(mPollingIntervalMs);
            return;
        }
        final long currentTime = System.currentTimeMillis();
        if (DBG) Log.d(TAG, "System time = " + currentTime);
        // Get the NTP time
        if (mLastNtpFetchTime == NOT_SET || refTime >= mLastNtpFetchTime + mPollingIntervalMs
                || event == EVENT_AUTO_TIME_CHANGED) {
            if (DBG) Log.d(TAG, "Before Ntp fetch");

            // force refresh NTP cache when outdated
            if (mTime.getCacheAge() >= mPollingIntervalMs) {
                mTime.forceRefresh();
            }

            // only update when NTP time is fresh
            if (mTime.getCacheAge() < mPollingIntervalMs) {
                final long ntp = mTime.currentTimeMillis();
                mTryAgainCounter = 0;
                // If the clock is more than N seconds off or this is the first time it's been
                // fetched since boot, set the current time.
                if (Math.abs(ntp - currentTime) > mTimeErrorThresholdMs
                        || mLastNtpFetchTime == NOT_SET) {
                    // Set the system time
                    if (DBG && mLastNtpFetchTime == NOT_SET
                            && Math.abs(ntp - currentTime) <= mTimeErrorThresholdMs) {
                        Log.d(TAG, "For initial setup, rtc = " + currentTime);
                    }
                    if (DBG) Log.d(TAG, "Ntp time to be set = " + ntp);
                    // Make sure we don't overflow, since it's going to be converted to an int
                    if (ntp / 1000 < Integer.MAX_VALUE) {
                        SystemClock.setCurrentTimeMillis(ntp);
                    }
                } else {
                    if (DBG) Log.d(TAG, "Ntp time is close enough = " + ntp);
                }
                mLastNtpFetchTime = SystemClock.elapsedRealtime();
            } else {
                // Try again shortly
                mTryAgainCounter++;
                if (mTryAgainTimesMax < 0 || mTryAgainCounter <= mTryAgainTimesMax) {
                    resetAlarm(mPollingIntervalShorterMs);
                } else {
                    // Try much later
                    mTryAgainCounter = 0;
                    resetAlarm(mPollingIntervalMs);
                }
                return;
            }
        }
        resetAlarm(mPollingIntervalMs);
    }

    /**
     * Cancel old alarm and starts a new one for the specified interval.
     *
     * @param interval when to trigger the alarm, starting from now.
     */
    private void resetAlarm(long interval) {
        mAlarmManager.cancel(mPendingPollIntent);
        long now = SystemClock.elapsedRealtime();
        long next = now + interval;
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME, next, mPendingPollIntent);
    }

    /**
     * Checks if the user prefers to automatically set the time.
     */
    private boolean isAutomaticTimeRequested() {
        return Settings.Global.getInt(
                mContext.getContentResolver(), Settings.Global.AUTO_TIME, 0) != 0;
    }

    /** Receiver for Nitz time events */
    private BroadcastReceiver mNitzReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (DBG) Log.d(TAG, "Received " + action);
            if (TelephonyIntents.ACTION_NETWORK_SET_TIME.equals(action)) {
                mNitzTimeSetTime = SystemClock.elapsedRealtime();
            } else if (TelephonyIntents.ACTION_NETWORK_SET_TIMEZONE.equals(action)) {
                mNitzZoneSetTime = SystemClock.elapsedRealtime();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                int mode = SystemProperties.getInt("persist.rtk.screenoff.suspend",0);
                if (mode==1){
                    if (DBG) Log.d(TAG, "S3 Suspend");
                    mNeedRefreshTime = true;
                }
            }
        }
    };

    /** Receiver for ConnectivityManager events */
    private BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(DBG) Log.d(TAG, "mConnectivityReceiver action="+action);
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)
                ||ConnectivityManager.INET_CONDITION_ACTION.equals(action)) {
                if (DBG) Log.d(TAG, "Received CONNECTIVITY_ACTION ");
                // Don't bother checking if we have connectivity, NtpTrustedTime does that for us.
                Message message = mHandler.obtainMessage(EVENT_NETWORK_CHANGED);
                // Send with a short delay to make sure the network is ready for use
                mHandler.sendMessageDelayed(message, NETWORK_CHANGE_EVENT_DELAY_MS);
            }
        }
    };

    /** Handler to do the network accesses on */
    private class MyHandler extends Handler {

        public MyHandler(Looper l) {
            super(l);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_AUTO_TIME_CHANGED:
                case EVENT_POLL_NETWORK_TIME:
                case EVENT_NETWORK_CHANGED:
                    onPollNetworkTime(msg.what);
                    break;
            }
        }
    }

    /** Observer to watch for changes to the AUTO_TIME setting */
    private static class SettingsObserver extends ContentObserver {

        private int mMsg;
        private Handler mHandler;

        SettingsObserver(Handler handler, int msg) {
            super(handler);
            mHandler = handler;
            mMsg = msg;
        }

        void observe(Context context) {
            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.AUTO_TIME),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            mHandler.obtainMessage(mMsg).sendToTarget();
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump NetworkTimeUpdateService from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " without permission "
                    + android.Manifest.permission.DUMP);
            return;
        }
        pw.print("PollingIntervalMs: ");
        TimeUtils.formatDuration(mPollingIntervalMs, pw);
        pw.print("\nPollingIntervalShorterMs: ");
        TimeUtils.formatDuration(mPollingIntervalShorterMs, pw);
        pw.println("\nTryAgainTimesMax: " + mTryAgainTimesMax);
        pw.print("TimeErrorThresholdMs: ");
        TimeUtils.formatDuration(mTimeErrorThresholdMs, pw);
        pw.println("\nTryAgainCounter: " + mTryAgainCounter);
        pw.print("LastNtpFetchTime: ");
        TimeUtils.formatDuration(mLastNtpFetchTime, pw);
        pw.println();
    }
}
