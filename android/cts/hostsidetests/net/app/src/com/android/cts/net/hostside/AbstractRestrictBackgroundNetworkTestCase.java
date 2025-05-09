/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.net.hostside;

import static android.cts.util.SystemUtil.runShellCommand;
import static android.net.ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.test.InstrumentationTestCase;
import android.util.Log;

/**
 * Superclass for tests related to background network restrictions.
 */
abstract class AbstractRestrictBackgroundNetworkTestCase extends InstrumentationTestCase {
    protected static final String TAG = "RestrictBackgroundNetworkTests";

    protected static final String TEST_PKG = "com.android.cts.net.hostside";
    protected static final String TEST_APP2_PKG = "com.android.cts.net.hostside.app2";

    private static final int SLEEP_TIME_SEC = 1;
    private static final boolean DEBUG = true;

    // Constants below must match values defined on app2's Common.java
    private static final String MANIFEST_RECEIVER = "ManifestReceiver";
    private static final String DYNAMIC_RECEIVER = "DynamicReceiver";
    private static final String ACTION_GET_COUNTERS =
            "com.android.cts.net.hostside.app2.action.GET_COUNTERS";
    private static final String ACTION_GET_RESTRICT_BACKGROUND_STATUS =
            "com.android.cts.net.hostside.app2.action.GET_RESTRICT_BACKGROUND_STATUS";
    private static final String ACTION_CHECK_NETWORK =
            "com.android.cts.net.hostside.app2.action.CHECK_NETWORK";
    private static final String ACTION_RECEIVER_READY =
            "com.android.cts.net.hostside.app2.action.RECEIVER_READY";
    static final String ACTION_SEND_NOTIFICATION =
            "com.android.cts.net.hostside.app2.action.SEND_NOTIFICATION";
    static final String ACTION_SHOW_TOAST =
            "com.android.cts.net.hostside.app2.action.SHOW_TOAST";
    private static final String EXTRA_ACTION = "com.android.cts.net.hostside.app2.extra.ACTION";
    private static final String EXTRA_RECEIVER_NAME =
            "com.android.cts.net.hostside.app2.extra.RECEIVER_NAME";
    private static final String EXTRA_NOTIFICATION_ID =
            "com.android.cts.net.hostside.app2.extra.NOTIFICATION_ID";
    private static final String EXTRA_NOTIFICATION_TYPE =
            "com.android.cts.net.hostside.app2.extra.NOTIFICATION_TYPE";

    protected static final String NOTIFICATION_TYPE_CONTENT = "CONTENT";
    protected static final String NOTIFICATION_TYPE_DELETE = "DELETE";
    protected static final String NOTIFICATION_TYPE_FULL_SCREEN = "FULL_SCREEN";
    protected static final String NOTIFICATION_TYPE_BUNDLE = "BUNDLE";
    protected static final String NOTIFICATION_TYPE_ACTION = "ACTION";
    protected static final String NOTIFICATION_TYPE_ACTION_BUNDLE = "ACTION_BUNDLE";
    protected static final String NOTIFICATION_TYPE_ACTION_REMOTE_INPUT = "ACTION_REMOTE_INPUT";


    private static final String NETWORK_STATUS_SEPARATOR = "\\|";
    private static final int SECOND_IN_MS = 1000;
    static final int NETWORK_TIMEOUT_MS = 15 * SECOND_IN_MS;
    private static final int PROCESS_STATE_FOREGROUND_SERVICE = 4;
    private static final int PROCESS_STATE_TOP = 2;


    // Must be higher than NETWORK_TIMEOUT_MS
    private static final int ORDERED_BROADCAST_TIMEOUT_MS = NETWORK_TIMEOUT_MS * 4;

    protected Context mContext;
    protected Instrumentation mInstrumentation;
    protected ConnectivityManager mCm;
    protected WifiManager mWfm;
    protected int mUid;
    private int mMyUid;
    private String mMeteredWifi;
    private boolean mHasWatch;
    private String mDeviceIdleConstantsSetting;
    private boolean mSupported;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mWfm = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mUid = getUid(TEST_APP2_PKG);
        mMyUid = getUid(mContext.getPackageName());
        mHasWatch = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WATCH);
        if (mHasWatch) {
            mDeviceIdleConstantsSetting = "device_idle_constants_watch";
        } else {
            mDeviceIdleConstantsSetting = "device_idle_constants";
        }
        mSupported = setUpActiveNetworkMeteringState();

        Log.i(TAG, "Apps status on " + getName() + ":\n"
                + "\ttest app: uid=" + mMyUid + ", state=" + getProcessStateByUid(mMyUid) + "\n"
                + "\tapp2: uid=" + mUid + ", state=" + getProcessStateByUid(mUid));
   }

    protected int getUid(String packageName) throws Exception {
        return mContext.getPackageManager().getPackageUid(packageName, 0);
    }

    protected void assertRestrictBackgroundChangedReceived(int expectedCount) throws Exception {
        assertRestrictBackgroundChangedReceived(DYNAMIC_RECEIVER, expectedCount);
        assertRestrictBackgroundChangedReceived(MANIFEST_RECEIVER, 0);
    }

    protected void assertRestrictBackgroundChangedReceived(String receiverName, int expectedCount)
            throws Exception {
        int attempts = 0;
        int count = 0;
        final int maxAttempts = 5;
        do {
            attempts++;
            count = getNumberBroadcastsReceived(receiverName, ACTION_RESTRICT_BACKGROUND_CHANGED);
            if (count == expectedCount) {
                break;
            }
            Log.d(TAG, "Expecting count " + expectedCount + " but actual is " + count + " after "
                    + attempts + " attempts; sleeping "
                    + SLEEP_TIME_SEC + " seconds before trying again");
            SystemClock.sleep(SLEEP_TIME_SEC * SECOND_IN_MS);
        } while (attempts <= maxAttempts);
        assertEquals("Number of expected broadcasts for " + receiverName + " not reached after "
                + maxAttempts * SLEEP_TIME_SEC + " seconds", expectedCount, count);
    }

    protected String sendOrderedBroadcast(Intent intent) throws Exception {
        return sendOrderedBroadcast(intent, ORDERED_BROADCAST_TIMEOUT_MS);
    }

    protected String sendOrderedBroadcast(Intent intent, int timeoutMs) throws Exception {
        final LinkedBlockingQueue<String> result = new LinkedBlockingQueue<>(1);
        Log.d(TAG, "Sending ordered broadcast: " + intent);
        mContext.sendOrderedBroadcast(intent, null, new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                final String resultData = getResultData();
                if (resultData == null) {
                    Log.e(TAG, "Received null data from ordered intent");
                    return;
                }
                result.offer(resultData);
            }
        }, null, 0, null, null);

        final String resultData = result.poll(timeoutMs, TimeUnit.MILLISECONDS);
        Log.d(TAG, "Ordered broadcast response after " + timeoutMs + "ms: " + resultData );
        return resultData;
    }

    protected int getNumberBroadcastsReceived(String receiverName, String action) throws Exception {
        final Intent intent = new Intent(ACTION_GET_COUNTERS);
        intent.putExtra(EXTRA_ACTION, ACTION_RESTRICT_BACKGROUND_CHANGED);
        intent.putExtra(EXTRA_RECEIVER_NAME, receiverName);
        final String resultData = sendOrderedBroadcast(intent);
        assertNotNull("timeout waiting for ordered broadcast result", resultData);
        return Integer.valueOf(resultData);
    }

    protected void assertRestrictBackgroundStatus(int expectedStatus) throws Exception {
        final Intent intent = new Intent(ACTION_GET_RESTRICT_BACKGROUND_STATUS);
        final String resultData = sendOrderedBroadcast(intent);
        assertNotNull("timeout waiting for ordered broadcast result", resultData);
        final String actualStatus = toString(Integer.parseInt(resultData));
        assertEquals("wrong status", toString(expectedStatus), actualStatus);
    }

    protected void assertMyRestrictBackgroundStatus(int expectedStatus) throws Exception {
        final int actualStatus = mCm.getRestrictBackgroundStatus();
        assertEquals("Wrong status", toString(expectedStatus), toString(actualStatus));
    }

    protected boolean isMyRestrictBackgroundStatus(int expectedStatus) throws Exception {
        final int actualStatus = mCm.getRestrictBackgroundStatus();
        if (expectedStatus != actualStatus) {
            Log.d(TAG, "Expected: " + toString(expectedStatus)
                    + " but actual: " + toString(actualStatus));
            return false;
        }
        return true;
    }

    protected void assertBackgroundNetworkAccess(boolean expectAllowed) throws Exception {
        assertBackgroundState(); // Sanity check.
        assertNetworkAccess(expectAllowed);
    }

    protected void assertForegroundNetworkAccess() throws Exception {
        assertForegroundState(); // Sanity check.
        assertNetworkAccess(true);
    }

    protected void assertForegroundServiceNetworkAccess() throws Exception {
        assertForegroundServiceState(); // Sanity check.
        assertNetworkAccess(true);
    }

    /**
     * Whether this device suport this type of test.
     *
     * <p>Should be overridden when necessary (but always calling
     * {@code super.isSupported()} first), and explicitly used before each test
     * Example:
     *
     * <pre><code>
     * public void testSomething() {
     *    if (!isSupported()) return;
     * </code></pre>
     *
     * @return {@code true} by default.
     */
    protected boolean isSupported() throws Exception {
        return mSupported;
    }

    /**
     * Asserts that an app always have access while on foreground or running a foreground service.
     *
     * <p>This method will launch an activity and a foreground service to make the assertion, but
     * will finish the activity / stop the service afterwards.
     */
    protected void assertsForegroundAlwaysHasNetworkAccess() throws Exception{
        // Checks foreground first.
        launchActivity();
        assertForegroundNetworkAccess();
        finishActivity();

        // Then foreground service
        startForegroundService();
        assertForegroundServiceNetworkAccess();
        stopForegroundService();
    }

    protected final void assertBackgroundState() throws Exception {
        final int maxTries = 30;
        ProcessState state = null;
        for (int i = 1; i <= maxTries; i++) {
            state = getProcessStateByUid(mUid);
            Log.v(TAG, "assertBackgroundState(): status for app2 (" + mUid + ") on attempt #" + i
                    + ": " + state);
            if (isBackground(state.state)) {
                return;
            }
            Log.d(TAG, "App not on background state (" + state + ") on attempt #" + i
                    + "; sleeping 1s before trying again");
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail("App2 is not on background state after " + maxTries + " attempts: " + state );
    }

    protected final void assertForegroundState() throws Exception {
        final int maxTries = 30;
        ProcessState state = null;
        for (int i = 1; i <= maxTries; i++) {
            state = getProcessStateByUid(mUid);
            Log.v(TAG, "assertForegroundState(): status for app2 (" + mUid + ") on attempt #" + i
                    + ": " + state);
            if (!isBackground(state.state)) {
                return;
            }
            Log.d(TAG, "App not on foreground state on attempt #" + i
                    + "; sleeping 1s before trying again");
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail("App2 is not on foreground state after " + maxTries + " attempts: " + state );
    }

    protected final void assertForegroundServiceState() throws Exception {
        final int maxTries = 30;
        ProcessState state = null;
        for (int i = 1; i <= maxTries; i++) {
            state = getProcessStateByUid(mUid);
            Log.v(TAG, "assertForegroundServiceState(): status for app2 (" + mUid + ") on attempt #"
                    + i + ": " + state);
            if (state.state == PROCESS_STATE_FOREGROUND_SERVICE) {
                return;
            }
            Log.d(TAG, "App not on foreground service state on attempt #" + i
                    + "; sleeping 1s before trying again");
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail("App2 is not on foreground service state after " + maxTries + " attempts: " + state );
    }

    /**
     * Returns whether an app state should be considered "background" for restriction purposes.
     */
    protected boolean isBackground(int state) {
        return state > PROCESS_STATE_FOREGROUND_SERVICE;
    }

    /**
     * Asserts whether the active network is available or not.
     */
    private void assertNetworkAccess(boolean expectAvailable) throws Exception {
        final int maxTries = 5;
        String error = null;
        int timeoutMs = 500;

        for (int i = 1; i <= maxTries; i++) {
            error = checkNetworkAccess(expectAvailable);

            if (error.isEmpty()) return;

            // TODO: ideally, it should retry only when it cannot connect to an external site,
            // or no retry at all! But, currently, the initial change fails almost always on
            // battery saver tests because the netd changes are made asynchronously.
            // Once b/27803922 is fixed, this retry mechanism should be revisited.

            Log.w(TAG, "Network status didn't match for expectAvailable=" + expectAvailable
                    + " on attempt #" + i + ": " + error + "\n"
                    + "Sleeping " + timeoutMs + "ms before trying again");
            SystemClock.sleep(timeoutMs);
            // Exponential back-off.
            timeoutMs = Math.min(timeoutMs*2, NETWORK_TIMEOUT_MS);
        }
        fail("Invalid state for expectAvailable=" + expectAvailable + " after " + maxTries
                + " attempts.\nLast error: " + error);
    }

    /**
     * Checks whether the network is available as expected.
     *
     * @return error message with the mismatch (or empty if assertion passed).
     */
    private String checkNetworkAccess(boolean expectAvailable) throws Exception {
        String resultData = sendOrderedBroadcast(new Intent(ACTION_CHECK_NETWORK));
        if (resultData == null) {
            return "timeout waiting for ordered broadcast";
        }
        // Network status format is described on MyBroadcastReceiver.checkNetworkStatus()
        final String[] parts = resultData.split(NETWORK_STATUS_SEPARATOR);
        assertEquals("Wrong network status: " + resultData, 5, parts.length); // Sanity check
        final State state = State.valueOf(parts[0]);
        final DetailedState detailedState = DetailedState.valueOf(parts[1]);
        final boolean connected = Boolean.valueOf(parts[2]);
        final String connectionCheckDetails = parts[3];
        final String networkInfo = parts[4];

        final StringBuilder errors = new StringBuilder();
        final State expectedState;
        final DetailedState expectedDetailedState;
        if (expectAvailable) {
            expectedState = State.CONNECTED;
            expectedDetailedState = DetailedState.CONNECTED;
        } else {
            expectedState = State.DISCONNECTED;
            expectedDetailedState = DetailedState.BLOCKED;
        }

        if (expectAvailable != connected) {
            errors.append(String.format("External site connection failed: expected %s, got %s\n",
                    expectAvailable, connected));
        }
        if (expectedState != state || expectedDetailedState != detailedState) {
            errors.append(String.format("Connection state mismatch: expected %s/%s, got %s/%s\n",
                    expectedState, expectedDetailedState, state, detailedState));
        }

        if (errors.length() > 0) {
            errors.append("\tnetworkInfo: " + networkInfo + "\n");
            errors.append("\tconnectionCheckDetails: " + connectionCheckDetails + "\n");
        }
        return errors.toString();
    }

    protected String executeShellCommand(String command) throws Exception {
        final String result = runShellCommand(mInstrumentation, command).trim();
        if (DEBUG) Log.d(TAG, "Command '" + command + "' returned '" + result + "'");
        return result;
    }

    /**
     * Runs a Shell command which is not expected to generate output.
     */
    protected void executeSilentShellCommand(String command) throws Exception {
        final String result = executeShellCommand(command);
        assertTrue("Command '" + command + "' failed: " + result, result.trim().isEmpty());
    }

    /**
     * Asserts the result of a command, wait and re-running it a couple times if necessary.
     */
    protected void assertDelayedShellCommand(String command, final String expectedResult)
            throws Exception {
        assertDelayedShellCommand(command, 5, 1, expectedResult);
    }

    protected void assertDelayedShellCommand(String command, int maxTries, int napTimeSeconds,
            final String expectedResult) throws Exception {
        assertDelayedShellCommand(command, maxTries, napTimeSeconds, new ExpectResultChecker() {

            @Override
            public boolean isExpected(String result) {
                return expectedResult.equals(result);
            }

            @Override
            public String getExpected() {
                return expectedResult;
            }
        });
    }

    protected void assertDelayedShellCommand(String command, ExpectResultChecker checker)
            throws Exception {
        assertDelayedShellCommand(command, 5, 1, checker);
    }
    protected void assertDelayedShellCommand(String command, int maxTries, int napTimeSeconds,
            ExpectResultChecker checker) throws Exception {
        String result = "";
        for (int i = 1; i <= maxTries; i++) {
            result = executeShellCommand(command).trim();
            if (checker.isExpected(result)) return;
            Log.v(TAG, "Command '" + command + "' returned '" + result + " instead of '"
                    + checker.getExpected() + "' on attempt #" + i
                    + "; sleeping " + napTimeSeconds + "s before trying again");
            SystemClock.sleep(napTimeSeconds * SECOND_IN_MS);
        }
        fail("Command '" + command + "' did not return '" + checker.getExpected() + "' after "
                + maxTries
                + " attempts. Last result: '" + result + "'");
    }

    /**
     * Sets the initial metering state for the active network.
     *
     * <p>It's called on setup and by default does nothing - it's up to the
     * subclasses to override.
     *
     * @return whether the tests in the subclass are supported on this device.
     */
    protected boolean setUpActiveNetworkMeteringState() throws Exception {
        return true;
    }

    /**
     * Makes sure the active network is not metered.
     *
     * <p>If the device does not supoprt un-metered networks (for example if it
     * only has cellular data but not wi-fi), it should return {@code false};
     * otherwise, it should return {@code true} (or fail if the un-metered
     * network could not be set).
     *
     * @return {@code true} if the network is now unmetered.
     */
    protected boolean setUnmeteredNetwork() throws Exception {
        final NetworkInfo info = mCm.getActiveNetworkInfo();
        assertNotNull("Could not get active network", info);
        if (!mCm.isActiveNetworkMetered()) {
            Log.d(TAG, "Active network is not metered: " + info);
        } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
            Log.i(TAG, "Setting active WI-FI network as not metered: " + info );
            setWifiMeteredStatus(false);
        } else {
            Log.d(TAG, "Active network cannot be set to un-metered: " + info);
            return false;
        }
        assertActiveNetworkMetered(false); // Sanity check.
        return true;
    }

    /**
     * Enables metering on the active network if supported.
     *
     * <p>If the device does not support metered networks it should return
     * {@code false}; otherwise, it should return {@code true} (or fail if the
     * metered network could not be set).
     *
     * @return {@code true} if the network is now metered.
     */
    protected boolean setMeteredNetwork() throws Exception {
        final NetworkInfo info = mCm.getActiveNetworkInfo();
        final boolean metered = mCm.isActiveNetworkMetered();
        if (metered) {
            Log.d(TAG, "Active network already metered: " + info);
            return true;
        } else if (info.getType() != ConnectivityManager.TYPE_WIFI) {
            Log.w(TAG, "Active network does not support metering: " + info);
            return false;
        } else {
            Log.w(TAG, "Active network not metered: " + info);
        }
        final String netId = setWifiMeteredStatus(true);

        // Set flag so status is reverted on resetMeteredNetwork();
        mMeteredWifi = netId;
        // Sanity check.
        assertWifiMeteredStatus(netId, true);
        assertActiveNetworkMetered(true);
        return true;
    }

    /**
     * Resets the device metering state to what it was before the test started.
     *
     * <p>This reverts any metering changes made by {@code setMeteredNetwork}.
     */
    protected void resetMeteredNetwork() throws Exception {
        if (mMeteredWifi != null) {
            Log.i(TAG, "resetMeteredNetwork(): SID '" + mMeteredWifi
                    + "' was set as metered by test case; resetting it");
            setWifiMeteredStatus(mMeteredWifi, false);
            assertActiveNetworkMetered(false); // Sanity check.
        }
    }

    private void assertActiveNetworkMetered(boolean expected) throws Exception {
        final int maxTries = 5;
        NetworkInfo info = null;
        for (int i = 1; i <= maxTries; i++) {
            info = mCm.getActiveNetworkInfo();
            if (info != null) {
                break;
            }
            Log.v(TAG, "No active network info on attempt #" + i
                    + "; sleeping 1s before polling again");
            Thread.sleep(SECOND_IN_MS);
        }
        assertNotNull("No active network after " + maxTries + " attempts", info);
        assertEquals("Wrong metered status for active network " + info, expected,
                mCm.isActiveNetworkMetered());
    }

    private String setWifiMeteredStatus(boolean metered) throws Exception {
        // We could call setWifiEnabled() here, but it might take sometime to be in a consistent
        // state (for example, if one of the saved network is not properly authenticated), so it's
        // better to let the hostside test take care of that.
        assertTrue("wi-fi is disabled", mWfm.isWifiEnabled());
        // TODO: if it's not guaranteed the device has wi-fi, we need to change the tests
        // to make the actual verification of restrictions optional.
        final String ssid = mWfm.getConnectionInfo().getSSID();
        return setWifiMeteredStatus(ssid, metered);
    }

    private String setWifiMeteredStatus(String ssid, boolean metered) throws Exception {
        assertNotNull("null SSID", ssid);
        final String netId = ssid.trim().replaceAll("\"", ""); // remove quotes, if any.
        assertFalse("empty SSID", ssid.isEmpty());

        Log.i(TAG, "Setting wi-fi network " + netId + " metered status to " + metered);
        final String setCommand = "cmd netpolicy set metered-network " + netId + " " + metered;
        assertDelayedShellCommand(setCommand, "");

        return netId;
    }

    private void assertWifiMeteredStatus(String netId, boolean status) throws Exception {
        final String command = "cmd netpolicy list wifi-networks";
        final String expectedLine = netId + ";" + status;
        assertDelayedShellCommand(command, new ExpectResultChecker() {

            @Override
            public boolean isExpected(String result) {
                return result.contains(expectedLine);
            }

            @Override
            public String getExpected() {
                return "line containing " + expectedLine;
            }
        });
    }

    protected void setRestrictBackground(boolean enabled) throws Exception {
        executeShellCommand("cmd netpolicy set restrict-background " + enabled);
        final String output = executeShellCommand("cmd netpolicy get restrict-background ");
        final String expectedSuffix = enabled ? "enabled" : "disabled";
        // TODO: use MoreAsserts?
        assertTrue("output '" + output + "' should end with '" + expectedSuffix + "'",
                output.endsWith(expectedSuffix));
      }

    protected void addRestrictBackgroundWhitelist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy add restrict-background-whitelist " + uid);
        assertRestrictBackgroundWhitelist(uid, true);
    }

    protected void removeRestrictBackgroundWhitelist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy remove restrict-background-whitelist " + uid);
        assertRestrictBackgroundWhitelist(uid, false);
    }

    protected void assertRestrictBackgroundWhitelist(int uid, boolean expected) throws Exception {
        assertRestrictBackground("restrict-background-whitelist", uid, expected);
    }

    protected void addRestrictBackgroundBlacklist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy add restrict-background-blacklist " + uid);
        assertRestrictBackgroundBlacklist(uid, true);
    }

    protected void removeRestrictBackgroundBlacklist(int uid) throws Exception {
        executeShellCommand("cmd netpolicy remove restrict-background-blacklist " + uid);
        assertRestrictBackgroundBlacklist(uid, false);
    }

    protected void assertRestrictBackgroundBlacklist(int uid, boolean expected) throws Exception {
        assertRestrictBackground("restrict-background-blacklist", uid, expected);
    }

    private void assertRestrictBackground(String list, int uid, boolean expected) throws Exception {
        final int maxTries = 5;
        boolean actual = false;
        final String expectedUid = Integer.toString(uid);
        String uids = "";
        for (int i = 1; i <= maxTries; i++) {
            final String output =
                    executeShellCommand("cmd netpolicy list " + list);
            uids = output.split(":")[1];
            for (String candidate : uids.split(" ")) {
                actual = candidate.trim().equals(expectedUid);
                if (expected == actual) {
                    return;
                }
            }
            Log.v(TAG, list + " check for uid " + uid + " doesn't match yet (expected "
                    + expected + ", got " + actual + "); sleeping 1s before polling again");
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail(list + " check for uid " + uid + " failed: expected " + expected + ", got " + actual
                + ". Full list: " + uids);
    }

    protected void assertPowerSaveModeWhitelist(String packageName, boolean expected)
            throws Exception {
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        assertDelayedShellCommand("dumpsys deviceidle whitelist =" + packageName,
                Boolean.toString(expected));
    }

    protected void addPowerSaveModeWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Adding package " + packageName + " to power-save-mode whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle whitelist +" + packageName);
        assertPowerSaveModeWhitelist(packageName, true); // Sanity check
    }

    protected void removePowerSaveModeWhitelist(String packageName) throws Exception {
        Log.i(TAG, "Removing package " + packageName + " from power-save-mode whitelist");
        // TODO: currently the power-save mode is behaving like idle, but once it changes, we'll
        // need to use netpolicy for whitelisting
        executeShellCommand("dumpsys deviceidle whitelist -" + packageName);
        assertPowerSaveModeWhitelist(packageName, false); // Sanity check
    }

    protected void turnBatteryOff() throws Exception {
        executeSilentShellCommand("cmd battery unplug");
    }

    protected void turnBatteryOn() throws Exception {
        executeSilentShellCommand("cmd battery reset");
    }

    protected void turnScreenOff() throws Exception {
        executeSilentShellCommand("input keyevent KEYCODE_SLEEP");
    }

    protected void turnScreenOn() throws Exception {
        executeSilentShellCommand("input keyevent KEYCODE_WAKEUP");
        executeSilentShellCommand("wm dismiss-keyguard");
    }

    protected void setBatterySaverMode(boolean enabled) throws Exception {
        Log.i(TAG, "Setting Battery Saver Mode to " + enabled);
        if (enabled) {
            turnBatteryOff();
            executeSilentShellCommand("cmd battery unplug");
            executeSilentShellCommand("settings put global low_power 1");
        } else {
            executeSilentShellCommand("settings put global low_power 0");
            turnBatteryOn();
        }
    }

    protected void setDozeMode(boolean enabled) throws Exception {
        // Sanity check, since tests should check beforehand....
        assertTrue("Device does not support Doze Mode", isDozeModeEnabled());

        Log.i(TAG, "Setting Doze Mode to " + enabled);
        if (enabled) {
            turnBatteryOff();
            turnScreenOff();
            executeShellCommand("dumpsys deviceidle force-idle deep");
        } else {
            turnScreenOn();
            turnBatteryOn();
            executeShellCommand("dumpsys deviceidle unforce");
        }
        // Sanity check.
        assertDozeMode(enabled);
    }

    protected void assertDozeMode(boolean enabled) throws Exception {
        assertDelayedShellCommand("dumpsys deviceidle get deep", enabled ? "IDLE" : "ACTIVE");
    }

    protected boolean isDozeModeEnabled() throws Exception {
        final String result = executeShellCommand("cmd deviceidle enabled deep").trim();
        return result.equals("1");
    }

    protected void setAppIdle(boolean enabled) throws Exception {
        Log.i(TAG, "Setting app idle to " + enabled);
        executeSilentShellCommand("am set-inactive " + TEST_APP2_PKG + " " + enabled );
        assertAppIdle(enabled); // Sanity check
    }

    protected void assertAppIdle(boolean enabled) throws Exception {
        assertDelayedShellCommand("am get-inactive " + TEST_APP2_PKG, 10, 2, "Idle=" + enabled);
    }

    /**
     * Starts a service that will register a broadcast receiver to receive
     * {@code RESTRICT_BACKGROUND_CHANGE} intents.
     * <p>
     * The service must run in a separate app because otherwise it would be killed every time
     * {@link #runDeviceTests(String, String)} is executed.
     */
    protected void registerBroadcastReceiver() throws Exception {
        executeShellCommand("am startservice com.android.cts.net.hostside.app2/.MyService");
        // Wait until receiver is ready.
        final int maxTries = 5;
        for (int i = 1; i <= maxTries; i++) {
            final String message =
                    sendOrderedBroadcast(new Intent(ACTION_RECEIVER_READY), SECOND_IN_MS);
            Log.d(TAG, "app2 receiver acked: " + message);
            if (message != null) {
                return;
            }
            Log.v(TAG, "app2 receiver is not ready yet; sleeping 1s before polling again");
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail("app2 receiver is not ready");
    }

    /**
     * Registers a {@link NotificationListenerService} implementation that will execute the
     * notification actions right after the notification is sent.
     */
    protected void registerNotificationListenerService() throws Exception {
        final StringBuilder listeners = new StringBuilder(getNotificationListenerServices());
        if (listeners.length() > 0) {
            listeners.append(":");
        }
        listeners.append(MyNotificationListenerService.getId());
        executeShellCommand("settings put secure enabled_notification_listeners " + listeners);
        final String newListeners = getNotificationListenerServices();
        assertEquals("Failed to set 'enabled_notification_listeners'",
                listeners.toString(), newListeners);
    }

    private String getNotificationListenerServices() throws Exception {
        return executeShellCommand("settings get secure enabled_notification_listeners");
    }

    protected void setPendingIntentWhitelistDuration(int durationMs) throws Exception {
        executeSilentShellCommand(String.format(
                "settings put global %s %s=%d", mDeviceIdleConstantsSetting,
                "notification_whitelist_duration", durationMs));
    }

    protected void resetDeviceIdleSettings() throws Exception {
        executeShellCommand(String.format("settings delete global %s",
                mDeviceIdleConstantsSetting));
    }

    protected void startForegroundService() throws Exception {
        executeShellCommand(
                "am startservice -f 1 com.android.cts.net.hostside.app2/.MyForegroundService");
        assertForegroundServiceState();
    }

    protected void stopForegroundService() throws Exception {
        executeShellCommand(
                "am startservice -f 2 com.android.cts.net.hostside.app2/.MyForegroundService");
        // NOTE: cannot assert state because it depends on whether activity was on top before.
    }

    /**
     * Launches an activity on app2 so its process is elevated to foreground status.
     */
    protected void launchActivity() throws Exception {
        turnScreenOn();
        executeShellCommand("am start com.android.cts.net.hostside.app2/.MyActivity");
        final int maxTries = 30;
        ProcessState state = null;
        for (int i = 1; i <= maxTries; i++) {
            state = getProcessStateByUid(mUid);
            if (state.state == PROCESS_STATE_TOP) return;
            Log.w(TAG, "launchActivity(): uid " + mUid + " not on TOP state on attempt #" + i
                    + "; turning screen on and sleeping 1s before checking again");
            turnScreenOn();
            SystemClock.sleep(SECOND_IN_MS);
        }
        fail("App2 is not on foreground state after " + maxTries + " attempts: " + state);
    }

    /**
     * Finishes an activity on app2 so its process is demoted fromforeground status.
     */
    protected void finishActivity() throws Exception {
        executeShellCommand("am broadcast -a "
                + " com.android.cts.net.hostside.app2.action.FINISH_ACTIVITY "
                + "--receiver-foreground --receiver-registered-only");
    }

    protected void sendNotification(int notificationId, String notificationType) {
        final Intent intent = new Intent(ACTION_SEND_NOTIFICATION);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId);
        intent.putExtra(EXTRA_NOTIFICATION_TYPE, notificationType);
        Log.d(TAG, "Sending notification broadcast (id=" + notificationId + ", type="
                + notificationType + ": " + intent);
        mContext.sendBroadcast(intent);
    }

    protected String showToast() {
        final Intent intent = new Intent(ACTION_SHOW_TOAST);
        intent.setPackage(TEST_APP2_PKG);
        Log.d(TAG, "Sending request to show toast");
        try {
            return sendOrderedBroadcast(intent, 3 * SECOND_IN_MS);
        } catch (Exception e) {
            return "";
        }
    }

    private String toString(int status) {
        switch (status) {
            case RESTRICT_BACKGROUND_STATUS_DISABLED:
                return "DISABLED";
            case RESTRICT_BACKGROUND_STATUS_WHITELISTED:
                return "WHITELISTED";
            case RESTRICT_BACKGROUND_STATUS_ENABLED:
                return "ENABLED";
            default:
                return "UNKNOWN_STATUS_" + status;
        }
    }

    private ProcessState getProcessStateByUid(int uid) throws Exception {
        return new ProcessState(executeShellCommand("cmd activity get-uid-state " + uid));
    }

    private static class ProcessState {
        private final String fullState;
        final int state;

        ProcessState(String fullState) {
            this.fullState = fullState;
            try {
                this.state = Integer.parseInt(fullState.split(" ")[0]);
            } catch (Exception e) {
                throw new IllegalArgumentException("Could not parse " + fullState);
            }
        }

        @Override
        public String toString() {
            return fullState;
        }
    }

    /**
     * Helper class used to assert the result of a Shell command.
     */
    protected static interface ExpectResultChecker {
        /**
         * Checkes whether the result of the command matched the expectation.
         */
        boolean isExpected(String result);
        /**
         * Gets the expected result so it's displayed on log and failure messages.
         */
        String getExpected();
    }
}
