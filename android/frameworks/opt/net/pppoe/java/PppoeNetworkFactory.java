/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.server.pppoe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpResults;
import android.net.EthernetManager;
import android.net.IEthernetServiceListener;
import android.net.InterfaceConfiguration;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkUtils;
import android.net.StaticIpConfiguration;
import android.os.Handler;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.IndentingPrintWriter;
import com.android.server.net.BaseNetworkObserver;

import java.io.FileDescriptor;
import java.io.PrintWriter;

import java.net.InetAddress;
import android.net.LinkAddress;
import android.net.RouteInfo;
import android.os.SystemProperties;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import android.net.NetworkMisc;

/**
 * Manages connectivity for an Ethernet interface.
 *
 * Ethernet Interfaces may be present at boot time or appear after boot (e.g.,
 * for Ethernet adapters connected over USB). This class currently supports
 * only one interface. When an interface appears on the system (or is present
 * at boot time) this class will start tracking it and bring it up, and will
 * attempt to connect when requested. Any other interfaces that subsequently
 * appear will be ignored until the tracked interface disappears. Only
 * interfaces whose names match the <code>config_ethernet_iface_regex</code>
 * regular expression are tracked.
 *
 * This class reports a static network score of 70 when it is tracking an
 * interface and that interface's link is up, and a score of 0 otherwise.
 *
 * @hide
 */
public class PppoeNetworkFactory {
    private static final String NETWORK_TYPE = "PPPOE";
    private static final String TAG = "PppoeNetworkFactory";
    private static final int NETWORK_SCORE = 72;
    private static final boolean DBG = true;

    /** Tracks interface changes. Called from NetworkManagementService. */
    private InterfaceObserver mInterfaceObserver;

    /** To set link state and configure IP addresses. */
    private INetworkManagementService mNMService;

    /* To communicate with ConnectivityManager */
    private NetworkCapabilities mNetworkCapabilities;
    private NetworkAgent mNetworkAgent;
    private LocalNetworkFactory mFactory;
    // private final Looper mLooper;
    private Context mContext;

    /** Product-dependent regular expression of interface names we track. */
    private static String mIfaceMatch = "ppp\\d";

    /** Data members. All accesses to these must be synchronized(this). */
    private static String mIface = "";
    private String mHwAddr;
    private static boolean mLinkUp;
    private NetworkInfo mNetworkInfo;
    private LinkProperties mLinkProperties;

    StaticIpConfiguration mStaticIpConfig;

    public PppoeNetworkFactory() {
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_PPPOE, 0, NETWORK_TYPE, "");
        initNetworkCapabilities();
    }

    private class LocalNetworkFactory extends NetworkFactory {
        LocalNetworkFactory(String name, Context context, Looper looper) {
            super(looper, context, name, new NetworkCapabilities());
        }

        protected void startNetwork() {
            // onRequestNetwork();
        }
        protected void stopNetwork() {
        }
    }

    /**
     * Updates interface state variables.
     * Called on link state changes or on startup.
     */
    private void updateInterfaceState(String iface, boolean up) {
        if (!mIface.equals(iface)) {
            return;
        }
        Log.d(TAG, "updateInterface: " + iface + " link " + (up ? "up" : "down"));

        synchronized(this) {
            mLinkUp = up;
            mNetworkInfo.setIsAvailable(up);
            if (!up) {
                // Tell the agent we're disconnected. It will call disconnect().
                mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, mHwAddr);
            }
            updateAgent();
            // set our score lower than any network could go
            // so we get dropped.  TODO - just unregister the factory
            // when link goes down.
            mFactory.setScoreFilter(up ? NETWORK_SCORE : -1);
        }
    }

    private class InterfaceObserver extends BaseNetworkObserver {
        @Override
        public void interfaceLinkStateChanged(String iface, boolean up) {
            updateInterfaceState(iface, up);
        }

        @Override
        public void interfaceAdded(String iface) {
            maybeTrackInterface(iface);
        }

        @Override
        public void interfaceRemoved(String iface) {
            stopTrackingInterface(iface);
        }
    }

    private boolean maybeTrackInterface(String iface) {
        // If we don't already have an interface, and if this interface matches
        // our regex, start tracking it.
        if (!iface.matches(mIfaceMatch) || isTrackingInterface())
            return false;

        Log.d(TAG, "Started tracking interface " + iface);
        try{
            InterfaceConfiguration config = mNMService.getInterfaceConfig(iface);
            setInterfaceInfoLocked(iface, config.getHardwareAddress());
        } catch (RemoteException e){
            Log.e(TAG, "Error upping interface " + mIface + ": " + e);
            return false;
        }
        return true;
    }

    private void stopTrackingInterface(String iface) {
        if (!iface.equals(mIface))
            return;

        Log.d(TAG, "Stopped tracking interface " + iface);
        // TODO: Unify this codepath with stop().
        synchronized (this) {
            setInterfaceInfoLocked("", null);
            mNetworkInfo.setExtraInfo(null);
            mLinkUp = false;
            mNetworkInfo.setIsAvailable(false);
            mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
            updateAgent();
            mNetworkAgent = null;
            mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_PPPOE, 0, NETWORK_TYPE, "");
            mLinkProperties = new LinkProperties();
        }
    }

    public void updateAgent() {
        synchronized (PppoeNetworkFactory.this) {
            if (mNetworkAgent == null) return;
            if (DBG) {
                Log.i(TAG, "Updating mNetworkAgent with: " +
                      mNetworkCapabilities + ", " +
                      mNetworkInfo + ", " +
                      mLinkProperties);
            }
            mNetworkAgent.sendNetworkCapabilities(mNetworkCapabilities);
            mNetworkAgent.sendNetworkInfo(mNetworkInfo);
            mNetworkAgent.sendLinkProperties(mLinkProperties);
            // never set the network score below 0.
            // mNetworkAgent.sendNetworkScore(mLinkUp? NETWORK_SCORE : 0);
        }
    }

    /* Called by the NetworkFactory on the handler thread. */
    public void onRequestNetwork() {
        if (DBG) Log.i(TAG, "thread(" + mIface + "): mNetworkInfo=" + mNetworkInfo);

        synchronized(PppoeNetworkFactory.this) {
            if (mNetworkAgent != null) {
                Log.e(TAG, "Already have a NetworkAgent - aborting new request");
                return;
            }
            // mLinkProperties = linkProperties;
            mLinkUp = true;
            mFactory.setScoreFilter(mLinkUp ? NETWORK_SCORE : -1);
            mNetworkInfo.setDetailedState(DetailedState.CONNECTING, null, null);

            NetworkMisc networkMisc = new NetworkMisc();
            networkMisc.allowBypass = false;

            // Create our NetworkAgent.
            mNetworkAgent = new NetworkAgent(mFactory.getLooper(), mContext,
                    NETWORK_TYPE, mNetworkInfo, mNetworkCapabilities, mLinkProperties,
                    NETWORK_SCORE, networkMisc) {
                public void unwanted() {
                    // We are user controlled, not driven by NetworkRequest.
                };
            };
            mNetworkInfo.setIsAvailable(true);
            updateState(DetailedState.CONNECTED, "agentConnect");
        }
    }

    /**
     * Update current state, dispaching event to listeners.
     */
    private void updateState(DetailedState detailedState, String reason) {
        if (DBG) Log.d(TAG, "setting state=" + detailedState + ", reason=" + reason);
        mNetworkInfo.setDetailedState(detailedState, reason, null);
        if (mNetworkAgent != null) {
            mNetworkAgent.sendNetworkInfo(mNetworkInfo);
        }
    }

    /**
     * Begin monitoring connectivity
     */
    public synchronized void start(Context context, Handler target) {
        mLinkProperties = Utils.getPppoeLinkProperties();
        // The services we use.
        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNMService = INetworkManagementService.Stub.asInterface(b);

        // Create and register our NetworkFactory.
        mFactory = new LocalNetworkFactory(NETWORK_TYPE, context, target.getLooper());
        mFactory.setCapabilityFilter(mNetworkCapabilities);
        mFactory.setScoreFilter(-1); // this set high when we have an iface
        mFactory.register();

        mContext = context;

        // Start tracking interface change events.
        // mInterfaceObserver = new InterfaceObserver();
        // try {
        //     mNMService.registerObserver(mInterfaceObserver);
        // } catch (RemoteException e) {
        //     Log.e(TAG, "Could not register InterfaceObserver " + e);
        // }

        // If an Ethernet interface is already connected, start tracking that.
        // Otherwise, the first Ethernet interface to appear will be tracked.
        // try {
        //     final String[] ifaces = mNMService.listInterfaces();
        //     for (String iface : ifaces) {
        //         synchronized(this) {
        //             if (maybeTrackInterface(iface)) {
        //                 // We have our interface. Track it.
        //                 // Note: if the interface already has link (e.g., if we
        //                 // crashed and got restarted while it was running),
        //                 // we need to fake a link up notification so we start
        //                 // configuring it. Since we're already holding the lock,
        //                 // any real link up/down notification will only arrive
        //                 // after we've done this.
        //                 if (mNMService.getInterfaceConfig(iface).hasFlag("running")) {
        //                     updateInterfaceState(iface, true);
        //                 }
        //                 break;
        //             }
        //         }
        //     }
        // } catch (RemoteException e) {
        //     Log.e(TAG, "Could not get list of interfaces " + e);
        // }
        onRequestNetwork();
    }

    private synchronized void agentDisconnect() {
        mNetworkInfo.setIsAvailable(false);
        mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
        if(mNetworkAgent!=null)
            mNetworkAgent.sendNetworkInfo(mNetworkInfo);
    }

    public void stop() {
        Log.d(TAG, "stop");
        // ConnectivityService will only forget our NetworkAgent if we send it a NetworkInfo object
        // with a state of DISCONNECTED or SUSPENDED. So we can't simply clear our NetworkInfo here:
        // that sets the state to IDLE, and ConnectivityService will still think we're connected.
        //
        // TODO: stop using explicit comparisons to DISCONNECTED / SUSPENDED in ConnectivityService,
        // and instead use isConnectedOrConnecting().
        mLinkUp = false;
        mLinkProperties.clear();
        // agentDisconnect();
        mNetworkInfo.setIsAvailable(false);
        mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
        updateAgent();
        mNetworkAgent = null;
        // setInterfaceInfoLocked("", null);
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_PPPOE, 0, NETWORK_TYPE, "");
        mFactory.setScoreFilter(mLinkUp ? NETWORK_SCORE : -1);
        mFactory.unregister();
    }

    private void initNetworkCapabilities() {
        mNetworkCapabilities = new NetworkCapabilities();
        mNetworkCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET);
        // mNetworkCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_VPN);
        // mNetworkCapabilities.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
        // mNetworkCapabilities.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        mNetworkCapabilities.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public synchronized boolean isTrackingInterface() {
        return !TextUtils.isEmpty(mIface);
    }

    /**
     * Set interface information and notify listeners if availability is changed.
     * This should be called with the lock held.
     */
    private void setInterfaceInfoLocked(String iface, String hwAddr) {
        boolean oldAvailable = isTrackingInterface();
        mIface = iface;
        mHwAddr = hwAddr;
        boolean available = isTrackingInterface();


    }

    synchronized void dump(FileDescriptor fd, IndentingPrintWriter pw, String[] args) {
        if (isTrackingInterface()) {
            pw.println("Tracking interface: " + mIface);
            pw.increaseIndent();
            pw.println("MAC address: " + mHwAddr);
            pw.println("Link state: " + (mLinkUp ? "up" : "down"));
            pw.decreaseIndent();
        } else {
            pw.println("Not tracking any interface");
        }

        pw.println();
        pw.println("NetworkInfo: " + mNetworkInfo);
        pw.println("LinkProperties: " + mLinkProperties);
        pw.println("NetworkAgent: " + mNetworkAgent);
    }

    private static void loge(String s){
        Log.e(TAG, s);
    }
}
