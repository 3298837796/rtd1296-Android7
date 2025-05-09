/*
 * Copyright (C) 2006-2008 The Android Open Source Project
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

package com.android.server.am;

import com.android.internal.telephony.TelephonyIntents;
import com.google.android.collect.Lists;
import com.google.android.collect.Maps;
import com.android.internal.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.AssistUtils;
import com.android.internal.app.DumpHeapActivity;
import com.android.internal.app.IAppOpsCallback;
import com.android.internal.app.IAppOpsService;
import com.android.internal.app.IVoiceInteractor;
import com.android.internal.app.ProcessMap;
import com.android.internal.app.SystemUserHomeActivity;
import com.android.internal.app.procstats.ProcessStats;
import com.android.internal.os.BackgroundThread;
import com.android.internal.os.BatteryStatsImpl;
import com.android.internal.os.IResultReceiver;
import com.android.internal.os.ProcessCpuTracker;
import com.android.internal.os.TransferPipe;
import com.android.internal.os.Zygote;
import com.android.internal.os.InstallerConnection.InstallerException;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.FastPrintWriter;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.MemInfoReader;
import com.android.internal.util.Preconditions;
import com.android.server.AppOpsService;
import com.android.server.AttributeCache;
import com.android.server.DeviceIdleController;
import com.android.server.IntentResolver;
import com.android.server.LocalServices;
import com.android.server.LockGuard;
import com.android.server.ServiceThread;
import com.android.server.SystemService;
import com.android.server.SystemServiceManager;
import com.android.server.Watchdog;
import com.android.server.am.ActivityStack.ActivityState;
import com.android.server.firewall.IntentFirewall;
import com.android.server.pm.Installer;
import com.android.server.statusbar.StatusBarManagerInternal;
import com.android.server.vr.VrManagerInternal;
import com.android.server.wm.WindowManagerService;

// [RTK Begin]
import com.realtek.server.TvManagerService;
import com.realtek.server.ToGoService;
// [RTK  End ]

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import android.Manifest;
import android.Manifest.permission;
import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityManager.StackId;
import android.app.ActivityManager.StackInfo;
import android.app.ActivityManager.TaskThumbnailInfo;
import android.app.ActivityManagerInternal;
import android.app.ActivityManagerInternal.SleepToken;
import android.app.ActivityManagerNative;
import android.app.ActivityOptions;
import android.app.ActivityThread;
import android.app.AlertDialog;
import android.app.AppGlobals;
import android.app.AppOpsManager;
import android.app.ApplicationErrorReport;
import android.app.ApplicationThreadNative;
import android.app.BroadcastOptions;
import android.app.Dialog;
import android.app.IActivityContainer;
import android.app.IActivityContainerCallback;
import android.app.IActivityController;
import android.app.IAppTask;
import android.app.IApplicationThread;
import android.app.IInstrumentationWatcher;
import android.app.INotificationManager;
import android.app.IProcessObserver;
import android.app.IServiceConnection;
import android.app.IStopUserCallback;
import android.app.ITaskStackListener;
import android.app.IUiAutomationConnection;
import android.app.IUidObserver;
import android.app.IUserSwitchObserver;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProfilerInfo;
import android.app.admin.DevicePolicyManager;
import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.app.backup.IBackupManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManagerInternal;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IContentProvider;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageManager;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManagerInternal;
import android.content.pm.ParceledListSlice;
import android.content.pm.PathPermission;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.UserInfo;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.location.LocationManager;
import android.net.Proxy;
import android.net.ProxyInfo;
import android.net.Uri;
import android.os.BatteryStats;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.DropBoxManager;
import android.os.Environment;
import android.os.FactoryTest;
import android.os.FileObserver;
import android.os.FileUtils;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPermissionController;
import android.os.IProcessInfoService;
import android.os.IProgressListener;
import android.os.LocaleList;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.PowerManagerInternal;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.Trace;
import android.os.TransactionTooLargeException;
import android.os.UpdateLock;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.provider.Downloads;
import android.os.storage.IMountService;
import android.os.storage.MountServiceInternal;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.service.voice.IVoiceInteractionSession;
import android.service.voice.VoiceInteractionManagerInternal;
import android.service.voice.VoiceInteractionSession;
import android.telecom.TelecomManager;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.SuggestionSpan;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.DebugUtils;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.util.Pair;
import android.util.PrintWriterPrinter;
import android.util.Slog;
import android.util.SparseArray;
import android.util.TimeUtils;
import android.util.Xml;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dalvik.system.VMRuntime;

import libcore.io.IoUtils;
import libcore.util.EmptyArray;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.Manifest.permission.MANAGE_ACTIVITY_STACKS;
import static android.Manifest.permission.START_TASKS_FROM_RECENTS;
import static android.app.ActivityManager.DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT;
import static android.app.ActivityManager.RESIZE_MODE_PRESERVE_WINDOW;
import static android.app.ActivityManager.StackId.DOCKED_STACK_ID;
import static android.app.ActivityManager.StackId.FREEFORM_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.FULLSCREEN_WORKSPACE_STACK_ID;
import static android.app.ActivityManager.StackId.HOME_STACK_ID;
import static android.app.ActivityManager.StackId.INVALID_STACK_ID;
import static android.app.ActivityManager.StackId.PINNED_STACK_ID;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;
import static android.content.pm.PackageManager.FEATURE_LEANBACK_ONLY;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.content.pm.PackageManager.GET_PROVIDERS;
import static android.content.pm.PackageManager.MATCH_DEBUG_TRIAGED_MISSING;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE;
import static android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
import static android.content.pm.PackageManager.MATCH_SYSTEM_ONLY;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
import static android.os.Process.PROC_CHAR;
import static android.os.Process.PROC_OUT_LONG;
import static android.os.Process.PROC_PARENS;
import static android.os.Process.PROC_SPACE_TERM;
import static android.provider.Settings.Global.ALWAYS_FINISH_ACTIVITIES;
import static android.provider.Settings.Global.DEBUG_APP;
import static android.provider.Settings.Global.DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT;
import static android.provider.Settings.Global.DEVELOPMENT_FORCE_RESIZABLE_ACTIVITIES;
import static android.provider.Settings.Global.DEVELOPMENT_FORCE_RTL;
import static android.provider.Settings.Global.LENIENT_BACKGROUND_CHECK;
import static android.provider.Settings.Global.WAIT_FOR_DEBUGGER;
import static android.provider.Settings.System.FONT_SCALE;
import static com.android.internal.util.XmlUtils.readBooleanAttribute;
import static com.android.internal.util.XmlUtils.readIntAttribute;
import static com.android.internal.util.XmlUtils.readLongAttribute;
import static com.android.internal.util.XmlUtils.writeBooleanAttribute;
import static com.android.internal.util.XmlUtils.writeIntAttribute;
import static com.android.internal.util.XmlUtils.writeLongAttribute;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_ALL;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_ANR;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BACKUP;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BROADCAST;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BROADCAST_BACKGROUND;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_BROADCAST_LIGHT;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_CLEANUP;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_CONFIGURATION;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_FOCUS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_IMMERSIVE;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_LOCKSCREEN;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_LOCKTASK;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_LRU;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_MU;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_OOM_ADJ;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PERMISSIONS_REVIEW;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_POWER;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_POWER_QUICK;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PROCESSES;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PROCESS_OBSERVERS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PROVIDER;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_PSS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_RECENTS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_SERVICE;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_STACK;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_SWITCH;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_TASKS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_UID_OBSERVERS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_URI_PERMISSION;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_USAGE_STATS;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_VISIBILITY;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_VISIBLE_BEHIND;
import static com.android.server.am.ActivityManagerDebugConfig.DEBUG_WHITELISTS;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_BACKUP;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_BROADCAST;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_CLEANUP;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_CONFIGURATION;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_FOCUS;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_IMMERSIVE;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_LOCKSCREEN;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_LOCKTASK;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_LRU;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_MU;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_OOM_ADJ;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_POWER;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_PROCESSES;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_PROCESS_OBSERVERS;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_PROVIDER;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_PSS;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_RECENTS;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_SERVICE;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_STACK;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_SWITCH;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_UID_OBSERVERS;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_URI_PERMISSION;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_VISIBILITY;
import static com.android.server.am.ActivityManagerDebugConfig.POSTFIX_VISIBLE_BEHIND;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_AM;
import static com.android.server.am.ActivityManagerDebugConfig.TAG_WITH_CLASS_NAME;
import static com.android.server.am.ActivityRecord.RECENTS_ACTIVITY_TYPE;
import static com.android.server.am.ActivityStackSupervisor.ActivityContainer.FORCE_NEW_TASK_FLAGS;
import static com.android.server.am.ActivityStackSupervisor.DEFER_RESUME;
import static com.android.server.am.ActivityStackSupervisor.FORCE_FOCUS;
import static com.android.server.am.ActivityStackSupervisor.ON_TOP;
import static com.android.server.am.ActivityStackSupervisor.PRESERVE_WINDOWS;
import static com.android.server.am.ActivityStackSupervisor.RESTORE_FROM_RECENTS;
import static com.android.server.am.TaskRecord.INVALID_TASK_ID;
import static com.android.server.am.TaskRecord.LOCK_TASK_AUTH_DONT_LOCK;
import static com.android.server.am.TaskRecord.LOCK_TASK_AUTH_LAUNCHABLE_PRIV;
import static com.android.server.am.TaskRecord.LOCK_TASK_AUTH_PINNABLE;
import static com.android.server.wm.AppTransition.TRANSIT_ACTIVITY_OPEN;
import static com.android.server.wm.AppTransition.TRANSIT_ACTIVITY_RELAUNCH;
import static com.android.server.wm.AppTransition.TRANSIT_TASK_IN_PLACE;
import static com.android.server.wm.AppTransition.TRANSIT_TASK_OPEN;
import static com.android.server.wm.AppTransition.TRANSIT_TASK_TO_FRONT;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

public final class ActivityManagerService extends ActivityManagerNative
        implements Watchdog.Monitor, BatteryStatsImpl.BatteryCallback {

    private static final String TAG = TAG_WITH_CLASS_NAME ? "ActivityManagerService" : TAG_AM;
    private static final String TAG_BACKUP = TAG + POSTFIX_BACKUP;
    private static final String TAG_BROADCAST = TAG + POSTFIX_BROADCAST;
    private static final String TAG_CLEANUP = TAG + POSTFIX_CLEANUP;
    private static final String TAG_CONFIGURATION = TAG + POSTFIX_CONFIGURATION;
    private static final String TAG_FOCUS = TAG + POSTFIX_FOCUS;
    private static final String TAG_IMMERSIVE = TAG + POSTFIX_IMMERSIVE;
    private static final String TAG_LOCKSCREEN = TAG + POSTFIX_LOCKSCREEN;
    private static final String TAG_LOCKTASK = TAG + POSTFIX_LOCKTASK;
    private static final String TAG_LRU = TAG + POSTFIX_LRU;
    private static final String TAG_MU = TAG + POSTFIX_MU;
    private static final String TAG_OOM_ADJ = TAG + POSTFIX_OOM_ADJ;
    private static final String TAG_POWER = TAG + POSTFIX_POWER;
    private static final String TAG_PROCESS_OBSERVERS = TAG + POSTFIX_PROCESS_OBSERVERS;
    private static final String TAG_PROCESSES = TAG + POSTFIX_PROCESSES;
    private static final String TAG_PROVIDER = TAG + POSTFIX_PROVIDER;
    private static final String TAG_PSS = TAG + POSTFIX_PSS;
    private static final String TAG_RECENTS = TAG + POSTFIX_RECENTS;
    private static final String TAG_SERVICE = TAG + POSTFIX_SERVICE;
    private static final String TAG_STACK = TAG + POSTFIX_STACK;
    private static final String TAG_SWITCH = TAG + POSTFIX_SWITCH;
    private static final String TAG_UID_OBSERVERS = TAG + POSTFIX_UID_OBSERVERS;
    private static final String TAG_URI_PERMISSION = TAG + POSTFIX_URI_PERMISSION;
    private static final String TAG_VISIBILITY = TAG + POSTFIX_VISIBILITY;
    private static final String TAG_VISIBLE_BEHIND = TAG + POSTFIX_VISIBLE_BEHIND;

    // Mock "pretend we're idle now" broadcast action to the job scheduler; declared
    // here so that while the job scheduler can depend on AMS, the other way around
    // need not be the case.
    public static final String ACTION_TRIGGER_IDLE = "com.android.server.ACTION_TRIGGER_IDLE";

    /** Control over CPU and battery monitoring */
    // write battery stats every 30 minutes.
    static final long BATTERY_STATS_TIME = 30 * 60 * 1000;
    static final boolean MONITOR_CPU_USAGE = true;
    // don't sample cpu less than every 5 seconds.
    static final long MONITOR_CPU_MIN_TIME = 5 * 1000;
    // wait possibly forever for next cpu sample.
    static final long MONITOR_CPU_MAX_TIME = 0x0fffffff;
    static final boolean MONITOR_THREAD_CPU_USAGE = false;

    // The flags that are set for all calls we make to the package manager.
    static final int STOCK_PM_FLAGS = PackageManager.GET_SHARED_LIBRARY_FILES;

    static final String SYSTEM_DEBUGGABLE = "ro.debuggable";

    static final boolean IS_USER_BUILD = "user".equals(Build.TYPE);

    // Amount of time after a call to stopAppSwitches() during which we will
    // prevent further untrusted switches from happening.
    static final long APP_SWITCH_DELAY_TIME = 5*1000;

    // How long we wait for a launched process to attach to the activity manager
    // before we decide it's never going to come up for real.
    static final int PROC_START_TIMEOUT = 10*1000;
    // How long we wait for an attached process to publish its content providers
    // before we decide it must be hung.
    static final int CONTENT_PROVIDER_PUBLISH_TIMEOUT = 10*1000;

    // How long we will retain processes hosting content providers in the "last activity"
    // state before allowing them to drop down to the regular cached LRU list.  This is
    // to avoid thrashing of provider processes under low memory situations.
    static final int CONTENT_PROVIDER_RETAIN_TIME = 20*1000;

    // How long we wait for a launched process to attach to the activity manager
    // before we decide it's never going to come up for real, when the process was
    // started with a wrapper for instrumentation (such as Valgrind) because it
    // could take much longer than usual.
    static final int PROC_START_TIMEOUT_WITH_WRAPPER = 1200*1000;

    // How long to wait after going idle before forcing apps to GC.
    static final int GC_TIMEOUT = 5*1000;

    // The minimum amount of time between successive GC requests for a process.
    static final int GC_MIN_INTERVAL = 60*1000;

    // The minimum amount of time between successive PSS requests for a process.
    static final int FULL_PSS_MIN_INTERVAL = 10*60*1000;

    // The minimum amount of time between successive PSS requests for a process
    // when the request is due to the memory state being lowered.
    static final int FULL_PSS_LOWERED_INTERVAL = 2*60*1000;

    // The rate at which we check for apps using excessive power -- 15 mins.
    static final int POWER_CHECK_DELAY = (DEBUG_POWER_QUICK ? 2 : 15) * 60*1000;

    // The minimum sample duration we will allow before deciding we have
    // enough data on wake locks to start killing things.
    static final int WAKE_LOCK_MIN_CHECK_DURATION = (DEBUG_POWER_QUICK ? 1 : 5) * 60*1000;

    // The minimum sample duration we will allow before deciding we have
    // enough data on CPU usage to start killing things.
    static final int CPU_MIN_CHECK_DURATION = (DEBUG_POWER_QUICK ? 1 : 5) * 60*1000;

    // How long we allow a receiver to run before giving up on it.
    static final int BROADCAST_FG_TIMEOUT = 10*1000;
    static final int BROADCAST_BG_TIMEOUT = 60*1000;

    // How long we wait until we timeout on key dispatching.
    static final int KEY_DISPATCHING_TIMEOUT = 5*1000;

    // How long we wait until we timeout on key dispatching during instrumentation.
    static final int INSTRUMENTATION_KEY_DISPATCHING_TIMEOUT = 60*1000;

    // This is the amount of time an app needs to be running a foreground service before
    // we will consider it to be doing interaction for usage stats.
    static final int SERVICE_USAGE_INTERACTION_TIME = 30*60*1000;

    // Maximum amount of time we will allow to elapse before re-reporting usage stats
    // interaction with foreground processes.
    static final long USAGE_STATS_INTERACTION_INTERVAL = 24*60*60*1000L;

    // This is the amount of time we allow an app to settle after it goes into the background,
    // before we start restricting what it can do.
    static final int BACKGROUND_SETTLE_TIME = 1*60*1000;

    // How long to wait in getAssistContextExtras for the activity and foreground services
    // to respond with the result.
    static final int PENDING_ASSIST_EXTRAS_TIMEOUT = 500;

    // How long top wait when going through the modern assist (which doesn't need to block
    // on getting this result before starting to launch its UI).
    static final int PENDING_ASSIST_EXTRAS_LONG_TIMEOUT = 2000;

    // Maximum number of persisted Uri grants a package is allowed
    static final int MAX_PERSISTED_URI_GRANTS = 128;

    static final int MY_PID = Process.myPid();

    static final String[] EMPTY_STRING_ARRAY = new String[0];

    // How many bytes to write into the dropbox log before truncating
    static final int DROPBOX_MAX_SIZE = 192 * 1024;
    // Assumes logcat entries average around 100 bytes; that's not perfect stack traces count
    // as one line, but close enough for now.
    static final int RESERVED_BYTES_PER_LOGCAT_LINE = 100;

    // Access modes for handleIncomingUser.
    static final int ALLOW_NON_FULL = 0;
    static final int ALLOW_NON_FULL_IN_PROFILE = 1;
    static final int ALLOW_FULL_ONLY = 2;

    // Delay in notifying task stack change listeners (in millis)
    static final int NOTIFY_TASK_STACK_CHANGE_LISTENERS_DELAY = 100;

    // Necessary ApplicationInfo flags to mark an app as persistent
    private static final int PERSISTENT_MASK =
            ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_PERSISTENT;

    // Intent sent when remote bugreport collection has been completed
    private static final String INTENT_REMOTE_BUGREPORT_FINISHED =
            "android.intent.action.REMOTE_BUGREPORT_FINISHED";

    // Delay to disable app launch boost
    static final int APP_BOOST_MESSAGE_DELAY = 3000;
    // Lower delay than APP_BOOST_MESSAGE_DELAY to disable the boost
    static final int APP_BOOST_TIMEOUT = 2500;

    // Used to indicate that a task is removed it should also be removed from recents.
    private static final boolean REMOVE_FROM_RECENTS = true;
    // Used to indicate that an app transition should be animated.
    static final boolean ANIMATE = true;

    // Determines whether to take full screen screenshots
    static final boolean TAKE_FULLSCREEN_SCREENSHOTS = true;
    public static final float FULLSCREEN_SCREENSHOT_SCALE = 0.6f;

    private static native int nativeMigrateToBoost();
    private static native int nativeMigrateFromBoost();
    private boolean mIsBoosted = false;
    private long mBoostStartTime = 0;

    /** All system services */
    SystemServiceManager mSystemServiceManager;

    private Installer mInstaller;

    /** Run all ActivityStacks through this */
    final ActivityStackSupervisor mStackSupervisor;

    final ActivityStarter mActivityStarter;

    /** Task stack change listeners. */
    private final RemoteCallbackList<ITaskStackListener> mTaskStackListeners =
            new RemoteCallbackList<ITaskStackListener>();

    final InstrumentationReporter mInstrumentationReporter = new InstrumentationReporter();

    public IntentFirewall mIntentFirewall;

    // Whether we should show our dialogs (ANR, crash, etc) or just perform their
    // default actuion automatically.  Important for devices without direct input
    // devices.
    private boolean mShowDialogs = true;
    private boolean mInVrMode = false;

    // Whether we should use SCHED_FIFO for UI and RenderThreads.
    private boolean mUseFifoUiScheduling = false;

    BroadcastQueue mFgBroadcastQueue;
    BroadcastQueue mBgBroadcastQueue;
    // Convenient for easy iteration over the queues. Foreground is first
    // so that dispatch of foreground broadcasts gets precedence.
    final BroadcastQueue[] mBroadcastQueues = new BroadcastQueue[2];

    BroadcastStats mLastBroadcastStats;
    BroadcastStats mCurBroadcastStats;

    BroadcastQueue broadcastQueueForIntent(Intent intent) {
        final boolean isFg = (intent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND) != 0;
        if (DEBUG_BROADCAST_BACKGROUND) Slog.i(TAG_BROADCAST,
                "Broadcast intent " + intent + " on "
                + (isFg ? "foreground" : "background") + " queue");
        return (isFg) ? mFgBroadcastQueue : mBgBroadcastQueue;
    }

    /**
     * Activity we have told the window manager to have key focus.
     */
    ActivityRecord mFocusedActivity = null;

    /**
     * User id of the last activity mFocusedActivity was set to.
     */
    private int mLastFocusedUserId;

    /**
     * If non-null, we are tracking the time the user spends in the currently focused app.
     */
    private AppTimeTracker mCurAppTimeTracker;

    /**
     * List of intents that were used to start the most recent tasks.
     */
    final RecentTasks mRecentTasks;

    /**
     * For addAppTask: cached of the last activity component that was added.
     */
    ComponentName mLastAddedTaskComponent;

    /**
     * For addAppTask: cached of the last activity uid that was added.
     */
    int mLastAddedTaskUid;

    /**
     * For addAppTask: cached of the last ActivityInfo that was added.
     */
    ActivityInfo mLastAddedTaskActivity;

    /**
     * List of packages whitelisted by DevicePolicyManager for locktask. Indexed by userId.
     */
    SparseArray<String[]> mLockTaskPackages = new SparseArray<>();

    /**
     * The package name of the DeviceOwner. This package is not permitted to have its data cleared.
     */
    String mDeviceOwnerName;

    final UserController mUserController;

    final AppErrors mAppErrors;

    boolean mDoingSetFocusedActivity;

    public boolean canShowErrorDialogs() {
        return mShowDialogs && !mSleeping && !mShuttingDown
                && mLockScreenShown != LOCK_SCREEN_SHOWN;
    }

    private static final class PriorityState {
        // Acts as counter for number of synchronized region that needs to acquire 'this' as a lock
        // the current thread is currently in. When it drops down to zero, we will no longer boost
        // the thread's priority.
        private int regionCounter = 0;

        // The thread's previous priority before boosting.
        private int prevPriority = Integer.MIN_VALUE;
    }

    static ThreadLocal<PriorityState> sThreadPriorityState = new ThreadLocal<PriorityState>() {
        @Override protected PriorityState initialValue() {
            return new PriorityState();
        }
    };

    static void boostPriorityForLockedSection() {
        int tid = Process.myTid();
        int prevPriority = Process.getThreadPriority(tid);
        PriorityState state = sThreadPriorityState.get();
        if (state.regionCounter == 0 && prevPriority > -2) {
            state.prevPriority = prevPriority;
            Process.setThreadPriority(tid, -2);
        }
        state.regionCounter++;
    }

    static void resetPriorityAfterLockedSection() {
        PriorityState state = sThreadPriorityState.get();
        state.regionCounter--;
        if (state.regionCounter == 0 && state.prevPriority > -2) {
            Process.setThreadPriority(Process.myTid(), state.prevPriority);
        }
    }

    public class PendingAssistExtras extends Binder implements Runnable {
        public final ActivityRecord activity;
        public final Bundle extras;
        public final Intent intent;
        public final String hint;
        public final IResultReceiver receiver;
        public final int userHandle;
        public boolean haveResult = false;
        public Bundle result = null;
        public AssistStructure structure = null;
        public AssistContent content = null;
        public Bundle receiverExtras;

        public PendingAssistExtras(ActivityRecord _activity, Bundle _extras, Intent _intent,
                String _hint, IResultReceiver _receiver, Bundle _receiverExtras, int _userHandle) {
            activity = _activity;
            extras = _extras;
            intent = _intent;
            hint = _hint;
            receiver = _receiver;
            receiverExtras = _receiverExtras;
            userHandle = _userHandle;
        }
        @Override
        public void run() {
            Slog.w(TAG, "getAssistContextExtras failed: timeout retrieving from " + activity);
            synchronized (this) {
                haveResult = true;
                notifyAll();
            }
            pendingAssistExtrasTimedOut(this);
        }
    }

    final ArrayList<PendingAssistExtras> mPendingAssistExtras
            = new ArrayList<PendingAssistExtras>();

    /**
     * Process management.
     */
    final ProcessList mProcessList = new ProcessList();

    /**
     * All of the applications we currently have running organized by name.
     * The keys are strings of the application package name (as
     * returned by the package manager), and the keys are ApplicationRecord
     * objects.
     */
    final ProcessMap<ProcessRecord> mProcessNames = new ProcessMap<ProcessRecord>();

    /**
     * Tracking long-term execution of processes to look for abuse and other
     * bad app behavior.
     */
    final ProcessStatsService mProcessStats;

    /**
     * The currently running isolated processes.
     */
    final SparseArray<ProcessRecord> mIsolatedProcesses = new SparseArray<ProcessRecord>();

    /**
     * Counter for assigning isolated process uids, to avoid frequently reusing the
     * same ones.
     */
    int mNextIsolatedProcessUid = 0;

    /**
     * The currently running heavy-weight process, if any.
     */
    ProcessRecord mHeavyWeightProcess = null;

    /**
     * All of the processes we currently have running organized by pid.
     * The keys are the pid running the application.
     *
     * <p>NOTE: This object is protected by its own lock, NOT the global
     * activity manager lock!
     */
    final SparseArray<ProcessRecord> mPidsSelfLocked = new SparseArray<ProcessRecord>();

    /**
     * All of the processes that have been forced to be foreground.  The key
     * is the pid of the caller who requested it (we hold a death
     * link on it).
     */
    abstract class ForegroundToken implements IBinder.DeathRecipient {
        int pid;
        IBinder token;
    }
    final SparseArray<ForegroundToken> mForegroundProcesses = new SparseArray<ForegroundToken>();

    /**
     * List of records for processes that someone had tried to start before the
     * system was ready.  We don't start them at that point, but ensure they
     * are started by the time booting is complete.
     */
    final ArrayList<ProcessRecord> mProcessesOnHold = new ArrayList<ProcessRecord>();

    /**
     * List of persistent applications that are in the process
     * of being started.
     */
    final ArrayList<ProcessRecord> mPersistentStartingProcesses = new ArrayList<ProcessRecord>();

    /**
     * Processes that are being forcibly torn down.
     */
    final ArrayList<ProcessRecord> mRemovedProcesses = new ArrayList<ProcessRecord>();

    /**
     * List of running applications, sorted by recent usage.
     * The first entry in the list is the least recently used.
     */
    final ArrayList<ProcessRecord> mLruProcesses = new ArrayList<ProcessRecord>();

    /**
     * Where in mLruProcesses that the processes hosting activities start.
     */
    int mLruProcessActivityStart = 0;

    /**
     * Where in mLruProcesses that the processes hosting services start.
     * This is after (lower index) than mLruProcessesActivityStart.
     */
    int mLruProcessServiceStart = 0;

    /**
     * List of processes that should gc as soon as things are idle.
     */
    final ArrayList<ProcessRecord> mProcessesToGc = new ArrayList<ProcessRecord>();

    /**
     * Processes we want to collect PSS data from.
     */
    final ArrayList<ProcessRecord> mPendingPssProcesses = new ArrayList<ProcessRecord>();

    private boolean mBinderTransactionTrackingEnabled = false;

    /**
     * Last time we requested PSS data of all processes.
     */
    long mLastFullPssTime = SystemClock.uptimeMillis();

    /**
     * If set, the next time we collect PSS data we should do a full collection
     * with data from native processes and the kernel.
     */
    boolean mFullPssPending = false;

    /**
     * This is the process holding what we currently consider to be
     * the "home" activity.
     */
    ProcessRecord mHomeProcess;

    /**
     * This is the process holding the activity the user last visited that
     * is in a different process from the one they are currently in.
     */
    ProcessRecord mPreviousProcess;

    /**
     * The time at which the previous process was last visible.
     */
    long mPreviousProcessVisibleTime;

    /**
     * Track all uids that have actively running processes.
     */
    final SparseArray<UidRecord> mActiveUids = new SparseArray<>();

    /**
     * This is for verifying the UID report flow.
     */
    static final boolean VALIDATE_UID_STATES = true;
    final SparseArray<UidRecord> mValidateUids = new SparseArray<>();

    /**
     * Packages that the user has asked to have run in screen size
     * compatibility mode instead of filling the screen.
     */
    final CompatModePackages mCompatModePackages;

    /**
     * Set of IntentSenderRecord objects that are currently active.
     */
    final HashMap<PendingIntentRecord.Key, WeakReference<PendingIntentRecord>> mIntentSenderRecords
            = new HashMap<PendingIntentRecord.Key, WeakReference<PendingIntentRecord>>();

    /**
     * Fingerprints (hashCode()) of stack traces that we've
     * already logged DropBox entries for.  Guarded by itself.  If
     * something (rogue user app) forces this over
     * MAX_DUP_SUPPRESSED_STACKS entries, the contents are cleared.
     */
    private final HashSet<Integer> mAlreadyLoggedViolatedStacks = new HashSet<Integer>();
    private static final int MAX_DUP_SUPPRESSED_STACKS = 5000;

    /**
     * Strict Mode background batched logging state.
     *
     * The string buffer is guarded by itself, and its lock is also
     * used to determine if another batched write is already
     * in-flight.
     */
    private final StringBuilder mStrictModeBuffer = new StringBuilder();

    /**
     * Keeps track of all IIntentReceivers that have been registered for broadcasts.
     * Hash keys are the receiver IBinder, hash value is a ReceiverList.
     */
    final HashMap<IBinder, ReceiverList> mRegisteredReceivers = new HashMap<>();

    /**
     * Resolver for broadcast intents to registered receivers.
     * Holds BroadcastFilter (subclass of IntentFilter).
     */
    final IntentResolver<BroadcastFilter, BroadcastFilter> mReceiverResolver
            = new IntentResolver<BroadcastFilter, BroadcastFilter>() {
        @Override
        protected boolean allowFilterResult(
                BroadcastFilter filter, List<BroadcastFilter> dest) {
            IBinder target = filter.receiverList.receiver.asBinder();
            for (int i = dest.size() - 1; i >= 0; i--) {
                if (dest.get(i).receiverList.receiver.asBinder() == target) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected BroadcastFilter newResult(BroadcastFilter filter, int match, int userId) {
            if (userId == UserHandle.USER_ALL || filter.owningUserId == UserHandle.USER_ALL
                    || userId == filter.owningUserId) {
                return super.newResult(filter, match, userId);
            }
            return null;
        }

        @Override
        protected BroadcastFilter[] newArray(int size) {
            return new BroadcastFilter[size];
        }

        @Override
        protected boolean isPackageForFilter(String packageName, BroadcastFilter filter) {
            return packageName.equals(filter.packageName);
        }
    };

    /**
     * State of all active sticky broadcasts per user.  Keys are the action of the
     * sticky Intent, values are an ArrayList of all broadcasted intents with
     * that action (which should usually be one).  The SparseArray is keyed
     * by the user ID the sticky is for, and can include UserHandle.USER_ALL
     * for stickies that are sent to all users.
     */
    final SparseArray<ArrayMap<String, ArrayList<Intent>>> mStickyBroadcasts =
            new SparseArray<ArrayMap<String, ArrayList<Intent>>>();

    final ActiveServices mServices;

    final static class Association {
        final int mSourceUid;
        final String mSourceProcess;
        final int mTargetUid;
        final ComponentName mTargetComponent;
        final String mTargetProcess;

        int mCount;
        long mTime;

        int mNesting;
        long mStartTime;

        // states of the source process when the bind occurred.
        int mLastState = ActivityManager.MAX_PROCESS_STATE + 1;
        long mLastStateUptime;
        long[] mStateTimes = new long[ActivityManager.MAX_PROCESS_STATE
                - ActivityManager.MIN_PROCESS_STATE+1];

        Association(int sourceUid, String sourceProcess, int targetUid,
                ComponentName targetComponent, String targetProcess) {
            mSourceUid = sourceUid;
            mSourceProcess = sourceProcess;
            mTargetUid = targetUid;
            mTargetComponent = targetComponent;
            mTargetProcess = targetProcess;
        }
    }

    /**
     * When service association tracking is enabled, this is all of the associations we
     * have seen.  Mapping is target uid -> target component -> source uid -> source process name
     * -> association data.
     */
    final SparseArray<ArrayMap<ComponentName, SparseArray<ArrayMap<String, Association>>>>
            mAssociations = new SparseArray<>();
    boolean mTrackingAssociations;

    /**
     * Backup/restore process management
     */
    String mBackupAppName = null;
    BackupRecord mBackupTarget = null;

    final ProviderMap mProviderMap;

    /**
     * List of content providers who have clients waiting for them.  The
     * application is currently being launched and the provider will be
     * removed from this list once it is published.
     */
    final ArrayList<ContentProviderRecord> mLaunchingProviders
            = new ArrayList<ContentProviderRecord>();

    /**
     * File storing persisted {@link #mGrantedUriPermissions}.
     */
    private final AtomicFile mGrantFile;

    /** XML constants used in {@link #mGrantFile} */
    private static final String TAG_URI_GRANTS = "uri-grants";
    private static final String TAG_URI_GRANT = "uri-grant";
    private static final String ATTR_USER_HANDLE = "userHandle";
    private static final String ATTR_SOURCE_USER_ID = "sourceUserId";
    private static final String ATTR_TARGET_USER_ID = "targetUserId";
    private static final String ATTR_SOURCE_PKG = "sourcePkg";
    private static final String ATTR_TARGET_PKG = "targetPkg";
    private static final String ATTR_URI = "uri";
    private static final String ATTR_MODE_FLAGS = "modeFlags";
    private static final String ATTR_CREATED_TIME = "createdTime";
    private static final String ATTR_PREFIX = "prefix";

    /**
     * Global set of specific {@link Uri} permissions that have been granted.
     * This optimized lookup structure maps from {@link UriPermission#targetUid}
     * to {@link UriPermission#uri} to {@link UriPermission}.
     */
    @GuardedBy("this")
    private final SparseArray<ArrayMap<GrantUri, UriPermission>>
            mGrantedUriPermissions = new SparseArray<ArrayMap<GrantUri, UriPermission>>();

    public static class GrantUri {
        public final int sourceUserId;
        public final Uri uri;
        public boolean prefix;

        public GrantUri(int sourceUserId, Uri uri, boolean prefix) {
            this.sourceUserId = sourceUserId;
            this.uri = uri;
            this.prefix = prefix;
        }

        @Override
        public int hashCode() {
            int hashCode = 1;
            hashCode = 31 * hashCode + sourceUserId;
            hashCode = 31 * hashCode + uri.hashCode();
            hashCode = 31 * hashCode + (prefix ? 1231 : 1237);
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof GrantUri) {
                GrantUri other = (GrantUri) o;
                return uri.equals(other.uri) && (sourceUserId == other.sourceUserId)
                        && prefix == other.prefix;
            }
            return false;
        }

        @Override
        public String toString() {
            String result = Integer.toString(sourceUserId) + " @ " + uri.toString();
            if (prefix) result += " [prefix]";
            return result;
        }

        public String toSafeString() {
            String result = Integer.toString(sourceUserId) + " @ " + uri.toSafeString();
            if (prefix) result += " [prefix]";
            return result;
        }

        public static GrantUri resolve(int defaultSourceUserHandle, Uri uri) {
            return new GrantUri(ContentProvider.getUserIdFromUri(uri, defaultSourceUserHandle),
                    ContentProvider.getUriWithoutUserId(uri), false);
        }
    }

    CoreSettingsObserver mCoreSettingsObserver;

    FontScaleSettingObserver mFontScaleSettingObserver;

    private final class FontScaleSettingObserver extends ContentObserver {
        private final Uri mFontScaleUri = Settings.System.getUriFor(FONT_SCALE);

        public FontScaleSettingObserver() {
            super(mHandler);
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(mFontScaleUri, false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri, @UserIdInt int userId) {
            if (mFontScaleUri.equals(uri)) {
                updateFontScaleIfNeeded(userId);
            }
        }
    }

    /**
     * Thread-local storage used to carry caller permissions over through
     * indirect content-provider access.
     */
    private class Identity {
        public final IBinder token;
        public final int pid;
        public final int uid;

        Identity(IBinder _token, int _pid, int _uid) {
            token = _token;
            pid = _pid;
            uid = _uid;
        }
    }

    private static final ThreadLocal<Identity> sCallerIdentity = new ThreadLocal<Identity>();

    /**
     * All information we have collected about the runtime performance of
     * any user id that can impact battery performance.
     */
    final BatteryStatsService mBatteryStatsService;

    /**
     * Information about component usage
     */
    UsageStatsManagerInternal mUsageStatsService;

    /**
     * Access to DeviceIdleController service.
     */
    DeviceIdleController.LocalService mLocalDeviceIdleController;

    /**
     * Information about and control over application operations
     */
    final AppOpsService mAppOpsService;

    /**
     * Current configuration information.  HistoryRecord objects are given
     * a reference to this object to indicate which configuration they are
     * currently running in, so this object must be kept immutable.
     */
    Configuration mConfiguration = new Configuration();

    /**
     * Current sequencing integer of the configuration, for skipping old
     * configurations.
     */
    int mConfigurationSeq = 0;

    boolean mSuppressResizeConfigChanges = false;

    /**
     * Hardware-reported OpenGLES version.
     */
    final int GL_ES_VERSION;

    /**
     * List of initialization arguments to pass to all processes when binding applications to them.
     * For example, references to the commonly used services.
     */
    HashMap<String, IBinder> mAppBindArgs;
    HashMap<String, IBinder> mIsolatedAppBindArgs;

    /**
     * Temporary to avoid allocations.  Protected by main lock.
     */
    final StringBuilder mStringBuilder = new StringBuilder(256);

    /**
     * Used to control how we initialize the service.
     */
    ComponentName mTopComponent;
    String mTopAction = Intent.ACTION_MAIN;
    String mTopData;

    volatile boolean mProcessesReady = false;
    volatile boolean mSystemReady = false;
    volatile boolean mOnBattery = false;
    volatile int mFactoryTest;

    @GuardedBy("this") boolean mBooting = false;
    @GuardedBy("this") boolean mCallFinishBooting = false;
    @GuardedBy("this") boolean mBootAnimationComplete = false;
    @GuardedBy("this") boolean mLaunchWarningShown = false;
    @GuardedBy("this") boolean mCheckedForSetup = false;

    Context mContext;

    /**
     * The time at which we will allow normal application switches again,
     * after a call to {@link #stopAppSwitches()}.
     */
    long mAppSwitchesAllowedTime;

    /**
     * This is set to true after the first switch after mAppSwitchesAllowedTime
     * is set; any switches after that will clear the time.
     */
    boolean mDidAppSwitch;

    /**
     * Last time (in realtime) at which we checked for power usage.
     */
    long mLastPowerCheckRealtime;

    /**
     * Last time (in uptime) at which we checked for power usage.
     */
    long mLastPowerCheckUptime;

    /**
     * Set while we are wanting to sleep, to prevent any
     * activities from being started/resumed.
     */
    private boolean mSleeping = false;

    /**
     * The process state used for processes that are running the top activities.
     * This changes between TOP and TOP_SLEEPING to following mSleeping.
     */
    int mTopProcessState = ActivityManager.PROCESS_STATE_TOP;

    /**
     * Set while we are running a voice interaction.  This overrides
     * sleeping while it is active.
     */
    private IVoiceInteractionSession mRunningVoice;

    /**
     * For some direct access we need to power manager.
     */
    PowerManagerInternal mLocalPowerManager;

    /**
     * We want to hold a wake lock while running a voice interaction session, since
     * this may happen with the screen off and we need to keep the CPU running to
     * be able to continue to interact with the user.
     */
    PowerManager.WakeLock mVoiceWakeLock;

    /**
     * State of external calls telling us if the device is awake or asleep.
     */
    private int mWakefulness = PowerManagerInternal.WAKEFULNESS_AWAKE;

    /**
     * A list of tokens that cause the top activity to be put to sleep.
     * They are used by components that may hide and block interaction with underlying
     * activities.
     */
    final ArrayList<SleepToken> mSleepTokens = new ArrayList<SleepToken>();

    static final int LOCK_SCREEN_HIDDEN = 0;
    static final int LOCK_SCREEN_LEAVING = 1;
    static final int LOCK_SCREEN_SHOWN = 2;
    /**
     * State of external call telling us if the lock screen is shown.
     */
    int mLockScreenShown = LOCK_SCREEN_HIDDEN;

    /**
     * Set if we are shutting down the system, similar to sleeping.
     */
    boolean mShuttingDown = false;

    /**
     * Current sequence id for oom_adj computation traversal.
     */
    int mAdjSeq = 0;

    /**
     * Current sequence id for process LRU updating.
     */
    int mLruSeq = 0;

    /**
     * Keep track of the non-cached/empty process we last found, to help
     * determine how to distribute cached/empty processes next time.
     */
    int mNumNonCachedProcs = 0;

    /**
     * Keep track of the number of cached hidden procs, to balance oom adj
     * distribution between those and empty procs.
     */
    int mNumCachedHiddenProcs = 0;

    /**
     * Keep track of the number of service processes we last found, to
     * determine on the next iteration which should be B services.
     */
    int mNumServiceProcs = 0;
    int mNewNumAServiceProcs = 0;
    int mNewNumServiceProcs = 0;

    /**
     * Allow the current computed overall memory level of the system to go down?
     * This is set to false when we are killing processes for reasons other than
     * memory management, so that the now smaller process list will not be taken as
     * an indication that memory is tighter.
     */
    boolean mAllowLowerMemLevel = false;

    /**
     * The last computed memory level, for holding when we are in a state that
     * processes are going away for other reasons.
     */
    int mLastMemoryLevel = ProcessStats.ADJ_MEM_FACTOR_NORMAL;

    /**
     * The last total number of process we have, to determine if changes actually look
     * like a shrinking number of process due to lower RAM.
     */
    int mLastNumProcesses;

    /**
     * The uptime of the last time we performed idle maintenance.
     */
    long mLastIdleTime = SystemClock.uptimeMillis();

    /**
     * Total time spent with RAM that has been added in the past since the last idle time.
     */
    long mLowRamTimeSinceLastIdle = 0;

    /**
     * If RAM is currently low, when that horrible situation started.
     */
    long mLowRamStartTime = 0;

    /**
     * For reporting to battery stats the current top application.
     */
    private String mCurResumedPackage = null;
    private int mCurResumedUid = -1;

    /**
     * For reporting to battery stats the apps currently running foreground
     * service.  The ProcessMap is package/uid tuples; each of these contain
     * an array of the currently foreground processes.
     */
    final ProcessMap<ArrayList<ProcessRecord>> mForegroundPackages
            = new ProcessMap<ArrayList<ProcessRecord>>();

    /**
     * This is set if we had to do a delayed dexopt of an app before launching
     * it, to increase the ANR timeouts in that case.
     */
    boolean mDidDexOpt;

    /**
     * Set if the systemServer made a call to enterSafeMode.
     */
    boolean mSafeMode;

    /**
     * If true, we are running under a test environment so will sample PSS from processes
     * much more rapidly to try to collect better data when the tests are rapidly
     * running through apps.
     */
    boolean mTestPssMode = false;

    String mDebugApp = null;
    boolean mWaitForDebugger = false;
    boolean mDebugTransient = false;
    String mOrigDebugApp = null;
    boolean mOrigWaitForDebugger = false;
    boolean mAlwaysFinishActivities = false;
    boolean mLenientBackgroundCheck = false;
    boolean mForceResizableActivities;
    boolean mSupportsMultiWindow;
    boolean mSupportsFreeformWindowManagement;
    boolean mSupportsPictureInPicture;
    boolean mSupportsLeanbackOnly;
    Rect mDefaultPinnedStackBounds;
    IActivityController mController = null;
    boolean mControllerIsAMonkey = false;
    String mProfileApp = null;
    ProcessRecord mProfileProc = null;
    String mProfileFile;
    ParcelFileDescriptor mProfileFd;
    int mSamplingInterval = 0;
    boolean mAutoStopProfiler = false;
    int mProfileType = 0;
    final ProcessMap<Pair<Long, String>> mMemWatchProcesses = new ProcessMap<>();
    String mMemWatchDumpProcName;
    String mMemWatchDumpFile;
    int mMemWatchDumpPid;
    int mMemWatchDumpUid;
    String mTrackAllocationApp = null;
    String mNativeDebuggingApp = null;

    final long[] mTmpLong = new long[2];

    static final class ProcessChangeItem {
        static final int CHANGE_ACTIVITIES = 1<<0;
        static final int CHANGE_PROCESS_STATE = 1<<1;
        int changes;
        int uid;
        int pid;
        int processState;
        boolean foregroundActivities;
    }

    final RemoteCallbackList<IProcessObserver> mProcessObservers = new RemoteCallbackList<>();
    ProcessChangeItem[] mActiveProcessChanges = new ProcessChangeItem[5];

    final ArrayList<ProcessChangeItem> mPendingProcessChanges = new ArrayList<>();
    final ArrayList<ProcessChangeItem> mAvailProcessChanges = new ArrayList<>();

    final RemoteCallbackList<IUidObserver> mUidObservers = new RemoteCallbackList<>();
    UidRecord.ChangeItem[] mActiveUidChanges = new UidRecord.ChangeItem[5];

    final ArrayList<UidRecord.ChangeItem> mPendingUidChanges = new ArrayList<>();
    final ArrayList<UidRecord.ChangeItem> mAvailUidChanges = new ArrayList<>();

    /**
     * Runtime CPU use collection thread.  This object's lock is used to
     * perform synchronization with the thread (notifying it to run).
     */
    final Thread mProcessCpuThread;

    /**
     * Used to collect per-process CPU use for ANRs, battery stats, etc.
     * Must acquire this object's lock when accessing it.
     * NOTE: this lock will be held while doing long operations (trawling
     * through all processes in /proc), so it should never be acquired by
     * any critical paths such as when holding the main activity manager lock.
     */
    final ProcessCpuTracker mProcessCpuTracker = new ProcessCpuTracker(
            MONITOR_THREAD_CPU_USAGE);
    final AtomicLong mLastCpuTime = new AtomicLong(0);
    final AtomicBoolean mProcessCpuMutexFree = new AtomicBoolean(true);

    long mLastWriteTime = 0;

    /**
     * Used to retain an update lock when the foreground activity is in
     * immersive mode.
     */
    final UpdateLock mUpdateLock = new UpdateLock("immersive");

    /**
     * Set to true after the system has finished booting.
     */
    boolean mBooted = false;

    int mProcessLimit = ProcessList.MAX_CACHED_APPS;
    int mProcessLimitOverride = -1;
// [RTK Begin]
    TvManagerService mTvManager = null;
    ToGoService mToGoManager = null;
// [RTK  End ]

    WindowManagerService mWindowManager;
    final ActivityThread mSystemThread;

    private final class AppDeathRecipient implements IBinder.DeathRecipient {
        final ProcessRecord mApp;
        final int mPid;
        final IApplicationThread mAppThread;

        AppDeathRecipient(ProcessRecord app, int pid,
                IApplicationThread thread) {
            if (DEBUG_ALL) Slog.v(
                TAG, "New death recipient " + this
                + " for thread " + thread.asBinder());
            mApp = app;
            mPid = pid;
            mAppThread = thread;
        }

        @Override
        public void binderDied() {
            if (DEBUG_ALL) Slog.v(
                TAG, "Death received in " + this
                + " for thread " + mAppThread.asBinder());
            synchronized(ActivityManagerService.this) {
                appDiedLocked(mApp, mPid, mAppThread, true);
            }
        }
    }

    static final int SHOW_ERROR_UI_MSG = 1;
    static final int SHOW_NOT_RESPONDING_UI_MSG = 2;
    static final int SHOW_FACTORY_ERROR_UI_MSG = 3;
    static final int UPDATE_CONFIGURATION_MSG = 4;
    static final int GC_BACKGROUND_PROCESSES_MSG = 5;
    static final int WAIT_FOR_DEBUGGER_UI_MSG = 6;
    static final int SERVICE_TIMEOUT_MSG = 12;
    static final int UPDATE_TIME_ZONE = 13;
    static final int SHOW_UID_ERROR_UI_MSG = 14;
    static final int SHOW_FINGERPRINT_ERROR_UI_MSG = 15;
    static final int PROC_START_TIMEOUT_MSG = 20;
    static final int DO_PENDING_ACTIVITY_LAUNCHES_MSG = 21;
    static final int KILL_APPLICATION_MSG = 22;
    static final int FINALIZE_PENDING_INTENT_MSG = 23;
    static final int POST_HEAVY_NOTIFICATION_MSG = 24;
    static final int CANCEL_HEAVY_NOTIFICATION_MSG = 25;
    static final int SHOW_STRICT_MODE_VIOLATION_UI_MSG = 26;
    static final int CHECK_EXCESSIVE_WAKE_LOCKS_MSG = 27;
    static final int CLEAR_DNS_CACHE_MSG = 28;
    static final int UPDATE_HTTP_PROXY_MSG = 29;
    static final int SHOW_COMPAT_MODE_DIALOG_UI_MSG = 30;
    static final int DISPATCH_PROCESSES_CHANGED_UI_MSG = 31;
    static final int DISPATCH_PROCESS_DIED_UI_MSG = 32;
    static final int REPORT_MEM_USAGE_MSG = 33;
    static final int REPORT_USER_SWITCH_MSG = 34;
    static final int CONTINUE_USER_SWITCH_MSG = 35;
    static final int USER_SWITCH_TIMEOUT_MSG = 36;
    static final int IMMERSIVE_MODE_LOCK_MSG = 37;
    static final int PERSIST_URI_GRANTS_MSG = 38;
    static final int REQUEST_ALL_PSS_MSG = 39;
    static final int START_PROFILES_MSG = 40;
    static final int UPDATE_TIME = 41;
    static final int SYSTEM_USER_START_MSG = 42;
    static final int SYSTEM_USER_CURRENT_MSG = 43;
    static final int ENTER_ANIMATION_COMPLETE_MSG = 44;
    static final int FINISH_BOOTING_MSG = 45;
    static final int START_USER_SWITCH_UI_MSG = 46;
    static final int SEND_LOCALE_TO_MOUNT_DAEMON_MSG = 47;
    static final int DISMISS_DIALOG_UI_MSG = 48;
    static final int NOTIFY_TASK_STACK_CHANGE_LISTENERS_MSG = 49;
    static final int NOTIFY_CLEARTEXT_NETWORK_MSG = 50;
    static final int POST_DUMP_HEAP_NOTIFICATION_MSG = 51;
    static final int DELETE_DUMPHEAP_MSG = 52;
    static final int FOREGROUND_PROFILE_CHANGED_MSG = 53;
    static final int DISPATCH_UIDS_CHANGED_UI_MSG = 54;
    static final int REPORT_TIME_TRACKER_MSG = 55;
    static final int REPORT_USER_SWITCH_COMPLETE_MSG = 56;
    static final int SHUTDOWN_UI_AUTOMATION_CONNECTION_MSG = 57;
    static final int APP_BOOST_DEACTIVATE_MSG = 58;
    static final int CONTENT_PROVIDER_PUBLISH_TIMEOUT_MSG = 59;
    static final int IDLE_UIDS_MSG = 60;
    static final int SYSTEM_USER_UNLOCK_MSG = 61;
    static final int LOG_STACK_STATE = 62;
    static final int VR_MODE_CHANGE_MSG = 63;
    static final int NOTIFY_ACTIVITY_PINNED_LISTENERS_MSG = 64;
    static final int NOTIFY_PINNED_ACTIVITY_RESTART_ATTEMPT_LISTENERS_MSG = 65;
    static final int NOTIFY_PINNED_STACK_ANIMATION_ENDED_LISTENERS_MSG = 66;
    static final int NOTIFY_FORCED_RESIZABLE_MSG = 67;
    static final int NOTIFY_ACTIVITY_DISMISSING_DOCKED_STACK_MSG = 68;
    static final int VR_MODE_APPLY_IF_NEEDED_MSG = 69;
    static final int SHOW_UNSUPPORTED_DISPLAY_SIZE_DIALOG_MSG = 70;

    static final int FIRST_ACTIVITY_STACK_MSG = 100;
    static final int FIRST_BROADCAST_QUEUE_MSG = 200;
    static final int FIRST_COMPAT_MODE_MSG = 300;
    static final int FIRST_SUPERVISOR_STACK_MSG = 100;

    static ServiceThread sKillThread = null;
    static KillHandler sKillHandler = null;

    CompatModeDialog mCompatModeDialog;
    UnsupportedDisplaySizeDialog mUnsupportedDisplaySizeDialog;
    long mLastMemUsageReportTime = 0;

    /**
     * Flag whether the current user is a "monkey", i.e. whether
     * the UI is driven by a UI automation tool.
     */
    private boolean mUserIsMonkey;

    /** Flag whether the device has a Recents UI */
    boolean mHasRecents;

    /** The dimensions of the thumbnails in the Recents UI. */
    int mThumbnailWidth;
    int mThumbnailHeight;
    float mFullscreenThumbnailScale;

    final ServiceThread mHandlerThread;
    final MainHandler mHandler;
    final UiHandler mUiHandler;

    PackageManagerInternal mPackageManagerInt;

    // VoiceInteraction session ID that changes for each new request except when
    // being called for multiwindow assist in a single session.
    private int mViSessionId = 1000;

    final class KillHandler extends Handler {
        static final int KILL_PROCESS_GROUP_MSG = 4000;

        public KillHandler(Looper looper) {
            super(looper, null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case KILL_PROCESS_GROUP_MSG:
                {
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "killProcessGroup");
                    Process.killProcessGroup(msg.arg1 /* uid */, msg.arg2 /* pid */);
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                }
                break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    final class UiHandler extends Handler {
        public UiHandler() {
            super(com.android.server.UiThread.get().getLooper(), null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case SHOW_ERROR_UI_MSG: {
                mAppErrors.handleShowAppErrorUi(msg);
                ensureBootCompleted();
            } break;
            case SHOW_NOT_RESPONDING_UI_MSG: {
                mAppErrors.handleShowAnrUi(msg);
                ensureBootCompleted();
            } break;
            case SHOW_STRICT_MODE_VIOLATION_UI_MSG: {
                HashMap<String, Object> data = (HashMap<String, Object>) msg.obj;
                synchronized (ActivityManagerService.this) {
                    ProcessRecord proc = (ProcessRecord) data.get("app");
                    if (proc == null) {
                        Slog.e(TAG, "App not found when showing strict mode dialog.");
                        break;
                    }
                    if (proc.crashDialog != null) {
                        Slog.e(TAG, "App already has strict mode dialog: " + proc);
                        return;
                    }
                    AppErrorResult res = (AppErrorResult) data.get("result");
                    if (mShowDialogs && !mSleeping && !mShuttingDown) {
                        Dialog d = new StrictModeViolationDialog(mContext,
                                ActivityManagerService.this, res, proc);
                        d.show();
                        proc.crashDialog = d;
                    } else {
                        // The device is asleep, so just pretend that the user
                        // saw a crash dialog and hit "force quit".
                        res.set(0);
                    }
                }
                ensureBootCompleted();
            } break;
            case SHOW_FACTORY_ERROR_UI_MSG: {
                Dialog d = new FactoryErrorDialog(
                    mContext, msg.getData().getCharSequence("msg"));
                d.show();
                ensureBootCompleted();
            } break;
            case WAIT_FOR_DEBUGGER_UI_MSG: {
                synchronized (ActivityManagerService.this) {
                    ProcessRecord app = (ProcessRecord)msg.obj;
                    if (msg.arg1 != 0) {
                        if (!app.waitedForDebugger) {
                            Dialog d = new AppWaitingForDebuggerDialog(
                                    ActivityManagerService.this,
                                    mContext, app);
                            app.waitDialog = d;
                            app.waitedForDebugger = true;
                            d.show();
                        }
                    } else {
                        if (app.waitDialog != null) {
                            app.waitDialog.dismiss();
                            app.waitDialog = null;
                        }
                    }
                }
            } break;
            case SHOW_UID_ERROR_UI_MSG: {
                if (mShowDialogs) {
                    AlertDialog d = new BaseErrorDialog(mContext);
                    d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
                    d.setCancelable(false);
                    d.setTitle(mContext.getText(R.string.android_system_label));
                    d.setMessage(mContext.getText(R.string.system_error_wipe_data));
                    d.setButton(DialogInterface.BUTTON_POSITIVE, mContext.getText(R.string.ok),
                            obtainMessage(DISMISS_DIALOG_UI_MSG, d));
                    d.show();
                }
            } break;
            case SHOW_FINGERPRINT_ERROR_UI_MSG: {
                if (mShowDialogs) {
                    AlertDialog d = new BaseErrorDialog(mContext);
                    d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
                    d.setCancelable(false);
                    d.setTitle(mContext.getText(R.string.android_system_label));
                    d.setMessage(mContext.getText(R.string.system_error_manufacturer));
                    d.setButton(DialogInterface.BUTTON_POSITIVE, mContext.getText(R.string.ok),
                            obtainMessage(DISMISS_DIALOG_UI_MSG, d));
                    d.show();
                }
            } break;
            case SHOW_COMPAT_MODE_DIALOG_UI_MSG: {
                synchronized (ActivityManagerService.this) {
                    ActivityRecord ar = (ActivityRecord) msg.obj;
                    if (mCompatModeDialog != null) {
                        if (mCompatModeDialog.mAppInfo.packageName.equals(
                                ar.info.applicationInfo.packageName)) {
                            return;
                        }
                        mCompatModeDialog.dismiss();
                        mCompatModeDialog = null;
                    }
                    if (ar != null && false) {
                        if (mCompatModePackages.getPackageAskCompatModeLocked(
                                ar.packageName)) {
                            int mode = mCompatModePackages.computeCompatModeLocked(
                                    ar.info.applicationInfo);
                            if (mode == ActivityManager.COMPAT_MODE_DISABLED
                                    || mode == ActivityManager.COMPAT_MODE_ENABLED) {
                                mCompatModeDialog = new CompatModeDialog(
                                        ActivityManagerService.this, mContext,
                                        ar.info.applicationInfo);
                                mCompatModeDialog.show();
                            }
                        }
                    }
                }
                break;
            }
            case SHOW_UNSUPPORTED_DISPLAY_SIZE_DIALOG_MSG: {
                synchronized (ActivityManagerService.this) {
                    final ActivityRecord ar = (ActivityRecord) msg.obj;
                    if (mUnsupportedDisplaySizeDialog != null) {
                        mUnsupportedDisplaySizeDialog.dismiss();
                        mUnsupportedDisplaySizeDialog = null;
                    }
                    if (ar != null && mCompatModePackages.getPackageNotifyUnsupportedZoomLocked(
                            ar.packageName)) {
                        mUnsupportedDisplaySizeDialog = new UnsupportedDisplaySizeDialog(
                                ActivityManagerService.this, mContext, ar.info.applicationInfo);
                        mUnsupportedDisplaySizeDialog.show();
                    }
                }
                break;
            }
            case START_USER_SWITCH_UI_MSG: {
                mUserController.showUserSwitchDialog((Pair<UserInfo, UserInfo>) msg.obj);
                break;
            }
            case DISMISS_DIALOG_UI_MSG: {
                final Dialog d = (Dialog) msg.obj;
                d.dismiss();
                break;
            }
            case DISPATCH_PROCESSES_CHANGED_UI_MSG: {
                dispatchProcessesChanged();
                break;
            }
            case DISPATCH_PROCESS_DIED_UI_MSG: {
                final int pid = msg.arg1;
                final int uid = msg.arg2;
                dispatchProcessDied(pid, uid);
                break;
            }
            case DISPATCH_UIDS_CHANGED_UI_MSG: {
                dispatchUidsChanged();
            } break;
            }
        }
    }

    final class MainHandler extends Handler {
        public MainHandler(Looper looper) {
            super(looper, null, true);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case UPDATE_CONFIGURATION_MSG: {
                final ContentResolver resolver = mContext.getContentResolver();
                Settings.System.putConfigurationForUser(resolver, (Configuration) msg.obj,
                        msg.arg1);
            } break;
            case GC_BACKGROUND_PROCESSES_MSG: {
                synchronized (ActivityManagerService.this) {
                    performAppGcsIfAppropriateLocked();
                }
            } break;
            case SERVICE_TIMEOUT_MSG: {
                if (mDidDexOpt) {
                    mDidDexOpt = false;
                    Message nmsg = mHandler.obtainMessage(SERVICE_TIMEOUT_MSG);
                    nmsg.obj = msg.obj;
                    mHandler.sendMessageDelayed(nmsg, ActiveServices.SERVICE_TIMEOUT);
                    return;
                }
                mServices.serviceTimeout((ProcessRecord)msg.obj);
            } break;
            case UPDATE_TIME_ZONE: {
                synchronized (ActivityManagerService.this) {
                    for (int i = mLruProcesses.size() - 1 ; i >= 0 ; i--) {
                        ProcessRecord r = mLruProcesses.get(i);
                        if (r.thread != null) {
                            try {
                                r.thread.updateTimeZone();
                            } catch (RemoteException ex) {
                                Slog.w(TAG, "Failed to update time zone for: " + r.info.processName);
                            }
                        }
                    }
                }
            } break;
            case CLEAR_DNS_CACHE_MSG: {
                synchronized (ActivityManagerService.this) {
                    for (int i = mLruProcesses.size() - 1 ; i >= 0 ; i--) {
                        ProcessRecord r = mLruProcesses.get(i);
                        if (r.thread != null) {
                            try {
                                r.thread.clearDnsCache();
                            } catch (RemoteException ex) {
                                Slog.w(TAG, "Failed to clear dns cache for: " + r.info.processName);
                            }
                        }
                    }
                }
            } break;
            case UPDATE_HTTP_PROXY_MSG: {
                ProxyInfo proxy = (ProxyInfo)msg.obj;
                String host = "";
                String port = "";
                String exclList = "";
                Uri pacFileUrl = Uri.EMPTY;
                if (proxy != null) {
                    host = proxy.getHost();
                    port = Integer.toString(proxy.getPort());
                    exclList = proxy.getExclusionListAsString();
                    pacFileUrl = proxy.getPacFileUrl();
                }
                synchronized (ActivityManagerService.this) {
                    for (int i = mLruProcesses.size() - 1 ; i >= 0 ; i--) {
                        ProcessRecord r = mLruProcesses.get(i);
                        if (r.thread != null) {
                            try {
                                r.thread.setHttpProxy(host, port, exclList, pacFileUrl);
                            } catch (RemoteException ex) {
                                Slog.w(TAG, "Failed to update http proxy for: " +
                                        r.info.processName);
                            }
                        }
                    }
                }
            } break;
            case PROC_START_TIMEOUT_MSG: {
                if (mDidDexOpt) {
                    mDidDexOpt = false;
                    Message nmsg = mHandler.obtainMessage(PROC_START_TIMEOUT_MSG);
                    nmsg.obj = msg.obj;
                    mHandler.sendMessageDelayed(nmsg, PROC_START_TIMEOUT);
                    return;
                }
                ProcessRecord app = (ProcessRecord)msg.obj;
                synchronized (ActivityManagerService.this) {
                    processStartTimedOutLocked(app);
                }
            } break;
            case CONTENT_PROVIDER_PUBLISH_TIMEOUT_MSG: {
                ProcessRecord app = (ProcessRecord)msg.obj;
                synchronized (ActivityManagerService.this) {
                    processContentProviderPublishTimedOutLocked(app);
                }
            } break;
            case DO_PENDING_ACTIVITY_LAUNCHES_MSG: {
                synchronized (ActivityManagerService.this) {
                    mActivityStarter.doPendingActivityLaunchesLocked(true);
                }
            } break;
            case KILL_APPLICATION_MSG: {
                synchronized (ActivityManagerService.this) {
                    final int appId = msg.arg1;
                    final int userId = msg.arg2;
                    Bundle bundle = (Bundle)msg.obj;
                    String pkg = bundle.getString("pkg");
                    String reason = bundle.getString("reason");
                    forceStopPackageLocked(pkg, appId, false, false, true, false,
                            false, userId, reason);
                }
            } break;
            case FINALIZE_PENDING_INTENT_MSG: {
                ((PendingIntentRecord)msg.obj).completeFinalize();
            } break;
            case POST_HEAVY_NOTIFICATION_MSG: {
                INotificationManager inm = NotificationManager.getService();
                if (inm == null) {
                    return;
                }

                ActivityRecord root = (ActivityRecord)msg.obj;
                ProcessRecord process = root.app;
                if (process == null) {
                    return;
                }

                try {
                    Context context = mContext.createPackageContext(process.info.packageName, 0);
                    String text = mContext.getString(R.string.heavy_weight_notification,
                            context.getApplicationInfo().loadLabel(context.getPackageManager()));
                    Notification notification = new Notification.Builder(context)
                            .setSmallIcon(com.android.internal.R.drawable.stat_sys_adb)
                            .setWhen(0)
                            .setOngoing(true)
                            .setTicker(text)
                            .setColor(mContext.getColor(
                                    com.android.internal.R.color.system_notification_accent_color))
                            .setContentTitle(text)
                            .setContentText(
                                    mContext.getText(R.string.heavy_weight_notification_detail))
                            .setContentIntent(PendingIntent.getActivityAsUser(mContext, 0,
                                    root.intent, PendingIntent.FLAG_CANCEL_CURRENT, null,
                                    new UserHandle(root.userId)))
                            .build();
                    try {
                        int[] outId = new int[1];
                        inm.enqueueNotificationWithTag("android", "android", null,
                                R.string.heavy_weight_notification,
                                notification, outId, root.userId);
                    } catch (RuntimeException e) {
                        Slog.w(ActivityManagerService.TAG,
                                "Error showing notification for heavy-weight app", e);
                    } catch (RemoteException e) {
                    }
                } catch (NameNotFoundException e) {
                    Slog.w(TAG, "Unable to create context for heavy notification", e);
                }
            } break;
            case CANCEL_HEAVY_NOTIFICATION_MSG: {
                INotificationManager inm = NotificationManager.getService();
                if (inm == null) {
                    return;
                }
                try {
                    inm.cancelNotificationWithTag("android", null,
                            R.string.heavy_weight_notification,  msg.arg1);
                } catch (RuntimeException e) {
                    Slog.w(ActivityManagerService.TAG,
                            "Error canceling notification for service", e);
                } catch (RemoteException e) {
                }
            } break;
            case CHECK_EXCESSIVE_WAKE_LOCKS_MSG: {
                synchronized (ActivityManagerService.this) {
                    checkExcessivePowerUsageLocked(true);
                    removeMessages(CHECK_EXCESSIVE_WAKE_LOCKS_MSG);
                    Message nmsg = obtainMessage(CHECK_EXCESSIVE_WAKE_LOCKS_MSG);
                    sendMessageDelayed(nmsg, POWER_CHECK_DELAY);
                }
            } break;
            case REPORT_MEM_USAGE_MSG: {
                final ArrayList<ProcessMemInfo> memInfos = (ArrayList<ProcessMemInfo>)msg.obj;
                Thread thread = new Thread() {
                    @Override public void run() {
                        reportMemUsage(memInfos);
                    }
                };
                thread.start();
                break;
            }
            case REPORT_USER_SWITCH_MSG: {
                mUserController.dispatchUserSwitch((UserState) msg.obj, msg.arg1, msg.arg2);
                break;
            }
            case CONTINUE_USER_SWITCH_MSG: {
                mUserController.continueUserSwitch((UserState) msg.obj, msg.arg1, msg.arg2);
                break;
            }
            case USER_SWITCH_TIMEOUT_MSG: {
                mUserController.timeoutUserSwitch((UserState) msg.obj, msg.arg1, msg.arg2);
                break;
            }
            case IMMERSIVE_MODE_LOCK_MSG: {
                final boolean nextState = (msg.arg1 != 0);
                if (mUpdateLock.isHeld() != nextState) {
                    if (DEBUG_IMMERSIVE) Slog.d(TAG_IMMERSIVE,
                            "Applying new update lock state '" + nextState
                            + "' for " + (ActivityRecord)msg.obj);
                    if (nextState) {
                        mUpdateLock.acquire();
                    } else {
                        mUpdateLock.release();
                    }
                }
                break;
            }
            case PERSIST_URI_GRANTS_MSG: {
                writeGrantedUriPermissions();
                break;
            }
            case REQUEST_ALL_PSS_MSG: {
                synchronized (ActivityManagerService.this) {
                    requestPssAllProcsLocked(SystemClock.uptimeMillis(), true, false);
                }
                break;
            }
            case START_PROFILES_MSG: {
                synchronized (ActivityManagerService.this) {
                    mUserController.startProfilesLocked();
                }
                break;
            }
            case UPDATE_TIME: {
                synchronized (ActivityManagerService.this) {
                    for (int i = mLruProcesses.size() - 1 ; i >= 0 ; i--) {
                        ProcessRecord r = mLruProcesses.get(i);
                        if (r.thread != null) {
                            try {
                                r.thread.updateTimePrefs(msg.arg1 == 0 ? false : true);
                            } catch (RemoteException ex) {
                                Slog.w(TAG, "Failed to update preferences for: " + r.info.processName);
                            }
                        }
                    }
                }
                break;
            }
            case SYSTEM_USER_START_MSG: {
                mBatteryStatsService.noteEvent(BatteryStats.HistoryItem.EVENT_USER_RUNNING_START,
                        Integer.toString(msg.arg1), msg.arg1);
                mSystemServiceManager.startUser(msg.arg1);
                break;
            }
            case SYSTEM_USER_UNLOCK_MSG: {
                final int userId = msg.arg1;
                mSystemServiceManager.unlockUser(userId);
                synchronized (ActivityManagerService.this) {
                    mRecentTasks.loadUserRecentsLocked(userId);
                }
                if (userId == UserHandle.USER_SYSTEM) {
                    startPersistentApps(PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
                }
                installEncryptionUnawareProviders(userId);
                mUserController.finishUserUnlocked((UserState) msg.obj);
                break;
            }
            case SYSTEM_USER_CURRENT_MSG: {
                mBatteryStatsService.noteEvent(
                        BatteryStats.HistoryItem.EVENT_USER_FOREGROUND_FINISH,
                        Integer.toString(msg.arg2), msg.arg2);
                mBatteryStatsService.noteEvent(
                        BatteryStats.HistoryItem.EVENT_USER_FOREGROUND_START,
                        Integer.toString(msg.arg1), msg.arg1);
                mSystemServiceManager.switchUser(msg.arg1);
                break;
            }
            case ENTER_ANIMATION_COMPLETE_MSG: {
                synchronized (ActivityManagerService.this) {
                    ActivityRecord r = ActivityRecord.forTokenLocked((IBinder) msg.obj);
                    if (r != null && r.app != null && r.app.thread != null) {
                        try {
                            r.app.thread.scheduleEnterAnimationComplete(r.appToken);
                        } catch (RemoteException e) {
                        }
                    }
                }
                break;
            }
            case FINISH_BOOTING_MSG: {
                if (msg.arg1 != 0) {
                    Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "FinishBooting");
                    finishBooting();
                    Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
                }
                if (msg.arg2 != 0) {
                    enableScreenAfterBoot();
                }
                break;
            }
            case SEND_LOCALE_TO_MOUNT_DAEMON_MSG: {
                try {
                    Locale l = (Locale) msg.obj;
                    IBinder service = ServiceManager.getService("mount");
                    IMountService mountService = IMountService.Stub.asInterface(service);
                    Log.d(TAG, "Storing locale " + l.toLanguageTag() + " for decryption UI");
                    mountService.setField(StorageManager.SYSTEM_LOCALE_KEY, l.toLanguageTag());
                } catch (RemoteException e) {
                    Log.e(TAG, "Error storing locale for decryption UI", e);
                }
                break;
            }
            case NOTIFY_TASK_STACK_CHANGE_LISTENERS_MSG: {
                synchronized (ActivityManagerService.this) {
                    for (int i = mTaskStackListeners.beginBroadcast() - 1; i >= 0; i--) {
                        try {
                            // Make a one-way callback to the listener
                            mTaskStackListeners.getBroadcastItem(i).onTaskStackChanged();
                        } catch (RemoteException e){
                            // Handled by the RemoteCallbackList
                        }
                    }
                    mTaskStackListeners.finishBroadcast();
                }
                break;
            }
            case NOTIFY_ACTIVITY_PINNED_LISTENERS_MSG: {
                synchronized (ActivityManagerService.this) {
                    for (int i = mTaskStackListeners.beginBroadcast() - 1; i >= 0; i--) {
                        try {
                            // Make a one-way callback to the listener
                            mTaskStackListeners.getBroadcastItem(i).onActivityPinned();
                        } catch (RemoteException e){
                            // Handled by the RemoteCallbackList
                        }
                    }
                    mTaskStackListeners.finishBroadcast();
                }
                break;
            }
            case NOTIFY_PINNED_ACTIVITY_RESTART_ATTEMPT_LISTENERS_MSG: {
                synchronized (ActivityManagerService.this) {
                    for (int i = mTaskStackListeners.beginBroadcast() - 1; i >= 0; i--) {
                        try {
                            // Make a one-way callback to the listener
                            mTaskStackListeners.getBroadcastItem(i).onPinnedActivityRestartAttempt();
                        } catch (RemoteException e){
                            // Handled by the RemoteCallbackList
                        }
                    }
                    mTaskStackListeners.finishBroadcast();
                }
                break;
            }
            case NOTIFY_PINNED_STACK_ANIMATION_ENDED_LISTENERS_MSG: {
                synchronized (ActivityManagerService.this) {
                    for (int i = mTaskStackListeners.beginBroadcast() - 1; i >= 0; i--) {
                        try {
                            // Make a one-way callback to the listener
                            mTaskStackListeners.getBroadcastItem(i).onPinnedStackAnimationEnded();
                        } catch (RemoteException e){
                            // Handled by the RemoteCallbackList
                        }
                    }
                    mTaskStackListeners.finishBroadcast();
                }
                break;
            }
            case NOTIFY_FORCED_RESIZABLE_MSG: {
                synchronized (ActivityManagerService.this) {
                    for (int i = mTaskStackListeners.beginBroadcast() - 1; i >= 0; i--) {
                        try {
                            // Make a one-way callback to the listener
                            mTaskStackListeners.getBroadcastItem(i).onActivityForcedResizable(
                                    (String) msg.obj, msg.arg1);
                        } catch (RemoteException e){
                            // Handled by the RemoteCallbackList
                        }
                    }
                    mTaskStackListeners.finishBroadcast();
                }
                break;
            }
                case NOTIFY_ACTIVITY_DISMISSING_DOCKED_STACK_MSG: {
                    synchronized (ActivityManagerService.this) {
                        for (int i = mTaskStackListeners.beginBroadcast() - 1; i >= 0; i--) {
                            try {
                                // Make a one-way callback to the listener
                                mTaskStackListeners.getBroadcastItem(i)
                                        .onActivityDismissingDockedStack();
                            } catch (RemoteException e){
                                // Handled by the RemoteCallbackList
                            }
                        }
                        mTaskStackListeners.finishBroadcast();
                    }
                    break;
                }
            case NOTIFY_CLEARTEXT_NETWORK_MSG: {
                final int uid = msg.arg1;
                final byte[] firstPacket = (byte[]) msg.obj;

                synchronized (mPidsSelfLocked) {
                    for (int i = 0; i < mPidsSelfLocked.size(); i++) {
                        final ProcessRecord p = mPidsSelfLocked.valueAt(i);
                        if (p.uid == uid) {
                            try {
                                p.thread.notifyCleartextNetwork(firstPacket);
                            } catch (RemoteException ignored) {
                            }
                        }
                    }
                }
                break;
            }
            case POST_DUMP_HEAP_NOTIFICATION_MSG: {
                final String procName;
                final int uid;
                final long memLimit;
                final String reportPackage;
                synchronized (ActivityManagerService.this) {
                    procName = mMemWatchDumpProcName;
                    uid = mMemWatchDumpUid;
                    Pair<Long, String> val = mMemWatchProcesses.get(procName, uid);
                    if (val == null) {
                        val = mMemWatchProcesses.get(procName, 0);
                    }
                    if (val != null) {
                        memLimit = val.first;
                        reportPackage = val.second;
                    } else {
                        memLimit = 0;
                        reportPackage = null;
                    }
                }
                if (procName == null) {
                    return;
                }

                if (DEBUG_PSS) Slog.d(TAG_PSS,
                        "Showing dump heap notification from " + procName + "/" + uid);

                INotificationManager inm = NotificationManager.getService();
                if (inm == null) {
                    return;
                }

                String text = mContext.getString(R.string.dump_heap_notification, procName);


                Intent deleteIntent = new Intent();
                deleteIntent.setAction(DumpHeapActivity.ACTION_DELETE_DUMPHEAP);
                Intent intent = new Intent();
                intent.setClassName("android", DumpHeapActivity.class.getName());
                intent.putExtra(DumpHeapActivity.KEY_PROCESS, procName);
                intent.putExtra(DumpHeapActivity.KEY_SIZE, memLimit);
                if (reportPackage != null) {
                    intent.putExtra(DumpHeapActivity.KEY_DIRECT_LAUNCH, reportPackage);
                }
                int userId = UserHandle.getUserId(uid);
                Notification notification = new Notification.Builder(mContext)
                        .setSmallIcon(com.android.internal.R.drawable.stat_sys_adb)
                        .setWhen(0)
                        .setOngoing(true)
                        .setAutoCancel(true)
                        .setTicker(text)
                        .setColor(mContext.getColor(
                                com.android.internal.R.color.system_notification_accent_color))
                        .setContentTitle(text)
                        .setContentText(
                                mContext.getText(R.string.dump_heap_notification_detail))
                        .setContentIntent(PendingIntent.getActivityAsUser(mContext, 0,
                                intent, PendingIntent.FLAG_CANCEL_CURRENT, null,
                                new UserHandle(userId)))
                        .setDeleteIntent(PendingIntent.getBroadcastAsUser(mContext, 0,
                                deleteIntent, 0, UserHandle.SYSTEM))
                        .build();

                try {
                    int[] outId = new int[1];
                    inm.enqueueNotificationWithTag("android", "android", null,
                            R.string.dump_heap_notification,
                            notification, outId, userId);
                } catch (RuntimeException e) {
                    Slog.w(ActivityManagerService.TAG,
                            "Error showing notification for dump heap", e);
                } catch (RemoteException e) {
                }
            } break;
            case DELETE_DUMPHEAP_MSG: {
                revokeUriPermission(ActivityThread.currentActivityThread().getApplicationThread(),
                        DumpHeapActivity.JAVA_URI,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        UserHandle.myUserId());
                synchronized (ActivityManagerService.this) {
                    mMemWatchDumpFile = null;
                    mMemWatchDumpProcName = null;
                    mMemWatchDumpPid = -1;
                    mMemWatchDumpUid = -1;
                }
            } break;
            case FOREGROUND_PROFILE_CHANGED_MSG: {
                mUserController.dispatchForegroundProfileChanged(msg.arg1);
            } break;
            case REPORT_TIME_TRACKER_MSG: {
                AppTimeTracker tracker = (AppTimeTracker)msg.obj;
                tracker.deliverResult(mContext);
            } break;
            case REPORT_USER_SWITCH_COMPLETE_MSG: {
                mUserController.dispatchUserSwitchComplete(msg.arg1);
            } break;
            case SHUTDOWN_UI_AUTOMATION_CONNECTION_MSG: {
                IUiAutomationConnection connection = (IUiAutomationConnection) msg.obj;
                try {
                    connection.shutdown();
                } catch (RemoteException e) {
                    Slog.w(TAG, "Error shutting down UiAutomationConnection");
                }
                // Only a UiAutomation can set this flag and now that
                // it is finished we make sure it is reset to its default.
                mUserIsMonkey = false;
            } break;
            case APP_BOOST_DEACTIVATE_MSG: {
                synchronized(ActivityManagerService.this) {
                    if (mIsBoosted) {
                        if (mBoostStartTime < (SystemClock.uptimeMillis() - APP_BOOST_TIMEOUT)) {
                            nativeMigrateFromBoost();
                            mIsBoosted = false;
                            mBoostStartTime = 0;
                        } else {
                            Message newmsg = mHandler.obtainMessage(APP_BOOST_DEACTIVATE_MSG);
                            mHandler.sendMessageDelayed(newmsg, APP_BOOST_TIMEOUT);
                        }
                    }
                }
            } break;
            case IDLE_UIDS_MSG: {
                idleUids();
            } break;
            case LOG_STACK_STATE: {
                synchronized (ActivityManagerService.this) {
                    mStackSupervisor.logStackState();
                }
            } break;
            case VR_MODE_CHANGE_MSG: {
                VrManagerInternal vrService = LocalServices.getService(VrManagerInternal.class);
                final ActivityRecord r = (ActivityRecord) msg.obj;
                boolean vrMode;
                ComponentName requestedPackage;
                ComponentName callingPackage;
                int userId;
                synchronized (ActivityManagerService.this) {
                    vrMode = r.requestedVrComponent != null;
                    requestedPackage = r.requestedVrComponent;
                    userId = r.userId;
                    callingPackage = r.info.getComponentName();
                    if (mInVrMode != vrMode) {
                        mInVrMode = vrMode;
                        mShowDialogs = shouldShowDialogs(mConfiguration, mInVrMode);
                        if (r.app != null) {
                            ProcessRecord proc = r.app;
                            if (proc.vrThreadTid > 0) {
                                if (proc.curSchedGroup == ProcessList.SCHED_GROUP_TOP_APP) {
                                    try {
                                        if (mInVrMode == true) {
                                            Process.setThreadScheduler(proc.vrThreadTid,
                                                Process.SCHED_FIFO | Process.SCHED_RESET_ON_FORK, 1);
                                        } else {
                                            Process.setThreadScheduler(proc.vrThreadTid,
                                                Process.SCHED_OTHER, 0);
                                        }
                                    } catch (IllegalArgumentException e) {
                                        Slog.w(TAG, "Failed to set scheduling policy, thread does"
                                                + " not exist:\n" + e);
                                    }
                                }
                            }
                        }
                    }
                }
                vrService.setVrMode(vrMode, requestedPackage, userId, callingPackage);
            } break;
            case VR_MODE_APPLY_IF_NEEDED_MSG: {
                final ActivityRecord r = (ActivityRecord) msg.obj;
                final boolean needsVrMode = r != null && r.requestedVrComponent != null;
                if (needsVrMode) {
                    applyVrMode(msg.arg1 == 1, r.requestedVrComponent, r.userId,
                            r.info.getComponentName(), false);
                }
            } break;
            }
        }
    };

    static final int COLLECT_PSS_BG_MSG = 1;

    final Handler mBgHandler = new Handler(BackgroundThread.getHandler().getLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case COLLECT_PSS_BG_MSG: {
                long start = SystemClock.uptimeMillis();
                MemInfoReader memInfo = null;
                synchronized (ActivityManagerService.this) {
                    if (mFullPssPending) {
                        mFullPssPending = false;
                        memInfo = new MemInfoReader();
                    }
                }
                if (memInfo != null) {
                    updateCpuStatsNow();
                    long nativeTotalPss = 0;
                    final List<ProcessCpuTracker.Stats> stats;
                    synchronized (mProcessCpuTracker) {
                        stats = mProcessCpuTracker.getStats( (st)-> {
                            return st.vsize > 0 && st.uid < Process.FIRST_APPLICATION_UID;
                        });
                    }
                    final int N = stats.size();
                    for (int j = 0; j < N; j++) {
                        synchronized (mPidsSelfLocked) {
                            if (mPidsSelfLocked.indexOfKey(stats.get(j).pid) >= 0) {
                                // This is one of our own processes; skip it.
                                continue;
                            }
                        }
                        nativeTotalPss += Debug.getPss(stats.get(j).pid, null, null);
                    }
                    memInfo.readMemInfo();
                    synchronized (ActivityManagerService.this) {
                        if (DEBUG_PSS) Slog.d(TAG_PSS, "Collected native and kernel memory in "
                                + (SystemClock.uptimeMillis()-start) + "ms");
                        final long cachedKb = memInfo.getCachedSizeKb();
                        final long freeKb = memInfo.getFreeSizeKb();
                        final long zramKb = memInfo.getZramTotalSizeKb();
                        final long kernelKb = memInfo.getKernelUsedSizeKb();
                        EventLogTags.writeAmMeminfo(cachedKb*1024, freeKb*1024, zramKb*1024,
                                kernelKb*1024, nativeTotalPss*1024);
                        mProcessStats.addSysMemUsageLocked(cachedKb, freeKb, zramKb, kernelKb,
                                nativeTotalPss);
                    }
                }

                int num = 0;
                long[] tmp = new long[2];
                do {
                    ProcessRecord proc;
                    int procState;
                    int pid;
                    long lastPssTime;
                    synchronized (ActivityManagerService.this) {
                        if (mPendingPssProcesses.size() <= 0) {
                            if (mTestPssMode || DEBUG_PSS) Slog.d(TAG_PSS,
                                    "Collected PSS of " + num + " processes in "
                                    + (SystemClock.uptimeMillis() - start) + "ms");
                            mPendingPssProcesses.clear();
                            return;
                        }
                        proc = mPendingPssProcesses.remove(0);
                        procState = proc.pssProcState;
                        lastPssTime = proc.lastPssTime;
                        if (proc.thread != null && procState == proc.setProcState
                                && (lastPssTime+ProcessList.PSS_SAFE_TIME_FROM_STATE_CHANGE)
                                        < SystemClock.uptimeMillis()) {
                            pid = proc.pid;
                        } else {
                            proc = null;
                            pid = 0;
                        }
                    }
                    if (proc != null) {
                        long pss = Debug.getPss(pid, tmp, null);
                        synchronized (ActivityManagerService.this) {
                            if (pss != 0 && proc.thread != null && proc.setProcState == procState
                                    && proc.pid == pid && proc.lastPssTime == lastPssTime) {
                                num++;
                                recordPssSampleLocked(proc, procState, pss, tmp[0], tmp[1],
                                        SystemClock.uptimeMillis());
                            }
                        }
                    }
                } while (true);
            }
            }
        }
    };

    public void setSystemProcess() {
        try {
            ServiceManager.addService(Context.ACTIVITY_SERVICE, this, true);
            ServiceManager.addService(ProcessStats.SERVICE_NAME, mProcessStats);
            ServiceManager.addService("meminfo", new MemBinder(this));
            ServiceManager.addService("gfxinfo", new GraphicsBinder(this));
            ServiceManager.addService("dbinfo", new DbBinder(this));
            if (MONITOR_CPU_USAGE) {
                ServiceManager.addService("cpuinfo", new CpuBinder(this));
            }
            ServiceManager.addService("permission", new PermissionController(this));
            ServiceManager.addService("processinfo", new ProcessInfoService(this));

            ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(
                    "android", STOCK_PM_FLAGS | MATCH_SYSTEM_ONLY);
            mSystemThread.installSystemApplicationInfo(info, getClass().getClassLoader());

            synchronized (this) {
                ProcessRecord app = newProcessRecordLocked(info, info.processName, false, 0);
                app.persistent = true;
                app.pid = MY_PID;
                app.maxAdj = ProcessList.SYSTEM_ADJ;
                app.makeActive(mSystemThread.getApplicationThread(), mProcessStats);
                synchronized (mPidsSelfLocked) {
                    mPidsSelfLocked.put(app.pid, app);
                }
                updateLruProcessLocked(app, false, null);
                updateOomAdjLocked();
            }
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(
                    "Unable to find android system package", e);
        }
    }

// [RTK Begin]
    public void setTvManager(TvManagerService tv){
        mTvManager = tv;
    }

    public void setToGoManager(ToGoService togoService) {
        mToGoManager = togoService;
    }

    public TvManagerService getTvManager(){
        if(mTvManager != null)
            return mTvManager;
        else
            return null;
    }

    public ToGoService getToGoManager() {
        if (mToGoManager != null) {
            return mToGoManager;
        }
        else {
            return null;
        }
    }
// [RTK  End ]

    public void setWindowManager(WindowManagerService wm) {
        mWindowManager = wm;
        mStackSupervisor.setWindowManager(wm);
        mActivityStarter.setWindowManager(wm);
    }

    public void setUsageStatsManager(UsageStatsManagerInternal usageStatsManager) {
        mUsageStatsService = usageStatsManager;
    }

    public void startObservingNativeCrashes() {
        final NativeCrashListener ncl = new NativeCrashListener(this);
        ncl.start();
    }

    public IAppOpsService getAppOpsService() {
        return mAppOpsService;
    }

    static class MemBinder extends Binder {
        ActivityManagerService mActivityManagerService;
        MemBinder(ActivityManagerService activityManagerService) {
            mActivityManagerService = activityManagerService;
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (mActivityManagerService.checkCallingPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                pw.println("Permission Denial: can't dump meminfo from from pid="
                        + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                        + " without permission " + android.Manifest.permission.DUMP);
                return;
            }

            mActivityManagerService.dumpApplicationMemoryUsage(fd, pw, "  ", args, false, null);
        }
    }

    static class GraphicsBinder extends Binder {
        ActivityManagerService mActivityManagerService;
        GraphicsBinder(ActivityManagerService activityManagerService) {
            mActivityManagerService = activityManagerService;
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (mActivityManagerService.checkCallingPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                pw.println("Permission Denial: can't dump gfxinfo from from pid="
                        + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                        + " without permission " + android.Manifest.permission.DUMP);
                return;
            }

            mActivityManagerService.dumpGraphicsHardwareUsage(fd, pw, args);
        }
    }

    static class DbBinder extends Binder {
        ActivityManagerService mActivityManagerService;
        DbBinder(ActivityManagerService activityManagerService) {
            mActivityManagerService = activityManagerService;
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (mActivityManagerService.checkCallingPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                pw.println("Permission Denial: can't dump dbinfo from from pid="
                        + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                        + " without permission " + android.Manifest.permission.DUMP);
                return;
            }

            mActivityManagerService.dumpDbInfo(fd, pw, args);
        }
    }

    static class CpuBinder extends Binder {
        ActivityManagerService mActivityManagerService;
        CpuBinder(ActivityManagerService activityManagerService) {
            mActivityManagerService = activityManagerService;
        }

        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (mActivityManagerService.checkCallingPermission(android.Manifest.permission.DUMP)
                    != PackageManager.PERMISSION_GRANTED) {
                pw.println("Permission Denial: can't dump cpuinfo from from pid="
                        + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                        + " without permission " + android.Manifest.permission.DUMP);
                return;
            }

            synchronized (mActivityManagerService.mProcessCpuTracker) {
                pw.print(mActivityManagerService.mProcessCpuTracker.printCurrentLoad());
                pw.print(mActivityManagerService.mProcessCpuTracker.printCurrentState(
                        SystemClock.uptimeMillis()));
            }
        }
    }

    public static final class Lifecycle extends SystemService {
        private final ActivityManagerService mService;

        public Lifecycle(Context context) {
            super(context);
            mService = new ActivityManagerService(context);
        }

        @Override
        public void onStart() {
            mService.start();
        }

        public ActivityManagerService getService() {
            return mService;
        }
    }

    // Note: This method is invoked on the main thread but may need to attach various
    // handlers to other threads.  So take care to be explicit about the looper.
    public ActivityManagerService(Context systemContext) {
        mContext = systemContext;
        mFactoryTest = FactoryTest.getMode();
        mSystemThread = ActivityThread.currentActivityThread();

        Slog.i(TAG, "Memory class: " + ActivityManager.staticGetMemoryClass());

        mHandlerThread = new ServiceThread(TAG,
                android.os.Process.THREAD_PRIORITY_FOREGROUND, false /*allowIo*/);
        mHandlerThread.start();
        mHandler = new MainHandler(mHandlerThread.getLooper());
        mUiHandler = new UiHandler();

        /* static; one-time init here */
        if (sKillHandler == null) {
            sKillThread = new ServiceThread(TAG + ":kill",
                    android.os.Process.THREAD_PRIORITY_BACKGROUND, true /* allowIo */);
            sKillThread.start();
            sKillHandler = new KillHandler(sKillThread.getLooper());
        }

        mFgBroadcastQueue = new BroadcastQueue(this, mHandler,
                "foreground", BROADCAST_FG_TIMEOUT, false);
        mBgBroadcastQueue = new BroadcastQueue(this, mHandler,
                "background", BROADCAST_BG_TIMEOUT, true);
        mBroadcastQueues[0] = mFgBroadcastQueue;
        mBroadcastQueues[1] = mBgBroadcastQueue;

        mServices = new ActiveServices(this);
        mProviderMap = new ProviderMap(this);
        mAppErrors = new AppErrors(mContext, this);

        // TODO: Move creation of battery stats service outside of activity manager service.
        File dataDir = Environment.getDataDirectory();
        File systemDir = new File(dataDir, "system");
        systemDir.mkdirs();
        mBatteryStatsService = new BatteryStatsService(systemDir, mHandler);
        mBatteryStatsService.getActiveStatistics().readLocked();
        mBatteryStatsService.scheduleWriteToDisk();
        mOnBattery = DEBUG_POWER ? true
                : mBatteryStatsService.getActiveStatistics().getIsOnBattery();
        mBatteryStatsService.getActiveStatistics().setCallback(this);

        mProcessStats = new ProcessStatsService(this, new File(systemDir, "procstats"));

        mAppOpsService = new AppOpsService(new File(systemDir, "appops.xml"), mHandler);
        mAppOpsService.startWatchingMode(AppOpsManager.OP_RUN_IN_BACKGROUND, null,
                new IAppOpsCallback.Stub() {
                    @Override public void opChanged(int op, int uid, String packageName) {
                        if (op == AppOpsManager.OP_RUN_IN_BACKGROUND && packageName != null) {
                            if (mAppOpsService.checkOperation(op, uid, packageName)
                                    != AppOpsManager.MODE_ALLOWED) {
                                runInBackgroundDisabled(uid);
                            }
                        }
                    }
                });

        mGrantFile = new AtomicFile(new File(systemDir, "urigrants.xml"));

        mUserController = new UserController(this);

        GL_ES_VERSION = SystemProperties.getInt("ro.opengles.version",
            ConfigurationInfo.GL_ES_VERSION_UNDEFINED);

        if (SystemProperties.getInt("sys.use_fifo_ui", 0) != 0) {
            mUseFifoUiScheduling = true;
        }

        mTrackingAssociations = "1".equals(SystemProperties.get("debug.track-associations"));

        mConfiguration.setToDefaults();
        mConfiguration.setLocales(LocaleList.getDefault());

        mConfigurationSeq = mConfiguration.seq = 1;
        mProcessCpuTracker.init();

        mCompatModePackages = new CompatModePackages(this, systemDir, mHandler);
        mIntentFirewall = new IntentFirewall(new IntentFirewallInterface(), mHandler);
        mStackSupervisor = new ActivityStackSupervisor(this);
        mActivityStarter = new ActivityStarter(this, mStackSupervisor);
        mRecentTasks = new RecentTasks(this, mStackSupervisor);

        mProcessCpuThread = new Thread("CpuTracker") {
            @Override
            public void run() {
                while (true) {
                    try {
                        try {
                            synchronized(this) {
                                final long now = SystemClock.uptimeMillis();
                                long nextCpuDelay = (mLastCpuTime.get()+MONITOR_CPU_MAX_TIME)-now;
                                long nextWriteDelay = (mLastWriteTime+BATTERY_STATS_TIME)-now;
                                //Slog.i(TAG, "Cpu delay=" + nextCpuDelay
                                //        + ", write delay=" + nextWriteDelay);
                                if (nextWriteDelay < nextCpuDelay) {
                                    nextCpuDelay = nextWriteDelay;
                                }
                                if (nextCpuDelay > 0) {
                                    mProcessCpuMutexFree.set(true);
                                    this.wait(nextCpuDelay);
                                }
                            }
                        } catch (InterruptedException e) {
                        }
                        updateCpuStatsNow();
                    } catch (Exception e) {
                        Slog.e(TAG, "Unexpected exception collecting process stats", e);
                    }
                }
            }
        };

        Watchdog.getInstance().addMonitor(this);
        Watchdog.getInstance().addThread(mHandler);
    }

    public void setSystemServiceManager(SystemServiceManager mgr) {
        mSystemServiceManager = mgr;
    }

    public void setInstaller(Installer installer) {
        mInstaller = installer;
    }

    private void start() {
        Process.removeAllProcessGroups();
        mProcessCpuThread.start();

        mBatteryStatsService.publish(mContext);
        mAppOpsService.publish(mContext);
        Slog.d("AppOps", "AppOpsService published");
        LocalServices.addService(ActivityManagerInternal.class, new LocalService());
    }

    void onUserStoppedLocked(int userId) {
        mRecentTasks.unloadUserDataFromMemoryLocked(userId);
    }

    public void initPowerManagement() {
        mStackSupervisor.initPowerManagement();
        mBatteryStatsService.initPowerManagement();
        mLocalPowerManager = LocalServices.getService(PowerManagerInternal.class);
        PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
        mVoiceWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "*voice*");
        mVoiceWakeLock.setReferenceCounted(false);
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == SYSPROPS_TRANSACTION) {
            // We need to tell all apps about the system property change.
            ArrayList<IBinder> procs = new ArrayList<IBinder>();
            synchronized(this) {
                final int NP = mProcessNames.getMap().size();
                for (int ip=0; ip<NP; ip++) {
                    SparseArray<ProcessRecord> apps = mProcessNames.getMap().valueAt(ip);
                    final int NA = apps.size();
                    for (int ia=0; ia<NA; ia++) {
                        ProcessRecord app = apps.valueAt(ia);
                        if (app.thread != null) {
                            procs.add(app.thread.asBinder());
                        }
                    }
                }
            }

            int N = procs.size();
            for (int i=0; i<N; i++) {
                Parcel data2 = Parcel.obtain();
                try {
                    procs.get(i).transact(IBinder.SYSPROPS_TRANSACTION, data2, null, 0);
                } catch (RemoteException e) {
                }
                data2.recycle();
            }
        }
        try {
            return super.onTransact(code, data, reply, flags);
        } catch (RuntimeException e) {
            // The activity manager only throws security exceptions, so let's
            // log all others.
            if (!(e instanceof SecurityException)) {
                Slog.wtf(TAG, "Activity Manager Crash", e);
            }
            throw e;
        }
    }

    void updateCpuStats() {
        final long now = SystemClock.uptimeMillis();
        if (mLastCpuTime.get() >= now - MONITOR_CPU_MIN_TIME) {
            return;
        }
        if (mProcessCpuMutexFree.compareAndSet(true, false)) {
            synchronized (mProcessCpuThread) {
                mProcessCpuThread.notify();
            }
        }
    }

    void updateCpuStatsNow() {
        synchronized (mProcessCpuTracker) {
            mProcessCpuMutexFree.set(false);
            final long now = SystemClock.uptimeMillis();
            boolean haveNewCpuStats = false;

            if (MONITOR_CPU_USAGE &&
                    mLastCpuTime.get() < (now-MONITOR_CPU_MIN_TIME)) {
                mLastCpuTime.set(now);
                mProcessCpuTracker.update();
                if (mProcessCpuTracker.hasGoodLastStats()) {
                    haveNewCpuStats = true;
                    //Slog.i(TAG, mProcessCpu.printCurrentState());
                    //Slog.i(TAG, "Total CPU usage: "
                    //        + mProcessCpu.getTotalCpuPercent() + "%");

                    // Slog the cpu usage if the property is set.
                    if ("true".equals(SystemProperties.get("events.cpu"))) {
                        int user = mProcessCpuTracker.getLastUserTime();
                        int system = mProcessCpuTracker.getLastSystemTime();
                        int iowait = mProcessCpuTracker.getLastIoWaitTime();
                        int irq = mProcessCpuTracker.getLastIrqTime();
                        int softIrq = mProcessCpuTracker.getLastSoftIrqTime();
                        int idle = mProcessCpuTracker.getLastIdleTime();

                        int total = user + system + iowait + irq + softIrq + idle;
                        if (total == 0) total = 1;

                        EventLog.writeEvent(EventLogTags.CPU,
                                ((user+system+iowait+irq+softIrq) * 100) / total,
                                (user * 100) / total,
                                (system * 100) / total,
                                (iowait * 100) / total,
                                (irq * 100) / total,
                                (softIrq * 100) / total);
                    }
                }
            }

            final BatteryStatsImpl bstats = mBatteryStatsService.getActiveStatistics();
            synchronized(bstats) {
                synchronized(mPidsSelfLocked) {
                    if (haveNewCpuStats) {
                        if (bstats.startAddingCpuLocked()) {
                            int totalUTime = 0;
                            int totalSTime = 0;
                            final int N = mProcessCpuTracker.countStats();
                            for (int i=0; i<N; i++) {
                                ProcessCpuTracker.Stats st = mProcessCpuTracker.getStats(i);
                                if (!st.working) {
                                    continue;
                                }
                                ProcessRecord pr = mPidsSelfLocked.get(st.pid);
                                totalUTime += st.rel_utime;
                                totalSTime += st.rel_stime;
                                if (pr != null) {
                                    BatteryStatsImpl.Uid.Proc ps = pr.curProcBatteryStats;
                                    if (ps == null || !ps.isActive()) {
                                        pr.curProcBatteryStats = ps = bstats.getProcessStatsLocked(
                                                pr.info.uid, pr.processName);
                                    }
                                    ps.addCpuTimeLocked(st.rel_utime, st.rel_stime);
                                    pr.curCpuTime += st.rel_utime + st.rel_stime;
                                } else {
                                    BatteryStatsImpl.Uid.Proc ps = st.batteryStats;
                                    if (ps == null || !ps.isActive()) {
                                        st.batteryStats = ps = bstats.getProcessStatsLocked(
                                                bstats.mapUid(st.uid), st.name);
                                    }
                                    ps.addCpuTimeLocked(st.rel_utime, st.rel_stime);
                                }
                            }
                            final int userTime = mProcessCpuTracker.getLastUserTime();
                            final int systemTime = mProcessCpuTracker.getLastSystemTime();
                            final int iowaitTime = mProcessCpuTracker.getLastIoWaitTime();
                            final int irqTime = mProcessCpuTracker.getLastIrqTime();
                            final int softIrqTime = mProcessCpuTracker.getLastSoftIrqTime();
                            final int idleTime = mProcessCpuTracker.getLastIdleTime();
                            bstats.finishAddingCpuLocked(totalUTime, totalSTime, userTime,
                                    systemTime, iowaitTime, irqTime, softIrqTime, idleTime);
                        }
                    }
                }

                if (mLastWriteTime < (now-BATTERY_STATS_TIME)) {
                    mLastWriteTime = now;
                    mBatteryStatsService.scheduleWriteToDisk();
                }
            }
        }
    }

    @Override
    public void batteryNeedsCpuUpdate() {
        updateCpuStatsNow();
    }

    @Override
    public void batteryPowerChanged(boolean onBattery) {
        // When plugging in, update the CPU stats first before changing
        // the plug state.
        updateCpuStatsNow();
        synchronized (this) {
            synchronized(mPidsSelfLocked) {
                mOnBattery = DEBUG_POWER ? true : onBattery;
            }
        }
    }

    @Override
    public void batterySendBroadcast(Intent intent) {
        broadcastIntentLocked(null, null, intent, null, null, 0, null, null, null,
                AppOpsManager.OP_NONE, null, false, false,
                -1, Process.SYSTEM_UID, UserHandle.USER_ALL);
    }

    /**
     * Initialize the application bind args. These are passed to each
     * process when the bindApplication() IPC is sent to the process. They're
     * lazily setup to make sure the services are running when they're asked for.
     */
    private HashMap<String, IBinder> getCommonServicesLocked(boolean isolated) {
        // Isolated processes won't get this optimization, so that we don't
        // violate the rules about which services they have access to.
        if (isolated) {
            if (mIsolatedAppBindArgs == null) {
                mIsolatedAppBindArgs = new HashMap<>();
                mIsolatedAppBindArgs.put("package", ServiceManager.getService("package"));
            }
            return mIsolatedAppBindArgs;
        }

        if (mAppBindArgs == null) {
            mAppBindArgs = new HashMap<>();

            // Setup the application init args
            mAppBindArgs.put("package", ServiceManager.getService("package"));
            mAppBindArgs.put("window", ServiceManager.getService("window"));
            mAppBindArgs.put(Context.ALARM_SERVICE,
                    ServiceManager.getService(Context.ALARM_SERVICE));
        }
        return mAppBindArgs;
    }

    boolean setFocusedActivityLocked(ActivityRecord r, String reason) {
        if (r == null || mFocusedActivity == r) {
            return false;
        }

        if (!r.isFocusable()) {
            if (DEBUG_FOCUS) Slog.d(TAG_FOCUS, "setFocusedActivityLocked: unfocusable r=" + r);
            return false;
        }

        if (DEBUG_FOCUS) Slog.d(TAG_FOCUS, "setFocusedActivityLocked: r=" + r);

        final boolean wasDoingSetFocusedActivity = mDoingSetFocusedActivity;
        if (wasDoingSetFocusedActivity) Slog.w(TAG,
                "setFocusedActivityLocked: called recursively, r=" + r + ", reason=" + reason);
        mDoingSetFocusedActivity = true;

        final ActivityRecord last = mFocusedActivity;
        mFocusedActivity = r;
        if (r.task.isApplicationTask()) {
            if (mCurAppTimeTracker != r.appTimeTracker) {
                // We are switching app tracking.  Complete the current one.
                if (mCurAppTimeTracker != null) {
                    mCurAppTimeTracker.stop();
                    mHandler.obtainMessage(
                            REPORT_TIME_TRACKER_MSG, mCurAppTimeTracker).sendToTarget();
                    mStackSupervisor.clearOtherAppTimeTrackers(r.appTimeTracker);
                    mCurAppTimeTracker = null;
                }
                if (r.appTimeTracker != null) {
                    mCurAppTimeTracker = r.appTimeTracker;
                    startTimeTrackingFocusedActivityLocked();
                }
            } else {
                startTimeTrackingFocusedActivityLocked();
            }
        } else {
            r.appTimeTracker = null;
        }
        // TODO: VI Maybe r.task.voiceInteractor || r.voiceInteractor != null
        // TODO: Probably not, because we don't want to resume voice on switching
        // back to this activity
        if (r.task.voiceInteractor != null) {
            startRunningVoiceLocked(r.task.voiceSession, r.info.applicationInfo.uid);
        } else {
            finishRunningVoiceLocked();
            IVoiceInteractionSession session;
            if (last != null && ((session = last.task.voiceSession) != null
                    || (session = last.voiceSession) != null)) {
                // We had been in a voice interaction session, but now focused has
                // move to something different.  Just finish the session, we can't
                // return to it and retain the proper state and synchronization with
                // the voice interaction service.
                finishVoiceTask(session);
            }
        }
        if (mStackSupervisor.moveActivityStackToFront(r, reason + " setFocusedActivity")) {
            mWindowManager.setFocusedApp(r.appToken, true);
        }
        applyUpdateLockStateLocked(r);
        applyUpdateVrModeLocked(r);
        if (mFocusedActivity.userId != mLastFocusedUserId) {
            mHandler.removeMessages(FOREGROUND_PROFILE_CHANGED_MSG);
            mHandler.obtainMessage(
                    FOREGROUND_PROFILE_CHANGED_MSG, mFocusedActivity.userId, 0).sendToTarget();
            mLastFocusedUserId = mFocusedActivity.userId;
        }

        // Log a warning if the focused app is changed during the process. This could
        // indicate a problem of the focus setting logic!
        if (mFocusedActivity != r) Slog.w(TAG,
                "setFocusedActivityLocked: r=" + r + " but focused to " + mFocusedActivity);
        mDoingSetFocusedActivity = wasDoingSetFocusedActivity;

        EventLogTags.writeAmFocusedActivity(
                mFocusedActivity == null ? -1 : mFocusedActivity.userId,
                mFocusedActivity == null ? "NULL" : mFocusedActivity.shortComponentName,
                reason);
        return true;
    }

    final void resetFocusedActivityIfNeededLocked(ActivityRecord goingAway) {
        if (mFocusedActivity != goingAway) {
            return;
        }

        final ActivityStack focusedStack = mStackSupervisor.getFocusedStack();
        if (focusedStack != null) {
            final ActivityRecord top = focusedStack.topActivity();
            if (top != null && top.userId != mLastFocusedUserId) {
                mHandler.removeMessages(FOREGROUND_PROFILE_CHANGED_MSG);
                mHandler.sendMessage(
                        mHandler.obtainMessage(FOREGROUND_PROFILE_CHANGED_MSG, top.userId, 0));
                mLastFocusedUserId = top.userId;
            }
        }

        // Try to move focus to another activity if possible.
        if (setFocusedActivityLocked(
                focusedStack.topRunningActivityLocked(), "resetFocusedActivityIfNeeded")) {
            return;
        }

        if (DEBUG_FOCUS) Slog.d(TAG_FOCUS, "resetFocusedActivityIfNeeded: Setting focus to NULL "
                + "prev mFocusedActivity=" + mFocusedActivity + " goingAway=" + goingAway);
        mFocusedActivity = null;
        EventLogTags.writeAmFocusedActivity(-1, "NULL", "resetFocusedActivityIfNeeded");
    }

    @Override
    public void setFocusedStack(int stackId) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "setFocusedStack()");
        if (DEBUG_FOCUS) Slog.d(TAG_FOCUS, "setFocusedStack: stackId=" + stackId);
        final long callingId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final ActivityStack stack = mStackSupervisor.getStack(stackId);
                if (stack == null) {
                    return;
                }
                final ActivityRecord r = stack.topRunningActivityLocked();
                if (setFocusedActivityLocked(r, "setFocusedStack")) {
                    mStackSupervisor.resumeFocusedStackTopActivityLocked();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Override
    public void setFocusedTask(int taskId) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "setFocusedTask()");
        if (DEBUG_FOCUS) Slog.d(TAG_FOCUS, "setFocusedTask: taskId=" + taskId);
        final long callingId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final TaskRecord task = mStackSupervisor.anyTaskForIdLocked(taskId);
                if (task == null) {
                    return;
                }
                if (mUserController.shouldConfirmCredentials(task.userId)) {
                    mActivityStarter.showConfirmDeviceCredential(task.userId);
                    if (task.stack != null && task.stack.mStackId == FREEFORM_WORKSPACE_STACK_ID) {
                        mStackSupervisor.moveTaskToStackLocked(task.taskId,
                                FULLSCREEN_WORKSPACE_STACK_ID, !ON_TOP, !FORCE_FOCUS,
                                "setFocusedTask", ANIMATE);
                    }
                    return;
                }
                final ActivityRecord r = task.topRunningActivityLocked();
                if (setFocusedActivityLocked(r, "setFocusedTask")) {
                    mStackSupervisor.resumeFocusedStackTopActivityLocked();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /** Sets the task stack listener that gets callbacks when a task stack changes. */
    @Override
    public void registerTaskStackListener(ITaskStackListener listener) throws RemoteException {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "registerTaskStackListener()");
        synchronized (this) {
            if (listener != null) {
                mTaskStackListeners.register(listener);
            }
        }
    }

    @Override
    public void notifyActivityDrawn(IBinder token) {
        if (DEBUG_VISIBILITY) Slog.d(TAG_VISIBILITY, "notifyActivityDrawn: token=" + token);
        synchronized (this) {
            ActivityRecord r = mStackSupervisor.isInAnyStackLocked(token);
            if (r != null) {
                r.task.stack.notifyActivityDrawnLocked(r);
            }
        }
    }

    final void applyUpdateLockStateLocked(ActivityRecord r) {
        // Modifications to the UpdateLock state are done on our handler, outside
        // the activity manager's locks.  The new state is determined based on the
        // state *now* of the relevant activity record.  The object is passed to
        // the handler solely for logging detail, not to be consulted/modified.
        final boolean nextState = r != null && r.immersive;
        mHandler.sendMessage(
                mHandler.obtainMessage(IMMERSIVE_MODE_LOCK_MSG, (nextState) ? 1 : 0, 0, r));
    }

    final void applyUpdateVrModeLocked(ActivityRecord r) {
        mHandler.sendMessage(
                mHandler.obtainMessage(VR_MODE_CHANGE_MSG, 0, 0, r));
    }

    private void applyVrModeIfNeededLocked(ActivityRecord r, boolean enable) {
        mHandler.sendMessage(
                mHandler.obtainMessage(VR_MODE_APPLY_IF_NEEDED_MSG, enable ? 1 : 0, 0, r));
    }

    private void applyVrMode(boolean enabled, ComponentName packageName, int userId,
            ComponentName callingPackage, boolean immediate) {
        VrManagerInternal vrService =
                LocalServices.getService(VrManagerInternal.class);
        if (immediate) {
            vrService.setVrModeImmediate(enabled, packageName, userId, callingPackage);
        } else {
            vrService.setVrMode(enabled, packageName, userId, callingPackage);
        }
    }

    final void showAskCompatModeDialogLocked(ActivityRecord r) {
        Message msg = Message.obtain();
        msg.what = SHOW_COMPAT_MODE_DIALOG_UI_MSG;
        msg.obj = r.task.askedCompatMode ? null : r;
        mUiHandler.sendMessage(msg);
    }

    final void showUnsupportedZoomDialogIfNeededLocked(ActivityRecord r) {
        if (mConfiguration.densityDpi != DisplayMetrics.DENSITY_DEVICE_STABLE
                && r.appInfo.requiresSmallestWidthDp > mConfiguration.smallestScreenWidthDp) {
            final Message msg = Message.obtain();
            msg.what = SHOW_UNSUPPORTED_DISPLAY_SIZE_DIALOG_MSG;
            msg.obj = r;
            mUiHandler.sendMessage(msg);
        }
    }

    private int updateLruProcessInternalLocked(ProcessRecord app, long now, int index,
            String what, Object obj, ProcessRecord srcApp) {
        app.lastActivityTime = now;

        if (app.activities.size() > 0) {
            // Don't want to touch dependent processes that are hosting activities.
            return index;
        }

        int lrui = mLruProcesses.lastIndexOf(app);
        if (lrui < 0) {
            Slog.wtf(TAG, "Adding dependent process " + app + " not on LRU list: "
                    + what + " " + obj + " from " + srcApp);
            return index;
        }

        if (lrui >= index) {
            // Don't want to cause this to move dependent processes *back* in the
            // list as if they were less frequently used.
            return index;
        }

        if (lrui >= mLruProcessActivityStart) {
            // Don't want to touch dependent processes that are hosting activities.
            return index;
        }

        mLruProcesses.remove(lrui);
        if (index > 0) {
            index--;
        }
        if (DEBUG_LRU) Slog.d(TAG_LRU, "Moving dep from " + lrui + " to " + index
                + " in LRU list: " + app);
        mLruProcesses.add(index, app);
        return index;
    }

    static void killProcessGroup(int uid, int pid) {
        if (sKillHandler != null) {
            sKillHandler.sendMessage(
                    sKillHandler.obtainMessage(KillHandler.KILL_PROCESS_GROUP_MSG, uid, pid));
        } else {
            Slog.w(TAG, "Asked to kill process group before system bringup!");
            Process.killProcessGroup(uid, pid);
        }
    }

    final void removeLruProcessLocked(ProcessRecord app) {
        int lrui = mLruProcesses.lastIndexOf(app);
        if (lrui >= 0) {
            if (!app.killed) {
                Slog.wtfStack(TAG, "Removing process that hasn't been killed: " + app);
                Process.killProcessQuiet(app.pid);
                killProcessGroup(app.uid, app.pid);
            }
            if (lrui <= mLruProcessActivityStart) {
                mLruProcessActivityStart--;
            }
            if (lrui <= mLruProcessServiceStart) {
                mLruProcessServiceStart--;
            }
            mLruProcesses.remove(lrui);
        }
    }

    final void updateLruProcessLocked(ProcessRecord app, boolean activityChange,
            ProcessRecord client) {
        final boolean hasActivity = app.activities.size() > 0 || app.hasClientActivities
                || app.treatLikeActivity;
        final boolean hasService = false; // not impl yet. app.services.size() > 0;
        if (!activityChange && hasActivity) {
            // The process has activities, so we are only allowing activity-based adjustments
            // to move it.  It should be kept in the front of the list with other
            // processes that have activities, and we don't want those to change their
            // order except due to activity operations.
            return;
        }

        mLruSeq++;
        final long now = SystemClock.uptimeMillis();
        app.lastActivityTime = now;

        // First a quick reject: if the app is already at the position we will
        // put it, then there is nothing to do.
        if (hasActivity) {
            final int N = mLruProcesses.size();
            if (N > 0 && mLruProcesses.get(N-1) == app) {
                if (DEBUG_LRU) Slog.d(TAG_LRU, "Not moving, already top activity: " + app);
                return;
            }
        } else {
            if (mLruProcessServiceStart > 0
                    && mLruProcesses.get(mLruProcessServiceStart-1) == app) {
                if (DEBUG_LRU) Slog.d(TAG_LRU, "Not moving, already top other: " + app);
                return;
            }
        }

        int lrui = mLruProcesses.lastIndexOf(app);

        if (app.persistent && lrui >= 0) {
            // We don't care about the position of persistent processes, as long as
            // they are in the list.
            if (DEBUG_LRU) Slog.d(TAG_LRU, "Not moving, persistent: " + app);
            return;
        }

        /* In progress: compute new position first, so we can avoid doing work
           if the process is not actually going to move.  Not yet working.
        int addIndex;
        int nextIndex;
        boolean inActivity = false, inService = false;
        if (hasActivity) {
            // Process has activities, put it at the very tipsy-top.
            addIndex = mLruProcesses.size();
            nextIndex = mLruProcessServiceStart;
            inActivity = true;
        } else if (hasService) {
            // Process has services, put it at the top of the service list.
            addIndex = mLruProcessActivityStart;
            nextIndex = mLruProcessServiceStart;
            inActivity = true;
            inService = true;
        } else  {
            // Process not otherwise of interest, it goes to the top of the non-service area.
            addIndex = mLruProcessServiceStart;
            if (client != null) {
                int clientIndex = mLruProcesses.lastIndexOf(client);
                if (clientIndex < 0) Slog.d(TAG, "Unknown client " + client + " when updating "
                        + app);
                if (clientIndex >= 0 && addIndex > clientIndex) {
                    addIndex = clientIndex;
                }
            }
            nextIndex = addIndex > 0 ? addIndex-1 : addIndex;
        }

        Slog.d(TAG, "Update LRU at " + lrui + " to " + addIndex + " (act="
                + mLruProcessActivityStart + "): " + app);
        */

        if (lrui >= 0) {
            if (lrui < mLruProcessActivityStart) {
                mLruProcessActivityStart--;
            }
            if (lrui < mLruProcessServiceStart) {
                mLruProcessServiceStart--;
            }
            /*
            if (addIndex > lrui) {
                addIndex--;
            }
            if (nextIndex > lrui) {
                nextIndex--;
            }
            */
            mLruProcesses.remove(lrui);
        }

        /*
        mLruProcesses.add(addIndex, app);
        if (inActivity) {
            mLruProcessActivityStart++;
        }
        if (inService) {
            mLruProcessActivityStart++;
        }
        */

        int nextIndex;
        if (hasActivity) {
            final int N = mLruProcesses.size();
            if (app.activities.size() == 0 && mLruProcessActivityStart < (N - 1)) {
                // Process doesn't have activities, but has clients with
                // activities...  move it up, but one below the top (the top
                // should always have a real activity).
                if (DEBUG_LRU) Slog.d(TAG_LRU,
                        "Adding to second-top of LRU activity list: " + app);
                mLruProcesses.add(N - 1, app);
                // To keep it from spamming the LRU list (by making a bunch of clients),
                // we will push down any other entries owned by the app.
                final int uid = app.info.uid;
                for (int i = N - 2; i > mLruProcessActivityStart; i--) {
                    ProcessRecord subProc = mLruProcesses.get(i);
                    if (subProc.info.uid == uid) {
                        // We want to push this one down the list.  If the process after
                        // it is for the same uid, however, don't do so, because we don't
                        // want them internally to be re-ordered.
                        if (mLruProcesses.get(i - 1).info.uid != uid) {
                            if (DEBUG_LRU) Slog.d(TAG_LRU,
                                    "Pushing uid " + uid + " swapping at " + i + ": "
                                    + mLruProcesses.get(i) + " : " + mLruProcesses.get(i - 1));
                            ProcessRecord tmp = mLruProcesses.get(i);
                            mLruProcesses.set(i, mLruProcesses.get(i - 1));
                            mLruProcesses.set(i - 1, tmp);
                            i--;
                        }
                    } else {
                        // A gap, we can stop here.
                        break;
                    }
                }
            } else {
                // Process has activities, put it at the very tipsy-top.
                if (DEBUG_LRU) Slog.d(TAG_LRU, "Adding to top of LRU activity list: " + app);
                mLruProcesses.add(app);
            }
            nextIndex = mLruProcessServiceStart;
        } else if (hasService) {
            // Process has services, put it at the top of the service list.
            if (DEBUG_LRU) Slog.d(TAG_LRU, "Adding to top of LRU service list: " + app);
            mLruProcesses.add(mLruProcessActivityStart, app);
            nextIndex = mLruProcessServiceStart;
            mLruProcessActivityStart++;
        } else  {
            // Process not otherwise of interest, it goes to the top of the non-service area.
            int index = mLruProcessServiceStart;
            if (client != null) {
                // If there is a client, don't allow the process to be moved up higher
                // in the list than that client.
                int clientIndex = mLruProcesses.lastIndexOf(client);
                if (DEBUG_LRU && clientIndex < 0) Slog.d(TAG_LRU, "Unknown client " + client
                        + " when updating " + app);
                if (clientIndex <= lrui) {
                    // Don't allow the client index restriction to push it down farther in the
                    // list than it already is.
                    clientIndex = lrui;
                }
                if (clientIndex >= 0 && index > clientIndex) {
                    index = clientIndex;
                }
            }
            if (DEBUG_LRU) Slog.d(TAG_LRU, "Adding at " + index + " of LRU list: " + app);
            mLruProcesses.add(index, app);
            nextIndex = index-1;
            mLruProcessActivityStart++;
            mLruProcessServiceStart++;
        }

        // If the app is currently using a content provider or service,
        // bump those processes as well.
        for (int j=app.connections.size()-1; j>=0; j--) {
            ConnectionRecord cr = app.connections.valueAt(j);
            if (cr.binding != null && !cr.serviceDead && cr.binding.service != null
                    && cr.binding.service.app != null
                    && cr.binding.service.app.lruSeq != mLruSeq
                    && !cr.binding.service.app.persistent) {
                nextIndex = updateLruProcessInternalLocked(cr.binding.service.app, now, nextIndex,
                        "service connection", cr, app);
            }
        }
        for (int j=app.conProviders.size()-1; j>=0; j--) {
            ContentProviderRecord cpr = app.conProviders.get(j).provider;
            if (cpr.proc != null && cpr.proc.lruSeq != mLruSeq && !cpr.proc.persistent) {
                nextIndex = updateLruProcessInternalLocked(cpr.proc, now, nextIndex,
                        "provider reference", cpr, app);
            }
        }
    }

    final ProcessRecord getProcessRecordLocked(String processName, int uid, boolean keepIfLarge) {
        if (uid == Process.SYSTEM_UID) {
            // The system gets to run in any process.  If there are multiple
            // processes with the same uid, just pick the first (this
            // should never happen).
            SparseArray<ProcessRecord> procs = mProcessNames.getMap().get(processName);
            if (procs == null) return null;
            final int procCount = procs.size();
            for (int i = 0; i < procCount; i++) {
                final int procUid = procs.keyAt(i);
                if (UserHandle.isApp(procUid) || !UserHandle.isSameUser(procUid, uid)) {
                    // Don't use an app process or different user process for system component.
                    continue;
                }
                return procs.valueAt(i);
            }
        }
        ProcessRecord proc = mProcessNames.get(processName, uid);
        if (false && proc != null && !keepIfLarge
                && proc.setProcState >= ActivityManager.PROCESS_STATE_CACHED_EMPTY
                && proc.lastCachedPss >= 4000) {
            // Turn this condition on to cause killing to happen regularly, for testing.
            if (proc.baseProcessTracker != null) {
                proc.baseProcessTracker.reportCachedKill(proc.pkgList, proc.lastCachedPss);
            }
            proc.kill(Long.toString(proc.lastCachedPss) + "k from cached", true);
        } else if (proc != null && !keepIfLarge
                && mLastMemoryLevel > ProcessStats.ADJ_MEM_FACTOR_NORMAL
                && proc.setProcState >= ActivityManager.PROCESS_STATE_CACHED_EMPTY) {
            if (DEBUG_PSS) Slog.d(TAG_PSS, "May not keep " + proc + ": pss=" + proc.lastCachedPss);
            if (proc.lastCachedPss >= mProcessList.getCachedRestoreThresholdKb()) {
                if (proc.baseProcessTracker != null) {
                    proc.baseProcessTracker.reportCachedKill(proc.pkgList, proc.lastCachedPss);
                }
                proc.kill(Long.toString(proc.lastCachedPss) + "k from cached", true);
            }
        }
        return proc;
    }

    void notifyPackageUse(String packageName, int reason) {
        IPackageManager pm = AppGlobals.getPackageManager();
        try {
            pm.notifyPackageUse(packageName, reason);
        } catch (RemoteException e) {
        }
    }

    boolean isNextTransitionForward() {
        int transit = mWindowManager.getPendingAppTransition();
        return transit == TRANSIT_ACTIVITY_OPEN
                || transit == TRANSIT_TASK_OPEN
                || transit == TRANSIT_TASK_TO_FRONT;
    }

    int startIsolatedProcess(String entryPoint, String[] entryPointArgs,
            String processName, String abiOverride, int uid, Runnable crashHandler) {
        synchronized(this) {
            ApplicationInfo info = new ApplicationInfo();
            // In general the ApplicationInfo.uid isn't neccesarily equal to ProcessRecord.uid.
            // For isolated processes, the former contains the parent's uid and the latter the
            // actual uid of the isolated process.
            // In the special case introduced by this method (which is, starting an isolated
            // process directly from the SystemServer without an actual parent app process) the
            // closest thing to a parent's uid is SYSTEM_UID.
            // The only important thing here is to keep AI.uid != PR.uid, in order to trigger
            // the |isolated| logic in the ProcessRecord constructor.
            info.uid = Process.SYSTEM_UID;
            info.processName = processName;
            info.className = entryPoint;
            info.packageName = "android";
            ProcessRecord proc = startProcessLocked(processName, info /* info */,
                    false /* knownToBeDead */, 0 /* intentFlags */, ""  /* hostingType */,
                    null /* hostingName */, true /* allowWhileBooting */, true /* isolated */,
                    uid, true /* keepIfLarge */, abiOverride, entryPoint, entryPointArgs,
                    crashHandler);
            return proc != null ? proc.pid : 0;
        }
    }

    final ProcessRecord startProcessLocked(String processName,
            ApplicationInfo info, boolean knownToBeDead, int intentFlags,
            String hostingType, ComponentName hostingName, boolean allowWhileBooting,
            boolean isolated, boolean keepIfLarge) {
        return startProcessLocked(processName, info, knownToBeDead, intentFlags, hostingType,
                hostingName, allowWhileBooting, isolated, 0 /* isolatedUid */, keepIfLarge,
                null /* ABI override */, null /* entryPoint */, null /* entryPointArgs */,
                null /* crashHandler */);
    }

    final ProcessRecord startProcessLocked(String processName, ApplicationInfo info,
            boolean knownToBeDead, int intentFlags, String hostingType, ComponentName hostingName,
            boolean allowWhileBooting, boolean isolated, int isolatedUid, boolean keepIfLarge,
            String abiOverride, String entryPoint, String[] entryPointArgs, Runnable crashHandler) {
        long startTime = SystemClock.elapsedRealtime();
        ProcessRecord app;
        if (!isolated) {
            app = getProcessRecordLocked(processName, info.uid, keepIfLarge);
            checkTime(startTime, "startProcess: after getProcessRecord");

            if ((intentFlags & Intent.FLAG_FROM_BACKGROUND) != 0) {
                // If we are in the background, then check to see if this process
                // is bad.  If so, we will just silently fail.
                if (mAppErrors.isBadProcessLocked(info)) {
                    if (DEBUG_PROCESSES) Slog.v(TAG, "Bad process: " + info.uid
                            + "/" + info.processName);
                    return null;
                }
            } else {
                // When the user is explicitly starting a process, then clear its
                // crash count so that we won't make it bad until they see at
                // least one crash dialog again, and make the process good again
                // if it had been bad.
                if (DEBUG_PROCESSES) Slog.v(TAG, "Clearing bad process: " + info.uid
                        + "/" + info.processName);
                mAppErrors.resetProcessCrashTimeLocked(info);
                if (mAppErrors.isBadProcessLocked(info)) {
                    EventLog.writeEvent(EventLogTags.AM_PROC_GOOD,
                            UserHandle.getUserId(info.uid), info.uid,
                            info.processName);
                    mAppErrors.clearBadProcessLocked(info);
                    if (app != null) {
                        app.bad = false;
                    }
                }
            }
        } else {
            // If this is an isolated process, it can't re-use an existing process.
            app = null;
        }

        // app launch boost for big.little configurations
        // use cpusets to migrate freshly launched tasks to big cores
        nativeMigrateToBoost();
        mIsBoosted = true;
        mBoostStartTime = SystemClock.uptimeMillis();
        Message msg = mHandler.obtainMessage(APP_BOOST_DEACTIVATE_MSG);
        mHandler.sendMessageDelayed(msg, APP_BOOST_MESSAGE_DELAY);

        // We don't have to do anything more if:
        // (1) There is an existing application record; and
        // (2) The caller doesn't think it is dead, OR there is no thread
        //     object attached to it so we know it couldn't have crashed; and
        // (3) There is a pid assigned to it, so it is either starting or
        //     already running.
        if (DEBUG_PROCESSES) Slog.v(TAG_PROCESSES, "startProcess: name=" + processName
                + " app=" + app + " knownToBeDead=" + knownToBeDead
                + " thread=" + (app != null ? app.thread : null)
                + " pid=" + (app != null ? app.pid : -1));
        if (app != null && app.pid > 0) {
            if ((!knownToBeDead && !app.killed) || app.thread == null) {
                // We already have the app running, or are waiting for it to
                // come up (we have a pid but not yet its thread), so keep it.
                if (DEBUG_PROCESSES) Slog.v(TAG_PROCESSES, "App already running: " + app);
                // If this is a new package in the process, add the package to the list
                app.addPackage(info.packageName, info.versionCode, mProcessStats);
                checkTime(startTime, "startProcess: done, added package to proc");
                return app;
            }

            // An application record is attached to a previous process,
            // clean it up now.
            if (DEBUG_PROCESSES || DEBUG_CLEANUP) Slog.v(TAG_PROCESSES, "App died: " + app);
            checkTime(startTime, "startProcess: bad proc running, killing");
            killProcessGroup(app.uid, app.pid);
            handleAppDiedLocked(app, true, true);
            checkTime(startTime, "startProcess: done killing old proc");
        }

        String hostingNameStr = hostingName != null
                ? hostingName.flattenToShortString() : null;

        if (app == null) {
            checkTime(startTime, "startProcess: creating new process record");
            app = newProcessRecordLocked(info, processName, isolated, isolatedUid);
            if (app == null) {
                Slog.w(TAG, "Failed making new process record for "
                        + processName + "/" + info.uid + " isolated=" + isolated);
                return null;
            }
            app.crashHandler = crashHandler;
            checkTime(startTime, "startProcess: done creating new process record");
        } else {
            // If this is a new package in the process, add the package to the list
            app.addPackage(info.packageName, info.versionCode, mProcessStats);
            checkTime(startTime, "startProcess: added package to existing proc");
        }

        // If the system is not ready yet, then hold off on starting this
        // process until it is.
        if (!mProcessesReady
                && !isAllowedWhileBooting(info)
                && !allowWhileBooting) {
            if (!mProcessesOnHold.contains(app)) {
                mProcessesOnHold.add(app);
            }
            if (DEBUG_PROCESSES) Slog.v(TAG_PROCESSES,
                    "System not ready, putting on hold: " + app);
            checkTime(startTime, "startProcess: returning with proc on hold");
            return app;
        }

        checkTime(startTime, "startProcess: stepping in to startProcess");
        startProcessLocked(
                app, hostingType, hostingNameStr, abiOverride, entryPoint, entryPointArgs);
        checkTime(startTime, "startProcess: done starting proc!");
        return (app.pid != 0) ? app : null;
    }

    boolean isAllowedWhileBooting(ApplicationInfo ai) {
        return (ai.flags&ApplicationInfo.FLAG_PERSISTENT) != 0;
    }

    private final void startProcessLocked(ProcessRecord app,
            String hostingType, String hostingNameStr) {
        startProcessLocked(app, hostingType, hostingNameStr, null /* abiOverride */,
                null /* entryPoint */, null /* entryPointArgs */);
    }

    private final void startProcessLocked(ProcessRecord app, String hostingType,
            String hostingNameStr, String abiOverride, String entryPoint, String[] entryPointArgs) {
        long startTime = SystemClock.elapsedRealtime();
        if (app.pid > 0 && app.pid != MY_PID) {
            checkTime(startTime, "startProcess: removing from pids map");
            synchronized (mPidsSelfLocked) {
                mPidsSelfLocked.remove(app.pid);
                mHandler.removeMessages(PROC_START_TIMEOUT_MSG, app);
            }
            checkTime(startTime, "startProcess: done removing from pids map");
            app.setPid(0);
        }

        if (DEBUG_PROCESSES && mProcessesOnHold.contains(app)) Slog.v(TAG_PROCESSES,
                "startProcessLocked removing on hold: " + app);
        mProcessesOnHold.remove(app);

        checkTime(startTime, "startProcess: starting to update cpu stats");
        updateCpuStats();
        checkTime(startTime, "startProcess: done updating cpu stats");

        try {
            try {
                final int userId = UserHandle.getUserId(app.uid);
                AppGlobals.getPackageManager().checkPackageStartable(app.info.packageName, userId);
            } catch (RemoteException e) {
                throw e.rethrowAsRuntimeException();
            }

            int uid = app.uid;
            int[] gids = null;
            int mountExternal = Zygote.MOUNT_EXTERNAL_NONE;
            if (!app.isolated) {
                int[] permGids = null;
                try {
                    checkTime(startTime, "startProcess: getting gids from package manager");
                    final IPackageManager pm = AppGlobals.getPackageManager();
                    permGids = pm.getPackageGids(app.info.packageName,
                            MATCH_DEBUG_TRIAGED_MISSING, app.userId);
                    MountServiceInternal mountServiceInternal = LocalServices.getService(
                            MountServiceInternal.class);
                    mountExternal = mountServiceInternal.getExternalStorageMountMode(uid,
                            app.info.packageName);
                } catch (RemoteException e) {
                    throw e.rethrowAsRuntimeException();
                }

                /*
                 * Add shared application and profile GIDs so applications can share some
                 * resources like shared libraries and access user-wide resources
                 */
                if (ArrayUtils.isEmpty(permGids)) {
                    gids = new int[2];
                } else {
                    gids = new int[permGids.length + 2];
                    System.arraycopy(permGids, 0, gids, 2, permGids.length);
                }
                gids[0] = UserHandle.getSharedAppGid(UserHandle.getAppId(uid));
                gids[1] = UserHandle.getUserGid(UserHandle.getUserId(uid));
            }
            checkTime(startTime, "startProcess: building args");
            if (mFactoryTest != FactoryTest.FACTORY_TEST_OFF) {
                if (mFactoryTest == FactoryTest.FACTORY_TEST_LOW_LEVEL
                        && mTopComponent != null
                        && app.processName.equals(mTopComponent.getPackageName())) {
                    uid = 0;
                }
                if (mFactoryTest == FactoryTest.FACTORY_TEST_HIGH_LEVEL
                        && (app.info.flags&ApplicationInfo.FLAG_FACTORY_TEST) != 0) {
                    uid = 0;
                }
            }
            int debugFlags = 0;
            if ((app.info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                debugFlags |= Zygote.DEBUG_ENABLE_DEBUGGER;
                // Also turn on CheckJNI for debuggable apps. It's quite
                // awkward to turn on otherwise.
                debugFlags |= Zygote.DEBUG_ENABLE_CHECKJNI;
            }
            // Run the app in safe mode if its manifest requests so or the
            // system is booted in safe mode.
            if ((app.info.flags & ApplicationInfo.FLAG_VM_SAFE_MODE) != 0 ||
                mSafeMode == true) {
                debugFlags |= Zygote.DEBUG_ENABLE_SAFEMODE;
            }
            if ("1".equals(SystemProperties.get("debug.checkjni"))) {
                debugFlags |= Zygote.DEBUG_ENABLE_CHECKJNI;
            }
            String genDebugInfoProperty = SystemProperties.get("debug.generate-debug-info");
            if ("true".equals(genDebugInfoProperty)) {
                debugFlags |= Zygote.DEBUG_GENERATE_DEBUG_INFO;
            }
            if ("1".equals(SystemProperties.get("debug.jni.logging"))) {
                debugFlags |= Zygote.DEBUG_ENABLE_JNI_LOGGING;
            }
            if ("1".equals(SystemProperties.get("debug.assert"))) {
                debugFlags |= Zygote.DEBUG_ENABLE_ASSERT;
            }
            if (mNativeDebuggingApp != null && mNativeDebuggingApp.equals(app.processName)) {
                // Enable all debug flags required by the native debugger.
                debugFlags |= Zygote.DEBUG_ALWAYS_JIT;          // Don't interpret anything
                debugFlags |= Zygote.DEBUG_GENERATE_DEBUG_INFO; // Generate debug info
                debugFlags |= Zygote.DEBUG_NATIVE_DEBUGGABLE;   // Disbale optimizations
                mNativeDebuggingApp = null;
            }

            String requiredAbi = (abiOverride != null) ? abiOverride : app.info.primaryCpuAbi;
            if (requiredAbi == null) {
                requiredAbi = Build.SUPPORTED_ABIS[0];
            }

            String instructionSet = null;
            if (app.info.primaryCpuAbi != null) {
                instructionSet = VMRuntime.getInstructionSet(app.info.primaryCpuAbi);
            }

            app.gids = gids;
            app.requiredAbi = requiredAbi;
            app.instructionSet = instructionSet;

            // Start the process.  It will either succeed and return a result containing
            // the PID of the new process, or else throw a RuntimeException.
            boolean isActivityProcess = (entryPoint == null);
            if (entryPoint == null) entryPoint = "android.app.ActivityThread";
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "Start proc: " +
                    app.processName);
            checkTime(startTime, "startProcess: asking zygote to start proc");
            Process.ProcessStartResult startResult = Process.start(entryPoint,
                    app.processName, uid, uid, gids, debugFlags, mountExternal,
                    app.info.targetSdkVersion, app.info.seinfo, requiredAbi, instructionSet,
                    app.info.dataDir, entryPointArgs);
            checkTime(startTime, "startProcess: returned from zygote!");
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);

            mBatteryStatsService.noteProcessStart(app.processName, app.info.uid);
            checkTime(startTime, "startProcess: done updating battery stats");

            EventLog.writeEvent(EventLogTags.AM_PROC_START,
                    UserHandle.getUserId(uid), startResult.pid, uid,
                    app.processName, hostingType,
                    hostingNameStr != null ? hostingNameStr : "");

            try {
                AppGlobals.getPackageManager().logAppProcessStartIfNeeded(app.processName, app.uid,
                        app.info.seinfo, app.info.sourceDir, startResult.pid);
            } catch (RemoteException ex) {
                // Ignore
            }

            if (app.persistent) {
                Watchdog.getInstance().processStarted(app.processName, startResult.pid);
            }

            checkTime(startTime, "startProcess: building log message");
            StringBuilder buf = mStringBuilder;
            buf.setLength(0);
            buf.append("Start proc ");
            buf.append(startResult.pid);
            buf.append(':');
            buf.append(app.processName);
            buf.append('/');
            UserHandle.formatUid(buf, uid);
            if (!isActivityProcess) {
                buf.append(" [");
                buf.append(entryPoint);
                buf.append("]");
            }
            buf.append(" for ");
            buf.append(hostingType);
            if (hostingNameStr != null) {
                buf.append(" ");
                buf.append(hostingNameStr);
            }
            Slog.i(TAG, buf.toString());
            app.setPid(startResult.pid);
            app.usingWrapper = startResult.usingWrapper;
            app.removed = false;
            app.killed = false;
            app.killedByAm = false;
            checkTime(startTime, "startProcess: starting to update pids map");
            ProcessRecord oldApp;
            synchronized (mPidsSelfLocked) {
                oldApp = mPidsSelfLocked.get(startResult.pid);
            }
            // If there is already an app occupying that pid that hasn't been cleaned up
            if (oldApp != null && !app.isolated) {
                // Clean up anything relating to this pid first
                Slog.w(TAG, "Reusing pid " + startResult.pid
                        + " while app is still mapped to it");
                cleanUpApplicationRecordLocked(oldApp, false, false, -1,
                        true /*replacingPid*/);
            }
            synchronized (mPidsSelfLocked) {
                this.mPidsSelfLocked.put(startResult.pid, app);
                if (isActivityProcess) {
                    Message msg = mHandler.obtainMessage(PROC_START_TIMEOUT_MSG);
                    msg.obj = app;
                    mHandler.sendMessageDelayed(msg, startResult.usingWrapper
                            ? PROC_START_TIMEOUT_WITH_WRAPPER : PROC_START_TIMEOUT);
                }
            }
            checkTime(startTime, "startProcess: done updating pids map");
        } catch (RuntimeException e) {
            Slog.e(TAG, "Failure starting process " + app.processName, e);

            // Something went very wrong while trying to start this process; one
            // common case is when the package is frozen due to an active
            // upgrade. To recover, clean up any active bookkeeping related to
            // starting this process. (We already invoked this method once when
            // the package was initially frozen through KILL_APPLICATION_MSG, so
            // it doesn't hurt to use it again.)
            forceStopPackageLocked(app.info.packageName, UserHandle.getAppId(app.uid), false,
                    false, true, false, false, UserHandle.getUserId(app.userId), "start failure");
        }
    }

    void updateUsageStats(ActivityRecord component, boolean resumed) {
        if (DEBUG_SWITCH) Slog.d(TAG_SWITCH,
                "updateUsageStats: comp=" + component + "res=" + resumed);
        final BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
        if (resumed) {
            if (mUsageStatsService != null) {
                mUsageStatsService.reportEvent(component.realActivity, component.userId,
                        UsageEvents.Event.MOVE_TO_FOREGROUND);
            }
            synchronized (stats) {
                stats.noteActivityResumedLocked(component.app.uid);
            }
        } else {
            if (mUsageStatsService != null) {
                mUsageStatsService.reportEvent(component.realActivity, component.userId,
                        UsageEvents.Event.MOVE_TO_BACKGROUND);
            }
            synchronized (stats) {
                stats.noteActivityPausedLocked(component.app.uid);
            }
        }
    }

    Intent getHomeIntent() {
        Intent intent = new Intent(mTopAction, mTopData != null ? Uri.parse(mTopData) : null);
        intent.setComponent(mTopComponent);
        intent.addFlags(Intent.FLAG_DEBUG_TRIAGED_MISSING);
        if (mFactoryTest != FactoryTest.FACTORY_TEST_LOW_LEVEL) {
            intent.addCategory(Intent.CATEGORY_HOME);
        }
        return intent;
    }

    boolean startHomeActivityLocked(int userId, String reason) {
        if (mFactoryTest == FactoryTest.FACTORY_TEST_LOW_LEVEL
                && mTopAction == null) {
            // We are running in factory test mode, but unable to find
            // the factory test app, so just sit around displaying the
            // error message and don't try to start anything.
            return false;
        }
        Intent intent = getHomeIntent();
        ActivityInfo aInfo = resolveActivityInfo(intent, STOCK_PM_FLAGS, userId);
        if (aInfo != null) {
            intent.setComponent(new ComponentName(aInfo.applicationInfo.packageName, aInfo.name));
            // Don't do this if the home app is currently being
            // instrumented.
            aInfo = new ActivityInfo(aInfo);
            aInfo.applicationInfo = getAppInfoForUser(aInfo.applicationInfo, userId);
            ProcessRecord app = getProcessRecordLocked(aInfo.processName,
                    aInfo.applicationInfo.uid, true);
            if (app == null || app.instrumentationClass == null) {
                intent.setFlags(intent.getFlags() | Intent.FLAG_ACTIVITY_NEW_TASK);
                mActivityStarter.startHomeActivityLocked(intent, aInfo, reason);
            }
        } else {
            Slog.wtf(TAG, "No home screen found for " + intent, new Throwable());
        }

        return true;
    }

    private ActivityInfo resolveActivityInfo(Intent intent, int flags, int userId) {
        ActivityInfo ai = null;
        ComponentName comp = intent.getComponent();
        try {
            if (comp != null) {
                // Factory test.
                ai = AppGlobals.getPackageManager().getActivityInfo(comp, flags, userId);
            } else {
                ResolveInfo info = AppGlobals.getPackageManager().resolveIntent(
                        intent,
                        intent.resolveTypeIfNeeded(mContext.getContentResolver()),
                        flags, userId);

                if (info != null) {
                    ai = info.activityInfo;
                }
            }
        } catch (RemoteException e) {
            // ignore
        }

        return ai;
    }

    /**
     * Starts the "new version setup screen" if appropriate.
     */
    void startSetupActivityLocked() {
        // Only do this once per boot.
        if (mCheckedForSetup) {
            return;
        }

        // We will show this screen if the current one is a different
        // version than the last one shown, and we are not running in
        // low-level factory test mode.
        final ContentResolver resolver = mContext.getContentResolver();
        if (mFactoryTest != FactoryTest.FACTORY_TEST_LOW_LEVEL &&
                Settings.Global.getInt(resolver,
                        Settings.Global.DEVICE_PROVISIONED, 0) != 0) {
            mCheckedForSetup = true;

            // See if we should be showing the platform update setup UI.
            final Intent intent = new Intent(Intent.ACTION_UPGRADE_SETUP);
            final List<ResolveInfo> ris = mContext.getPackageManager().queryIntentActivities(intent,
                    PackageManager.MATCH_SYSTEM_ONLY | PackageManager.GET_META_DATA);
            if (!ris.isEmpty()) {
                final ResolveInfo ri = ris.get(0);
                String vers = ri.activityInfo.metaData != null
                        ? ri.activityInfo.metaData.getString(Intent.METADATA_SETUP_VERSION)
                        : null;
                if (vers == null && ri.activityInfo.applicationInfo.metaData != null) {
                    vers = ri.activityInfo.applicationInfo.metaData.getString(
                            Intent.METADATA_SETUP_VERSION);
                }
                String lastVers = Settings.Secure.getString(
                        resolver, Settings.Secure.LAST_SETUP_SHOWN);
                if (vers != null && !vers.equals(lastVers)) {
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setComponent(new ComponentName(
                            ri.activityInfo.packageName, ri.activityInfo.name));
                    mActivityStarter.startActivityLocked(null, intent, null /*ephemeralIntent*/,
                            null, ri.activityInfo, null /*rInfo*/, null, null, null, null, 0, 0, 0,
                            null, 0, 0, 0, null, false, false, null, null, null);
                }
            }
        }
    }

    CompatibilityInfo compatibilityInfoForPackageLocked(ApplicationInfo ai) {
        return mCompatModePackages.compatibilityInfoForPackageLocked(ai);
    }

    void enforceNotIsolatedCaller(String caller) {
        if (UserHandle.isIsolated(Binder.getCallingUid())) {
            throw new SecurityException("Isolated process not allowed to call " + caller);
        }
    }

    void enforceShellRestriction(String restriction, int userHandle) {
        if (Binder.getCallingUid() == Process.SHELL_UID) {
            if (userHandle < 0 || mUserController.hasUserRestriction(restriction, userHandle)) {
                throw new SecurityException("Shell does not have permission to access user "
                        + userHandle);
            }
        }
    }

    @Override
    public int getFrontActivityScreenCompatMode() {
        enforceNotIsolatedCaller("getFrontActivityScreenCompatMode");
        synchronized (this) {
            return mCompatModePackages.getFrontActivityScreenCompatModeLocked();
        }
    }

    @Override
    public void setFrontActivityScreenCompatMode(int mode) {
        enforceCallingPermission(android.Manifest.permission.SET_SCREEN_COMPATIBILITY,
                "setFrontActivityScreenCompatMode");
        synchronized (this) {
            mCompatModePackages.setFrontActivityScreenCompatModeLocked(mode);
        }
    }

    @Override
    public int getPackageScreenCompatMode(String packageName) {
        enforceNotIsolatedCaller("getPackageScreenCompatMode");
        synchronized (this) {
            return mCompatModePackages.getPackageScreenCompatModeLocked(packageName);
        }
    }

    @Override
    public void setPackageScreenCompatMode(String packageName, int mode) {
        enforceCallingPermission(android.Manifest.permission.SET_SCREEN_COMPATIBILITY,
                "setPackageScreenCompatMode");
        synchronized (this) {
            mCompatModePackages.setPackageScreenCompatModeLocked(packageName, mode);
        }
    }

    @Override
    public boolean getPackageAskScreenCompat(String packageName) {
        enforceNotIsolatedCaller("getPackageAskScreenCompat");
        synchronized (this) {
            return mCompatModePackages.getPackageAskCompatModeLocked(packageName);
        }
    }

    @Override
    public void setPackageAskScreenCompat(String packageName, boolean ask) {
        enforceCallingPermission(android.Manifest.permission.SET_SCREEN_COMPATIBILITY,
                "setPackageAskScreenCompat");
        synchronized (this) {
            mCompatModePackages.setPackageAskCompatModeLocked(packageName, ask);
        }
    }

    private boolean hasUsageStatsPermission(String callingPackage) {
        final int mode = mAppOpsService.checkOperation(AppOpsManager.OP_GET_USAGE_STATS,
                Binder.getCallingUid(), callingPackage);
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return checkCallingPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public int getPackageProcessState(String packageName, String callingPackage) {
        if (!hasUsageStatsPermission(callingPackage)) {
            enforceCallingPermission(android.Manifest.permission.GET_PACKAGE_IMPORTANCE,
                    "getPackageProcessState");
        }

        int procState = ActivityManager.PROCESS_STATE_NONEXISTENT;
        synchronized (this) {
            for (int i=mLruProcesses.size()-1; i>=0; i--) {
                final ProcessRecord proc = mLruProcesses.get(i);
                if (procState == ActivityManager.PROCESS_STATE_NONEXISTENT
                        || procState > proc.setProcState) {
                    boolean found = false;
                    for (int j=proc.pkgList.size()-1; j>=0 && !found; j--) {
                        if (proc.pkgList.keyAt(j).equals(packageName)) {
                            procState = proc.setProcState;
                            found = true;
                        }
                    }
                    if (proc.pkgDeps != null && !found) {
                        for (int j=proc.pkgDeps.size()-1; j>=0; j--) {
                            if (proc.pkgDeps.valueAt(j).equals(packageName)) {
                                procState = proc.setProcState;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return procState;
    }

    @Override
    public boolean setProcessMemoryTrimLevel(String process, int userId, int level) {
        synchronized (this) {
            final ProcessRecord app = findProcessLocked(process, userId, "setProcessMemoryTrimLevel");
            if (app == null) {
                return false;
            }
            if (app.trimMemoryLevel < level && app.thread != null &&
                    (level < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                            app.curProcState >= ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND)) {
                try {
                    app.thread.scheduleTrimMemory(level);
                    app.trimMemoryLevel = level;
                    return true;
                } catch (RemoteException e) {
                    // Fallthrough to failure case.
                }
            }
        }
        return false;
    }

    private void dispatchProcessesChanged() {
        int N;
        synchronized (this) {
            N = mPendingProcessChanges.size();
            if (mActiveProcessChanges.length < N) {
                mActiveProcessChanges = new ProcessChangeItem[N];
            }
            mPendingProcessChanges.toArray(mActiveProcessChanges);
            mPendingProcessChanges.clear();
            if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                    "*** Delivering " + N + " process changes");
        }

        int i = mProcessObservers.beginBroadcast();
        while (i > 0) {
            i--;
            final IProcessObserver observer = mProcessObservers.getBroadcastItem(i);
            if (observer != null) {
                try {
                    for (int j=0; j<N; j++) {
                        ProcessChangeItem item = mActiveProcessChanges[j];
                        if ((item.changes&ProcessChangeItem.CHANGE_ACTIVITIES) != 0) {
                            if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                                    "ACTIVITIES CHANGED pid=" + item.pid + " uid="
                                    + item.uid + ": " + item.foregroundActivities);
                            observer.onForegroundActivitiesChanged(item.pid, item.uid,
                                    item.foregroundActivities);
                        }
                        if ((item.changes&ProcessChangeItem.CHANGE_PROCESS_STATE) != 0) {
                            if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                                    "PROCSTATE CHANGED pid=" + item.pid + " uid=" + item.uid
                                    + ": " + item.processState);
                            observer.onProcessStateChanged(item.pid, item.uid, item.processState);
                        }
                    }
                } catch (RemoteException e) {
                }
            }
        }
        mProcessObservers.finishBroadcast();

        synchronized (this) {
            for (int j=0; j<N; j++) {
                mAvailProcessChanges.add(mActiveProcessChanges[j]);
            }
        }
    }

    private void dispatchProcessDied(int pid, int uid) {
        int i = mProcessObservers.beginBroadcast();
        while (i > 0) {
            i--;
            final IProcessObserver observer = mProcessObservers.getBroadcastItem(i);
            if (observer != null) {
                try {
                    observer.onProcessDied(pid, uid);
                } catch (RemoteException e) {
                }
            }
        }
        mProcessObservers.finishBroadcast();
    }

    private void dispatchUidsChanged() {
        int N;
        synchronized (this) {
            N = mPendingUidChanges.size();
            if (mActiveUidChanges.length < N) {
                mActiveUidChanges = new UidRecord.ChangeItem[N];
            }
            for (int i=0; i<N; i++) {
                final UidRecord.ChangeItem change = mPendingUidChanges.get(i);
                mActiveUidChanges[i] = change;
                if (change.uidRecord != null) {
                    change.uidRecord.pendingChange = null;
                    change.uidRecord = null;
                }
            }
            mPendingUidChanges.clear();
            if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                    "*** Delivering " + N + " uid changes");
        }

        if (mLocalPowerManager != null) {
            for (int j=0; j<N; j++) {
                UidRecord.ChangeItem item = mActiveUidChanges[j];
                if (item.change == UidRecord.CHANGE_GONE
                        || item.change == UidRecord.CHANGE_GONE_IDLE) {
                    mLocalPowerManager.uidGone(item.uid);
                } else {
                    mLocalPowerManager.updateUidProcState(item.uid, item.processState);
                }
            }
        }

        int i = mUidObservers.beginBroadcast();
        while (i > 0) {
            i--;
            final IUidObserver observer = mUidObservers.getBroadcastItem(i);
            final int which = (Integer)mUidObservers.getBroadcastCookie(i);
            if (observer != null) {
                try {
                    for (int j=0; j<N; j++) {
                        UidRecord.ChangeItem item = mActiveUidChanges[j];
                        final int change = item.change;
                        UidRecord validateUid = null;
                        if (VALIDATE_UID_STATES && i == 0) {
                            validateUid = mValidateUids.get(item.uid);
                            if (validateUid == null && change != UidRecord.CHANGE_GONE
                                    && change != UidRecord.CHANGE_GONE_IDLE) {
                                validateUid = new UidRecord(item.uid);
                                mValidateUids.put(item.uid, validateUid);
                            }
                        }
                        if (change == UidRecord.CHANGE_IDLE
                                || change == UidRecord.CHANGE_GONE_IDLE) {
                            if ((which & ActivityManager.UID_OBSERVER_IDLE) != 0) {
                                if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                                        "UID idle uid=" + item.uid);
                                observer.onUidIdle(item.uid);
                            }
                            if (VALIDATE_UID_STATES && i == 0) {
                                if (validateUid != null) {
                                    validateUid.idle = true;
                                }
                            }
                        } else if (change == UidRecord.CHANGE_ACTIVE) {
                            if ((which & ActivityManager.UID_OBSERVER_ACTIVE) != 0) {
                                if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                                        "UID active uid=" + item.uid);
                                observer.onUidActive(item.uid);
                            }
                            if (VALIDATE_UID_STATES && i == 0) {
                                validateUid.idle = false;
                            }
                        }
                        if (change == UidRecord.CHANGE_GONE
                                || change == UidRecord.CHANGE_GONE_IDLE) {
                            if ((which & ActivityManager.UID_OBSERVER_GONE) != 0) {
                                if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                                        "UID gone uid=" + item.uid);
                                observer.onUidGone(item.uid);
                            }
                            if (VALIDATE_UID_STATES && i == 0) {
                                if (validateUid != null) {
                                    mValidateUids.remove(item.uid);
                                }
                            }
                        } else {
                            if ((which & ActivityManager.UID_OBSERVER_PROCSTATE) != 0) {
                                if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                                        "UID CHANGED uid=" + item.uid
                                                + ": " + item.processState);
                                observer.onUidStateChanged(item.uid, item.processState);
                            }
                            if (VALIDATE_UID_STATES && i == 0) {
                                validateUid.curProcState = validateUid.setProcState
                                        = item.processState;
                            }
                        }
                    }
                } catch (RemoteException e) {
                }
            }
        }
        mUidObservers.finishBroadcast();

        synchronized (this) {
            for (int j=0; j<N; j++) {
                mAvailUidChanges.add(mActiveUidChanges[j]);
            }
        }
    }

    @Override
    public final int startActivity(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, ProfilerInfo profilerInfo, Bundle bOptions) {
        return startActivityAsUser(caller, callingPackage, intent, resolvedType, resultTo,
                resultWho, requestCode, startFlags, profilerInfo, bOptions,
                UserHandle.getCallingUserId());
    }

    final int startActivity(Intent intent, ActivityStackSupervisor.ActivityContainer container) {
        enforceNotIsolatedCaller("ActivityContainer.startActivity");
        final int userId = mUserController.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), mStackSupervisor.mCurrentUser, false,
                ActivityManagerService.ALLOW_FULL_ONLY, "ActivityContainer", null);

        // TODO: Switch to user app stacks here.
        String mimeType = intent.getType();
        final Uri data = intent.getData();
        if (mimeType == null && data != null && "content".equals(data.getScheme())) {
            mimeType = getProviderMimeType(data, userId);
        }
        container.checkEmbeddedAllowedInner(userId, intent, mimeType);

        intent.addFlags(FORCE_NEW_TASK_FLAGS);
        return mActivityStarter.startActivityMayWait(null, -1, null, intent, mimeType, null, null, null,
                null, 0, 0, null, null, null, null, false, userId, container, null);
    }

    @Override
    public final int startActivityAsUser(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId) {
        enforceNotIsolatedCaller("startActivity");
        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, false, ALLOW_FULL_ONLY, "startActivity", null);
        // TODO: Switch to user app stacks here.
        return mActivityStarter.startActivityMayWait(caller, -1, callingPackage, intent,
                resolvedType, null, null, resultTo, resultWho, requestCode, startFlags,
                profilerInfo, null, null, bOptions, false, userId, null, null);
    }

    @Override
    public final int startActivityAsCaller(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, boolean ignoreTargetSecurity,
            int userId) {

        // This is very dangerous -- it allows you to perform a start activity (including
        // permission grants) as any app that may launch one of your own activities.  So
        // we will only allow this to be done from activities that are part of the core framework,
        // and then only when they are running as the system.
        final ActivityRecord sourceRecord;
        final int targetUid;
        final String targetPackage;
        synchronized (this) {
            if (resultTo == null) {
                throw new SecurityException("Must be called from an activity");
            }
            sourceRecord = mStackSupervisor.isInAnyStackLocked(resultTo);
            if (sourceRecord == null) {
                throw new SecurityException("Called with bad activity token: " + resultTo);
            }
            if (!sourceRecord.info.packageName.equals("android")) {
                throw new SecurityException(
                        "Must be called from an activity that is declared in the android package");
            }
            if (sourceRecord.app == null) {
                throw new SecurityException("Called without a process attached to activity");
            }
            if (UserHandle.getAppId(sourceRecord.app.uid) != Process.SYSTEM_UID) {
                // This is still okay, as long as this activity is running under the
                // uid of the original calling activity.
                if (sourceRecord.app.uid != sourceRecord.launchedFromUid) {
                    throw new SecurityException(
                            "Calling activity in uid " + sourceRecord.app.uid
                                    + " must be system uid or original calling uid "
                                    + sourceRecord.launchedFromUid);
                }
            }
            if (ignoreTargetSecurity) {
                if (intent.getComponent() == null) {
                    throw new SecurityException(
                            "Component must be specified with ignoreTargetSecurity");
                }
                if (intent.getSelector() != null) {
                    throw new SecurityException(
                            "Selector not allowed with ignoreTargetSecurity");
                }
            }
            targetUid = sourceRecord.launchedFromUid;
            targetPackage = sourceRecord.launchedFromPackage;
        }

        if (userId == UserHandle.USER_NULL) {
            userId = UserHandle.getUserId(sourceRecord.app.uid);
        }

        // TODO: Switch to user app stacks here.
        try {
            int ret = mActivityStarter.startActivityMayWait(null, targetUid, targetPackage, intent,
                    resolvedType, null, null, resultTo, resultWho, requestCode, startFlags, null,
                    null, null, bOptions, ignoreTargetSecurity, userId, null, null);
            return ret;
        } catch (SecurityException e) {
            // XXX need to figure out how to propagate to original app.
            // A SecurityException here is generally actually a fault of the original
            // calling activity (such as a fairly granting permissions), so propagate it
            // back to them.
            /*
            StringBuilder msg = new StringBuilder();
            msg.append("While launching");
            msg.append(intent.toString());
            msg.append(": ");
            msg.append(e.getMessage());
            */
            throw e;
        }
    }

    @Override
    public final WaitResult startActivityAndWait(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, ProfilerInfo profilerInfo, Bundle bOptions, int userId) {
        enforceNotIsolatedCaller("startActivityAndWait");
        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, false, ALLOW_FULL_ONLY, "startActivityAndWait", null);
        WaitResult res = new WaitResult();
        // TODO: Switch to user app stacks here.
        mActivityStarter.startActivityMayWait(caller, -1, callingPackage, intent, resolvedType,
                null, null, resultTo, resultWho, requestCode, startFlags, profilerInfo, res, null,
                bOptions, false, userId, null, null);
        return res;
    }

    @Override
    public final int startActivityWithConfig(IApplicationThread caller, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode,
            int startFlags, Configuration config, Bundle bOptions, int userId) {
        enforceNotIsolatedCaller("startActivityWithConfig");
        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, false, ALLOW_FULL_ONLY, "startActivityWithConfig", null);
        // TODO: Switch to user app stacks here.
        int ret = mActivityStarter.startActivityMayWait(caller, -1, callingPackage, intent,
                resolvedType, null, null, resultTo, resultWho, requestCode, startFlags,
                null, null, config, bOptions, false, userId, null, null);
        return ret;
    }

    @Override
    public int startActivityIntentSender(IApplicationThread caller, IntentSender intent,
            Intent fillInIntent, String resolvedType, IBinder resultTo, String resultWho,
            int requestCode, int flagsMask, int flagsValues, Bundle bOptions)
            throws TransactionTooLargeException {
        enforceNotIsolatedCaller("startActivityIntentSender");
        // Refuse possible leaked file descriptors
        if (fillInIntent != null && fillInIntent.hasFileDescriptors()) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        IIntentSender sender = intent.getTarget();
        if (!(sender instanceof PendingIntentRecord)) {
            throw new IllegalArgumentException("Bad PendingIntent object");
        }

        PendingIntentRecord pir = (PendingIntentRecord)sender;

        synchronized (this) {
            // If this is coming from the currently resumed activity, it is
            // effectively saying that app switches are allowed at this point.
            final ActivityStack stack = getFocusedStack();
            if (stack.mResumedActivity != null &&
                    stack.mResumedActivity.info.applicationInfo.uid == Binder.getCallingUid()) {
                mAppSwitchesAllowedTime = 0;
            }
        }
        int ret = pir.sendInner(0, fillInIntent, resolvedType, null, null,
                resultTo, resultWho, requestCode, flagsMask, flagsValues, bOptions, null);
        return ret;
    }

    @Override
    public int startVoiceActivity(String callingPackage, int callingPid, int callingUid,
            Intent intent, String resolvedType, IVoiceInteractionSession session,
            IVoiceInteractor interactor, int startFlags, ProfilerInfo profilerInfo,
            Bundle bOptions, int userId) {
        if (checkCallingPermission(Manifest.permission.BIND_VOICE_INTERACTION)
                != PackageManager.PERMISSION_GRANTED) {
            String msg = "Permission Denial: startVoiceActivity() from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " requires " + android.Manifest.permission.BIND_VOICE_INTERACTION;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        if (session == null || interactor == null) {
            throw new NullPointerException("null session or interactor");
        }
        userId = mUserController.handleIncomingUser(callingPid, callingUid, userId, false,
                ALLOW_FULL_ONLY, "startVoiceActivity", null);
        // TODO: Switch to user app stacks here.
        return mActivityStarter.startActivityMayWait(null, callingUid, callingPackage, intent,
                resolvedType, session, interactor, null, null, 0, startFlags, profilerInfo, null,
                null, bOptions, false, userId, null, null);
    }

    @Override
    public void startLocalVoiceInteraction(IBinder callingActivity, Bundle options)
            throws RemoteException {
        Slog.i(TAG, "Activity tried to startVoiceInteraction");
        synchronized (this) {
            ActivityRecord activity = getFocusedStack().topActivity();
            if (ActivityRecord.forTokenLocked(callingActivity) != activity) {
                throw new SecurityException("Only focused activity can call startVoiceInteraction");
            }
            if (mRunningVoice != null || activity.task.voiceSession != null
                    || activity.voiceSession != null) {
                Slog.w(TAG, "Already in a voice interaction, cannot start new voice interaction");
                return;
            }
            if (activity.pendingVoiceInteractionStart) {
                Slog.w(TAG, "Pending start of voice interaction already.");
                return;
            }
            activity.pendingVoiceInteractionStart = true;
        }
        LocalServices.getService(VoiceInteractionManagerInternal.class)
                .startLocalVoiceInteraction(callingActivity, options);
    }

    @Override
    public void stopLocalVoiceInteraction(IBinder callingActivity) throws RemoteException {
        LocalServices.getService(VoiceInteractionManagerInternal.class)
                .stopLocalVoiceInteraction(callingActivity);
    }

    @Override
    public boolean supportsLocalVoiceInteraction() throws RemoteException {
        return LocalServices.getService(VoiceInteractionManagerInternal.class)
                .supportsLocalVoiceInteraction();
    }

    void onLocalVoiceInteractionStartedLocked(IBinder activity,
            IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor) {
        ActivityRecord activityToCallback = ActivityRecord.forTokenLocked(activity);
        if (activityToCallback == null) return;
        activityToCallback.setVoiceSessionLocked(voiceSession);

        // Inform the activity
        try {
            activityToCallback.app.thread.scheduleLocalVoiceInteractionStarted(activity,
                    voiceInteractor);
            long token = Binder.clearCallingIdentity();
            try {
                startRunningVoiceLocked(voiceSession, activityToCallback.appInfo.uid);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            // TODO: VI Should we cache the activity so that it's easier to find later
            // rather than scan through all the stacks and activities?
        } catch (RemoteException re) {
            activityToCallback.clearVoiceSessionLocked();
            // TODO: VI Should this terminate the voice session?
        }
    }

    @Override
    public void setVoiceKeepAwake(IVoiceInteractionSession session, boolean keepAwake) {
        synchronized (this) {
            if (mRunningVoice != null && mRunningVoice.asBinder() == session.asBinder()) {
                if (keepAwake) {
                    mVoiceWakeLock.acquire();
                } else {
                    mVoiceWakeLock.release();
                }
            }
        }
    }

    @Override
    public boolean startNextMatchingActivity(IBinder callingActivity,
            Intent intent, Bundle bOptions) {
        // Refuse possible leaked file descriptors
        if (intent != null && intent.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }
        ActivityOptions options = ActivityOptions.fromBundle(bOptions);

        synchronized (this) {
            final ActivityRecord r = ActivityRecord.isInStackLocked(callingActivity);
            if (r == null) {
                ActivityOptions.abort(options);
                return false;
            }
            if (r.app == null || r.app.thread == null) {
                // The caller is not running...  d'oh!
                ActivityOptions.abort(options);
                return false;
            }
            intent = new Intent(intent);
            // The caller is not allowed to change the data.
            intent.setDataAndType(r.intent.getData(), r.intent.getType());
            // And we are resetting to find the next component...
            intent.setComponent(null);

            final boolean debug = ((intent.getFlags() & Intent.FLAG_DEBUG_LOG_RESOLUTION) != 0);

            ActivityInfo aInfo = null;
            try {
                List<ResolveInfo> resolves =
                    AppGlobals.getPackageManager().queryIntentActivities(
                            intent, r.resolvedType,
                            PackageManager.MATCH_DEFAULT_ONLY | STOCK_PM_FLAGS,
                            UserHandle.getCallingUserId()).getList();

                // Look for the original activity in the list...
                final int N = resolves != null ? resolves.size() : 0;
                for (int i=0; i<N; i++) {
                    ResolveInfo rInfo = resolves.get(i);
                    if (rInfo.activityInfo.packageName.equals(r.packageName)
                            && rInfo.activityInfo.name.equals(r.info.name)) {
                        // We found the current one...  the next matching is
                        // after it.
                        i++;
                        if (i<N) {
                            aInfo = resolves.get(i).activityInfo;
                        }
                        if (debug) {
                            Slog.v(TAG, "Next matching activity: found current " + r.packageName
                                    + "/" + r.info.name);
                            Slog.v(TAG, "Next matching activity: next is " + ((aInfo == null)
                                    ? "null" : aInfo.packageName + "/" + aInfo.name));
                        }
                        break;
                    }
                }
            } catch (RemoteException e) {
            }

            if (aInfo == null) {
                // Nobody who is next!
                ActivityOptions.abort(options);
                if (debug) Slog.d(TAG, "Next matching activity: nothing found");
                return false;
            }

            intent.setComponent(new ComponentName(
                    aInfo.applicationInfo.packageName, aInfo.name));
            intent.setFlags(intent.getFlags()&~(
                    Intent.FLAG_ACTIVITY_FORWARD_RESULT|
                    Intent.FLAG_ACTIVITY_CLEAR_TOP|
                    Intent.FLAG_ACTIVITY_MULTIPLE_TASK|
                    Intent.FLAG_ACTIVITY_NEW_TASK));

            // Okay now we need to start the new activity, replacing the
            // currently running activity.  This is a little tricky because
            // we want to start the new one as if the current one is finished,
            // but not finish the current one first so that there is no flicker.
            // And thus...
            final boolean wasFinishing = r.finishing;
            r.finishing = true;

            // Propagate reply information over to the new activity.
            final ActivityRecord resultTo = r.resultTo;
            final String resultWho = r.resultWho;
            final int requestCode = r.requestCode;
            r.resultTo = null;
            if (resultTo != null) {
                resultTo.removeResultsLocked(r, resultWho, requestCode);
            }

            final long origId = Binder.clearCallingIdentity();
            int res = mActivityStarter.startActivityLocked(r.app.thread, intent,
                    null /*ephemeralIntent*/, r.resolvedType, aInfo, null /*rInfo*/, null,
                    null, resultTo != null ? resultTo.appToken : null, resultWho, requestCode, -1,
                    r.launchedFromUid, r.launchedFromPackage, -1, r.launchedFromUid, 0, options,
                    false, false, null, null, null);
            Binder.restoreCallingIdentity(origId);

            r.finishing = wasFinishing;
            if (res != ActivityManager.START_SUCCESS) {
                return false;
            }
            return true;
        }
    }

    @Override
    public final int startActivityFromRecents(int taskId, Bundle bOptions) {
        if (checkCallingPermission(START_TASKS_FROM_RECENTS) != PackageManager.PERMISSION_GRANTED) {
            String msg = "Permission Denial: startActivityFromRecents called without " +
                    START_TASKS_FROM_RECENTS;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                return mStackSupervisor.startActivityFromRecentsInner(taskId, bOptions);
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    final int startActivityInPackage(int uid, String callingPackage,
            Intent intent, String resolvedType, IBinder resultTo,
            String resultWho, int requestCode, int startFlags, Bundle bOptions, int userId,
            IActivityContainer container, TaskRecord inTask) {

        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, false, ALLOW_FULL_ONLY, "startActivityInPackage", null);

        // TODO: Switch to user app stacks here.
        int ret = mActivityStarter.startActivityMayWait(null, uid, callingPackage, intent,
                resolvedType, null, null, resultTo, resultWho, requestCode, startFlags,
                null, null, null, bOptions, false, userId, container, inTask);
        return ret;
    }

    @Override
    public final int startActivities(IApplicationThread caller, String callingPackage,
            Intent[] intents, String[] resolvedTypes, IBinder resultTo, Bundle bOptions,
            int userId) {
        enforceNotIsolatedCaller("startActivities");
        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, false, ALLOW_FULL_ONLY, "startActivity", null);
        // TODO: Switch to user app stacks here.
        int ret = mActivityStarter.startActivities(caller, -1, callingPackage, intents,
                resolvedTypes, resultTo, bOptions, userId);
        return ret;
    }

    final int startActivitiesInPackage(int uid, String callingPackage,
            Intent[] intents, String[] resolvedTypes, IBinder resultTo,
            Bundle bOptions, int userId) {

        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, false, ALLOW_FULL_ONLY, "startActivityInPackage", null);
        // TODO: Switch to user app stacks here.
        int ret = mActivityStarter.startActivities(null, uid, callingPackage, intents, resolvedTypes,
                resultTo, bOptions, userId);
        return ret;
    }

    @Override
    public void reportActivityFullyDrawn(IBinder token) {
        synchronized (this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                return;
            }
            r.reportFullyDrawnLocked();
        }
    }

    @Override
    public void setRequestedOrientation(IBinder token, int requestedOrientation) {
        synchronized (this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                return;
            }
            TaskRecord task = r.task;
            if (task != null && (!task.mFullscreen || !task.stack.mFullscreen)) {
                // Fixed screen orientation isn't supported when activities aren't in full screen
                // mode.
                return;
            }
            final long origId = Binder.clearCallingIdentity();
            mWindowManager.setAppOrientation(r.appToken, requestedOrientation);
            Configuration config = mWindowManager.updateOrientationFromAppTokens(
                    mConfiguration, r.mayFreezeScreenLocked(r.app) ? r.appToken : null);
            if (config != null) {
                r.frozenBeforeDestroy = true;
                if (!updateConfigurationLocked(config, r, false)) {
                    mStackSupervisor.resumeFocusedStackTopActivityLocked();
                }
            }
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public int getRequestedOrientation(IBinder token) {
        synchronized (this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
            }
            return mWindowManager.getAppOrientation(r.appToken);
        }
    }

    /**
     * This is the internal entry point for handling Activity.finish().
     *
     * @param token The Binder token referencing the Activity we want to finish.
     * @param resultCode Result code, if any, from this Activity.
     * @param resultData Result data (Intent), if any, from this Activity.
     * @param finishTask Whether to finish the task associated with this Activity.
     *
     * @return Returns true if the activity successfully finished, or false if it is still running.
     */
    @Override
    public final boolean finishActivity(IBinder token, int resultCode, Intent resultData,
            int finishTask) {
        // Refuse possible leaked file descriptors
        if (resultData != null && resultData.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        synchronized(this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                return true;
            }
            // Keep track of the root activity of the task before we finish it
            TaskRecord tr = r.task;
            ActivityRecord rootR = tr.getRootActivity();
            if (rootR == null) {
                Slog.w(TAG, "Finishing task with all activities already finished");
            }
            // Do not allow task to finish if last task in lockTask mode. Launchable priv-apps can
            // finish.
            if (tr.mLockTaskAuth != LOCK_TASK_AUTH_LAUNCHABLE_PRIV && rootR == r &&
                    mStackSupervisor.isLastLockedTask(tr)) {
                Slog.i(TAG, "Not finishing task in lock task mode");
                mStackSupervisor.showLockTaskToast();
                return false;
            }
            if (mController != null) {
                // Find the first activity that is not finishing.
                ActivityRecord next = r.task.stack.topRunningActivityLocked(token, 0);
                if (next != null) {
                    // ask watcher if this is allowed
                    boolean resumeOK = true;
                    try {
                        resumeOK = mController.activityResuming(next.packageName);
                    } catch (RemoteException e) {
                        mController = null;
                        Watchdog.getInstance().setActivityController(null);
                    }

                    if (!resumeOK) {
                        Slog.i(TAG, "Not finishing activity because controller resumed");
                        return false;
                    }
                }
            }
            final long origId = Binder.clearCallingIdentity();
            try {
                boolean res;
                final boolean finishWithRootActivity =
                        finishTask == Activity.FINISH_TASK_WITH_ROOT_ACTIVITY;
                if (finishTask == Activity.FINISH_TASK_WITH_ACTIVITY
                        || (finishWithRootActivity && r == rootR)) {
                    // If requested, remove the task that is associated to this activity only if it
                    // was the root activity in the task. The result code and data is ignored
                    // because we don't support returning them across task boundaries. Also, to
                    // keep backwards compatibility we remove the task from recents when finishing
                    // task with root activity.
                    res = removeTaskByIdLocked(tr.taskId, false, finishWithRootActivity);
                    if (!res) {
                        Slog.i(TAG, "Removing task failed to finish activity");
                    }
                } else {
                    res = tr.stack.requestFinishActivityLocked(token, resultCode,
                            resultData, "app-request", true);
                    if (!res) {
                        Slog.i(TAG, "Failed to finish by app-request");
                    }
                }
                return res;
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    @Override
    public final void finishHeavyWeightApp() {
        if (checkCallingPermission(android.Manifest.permission.FORCE_STOP_PACKAGES)
                != PackageManager.PERMISSION_GRANTED) {
            String msg = "Permission Denial: finishHeavyWeightApp() from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " requires " + android.Manifest.permission.FORCE_STOP_PACKAGES;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }

        synchronized(this) {
            if (mHeavyWeightProcess == null) {
                return;
            }

            ArrayList<ActivityRecord> activities = new ArrayList<>(mHeavyWeightProcess.activities);
            for (int i = 0; i < activities.size(); i++) {
                ActivityRecord r = activities.get(i);
                if (!r.finishing && r.isInStackLocked()) {
                    r.task.stack.finishActivityLocked(r, Activity.RESULT_CANCELED,
                            null, "finish-heavy", true);
                }
            }

            mHandler.sendMessage(mHandler.obtainMessage(CANCEL_HEAVY_NOTIFICATION_MSG,
                    mHeavyWeightProcess.userId, 0));
            mHeavyWeightProcess = null;
        }
    }

    @Override
    public void crashApplication(int uid, int initialPid, String packageName,
            String message) {
        if (checkCallingPermission(android.Manifest.permission.FORCE_STOP_PACKAGES)
                != PackageManager.PERMISSION_GRANTED) {
            String msg = "Permission Denial: crashApplication() from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " requires " + android.Manifest.permission.FORCE_STOP_PACKAGES;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }

        synchronized(this) {
            mAppErrors.scheduleAppCrashLocked(uid, initialPid, packageName, message);
        }
    }

    @Override
    public final void finishSubActivity(IBinder token, String resultWho,
            int requestCode) {
        synchronized(this) {
            final long origId = Binder.clearCallingIdentity();
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r != null) {
                r.task.stack.finishSubActivityLocked(r, resultWho, requestCode);
            }
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public boolean finishActivityAffinity(IBinder token) {
        synchronized(this) {
            final long origId = Binder.clearCallingIdentity();
            try {
                ActivityRecord r = ActivityRecord.isInStackLocked(token);
                if (r == null) {
                    return false;
                }

                // Do not allow task to finish if last task in lockTask mode. Launchable priv-apps
                // can finish.
                final TaskRecord task = r.task;
                if (task.mLockTaskAuth != LOCK_TASK_AUTH_LAUNCHABLE_PRIV &&
                        mStackSupervisor.isLastLockedTask(task) && task.getRootActivity() == r) {
                    mStackSupervisor.showLockTaskToast();
                    return false;
                }
                return task.stack.finishActivityAffinityLocked(r);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    @Override
    public void finishVoiceTask(IVoiceInteractionSession session) {
        synchronized (this) {
            final long origId = Binder.clearCallingIdentity();
            try {
                // TODO: VI Consider treating local voice interactions and voice tasks
                // differently here
                mStackSupervisor.finishVoiceTask(session);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }

    }

    @Override
    public boolean releaseActivityInstance(IBinder token) {
        synchronized(this) {
            final long origId = Binder.clearCallingIdentity();
            try {
                ActivityRecord r = ActivityRecord.isInStackLocked(token);
                if (r == null) {
                    return false;
                }
                return r.task.stack.safelyDestroyActivityLocked(r, "app-req");
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    @Override
    public void releaseSomeActivities(IApplicationThread appInt) {
        synchronized(this) {
            final long origId = Binder.clearCallingIdentity();
            try {
                ProcessRecord app = getRecordForAppLocked(appInt);
                mStackSupervisor.releaseSomeActivitiesLocked(app, "low-mem");
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    @Override
    public boolean willActivityBeVisible(IBinder token) {
        synchronized(this) {
            ActivityStack stack = ActivityRecord.getStackLocked(token);
            if (stack != null) {
                return stack.willActivityBeVisibleLocked(token);
            }
            return false;
        }
    }

    @Override
    public void overridePendingTransition(IBinder token, String packageName,
            int enterAnim, int exitAnim) {
        synchronized(this) {
            ActivityRecord self = ActivityRecord.isInStackLocked(token);
            if (self == null) {
                return;
            }

            final long origId = Binder.clearCallingIdentity();

            if (self.state == ActivityState.RESUMED
                    || self.state == ActivityState.PAUSING) {
                mWindowManager.overridePendingAppTransition(packageName,
                        enterAnim, exitAnim, null);
            }

            Binder.restoreCallingIdentity(origId);
        }
    }

    /**
     * Main function for removing an existing process from the activity manager
     * as a result of that process going away.  Clears out all connections
     * to the process.
     */
    private final void handleAppDiedLocked(ProcessRecord app,
            boolean restarting, boolean allowRestart) {
        int pid = app.pid;
        boolean kept = cleanUpApplicationRecordLocked(app, restarting, allowRestart, -1,
                false /*replacingPid*/);
        if (!kept && !restarting) {
            removeLruProcessLocked(app);
            if (pid > 0) {
                ProcessList.remove(pid);
            }
        }

        if (mProfileProc == app) {
            clearProfilerLocked();
        }

        // Remove this application's activities from active lists.
        boolean hasVisibleActivities = mStackSupervisor.handleAppDiedLocked(app);

        app.activities.clear();

        if (app.instrumentationClass != null) {
            Slog.w(TAG, "Crash of app " + app.processName
                  + " running instrumentation " + app.instrumentationClass);
            Bundle info = new Bundle();
            info.putString("shortMsg", "Process crashed.");
            finishInstrumentationLocked(app, Activity.RESULT_CANCELED, info);
        }

        if (!restarting && hasVisibleActivities
                && !mStackSupervisor.resumeFocusedStackTopActivityLocked()) {
            // If there was nothing to resume, and we are not already restarting this process, but
            // there is a visible activity that is hosted by the process...  then make sure all
            // visible activities are running, taking care of restarting this process.
            mStackSupervisor.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
        }
    }

    private final int getLRURecordIndexForAppLocked(IApplicationThread thread) {
        IBinder threadBinder = thread.asBinder();
        // Find the application record.
        for (int i=mLruProcesses.size()-1; i>=0; i--) {
            ProcessRecord rec = mLruProcesses.get(i);
            if (rec.thread != null && rec.thread.asBinder() == threadBinder) {
                return i;
            }
        }
        return -1;
    }

    final ProcessRecord getRecordForAppLocked(
            IApplicationThread thread) {
        if (thread == null) {
            return null;
        }

        int appIndex = getLRURecordIndexForAppLocked(thread);
        return appIndex >= 0 ? mLruProcesses.get(appIndex) : null;
    }

    final void doLowMemReportIfNeededLocked(ProcessRecord dyingProc) {
        // If there are no longer any background processes running,
        // and the app that died was not running instrumentation,
        // then tell everyone we are now low on memory.
        boolean haveBg = false;
        for (int i=mLruProcesses.size()-1; i>=0; i--) {
            ProcessRecord rec = mLruProcesses.get(i);
            if (rec.thread != null
                    && rec.setProcState >= ActivityManager.PROCESS_STATE_CACHED_ACTIVITY) {
                haveBg = true;
                break;
            }
        }

        if (!haveBg) {
            boolean doReport = "1".equals(SystemProperties.get(SYSTEM_DEBUGGABLE, "0"));
            if (doReport) {
                long now = SystemClock.uptimeMillis();
                if (now < (mLastMemUsageReportTime+5*60*1000)) {
                    doReport = false;
                } else {
                    mLastMemUsageReportTime = now;
                }
            }
            final ArrayList<ProcessMemInfo> memInfos
                    = doReport ? new ArrayList<ProcessMemInfo>(mLruProcesses.size()) : null;
            EventLog.writeEvent(EventLogTags.AM_LOW_MEMORY, mLruProcesses.size());
            long now = SystemClock.uptimeMillis();
            for (int i=mLruProcesses.size()-1; i>=0; i--) {
                ProcessRecord rec = mLruProcesses.get(i);
                if (rec == dyingProc || rec.thread == null) {
                    continue;
                }
                if (doReport) {
                    memInfos.add(new ProcessMemInfo(rec.processName, rec.pid, rec.setAdj,
                            rec.setProcState, rec.adjType, rec.makeAdjReason()));
                }
                if ((rec.lastLowMemory+GC_MIN_INTERVAL) <= now) {
                    // The low memory report is overriding any current
                    // state for a GC request.  Make sure to do
                    // heavy/important/visible/foreground processes first.
                    if (rec.setAdj <= ProcessList.HEAVY_WEIGHT_APP_ADJ) {
                        rec.lastRequestedGc = 0;
                    } else {
                        rec.lastRequestedGc = rec.lastLowMemory;
                    }
                    rec.reportLowMemory = true;
                    rec.lastLowMemory = now;
                    mProcessesToGc.remove(rec);
                    addProcessToGcListLocked(rec);
                }
            }
            if (doReport) {
                Message msg = mHandler.obtainMessage(REPORT_MEM_USAGE_MSG, memInfos);
                mHandler.sendMessage(msg);
            }
            scheduleAppGcsLocked();
        }
    }

    final void appDiedLocked(ProcessRecord app) {
       appDiedLocked(app, app.pid, app.thread, false);
    }

    final void appDiedLocked(ProcessRecord app, int pid, IApplicationThread thread,
            boolean fromBinderDied) {
        // First check if this ProcessRecord is actually active for the pid.
        synchronized (mPidsSelfLocked) {
            ProcessRecord curProc = mPidsSelfLocked.get(pid);
            if (curProc != app) {
                Slog.w(TAG, "Spurious death for " + app + ", curProc for " + pid + ": " + curProc);
                return;
            }
        }

        BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
        synchronized (stats) {
            stats.noteProcessDiedLocked(app.info.uid, pid);
        }

        if (!app.killed) {
            if (!fromBinderDied) {
                Process.killProcessQuiet(pid);
            }
            killProcessGroup(app.uid, pid);
            app.killed = true;
        }

        // Clean up already done if the process has been re-started.
        if (app.pid == pid && app.thread != null &&
                app.thread.asBinder() == thread.asBinder()) {
            boolean doLowMem = app.instrumentationClass == null;
            boolean doOomAdj = doLowMem;
            if (!app.killedByAm) {
                Slog.i(TAG, "Process " + app.processName + " (pid " + pid
                        + ") has died");
                mAllowLowerMemLevel = true;
            } else {
                // Note that we always want to do oom adj to update our state with the
                // new number of procs.
                mAllowLowerMemLevel = false;
                doLowMem = false;
            }
            EventLog.writeEvent(EventLogTags.AM_PROC_DIED, app.userId, app.pid, app.processName);
            if (DEBUG_CLEANUP) Slog.v(TAG_CLEANUP,
                "Dying app: " + app + ", pid: " + pid + ", thread: " + thread.asBinder());
            handleAppDiedLocked(app, false, true);

            if (doOomAdj) {
                updateOomAdjLocked();
            }
            if (doLowMem) {
                doLowMemReportIfNeededLocked(app);
            }
        } else if (app.pid != pid) {
            // A new process has already been started.
            Slog.i(TAG, "Process " + app.processName + " (pid " + pid
                    + ") has died and restarted (pid " + app.pid + ").");
            EventLog.writeEvent(EventLogTags.AM_PROC_DIED, app.userId, app.pid, app.processName);
        } else if (DEBUG_PROCESSES) {
            Slog.d(TAG_PROCESSES, "Received spurious death notification for thread "
                    + thread.asBinder());
        }
    }

    /**
     * If a stack trace dump file is configured, dump process stack traces.
     * @param clearTraces causes the dump file to be erased prior to the new
     *    traces being written, if true; when false, the new traces will be
     *    appended to any existing file content.
     * @param firstPids of dalvik VM processes to dump stack traces for first
     * @param lastPids of dalvik VM processes to dump stack traces for last
     * @param nativeProcs optional list of native process names to dump stack crawls
     * @return file containing stack traces, or null if no dump file is configured
     */
    public static File dumpStackTraces(boolean clearTraces, ArrayList<Integer> firstPids,
            ProcessCpuTracker processCpuTracker, SparseArray<Boolean> lastPids, String[] nativeProcs) {
        String tracesPath = SystemProperties.get("dalvik.vm.stack-trace-file", null);
        if (tracesPath == null || tracesPath.length() == 0) {
            return null;
        }

        File tracesFile = new File(tracesPath);
        try {
            if (clearTraces && tracesFile.exists()) tracesFile.delete();
            tracesFile.createNewFile();
            FileUtils.setPermissions(tracesFile.getPath(), 0666, -1, -1); // -rw-rw-rw-
        } catch (IOException e) {
            Slog.w(TAG, "Unable to prepare ANR traces file: " + tracesPath, e);
            return null;
        }

        dumpStackTraces(tracesPath, firstPids, processCpuTracker, lastPids, nativeProcs);
        return tracesFile;
    }

    private static void dumpStackTraces(String tracesPath, ArrayList<Integer> firstPids,
            ProcessCpuTracker processCpuTracker, SparseArray<Boolean> lastPids, String[] nativeProcs) {
        // Use a FileObserver to detect when traces finish writing.
        // The order of traces is considered important to maintain for legibility.
        FileObserver observer = new FileObserver(tracesPath, FileObserver.CLOSE_WRITE) {
            @Override
            public synchronized void onEvent(int event, String path) { notify(); }
        };

        try {
            observer.startWatching();

            // First collect all of the stacks of the most important pids.
            if (firstPids != null) {
                try {
                    int num = firstPids.size();
                    for (int i = 0; i < num; i++) {
                        synchronized (observer) {
                            if (DEBUG_ANR) Slog.d(TAG, "Collecting stacks for pid "
                                    + firstPids.get(i));
                            final long sime = SystemClock.elapsedRealtime();
                            Process.sendSignal(firstPids.get(i), Process.SIGNAL_QUIT);
                            observer.wait(1000);  // Wait for write-close, give up after 1 sec
                            if (DEBUG_ANR) Slog.d(TAG, "Done with pid " + firstPids.get(i)
                                    + " in " + (SystemClock.elapsedRealtime()-sime) + "ms");
                        }
                    }
                } catch (InterruptedException e) {
                    Slog.wtf(TAG, e);
                }
            }

            // Next collect the stacks of the native pids
            if (nativeProcs != null) {
                int[] pids = Process.getPidsForCommands(nativeProcs);
                if (pids != null) {
                    for (int pid : pids) {
                        if (DEBUG_ANR) Slog.d(TAG, "Collecting stacks for native pid " + pid);
                        final long sime = SystemClock.elapsedRealtime();
                        Debug.dumpNativeBacktraceToFile(pid, tracesPath);
                        if (DEBUG_ANR) Slog.d(TAG, "Done with native pid " + pid
                                + " in " + (SystemClock.elapsedRealtime()-sime) + "ms");
                    }
                }
            }

            // Lastly, measure CPU usage.
            if (processCpuTracker != null) {
                processCpuTracker.init();
                System.gc();
                processCpuTracker.update();
                try {
                    synchronized (processCpuTracker) {
                        processCpuTracker.wait(500); // measure over 1/2 second.
                    }
                } catch (InterruptedException e) {
                }
                processCpuTracker.update();

                // We'll take the stack crawls of just the top apps using CPU.
                final int N = processCpuTracker.countWorkingStats();
                int numProcs = 0;
                for (int i=0; i<N && numProcs<5; i++) {
                    ProcessCpuTracker.Stats stats = processCpuTracker.getWorkingStats(i);
                    if (lastPids.indexOfKey(stats.pid) >= 0) {
                        numProcs++;
                        try {
                            synchronized (observer) {
                                if (DEBUG_ANR) Slog.d(TAG, "Collecting stacks for extra pid "
                                        + stats.pid);
                                final long stime = SystemClock.elapsedRealtime();
                                Process.sendSignal(stats.pid, Process.SIGNAL_QUIT);
                                observer.wait(1000);  // Wait for write-close, give up after 1 sec
                                if (DEBUG_ANR) Slog.d(TAG, "Done with extra pid " + stats.pid
                                        + " in " + (SystemClock.elapsedRealtime()-stime) + "ms");
                            }
                        } catch (InterruptedException e) {
                            Slog.wtf(TAG, e);
                        }
                    } else if (DEBUG_ANR) {
                        Slog.d(TAG, "Skipping next CPU consuming process, not a java proc: "
                                + stats.pid);
                    }
                }
            }
        } finally {
            observer.stopWatching();
        }
    }

    final void logAppTooSlow(ProcessRecord app, long startTime, String msg) {
        if (true || IS_USER_BUILD) {
            return;
        }
        String tracesPath = SystemProperties.get("dalvik.vm.stack-trace-file", null);
        if (tracesPath == null || tracesPath.length() == 0) {
            return;
        }

        StrictMode.ThreadPolicy oldPolicy = StrictMode.allowThreadDiskReads();
        StrictMode.allowThreadDiskWrites();
        try {
            final File tracesFile = new File(tracesPath);
            final File tracesDir = tracesFile.getParentFile();
            final File tracesTmp = new File(tracesDir, "__tmp__");
            try {
                if (tracesFile.exists()) {
                    tracesTmp.delete();
                    tracesFile.renameTo(tracesTmp);
                }
                StringBuilder sb = new StringBuilder();
                Time tobj = new Time();
                tobj.set(System.currentTimeMillis());
                sb.append(tobj.format("%Y-%m-%d %H:%M:%S"));
                sb.append(": ");
                TimeUtils.formatDuration(SystemClock.uptimeMillis()-startTime, sb);
                sb.append(" since ");
                sb.append(msg);
                FileOutputStream fos = new FileOutputStream(tracesFile);
                fos.write(sb.toString().getBytes());
                if (app == null) {
                    fos.write("\n*** No application process!".getBytes());
                }
                fos.close();
                FileUtils.setPermissions(tracesFile.getPath(), 0666, -1, -1); // -rw-rw-rw-
            } catch (IOException e) {
                Slog.w(TAG, "Unable to prepare slow app traces file: " + tracesPath, e);
                return;
            }

            if (app != null) {
                ArrayList<Integer> firstPids = new ArrayList<Integer>();
                firstPids.add(app.pid);
                dumpStackTraces(tracesPath, firstPids, null, null, null);
            }

            File lastTracesFile = null;
            File curTracesFile = null;
            for (int i=9; i>=0; i--) {
                String name = String.format(Locale.US, "slow%02d.txt", i);
                curTracesFile = new File(tracesDir, name);
                if (curTracesFile.exists()) {
                    if (lastTracesFile != null) {
                        curTracesFile.renameTo(lastTracesFile);
                    } else {
                        curTracesFile.delete();
                    }
                }
                lastTracesFile = curTracesFile;
            }
            tracesFile.renameTo(curTracesFile);
            if (tracesTmp.exists()) {
                tracesTmp.renameTo(tracesFile);
            }
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    final void showLaunchWarningLocked(final ActivityRecord cur, final ActivityRecord next) {
        if (!mLaunchWarningShown) {
            mLaunchWarningShown = true;
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (ActivityManagerService.this) {
                        final Dialog d = new LaunchWarningWindow(mContext, cur, next);
                        d.show();
                        mUiHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                synchronized (ActivityManagerService.this) {
                                    d.dismiss();
                                    mLaunchWarningShown = false;
                                }
                            }
                        }, 4000);
                    }
                }
            });
        }
    }

    @Override
    public boolean clearApplicationUserData(final String packageName,
            final IPackageDataObserver observer, int userId) {
        enforceNotIsolatedCaller("clearApplicationUserData");
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        userId = mUserController.handleIncomingUser(pid, uid, userId, false,
                ALLOW_FULL_ONLY, "clearApplicationUserData", null);


        long callingId = Binder.clearCallingIdentity();
        try {
            IPackageManager pm = AppGlobals.getPackageManager();
            int pkgUid = -1;
            synchronized(this) {
                if (getPackageManagerInternalLocked().isPackageDataProtected(
                        userId, packageName)) {
                    throw new SecurityException(
                            "Cannot clear data for a protected package: " + packageName);
                }

                try {
                    pkgUid = pm.getPackageUid(packageName, MATCH_UNINSTALLED_PACKAGES, userId);
                } catch (RemoteException e) {
                }
                if (pkgUid == -1) {
                    Slog.w(TAG, "Invalid packageName: " + packageName);
                    if (observer != null) {
                        try {
                            observer.onRemoveCompleted(packageName, false);
                        } catch (RemoteException e) {
                            Slog.i(TAG, "Observer no longer exists.");
                        }
                    }
                    return false;
                }
                if (uid == pkgUid || checkComponentPermission(
                        android.Manifest.permission.CLEAR_APP_USER_DATA,
                        pid, uid, -1, true)
                        == PackageManager.PERMISSION_GRANTED) {
                    forceStopPackageLocked(packageName, pkgUid, "clear data");
                } else {
                    throw new SecurityException("PID " + pid + " does not have permission "
                            + android.Manifest.permission.CLEAR_APP_USER_DATA + " to clear data"
                                    + " of package " + packageName);
                }

                // Remove all tasks match the cleared application package and user
                for (int i = mRecentTasks.size() - 1; i >= 0; i--) {
                    final TaskRecord tr = mRecentTasks.get(i);
                    final String taskPackageName =
                            tr.getBaseIntent().getComponent().getPackageName();
                    if (tr.userId != userId) continue;
                    if (!taskPackageName.equals(packageName)) continue;
                    removeTaskByIdLocked(tr.taskId, false, REMOVE_FROM_RECENTS);
                }
            }

            final int pkgUidF = pkgUid;
            final int userIdF = userId;
            final IPackageDataObserver localObserver = new IPackageDataObserver.Stub() {
                @Override
                public void onRemoveCompleted(String packageName, boolean succeeded)
                        throws RemoteException {
                    synchronized (ActivityManagerService.this) {
                        finishForceStopPackageLocked(packageName, pkgUidF);
                    }

                    final Intent intent = new Intent(Intent.ACTION_PACKAGE_DATA_CLEARED,
                            Uri.fromParts("package", packageName, null));
                    intent.putExtra(Intent.EXTRA_UID, pkgUidF);
                    intent.putExtra(Intent.EXTRA_USER_HANDLE, UserHandle.getUserId(pkgUidF));
                    broadcastIntentInPackage("android", Process.SYSTEM_UID, intent,
                            null, null, 0, null, null, null, null, false, false, userIdF);

                    if (observer != null) {
                        observer.onRemoveCompleted(packageName, succeeded);
                    }
                }
            };

            try {
                // Clear application user data
                pm.clearApplicationUserData(packageName, localObserver, userId);

                synchronized(this) {
                    // Remove all permissions granted from/to this package
                    removeUriPermissionsForPackageLocked(packageName, userId, true);
                }

                // Remove all zen rules created by this package; revoke it's zen access.
                INotificationManager inm = NotificationManager.getService();
                inm.removeAutomaticZenRules(packageName);
                inm.setNotificationPolicyAccessGranted(packageName, false);

            } catch (RemoteException e) {
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
        return true;
    }

    @Override
    public void killBackgroundProcesses(final String packageName, int userId) {
        if (checkCallingPermission(android.Manifest.permission.KILL_BACKGROUND_PROCESSES)
                != PackageManager.PERMISSION_GRANTED &&
                checkCallingPermission(android.Manifest.permission.RESTART_PACKAGES)
                        != PackageManager.PERMISSION_GRANTED) {
            String msg = "Permission Denial: killBackgroundProcesses() from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " requires " + android.Manifest.permission.KILL_BACKGROUND_PROCESSES;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }

        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, true, ALLOW_FULL_ONLY, "killBackgroundProcesses", null);
        long callingId = Binder.clearCallingIdentity();
        try {
            IPackageManager pm = AppGlobals.getPackageManager();
            synchronized(this) {
                int appId = -1;
                try {
                    appId = UserHandle.getAppId(
                            pm.getPackageUid(packageName, MATCH_DEBUG_TRIAGED_MISSING, userId));
                } catch (RemoteException e) {
                }
                if (appId == -1) {
                    Slog.w(TAG, "Invalid packageName: " + packageName);
                    return;
                }
                killPackageProcessesLocked(packageName, appId, userId,
                        ProcessList.SERVICE_ADJ, false, true, true, false, "kill background");
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Override
    public void killAllBackgroundProcesses() {
        if (checkCallingPermission(android.Manifest.permission.KILL_BACKGROUND_PROCESSES)
                != PackageManager.PERMISSION_GRANTED) {
            final String msg = "Permission Denial: killAllBackgroundProcesses() from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " requires " + android.Manifest.permission.KILL_BACKGROUND_PROCESSES;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }

        final long callingId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final ArrayList<ProcessRecord> procs = new ArrayList<>();
                final int NP = mProcessNames.getMap().size();
                for (int ip = 0; ip < NP; ip++) {
                    final SparseArray<ProcessRecord> apps = mProcessNames.getMap().valueAt(ip);
                    final int NA = apps.size();
                    for (int ia = 0; ia < NA; ia++) {
                        final ProcessRecord app = apps.valueAt(ia);
                        if (app.persistent) {
                            // We don't kill persistent processes.
                            continue;
                        }
                        if (app.removed) {
                            procs.add(app);
                        } else if (app.setAdj >= ProcessList.CACHED_APP_MIN_ADJ) {
                            app.removed = true;
                            procs.add(app);
                        }
                    }
                }

                final int N = procs.size();
                for (int i = 0; i < N; i++) {
                    removeProcessLocked(procs.get(i), false, true, "kill all background");
                }

                mAllowLowerMemLevel = true;

                updateOomAdjLocked();
                doLowMemReportIfNeededLocked(null);
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Kills all background processes, except those matching any of the
     * specified properties.
     *
     * @param minTargetSdk the target SDK version at or above which to preserve
     *                     processes, or {@code -1} to ignore the target SDK
     * @param maxProcState the process state at or below which to preserve
     *                     processes, or {@code -1} to ignore the process state
     */
    private void killAllBackgroundProcessesExcept(int minTargetSdk, int maxProcState) {
        if (checkCallingPermission(android.Manifest.permission.KILL_BACKGROUND_PROCESSES)
                != PackageManager.PERMISSION_GRANTED) {
            final String msg = "Permission Denial: killAllBackgroundProcessesExcept() from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " requires " + android.Manifest.permission.KILL_BACKGROUND_PROCESSES;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }

        final long callingId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final ArrayList<ProcessRecord> procs = new ArrayList<>();
                final int NP = mProcessNames.getMap().size();
                for (int ip = 0; ip < NP; ip++) {
                    final SparseArray<ProcessRecord> apps = mProcessNames.getMap().valueAt(ip);
                    final int NA = apps.size();
                    for (int ia = 0; ia < NA; ia++) {
                        final ProcessRecord app = apps.valueAt(ia);
                        if (app.removed) {
                            procs.add(app);
                        } else if ((minTargetSdk < 0 || app.info.targetSdkVersion < minTargetSdk)
                                && (maxProcState < 0 || app.setProcState > maxProcState)) {
                            app.removed = true;
                            procs.add(app);
                        }
                    }
                }

                final int N = procs.size();
                for (int i = 0; i < N; i++) {
                    removeProcessLocked(procs.get(i), false, true, "kill all background except");
                }
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Override
    public void forceStopPackage(final String packageName, int userId) {
        if (checkCallingPermission(android.Manifest.permission.FORCE_STOP_PACKAGES)
                != PackageManager.PERMISSION_GRANTED) {
            String msg = "Permission Denial: forceStopPackage() from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " requires " + android.Manifest.permission.FORCE_STOP_PACKAGES;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        final int callingPid = Binder.getCallingPid();
        userId = mUserController.handleIncomingUser(callingPid, Binder.getCallingUid(),
                userId, true, ALLOW_FULL_ONLY, "forceStopPackage", null);
        long callingId = Binder.clearCallingIdentity();
        try {
            IPackageManager pm = AppGlobals.getPackageManager();
            synchronized(this) {
                int[] users = userId == UserHandle.USER_ALL
                        ? mUserController.getUsers() : new int[] { userId };
                for (int user : users) {
                    int pkgUid = -1;
                    try {
                        pkgUid = pm.getPackageUid(packageName, MATCH_DEBUG_TRIAGED_MISSING,
                                user);
                    } catch (RemoteException e) {
                    }
                    if (pkgUid == -1) {
                        Slog.w(TAG, "Invalid packageName: " + packageName);
                        continue;
                    }
                    try {
                        pm.setPackageStoppedState(packageName, true, user);
                    } catch (RemoteException e) {
                    } catch (IllegalArgumentException e) {
                        Slog.w(TAG, "Failed trying to unstop package "
                                + packageName + ": " + e);
                    }
                    if (mUserController.isUserRunningLocked(user, 0)) {
                        forceStopPackageLocked(packageName, pkgUid, "from pid " + callingPid);
                        finishForceStopPackageLocked(packageName, pkgUid);
                    }
                }
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Override
    public void addPackageDependency(String packageName) {
        synchronized (this) {
            int callingPid = Binder.getCallingPid();
            if (callingPid == Process.myPid()) {
                //  Yeah, um, no.
                return;
            }
            ProcessRecord proc;
            synchronized (mPidsSelfLocked) {
                proc = mPidsSelfLocked.get(Binder.getCallingPid());
            }
            if (proc != null) {
                if (proc.pkgDeps == null) {
                    proc.pkgDeps = new ArraySet<String>(1);
                }
                proc.pkgDeps.add(packageName);
            }
        }
    }

    /*
     * The pkg name and app id have to be specified.
     */
    @Override
    public void killApplication(String pkg, int appId, int userId, String reason) {
        if (pkg == null) {
            return;
        }
        // Make sure the uid is valid.
        if (appId < 0) {
            Slog.w(TAG, "Invalid appid specified for pkg : " + pkg);
            return;
        }
        int callerUid = Binder.getCallingUid();
        // Only the system server can kill an application
        if (UserHandle.getAppId(callerUid) == Process.SYSTEM_UID) {
            // Post an aysnc message to kill the application
            Message msg = mHandler.obtainMessage(KILL_APPLICATION_MSG);
            msg.arg1 = appId;
            msg.arg2 = userId;
            Bundle bundle = new Bundle();
            bundle.putString("pkg", pkg);
            bundle.putString("reason", reason);
            msg.obj = bundle;
            mHandler.sendMessage(msg);
        } else {
            throw new SecurityException(callerUid + " cannot kill pkg: " +
                    pkg);
        }
    }

    @Override
    public void closeSystemDialogs(String reason) {
        enforceNotIsolatedCaller("closeSystemDialogs");

        final int pid = Binder.getCallingPid();
        final int uid = Binder.getCallingUid();
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                // Only allow this from foreground processes, so that background
                // applications can't abuse it to prevent system UI from being shown.
                if (uid >= Process.FIRST_APPLICATION_UID) {
                    ProcessRecord proc;
                    synchronized (mPidsSelfLocked) {
                        proc = mPidsSelfLocked.get(pid);
                    }
                    if (proc.curRawAdj > ProcessList.PERCEPTIBLE_APP_ADJ) {
                        Slog.w(TAG, "Ignoring closeSystemDialogs " + reason
                                + " from background process " + proc);
                        return;
                    }
                }
                closeSystemDialogsLocked(reason);
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    void closeSystemDialogsLocked(String reason) {
        Intent intent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                | Intent.FLAG_RECEIVER_FOREGROUND);
        if (reason != null) {
            intent.putExtra("reason", reason);
        }
        mWindowManager.closeSystemDialogs(reason);

        mStackSupervisor.closeSystemDialogsLocked();

        broadcastIntentLocked(null, null, intent, null, null, 0, null, null, null,
                AppOpsManager.OP_NONE, null, false, false,
                -1, Process.SYSTEM_UID, UserHandle.USER_ALL);
    }

    @Override
    public Debug.MemoryInfo[] getProcessMemoryInfo(int[] pids) {
        enforceNotIsolatedCaller("getProcessMemoryInfo");
        Debug.MemoryInfo[] infos = new Debug.MemoryInfo[pids.length];
        for (int i=pids.length-1; i>=0; i--) {
            ProcessRecord proc;
            int oomAdj;
            synchronized (this) {
                synchronized (mPidsSelfLocked) {
                    proc = mPidsSelfLocked.get(pids[i]);
                    oomAdj = proc != null ? proc.setAdj : 0;
                }
            }
            infos[i] = new Debug.MemoryInfo();
            Debug.getMemoryInfo(pids[i], infos[i]);
            if (proc != null) {
                synchronized (this) {
                    if (proc.thread != null && proc.setAdj == oomAdj) {
                        // Record this for posterity if the process has been stable.
                        proc.baseProcessTracker.addPss(infos[i].getTotalPss(),
                                infos[i].getTotalUss(), false, proc.pkgList);
                    }
                }
            }
        }
        return infos;
    }

    @Override
    public long[] getProcessPss(int[] pids) {
        enforceNotIsolatedCaller("getProcessPss");
        long[] pss = new long[pids.length];
        for (int i=pids.length-1; i>=0; i--) {
            ProcessRecord proc;
            int oomAdj;
            synchronized (this) {
                synchronized (mPidsSelfLocked) {
                    proc = mPidsSelfLocked.get(pids[i]);
                    oomAdj = proc != null ? proc.setAdj : 0;
                }
            }
            long[] tmpUss = new long[1];
            pss[i] = Debug.getPss(pids[i], tmpUss, null);
            if (proc != null) {
                synchronized (this) {
                    if (proc.thread != null && proc.setAdj == oomAdj) {
                        // Record this for posterity if the process has been stable.
                        proc.baseProcessTracker.addPss(pss[i], tmpUss[0], false, proc.pkgList);
                    }
                }
            }
        }
        return pss;
    }

    @Override
    public void killApplicationProcess(String processName, int uid) {
        if (processName == null) {
            return;
        }

        int callerUid = Binder.getCallingUid();
        // Only the system server can kill an application
        if (callerUid == Process.SYSTEM_UID) {
            synchronized (this) {
                ProcessRecord app = getProcessRecordLocked(processName, uid, true);
                if (app != null && app.thread != null) {
                    try {
                        app.thread.scheduleSuicide();
                    } catch (RemoteException e) {
                        // If the other end already died, then our work here is done.
                    }
                } else {
                    Slog.w(TAG, "Process/uid not found attempting kill of "
                            + processName + " / " + uid);
                }
            }
        } else {
            throw new SecurityException(callerUid + " cannot kill app process: " +
                    processName);
        }
    }

    private void forceStopPackageLocked(final String packageName, int uid, String reason) {
        forceStopPackageLocked(packageName, UserHandle.getAppId(uid), false,
                false, true, false, false, UserHandle.getUserId(uid), reason);
    }

    private void finishForceStopPackageLocked(final String packageName, int uid) {
        Intent intent = new Intent(Intent.ACTION_PACKAGE_RESTARTED,
                Uri.fromParts("package", packageName, null));
        if (!mProcessesReady) {
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                    | Intent.FLAG_RECEIVER_FOREGROUND);
        }
        intent.putExtra(Intent.EXTRA_UID, uid);
        intent.putExtra(Intent.EXTRA_USER_HANDLE, UserHandle.getUserId(uid));
        broadcastIntentLocked(null, null, intent,
                null, null, 0, null, null, null, AppOpsManager.OP_NONE,
                null, false, false, MY_PID, Process.SYSTEM_UID, UserHandle.getUserId(uid));
    }


    private final boolean killPackageProcessesLocked(String packageName, int appId,
            int userId, int minOomAdj, boolean callerWillRestart, boolean allowRestart,
            boolean doit, boolean evenPersistent, String reason) {
        ArrayList<ProcessRecord> procs = new ArrayList<>();

        // Remove all processes this package may have touched: all with the
        // same UID (except for the system or root user), and all whose name
        // matches the package name.
        final int NP = mProcessNames.getMap().size();
        for (int ip=0; ip<NP; ip++) {
            SparseArray<ProcessRecord> apps = mProcessNames.getMap().valueAt(ip);
            final int NA = apps.size();
            for (int ia=0; ia<NA; ia++) {
                ProcessRecord app = apps.valueAt(ia);
                if (app.persistent && !evenPersistent) {
                    // we don't kill persistent processes
                    continue;
                }
                if (app.removed) {
                    if (doit) {
                        procs.add(app);
                    }
                    continue;
                }

                // Skip process if it doesn't meet our oom adj requirement.
                if (app.setAdj < minOomAdj) {
                    continue;
                }

                // If no package is specified, we call all processes under the
                // give user id.
                if (packageName == null) {
                    if (userId != UserHandle.USER_ALL && app.userId != userId) {
                        continue;
                    }
                    if (appId >= 0 && UserHandle.getAppId(app.uid) != appId) {
                        continue;
                    }
                // Package has been specified, we want to hit all processes
                // that match it.  We need to qualify this by the processes
                // that are running under the specified app and user ID.
                } else {
                    final boolean isDep = app.pkgDeps != null
                            && app.pkgDeps.contains(packageName);
                    if (!isDep && UserHandle.getAppId(app.uid) != appId) {
                        continue;
                    }
                    if (userId != UserHandle.USER_ALL && app.userId != userId) {
                        continue;
                    }
                    if (!app.pkgList.containsKey(packageName) && !isDep) {
                        continue;
                    }
                }

                // Process has passed all conditions, kill it!
                if (!doit) {
                    return true;
                }
                app.removed = true;
                procs.add(app);
            }
        }

        int N = procs.size();
        for (int i=0; i<N; i++) {
            removeProcessLocked(procs.get(i), callerWillRestart, allowRestart, reason);
        }
        updateOomAdjLocked();
        return N > 0;
    }

    private void cleanupDisabledPackageComponentsLocked(
            String packageName, int userId, boolean killProcess, String[] changedClasses) {

        Set<String> disabledClasses = null;
        boolean packageDisabled = false;
        IPackageManager pm = AppGlobals.getPackageManager();

        if (changedClasses == null) {
            // Nothing changed...
            return;
        }

        // Determine enable/disable state of the package and its components.
        int enabled = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        for (int i = changedClasses.length - 1; i >= 0; i--) {
            final String changedClass = changedClasses[i];

            if (changedClass.equals(packageName)) {
                try {
                    // Entire package setting changed
                    enabled = pm.getApplicationEnabledSetting(packageName,
                            (userId != UserHandle.USER_ALL) ? userId : UserHandle.USER_SYSTEM);
                } catch (Exception e) {
                    // No such package/component; probably racing with uninstall.  In any
                    // event it means we have nothing further to do here.
                    return;
                }
                packageDisabled = enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        && enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
                if (packageDisabled) {
                    // Entire package is disabled.
                    // No need to continue to check component states.
                    disabledClasses = null;
                    break;
                }
            } else {
                try {
                    enabled = pm.getComponentEnabledSetting(
                            new ComponentName(packageName, changedClass),
                            (userId != UserHandle.USER_ALL) ? userId : UserHandle.USER_SYSTEM);
                } catch (Exception e) {
                    // As above, probably racing with uninstall.
                    return;
                }
                if (enabled != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        && enabled != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
                    if (disabledClasses == null) {
                        disabledClasses = new ArraySet<>(changedClasses.length);
                    }
                    disabledClasses.add(changedClass);
                }
            }
        }

        if (!packageDisabled && disabledClasses == null) {
            // Nothing to do here...
            return;
        }

        // Clean-up disabled activities.
        if (mStackSupervisor.finishDisabledPackageActivitiesLocked(
                packageName, disabledClasses, true, false, userId) && mBooted) {
            mStackSupervisor.resumeFocusedStackTopActivityLocked();
            mStackSupervisor.scheduleIdleLocked();
        }

        // Clean-up disabled tasks
        cleanupDisabledPackageTasksLocked(packageName, disabledClasses, userId);

        // Clean-up disabled services.
        mServices.bringDownDisabledPackageServicesLocked(
                packageName, disabledClasses, userId, false, killProcess, true);

        // Clean-up disabled providers.
        ArrayList<ContentProviderRecord> providers = new ArrayList<>();
        mProviderMap.collectPackageProvidersLocked(
                packageName, disabledClasses, true, false, userId, providers);
        for (int i = providers.size() - 1; i >= 0; i--) {
            removeDyingProviderLocked(null, providers.get(i), true);
        }

        // Clean-up disabled broadcast receivers.
        for (int i = mBroadcastQueues.length - 1; i >= 0; i--) {
            mBroadcastQueues[i].cleanupDisabledPackageReceiversLocked(
                    packageName, disabledClasses, userId, true);
        }

    }

    final boolean clearBroadcastQueueForUserLocked(int userId) {
        boolean didSomething = false;
        for (int i = mBroadcastQueues.length - 1; i >= 0; i--) {
            didSomething |= mBroadcastQueues[i].cleanupDisabledPackageReceiversLocked(
                    null, null, userId, true);
        }
        return didSomething;
    }

    final boolean forceStopPackageLocked(String packageName, int appId,
            boolean callerWillRestart, boolean purgeCache, boolean doit,
            boolean evenPersistent, boolean uninstalling, int userId, String reason) {
        int i;

        if (userId == UserHandle.USER_ALL && packageName == null) {
            Slog.w(TAG, "Can't force stop all processes of all users, that is insane!");
        }

        if (appId < 0 && packageName != null) {
            try {
                appId = UserHandle.getAppId(AppGlobals.getPackageManager()
                        .getPackageUid(packageName, MATCH_DEBUG_TRIAGED_MISSING, 0));
            } catch (RemoteException e) {
            }
        }

        if (doit) {
            if (packageName != null) {
                Slog.i(TAG, "Force stopping " + packageName + " appid=" + appId
                        + " user=" + userId + ": " + reason);
            } else {
                Slog.i(TAG, "Force stopping u" + userId + ": " + reason);
            }

            mAppErrors.resetProcessCrashTimeLocked(packageName == null, appId, userId);
        }

        boolean didSomething = killPackageProcessesLocked(packageName, appId, userId,
                ProcessList.INVALID_ADJ, callerWillRestart, true, doit, evenPersistent,
                packageName == null ? ("stop user " + userId) : ("stop " + packageName));

        if (mStackSupervisor.finishDisabledPackageActivitiesLocked(
                packageName, null, doit, evenPersistent, userId)) {
            if (!doit) {
                return true;
            }
            didSomething = true;
        }

        if (mServices.bringDownDisabledPackageServicesLocked(
                packageName, null, userId, evenPersistent, true, doit)) {
            if (!doit) {
                return true;
            }
            didSomething = true;
        }

        if (packageName == null) {
            // Remove all sticky broadcasts from this user.
            mStickyBroadcasts.remove(userId);
        }

        ArrayList<ContentProviderRecord> providers = new ArrayList<>();
        if (mProviderMap.collectPackageProvidersLocked(packageName, null, doit, evenPersistent,
                userId, providers)) {
            if (!doit) {
                return true;
            }
            didSomething = true;
        }
        for (i = providers.size() - 1; i >= 0; i--) {
            removeDyingProviderLocked(null, providers.get(i), true);
        }

        // Remove transient permissions granted from/to this package/user
        removeUriPermissionsForPackageLocked(packageName, userId, false);

        if (doit) {
            for (i = mBroadcastQueues.length - 1; i >= 0; i--) {
                didSomething |= mBroadcastQueues[i].cleanupDisabledPackageReceiversLocked(
                        packageName, null, userId, doit);
            }
        }

        if (packageName == null || uninstalling) {
            // Remove pending intents.  For now we only do this when force
            // stopping users, because we have some problems when doing this
            // for packages -- app widgets are not currently cleaned up for
            // such packages, so they can be left with bad pending intents.
            if (mIntentSenderRecords.size() > 0) {
                Iterator<WeakReference<PendingIntentRecord>> it
                        = mIntentSenderRecords.values().iterator();
                while (it.hasNext()) {
                    WeakReference<PendingIntentRecord> wpir = it.next();
                    if (wpir == null) {
                        it.remove();
                        continue;
                    }
                    PendingIntentRecord pir = wpir.get();
                    if (pir == null) {
                        it.remove();
                        continue;
                    }
                    if (packageName == null) {
                        // Stopping user, remove all objects for the user.
                        if (pir.key.userId != userId) {
                            // Not the same user, skip it.
                            continue;
                        }
                    } else {
                        if (UserHandle.getAppId(pir.uid) != appId) {
                            // Different app id, skip it.
                            continue;
                        }
                        if (userId != UserHandle.USER_ALL && pir.key.userId != userId) {
                            // Different user, skip it.
                            continue;
                        }
                        if (!pir.key.packageName.equals(packageName)) {
                            // Different package, skip it.
                            continue;
                        }
                    }
                    if (!doit) {
                        return true;
                    }
                    didSomething = true;
                    it.remove();
                    pir.canceled = true;
                    if (pir.key.activity != null && pir.key.activity.pendingResults != null) {
                        pir.key.activity.pendingResults.remove(pir.ref);
                    }
                }
            }
        }

        if (doit) {
            if (purgeCache && packageName != null) {
                AttributeCache ac = AttributeCache.instance();
                if (ac != null) {
                    ac.removePackage(packageName);
                }
            }
            if (mBooted) {
                mStackSupervisor.resumeFocusedStackTopActivityLocked();
                mStackSupervisor.scheduleIdleLocked();
            }
        }

        return didSomething;
    }

    private final ProcessRecord removeProcessNameLocked(final String name, final int uid) {
        ProcessRecord old = mProcessNames.remove(name, uid);
        if (old != null) {
            old.uidRecord.numProcs--;
            if (old.uidRecord.numProcs == 0) {
                // No more processes using this uid, tell clients it is gone.
                if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                        "No more processes in " + old.uidRecord);
                enqueueUidChangeLocked(old.uidRecord, -1, UidRecord.CHANGE_GONE);
                mActiveUids.remove(uid);
                noteUidProcessState(uid, ActivityManager.PROCESS_STATE_NONEXISTENT);
            }
            old.uidRecord = null;
        }
        mIsolatedProcesses.remove(uid);
        return old;
    }

    private final void addProcessNameLocked(ProcessRecord proc) {
        // We shouldn't already have a process under this name, but just in case we
        // need to clean up whatever may be there now.
        ProcessRecord old = removeProcessNameLocked(proc.processName, proc.uid);
        if (old == proc && proc.persistent) {
            // We are re-adding a persistent process.  Whatevs!  Just leave it there.
            Slog.w(TAG, "Re-adding persistent process " + proc);
        } else if (old != null) {
            Slog.wtf(TAG, "Already have existing proc " + old + " when adding " + proc);
        }
        UidRecord uidRec = mActiveUids.get(proc.uid);
        if (uidRec == null) {
            uidRec = new UidRecord(proc.uid);
            // This is the first appearance of the uid, report it now!
            if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                    "Creating new process uid: " + uidRec);
            mActiveUids.put(proc.uid, uidRec);
            noteUidProcessState(uidRec.uid, uidRec.curProcState);
            enqueueUidChangeLocked(uidRec, -1, UidRecord.CHANGE_ACTIVE);
        }
        proc.uidRecord = uidRec;

        // Reset render thread tid if it was already set, so new process can set it again.
        proc.renderThreadTid = 0;
        uidRec.numProcs++;
        mProcessNames.put(proc.processName, proc.uid, proc);
        if (proc.isolated) {
            mIsolatedProcesses.put(proc.uid, proc);
        }
    }

    boolean removeProcessLocked(ProcessRecord app,
            boolean callerWillRestart, boolean allowRestart, String reason) {
        final String name = app.processName;
        final int uid = app.uid;
        if (DEBUG_PROCESSES) Slog.d(TAG_PROCESSES,
            "Force removing proc " + app.toShortString() + " (" + name + "/" + uid + ")");

        ProcessRecord old = mProcessNames.get(name, uid);
        if (old != app) {
            // This process is no longer active, so nothing to do.
            Slog.w(TAG, "Ignoring remove of inactive process: " + app);
            return false;
        }
        removeProcessNameLocked(name, uid);
        if (mHeavyWeightProcess == app) {
            mHandler.sendMessage(mHandler.obtainMessage(CANCEL_HEAVY_NOTIFICATION_MSG,
                    mHeavyWeightProcess.userId, 0));
            mHeavyWeightProcess = null;
        }
        boolean needRestart = false;
        if (app.pid > 0 && app.pid != MY_PID) {
            int pid = app.pid;
            synchronized (mPidsSelfLocked) {
                mPidsSelfLocked.remove(pid);
                mHandler.removeMessages(PROC_START_TIMEOUT_MSG, app);
            }
            mBatteryStatsService.noteProcessFinish(app.processName, app.info.uid);
            if (app.isolated) {
                mBatteryStatsService.removeIsolatedUid(app.uid, app.info.uid);
            }
            boolean willRestart = false;
            if (app.persistent && !app.isolated) {
                if (!callerWillRestart) {
                    willRestart = true;
                } else {
                    needRestart = true;
                }
            }
            app.kill(reason, true);
            handleAppDiedLocked(app, willRestart, allowRestart);
            if (willRestart) {
                removeLruProcessLocked(app);
                addAppLocked(app.info, false, null /* ABI override */);
            }
        } else {
            mRemovedProcesses.add(app);
        }

        return needRestart;
    }

    private final void processContentProviderPublishTimedOutLocked(ProcessRecord app) {
        cleanupAppInLaunchingProvidersLocked(app, true);
        removeProcessLocked(app, false, true, "timeout publishing content providers");
    }

    private final void processStartTimedOutLocked(ProcessRecord app) {
        final int pid = app.pid;
        boolean gone = false;
        synchronized (mPidsSelfLocked) {
            ProcessRecord knownApp = mPidsSelfLocked.get(pid);
            if (knownApp != null && knownApp.thread == null) {
                mPidsSelfLocked.remove(pid);
                gone = true;
            }
        }

        if (gone) {
            Slog.w(TAG, "Process " + app + " failed to attach");
            EventLog.writeEvent(EventLogTags.AM_PROCESS_START_TIMEOUT, app.userId,
                    pid, app.uid, app.processName);
            removeProcessNameLocked(app.processName, app.uid);
            if (mHeavyWeightProcess == app) {
                mHandler.sendMessage(mHandler.obtainMessage(CANCEL_HEAVY_NOTIFICATION_MSG,
                        mHeavyWeightProcess.userId, 0));
                mHeavyWeightProcess = null;
            }
            mBatteryStatsService.noteProcessFinish(app.processName, app.info.uid);
            if (app.isolated) {
                mBatteryStatsService.removeIsolatedUid(app.uid, app.info.uid);
            }
            // Take care of any launching providers waiting for this process.
            cleanupAppInLaunchingProvidersLocked(app, true);
            // Take care of any services that are waiting for the process.
            mServices.processStartTimedOutLocked(app);
            app.kill("start timeout", true);
            removeLruProcessLocked(app);
            if (mBackupTarget != null && mBackupTarget.app.pid == pid) {
                Slog.w(TAG, "Unattached app died before backup, skipping");
                try {
                    IBackupManager bm = IBackupManager.Stub.asInterface(
                            ServiceManager.getService(Context.BACKUP_SERVICE));
                    bm.agentDisconnected(app.info.packageName);
                } catch (RemoteException e) {
                    // Can't happen; the backup manager is local
                }
            }
            if (isPendingBroadcastProcessLocked(pid)) {
                Slog.w(TAG, "Unattached app died before broadcast acknowledged, skipping");
                skipPendingBroadcastLocked(pid);
            }
        } else {
            Slog.w(TAG, "Spurious process start timeout - pid not known for " + app);
        }
    }

    private final boolean attachApplicationLocked(IApplicationThread thread,
            int pid) {

        // Find the application record that is being attached...  either via
        // the pid if we are running in multiple processes, or just pull the
        // next app record if we are emulating process with anonymous threads.
        ProcessRecord app;
        if (pid != MY_PID && pid >= 0) {
            synchronized (mPidsSelfLocked) {
                app = mPidsSelfLocked.get(pid);
            }
        } else {
            app = null;
        }

        if (app == null) {
            Slog.w(TAG, "No pending application record for pid " + pid
                    + " (IApplicationThread " + thread + "); dropping process");
            EventLog.writeEvent(EventLogTags.AM_DROP_PROCESS, pid);
            if (pid > 0 && pid != MY_PID) {
                Process.killProcessQuiet(pid);
                //TODO: killProcessGroup(app.info.uid, pid);
            } else {
                try {
                    thread.scheduleExit();
                } catch (Exception e) {
                    // Ignore exceptions.
                }
            }
            return false;
        }

        // If this application record is still attached to a previous
        // process, clean it up now.
        if (app.thread != null) {
            handleAppDiedLocked(app, true, true);
        }

        // Tell the process all about itself.

        if (DEBUG_ALL) Slog.v(
                TAG, "Binding process pid " + pid + " to record " + app);

        final String processName = app.processName;
        try {
            AppDeathRecipient adr = new AppDeathRecipient(
                    app, pid, thread);
            thread.asBinder().linkToDeath(adr, 0);
            app.deathRecipient = adr;
        } catch (RemoteException e) {
            app.resetPackageList(mProcessStats);
            startProcessLocked(app, "link fail", processName);
            return false;
        }

        EventLog.writeEvent(EventLogTags.AM_PROC_BOUND, app.userId, app.pid, app.processName);

        app.makeActive(thread, mProcessStats);
        app.curAdj = app.setAdj = app.verifiedAdj = ProcessList.INVALID_ADJ;
        app.curSchedGroup = app.setSchedGroup = ProcessList.SCHED_GROUP_DEFAULT;
        app.forcingToForeground = null;
        updateProcessForegroundLocked(app, false, false);
        app.hasShownUi = false;
        app.debugging = false;
        app.cached = false;
        app.killedByAm = false;

        // We carefully use the same state that PackageManager uses for
        // filtering, since we use this flag to decide if we need to install
        // providers when user is unlocked later
        app.unlocked = StorageManager.isUserKeyUnlocked(app.userId);

        mHandler.removeMessages(PROC_START_TIMEOUT_MSG, app);

        boolean normalMode = mProcessesReady || isAllowedWhileBooting(app.info);
        List<ProviderInfo> providers = normalMode ? generateApplicationProvidersLocked(app) : null;

        if (providers != null && checkAppInLaunchingProvidersLocked(app)) {
            Message msg = mHandler.obtainMessage(CONTENT_PROVIDER_PUBLISH_TIMEOUT_MSG);
            msg.obj = app;
            mHandler.sendMessageDelayed(msg, CONTENT_PROVIDER_PUBLISH_TIMEOUT);
        }

        if (!normalMode) {
            Slog.i(TAG, "Launching preboot mode app: " + app);
        }

        if (DEBUG_ALL) Slog.v(
            TAG, "New app record " + app
            + " thread=" + thread.asBinder() + " pid=" + pid);
        try {
            int testMode = IApplicationThread.DEBUG_OFF;
            if (mDebugApp != null && mDebugApp.equals(processName)) {
                testMode = mWaitForDebugger
                    ? IApplicationThread.DEBUG_WAIT
                    : IApplicationThread.DEBUG_ON;
                app.debugging = true;
                if (mDebugTransient) {
                    mDebugApp = mOrigDebugApp;
                    mWaitForDebugger = mOrigWaitForDebugger;
                }
            }
            String profileFile = app.instrumentationProfileFile;
            ParcelFileDescriptor profileFd = null;
            int samplingInterval = 0;
            boolean profileAutoStop = false;
            if (mProfileApp != null && mProfileApp.equals(processName)) {
                mProfileProc = app;
                profileFile = mProfileFile;
                profileFd = mProfileFd;
                samplingInterval = mSamplingInterval;
                profileAutoStop = mAutoStopProfiler;
            }
            boolean enableTrackAllocation = false;
            if (mTrackAllocationApp != null && mTrackAllocationApp.equals(processName)) {
                enableTrackAllocation = true;
                mTrackAllocationApp = null;
            }

            // If the app is being launched for restore or full backup, set it up specially
            boolean isRestrictedBackupMode = false;
            if (mBackupTarget != null && mBackupAppName.equals(processName)) {
                isRestrictedBackupMode = mBackupTarget.appInfo.uid >= Process.FIRST_APPLICATION_UID
                        && ((mBackupTarget.backupMode == BackupRecord.RESTORE)
                                || (mBackupTarget.backupMode == BackupRecord.RESTORE_FULL)
                                || (mBackupTarget.backupMode == BackupRecord.BACKUP_FULL));
            }

            if (app.instrumentationClass != null) {
                notifyPackageUse(app.instrumentationClass.getPackageName(),
                                 PackageManager.NOTIFY_PACKAGE_USE_INSTRUMENTATION);
            }
            if (DEBUG_CONFIGURATION) Slog.v(TAG_CONFIGURATION, "Binding proc "
                    + processName + " with config " + mConfiguration);
            ApplicationInfo appInfo = app.instrumentationInfo != null
                    ? app.instrumentationInfo : app.info;
            app.compat = compatibilityInfoForPackageLocked(appInfo);
            if (profileFd != null) {
                profileFd = profileFd.dup();
            }
            ProfilerInfo profilerInfo = profileFile == null ? null
                    : new ProfilerInfo(profileFile, profileFd, samplingInterval, profileAutoStop);
            thread.bindApplication(processName, appInfo, providers, app.instrumentationClass,
                    profilerInfo, app.instrumentationArguments, app.instrumentationWatcher,
                    app.instrumentationUiAutomationConnection, testMode,
                    mBinderTransactionTrackingEnabled, enableTrackAllocation,
                    isRestrictedBackupMode || !normalMode, app.persistent,
                    new Configuration(mConfiguration), app.compat,
                    getCommonServicesLocked(app.isolated),
                    mCoreSettingsObserver.getCoreSettingsLocked());
            updateLruProcessLocked(app, false, null);
            app.lastRequestedGc = app.lastLowMemory = SystemClock.uptimeMillis();
        } catch (Exception e) {
            // todo: Yikes!  What should we do?  For now we will try to
            // start another process, but that could easily get us in
            // an infinite loop of restarting processes...
            Slog.wtf(TAG, "Exception thrown during bind of " + app, e);

            app.resetPackageList(mProcessStats);
            app.unlinkDeathRecipient();
            startProcessLocked(app, "bind fail", processName);
            return false;
        }

        // Remove this record from the list of starting applications.
        mPersistentStartingProcesses.remove(app);
        if (DEBUG_PROCESSES && mProcessesOnHold.contains(app)) Slog.v(TAG_PROCESSES,
                "Attach application locked removing on hold: " + app);
        mProcessesOnHold.remove(app);

        boolean badApp = false;
        boolean didSomething = false;

        // See if the top visible activity is waiting to run in this process...
        if (normalMode) {
            try {
                if (mStackSupervisor.attachApplicationLocked(app)) {
                    didSomething = true;
                }
            } catch (Exception e) {
                Slog.wtf(TAG, "Exception thrown launching activities in " + app, e);
                badApp = true;
            }
        }

        // Find any services that should be running in this process...
        if (!badApp) {
            try {
                didSomething |= mServices.attachApplicationLocked(app, processName);
            } catch (Exception e) {
                Slog.wtf(TAG, "Exception thrown starting services in " + app, e);
                badApp = true;
            }
        }

        // Check if a next-broadcast receiver is in this process...
        if (!badApp && isPendingBroadcastProcessLocked(pid)) {
            try {
                didSomething |= sendPendingBroadcastsLocked(app);
            } catch (Exception e) {
                // If the app died trying to launch the receiver we declare it 'bad'
                Slog.wtf(TAG, "Exception thrown dispatching broadcasts in " + app, e);
                badApp = true;
            }
        }

        // Check whether the next backup agent is in this process...
        if (!badApp && mBackupTarget != null && mBackupTarget.appInfo.uid == app.uid) {
            if (DEBUG_BACKUP) Slog.v(TAG_BACKUP,
                    "New app is backup target, launching agent for " + app);
            notifyPackageUse(mBackupTarget.appInfo.packageName,
                             PackageManager.NOTIFY_PACKAGE_USE_BACKUP);
            try {
                thread.scheduleCreateBackupAgent(mBackupTarget.appInfo,
                        compatibilityInfoForPackageLocked(mBackupTarget.appInfo),
                        mBackupTarget.backupMode);
            } catch (Exception e) {
                Slog.wtf(TAG, "Exception thrown creating backup agent in " + app, e);
                badApp = true;
            }
        }

        if (badApp) {
            app.kill("error during init", true);
            handleAppDiedLocked(app, false, true);
            return false;
        }

        if (!didSomething) {
            updateOomAdjLocked();
        }

        return true;
    }

    @Override
    public final void attachApplication(IApplicationThread thread) {
        synchronized (this) {
            int callingPid = Binder.getCallingPid();
            final long origId = Binder.clearCallingIdentity();
            attachApplicationLocked(thread, callingPid);
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public final void activityIdle(IBinder token, Configuration config, boolean stopProfiling) {
        final long origId = Binder.clearCallingIdentity();
        synchronized (this) {
            ActivityStack stack = ActivityRecord.getStackLocked(token);
            if (stack != null) {
                ActivityRecord r =
                        mStackSupervisor.activityIdleInternalLocked(token, false, config);
                if (stopProfiling) {
                    if ((mProfileProc == r.app) && (mProfileFd != null)) {
                        try {
                            mProfileFd.close();
                        } catch (IOException e) {
                        }
                        clearProfilerLocked();
                    }
                }
            }
        }
        Binder.restoreCallingIdentity(origId);
    }

    void postFinishBooting(boolean finishBooting, boolean enableScreen) {
        mHandler.sendMessage(mHandler.obtainMessage(FINISH_BOOTING_MSG,
                finishBooting ? 1 : 0, enableScreen ? 1 : 0));
    }

    void enableScreenAfterBoot() {
        EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_ENABLE_SCREEN,
                SystemClock.uptimeMillis());
        mWindowManager.enableScreenAfterBoot();

        synchronized (this) {
            updateEventDispatchingLocked();
        }
    }

    @Override
    public void showBootMessage(final CharSequence msg, final boolean always) {
        if (Binder.getCallingUid() != Process.myUid()) {
            throw new SecurityException();
        }
        mWindowManager.showBootMessage(msg, always);
    }

    @Override
    public void keyguardWaitingForActivityDrawn() {
        enforceNotIsolatedCaller("keyguardWaitingForActivityDrawn");
        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                if (DEBUG_LOCKSCREEN) logLockScreen("");
                mWindowManager.keyguardWaitingForActivityDrawn();
                if (mLockScreenShown == LOCK_SCREEN_SHOWN) {
                    mLockScreenShown = LOCK_SCREEN_LEAVING;
                    updateSleepIfNeededLocked();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void keyguardGoingAway(int flags) {
        enforceNotIsolatedCaller("keyguardGoingAway");
        final long token = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                if (DEBUG_LOCKSCREEN) logLockScreen("");
                mWindowManager.keyguardGoingAway(flags);
                if (mLockScreenShown == LOCK_SCREEN_SHOWN) {
                    mLockScreenShown = LOCK_SCREEN_HIDDEN;
                    updateSleepIfNeededLocked();

                    // Some stack visibility might change (e.g. docked stack)
                    mStackSupervisor.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
                    applyVrModeIfNeededLocked(mFocusedActivity, true);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    final void finishBooting() {
        synchronized (this) {
            if (!mBootAnimationComplete) {
                mCallFinishBooting = true;
                return;
            }
            mCallFinishBooting = false;
        }

        ArraySet<String> completedIsas = new ArraySet<String>();
        for (String abi : Build.SUPPORTED_ABIS) {
            Process.establishZygoteConnectionForAbi(abi);
            final String instructionSet = VMRuntime.getInstructionSet(abi);
            if (!completedIsas.contains(instructionSet)) {
                try {
                    mInstaller.markBootComplete(VMRuntime.getInstructionSet(abi));
                } catch (InstallerException e) {
                    Slog.w(TAG, "Unable to mark boot complete for abi: " + abi + " (" +
                            e.getMessage() +")");
                }
                completedIsas.add(instructionSet);
            }
        }

        IntentFilter pkgFilter = new IntentFilter();
        pkgFilter.addAction(Intent.ACTION_QUERY_PACKAGE_RESTART);
        pkgFilter.addDataScheme("package");
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String[] pkgs = intent.getStringArrayExtra(Intent.EXTRA_PACKAGES);
                if (pkgs != null) {
                    for (String pkg : pkgs) {
                        synchronized (ActivityManagerService.this) {
                            if (forceStopPackageLocked(pkg, -1, false, false, false, false, false,
                                    0, "query restart")) {
                                setResultCode(Activity.RESULT_OK);
                                return;
                            }
                        }
                    }
                }
            }
        }, pkgFilter);

        IntentFilter dumpheapFilter = new IntentFilter();
        dumpheapFilter.addAction(DumpHeapActivity.ACTION_DELETE_DUMPHEAP);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getBooleanExtra(DumpHeapActivity.EXTRA_DELAY_DELETE, false)) {
                    mHandler.sendEmptyMessageDelayed(POST_DUMP_HEAP_NOTIFICATION_MSG, 5*60*1000);
                } else {
                    mHandler.sendEmptyMessage(POST_DUMP_HEAP_NOTIFICATION_MSG);
                }
            }
        }, dumpheapFilter);

        // Let system services know.
        mSystemServiceManager.startBootPhase(SystemService.PHASE_BOOT_COMPLETED);

        synchronized (this) {
            // Ensure that any processes we had put on hold are now started
            // up.
            final int NP = mProcessesOnHold.size();
            if (NP > 0) {
                ArrayList<ProcessRecord> procs =
                    new ArrayList<ProcessRecord>(mProcessesOnHold);
                for (int ip=0; ip<NP; ip++) {
                    if (DEBUG_PROCESSES) Slog.v(TAG_PROCESSES, "Starting process on hold: "
                            + procs.get(ip));
                    startProcessLocked(procs.get(ip), "on-hold", null);
                }
            }

            if (mFactoryTest != FactoryTest.FACTORY_TEST_LOW_LEVEL) {
                // Start looking for apps that are abusing wake locks.
                Message nmsg = mHandler.obtainMessage(CHECK_EXCESSIVE_WAKE_LOCKS_MSG);
                mHandler.sendMessageDelayed(nmsg, POWER_CHECK_DELAY);
                // Tell anyone interested that we are done booting!
                SystemProperties.set("sys.boot_completed", "1");

                // And trigger dev.bootcomplete if we are not showing encryption progress
                if (!"trigger_restart_min_framework".equals(SystemProperties.get("vold.decrypt"))
                    || "".equals(SystemProperties.get("vold.encrypt_progress"))) {
                    SystemProperties.set("dev.bootcomplete", "1");
                }
                mUserController.sendBootCompletedLocked(
                        new IIntentReceiver.Stub() {
                            @Override
                            public void performReceive(Intent intent, int resultCode,
                                    String data, Bundle extras, boolean ordered,
                                    boolean sticky, int sendingUser) {
                                synchronized (ActivityManagerService.this) {
                                    requestPssAllProcsLocked(SystemClock.uptimeMillis(),
                                            true, false);
                                }
                            }
                        });
                scheduleStartProfilesLocked();
            }
        }
    }

    @Override
    public void bootAnimationComplete() {
        final boolean callFinishBooting;
        synchronized (this) {
            callFinishBooting = mCallFinishBooting;
            mBootAnimationComplete = true;
        }
        if (callFinishBooting) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "FinishBooting");
            finishBooting();
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        }
    }

    final void ensureBootCompleted() {
        boolean booting;
        boolean enableScreen;
        synchronized (this) {
            booting = mBooting;
            mBooting = false;
            enableScreen = !mBooted;
            mBooted = true;
        }

        if (booting) {
            Trace.traceBegin(Trace.TRACE_TAG_ACTIVITY_MANAGER, "FinishBooting");
            finishBooting();
            Trace.traceEnd(Trace.TRACE_TAG_ACTIVITY_MANAGER);
        }

        if (enableScreen) {
            enableScreenAfterBoot();
        }
    }

    @Override
    public final void activityResumed(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        synchronized(this) {
            ActivityStack stack = ActivityRecord.getStackLocked(token);
            if (stack != null) {
                stack.activityResumedLocked(token);
            }
        }
        Binder.restoreCallingIdentity(origId);
    }

    @Override
    public final void activityPaused(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        synchronized(this) {
            ActivityStack stack = ActivityRecord.getStackLocked(token);
            if (stack != null) {
                stack.activityPausedLocked(token, false);
            }
        }
        Binder.restoreCallingIdentity(origId);
    }

    @Override
    public final void activityStopped(IBinder token, Bundle icicle,
            PersistableBundle persistentState, CharSequence description) {
        if (DEBUG_ALL) Slog.v(TAG, "Activity stopped: token=" + token);

        // Refuse possible leaked file descriptors
        if (icicle != null && icicle.hasFileDescriptors()) {
            throw new IllegalArgumentException("File descriptors passed in Bundle");
        }

        final long origId = Binder.clearCallingIdentity();

        synchronized (this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r != null) {
                r.task.stack.activityStoppedLocked(r, icicle, persistentState, description);
            }
        }

        trimApplications();

        Binder.restoreCallingIdentity(origId);
    }

    @Override
    public final void activityDestroyed(IBinder token) {
        if (DEBUG_SWITCH) Slog.v(TAG_SWITCH, "ACTIVITY DESTROYED: " + token);
        synchronized (this) {
            ActivityStack stack = ActivityRecord.getStackLocked(token);
            if (stack != null) {
                stack.activityDestroyedLocked(token, "activityDestroyed");
            }
        }
    }

    @Override
    public final void activityRelaunched(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        synchronized (this) {
            mStackSupervisor.activityRelaunchedLocked(token);
        }
        Binder.restoreCallingIdentity(origId);
    }

    @Override
    public void reportSizeConfigurations(IBinder token, int[] horizontalSizeConfiguration,
            int[] verticalSizeConfigurations, int[] smallestSizeConfigurations) {
        if (DEBUG_CONFIGURATION) Slog.v(TAG, "Report configuration: " + token + " "
                + horizontalSizeConfiguration + " " + verticalSizeConfigurations);
        synchronized (this) {
            ActivityRecord record = ActivityRecord.isInStackLocked(token);
            if (record == null) {
                throw new IllegalArgumentException("reportSizeConfigurations: ActivityRecord not "
                        + "found for: " + token);
            }
            record.setSizeConfigurations(horizontalSizeConfiguration,
                    verticalSizeConfigurations, smallestSizeConfigurations);
        }
    }

    @Override
    public final void backgroundResourcesReleased(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                ActivityStack stack = ActivityRecord.getStackLocked(token);
                if (stack != null) {
                    stack.backgroundResourcesReleased();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public final void notifyLaunchTaskBehindComplete(IBinder token) {
        mStackSupervisor.scheduleLaunchTaskBehindComplete(token);
    }

    @Override
    public final void notifyEnterAnimationComplete(IBinder token) {
        mHandler.sendMessage(mHandler.obtainMessage(ENTER_ANIMATION_COMPLETE_MSG, token));
    }

    @Override
    public String getCallingPackage(IBinder token) {
        synchronized (this) {
            ActivityRecord r = getCallingRecordLocked(token);
            return r != null ? r.info.packageName : null;
        }
    }

    @Override
    public ComponentName getCallingActivity(IBinder token) {
        synchronized (this) {
            ActivityRecord r = getCallingRecordLocked(token);
            return r != null ? r.intent.getComponent() : null;
        }
    }

    private ActivityRecord getCallingRecordLocked(IBinder token) {
        ActivityRecord r = ActivityRecord.isInStackLocked(token);
        if (r == null) {
            return null;
        }
        return r.resultTo;
    }

    @Override
    public ComponentName getActivityClassForToken(IBinder token) {
        synchronized(this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                return null;
            }
            return r.intent.getComponent();
        }
    }

    @Override
    public String getPackageForToken(IBinder token) {
        synchronized(this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                return null;
            }
            return r.packageName;
        }
    }

    @Override
    public boolean isRootVoiceInteraction(IBinder token) {
        synchronized(this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                return false;
            }
            return r.rootVoiceInteraction;
        }
    }

    @Override
    public IIntentSender getIntentSender(int type,
            String packageName, IBinder token, String resultWho,
            int requestCode, Intent[] intents, String[] resolvedTypes,
            int flags, Bundle bOptions, int userId) {
        enforceNotIsolatedCaller("getIntentSender");
        // Refuse possible leaked file descriptors
        if (intents != null) {
            if (intents.length < 1) {
                throw new IllegalArgumentException("Intents array length must be >= 1");
            }
            for (int i=0; i<intents.length; i++) {
                Intent intent = intents[i];
                if (intent != null) {
                    if (intent.hasFileDescriptors()) {
                        throw new IllegalArgumentException("File descriptors passed in Intent");
                    }
                    if (type == ActivityManager.INTENT_SENDER_BROADCAST &&
                            (intent.getFlags()&Intent.FLAG_RECEIVER_BOOT_UPGRADE) != 0) {
                        throw new IllegalArgumentException(
                                "Can't use FLAG_RECEIVER_BOOT_UPGRADE here");
                    }
                    intents[i] = new Intent(intent);
                }
            }
            if (resolvedTypes != null && resolvedTypes.length != intents.length) {
                throw new IllegalArgumentException(
                        "Intent array length does not match resolvedTypes length");
            }
        }
        if (bOptions != null) {
            if (bOptions.hasFileDescriptors()) {
                throw new IllegalArgumentException("File descriptors passed in options");
            }
        }

        synchronized(this) {
            int callingUid = Binder.getCallingUid();
            int origUserId = userId;
            userId = mUserController.handleIncomingUser(Binder.getCallingPid(), callingUid, userId,
                    type == ActivityManager.INTENT_SENDER_BROADCAST,
                    ALLOW_NON_FULL, "getIntentSender", null);
            if (origUserId == UserHandle.USER_CURRENT) {
                // We don't want to evaluate this until the pending intent is
                // actually executed.  However, we do want to always do the
                // security checking for it above.
                userId = UserHandle.USER_CURRENT;
            }
            try {
                if (callingUid != 0 && callingUid != Process.SYSTEM_UID) {
                    final int uid = AppGlobals.getPackageManager().getPackageUid(packageName,
                            MATCH_DEBUG_TRIAGED_MISSING, UserHandle.getUserId(callingUid));
                    if (!UserHandle.isSameApp(callingUid, uid)) {
                        String msg = "Permission Denial: getIntentSender() from pid="
                            + Binder.getCallingPid()
                            + ", uid=" + Binder.getCallingUid()
                            + ", (need uid=" + uid + ")"
                            + " is not allowed to send as package " + packageName;
                        Slog.w(TAG, msg);
                        throw new SecurityException(msg);
                    }
                }

                return getIntentSenderLocked(type, packageName, callingUid, userId,
                        token, resultWho, requestCode, intents, resolvedTypes, flags, bOptions);

            } catch (RemoteException e) {
                throw new SecurityException(e);
            }
        }
    }

    IIntentSender getIntentSenderLocked(int type, String packageName,
            int callingUid, int userId, IBinder token, String resultWho,
            int requestCode, Intent[] intents, String[] resolvedTypes, int flags,
            Bundle bOptions) {
        if (DEBUG_MU) Slog.v(TAG_MU, "getIntentSenderLocked(): uid=" + callingUid);
        ActivityRecord activity = null;
        if (type == ActivityManager.INTENT_SENDER_ACTIVITY_RESULT) {
            activity = ActivityRecord.isInStackLocked(token);
            if (activity == null) {
                Slog.w(TAG, "Failed createPendingResult: activity " + token + " not in any stack");
                return null;
            }
            if (activity.finishing) {
                Slog.w(TAG, "Failed createPendingResult: activity " + activity + " is finishing");
                return null;
            }
        }

        // We're going to be splicing together extras before sending, so we're
        // okay poking into any contained extras.
        if (intents != null) {
            for (int i = 0; i < intents.length; i++) {
                intents[i].setDefusable(true);
            }
        }
        Bundle.setDefusable(bOptions, true);

        final boolean noCreate = (flags&PendingIntent.FLAG_NO_CREATE) != 0;
        final boolean cancelCurrent = (flags&PendingIntent.FLAG_CANCEL_CURRENT) != 0;
        final boolean updateCurrent = (flags&PendingIntent.FLAG_UPDATE_CURRENT) != 0;
        flags &= ~(PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_CANCEL_CURRENT
                |PendingIntent.FLAG_UPDATE_CURRENT);

        PendingIntentRecord.Key key = new PendingIntentRecord.Key(
                type, packageName, activity, resultWho,
                requestCode, intents, resolvedTypes, flags, bOptions, userId);
        WeakReference<PendingIntentRecord> ref;
        ref = mIntentSenderRecords.get(key);
        PendingIntentRecord rec = ref != null ? ref.get() : null;
        if (rec != null) {
            if (!cancelCurrent) {
                if (updateCurrent) {
                    if (rec.key.requestIntent != null) {
                        rec.key.requestIntent.replaceExtras(intents != null ?
                                intents[intents.length - 1] : null);
                    }
                    if (intents != null) {
                        intents[intents.length-1] = rec.key.requestIntent;
                        rec.key.allIntents = intents;
                        rec.key.allResolvedTypes = resolvedTypes;
                    } else {
                        rec.key.allIntents = null;
                        rec.key.allResolvedTypes = null;
                    }
                }
                return rec;
            }
            rec.canceled = true;
            mIntentSenderRecords.remove(key);
        }
        if (noCreate) {
            return rec;
        }
        rec = new PendingIntentRecord(this, key, callingUid);
        mIntentSenderRecords.put(key, rec.ref);
        if (type == ActivityManager.INTENT_SENDER_ACTIVITY_RESULT) {
            if (activity.pendingResults == null) {
                activity.pendingResults
                        = new HashSet<WeakReference<PendingIntentRecord>>();
            }
            activity.pendingResults.add(rec.ref);
        }
        return rec;
    }

    @Override
    public int sendIntentSender(IIntentSender target, int code, Intent intent, String resolvedType,
            IIntentReceiver finishedReceiver, String requiredPermission, Bundle options) {
        if (target instanceof PendingIntentRecord) {
            return ((PendingIntentRecord)target).sendWithResult(code, intent, resolvedType,
                    finishedReceiver, requiredPermission, options);
        } else {
            if (intent == null) {
                // Weird case: someone has given us their own custom IIntentSender, and now
                // they have someone else trying to send to it but of course this isn't
                // really a PendingIntent, so there is no base Intent, and the caller isn't
                // supplying an Intent... but we never want to dispatch a null Intent to
                // a receiver, so um...  let's make something up.
                Slog.wtf(TAG, "Can't use null intent with direct IIntentSender call");
                intent = new Intent(Intent.ACTION_MAIN);
            }
            try {
                target.send(code, intent, resolvedType, null, requiredPermission, options);
            } catch (RemoteException e) {
            }
            // Platform code can rely on getting a result back when the send is done, but if
            // this intent sender is from outside of the system we can't rely on it doing that.
            // So instead we don't give it the result receiver, and instead just directly
            // report the finish immediately.
            if (finishedReceiver != null) {
                try {
                    finishedReceiver.performReceive(intent, 0,
                            null, null, false, false, UserHandle.getCallingUserId());
                } catch (RemoteException e) {
                }
            }
            return 0;
        }
    }

    /**
     * Whitelists {@code targetUid} to temporarily bypass Power Save mode.
     *
     * <p>{@code callerUid} must be allowed to request such whitelist by calling
     * {@link #addTempPowerSaveWhitelistGrantorUid(int)}.
     */
    void tempWhitelistAppForPowerSave(int callerPid, int callerUid, int targetUid, long duration) {
        if (DEBUG_WHITELISTS) {
            Slog.d(TAG, "tempWhitelistAppForPowerSave(" + callerPid + ", " + callerUid + ", "
                    + targetUid + ", " + duration + ")");
        }
        synchronized (mPidsSelfLocked) {
            final ProcessRecord pr = mPidsSelfLocked.get(callerPid);
            if (pr == null) {
                Slog.w(TAG, "tempWhitelistAppForPowerSave() no ProcessRecord for pid " + callerPid);
                return;
            }
            if (!pr.whitelistManager) {
                if (DEBUG_WHITELISTS) {
                    Slog.d(TAG, "tempWhitelistAppForPowerSave() for target " + targetUid + ": pid "
                            + callerPid + " is not allowed");
                }
                return;
            }
        }

        final long token = Binder.clearCallingIdentity();
        try {
            mLocalDeviceIdleController.addPowerSaveTempWhitelistAppDirect(targetUid, duration,
                    true, "pe from uid:" + callerUid);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void cancelIntentSender(IIntentSender sender) {
        if (!(sender instanceof PendingIntentRecord)) {
            return;
        }
        synchronized(this) {
            PendingIntentRecord rec = (PendingIntentRecord)sender;
            try {
                final int uid = AppGlobals.getPackageManager().getPackageUid(rec.key.packageName,
                        MATCH_DEBUG_TRIAGED_MISSING, UserHandle.getCallingUserId());
                if (!UserHandle.isSameApp(uid, Binder.getCallingUid())) {
                    String msg = "Permission Denial: cancelIntentSender() from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + Binder.getCallingUid()
                        + " is not allowed to cancel packges "
                        + rec.key.packageName;
                    Slog.w(TAG, msg);
                    throw new SecurityException(msg);
                }
            } catch (RemoteException e) {
                throw new SecurityException(e);
            }
            cancelIntentSenderLocked(rec, true);
        }
    }

    void cancelIntentSenderLocked(PendingIntentRecord rec, boolean cleanActivity) {
        rec.canceled = true;
        mIntentSenderRecords.remove(rec.key);
        if (cleanActivity && rec.key.activity != null) {
            rec.key.activity.pendingResults.remove(rec.ref);
        }
    }

    @Override
    public String getPackageForIntentSender(IIntentSender pendingResult) {
        if (!(pendingResult instanceof PendingIntentRecord)) {
            return null;
        }
        try {
            PendingIntentRecord res = (PendingIntentRecord)pendingResult;
            return res.key.packageName;
        } catch (ClassCastException e) {
        }
        return null;
    }

    @Override
    public int getUidForIntentSender(IIntentSender sender) {
        if (sender instanceof PendingIntentRecord) {
            try {
                PendingIntentRecord res = (PendingIntentRecord)sender;
                return res.uid;
            } catch (ClassCastException e) {
            }
        }
        return -1;
    }

    @Override
    public boolean isIntentSenderTargetedToPackage(IIntentSender pendingResult) {
        if (!(pendingResult instanceof PendingIntentRecord)) {
            return false;
        }
        try {
            PendingIntentRecord res = (PendingIntentRecord)pendingResult;
            if (res.key.allIntents == null) {
                return false;
            }
            for (int i=0; i<res.key.allIntents.length; i++) {
                Intent intent = res.key.allIntents[i];
                if (intent.getPackage() != null && intent.getComponent() != null) {
                    return false;
                }
            }
            return true;
        } catch (ClassCastException e) {
        }
        return false;
    }

    @Override
    public boolean isIntentSenderAnActivity(IIntentSender pendingResult) {
        if (!(pendingResult instanceof PendingIntentRecord)) {
            return false;
        }
        try {
            PendingIntentRecord res = (PendingIntentRecord)pendingResult;
            if (res.key.type == ActivityManager.INTENT_SENDER_ACTIVITY) {
                return true;
            }
            return false;
        } catch (ClassCastException e) {
        }
        return false;
    }

    @Override
    public Intent getIntentForIntentSender(IIntentSender pendingResult) {
        enforceCallingPermission(Manifest.permission.GET_INTENT_SENDER_INTENT,
                "getIntentForIntentSender()");
        if (!(pendingResult instanceof PendingIntentRecord)) {
            return null;
        }
        try {
            PendingIntentRecord res = (PendingIntentRecord)pendingResult;
            return res.key.requestIntent != null ? new Intent(res.key.requestIntent) : null;
        } catch (ClassCastException e) {
        }
        return null;
    }

    @Override
    public String getTagForIntentSender(IIntentSender pendingResult, String prefix) {
        if (!(pendingResult instanceof PendingIntentRecord)) {
            return null;
        }
        try {
            PendingIntentRecord res = (PendingIntentRecord)pendingResult;
            synchronized (this) {
                return getTagForIntentSenderLocked(res, prefix);
            }
        } catch (ClassCastException e) {
        }
        return null;
    }

    String getTagForIntentSenderLocked(PendingIntentRecord res, String prefix) {
        final Intent intent = res.key.requestIntent;
        if (intent != null) {
            if (res.lastTag != null && res.lastTagPrefix == prefix && (res.lastTagPrefix == null
                    || res.lastTagPrefix.equals(prefix))) {
                return res.lastTag;
            }
            res.lastTagPrefix = prefix;
            final StringBuilder sb = new StringBuilder(128);
            if (prefix != null) {
                sb.append(prefix);
            }
            if (intent.getAction() != null) {
                sb.append(intent.getAction());
            } else if (intent.getComponent() != null) {
                intent.getComponent().appendShortString(sb);
            } else {
                sb.append("?");
            }
            return res.lastTag = sb.toString();
        }
        return null;
    }

    @Override
    public void setProcessLimit(int max) {
        enforceCallingPermission(android.Manifest.permission.SET_PROCESS_LIMIT,
                "setProcessLimit()");
        synchronized (this) {
            mProcessLimit = max < 0 ? ProcessList.MAX_CACHED_APPS : max;
            mProcessLimitOverride = max;
        }
        trimApplications();
    }

    @Override
    public int getProcessLimit() {
        synchronized (this) {
            return mProcessLimitOverride;
        }
    }

    void foregroundTokenDied(ForegroundToken token) {
        synchronized (ActivityManagerService.this) {
            synchronized (mPidsSelfLocked) {
                ForegroundToken cur
                    = mForegroundProcesses.get(token.pid);
                if (cur != token) {
                    return;
                }
                mForegroundProcesses.remove(token.pid);
                ProcessRecord pr = mPidsSelfLocked.get(token.pid);
                if (pr == null) {
                    return;
                }
                pr.forcingToForeground = null;
                updateProcessForegroundLocked(pr, false, false);
            }
            updateOomAdjLocked();
        }
    }

    @Override
    public void setProcessForeground(IBinder token, int pid, boolean isForeground) {
        enforceCallingPermission(android.Manifest.permission.SET_PROCESS_LIMIT,
                "setProcessForeground()");
        synchronized(this) {
            boolean changed = false;

            synchronized (mPidsSelfLocked) {
                ProcessRecord pr = mPidsSelfLocked.get(pid);
                if (pr == null && isForeground) {
                    Slog.w(TAG, "setProcessForeground called on unknown pid: " + pid);
                    return;
                }
                ForegroundToken oldToken = mForegroundProcesses.get(pid);
                if (oldToken != null) {
                    oldToken.token.unlinkToDeath(oldToken, 0);
                    mForegroundProcesses.remove(pid);
                    if (pr != null) {
                        pr.forcingToForeground = null;
                    }
                    changed = true;
                }
                if (isForeground && token != null) {
                    ForegroundToken newToken = new ForegroundToken() {
                        @Override
                        public void binderDied() {
                            foregroundTokenDied(this);
                        }
                    };
                    newToken.pid = pid;
                    newToken.token = token;
                    try {
                        token.linkToDeath(newToken, 0);
                        mForegroundProcesses.put(pid, newToken);
                        pr.forcingToForeground = token;
                        changed = true;
                    } catch (RemoteException e) {
                        // If the process died while doing this, we will later
                        // do the cleanup with the process death link.
                    }
                }
            }

            if (changed) {
                updateOomAdjLocked();
            }
        }
    }

    @Override
    public boolean isAppForeground(int uid) throws RemoteException {
        synchronized (this) {
            UidRecord uidRec = mActiveUids.get(uid);
            if (uidRec == null || uidRec.idle) {
                return false;
            }
            return uidRec.curProcState <= ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
        }
    }

    // NOTE: this is an internal method used by the OnShellCommand implementation only and should
    // be guarded by permission checking.
    int getUidState(int uid) {
        synchronized (this) {
            UidRecord uidRec = mActiveUids.get(uid);
            return uidRec == null ? ActivityManager.PROCESS_STATE_NONEXISTENT : uidRec.curProcState;
        }
    }

    @Override
    public boolean isInMultiWindowMode(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized(this) {
                final ActivityRecord r = ActivityRecord.isInStackLocked(token);
                if (r == null) {
                    return false;
                }
                // An activity is consider to be in multi-window mode if its task isn't fullscreen.
                return !r.task.mFullscreen;
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public boolean isInPictureInPictureMode(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized(this) {
                final ActivityStack stack = ActivityRecord.getStackLocked(token);
                if (stack == null) {
                    return false;
                }
                return stack.mStackId == PINNED_STACK_ID;
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public void enterPictureInPictureMode(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized(this) {
                if (!mSupportsPictureInPicture) {
                    throw new IllegalStateException("enterPictureInPictureMode: "
                            + "Device doesn't support picture-in-picture mode.");
                }

                final ActivityRecord r = ActivityRecord.forTokenLocked(token);

                if (r == null) {
                    throw new IllegalStateException("enterPictureInPictureMode: "
                            + "Can't find activity for token=" + token);
                }

                if (!r.supportsPictureInPicture()) {
                    throw new IllegalArgumentException("enterPictureInPictureMode: "
                            + "Picture-In-Picture not supported for r=" + r);
                }

                // Use the default launch bounds for pinned stack if it doesn't exist yet or use the
                // current bounds.
                final ActivityStack pinnedStack = mStackSupervisor.getStack(PINNED_STACK_ID);
                final Rect bounds = (pinnedStack != null)
                        ? pinnedStack.mBounds : mDefaultPinnedStackBounds;

                mStackSupervisor.moveActivityToPinnedStackLocked(
                        r, "enterPictureInPictureMode", bounds);
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    // =========================================================
    // PROCESS INFO
    // =========================================================

    static class ProcessInfoService extends IProcessInfoService.Stub {
        final ActivityManagerService mActivityManagerService;
        ProcessInfoService(ActivityManagerService activityManagerService) {
            mActivityManagerService = activityManagerService;
        }

        @Override
        public void getProcessStatesFromPids(/*in*/ int[] pids, /*out*/ int[] states) {
            mActivityManagerService.getProcessStatesAndOomScoresForPIDs(
                    /*in*/ pids, /*out*/ states, null);
        }

        @Override
        public void getProcessStatesAndOomScoresFromPids(
                /*in*/ int[] pids, /*out*/ int[] states, /*out*/ int[] scores) {
            mActivityManagerService.getProcessStatesAndOomScoresForPIDs(
                    /*in*/ pids, /*out*/ states, /*out*/ scores);
        }
    }

    /**
     * For each PID in the given input array, write the current process state
     * for that process into the states array, or -1 to indicate that no
     * process with the given PID exists. If scores array is provided, write
     * the oom score for the process into the scores array, with INVALID_ADJ
     * indicating the PID doesn't exist.
     */
    public void getProcessStatesAndOomScoresForPIDs(
            /*in*/ int[] pids, /*out*/ int[] states, /*out*/ int[] scores) {
        if (scores != null) {
            enforceCallingPermission(android.Manifest.permission.GET_PROCESS_STATE_AND_OOM_SCORE,
                    "getProcessStatesAndOomScoresForPIDs()");
        }

        if (pids == null) {
            throw new NullPointerException("pids");
        } else if (states == null) {
            throw new NullPointerException("states");
        } else if (pids.length != states.length) {
            throw new IllegalArgumentException("pids and states arrays have different lengths!");
        } else if (scores != null && pids.length != scores.length) {
            throw new IllegalArgumentException("pids and scores arrays have different lengths!");
        }

        synchronized (mPidsSelfLocked) {
            for (int i = 0; i < pids.length; i++) {
                ProcessRecord pr = mPidsSelfLocked.get(pids[i]);
                states[i] = (pr == null) ? ActivityManager.PROCESS_STATE_NONEXISTENT :
                        pr.curProcState;
                if (scores != null) {
                    scores[i] = (pr == null) ? ProcessList.INVALID_ADJ : pr.curAdj;
                }
            }
        }
    }

    // =========================================================
    // PERMISSIONS
    // =========================================================

    static class PermissionController extends IPermissionController.Stub {
        ActivityManagerService mActivityManagerService;
        PermissionController(ActivityManagerService activityManagerService) {
            mActivityManagerService = activityManagerService;
        }

        @Override
        public boolean checkPermission(String permission, int pid, int uid) {
            return mActivityManagerService.checkPermission(permission, pid,
                    uid) == PackageManager.PERMISSION_GRANTED;
        }

        @Override
        public String[] getPackagesForUid(int uid) {
            return mActivityManagerService.mContext.getPackageManager()
                    .getPackagesForUid(uid);
        }

        @Override
        public boolean isRuntimePermission(String permission) {
            try {
                PermissionInfo info = mActivityManagerService.mContext.getPackageManager()
                        .getPermissionInfo(permission, 0);
                return info.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS;
            } catch (NameNotFoundException nnfe) {
                Slog.e(TAG, "No such permission: "+ permission, nnfe);
            }
            return false;
        }
    }

    class IntentFirewallInterface implements IntentFirewall.AMSInterface {
        @Override
        public int checkComponentPermission(String permission, int pid, int uid,
                int owningUid, boolean exported) {
            return ActivityManagerService.this.checkComponentPermission(permission, pid, uid,
                    owningUid, exported);
        }

        @Override
        public Object getAMSLock() {
            return ActivityManagerService.this;
        }
    }

    /**
     * This can be called with or without the global lock held.
     */
    int checkComponentPermission(String permission, int pid, int uid,
            int owningUid, boolean exported) {
        if (pid == MY_PID) {
            return PackageManager.PERMISSION_GRANTED;
        }
        return ActivityManager.checkComponentPermission(permission, uid,
                owningUid, exported);
    }

    /**
     * As the only public entry point for permissions checking, this method
     * can enforce the semantic that requesting a check on a null global
     * permission is automatically denied.  (Internally a null permission
     * string is used when calling {@link #checkComponentPermission} in cases
     * when only uid-based security is needed.)
     *
     * This can be called with or without the global lock held.
     */
    @Override
    public int checkPermission(String permission, int pid, int uid) {
        if (permission == null) {
            return PackageManager.PERMISSION_DENIED;
        }
        return checkComponentPermission(permission, pid, uid, -1, true);
    }

    @Override
    public int checkPermissionWithToken(String permission, int pid, int uid, IBinder callerToken) {
        if (permission == null) {
            return PackageManager.PERMISSION_DENIED;
        }

        // We might be performing an operation on behalf of an indirect binder
        // invocation, e.g. via {@link #openContentUri}.  Check and adjust the
        // client identity accordingly before proceeding.
        Identity tlsIdentity = sCallerIdentity.get();
        if (tlsIdentity != null && tlsIdentity.token == callerToken) {
            Slog.d(TAG, "checkComponentPermission() adjusting {pid,uid} to {"
                    + tlsIdentity.pid + "," + tlsIdentity.uid + "}");
            uid = tlsIdentity.uid;
            pid = tlsIdentity.pid;
        }

        return checkComponentPermission(permission, pid, uid, -1, true);
    }

    /**
     * Binder IPC calls go through the public entry point.
     * This can be called with or without the global lock held.
     */
    int checkCallingPermission(String permission) {
        return checkPermission(permission,
                Binder.getCallingPid(),
                UserHandle.getAppId(Binder.getCallingUid()));
    }

    /**
     * This can be called with or without the global lock held.
     */
    void enforceCallingPermission(String permission, String func) {
        if (checkCallingPermission(permission)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String msg = "Permission Denial: " + func + " from pid="
                + Binder.getCallingPid()
                + ", uid=" + Binder.getCallingUid()
                + " requires " + permission;
        Slog.w(TAG, msg);
        throw new SecurityException(msg);
    }

    /**
     * Determine if UID is holding permissions required to access {@link Uri} in
     * the given {@link ProviderInfo}. Final permission checking is always done
     * in {@link ContentProvider}.
     */
    private final boolean checkHoldingPermissionsLocked(
            IPackageManager pm, ProviderInfo pi, GrantUri grantUri, int uid, final int modeFlags) {
        if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                "checkHoldingPermissionsLocked: uri=" + grantUri + " uid=" + uid);
        if (UserHandle.getUserId(uid) != grantUri.sourceUserId) {
            if (ActivityManager.checkComponentPermission(INTERACT_ACROSS_USERS, uid, -1, true)
                    != PERMISSION_GRANTED) {
                return false;
            }
        }
        return checkHoldingPermissionsInternalLocked(pm, pi, grantUri, uid, modeFlags, true);
    }

    private final boolean checkHoldingPermissionsInternalLocked(IPackageManager pm, ProviderInfo pi,
            GrantUri grantUri, int uid, final int modeFlags, boolean considerUidPermissions) {
        if (pi.applicationInfo.uid == uid) {
            return true;
        } else if (!pi.exported) {
            return false;
        }

        boolean readMet = (modeFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0;
        boolean writeMet = (modeFlags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == 0;
        try {
            // check if target holds top-level <provider> permissions
            if (!readMet && pi.readPermission != null && considerUidPermissions
                    && (pm.checkUidPermission(pi.readPermission, uid) == PERMISSION_GRANTED)) {
                readMet = true;
            }
            if (!writeMet && pi.writePermission != null && considerUidPermissions
                    && (pm.checkUidPermission(pi.writePermission, uid) == PERMISSION_GRANTED)) {
                writeMet = true;
            }

            // track if unprotected read/write is allowed; any denied
            // <path-permission> below removes this ability
            boolean allowDefaultRead = pi.readPermission == null;
            boolean allowDefaultWrite = pi.writePermission == null;

            // check if target holds any <path-permission> that match uri
            final PathPermission[] pps = pi.pathPermissions;
            if (pps != null) {
                final String path = grantUri.uri.getPath();
                int i = pps.length;
                while (i > 0 && (!readMet || !writeMet)) {
                    i--;
                    PathPermission pp = pps[i];
                    if (pp.match(path)) {
                        if (!readMet) {
                            final String pprperm = pp.getReadPermission();
                            if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                                    "Checking read perm for " + pprperm + " for " + pp.getPath()
                                    + ": match=" + pp.match(path)
                                    + " check=" + pm.checkUidPermission(pprperm, uid));
                            if (pprperm != null) {
                                if (considerUidPermissions && pm.checkUidPermission(pprperm, uid)
                                        == PERMISSION_GRANTED) {
                                    readMet = true;
                                } else {
                                    allowDefaultRead = false;
                                }
                            }
                        }
                        if (!writeMet) {
                            final String ppwperm = pp.getWritePermission();
                            if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                                    "Checking write perm " + ppwperm + " for " + pp.getPath()
                                    + ": match=" + pp.match(path)
                                    + " check=" + pm.checkUidPermission(ppwperm, uid));
                            if (ppwperm != null) {
                                if (considerUidPermissions && pm.checkUidPermission(ppwperm, uid)
                                        == PERMISSION_GRANTED) {
                                    writeMet = true;
                                } else {
                                    allowDefaultWrite = false;
                                }
                            }
                        }
                    }
                }
            }

            // grant unprotected <provider> read/write, if not blocked by
            // <path-permission> above
            if (allowDefaultRead) readMet = true;
            if (allowDefaultWrite) writeMet = true;

        } catch (RemoteException e) {
            return false;
        }

        return readMet && writeMet;
    }

    public int getAppStartMode(int uid, String packageName) {
        synchronized (this) {
            return checkAllowBackgroundLocked(uid, packageName, -1, true);
        }
    }

    int checkAllowBackgroundLocked(int uid, String packageName, int callingPid,
            boolean allowWhenForeground) {
        UidRecord uidRec = mActiveUids.get(uid);
        if (!mLenientBackgroundCheck) {
            if (!allowWhenForeground || uidRec == null
                    || uidRec.curProcState >= ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND) {
                if (mAppOpsService.noteOperation(AppOpsManager.OP_RUN_IN_BACKGROUND, uid,
                        packageName) != AppOpsManager.MODE_ALLOWED) {
                    return ActivityManager.APP_START_MODE_DELAYED;
                }
            }

        } else if (uidRec == null || uidRec.idle) {
            if (callingPid >= 0) {
                ProcessRecord proc;
                synchronized (mPidsSelfLocked) {
                    proc = mPidsSelfLocked.get(callingPid);
                }
                if (proc != null && proc.curProcState < ActivityManager.PROCESS_STATE_RECEIVER) {
                    // Whoever is instigating this is in the foreground, so we will allow it
                    // to go through.
                    return ActivityManager.APP_START_MODE_NORMAL;
                }
            }
            if (mAppOpsService.noteOperation(AppOpsManager.OP_RUN_IN_BACKGROUND, uid, packageName)
                    != AppOpsManager.MODE_ALLOWED) {
                return ActivityManager.APP_START_MODE_DELAYED;
            }
        }
        return ActivityManager.APP_START_MODE_NORMAL;
    }

    private ProviderInfo getProviderInfoLocked(String authority, int userHandle, int pmFlags) {
        ProviderInfo pi = null;
        ContentProviderRecord cpr = mProviderMap.getProviderByName(authority, userHandle);
        if (cpr != null) {
            pi = cpr.info;
        } else {
            try {
                pi = AppGlobals.getPackageManager().resolveContentProvider(
                        authority, PackageManager.GET_URI_PERMISSION_PATTERNS | pmFlags,
                        userHandle);
            } catch (RemoteException ex) {
            }
        }
        return pi;
    }

    private UriPermission findUriPermissionLocked(int targetUid, GrantUri grantUri) {
        final ArrayMap<GrantUri, UriPermission> targetUris = mGrantedUriPermissions.get(targetUid);
        if (targetUris != null) {
            return targetUris.get(grantUri);
        }
        return null;
    }

    private UriPermission findOrCreateUriPermissionLocked(String sourcePkg,
            String targetPkg, int targetUid, GrantUri grantUri) {
        ArrayMap<GrantUri, UriPermission> targetUris = mGrantedUriPermissions.get(targetUid);
        if (targetUris == null) {
            targetUris = Maps.newArrayMap();
            mGrantedUriPermissions.put(targetUid, targetUris);
        }

        UriPermission perm = targetUris.get(grantUri);
        if (perm == null) {
            perm = new UriPermission(sourcePkg, targetPkg, targetUid, grantUri);
            targetUris.put(grantUri, perm);
        }

        return perm;
    }

    private final boolean checkUriPermissionLocked(GrantUri grantUri, int uid,
            final int modeFlags) {
        final boolean persistable = (modeFlags & Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0;
        final int minStrength = persistable ? UriPermission.STRENGTH_PERSISTABLE
                : UriPermission.STRENGTH_OWNED;

        // Root gets to do everything.
        if (uid == 0) {
            return true;
        }

        final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(uid);
        if (perms == null) return false;

        // First look for exact match
        final UriPermission exactPerm = perms.get(grantUri);
        if (exactPerm != null && exactPerm.getStrength(modeFlags) >= minStrength) {
            return true;
        }

        // No exact match, look for prefixes
        final int N = perms.size();
        for (int i = 0; i < N; i++) {
            final UriPermission perm = perms.valueAt(i);
            if (perm.uri.prefix && grantUri.uri.isPathPrefixMatch(perm.uri.uri)
                    && perm.getStrength(modeFlags) >= minStrength) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param uri This uri must NOT contain an embedded userId.
     * @param userId The userId in which the uri is to be resolved.
     */
    @Override
    public int checkUriPermission(Uri uri, int pid, int uid,
            final int modeFlags, int userId, IBinder callerToken) {
        enforceNotIsolatedCaller("checkUriPermission");

        // Another redirected-binder-call permissions check as in
        // {@link checkPermissionWithToken}.
        Identity tlsIdentity = sCallerIdentity.get();
        if (tlsIdentity != null && tlsIdentity.token == callerToken) {
            uid = tlsIdentity.uid;
            pid = tlsIdentity.pid;
        }

        // Our own process gets to do everything.
        if (pid == MY_PID) {
            return PackageManager.PERMISSION_GRANTED;
        }
        synchronized (this) {
            return checkUriPermissionLocked(new GrantUri(userId, uri, false), uid, modeFlags)
                    ? PackageManager.PERMISSION_GRANTED
                    : PackageManager.PERMISSION_DENIED;
        }
    }

    /**
     * Check if the targetPkg can be granted permission to access uri by
     * the callingUid using the given modeFlags.  Throws a security exception
     * if callingUid is not allowed to do this.  Returns the uid of the target
     * if the URI permission grant should be performed; returns -1 if it is not
     * needed (for example targetPkg already has permission to access the URI).
     * If you already know the uid of the target, you can supply it in
     * lastTargetUid else set that to -1.
     */
    int checkGrantUriPermissionLocked(int callingUid, String targetPkg, GrantUri grantUri,
            final int modeFlags, int lastTargetUid) {
        if (!Intent.isAccessUriMode(modeFlags)) {
            return -1;
        }

        if (targetPkg != null) {
            if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                    "Checking grant " + targetPkg + " permission to " + grantUri);
        }

        final IPackageManager pm = AppGlobals.getPackageManager();

        // If this is not a content: uri, we can't do anything with it.
        if (!ContentResolver.SCHEME_CONTENT.equals(grantUri.uri.getScheme())) {
            if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                    "Can't grant URI permission for non-content URI: " + grantUri);
            return -1;
        }

        final String authority = grantUri.uri.getAuthority();
        final ProviderInfo pi = getProviderInfoLocked(authority, grantUri.sourceUserId,
                MATCH_DEBUG_TRIAGED_MISSING);
        if (pi == null) {
            Slog.w(TAG, "No content provider found for permission check: " +
                    grantUri.uri.toSafeString());
            return -1;
        }

        int targetUid = lastTargetUid;
        if (targetUid < 0 && targetPkg != null) {
            try {
                targetUid = pm.getPackageUid(targetPkg, MATCH_DEBUG_TRIAGED_MISSING,
                        UserHandle.getUserId(callingUid));
                if (targetUid < 0) {
                    if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                            "Can't grant URI permission no uid for: " + targetPkg);
                    return -1;
                }
            } catch (RemoteException ex) {
                return -1;
            }
        }

        if (targetUid >= 0) {
            // First...  does the target actually need this permission?
            if (checkHoldingPermissionsLocked(pm, pi, grantUri, targetUid, modeFlags)) {
                // No need to grant the target this permission.
                if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                        "Target " + targetPkg + " already has full permission to " + grantUri);
                return -1;
            }
        } else {
            // First...  there is no target package, so can anyone access it?
            boolean allowed = pi.exported;
            if ((modeFlags&Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                if (pi.readPermission != null) {
                    allowed = false;
                }
            }
            if ((modeFlags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                if (pi.writePermission != null) {
                    allowed = false;
                }
            }
            if (allowed) {
                return -1;
            }
        }

        /* There is a special cross user grant if:
         * - The target is on another user.
         * - Apps on the current user can access the uri without any uid permissions.
         * In this case, we grant a uri permission, even if the ContentProvider does not normally
         * grant uri permissions.
         */
        boolean specialCrossUserGrant = UserHandle.getUserId(targetUid) != grantUri.sourceUserId
                && checkHoldingPermissionsInternalLocked(pm, pi, grantUri, callingUid,
                modeFlags, false /*without considering the uid permissions*/);

        // Second...  is the provider allowing granting of URI permissions?
        if (!specialCrossUserGrant) {
            if (!pi.grantUriPermissions) {
                throw new SecurityException("Provider " + pi.packageName
                        + "/" + pi.name
                        + " does not allow granting of Uri permissions (uri "
                        + grantUri + ")");
            }
            if (pi.uriPermissionPatterns != null) {
                final int N = pi.uriPermissionPatterns.length;
                boolean allowed = false;
                for (int i=0; i<N; i++) {
                    if (pi.uriPermissionPatterns[i] != null
                            && pi.uriPermissionPatterns[i].match(grantUri.uri.getPath())) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    throw new SecurityException("Provider " + pi.packageName
                            + "/" + pi.name
                            + " does not allow granting of permission to path of Uri "
                            + grantUri);
                }
            }
        }

        // Third...  does the caller itself have permission to access
        // this uri?
        if (UserHandle.getAppId(callingUid) != Process.SYSTEM_UID) {
            if (!checkHoldingPermissionsLocked(pm, pi, grantUri, callingUid, modeFlags)) {
                // Require they hold a strong enough Uri permission
                if (!checkUriPermissionLocked(grantUri, callingUid, modeFlags)) {
                    throw new SecurityException("Uid " + callingUid
                            + " does not have permission to uri " + grantUri);
                }
            }
        }
        return targetUid;
    }

    /**
     * @param uri This uri must NOT contain an embedded userId.
     * @param userId The userId in which the uri is to be resolved.
     */
    @Override
    public int checkGrantUriPermission(int callingUid, String targetPkg, Uri uri,
            final int modeFlags, int userId) {
        enforceNotIsolatedCaller("checkGrantUriPermission");
        synchronized(this) {
            return checkGrantUriPermissionLocked(callingUid, targetPkg,
                    new GrantUri(userId, uri, false), modeFlags, -1);
        }
    }

    void grantUriPermissionUncheckedLocked(int targetUid, String targetPkg, GrantUri grantUri,
            final int modeFlags, UriPermissionOwner owner) {
        if (!Intent.isAccessUriMode(modeFlags)) {
            return;
        }

        // So here we are: the caller has the assumed permission
        // to the uri, and the target doesn't.  Let's now give this to
        // the target.

        if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                "Granting " + targetPkg + "/" + targetUid + " permission to " + grantUri);

        final String authority = grantUri.uri.getAuthority();
        final ProviderInfo pi = getProviderInfoLocked(authority, grantUri.sourceUserId,
                MATCH_DEBUG_TRIAGED_MISSING);
        if (pi == null) {
            Slog.w(TAG, "No content provider found for grant: " + grantUri.toSafeString());
            return;
        }

        if ((modeFlags & Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) != 0) {
            grantUri.prefix = true;
        }
        final UriPermission perm = findOrCreateUriPermissionLocked(
                pi.packageName, targetPkg, targetUid, grantUri);
        perm.grantModes(modeFlags, owner);
    }

    void grantUriPermissionLocked(int callingUid, String targetPkg, GrantUri grantUri,
            final int modeFlags, UriPermissionOwner owner, int targetUserId) {
        if (targetPkg == null) {
            throw new NullPointerException("targetPkg");
        }
        int targetUid;
        final IPackageManager pm = AppGlobals.getPackageManager();
        try {
            targetUid = pm.getPackageUid(targetPkg, MATCH_DEBUG_TRIAGED_MISSING, targetUserId);
        } catch (RemoteException ex) {
            return;
        }

        targetUid = checkGrantUriPermissionLocked(callingUid, targetPkg, grantUri, modeFlags,
                targetUid);
        if (targetUid < 0) {
            return;
        }

        grantUriPermissionUncheckedLocked(targetUid, targetPkg, grantUri, modeFlags,
                owner);
    }

    static class NeededUriGrants extends ArrayList<GrantUri> {
        final String targetPkg;
        final int targetUid;
        final int flags;

        NeededUriGrants(String targetPkg, int targetUid, int flags) {
            this.targetPkg = targetPkg;
            this.targetUid = targetUid;
            this.flags = flags;
        }
    }

    /**
     * Like checkGrantUriPermissionLocked, but takes an Intent.
     */
    NeededUriGrants checkGrantUriPermissionFromIntentLocked(int callingUid,
            String targetPkg, Intent intent, int mode, NeededUriGrants needed, int targetUserId) {
        if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                "Checking URI perm to data=" + (intent != null ? intent.getData() : null)
                + " clip=" + (intent != null ? intent.getClipData() : null)
                + " from " + intent + "; flags=0x"
                + Integer.toHexString(intent != null ? intent.getFlags() : 0));

        if (targetPkg == null) {
            throw new NullPointerException("targetPkg");
        }

        if (intent == null) {
            return null;
        }
        Uri data = intent.getData();
        ClipData clip = intent.getClipData();
        if (data == null && clip == null) {
            return null;
        }
        // Default userId for uris in the intent (if they don't specify it themselves)
        int contentUserHint = intent.getContentUserHint();
        if (contentUserHint == UserHandle.USER_CURRENT) {
            contentUserHint = UserHandle.getUserId(callingUid);
        }
        final IPackageManager pm = AppGlobals.getPackageManager();
        int targetUid;
        if (needed != null) {
            targetUid = needed.targetUid;
        } else {
            try {
                targetUid = pm.getPackageUid(targetPkg, MATCH_DEBUG_TRIAGED_MISSING,
                        targetUserId);
            } catch (RemoteException ex) {
                return null;
            }
            if (targetUid < 0) {
                if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                        "Can't grant URI permission no uid for: " + targetPkg
                        + " on user " + targetUserId);
                return null;
            }
        }
        if (data != null) {
            GrantUri grantUri = GrantUri.resolve(contentUserHint, data);
            targetUid = checkGrantUriPermissionLocked(callingUid, targetPkg, grantUri, mode,
                    targetUid);
            if (targetUid > 0) {
                if (needed == null) {
                    needed = new NeededUriGrants(targetPkg, targetUid, mode);
                }
                needed.add(grantUri);
            }
        }
        if (clip != null) {
            for (int i=0; i<clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) {
                    GrantUri grantUri = GrantUri.resolve(contentUserHint, uri);
                    targetUid = checkGrantUriPermissionLocked(callingUid, targetPkg, grantUri, mode,
                            targetUid);
                    if (targetUid > 0) {
                        if (needed == null) {
                            needed = new NeededUriGrants(targetPkg, targetUid, mode);
                        }
                        needed.add(grantUri);
                    }
                } else {
                    Intent clipIntent = clip.getItemAt(i).getIntent();
                    if (clipIntent != null) {
                        NeededUriGrants newNeeded = checkGrantUriPermissionFromIntentLocked(
                                callingUid, targetPkg, clipIntent, mode, needed, targetUserId);
                        if (newNeeded != null) {
                            needed = newNeeded;
                        }
                    }
                }
            }
        }

        return needed;
    }

    /**
     * Like grantUriPermissionUncheckedLocked, but takes an Intent.
     */
    void grantUriPermissionUncheckedFromIntentLocked(NeededUriGrants needed,
            UriPermissionOwner owner) {
        if (needed != null) {
            for (int i=0; i<needed.size(); i++) {
                GrantUri grantUri = needed.get(i);
                grantUriPermissionUncheckedLocked(needed.targetUid, needed.targetPkg,
                        grantUri, needed.flags, owner);
            }
        }
    }

    void grantUriPermissionFromIntentLocked(int callingUid,
            String targetPkg, Intent intent, UriPermissionOwner owner, int targetUserId) {
        NeededUriGrants needed = checkGrantUriPermissionFromIntentLocked(callingUid, targetPkg,
                intent, intent != null ? intent.getFlags() : 0, null, targetUserId);
        if (needed == null) {
            return;
        }

        grantUriPermissionUncheckedFromIntentLocked(needed, owner);
    }

    /**
     * @param uri This uri must NOT contain an embedded userId.
     * @param userId The userId in which the uri is to be resolved.
     */
    @Override
    public void grantUriPermission(IApplicationThread caller, String targetPkg, Uri uri,
            final int modeFlags, int userId) {
        enforceNotIsolatedCaller("grantUriPermission");
        GrantUri grantUri = new GrantUri(userId, uri, false);
        synchronized(this) {
            final ProcessRecord r = getRecordForAppLocked(caller);
            if (r == null) {
                throw new SecurityException("Unable to find app for caller "
                        + caller
                        + " when granting permission to uri " + grantUri);
            }
            if (targetPkg == null) {
                throw new IllegalArgumentException("null target");
            }
            if (grantUri == null) {
                throw new IllegalArgumentException("null uri");
            }

            Preconditions.checkFlagsArgument(modeFlags, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

            grantUriPermissionLocked(r.uid, targetPkg, grantUri, modeFlags, null,
                    UserHandle.getUserId(r.uid));
        }
    }

    void removeUriPermissionIfNeededLocked(UriPermission perm) {
        if (perm.modeFlags == 0) {
            final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(
                    perm.targetUid);
            if (perms != null) {
                if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                        "Removing " + perm.targetUid + " permission to " + perm.uri);

                perms.remove(perm.uri);
                if (perms.isEmpty()) {
                    mGrantedUriPermissions.remove(perm.targetUid);
                }
            }
        }
    }

    private void revokeUriPermissionLocked(int callingUid, GrantUri grantUri, final int modeFlags) {
        if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                "Revoking all granted permissions to " + grantUri);

        final IPackageManager pm = AppGlobals.getPackageManager();
        final String authority = grantUri.uri.getAuthority();
        final ProviderInfo pi = getProviderInfoLocked(authority, grantUri.sourceUserId,
                MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);
        if (pi == null) {
            Slog.w(TAG, "No content provider found for permission revoke: "
                    + grantUri.toSafeString());
            return;
        }

        // Does the caller have this permission on the URI?
        if (!checkHoldingPermissionsLocked(pm, pi, grantUri, callingUid, modeFlags)) {
            // If they don't have direct access to the URI, then revoke any
            // ownerless URI permissions that have been granted to them.
            final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(callingUid);
            if (perms != null) {
                boolean persistChanged = false;
                for (Iterator<UriPermission> it = perms.values().iterator(); it.hasNext();) {
                    final UriPermission perm = it.next();
                    if (perm.uri.sourceUserId == grantUri.sourceUserId
                            && perm.uri.uri.isPathPrefixMatch(grantUri.uri)) {
                        if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                                "Revoking non-owned " + perm.targetUid
                                + " permission to " + perm.uri);
                        persistChanged |= perm.revokeModes(
                                modeFlags | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, false);
                        if (perm.modeFlags == 0) {
                            it.remove();
                        }
                    }
                }
                if (perms.isEmpty()) {
                    mGrantedUriPermissions.remove(callingUid);
                }
                if (persistChanged) {
                    schedulePersistUriGrants();
                }
            }
            return;
        }

        boolean persistChanged = false;

        // Go through all of the permissions and remove any that match.
        int N = mGrantedUriPermissions.size();
        for (int i = 0; i < N; i++) {
            final int targetUid = mGrantedUriPermissions.keyAt(i);
            final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);

            for (Iterator<UriPermission> it = perms.values().iterator(); it.hasNext();) {
                final UriPermission perm = it.next();
                if (perm.uri.sourceUserId == grantUri.sourceUserId
                        && perm.uri.uri.isPathPrefixMatch(grantUri.uri)) {
                    if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                                "Revoking " + perm.targetUid + " permission to " + perm.uri);
                    persistChanged |= perm.revokeModes(
                            modeFlags | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, true);
                    if (perm.modeFlags == 0) {
                        it.remove();
                    }
                }
            }

            if (perms.isEmpty()) {
                mGrantedUriPermissions.remove(targetUid);
                N--;
                i--;
            }
        }

        if (persistChanged) {
            schedulePersistUriGrants();
        }
    }

    /**
     * @param uri This uri must NOT contain an embedded userId.
     * @param userId The userId in which the uri is to be resolved.
     */
    @Override
    public void revokeUriPermission(IApplicationThread caller, Uri uri, final int modeFlags,
            int userId) {
        enforceNotIsolatedCaller("revokeUriPermission");
        synchronized(this) {
            final ProcessRecord r = getRecordForAppLocked(caller);
            if (r == null) {
                throw new SecurityException("Unable to find app for caller "
                        + caller
                        + " when revoking permission to uri " + uri);
            }
            if (uri == null) {
                Slog.w(TAG, "revokeUriPermission: null uri");
                return;
            }

            if (!Intent.isAccessUriMode(modeFlags)) {
                return;
            }

            final String authority = uri.getAuthority();
            final ProviderInfo pi = getProviderInfoLocked(authority, userId,
                    MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE);
            if (pi == null) {
                Slog.w(TAG, "No content provider found for permission revoke: "
                        + uri.toSafeString());
                return;
            }

            revokeUriPermissionLocked(r.uid, new GrantUri(userId, uri, false), modeFlags);
        }
    }

    /**
     * Remove any {@link UriPermission} granted <em>from</em> or <em>to</em> the
     * given package.
     *
     * @param packageName Package name to match, or {@code null} to apply to all
     *            packages.
     * @param userHandle User to match, or {@link UserHandle#USER_ALL} to apply
     *            to all users.
     * @param persistable If persistable grants should be removed.
     */
    private void removeUriPermissionsForPackageLocked(
            String packageName, int userHandle, boolean persistable) {
        if (userHandle == UserHandle.USER_ALL && packageName == null) {
            throw new IllegalArgumentException("Must narrow by either package or user");
        }

        boolean persistChanged = false;

        int N = mGrantedUriPermissions.size();
        for (int i = 0; i < N; i++) {
            final int targetUid = mGrantedUriPermissions.keyAt(i);
            final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);

            // Only inspect grants matching user
            if (userHandle == UserHandle.USER_ALL
                    || userHandle == UserHandle.getUserId(targetUid)) {
                for (Iterator<UriPermission> it = perms.values().iterator(); it.hasNext();) {
                    final UriPermission perm = it.next();

                    // Only inspect grants matching package
                    if (packageName == null || perm.sourcePkg.equals(packageName)
                            || perm.targetPkg.equals(packageName)) {
                        // Hacky solution as part of fixing a security bug; ignore
                        // grants associated with DownloadManager so we don't have
                        // to immediately launch it to regrant the permissions
                        if (Downloads.Impl.AUTHORITY.equals(perm.uri.uri.getAuthority())
                                && !persistable) continue;

                        persistChanged |= perm.revokeModes(persistable
                                ? ~0 : ~Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION, true);

                        // Only remove when no modes remain; any persisted grants
                        // will keep this alive.
                        if (perm.modeFlags == 0) {
                            it.remove();
                        }
                    }
                }

                if (perms.isEmpty()) {
                    mGrantedUriPermissions.remove(targetUid);
                    N--;
                    i--;
                }
            }
        }

        if (persistChanged) {
            schedulePersistUriGrants();
        }
    }

    @Override
    public IBinder newUriPermissionOwner(String name) {
        enforceNotIsolatedCaller("newUriPermissionOwner");
        synchronized(this) {
            UriPermissionOwner owner = new UriPermissionOwner(this, name);
            return owner.getExternalTokenLocked();
        }
    }

    @Override
    public IBinder getUriPermissionOwnerForActivity(IBinder activityToken) {
        enforceNotIsolatedCaller("getUriPermissionOwnerForActivity");
        synchronized(this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(activityToken);
            if (r == null) {
                throw new IllegalArgumentException("Activity does not exist; token="
                        + activityToken);
            }
            return r.getUriPermissionsLocked().getExternalTokenLocked();
        }
    }
    /**
     * @param uri This uri must NOT contain an embedded userId.
     * @param sourceUserId The userId in which the uri is to be resolved.
     * @param targetUserId The userId of the app that receives the grant.
     */
    @Override
    public void grantUriPermissionFromOwner(IBinder token, int fromUid, String targetPkg, Uri uri,
            final int modeFlags, int sourceUserId, int targetUserId) {
        targetUserId = mUserController.handleIncomingUser(Binder.getCallingPid(),
                Binder.getCallingUid(), targetUserId, false, ALLOW_FULL_ONLY,
                "grantUriPermissionFromOwner", null);
        synchronized(this) {
            UriPermissionOwner owner = UriPermissionOwner.fromExternalToken(token);
            if (owner == null) {
                throw new IllegalArgumentException("Unknown owner: " + token);
            }
            if (fromUid != Binder.getCallingUid()) {
                if (Binder.getCallingUid() != Process.myUid()) {
                    // Only system code can grant URI permissions on behalf
                    // of other users.
                    throw new SecurityException("nice try");
                }
            }
            if (targetPkg == null) {
                throw new IllegalArgumentException("null target");
            }
            if (uri == null) {
                throw new IllegalArgumentException("null uri");
            }

            grantUriPermissionLocked(fromUid, targetPkg, new GrantUri(sourceUserId, uri, false),
                    modeFlags, owner, targetUserId);
        }
    }

    /**
     * @param uri This uri must NOT contain an embedded userId.
     * @param userId The userId in which the uri is to be resolved.
     */
    @Override
    public void revokeUriPermissionFromOwner(IBinder token, Uri uri, int mode, int userId) {
        synchronized(this) {
            UriPermissionOwner owner = UriPermissionOwner.fromExternalToken(token);
            if (owner == null) {
                throw new IllegalArgumentException("Unknown owner: " + token);
            }

            if (uri == null) {
                owner.removeUriPermissionsLocked(mode);
            } else {
                final boolean prefix = (mode & Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) != 0;
                owner.removeUriPermissionLocked(new GrantUri(userId, uri, prefix), mode);
            }
        }
    }

    private void schedulePersistUriGrants() {
        if (!mHandler.hasMessages(PERSIST_URI_GRANTS_MSG)) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(PERSIST_URI_GRANTS_MSG),
                    10 * DateUtils.SECOND_IN_MILLIS);
        }
    }

    private void writeGrantedUriPermissions() {
        if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION, "writeGrantedUriPermissions()");

        // Snapshot permissions so we can persist without lock
        ArrayList<UriPermission.Snapshot> persist = Lists.newArrayList();
        synchronized (this) {
            final int size = mGrantedUriPermissions.size();
            for (int i = 0; i < size; i++) {
                final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);
                for (UriPermission perm : perms.values()) {
                    if (perm.persistedModeFlags != 0) {
                        persist.add(perm.snapshot());
                    }
                }
            }
        }

        FileOutputStream fos = null;
        try {
            fos = mGrantFile.startWrite();

            XmlSerializer out = new FastXmlSerializer();
            out.setOutput(fos, StandardCharsets.UTF_8.name());
            out.startDocument(null, true);
            out.startTag(null, TAG_URI_GRANTS);
            for (UriPermission.Snapshot perm : persist) {
                out.startTag(null, TAG_URI_GRANT);
                writeIntAttribute(out, ATTR_SOURCE_USER_ID, perm.uri.sourceUserId);
                writeIntAttribute(out, ATTR_TARGET_USER_ID, perm.targetUserId);
                out.attribute(null, ATTR_SOURCE_PKG, perm.sourcePkg);
                out.attribute(null, ATTR_TARGET_PKG, perm.targetPkg);
                out.attribute(null, ATTR_URI, String.valueOf(perm.uri.uri));
                writeBooleanAttribute(out, ATTR_PREFIX, perm.uri.prefix);
                writeIntAttribute(out, ATTR_MODE_FLAGS, perm.persistedModeFlags);
                writeLongAttribute(out, ATTR_CREATED_TIME, perm.persistedCreateTime);
                out.endTag(null, TAG_URI_GRANT);
            }
            out.endTag(null, TAG_URI_GRANTS);
            out.endDocument();

            mGrantFile.finishWrite(fos);
        } catch (IOException e) {
            if (fos != null) {
                mGrantFile.failWrite(fos);
            }
        }
    }

    private void readGrantedUriPermissionsLocked() {
        if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION, "readGrantedUriPermissions()");

        final long now = System.currentTimeMillis();

        FileInputStream fis = null;
        try {
            fis = mGrantFile.openRead();
            final XmlPullParser in = Xml.newPullParser();
            in.setInput(fis, StandardCharsets.UTF_8.name());

            int type;
            while ((type = in.next()) != END_DOCUMENT) {
                final String tag = in.getName();
                if (type == START_TAG) {
                    if (TAG_URI_GRANT.equals(tag)) {
                        final int sourceUserId;
                        final int targetUserId;
                        final int userHandle = readIntAttribute(in,
                                ATTR_USER_HANDLE, UserHandle.USER_NULL);
                        if (userHandle != UserHandle.USER_NULL) {
                            // For backwards compatibility.
                            sourceUserId = userHandle;
                            targetUserId = userHandle;
                        } else {
                            sourceUserId = readIntAttribute(in, ATTR_SOURCE_USER_ID);
                            targetUserId = readIntAttribute(in, ATTR_TARGET_USER_ID);
                        }
                        final String sourcePkg = in.getAttributeValue(null, ATTR_SOURCE_PKG);
                        final String targetPkg = in.getAttributeValue(null, ATTR_TARGET_PKG);
                        final Uri uri = Uri.parse(in.getAttributeValue(null, ATTR_URI));
                        final boolean prefix = readBooleanAttribute(in, ATTR_PREFIX);
                        final int modeFlags = readIntAttribute(in, ATTR_MODE_FLAGS);
                        final long createdTime = readLongAttribute(in, ATTR_CREATED_TIME, now);

                        // Sanity check that provider still belongs to source package
                        // Both direct boot aware and unaware packages are fine as we
                        // will do filtering at query time to avoid multiple parsing.
                        final ProviderInfo pi = getProviderInfoLocked(
                                uri.getAuthority(), sourceUserId, MATCH_DIRECT_BOOT_AWARE
                                        | MATCH_DIRECT_BOOT_UNAWARE);
                        if (pi != null && sourcePkg.equals(pi.packageName)) {
                            int targetUid = -1;
                            try {
                                targetUid = AppGlobals.getPackageManager().getPackageUid(
                                        targetPkg, MATCH_UNINSTALLED_PACKAGES, targetUserId);
                            } catch (RemoteException e) {
                            }
                            if (targetUid != -1) {
                                final UriPermission perm = findOrCreateUriPermissionLocked(
                                        sourcePkg, targetPkg, targetUid,
                                        new GrantUri(sourceUserId, uri, prefix));
                                perm.initPersistedModes(modeFlags, createdTime);
                            }
                        } else {
                            Slog.w(TAG, "Persisted grant for " + uri + " had source " + sourcePkg
                                    + " but instead found " + pi);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // Missing grants is okay
        } catch (IOException e) {
            Slog.wtf(TAG, "Failed reading Uri grants", e);
        } catch (XmlPullParserException e) {
            Slog.wtf(TAG, "Failed reading Uri grants", e);
        } finally {
            IoUtils.closeQuietly(fis);
        }
    }

    /**
     * @param uri This uri must NOT contain an embedded userId.
     * @param userId The userId in which the uri is to be resolved.
     */
    @Override
    public void takePersistableUriPermission(Uri uri, final int modeFlags, int userId) {
        enforceNotIsolatedCaller("takePersistableUriPermission");

        Preconditions.checkFlagsArgument(modeFlags,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        synchronized (this) {
            final int callingUid = Binder.getCallingUid();
            boolean persistChanged = false;
            GrantUri grantUri = new GrantUri(userId, uri, false);

            UriPermission exactPerm = findUriPermissionLocked(callingUid,
                    new GrantUri(userId, uri, false));
            UriPermission prefixPerm = findUriPermissionLocked(callingUid,
                    new GrantUri(userId, uri, true));

            final boolean exactValid = (exactPerm != null)
                    && ((modeFlags & exactPerm.persistableModeFlags) == modeFlags);
            final boolean prefixValid = (prefixPerm != null)
                    && ((modeFlags & prefixPerm.persistableModeFlags) == modeFlags);

            if (!(exactValid || prefixValid)) {
                throw new SecurityException("No persistable permission grants found for UID "
                        + callingUid + " and Uri " + grantUri.toSafeString());
            }

            if (exactValid) {
                persistChanged |= exactPerm.takePersistableModes(modeFlags);
            }
            if (prefixValid) {
                persistChanged |= prefixPerm.takePersistableModes(modeFlags);
            }

            persistChanged |= maybePrunePersistedUriGrantsLocked(callingUid);

            if (persistChanged) {
                schedulePersistUriGrants();
            }
        }
    }

    /**
     * @param uri This uri must NOT contain an embedded userId.
     * @param userId The userId in which the uri is to be resolved.
     */
    @Override
    public void releasePersistableUriPermission(Uri uri, final int modeFlags, int userId) {
        enforceNotIsolatedCaller("releasePersistableUriPermission");

        Preconditions.checkFlagsArgument(modeFlags,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        synchronized (this) {
            final int callingUid = Binder.getCallingUid();
            boolean persistChanged = false;

            UriPermission exactPerm = findUriPermissionLocked(callingUid,
                    new GrantUri(userId, uri, false));
            UriPermission prefixPerm = findUriPermissionLocked(callingUid,
                    new GrantUri(userId, uri, true));
            if (exactPerm == null && prefixPerm == null) {
                throw new SecurityException("No permission grants found for UID " + callingUid
                        + " and Uri " + uri.toSafeString());
            }

            if (exactPerm != null) {
                persistChanged |= exactPerm.releasePersistableModes(modeFlags);
                removeUriPermissionIfNeededLocked(exactPerm);
            }
            if (prefixPerm != null) {
                persistChanged |= prefixPerm.releasePersistableModes(modeFlags);
                removeUriPermissionIfNeededLocked(prefixPerm);
            }

            if (persistChanged) {
                schedulePersistUriGrants();
            }
        }
    }

    /**
     * Prune any older {@link UriPermission} for the given UID until outstanding
     * persisted grants are below {@link #MAX_PERSISTED_URI_GRANTS}.
     *
     * @return if any mutations occured that require persisting.
     */
    private boolean maybePrunePersistedUriGrantsLocked(int uid) {
        final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(uid);
        if (perms == null) return false;
        if (perms.size() < MAX_PERSISTED_URI_GRANTS) return false;

        final ArrayList<UriPermission> persisted = Lists.newArrayList();
        for (UriPermission perm : perms.values()) {
            if (perm.persistedModeFlags != 0) {
                persisted.add(perm);
            }
        }

        final int trimCount = persisted.size() - MAX_PERSISTED_URI_GRANTS;
        if (trimCount <= 0) return false;

        Collections.sort(persisted, new UriPermission.PersistedTimeComparator());
        for (int i = 0; i < trimCount; i++) {
            final UriPermission perm = persisted.get(i);

            if (DEBUG_URI_PERMISSION) Slog.v(TAG_URI_PERMISSION,
                    "Trimming grant created at " + perm.persistedCreateTime);

            perm.releasePersistableModes(~0);
            removeUriPermissionIfNeededLocked(perm);
        }

        return true;
    }

    @Override
    public ParceledListSlice<android.content.UriPermission> getPersistedUriPermissions(
            String packageName, boolean incoming) {
        enforceNotIsolatedCaller("getPersistedUriPermissions");
        Preconditions.checkNotNull(packageName, "packageName");

        final int callingUid = Binder.getCallingUid();
        final int callingUserId = UserHandle.getUserId(callingUid);
        final IPackageManager pm = AppGlobals.getPackageManager();
        try {
            final int packageUid = pm.getPackageUid(packageName,
                    MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE, callingUserId);
            if (packageUid != callingUid) {
                throw new SecurityException(
                        "Package " + packageName + " does not belong to calling UID " + callingUid);
            }
        } catch (RemoteException e) {
            throw new SecurityException("Failed to verify package name ownership");
        }

        final ArrayList<android.content.UriPermission> result = Lists.newArrayList();
        synchronized (this) {
            if (incoming) {
                final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(
                        callingUid);
                if (perms == null) {
                    Slog.w(TAG, "No permission grants found for " + packageName);
                } else {
                    for (UriPermission perm : perms.values()) {
                        if (packageName.equals(perm.targetPkg) && perm.persistedModeFlags != 0) {
                            result.add(perm.buildPersistedPublicApiObject());
                        }
                    }
                }
            } else {
                final int size = mGrantedUriPermissions.size();
                for (int i = 0; i < size; i++) {
                    final ArrayMap<GrantUri, UriPermission> perms =
                            mGrantedUriPermissions.valueAt(i);
                    for (UriPermission perm : perms.values()) {
                        if (packageName.equals(perm.sourcePkg) && perm.persistedModeFlags != 0) {
                            result.add(perm.buildPersistedPublicApiObject());
                        }
                    }
                }
            }
        }
        return new ParceledListSlice<android.content.UriPermission>(result);
    }

    @Override
    public ParceledListSlice<android.content.UriPermission> getGrantedUriPermissions(
            String packageName, int userId) {
        enforceCallingPermission(android.Manifest.permission.GET_APP_GRANTED_URI_PERMISSIONS,
                "getGrantedUriPermissions");

        final ArrayList<android.content.UriPermission> result = Lists.newArrayList();
        synchronized (this) {
            final int size = mGrantedUriPermissions.size();
            for (int i = 0; i < size; i++) {
                final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);
                for (UriPermission perm : perms.values()) {
                    if (packageName.equals(perm.targetPkg) && perm.targetUserId == userId
                            && perm.persistedModeFlags != 0) {
                        result.add(perm.buildPersistedPublicApiObject());
                    }
                }
            }
        }
        return new ParceledListSlice<android.content.UriPermission>(result);
    }

    @Override
    public void clearGrantedUriPermissions(String packageName, int userId) {
        enforceCallingPermission(android.Manifest.permission.CLEAR_APP_GRANTED_URI_PERMISSIONS,
                "clearGrantedUriPermissions");
        removeUriPermissionsForPackageLocked(packageName, userId, true);
    }

    @Override
    public void showWaitingForDebugger(IApplicationThread who, boolean waiting) {
        synchronized (this) {
            ProcessRecord app =
                who != null ? getRecordForAppLocked(who) : null;
            if (app == null) return;

            Message msg = Message.obtain();
            msg.what = WAIT_FOR_DEBUGGER_UI_MSG;
            msg.obj = app;
            msg.arg1 = waiting ? 1 : 0;
            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void getMemoryInfo(ActivityManager.MemoryInfo outInfo) {
        final long homeAppMem = mProcessList.getMemLevel(ProcessList.HOME_APP_ADJ);
        final long cachedAppMem = mProcessList.getMemLevel(ProcessList.CACHED_APP_MIN_ADJ);
        outInfo.availMem = Process.getFreeMemory();
        outInfo.totalMem = Process.getTotalMemory();
        outInfo.threshold = homeAppMem;
        outInfo.lowMemory = outInfo.availMem < (homeAppMem + ((cachedAppMem-homeAppMem)/2));
        outInfo.hiddenAppThreshold = cachedAppMem;
        outInfo.secondaryServerThreshold = mProcessList.getMemLevel(
                ProcessList.SERVICE_ADJ);
        outInfo.visibleAppThreshold = mProcessList.getMemLevel(
                ProcessList.VISIBLE_APP_ADJ);
        outInfo.foregroundAppThreshold = mProcessList.getMemLevel(
                ProcessList.FOREGROUND_APP_ADJ);
    }

    // =========================================================
    // TASK MANAGEMENT
    // =========================================================

    @Override
    public List<IAppTask> getAppTasks(String callingPackage) {
        int callingUid = Binder.getCallingUid();
        long ident = Binder.clearCallingIdentity();

        synchronized(this) {
            ArrayList<IAppTask> list = new ArrayList<IAppTask>();
            try {
                if (DEBUG_ALL) Slog.v(TAG, "getAppTasks");

                final int N = mRecentTasks.size();
                for (int i = 0; i < N; i++) {
                    TaskRecord tr = mRecentTasks.get(i);
                    // Skip tasks that do not match the caller.  We don't need to verify
                    // callingPackage, because we are also limiting to callingUid and know
                    // that will limit to the correct security sandbox.
                    if (tr.effectiveUid != callingUid) {
                        continue;
                    }
                    Intent intent = tr.getBaseIntent();
                    if (intent == null ||
                            !callingPackage.equals(intent.getComponent().getPackageName())) {
                        continue;
                    }
                    ActivityManager.RecentTaskInfo taskInfo =
                            createRecentTaskInfoFromTaskRecord(tr);
                    AppTaskImpl taskImpl = new AppTaskImpl(taskInfo.persistentId, callingUid);
                    list.add(taskImpl);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
            return list;
        }
    }

    @Override
    public List<RunningTaskInfo> getTasks(int maxNum, int flags) {
        final int callingUid = Binder.getCallingUid();
        ArrayList<RunningTaskInfo> list = new ArrayList<RunningTaskInfo>();

        synchronized(this) {
            if (DEBUG_ALL) Slog.v(
                TAG, "getTasks: max=" + maxNum + ", flags=" + flags);

            final boolean allowed = isGetTasksAllowed("getTasks", Binder.getCallingPid(),
                    callingUid);

            // TODO: Improve with MRU list from all ActivityStacks.
            mStackSupervisor.getTasksLocked(maxNum, list, callingUid, allowed);
        }

        return list;
    }

    /**
     * Creates a new RecentTaskInfo from a TaskRecord.
     */
    private ActivityManager.RecentTaskInfo createRecentTaskInfoFromTaskRecord(TaskRecord tr) {
        // Update the task description to reflect any changes in the task stack
        tr.updateTaskDescription();

        // Compose the recent task info
        ActivityManager.RecentTaskInfo rti = new ActivityManager.RecentTaskInfo();
        rti.id = tr.getTopActivity() == null ? INVALID_TASK_ID : tr.taskId;
        rti.persistentId = tr.taskId;
        rti.baseIntent = new Intent(tr.getBaseIntent());
        rti.origActivity = tr.origActivity;
        rti.realActivity = tr.realActivity;
        rti.description = tr.lastDescription;
        rti.stackId = tr.stack != null ? tr.stack.mStackId : -1;
        rti.userId = tr.userId;
        rti.taskDescription = new ActivityManager.TaskDescription(tr.lastTaskDescription);
        rti.firstActiveTime = tr.firstActiveTime;
        rti.lastActiveTime = tr.lastActiveTime;
        rti.affiliatedTaskId = tr.mAffiliatedTaskId;
        rti.affiliatedTaskColor = tr.mAffiliatedTaskColor;
        rti.numActivities = 0;
        if (tr.mBounds != null) {
            rti.bounds = new Rect(tr.mBounds);
        }
        rti.isDockable = tr.canGoInDockedStack();
        rti.resizeMode = tr.mResizeMode;

        ActivityRecord base = null;
        ActivityRecord top = null;
        ActivityRecord tmp;

        for (int i = tr.mActivities.size() - 1; i >= 0; --i) {
            tmp = tr.mActivities.get(i);
            if (tmp.finishing) {
                continue;
            }
            base = tmp;
            if (top == null || (top.state == ActivityState.INITIALIZING)) {
                top = base;
            }
            rti.numActivities++;
        }

        rti.baseActivity = (base != null) ? base.intent.getComponent() : null;
        rti.topActivity = (top != null) ? top.intent.getComponent() : null;

        return rti;
    }

    private boolean isGetTasksAllowed(String caller, int callingPid, int callingUid) {
        boolean allowed = checkPermission(android.Manifest.permission.REAL_GET_TASKS,
                callingPid, callingUid) == PackageManager.PERMISSION_GRANTED;
        if (!allowed) {
            if (checkPermission(android.Manifest.permission.GET_TASKS,
                    callingPid, callingUid) == PackageManager.PERMISSION_GRANTED) {
                // Temporary compatibility: some existing apps on the system image may
                // still be requesting the old permission and not switched to the new
                // one; if so, we'll still allow them full access.  This means we need
                // to see if they are holding the old permission and are a system app.
                try {
                    if (AppGlobals.getPackageManager().isUidPrivileged(callingUid)) {
                        allowed = true;
                        if (DEBUG_TASKS) Slog.w(TAG, caller + ": caller " + callingUid
                                + " is using old GET_TASKS but privileged; allowing");
                    }
                } catch (RemoteException e) {
                }
            }
        }
        if (!allowed) {
            if (DEBUG_TASKS) Slog.w(TAG, caller + ": caller " + callingUid
                    + " does not hold REAL_GET_TASKS; limiting output");
        }
        return allowed;
    }

    @Override
    public ParceledListSlice<ActivityManager.RecentTaskInfo> getRecentTasks(int maxNum, int flags,
            int userId) {
        final int callingUid = Binder.getCallingUid();
        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), callingUid, userId,
                false, ALLOW_FULL_ONLY, "getRecentTasks", null);

        final boolean includeProfiles = (flags & ActivityManager.RECENT_INCLUDE_PROFILES) != 0;
        final boolean withExcluded = (flags&ActivityManager.RECENT_WITH_EXCLUDED) != 0;
        synchronized (this) {
            final boolean allowed = isGetTasksAllowed("getRecentTasks", Binder.getCallingPid(),
                    callingUid);
            final boolean detailed = checkCallingPermission(
                    android.Manifest.permission.GET_DETAILED_TASKS)
                    == PackageManager.PERMISSION_GRANTED;

            if (!isUserRunning(userId, ActivityManager.FLAG_AND_UNLOCKED)) {
                Slog.i(TAG, "user " + userId + " is still locked. Cannot load recents");
                return ParceledListSlice.emptyList();
            }
            mRecentTasks.loadUserRecentsLocked(userId);

            final int recentsCount = mRecentTasks.size();
            ArrayList<ActivityManager.RecentTaskInfo> res =
                    new ArrayList<>(maxNum < recentsCount ? maxNum : recentsCount);

            final Set<Integer> includedUsers;
            if (includeProfiles) {
                includedUsers = mUserController.getProfileIds(userId);
            } else {
                includedUsers = new HashSet<>();
            }
            includedUsers.add(Integer.valueOf(userId));

            for (int i = 0; i < recentsCount && maxNum > 0; i++) {
                TaskRecord tr = mRecentTasks.get(i);
                // Only add calling user or related users recent tasks
                if (!includedUsers.contains(Integer.valueOf(tr.userId))) {
                    if (DEBUG_RECENTS) Slog.d(TAG_RECENTS, "Skipping, not user: " + tr);
                    continue;
                }

                if (tr.realActivitySuspended) {
                    if (DEBUG_RECENTS) Slog.d(TAG_RECENTS, "Skipping, activity suspended: " + tr);
                    continue;
                }

                // Return the entry if desired by the caller.  We always return
                // the first entry, because callers always expect this to be the
                // foreground app.  We may filter others if the caller has
                // not supplied RECENT_WITH_EXCLUDED and there is some reason
                // we should exclude the entry.

                if (i == 0
                        || withExcluded
                        || (tr.intent == null)
                        || ((tr.intent.getFlags() & Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                == 0)) {
                    if (!allowed) {
                        // If the caller doesn't have the GET_TASKS permission, then only
                        // allow them to see a small subset of tasks -- their own and home.
                        if (!tr.isHomeTask() && tr.effectiveUid != callingUid) {
                            if (DEBUG_RECENTS) Slog.d(TAG_RECENTS, "Skipping, not allowed: " + tr);
                            continue;
                        }
                    }
                    if ((flags & ActivityManager.RECENT_IGNORE_HOME_STACK_TASKS) != 0) {
                        if (tr.stack != null && tr.stack.isHomeStack()) {
                            if (DEBUG_RECENTS) Slog.d(TAG_RECENTS,
                                    "Skipping, home stack task: " + tr);
                            continue;
                        }
                    }
                    if ((flags & ActivityManager.RECENT_INGORE_DOCKED_STACK_TOP_TASK) != 0) {
                        final ActivityStack stack = tr.stack;
                        if (stack != null && stack.isDockedStack() && stack.topTask() == tr) {
                            if (DEBUG_RECENTS) Slog.d(TAG_RECENTS,
                                    "Skipping, top task in docked stack: " + tr);
                            continue;
                        }
                    }
                    if ((flags & ActivityManager.RECENT_INGORE_PINNED_STACK_TASKS) != 0) {
                        if (tr.stack != null && tr.stack.isPinnedStack()) {
                            if (DEBUG_RECENTS) Slog.d(TAG_RECENTS,
                                    "Skipping, pinned stack task: " + tr);
                            continue;
                        }
                    }
                    if (tr.autoRemoveRecents && tr.getTopActivity() == null) {
                        // Don't include auto remove tasks that are finished or finishing.
                        if (DEBUG_RECENTS) Slog.d(TAG_RECENTS,
                                "Skipping, auto-remove without activity: " + tr);
                        continue;
                    }
                    if ((flags&ActivityManager.RECENT_IGNORE_UNAVAILABLE) != 0
                            && !tr.isAvailable) {
                        if (DEBUG_RECENTS) Slog.d(TAG_RECENTS,
                                "Skipping, unavail real act: " + tr);
                        continue;
                    }

                    if (!tr.mUserSetupComplete) {
                        // Don't include task launched while user is not done setting-up.
                        if (DEBUG_RECENTS) Slog.d(TAG_RECENTS,
                                "Skipping, user setup not complete: " + tr);
                        continue;
                    }

                    ActivityManager.RecentTaskInfo rti = createRecentTaskInfoFromTaskRecord(tr);
                    if (!detailed) {
                        rti.baseIntent.replaceExtras((Bundle)null);
                    }

                    res.add(rti);
                    maxNum--;
                }
            }
            return new ParceledListSlice<>(res);
        }
    }

    @Override
    public ActivityManager.TaskThumbnail getTaskThumbnail(int id) {
        synchronized (this) {
            enforceCallingPermission(android.Manifest.permission.READ_FRAME_BUFFER,
                    "getTaskThumbnail()");
            final TaskRecord tr = mStackSupervisor.anyTaskForIdLocked(
                    id, !RESTORE_FROM_RECENTS, INVALID_STACK_ID);
            if (tr != null) {
                return tr.getTaskThumbnailLocked();
            }
        }
        return null;
    }

    @Override
    public int addAppTask(IBinder activityToken, Intent intent,
            ActivityManager.TaskDescription description, Bitmap thumbnail) throws RemoteException {
        final int callingUid = Binder.getCallingUid();
        final long callingIdent = Binder.clearCallingIdentity();

        try {
            synchronized (this) {
                ActivityRecord r = ActivityRecord.isInStackLocked(activityToken);
                if (r == null) {
                    throw new IllegalArgumentException("Activity does not exist; token="
                            + activityToken);
                }
                ComponentName comp = intent.getComponent();
                if (comp == null) {
                    throw new IllegalArgumentException("Intent " + intent
                            + " must specify explicit component");
                }
                if (thumbnail.getWidth() != mThumbnailWidth
                        || thumbnail.getHeight() != mThumbnailHeight) {
                    throw new IllegalArgumentException("Bad thumbnail size: got "
                            + thumbnail.getWidth() + "x" + thumbnail.getHeight() + ", require "
                            + mThumbnailWidth + "x" + mThumbnailHeight);
                }
                if (intent.getSelector() != null) {
                    intent.setSelector(null);
                }
                if (intent.getSourceBounds() != null) {
                    intent.setSourceBounds(null);
                }
                if ((intent.getFlags()&Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0) {
                    if ((intent.getFlags()&Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS) == 0) {
                        // The caller has added this as an auto-remove task...  that makes no
                        // sense, so turn off auto-remove.
                        intent.addFlags(Intent.FLAG_ACTIVITY_RETAIN_IN_RECENTS);
                    }
                }
                if (!comp.equals(mLastAddedTaskComponent) || callingUid != mLastAddedTaskUid) {
                    mLastAddedTaskActivity = null;
                }
                ActivityInfo ainfo = mLastAddedTaskActivity;
                if (ainfo == null) {
                    ainfo = mLastAddedTaskActivity = AppGlobals.getPackageManager().getActivityInfo(
                            comp, 0, UserHandle.getUserId(callingUid));
                    if (ainfo.applicationInfo.uid != callingUid) {
                        throw new SecurityException(
                                "Can't add task for another application: target uid="
                                + ainfo.applicationInfo.uid + ", calling uid=" + callingUid);
                    }
                }

                // Use the full screen as the context for the task thumbnail
                final Point displaySize = new Point();
                final TaskThumbnailInfo thumbnailInfo = new TaskThumbnailInfo();
                r.task.stack.getDisplaySize(displaySize);
                thumbnailInfo.taskWidth = displaySize.x;
                thumbnailInfo.taskHeight = displaySize.y;
                thumbnailInfo.screenOrientation = mConfiguration.orientation;

                TaskRecord task = new TaskRecord(this,
                        mStackSupervisor.getNextTaskIdForUserLocked(r.userId),
                        ainfo, intent, description, thumbnailInfo);

                int trimIdx = mRecentTasks.trimForTaskLocked(task, false);
                if (trimIdx >= 0) {
                    // If this would have caused a trim, then we'll abort because that
                    // means it would be added at the end of the list but then just removed.
                    return INVALID_TASK_ID;
                }

                final int N = mRecentTasks.size();
                if (N >= (ActivityManager.getMaxRecentTasksStatic()-1)) {
                    final TaskRecord tr = mRecentTasks.remove(N - 1);
                    tr.removedFromRecents();
                }

                task.inRecents = true;
                mRecentTasks.add(task);
                r.task.stack.addTask(task, false, "addAppTask");

                task.setLastThumbnailLocked(thumbnail);
                task.freeLastThumbnail();

                return task.taskId;
            }
        } finally {
            Binder.restoreCallingIdentity(callingIdent);
        }
    }

    @Override
    public Point getAppTaskThumbnailSize() {
        synchronized (this) {
            return new Point(mThumbnailWidth,  mThumbnailHeight);
        }
    }

    @Override
    public void setTaskDescription(IBinder token, ActivityManager.TaskDescription td) {
        synchronized (this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r != null) {
                r.setTaskDescription(td);
                r.task.updateTaskDescription();
            }
        }
    }

    @Override
    public void setTaskResizeable(int taskId, int resizeableMode) {
        synchronized (this) {
            final TaskRecord task = mStackSupervisor.anyTaskForIdLocked(
                    taskId, !RESTORE_FROM_RECENTS, INVALID_STACK_ID);
            if (task == null) {
                Slog.w(TAG, "setTaskResizeable: taskId=" + taskId + " not found");
                return;
            }
            if (task.mResizeMode != resizeableMode) {
                task.mResizeMode = resizeableMode;
                mWindowManager.setTaskResizeable(taskId, resizeableMode);
                mStackSupervisor.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
                mStackSupervisor.resumeFocusedStackTopActivityLocked();
            }
        }
    }

    @Override
    public void resizeTask(int taskId, Rect bounds, int resizeMode) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "resizeTask()");
        long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                TaskRecord task = mStackSupervisor.anyTaskForIdLocked(taskId);
                if (task == null) {
                    Slog.w(TAG, "resizeTask: taskId=" + taskId + " not found");
                    return;
                }
                int stackId = task.stack.mStackId;
                // We allow the task to scroll instead of resizing if this is a non-resizeable task
                // in crop windows resize mode or if the task size is affected by the docked stack
                // changing size. No need to update configuration.
                if (bounds != null && task.inCropWindowsResizeMode()
                        && mStackSupervisor.isStackDockedInEffect(stackId)) {
                    mWindowManager.scrollTask(task.taskId, bounds);
                    return;
                }

                // Place the task in the right stack if it isn't there already based on
                // the requested bounds.
                // The stack transition logic is:
                // - a null bounds on a freeform task moves that task to fullscreen
                // - a non-null bounds on a non-freeform (fullscreen OR docked) task moves
                //   that task to freeform
                // - otherwise the task is not moved
                if (!StackId.isTaskResizeAllowed(stackId)) {
                    throw new IllegalArgumentException("resizeTask not allowed on task=" + task);
                }
                if (bounds == null && stackId == FREEFORM_WORKSPACE_STACK_ID) {
                    stackId = FULLSCREEN_WORKSPACE_STACK_ID;
                } else if (bounds != null && stackId != FREEFORM_WORKSPACE_STACK_ID ) {
                    stackId = FREEFORM_WORKSPACE_STACK_ID;
                }
                boolean preserveWindow = (resizeMode & RESIZE_MODE_PRESERVE_WINDOW) != 0;
                if (stackId != task.stack.mStackId) {
                    mStackSupervisor.moveTaskToStackUncheckedLocked(
                            task, stackId, ON_TOP, !FORCE_FOCUS, "resizeTask");
                    preserveWindow = false;
                }

                mStackSupervisor.resizeTaskLocked(task, bounds, resizeMode, preserveWindow,
                        false /* deferResume */);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public Rect getTaskBounds(int taskId) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "getTaskBounds()");
        long ident = Binder.clearCallingIdentity();
        Rect rect = new Rect();
        try {
            synchronized (this) {
                final TaskRecord task = mStackSupervisor.anyTaskForIdLocked(
                        taskId, !RESTORE_FROM_RECENTS, INVALID_STACK_ID);
                if (task == null) {
                    Slog.w(TAG, "getTaskBounds: taskId=" + taskId + " not found");
                    return rect;
                }
                if (task.stack != null) {
                    // Return the bounds from window manager since it will be adjusted for various
                    // things like the presense of a docked stack for tasks that aren't resizeable.
                    mWindowManager.getTaskBounds(task.taskId, rect);
                } else {
                    // Task isn't in window manager yet since it isn't associated with a stack.
                    // Return the persist value from activity manager
                    if (task.mBounds != null) {
                        rect.set(task.mBounds);
                    } else if (task.mLastNonFullscreenBounds != null) {
                        rect.set(task.mLastNonFullscreenBounds);
                    }
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
        return rect;
    }

    @Override
    public Bitmap getTaskDescriptionIcon(String filePath, int userId) {
        if (userId != UserHandle.getCallingUserId()) {
            enforceCallingPermission(android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                    "getTaskDescriptionIcon");
        }
        final File passedIconFile = new File(filePath);
        final File legitIconFile = new File(TaskPersister.getUserImagesDir(userId),
                passedIconFile.getName());
        if (!legitIconFile.getPath().equals(filePath)
                || !filePath.contains(ActivityRecord.ACTIVITY_ICON_SUFFIX)) {
            throw new IllegalArgumentException("Bad file path: " + filePath
                    + " passed for userId " + userId);
        }
        return mRecentTasks.getTaskDescriptionIcon(filePath);
    }

    @Override
    public void startInPlaceAnimationOnFrontMostApplication(ActivityOptions opts)
            throws RemoteException {
        if (opts.getAnimationType() != ActivityOptions.ANIM_CUSTOM_IN_PLACE ||
                opts.getCustomInPlaceResId() == 0) {
            throw new IllegalArgumentException("Expected in-place ActivityOption " +
                    "with valid animation");
        }
        mWindowManager.prepareAppTransition(TRANSIT_TASK_IN_PLACE, false);
        mWindowManager.overridePendingAppTransitionInPlace(opts.getPackageName(),
                opts.getCustomInPlaceResId());
        mWindowManager.executeAppTransition();
    }

    private void cleanUpRemovedTaskLocked(TaskRecord tr, boolean killProcess,
            boolean removeFromRecents) {
        if (removeFromRecents) {
            mRecentTasks.remove(tr);
            tr.removedFromRecents();
        }
        ComponentName component = tr.getBaseIntent().getComponent();
        if (component == null) {
            Slog.w(TAG, "No component for base intent of task: " + tr);
            return;
        }

        // Find any running services associated with this app and stop if needed.
        mServices.cleanUpRemovedTaskLocked(tr, component, new Intent(tr.getBaseIntent()));

        if (!killProcess) {
            return;
        }

        // Determine if the process(es) for this task should be killed.
        final String pkg = component.getPackageName();
        ArrayList<ProcessRecord> procsToKill = new ArrayList<>();
        ArrayMap<String, SparseArray<ProcessRecord>> pmap = mProcessNames.getMap();
        for (int i = 0; i < pmap.size(); i++) {

            SparseArray<ProcessRecord> uids = pmap.valueAt(i);
            for (int j = 0; j < uids.size(); j++) {
                ProcessRecord proc = uids.valueAt(j);
                if (proc.userId != tr.userId) {
                    // Don't kill process for a different user.
                    continue;
                }
                if (proc == mHomeProcess) {
                    // Don't kill the home process along with tasks from the same package.
                    continue;
                }
                if (!proc.pkgList.containsKey(pkg)) {
                    // Don't kill process that is not associated with this task.
                    continue;
                }

                for (int k = 0; k < proc.activities.size(); k++) {
                    TaskRecord otherTask = proc.activities.get(k).task;
                    if (tr.taskId != otherTask.taskId && otherTask.inRecents) {
                        // Don't kill process(es) that has an activity in a different task that is
                        // also in recents.
                        return;
                    }
                }

                if (proc.foregroundServices) {
                    // Don't kill process(es) with foreground service.
                    return;
                }

                // Add process to kill list.
                procsToKill.add(proc);
            }
        }

        // Kill the running processes.
        for (int i = 0; i < procsToKill.size(); i++) {
            ProcessRecord pr = procsToKill.get(i);
            if (pr.setSchedGroup == ProcessList.SCHED_GROUP_BACKGROUND
                    && pr.curReceiver == null) {
                pr.kill("remove task", true);
            } else {
                // We delay killing processes that are not in the background or running a receiver.
                pr.waitingToKill = "remove task";
            }
        }
    }

    private void removeTasksByPackageNameLocked(String packageName, int userId) {
        // Remove all tasks with activities in the specified package from the list of recent tasks
        for (int i = mRecentTasks.size() - 1; i >= 0; i--) {
            TaskRecord tr = mRecentTasks.get(i);
            if (tr.userId != userId) continue;

            ComponentName cn = tr.intent.getComponent();
            if (cn != null && cn.getPackageName().equals(packageName)) {
                // If the package name matches, remove the task.
                removeTaskByIdLocked(tr.taskId, true, REMOVE_FROM_RECENTS);
            }
        }
    }

    private void cleanupDisabledPackageTasksLocked(String packageName, Set<String> filterByClasses,
            int userId) {

        for (int i = mRecentTasks.size() - 1; i >= 0; i--) {
            TaskRecord tr = mRecentTasks.get(i);
            if (userId != UserHandle.USER_ALL && tr.userId != userId) {
                continue;
            }

            ComponentName cn = tr.intent.getComponent();
            final boolean sameComponent = cn != null && cn.getPackageName().equals(packageName)
                    && (filterByClasses == null || filterByClasses.contains(cn.getClassName()));
            if (sameComponent) {
                removeTaskByIdLocked(tr.taskId, false, REMOVE_FROM_RECENTS);
            }
        }
    }

    /**
     * Removes the task with the specified task id.
     *
     * @param taskId Identifier of the task to be removed.
     * @param killProcess Kill any process associated with the task if possible.
     * @param removeFromRecents Whether to also remove the task from recents.
     * @return Returns true if the given task was found and removed.
     */
    private boolean removeTaskByIdLocked(int taskId, boolean killProcess,
            boolean removeFromRecents) {
        final TaskRecord tr = mStackSupervisor.anyTaskForIdLocked(
                taskId, !RESTORE_FROM_RECENTS, INVALID_STACK_ID);
        if (tr != null) {
            tr.removeTaskActivitiesLocked();
            cleanUpRemovedTaskLocked(tr, killProcess, removeFromRecents);
            if (tr.isPersistable) {
                notifyTaskPersisterLocked(null, true);
            }
            return true;
        }
        Slog.w(TAG, "Request to remove task ignored for non-existent task " + taskId);
        return false;
    }

    @Override
    public void removeStack(int stackId) {
        enforceCallingPermission(Manifest.permission.MANAGE_ACTIVITY_STACKS, "removeStack()");
        if (stackId == HOME_STACK_ID) {
            throw new IllegalArgumentException("Removing home stack is not allowed.");
        }

        synchronized (this) {
            final long ident = Binder.clearCallingIdentity();
            try {
                final ActivityStack stack = mStackSupervisor.getStack(stackId);
                if (stack == null) {
                    return;
                }
                final ArrayList<TaskRecord> tasks = stack.getAllTasks();
                for (int i = tasks.size() - 1; i >= 0; i--) {
                    removeTaskByIdLocked(
                            tasks.get(i).taskId, true /* killProcess */, REMOVE_FROM_RECENTS);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    @Override
    public boolean removeTask(int taskId) {
        enforceCallingPermission(android.Manifest.permission.REMOVE_TASKS, "removeTask()");
        synchronized (this) {
            final long ident = Binder.clearCallingIdentity();
            try {
                return removeTaskByIdLocked(taskId, true, REMOVE_FROM_RECENTS);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    /**
     * TODO: Add mController hook
     */
    @Override
    public void moveTaskToFront(int taskId, int flags, Bundle bOptions) {
        enforceCallingPermission(android.Manifest.permission.REORDER_TASKS, "moveTaskToFront()");

        if (DEBUG_STACK) Slog.d(TAG_STACK, "moveTaskToFront: moving taskId=" + taskId);
        synchronized(this) {
            moveTaskToFrontLocked(taskId, flags, bOptions);
        }
    }

    void moveTaskToFrontLocked(int taskId, int flags, Bundle bOptions) {
        ActivityOptions options = ActivityOptions.fromBundle(bOptions);

        if (!checkAppSwitchAllowedLocked(Binder.getCallingPid(),
                Binder.getCallingUid(), -1, -1, "Task to front")) {
            ActivityOptions.abort(options);
            return;
        }
        final long origId = Binder.clearCallingIdentity();
        try {
            final TaskRecord task = mStackSupervisor.anyTaskForIdLocked(taskId);
            if (task == null) {
                Slog.d(TAG, "Could not find task for id: "+ taskId);
                return;
            }
            if (mStackSupervisor.isLockTaskModeViolation(task)) {
                mStackSupervisor.showLockTaskToast();
                Slog.e(TAG, "moveTaskToFront: Attempt to violate Lock Task Mode");
                return;
            }
            final ActivityRecord prev = mStackSupervisor.topRunningActivityLocked();
            if (prev != null && prev.isRecentsActivity()) {
                task.setTaskToReturnTo(ActivityRecord.RECENTS_ACTIVITY_TYPE);
            }
            mStackSupervisor.findTaskToMoveToFrontLocked(task, flags, options, "moveTaskToFront",
                    false /* forceNonResizable */);
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
        ActivityOptions.abort(options);
    }

    /**
     * Moves an activity, and all of the other activities within the same task, to the bottom
     * of the history stack.  The activity's order within the task is unchanged.
     *
     * @param token A reference to the activity we wish to move
     * @param nonRoot If false then this only works if the activity is the root
     *                of a task; if true it will work for any activity in a task.
     * @return Returns true if the move completed, false if not.
     */
    @Override
    public boolean moveActivityTaskToBack(IBinder token, boolean nonRoot) {
        enforceNotIsolatedCaller("moveActivityTaskToBack");
        synchronized(this) {
            final long origId = Binder.clearCallingIdentity();
            try {
                int taskId = ActivityRecord.getTaskForActivityLocked(token, !nonRoot);
                final TaskRecord task = mStackSupervisor.anyTaskForIdLocked(taskId);
                if (task != null) {
                    if (mStackSupervisor.isLockedTask(task)) {
                        mStackSupervisor.showLockTaskToast();
                        return false;
                    }
                    return ActivityRecord.getStackLocked(token).moveTaskToBackLocked(taskId);
                }
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
        return false;
    }

    @Override
    public void moveTaskBackwards(int task) {
        enforceCallingPermission(android.Manifest.permission.REORDER_TASKS,
                "moveTaskBackwards()");

        synchronized(this) {
            if (!checkAppSwitchAllowedLocked(Binder.getCallingPid(),
                    Binder.getCallingUid(), -1, -1, "Task backwards")) {
                return;
            }
            final long origId = Binder.clearCallingIdentity();
            moveTaskBackwardsLocked(task);
            Binder.restoreCallingIdentity(origId);
        }
    }

    private final void moveTaskBackwardsLocked(int task) {
        Slog.e(TAG, "moveTaskBackwards not yet implemented!");
    }

    @Override
    public IActivityContainer createVirtualActivityContainer(IBinder parentActivityToken,
            IActivityContainerCallback callback) throws RemoteException {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "createActivityContainer()");
        synchronized (this) {
            if (parentActivityToken == null) {
                throw new IllegalArgumentException("parent token must not be null");
            }
            ActivityRecord r = ActivityRecord.forTokenLocked(parentActivityToken);
            if (r == null) {
                return null;
            }
            if (callback == null) {
                throw new IllegalArgumentException("callback must not be null");
            }
            return mStackSupervisor.createVirtualActivityContainer(r, callback);
        }
    }

    @Override
    public void deleteActivityContainer(IActivityContainer container) throws RemoteException {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "deleteActivityContainer()");
        synchronized (this) {
            mStackSupervisor.deleteActivityContainer(container);
        }
    }

    @Override
    public IActivityContainer createStackOnDisplay(int displayId) throws RemoteException {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "createStackOnDisplay()");
        synchronized (this) {
            final int stackId = mStackSupervisor.getNextStackId();
            final ActivityStack stack =
                    mStackSupervisor.createStackOnDisplay(stackId, displayId, true /*onTop*/);
            if (stack == null) {
                return null;
            }
            return stack.mActivityContainer;
        }
    }

    @Override
    public int getActivityDisplayId(IBinder activityToken) throws RemoteException {
        synchronized (this) {
            ActivityStack stack = ActivityRecord.getStackLocked(activityToken);
            if (stack != null && stack.mActivityContainer.isAttachedLocked()) {
                return stack.mActivityContainer.getDisplayId();
            }
            return Display.DEFAULT_DISPLAY;
        }
    }

    @Override
    public int getActivityStackId(IBinder token) throws RemoteException {
        synchronized (this) {
            ActivityStack stack = ActivityRecord.getStackLocked(token);
            if (stack == null) {
                return INVALID_STACK_ID;
            }
            return stack.mStackId;
        }
    }

    @Override
    public void exitFreeformMode(IBinder token) throws RemoteException {
        synchronized (this) {
            long ident = Binder.clearCallingIdentity();
            try {
                final ActivityRecord r = ActivityRecord.forTokenLocked(token);
                if (r == null) {
                    throw new IllegalArgumentException(
                            "exitFreeformMode: No activity record matching token=" + token);
                }
                final ActivityStack stack = r.getStackLocked(token);
                if (stack == null || stack.mStackId != FREEFORM_WORKSPACE_STACK_ID) {
                    throw new IllegalStateException(
                            "exitFreeformMode: You can only go fullscreen from freeform.");
                }
                if (DEBUG_STACK) Slog.d(TAG_STACK, "exitFreeformMode: " + r);
                mStackSupervisor.moveTaskToStackLocked(r.task.taskId, FULLSCREEN_WORKSPACE_STACK_ID,
                        ON_TOP, !FORCE_FOCUS, "exitFreeformMode", ANIMATE);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    @Override
    public void moveTaskToStack(int taskId, int stackId, boolean toTop) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "moveTaskToStack()");
        if (stackId == HOME_STACK_ID) {
            throw new IllegalArgumentException(
                    "moveTaskToStack: Attempt to move task " + taskId + " to home stack");
        }
        synchronized (this) {
            long ident = Binder.clearCallingIdentity();
            try {
                if (DEBUG_STACK) Slog.d(TAG_STACK, "moveTaskToStack: moving task=" + taskId
                        + " to stackId=" + stackId + " toTop=" + toTop);
                if (stackId == DOCKED_STACK_ID) {
                    mWindowManager.setDockedStackCreateState(DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT,
                            null /* initialBounds */);
                }
                boolean result = mStackSupervisor.moveTaskToStackLocked(taskId, stackId, toTop,
                        !FORCE_FOCUS, "moveTaskToStack", ANIMATE);
                if (result && stackId == DOCKED_STACK_ID) {
                    // If task moved to docked stack - show recents if needed.
                    mStackSupervisor.moveHomeStackTaskToTop(RECENTS_ACTIVITY_TYPE,
                            "moveTaskToDockedStack");
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    @Override
    public void swapDockedAndFullscreenStack() throws RemoteException {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "swapDockedAndFullscreenStack()");
        synchronized (this) {
            long ident = Binder.clearCallingIdentity();
            try {
                final ActivityStack fullscreenStack = mStackSupervisor.getStack(
                        FULLSCREEN_WORKSPACE_STACK_ID);
                final TaskRecord topTask = fullscreenStack != null ? fullscreenStack.topTask()
                        : null;
                final ActivityStack dockedStack = mStackSupervisor.getStack(DOCKED_STACK_ID);
                final ArrayList<TaskRecord> tasks = dockedStack != null ? dockedStack.getAllTasks()
                        : null;
                if (topTask == null || tasks == null || tasks.size() == 0) {
                    Slog.w(TAG,
                            "Unable to swap tasks, either docked or fullscreen stack is empty.");
                    return;
                }

                // TODO: App transition
                mWindowManager.prepareAppTransition(TRANSIT_ACTIVITY_RELAUNCH, false);

                // Defer the resume so resume/pausing while moving stacks is dangerous.
                mStackSupervisor.moveTaskToStackLocked(topTask.taskId, DOCKED_STACK_ID,
                        false /* toTop */, !FORCE_FOCUS, "swapDockedAndFullscreenStack",
                        ANIMATE, true /* deferResume */);
                final int size = tasks.size();
                for (int i = 0; i < size; i++) {
                    final int id = tasks.get(i).taskId;
                    if (id == topTask.taskId) {
                        continue;
                    }
                    mStackSupervisor.moveTaskToStackLocked(id,
                            FULLSCREEN_WORKSPACE_STACK_ID, true /* toTop */, !FORCE_FOCUS,
                            "swapDockedAndFullscreenStack", ANIMATE, true /* deferResume */);
                }

                // Because we deferred the resume, to avoid conflicts with stack switches while
                // resuming, we need to do it after all the tasks are moved.
                mStackSupervisor.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
                mStackSupervisor.resumeFocusedStackTopActivityLocked();

                mWindowManager.executeAppTransition();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    /**
     * Moves the input task to the docked stack.
     *
     * @param taskId Id of task to move.
     * @param createMode The mode the docked stack should be created in if it doesn't exist
     *                   already. See
     *                   {@link android.app.ActivityManager#DOCKED_STACK_CREATE_MODE_TOP_OR_LEFT}
     *                   and
     *                   {@link android.app.ActivityManager#DOCKED_STACK_CREATE_MODE_BOTTOM_OR_RIGHT}
     * @param toTop If the task and stack should be moved to the top.
     * @param animate Whether we should play an animation for the moving the task
     * @param initialBounds If the docked stack gets created, it will use these bounds for the
     *                      docked stack. Pass {@code null} to use default bounds.
     */
    @Override
    public boolean moveTaskToDockedStack(int taskId, int createMode, boolean toTop, boolean animate,
            Rect initialBounds, boolean moveHomeStackFront) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "moveTaskToDockedStack()");
        synchronized (this) {
            long ident = Binder.clearCallingIdentity();
            try {
                if (DEBUG_STACK) Slog.d(TAG_STACK, "moveTaskToDockedStack: moving task=" + taskId
                        + " to createMode=" + createMode + " toTop=" + toTop);
                mWindowManager.setDockedStackCreateState(createMode, initialBounds);
                final boolean moved = mStackSupervisor.moveTaskToStackLocked(
                        taskId, DOCKED_STACK_ID, toTop, !FORCE_FOCUS, "moveTaskToDockedStack",
                        animate, DEFER_RESUME);
                if (moved) {
                    if (moveHomeStackFront) {
                        mStackSupervisor.moveHomeStackToFront("moveTaskToDockedStack");
                    }
                    mStackSupervisor.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
                }
                return moved;
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    /**
     * Moves the top activity in the input stackId to the pinned stack.
     *
     * @param stackId Id of stack to move the top activity to pinned stack.
     * @param bounds Bounds to use for pinned stack.
     *
     * @return True if the top activity of the input stack was successfully moved to the pinned
     *          stack.
     */
    @Override
    public boolean moveTopActivityToPinnedStack(int stackId, Rect bounds) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "moveTopActivityToPinnedStack()");
        synchronized (this) {
            if (!mSupportsPictureInPicture) {
                throw new IllegalStateException("moveTopActivityToPinnedStack:"
                        + "Device doesn't support picture-in-pciture mode");
            }

            long ident = Binder.clearCallingIdentity();
            try {
                return mStackSupervisor.moveTopStackActivityToPinnedStackLocked(stackId, bounds);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    @Override
    public void resizeStack(int stackId, Rect bounds, boolean allowResizeInDockedMode,
            boolean preserveWindows, boolean animate, int animationDuration) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "resizeStack()");
        long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                if (animate) {
                    if (stackId == PINNED_STACK_ID) {
                        mWindowManager.animateResizePinnedStack(bounds, animationDuration);
                    } else {
                        throw new IllegalArgumentException("Stack: " + stackId
                                + " doesn't support animated resize.");
                    }
                } else {
                    mStackSupervisor.resizeStackLocked(stackId, bounds, null /* tempTaskBounds */,
                            null /* tempTaskInsetBounds */, preserveWindows,
                            allowResizeInDockedMode, !DEFER_RESUME);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void resizeDockedStack(Rect dockedBounds, Rect tempDockedTaskBounds,
            Rect tempDockedTaskInsetBounds,
            Rect tempOtherTaskBounds, Rect tempOtherTaskInsetBounds) {
        enforceCallingPermission(android.Manifest.permission.MANAGE_ACTIVITY_STACKS,
                "resizeDockedStack()");
        long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                mStackSupervisor.resizeDockedStackLocked(dockedBounds, tempDockedTaskBounds,
                        tempDockedTaskInsetBounds, tempOtherTaskBounds, tempOtherTaskInsetBounds,
                        PRESERVE_WINDOWS);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void resizePinnedStack(Rect pinnedBounds, Rect tempPinnedTaskBounds) {
        enforceCallingPermission(android.Manifest.permission.MANAGE_ACTIVITY_STACKS,
                "resizePinnedStack()");
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                mStackSupervisor.resizePinnedStackLocked(pinnedBounds, tempPinnedTaskBounds);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void positionTaskInStack(int taskId, int stackId, int position) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "positionTaskInStack()");
        if (stackId == HOME_STACK_ID) {
            throw new IllegalArgumentException(
                    "positionTaskInStack: Attempt to change the position of task "
                    + taskId + " in/to home stack");
        }
        synchronized (this) {
            long ident = Binder.clearCallingIdentity();
            try {
                if (DEBUG_STACK) Slog.d(TAG_STACK,
                        "positionTaskInStack: positioning task=" + taskId
                        + " in stackId=" + stackId + " at position=" + position);
                mStackSupervisor.positionTaskInStackLocked(taskId, stackId, position);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    @Override
    public List<StackInfo> getAllStackInfos() {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "getAllStackInfos()");
        long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                return mStackSupervisor.getAllStackInfosLocked();
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public StackInfo getStackInfo(int stackId) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "getStackInfo()");
        long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                return mStackSupervisor.getStackInfoLocked(stackId);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public boolean isInHomeStack(int taskId) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "getStackInfo()");
        long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final TaskRecord tr = mStackSupervisor.anyTaskForIdLocked(
                        taskId, !RESTORE_FROM_RECENTS, INVALID_STACK_ID);
                return tr != null && tr.stack != null && tr.stack.isHomeStack();
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public int getTaskForActivity(IBinder token, boolean onlyRoot) {
        synchronized(this) {
            return ActivityRecord.getTaskForActivityLocked(token, onlyRoot);
        }
    }

    @Override
    public void updateDeviceOwner(String packageName) {
        final int callingUid = Binder.getCallingUid();
        if (callingUid != 0 && callingUid != Process.SYSTEM_UID) {
            throw new SecurityException("updateDeviceOwner called from non-system process");
        }
        synchronized (this) {
            mDeviceOwnerName = packageName;
        }
    }

    @Override
    public void updateLockTaskPackages(int userId, String[] packages) {
        final int callingUid = Binder.getCallingUid();
        if (callingUid != 0 && callingUid != Process.SYSTEM_UID) {
            enforceCallingPermission(android.Manifest.permission.UPDATE_LOCK_TASK_PACKAGES,
                    "updateLockTaskPackages()");
        }
        synchronized (this) {
            if (DEBUG_LOCKTASK) Slog.w(TAG_LOCKTASK, "Whitelisting " + userId + ":" +
                    Arrays.toString(packages));
            mLockTaskPackages.put(userId, packages);
            mStackSupervisor.onLockTaskPackagesUpdatedLocked();
        }
    }


    void startLockTaskModeLocked(TaskRecord task) {
        if (DEBUG_LOCKTASK) Slog.w(TAG_LOCKTASK, "startLockTaskModeLocked: " + task);
        if (task.mLockTaskAuth == LOCK_TASK_AUTH_DONT_LOCK) {
            return;
        }

        // isSystemInitiated is used to distinguish between locked and pinned mode, as pinned mode
        // is initiated by system after the pinning request was shown and locked mode is initiated
        // by an authorized app directly
        final int callingUid = Binder.getCallingUid();
        boolean isSystemInitiated = callingUid == Process.SYSTEM_UID;
        long ident = Binder.clearCallingIdentity();
        try {
            if (!isSystemInitiated) {
                task.mLockTaskUid = callingUid;
                if (task.mLockTaskAuth == LOCK_TASK_AUTH_PINNABLE) {
                    // startLockTask() called by app and task mode is lockTaskModeDefault.
                    if (DEBUG_LOCKTASK) Slog.w(TAG_LOCKTASK, "Mode default, asking user");
                    StatusBarManagerInternal statusBarManager =
                            LocalServices.getService(StatusBarManagerInternal.class);
                    if (statusBarManager != null) {
                        statusBarManager.showScreenPinningRequest(task.taskId);
                    }
                    return;
                }

                final ActivityStack stack = mStackSupervisor.getFocusedStack();
                if (stack == null || task != stack.topTask()) {
                    throw new IllegalArgumentException("Invalid task, not in foreground");
                }
            }
            if (DEBUG_LOCKTASK) Slog.w(TAG_LOCKTASK, isSystemInitiated ? "Locking pinned" :
                    "Locking fully");
            mStackSupervisor.setLockTaskModeLocked(task, isSystemInitiated ?
                    ActivityManager.LOCK_TASK_MODE_PINNED :
                    ActivityManager.LOCK_TASK_MODE_LOCKED,
                    "startLockTask", true);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void startLockTaskMode(int taskId) {
        synchronized (this) {
            final TaskRecord task = mStackSupervisor.anyTaskForIdLocked(taskId);
            if (task != null) {
                startLockTaskModeLocked(task);
            }
        }
    }

    @Override
    public void startLockTaskMode(IBinder token) {
        synchronized (this) {
            final ActivityRecord r = ActivityRecord.forTokenLocked(token);
            if (r == null) {
                return;
            }
            final TaskRecord task = r.task;
            if (task != null) {
                startLockTaskModeLocked(task);
            }
        }
    }

    @Override
    public void startSystemLockTaskMode(int taskId) throws RemoteException {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "startSystemLockTaskMode");
        // This makes inner call to look as if it was initiated by system.
        long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                startLockTaskMode(taskId);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void stopLockTaskMode() {
        final TaskRecord lockTask = mStackSupervisor.getLockedTaskLocked();
        if (lockTask == null) {
            // Our work here is done.
            return;
        }

        final int callingUid = Binder.getCallingUid();
        final int lockTaskUid = lockTask.mLockTaskUid;
        final int lockTaskModeState = mStackSupervisor.getLockTaskModeState();
        if (lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            // Done.
            return;
        } else {
            // Ensure the same caller for startLockTaskMode and stopLockTaskMode.
            // It is possible lockTaskMode was started by the system process because
            // android:lockTaskMode is set to a locking value in the application manifest
            // instead of the app calling startLockTaskMode. In this case
            // {@link TaskRecord.mLockTaskUid} will be 0, so we compare the callingUid to the
            // {@link TaskRecord.effectiveUid} instead. Also caller with
            // {@link MANAGE_ACTIVITY_STACKS} can stop any lock task.
            if (checkCallingPermission(MANAGE_ACTIVITY_STACKS) != PERMISSION_GRANTED
                    && callingUid != lockTaskUid
                    && (lockTaskUid != 0 || callingUid != lockTask.effectiveUid)) {
                throw new SecurityException("Invalid uid, expected " + lockTaskUid
                        + " callingUid=" + callingUid + " effectiveUid=" + lockTask.effectiveUid);
            }
        }
        long ident = Binder.clearCallingIdentity();
        try {
            Log.d(TAG, "stopLockTaskMode");
            // Stop lock task
            synchronized (this) {
                mStackSupervisor.setLockTaskModeLocked(null, ActivityManager.LOCK_TASK_MODE_NONE,
                        "stopLockTask", true);
            }
            TelecomManager tm = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null) {
                tm.showInCallScreen(false);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /**
     * This API should be called by SystemUI only when user perform certain action to dismiss
     * lock task mode. We should only dismiss pinned lock task mode in this case.
     */
    @Override
    public void stopSystemLockTaskMode() throws RemoteException {
        if (mStackSupervisor.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_PINNED) {
            stopLockTaskMode();
        } else {
            mStackSupervisor.showLockTaskToast();
        }
    }

    @Override
    public boolean isInLockTaskMode() {
        return getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE;
    }

    @Override
    public int getLockTaskModeState() {
        synchronized (this) {
            return mStackSupervisor.getLockTaskModeState();
        }
    }

    @Override
    public void showLockTaskEscapeMessage(IBinder token) {
        synchronized (this) {
            final ActivityRecord r = ActivityRecord.forTokenLocked(token);
            if (r == null) {
                return;
            }
            mStackSupervisor.showLockTaskEscapeMessageLocked(r.task);
        }
    }

    // =========================================================
    // CONTENT PROVIDERS
    // =========================================================

    private final List<ProviderInfo> generateApplicationProvidersLocked(ProcessRecord app) {
        List<ProviderInfo> providers = null;
        try {
            providers = AppGlobals.getPackageManager()
                    .queryContentProviders(app.processName, app.uid,
                            STOCK_PM_FLAGS | PackageManager.GET_URI_PERMISSION_PATTERNS
                                    | MATCH_DEBUG_TRIAGED_MISSING)
                    .getList();
        } catch (RemoteException ex) {
        }
        if (DEBUG_MU) Slog.v(TAG_MU,
                "generateApplicationProvidersLocked, app.info.uid = " + app.uid);
        int userId = app.userId;
        if (providers != null) {
            int N = providers.size();
            app.pubProviders.ensureCapacity(N + app.pubProviders.size());
            for (int i=0; i<N; i++) {
                // TODO: keep logic in sync with installEncryptionUnawareProviders
                ProviderInfo cpi =
                    (ProviderInfo)providers.get(i);
                boolean singleton = isSingleton(cpi.processName, cpi.applicationInfo,
                        cpi.name, cpi.flags);
                if (singleton && UserHandle.getUserId(app.uid) != UserHandle.USER_SYSTEM) {
                    // This is a singleton provider, but a user besides the
                    // default user is asking to initialize a process it runs
                    // in...  well, no, it doesn't actually run in this process,
                    // it runs in the process of the default user.  Get rid of it.
                    providers.remove(i);
                    N--;
                    i--;
                    continue;
                }

                ComponentName comp = new ComponentName(cpi.packageName, cpi.name);
                ContentProviderRecord cpr = mProviderMap.getProviderByClass(comp, userId);
                if (cpr == null) {
                    cpr = new ContentProviderRecord(this, cpi, app.info, comp, singleton);
                    mProviderMap.putProviderByClass(comp, cpr);
                }
                if (DEBUG_MU) Slog.v(TAG_MU,
                        "generateApplicationProvidersLocked, cpi.uid = " + cpr.uid);
                app.pubProviders.put(cpi.name, cpr);
                if (!cpi.multiprocess || !"android".equals(cpi.packageName)) {
                    // Don't add this if it is a platform component that is marked
                    // to run in multiple processes, because this is actually
                    // part of the framework so doesn't make sense to track as a
                    // separate apk in the process.
                    app.addPackage(cpi.applicationInfo.packageName, cpi.applicationInfo.versionCode,
                            mProcessStats);
                }
                notifyPackageUse(cpi.applicationInfo.packageName,
                                 PackageManager.NOTIFY_PACKAGE_USE_CONTENT_PROVIDER);
            }
        }
        return providers;
    }

    /**
     * Check if the calling UID has a possible chance at accessing the provider
     * at the given authority and user.
     */
    public String checkContentProviderAccess(String authority, int userId) {
        if (userId == UserHandle.USER_ALL) {
            mContext.enforceCallingOrSelfPermission(
                    Manifest.permission.INTERACT_ACROSS_USERS_FULL, TAG);
            userId = UserHandle.getCallingUserId();
        }

        ProviderInfo cpi = null;
        try {
            cpi = AppGlobals.getPackageManager().resolveContentProvider(authority,
                    STOCK_PM_FLAGS | PackageManager.GET_URI_PERMISSION_PATTERNS
                            | PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE,
                    userId);
        } catch (RemoteException ignored) {
        }
        if (cpi == null) {
            // TODO: make this an outright failure in a future platform release;
            // until then anonymous content notifications are unprotected
            //return "Failed to find provider " + authority + " for user " + userId;
            return null;
        }

        ProcessRecord r = null;
        synchronized (mPidsSelfLocked) {
            r = mPidsSelfLocked.get(Binder.getCallingPid());
        }
        if (r == null) {
            return "Failed to find PID " + Binder.getCallingPid();
        }

        synchronized (this) {
            return checkContentProviderPermissionLocked(cpi, r, userId, true);
        }
    }

    /**
     * Check if {@link ProcessRecord} has a possible chance at accessing the
     * given {@link ProviderInfo}. Final permission checking is always done
     * in {@link ContentProvider}.
     */
    private final String checkContentProviderPermissionLocked(
            ProviderInfo cpi, ProcessRecord r, int userId, boolean checkUser) {
        final int callingPid = (r != null) ? r.pid : Binder.getCallingPid();
        final int callingUid = (r != null) ? r.uid : Binder.getCallingUid();
        boolean checkedGrants = false;
        if (checkUser) {
            // Looking for cross-user grants before enforcing the typical cross-users permissions
            int tmpTargetUserId = mUserController.unsafeConvertIncomingUserLocked(userId);
            if (tmpTargetUserId != UserHandle.getUserId(callingUid)) {
                if (checkAuthorityGrants(callingUid, cpi, tmpTargetUserId, checkUser)) {
                    return null;
                }
                checkedGrants = true;
            }
            userId = mUserController.handleIncomingUser(callingPid, callingUid, userId, false,
                    ALLOW_NON_FULL, "checkContentProviderPermissionLocked " + cpi.authority, null);
            if (userId != tmpTargetUserId) {
                // When we actually went to determine the final targer user ID, this ended
                // up different than our initial check for the authority.  This is because
                // they had asked for USER_CURRENT_OR_SELF and we ended up switching to
                // SELF.  So we need to re-check the grants again.
                checkedGrants = false;
            }
        }
        if (checkComponentPermission(cpi.readPermission, callingPid, callingUid,
                cpi.applicationInfo.uid, cpi.exported)
                == PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        if (checkComponentPermission(cpi.writePermission, callingPid, callingUid,
                cpi.applicationInfo.uid, cpi.exported)
                == PackageManager.PERMISSION_GRANTED) {
            return null;
        }

        PathPermission[] pps = cpi.pathPermissions;
        if (pps != null) {
            int i = pps.length;
            while (i > 0) {
                i--;
                PathPermission pp = pps[i];
                String pprperm = pp.getReadPermission();
                if (pprperm != null && checkComponentPermission(pprperm, callingPid, callingUid,
                        cpi.applicationInfo.uid, cpi.exported)
                        == PackageManager.PERMISSION_GRANTED) {
                    return null;
                }
                String ppwperm = pp.getWritePermission();
                if (ppwperm != null && checkComponentPermission(ppwperm, callingPid, callingUid,
                        cpi.applicationInfo.uid, cpi.exported)
                        == PackageManager.PERMISSION_GRANTED) {
                    return null;
                }
            }
        }
        if (!checkedGrants && checkAuthorityGrants(callingUid, cpi, userId, checkUser)) {
            return null;
        }

        String msg;
        if (!cpi.exported) {
            msg = "Permission Denial: opening provider " + cpi.name
                    + " from " + (r != null ? r : "(null)") + " (pid=" + callingPid
                    + ", uid=" + callingUid + ") that is not exported from uid "
                    + cpi.applicationInfo.uid;
        } else {
            msg = "Permission Denial: opening provider " + cpi.name
                    + " from " + (r != null ? r : "(null)") + " (pid=" + callingPid
                    + ", uid=" + callingUid + ") requires "
                    + cpi.readPermission + " or " + cpi.writePermission;
        }
        Slog.w(TAG, msg);
        return msg;
    }

    /**
     * Returns if the ContentProvider has granted a uri to callingUid
     */
    boolean checkAuthorityGrants(int callingUid, ProviderInfo cpi, int userId, boolean checkUser) {
        final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.get(callingUid);
        if (perms != null) {
            for (int i=perms.size()-1; i>=0; i--) {
                GrantUri grantUri = perms.keyAt(i);
                if (grantUri.sourceUserId == userId || !checkUser) {
                    if (matchesProvider(grantUri.uri, cpi)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the uri authority is one of the authorities specified in the provider.
     */
    boolean matchesProvider(Uri uri, ProviderInfo cpi) {
        String uriAuth = uri.getAuthority();
        String cpiAuth = cpi.authority;
        if (cpiAuth.indexOf(';') == -1) {
            return cpiAuth.equals(uriAuth);
        }
        String[] cpiAuths = cpiAuth.split(";");
        int length = cpiAuths.length;
        for (int i = 0; i < length; i++) {
            if (cpiAuths[i].equals(uriAuth)) return true;
        }
        return false;
    }

    ContentProviderConnection incProviderCountLocked(ProcessRecord r,
            final ContentProviderRecord cpr, IBinder externalProcessToken, boolean stable) {
        if (r != null) {
            for (int i=0; i<r.conProviders.size(); i++) {
                ContentProviderConnection conn = r.conProviders.get(i);
                if (conn.provider == cpr) {
                    if (DEBUG_PROVIDER) Slog.v(TAG_PROVIDER,
                            "Adding provider requested by "
                            + r.processName + " from process "
                            + cpr.info.processName + ": " + cpr.name.flattenToShortString()
                            + " scnt=" + conn.stableCount + " uscnt=" + conn.unstableCount);
                    if (stable) {
                        conn.stableCount++;
                        conn.numStableIncs++;
                    } else {
                        conn.unstableCount++;
                        conn.numUnstableIncs++;
                    }
                    return conn;
                }
            }
            ContentProviderConnection conn = new ContentProviderConnection(cpr, r);
            if (stable) {
                conn.stableCount = 1;
                conn.numStableIncs = 1;
            } else {
                conn.unstableCount = 1;
                conn.numUnstableIncs = 1;
            }
            cpr.connections.add(conn);
            r.conProviders.add(conn);
            startAssociationLocked(r.uid, r.processName, r.curProcState,
                    cpr.uid, cpr.name, cpr.info.processName);
            return conn;
        }
        cpr.addExternalProcessHandleLocked(externalProcessToken);
        return null;
    }

    boolean decProviderCountLocked(ContentProviderConnection conn,
            ContentProviderRecord cpr, IBinder externalProcessToken, boolean stable) {
        if (conn != null) {
            cpr = conn.provider;
            if (DEBUG_PROVIDER) Slog.v(TAG_PROVIDER,
                    "Removing provider requested by "
                    + conn.client.processName + " from process "
                    + cpr.info.processName + ": " + cpr.name.flattenToShortString()
                    + " scnt=" + conn.stableCount + " uscnt=" + conn.unstableCount);
            if (stable) {
                conn.stableCount--;
            } else {
                conn.unstableCount--;
            }
            if (conn.stableCount == 0 && conn.unstableCount == 0) {
                cpr.connections.remove(conn);
                conn.client.conProviders.remove(conn);
                if (conn.client.setProcState < ActivityManager.PROCESS_STATE_LAST_ACTIVITY) {
                    // The client is more important than last activity -- note the time this
                    // is happening, so we keep the old provider process around a bit as last
                    // activity to avoid thrashing it.
                    if (cpr.proc != null) {
                        cpr.proc.lastProviderTime = SystemClock.uptimeMillis();
                    }
                }
                stopAssociationLocked(conn.client.uid, conn.client.processName, cpr.uid, cpr.name);
                return true;
            }
            return false;
        }
        cpr.removeExternalProcessHandleLocked(externalProcessToken);
        return false;
    }

    private void checkTime(long startTime, String where) {
        long now = SystemClock.uptimeMillis();
        if ((now-startTime) > 50) {
            // If we are taking more than 50ms, log about it.
            Slog.w(TAG, "Slow operation: " + (now-startTime) + "ms so far, now at " + where);
        }
    }

    private static final int[] PROCESS_STATE_STATS_FORMAT = new int[] {
            PROC_SPACE_TERM,
            PROC_SPACE_TERM|PROC_PARENS,
            PROC_SPACE_TERM|PROC_CHAR|PROC_OUT_LONG,        // 3: process state
    };

    private final long[] mProcessStateStatsLongs = new long[1];

    boolean isProcessAliveLocked(ProcessRecord proc) {
        if (proc.procStatFile == null) {
            proc.procStatFile = "/proc/" + proc.pid + "/stat";
        }
        mProcessStateStatsLongs[0] = 0;
        if (!Process.readProcFile(proc.procStatFile, PROCESS_STATE_STATS_FORMAT, null,
                mProcessStateStatsLongs, null)) {
            if (DEBUG_OOM_ADJ) Slog.d(TAG, "UNABLE TO RETRIEVE STATE FOR " + proc.procStatFile);
            return false;
        }
        final long state = mProcessStateStatsLongs[0];
        if (DEBUG_OOM_ADJ) Slog.d(TAG, "RETRIEVED STATE FOR " + proc.procStatFile + ": "
                + (char)state);
        return state != 'Z' && state != 'X' && state != 'x' && state != 'K';
    }

    private ContentProviderHolder getContentProviderImpl(IApplicationThread caller,
            String name, IBinder token, boolean stable, int userId) {
        ContentProviderRecord cpr;
        ContentProviderConnection conn = null;
        ProviderInfo cpi = null;

        synchronized(this) {
            long startTime = SystemClock.uptimeMillis();

            ProcessRecord r = null;
            if (caller != null) {
                r = getRecordForAppLocked(caller);
                if (r == null) {
                    throw new SecurityException(
                            "Unable to find app for caller " + caller
                          + " (pid=" + Binder.getCallingPid()
                          + ") when getting content provider " + name);
                }
            }

            boolean checkCrossUser = true;

            checkTime(startTime, "getContentProviderImpl: getProviderByName");

            // First check if this content provider has been published...
            cpr = mProviderMap.getProviderByName(name, userId);
            // If that didn't work, check if it exists for user 0 and then
            // verify that it's a singleton provider before using it.
            if (cpr == null && userId != UserHandle.USER_SYSTEM) {
                cpr = mProviderMap.getProviderByName(name, UserHandle.USER_SYSTEM);
                if (cpr != null) {
                    cpi = cpr.info;
                    if (isSingleton(cpi.processName, cpi.applicationInfo,
                            cpi.name, cpi.flags)
                            && isValidSingletonCall(r.uid, cpi.applicationInfo.uid)) {
                        userId = UserHandle.USER_SYSTEM;
                        checkCrossUser = false;
                    } else {
                        cpr = null;
                        cpi = null;
                    }
                }
            }

            boolean providerRunning = cpr != null && cpr.proc != null && !cpr.proc.killed;
            if (providerRunning) {
                cpi = cpr.info;
                String msg;
                checkTime(startTime, "getContentProviderImpl: before checkContentProviderPermission");
                if ((msg = checkContentProviderPermissionLocked(cpi, r, userId, checkCrossUser))
                        != null) {
                    throw new SecurityException(msg);
                }
                checkTime(startTime, "getContentProviderImpl: after checkContentProviderPermission");

                if (r != null && cpr.canRunHere(r)) {
                    // This provider has been published or is in the process
                    // of being published...  but it is also allowed to run
                    // in the caller's process, so don't make a connection
                    // and just let the caller instantiate its own instance.
                    ContentProviderHolder holder = cpr.newHolder(null);
                    // don't give caller the provider object, it needs
                    // to make its own.
                    holder.provider = null;
                    return holder;
                }

                final long origId = Binder.clearCallingIdentity();

                checkTime(startTime, "getContentProviderImpl: incProviderCountLocked");

                // In this case the provider instance already exists, so we can
                // return it right away.
                conn = incProviderCountLocked(r, cpr, token, stable);
                if (conn != null && (conn.stableCount+conn.unstableCount) == 1) {
                    if (cpr.proc != null && r.setAdj <= ProcessList.PERCEPTIBLE_APP_ADJ) {
                        // If this is a perceptible app accessing the provider,
                        // make sure to count it as being accessed and thus
                        // back up on the LRU list.  This is good because
                        // content providers are often expensive to start.
                        checkTime(startTime, "getContentProviderImpl: before updateLruProcess");
                        updateLruProcessLocked(cpr.proc, false, null);
                        checkTime(startTime, "getContentProviderImpl: after updateLruProcess");
                    }
                }

                checkTime(startTime, "getContentProviderImpl: before updateOomAdj");
                final int verifiedAdj = cpr.proc.verifiedAdj;
                boolean success = updateOomAdjLocked(cpr.proc);
                // XXX things have changed so updateOomAdjLocked doesn't actually tell us
                // if the process has been successfully adjusted.  So to reduce races with
                // it, we will check whether the process still exists.  Note that this doesn't
                // completely get rid of races with LMK killing the process, but should make
                // them much smaller.
                if (success && verifiedAdj != cpr.proc.setAdj && !isProcessAliveLocked(cpr.proc)) {
                    success = false;
                }
                maybeUpdateProviderUsageStatsLocked(r, cpr.info.packageName, name);
                checkTime(startTime, "getContentProviderImpl: after updateOomAdj");
                if (DEBUG_PROVIDER) Slog.i(TAG_PROVIDER, "Adjust success: " + success);
                // NOTE: there is still a race here where a signal could be
                // pending on the process even though we managed to update its
                // adj level.  Not sure what to do about this, but at least
                // the race is now smaller.
                if (!success) {
                    // Uh oh...  it looks like the provider's process
                    // has been killed on us.  We need to wait for a new
                    // process to be started, and make sure its death
                    // doesn't kill our process.
                    Slog.i(TAG, "Existing provider " + cpr.name.flattenToShortString()
                            + " is crashing; detaching " + r);
                    boolean lastRef = decProviderCountLocked(conn, cpr, token, stable);
                    checkTime(startTime, "getContentProviderImpl: before appDied");
                    appDiedLocked(cpr.proc);
                    checkTime(startTime, "getContentProviderImpl: after appDied");
                    if (!lastRef) {
                        // This wasn't the last ref our process had on
                        // the provider...  we have now been killed, bail.
                        return null;
                    }
                    providerRunning = false;
                    conn = null;
                } else {
                    cpr.proc.verifiedAdj = cpr.proc.setAdj;
                }

                Binder.restoreCallingIdentity(origId);
            }

            if (!providerRunning) {
                try {
                    checkTime(startTime, "getContentProviderImpl: before resolveContentProvider");
                    cpi = AppGlobals.getPackageManager().
                        resolveContentProvider(name,
                            STOCK_PM_FLAGS | PackageManager.GET_URI_PERMISSION_PATTERNS, userId);
                    checkTime(startTime, "getContentProviderImpl: after resolveContentProvider");
                } catch (RemoteException ex) {
                }
                if (cpi == null) {
                    return null;
                }
                // If the provider is a singleton AND
                // (it's a call within the same user || the provider is a
                // privileged app)
                // Then allow connecting to the singleton provider
                boolean singleton = isSingleton(cpi.processName, cpi.applicationInfo,
                        cpi.name, cpi.flags)
                        && isValidSingletonCall(r.uid, cpi.applicationInfo.uid);
                if (singleton) {
                    userId = UserHandle.USER_SYSTEM;
                }
                cpi.applicationInfo = getAppInfoForUser(cpi.applicationInfo, userId);
                checkTime(startTime, "getContentProviderImpl: got app info for user");

                String msg;
                checkTime(startTime, "getContentProviderImpl: before checkContentProviderPermission");
                if ((msg = checkContentProviderPermissionLocked(cpi, r, userId, !singleton))
                        != null) {
                    throw new SecurityException(msg);
                }
                checkTime(startTime, "getContentProviderImpl: after checkContentProviderPermission");

                if (!mProcessesReady
                        && !cpi.processName.equals("system")) {
                    // If this content provider does not run in the system
                    // process, and the system is not yet ready to run other
                    // processes, then fail fast instead of hanging.
                    throw new IllegalArgumentException(
                            "Attempt to launch content provider before system ready");
                }

                // Make sure that the user who owns this provider is running.  If not,
                // we don't want to allow it to run.
                if (!mUserController.isUserRunningLocked(userId, 0)) {
                    Slog.w(TAG, "Unable to launch app "
                            + cpi.applicationInfo.packageName + "/"
                            + cpi.applicationInfo.uid + " for provider "
                            + name + ": user " + userId + " is stopped");
                    return null;
                }

                ComponentName comp = new ComponentName(cpi.packageName, cpi.name);
                checkTime(startTime, "getContentProviderImpl: before getProviderByClass");
                cpr = mProviderMap.getProviderByClass(comp, userId);
                checkTime(startTime, "getContentProviderImpl: after getProviderByClass");
                final boolean firstClass = cpr == null;
                if (firstClass) {
                    final long ident = Binder.clearCallingIdentity();

                    // If permissions need a review before any of the app components can run,
                    // we return no provider and launch a review activity if the calling app
                    // is in the foreground.
                    if (Build.PERMISSIONS_REVIEW_REQUIRED) {
                        if (!requestTargetProviderPermissionsReviewIfNeededLocked(cpi, r, userId)) {
                            return null;
                        }
                    }

                    try {
                        checkTime(startTime, "getContentProviderImpl: before getApplicationInfo");
                        ApplicationInfo ai =
                            AppGlobals.getPackageManager().
                                getApplicationInfo(
                                        cpi.applicationInfo.packageName,
                                        STOCK_PM_FLAGS, userId);
                        checkTime(startTime, "getContentProviderImpl: after getApplicationInfo");
                        if (ai == null) {
                            Slog.w(TAG, "No package info for content provider "
                                    + cpi.name);
                            return null;
                        }
                        ai = getAppInfoForUser(ai, userId);
                        cpr = new ContentProviderRecord(this, cpi, ai, comp, singleton);
                    } catch (RemoteException ex) {
                        // pm is in same process, this will never happen.
                    } finally {
                        Binder.restoreCallingIdentity(ident);
                    }
                }

                checkTime(startTime, "getContentProviderImpl: now have ContentProviderRecord");

                if (r != null && cpr.canRunHere(r)) {
                    // If this is a multiprocess provider, then just return its
                    // info and allow the caller to instantiate it.  Only do
                    // this if the provider is the same user as the caller's
                    // process, or can run as root (so can be in any process).
                    return cpr.newHolder(null);
                }

                if (DEBUG_PROVIDER) Slog.w(TAG_PROVIDER, "LAUNCHING REMOTE PROVIDER (myuid "
                            + (r != null ? r.uid : null) + " pruid " + cpr.appInfo.uid + "): "
                            + cpr.info.name + " callers=" + Debug.getCallers(6));

                // This is single process, and our app is now connecting to it.
                // See if we are already in the process of launching this
                // provider.
                final int N = mLaunchingProviders.size();
                int i;
                for (i = 0; i < N; i++) {
                    if (mLaunchingProviders.get(i) == cpr) {
                        break;
                    }
                }

                // If the provider is not already being launched, then get it
                // started.
                if (i >= N) {
                    final long origId = Binder.clearCallingIdentity();

                    try {
                        // Content provider is now in use, its package can't be stopped.
                        try {
                            checkTime(startTime, "getContentProviderImpl: before set stopped state");
                            AppGlobals.getPackageManager().setPackageStoppedState(
                                    cpr.appInfo.packageName, false, userId);
                            checkTime(startTime, "getContentProviderImpl: after set stopped state");
                        } catch (RemoteException e) {
                        } catch (IllegalArgumentException e) {
                            Slog.w(TAG, "Failed trying to unstop package "
                                    + cpr.appInfo.packageName + ": " + e);
                        }

                        // Use existing process if already started
                        checkTime(startTime, "getContentProviderImpl: looking for process record");
                        ProcessRecord proc = getProcessRecordLocked(
                                cpi.processName, cpr.appInfo.uid, false);
                        if (proc != null && proc.thread != null && !proc.killed) {
                            if (DEBUG_PROVIDER) Slog.d(TAG_PROVIDER,
                                    "Installing in existing process " + proc);
                            if (!proc.pubProviders.containsKey(cpi.name)) {
                                checkTime(startTime, "getContentProviderImpl: scheduling install");
                                proc.pubProviders.put(cpi.name, cpr);
                                try {
                                    proc.thread.scheduleInstallProvider(cpi);
                                } catch (RemoteException e) {
                                }
                            }
                        } else {
                            checkTime(startTime, "getContentProviderImpl: before start process");
                            proc = startProcessLocked(cpi.processName,
                                    cpr.appInfo, false, 0, "content provider",
                                    new ComponentName(cpi.applicationInfo.packageName,
                                            cpi.name), false, false, false);
                            checkTime(startTime, "getContentProviderImpl: after start process");
                            if (proc == null) {
                                Slog.w(TAG, "Unable to launch app "
                                        + cpi.applicationInfo.packageName + "/"
                                        + cpi.applicationInfo.uid + " for provider "
                                        + name + ": process is bad");
                                return null;
                            }
                        }
                        cpr.launchingApp = proc;
                        mLaunchingProviders.add(cpr);
                    } finally {
                        Binder.restoreCallingIdentity(origId);
                    }
                }

                checkTime(startTime, "getContentProviderImpl: updating data structures");

                // Make sure the provider is published (the same provider class
                // may be published under multiple names).
                if (firstClass) {
                    mProviderMap.putProviderByClass(comp, cpr);
                }

                mProviderMap.putProviderByName(name, cpr);
                conn = incProviderCountLocked(r, cpr, token, stable);
                if (conn != null) {
                    conn.waiting = true;
                }
            }
            checkTime(startTime, "getContentProviderImpl: done!");
        }

        // Wait for the provider to be published...
        synchronized (cpr) {
            while (cpr.provider == null) {
                if (cpr.launchingApp == null) {
                    Slog.w(TAG, "Unable to launch app "
                            + cpi.applicationInfo.packageName + "/"
                            + cpi.applicationInfo.uid + " for provider "
                            + name + ": launching app became null");
                    EventLog.writeEvent(EventLogTags.AM_PROVIDER_LOST_PROCESS,
                            UserHandle.getUserId(cpi.applicationInfo.uid),
                            cpi.applicationInfo.packageName,
                            cpi.applicationInfo.uid, name);
                    return null;
                }
                try {
                    if (DEBUG_MU) Slog.v(TAG_MU,
                            "Waiting to start provider " + cpr
                            + " launchingApp=" + cpr.launchingApp);
                    if (conn != null) {
                        conn.waiting = true;
                    }
                    cpr.wait();
                } catch (InterruptedException ex) {
                } finally {
                    if (conn != null) {
                        conn.waiting = false;
                    }
                }
            }
        }
        return cpr != null ? cpr.newHolder(conn) : null;
    }

    private boolean requestTargetProviderPermissionsReviewIfNeededLocked(ProviderInfo cpi,
            ProcessRecord r, final int userId) {
        if (getPackageManagerInternalLocked().isPermissionsReviewRequired(
                cpi.packageName, userId)) {

            final boolean callerForeground = r == null || r.setSchedGroup
                    != ProcessList.SCHED_GROUP_BACKGROUND;

            // Show a permission review UI only for starting from a foreground app
            if (!callerForeground) {
                Slog.w(TAG, "u" + userId + " Instantiating a provider in package"
                        + cpi.packageName + " requires a permissions review");
                return false;
            }

            final Intent intent = new Intent(Intent.ACTION_REVIEW_PERMISSIONS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, cpi.packageName);

            if (DEBUG_PERMISSIONS_REVIEW) {
                Slog.i(TAG, "u" + userId + " Launching permission review "
                        + "for package " + cpi.packageName);
            }

            final UserHandle userHandle = new UserHandle(userId);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mContext.startActivityAsUser(intent, userHandle);
                }
            });

            return false;
        }

        return true;
    }

    PackageManagerInternal getPackageManagerInternalLocked() {
        if (mPackageManagerInt == null) {
            mPackageManagerInt = LocalServices.getService(PackageManagerInternal.class);
        }
        return mPackageManagerInt;
    }

    @Override
    public final ContentProviderHolder getContentProvider(
            IApplicationThread caller, String name, int userId, boolean stable) {
        enforceNotIsolatedCaller("getContentProvider");
        if (caller == null) {
            String msg = "null IApplicationThread when getting content provider "
                    + name;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        // The incoming user check is now handled in checkContentProviderPermissionLocked() to deal
        // with cross-user grant.
        return getContentProviderImpl(caller, name, null, stable, userId);
    }

    public ContentProviderHolder getContentProviderExternal(
            String name, int userId, IBinder token) {
        enforceCallingPermission(android.Manifest.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY,
            "Do not have permission in call getContentProviderExternal()");
        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, false, ALLOW_FULL_ONLY, "getContentProvider", null);
        return getContentProviderExternalUnchecked(name, token, userId);
    }

    private ContentProviderHolder getContentProviderExternalUnchecked(String name,
            IBinder token, int userId) {
        return getContentProviderImpl(null, name, token, true, userId);
    }

    /**
     * Drop a content provider from a ProcessRecord's bookkeeping
     */
    public void removeContentProvider(IBinder connection, boolean stable) {
        enforceNotIsolatedCaller("removeContentProvider");
        long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                ContentProviderConnection conn;
                try {
                    conn = (ContentProviderConnection)connection;
                } catch (ClassCastException e) {
                    String msg ="removeContentProvider: " + connection
                            + " not a ContentProviderConnection";
                    Slog.w(TAG, msg);
                    throw new IllegalArgumentException(msg);
                }
                if (conn == null) {
                    throw new NullPointerException("connection is null");
                }
                if (decProviderCountLocked(conn, null, null, stable)) {
                    updateOomAdjLocked();
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public void removeContentProviderExternal(String name, IBinder token) {
        enforceCallingPermission(android.Manifest.permission.ACCESS_CONTENT_PROVIDERS_EXTERNALLY,
            "Do not have permission in call removeContentProviderExternal()");
        int userId = UserHandle.getCallingUserId();
        long ident = Binder.clearCallingIdentity();
        try {
            removeContentProviderExternalUnchecked(name, token, userId);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void removeContentProviderExternalUnchecked(String name, IBinder token, int userId) {
        synchronized (this) {
            ContentProviderRecord cpr = mProviderMap.getProviderByName(name, userId);
            if(cpr == null) {
                //remove from mProvidersByClass
                if(DEBUG_ALL) Slog.v(TAG, name+" content provider not found in providers list");
                return;
            }

            //update content provider record entry info
            ComponentName comp = new ComponentName(cpr.info.packageName, cpr.info.name);
            ContentProviderRecord localCpr = mProviderMap.getProviderByClass(comp, userId);
            if (localCpr.hasExternalProcessHandles()) {
                if (localCpr.removeExternalProcessHandleLocked(token)) {
                    updateOomAdjLocked();
                } else {
                    Slog.e(TAG, "Attmpt to remove content provider " + localCpr
                            + " with no external reference for token: "
                            + token + ".");
                }
            } else {
                Slog.e(TAG, "Attmpt to remove content provider: " + localCpr
                        + " with no external references.");
            }
        }
    }

    public final void publishContentProviders(IApplicationThread caller,
            List<ContentProviderHolder> providers) {
        if (providers == null) {
            return;
        }

        enforceNotIsolatedCaller("publishContentProviders");
        synchronized (this) {
            final ProcessRecord r = getRecordForAppLocked(caller);
            if (DEBUG_MU) Slog.v(TAG_MU, "ProcessRecord uid = " + r.uid);
            if (r == null) {
                throw new SecurityException(
                        "Unable to find app for caller " + caller
                      + " (pid=" + Binder.getCallingPid()
                      + ") when publishing content providers");
            }

            final long origId = Binder.clearCallingIdentity();

            final int N = providers.size();
            for (int i = 0; i < N; i++) {
                ContentProviderHolder src = providers.get(i);
                if (src == null || src.info == null || src.provider == null) {
                    continue;
                }
                ContentProviderRecord dst = r.pubProviders.get(src.info.name);
                if (DEBUG_MU) Slog.v(TAG_MU, "ContentProviderRecord uid = " + dst.uid);
                if (dst != null) {
                    ComponentName comp = new ComponentName(dst.info.packageName, dst.info.name);
                    mProviderMap.putProviderByClass(comp, dst);
                    String names[] = dst.info.authority.split(";");
                    for (int j = 0; j < names.length; j++) {
                        mProviderMap.putProviderByName(names[j], dst);
                    }

                    int launchingCount = mLaunchingProviders.size();
                    int j;
                    boolean wasInLaunchingProviders = false;
                    for (j = 0; j < launchingCount; j++) {
                        if (mLaunchingProviders.get(j) == dst) {
                            mLaunchingProviders.remove(j);
                            wasInLaunchingProviders = true;
                            j--;
                            launchingCount--;
                        }
                    }
                    if (wasInLaunchingProviders) {
                        mHandler.removeMessages(CONTENT_PROVIDER_PUBLISH_TIMEOUT_MSG, r);
                    }
                    synchronized (dst) {
                        dst.provider = src.provider;
                        dst.proc = r;
                        dst.notifyAll();
                    }
                    updateOomAdjLocked(r);
                    maybeUpdateProviderUsageStatsLocked(r, src.info.packageName,
                            src.info.authority);
                }
            }

            Binder.restoreCallingIdentity(origId);
        }
    }

    public boolean refContentProvider(IBinder connection, int stable, int unstable) {
        ContentProviderConnection conn;
        try {
            conn = (ContentProviderConnection)connection;
        } catch (ClassCastException e) {
            String msg ="refContentProvider: " + connection
                    + " not a ContentProviderConnection";
            Slog.w(TAG, msg);
            throw new IllegalArgumentException(msg);
        }
        if (conn == null) {
            throw new NullPointerException("connection is null");
        }

        synchronized (this) {
            if (stable > 0) {
                conn.numStableIncs += stable;
            }
            stable = conn.stableCount + stable;
            if (stable < 0) {
                throw new IllegalStateException("stableCount < 0: " + stable);
            }

            if (unstable > 0) {
                conn.numUnstableIncs += unstable;
            }
            unstable = conn.unstableCount + unstable;
            if (unstable < 0) {
                throw new IllegalStateException("unstableCount < 0: " + unstable);
            }

            if ((stable+unstable) <= 0) {
                throw new IllegalStateException("ref counts can't go to zero here: stable="
                        + stable + " unstable=" + unstable);
            }
            conn.stableCount = stable;
            conn.unstableCount = unstable;
            return !conn.dead;
        }
    }

    public void unstableProviderDied(IBinder connection) {
        ContentProviderConnection conn;
        try {
            conn = (ContentProviderConnection)connection;
        } catch (ClassCastException e) {
            String msg ="refContentProvider: " + connection
                    + " not a ContentProviderConnection";
            Slog.w(TAG, msg);
            throw new IllegalArgumentException(msg);
        }
        if (conn == null) {
            throw new NullPointerException("connection is null");
        }

        // Safely retrieve the content provider associated with the connection.
        IContentProvider provider;
        synchronized (this) {
            provider = conn.provider.provider;
        }

        if (provider == null) {
            // Um, yeah, we're way ahead of you.
            return;
        }

        // Make sure the caller is being honest with us.
        if (provider.asBinder().pingBinder()) {
            // Er, no, still looks good to us.
            synchronized (this) {
                Slog.w(TAG, "unstableProviderDied: caller " + Binder.getCallingUid()
                        + " says " + conn + " died, but we don't agree");
                return;
            }
        }

        // Well look at that!  It's dead!
        synchronized (this) {
            if (conn.provider.provider != provider) {
                // But something changed...  good enough.
                return;
            }

            ProcessRecord proc = conn.provider.proc;
            if (proc == null || proc.thread == null) {
                // Seems like the process is already cleaned up.
                return;
            }

            // As far as we're concerned, this is just like receiving a
            // death notification...  just a bit prematurely.
            Slog.i(TAG, "Process " + proc.processName + " (pid " + proc.pid
                    + ") early provider death");
            final long ident = Binder.clearCallingIdentity();
            try {
                appDiedLocked(proc);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    @Override
    public void appNotRespondingViaProvider(IBinder connection) {
        enforceCallingPermission(
                android.Manifest.permission.REMOVE_TASKS, "appNotRespondingViaProvider()");

        final ContentProviderConnection conn = (ContentProviderConnection) connection;
        if (conn == null) {
            Slog.w(TAG, "ContentProviderConnection is null");
            return;
        }

        final ProcessRecord host = conn.provider.proc;
        if (host == null) {
            Slog.w(TAG, "Failed to find hosting ProcessRecord");
            return;
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mAppErrors.appNotResponding(host, null, null, false,
                        "ContentProvider not responding");
            }
        });
    }

    public final void installSystemProviders() {
        List<ProviderInfo> providers;
        synchronized (this) {
            ProcessRecord app = mProcessNames.get("system", Process.SYSTEM_UID);
            providers = generateApplicationProvidersLocked(app);
            if (providers != null) {
                for (int i=providers.size()-1; i>=0; i--) {
                    ProviderInfo pi = (ProviderInfo)providers.get(i);
                    if ((pi.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM) == 0) {
                        Slog.w(TAG, "Not installing system proc provider " + pi.name
                                + ": not system .apk");
                        providers.remove(i);
                    }
                }
            }
        }
        if (providers != null) {
            mSystemThread.installSystemProviders(providers);
        }

        mCoreSettingsObserver = new CoreSettingsObserver(this);
        mFontScaleSettingObserver = new FontScaleSettingObserver();

        //mUsageStatsService.monitorPackages();
    }

    private void startPersistentApps(int matchFlags) {
        if (mFactoryTest == FactoryTest.FACTORY_TEST_LOW_LEVEL) return;

        synchronized (this) {
            try {
                final List<ApplicationInfo> apps = AppGlobals.getPackageManager()
                        .getPersistentApplications(STOCK_PM_FLAGS | matchFlags).getList();
                for (ApplicationInfo app : apps) {
                    if (!"android".equals(app.packageName)) {
                        addAppLocked(app, false, null /* ABI override */);
                    }
                }
            } catch (RemoteException ex) {
            }
        }
    }

    /**
     * When a user is unlocked, we need to install encryption-unaware providers
     * belonging to any running apps.
     */
    private void installEncryptionUnawareProviders(int userId) {
        // We're only interested in providers that are encryption unaware, and
        // we don't care about uninstalled apps, since there's no way they're
        // running at this point.
        final int matchFlags = GET_PROVIDERS | MATCH_DIRECT_BOOT_UNAWARE;

        synchronized (this) {
            final int NP = mProcessNames.getMap().size();
            for (int ip = 0; ip < NP; ip++) {
                final SparseArray<ProcessRecord> apps = mProcessNames.getMap().valueAt(ip);
                final int NA = apps.size();
                for (int ia = 0; ia < NA; ia++) {
                    final ProcessRecord app = apps.valueAt(ia);
                    if (app.userId != userId || app.thread == null || app.unlocked) continue;

                    final int NG = app.pkgList.size();
                    for (int ig = 0; ig < NG; ig++) {
                        try {
                            final String pkgName = app.pkgList.keyAt(ig);
                            final PackageInfo pkgInfo = AppGlobals.getPackageManager()
                                    .getPackageInfo(pkgName, matchFlags, userId);
                            if (pkgInfo != null && !ArrayUtils.isEmpty(pkgInfo.providers)) {
                                for (ProviderInfo pi : pkgInfo.providers) {
                                    // TODO: keep in sync with generateApplicationProvidersLocked
                                    final boolean processMatch = Objects.equals(pi.processName,
                                            app.processName) || pi.multiprocess;
                                    final boolean userMatch = isSingleton(pi.processName,
                                            pi.applicationInfo, pi.name, pi.flags)
                                                    ? (app.userId == UserHandle.USER_SYSTEM) : true;
                                    if (processMatch && userMatch) {
                                        Log.v(TAG, "Installing " + pi);
                                        app.thread.scheduleInstallProvider(pi);
                                    } else {
                                        Log.v(TAG, "Skipping " + pi);
                                    }
                                }
                            }
                        } catch (RemoteException ignored) {
                        }
                    }
                }
            }
        }
    }

    /**
     * Allows apps to retrieve the MIME type of a URI.
     * If an app is in the same user as the ContentProvider, or if it is allowed to interact across
     * users, then it does not need permission to access the ContentProvider.
     * Either, it needs cross-user uri grants.
     *
     * CTS tests for this functionality can be run with "runtest cts-appsecurity".
     *
     * Test cases are at cts/tests/appsecurity-tests/test-apps/UsePermissionDiffCert/
     *     src/com/android/cts/usespermissiondiffcertapp/AccessPermissionWithDiffSigTest.java
     */
    public String getProviderMimeType(Uri uri, int userId) {
        enforceNotIsolatedCaller("getProviderMimeType");
        final String name = uri.getAuthority();
        int callingUid = Binder.getCallingUid();
        int callingPid = Binder.getCallingPid();
        long ident = 0;
        boolean clearedIdentity = false;
        synchronized (this) {
            userId = mUserController.unsafeConvertIncomingUserLocked(userId);
        }
        if (canClearIdentity(callingPid, callingUid, userId)) {
            clearedIdentity = true;
            ident = Binder.clearCallingIdentity();
        }
        ContentProviderHolder holder = null;
        try {
            holder = getContentProviderExternalUnchecked(name, null, userId);
            if (holder != null) {
                return holder.provider.getType(uri);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Content provider dead retrieving " + uri, e);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "Exception while determining type of " + uri, e);
            return null;
        } finally {
            // We need to clear the identity to call removeContentProviderExternalUnchecked
            if (!clearedIdentity) {
                ident = Binder.clearCallingIdentity();
            }
            try {
                if (holder != null) {
                    removeContentProviderExternalUnchecked(name, null, userId);
                }
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        return null;
    }

    private boolean canClearIdentity(int callingPid, int callingUid, int userId) {
        if (UserHandle.getUserId(callingUid) == userId) {
            return true;
        }
        if (checkComponentPermission(INTERACT_ACROSS_USERS, callingPid,
                callingUid, -1, true) == PackageManager.PERMISSION_GRANTED
                || checkComponentPermission(INTERACT_ACROSS_USERS_FULL, callingPid,
                callingUid, -1, true) == PackageManager.PERMISSION_GRANTED) {
                return true;
        }
        return false;
    }

    // =========================================================
    // GLOBAL MANAGEMENT
    // =========================================================

    final ProcessRecord newProcessRecordLocked(ApplicationInfo info, String customProcess,
            boolean isolated, int isolatedUid) {
        String proc = customProcess != null ? customProcess : info.processName;
        BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
        final int userId = UserHandle.getUserId(info.uid);
        int uid = info.uid;
        if (isolated) {
            if (isolatedUid == 0) {
                int stepsLeft = Process.LAST_ISOLATED_UID - Process.FIRST_ISOLATED_UID + 1;
                while (true) {
                    if (mNextIsolatedProcessUid < Process.FIRST_ISOLATED_UID
                            || mNextIsolatedProcessUid > Process.LAST_ISOLATED_UID) {
                        mNextIsolatedProcessUid = Process.FIRST_ISOLATED_UID;
                    }
                    uid = UserHandle.getUid(userId, mNextIsolatedProcessUid);
                    mNextIsolatedProcessUid++;
                    if (mIsolatedProcesses.indexOfKey(uid) < 0) {
                        // No process for this uid, use it.
                        break;
                    }
                    stepsLeft--;
                    if (stepsLeft <= 0) {
                        return null;
                    }
                }
            } else {
                // Special case for startIsolatedProcess (internal only), where
                // the uid of the isolated process is specified by the caller.
                uid = isolatedUid;
            }

            // Register the isolated UID with this application so BatteryStats knows to
            // attribute resource usage to the application.
            //
            // NOTE: This is done here before addProcessNameLocked, which will tell BatteryStats
            // about the process state of the isolated UID *before* it is registered with the
            // owning application.
            mBatteryStatsService.addIsolatedUid(uid, info.uid);
        }
        final ProcessRecord r = new ProcessRecord(stats, info, proc, uid);
        if (!mBooted && !mBooting
                && userId == UserHandle.USER_SYSTEM
                && (info.flags & PERSISTENT_MASK) == PERSISTENT_MASK) {
            r.persistent = true;
        }
        addProcessNameLocked(r);
        return r;
    }

    final ProcessRecord addAppLocked(ApplicationInfo info, boolean isolated,
            String abiOverride) {
        ProcessRecord app;
        if (!isolated) {
            app = getProcessRecordLocked(info.processName, info.uid, true);
        } else {
            app = null;
        }

        if (app == null) {
            app = newProcessRecordLocked(info, null, isolated, 0);
            updateLruProcessLocked(app, false, null);
            updateOomAdjLocked();
        }

        // This package really, really can not be stopped.
        try {
            AppGlobals.getPackageManager().setPackageStoppedState(
                    info.packageName, false, UserHandle.getUserId(app.uid));
        } catch (RemoteException e) {
        } catch (IllegalArgumentException e) {
            Slog.w(TAG, "Failed trying to unstop package "
                    + info.packageName + ": " + e);
        }

        if ((info.flags & PERSISTENT_MASK) == PERSISTENT_MASK) {
            app.persistent = true;
            app.maxAdj = ProcessList.PERSISTENT_PROC_ADJ;
        }
        if (app.thread == null && mPersistentStartingProcesses.indexOf(app) < 0) {
            mPersistentStartingProcesses.add(app);
            startProcessLocked(app, "added application", app.processName, abiOverride,
                    null /* entryPoint */, null /* entryPointArgs */);
        }

        return app;
    }

    public void unhandledBack() {
        enforceCallingPermission(android.Manifest.permission.FORCE_BACK,
                "unhandledBack()");

        synchronized(this) {
            final long origId = Binder.clearCallingIdentity();
            try {
                getFocusedStack().unhandledBackLocked();
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    public ParcelFileDescriptor openContentUri(Uri uri) throws RemoteException {
        enforceNotIsolatedCaller("openContentUri");
        final int userId = UserHandle.getCallingUserId();
        String name = uri.getAuthority();
        ContentProviderHolder cph = getContentProviderExternalUnchecked(name, null, userId);
        ParcelFileDescriptor pfd = null;
        if (cph != null) {
            // We record the binder invoker's uid in thread-local storage before
            // going to the content provider to open the file.  Later, in the code
            // that handles all permissions checks, we look for this uid and use
            // that rather than the Activity Manager's own uid.  The effect is that
            // we do the check against the caller's permissions even though it looks
            // to the content provider like the Activity Manager itself is making
            // the request.
            Binder token = new Binder();
            sCallerIdentity.set(new Identity(
                    token, Binder.getCallingPid(), Binder.getCallingUid()));
            try {
                pfd = cph.provider.openFile(null, uri, "r", null, token);
            } catch (FileNotFoundException e) {
                // do nothing; pfd will be returned null
            } finally {
                // Ensure that whatever happens, we clean up the identity state
                sCallerIdentity.remove();
                // Ensure we're done with the provider.
                removeContentProviderExternalUnchecked(name, null, userId);
            }
        } else {
            Slog.d(TAG, "Failed to get provider for authority '" + name + "'");
        }
        return pfd;
    }

    // Actually is sleeping or shutting down or whatever else in the future
    // is an inactive state.
    boolean isSleepingOrShuttingDownLocked() {
        return isSleepingLocked() || mShuttingDown;
    }

    boolean isShuttingDownLocked() {
        return mShuttingDown;
    }

    boolean isSleepingLocked() {
        return mSleeping;
    }

    void onWakefulnessChanged(int wakefulness) {
        synchronized(this) {
            mWakefulness = wakefulness;
            updateSleepIfNeededLocked();
        }
    }

    void finishRunningVoiceLocked() {
        if (mRunningVoice != null) {
            mRunningVoice = null;
            mVoiceWakeLock.release();
            updateSleepIfNeededLocked();
        }
    }

    void startTimeTrackingFocusedActivityLocked() {
        if (!mSleeping && mCurAppTimeTracker != null && mFocusedActivity != null) {
            mCurAppTimeTracker.start(mFocusedActivity.packageName);
        }
    }

    void updateSleepIfNeededLocked() {
        if (mSleeping && !shouldSleepLocked()) {
            mSleeping = false;
            startTimeTrackingFocusedActivityLocked();
            mTopProcessState = ActivityManager.PROCESS_STATE_TOP;
            mStackSupervisor.comeOutOfSleepIfNeededLocked();
            updateOomAdjLocked();
        } else if (!mSleeping && shouldSleepLocked()) {
            mSleeping = true;
            if (mCurAppTimeTracker != null) {
                mCurAppTimeTracker.stop();
            }
            mTopProcessState = ActivityManager.PROCESS_STATE_TOP_SLEEPING;
            mStackSupervisor.goingToSleepLocked();
            updateOomAdjLocked();

            // Initialize the wake times of all processes.
            checkExcessivePowerUsageLocked(false);
            mHandler.removeMessages(CHECK_EXCESSIVE_WAKE_LOCKS_MSG);
            Message nmsg = mHandler.obtainMessage(CHECK_EXCESSIVE_WAKE_LOCKS_MSG);
            mHandler.sendMessageDelayed(nmsg, POWER_CHECK_DELAY);
        }
    }

    private boolean shouldSleepLocked() {
        // Resume applications while running a voice interactor.
        if (mRunningVoice != null) {
            return false;
        }

        // TODO: Transform the lock screen state into a sleep token instead.
        switch (mWakefulness) {
            case PowerManagerInternal.WAKEFULNESS_AWAKE:
            case PowerManagerInternal.WAKEFULNESS_DREAMING:
            case PowerManagerInternal.WAKEFULNESS_DOZING:
                // Pause applications whenever the lock screen is shown or any sleep
                // tokens have been acquired.
                return (mLockScreenShown != LOCK_SCREEN_HIDDEN || !mSleepTokens.isEmpty());
            case PowerManagerInternal.WAKEFULNESS_ASLEEP:
            default:
                // If we're asleep then pause applications unconditionally.
                return true;
        }
    }

    /** Pokes the task persister. */
    void notifyTaskPersisterLocked(TaskRecord task, boolean flush) {
        mRecentTasks.notifyTaskPersisterLocked(task, flush);
    }

    /** Notifies all listeners when the task stack has changed. */
    void notifyTaskStackChangedLocked() {
        mHandler.sendEmptyMessage(LOG_STACK_STATE);
        mHandler.removeMessages(NOTIFY_TASK_STACK_CHANGE_LISTENERS_MSG);
        Message nmsg = mHandler.obtainMessage(NOTIFY_TASK_STACK_CHANGE_LISTENERS_MSG);
        mHandler.sendMessageDelayed(nmsg, NOTIFY_TASK_STACK_CHANGE_LISTENERS_DELAY);
    }

    /** Notifies all listeners when an Activity is pinned. */
    void notifyActivityPinnedLocked() {
        mHandler.removeMessages(NOTIFY_ACTIVITY_PINNED_LISTENERS_MSG);
        mHandler.obtainMessage(NOTIFY_ACTIVITY_PINNED_LISTENERS_MSG).sendToTarget();
    }

    /**
     * Notifies all listeners when an attempt was made to start an an activity that is already
     * running in the pinned stack and the activity was not actually started, but the task is
     * either brought to the front or a new Intent is delivered to it.
     */
    void notifyPinnedActivityRestartAttemptLocked() {
        mHandler.removeMessages(NOTIFY_PINNED_ACTIVITY_RESTART_ATTEMPT_LISTENERS_MSG);
        mHandler.obtainMessage(NOTIFY_PINNED_ACTIVITY_RESTART_ATTEMPT_LISTENERS_MSG).sendToTarget();
    }

    /** Notifies all listeners when the pinned stack animation ends. */
    @Override
    public void notifyPinnedStackAnimationEnded() {
        synchronized (this) {
            mHandler.removeMessages(NOTIFY_PINNED_STACK_ANIMATION_ENDED_LISTENERS_MSG);
            mHandler.obtainMessage(
                    NOTIFY_PINNED_STACK_ANIMATION_ENDED_LISTENERS_MSG).sendToTarget();
        }
    }

    @Override
    public void notifyCleartextNetwork(int uid, byte[] firstPacket) {
        mHandler.obtainMessage(NOTIFY_CLEARTEXT_NETWORK_MSG, uid, 0, firstPacket).sendToTarget();
    }

    @Override
    public boolean shutdown(int timeout) {
        if (checkCallingPermission(android.Manifest.permission.SHUTDOWN)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires permission "
                    + android.Manifest.permission.SHUTDOWN);
        }

        boolean timedout = false;

        synchronized(this) {
            mShuttingDown = true;
            updateEventDispatchingLocked();
            timedout = mStackSupervisor.shutdownLocked(timeout);
        }

        mAppOpsService.shutdown();
        if (mUsageStatsService != null) {
            mUsageStatsService.prepareShutdown();
        }
        mBatteryStatsService.shutdown();
        synchronized (this) {
            mProcessStats.shutdownLocked();
            notifyTaskPersisterLocked(null, true);
        }

        return timedout;
    }

    public final void activitySlept(IBinder token) {
        if (DEBUG_ALL) Slog.v(TAG, "Activity slept: token=" + token);

        final long origId = Binder.clearCallingIdentity();

        synchronized (this) {
            final ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r != null) {
                mStackSupervisor.activitySleptLocked(r);
            }
        }

        Binder.restoreCallingIdentity(origId);
    }

    private String lockScreenShownToString() {
        switch (mLockScreenShown) {
            case LOCK_SCREEN_HIDDEN: return "LOCK_SCREEN_HIDDEN";
            case LOCK_SCREEN_LEAVING: return "LOCK_SCREEN_LEAVING";
            case LOCK_SCREEN_SHOWN: return "LOCK_SCREEN_SHOWN";
            default: return "Unknown=" + mLockScreenShown;
        }
    }

    void logLockScreen(String msg) {
        if (DEBUG_LOCKSCREEN) Slog.d(TAG_LOCKSCREEN, Debug.getCallers(2) + ":" + msg
                + " mLockScreenShown=" + lockScreenShownToString() + " mWakefulness="
                + PowerManagerInternal.wakefulnessToString(mWakefulness)
                + " mSleeping=" + mSleeping);
    }

    void startRunningVoiceLocked(IVoiceInteractionSession session, int targetUid) {
        Slog.d(TAG, "<<<  startRunningVoiceLocked()");
        mVoiceWakeLock.setWorkSource(new WorkSource(targetUid));
        if (mRunningVoice == null || mRunningVoice.asBinder() != session.asBinder()) {
            boolean wasRunningVoice = mRunningVoice != null;
            mRunningVoice = session;
            if (!wasRunningVoice) {
                mVoiceWakeLock.acquire();
                updateSleepIfNeededLocked();
            }
        }
    }

    private void updateEventDispatchingLocked() {
        mWindowManager.setEventDispatching(mBooted && !mShuttingDown);
    }

    public void setLockScreenShown(boolean showing, boolean occluded) {
        if (checkCallingPermission(android.Manifest.permission.DEVICE_POWER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires permission "
                    + android.Manifest.permission.DEVICE_POWER);
        }

        synchronized(this) {
            long ident = Binder.clearCallingIdentity();
            try {
                if (DEBUG_LOCKSCREEN) logLockScreen(" showing=" + showing + " occluded=" + occluded);
                mLockScreenShown = (showing && !occluded) ? LOCK_SCREEN_SHOWN : LOCK_SCREEN_HIDDEN;
                if (showing && occluded) {
                    // The lock screen is currently showing, but is occluded by a window that can
                    // show on top of the lock screen. In this can we want to dismiss the docked
                    // stack since it will be complicated/risky to try to put the activity on top
                    // of the lock screen in the right fullscreen configuration.
                    mStackSupervisor.moveTasksToFullscreenStackLocked(DOCKED_STACK_ID,
                            mStackSupervisor.mFocusedStack.getStackId() == DOCKED_STACK_ID);
                }

                updateSleepIfNeededLocked();
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    @Override
    public void notifyLockedProfile(@UserIdInt int userId) {
        try {
            if (!AppGlobals.getPackageManager().isUidPrivileged(Binder.getCallingUid())) {
                throw new SecurityException("Only privileged app can call notifyLockedProfile");
            }
        } catch (RemoteException ex) {
            throw new SecurityException("Fail to check is caller a privileged app", ex);
        }

        synchronized (this) {
            if (mStackSupervisor.isUserLockedProfile(userId)) {
                final long ident = Binder.clearCallingIdentity();
                try {
                    final int currentUserId = mUserController.getCurrentUserIdLocked();

                    // Drop locked freeform tasks out into the fullscreen stack.
                    // TODO: Redact the tasks in place. It's much better to keep them on the screen
                    //       where they were before, but in an obscured state.
                    mStackSupervisor.moveProfileTasksFromFreeformToFullscreenStackLocked(userId);

                    if (mUserController.isLockScreenDisabled(currentUserId)) {
                        // If there is no device lock, we will show the profile's credential page.
                        mActivityStarter.showConfirmDeviceCredential(userId);
                    } else {
                        // Showing launcher to avoid user entering credential twice.
                        startHomeActivityLocked(currentUserId, "notifyLockedProfile");
                    }
                } finally {
                    Binder.restoreCallingIdentity(ident);
                }
            }
        }
    }

    @Override
    public void startConfirmDeviceCredentialIntent(Intent intent) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "startConfirmDeviceCredentialIntent");
        synchronized (this) {
            final long ident = Binder.clearCallingIdentity();
            try {
                mActivityStarter.startConfirmCredentialIntent(intent);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    @Override
    public void stopAppSwitches() {
        if (checkCallingPermission(android.Manifest.permission.STOP_APP_SWITCHES)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("viewquires permission "
                    + android.Manifest.permission.STOP_APP_SWITCHES);
        }

        synchronized(this) {
            mAppSwitchesAllowedTime = SystemClock.uptimeMillis()
                    + APP_SWITCH_DELAY_TIME;
            mDidAppSwitch = false;
            mHandler.removeMessages(DO_PENDING_ACTIVITY_LAUNCHES_MSG);
            Message msg = mHandler.obtainMessage(DO_PENDING_ACTIVITY_LAUNCHES_MSG);
            mHandler.sendMessageDelayed(msg, APP_SWITCH_DELAY_TIME);
        }
    }

    public void resumeAppSwitches() {
        if (checkCallingPermission(android.Manifest.permission.STOP_APP_SWITCHES)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires permission "
                    + android.Manifest.permission.STOP_APP_SWITCHES);
        }

        synchronized(this) {
            // Note that we don't execute any pending app switches... we will
            // let those wait until either the timeout, or the next start
            // activity request.
            mAppSwitchesAllowedTime = 0;
        }
    }

    boolean checkAppSwitchAllowedLocked(int sourcePid, int sourceUid,
            int callingPid, int callingUid, String name) {
        if (mAppSwitchesAllowedTime < SystemClock.uptimeMillis()) {
            return true;
        }

        int perm = checkComponentPermission(
                android.Manifest.permission.STOP_APP_SWITCHES, sourcePid,
                sourceUid, -1, true);
        if (perm == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        // If the actual IPC caller is different from the logical source, then
        // also see if they are allowed to control app switches.
        if (callingUid != -1 && callingUid != sourceUid) {
            perm = checkComponentPermission(
                    android.Manifest.permission.STOP_APP_SWITCHES, callingPid,
                    callingUid, -1, true);
            if (perm == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }

        Slog.w(TAG, name + " request from " + sourceUid + " stopped");
        return false;
    }

    public void setDebugApp(String packageName, boolean waitForDebugger,
            boolean persistent) {
        enforceCallingPermission(android.Manifest.permission.SET_DEBUG_APP,
                "setDebugApp()");

        long ident = Binder.clearCallingIdentity();
        try {
            // Note that this is not really thread safe if there are multiple
            // callers into it at the same time, but that's not a situation we
            // care about.
            if (persistent) {
                final ContentResolver resolver = mContext.getContentResolver();
                Settings.Global.putString(
                    resolver, Settings.Global.DEBUG_APP,
                    packageName);
                Settings.Global.putInt(
                    resolver, Settings.Global.WAIT_FOR_DEBUGGER,
                    waitForDebugger ? 1 : 0);
            }

            synchronized (this) {
                if (!persistent) {
                    mOrigDebugApp = mDebugApp;
                    mOrigWaitForDebugger = mWaitForDebugger;
                }
                mDebugApp = packageName;
                mWaitForDebugger = waitForDebugger;
                mDebugTransient = !persistent;
                if (packageName != null) {
                    forceStopPackageLocked(packageName, -1, false, false, true, true,
                            false, UserHandle.USER_ALL, "set debug app");
                }
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    void setTrackAllocationApp(ApplicationInfo app, String processName) {
        synchronized (this) {
            boolean isDebuggable = "1".equals(SystemProperties.get(SYSTEM_DEBUGGABLE, "0"));
            if (!isDebuggable) {
                if ((app.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                    throw new SecurityException("Process not debuggable: " + app.packageName);
                }
            }

            mTrackAllocationApp = processName;
        }
    }

    void setProfileApp(ApplicationInfo app, String processName, ProfilerInfo profilerInfo) {
        synchronized (this) {
            boolean isDebuggable = "1".equals(SystemProperties.get(SYSTEM_DEBUGGABLE, "0"));
            if (!isDebuggable) {
                if ((app.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                    throw new SecurityException("Process not debuggable: " + app.packageName);
                }
            }
            mProfileApp = processName;
            mProfileFile = profilerInfo.profileFile;
            if (mProfileFd != null) {
                try {
                    mProfileFd.close();
                } catch (IOException e) {
                }
                mProfileFd = null;
            }
            mProfileFd = profilerInfo.profileFd;
            mSamplingInterval = profilerInfo.samplingInterval;
            mAutoStopProfiler = profilerInfo.autoStopProfiler;
            mProfileType = 0;
        }
    }

    void setNativeDebuggingAppLocked(ApplicationInfo app, String processName) {
        boolean isDebuggable = "1".equals(SystemProperties.get(SYSTEM_DEBUGGABLE, "0"));
        if (!isDebuggable) {
            if ((app.flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                throw new SecurityException("Process not debuggable: " + app.packageName);
            }
        }
        mNativeDebuggingApp = processName;
    }

    @Override
    public void setAlwaysFinish(boolean enabled) {
        enforceCallingPermission(android.Manifest.permission.SET_ALWAYS_FINISH,
                "setAlwaysFinish()");

        long ident = Binder.clearCallingIdentity();
        try {
            Settings.Global.putInt(
                    mContext.getContentResolver(),
                    Settings.Global.ALWAYS_FINISH_ACTIVITIES, enabled ? 1 : 0);

            synchronized (this) {
                mAlwaysFinishActivities = enabled;
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void setLenientBackgroundCheck(boolean enabled) {
        enforceCallingPermission(android.Manifest.permission.SET_PROCESS_LIMIT,
                "setLenientBackgroundCheck()");

        long ident = Binder.clearCallingIdentity();
        try {
            Settings.Global.putInt(
                    mContext.getContentResolver(),
                    Settings.Global.LENIENT_BACKGROUND_CHECK, enabled ? 1 : 0);

            synchronized (this) {
                mLenientBackgroundCheck = enabled;
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void setActivityController(IActivityController controller, boolean imAMonkey) {
        enforceCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER,
                "setActivityController()");
        synchronized (this) {
            mController = controller;
            mControllerIsAMonkey = imAMonkey;
            Watchdog.getInstance().setActivityController(controller);
        }
    }

    @Override
    public void setUserIsMonkey(boolean userIsMonkey) {
        synchronized (this) {
            synchronized (mPidsSelfLocked) {
                final int callingPid = Binder.getCallingPid();
                ProcessRecord precessRecord = mPidsSelfLocked.get(callingPid);
                if (precessRecord == null) {
                    throw new SecurityException("Unknown process: " + callingPid);
                }
                if (precessRecord.instrumentationUiAutomationConnection  == null) {
                    throw new SecurityException("Only an instrumentation process "
                            + "with a UiAutomation can call setUserIsMonkey");
                }
            }
            mUserIsMonkey = userIsMonkey;
        }
    }

    @Override
    public boolean isUserAMonkey() {
        synchronized (this) {
            // If there is a controller also implies the user is a monkey.
            return (mUserIsMonkey || (mController != null && mControllerIsAMonkey));
        }
    }

    public void requestBugReport(int bugreportType) {
        String service = null;
        switch (bugreportType) {
            case ActivityManager.BUGREPORT_OPTION_FULL:
                service = "bugreport";
                break;
            case ActivityManager.BUGREPORT_OPTION_INTERACTIVE:
                service = "bugreportplus";
                break;
            case ActivityManager.BUGREPORT_OPTION_REMOTE:
                service = "bugreportremote";
                break;
            case ActivityManager.BUGREPORT_OPTION_WEAR:
                service = "bugreportwear";
                break;
        }
        if (service == null) {
            throw new IllegalArgumentException("Provided bugreport type is not correct, value: "
                    + bugreportType);
        }
        enforceCallingPermission(android.Manifest.permission.DUMP, "requestBugReport");
        SystemProperties.set("ctl.start", service);
    }

    public static long getInputDispatchingTimeoutLocked(ActivityRecord r) {
        return r != null ? getInputDispatchingTimeoutLocked(r.app) : KEY_DISPATCHING_TIMEOUT;
    }

    public static long getInputDispatchingTimeoutLocked(ProcessRecord r) {
        if (r != null && (r.instrumentationClass != null || r.usingWrapper)) {
            return INSTRUMENTATION_KEY_DISPATCHING_TIMEOUT;
        }
        return KEY_DISPATCHING_TIMEOUT;
    }

    @Override
    public long inputDispatchingTimedOut(int pid, final boolean aboveSystem, String reason) {
        if (checkCallingPermission(android.Manifest.permission.FILTER_EVENTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires permission "
                    + android.Manifest.permission.FILTER_EVENTS);
        }
        ProcessRecord proc;
        long timeout;
        synchronized (this) {
            synchronized (mPidsSelfLocked) {
                proc = mPidsSelfLocked.get(pid);
            }
            timeout = getInputDispatchingTimeoutLocked(proc);
        }

        if (!inputDispatchingTimedOut(proc, null, null, aboveSystem, reason)) {
            return -1;
        }

        return timeout;
    }

    /**
     * Handle input dispatching timeouts.
     * Returns whether input dispatching should be aborted or not.
     */
    public boolean inputDispatchingTimedOut(final ProcessRecord proc,
            final ActivityRecord activity, final ActivityRecord parent,
            final boolean aboveSystem, String reason) {
        if (checkCallingPermission(android.Manifest.permission.FILTER_EVENTS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires permission "
                    + android.Manifest.permission.FILTER_EVENTS);
        }

        final String annotation;
        if (reason == null) {
            annotation = "Input dispatching timed out";
        } else {
            annotation = "Input dispatching timed out (" + reason + ")";
        }

        if (proc != null) {
            synchronized (this) {
                if (proc.debugging) {
                    return false;
                }

                if (mDidDexOpt) {
                    // Give more time since we were dexopting.
                    mDidDexOpt = false;
                    return false;
                }

                if (proc.instrumentationClass != null) {
                    Bundle info = new Bundle();
                    info.putString("shortMsg", "keyDispatchingTimedOut");
                    info.putString("longMsg", annotation);
                    finishInstrumentationLocked(proc, Activity.RESULT_CANCELED, info);
                    return true;
                }
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mAppErrors.appNotResponding(proc, activity, parent, aboveSystem, annotation);
                }
            });
        }

        return true;
    }

    @Override
    public Bundle getAssistContextExtras(int requestType) {
        PendingAssistExtras pae = enqueueAssistContext(requestType, null, null, null,
                null, null, true /* focused */, true /* newSessionId */,
                UserHandle.getCallingUserId(), null, PENDING_ASSIST_EXTRAS_TIMEOUT);
        if (pae == null) {
            return null;
        }
        synchronized (pae) {
            while (!pae.haveResult) {
                try {
                    pae.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        synchronized (this) {
            buildAssistBundleLocked(pae, pae.result);
            mPendingAssistExtras.remove(pae);
            mUiHandler.removeCallbacks(pae);
        }
        return pae.extras;
    }

    @Override
    public boolean isAssistDataAllowedOnCurrentActivity() {
        int userId;
        synchronized (this) {
            userId = mUserController.getCurrentUserIdLocked();
            ActivityRecord activity = getFocusedStack().topActivity();
            if (activity == null) {
                return false;
            }
            userId = activity.userId;
        }
        DevicePolicyManager dpm = (DevicePolicyManager) mContext.getSystemService(
                Context.DEVICE_POLICY_SERVICE);
        return (dpm == null) || (!dpm.getScreenCaptureDisabled(null, userId));
    }

    @Override
    public boolean showAssistFromActivity(IBinder token, Bundle args) {
        long ident = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                ActivityRecord caller = ActivityRecord.forTokenLocked(token);
                ActivityRecord top = getFocusedStack().topActivity();
                if (top != caller) {
                    Slog.w(TAG, "showAssistFromActivity failed: caller " + caller
                            + " is not current top " + top);
                    return false;
                }
                if (!top.nowVisible) {
                    Slog.w(TAG, "showAssistFromActivity failed: caller " + caller
                            + " is not visible");
                    return false;
                }
            }
            AssistUtils utils = new AssistUtils(mContext);
            return utils.showSessionForActiveService(args,
                    VoiceInteractionSession.SHOW_SOURCE_APPLICATION, null, token);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public boolean requestAssistContextExtras(int requestType, IResultReceiver receiver,
            Bundle receiverExtras,
            IBinder activityToken, boolean focused, boolean newSessionId) {
        return enqueueAssistContext(requestType, null, null, receiver, receiverExtras,
                activityToken, focused, newSessionId,
                UserHandle.getCallingUserId(), null, PENDING_ASSIST_EXTRAS_LONG_TIMEOUT)
                != null;
    }

    private PendingAssistExtras enqueueAssistContext(int requestType, Intent intent, String hint,
            IResultReceiver receiver, Bundle receiverExtras, IBinder activityToken,
            boolean focused, boolean newSessionId, int userHandle, Bundle args, long timeout) {
        enforceCallingPermission(android.Manifest.permission.GET_TOP_ACTIVITY_INFO,
                "enqueueAssistContext()");
        synchronized (this) {
            ActivityRecord activity = getFocusedStack().topActivity();
            if (activity == null) {
                Slog.w(TAG, "getAssistContextExtras failed: no top activity");
                return null;
            }
            if (activity.app == null || activity.app.thread == null) {
                Slog.w(TAG, "getAssistContextExtras failed: no process for " + activity);
                return null;
            }
            if (focused) {
                if (activityToken != null) {
                    ActivityRecord caller = ActivityRecord.forTokenLocked(activityToken);
                    if (activity != caller) {
                        Slog.w(TAG, "enqueueAssistContext failed: caller " + caller
                                + " is not current top " + activity);
                        return null;
                    }
                }
            } else {
                activity = ActivityRecord.forTokenLocked(activityToken);
                if (activity == null) {
                    Slog.w(TAG, "enqueueAssistContext failed: activity for token=" + activityToken
                            + " couldn't be found");
                    return null;
                }
            }

            PendingAssistExtras pae;
            Bundle extras = new Bundle();
            if (args != null) {
                extras.putAll(args);
            }
            extras.putString(Intent.EXTRA_ASSIST_PACKAGE, activity.packageName);
            extras.putInt(Intent.EXTRA_ASSIST_UID, activity.app.uid);
            pae = new PendingAssistExtras(activity, extras, intent, hint, receiver, receiverExtras,
                    userHandle);
            // Increment the sessionId if necessary
            if (newSessionId) {
                mViSessionId++;
            }
            try {
                activity.app.thread.requestAssistContextExtras(activity.appToken, pae,
                        requestType, mViSessionId);
                mPendingAssistExtras.add(pae);
                mUiHandler.postDelayed(pae, timeout);
            } catch (RemoteException e) {
                Slog.w(TAG, "getAssistContextExtras failed: crash calling " + activity);
                return null;
            }
            return pae;
        }
    }

    void pendingAssistExtrasTimedOut(PendingAssistExtras pae) {
        IResultReceiver receiver;
        synchronized (this) {
            mPendingAssistExtras.remove(pae);
            receiver = pae.receiver;
        }
        if (receiver != null) {
            // Caller wants result sent back to them.
            Bundle sendBundle = new Bundle();
            // At least return the receiver extras
            sendBundle.putBundle(VoiceInteractionSession.KEY_RECEIVER_EXTRAS,
                    pae.receiverExtras);
            try {
                pae.receiver.send(0, sendBundle);
            } catch (RemoteException e) {
            }
        }
    }

    private void buildAssistBundleLocked(PendingAssistExtras pae, Bundle result) {
        if (result != null) {
            pae.extras.putBundle(Intent.EXTRA_ASSIST_CONTEXT, result);
        }
        if (pae.hint != null) {
            pae.extras.putBoolean(pae.hint, true);
        }
    }

    public void reportAssistContextExtras(IBinder token, Bundle extras, AssistStructure structure,
            AssistContent content, Uri referrer) {
        PendingAssistExtras pae = (PendingAssistExtras)token;
        synchronized (pae) {
            pae.result = extras;
            pae.structure = structure;
            pae.content = content;
            if (referrer != null) {
                pae.extras.putParcelable(Intent.EXTRA_REFERRER, referrer);
            }
            pae.haveResult = true;
            pae.notifyAll();
            if (pae.intent == null && pae.receiver == null) {
                // Caller is just waiting for the result.
                return;
            }
        }

        // We are now ready to launch the assist activity.
        IResultReceiver sendReceiver = null;
        Bundle sendBundle = null;
        synchronized (this) {
            buildAssistBundleLocked(pae, extras);
            boolean exists = mPendingAssistExtras.remove(pae);
            mUiHandler.removeCallbacks(pae);
            if (!exists) {
                // Timed out.
                return;
            }
            if ((sendReceiver=pae.receiver) != null) {
                // Caller wants result sent back to them.
                sendBundle = new Bundle();
                sendBundle.putBundle(VoiceInteractionSession.KEY_DATA, pae.extras);
                sendBundle.putParcelable(VoiceInteractionSession.KEY_STRUCTURE, pae.structure);
                sendBundle.putParcelable(VoiceInteractionSession.KEY_CONTENT, pae.content);
                sendBundle.putBundle(VoiceInteractionSession.KEY_RECEIVER_EXTRAS,
                        pae.receiverExtras);
            }
        }
        if (sendReceiver != null) {
            try {
                sendReceiver.send(0, sendBundle);
            } catch (RemoteException e) {
            }
            return;
        }

        long ident = Binder.clearCallingIdentity();
        try {
            pae.intent.replaceExtras(pae.extras);
            pae.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            closeSystemDialogs("assist");
            try {
                mContext.startActivityAsUser(pae.intent, new UserHandle(pae.userHandle));
            } catch (ActivityNotFoundException e) {
                Slog.w(TAG, "No activity to handle assist action.", e);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    public boolean launchAssistIntent(Intent intent, int requestType, String hint, int userHandle,
            Bundle args) {
        return enqueueAssistContext(requestType, intent, hint, null, null, null,
                true /* focused */, true /* newSessionId */,
                userHandle, args, PENDING_ASSIST_EXTRAS_TIMEOUT) != null;
    }

    public void registerProcessObserver(IProcessObserver observer) {
        enforceCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER,
                "registerProcessObserver()");
        synchronized (this) {
            mProcessObservers.register(observer);
        }
    }

    @Override
    public void unregisterProcessObserver(IProcessObserver observer) {
        synchronized (this) {
            mProcessObservers.unregister(observer);
        }
    }

    @Override
    public void registerUidObserver(IUidObserver observer, int which) {
        enforceCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER,
                "registerUidObserver()");
        synchronized (this) {
            mUidObservers.register(observer, which);
        }
    }

    @Override
    public void unregisterUidObserver(IUidObserver observer) {
        synchronized (this) {
            mUidObservers.unregister(observer);
        }
    }

    @Override
    public boolean convertFromTranslucent(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final ActivityRecord r = ActivityRecord.isInStackLocked(token);
                if (r == null) {
                    return false;
                }
                final boolean translucentChanged = r.changeWindowTranslucency(true);
                if (translucentChanged) {
                    r.task.stack.releaseBackgroundResources(r);
                    mStackSupervisor.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
                }
                mWindowManager.setAppFullscreen(token, true);
                return translucentChanged;
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public boolean convertToTranslucent(IBinder token, ActivityOptions options) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final ActivityRecord r = ActivityRecord.isInStackLocked(token);
                if (r == null) {
                    return false;
                }
                int index = r.task.mActivities.lastIndexOf(r);
                if (index > 0) {
                    ActivityRecord under = r.task.mActivities.get(index - 1);
                    under.returningOptions = options;
                }
                final boolean translucentChanged = r.changeWindowTranslucency(false);
                if (translucentChanged) {
                    r.task.stack.convertActivityToTranslucent(r);
                }
                mStackSupervisor.ensureActivitiesVisibleLocked(null, 0, !PRESERVE_WINDOWS);
                mWindowManager.setAppFullscreen(token, false);
                return translucentChanged;
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public boolean requestVisibleBehind(IBinder token, boolean visible) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final ActivityRecord r = ActivityRecord.isInStackLocked(token);
                if (r != null) {
                    return mStackSupervisor.requestVisibleBehindLocked(r, visible);
                }
            }
            return false;
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public boolean isBackgroundVisibleBehind(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final ActivityStack stack = ActivityRecord.getStackLocked(token);
                final boolean visible = stack == null ? false : stack.hasVisibleBehindActivity();
                if (DEBUG_VISIBLE_BEHIND) Slog.d(TAG_VISIBLE_BEHIND,
                        "isBackgroundVisibleBehind: stack=" + stack + " visible=" + visible);
                return visible;
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public ActivityOptions getActivityOptions(IBinder token) {
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                final ActivityRecord r = ActivityRecord.isInStackLocked(token);
                if (r != null) {
                    final ActivityOptions activityOptions = r.pendingOptions;
                    r.pendingOptions = null;
                    return activityOptions;
                }
                return null;
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    @Override
    public void setImmersive(IBinder token, boolean immersive) {
        synchronized(this) {
            final ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                throw new IllegalArgumentException();
            }
            r.immersive = immersive;

            // update associated state if we're frontmost
            if (r == mFocusedActivity) {
                if (DEBUG_IMMERSIVE) Slog.d(TAG_IMMERSIVE, "Frontmost changed immersion: "+ r);
                applyUpdateLockStateLocked(r);
            }
        }
    }

    @Override
    public boolean isImmersive(IBinder token) {
        synchronized (this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                throw new IllegalArgumentException();
            }
            return r.immersive;
        }
    }

    public void setVrThread(int tid) {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_VR_MODE)) {
            throw new UnsupportedOperationException("VR mode not supported on this device!");
        }

        synchronized (this) {
            ProcessRecord proc;
            synchronized (mPidsSelfLocked) {
                final int pid = Binder.getCallingPid();
                proc = mPidsSelfLocked.get(pid);

                if (proc != null && mInVrMode && tid >= 0) {
                    // ensure the tid belongs to the process
                    if (!Process.isThreadInProcess(pid, tid)) {
                        throw new IllegalArgumentException("VR thread does not belong to process");
                    }

                    // reset existing VR thread to CFS if this thread still exists and belongs to
                    // the calling process
                    if (proc.vrThreadTid != 0
                            && Process.isThreadInProcess(pid, proc.vrThreadTid)) {
                        try {
                            Process.setThreadScheduler(proc.vrThreadTid, Process.SCHED_OTHER, 0);
                        } catch (IllegalArgumentException e) {
                            // Ignore this.  Only occurs in race condition where previous VR thread
                            // was destroyed during this method call.
                        }
                    }

                    proc.vrThreadTid = tid;

                    // promote to FIFO now if the tid is non-zero
                    try {
                        if (proc.curSchedGroup == ProcessList.SCHED_GROUP_TOP_APP &&
                            proc.vrThreadTid > 0) {
                            Process.setThreadScheduler(proc.vrThreadTid,
                                Process.SCHED_FIFO | Process.SCHED_RESET_ON_FORK, 1);
                        }
                    } catch (IllegalArgumentException e) {
                        Slog.e(TAG, "Failed to set scheduling policy, thread does"
                               + " not exist:\n" + e);
                    }
                }
            }
        }
    }

    @Override
    public void setRenderThread(int tid) {
        synchronized (this) {
            ProcessRecord proc;
            synchronized (mPidsSelfLocked) {
                int pid = Binder.getCallingPid();
                proc = mPidsSelfLocked.get(pid);
                if (proc != null && proc.renderThreadTid == 0 && tid > 0) {
                    // ensure the tid belongs to the process
                    if (!Process.isThreadInProcess(pid, tid)) {
                        throw new IllegalArgumentException(
                            "Render thread does not belong to process");
                    }
                    proc.renderThreadTid = tid;
                    if (DEBUG_OOM_ADJ) {
                        Slog.d("UI_FIFO", "Set RenderThread tid " + tid + " for pid " + pid);
                    }
                    // promote to FIFO now
                    if (proc.curSchedGroup == ProcessList.SCHED_GROUP_TOP_APP) {
                        if (DEBUG_OOM_ADJ) Slog.d("UI_FIFO", "Promoting " + tid + "out of band");
                        if (mUseFifoUiScheduling) {
                            Process.setThreadScheduler(proc.renderThreadTid,
                                Process.SCHED_FIFO | Process.SCHED_RESET_ON_FORK, 1);
                        } else {
                            Process.setThreadPriority(proc.renderThreadTid, -10);
                        }
                    }
                } else {
                    if (DEBUG_OOM_ADJ) {
                        Slog.d("UI_FIFO", "Didn't set thread from setRenderThread? " +
                               "PID: " + pid + ", TID: " + tid + " FIFO: " +
                               mUseFifoUiScheduling);
                    }
                }
            }
        }
    }

    @Override
    public int setVrMode(IBinder token, boolean enabled, ComponentName packageName) {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_VR_MODE)) {
            throw new UnsupportedOperationException("VR mode not supported on this device!");
        }

        final VrManagerInternal vrService = LocalServices.getService(VrManagerInternal.class);

        ActivityRecord r;
        synchronized (this) {
            r = ActivityRecord.isInStackLocked(token);
        }

        if (r == null) {
            throw new IllegalArgumentException();
        }

        int err;
        if ((err = vrService.hasVrPackage(packageName, r.userId)) !=
                VrManagerInternal.NO_ERROR) {
            return err;
        }

        synchronized(this) {
            r.requestedVrComponent = (enabled) ? packageName : null;

            // Update associated state if this activity is currently focused
            if (r == mFocusedActivity) {
                applyUpdateVrModeLocked(r);
            }
            return 0;
        }
    }

    @Override
    public boolean isVrModePackageEnabled(ComponentName packageName) {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_VR_MODE)) {
            throw new UnsupportedOperationException("VR mode not supported on this device!");
        }

        final VrManagerInternal vrService = LocalServices.getService(VrManagerInternal.class);

        return vrService.hasVrPackage(packageName, UserHandle.getCallingUserId()) ==
                VrManagerInternal.NO_ERROR;
    }

    public boolean isTopActivityImmersive() {
        enforceNotIsolatedCaller("startActivity");
        synchronized (this) {
            ActivityRecord r = getFocusedStack().topRunningActivityLocked();
            return (r != null) ? r.immersive : false;
        }
    }

    @Override
    public boolean isTopOfTask(IBinder token) {
        synchronized (this) {
            ActivityRecord r = ActivityRecord.isInStackLocked(token);
            if (r == null) {
                throw new IllegalArgumentException();
            }
            return r.task.getTopActivity() == r;
        }
    }

    @Override
    public void setHasTopUi(boolean hasTopUi) throws RemoteException {
        if (checkCallingPermission(permission.INTERNAL_SYSTEM_WINDOW) != PERMISSION_GRANTED) {
            String msg = "Permission Denial: setHasTopUi() from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " requires " + permission.INTERNAL_SYSTEM_WINDOW;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        final int pid = Binder.getCallingPid();
        final long origId = Binder.clearCallingIdentity();
        try {
            synchronized (this) {
                boolean changed = false;
                ProcessRecord pr;
                synchronized (mPidsSelfLocked) {
                    pr = mPidsSelfLocked.get(pid);
                    if (pr == null) {
                        Slog.w(TAG, "setHasTopUi called on unknown pid: " + pid);
                        return;
                    }
                    if (pr.hasTopUi != hasTopUi) {
                        Slog.i(TAG, "Setting hasTopUi=" + hasTopUi + " for pid=" + pid);
                        pr.hasTopUi = hasTopUi;
                        changed = true;
                    }
                }
                if (changed) {
                    updateOomAdjLocked(pr);
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    public final void enterSafeMode() {
        synchronized(this) {
            // It only makes sense to do this before the system is ready
            // and started launching other packages.
            if (!mSystemReady) {
                try {
                    AppGlobals.getPackageManager().enterSafeMode();
                } catch (RemoteException e) {
                }
            }

            mSafeMode = true;
        }
    }

    public final void showSafeModeOverlay() {
        View v = LayoutInflater.from(mContext).inflate(
                com.android.internal.R.layout.safe_mode, null);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_SECURE_SYSTEM_OVERLAY;
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
        lp.gravity = Gravity.BOTTOM | Gravity.START;
        lp.format = v.getBackground().getOpacity();
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        lp.privateFlags |= WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
        ((WindowManager)mContext.getSystemService(
                Context.WINDOW_SERVICE)).addView(v, lp);
    }

    public void noteWakeupAlarm(IIntentSender sender, int sourceUid, String sourcePkg, String tag) {
        if (sender != null && !(sender instanceof PendingIntentRecord)) {
            return;
        }
        final PendingIntentRecord rec = (PendingIntentRecord)sender;
        final BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
        synchronized (stats) {
            if (mBatteryStatsService.isOnBattery()) {
                mBatteryStatsService.enforceCallingPermission();
                int MY_UID = Binder.getCallingUid();
                final int uid;
                if (sender == null) {
                    uid = sourceUid;
                } else {
                    uid = rec.uid == MY_UID ? Process.SYSTEM_UID : rec.uid;
                }
                BatteryStatsImpl.Uid.Pkg pkg =
                    stats.getPackageStatsLocked(sourceUid >= 0 ? sourceUid : uid,
                            sourcePkg != null ? sourcePkg : rec.key.packageName);
                pkg.noteWakeupAlarmLocked(tag);
            }
        }
    }

    public void noteAlarmStart(IIntentSender sender, int sourceUid, String tag) {
        if (sender != null && !(sender instanceof PendingIntentRecord)) {
            return;
        }
        final PendingIntentRecord rec = (PendingIntentRecord)sender;
        final BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
        synchronized (stats) {
            mBatteryStatsService.enforceCallingPermission();
            int MY_UID = Binder.getCallingUid();
            final int uid;
            if (sender == null) {
                uid = sourceUid;
            } else {
                uid = rec.uid == MY_UID ? Process.SYSTEM_UID : rec.uid;
            }
            mBatteryStatsService.noteAlarmStart(tag, sourceUid >= 0 ? sourceUid : uid);
        }
    }

    public void noteAlarmFinish(IIntentSender sender, int sourceUid, String tag) {
        if (sender != null && !(sender instanceof PendingIntentRecord)) {
            return;
        }
        final PendingIntentRecord rec = (PendingIntentRecord)sender;
        final BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
        synchronized (stats) {
            mBatteryStatsService.enforceCallingPermission();
            int MY_UID = Binder.getCallingUid();
            final int uid;
            if (sender == null) {
                uid = sourceUid;
            } else {
                uid = rec.uid == MY_UID ? Process.SYSTEM_UID : rec.uid;
            }
            mBatteryStatsService.noteAlarmFinish(tag, sourceUid >= 0 ? sourceUid : uid);
        }
    }

    public boolean killPids(int[] pids, String pReason, boolean secure) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("killPids only available to the system");
        }
        String reason = (pReason == null) ? "Unknown" : pReason;
        // XXX Note: don't acquire main activity lock here, because the window
        // manager calls in with its locks held.

        boolean killed = false;
        synchronized (mPidsSelfLocked) {
            int worstType = 0;
            for (int i=0; i<pids.length; i++) {
                ProcessRecord proc = mPidsSelfLocked.get(pids[i]);
                if (proc != null) {
                    int type = proc.setAdj;
                    if (type > worstType) {
                        worstType = type;
                    }
                }
            }

            // If the worst oom_adj is somewhere in the cached proc LRU range,
            // then constrain it so we will kill all cached procs.
            if (worstType < ProcessList.CACHED_APP_MAX_ADJ
                    && worstType > ProcessList.CACHED_APP_MIN_ADJ) {
                worstType = ProcessList.CACHED_APP_MIN_ADJ;
            }

            // If this is not a secure call, don't let it kill processes that
            // are important.
            if (!secure && worstType < ProcessList.SERVICE_ADJ) {
                worstType = ProcessList.SERVICE_ADJ;
            }

            Slog.w(TAG, "Killing processes " + reason + " at adjustment " + worstType);
            for (int i=0; i<pids.length; i++) {
                ProcessRecord proc = mPidsSelfLocked.get(pids[i]);
                if (proc == null) {
                    continue;
                }
                int adj = proc.setAdj;
                if (adj >= worstType && !proc.killedByAm) {
                    proc.kill(reason, true);
                    killed = true;
                }
            }
        }
        return killed;
    }

    @Override
    public void killUid(int appId, int userId, String reason) {
        enforceCallingPermission(Manifest.permission.KILL_UID, "killUid");
        synchronized (this) {
            final long identity = Binder.clearCallingIdentity();
            try {
                killPackageProcessesLocked(null, appId, userId,
                        ProcessList.PERSISTENT_PROC_ADJ, false, true, true, true,
                        reason != null ? reason : "kill uid");
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
    }

    @Override
    public boolean killProcessesBelowForeground(String reason) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("killProcessesBelowForeground() only available to system");
        }

        return killProcessesBelowAdj(ProcessList.FOREGROUND_APP_ADJ, reason);
    }

    private boolean killProcessesBelowAdj(int belowAdj, String reason) {
        if (Binder.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("killProcessesBelowAdj() only available to system");
        }

        boolean killed = false;
        synchronized (mPidsSelfLocked) {
            final int size = mPidsSelfLocked.size();
            for (int i = 0; i < size; i++) {
                final int pid = mPidsSelfLocked.keyAt(i);
                final ProcessRecord proc = mPidsSelfLocked.valueAt(i);
                if (proc == null) continue;

                final int adj = proc.setAdj;
                if (adj > belowAdj && !proc.killedByAm) {
                    proc.kill(reason, true);
                    killed = true;
                }
            }
        }
        return killed;
    }

    @Override
    public void hang(final IBinder who, boolean allowRestart) {
        if (checkCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires permission "
                    + android.Manifest.permission.SET_ACTIVITY_WATCHER);
        }

        final IBinder.DeathRecipient death = new DeathRecipient() {
            @Override
            public void binderDied() {
                synchronized (this) {
                    notifyAll();
                }
            }
        };

        try {
            who.linkToDeath(death, 0);
        } catch (RemoteException e) {
            Slog.w(TAG, "hang: given caller IBinder is already dead.");
            return;
        }

        synchronized (this) {
            Watchdog.getInstance().setAllowRestart(allowRestart);
            Slog.i(TAG, "Hanging system process at request of pid " + Binder.getCallingPid());
            synchronized (death) {
                while (who.isBinderAlive()) {
                    try {
                        death.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            Watchdog.getInstance().setAllowRestart(true);
        }
    }

    @Override
    public void restart() {
        if (checkCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires permission "
                    + android.Manifest.permission.SET_ACTIVITY_WATCHER);
        }

        Log.i(TAG, "Sending shutdown broadcast...");

        BroadcastReceiver br = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                // Now the broadcast is done, finish up the low-level shutdown.
                Log.i(TAG, "Shutting down activity manager...");
                shutdown(10000);
                Log.i(TAG, "Shutdown complete, restarting!");
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        };

        // First send the high-level shut down broadcast.
        Intent intent = new Intent(Intent.ACTION_SHUTDOWN);
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(Intent.EXTRA_SHUTDOWN_USERSPACE_ONLY, true);
        /* For now we are not doing a clean shutdown, because things seem to get unhappy.
        mContext.sendOrderedBroadcastAsUser(intent,
                UserHandle.ALL, null, br, mHandler, 0, null, null);
        */
        br.onReceive(mContext, intent);
    }

    private long getLowRamTimeSinceIdle(long now) {
        return mLowRamTimeSinceLastIdle + (mLowRamStartTime > 0 ? (now-mLowRamStartTime) : 0);
    }

    @Override
    public void performIdleMaintenance() {
        if (checkCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires permission "
                    + android.Manifest.permission.SET_ACTIVITY_WATCHER);
        }

        synchronized (this) {
            final long now = SystemClock.uptimeMillis();
            final long timeSinceLastIdle = now - mLastIdleTime;
            final long lowRamSinceLastIdle = getLowRamTimeSinceIdle(now);
            mLastIdleTime = now;
            mLowRamTimeSinceLastIdle = 0;
            if (mLowRamStartTime != 0) {
                mLowRamStartTime = now;
            }

            StringBuilder sb = new StringBuilder(128);
            sb.append("Idle maintenance over ");
            TimeUtils.formatDuration(timeSinceLastIdle, sb);
            sb.append(" low RAM for ");
            TimeUtils.formatDuration(lowRamSinceLastIdle, sb);
            Slog.i(TAG, sb.toString());

            // If at least 1/3 of our time since the last idle period has been spent
            // with RAM low, then we want to kill processes.
            boolean doKilling = lowRamSinceLastIdle > (timeSinceLastIdle/3);

            for (int i = mLruProcesses.size() - 1 ; i >= 0 ; i--) {
                ProcessRecord proc = mLruProcesses.get(i);
                if (proc.notCachedSinceIdle) {
                    if (proc.setProcState != ActivityManager.PROCESS_STATE_TOP_SLEEPING
                            && proc.setProcState >= ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE
                            && proc.setProcState <= ActivityManager.PROCESS_STATE_SERVICE) {
                        if (doKilling && proc.initialIdlePss != 0
                                && proc.lastPss > ((proc.initialIdlePss*3)/2)) {
                            sb = new StringBuilder(128);
                            sb.append("Kill");
                            sb.append(proc.processName);
                            sb.append(" in idle maint: pss=");
                            sb.append(proc.lastPss);
                            sb.append(", swapPss=");
                            sb.append(proc.lastSwapPss);
                            sb.append(", initialPss=");
                            sb.append(proc.initialIdlePss);
                            sb.append(", period=");
                            TimeUtils.formatDuration(timeSinceLastIdle, sb);
                            sb.append(", lowRamPeriod=");
                            TimeUtils.formatDuration(lowRamSinceLastIdle, sb);
                            Slog.wtfQuiet(TAG, sb.toString());
                            proc.kill("idle maint (pss " + proc.lastPss
                                    + " from " + proc.initialIdlePss + ")", true);
                        }
                    }
                } else if (proc.setProcState < ActivityManager.PROCESS_STATE_HOME
                        && proc.setProcState > ActivityManager.PROCESS_STATE_NONEXISTENT) {
                    proc.notCachedSinceIdle = true;
                    proc.initialIdlePss = 0;
                    proc.nextPssTime = ProcessList.computeNextPssTime(proc.setProcState, true,
                            mTestPssMode, isSleepingLocked(), now);
                }
            }

            mHandler.removeMessages(REQUEST_ALL_PSS_MSG);
            mHandler.sendEmptyMessageDelayed(REQUEST_ALL_PSS_MSG, 2*60*1000);
        }
    }

    @Override
    public void sendIdleJobTrigger() {
        if (checkCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Requires permission "
                    + android.Manifest.permission.SET_ACTIVITY_WATCHER);
        }

        final long ident = Binder.clearCallingIdentity();
        try {
            Intent intent = new Intent(ACTION_TRIGGER_IDLE)
                    .setPackage("android")
                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            broadcastIntent(null, intent, null, null, 0, null, null, null,
                    android.app.AppOpsManager.OP_NONE, null, true, false, UserHandle.USER_ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void retrieveSettings() {
        final ContentResolver resolver = mContext.getContentResolver();
        final boolean freeformWindowManagement =
                mContext.getPackageManager().hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT)
                        || Settings.Global.getInt(
                                resolver, DEVELOPMENT_ENABLE_FREEFORM_WINDOWS_SUPPORT, 0) != 0;
        final boolean supportsPictureInPicture =
                mContext.getPackageManager().hasSystemFeature(FEATURE_PICTURE_IN_PICTURE);

        final boolean supportsMultiWindow = ActivityManager.supportsMultiWindow();
        final String debugApp = Settings.Global.getString(resolver, DEBUG_APP);
        final boolean waitForDebugger = Settings.Global.getInt(resolver, WAIT_FOR_DEBUGGER, 0) != 0;
        final boolean alwaysFinishActivities =
                Settings.Global.getInt(resolver, ALWAYS_FINISH_ACTIVITIES, 0) != 0;
        final boolean lenientBackgroundCheck =
                Settings.Global.getInt(resolver, LENIENT_BACKGROUND_CHECK, 0) != 0;
        final boolean forceRtl = Settings.Global.getInt(resolver, DEVELOPMENT_FORCE_RTL, 0) != 0;
        final boolean forceResizable = Settings.Global.getInt(
                resolver, DEVELOPMENT_FORCE_RESIZABLE_ACTIVITIES, 0) != 0;
        final boolean supportsLeanbackOnly =
                mContext.getPackageManager().hasSystemFeature(FEATURE_LEANBACK_ONLY);

        // Transfer any global setting for forcing RTL layout, into a System Property
        SystemProperties.set(DEVELOPMENT_FORCE_RTL, forceRtl ? "1":"0");

        final Configuration configuration = new Configuration();
        Settings.System.getConfiguration(resolver, configuration);
        if (forceRtl) {
            // This will take care of setting the correct layout direction flags
            configuration.setLayoutDirection(configuration.locale);
        }

        synchronized (this) {
            mDebugApp = mOrigDebugApp = debugApp;
            mWaitForDebugger = mOrigWaitForDebugger = waitForDebugger;
            mAlwaysFinishActivities = alwaysFinishActivities;
            mLenientBackgroundCheck = lenientBackgroundCheck;
            mSupportsLeanbackOnly = supportsLeanbackOnly;
            mForceResizableActivities = forceResizable;
            mWindowManager.setForceResizableTasks(mForceResizableActivities);
            if (supportsMultiWindow || forceResizable) {
                mSupportsMultiWindow = true;
                mSupportsFreeformWindowManagement = freeformWindowManagement || forceResizable;
                mSupportsPictureInPicture = supportsPictureInPicture || forceResizable;
            } else {
                mSupportsMultiWindow = false;
                mSupportsFreeformWindowManagement = false;
                mSupportsPictureInPicture = false;
            }
            // This happens before any activities are started, so we can
            // change mConfiguration in-place.
            updateConfigurationLocked(configuration, null, true);
            if (DEBUG_CONFIGURATION) Slog.v(TAG_CONFIGURATION,
                    "Initial config: " + mConfiguration);

            // Load resources only after the current configuration has been set.
            final Resources res = mContext.getResources();
            mHasRecents = res.getBoolean(com.android.internal.R.bool.config_hasRecents);
            mThumbnailWidth = res.getDimensionPixelSize(
                    com.android.internal.R.dimen.thumbnail_width);
            mThumbnailHeight = res.getDimensionPixelSize(
                    com.android.internal.R.dimen.thumbnail_height);
            mDefaultPinnedStackBounds = Rect.unflattenFromString(res.getString(
                    com.android.internal.R.string.config_defaultPictureInPictureBounds));
            mAppErrors.loadAppsNotReportingCrashesFromConfigLocked(res.getString(
                    com.android.internal.R.string.config_appsNotReportingCrashes));
            if ((mConfiguration.uiMode & UI_MODE_TYPE_TELEVISION) == UI_MODE_TYPE_TELEVISION) {
                mFullscreenThumbnailScale = (float) res
                    .getInteger(com.android.internal.R.integer.thumbnail_width_tv) /
                    (float) mConfiguration.screenWidthDp;
            } else {
                mFullscreenThumbnailScale = res.getFraction(
                    com.android.internal.R.fraction.thumbnail_fullscreen_scale, 1, 1);
            }
        }
    }

    public boolean testIsSystemReady() {
        // no need to synchronize(this) just to read & return the value
        return mSystemReady;
    }

    public void systemReady(final Runnable goingCallback) {
        synchronized(this) {
            if (mSystemReady) {
                // If we're done calling all the receivers, run the next "boot phase" passed in
                // by the SystemServer
                if (goingCallback != null) {
                    goingCallback.run();
                }
                return;
            }

            mLocalDeviceIdleController
                    = LocalServices.getService(DeviceIdleController.LocalService.class);

            // Make sure we have the current profile info, since it is needed for security checks.
            mUserController.onSystemReady();
            mRecentTasks.onSystemReadyLocked();
            mAppOpsService.systemReady();
            mSystemReady = true;
        }

        ArrayList<ProcessRecord> procsToKill = null;
        synchronized(mPidsSelfLocked) {
            for (int i=mPidsSelfLocked.size()-1; i>=0; i--) {
                ProcessRecord proc = mPidsSelfLocked.valueAt(i);
                if (!isAllowedWhileBooting(proc.info)){
                    if (procsToKill == null) {
                        procsToKill = new ArrayList<ProcessRecord>();
                    }
                    procsToKill.add(proc);
                }
            }
        }

        synchronized(this) {
            if (procsToKill != null) {
                for (int i=procsToKill.size()-1; i>=0; i--) {
                    ProcessRecord proc = procsToKill.get(i);
                    Slog.i(TAG, "Removing system update proc: " + proc);
                    removeProcessLocked(proc, true, false, "system update done");
                }
            }

            // Now that we have cleaned up any update processes, we
            // are ready to start launching real processes and know that
            // we won't trample on them any more.
            mProcessesReady = true;
        }

        Slog.i(TAG, "System now ready");
        EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_AMS_READY,
            SystemClock.uptimeMillis());

        synchronized(this) {
            // Make sure we have no pre-ready processes sitting around.

            if (mFactoryTest == FactoryTest.FACTORY_TEST_LOW_LEVEL) {
                ResolveInfo ri = mContext.getPackageManager()
                        .resolveActivity(new Intent(Intent.ACTION_FACTORY_TEST),
                                STOCK_PM_FLAGS);
                CharSequence errorMsg = null;
                if (ri != null) {
                    ActivityInfo ai = ri.activityInfo;
                    ApplicationInfo app = ai.applicationInfo;
                    if ((app.flags&ApplicationInfo.FLAG_SYSTEM) != 0) {
                        mTopAction = Intent.ACTION_FACTORY_TEST;
                        mTopData = null;
                        mTopComponent = new ComponentName(app.packageName,
                                ai.name);
                    } else {
                        errorMsg = mContext.getResources().getText(
                                com.android.internal.R.string.factorytest_not_system);
                    }
                } else {
                    errorMsg = mContext.getResources().getText(
                            com.android.internal.R.string.factorytest_no_action);
                }
                if (errorMsg != null) {
                    mTopAction = null;
                    mTopData = null;
                    mTopComponent = null;
                    Message msg = Message.obtain();
                    msg.what = SHOW_FACTORY_ERROR_UI_MSG;
                    msg.getData().putCharSequence("msg", errorMsg);
                    mUiHandler.sendMessage(msg);
                }
            }
        }

        retrieveSettings();
        final int currentUserId;
        synchronized (this) {
            currentUserId = mUserController.getCurrentUserIdLocked();
            readGrantedUriPermissionsLocked();
        }

        if (goingCallback != null) goingCallback.run();

        mBatteryStatsService.noteEvent(BatteryStats.HistoryItem.EVENT_USER_RUNNING_START,
                Integer.toString(currentUserId), currentUserId);
        mBatteryStatsService.noteEvent(BatteryStats.HistoryItem.EVENT_USER_FOREGROUND_START,
                Integer.toString(currentUserId), currentUserId);
        mSystemServiceManager.startUser(currentUserId);

        synchronized (this) {
            // Only start up encryption-aware persistent apps; once user is
            // unlocked we'll come back around and start unaware apps
            startPersistentApps(PackageManager.MATCH_DIRECT_BOOT_AWARE);

            // Start up initial activity.
            mBooting = true;
            // Enable home activity for system user, so that the system can always boot
            if (UserManager.isSplitSystemUser()) {
                ComponentName cName = new ComponentName(mContext, SystemUserHomeActivity.class);
                try {
                    AppGlobals.getPackageManager().setComponentEnabledSetting(cName,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0,
                            UserHandle.USER_SYSTEM);
                } catch (RemoteException e) {
                    throw e.rethrowAsRuntimeException();
                }
            }
            startHomeActivityLocked(currentUserId, "systemReady");

            try {
                if (AppGlobals.getPackageManager().hasSystemUidErrors()) {
                    Slog.e(TAG, "UIDs on the system are inconsistent, you need to wipe your"
                            + " data partition or your device will be unstable.");
                    mUiHandler.obtainMessage(SHOW_UID_ERROR_UI_MSG).sendToTarget();
                }
            } catch (RemoteException e) {
            }

            if (!Build.isBuildConsistent()) {
                Slog.e(TAG, "Build fingerprint is not consistent, warning user");
                mUiHandler.obtainMessage(SHOW_FINGERPRINT_ERROR_UI_MSG).sendToTarget();
            }

            long ident = Binder.clearCallingIdentity();
            try {
                Intent intent = new Intent(Intent.ACTION_USER_STARTED);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                        | Intent.FLAG_RECEIVER_FOREGROUND);
                intent.putExtra(Intent.EXTRA_USER_HANDLE, currentUserId);
                broadcastIntentLocked(null, null, intent,
                        null, null, 0, null, null, null, AppOpsManager.OP_NONE,
                        null, false, false, MY_PID, Process.SYSTEM_UID,
                        currentUserId);
                intent = new Intent(Intent.ACTION_USER_STARTING);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                intent.putExtra(Intent.EXTRA_USER_HANDLE, currentUserId);
                broadcastIntentLocked(null, null, intent,
                        null, new IIntentReceiver.Stub() {
                            @Override
                            public void performReceive(Intent intent, int resultCode, String data,
                                    Bundle extras, boolean ordered, boolean sticky, int sendingUser)
                                    throws RemoteException {
                            }
                        }, 0, null, null,
                        new String[] {INTERACT_ACROSS_USERS}, AppOpsManager.OP_NONE,
                        null, true, false, MY_PID, Process.SYSTEM_UID, UserHandle.USER_ALL);
            } catch (Throwable t) {
                Slog.wtf(TAG, "Failed sending first user broadcasts", t);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
            mStackSupervisor.resumeFocusedStackTopActivityLocked();
            mUserController.sendUserSwitchBroadcastsLocked(-1, currentUserId);
        }
    }

    void killAppAtUsersRequest(ProcessRecord app, Dialog fromDialog) {
        synchronized (this) {
            mAppErrors.killAppAtUserRequestLocked(app, fromDialog);
        }
    }

    void skipCurrentReceiverLocked(ProcessRecord app) {
        for (BroadcastQueue queue : mBroadcastQueues) {
            queue.skipCurrentReceiverLocked(app);
        }
    }

    /**
     * Used by {@link com.android.internal.os.RuntimeInit} to report when an application crashes.
     * The application process will exit immediately after this call returns.
     * @param app object of the crashing app, null for the system server
     * @param crashInfo describing the exception
     */
    public void handleApplicationCrash(IBinder app, ApplicationErrorReport.CrashInfo crashInfo) {
        ProcessRecord r = findAppProcess(app, "Crash");
        final String processName = app == null ? "system_server"
                : (r == null ? "unknown" : r.processName);

        handleApplicationCrashInner("crash", r, processName, crashInfo);
    }

    /* Native crash reporting uses this inner version because it needs to be somewhat
     * decoupled from the AM-managed cleanup lifecycle
     */
    void handleApplicationCrashInner(String eventType, ProcessRecord r, String processName,
            ApplicationErrorReport.CrashInfo crashInfo) {
        EventLog.writeEvent(EventLogTags.AM_CRASH, Binder.getCallingPid(),
                UserHandle.getUserId(Binder.getCallingUid()), processName,
                r == null ? -1 : r.info.flags,
                crashInfo.exceptionClassName,
                crashInfo.exceptionMessage,
                crashInfo.throwFileName,
                crashInfo.throwLineNumber);

        addErrorToDropBox(eventType, r, processName, null, null, null, null, null, crashInfo);

        mAppErrors.crashApplication(r, crashInfo);
    }

    public void handleApplicationStrictModeViolation(
            IBinder app,
            int violationMask,
            StrictMode.ViolationInfo info) {
        ProcessRecord r = findAppProcess(app, "StrictMode");
        if (r == null) {
            return;
        }

        if ((violationMask & StrictMode.PENALTY_DROPBOX) != 0) {
            Integer stackFingerprint = info.hashCode();
            boolean logIt = true;
            synchronized (mAlreadyLoggedViolatedStacks) {
                if (mAlreadyLoggedViolatedStacks.contains(stackFingerprint)) {
                    logIt = false;
                    // TODO: sub-sample into EventLog for these, with
                    // the info.durationMillis?  Then we'd get
                    // the relative pain numbers, without logging all
                    // the stack traces repeatedly.  We'd want to do
                    // likewise in the client code, which also does
                    // dup suppression, before the Binder call.
                } else {
                    if (mAlreadyLoggedViolatedStacks.size() >= MAX_DUP_SUPPRESSED_STACKS) {
                        mAlreadyLoggedViolatedStacks.clear();
                    }
                    mAlreadyLoggedViolatedStacks.add(stackFingerprint);
                }
            }
            if (logIt) {
                logStrictModeViolationToDropBox(r, info);
            }
        }

        if ((violationMask & StrictMode.PENALTY_DIALOG) != 0) {
            AppErrorResult result = new AppErrorResult();
            synchronized (this) {
                final long origId = Binder.clearCallingIdentity();

                Message msg = Message.obtain();
                msg.what = SHOW_STRICT_MODE_VIOLATION_UI_MSG;
                HashMap<String, Object> data = new HashMap<String, Object>();
                data.put("result", result);
                data.put("app", r);
                data.put("violationMask", violationMask);
                data.put("info", info);
                msg.obj = data;
                mUiHandler.sendMessage(msg);

                Binder.restoreCallingIdentity(origId);
            }
            int res = result.get();
            Slog.w(TAG, "handleApplicationStrictModeViolation; res=" + res);
        }
    }

    // Depending on the policy in effect, there could be a bunch of
    // these in quick succession so we try to batch these together to
    // minimize disk writes, number of dropbox entries, and maximize
    // compression, by having more fewer, larger records.
    private void logStrictModeViolationToDropBox(
            ProcessRecord process,
            StrictMode.ViolationInfo info) {
        if (info == null) {
            return;
        }
        final boolean isSystemApp = process == null ||
                (process.info.flags & (ApplicationInfo.FLAG_SYSTEM |
                                       ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        final String processName = process == null ? "unknown" : process.processName;
        final String dropboxTag = isSystemApp ? "system_app_strictmode" : "data_app_strictmode";
        final DropBoxManager dbox = (DropBoxManager)
                mContext.getSystemService(Context.DROPBOX_SERVICE);

        // Exit early if the dropbox isn't configured to accept this report type.
        if (dbox == null || !dbox.isTagEnabled(dropboxTag)) return;

        boolean bufferWasEmpty;
        boolean needsFlush;
        final StringBuilder sb = isSystemApp ? mStrictModeBuffer : new StringBuilder(1024);
        synchronized (sb) {
            bufferWasEmpty = sb.length() == 0;
            appendDropBoxProcessHeaders(process, processName, sb);
            sb.append("Build: ").append(Build.FINGERPRINT).append("\n");
            sb.append("System-App: ").append(isSystemApp).append("\n");
            sb.append("Uptime-Millis: ").append(info.violationUptimeMillis).append("\n");
            if (info.violationNumThisLoop != 0) {
                sb.append("Loop-Violation-Number: ").append(info.violationNumThisLoop).append("\n");
            }
            if (info.numAnimationsRunning != 0) {
                sb.append("Animations-Running: ").append(info.numAnimationsRunning).append("\n");
            }
            if (info.broadcastIntentAction != null) {
                sb.append("Broadcast-Intent-Action: ").append(info.broadcastIntentAction).append("\n");
            }
            if (info.durationMillis != -1) {
                sb.append("Duration-Millis: ").append(info.durationMillis).append("\n");
            }
            if (info.numInstances != -1) {
                sb.append("Instance-Count: ").append(info.numInstances).append("\n");
            }
            if (info.tags != null) {
                for (String tag : info.tags) {
                    sb.append("Span-Tag: ").append(tag).append("\n");
                }
            }
            sb.append("\n");
            if (info.crashInfo != null && info.crashInfo.stackTrace != null) {
                sb.append(info.crashInfo.stackTrace);
                sb.append("\n");
            }
            if (info.message != null) {
                sb.append(info.message);
                sb.append("\n");
            }

            // Only buffer up to ~64k.  Various logging bits truncate
            // things at 128k.
            needsFlush = (sb.length() > 64 * 1024);
        }

        // Flush immediately if the buffer's grown too large, or this
        // is a non-system app.  Non-system apps are isolated with a
        // different tag & policy and not batched.
        //
        // Batching is useful during internal testing with
        // StrictMode settings turned up high.  Without batching,
        // thousands of separate files could be created on boot.
        if (!isSystemApp || needsFlush) {
            new Thread("Error dump: " + dropboxTag) {
                @Override
                public void run() {
                    String report;
                    synchronized (sb) {
                        report = sb.toString();
                        sb.delete(0, sb.length());
                        sb.trimToSize();
                    }
                    if (report.length() != 0) {
                        dbox.addText(dropboxTag, report);
                    }
                }
            }.start();
            return;
        }

        // System app batching:
        if (!bufferWasEmpty) {
            // An existing dropbox-writing thread is outstanding, so
            // we don't need to start it up.  The existing thread will
            // catch the buffer appends we just did.
            return;
        }

        // Worker thread to both batch writes and to avoid blocking the caller on I/O.
        // (After this point, we shouldn't access AMS internal data structures.)
        new Thread("Error dump: " + dropboxTag) {
            @Override
            public void run() {
                // 5 second sleep to let stacks arrive and be batched together
                try {
                    Thread.sleep(5000);  // 5 seconds
                } catch (InterruptedException e) {}

                String errorReport;
                synchronized (mStrictModeBuffer) {
                    errorReport = mStrictModeBuffer.toString();
                    if (errorReport.length() == 0) {
                        return;
                    }
                    mStrictModeBuffer.delete(0, mStrictModeBuffer.length());
                    mStrictModeBuffer.trimToSize();
                }
                dbox.addText(dropboxTag, errorReport);
            }
        }.start();
    }

    /**
     * Used by {@link Log} via {@link com.android.internal.os.RuntimeInit} to report serious errors.
     * @param app object of the crashing app, null for the system server
     * @param tag reported by the caller
     * @param system whether this wtf is coming from the system
     * @param crashInfo describing the context of the error
     * @return true if the process should exit immediately (WTF is fatal)
     */
    public boolean handleApplicationWtf(final IBinder app, final String tag, boolean system,
            final ApplicationErrorReport.CrashInfo crashInfo) {
        final int callingUid = Binder.getCallingUid();
        final int callingPid = Binder.getCallingPid();

        if (system) {
            // If this is coming from the system, we could very well have low-level
            // system locks held, so we want to do this all asynchronously.  And we
            // never want this to become fatal, so there is that too.
            mHandler.post(new Runnable() {
                @Override public void run() {
                    handleApplicationWtfInner(callingUid, callingPid, app, tag, crashInfo);
                }
            });
            return false;
        }

        final ProcessRecord r = handleApplicationWtfInner(callingUid, callingPid, app, tag,
                crashInfo);

        if (r != null && r.pid != Process.myPid() &&
                Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.WTF_IS_FATAL, 0) != 0) {
            mAppErrors.crashApplication(r, crashInfo);
            return true;
        } else {
            return false;
        }
    }

    ProcessRecord handleApplicationWtfInner(int callingUid, int callingPid, IBinder app, String tag,
            final ApplicationErrorReport.CrashInfo crashInfo) {
        final ProcessRecord r = findAppProcess(app, "WTF");
        final String processName = app == null ? "system_server"
                : (r == null ? "unknown" : r.processName);

        EventLog.writeEvent(EventLogTags.AM_WTF, UserHandle.getUserId(callingUid), callingPid,
                processName, r == null ? -1 : r.info.flags, tag, crashInfo.exceptionMessage);

        addErrorToDropBox("wtf", r, processName, null, null, tag, null, null, crashInfo);

        return r;
    }

    /**
     * @param app object of some object (as stored in {@link com.android.internal.os.RuntimeInit})
     * @return the corresponding {@link ProcessRecord} object, or null if none could be found
     */
    private ProcessRecord findAppProcess(IBinder app, String reason) {
        if (app == null) {
            return null;
        }

        synchronized (this) {
            final int NP = mProcessNames.getMap().size();
            for (int ip=0; ip<NP; ip++) {
                SparseArray<ProcessRecord> apps = mProcessNames.getMap().valueAt(ip);
                final int NA = apps.size();
                for (int ia=0; ia<NA; ia++) {
                    ProcessRecord p = apps.valueAt(ia);
                    if (p.thread != null && p.thread.asBinder() == app) {
                        return p;
                    }
                }
            }

            Slog.w(TAG, "Can't find mystery application for " + reason
                    + " from pid=" + Binder.getCallingPid()
                    + " uid=" + Binder.getCallingUid() + ": " + app);
            return null;
        }
    }

    /**
     * Utility function for addErrorToDropBox and handleStrictModeViolation's logging
     * to append various headers to the dropbox log text.
     */
    private void appendDropBoxProcessHeaders(ProcessRecord process, String processName,
            StringBuilder sb) {
        // Watchdog thread ends up invoking this function (with
        // a null ProcessRecord) to add the stack file to dropbox.
        // Do not acquire a lock on this (am) in such cases, as it
        // could cause a potential deadlock, if and when watchdog
        // is invoked due to unavailability of lock on am and it
        // would prevent watchdog from killing system_server.
        if (process == null) {
            sb.append("Process: ").append(processName).append("\n");
            return;
        }
        // Note: ProcessRecord 'process' is guarded by the service
        // instance.  (notably process.pkgList, which could otherwise change
        // concurrently during execution of this method)
        synchronized (this) {
            sb.append("Process: ").append(processName).append("\n");
            int flags = process.info.flags;
            IPackageManager pm = AppGlobals.getPackageManager();
            sb.append("Flags: 0x").append(Integer.toHexString(flags)).append("\n");
            for (int ip=0; ip<process.pkgList.size(); ip++) {
                String pkg = process.pkgList.keyAt(ip);
                sb.append("Package: ").append(pkg);
                try {
                    PackageInfo pi = pm.getPackageInfo(pkg, 0, UserHandle.getCallingUserId());
                    if (pi != null) {
                        sb.append(" v").append(pi.versionCode);
                        if (pi.versionName != null) {
                            sb.append(" (").append(pi.versionName).append(")");
                        }
                    }
                } catch (RemoteException e) {
                    Slog.e(TAG, "Error getting package info: " + pkg, e);
                }
                sb.append("\n");
            }
        }
    }

    private static String processClass(ProcessRecord process) {
        if (process == null || process.pid == MY_PID) {
            return "system_server";
        } else if ((process.info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return "system_app";
        } else {
            return "data_app";
        }
    }

    private volatile long mWtfClusterStart;
    private volatile int mWtfClusterCount;

    /**
     * Write a description of an error (crash, WTF, ANR) to the drop box.
     * @param eventType to include in the drop box tag ("crash", "wtf", etc.)
     * @param process which caused the error, null means the system server
     * @param activity which triggered the error, null if unknown
     * @param parent activity related to the error, null if unknown
     * @param subject line related to the error, null if absent
     * @param report in long form describing the error, null if absent
     * @param dataFile text file to include in the report, null if none
     * @param crashInfo giving an application stack trace, null if absent
     */
    public void addErrorToDropBox(String eventType,
            ProcessRecord process, String processName, ActivityRecord activity,
            ActivityRecord parent, String subject,
            final String report, final File dataFile,
            final ApplicationErrorReport.CrashInfo crashInfo) {
        // NOTE -- this must never acquire the ActivityManagerService lock,
        // otherwise the watchdog may be prevented from resetting the system.

        final String dropboxTag = processClass(process) + "_" + eventType;
        final DropBoxManager dbox = (DropBoxManager)
                mContext.getSystemService(Context.DROPBOX_SERVICE);

        // Exit early if the dropbox isn't configured to accept this report type.
        if (dbox == null || !dbox.isTagEnabled(dropboxTag)) return;

        // Rate-limit how often we're willing to do the heavy lifting below to
        // collect and record logs; currently 5 logs per 10 second period.
        final long now = SystemClock.elapsedRealtime();
        if (now - mWtfClusterStart > 10 * DateUtils.SECOND_IN_MILLIS) {
            mWtfClusterStart = now;
            mWtfClusterCount = 1;
        } else {
            if (mWtfClusterCount++ >= 5) return;
        }

        final StringBuilder sb = new StringBuilder(1024);
        appendDropBoxProcessHeaders(process, processName, sb);
        if (process != null) {
            sb.append("Foreground: ")
                    .append(process.isInterestingToUserLocked() ? "Yes" : "No")
                    .append("\n");
        }
        if (activity != null) {
            sb.append("Activity: ").append(activity.shortComponentName).append("\n");
        }
        if (parent != null && parent.app != null && parent.app.pid != process.pid) {
            sb.append("Parent-Process: ").append(parent.app.processName).append("\n");
        }
        if (parent != null && parent != activity) {
            sb.append("Parent-Activity: ").append(parent.shortComponentName).append("\n");
        }
        if (subject != null) {
            sb.append("Subject: ").append(subject).append("\n");
        }
        sb.append("Build: ").append(Build.FINGERPRINT).append("\n");
        if (Debug.isDebuggerConnected()) {
            sb.append("Debugger: Connected\n");
        }
        sb.append("\n");

        // Do the rest in a worker thread to avoid blocking the caller on I/O
        // (After this point, we shouldn't access AMS internal data structures.)
        Thread worker = new Thread("Error dump: " + dropboxTag) {
            @Override
            public void run() {
                if (report != null) {
                    sb.append(report);
                }

                String setting = Settings.Global.ERROR_LOGCAT_PREFIX + dropboxTag;
                int lines = Settings.Global.getInt(mContext.getContentResolver(), setting, 0);
                int maxDataFileSize = DROPBOX_MAX_SIZE - sb.length()
                        - lines * RESERVED_BYTES_PER_LOGCAT_LINE;

                if (dataFile != null && maxDataFileSize > 0) {
                    try {
                        sb.append(FileUtils.readTextFile(dataFile, maxDataFileSize,
                                    "\n\n[[TRUNCATED]]"));
                    } catch (IOException e) {
                        Slog.e(TAG, "Error reading " + dataFile, e);
                    }
                }
                if (crashInfo != null && crashInfo.stackTrace != null) {
                    sb.append(crashInfo.stackTrace);
                }

                if (lines > 0) {
                    sb.append("\n");

                    // Merge several logcat streams, and take the last N lines
                    InputStreamReader input = null;
                    try {
                        java.lang.Process logcat = new ProcessBuilder(
                                "/system/bin/timeout", "-k", "15s", "10s",
                                "/system/bin/logcat", "-v", "threadtime", "-b", "events", "-b", "system",
                                "-b", "main", "-b", "crash", "-t", String.valueOf(lines))
                                        .redirectErrorStream(true).start();

                        try { logcat.getOutputStream().close(); } catch (IOException e) {}
                        try { logcat.getErrorStream().close(); } catch (IOException e) {}
                        input = new InputStreamReader(logcat.getInputStream());

                        int num;
                        char[] buf = new char[8192];
                        while ((num = input.read(buf)) > 0) sb.append(buf, 0, num);
                    } catch (IOException e) {
                        Slog.e(TAG, "Error running logcat", e);
                    } finally {
                        if (input != null) try { input.close(); } catch (IOException e) {}
                    }
                }

                dbox.addText(dropboxTag, sb.toString());
            }
        };

        if (process == null) {
            // If process is null, we are being called from some internal code
            // and may be about to die -- run this synchronously.
            worker.run();
        } else {
            worker.start();
        }
    }

    @Override
    public List<ActivityManager.ProcessErrorStateInfo> getProcessesInErrorState() {
        enforceNotIsolatedCaller("getProcessesInErrorState");
        // assume our apps are happy - lazy create the list
        List<ActivityManager.ProcessErrorStateInfo> errList = null;

        final boolean allUsers = ActivityManager.checkUidPermission(INTERACT_ACROSS_USERS_FULL,
                Binder.getCallingUid()) == PackageManager.PERMISSION_GRANTED;
        int userId = UserHandle.getUserId(Binder.getCallingUid());

        synchronized (this) {

            // iterate across all processes
            for (int i=mLruProcesses.size()-1; i>=0; i--) {
                ProcessRecord app = mLruProcesses.get(i);
                if (!allUsers && app.userId != userId) {
                    continue;
                }
                if ((app.thread != null) && (app.crashing || app.notResponding)) {
                    // This one's in trouble, so we'll generate a report for it
                    // crashes are higher priority (in case there's a crash *and* an anr)
                    ActivityManager.ProcessErrorStateInfo report = null;
                    if (app.crashing) {
                        report = app.crashingReport;
                    } else if (app.notResponding) {
                        report = app.notRespondingReport;
                    }

                    if (report != null) {
                        if (errList == null) {
                            errList = new ArrayList<ActivityManager.ProcessErrorStateInfo>(1);
                        }
                        errList.add(report);
                    } else {
                        Slog.w(TAG, "Missing app error report, app = " + app.processName +
                                " crashing = " + app.crashing +
                                " notResponding = " + app.notResponding);
                    }
                }
            }
        }

        return errList;
    }

    static int procStateToImportance(int procState, int memAdj,
            ActivityManager.RunningAppProcessInfo currApp) {
        int imp = ActivityManager.RunningAppProcessInfo.procStateToImportance(procState);
        if (imp == ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
            currApp.lru = memAdj;
        } else {
            currApp.lru = 0;
        }
        return imp;
    }

    private void fillInProcMemInfo(ProcessRecord app,
            ActivityManager.RunningAppProcessInfo outInfo) {
        outInfo.pid = app.pid;
        outInfo.uid = app.info.uid;
        if (mHeavyWeightProcess == app) {
            outInfo.flags |= ActivityManager.RunningAppProcessInfo.FLAG_CANT_SAVE_STATE;
        }
        if (app.persistent) {
            outInfo.flags |= ActivityManager.RunningAppProcessInfo.FLAG_PERSISTENT;
        }
        if (app.activities.size() > 0) {
            outInfo.flags |= ActivityManager.RunningAppProcessInfo.FLAG_HAS_ACTIVITIES;
        }
        outInfo.lastTrimLevel = app.trimMemoryLevel;
        int adj = app.curAdj;
        int procState = app.curProcState;
        outInfo.importance = procStateToImportance(procState, adj, outInfo);
        outInfo.importanceReasonCode = app.adjTypeCode;
        outInfo.processState = app.curProcState;
    }

    @Override
    public List<ActivityManager.RunningAppProcessInfo> getRunningAppProcesses() {
        enforceNotIsolatedCaller("getRunningAppProcesses");

        final int callingUid = Binder.getCallingUid();

        // Lazy instantiation of list
        List<ActivityManager.RunningAppProcessInfo> runList = null;
        final boolean allUsers = ActivityManager.checkUidPermission(INTERACT_ACROSS_USERS_FULL,
                callingUid) == PackageManager.PERMISSION_GRANTED;
        final int userId = UserHandle.getUserId(callingUid);
        final boolean allUids = isGetTasksAllowed(
                "getRunningAppProcesses", Binder.getCallingPid(), callingUid);

        synchronized (this) {
            // Iterate across all processes
            for (int i = mLruProcesses.size() - 1; i >= 0; i--) {
                ProcessRecord app = mLruProcesses.get(i);
                if ((!allUsers && app.userId != userId)
                        || (!allUids && app.uid != callingUid)) {
                    continue;
                }
                if ((app.thread != null) && (!app.crashing && !app.notResponding)) {
                    // Generate process state info for running application
                    ActivityManager.RunningAppProcessInfo currApp =
                        new ActivityManager.RunningAppProcessInfo(app.processName,
                                app.pid, app.getPackageList());
                    fillInProcMemInfo(app, currApp);
                    if (app.adjSource instanceof ProcessRecord) {
                        currApp.importanceReasonPid = ((ProcessRecord)app.adjSource).pid;
                        currApp.importanceReasonImportance =
                                ActivityManager.RunningAppProcessInfo.procStateToImportance(
                                        app.adjSourceProcState);
                    } else if (app.adjSource instanceof ActivityRecord) {
                        ActivityRecord r = (ActivityRecord)app.adjSource;
                        if (r.app != null) currApp.importanceReasonPid = r.app.pid;
                    }
                    if (app.adjTarget instanceof ComponentName) {
                        currApp.importanceReasonComponent = (ComponentName)app.adjTarget;
                    }
                    //Slog.v(TAG, "Proc " + app.processName + ": imp=" + currApp.importance
                    //        + " lru=" + currApp.lru);
                    if (runList == null) {
                        runList = new ArrayList<>();
                    }
                    runList.add(currApp);
                }
            }
        }
        return runList;
    }

    @Override
    public List<ApplicationInfo> getRunningExternalApplications() {
        enforceNotIsolatedCaller("getRunningExternalApplications");
        List<ActivityManager.RunningAppProcessInfo> runningApps = getRunningAppProcesses();
        List<ApplicationInfo> retList = new ArrayList<ApplicationInfo>();
        if (runningApps != null && runningApps.size() > 0) {
            Set<String> extList = new HashSet<String>();
            for (ActivityManager.RunningAppProcessInfo app : runningApps) {
                if (app.pkgList != null) {
                    for (String pkg : app.pkgList) {
                        extList.add(pkg);
                    }
                }
            }
            IPackageManager pm = AppGlobals.getPackageManager();
            for (String pkg : extList) {
                try {
                    ApplicationInfo info = pm.getApplicationInfo(pkg, 0, UserHandle.getCallingUserId());
                    if ((info.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0) {
                        retList.add(info);
                    }
                } catch (RemoteException e) {
                }
            }
        }
        return retList;
    }

    @Override
    public void getMyMemoryState(ActivityManager.RunningAppProcessInfo outInfo) {
        enforceNotIsolatedCaller("getMyMemoryState");
        synchronized (this) {
            ProcessRecord proc;
            synchronized (mPidsSelfLocked) {
                proc = mPidsSelfLocked.get(Binder.getCallingPid());
            }
            fillInProcMemInfo(proc, outInfo);
        }
    }

    @Override
    public int getMemoryTrimLevel() {
        enforceNotIsolatedCaller("getMyMemoryState");
        synchronized (this) {
            return mLastMemoryLevel;
        }
    }

    @Override
    public void onShellCommand(FileDescriptor in, FileDescriptor out,
            FileDescriptor err, String[] args, ResultReceiver resultReceiver) {
        (new ActivityManagerShellCommand(this, false)).exec(
                this, in, out, err, args, resultReceiver);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (checkCallingPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump ActivityManager from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " without permission "
                    + android.Manifest.permission.DUMP);
            return;
        }

        boolean dumpAll = false;
        boolean dumpClient = false;
        boolean dumpCheckin = false;
        boolean dumpCheckinFormat = false;
        String dumpPackage = null;

        int opti = 0;
        while (opti < args.length) {
            String opt = args[opti];
            if (opt == null || opt.length() <= 0 || opt.charAt(0) != '-') {
                break;
            }
            opti++;
            if ("-a".equals(opt)) {
                dumpAll = true;
            } else if ("-c".equals(opt)) {
                dumpClient = true;
            } else if ("-p".equals(opt)) {
                if (opti < args.length) {
                    dumpPackage = args[opti];
                    opti++;
                } else {
                    pw.println("Error: -p option requires package argument");
                    return;
                }
                dumpClient = true;
            } else if ("--checkin".equals(opt)) {
                dumpCheckin = dumpCheckinFormat = true;
            } else if ("-C".equals(opt)) {
                dumpCheckinFormat = true;
            } else if ("-h".equals(opt)) {
                ActivityManagerShellCommand.dumpHelp(pw, true);
                return;
            } else {
                pw.println("Unknown argument: " + opt + "; use -h for help");
            }
        }

        long origId = Binder.clearCallingIdentity();
        boolean more = false;
        // Is the caller requesting to dump a particular piece of data?
        if (opti < args.length) {
            String cmd = args[opti];
            opti++;
            if ("activities".equals(cmd) || "a".equals(cmd)) {
                synchronized (this) {
                    dumpActivitiesLocked(fd, pw, args, opti, true, dumpClient, dumpPackage);
                }
            } else if ("recents".equals(cmd) || "r".equals(cmd)) {
                synchronized (this) {
                    dumpRecentsLocked(fd, pw, args, opti, true, dumpPackage);
                }
            } else if ("broadcasts".equals(cmd) || "b".equals(cmd)) {
                String[] newArgs;
                String name;
                if (opti >= args.length) {
                    name = null;
                    newArgs = EMPTY_STRING_ARRAY;
                } else {
                    dumpPackage = args[opti];
                    opti++;
                    newArgs = new String[args.length - opti];
                    if (args.length > 2) System.arraycopy(args, opti, newArgs, 0,
                            args.length - opti);
                }
                synchronized (this) {
                    dumpBroadcastsLocked(fd, pw, args, opti, true, dumpPackage);
                }
            } else if ("broadcast-stats".equals(cmd)) {
                String[] newArgs;
                String name;
                if (opti >= args.length) {
                    name = null;
                    newArgs = EMPTY_STRING_ARRAY;
                } else {
                    dumpPackage = args[opti];
                    opti++;
                    newArgs = new String[args.length - opti];
                    if (args.length > 2) System.arraycopy(args, opti, newArgs, 0,
                            args.length - opti);
                }
                synchronized (this) {
                    if (dumpCheckinFormat) {
                        dumpBroadcastStatsCheckinLocked(fd, pw, args, opti, dumpCheckin,
                                dumpPackage);
                    } else {
                        dumpBroadcastStatsLocked(fd, pw, args, opti, true, dumpPackage);
                    }
                }
            } else if ("intents".equals(cmd) || "i".equals(cmd)) {
                String[] newArgs;
                String name;
                if (opti >= args.length) {
                    name = null;
                    newArgs = EMPTY_STRING_ARRAY;
                } else {
                    dumpPackage = args[opti];
                    opti++;
                    newArgs = new String[args.length - opti];
                    if (args.length > 2) System.arraycopy(args, opti, newArgs, 0,
                            args.length - opti);
                }
                synchronized (this) {
                    dumpPendingIntentsLocked(fd, pw, args, opti, true, dumpPackage);
                }
            } else if ("processes".equals(cmd) || "p".equals(cmd)) {
                String[] newArgs;
                String name;
                if (opti >= args.length) {
                    name = null;
                    newArgs = EMPTY_STRING_ARRAY;
                } else {
                    dumpPackage = args[opti];
                    opti++;
                    newArgs = new String[args.length - opti];
                    if (args.length > 2) System.arraycopy(args, opti, newArgs, 0,
                            args.length - opti);
                }
                synchronized (this) {
                    dumpProcessesLocked(fd, pw, args, opti, true, dumpPackage);
                }
            } else if ("oom".equals(cmd) || "o".equals(cmd)) {
                synchronized (this) {
                    dumpOomLocked(fd, pw, args, opti, true);
                }
            } else if ("permissions".equals(cmd) || "perm".equals(cmd)) {
                synchronized (this) {
                    dumpPermissionsLocked(fd, pw, args, opti, true, null);
                }
            } else if ("provider".equals(cmd)) {
                String[] newArgs;
                String name;
                if (opti >= args.length) {
                    name = null;
                    newArgs = EMPTY_STRING_ARRAY;
                } else {
                    name = args[opti];
                    opti++;
                    newArgs = new String[args.length - opti];
                    if (args.length > 2) System.arraycopy(args, opti, newArgs, 0, args.length - opti);
                }
                if (!dumpProvider(fd, pw, name, newArgs, 0, dumpAll)) {
                    pw.println("No providers match: " + name);
                    pw.println("Use -h for help.");
                }
            } else if ("providers".equals(cmd) || "prov".equals(cmd)) {
                synchronized (this) {
                    dumpProvidersLocked(fd, pw, args, opti, true, null);
                }
            } else if ("service".equals(cmd)) {
                String[] newArgs;
                String name;
                if (opti >= args.length) {
                    name = null;
                    newArgs = EMPTY_STRING_ARRAY;
                } else {
                    name = args[opti];
                    opti++;
                    newArgs = new String[args.length - opti];
                    if (args.length > 2) System.arraycopy(args, opti, newArgs, 0,
                            args.length - opti);
                }
                if (!mServices.dumpService(fd, pw, name, newArgs, 0, dumpAll)) {
                    pw.println("No services match: " + name);
                    pw.println("Use -h for help.");
                }
            } else if ("package".equals(cmd)) {
                String[] newArgs;
                if (opti >= args.length) {
                    pw.println("package: no package name specified");
                    pw.println("Use -h for help.");
                } else {
                    dumpPackage = args[opti];
                    opti++;
                    newArgs = new String[args.length - opti];
                    if (args.length > 2) System.arraycopy(args, opti, newArgs, 0,
                            args.length - opti);
                    args = newArgs;
                    opti = 0;
                    more = true;
                }
            } else if ("associations".equals(cmd) || "as".equals(cmd)) {
                synchronized (this) {
                    dumpAssociationsLocked(fd, pw, args, opti, true, dumpClient, dumpPackage);
                }
            } else if ("services".equals(cmd) || "s".equals(cmd)) {
                if (dumpClient) {
                    ActiveServices.ServiceDumper dumper;
                    synchronized (this) {
                        dumper = mServices.newServiceDumperLocked(fd, pw, args, opti, true,
                                dumpPackage);
                    }
                    dumper.dumpWithClient();
                } else {
                    synchronized (this) {
                        mServices.newServiceDumperLocked(fd, pw, args, opti, true,
                                dumpPackage).dumpLocked();
                    }
                }
            } else if ("locks".equals(cmd)) {
                LockGuard.dump(fd, pw, args);
            } else {
                // Dumping a single activity?
                if (!dumpActivity(fd, pw, cmd, args, opti, dumpAll)) {
                    ActivityManagerShellCommand shell = new ActivityManagerShellCommand(this, true);
                    int res = shell.exec(this, null, fd, null, args, new ResultReceiver(null));
                    if (res < 0) {
                        pw.println("Bad activity command, or no activities match: " + cmd);
                        pw.println("Use -h for help.");
                    }
                }
            }
            if (!more) {
                Binder.restoreCallingIdentity(origId);
                return;
            }
        }

        // No piece of data specified, dump everything.
        if (dumpCheckinFormat) {
            dumpBroadcastStatsCheckinLocked(fd, pw, args, opti, dumpCheckin, dumpPackage);
        } else if (dumpClient) {
            ActiveServices.ServiceDumper sdumper;
            synchronized (this) {
                dumpPendingIntentsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpBroadcastsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                if (dumpAll || dumpPackage != null) {
                    dumpBroadcastStatsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                    pw.println();
                    if (dumpAll) {
                        pw.println("-------------------------------------------------------------------------------");
                    }
                }
                dumpProvidersLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpPermissionsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                sdumper = mServices.newServiceDumperLocked(fd, pw, args, opti, dumpAll,
                        dumpPackage);
            }
            sdumper.dumpWithClient();
            pw.println();
            synchronized (this) {
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpRecentsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpActivitiesLocked(fd, pw, args, opti, dumpAll, dumpClient, dumpPackage);
                if (mAssociations.size() > 0) {
                    pw.println();
                    if (dumpAll) {
                        pw.println("-------------------------------------------------------------------------------");
                    }
                    dumpAssociationsLocked(fd, pw, args, opti, dumpAll, dumpClient, dumpPackage);
                }
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpProcessesLocked(fd, pw, args, opti, dumpAll, dumpPackage);
            }

        } else {
            synchronized (this) {
                dumpPendingIntentsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpBroadcastsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                if (dumpAll || dumpPackage != null) {
                    dumpBroadcastStatsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                    pw.println();
                    if (dumpAll) {
                        pw.println("-------------------------------------------------------------------------------");
                    }
                }
                dumpProvidersLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpPermissionsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                mServices.newServiceDumperLocked(fd, pw, args, opti, dumpAll, dumpPackage)
                        .dumpLocked();
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpRecentsLocked(fd, pw, args, opti, dumpAll, dumpPackage);
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpActivitiesLocked(fd, pw, args, opti, dumpAll, dumpClient, dumpPackage);
                if (mAssociations.size() > 0) {
                    pw.println();
                    if (dumpAll) {
                        pw.println("-------------------------------------------------------------------------------");
                    }
                    dumpAssociationsLocked(fd, pw, args, opti, dumpAll, dumpClient, dumpPackage);
                }
                pw.println();
                if (dumpAll) {
                    pw.println("-------------------------------------------------------------------------------");
                }
                dumpProcessesLocked(fd, pw, args, opti, dumpAll, dumpPackage);
            }
        }
        Binder.restoreCallingIdentity(origId);
    }

    void dumpActivitiesLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, boolean dumpClient, String dumpPackage) {
        pw.println("ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)");

        boolean printedAnything = mStackSupervisor.dumpActivitiesLocked(fd, pw, dumpAll, dumpClient,
                dumpPackage);
        boolean needSep = printedAnything;

        boolean printed = ActivityStackSupervisor.printThisActivity(pw, mFocusedActivity,
                dumpPackage, needSep, "  mFocusedActivity: ");
        if (printed) {
            printedAnything = true;
            needSep = false;
        }

        if (dumpPackage == null) {
            if (needSep) {
                pw.println();
            }
            needSep = true;
            printedAnything = true;
            mStackSupervisor.dump(pw, "  ");
        }

        if (!printedAnything) {
            pw.println("  (nothing)");
        }
    }

    void dumpRecentsLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, String dumpPackage) {
        pw.println("ACTIVITY MANAGER RECENT TASKS (dumpsys activity recents)");

        boolean printedAnything = false;

        if (mRecentTasks != null && mRecentTasks.size() > 0) {
            boolean printedHeader = false;

            final int N = mRecentTasks.size();
            for (int i=0; i<N; i++) {
                TaskRecord tr = mRecentTasks.get(i);
                if (dumpPackage != null) {
                    if (tr.realActivity == null ||
                            !dumpPackage.equals(tr.realActivity)) {
                        continue;
                    }
                }
                if (!printedHeader) {
                    pw.println("  Recent tasks:");
                    printedHeader = true;
                    printedAnything = true;
                }
                pw.print("  * Recent #"); pw.print(i); pw.print(": ");
                        pw.println(tr);
                if (dumpAll) {
                    mRecentTasks.get(i).dump(pw, "    ");
                }
            }
        }

        if (!printedAnything) {
            pw.println("  (nothing)");
        }
    }

    void dumpAssociationsLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, boolean dumpClient, String dumpPackage) {
        pw.println("ACTIVITY MANAGER ASSOCIATIONS (dumpsys activity associations)");

        int dumpUid = 0;
        if (dumpPackage != null) {
            IPackageManager pm = AppGlobals.getPackageManager();
            try {
                dumpUid = pm.getPackageUid(dumpPackage, MATCH_UNINSTALLED_PACKAGES, 0);
            } catch (RemoteException e) {
            }
        }

        boolean printedAnything = false;

        final long now = SystemClock.uptimeMillis();

        for (int i1=0, N1=mAssociations.size(); i1<N1; i1++) {
            ArrayMap<ComponentName, SparseArray<ArrayMap<String, Association>>> targetComponents
                    = mAssociations.valueAt(i1);
            for (int i2=0, N2=targetComponents.size(); i2<N2; i2++) {
                SparseArray<ArrayMap<String, Association>> sourceUids
                        = targetComponents.valueAt(i2);
                for (int i3=0, N3=sourceUids.size(); i3<N3; i3++) {
                    ArrayMap<String, Association> sourceProcesses = sourceUids.valueAt(i3);
                    for (int i4=0, N4=sourceProcesses.size(); i4<N4; i4++) {
                        Association ass = sourceProcesses.valueAt(i4);
                        if (dumpPackage != null) {
                            if (!ass.mTargetComponent.getPackageName().equals(dumpPackage)
                                    && UserHandle.getAppId(ass.mSourceUid) != dumpUid) {
                                continue;
                            }
                        }
                        printedAnything = true;
                        pw.print("  ");
                        pw.print(ass.mTargetProcess);
                        pw.print("/");
                        UserHandle.formatUid(pw, ass.mTargetUid);
                        pw.print(" <- ");
                        pw.print(ass.mSourceProcess);
                        pw.print("/");
                        UserHandle.formatUid(pw, ass.mSourceUid);
                        pw.println();
                        pw.print("    via ");
                        pw.print(ass.mTargetComponent.flattenToShortString());
                        pw.println();
                        pw.print("    ");
                        long dur = ass.mTime;
                        if (ass.mNesting > 0) {
                            dur += now - ass.mStartTime;
                        }
                        TimeUtils.formatDuration(dur, pw);
                        pw.print(" (");
                        pw.print(ass.mCount);
                        pw.print(" times)");
                        pw.print("  ");
                        for (int i=0; i<ass.mStateTimes.length; i++) {
                            long amt = ass.mStateTimes[i];
                            if (ass.mLastState-ActivityManager.MIN_PROCESS_STATE == i) {
                                amt += now - ass.mLastStateUptime;
                            }
                            if (amt != 0) {
                                pw.print(" ");
                                pw.print(ProcessList.makeProcStateString(
                                            i + ActivityManager.MIN_PROCESS_STATE));
                                pw.print("=");
                                TimeUtils.formatDuration(amt, pw);
                                if (ass.mLastState-ActivityManager.MIN_PROCESS_STATE == i) {
                                    pw.print("*");
                                }
                            }
                        }
                        pw.println();
                        if (ass.mNesting > 0) {
                            pw.print("    Currently active: ");
                            TimeUtils.formatDuration(now - ass.mStartTime, pw);
                            pw.println();
                        }
                    }
                }
            }

        }

        if (!printedAnything) {
            pw.println("  (nothing)");
        }
    }

    boolean dumpUids(PrintWriter pw, String dumpPackage, SparseArray<UidRecord> uids,
            String header, boolean needSep) {
        boolean printed = false;
        int whichAppId = -1;
        if (dumpPackage != null) {
            try {
                ApplicationInfo info = mContext.getPackageManager().getApplicationInfo(
                        dumpPackage, 0);
                whichAppId = UserHandle.getAppId(info.uid);
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            }
        }
        for (int i=0; i<uids.size(); i++) {
            UidRecord uidRec = uids.valueAt(i);
            if (dumpPackage != null && UserHandle.getAppId(uidRec.uid) != whichAppId) {
                continue;
            }
            if (!printed) {
                printed = true;
                if (needSep) {
                    pw.println();
                }
                pw.print("  ");
                pw.println(header);
                needSep = true;
            }
            pw.print("    UID "); UserHandle.formatUid(pw, uidRec.uid);
            pw.print(": "); pw.println(uidRec);
        }
        return printed;
    }

    void dumpProcessesLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, String dumpPackage) {
        boolean needSep = false;
        boolean printedAnything = false;
        int numPers = 0;

        pw.println("ACTIVITY MANAGER RUNNING PROCESSES (dumpsys activity processes)");

        if (dumpAll) {
            final int NP = mProcessNames.getMap().size();
            for (int ip=0; ip<NP; ip++) {
                SparseArray<ProcessRecord> procs = mProcessNames.getMap().valueAt(ip);
                final int NA = procs.size();
                for (int ia=0; ia<NA; ia++) {
                    ProcessRecord r = procs.valueAt(ia);
                    if (dumpPackage != null && !r.pkgList.containsKey(dumpPackage)) {
                        continue;
                    }
                    if (!needSep) {
                        pw.println("  All known processes:");
                        needSep = true;
                        printedAnything = true;
                    }
                    pw.print(r.persistent ? "  *PERS*" : "  *APP*");
                        pw.print(" UID "); pw.print(procs.keyAt(ia));
                        pw.print(" "); pw.println(r);
                    r.dump(pw, "    ");
                    if (r.persistent) {
                        numPers++;
                    }
                }
            }
        }

        if (mIsolatedProcesses.size() > 0) {
            boolean printed = false;
            for (int i=0; i<mIsolatedProcesses.size(); i++) {
                ProcessRecord r = mIsolatedProcesses.valueAt(i);
                if (dumpPackage != null && !r.pkgList.containsKey(dumpPackage)) {
                    continue;
                }
                if (!printed) {
                    if (needSep) {
                        pw.println();
                    }
                    pw.println("  Isolated process list (sorted by uid):");
                    printedAnything = true;
                    printed = true;
                    needSep = true;
                }
                pw.println(String.format("%sIsolated #%2d: %s",
                        "    ", i, r.toString()));
            }
        }

        if (mActiveUids.size() > 0) {
            if (dumpUids(pw, dumpPackage, mActiveUids, "UID states:", needSep)) {
                printedAnything = needSep = true;
            }
        }
        if (mValidateUids.size() > 0) {
            if (dumpUids(pw, dumpPackage, mValidateUids, "UID validation:", needSep)) {
                printedAnything = needSep = true;
            }
        }

        if (mLruProcesses.size() > 0) {
            if (needSep) {
                pw.println();
            }
            pw.print("  Process LRU list (sorted by oom_adj, "); pw.print(mLruProcesses.size());
                    pw.print(" total, non-act at ");
                    pw.print(mLruProcesses.size()-mLruProcessActivityStart);
                    pw.print(", non-svc at ");
                    pw.print(mLruProcesses.size()-mLruProcessServiceStart);
                    pw.println("):");
            dumpProcessOomList(pw, this, mLruProcesses, "    ", "Proc", "PERS", false, dumpPackage);
            needSep = true;
            printedAnything = true;
        }

        if (dumpAll || dumpPackage != null) {
            synchronized (mPidsSelfLocked) {
                boolean printed = false;
                for (int i=0; i<mPidsSelfLocked.size(); i++) {
                    ProcessRecord r = mPidsSelfLocked.valueAt(i);
                    if (dumpPackage != null && !r.pkgList.containsKey(dumpPackage)) {
                        continue;
                    }
                    if (!printed) {
                        if (needSep) pw.println();
                        needSep = true;
                        pw.println("  PID mappings:");
                        printed = true;
                        printedAnything = true;
                    }
                    pw.print("    PID #"); pw.print(mPidsSelfLocked.keyAt(i));
                        pw.print(": "); pw.println(mPidsSelfLocked.valueAt(i));
                }
            }
        }

        if (mForegroundProcesses.size() > 0) {
            synchronized (mPidsSelfLocked) {
                boolean printed = false;
                for (int i=0; i<mForegroundProcesses.size(); i++) {
                    ProcessRecord r = mPidsSelfLocked.get(
                            mForegroundProcesses.valueAt(i).pid);
                    if (dumpPackage != null && (r == null
                            || !r.pkgList.containsKey(dumpPackage))) {
                        continue;
                    }
                    if (!printed) {
                        if (needSep) pw.println();
                        needSep = true;
                        pw.println("  Foreground Processes:");
                        printed = true;
                        printedAnything = true;
                    }
                    pw.print("    PID #"); pw.print(mForegroundProcesses.keyAt(i));
                            pw.print(": "); pw.println(mForegroundProcesses.valueAt(i));
                }
            }
        }

        if (mPersistentStartingProcesses.size() > 0) {
            if (needSep) pw.println();
            needSep = true;
            printedAnything = true;
            pw.println("  Persisent processes that are starting:");
            dumpProcessList(pw, this, mPersistentStartingProcesses, "    ",
                    "Starting Norm", "Restarting PERS", dumpPackage);
        }

        if (mRemovedProcesses.size() > 0) {
            if (needSep) pw.println();
            needSep = true;
            printedAnything = true;
            pw.println("  Processes that are being removed:");
            dumpProcessList(pw, this, mRemovedProcesses, "    ",
                    "Removed Norm", "Removed PERS", dumpPackage);
        }

        if (mProcessesOnHold.size() > 0) {
            if (needSep) pw.println();
            needSep = true;
            printedAnything = true;
            pw.println("  Processes that are on old until the system is ready:");
            dumpProcessList(pw, this, mProcessesOnHold, "    ",
                    "OnHold Norm", "OnHold PERS", dumpPackage);
        }

        needSep = dumpProcessesToGc(fd, pw, args, opti, needSep, dumpAll, dumpPackage);

        needSep = mAppErrors.dumpLocked(fd, pw, needSep, dumpPackage);
        if (needSep) {
            printedAnything = true;
        }

        if (dumpPackage == null) {
            pw.println();
            needSep = false;
            mUserController.dump(pw, dumpAll);
        }
        if (mHomeProcess != null && (dumpPackage == null
                || mHomeProcess.pkgList.containsKey(dumpPackage))) {
            if (needSep) {
                pw.println();
                needSep = false;
            }
            pw.println("  mHomeProcess: " + mHomeProcess);
        }
        if (mPreviousProcess != null && (dumpPackage == null
                || mPreviousProcess.pkgList.containsKey(dumpPackage))) {
            if (needSep) {
                pw.println();
                needSep = false;
            }
            pw.println("  mPreviousProcess: " + mPreviousProcess);
        }
        if (dumpAll) {
            StringBuilder sb = new StringBuilder(128);
            sb.append("  mPreviousProcessVisibleTime: ");
            TimeUtils.formatDuration(mPreviousProcessVisibleTime, sb);
            pw.println(sb);
        }
        if (mHeavyWeightProcess != null && (dumpPackage == null
                || mHeavyWeightProcess.pkgList.containsKey(dumpPackage))) {
            if (needSep) {
                pw.println();
                needSep = false;
            }
            pw.println("  mHeavyWeightProcess: " + mHeavyWeightProcess);
        }
        if (dumpPackage == null) {
            pw.println("  mConfiguration: " + mConfiguration);
        }
        if (dumpAll) {
            pw.println("  mConfigWillChange: " + getFocusedStack().mConfigWillChange);
            if (mCompatModePackages.getPackages().size() > 0) {
                boolean printed = false;
                for (Map.Entry<String, Integer> entry
                        : mCompatModePackages.getPackages().entrySet()) {
                    String pkg = entry.getKey();
                    int mode = entry.getValue();
                    if (dumpPackage != null && !dumpPackage.equals(pkg)) {
                        continue;
                    }
                    if (!printed) {
                        pw.println("  mScreenCompatPackages:");
                        printed = true;
                    }
                    pw.print("    "); pw.print(pkg); pw.print(": ");
                            pw.print(mode); pw.println();
                }
            }
        }
        if (dumpPackage == null) {
            pw.println("  mWakefulness="
                    + PowerManagerInternal.wakefulnessToString(mWakefulness));
            pw.println("  mSleepTokens=" + mSleepTokens);
            pw.println("  mSleeping=" + mSleeping + " mLockScreenShown="
                    + lockScreenShownToString());
            pw.println("  mShuttingDown=" + mShuttingDown + " mTestPssMode=" + mTestPssMode);
            if (mRunningVoice != null) {
                pw.println("  mRunningVoice=" + mRunningVoice);
                pw.println("  mVoiceWakeLock" + mVoiceWakeLock);
            }
        }
        if (mDebugApp != null || mOrigDebugApp != null || mDebugTransient
                || mOrigWaitForDebugger) {
            if (dumpPackage == null || dumpPackage.equals(mDebugApp)
                    || dumpPackage.equals(mOrigDebugApp)) {
                if (needSep) {
                    pw.println();
                    needSep = false;
                }
                pw.println("  mDebugApp=" + mDebugApp + "/orig=" + mOrigDebugApp
                        + " mDebugTransient=" + mDebugTransient
                        + " mOrigWaitForDebugger=" + mOrigWaitForDebugger);
            }
        }
        if (mCurAppTimeTracker != null) {
            mCurAppTimeTracker.dumpWithHeader(pw, "  ", true);
        }
        if (mMemWatchProcesses.getMap().size() > 0) {
            pw.println("  Mem watch processes:");
            final ArrayMap<String, SparseArray<Pair<Long, String>>> procs
                    = mMemWatchProcesses.getMap();
            for (int i=0; i<procs.size(); i++) {
                final String proc = procs.keyAt(i);
                final SparseArray<Pair<Long, String>> uids = procs.valueAt(i);
                for (int j=0; j<uids.size(); j++) {
                    if (needSep) {
                        pw.println();
                        needSep = false;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("    ").append(proc).append('/');
                    UserHandle.formatUid(sb, uids.keyAt(j));
                    Pair<Long, String> val = uids.valueAt(j);
                    sb.append(": "); DebugUtils.sizeValueToString(val.first, sb);
                    if (val.second != null) {
                        sb.append(", report to ").append(val.second);
                    }
                    pw.println(sb.toString());
                }
            }
            pw.print("  mMemWatchDumpProcName="); pw.println(mMemWatchDumpProcName);
            pw.print("  mMemWatchDumpFile="); pw.println(mMemWatchDumpFile);
            pw.print("  mMemWatchDumpPid="); pw.print(mMemWatchDumpPid);
                    pw.print(" mMemWatchDumpUid="); pw.println(mMemWatchDumpUid);
        }
        if (mTrackAllocationApp != null) {
            if (dumpPackage == null || dumpPackage.equals(mTrackAllocationApp)) {
                if (needSep) {
                    pw.println();
                    needSep = false;
                }
                pw.println("  mTrackAllocationApp=" + mTrackAllocationApp);
            }
        }
        if (mProfileApp != null || mProfileProc != null || mProfileFile != null
                || mProfileFd != null) {
            if (dumpPackage == null || dumpPackage.equals(mProfileApp)) {
                if (needSep) {
                    pw.println();
                    needSep = false;
                }
                pw.println("  mProfileApp=" + mProfileApp + " mProfileProc=" + mProfileProc);
                pw.println("  mProfileFile=" + mProfileFile + " mProfileFd=" + mProfileFd);
                pw.println("  mSamplingInterval=" + mSamplingInterval + " mAutoStopProfiler="
                        + mAutoStopProfiler);
                pw.println("  mProfileType=" + mProfileType);
            }
        }
        if (mNativeDebuggingApp != null) {
            if (dumpPackage == null || dumpPackage.equals(mNativeDebuggingApp)) {
                if (needSep) {
                    pw.println();
                    needSep = false;
                }
                pw.println("  mNativeDebuggingApp=" + mNativeDebuggingApp);
            }
        }
        if (dumpPackage == null) {
            if (mAlwaysFinishActivities || mLenientBackgroundCheck) {
                pw.println("  mAlwaysFinishActivities=" + mAlwaysFinishActivities
                        + " mLenientBackgroundCheck=" + mLenientBackgroundCheck);
            }
            if (mController != null) {
                pw.println("  mController=" + mController
                        + " mControllerIsAMonkey=" + mControllerIsAMonkey);
            }
            if (dumpAll) {
                pw.println("  Total persistent processes: " + numPers);
                pw.println("  mProcessesReady=" + mProcessesReady
                        + " mSystemReady=" + mSystemReady
                        + " mBooted=" + mBooted
                        + " mFactoryTest=" + mFactoryTest);
                pw.println("  mBooting=" + mBooting
                        + " mCallFinishBooting=" + mCallFinishBooting
                        + " mBootAnimationComplete=" + mBootAnimationComplete);
                pw.print("  mLastPowerCheckRealtime=");
                        TimeUtils.formatDuration(mLastPowerCheckRealtime, pw);
                        pw.println("");
                pw.print("  mLastPowerCheckUptime=");
                        TimeUtils.formatDuration(mLastPowerCheckUptime, pw);
                        pw.println("");
                pw.println("  mGoingToSleep=" + mStackSupervisor.mGoingToSleep);
                pw.println("  mLaunchingActivity=" + mStackSupervisor.mLaunchingActivity);
                pw.println("  mAdjSeq=" + mAdjSeq + " mLruSeq=" + mLruSeq);
                pw.println("  mNumNonCachedProcs=" + mNumNonCachedProcs
                        + " (" + mLruProcesses.size() + " total)"
                        + " mNumCachedHiddenProcs=" + mNumCachedHiddenProcs
                        + " mNumServiceProcs=" + mNumServiceProcs
                        + " mNewNumServiceProcs=" + mNewNumServiceProcs);
                pw.println("  mAllowLowerMemLevel=" + mAllowLowerMemLevel
                        + " mLastMemoryLevel=" + mLastMemoryLevel
                        + " mLastNumProcesses=" + mLastNumProcesses);
                long now = SystemClock.uptimeMillis();
                pw.print("  mLastIdleTime=");
                        TimeUtils.formatDuration(now, mLastIdleTime, pw);
                        pw.print(" mLowRamSinceLastIdle=");
                        TimeUtils.formatDuration(getLowRamTimeSinceIdle(now), pw);
                        pw.println();
            }
        }

        if (!printedAnything) {
            pw.println("  (nothing)");
        }
    }

    boolean dumpProcessesToGc(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean needSep, boolean dumpAll, String dumpPackage) {
        if (mProcessesToGc.size() > 0) {
            boolean printed = false;
            long now = SystemClock.uptimeMillis();
            for (int i=0; i<mProcessesToGc.size(); i++) {
                ProcessRecord proc = mProcessesToGc.get(i);
                if (dumpPackage != null && !dumpPackage.equals(proc.info.packageName)) {
                    continue;
                }
                if (!printed) {
                    if (needSep) pw.println();
                    needSep = true;
                    pw.println("  Processes that are waiting to GC:");
                    printed = true;
                }
                pw.print("    Process "); pw.println(proc);
                pw.print("      lowMem="); pw.print(proc.reportLowMemory);
                        pw.print(", last gced=");
                        pw.print(now-proc.lastRequestedGc);
                        pw.print(" ms ago, last lowMem=");
                        pw.print(now-proc.lastLowMemory);
                        pw.println(" ms ago");

            }
        }
        return needSep;
    }

    void printOomLevel(PrintWriter pw, String name, int adj) {
        pw.print("    ");
        if (adj >= 0) {
            pw.print(' ');
            if (adj < 10) pw.print(' ');
        } else {
            if (adj > -10) pw.print(' ');
        }
        pw.print(adj);
        pw.print(": ");
        pw.print(name);
        pw.print(" (");
        pw.print(stringifySize(mProcessList.getMemLevel(adj), 1024));
        pw.println(")");
    }

    boolean dumpOomLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll) {
        boolean needSep = false;

        if (mLruProcesses.size() > 0) {
            if (needSep) pw.println();
            needSep = true;
            pw.println("  OOM levels:");
            printOomLevel(pw, "SYSTEM_ADJ", ProcessList.SYSTEM_ADJ);
            printOomLevel(pw, "PERSISTENT_PROC_ADJ", ProcessList.PERSISTENT_PROC_ADJ);
            printOomLevel(pw, "PERSISTENT_SERVICE_ADJ", ProcessList.PERSISTENT_SERVICE_ADJ);
            printOomLevel(pw, "FOREGROUND_APP_ADJ", ProcessList.FOREGROUND_APP_ADJ);
            printOomLevel(pw, "VISIBLE_APP_ADJ", ProcessList.VISIBLE_APP_ADJ);
            printOomLevel(pw, "PERCEPTIBLE_APP_ADJ", ProcessList.PERCEPTIBLE_APP_ADJ);
            printOomLevel(pw, "BACKUP_APP_ADJ", ProcessList.BACKUP_APP_ADJ);
            printOomLevel(pw, "HEAVY_WEIGHT_APP_ADJ", ProcessList.HEAVY_WEIGHT_APP_ADJ);
            printOomLevel(pw, "SERVICE_ADJ", ProcessList.SERVICE_ADJ);
            printOomLevel(pw, "HOME_APP_ADJ", ProcessList.HOME_APP_ADJ);
            printOomLevel(pw, "PREVIOUS_APP_ADJ", ProcessList.PREVIOUS_APP_ADJ);
            printOomLevel(pw, "SERVICE_B_ADJ", ProcessList.SERVICE_B_ADJ);
            printOomLevel(pw, "CACHED_APP_MIN_ADJ", ProcessList.CACHED_APP_MIN_ADJ);
            printOomLevel(pw, "CACHED_APP_MAX_ADJ", ProcessList.CACHED_APP_MAX_ADJ);

            if (needSep) pw.println();
            pw.print("  Process OOM control ("); pw.print(mLruProcesses.size());
                    pw.print(" total, non-act at ");
                    pw.print(mLruProcesses.size()-mLruProcessActivityStart);
                    pw.print(", non-svc at ");
                    pw.print(mLruProcesses.size()-mLruProcessServiceStart);
                    pw.println("):");
            dumpProcessOomList(pw, this, mLruProcesses, "    ", "Proc", "PERS", true, null);
            needSep = true;
        }

        dumpProcessesToGc(fd, pw, args, opti, needSep, dumpAll, null);

        pw.println();
        pw.println("  mHomeProcess: " + mHomeProcess);
        pw.println("  mPreviousProcess: " + mPreviousProcess);
        if (mHeavyWeightProcess != null) {
            pw.println("  mHeavyWeightProcess: " + mHeavyWeightProcess);
        }

        return true;
    }

    /**
     * There are three ways to call this:
     *  - no provider specified: dump all the providers
     *  - a flattened component name that matched an existing provider was specified as the
     *    first arg: dump that one provider
     *  - the first arg isn't the flattened component name of an existing provider:
     *    dump all providers whose component contains the first arg as a substring
     */
    protected boolean dumpProvider(FileDescriptor fd, PrintWriter pw, String name, String[] args,
            int opti, boolean dumpAll) {
        return mProviderMap.dumpProvider(fd, pw, name, args, opti, dumpAll);
    }

    static class ItemMatcher {
        ArrayList<ComponentName> components;
        ArrayList<String> strings;
        ArrayList<Integer> objects;
        boolean all;

        ItemMatcher() {
            all = true;
        }

        void build(String name) {
            ComponentName componentName = ComponentName.unflattenFromString(name);
            if (componentName != null) {
                if (components == null) {
                    components = new ArrayList<ComponentName>();
                }
                components.add(componentName);
                all = false;
            } else {
                int objectId = 0;
                // Not a '/' separated full component name; maybe an object ID?
                try {
                    objectId = Integer.parseInt(name, 16);
                    if (objects == null) {
                        objects = new ArrayList<Integer>();
                    }
                    objects.add(objectId);
                    all = false;
                } catch (RuntimeException e) {
                    // Not an integer; just do string match.
                    if (strings == null) {
                        strings = new ArrayList<String>();
                    }
                    strings.add(name);
                    all = false;
                }
            }
        }

        int build(String[] args, int opti) {
            for (; opti<args.length; opti++) {
                String name = args[opti];
                if ("--".equals(name)) {
                    return opti+1;
                }
                build(name);
            }
            return opti;
        }

        boolean match(Object object, ComponentName comp) {
            if (all) {
                return true;
            }
            if (components != null) {
                for (int i=0; i<components.size(); i++) {
                    if (components.get(i).equals(comp)) {
                        return true;
                    }
                }
            }
            if (objects != null) {
                for (int i=0; i<objects.size(); i++) {
                    if (System.identityHashCode(object) == objects.get(i)) {
                        return true;
                    }
                }
            }
            if (strings != null) {
                String flat = comp.flattenToString();
                for (int i=0; i<strings.size(); i++) {
                    if (flat.contains(strings.get(i))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * There are three things that cmd can be:
     *  - a flattened component name that matches an existing activity
     *  - the cmd arg isn't the flattened component name of an existing activity:
     *    dump all activity whose component contains the cmd as a substring
     *  - A hex number of the ActivityRecord object instance.
     */
    protected boolean dumpActivity(FileDescriptor fd, PrintWriter pw, String name, String[] args,
            int opti, boolean dumpAll) {
        ArrayList<ActivityRecord> activities;

        synchronized (this) {
            activities = mStackSupervisor.getDumpActivitiesLocked(name);
        }

        if (activities.size() <= 0) {
            return false;
        }

        String[] newArgs = new String[args.length - opti];
        System.arraycopy(args, opti, newArgs, 0, args.length - opti);

        TaskRecord lastTask = null;
        boolean needSep = false;
        for (int i=activities.size()-1; i>=0; i--) {
            ActivityRecord r = activities.get(i);
            if (needSep) {
                pw.println();
            }
            needSep = true;
            synchronized (this) {
                if (lastTask != r.task) {
                    lastTask = r.task;
                    pw.print("TASK "); pw.print(lastTask.affinity);
                            pw.print(" id="); pw.println(lastTask.taskId);
                    if (dumpAll) {
                        lastTask.dump(pw, "  ");
                    }
                }
            }
            dumpActivity("  ", fd, pw, activities.get(i), newArgs, dumpAll);
        }
        return true;
    }

    /**
     * Invokes IApplicationThread.dumpActivity() on the thread of the specified activity if
     * there is a thread associated with the activity.
     */
    private void dumpActivity(String prefix, FileDescriptor fd, PrintWriter pw,
            final ActivityRecord r, String[] args, boolean dumpAll) {
        String innerPrefix = prefix + "  ";
        synchronized (this) {
            pw.print(prefix); pw.print("ACTIVITY "); pw.print(r.shortComponentName);
                    pw.print(" "); pw.print(Integer.toHexString(System.identityHashCode(r)));
                    pw.print(" pid=");
                    if (r.app != null) pw.println(r.app.pid);
                    else pw.println("(not running)");
            if (dumpAll) {
                r.dump(pw, innerPrefix);
            }
        }
        if (r.app != null && r.app.thread != null) {
            // flush anything that is already in the PrintWriter since the thread is going
            // to write to the file descriptor directly
            pw.flush();
            try {
                TransferPipe tp = new TransferPipe();
                try {
                    r.app.thread.dumpActivity(tp.getWriteFd().getFileDescriptor(),
                            r.appToken, innerPrefix, args);
                    tp.go(fd);
                } finally {
                    tp.kill();
                }
            } catch (IOException e) {
                pw.println(innerPrefix + "Failure while dumping the activity: " + e);
            } catch (RemoteException e) {
                pw.println(innerPrefix + "Got a RemoteException while dumping the activity");
            }
        }
    }

    void dumpBroadcastsLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, String dumpPackage) {
        boolean needSep = false;
        boolean onlyHistory = false;
        boolean printedAnything = false;

        if ("history".equals(dumpPackage)) {
            if (opti < args.length && "-s".equals(args[opti])) {
                dumpAll = false;
            }
            onlyHistory = true;
            dumpPackage = null;
        }

        pw.println("ACTIVITY MANAGER BROADCAST STATE (dumpsys activity broadcasts)");
        if (!onlyHistory && dumpAll) {
            if (mRegisteredReceivers.size() > 0) {
                boolean printed = false;
                Iterator it = mRegisteredReceivers.values().iterator();
                while (it.hasNext()) {
                    ReceiverList r = (ReceiverList)it.next();
                    if (dumpPackage != null && (r.app == null ||
                            !dumpPackage.equals(r.app.info.packageName))) {
                        continue;
                    }
                    if (!printed) {
                        pw.println("  Registered Receivers:");
                        needSep = true;
                        printed = true;
                        printedAnything = true;
                    }
                    pw.print("  * "); pw.println(r);
                    r.dump(pw, "    ");
                }
            }

            if (mReceiverResolver.dump(pw, needSep ?
                    "\n  Receiver Resolver Table:" : "  Receiver Resolver Table:",
                    "    ", dumpPackage, false, false)) {
                needSep = true;
                printedAnything = true;
            }
        }

        for (BroadcastQueue q : mBroadcastQueues) {
            needSep = q.dumpLocked(fd, pw, args, opti, dumpAll, dumpPackage, needSep);
            printedAnything |= needSep;
        }

        needSep = true;

        if (!onlyHistory && mStickyBroadcasts != null && dumpPackage == null) {
            for (int user=0; user<mStickyBroadcasts.size(); user++) {
                if (needSep) {
                    pw.println();
                }
                needSep = true;
                printedAnything = true;
                pw.print("  Sticky broadcasts for user ");
                        pw.print(mStickyBroadcasts.keyAt(user)); pw.println(":");
                StringBuilder sb = new StringBuilder(128);
                for (Map.Entry<String, ArrayList<Intent>> ent
                        : mStickyBroadcasts.valueAt(user).entrySet()) {
                    pw.print("  * Sticky action "); pw.print(ent.getKey());
                    if (dumpAll) {
                        pw.println(":");
                        ArrayList<Intent> intents = ent.getValue();
                        final int N = intents.size();
                        for (int i=0; i<N; i++) {
                            sb.setLength(0);
                            sb.append("    Intent: ");
                            intents.get(i).toShortString(sb, false, true, false, false);
                            pw.println(sb.toString());
                            Bundle bundle = intents.get(i).getExtras();
                            if (bundle != null) {
                                pw.print("      ");
                                pw.println(bundle.toString());
                            }
                        }
                    } else {
                        pw.println("");
                    }
                }
            }
        }

        if (!onlyHistory && dumpAll) {
            pw.println();
            for (BroadcastQueue queue : mBroadcastQueues) {
                pw.println("  mBroadcastsScheduled [" + queue.mQueueName + "]="
                        + queue.mBroadcastsScheduled);
            }
            pw.println("  mHandler:");
            mHandler.dump(new PrintWriterPrinter(pw), "    ");
            needSep = true;
            printedAnything = true;
        }

        if (!printedAnything) {
            pw.println("  (nothing)");
        }
    }

    void dumpBroadcastStatsLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, String dumpPackage) {
        if (mCurBroadcastStats == null) {
            return;
        }

        pw.println("ACTIVITY MANAGER BROADCAST STATS STATE (dumpsys activity broadcast-stats)");
        final long now = SystemClock.elapsedRealtime();
        if (mLastBroadcastStats != null) {
            pw.print("  Last stats (from ");
            TimeUtils.formatDuration(mLastBroadcastStats.mStartRealtime, now, pw);
            pw.print(" to ");
            TimeUtils.formatDuration(mLastBroadcastStats.mEndRealtime, now, pw);
            pw.print(", ");
            TimeUtils.formatDuration(mLastBroadcastStats.mEndUptime
                    - mLastBroadcastStats.mStartUptime, pw);
            pw.println(" uptime):");
            if (!mLastBroadcastStats.dumpStats(pw, "    ", dumpPackage)) {
                pw.println("    (nothing)");
            }
            pw.println();
        }
        pw.print("  Current stats (from ");
        TimeUtils.formatDuration(mCurBroadcastStats.mStartRealtime, now, pw);
        pw.print(" to now, ");
        TimeUtils.formatDuration(SystemClock.uptimeMillis()
                - mCurBroadcastStats.mStartUptime, pw);
        pw.println(" uptime):");
        if (!mCurBroadcastStats.dumpStats(pw, "    ", dumpPackage)) {
            pw.println("    (nothing)");
        }
    }

    void dumpBroadcastStatsCheckinLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean fullCheckin, String dumpPackage) {
        if (mCurBroadcastStats == null) {
            return;
        }

        if (mLastBroadcastStats != null) {
            mLastBroadcastStats.dumpCheckinStats(pw, dumpPackage);
            if (fullCheckin) {
                mLastBroadcastStats = null;
                return;
            }
        }
        mCurBroadcastStats.dumpCheckinStats(pw, dumpPackage);
        if (fullCheckin) {
            mCurBroadcastStats = null;
        }
    }

    void dumpProvidersLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, String dumpPackage) {
        boolean needSep;
        boolean printedAnything = false;

        ItemMatcher matcher = new ItemMatcher();
        matcher.build(args, opti);

        pw.println("ACTIVITY MANAGER CONTENT PROVIDERS (dumpsys activity providers)");

        needSep = mProviderMap.dumpProvidersLocked(pw, dumpAll, dumpPackage);
        printedAnything |= needSep;

        if (mLaunchingProviders.size() > 0) {
            boolean printed = false;
            for (int i=mLaunchingProviders.size()-1; i>=0; i--) {
                ContentProviderRecord r = mLaunchingProviders.get(i);
                if (dumpPackage != null && !dumpPackage.equals(r.name.getPackageName())) {
                    continue;
                }
                if (!printed) {
                    if (needSep) pw.println();
                    needSep = true;
                    pw.println("  Launching content providers:");
                    printed = true;
                    printedAnything = true;
                }
                pw.print("  Launching #"); pw.print(i); pw.print(": ");
                        pw.println(r);
            }
        }

        if (!printedAnything) {
            pw.println("  (nothing)");
        }
    }

    void dumpPermissionsLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, String dumpPackage) {
        boolean needSep = false;
        boolean printedAnything = false;

        pw.println("ACTIVITY MANAGER URI PERMISSIONS (dumpsys activity permissions)");

        if (mGrantedUriPermissions.size() > 0) {
            boolean printed = false;
            int dumpUid = -2;
            if (dumpPackage != null) {
                try {
                    dumpUid = mContext.getPackageManager().getPackageUidAsUser(dumpPackage,
                            MATCH_UNINSTALLED_PACKAGES, 0);
                } catch (NameNotFoundException e) {
                    dumpUid = -1;
                }
            }
            for (int i=0; i<mGrantedUriPermissions.size(); i++) {
                int uid = mGrantedUriPermissions.keyAt(i);
                if (dumpUid >= -1 && UserHandle.getAppId(uid) != dumpUid) {
                    continue;
                }
                final ArrayMap<GrantUri, UriPermission> perms = mGrantedUriPermissions.valueAt(i);
                if (!printed) {
                    if (needSep) pw.println();
                    needSep = true;
                    pw.println("  Granted Uri Permissions:");
                    printed = true;
                    printedAnything = true;
                }
                pw.print("  * UID "); pw.print(uid); pw.println(" holds:");
                for (UriPermission perm : perms.values()) {
                    pw.print("    "); pw.println(perm);
                    if (dumpAll) {
                        perm.dump(pw, "      ");
                    }
                }
            }
        }

        if (!printedAnything) {
            pw.println("  (nothing)");
        }
    }

    void dumpPendingIntentsLocked(FileDescriptor fd, PrintWriter pw, String[] args,
            int opti, boolean dumpAll, String dumpPackage) {
        boolean printed = false;

        pw.println("ACTIVITY MANAGER PENDING INTENTS (dumpsys activity intents)");

        if (mIntentSenderRecords.size() > 0) {
            Iterator<WeakReference<PendingIntentRecord>> it
                    = mIntentSenderRecords.values().iterator();
            while (it.hasNext()) {
                WeakReference<PendingIntentRecord> ref = it.next();
                PendingIntentRecord rec = ref != null ? ref.get(): null;
                if (dumpPackage != null && (rec == null
                        || !dumpPackage.equals(rec.key.packageName))) {
                    continue;
                }
                printed = true;
                if (rec != null) {
                    pw.print("  * "); pw.println(rec);
                    if (dumpAll) {
                        rec.dump(pw, "    ");
                    }
                } else {
                    pw.print("  * "); pw.println(ref);
                }
            }
        }

        if (!printed) {
            pw.println("  (nothing)");
        }
    }

    private static final int dumpProcessList(PrintWriter pw,
            ActivityManagerService service, List list,
            String prefix, String normalLabel, String persistentLabel,
            String dumpPackage) {
        int numPers = 0;
        final int N = list.size()-1;
        for (int i=N; i>=0; i--) {
            ProcessRecord r = (ProcessRecord)list.get(i);
            if (dumpPackage != null && !dumpPackage.equals(r.info.packageName)) {
                continue;
            }
            pw.println(String.format("%s%s #%2d: %s",
                    prefix, (r.persistent ? persistentLabel : normalLabel),
                    i, r.toString()));
            if (r.persistent) {
                numPers++;
            }
        }
        return numPers;
    }

    private static final boolean dumpProcessOomList(PrintWriter pw,
            ActivityManagerService service, List<ProcessRecord> origList,
            String prefix, String normalLabel, String persistentLabel,
            boolean inclDetails, String dumpPackage) {

        ArrayList<Pair<ProcessRecord, Integer>> list
                = new ArrayList<Pair<ProcessRecord, Integer>>(origList.size());
        for (int i=0; i<origList.size(); i++) {
            ProcessRecord r = origList.get(i);
            if (dumpPackage != null && !r.pkgList.containsKey(dumpPackage)) {
                continue;
            }
            list.add(new Pair<ProcessRecord, Integer>(origList.get(i), i));
        }

        if (list.size() <= 0) {
            return false;
        }

        Comparator<Pair<ProcessRecord, Integer>> comparator
                = new Comparator<Pair<ProcessRecord, Integer>>() {
            @Override
            public int compare(Pair<ProcessRecord, Integer> object1,
                    Pair<ProcessRecord, Integer> object2) {
                if (object1.first.setAdj != object2.first.setAdj) {
                    return object1.first.setAdj > object2.first.setAdj ? -1 : 1;
                }
                if (object1.first.setProcState != object2.first.setProcState) {
                    return object1.first.setProcState > object2.first.setProcState ? -1 : 1;
                }
                if (object1.second.intValue() != object2.second.intValue()) {
                    return object1.second.intValue() > object2.second.intValue() ? -1 : 1;
                }
                return 0;
            }
        };

        Collections.sort(list, comparator);

        final long curRealtime = SystemClock.elapsedRealtime();
        final long realtimeSince = curRealtime - service.mLastPowerCheckRealtime;
        final long curUptime = SystemClock.uptimeMillis();
        final long uptimeSince = curUptime - service.mLastPowerCheckUptime;

        for (int i=list.size()-1; i>=0; i--) {
            ProcessRecord r = list.get(i).first;
            String oomAdj = ProcessList.makeOomAdjString(r.setAdj);
            char schedGroup;
            switch (r.setSchedGroup) {
                case ProcessList.SCHED_GROUP_BACKGROUND:
                    schedGroup = 'B';
                    break;
                case ProcessList.SCHED_GROUP_DEFAULT:
                    schedGroup = 'F';
                    break;
                case ProcessList.SCHED_GROUP_TOP_APP:
                    schedGroup = 'T';
                    break;
                default:
                    schedGroup = '?';
                    break;
            }
            char foreground;
            if (r.foregroundActivities) {
                foreground = 'A';
            } else if (r.foregroundServices) {
                foreground = 'S';
            } else {
                foreground = ' ';
            }
            String procState = ProcessList.makeProcStateString(r.curProcState);
            pw.print(prefix);
            pw.print(r.persistent ? persistentLabel : normalLabel);
            pw.print(" #");
            int num = (origList.size()-1)-list.get(i).second;
            if (num < 10) pw.print(' ');
            pw.print(num);
            pw.print(": ");
            pw.print(oomAdj);
            pw.print(' ');
            pw.print(schedGroup);
            pw.print('/');
            pw.print(foreground);
            pw.print('/');
            pw.print(procState);
            pw.print(" trm:");
            if (r.trimMemoryLevel < 10) pw.print(' ');
            pw.print(r.trimMemoryLevel);
            pw.print(' ');
            pw.print(r.toShortString());
            pw.print(" (");
            pw.print(r.adjType);
            pw.println(')');
            if (r.adjSource != null || r.adjTarget != null) {
                pw.print(prefix);
                pw.print("    ");
                if (r.adjTarget instanceof ComponentName) {
                    pw.print(((ComponentName)r.adjTarget).flattenToShortString());
                } else if (r.adjTarget != null) {
                    pw.print(r.adjTarget.toString());
                } else {
                    pw.print("{null}");
                }
                pw.print("<=");
                if (r.adjSource instanceof ProcessRecord) {
                    pw.print("Proc{");
                    pw.print(((ProcessRecord)r.adjSource).toShortString());
                    pw.println("}");
                } else if (r.adjSource != null) {
                    pw.println(r.adjSource.toString());
                } else {
                    pw.println("{null}");
                }
            }
            if (inclDetails) {
                pw.print(prefix);
                pw.print("    ");
                pw.print("oom: max="); pw.print(r.maxAdj);
                pw.print(" curRaw="); pw.print(r.curRawAdj);
                pw.print(" setRaw="); pw.print(r.setRawAdj);
                pw.print(" cur="); pw.print(r.curAdj);
                pw.print(" set="); pw.println(r.setAdj);
                pw.print(prefix);
                pw.print("    ");
                pw.print("state: cur="); pw.print(ProcessList.makeProcStateString(r.curProcState));
                pw.print(" set="); pw.print(ProcessList.makeProcStateString(r.setProcState));
                pw.print(" lastPss="); DebugUtils.printSizeValue(pw, r.lastPss*1024);
                pw.print(" lastSwapPss="); DebugUtils.printSizeValue(pw, r.lastSwapPss*1024);
                pw.print(" lastCachedPss="); DebugUtils.printSizeValue(pw, r.lastCachedPss*1024);
                pw.println();
                pw.print(prefix);
                pw.print("    ");
                pw.print("cached="); pw.print(r.cached);
                pw.print(" empty="); pw.print(r.empty);
                pw.print(" hasAboveClient="); pw.println(r.hasAboveClient);

                if (r.setProcState >= ActivityManager.PROCESS_STATE_SERVICE) {
                    if (r.lastWakeTime != 0) {
                        long wtime;
                        BatteryStatsImpl stats = service.mBatteryStatsService.getActiveStatistics();
                        synchronized (stats) {
                            wtime = stats.getProcessWakeTime(r.info.uid,
                                    r.pid, curRealtime);
                        }
                        long timeUsed = wtime - r.lastWakeTime;
                        pw.print(prefix);
                        pw.print("    ");
                        pw.print("keep awake over ");
                        TimeUtils.formatDuration(realtimeSince, pw);
                        pw.print(" used ");
                        TimeUtils.formatDuration(timeUsed, pw);
                        pw.print(" (");
                        pw.print((timeUsed*100)/realtimeSince);
                        pw.println("%)");
                    }
                    if (r.lastCpuTime != 0) {
                        long timeUsed = r.curCpuTime - r.lastCpuTime;
                        pw.print(prefix);
                        pw.print("    ");
                        pw.print("run cpu over ");
                        TimeUtils.formatDuration(uptimeSince, pw);
                        pw.print(" used ");
                        TimeUtils.formatDuration(timeUsed, pw);
                        pw.print(" (");
                        pw.print((timeUsed*100)/uptimeSince);
                        pw.println("%)");
                    }
                }
            }
        }
        return true;
    }

    ArrayList<ProcessRecord> collectProcesses(PrintWriter pw, int start, boolean allPkgs,
            String[] args) {
        ArrayList<ProcessRecord> procs;
        synchronized (this) {
            if (args != null && args.length > start
                    && args[start].charAt(0) != '-') {
                procs = new ArrayList<ProcessRecord>();
                int pid = -1;
                try {
                    pid = Integer.parseInt(args[start]);
                } catch (NumberFormatException e) {
                }
                for (int i=mLruProcesses.size()-1; i>=0; i--) {
                    ProcessRecord proc = mLruProcesses.get(i);
                    if (proc.pid == pid) {
                        procs.add(proc);
                    } else if (allPkgs && proc.pkgList != null
                            && proc.pkgList.containsKey(args[start])) {
                        procs.add(proc);
                    } else if (proc.processName.equals(args[start])) {
                        procs.add(proc);
                    }
                }
                if (procs.size() <= 0) {
                    return null;
                }
            } else {
                procs = new ArrayList<ProcessRecord>(mLruProcesses);
            }
        }
        return procs;
    }

    final void dumpGraphicsHardwareUsage(FileDescriptor fd,
            PrintWriter pw, String[] args) {
        ArrayList<ProcessRecord> procs = collectProcesses(pw, 0, false, args);
        if (procs == null) {
            pw.println("No process found for: " + args[0]);
            return;
        }

        long uptime = SystemClock.uptimeMillis();
        long realtime = SystemClock.elapsedRealtime();
        pw.println("Applications Graphics Acceleration Info:");
        pw.println("Uptime: " + uptime + " Realtime: " + realtime);

        for (int i = procs.size() - 1 ; i >= 0 ; i--) {
            ProcessRecord r = procs.get(i);
            if (r.thread != null) {
                pw.println("\n** Graphics info for pid " + r.pid + " [" + r.processName + "] **");
                pw.flush();
                try {
                    TransferPipe tp = new TransferPipe();
                    try {
                        r.thread.dumpGfxInfo(tp.getWriteFd().getFileDescriptor(), args);
                        tp.go(fd);
                    } finally {
                        tp.kill();
                    }
                } catch (IOException e) {
                    pw.println("Failure while dumping the app: " + r);
                    pw.flush();
                } catch (RemoteException e) {
                    pw.println("Got a RemoteException while dumping the app " + r);
                    pw.flush();
                }
            }
        }
    }

    final void dumpDbInfo(FileDescriptor fd, PrintWriter pw, String[] args) {
        ArrayList<ProcessRecord> procs = collectProcesses(pw, 0, false, args);
        if (procs == null) {
            pw.println("No process found for: " + args[0]);
            return;
        }

        pw.println("Applications Database Info:");

        for (int i = procs.size() - 1 ; i >= 0 ; i--) {
            ProcessRecord r = procs.get(i);
            if (r.thread != null) {
                pw.println("\n** Database info for pid " + r.pid + " [" + r.processName + "] **");
                pw.flush();
                try {
                    TransferPipe tp = new TransferPipe();
                    try {
                        r.thread.dumpDbInfo(tp.getWriteFd().getFileDescriptor(), args);
                        tp.go(fd);
                    } finally {
                        tp.kill();
                    }
                } catch (IOException e) {
                    pw.println("Failure while dumping the app: " + r);
                    pw.flush();
                } catch (RemoteException e) {
                    pw.println("Got a RemoteException while dumping the app " + r);
                    pw.flush();
                }
            }
        }
    }

    final static class MemItem {
        final boolean isProc;
        final String label;
        final String shortLabel;
        final long pss;
        final long swapPss;
        final int id;
        final boolean hasActivities;
        ArrayList<MemItem> subitems;

        public MemItem(String _label, String _shortLabel, long _pss, long _swapPss, int _id,
                boolean _hasActivities) {
            isProc = true;
            label = _label;
            shortLabel = _shortLabel;
            pss = _pss;
            swapPss = _swapPss;
            id = _id;
            hasActivities = _hasActivities;
        }

        public MemItem(String _label, String _shortLabel, long _pss, long _swapPss, int _id) {
            isProc = false;
            label = _label;
            shortLabel = _shortLabel;
            pss = _pss;
            swapPss = _swapPss;
            id = _id;
            hasActivities = false;
        }
    }

    static final void dumpMemItems(PrintWriter pw, String prefix, String tag,
            ArrayList<MemItem> items, boolean sort, boolean isCompact, boolean dumpSwapPss) {
        if (sort && !isCompact) {
            Collections.sort(items, new Comparator<MemItem>() {
                @Override
                public int compare(MemItem lhs, MemItem rhs) {
                    if (lhs.pss < rhs.pss) {
                        return 1;
                    } else if (lhs.pss > rhs.pss) {
                        return -1;
                    }
                    return 0;
                }
            });
        }

        for (int i=0; i<items.size(); i++) {
            MemItem mi = items.get(i);
            if (!isCompact) {
                if (dumpSwapPss) {
                    pw.printf("%s%s: %-60s (%s in swap)\n", prefix, stringifyKBSize(mi.pss),
                            mi.label, stringifyKBSize(mi.swapPss));
                } else {
                    pw.printf("%s%s: %s\n", prefix, stringifyKBSize(mi.pss), mi.label);
                }
            } else if (mi.isProc) {
                pw.print("proc,"); pw.print(tag); pw.print(","); pw.print(mi.shortLabel);
                pw.print(","); pw.print(mi.id); pw.print(","); pw.print(mi.pss); pw.print(",");
                pw.print(dumpSwapPss ? mi.swapPss : "N/A");
                pw.println(mi.hasActivities ? ",a" : ",e");
            } else {
                pw.print(tag); pw.print(","); pw.print(mi.shortLabel); pw.print(",");
                pw.print(mi.pss); pw.print(","); pw.println(dumpSwapPss ? mi.swapPss : "N/A");
            }
            if (mi.subitems != null) {
                dumpMemItems(pw, prefix + "    ", mi.shortLabel, mi.subitems,
                        true, isCompact, dumpSwapPss);
            }
        }
    }

    // These are in KB.
    static final long[] DUMP_MEM_BUCKETS = new long[] {
        5*1024, 7*1024, 10*1024, 15*1024, 20*1024, 30*1024, 40*1024, 80*1024,
        120*1024, 160*1024, 200*1024,
        250*1024, 300*1024, 350*1024, 400*1024, 500*1024, 600*1024, 800*1024,
        1*1024*1024, 2*1024*1024, 5*1024*1024, 10*1024*1024, 20*1024*1024
    };

    static final void appendMemBucket(StringBuilder out, long memKB, String label,
            boolean stackLike) {
        int start = label.lastIndexOf('.');
        if (start >= 0) start++;
        else start = 0;
        int end = label.length();
        for (int i=0; i<DUMP_MEM_BUCKETS.length; i++) {
            if (DUMP_MEM_BUCKETS[i] >= memKB) {
                long bucket = DUMP_MEM_BUCKETS[i]/1024;
                out.append(bucket);
                out.append(stackLike ? "MB." : "MB ");
                out.append(label, start, end);
                return;
            }
        }
        out.append(memKB/1024);
        out.append(stackLike ? "MB." : "MB ");
        out.append(label, start, end);
    }

    static final int[] DUMP_MEM_OOM_ADJ = new int[] {
            ProcessList.NATIVE_ADJ,
            ProcessList.SYSTEM_ADJ, ProcessList.PERSISTENT_PROC_ADJ,
            ProcessList.PERSISTENT_SERVICE_ADJ, ProcessList.FOREGROUND_APP_ADJ,
            ProcessList.VISIBLE_APP_ADJ, ProcessList.PERCEPTIBLE_APP_ADJ,
            ProcessList.BACKUP_APP_ADJ, ProcessList.HEAVY_WEIGHT_APP_ADJ,
            ProcessList.SERVICE_ADJ, ProcessList.HOME_APP_ADJ,
            ProcessList.PREVIOUS_APP_ADJ, ProcessList.SERVICE_B_ADJ, ProcessList.CACHED_APP_MIN_ADJ
    };
    static final String[] DUMP_MEM_OOM_LABEL = new String[] {
            "Native",
            "System", "Persistent", "Persistent Service", "Foreground",
            "Visible", "Perceptible",
            "Heavy Weight", "Backup",
            "A Services", "Home",
            "Previous", "B Services", "Cached"
    };
    static final String[] DUMP_MEM_OOM_COMPACT_LABEL = new String[] {
            "native",
            "sys", "pers", "persvc", "fore",
            "vis", "percept",
            "heavy", "backup",
            "servicea", "home",
            "prev", "serviceb", "cached"
    };

    private final void dumpApplicationMemoryUsageHeader(PrintWriter pw, long uptime,
            long realtime, boolean isCheckinRequest, boolean isCompact) {
        if (isCompact) {
            pw.print("version,"); pw.println(MEMINFO_COMPACT_VERSION);
        }
        if (isCheckinRequest || isCompact) {
            // short checkin version
            pw.print("time,"); pw.print(uptime); pw.print(","); pw.println(realtime);
        } else {
            pw.println("Applications Memory Usage (in Kilobytes):");
            pw.println("Uptime: " + uptime + " Realtime: " + realtime);
        }
    }

    private static final int KSM_SHARED = 0;
    private static final int KSM_SHARING = 1;
    private static final int KSM_UNSHARED = 2;
    private static final int KSM_VOLATILE = 3;

    private final long[] getKsmInfo() {
        long[] longOut = new long[4];
        final int[] SINGLE_LONG_FORMAT = new int[] {
            Process.PROC_SPACE_TERM|Process.PROC_OUT_LONG
        };
        long[] longTmp = new long[1];
        Process.readProcFile("/sys/kernel/mm/ksm/pages_shared",
                SINGLE_LONG_FORMAT, null, longTmp, null);
        longOut[KSM_SHARED] = longTmp[0] * ProcessList.PAGE_SIZE / 1024;
        longTmp[0] = 0;
        Process.readProcFile("/sys/kernel/mm/ksm/pages_sharing",
                SINGLE_LONG_FORMAT, null, longTmp, null);
        longOut[KSM_SHARING] = longTmp[0] * ProcessList.PAGE_SIZE / 1024;
        longTmp[0] = 0;
        Process.readProcFile("/sys/kernel/mm/ksm/pages_unshared",
                SINGLE_LONG_FORMAT, null, longTmp, null);
        longOut[KSM_UNSHARED] = longTmp[0] * ProcessList.PAGE_SIZE / 1024;
        longTmp[0] = 0;
        Process.readProcFile("/sys/kernel/mm/ksm/pages_volatile",
                SINGLE_LONG_FORMAT, null, longTmp, null);
        longOut[KSM_VOLATILE] = longTmp[0] * ProcessList.PAGE_SIZE / 1024;
        return longOut;
    }

    private static String stringifySize(long size, int order) {
        Locale locale = Locale.US;
        switch (order) {
            case 1:
                return String.format(locale, "%,13d", size);
            case 1024:
                return String.format(locale, "%,9dK", size / 1024);
            case 1024 * 1024:
                return String.format(locale, "%,5dM", size / 1024 / 1024);
            case 1024 * 1024 * 1024:
                return String.format(locale, "%,1dG", size / 1024 / 1024 / 1024);
            default:
                throw new IllegalArgumentException("Invalid size order");
        }
    }

    private static String stringifyKBSize(long size) {
        return stringifySize(size * 1024, 1024);
    }

    // Update this version number in case you change the 'compact' format
    private static final int MEMINFO_COMPACT_VERSION = 1;

    final void dumpApplicationMemoryUsage(FileDescriptor fd,
            PrintWriter pw, String prefix, String[] args, boolean brief, PrintWriter categoryPw) {
        boolean dumpDetails = false;
        boolean dumpFullDetails = false;
        boolean dumpDalvik = false;
        boolean dumpSummaryOnly = false;
        boolean dumpUnreachable = false;
        boolean oomOnly = false;
        boolean isCompact = false;
        boolean localOnly = false;
        boolean packages = false;
        boolean isCheckinRequest = false;
        boolean dumpSwapPss = false;

        int opti = 0;
        while (opti < args.length) {
            String opt = args[opti];
            if (opt == null || opt.length() <= 0 || opt.charAt(0) != '-') {
                break;
            }
            opti++;
            if ("-a".equals(opt)) {
                dumpDetails = true;
                dumpFullDetails = true;
                dumpDalvik = true;
                dumpSwapPss = true;
            } else if ("-d".equals(opt)) {
                dumpDalvik = true;
            } else if ("-c".equals(opt)) {
                isCompact = true;
            } else if ("-s".equals(opt)) {
                dumpDetails = true;
                dumpSummaryOnly = true;
            } else if ("-S".equals(opt)) {
                dumpSwapPss = true;
            } else if ("--unreachable".equals(opt)) {
                dumpUnreachable = true;
            } else if ("--oom".equals(opt)) {
                oomOnly = true;
            } else if ("--local".equals(opt)) {
                localOnly = true;
            } else if ("--package".equals(opt)) {
                packages = true;
            } else if ("--checkin".equals(opt)) {
                isCheckinRequest = true;

            } else if ("-h".equals(opt)) {
                pw.println("meminfo dump options: [-a] [-d] [-c] [-s] [--oom] [process]");
                pw.println("  -a: include all available information for each process.");
                pw.println("  -d: include dalvik details.");
                pw.println("  -c: dump in a compact machine-parseable representation.");
                pw.println("  -s: dump only summary of application memory usage.");
                pw.println("  -S: dump also SwapPss.");
                pw.println("  --oom: only show processes organized by oom adj.");
                pw.println("  --local: only collect details locally, don't call process.");
                pw.println("  --package: interpret process arg as package, dumping all");
                pw.println("             processes that have loaded that package.");
                pw.println("  --checkin: dump data for a checkin");
                pw.println("If [process] is specified it can be the name or ");
                pw.println("pid of a specific process to dump.");
                return;
            } else {
                pw.println("Unknown argument: " + opt + "; use -h for help");
            }
        }

        long uptime = SystemClock.uptimeMillis();
        long realtime = SystemClock.elapsedRealtime();
        final long[] tmpLong = new long[1];

        ArrayList<ProcessRecord> procs = collectProcesses(pw, opti, packages, args);
        if (procs == null) {
            // No Java processes.  Maybe they want to print a native process.
            if (args != null && args.length > opti
                    && args[opti].charAt(0) != '-') {
                ArrayList<ProcessCpuTracker.Stats> nativeProcs
                        = new ArrayList<ProcessCpuTracker.Stats>();
                updateCpuStatsNow();
                int findPid = -1;
                try {
                    findPid = Integer.parseInt(args[opti]);
                } catch (NumberFormatException e) {
                }
                synchronized (mProcessCpuTracker) {
                    final int N = mProcessCpuTracker.countStats();
                    for (int i=0; i<N; i++) {
                        ProcessCpuTracker.Stats st = mProcessCpuTracker.getStats(i);
                        if (st.pid == findPid || (st.baseName != null
                                && st.baseName.equals(args[opti]))) {
                            nativeProcs.add(st);
                        }
                    }
                }
                if (nativeProcs.size() > 0) {
                    dumpApplicationMemoryUsageHeader(pw, uptime, realtime, isCheckinRequest,
                            isCompact);
                    Debug.MemoryInfo mi = null;
                    for (int i = nativeProcs.size() - 1 ; i >= 0 ; i--) {
                        final ProcessCpuTracker.Stats r = nativeProcs.get(i);
                        final int pid = r.pid;
                        if (!isCheckinRequest && dumpDetails) {
                            pw.println("\n** MEMINFO in pid " + pid + " [" + r.baseName + "] **");
                        }
                        if (mi == null) {
                            mi = new Debug.MemoryInfo();
                        }
                        if (dumpDetails || (!brief && !oomOnly)) {
                            Debug.getMemoryInfo(pid, mi);
                        } else {
                            mi.dalvikPss = (int)Debug.getPss(pid, tmpLong, null);
                            mi.dalvikPrivateDirty = (int)tmpLong[0];
                        }
                        ActivityThread.dumpMemInfoTable(pw, mi, isCheckinRequest, dumpFullDetails,
                                dumpDalvik, dumpSummaryOnly, pid, r.baseName, 0, 0, 0, 0, 0, 0);
                        if (isCheckinRequest) {
                            pw.println();
                        }
                    }
                    return;
                }
            }
            pw.println("No process found for: " + args[opti]);
            return;
        }

        if (!brief && !oomOnly && (procs.size() == 1 || isCheckinRequest || packages)) {
            dumpDetails = true;
        }

        dumpApplicationMemoryUsageHeader(pw, uptime, realtime, isCheckinRequest, isCompact);

        String[] innerArgs = new String[args.length-opti];
        System.arraycopy(args, opti, innerArgs, 0, args.length-opti);

        ArrayList<MemItem> procMems = new ArrayList<MemItem>();
        final SparseArray<MemItem> procMemsMap = new SparseArray<MemItem>();
        long nativePss = 0;
        long nativeSwapPss = 0;
        long dalvikPss = 0;
        long dalvikSwapPss = 0;
        long[] dalvikSubitemPss = dumpDalvik ? new long[Debug.MemoryInfo.NUM_DVK_STATS] :
                EmptyArray.LONG;
        long[] dalvikSubitemSwapPss = dumpDalvik ? new long[Debug.MemoryInfo.NUM_DVK_STATS] :
                EmptyArray.LONG;
        long otherPss = 0;
        long otherSwapPss = 0;
        long[] miscPss = new long[Debug.MemoryInfo.NUM_OTHER_STATS];
        long[] miscSwapPss = new long[Debug.MemoryInfo.NUM_OTHER_STATS];

        long oomPss[] = new long[DUMP_MEM_OOM_LABEL.length];
        long oomSwapPss[] = new long[DUMP_MEM_OOM_LABEL.length];
        ArrayList<MemItem>[] oomProcs = (ArrayList<MemItem>[])
                new ArrayList[DUMP_MEM_OOM_LABEL.length];

        long totalPss = 0;
        long totalSwapPss = 0;
        long cachedPss = 0;
        long cachedSwapPss = 0;
        boolean hasSwapPss = false;

        Debug.MemoryInfo mi = null;
        for (int i = procs.size() - 1 ; i >= 0 ; i--) {
            final ProcessRecord r = procs.get(i);
            final IApplicationThread thread;
            final int pid;
            final int oomAdj;
            final boolean hasActivities;
            synchronized (this) {
                thread = r.thread;
                pid = r.pid;
                oomAdj = r.getSetAdjWithServices();
                hasActivities = r.activities.size() > 0;
            }
            if (thread != null) {
                if (!isCheckinRequest && dumpDetails) {
                    pw.println("\n** MEMINFO in pid " + pid + " [" + r.processName + "] **");
                }
                if (mi == null) {
                    mi = new Debug.MemoryInfo();
                }
                if (dumpDetails || (!brief && !oomOnly)) {
                    Debug.getMemoryInfo(pid, mi);
                    hasSwapPss = mi.hasSwappedOutPss;
                } else {
                    mi.dalvikPss = (int)Debug.getPss(pid, tmpLong, null);
                    mi.dalvikPrivateDirty = (int)tmpLong[0];
                }
                if (dumpDetails) {
                    if (localOnly) {
                        ActivityThread.dumpMemInfoTable(pw, mi, isCheckinRequest, dumpFullDetails,
                                dumpDalvik, dumpSummaryOnly, pid, r.processName, 0, 0, 0, 0, 0, 0);
                        if (isCheckinRequest) {
                            pw.println();
                        }
                    } else {
                        try {
                            pw.flush();
                            thread.dumpMemInfo(fd, mi, isCheckinRequest, dumpFullDetails,
                                    dumpDalvik, dumpSummaryOnly, dumpUnreachable, innerArgs);
                        } catch (RemoteException e) {
                            if (!isCheckinRequest) {
                                pw.println("Got RemoteException!");
                                pw.flush();
                            }
                        }
                    }
                }

                final long myTotalPss = mi.getTotalPss();
                final long myTotalUss = mi.getTotalUss();
                final long myTotalSwapPss = mi.getTotalSwappedOutPss();

                synchronized (this) {
                    if (r.thread != null && oomAdj == r.getSetAdjWithServices()) {
                        // Record this for posterity if the process has been stable.
                        r.baseProcessTracker.addPss(myTotalPss, myTotalUss, true, r.pkgList);
                    }
                }

                if (!isCheckinRequest && mi != null) {
                    totalPss += myTotalPss;
                    totalSwapPss += myTotalSwapPss;
                    MemItem pssItem = new MemItem(r.processName + " (pid " + pid +
                            (hasActivities ? " / activities)" : ")"), r.processName, myTotalPss,
                            myTotalSwapPss, pid, hasActivities);
                    procMems.add(pssItem);
                    procMemsMap.put(pid, pssItem);

                    nativePss += mi.nativePss;
                    nativeSwapPss += mi.nativeSwappedOutPss;
                    dalvikPss += mi.dalvikPss;
                    dalvikSwapPss += mi.dalvikSwappedOutPss;
                    for (int j=0; j<dalvikSubitemPss.length; j++) {
                        dalvikSubitemPss[j] += mi.getOtherPss(Debug.MemoryInfo.NUM_OTHER_STATS + j);
                        dalvikSubitemSwapPss[j] +=
                                mi.getOtherSwappedOutPss(Debug.MemoryInfo.NUM_OTHER_STATS + j);
                    }
                    otherPss += mi.otherPss;
                    otherSwapPss += mi.otherSwappedOutPss;
                    for (int j=0; j<Debug.MemoryInfo.NUM_OTHER_STATS; j++) {
                        long mem = mi.getOtherPss(j);
                        miscPss[j] += mem;
                        otherPss -= mem;
                        mem = mi.getOtherSwappedOutPss(j);
                        miscSwapPss[j] += mem;
                        otherSwapPss -= mem;
                    }

                    if (oomAdj >= ProcessList.CACHED_APP_MIN_ADJ) {
                        cachedPss += myTotalPss;
                        cachedSwapPss += myTotalSwapPss;
                    }

                    for (int oomIndex=0; oomIndex<oomPss.length; oomIndex++) {
                        if (oomIndex == (oomPss.length - 1)
                                || (oomAdj >= DUMP_MEM_OOM_ADJ[oomIndex]
                                        && oomAdj < DUMP_MEM_OOM_ADJ[oomIndex + 1])) {
                            oomPss[oomIndex] += myTotalPss;
                            oomSwapPss[oomIndex] += myTotalSwapPss;
                            if (oomProcs[oomIndex] == null) {
                                oomProcs[oomIndex] = new ArrayList<MemItem>();
                            }
                            oomProcs[oomIndex].add(pssItem);
                            break;
                        }
                    }
                }
            }
        }

        long nativeProcTotalPss = 0;

        if (!isCheckinRequest && procs.size() > 1 && !packages) {
            // If we are showing aggregations, also look for native processes to
            // include so that our aggregations are more accurate.
            updateCpuStatsNow();
            mi = null;
            synchronized (mProcessCpuTracker) {
                final int N = mProcessCpuTracker.countStats();
                for (int i=0; i<N; i++) {
                    ProcessCpuTracker.Stats st = mProcessCpuTracker.getStats(i);
                    if (st.vsize > 0 && procMemsMap.indexOfKey(st.pid) < 0) {
                        if (mi == null) {
                            mi = new Debug.MemoryInfo();
                        }
                        if (!brief && !oomOnly) {
                            Debug.getMemoryInfo(st.pid, mi);
                        } else {
                            mi.nativePss = (int)Debug.getPss(st.pid, tmpLong, null);
                            mi.nativePrivateDirty = (int)tmpLong[0];
                        }

                        final long myTotalPss = mi.getTotalPss();
                        final long myTotalSwapPss = mi.getTotalSwappedOutPss();
                        totalPss += myTotalPss;
                        nativeProcTotalPss += myTotalPss;

                        MemItem pssItem = new MemItem(st.name + " (pid " + st.pid + ")",
                                st.name, myTotalPss, mi.getSummaryTotalSwapPss(), st.pid, false);
                        procMems.add(pssItem);

                        nativePss += mi.nativePss;
                        nativeSwapPss += mi.nativeSwappedOutPss;
                        dalvikPss += mi.dalvikPss;
                        dalvikSwapPss += mi.dalvikSwappedOutPss;
                        for (int j=0; j<dalvikSubitemPss.length; j++) {
                            dalvikSubitemPss[j] += mi.getOtherPss(Debug.MemoryInfo.NUM_OTHER_STATS + j);
                            dalvikSubitemSwapPss[j] +=
                                    mi.getOtherSwappedOutPss(Debug.MemoryInfo.NUM_OTHER_STATS + j);
                        }
                        otherPss += mi.otherPss;
                        otherSwapPss += mi.otherSwappedOutPss;
                        for (int j=0; j<Debug.MemoryInfo.NUM_OTHER_STATS; j++) {
                            long mem = mi.getOtherPss(j);
                            miscPss[j] += mem;
                            otherPss -= mem;
                            mem = mi.getOtherSwappedOutPss(j);
                            miscSwapPss[j] += mem;
                            otherSwapPss -= mem;
                        }
                        oomPss[0] += myTotalPss;
                        oomSwapPss[0] += myTotalSwapPss;
                        if (oomProcs[0] == null) {
                            oomProcs[0] = new ArrayList<MemItem>();
                        }
                        oomProcs[0].add(pssItem);
                    }
                }
            }

            ArrayList<MemItem> catMems = new ArrayList<MemItem>();

            catMems.add(new MemItem("Native", "Native", nativePss, nativeSwapPss, -1));
            final MemItem dalvikItem =
                    new MemItem("Dalvik", "Dalvik", dalvikPss, dalvikSwapPss, -2);
            if (dalvikSubitemPss.length > 0) {
                dalvikItem.subitems = new ArrayList<MemItem>();
                for (int j=0; j<dalvikSubitemPss.length; j++) {
                    final String name = Debug.MemoryInfo.getOtherLabel(
                            Debug.MemoryInfo.NUM_OTHER_STATS + j);
                    dalvikItem.subitems.add(new MemItem(name, name, dalvikSubitemPss[j],
                                    dalvikSubitemSwapPss[j], j));
                }
            }
            catMems.add(dalvikItem);
            catMems.add(new MemItem("Unknown", "Unknown", otherPss, otherSwapPss, -3));
            for (int j=0; j<Debug.MemoryInfo.NUM_OTHER_STATS; j++) {
                String label = Debug.MemoryInfo.getOtherLabel(j);
                catMems.add(new MemItem(label, label, miscPss[j], miscSwapPss[j], j));
            }

            ArrayList<MemItem> oomMems = new ArrayList<MemItem>();
            for (int j=0; j<oomPss.length; j++) {
                if (oomPss[j] != 0) {
                    String label = isCompact ? DUMP_MEM_OOM_COMPACT_LABEL[j]
                            : DUMP_MEM_OOM_LABEL[j];
                    MemItem item = new MemItem(label, label, oomPss[j], oomSwapPss[j],
                            DUMP_MEM_OOM_ADJ[j]);
                    item.subitems = oomProcs[j];
                    oomMems.add(item);
                }
            }

            dumpSwapPss = dumpSwapPss && hasSwapPss && totalSwapPss != 0;
            if (!brief && !oomOnly && !isCompact) {
                pw.println();
                pw.println("Total PSS by process:");
                dumpMemItems(pw, "  ", "proc", procMems, true, isCompact, dumpSwapPss);
                pw.println();
            }
            if (!isCompact) {
                pw.println("Total PSS by OOM adjustment:");
            }
            dumpMemItems(pw, "  ", "oom", oomMems, false, isCompact, dumpSwapPss);
            if (!brief && !oomOnly) {
                PrintWriter out = categoryPw != null ? categoryPw : pw;
                if (!isCompact) {
                    out.println();
                    out.println("Total PSS by category:");
                }
                dumpMemItems(out, "  ", "cat", catMems, true, isCompact, dumpSwapPss);
            }
            if (!isCompact) {
                pw.println();
            }
            MemInfoReader memInfo = new MemInfoReader();
            memInfo.readMemInfo();
            if (nativeProcTotalPss > 0) {
                synchronized (this) {
                    final long cachedKb = memInfo.getCachedSizeKb();
                    final long freeKb = memInfo.getFreeSizeKb();
                    final long zramKb = memInfo.getZramTotalSizeKb();
                    final long kernelKb = memInfo.getKernelUsedSizeKb();
                    EventLogTags.writeAmMeminfo(cachedKb*1024, freeKb*1024, zramKb*1024,
                            kernelKb*1024, nativeProcTotalPss*1024);
                    mProcessStats.addSysMemUsageLocked(cachedKb, freeKb, zramKb, kernelKb,
                            nativeProcTotalPss);
                }
            }
            if (!brief) {
                if (!isCompact) {
                    pw.print("Total RAM: "); pw.print(stringifyKBSize(memInfo.getTotalSizeKb()));
                    pw.print(" (status ");
                    switch (mLastMemoryLevel) {
                        case ProcessStats.ADJ_MEM_FACTOR_NORMAL:
                            pw.println("normal)");
                            break;
                        case ProcessStats.ADJ_MEM_FACTOR_MODERATE:
                            pw.println("moderate)");
                            break;
                        case ProcessStats.ADJ_MEM_FACTOR_LOW:
                            pw.println("low)");
                            break;
                        case ProcessStats.ADJ_MEM_FACTOR_CRITICAL:
                            pw.println("critical)");
                            break;
                        default:
                            pw.print(mLastMemoryLevel);
                            pw.println(")");
                            break;
                    }
                    pw.print(" Free RAM: ");
                    pw.print(stringifyKBSize(cachedPss + memInfo.getCachedSizeKb()
                            + memInfo.getFreeSizeKb()));
                    pw.print(" (");
                    pw.print(stringifyKBSize(cachedPss));
                    pw.print(" cached pss + ");
                    pw.print(stringifyKBSize(memInfo.getCachedSizeKb()));
                    pw.print(" cached kernel + ");
                    pw.print(stringifyKBSize(memInfo.getFreeSizeKb()));
                    pw.println(" free)");
                } else {
                    pw.print("ram,"); pw.print(memInfo.getTotalSizeKb()); pw.print(",");
                    pw.print(cachedPss + memInfo.getCachedSizeKb()
                            + memInfo.getFreeSizeKb()); pw.print(",");
                    pw.println(totalPss - cachedPss);
                }
            }
            long lostRAM = memInfo.getTotalSizeKb() - (totalPss - totalSwapPss)
                    - memInfo.getFreeSizeKb() - memInfo.getCachedSizeKb()
                    - memInfo.getKernelUsedSizeKb() - memInfo.getZramTotalSizeKb();
            if (!isCompact) {
                pw.print(" Used RAM: "); pw.print(stringifyKBSize(totalPss - cachedPss
                        + memInfo.getKernelUsedSizeKb())); pw.print(" (");
                pw.print(stringifyKBSize(totalPss - cachedPss)); pw.print(" used pss + ");
                pw.print(stringifyKBSize(memInfo.getKernelUsedSizeKb())); pw.print(" kernel)\n");
                pw.print(" Lost RAM: "); pw.println(stringifyKBSize(lostRAM));
            } else {
                pw.print("lostram,"); pw.println(lostRAM);
            }
            if (!brief) {
                if (memInfo.getZramTotalSizeKb() != 0) {
                    if (!isCompact) {
                        pw.print("     ZRAM: ");
                        pw.print(stringifyKBSize(memInfo.getZramTotalSizeKb()));
                                pw.print(" physical used for ");
                                pw.print(stringifyKBSize(memInfo.getSwapTotalSizeKb()
                                        - memInfo.getSwapFreeSizeKb()));
                                pw.print(" in swap (");
                                pw.print(stringifyKBSize(memInfo.getSwapTotalSizeKb()));
                                pw.println(" total swap)");
                    } else {
                        pw.print("zram,"); pw.print(memInfo.getZramTotalSizeKb()); pw.print(",");
                                pw.print(memInfo.getSwapTotalSizeKb()); pw.print(",");
                                pw.println(memInfo.getSwapFreeSizeKb());
                    }
                }
                final long[] ksm = getKsmInfo();
                if (!isCompact) {
                    if (ksm[KSM_SHARING] != 0 || ksm[KSM_SHARED] != 0 || ksm[KSM_UNSHARED] != 0
                            || ksm[KSM_VOLATILE] != 0) {
                        pw.print("      KSM: "); pw.print(stringifyKBSize(ksm[KSM_SHARING]));
                                pw.print(" saved from shared ");
                                pw.print(stringifyKBSize(ksm[KSM_SHARED]));
                        pw.print("           "); pw.print(stringifyKBSize(ksm[KSM_UNSHARED]));
                                pw.print(" unshared; ");
                                pw.print(stringifyKBSize(
                                             ksm[KSM_VOLATILE])); pw.println(" volatile");
                    }
                    pw.print("   Tuning: ");
                    pw.print(ActivityManager.staticGetMemoryClass());
                    pw.print(" (large ");
                    pw.print(ActivityManager.staticGetLargeMemoryClass());
                    pw.print("), oom ");
                    pw.print(stringifySize(
                                mProcessList.getMemLevel(ProcessList.CACHED_APP_MAX_ADJ), 1024));
                    pw.print(", restore limit ");
                    pw.print(stringifyKBSize(mProcessList.getCachedRestoreThresholdKb()));
                    if (ActivityManager.isLowRamDeviceStatic()) {
                        pw.print(" (low-ram)");
                    }
                    if (ActivityManager.isHighEndGfx()) {
                        pw.print(" (high-end-gfx)");
                    }
                    pw.println();
                } else {
                    pw.print("ksm,"); pw.print(ksm[KSM_SHARING]); pw.print(",");
                    pw.print(ksm[KSM_SHARED]); pw.print(","); pw.print(ksm[KSM_UNSHARED]);
                    pw.print(","); pw.println(ksm[KSM_VOLATILE]);
                    pw.print("tuning,");
                    pw.print(ActivityManager.staticGetMemoryClass());
                    pw.print(',');
                    pw.print(ActivityManager.staticGetLargeMemoryClass());
                    pw.print(',');
                    pw.print(mProcessList.getMemLevel(ProcessList.CACHED_APP_MAX_ADJ)/1024);
                    if (ActivityManager.isLowRamDeviceStatic()) {
                        pw.print(",low-ram");
                    }
                    if (ActivityManager.isHighEndGfx()) {
                        pw.print(",high-end-gfx");
                    }
                    pw.println();
                }
            }
        }
    }

    private void appendBasicMemEntry(StringBuilder sb, int oomAdj, int procState, long pss,
            long memtrack, String name) {
        sb.append("  ");
        sb.append(ProcessList.makeOomAdjString(oomAdj));
        sb.append(' ');
        sb.append(ProcessList.makeProcStateString(procState));
        sb.append(' ');
        ProcessList.appendRamKb(sb, pss);
        sb.append(": ");
        sb.append(name);
        if (memtrack > 0) {
            sb.append(" (");
            sb.append(stringifyKBSize(memtrack));
            sb.append(" memtrack)");
        }
    }

    private void appendMemInfo(StringBuilder sb, ProcessMemInfo mi) {
        appendBasicMemEntry(sb, mi.oomAdj, mi.procState, mi.pss, mi.memtrack, mi.name);
        sb.append(" (pid ");
        sb.append(mi.pid);
        sb.append(") ");
        sb.append(mi.adjType);
        sb.append('\n');
        if (mi.adjReason != null) {
            sb.append("                      ");
            sb.append(mi.adjReason);
            sb.append('\n');
        }
    }

    void reportMemUsage(ArrayList<ProcessMemInfo> memInfos) {
        final SparseArray<ProcessMemInfo> infoMap = new SparseArray<>(memInfos.size());
        for (int i=0, N=memInfos.size(); i<N; i++) {
            ProcessMemInfo mi = memInfos.get(i);
            infoMap.put(mi.pid, mi);
        }
        updateCpuStatsNow();
        long[] memtrackTmp = new long[1];
        final List<ProcessCpuTracker.Stats> stats;
        // Get a list of Stats that have vsize > 0
        synchronized (mProcessCpuTracker) {
            stats = mProcessCpuTracker.getStats((st) -> {
                return st.vsize > 0;
            });
        }
        final int statsCount = stats.size();
        for (int i = 0; i < statsCount; i++) {
            ProcessCpuTracker.Stats st = stats.get(i);
            long pss = Debug.getPss(st.pid, null, memtrackTmp);
            if (pss > 0) {
                if (infoMap.indexOfKey(st.pid) < 0) {
                    ProcessMemInfo mi = new ProcessMemInfo(st.name, st.pid,
                            ProcessList.NATIVE_ADJ, -1, "native", null);
                    mi.pss = pss;
                    mi.memtrack = memtrackTmp[0];
                    memInfos.add(mi);
                }
            }
        }

        long totalPss = 0;
        long totalMemtrack = 0;
        for (int i=0, N=memInfos.size(); i<N; i++) {
            ProcessMemInfo mi = memInfos.get(i);
            if (mi.pss == 0) {
                mi.pss = Debug.getPss(mi.pid, null, memtrackTmp);
                mi.memtrack = memtrackTmp[0];
            }
            totalPss += mi.pss;
            totalMemtrack += mi.memtrack;
        }
        Collections.sort(memInfos, new Comparator<ProcessMemInfo>() {
            @Override public int compare(ProcessMemInfo lhs, ProcessMemInfo rhs) {
                if (lhs.oomAdj != rhs.oomAdj) {
                    return lhs.oomAdj < rhs.oomAdj ? -1 : 1;
                }
                if (lhs.pss != rhs.pss) {
                    return lhs.pss < rhs.pss ? 1 : -1;
                }
                return 0;
            }
        });

        StringBuilder tag = new StringBuilder(128);
        StringBuilder stack = new StringBuilder(128);
        tag.append("Low on memory -- ");
        appendMemBucket(tag, totalPss, "total", false);
        appendMemBucket(stack, totalPss, "total", true);

        StringBuilder fullNativeBuilder = new StringBuilder(1024);
        StringBuilder shortNativeBuilder = new StringBuilder(1024);
        StringBuilder fullJavaBuilder = new StringBuilder(1024);

        boolean firstLine = true;
        int lastOomAdj = Integer.MIN_VALUE;
        long extraNativeRam = 0;
        long extraNativeMemtrack = 0;
        long cachedPss = 0;
        for (int i=0, N=memInfos.size(); i<N; i++) {
            ProcessMemInfo mi = memInfos.get(i);

            if (mi.oomAdj >= ProcessList.CACHED_APP_MIN_ADJ) {
                cachedPss += mi.pss;
            }

            if (mi.oomAdj != ProcessList.NATIVE_ADJ
                    && (mi.oomAdj < ProcessList.SERVICE_ADJ
                            || mi.oomAdj == ProcessList.HOME_APP_ADJ
                            || mi.oomAdj == ProcessList.PREVIOUS_APP_ADJ)) {
                if (lastOomAdj != mi.oomAdj) {
                    lastOomAdj = mi.oomAdj;
                    if (mi.oomAdj <= ProcessList.FOREGROUND_APP_ADJ) {
                        tag.append(" / ");
                    }
                    if (mi.oomAdj >= ProcessList.FOREGROUND_APP_ADJ) {
                        if (firstLine) {
                            stack.append(":");
                            firstLine = false;
                        }
                        stack.append("\n\t at ");
                    } else {
                        stack.append("$");
                    }
                } else {
                    tag.append(" ");
                    stack.append("$");
                }
                if (mi.oomAdj <= ProcessList.FOREGROUND_APP_ADJ) {
                    appendMemBucket(tag, mi.pss, mi.name, false);
                }
                appendMemBucket(stack, mi.pss, mi.name, true);
                if (mi.oomAdj >= ProcessList.FOREGROUND_APP_ADJ
                        && ((i+1) >= N || memInfos.get(i+1).oomAdj != lastOomAdj)) {
                    stack.append("(");
                    for (int k=0; k<DUMP_MEM_OOM_ADJ.length; k++) {
                        if (DUMP_MEM_OOM_ADJ[k] == mi.oomAdj) {
                            stack.append(DUMP_MEM_OOM_LABEL[k]);
                            stack.append(":");
                            stack.append(DUMP_MEM_OOM_ADJ[k]);
                        }
                    }
                    stack.append(")");
                }
            }

            appendMemInfo(fullNativeBuilder, mi);
            if (mi.oomAdj == ProcessList.NATIVE_ADJ) {
                // The short form only has native processes that are >= 512K.
                if (mi.pss >= 512) {
                    appendMemInfo(shortNativeBuilder, mi);
                } else {
                    extraNativeRam += mi.pss;
                    extraNativeMemtrack += mi.memtrack;
                }
            } else {
                // Short form has all other details, but if we have collected RAM
                // from smaller native processes let's dump a summary of that.
                if (extraNativeRam > 0) {
                    appendBasicMemEntry(shortNativeBuilder, ProcessList.NATIVE_ADJ,
                            -1, extraNativeRam, extraNativeMemtrack, "(Other native)");
                    shortNativeBuilder.append('\n');
                    extraNativeRam = 0;
                }
                appendMemInfo(fullJavaBuilder, mi);
            }
        }

        fullJavaBuilder.append("           ");
        ProcessList.appendRamKb(fullJavaBuilder, totalPss);
        fullJavaBuilder.append(": TOTAL");
        if (totalMemtrack > 0) {
            fullJavaBuilder.append(" (");
            fullJavaBuilder.append(stringifyKBSize(totalMemtrack));
            fullJavaBuilder.append(" memtrack)");
        } else {
        }
        fullJavaBuilder.append("\n");

        MemInfoReader memInfo = new MemInfoReader();
        memInfo.readMemInfo();
        final long[] infos = memInfo.getRawInfo();

        StringBuilder memInfoBuilder = new StringBuilder(1024);
        Debug.getMemInfo(infos);
        memInfoBuilder.append("  MemInfo: ");
        memInfoBuilder.append(stringifyKBSize(infos[Debug.MEMINFO_SLAB])).append(" slab, ");
        memInfoBuilder.append(stringifyKBSize(infos[Debug.MEMINFO_SHMEM])).append(" shmem, ");
        memInfoBuilder.append(stringifyKBSize(
                                  infos[Debug.MEMINFO_VM_ALLOC_USED])).append(" vm alloc, ");
        memInfoBuilder.append(stringifyKBSize(
                                  infos[Debug.MEMINFO_PAGE_TABLES])).append(" page tables ");
        memInfoBuilder.append(stringifyKBSize(
                                  infos[Debug.MEMINFO_KERNEL_STACK])).append(" kernel stack\n");
        memInfoBuilder.append("           ");
        memInfoBuilder.append(stringifyKBSize(infos[Debug.MEMINFO_BUFFERS])).append(" buffers, ");
        memInfoBuilder.append(stringifyKBSize(infos[Debug.MEMINFO_CACHED])).append(" cached, ");
        memInfoBuilder.append(stringifyKBSize(infos[Debug.MEMINFO_MAPPED])).append(" mapped, ");
        memInfoBuilder.append(stringifyKBSize(infos[Debug.MEMINFO_FREE])).append(" free\n");
        if (infos[Debug.MEMINFO_ZRAM_TOTAL] != 0) {
            memInfoBuilder.append("  ZRAM: ");
            memInfoBuilder.append(stringifyKBSize(infos[Debug.MEMINFO_ZRAM_TOTAL]));
            memInfoBuilder.append(" RAM, ");
            memInfoBuilder.append(stringifyKBSize(infos[Debug.MEMINFO_SWAP_TOTAL]));
            memInfoBuilder.append(" swap total, ");
            memInfoBuilder.append(stringifyKBSize(infos[Debug.MEMINFO_SWAP_FREE]));
            memInfoBuilder.append(" swap free\n");
        }
        final long[] ksm = getKsmInfo();
        if (ksm[KSM_SHARING] != 0 || ksm[KSM_SHARED] != 0 || ksm[KSM_UNSHARED] != 0
                || ksm[KSM_VOLATILE] != 0) {
            memInfoBuilder.append("  KSM: ");
            memInfoBuilder.append(stringifyKBSize(ksm[KSM_SHARING]));
            memInfoBuilder.append(" saved from shared ");
            memInfoBuilder.append(stringifyKBSize(ksm[KSM_SHARED]));
            memInfoBuilder.append("\n       ");
            memInfoBuilder.append(stringifyKBSize(ksm[KSM_UNSHARED]));
            memInfoBuilder.append(" unshared; ");
            memInfoBuilder.append(stringifyKBSize(ksm[KSM_VOLATILE]));
            memInfoBuilder.append(" volatile\n");
        }
        memInfoBuilder.append("  Free RAM: ");
        memInfoBuilder.append(stringifyKBSize(cachedPss + memInfo.getCachedSizeKb()
                + memInfo.getFreeSizeKb()));
        memInfoBuilder.append("\n");
        memInfoBuilder.append("  Used RAM: ");
        memInfoBuilder.append(stringifyKBSize(
                                  totalPss - cachedPss + memInfo.getKernelUsedSizeKb()));
        memInfoBuilder.append("\n");
        memInfoBuilder.append("  Lost RAM: ");
        memInfoBuilder.append(stringifyKBSize(memInfo.getTotalSizeKb()
                - totalPss - memInfo.getFreeSizeKb() - memInfo.getCachedSizeKb()
                - memInfo.getKernelUsedSizeKb() - memInfo.getZramTotalSizeKb()));
        memInfoBuilder.append("\n");
        Slog.i(TAG, "Low on memory:");
        Slog.i(TAG, shortNativeBuilder.toString());
        Slog.i(TAG, fullJavaBuilder.toString());
        Slog.i(TAG, memInfoBuilder.toString());

        StringBuilder dropBuilder = new StringBuilder(1024);
        /*
        StringWriter oomSw = new StringWriter();
        PrintWriter oomPw = new FastPrintWriter(oomSw, false, 256);
        StringWriter catSw = new StringWriter();
        PrintWriter catPw = new FastPrintWriter(catSw, false, 256);
        String[] emptyArgs = new String[] { };
        dumpApplicationMemoryUsage(null, oomPw, "  ", emptyArgs, true, catPw);
        oomPw.flush();
        String oomString = oomSw.toString();
        */
        dropBuilder.append("Low on memory:");
        dropBuilder.append(stack);
        dropBuilder.append('\n');
        dropBuilder.append(fullNativeBuilder);
        dropBuilder.append(fullJavaBuilder);
        dropBuilder.append('\n');
        dropBuilder.append(memInfoBuilder);
        dropBuilder.append('\n');
        /*
        dropBuilder.append(oomString);
        dropBuilder.append('\n');
        */
        StringWriter catSw = new StringWriter();
        synchronized (ActivityManagerService.this) {
            PrintWriter catPw = new FastPrintWriter(catSw, false, 256);
            String[] emptyArgs = new String[] { };
            catPw.println();
            dumpProcessesLocked(null, catPw, emptyArgs, 0, false, null);
            catPw.println();
            mServices.newServiceDumperLocked(null, catPw, emptyArgs, 0,
                    false, null).dumpLocked();
            catPw.println();
            dumpActivitiesLocked(null, catPw, emptyArgs, 0, false, false, null);
            catPw.flush();
        }
        dropBuilder.append(catSw.toString());
        addErrorToDropBox("lowmem", null, "system_server", null,
                null, tag.toString(), dropBuilder.toString(), null, null);
        //Slog.i(TAG, "Sent to dropbox:");
        //Slog.i(TAG, dropBuilder.toString());
        synchronized (ActivityManagerService.this) {
            long now = SystemClock.uptimeMillis();
            if (mLastMemUsageReportTime < now) {
                mLastMemUsageReportTime = now;
            }
        }
    }

    /**
     * Searches array of arguments for the specified string
     * @param args array of argument strings
     * @param value value to search for
     * @return true if the value is contained in the array
     */
    private static boolean scanArgs(String[] args, String value) {
        if (args != null) {
            for (String arg : args) {
                if (value.equals(arg)) {
                    return true;
                }
            }
        }
        return false;
    }

    private final boolean removeDyingProviderLocked(ProcessRecord proc,
            ContentProviderRecord cpr, boolean always) {
        final boolean inLaunching = mLaunchingProviders.contains(cpr);

        if (!inLaunching || always) {
            synchronized (cpr) {
                cpr.launchingApp = null;
                cpr.notifyAll();
            }
            mProviderMap.removeProviderByClass(cpr.name, UserHandle.getUserId(cpr.uid));
            String names[] = cpr.info.authority.split(";");
            for (int j = 0; j < names.length; j++) {
                mProviderMap.removeProviderByName(names[j], UserHandle.getUserId(cpr.uid));
            }
        }

        for (int i = cpr.connections.size() - 1; i >= 0; i--) {
            ContentProviderConnection conn = cpr.connections.get(i);
            if (conn.waiting) {
                // If this connection is waiting for the provider, then we don't
                // need to mess with its process unless we are always removing
                // or for some reason the provider is not currently launching.
                if (inLaunching && !always) {
                    continue;
                }
            }
            ProcessRecord capp = conn.client;
            conn.dead = true;
            if (conn.stableCount > 0) {
                if (!capp.persistent && capp.thread != null
                        && capp.pid != 0
                        && capp.pid != MY_PID) {
                    capp.kill("depends on provider "
                            + cpr.name.flattenToShortString()
                            + " in dying proc " + (proc != null ? proc.processName : "??")
                            + " (adj " + (proc != null ? proc.setAdj : "??") + ")", true);
                }
            } else if (capp.thread != null && conn.provider.provider != null) {
                try {
                    capp.thread.unstableProviderDied(conn.provider.provider.asBinder());
                } catch (RemoteException e) {
                }
                // In the protocol here, we don't expect the client to correctly
                // clean up this connection, we'll just remove it.
                cpr.connections.remove(i);
                if (conn.client.conProviders.remove(conn)) {
                    stopAssociationLocked(capp.uid, capp.processName, cpr.uid, cpr.name);
                }
            }
        }

        if (inLaunching && always) {
            mLaunchingProviders.remove(cpr);
        }
        return inLaunching;
    }

    /**
     * Main code for cleaning up a process when it has gone away.  This is
     * called both as a result of the process dying, or directly when stopping
     * a process when running in single process mode.
     *
     * @return Returns true if the given process has been restarted, so the
     * app that was passed in must remain on the process lists.
     */
    private final boolean cleanUpApplicationRecordLocked(ProcessRecord app,
            boolean restarting, boolean allowRestart, int index, boolean replacingPid) {
        Slog.d(TAG, "cleanUpApplicationRecord -- " + app.pid);
        if (index >= 0) {
            removeLruProcessLocked(app);
            ProcessList.remove(app.pid);
        }

        mProcessesToGc.remove(app);
        mPendingPssProcesses.remove(app);

        // Dismiss any open dialogs.
        if (app.crashDialog != null && !app.forceCrashReport) {
            app.crashDialog.dismiss();
            app.crashDialog = null;
        }
        if (app.anrDialog != null) {
            app.anrDialog.dismiss();
            app.anrDialog = null;
        }
        if (app.waitDialog != null) {
            app.waitDialog.dismiss();
            app.waitDialog = null;
        }

        app.crashing = false;
        app.notResponding = false;

        app.resetPackageList(mProcessStats);
        app.unlinkDeathRecipient();
        app.makeInactive(mProcessStats);
        app.waitingToKill = null;
        app.forcingToForeground = null;
        updateProcessForegroundLocked(app, false, false);
        app.foregroundActivities = false;
        app.hasShownUi = false;
        app.treatLikeActivity = false;
        app.hasAboveClient = false;
        app.hasClientActivities = false;

        mServices.killServicesLocked(app, allowRestart);

        boolean restart = false;

        // Remove published content providers.
        for (int i = app.pubProviders.size() - 1; i >= 0; i--) {
            ContentProviderRecord cpr = app.pubProviders.valueAt(i);
            final boolean always = app.bad || !allowRestart;
            boolean inLaunching = removeDyingProviderLocked(app, cpr, always);
            if ((inLaunching || always) && cpr.hasConnectionOrHandle()) {
                // We left the provider in the launching list, need to
                // restart it.
                restart = true;
            }

            cpr.provider = null;
            cpr.proc = null;
        }
        app.pubProviders.clear();

        // Take care of any launching providers waiting for this process.
        if (cleanupAppInLaunchingProvidersLocked(app, false)) {
            restart = true;
        }

        // Unregister from connected content providers.
        if (!app.conProviders.isEmpty()) {
            for (int i = app.conProviders.size() - 1; i >= 0; i--) {
                ContentProviderConnection conn = app.conProviders.get(i);
                conn.provider.connections.remove(conn);
                stopAssociationLocked(app.uid, app.processName, conn.provider.uid,
                        conn.provider.name);
            }
            app.conProviders.clear();
        }

        // At this point there may be remaining entries in mLaunchingProviders
        // where we were the only one waiting, so they are no longer of use.
        // Look for these and clean up if found.
        // XXX Commented out for now.  Trying to figure out a way to reproduce
        // the actual situation to identify what is actually going on.
        if (false) {
            for (int i = mLaunchingProviders.size() - 1; i >= 0; i--) {
                ContentProviderRecord cpr = mLaunchingProviders.get(i);
                if (cpr.connections.size() <= 0 && !cpr.hasExternalProcessHandles()) {
                    synchronized (cpr) {
                        cpr.launchingApp = null;
                        cpr.notifyAll();
                    }
                }
            }
        }

        skipCurrentReceiverLocked(app);

        // Unregister any receivers.
        for (int i = app.receivers.size() - 1; i >= 0; i--) {
            removeReceiverLocked(app.receivers.valueAt(i));
        }
        app.receivers.clear();

        // If the app is undergoing backup, tell the backup manager about it
        if (mBackupTarget != null && app.pid == mBackupTarget.app.pid) {
            if (DEBUG_BACKUP || DEBUG_CLEANUP) Slog.d(TAG_CLEANUP, "App "
                    + mBackupTarget.appInfo + " died during backup");
            try {
                IBackupManager bm = IBackupManager.Stub.asInterface(
                        ServiceManager.getService(Context.BACKUP_SERVICE));
                bm.agentDisconnected(app.info.packageName);
            } catch (RemoteException e) {
                // can't happen; backup manager is local
            }
        }

        for (int i = mPendingProcessChanges.size() - 1; i >= 0; i--) {
            ProcessChangeItem item = mPendingProcessChanges.get(i);
            if (item.pid == app.pid) {
                mPendingProcessChanges.remove(i);
                mAvailProcessChanges.add(item);
            }
        }
        mUiHandler.obtainMessage(DISPATCH_PROCESS_DIED_UI_MSG, app.pid, app.info.uid,
                null).sendToTarget();

        // If the caller is restarting this app, then leave it in its
        // current lists and let the caller take care of it.
        if (restarting) {
            return false;
        }

        if (!app.persistent || app.isolated) {
            if (DEBUG_PROCESSES || DEBUG_CLEANUP) Slog.v(TAG_CLEANUP,
                    "Removing non-persistent process during cleanup: " + app);
            if (!replacingPid) {
                removeProcessNameLocked(app.processName, app.uid);
            }
            if (mHeavyWeightProcess == app) {
                mHandler.sendMessage(mHandler.obtainMessage(CANCEL_HEAVY_NOTIFICATION_MSG,
                        mHeavyWeightProcess.userId, 0));
                mHeavyWeightProcess = null;
            }
        } else if (!app.removed) {
            // This app is persistent, so we need to keep its record around.
            // If it is not already on the pending app list, add it there
            // and start a new process for it.
            if (mPersistentStartingProcesses.indexOf(app) < 0) {
                mPersistentStartingProcesses.add(app);
                restart = true;
            }
        }
        if ((DEBUG_PROCESSES || DEBUG_CLEANUP) && mProcessesOnHold.contains(app)) Slog.v(
                TAG_CLEANUP, "Clean-up removing on hold: " + app);
        mProcessesOnHold.remove(app);

        if (app == mHomeProcess) {
            mHomeProcess = null;
        }
        if (app == mPreviousProcess) {
            mPreviousProcess = null;
        }

        if (restart && !app.isolated) {
            // We have components that still need to be running in the
            // process, so re-launch it.
            if (index < 0) {
                ProcessList.remove(app.pid);
            }
            addProcessNameLocked(app);
            startProcessLocked(app, "restart", app.processName);
            return true;
        } else if (app.pid > 0 && app.pid != MY_PID) {
            // Goodbye!
            boolean removed;
            synchronized (mPidsSelfLocked) {
                mPidsSelfLocked.remove(app.pid);
                mHandler.removeMessages(PROC_START_TIMEOUT_MSG, app);
            }
            mBatteryStatsService.noteProcessFinish(app.processName, app.info.uid);
            if (app.isolated) {
                mBatteryStatsService.removeIsolatedUid(app.uid, app.info.uid);
            }
            app.setPid(0);
        }
        return false;
    }

    boolean checkAppInLaunchingProvidersLocked(ProcessRecord app) {
        for (int i = mLaunchingProviders.size() - 1; i >= 0; i--) {
            ContentProviderRecord cpr = mLaunchingProviders.get(i);
            if (cpr.launchingApp == app) {
                return true;
            }
        }
        return false;
    }

    boolean cleanupAppInLaunchingProvidersLocked(ProcessRecord app, boolean alwaysBad) {
        // Look through the content providers we are waiting to have launched,
        // and if any run in this process then either schedule a restart of
        // the process or kill the client waiting for it if this process has
        // gone bad.
        boolean restart = false;
        for (int i = mLaunchingProviders.size() - 1; i >= 0; i--) {
            ContentProviderRecord cpr = mLaunchingProviders.get(i);
            if (cpr.launchingApp == app) {
                if (!alwaysBad && !app.bad && cpr.hasConnectionOrHandle()) {
                    restart = true;
                } else {
                    removeDyingProviderLocked(app, cpr, true);
                }
            }
        }
        return restart;
    }

    // =========================================================
    // SERVICES
    // =========================================================

    @Override
    public List<ActivityManager.RunningServiceInfo> getServices(int maxNum,
            int flags) {
        enforceNotIsolatedCaller("getServices");
        synchronized (this) {
            return mServices.getRunningServiceInfoLocked(maxNum, flags);
        }
    }

    @Override
    public PendingIntent getRunningServiceControlPanel(ComponentName name) {
        enforceNotIsolatedCaller("getRunningServiceControlPanel");
        synchronized (this) {
            return mServices.getRunningServiceControlPanelLocked(name);
        }
    }

    @Override
    public ComponentName startService(IApplicationThread caller, Intent service,
            String resolvedType, String callingPackage, int userId)
            throws TransactionTooLargeException {
        enforceNotIsolatedCaller("startService");
        // Refuse possible leaked file descriptors
        if (service != null && service.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        if (callingPackage == null) {
            throw new IllegalArgumentException("callingPackage cannot be null");
        }

        if (DEBUG_SERVICE) Slog.v(TAG_SERVICE,
                "startService: " + service + " type=" + resolvedType);
        synchronized(this) {
            final int callingPid = Binder.getCallingPid();
            final int callingUid = Binder.getCallingUid();
            final long origId = Binder.clearCallingIdentity();
            ComponentName res = mServices.startServiceLocked(caller, service,
                    resolvedType, callingPid, callingUid, callingPackage, userId);
            Binder.restoreCallingIdentity(origId);
            return res;
        }
    }

    ComponentName startServiceInPackage(int uid, Intent service, String resolvedType,
            String callingPackage, int userId)
            throws TransactionTooLargeException {
        synchronized(this) {
            if (DEBUG_SERVICE) Slog.v(TAG_SERVICE,
                    "startServiceInPackage: " + service + " type=" + resolvedType);
            final long origId = Binder.clearCallingIdentity();
            ComponentName res = mServices.startServiceLocked(null, service,
                    resolvedType, -1, uid, callingPackage, userId);
            Binder.restoreCallingIdentity(origId);
            return res;
        }
    }

    @Override
    public int stopService(IApplicationThread caller, Intent service,
            String resolvedType, int userId) {
        enforceNotIsolatedCaller("stopService");
        // Refuse possible leaked file descriptors
        if (service != null && service.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        synchronized(this) {
            return mServices.stopServiceLocked(caller, service, resolvedType, userId);
        }
    }

    @Override
    public IBinder peekService(Intent service, String resolvedType, String callingPackage) {
        enforceNotIsolatedCaller("peekService");
        // Refuse possible leaked file descriptors
        if (service != null && service.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        if (callingPackage == null) {
            throw new IllegalArgumentException("callingPackage cannot be null");
        }

        synchronized(this) {
            return mServices.peekServiceLocked(service, resolvedType, callingPackage);
        }
    }

    @Override
    public boolean stopServiceToken(ComponentName className, IBinder token,
            int startId) {
        synchronized(this) {
            return mServices.stopServiceTokenLocked(className, token, startId);
        }
    }

    @Override
    public void setServiceForeground(ComponentName className, IBinder token,
            int id, Notification notification, int flags) {
        synchronized(this) {
            mServices.setServiceForegroundLocked(className, token, id, notification, flags);
        }
    }

    @Override
    public int handleIncomingUser(int callingPid, int callingUid, int userId, boolean allowAll,
            boolean requireFull, String name, String callerPackage) {
        return mUserController.handleIncomingUser(callingPid, callingUid, userId, allowAll,
                requireFull ? ALLOW_FULL_ONLY : ALLOW_NON_FULL, name, callerPackage);
    }

    boolean isSingleton(String componentProcessName, ApplicationInfo aInfo,
            String className, int flags) {
        boolean result = false;
        // For apps that don't have pre-defined UIDs, check for permission
        if (UserHandle.getAppId(aInfo.uid) >= Process.FIRST_APPLICATION_UID) {
            if ((flags & ServiceInfo.FLAG_SINGLE_USER) != 0) {
                if (ActivityManager.checkUidPermission(
                        INTERACT_ACROSS_USERS,
                        aInfo.uid) != PackageManager.PERMISSION_GRANTED) {
                    ComponentName comp = new ComponentName(aInfo.packageName, className);
                    String msg = "Permission Denial: Component " + comp.flattenToShortString()
                            + " requests FLAG_SINGLE_USER, but app does not hold "
                            + INTERACT_ACROSS_USERS;
                    Slog.w(TAG, msg);
                    throw new SecurityException(msg);
                }
                // Permission passed
                result = true;
            }
        } else if ("system".equals(componentProcessName)) {
            result = true;
        } else if ((flags & ServiceInfo.FLAG_SINGLE_USER) != 0) {
            // Phone app and persistent apps are allowed to export singleuser providers.
            result = UserHandle.isSameApp(aInfo.uid, Process.PHONE_UID)
                    || (aInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0;
        }
        if (DEBUG_MU) Slog.v(TAG_MU,
                "isSingleton(" + componentProcessName + ", " + aInfo + ", " + className + ", 0x"
                + Integer.toHexString(flags) + ") = " + result);
        return result;
    }

    /**
     * Checks to see if the caller is in the same app as the singleton
     * component, or the component is in a special app. It allows special apps
     * to export singleton components but prevents exporting singleton
     * components for regular apps.
     */
    boolean isValidSingletonCall(int callingUid, int componentUid) {
        int componentAppId = UserHandle.getAppId(componentUid);
        return UserHandle.isSameApp(callingUid, componentUid)
                || componentAppId == Process.SYSTEM_UID
                || componentAppId == Process.PHONE_UID
                || ActivityManager.checkUidPermission(INTERACT_ACROSS_USERS_FULL, componentUid)
                        == PackageManager.PERMISSION_GRANTED;
    }

    public int bindService(IApplicationThread caller, IBinder token, Intent service,
            String resolvedType, IServiceConnection connection, int flags, String callingPackage,
            int userId) throws TransactionTooLargeException {
        enforceNotIsolatedCaller("bindService");

        // Refuse possible leaked file descriptors
        if (service != null && service.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        if (callingPackage == null) {
            throw new IllegalArgumentException("callingPackage cannot be null");
        }

        synchronized(this) {
            return mServices.bindServiceLocked(caller, token, service,
                    resolvedType, connection, flags, callingPackage, userId);
        }
    }

    public boolean unbindService(IServiceConnection connection) {
        synchronized (this) {
            return mServices.unbindServiceLocked(connection);
        }
    }

    public void publishService(IBinder token, Intent intent, IBinder service) {
        // Refuse possible leaked file descriptors
        if (intent != null && intent.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        synchronized(this) {
            if (!(token instanceof ServiceRecord)) {
                throw new IllegalArgumentException("Invalid service token");
            }
            mServices.publishServiceLocked((ServiceRecord)token, intent, service);
        }
    }

    public void unbindFinished(IBinder token, Intent intent, boolean doRebind) {
        // Refuse possible leaked file descriptors
        if (intent != null && intent.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        synchronized(this) {
            mServices.unbindFinishedLocked((ServiceRecord)token, intent, doRebind);
        }
    }

    public void serviceDoneExecuting(IBinder token, int type, int startId, int res) {
        synchronized(this) {
            if (!(token instanceof ServiceRecord)) {
                Slog.e(TAG, "serviceDoneExecuting: Invalid service token=" + token);
                throw new IllegalArgumentException("Invalid service token");
            }
            mServices.serviceDoneExecutingLocked((ServiceRecord)token, type, startId, res);
        }
    }

    // =========================================================
    // BACKUP AND RESTORE
    // =========================================================

    // Cause the target app to be launched if necessary and its backup agent
    // instantiated.  The backup agent will invoke backupAgentCreated() on the
    // activity manager to announce its creation.
    public boolean bindBackupAgent(String packageName, int backupMode, int userId) {
        if (DEBUG_BACKUP) Slog.v(TAG, "bindBackupAgent: app=" + packageName + " mode=" + backupMode);
        enforceCallingPermission("android.permission.CONFIRM_FULL_BACKUP", "bindBackupAgent");

        IPackageManager pm = AppGlobals.getPackageManager();
        ApplicationInfo app = null;
        try {
            app = pm.getApplicationInfo(packageName, 0, userId);
        } catch (RemoteException e) {
            // can't happen; package manager is process-local
        }
        if (app == null) {
            Slog.w(TAG, "Unable to bind backup agent for " + packageName);
            return false;
        }

        synchronized(this) {
            // !!! TODO: currently no check here that we're already bound
            BatteryStatsImpl.Uid.Pkg.Serv ss = null;
            BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
            synchronized (stats) {
                ss = stats.getServiceStatsLocked(app.uid, app.packageName, app.name);
            }

            // Backup agent is now in use, its package can't be stopped.
            try {
                AppGlobals.getPackageManager().setPackageStoppedState(
                        app.packageName, false, UserHandle.getUserId(app.uid));
            } catch (RemoteException e) {
            } catch (IllegalArgumentException e) {
                Slog.w(TAG, "Failed trying to unstop package "
                        + app.packageName + ": " + e);
            }

            BackupRecord r = new BackupRecord(ss, app, backupMode);
            ComponentName hostingName = (backupMode == IApplicationThread.BACKUP_MODE_INCREMENTAL)
                    ? new ComponentName(app.packageName, app.backupAgentName)
                    : new ComponentName("android", "FullBackupAgent");
            // startProcessLocked() returns existing proc's record if it's already running
            ProcessRecord proc = startProcessLocked(app.processName, app,
                    false, 0, "backup", hostingName, false, false, false);
            if (proc == null) {
                Slog.e(TAG, "Unable to start backup agent process " + r);
                return false;
            }

            // If the app is a regular app (uid >= 10000) and not the system server or phone
            // process, etc, then mark it as being in full backup so that certain calls to the
            // process can be blocked. This is not reset to false anywhere because we kill the
            // process after the full backup is done and the ProcessRecord will vaporize anyway.
            if (UserHandle.isApp(app.uid) && backupMode == IApplicationThread.BACKUP_MODE_FULL) {
                proc.inFullBackup = true;
            }
            r.app = proc;
            mBackupTarget = r;
            mBackupAppName = app.packageName;

            // Try not to kill the process during backup
            updateOomAdjLocked(proc);

            // If the process is already attached, schedule the creation of the backup agent now.
            // If it is not yet live, this will be done when it attaches to the framework.
            if (proc.thread != null) {
                if (DEBUG_BACKUP) Slog.v(TAG_BACKUP, "Agent proc already running: " + proc);
                try {
                    proc.thread.scheduleCreateBackupAgent(app,
                            compatibilityInfoForPackageLocked(app), backupMode);
                } catch (RemoteException e) {
                    // Will time out on the backup manager side
                }
            } else {
                if (DEBUG_BACKUP) Slog.v(TAG_BACKUP, "Agent proc not running, waiting for attach");
            }
            // Invariants: at this point, the target app process exists and the application
            // is either already running or in the process of coming up.  mBackupTarget and
            // mBackupAppName describe the app, so that when it binds back to the AM we
            // know that it's scheduled for a backup-agent operation.
        }

        return true;
    }

    @Override
    public void clearPendingBackup() {
        if (DEBUG_BACKUP) Slog.v(TAG_BACKUP, "clearPendingBackup");
        enforceCallingPermission("android.permission.BACKUP", "clearPendingBackup");

        synchronized (this) {
            mBackupTarget = null;
            mBackupAppName = null;
        }
    }

    // A backup agent has just come up
    public void backupAgentCreated(String agentPackageName, IBinder agent) {
        if (DEBUG_BACKUP) Slog.v(TAG_BACKUP, "backupAgentCreated: " + agentPackageName
                + " = " + agent);

        synchronized(this) {
            if (!agentPackageName.equals(mBackupAppName)) {
                Slog.e(TAG, "Backup agent created for " + agentPackageName + " but not requested!");
                return;
            }
        }

        long oldIdent = Binder.clearCallingIdentity();
        try {
            IBackupManager bm = IBackupManager.Stub.asInterface(
                    ServiceManager.getService(Context.BACKUP_SERVICE));
            bm.agentConnected(agentPackageName, agent);
        } catch (RemoteException e) {
            // can't happen; the backup manager service is local
        } catch (Exception e) {
            Slog.w(TAG, "Exception trying to deliver BackupAgent binding: ");
            e.printStackTrace();
        } finally {
            Binder.restoreCallingIdentity(oldIdent);
        }
    }

    // done with this agent
    public void unbindBackupAgent(ApplicationInfo appInfo) {
        if (DEBUG_BACKUP) Slog.v(TAG_BACKUP, "unbindBackupAgent: " + appInfo);
        if (appInfo == null) {
            Slog.w(TAG, "unbind backup agent for null app");
            return;
        }

        synchronized(this) {
            try {
                if (mBackupAppName == null) {
                    Slog.w(TAG, "Unbinding backup agent with no active backup");
                    return;
                }

                if (!mBackupAppName.equals(appInfo.packageName)) {
                    Slog.e(TAG, "Unbind of " + appInfo + " but is not the current backup target");
                    return;
                }

                // Not backing this app up any more; reset its OOM adjustment
                final ProcessRecord proc = mBackupTarget.app;
                updateOomAdjLocked(proc);

                // If the app crashed during backup, 'thread' will be null here
                if (proc.thread != null) {
                    try {
                        proc.thread.scheduleDestroyBackupAgent(appInfo,
                                compatibilityInfoForPackageLocked(appInfo));
                    } catch (Exception e) {
                        Slog.e(TAG, "Exception when unbinding backup agent:");
                        e.printStackTrace();
                    }
                }
            } finally {
                mBackupTarget = null;
                mBackupAppName = null;
            }
        }
    }
    // =========================================================
    // BROADCASTS
    // =========================================================

    boolean isPendingBroadcastProcessLocked(int pid) {
        return mFgBroadcastQueue.isPendingBroadcastProcessLocked(pid)
                || mBgBroadcastQueue.isPendingBroadcastProcessLocked(pid);
    }

    void skipPendingBroadcastLocked(int pid) {
            Slog.w(TAG, "Unattached app died before broadcast acknowledged, skipping");
            for (BroadcastQueue queue : mBroadcastQueues) {
                queue.skipPendingBroadcastLocked(pid);
            }
    }

    // The app just attached; send any pending broadcasts that it should receive
    boolean sendPendingBroadcastsLocked(ProcessRecord app) {
        boolean didSomething = false;
        for (BroadcastQueue queue : mBroadcastQueues) {
            didSomething |= queue.sendPendingBroadcastsLocked(app);
        }
        return didSomething;
    }

    public Intent registerReceiver(IApplicationThread caller, String callerPackage,
            IIntentReceiver receiver, IntentFilter filter, String permission, int userId) {
        enforceNotIsolatedCaller("registerReceiver");
        ArrayList<Intent> stickyIntents = null;
        ProcessRecord callerApp = null;
        int callingUid;
        int callingPid;
        synchronized(this) {
            if (caller != null) {
                callerApp = getRecordForAppLocked(caller);
                if (callerApp == null) {
                    throw new SecurityException(
                            "Unable to find app for caller " + caller
                            + " (pid=" + Binder.getCallingPid()
                            + ") when registering receiver " + receiver);
                }
                if (callerApp.info.uid != Process.SYSTEM_UID &&
                        !callerApp.pkgList.containsKey(callerPackage) &&
                        !"android".equals(callerPackage)) {
                    throw new SecurityException("Given caller package " + callerPackage
                            + " is not running in process " + callerApp);
                }
                callingUid = callerApp.info.uid;
                callingPid = callerApp.pid;
            } else {
                callerPackage = null;
                callingUid = Binder.getCallingUid();
                callingPid = Binder.getCallingPid();
            }

            userId = mUserController.handleIncomingUser(callingPid, callingUid, userId, true,
                    ALLOW_FULL_ONLY, "registerReceiver", callerPackage);

            Iterator<String> actions = filter.actionsIterator();
            if (actions == null) {
                ArrayList<String> noAction = new ArrayList<String>(1);
                noAction.add(null);
                actions = noAction.iterator();
            }

            // Collect stickies of users
            int[] userIds = { UserHandle.USER_ALL, UserHandle.getUserId(callingUid) };
            while (actions.hasNext()) {
                String action = actions.next();
                for (int id : userIds) {
                    ArrayMap<String, ArrayList<Intent>> stickies = mStickyBroadcasts.get(id);
                    if (stickies != null) {
                        ArrayList<Intent> intents = stickies.get(action);
                        if (intents != null) {
                            if (stickyIntents == null) {
                                stickyIntents = new ArrayList<Intent>();
                            }
                            stickyIntents.addAll(intents);
                        }
                    }
                }
            }
        }

        ArrayList<Intent> allSticky = null;
        if (stickyIntents != null) {
            final ContentResolver resolver = mContext.getContentResolver();
            // Look for any matching sticky broadcasts...
            for (int i = 0, N = stickyIntents.size(); i < N; i++) {
                Intent intent = stickyIntents.get(i);
                // If intent has scheme "content", it will need to acccess
                // provider that needs to lock mProviderMap in ActivityThread
                // and also it may need to wait application response, so we
                // cannot lock ActivityManagerService here.
                if (filter.match(resolver, intent, true, TAG) >= 0) {
                    if (allSticky == null) {
                        allSticky = new ArrayList<Intent>();
                    }
                    allSticky.add(intent);
                }
            }
        }

        // The first sticky in the list is returned directly back to the client.
        Intent sticky = allSticky != null ? allSticky.get(0) : null;
        if (DEBUG_BROADCAST) Slog.v(TAG_BROADCAST, "Register receiver " + filter + ": " + sticky);
        if (receiver == null) {
            return sticky;
        }

        synchronized (this) {
            if (callerApp != null && (callerApp.thread == null
                    || callerApp.thread.asBinder() != caller.asBinder())) {
                // Original caller already died
                return null;
            }
            ReceiverList rl = mRegisteredReceivers.get(receiver.asBinder());
            if (rl == null) {
                rl = new ReceiverList(this, callerApp, callingPid, callingUid,
                        userId, receiver);
                if (rl.app != null) {
                    rl.app.receivers.add(rl);
                } else {
                    try {
                        receiver.asBinder().linkToDeath(rl, 0);
                    } catch (RemoteException e) {
                        return sticky;
                    }
                    rl.linkedToDeath = true;
                }
                mRegisteredReceivers.put(receiver.asBinder(), rl);
            } else if (rl.uid != callingUid) {
                throw new IllegalArgumentException(
                        "Receiver requested to register for uid " + callingUid
                        + " was previously registered for uid " + rl.uid);
            } else if (rl.pid != callingPid) {
                throw new IllegalArgumentException(
                        "Receiver requested to register for pid " + callingPid
                        + " was previously registered for pid " + rl.pid);
            } else if (rl.userId != userId) {
                throw new IllegalArgumentException(
                        "Receiver requested to register for user " + userId
                        + " was previously registered for user " + rl.userId);
            }
            BroadcastFilter bf = new BroadcastFilter(filter, rl, callerPackage,
                    permission, callingUid, userId);
            rl.add(bf);
            if (!bf.debugCheck()) {
                Slog.w(TAG, "==> For Dynamic broadcast");
            }
            mReceiverResolver.addFilter(bf);

            // Enqueue broadcasts for all existing stickies that match
            // this filter.
            if (allSticky != null) {
                ArrayList receivers = new ArrayList();
                receivers.add(bf);

                final int stickyCount = allSticky.size();
                for (int i = 0; i < stickyCount; i++) {
                    Intent intent = allSticky.get(i);
                    BroadcastQueue queue = broadcastQueueForIntent(intent);
                    BroadcastRecord r = new BroadcastRecord(queue, intent, null,
                            null, -1, -1, null, null, AppOpsManager.OP_NONE, null, receivers,
                            null, 0, null, null, false, true, true, -1);
                    queue.enqueueParallelBroadcastLocked(r);
                    queue.scheduleBroadcastsLocked();
                }
            }

            return sticky;
        }
    }

    public void unregisterReceiver(IIntentReceiver receiver) {
        if (DEBUG_BROADCAST) Slog.v(TAG_BROADCAST, "Unregister receiver: " + receiver);

        final long origId = Binder.clearCallingIdentity();
        try {
            boolean doTrim = false;

            synchronized(this) {
                ReceiverList rl = mRegisteredReceivers.get(receiver.asBinder());
                if (rl != null) {
                    final BroadcastRecord r = rl.curBroadcast;
                    if (r != null && r == r.queue.getMatchingOrderedReceiver(r)) {
                        final boolean doNext = r.queue.finishReceiverLocked(
                                r, r.resultCode, r.resultData, r.resultExtras,
                                r.resultAbort, false);
                        if (doNext) {
                            doTrim = true;
                            r.queue.processNextBroadcast(false);
                        }
                    }

                    if (rl.app != null) {
                        rl.app.receivers.remove(rl);
                    }
                    removeReceiverLocked(rl);
                    if (rl.linkedToDeath) {
                        rl.linkedToDeath = false;
                        rl.receiver.asBinder().unlinkToDeath(rl, 0);
                    }
                }
            }

            // If we actually concluded any broadcasts, we might now be able
            // to trim the recipients' apps from our working set
            if (doTrim) {
                trimApplications();
                return;
            }

        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    void removeReceiverLocked(ReceiverList rl) {
        mRegisteredReceivers.remove(rl.receiver.asBinder());
        for (int i = rl.size() - 1; i >= 0; i--) {
            mReceiverResolver.removeFilter(rl.get(i));
        }
    }

    private final void sendPackageBroadcastLocked(int cmd, String[] packages, int userId) {
        for (int i = mLruProcesses.size() - 1 ; i >= 0 ; i--) {
            ProcessRecord r = mLruProcesses.get(i);
            if (r.thread != null && (userId == UserHandle.USER_ALL || r.userId == userId)) {
                try {
                    r.thread.dispatchPackageBroadcast(cmd, packages);
                } catch (RemoteException ex) {
                }
            }
        }
    }

    private List<ResolveInfo> collectReceiverComponents(Intent intent, String resolvedType,
            int callingUid, int[] users) {
        // TODO: come back and remove this assumption to triage all broadcasts
        int pmFlags = STOCK_PM_FLAGS | MATCH_DEBUG_TRIAGED_MISSING;

        List<ResolveInfo> receivers = null;
        try {
            HashSet<ComponentName> singleUserReceivers = null;
            boolean scannedFirstReceivers = false;
            for (int user : users) {
                // Skip users that have Shell restrictions, with exception of always permitted
                // Shell broadcasts
                if (callingUid == Process.SHELL_UID
                        && mUserController.hasUserRestriction(
                                UserManager.DISALLOW_DEBUGGING_FEATURES, user)
                        && !isPermittedShellBroadcast(intent)) {
                    continue;
                }
                List<ResolveInfo> newReceivers = AppGlobals.getPackageManager()
                        .queryIntentReceivers(intent, resolvedType, pmFlags, user).getList();
                if (user != UserHandle.USER_SYSTEM && newReceivers != null) {
                    // If this is not the system user, we need to check for
                    // any receivers that should be filtered out.
                    for (int i=0; i<newReceivers.size(); i++) {
                        ResolveInfo ri = newReceivers.get(i);
                        if ((ri.activityInfo.flags&ActivityInfo.FLAG_SYSTEM_USER_ONLY) != 0) {
                            newReceivers.remove(i);
                            i--;
                        }
                    }
                }
                if (newReceivers != null && newReceivers.size() == 0) {
                    newReceivers = null;
                }
                if (receivers == null) {
                    receivers = newReceivers;
                } else if (newReceivers != null) {
                    // We need to concatenate the additional receivers
                    // found with what we have do far.  This would be easy,
                    // but we also need to de-dup any receivers that are
                    // singleUser.
                    if (!scannedFirstReceivers) {
                        // Collect any single user receivers we had already retrieved.
                        scannedFirstReceivers = true;
                        for (int i=0; i<receivers.size(); i++) {
                            ResolveInfo ri = receivers.get(i);
                            if ((ri.activityInfo.flags&ActivityInfo.FLAG_SINGLE_USER) != 0) {
                                ComponentName cn = new ComponentName(
                                        ri.activityInfo.packageName, ri.activityInfo.name);
                                if (singleUserReceivers == null) {
                                    singleUserReceivers = new HashSet<ComponentName>();
                                }
                                singleUserReceivers.add(cn);
                            }
                        }
                    }
                    // Add the new results to the existing results, tracking
                    // and de-dupping single user receivers.
                    for (int i=0; i<newReceivers.size(); i++) {
                        ResolveInfo ri = newReceivers.get(i);
                        if ((ri.activityInfo.flags&ActivityInfo.FLAG_SINGLE_USER) != 0) {
                            ComponentName cn = new ComponentName(
                                    ri.activityInfo.packageName, ri.activityInfo.name);
                            if (singleUserReceivers == null) {
                                singleUserReceivers = new HashSet<ComponentName>();
                            }
                            if (!singleUserReceivers.contains(cn)) {
                                singleUserReceivers.add(cn);
                                receivers.add(ri);
                            }
                        } else {
                            receivers.add(ri);
                        }
                    }
                }
            }
        } catch (RemoteException ex) {
            // pm is in same process, this will never happen.
        }
        return receivers;
    }

    private boolean isPermittedShellBroadcast(Intent intent) {
        // remote bugreport should always be allowed to be taken
        return INTENT_REMOTE_BUGREPORT_FINISHED.equals(intent.getAction());
    }

    private void checkBroadcastFromSystem(Intent intent, ProcessRecord callerApp,
            String callerPackage, int callingUid, boolean isProtectedBroadcast, List receivers) {
        final String action = intent.getAction();
        if (isProtectedBroadcast
                || Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                || Intent.ACTION_DISMISS_KEYBOARD_SHORTCUTS.equals(action)
                || Intent.ACTION_MEDIA_BUTTON.equals(action)
                || Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action)
                || Intent.ACTION_SHOW_KEYBOARD_SHORTCUTS.equals(action)
                || Intent.ACTION_MASTER_CLEAR.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || LocationManager.HIGH_POWER_REQUEST_CHANGE_ACTION.equals(action)
                || TelephonyIntents.ACTION_REQUEST_OMADM_CONFIGURATION_UPDATE.equals(action)
                || SuggestionSpan.ACTION_SUGGESTION_PICKED.equals(action)) {
            // Broadcast is either protected, or it's a public action that
            // we've relaxed, so it's fine for system internals to send.
            return;
        }

        // This broadcast may be a problem...  but there are often system components that
        // want to send an internal broadcast to themselves, which is annoying to have to
        // explicitly list each action as a protected broadcast, so we will check for that
        // one safe case and allow it: an explicit broadcast, only being received by something
        // that has protected itself.
        if (receivers != null && receivers.size() > 0
                && (intent.getPackage() != null || intent.getComponent() != null)) {
            boolean allProtected = true;
            for (int i = receivers.size()-1; i >= 0; i--) {
                Object target = receivers.get(i);
                if (target instanceof ResolveInfo) {
                    ResolveInfo ri = (ResolveInfo)target;
                    if (ri.activityInfo.exported && ri.activityInfo.permission == null) {
                        allProtected = false;
                        break;
                    }
                } else {
                    BroadcastFilter bf = (BroadcastFilter)target;
                    if (bf.requiredPermission == null) {
                        allProtected = false;
                        break;
                    }
                }
            }
            if (allProtected) {
                // All safe!
                return;
            }
        }

        // The vast majority of broadcasts sent from system internals
        // should be protected to avoid security holes, so yell loudly
        // to ensure we examine these cases.
        if (callerApp != null) {
            Log.wtf(TAG, "Sending non-protected broadcast " + action
                            + " from system " + callerApp.toShortString() + " pkg " + callerPackage,
                    new Throwable());
        } else {
            Log.wtf(TAG, "Sending non-protected broadcast " + action
                            + " from system uid " + UserHandle.formatUid(callingUid)
                            + " pkg " + callerPackage,
                    new Throwable());
        }
    }

    final int broadcastIntentLocked(ProcessRecord callerApp,
            String callerPackage, Intent intent, String resolvedType,
            IIntentReceiver resultTo, int resultCode, String resultData,
            Bundle resultExtras, String[] requiredPermissions, int appOp, Bundle bOptions,
            boolean ordered, boolean sticky, int callingPid, int callingUid, int userId) {
        intent = new Intent(intent);

        // By default broadcasts do not go to stopped apps.
        intent.addFlags(Intent.FLAG_EXCLUDE_STOPPED_PACKAGES);

        // If we have not finished booting, don't allow this to launch new processes.
        if (!mProcessesReady && (intent.getFlags()&Intent.FLAG_RECEIVER_BOOT_UPGRADE) == 0) {
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        }

        if (DEBUG_BROADCAST_LIGHT) Slog.v(TAG_BROADCAST,
                (sticky ? "Broadcast sticky: ": "Broadcast: ") + intent
                + " ordered=" + ordered + " userid=" + userId);
        if ((resultTo != null) && !ordered) {
            Slog.w(TAG, "Broadcast " + intent + " not ordered but result callback requested!");
        }

        userId = mUserController.handleIncomingUser(callingPid, callingUid, userId, true,
                ALLOW_NON_FULL, "broadcast", callerPackage);

        // Make sure that the user who is receiving this broadcast is running.
        // If not, we will just skip it. Make an exception for shutdown broadcasts
        // and upgrade steps.

        if (userId != UserHandle.USER_ALL && !mUserController.isUserRunningLocked(userId, 0)) {
            if ((callingUid != Process.SYSTEM_UID
                    || (intent.getFlags() & Intent.FLAG_RECEIVER_BOOT_UPGRADE) == 0)
                    && !Intent.ACTION_SHUTDOWN.equals(intent.getAction())) {
                Slog.w(TAG, "Skipping broadcast of " + intent
                        + ": user " + userId + " is stopped");
                return ActivityManager.BROADCAST_FAILED_USER_STOPPED;
            }
        }

        BroadcastOptions brOptions = null;
        if (bOptions != null) {
            brOptions = new BroadcastOptions(bOptions);
            if (brOptions.getTemporaryAppWhitelistDuration() > 0) {
                // See if the caller is allowed to do this.  Note we are checking against
                // the actual real caller (not whoever provided the operation as say a
                // PendingIntent), because that who is actually supplied the arguments.
                if (checkComponentPermission(
                        android.Manifest.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST,
                        Binder.getCallingPid(), Binder.getCallingUid(), -1, true)
                        != PackageManager.PERMISSION_GRANTED) {
                    String msg = "Permission Denial: " + intent.getAction()
                            + " broadcast from " + callerPackage + " (pid=" + callingPid
                            + ", uid=" + callingUid + ")"
                            + " requires "
                            + android.Manifest.permission.CHANGE_DEVICE_IDLE_TEMP_WHITELIST;
                    Slog.w(TAG, msg);
                    throw new SecurityException(msg);
                }
            }
        }

        // Verify that protected broadcasts are only being sent by system code,
        // and that system code is only sending protected broadcasts.
        final String action = intent.getAction();
        final boolean isProtectedBroadcast;
        try {
            isProtectedBroadcast = AppGlobals.getPackageManager().isProtectedBroadcast(action);
        } catch (RemoteException e) {
            Slog.w(TAG, "Remote exception", e);
            return ActivityManager.BROADCAST_SUCCESS;
        }

        final boolean isCallerSystem;
        switch (UserHandle.getAppId(callingUid)) {
            case Process.ROOT_UID:
            case Process.SYSTEM_UID:
            case Process.PHONE_UID:
            case Process.BLUETOOTH_UID:
            case Process.NFC_UID:
                isCallerSystem = true;
                break;
            default:
                isCallerSystem = (callerApp != null) && callerApp.persistent;
                break;
        }

        // First line security check before anything else: stop non-system apps from
        // sending protected broadcasts.
        if (!isCallerSystem) {
            if (isProtectedBroadcast) {
                String msg = "Permission Denial: not allowed to send broadcast "
                        + action + " from pid="
                        + callingPid + ", uid=" + callingUid;
                Slog.w(TAG, msg);
                throw new SecurityException(msg);

            } else if (AppWidgetManager.ACTION_APPWIDGET_CONFIGURE.equals(action)
                    || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
                // Special case for compatibility: we don't want apps to send this,
                // but historically it has not been protected and apps may be using it
                // to poke their own app widget.  So, instead of making it protected,
                // just limit it to the caller.
                if (callerPackage == null) {
                    String msg = "Permission Denial: not allowed to send broadcast "
                            + action + " from unknown caller.";
                    Slog.w(TAG, msg);
                    throw new SecurityException(msg);
                } else if (intent.getComponent() != null) {
                    // They are good enough to send to an explicit component...  verify
                    // it is being sent to the calling app.
                    if (!intent.getComponent().getPackageName().equals(
                            callerPackage)) {
                        String msg = "Permission Denial: not allowed to send broadcast "
                                + action + " to "
                                + intent.getComponent().getPackageName() + " from "
                                + callerPackage;
                        Slog.w(TAG, msg);
                        throw new SecurityException(msg);
                    }
                } else {
                    // Limit broadcast to their own package.
                    intent.setPackage(callerPackage);
                }
            }
        }

        if (action != null) {
            switch (action) {
                case Intent.ACTION_UID_REMOVED:
                case Intent.ACTION_PACKAGE_REMOVED:
                case Intent.ACTION_PACKAGE_CHANGED:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
                case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                case Intent.ACTION_PACKAGES_SUSPENDED:
                case Intent.ACTION_PACKAGES_UNSUSPENDED:
                    // Handle special intents: if this broadcast is from the package
                    // manager about a package being removed, we need to remove all of
                    // its activities from the history stack.
                    if (checkComponentPermission(
                            android.Manifest.permission.BROADCAST_PACKAGE_REMOVED,
                            callingPid, callingUid, -1, true)
                            != PackageManager.PERMISSION_GRANTED) {
                        String msg = "Permission Denial: " + intent.getAction()
                                + " broadcast from " + callerPackage + " (pid=" + callingPid
                                + ", uid=" + callingUid + ")"
                                + " requires "
                                + android.Manifest.permission.BROADCAST_PACKAGE_REMOVED;
                        Slog.w(TAG, msg);
                        throw new SecurityException(msg);
                    }
                    switch (action) {
                        case Intent.ACTION_UID_REMOVED:
                            final Bundle intentExtras = intent.getExtras();
                            final int uid = intentExtras != null
                                    ? intentExtras.getInt(Intent.EXTRA_UID) : -1;
                            if (uid >= 0) {
                                mBatteryStatsService.removeUid(uid);
                                mAppOpsService.uidRemoved(uid);
                            }
                            break;
                        case Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE:
                            // If resources are unavailable just force stop all those packages
                            // and flush the attribute cache as well.
                            String list[] =
                                    intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                            if (list != null && list.length > 0) {
                                for (int i = 0; i < list.length; i++) {
                                    forceStopPackageLocked(list[i], -1, false, true, true,
                                            false, false, userId, "storage unmount");
                                }
                                mRecentTasks.cleanupLocked(UserHandle.USER_ALL);
                                sendPackageBroadcastLocked(
                                        IApplicationThread.EXTERNAL_STORAGE_UNAVAILABLE, list,
                                        userId);
                            }
                            break;
                        case Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE:
                            mRecentTasks.cleanupLocked(UserHandle.USER_ALL);
                            break;
                        case Intent.ACTION_PACKAGE_REMOVED:
                        case Intent.ACTION_PACKAGE_CHANGED:
                            Uri data = intent.getData();
                            String ssp;
                            if (data != null && (ssp=data.getSchemeSpecificPart()) != null) {
                                boolean removed = Intent.ACTION_PACKAGE_REMOVED.equals(action);
                                final boolean replacing =
                                        intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                                final boolean killProcess =
                                        !intent.getBooleanExtra(Intent.EXTRA_DONT_KILL_APP, false);
                                final boolean fullUninstall = removed && !replacing;
                                if (removed) {
                                    if (killProcess) {
                                        forceStopPackageLocked(ssp, UserHandle.getAppId(
                                                intent.getIntExtra(Intent.EXTRA_UID, -1)),
                                                false, true, true, false, fullUninstall, userId,
                                                removed ? "pkg removed" : "pkg changed");
                                    }
                                    final int cmd = killProcess
                                            ? IApplicationThread.PACKAGE_REMOVED
                                            : IApplicationThread.PACKAGE_REMOVED_DONT_KILL;
                                    sendPackageBroadcastLocked(cmd,
                                            new String[] {ssp}, userId);
                                    if (fullUninstall) {
                                        mAppOpsService.packageRemoved(
                                                intent.getIntExtra(Intent.EXTRA_UID, -1), ssp);

                                        // Remove all permissions granted from/to this package
                                        removeUriPermissionsForPackageLocked(ssp, userId, true);

                                        removeTasksByPackageNameLocked(ssp, userId);

                                        // Hide the "unsupported display" dialog if necessary.
                                        if (mUnsupportedDisplaySizeDialog != null && ssp.equals(
                                                mUnsupportedDisplaySizeDialog.getPackageName())) {
                                            mUnsupportedDisplaySizeDialog.dismiss();
                                            mUnsupportedDisplaySizeDialog = null;
                                        }
                                        mCompatModePackages.handlePackageUninstalledLocked(ssp);
                                        mBatteryStatsService.notePackageUninstalled(ssp);
                                    }
                                } else {
                                    if (killProcess) {
                                        killPackageProcessesLocked(ssp, UserHandle.getAppId(
                                                intent.getIntExtra(Intent.EXTRA_UID, -1)),
                                                userId, ProcessList.INVALID_ADJ,
                                                false, true, true, false, "change " + ssp);
                                    }
                                    cleanupDisabledPackageComponentsLocked(ssp, userId, killProcess,
                                            intent.getStringArrayExtra(
                                                    Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST));
                                }
                            }
                            break;
                        case Intent.ACTION_PACKAGES_SUSPENDED:
                        case Intent.ACTION_PACKAGES_UNSUSPENDED:
                            final boolean suspended = Intent.ACTION_PACKAGES_SUSPENDED.equals(
                                    intent.getAction());
                            final String[] packageNames = intent.getStringArrayExtra(
                                    Intent.EXTRA_CHANGED_PACKAGE_LIST);
                            final int userHandle = intent.getIntExtra(
                                    Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL);

                            synchronized(ActivityManagerService.this) {
                                mRecentTasks.onPackagesSuspendedChanged(
                                        packageNames, suspended, userHandle);
                            }
                            break;
                    }
                    break;
                case Intent.ACTION_PACKAGE_REPLACED:
                {
                    final Uri data = intent.getData();
                    final String ssp;
                    if (data != null && (ssp = data.getSchemeSpecificPart()) != null) {
                        final ApplicationInfo aInfo =
                                getPackageManagerInternalLocked().getApplicationInfo(
                                        ssp,
                                        userId);
                        if (aInfo == null) {
                            Slog.w(TAG, "Dropping ACTION_PACKAGE_REPLACED for non-existent pkg:"
                                    + " ssp=" + ssp + " data=" + data);
                            return ActivityManager.BROADCAST_SUCCESS;
                        }
                        mStackSupervisor.updateActivityApplicationInfoLocked(aInfo);
                        sendPackageBroadcastLocked(IApplicationThread.PACKAGE_REPLACED,
                                new String[] {ssp}, userId);
                    }
                    break;
                }
                case Intent.ACTION_PACKAGE_ADDED:
                {
                    // Special case for adding a package: by default turn on compatibility mode.
                    Uri data = intent.getData();
                    String ssp;
                    if (data != null && (ssp = data.getSchemeSpecificPart()) != null) {
                        final boolean replacing =
                                intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                        mCompatModePackages.handlePackageAddedLocked(ssp, replacing);

                        try {
                            ApplicationInfo ai = AppGlobals.getPackageManager().
                                    getApplicationInfo(ssp, 0, 0);
                            mBatteryStatsService.notePackageInstalled(ssp,
                                    ai != null ? ai.versionCode : 0);
                        } catch (RemoteException e) {
                        }
                    }
                    break;
                }
                case Intent.ACTION_PACKAGE_DATA_CLEARED:
                {
                    Uri data = intent.getData();
                    String ssp;
                    if (data != null && (ssp = data.getSchemeSpecificPart()) != null) {
                        // Hide the "unsupported display" dialog if necessary.
                        if (mUnsupportedDisplaySizeDialog != null && ssp.equals(
                                mUnsupportedDisplaySizeDialog.getPackageName())) {
                            mUnsupportedDisplaySizeDialog.dismiss();
                            mUnsupportedDisplaySizeDialog = null;
                        }
                        mCompatModePackages.handlePackageDataClearedLocked(ssp);
                    }
                    break;
                }
                case Intent.ACTION_TIMEZONE_CHANGED:
                    // If this is the time zone changed action, queue up a message that will reset
                    // the timezone of all currently running processes. This message will get
                    // queued up before the broadcast happens.
                    mHandler.sendEmptyMessage(UPDATE_TIME_ZONE);
                    break;
                case Intent.ACTION_TIME_CHANGED:
                    // If the user set the time, let all running processes know.
                    final int is24Hour =
                            intent.getBooleanExtra(Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT, false) ? 1
                                    : 0;
                    mHandler.sendMessage(mHandler.obtainMessage(UPDATE_TIME, is24Hour, 0));
                    BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
                    synchronized (stats) {
                        stats.noteCurrentTimeChangedLocked();
                    }
                    break;
                case Intent.ACTION_CLEAR_DNS_CACHE:
                    mHandler.sendEmptyMessage(CLEAR_DNS_CACHE_MSG);
                    break;
                case Proxy.PROXY_CHANGE_ACTION:
                    ProxyInfo proxy = intent.getParcelableExtra(Proxy.EXTRA_PROXY_INFO);
                    mHandler.sendMessage(mHandler.obtainMessage(UPDATE_HTTP_PROXY_MSG, proxy));
                    break;
                case android.hardware.Camera.ACTION_NEW_PICTURE:
                case android.hardware.Camera.ACTION_NEW_VIDEO:
                    // These broadcasts are no longer allowed by the system, since they can
                    // cause significant thrashing at a crictical point (using the camera).
                    // Apps should use JobScehduler to monitor for media provider changes.
                    Slog.w(TAG, action + " no longer allowed; dropping from "
                            + UserHandle.formatUid(callingUid));
                    if (resultTo != null) {
                        final BroadcastQueue queue = broadcastQueueForIntent(intent);
                        try {
                            queue.performReceiveLocked(callerApp, resultTo, intent,
                                    Activity.RESULT_CANCELED, null, null,
                                    false, false, userId);
                        } catch (RemoteException e) {
                            Slog.w(TAG, "Failure ["
                                    + queue.mQueueName + "] sending broadcast result of "
                                    + intent, e);

                        }
                    }
                    // Lie; we don't want to crash the app.
                    return ActivityManager.BROADCAST_SUCCESS;
            }
        }

        // Add to the sticky list if requested.
        if (sticky) {
            if (checkPermission(android.Manifest.permission.BROADCAST_STICKY,
                    callingPid, callingUid)
                    != PackageManager.PERMISSION_GRANTED) {
                String msg = "Permission Denial: broadcastIntent() requesting a sticky broadcast from pid="
                        + callingPid + ", uid=" + callingUid
                        + " requires " + android.Manifest.permission.BROADCAST_STICKY;
                Slog.w(TAG, msg);
                throw new SecurityException(msg);
            }
            if (requiredPermissions != null && requiredPermissions.length > 0) {
                Slog.w(TAG, "Can't broadcast sticky intent " + intent
                        + " and enforce permissions " + Arrays.toString(requiredPermissions));
                return ActivityManager.BROADCAST_STICKY_CANT_HAVE_PERMISSION;
            }
            if (intent.getComponent() != null) {
                throw new SecurityException(
                        "Sticky broadcasts can't target a specific component");
            }
            // We use userId directly here, since the "all" target is maintained
            // as a separate set of sticky broadcasts.
            if (userId != UserHandle.USER_ALL) {
                // But first, if this is not a broadcast to all users, then
                // make sure it doesn't conflict with an existing broadcast to
                // all users.
                ArrayMap<String, ArrayList<Intent>> stickies = mStickyBroadcasts.get(
                        UserHandle.USER_ALL);
                if (stickies != null) {
                    ArrayList<Intent> list = stickies.get(intent.getAction());
                    if (list != null) {
                        int N = list.size();
                        int i;
                        for (i=0; i<N; i++) {
                            if (intent.filterEquals(list.get(i))) {
                                throw new IllegalArgumentException(
                                        "Sticky broadcast " + intent + " for user "
                                        + userId + " conflicts with existing global broadcast");
                            }
                        }
                    }
                }
            }
            ArrayMap<String, ArrayList<Intent>> stickies = mStickyBroadcasts.get(userId);
            if (stickies == null) {
                stickies = new ArrayMap<>();
                mStickyBroadcasts.put(userId, stickies);
            }
            ArrayList<Intent> list = stickies.get(intent.getAction());
            if (list == null) {
                list = new ArrayList<>();
                stickies.put(intent.getAction(), list);
            }
            final int stickiesCount = list.size();
            int i;
            for (i = 0; i < stickiesCount; i++) {
                if (intent.filterEquals(list.get(i))) {
                    // This sticky already exists, replace it.
                    list.set(i, new Intent(intent));
                    break;
                }
            }
            if (i >= stickiesCount) {
                list.add(new Intent(intent));
            }
        }

        int[] users;
        if (userId == UserHandle.USER_ALL) {
            // Caller wants broadcast to go to all started users.
            users = mUserController.getStartedUserArrayLocked();
        } else {
            // Caller wants broadcast to go to one specific user.
            users = new int[] {userId};
        }

        // Figure out who all will receive this broadcast.
        List receivers = null;
        List<BroadcastFilter> registeredReceivers = null;
        // Need to resolve the intent to interested receivers...
        if ((intent.getFlags()&Intent.FLAG_RECEIVER_REGISTERED_ONLY)
                 == 0) {
            receivers = collectReceiverComponents(intent, resolvedType, callingUid, users);
        }
        if (intent.getComponent() == null) {
            if (userId == UserHandle.USER_ALL && callingUid == Process.SHELL_UID) {
                // Query one target user at a time, excluding shell-restricted users
                for (int i = 0; i < users.length; i++) {
                    if (mUserController.hasUserRestriction(
                            UserManager.DISALLOW_DEBUGGING_FEATURES, users[i])) {
                        continue;
                    }
                    List<BroadcastFilter> registeredReceiversForUser =
                            mReceiverResolver.queryIntent(intent,
                                    resolvedType, false, users[i]);
                    if (registeredReceivers == null) {
                        registeredReceivers = registeredReceiversForUser;
                    } else if (registeredReceiversForUser != null) {
                        registeredReceivers.addAll(registeredReceiversForUser);
                    }
                }
            } else {
                registeredReceivers = mReceiverResolver.queryIntent(intent,
                        resolvedType, false, userId);
            }
        }

        final boolean replacePending =
                (intent.getFlags()&Intent.FLAG_RECEIVER_REPLACE_PENDING) != 0;

        if (DEBUG_BROADCAST) Slog.v(TAG_BROADCAST, "Enqueing broadcast: " + intent.getAction()
                + " replacePending=" + replacePending);

        int NR = registeredReceivers != null ? registeredReceivers.size() : 0;
        if (!ordered && NR > 0) {
            // If we are not serializing this broadcast, then send the
            // registered receivers separately so they don't wait for the
            // components to be launched.
            if (isCallerSystem) {
                checkBroadcastFromSystem(intent, callerApp, callerPackage, callingUid,
                        isProtectedBroadcast, registeredReceivers);
            }
            final BroadcastQueue queue = broadcastQueueForIntent(intent);
            BroadcastRecord r = new BroadcastRecord(queue, intent, callerApp,
                    callerPackage, callingPid, callingUid, resolvedType, requiredPermissions,
                    appOp, brOptions, registeredReceivers, resultTo, resultCode, resultData,
                    resultExtras, ordered, sticky, false, userId);
            if (DEBUG_BROADCAST) Slog.v(TAG_BROADCAST, "Enqueueing parallel broadcast " + r);
            final boolean replaced = replacePending && queue.replaceParallelBroadcastLocked(r);
            if (!replaced) {
                queue.enqueueParallelBroadcastLocked(r);
                queue.scheduleBroadcastsLocked();
            }
            registeredReceivers = null;
            NR = 0;
        }

        // Merge into one list.
        int ir = 0;
        if (receivers != null) {
            // A special case for PACKAGE_ADDED: do not allow the package
            // being added to see this broadcast.  This prevents them from
            // using this as a back door to get run as soon as they are
            // installed.  Maybe in the future we want to have a special install
            // broadcast or such for apps, but we'd like to deliberately make
            // this decision.
            String skipPackages[] = null;
            if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())
                    || Intent.ACTION_PACKAGE_RESTARTED.equals(intent.getAction())
                    || Intent.ACTION_PACKAGE_DATA_CLEARED.equals(intent.getAction())) {
                Uri data = intent.getData();
                if (data != null) {
                    String pkgName = data.getSchemeSpecificPart();
                    if (pkgName != null) {
                        skipPackages = new String[] { pkgName };
                    }
                }
            } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(intent.getAction())) {
                skipPackages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
            }
            if (skipPackages != null && (skipPackages.length > 0)) {
                for (String skipPackage : skipPackages) {
                    if (skipPackage != null) {
                        int NT = receivers.size();
                        for (int it=0; it<NT; it++) {
                            ResolveInfo curt = (ResolveInfo)receivers.get(it);
                            if (curt.activityInfo.packageName.equals(skipPackage)) {
                                receivers.remove(it);
                                it--;
                                NT--;
                            }
                        }
                    }
                }
            }

            int NT = receivers != null ? receivers.size() : 0;
            int it = 0;
            ResolveInfo curt = null;
            BroadcastFilter curr = null;
            while (it < NT && ir < NR) {
                if (curt == null) {
                    curt = (ResolveInfo)receivers.get(it);
                }
                if (curr == null) {
                    curr = registeredReceivers.get(ir);
                }
                if (curr.getPriority() >= curt.priority) {
                    // Insert this broadcast record into the final list.
                    receivers.add(it, curr);
                    ir++;
                    curr = null;
                    it++;
                    NT++;
                } else {
                    // Skip to the next ResolveInfo in the final list.
                    it++;
                    curt = null;
                }
            }
        }
        while (ir < NR) {
            if (receivers == null) {
                receivers = new ArrayList();
            }
            receivers.add(registeredReceivers.get(ir));
            ir++;
        }

        if (isCallerSystem) {
            checkBroadcastFromSystem(intent, callerApp, callerPackage, callingUid,
                    isProtectedBroadcast, receivers);
        }

        if ((receivers != null && receivers.size() > 0)
                || resultTo != null) {
            BroadcastQueue queue = broadcastQueueForIntent(intent);
            BroadcastRecord r = new BroadcastRecord(queue, intent, callerApp,
                    callerPackage, callingPid, callingUid, resolvedType,
                    requiredPermissions, appOp, brOptions, receivers, resultTo, resultCode,
                    resultData, resultExtras, ordered, sticky, false, userId);

            if (DEBUG_BROADCAST) Slog.v(TAG_BROADCAST, "Enqueueing ordered broadcast " + r
                    + ": prev had " + queue.mOrderedBroadcasts.size());
            if (DEBUG_BROADCAST) Slog.i(TAG_BROADCAST,
                    "Enqueueing broadcast " + r.intent.getAction());

            boolean replaced = replacePending && queue.replaceOrderedBroadcastLocked(r);
            if (!replaced) {
                queue.enqueueOrderedBroadcastLocked(r);
                queue.scheduleBroadcastsLocked();
            }
        } else {
            // There was nobody interested in the broadcast, but we still want to record
            // that it happened.
            if (intent.getComponent() == null && intent.getPackage() == null
                    && (intent.getFlags()&Intent.FLAG_RECEIVER_REGISTERED_ONLY) == 0) {
                // This was an implicit broadcast... let's record it for posterity.
                addBroadcastStatLocked(intent.getAction(), callerPackage, 0, 0, 0);
            }
        }

        return ActivityManager.BROADCAST_SUCCESS;
    }

    final void addBroadcastStatLocked(String action, String srcPackage, int receiveCount,
            int skipCount, long dispatchTime) {
        final long now = SystemClock.elapsedRealtime();
        if (mCurBroadcastStats == null ||
                (mCurBroadcastStats.mStartRealtime +(24*60*60*1000) < now)) {
            mLastBroadcastStats = mCurBroadcastStats;
            if (mLastBroadcastStats != null) {
                mLastBroadcastStats.mEndRealtime = SystemClock.elapsedRealtime();
                mLastBroadcastStats.mEndUptime = SystemClock.uptimeMillis();
            }
            mCurBroadcastStats = new BroadcastStats();
        }
        mCurBroadcastStats.addBroadcast(action, srcPackage, receiveCount, skipCount, dispatchTime);
    }

    final Intent verifyBroadcastLocked(Intent intent) {
        // Refuse possible leaked file descriptors
        if (intent != null && intent.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        int flags = intent.getFlags();

        if (!mProcessesReady) {
            // if the caller really truly claims to know what they're doing, go
            // ahead and allow the broadcast without launching any receivers
            if ((flags&Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT) != 0) {
                // This will be turned into a FLAG_RECEIVER_REGISTERED_ONLY later on if needed.
            } else if ((flags&Intent.FLAG_RECEIVER_REGISTERED_ONLY) == 0) {
                Slog.e(TAG, "Attempt to launch receivers of broadcast intent " + intent
                        + " before boot completion");
                throw new IllegalStateException("Cannot broadcast before boot completed");
            }
        }

        if ((flags&Intent.FLAG_RECEIVER_BOOT_UPGRADE) != 0) {
            throw new IllegalArgumentException(
                    "Can't use FLAG_RECEIVER_BOOT_UPGRADE here");
        }

        return intent;
    }

    public final int broadcastIntent(IApplicationThread caller,
            Intent intent, String resolvedType, IIntentReceiver resultTo,
            int resultCode, String resultData, Bundle resultExtras,
            String[] requiredPermissions, int appOp, Bundle bOptions,
            boolean serialized, boolean sticky, int userId) {
        enforceNotIsolatedCaller("broadcastIntent");
        synchronized(this) {
            intent = verifyBroadcastLocked(intent);

            final ProcessRecord callerApp = getRecordForAppLocked(caller);
            final int callingPid = Binder.getCallingPid();
            final int callingUid = Binder.getCallingUid();
            final long origId = Binder.clearCallingIdentity();
            int res = broadcastIntentLocked(callerApp,
                    callerApp != null ? callerApp.info.packageName : null,
                    intent, resolvedType, resultTo, resultCode, resultData, resultExtras,
                    requiredPermissions, appOp, bOptions, serialized, sticky,
                    callingPid, callingUid, userId);
            Binder.restoreCallingIdentity(origId);
            return res;
        }
    }


    int broadcastIntentInPackage(String packageName, int uid,
            Intent intent, String resolvedType, IIntentReceiver resultTo,
            int resultCode, String resultData, Bundle resultExtras,
            String requiredPermission, Bundle bOptions, boolean serialized, boolean sticky,
            int userId) {
        synchronized(this) {
            intent = verifyBroadcastLocked(intent);

            final long origId = Binder.clearCallingIdentity();
            String[] requiredPermissions = requiredPermission == null ? null
                    : new String[] {requiredPermission};
            int res = broadcastIntentLocked(null, packageName, intent, resolvedType,
                    resultTo, resultCode, resultData, resultExtras,
                    requiredPermissions, AppOpsManager.OP_NONE, bOptions, serialized,
                    sticky, -1, uid, userId);
            Binder.restoreCallingIdentity(origId);
            return res;
        }
    }

    public final void unbroadcastIntent(IApplicationThread caller, Intent intent, int userId) {
        // Refuse possible leaked file descriptors
        if (intent != null && intent.hasFileDescriptors() == true) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, true, ALLOW_NON_FULL, "removeStickyBroadcast", null);

        synchronized(this) {
            if (checkCallingPermission(android.Manifest.permission.BROADCAST_STICKY)
                    != PackageManager.PERMISSION_GRANTED) {
                String msg = "Permission Denial: unbroadcastIntent() from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + Binder.getCallingUid()
                        + " requires " + android.Manifest.permission.BROADCAST_STICKY;
                Slog.w(TAG, msg);
                throw new SecurityException(msg);
            }
            ArrayMap<String, ArrayList<Intent>> stickies = mStickyBroadcasts.get(userId);
            if (stickies != null) {
                ArrayList<Intent> list = stickies.get(intent.getAction());
                if (list != null) {
                    int N = list.size();
                    int i;
                    for (i=0; i<N; i++) {
                        if (intent.filterEquals(list.get(i))) {
                            list.remove(i);
                            break;
                        }
                    }
                    if (list.size() <= 0) {
                        stickies.remove(intent.getAction());
                    }
                }
                if (stickies.size() <= 0) {
                    mStickyBroadcasts.remove(userId);
                }
            }
        }
    }

    void backgroundServicesFinishedLocked(int userId) {
        for (BroadcastQueue queue : mBroadcastQueues) {
            queue.backgroundServicesFinishedLocked(userId);
        }
    }

    public void finishReceiver(IBinder who, int resultCode, String resultData,
            Bundle resultExtras, boolean resultAbort, int flags) {
        if (DEBUG_BROADCAST) Slog.v(TAG_BROADCAST, "Finish receiver: " + who);

        // Refuse possible leaked file descriptors
        if (resultExtras != null && resultExtras.hasFileDescriptors()) {
            throw new IllegalArgumentException("File descriptors passed in Bundle");
        }

        final long origId = Binder.clearCallingIdentity();
        try {
            boolean doNext = false;
            BroadcastRecord r;

            synchronized(this) {
                BroadcastQueue queue = (flags & Intent.FLAG_RECEIVER_FOREGROUND) != 0
                        ? mFgBroadcastQueue : mBgBroadcastQueue;
                r = queue.getMatchingOrderedReceiver(who);
                if (r != null) {
                    doNext = r.queue.finishReceiverLocked(r, resultCode,
                        resultData, resultExtras, resultAbort, true);
                }
            }

            if (doNext) {
                r.queue.processNextBroadcast(false);
            }
            trimApplications();
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    // =========================================================
    // INSTRUMENTATION
    // =========================================================

    public boolean startInstrumentation(ComponentName className,
            String profileFile, int flags, Bundle arguments,
            IInstrumentationWatcher watcher, IUiAutomationConnection uiAutomationConnection,
            int userId, String abiOverride) {
        enforceNotIsolatedCaller("startInstrumentation");
        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, false, ALLOW_FULL_ONLY, "startInstrumentation", null);
        // Refuse possible leaked file descriptors
        if (arguments != null && arguments.hasFileDescriptors()) {
            throw new IllegalArgumentException("File descriptors passed in Bundle");
        }

        synchronized(this) {
            InstrumentationInfo ii = null;
            ApplicationInfo ai = null;
            try {
                ii = mContext.getPackageManager().getInstrumentationInfo(
                    className, STOCK_PM_FLAGS);
                ai = AppGlobals.getPackageManager().getApplicationInfo(
                        ii.targetPackage, STOCK_PM_FLAGS, userId);
            } catch (PackageManager.NameNotFoundException e) {
            } catch (RemoteException e) {
            }
            if (ii == null) {
                reportStartInstrumentationFailureLocked(watcher, className,
                        "Unable to find instrumentation info for: " + className);
                return false;
            }
            if (ai == null) {
                reportStartInstrumentationFailureLocked(watcher, className,
                        "Unable to find instrumentation target package: " + ii.targetPackage);
                return false;
            }
            if (!ai.hasCode()) {
                reportStartInstrumentationFailureLocked(watcher, className,
                        "Instrumentation target has no code: " + ii.targetPackage);
                return false;
            }

            int match = mContext.getPackageManager().checkSignatures(
                    ii.targetPackage, ii.packageName);
            if (match < 0 && match != PackageManager.SIGNATURE_FIRST_NOT_SIGNED) {
                String msg = "Permission Denial: starting instrumentation "
                        + className + " from pid="
                        + Binder.getCallingPid()
                        + ", uid=" + Binder.getCallingPid()
                        + " not allowed because package " + ii.packageName
                        + " does not have a signature matching the target "
                        + ii.targetPackage;
                reportStartInstrumentationFailureLocked(watcher, className, msg);
                throw new SecurityException(msg);
            }

            final long origId = Binder.clearCallingIdentity();
            // Instrumentation can kill and relaunch even persistent processes
            forceStopPackageLocked(ii.targetPackage, -1, true, false, true, true, false, userId,
                    "start instr");
            ProcessRecord app = addAppLocked(ai, false, abiOverride);
            app.instrumentationClass = className;
            app.instrumentationInfo = ai;
            app.instrumentationProfileFile = profileFile;
            app.instrumentationArguments = arguments;
            app.instrumentationWatcher = watcher;
            app.instrumentationUiAutomationConnection = uiAutomationConnection;
            app.instrumentationResultClass = className;
            Binder.restoreCallingIdentity(origId);
        }

        return true;
    }

    /**
     * Report errors that occur while attempting to start Instrumentation.  Always writes the
     * error to the logs, but if somebody is watching, send the report there too.  This enables
     * the "am" command to report errors with more information.
     *
     * @param watcher The IInstrumentationWatcher.  Null if there isn't one.
     * @param cn The component name of the instrumentation.
     * @param report The error report.
     */
    private void reportStartInstrumentationFailureLocked(IInstrumentationWatcher watcher,
            ComponentName cn, String report) {
        Slog.w(TAG, report);
        if (watcher != null) {
            Bundle results = new Bundle();
            results.putString(Instrumentation.REPORT_KEY_IDENTIFIER, "ActivityManagerService");
            results.putString("Error", report);
            mInstrumentationReporter.reportStatus(watcher, cn, -1, results);
        }
    }

    void finishInstrumentationLocked(ProcessRecord app, int resultCode, Bundle results) {
        if (app.instrumentationWatcher != null) {
            mInstrumentationReporter.reportFinished(app.instrumentationWatcher,
                    app.instrumentationClass, resultCode, results);
        }

        // Can't call out of the system process with a lock held, so post a message.
        if (app.instrumentationUiAutomationConnection != null) {
            mHandler.obtainMessage(SHUTDOWN_UI_AUTOMATION_CONNECTION_MSG,
                    app.instrumentationUiAutomationConnection).sendToTarget();
        }

        app.instrumentationWatcher = null;
        app.instrumentationUiAutomationConnection = null;
        app.instrumentationClass = null;
        app.instrumentationInfo = null;
        app.instrumentationProfileFile = null;
        app.instrumentationArguments = null;

        forceStopPackageLocked(app.info.packageName, -1, false, false, true, true, false, app.userId,
                "finished inst");
    }

    public void finishInstrumentation(IApplicationThread target,
            int resultCode, Bundle results) {
        int userId = UserHandle.getCallingUserId();
        // Refuse possible leaked file descriptors
        if (results != null && results.hasFileDescriptors()) {
            throw new IllegalArgumentException("File descriptors passed in Intent");
        }

        synchronized(this) {
            ProcessRecord app = getRecordForAppLocked(target);
            if (app == null) {
                Slog.w(TAG, "finishInstrumentation: no app for " + target);
                return;
            }
            final long origId = Binder.clearCallingIdentity();
            finishInstrumentationLocked(app, resultCode, results);
            Binder.restoreCallingIdentity(origId);
        }
    }

    // =========================================================
    // CONFIGURATION
    // =========================================================

    public ConfigurationInfo getDeviceConfigurationInfo() {
        ConfigurationInfo config = new ConfigurationInfo();
        synchronized (this) {
            config.reqTouchScreen = mConfiguration.touchscreen;
            config.reqKeyboardType = mConfiguration.keyboard;
            config.reqNavigation = mConfiguration.navigation;
            if (mConfiguration.navigation == Configuration.NAVIGATION_DPAD
                    || mConfiguration.navigation == Configuration.NAVIGATION_TRACKBALL) {
                config.reqInputFeatures |= ConfigurationInfo.INPUT_FEATURE_FIVE_WAY_NAV;
            }
            if (mConfiguration.keyboard != Configuration.KEYBOARD_UNDEFINED
                    && mConfiguration.keyboard != Configuration.KEYBOARD_NOKEYS) {
                config.reqInputFeatures |= ConfigurationInfo.INPUT_FEATURE_HARD_KEYBOARD;
            }
            config.reqGlEsVersion = GL_ES_VERSION;
        }
        return config;
    }

    ActivityStack getFocusedStack() {
        return mStackSupervisor.getFocusedStack();
    }

    @Override
    public int getFocusedStackId() throws RemoteException {
        ActivityStack focusedStack = getFocusedStack();
        if (focusedStack != null) {
            return focusedStack.getStackId();
        }
        return -1;
    }

    public Configuration getConfiguration() {
        Configuration ci;
        synchronized(this) {
            ci = new Configuration(mConfiguration);
            ci.userSetLocale = false;
        }
        return ci;
    }

    @Override
    public void suppressResizeConfigChanges(boolean suppress) throws RemoteException {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "suppressResizeConfigChanges()");
        synchronized (this) {
            mSuppressResizeConfigChanges = suppress;
        }
    }

    @Override
    public void moveTasksToFullscreenStack(int fromStackId, boolean onTop) {
        enforceCallingPermission(MANAGE_ACTIVITY_STACKS, "moveTasksToFullscreenStack()");
        if (fromStackId == HOME_STACK_ID) {
            throw new IllegalArgumentException("You can't move tasks from the home stack.");
        }
        synchronized (this) {
            final long origId = Binder.clearCallingIdentity();
            try {
                mStackSupervisor.moveTasksToFullscreenStackLocked(fromStackId, onTop);
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }
    }

    @Override
    public void updatePersistentConfiguration(Configuration values) {
        enforceCallingPermission(android.Manifest.permission.CHANGE_CONFIGURATION,
                "updateConfiguration()");
        enforceWriteSettingsPermission("updateConfiguration()");
        if (values == null) {
            throw new NullPointerException("Configuration must not be null");
        }

        int userId = UserHandle.getCallingUserId();

        synchronized(this) {
            updatePersistentConfigurationLocked(values, userId);
        }
    }

    private void updatePersistentConfigurationLocked(Configuration values, @UserIdInt int userId) {
        final long origId = Binder.clearCallingIdentity();
        try {
            updateConfigurationLocked(values, null, false, true, userId, false /* deferResume */);
        } finally {
            Binder.restoreCallingIdentity(origId);
        }
    }

    private void updateFontScaleIfNeeded(@UserIdInt int userId) {
        final float scaleFactor = Settings.System.getFloatForUser(mContext.getContentResolver(),
                FONT_SCALE, 1.0f, userId);
        if (mConfiguration.fontScale != scaleFactor) {
            final Configuration configuration = mWindowManager.computeNewConfiguration();
            configuration.fontScale = scaleFactor;
            synchronized (this) {
                updatePersistentConfigurationLocked(configuration, userId);
            }
        }
    }

    private void enforceWriteSettingsPermission(String func) {
        int uid = Binder.getCallingUid();
        if (uid == Process.ROOT_UID) {
            return;
        }

        if (Settings.checkAndNoteWriteSettingsOperation(mContext, uid,
                Settings.getPackageNameForUid(mContext, uid), false)) {
            return;
        }

        String msg = "Permission Denial: " + func + " from pid="
                + Binder.getCallingPid()
                + ", uid=" + uid
                + " requires " + android.Manifest.permission.WRITE_SETTINGS;
        Slog.w(TAG, msg);
        throw new SecurityException(msg);
    }

    public void updateConfiguration(Configuration values) {
        enforceCallingPermission(android.Manifest.permission.CHANGE_CONFIGURATION,
                "updateConfiguration()");

        synchronized(this) {
            if (values == null && mWindowManager != null) {
                // sentinel: fetch the current configuration from the window manager
                values = mWindowManager.computeNewConfiguration();
            }

            if (mWindowManager != null) {
                mProcessList.applyDisplaySize(mWindowManager);
            }

            final long origId = Binder.clearCallingIdentity();
            if (values != null) {
                Settings.System.clearConfiguration(values);
            }
            updateConfigurationLocked(values, null, false);
            Binder.restoreCallingIdentity(origId);
        }
    }

    void updateUserConfigurationLocked() {
        Configuration configuration = new Configuration(mConfiguration);
        Settings.System.adjustConfigurationForUser(mContext.getContentResolver(), configuration,
                mUserController.getCurrentUserIdLocked(), Settings.System.canWrite(mContext));
        updateConfigurationLocked(configuration, null, false);
    }

    boolean updateConfigurationLocked(Configuration values, ActivityRecord starting,
            boolean initLocale) {
        return updateConfigurationLocked(values, starting, initLocale, false /* deferResume */);
    }

    boolean updateConfigurationLocked(Configuration values, ActivityRecord starting,
            boolean initLocale, boolean deferResume) {
        // pass UserHandle.USER_NULL as userId because we don't persist configuration for any user
        return updateConfigurationLocked(values, starting, initLocale, false /* persistent */,
                UserHandle.USER_NULL, deferResume);
    }

    // To cache the list of supported system locales
    private String[] mSupportedSystemLocales = null;

    /**
     * Do either or both things: (1) change the current configuration, and (2)
     * make sure the given activity is running with the (now) current
     * configuration.  Returns true if the activity has been left running, or
     * false if <var>starting</var> is being destroyed to match the new
     * configuration.
     *
     * @param userId is only used when persistent parameter is set to true to persist configuration
     *               for that particular user
     */
    private boolean updateConfigurationLocked(Configuration values, ActivityRecord starting,
            boolean initLocale, boolean persistent, int userId, boolean deferResume) {
        int changes = 0;

        if (mWindowManager != null) {
            mWindowManager.deferSurfaceLayout();
        }
        if (values != null) {
            Configuration newConfig = new Configuration(mConfiguration);
            changes = newConfig.updateFrom(values);
            if (changes != 0) {
                if (DEBUG_SWITCH || DEBUG_CONFIGURATION) Slog.i(TAG_CONFIGURATION,
                        "Updating configuration to: " + values);

                EventLog.writeEvent(EventLogTags.CONFIGURATION_CHANGED, changes);

                if (!initLocale && !values.getLocales().isEmpty() && values.userSetLocale) {
                    final LocaleList locales = values.getLocales();
                    int bestLocaleIndex = 0;
                    if (locales.size() > 1) {
                        if (mSupportedSystemLocales == null) {
                            mSupportedSystemLocales =
                                    Resources.getSystem().getAssets().getLocales();
                        }
                        bestLocaleIndex = Math.max(0,
                                locales.getFirstMatchIndex(mSupportedSystemLocales));
                    }
                    SystemProperties.set("persist.sys.locale",
                            locales.get(bestLocaleIndex).toLanguageTag());
                    LocaleList.setDefault(locales, bestLocaleIndex);
                    mHandler.sendMessage(mHandler.obtainMessage(SEND_LOCALE_TO_MOUNT_DAEMON_MSG,
                            locales.get(bestLocaleIndex)));
                }

                mConfigurationSeq++;
                if (mConfigurationSeq <= 0) {
                    mConfigurationSeq = 1;
                }
                newConfig.seq = mConfigurationSeq;
                mConfiguration = newConfig;
                Slog.i(TAG, "Config changes=" + Integer.toHexString(changes) + " " + newConfig);
                mUsageStatsService.reportConfigurationChange(newConfig,
                        mUserController.getCurrentUserIdLocked());
                //mUsageStatsService.noteStartConfig(newConfig);

                final Configuration configCopy = new Configuration(mConfiguration);

                // TODO: If our config changes, should we auto dismiss any currently
                // showing dialogs?
                mShowDialogs = shouldShowDialogs(newConfig, mInVrMode);

                AttributeCache ac = AttributeCache.instance();
                if (ac != null) {
                    ac.updateConfiguration(configCopy);
                }

                // Make sure all resources in our process are updated
                // right now, so that anyone who is going to retrieve
                // resource values after we return will be sure to get
                // the new ones.  This is especially important during
                // boot, where the first config change needs to guarantee
                // all resources have that config before following boot
                // code is executed.
                mSystemThread.applyConfigurationToResources(configCopy);

                if (persistent && Settings.System.hasInterestingConfigurationChanges(changes)) {
                    Message msg = mHandler.obtainMessage(UPDATE_CONFIGURATION_MSG);
                    msg.obj = new Configuration(configCopy);
                    msg.arg1 = userId;
                    mHandler.sendMessage(msg);
                }

                final boolean isDensityChange = (changes & ActivityInfo.CONFIG_DENSITY) != 0;
                if (isDensityChange) {
                    // Reset the unsupported display size dialog.
                    mUiHandler.sendEmptyMessage(SHOW_UNSUPPORTED_DISPLAY_SIZE_DIALOG_MSG);

                    killAllBackgroundProcessesExcept(Build.VERSION_CODES.N,
                            ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE);
                }

                for (int i=mLruProcesses.size()-1; i>=0; i--) {
                    ProcessRecord app = mLruProcesses.get(i);
                    try {
                        if (app.thread != null) {
                            if (DEBUG_CONFIGURATION) Slog.v(TAG_CONFIGURATION, "Sending to proc "
                                    + app.processName + " new config " + mConfiguration);
                            app.thread.scheduleConfigurationChanged(configCopy);
                        }
                    } catch (Exception e) {
                    }
                }
                Intent intent = new Intent(Intent.ACTION_CONFIGURATION_CHANGED);
                intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                        | Intent.FLAG_RECEIVER_REPLACE_PENDING
                        | Intent.FLAG_RECEIVER_FOREGROUND);
                broadcastIntentLocked(null, null, intent, null, null, 0, null, null,
                        null, AppOpsManager.OP_NONE, null, false, false,
                        MY_PID, Process.SYSTEM_UID, UserHandle.USER_ALL);
                if ((changes&ActivityInfo.CONFIG_LOCALE) != 0) {
                    intent = new Intent(Intent.ACTION_LOCALE_CHANGED);
                    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
	            if (initLocale || !mProcessesReady) {
                        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
                    }
                    broadcastIntentLocked(null, null, intent,
                            null, null, 0, null, null, null, AppOpsManager.OP_NONE,
                            null, false, false, MY_PID, Process.SYSTEM_UID, UserHandle.USER_ALL);
                }
            }
            // Update the configuration with WM first and check if any of the stacks need to be
            // resized due to the configuration change. If so, resize the stacks now and do any
            // relaunches if necessary. This way we don't need to relaunch again below in
            // ensureActivityConfigurationLocked().
            if (mWindowManager != null) {
                final int[] resizedStacks = mWindowManager.setNewConfiguration(mConfiguration);
                if (resizedStacks != null) {
                    for (int stackId : resizedStacks) {
                        final Rect newBounds = mWindowManager.getBoundsForNewConfiguration(stackId);
                        mStackSupervisor.resizeStackLocked(
                                stackId, newBounds, null, null, false, false, deferResume);
                    }
                }
            }
        }

        boolean kept = true;
        final ActivityStack mainStack = mStackSupervisor.getFocusedStack();
        // mainStack is null during startup.
        if (mainStack != null) {
            if (changes != 0 && starting == null) {
                // If the configuration changed, and the caller is not already
                // in the process of starting an activity, then find the top
                // activity to check if its configuration needs to change.
                starting = mainStack.topRunningActivityLocked();
            }

            if (starting != null) {
                kept = mainStack.ensureActivityConfigurationLocked(starting, changes, false);
                // And we need to make sure at this point that all other activities
                // are made visible with the correct configuration.
                mStackSupervisor.ensureActivitiesVisibleLocked(starting, changes,
                        !PRESERVE_WINDOWS);
            }
        }
        if (mWindowManager != null) {
            mWindowManager.continueSurfaceLayout();
        }
        return kept;
    }

    /**
     * Decide based on the configuration whether we should shouw the ANR,
     * crash, etc dialogs.  The idea is that if there is no affordence to
     * press the on-screen buttons, or the user experience would be more
     * greatly impacted than the crash itself, we shouldn't show the dialog.
     *
     * A thought: SystemUI might also want to get told about this, the Power
     * dialog / global actions also might want different behaviors.
     */
    private static final boolean shouldShowDialogs(Configuration config, boolean inVrMode) {
        final boolean inputMethodExists = !(config.keyboard == Configuration.KEYBOARD_NOKEYS
                                   && config.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH
                                   && config.navigation == Configuration.NAVIGATION_NONAV);
        int modeType = config.uiMode & Configuration.UI_MODE_TYPE_MASK;
        final boolean uiModeSupportsDialogs = (modeType != Configuration.UI_MODE_TYPE_CAR
                && !(modeType == Configuration.UI_MODE_TYPE_WATCH && "user".equals(Build.TYPE))
                && modeType != Configuration.UI_MODE_TYPE_TELEVISION);
        return inputMethodExists && uiModeSupportsDialogs && !inVrMode;
    }

    @Override
    public boolean shouldUpRecreateTask(IBinder token, String destAffinity) {
        synchronized (this) {
            ActivityRecord srec = ActivityRecord.forTokenLocked(token);
            if (srec != null) {
                return srec.task.stack.shouldUpRecreateTaskLocked(srec, destAffinity);
            }
        }
        return false;
    }

    public boolean navigateUpTo(IBinder token, Intent destIntent, int resultCode,
            Intent resultData) {

        synchronized (this) {
            final ActivityRecord r = ActivityRecord.forTokenLocked(token);
            if (r != null) {
                return r.task.stack.navigateUpToLocked(r, destIntent, resultCode, resultData);
            }
            return false;
        }
    }

    public int getLaunchedFromUid(IBinder activityToken) {
        ActivityRecord srec;
        synchronized (this) {
            srec = ActivityRecord.forTokenLocked(activityToken);
        }
        if (srec == null) {
            return -1;
        }
        return srec.launchedFromUid;
    }

    public String getLaunchedFromPackage(IBinder activityToken) {
        ActivityRecord srec;
        synchronized (this) {
            srec = ActivityRecord.forTokenLocked(activityToken);
        }
        if (srec == null) {
            return null;
        }
        return srec.launchedFromPackage;
    }

    // =========================================================
    // LIFETIME MANAGEMENT
    // =========================================================

    // Returns which broadcast queue the app is the current [or imminent] receiver
    // on, or 'null' if the app is not an active broadcast recipient.
    private BroadcastQueue isReceivingBroadcast(ProcessRecord app) {
        BroadcastRecord r = app.curReceiver;
        if (r != null) {
            return r.queue;
        }

        // It's not the current receiver, but it might be starting up to become one
        synchronized (this) {
            for (BroadcastQueue queue : mBroadcastQueues) {
                r = queue.mPendingBroadcast;
                if (r != null && r.curApp == app) {
                    // found it; report which queue it's in
                    return queue;
                }
            }
        }

        return null;
    }

    Association startAssociationLocked(int sourceUid, String sourceProcess, int sourceState,
            int targetUid, ComponentName targetComponent, String targetProcess) {
        if (!mTrackingAssociations) {
            return null;
        }
        ArrayMap<ComponentName, SparseArray<ArrayMap<String, Association>>> components
                = mAssociations.get(targetUid);
        if (components == null) {
            components = new ArrayMap<>();
            mAssociations.put(targetUid, components);
        }
        SparseArray<ArrayMap<String, Association>> sourceUids = components.get(targetComponent);
        if (sourceUids == null) {
            sourceUids = new SparseArray<>();
            components.put(targetComponent, sourceUids);
        }
        ArrayMap<String, Association> sourceProcesses = sourceUids.get(sourceUid);
        if (sourceProcesses == null) {
            sourceProcesses = new ArrayMap<>();
            sourceUids.put(sourceUid, sourceProcesses);
        }
        Association ass = sourceProcesses.get(sourceProcess);
        if (ass == null) {
            ass = new Association(sourceUid, sourceProcess, targetUid, targetComponent,
                    targetProcess);
            sourceProcesses.put(sourceProcess, ass);
        }
        ass.mCount++;
        ass.mNesting++;
        if (ass.mNesting == 1) {
            ass.mStartTime = ass.mLastStateUptime = SystemClock.uptimeMillis();
            ass.mLastState = sourceState;
        }
        return ass;
    }

    void stopAssociationLocked(int sourceUid, String sourceProcess, int targetUid,
            ComponentName targetComponent) {
        if (!mTrackingAssociations) {
            return;
        }
        ArrayMap<ComponentName, SparseArray<ArrayMap<String, Association>>> components
                = mAssociations.get(targetUid);
        if (components == null) {
            return;
        }
        SparseArray<ArrayMap<String, Association>> sourceUids = components.get(targetComponent);
        if (sourceUids == null) {
            return;
        }
        ArrayMap<String, Association> sourceProcesses = sourceUids.get(sourceUid);
        if (sourceProcesses == null) {
            return;
        }
        Association ass = sourceProcesses.get(sourceProcess);
        if (ass == null || ass.mNesting <= 0) {
            return;
        }
        ass.mNesting--;
        if (ass.mNesting == 0) {
            long uptime = SystemClock.uptimeMillis();
            ass.mTime += uptime - ass.mStartTime;
            ass.mStateTimes[ass.mLastState-ActivityManager.MIN_PROCESS_STATE]
                    += uptime - ass.mLastStateUptime;
            ass.mLastState = ActivityManager.MAX_PROCESS_STATE + 2;
        }
    }

    private void noteUidProcessState(final int uid, final int state) {
        mBatteryStatsService.noteUidProcessState(uid, state);
        if (mTrackingAssociations) {
            for (int i1=0, N1=mAssociations.size(); i1<N1; i1++) {
                ArrayMap<ComponentName, SparseArray<ArrayMap<String, Association>>> targetComponents
                        = mAssociations.valueAt(i1);
                for (int i2=0, N2=targetComponents.size(); i2<N2; i2++) {
                    SparseArray<ArrayMap<String, Association>> sourceUids
                            = targetComponents.valueAt(i2);
                    ArrayMap<String, Association> sourceProcesses = sourceUids.get(uid);
                    if (sourceProcesses != null) {
                        for (int i4=0, N4=sourceProcesses.size(); i4<N4; i4++) {
                            Association ass = sourceProcesses.valueAt(i4);
                            if (ass.mNesting >= 1) {
                                // currently associated
                                long uptime = SystemClock.uptimeMillis();
                                ass.mStateTimes[ass.mLastState-ActivityManager.MIN_PROCESS_STATE]
                                        += uptime - ass.mLastStateUptime;
                                ass.mLastState = state;
                                ass.mLastStateUptime = uptime;
                            }
                        }
                    }
                }
            }
        }
    }

    private final int computeOomAdjLocked(ProcessRecord app, int cachedAdj, ProcessRecord TOP_APP,
            boolean doingAll, long now) {
        if (mAdjSeq == app.adjSeq) {
            // This adjustment has already been computed.
            return app.curRawAdj;
        }

        if (app.thread == null) {
            app.adjSeq = mAdjSeq;
            app.curSchedGroup = ProcessList.SCHED_GROUP_BACKGROUND;
            app.curProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
            return (app.curAdj=app.curRawAdj=ProcessList.CACHED_APP_MAX_ADJ);
        }

        app.adjTypeCode = ActivityManager.RunningAppProcessInfo.REASON_UNKNOWN;
        app.adjSource = null;
        app.adjTarget = null;
        app.empty = false;
        app.cached = false;

        final int activitiesSize = app.activities.size();

        if (app.maxAdj <= ProcessList.FOREGROUND_APP_ADJ) {
            // The max adjustment doesn't allow this app to be anything
            // below foreground, so it is not worth doing work for it.
            app.adjType = "fixed";
            app.adjSeq = mAdjSeq;
            app.curRawAdj = app.maxAdj;
            app.foregroundActivities = false;
            app.curSchedGroup = ProcessList.SCHED_GROUP_DEFAULT;
            app.curProcState = ActivityManager.PROCESS_STATE_PERSISTENT;
            // System processes can do UI, and when they do we want to have
            // them trim their memory after the user leaves the UI.  To
            // facilitate this, here we need to determine whether or not it
            // is currently showing UI.
            app.systemNoUi = true;
            if (app == TOP_APP) {
                app.systemNoUi = false;
                app.curSchedGroup = ProcessList.SCHED_GROUP_TOP_APP;
                app.adjType = "pers-top-activity";
            } else if (app.hasTopUi) {
                app.systemNoUi = false;
                app.curSchedGroup = ProcessList.SCHED_GROUP_TOP_APP;
                app.adjType = "pers-top-ui";
            } else if (activitiesSize > 0) {
                for (int j = 0; j < activitiesSize; j++) {
                    final ActivityRecord r = app.activities.get(j);
                    if (r.visible) {
                        app.systemNoUi = false;
                    }
                }
            }
            if (!app.systemNoUi) {
                app.curProcState = ActivityManager.PROCESS_STATE_PERSISTENT_UI;
            }
            return (app.curAdj=app.maxAdj);
        }

        app.systemNoUi = false;

        final int PROCESS_STATE_CUR_TOP = mTopProcessState;

        // Determine the importance of the process, starting with most
        // important to least, and assign an appropriate OOM adjustment.
        int adj;
        int schedGroup;
        int procState;
        boolean foregroundActivities = false;
        BroadcastQueue queue;
        if (app == TOP_APP) {
            // The last app on the list is the foreground app.
            adj = ProcessList.FOREGROUND_APP_ADJ;
            schedGroup = ProcessList.SCHED_GROUP_TOP_APP;
            app.adjType = "top-activity";
            foregroundActivities = true;
            procState = PROCESS_STATE_CUR_TOP;
        } else if (app.instrumentationClass != null) {
            // Don't want to kill running instrumentation.
            adj = ProcessList.FOREGROUND_APP_ADJ;
            schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
            app.adjType = "instrumentation";
            procState = ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
        } else if ((queue = isReceivingBroadcast(app)) != null) {
            // An app that is currently receiving a broadcast also
            // counts as being in the foreground for OOM killer purposes.
            // It's placed in a sched group based on the nature of the
            // broadcast as reflected by which queue it's active in.
            adj = ProcessList.FOREGROUND_APP_ADJ;
            schedGroup = (queue == mFgBroadcastQueue)
                    ? ProcessList.SCHED_GROUP_DEFAULT : ProcessList.SCHED_GROUP_BACKGROUND;
            app.adjType = "broadcast";
            procState = ActivityManager.PROCESS_STATE_RECEIVER;
        } else if (app.executingServices.size() > 0) {
            // An app that is currently executing a service callback also
            // counts as being in the foreground.
            adj = ProcessList.FOREGROUND_APP_ADJ;
            schedGroup = app.execServicesFg ?
                    ProcessList.SCHED_GROUP_DEFAULT : ProcessList.SCHED_GROUP_BACKGROUND;
            app.adjType = "exec-service";
            procState = ActivityManager.PROCESS_STATE_SERVICE;
            //Slog.i(TAG, "EXEC " + (app.execServicesFg ? "FG" : "BG") + ": " + app);
        } else {
            // As far as we know the process is empty.  We may change our mind later.
            schedGroup = ProcessList.SCHED_GROUP_BACKGROUND;
            // At this point we don't actually know the adjustment.  Use the cached adj
            // value that the caller wants us to.
            adj = cachedAdj;
            procState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
            app.cached = true;
            app.empty = true;
            app.adjType = "cch-empty";
        }

        // Examine all activities if not already foreground.
        if (!foregroundActivities && activitiesSize > 0) {
            int minLayer = ProcessList.VISIBLE_APP_LAYER_MAX;
            for (int j = 0; j < activitiesSize; j++) {
                final ActivityRecord r = app.activities.get(j);
                if (r.app != app) {
                    Log.e(TAG, "Found activity " + r + " in proc activity list using " + r.app
                            + " instead of expected " + app);
                    if (r.app == null || (r.app.uid == app.uid)) {
                        // Only fix things up when they look sane
                        r.app = app;
                    } else {
                        continue;
                    }
                }
                if (r.visible) {
                    // App has a visible activity; only upgrade adjustment.
                    if (adj > ProcessList.VISIBLE_APP_ADJ) {
                        adj = ProcessList.VISIBLE_APP_ADJ;
                        app.adjType = "visible";
                    }
                    if (procState > PROCESS_STATE_CUR_TOP) {
                        procState = PROCESS_STATE_CUR_TOP;
                    }
                    schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
                    app.cached = false;
                    app.empty = false;
                    foregroundActivities = true;
                    if (r.task != null && minLayer > 0) {
                        final int layer = r.task.mLayerRank;
                        if (layer >= 0 && minLayer > layer) {
                            minLayer = layer;
                        }
                    }
                    break;
                } else if (r.state == ActivityState.PAUSING || r.state == ActivityState.PAUSED) {
                    if (adj > ProcessList.PERCEPTIBLE_APP_ADJ) {
                        adj = ProcessList.PERCEPTIBLE_APP_ADJ;
                        app.adjType = "pausing";
                    }
                    if (procState > PROCESS_STATE_CUR_TOP) {
                        procState = PROCESS_STATE_CUR_TOP;
                    }
                    schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
                    app.cached = false;
                    app.empty = false;
                    foregroundActivities = true;
                } else if (r.state == ActivityState.STOPPING) {
                    if (adj > ProcessList.PERCEPTIBLE_APP_ADJ) {
                        adj = ProcessList.PERCEPTIBLE_APP_ADJ;
                        app.adjType = "stopping";
                    }
                    // For the process state, we will at this point consider the
                    // process to be cached.  It will be cached either as an activity
                    // or empty depending on whether the activity is finishing.  We do
                    // this so that we can treat the process as cached for purposes of
                    // memory trimming (determing current memory level, trim command to
                    // send to process) since there can be an arbitrary number of stopping
                    // processes and they should soon all go into the cached state.
                    if (!r.finishing) {
                        if (procState > ActivityManager.PROCESS_STATE_LAST_ACTIVITY) {
                            procState = ActivityManager.PROCESS_STATE_LAST_ACTIVITY;
                        }
                    }
                    app.cached = false;
                    app.empty = false;
                    foregroundActivities = true;
                } else {
                    if (procState > ActivityManager.PROCESS_STATE_CACHED_ACTIVITY) {
                        procState = ActivityManager.PROCESS_STATE_CACHED_ACTIVITY;
                        app.adjType = "cch-act";
                    }
                }
            }
            if (adj == ProcessList.VISIBLE_APP_ADJ) {
                adj += minLayer;
            }
        }

        if (adj > ProcessList.PERCEPTIBLE_APP_ADJ
                || procState > ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE) {
            if (app.foregroundServices) {
                // The user is aware of this app, so make it visible.
                adj = ProcessList.PERCEPTIBLE_APP_ADJ;
                procState = ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
                app.cached = false;
                app.adjType = "fg-service";
                schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
            } else if (app.forcingToForeground != null) {
                // The user is aware of this app, so make it visible.
                adj = ProcessList.PERCEPTIBLE_APP_ADJ;
                procState = ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
                app.cached = false;
                app.adjType = "force-fg";
                app.adjSource = app.forcingToForeground;
                schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
            }
        }

        if (app == mHeavyWeightProcess) {
            if (adj > ProcessList.HEAVY_WEIGHT_APP_ADJ) {
                // We don't want to kill the current heavy-weight process.
                adj = ProcessList.HEAVY_WEIGHT_APP_ADJ;
                schedGroup = ProcessList.SCHED_GROUP_BACKGROUND;
                app.cached = false;
                app.adjType = "heavy";
            }
            if (procState > ActivityManager.PROCESS_STATE_HEAVY_WEIGHT) {
                procState = ActivityManager.PROCESS_STATE_HEAVY_WEIGHT;
            }
        }

        if (app == mHomeProcess) {
            if (adj > ProcessList.HOME_APP_ADJ) {
                // This process is hosting what we currently consider to be the
                // home app, so we don't want to let it go into the background.
                adj = ProcessList.HOME_APP_ADJ;
                schedGroup = ProcessList.SCHED_GROUP_BACKGROUND;
                app.cached = false;
                app.adjType = "home";
            }
            if (procState > ActivityManager.PROCESS_STATE_HOME) {
                procState = ActivityManager.PROCESS_STATE_HOME;
            }
        }

        if (app == mPreviousProcess && app.activities.size() > 0) {
            if (adj > ProcessList.PREVIOUS_APP_ADJ) {
                // This was the previous process that showed UI to the user.
                // We want to try to keep it around more aggressively, to give
                // a good experience around switching between two apps.
                adj = ProcessList.PREVIOUS_APP_ADJ;
                schedGroup = ProcessList.SCHED_GROUP_BACKGROUND;
                app.cached = false;
                app.adjType = "previous";
            }
            if (procState > ActivityManager.PROCESS_STATE_LAST_ACTIVITY) {
                procState = ActivityManager.PROCESS_STATE_LAST_ACTIVITY;
            }
        }

        if (false) Slog.i(TAG, "OOM " + app + ": initial adj=" + adj
                + " reason=" + app.adjType);

        // By default, we use the computed adjustment.  It may be changed if
        // there are applications dependent on our services or providers, but
        // this gives us a baseline and makes sure we don't get into an
        // infinite recursion.
        app.adjSeq = mAdjSeq;
        app.curRawAdj = adj;
        app.hasStartedServices = false;

        if (mBackupTarget != null && app == mBackupTarget.app) {
            // If possible we want to avoid killing apps while they're being backed up
            if (adj > ProcessList.BACKUP_APP_ADJ) {
                if (DEBUG_BACKUP) Slog.v(TAG_BACKUP, "oom BACKUP_APP_ADJ for " + app);
                adj = ProcessList.BACKUP_APP_ADJ;
                if (procState > ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND) {
                    procState = ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
                }
                app.adjType = "backup";
                app.cached = false;
            }
            if (procState > ActivityManager.PROCESS_STATE_BACKUP) {
                procState = ActivityManager.PROCESS_STATE_BACKUP;
            }
        }

        boolean mayBeTop = false;

        for (int is = app.services.size()-1;
                is >= 0 && (adj > ProcessList.FOREGROUND_APP_ADJ
                        || schedGroup == ProcessList.SCHED_GROUP_BACKGROUND
                        || procState > ActivityManager.PROCESS_STATE_TOP);
                is--) {
            ServiceRecord s = app.services.valueAt(is);
            if (s.startRequested) {
                app.hasStartedServices = true;
                if (procState > ActivityManager.PROCESS_STATE_SERVICE) {
                    procState = ActivityManager.PROCESS_STATE_SERVICE;
                }
                if (app.hasShownUi && app != mHomeProcess) {
                    // If this process has shown some UI, let it immediately
                    // go to the LRU list because it may be pretty heavy with
                    // UI stuff.  We'll tag it with a label just to help
                    // debug and understand what is going on.
                    if (adj > ProcessList.SERVICE_ADJ) {
                        app.adjType = "cch-started-ui-services";
                    }
                } else {
                    if (now < (s.lastActivity + ActiveServices.MAX_SERVICE_INACTIVITY)) {
                        // This service has seen some activity within
                        // recent memory, so we will keep its process ahead
                        // of the background processes.
                        if (adj > ProcessList.SERVICE_ADJ) {
                            adj = ProcessList.SERVICE_ADJ;
                            app.adjType = "started-services";
                            app.cached = false;
                        }
                    }
                    // If we have let the service slide into the background
                    // state, still have some text describing what it is doing
                    // even though the service no longer has an impact.
                    if (adj > ProcessList.SERVICE_ADJ) {
                        app.adjType = "cch-started-services";
                    }
                }
            }

            for (int conni = s.connections.size()-1;
                    conni >= 0 && (adj > ProcessList.FOREGROUND_APP_ADJ
                            || schedGroup == ProcessList.SCHED_GROUP_BACKGROUND
                            || procState > ActivityManager.PROCESS_STATE_TOP);
                    conni--) {
                ArrayList<ConnectionRecord> clist = s.connections.valueAt(conni);
                for (int i = 0;
                        i < clist.size() && (adj > ProcessList.FOREGROUND_APP_ADJ
                                || schedGroup == ProcessList.SCHED_GROUP_BACKGROUND
                                || procState > ActivityManager.PROCESS_STATE_TOP);
                        i++) {
                    // XXX should compute this based on the max of
                    // all connected clients.
                    ConnectionRecord cr = clist.get(i);
                    if (cr.binding.client == app) {
                        // Binding to ourself is not interesting.
                        continue;
                    }

                    if ((cr.flags&Context.BIND_WAIVE_PRIORITY) == 0) {
                        ProcessRecord client = cr.binding.client;
                        int clientAdj = computeOomAdjLocked(client, cachedAdj,
                                TOP_APP, doingAll, now);
                        int clientProcState = client.curProcState;
                        if (clientProcState >= ActivityManager.PROCESS_STATE_CACHED_ACTIVITY) {
                            // If the other app is cached for any reason, for purposes here
                            // we are going to consider it empty.  The specific cached state
                            // doesn't propagate except under certain conditions.
                            clientProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
                        }
                        String adjType = null;
                        if ((cr.flags&Context.BIND_ALLOW_OOM_MANAGEMENT) != 0) {
                            // Not doing bind OOM management, so treat
                            // this guy more like a started service.
                            if (app.hasShownUi && app != mHomeProcess) {
                                // If this process has shown some UI, let it immediately
                                // go to the LRU list because it may be pretty heavy with
                                // UI stuff.  We'll tag it with a label just to help
                                // debug and understand what is going on.
                                if (adj > clientAdj) {
                                    adjType = "cch-bound-ui-services";
                                }
                                app.cached = false;
                                clientAdj = adj;
                                clientProcState = procState;
                            } else {
                                if (now >= (s.lastActivity
                                        + ActiveServices.MAX_SERVICE_INACTIVITY)) {
                                    // This service has not seen activity within
                                    // recent memory, so allow it to drop to the
                                    // LRU list if there is no other reason to keep
                                    // it around.  We'll also tag it with a label just
                                    // to help debug and undertand what is going on.
                                    if (adj > clientAdj) {
                                        adjType = "cch-bound-services";
                                    }
                                    clientAdj = adj;
                                }
                            }
                        }
                        if (adj > clientAdj) {
                            // If this process has recently shown UI, and
                            // the process that is binding to it is less
                            // important than being visible, then we don't
                            // care about the binding as much as we care
                            // about letting this process get into the LRU
                            // list to be killed and restarted if needed for
                            // memory.
                            if (app.hasShownUi && app != mHomeProcess
                                    && clientAdj > ProcessList.PERCEPTIBLE_APP_ADJ) {
                                adjType = "cch-bound-ui-services";
                            } else {
                                if ((cr.flags&(Context.BIND_ABOVE_CLIENT
                                        |Context.BIND_IMPORTANT)) != 0) {
                                    adj = clientAdj >= ProcessList.PERSISTENT_SERVICE_ADJ
                                            ? clientAdj : ProcessList.PERSISTENT_SERVICE_ADJ;
                                } else if ((cr.flags&Context.BIND_NOT_VISIBLE) != 0
                                        && clientAdj < ProcessList.PERCEPTIBLE_APP_ADJ
                                        && adj > ProcessList.PERCEPTIBLE_APP_ADJ) {
                                    adj = ProcessList.PERCEPTIBLE_APP_ADJ;
                                } else if (clientAdj >= ProcessList.PERCEPTIBLE_APP_ADJ) {
                                    adj = clientAdj;
                                } else {
                                    if (adj > ProcessList.VISIBLE_APP_ADJ) {
                                        adj = Math.max(clientAdj, ProcessList.VISIBLE_APP_ADJ);
                                    }
                                }
                                if (!client.cached) {
                                    app.cached = false;
                                }
                                adjType = "service";
                            }
                        }
                        if ((cr.flags&Context.BIND_NOT_FOREGROUND) == 0) {
                            // This will treat important bound services identically to
                            // the top app, which may behave differently than generic
                            // foreground work.
                            if (client.curSchedGroup > schedGroup) {
                                if ((cr.flags&Context.BIND_IMPORTANT) != 0) {
                                    schedGroup = client.curSchedGroup;
                                } else {
                                    schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
                                }
                            }
                            if (clientProcState <= ActivityManager.PROCESS_STATE_TOP) {
                                if (clientProcState == ActivityManager.PROCESS_STATE_TOP) {
                                    // Special handling of clients who are in the top state.
                                    // We *may* want to consider this process to be in the
                                    // top state as well, but only if there is not another
                                    // reason for it to be running.  Being on the top is a
                                    // special state, meaning you are specifically running
                                    // for the current top app.  If the process is already
                                    // running in the background for some other reason, it
                                    // is more important to continue considering it to be
                                    // in the background state.
                                    mayBeTop = true;
                                    clientProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
                                } else {
                                    // Special handling for above-top states (persistent
                                    // processes).  These should not bring the current process
                                    // into the top state, since they are not on top.  Instead
                                    // give them the best state after that.
                                    if ((cr.flags&Context.BIND_FOREGROUND_SERVICE) != 0) {
                                        clientProcState =
                                                ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
                                    } else if (mWakefulness
                                                    == PowerManagerInternal.WAKEFULNESS_AWAKE &&
                                            (cr.flags&Context.BIND_FOREGROUND_SERVICE_WHILE_AWAKE)
                                                    != 0) {
                                        clientProcState =
                                                ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
                                    } else {
                                        clientProcState =
                                                ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
                                    }
                                }
                            }
                        } else {
                            if (clientProcState <
                                    ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND) {
                                clientProcState =
                                        ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
                            }
                        }
                        if (procState > clientProcState) {
                            procState = clientProcState;
                        }
                        if (procState < ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND
                                && (cr.flags&Context.BIND_SHOWING_UI) != 0) {
                            app.pendingUiClean = true;
                        }
                        if (adjType != null) {
                            app.adjType = adjType;
                            app.adjTypeCode = ActivityManager.RunningAppProcessInfo
                                    .REASON_SERVICE_IN_USE;
                            app.adjSource = cr.binding.client;
                            app.adjSourceProcState = clientProcState;
                            app.adjTarget = s.name;
                        }
                    }
                    if ((cr.flags&Context.BIND_TREAT_LIKE_ACTIVITY) != 0) {
                        app.treatLikeActivity = true;
                    }
                    final ActivityRecord a = cr.activity;
                    if ((cr.flags&Context.BIND_ADJUST_WITH_ACTIVITY) != 0) {
                        if (a != null && adj > ProcessList.FOREGROUND_APP_ADJ &&
                            (a.visible || a.state == ActivityState.RESUMED ||
                             a.state == ActivityState.PAUSING)) {
                            adj = ProcessList.FOREGROUND_APP_ADJ;
                            if ((cr.flags&Context.BIND_NOT_FOREGROUND) == 0) {
                                if ((cr.flags&Context.BIND_IMPORTANT) != 0) {
                                    schedGroup = ProcessList.SCHED_GROUP_TOP_APP_BOUND;
                                } else {
                                    schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
                                }
                            }
                            app.cached = false;
                            app.adjType = "service";
                            app.adjTypeCode = ActivityManager.RunningAppProcessInfo
                                    .REASON_SERVICE_IN_USE;
                            app.adjSource = a;
                            app.adjSourceProcState = procState;
                            app.adjTarget = s.name;
                        }
                    }
                }
            }
        }

        for (int provi = app.pubProviders.size()-1;
                provi >= 0 && (adj > ProcessList.FOREGROUND_APP_ADJ
                        || schedGroup == ProcessList.SCHED_GROUP_BACKGROUND
                        || procState > ActivityManager.PROCESS_STATE_TOP);
                provi--) {
            ContentProviderRecord cpr = app.pubProviders.valueAt(provi);
            for (int i = cpr.connections.size()-1;
                    i >= 0 && (adj > ProcessList.FOREGROUND_APP_ADJ
                            || schedGroup == ProcessList.SCHED_GROUP_BACKGROUND
                            || procState > ActivityManager.PROCESS_STATE_TOP);
                    i--) {
                ContentProviderConnection conn = cpr.connections.get(i);
                ProcessRecord client = conn.client;
                if (client == app) {
                    // Being our own client is not interesting.
                    continue;
                }
                int clientAdj = computeOomAdjLocked(client, cachedAdj, TOP_APP, doingAll, now);
                int clientProcState = client.curProcState;
                if (clientProcState >= ActivityManager.PROCESS_STATE_CACHED_ACTIVITY) {
                    // If the other app is cached for any reason, for purposes here
                    // we are going to consider it empty.
                    clientProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
                }
                if (adj > clientAdj) {
                    if (app.hasShownUi && app != mHomeProcess
                            && clientAdj > ProcessList.PERCEPTIBLE_APP_ADJ) {
                        app.adjType = "cch-ui-provider";
                    } else {
                        adj = clientAdj > ProcessList.FOREGROUND_APP_ADJ
                                ? clientAdj : ProcessList.FOREGROUND_APP_ADJ;
                        app.adjType = "provider";
                    }
                    app.cached &= client.cached;
                    app.adjTypeCode = ActivityManager.RunningAppProcessInfo
                            .REASON_PROVIDER_IN_USE;
                    app.adjSource = client;
                    app.adjSourceProcState = clientProcState;
                    app.adjTarget = cpr.name;
                }
                if (clientProcState <= ActivityManager.PROCESS_STATE_TOP) {
                    if (clientProcState == ActivityManager.PROCESS_STATE_TOP) {
                        // Special handling of clients who are in the top state.
                        // We *may* want to consider this process to be in the
                        // top state as well, but only if there is not another
                        // reason for it to be running.  Being on the top is a
                        // special state, meaning you are specifically running
                        // for the current top app.  If the process is already
                        // running in the background for some other reason, it
                        // is more important to continue considering it to be
                        // in the background state.
                        mayBeTop = true;
                        clientProcState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
                    } else {
                        // Special handling for above-top states (persistent
                        // processes).  These should not bring the current process
                        // into the top state, since they are not on top.  Instead
                        // give them the best state after that.
                        clientProcState =
                                ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
                    }
                }
                if (procState > clientProcState) {
                    procState = clientProcState;
                }
                if (client.curSchedGroup > schedGroup) {
                    schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
                }
            }
            // If the provider has external (non-framework) process
            // dependencies, ensure that its adjustment is at least
            // FOREGROUND_APP_ADJ.
            if (cpr.hasExternalProcessHandles()) {
                if (adj > ProcessList.FOREGROUND_APP_ADJ) {
                    adj = ProcessList.FOREGROUND_APP_ADJ;
                    schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
                    app.cached = false;
                    app.adjType = "provider";
                    app.adjTarget = cpr.name;
                }
                if (procState > ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND) {
                    procState = ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
                }
            }
        }

        if (app.lastProviderTime > 0 && (app.lastProviderTime+CONTENT_PROVIDER_RETAIN_TIME) > now) {
            if (adj > ProcessList.PREVIOUS_APP_ADJ) {
                adj = ProcessList.PREVIOUS_APP_ADJ;
                schedGroup = ProcessList.SCHED_GROUP_BACKGROUND;
                app.cached = false;
                app.adjType = "provider";
            }
            if (procState > ActivityManager.PROCESS_STATE_LAST_ACTIVITY) {
                procState = ActivityManager.PROCESS_STATE_LAST_ACTIVITY;
            }
        }

        if (mayBeTop && procState > ActivityManager.PROCESS_STATE_TOP) {
            // A client of one of our services or providers is in the top state.  We
            // *may* want to be in the top state, but not if we are already running in
            // the background for some other reason.  For the decision here, we are going
            // to pick out a few specific states that we want to remain in when a client
            // is top (states that tend to be longer-term) and otherwise allow it to go
            // to the top state.
            switch (procState) {
                case ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND:
                case ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND:
                case ActivityManager.PROCESS_STATE_SERVICE:
                    // These all are longer-term states, so pull them up to the top
                    // of the background states, but not all the way to the top state.
                    procState = ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
                    break;
                default:
                    // Otherwise, top is a better choice, so take it.
                    procState = ActivityManager.PROCESS_STATE_TOP;
                    break;
            }
        }

        if (procState >= ActivityManager.PROCESS_STATE_CACHED_EMPTY) {
            if (app.hasClientActivities) {
                // This is a cached process, but with client activities.  Mark it so.
                procState = ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT;
                app.adjType = "cch-client-act";
            } else if (app.treatLikeActivity) {
                // This is a cached process, but somebody wants us to treat it like it has
                // an activity, okay!
                procState = ActivityManager.PROCESS_STATE_CACHED_ACTIVITY;
                app.adjType = "cch-as-act";
            }
        }

        if (adj == ProcessList.SERVICE_ADJ) {
            if (doingAll) {
                app.serviceb = mNewNumAServiceProcs > (mNumServiceProcs/3);
                mNewNumServiceProcs++;
                //Slog.i(TAG, "ADJ " + app + " serviceb=" + app.serviceb);
                if (!app.serviceb) {
                    // This service isn't far enough down on the LRU list to
                    // normally be a B service, but if we are low on RAM and it
                    // is large we want to force it down since we would prefer to
                    // keep launcher over it.
                    if (mLastMemoryLevel > ProcessStats.ADJ_MEM_FACTOR_NORMAL
                            && app.lastPss >= mProcessList.getCachedRestoreThresholdKb()) {
                        app.serviceHighRam = true;
                        app.serviceb = true;
                        //Slog.i(TAG, "ADJ " + app + " high ram!");
                    } else {
                        mNewNumAServiceProcs++;
                        //Slog.i(TAG, "ADJ " + app + " not high ram!");
                    }
                } else {
                    app.serviceHighRam = false;
                }
            }
            if (app.serviceb) {
                adj = ProcessList.SERVICE_B_ADJ;
            }
        }

        app.curRawAdj = adj;

        //Slog.i(TAG, "OOM ADJ " + app + ": pid=" + app.pid +
        //      " adj=" + adj + " curAdj=" + app.curAdj + " maxAdj=" + app.maxAdj);
        if (adj > app.maxAdj) {
            adj = app.maxAdj;
            if (app.maxAdj <= ProcessList.PERCEPTIBLE_APP_ADJ) {
                schedGroup = ProcessList.SCHED_GROUP_DEFAULT;
            }
        }

        // Do final modification to adj.  Everything we do between here and applying
        // the final setAdj must be done in this function, because we will also use
        // it when computing the final cached adj later.  Note that we don't need to
        // worry about this for max adj above, since max adj will always be used to
        // keep it out of the cached vaues.
        app.curAdj = app.modifyRawOomAdj(adj);
        app.curSchedGroup = schedGroup;
        app.curProcState = procState;
        app.foregroundActivities = foregroundActivities;

        return app.curRawAdj;
    }

    /**
     * Record new PSS sample for a process.
     */
    void recordPssSampleLocked(ProcessRecord proc, int procState, long pss, long uss, long swapPss,
            long now) {
        EventLogTags.writeAmPss(proc.pid, proc.uid, proc.processName, pss * 1024, uss * 1024,
                swapPss * 1024);
        proc.lastPssTime = now;
        proc.baseProcessTracker.addPss(pss, uss, true, proc.pkgList);
        if (DEBUG_PSS) Slog.d(TAG_PSS,
                "PSS of " + proc.toShortString() + ": " + pss + " lastPss=" + proc.lastPss
                + " state=" + ProcessList.makeProcStateString(procState));
        if (proc.initialIdlePss == 0) {
            proc.initialIdlePss = pss;
        }
        proc.lastPss = pss;
        proc.lastSwapPss = swapPss;
        if (procState >= ActivityManager.PROCESS_STATE_HOME) {
            proc.lastCachedPss = pss;
            proc.lastCachedSwapPss = swapPss;
        }

        final SparseArray<Pair<Long, String>> watchUids
                = mMemWatchProcesses.getMap().get(proc.processName);
        Long check = null;
        if (watchUids != null) {
            Pair<Long, String> val = watchUids.get(proc.uid);
            if (val == null) {
                val = watchUids.get(0);
            }
            if (val != null) {
                check = val.first;
            }
        }
        if (check != null) {
            if ((pss * 1024) >= check && proc.thread != null && mMemWatchDumpProcName == null) {
                boolean isDebuggable = "1".equals(SystemProperties.get(SYSTEM_DEBUGGABLE, "0"));
                if (!isDebuggable) {
                    if ((proc.info.flags&ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                        isDebuggable = true;
                    }
                }
                if (isDebuggable) {
                    Slog.w(TAG, "Process " + proc + " exceeded pss limit " + check + "; reporting");
                    final ProcessRecord myProc = proc;
                    final File heapdumpFile = DumpHeapProvider.getJavaFile();
                    mMemWatchDumpProcName = proc.processName;
                    mMemWatchDumpFile = heapdumpFile.toString();
                    mMemWatchDumpPid = proc.pid;
                    mMemWatchDumpUid = proc.uid;
                    BackgroundThread.getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            revokeUriPermission(ActivityThread.currentActivityThread()
                                            .getApplicationThread(),
                                    DumpHeapActivity.JAVA_URI,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                    UserHandle.myUserId());
                            ParcelFileDescriptor fd = null;
                            try {
                                heapdumpFile.delete();
                                fd = ParcelFileDescriptor.open(heapdumpFile,
                                        ParcelFileDescriptor.MODE_CREATE |
                                                ParcelFileDescriptor.MODE_TRUNCATE |
                                                ParcelFileDescriptor.MODE_WRITE_ONLY |
                                                ParcelFileDescriptor.MODE_APPEND);
                                IApplicationThread thread = myProc.thread;
                                if (thread != null) {
                                    try {
                                        if (DEBUG_PSS) Slog.d(TAG_PSS,
                                                "Requesting dump heap from "
                                                + myProc + " to " + heapdumpFile);
                                        thread.dumpHeap(true, heapdumpFile.toString(), fd);
                                    } catch (RemoteException e) {
                                    }
                                }
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            } finally {
                                if (fd != null) {
                                    try {
                                        fd.close();
                                    } catch (IOException e) {
                                    }
                                }
                            }
                        }
                    });
                } else {
                    Slog.w(TAG, "Process " + proc + " exceeded pss limit " + check
                            + ", but debugging not enabled");
                }
            }
        }
    }

    /**
     * Schedule PSS collection of a process.
     */
    void requestPssLocked(ProcessRecord proc, int procState) {
        if (mPendingPssProcesses.contains(proc)) {
            return;
        }
        if (mPendingPssProcesses.size() == 0) {
            mBgHandler.sendEmptyMessage(COLLECT_PSS_BG_MSG);
        }
        if (DEBUG_PSS) Slog.d(TAG_PSS, "Requesting PSS of: " + proc);
        proc.pssProcState = procState;
        mPendingPssProcesses.add(proc);
    }

    /**
     * Schedule PSS collection of all processes.
     */
    void requestPssAllProcsLocked(long now, boolean always, boolean memLowered) {
        if (!always) {
            if (now < (mLastFullPssTime +
                    (memLowered ? FULL_PSS_LOWERED_INTERVAL : FULL_PSS_MIN_INTERVAL))) {
                return;
            }
        }
        if (DEBUG_PSS) Slog.d(TAG_PSS, "Requesting PSS of all procs!  memLowered=" + memLowered);
        mLastFullPssTime = now;
        mFullPssPending = true;
        mPendingPssProcesses.ensureCapacity(mLruProcesses.size());
        mPendingPssProcesses.clear();
        for (int i = mLruProcesses.size() - 1; i >= 0; i--) {
            ProcessRecord app = mLruProcesses.get(i);
            if (app.thread == null
                    || app.curProcState == ActivityManager.PROCESS_STATE_NONEXISTENT) {
                continue;
            }
            if (memLowered || now > (app.lastStateTime+ProcessList.PSS_ALL_INTERVAL)) {
                app.pssProcState = app.setProcState;
                app.nextPssTime = ProcessList.computeNextPssTime(app.curProcState, true,
                        mTestPssMode, isSleepingLocked(), now);
                mPendingPssProcesses.add(app);
            }
        }
        mBgHandler.sendEmptyMessage(COLLECT_PSS_BG_MSG);
    }

    public void setTestPssMode(boolean enabled) {
        synchronized (this) {
            mTestPssMode = enabled;
            if (enabled) {
                // Whenever we enable the mode, we want to take a snapshot all of current
                // process mem use.
                requestPssAllProcsLocked(SystemClock.uptimeMillis(), true, true);
            }
        }
    }

    /**
     * Ask a given process to GC right now.
     */
    final void performAppGcLocked(ProcessRecord app) {
        try {
            app.lastRequestedGc = SystemClock.uptimeMillis();
            if (app.thread != null) {
                if (app.reportLowMemory) {
                    app.reportLowMemory = false;
                    app.thread.scheduleLowMemory();
                } else {
                    app.thread.processInBackground();
                }
            }
        } catch (Exception e) {
            // whatever.
        }
    }

    /**
     * Returns true if things are idle enough to perform GCs.
     */
    private final boolean canGcNowLocked() {
        boolean processingBroadcasts = false;
        for (BroadcastQueue q : mBroadcastQueues) {
            if (q.mParallelBroadcasts.size() != 0 || q.mOrderedBroadcasts.size() != 0) {
                processingBroadcasts = true;
            }
        }
        return !processingBroadcasts
                && (isSleepingLocked() || mStackSupervisor.allResumedActivitiesIdle());
    }

    /**
     * Perform GCs on all processes that are waiting for it, but only
     * if things are idle.
     */
    final void performAppGcsLocked() {
        final int N = mProcessesToGc.size();
        if (N <= 0) {
            return;
        }
        if (canGcNowLocked()) {
            while (mProcessesToGc.size() > 0) {
                ProcessRecord proc = mProcessesToGc.remove(0);
                if (proc.curRawAdj > ProcessList.PERCEPTIBLE_APP_ADJ || proc.reportLowMemory) {
                    if ((proc.lastRequestedGc+GC_MIN_INTERVAL)
                            <= SystemClock.uptimeMillis()) {
                        // To avoid spamming the system, we will GC processes one
                        // at a time, waiting a few seconds between each.
                        performAppGcLocked(proc);
                        scheduleAppGcsLocked();
                        return;
                    } else {
                        // It hasn't been long enough since we last GCed this
                        // process...  put it in the list to wait for its time.
                        addProcessToGcListLocked(proc);
                        break;
                    }
                }
            }

            scheduleAppGcsLocked();
        }
    }

    /**
     * If all looks good, perform GCs on all processes waiting for them.
     */
    final void performAppGcsIfAppropriateLocked() {
        if (canGcNowLocked()) {
            performAppGcsLocked();
            return;
        }
        // Still not idle, wait some more.
        scheduleAppGcsLocked();
    }

    /**
     * Schedule the execution of all pending app GCs.
     */
    final void scheduleAppGcsLocked() {
        mHandler.removeMessages(GC_BACKGROUND_PROCESSES_MSG);

        if (mProcessesToGc.size() > 0) {
            // Schedule a GC for the time to the next process.
            ProcessRecord proc = mProcessesToGc.get(0);
            Message msg = mHandler.obtainMessage(GC_BACKGROUND_PROCESSES_MSG);

            long when = proc.lastRequestedGc + GC_MIN_INTERVAL;
            long now = SystemClock.uptimeMillis();
            if (when < (now+GC_TIMEOUT)) {
                when = now + GC_TIMEOUT;
            }
            mHandler.sendMessageAtTime(msg, when);
        }
    }

    /**
     * Add a process to the array of processes waiting to be GCed.  Keeps the
     * list in sorted order by the last GC time.  The process can't already be
     * on the list.
     */
    final void addProcessToGcListLocked(ProcessRecord proc) {
        boolean added = false;
        for (int i=mProcessesToGc.size()-1; i>=0; i--) {
            if (mProcessesToGc.get(i).lastRequestedGc <
                    proc.lastRequestedGc) {
                added = true;
                mProcessesToGc.add(i+1, proc);
                break;
            }
        }
        if (!added) {
            mProcessesToGc.add(0, proc);
        }
    }

    /**
     * Set up to ask a process to GC itself.  This will either do it
     * immediately, or put it on the list of processes to gc the next
     * time things are idle.
     */
    final void scheduleAppGcLocked(ProcessRecord app) {
        long now = SystemClock.uptimeMillis();
        if ((app.lastRequestedGc+GC_MIN_INTERVAL) > now) {
            return;
        }
        if (!mProcessesToGc.contains(app)) {
            addProcessToGcListLocked(app);
            scheduleAppGcsLocked();
        }
    }

    final void checkExcessivePowerUsageLocked(boolean doKills) {
        updateCpuStatsNow();

        BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
        boolean doWakeKills = doKills;
        boolean doCpuKills = doKills;
        if (mLastPowerCheckRealtime == 0) {
            doWakeKills = false;
        }
        if (mLastPowerCheckUptime == 0) {
            doCpuKills = false;
        }
        if (stats.isScreenOn()) {
            doWakeKills = false;
        }
        final long curRealtime = SystemClock.elapsedRealtime();
        final long realtimeSince = curRealtime - mLastPowerCheckRealtime;
        final long curUptime = SystemClock.uptimeMillis();
        final long uptimeSince = curUptime - mLastPowerCheckUptime;
        mLastPowerCheckRealtime = curRealtime;
        mLastPowerCheckUptime = curUptime;
        if (realtimeSince < WAKE_LOCK_MIN_CHECK_DURATION) {
            doWakeKills = false;
        }
        if (uptimeSince < CPU_MIN_CHECK_DURATION) {
            doCpuKills = false;
        }
        int i = mLruProcesses.size();
        while (i > 0) {
            i--;
            ProcessRecord app = mLruProcesses.get(i);
            if (app.setProcState >= ActivityManager.PROCESS_STATE_HOME) {
                long wtime;
                synchronized (stats) {
                    wtime = stats.getProcessWakeTime(app.info.uid,
                            app.pid, curRealtime);
                }
                long wtimeUsed = wtime - app.lastWakeTime;
                long cputimeUsed = app.curCpuTime - app.lastCpuTime;
                if (DEBUG_POWER) {
                    StringBuilder sb = new StringBuilder(128);
                    sb.append("Wake for ");
                    app.toShortString(sb);
                    sb.append(": over ");
                    TimeUtils.formatDuration(realtimeSince, sb);
                    sb.append(" used ");
                    TimeUtils.formatDuration(wtimeUsed, sb);
                    sb.append(" (");
                    sb.append((wtimeUsed*100)/realtimeSince);
                    sb.append("%)");
                    Slog.i(TAG_POWER, sb.toString());
                    sb.setLength(0);
                    sb.append("CPU for ");
                    app.toShortString(sb);
                    sb.append(": over ");
                    TimeUtils.formatDuration(uptimeSince, sb);
                    sb.append(" used ");
                    TimeUtils.formatDuration(cputimeUsed, sb);
                    sb.append(" (");
                    sb.append((cputimeUsed*100)/uptimeSince);
                    sb.append("%)");
                    Slog.i(TAG_POWER, sb.toString());
                }
                // If a process has held a wake lock for more
                // than 50% of the time during this period,
                // that sounds bad.  Kill!
                if (doWakeKills && realtimeSince > 0
                        && ((wtimeUsed*100)/realtimeSince) >= 50) {
                    synchronized (stats) {
                        stats.reportExcessiveWakeLocked(app.info.uid, app.processName,
                                realtimeSince, wtimeUsed);
                    }
                    app.kill("excessive wake held " + wtimeUsed + " during " + realtimeSince, true);
                    app.baseProcessTracker.reportExcessiveWake(app.pkgList);
                } else if (doCpuKills && uptimeSince > 0
                        && ((cputimeUsed*100)/uptimeSince) >= 25) {
                    synchronized (stats) {
                        stats.reportExcessiveCpuLocked(app.info.uid, app.processName,
                                uptimeSince, cputimeUsed);
                    }
                    app.kill("excessive cpu " + cputimeUsed + " during " + uptimeSince, true);
                    app.baseProcessTracker.reportExcessiveCpu(app.pkgList);
                } else {
                    app.lastWakeTime = wtime;
                    app.lastCpuTime = app.curCpuTime;
                }
            }
        }
    }

    private final boolean applyOomAdjLocked(ProcessRecord app, boolean doingAll, long now,
            long nowElapsed) {
        boolean success = true;

        if (app.curRawAdj != app.setRawAdj) {
            app.setRawAdj = app.curRawAdj;
        }

        int changes = 0;

        if (app.curAdj != app.setAdj) {
            ProcessList.setOomAdj(app.pid, app.info.uid, app.curAdj);
            if (DEBUG_SWITCH || DEBUG_OOM_ADJ) Slog.v(TAG_OOM_ADJ,
                    "Set " + app.pid + " " + app.processName + " adj " + app.curAdj + ": "
                    + app.adjType);
            app.setAdj = app.curAdj;
            app.verifiedAdj = ProcessList.INVALID_ADJ;
        }

        if (app.setSchedGroup != app.curSchedGroup) {
            int oldSchedGroup = app.setSchedGroup;
            app.setSchedGroup = app.curSchedGroup;
            if (DEBUG_SWITCH || DEBUG_OOM_ADJ) Slog.v(TAG_OOM_ADJ,
                    "Setting sched group of " + app.processName
                    + " to " + app.curSchedGroup);
            if (app.waitingToKill != null && app.curReceiver == null
                    && app.setSchedGroup == ProcessList.SCHED_GROUP_BACKGROUND) {
                app.kill(app.waitingToKill, true);
                success = false;
            } else {
                int processGroup;
                switch (app.curSchedGroup) {
                    case ProcessList.SCHED_GROUP_BACKGROUND:
                        processGroup = Process.THREAD_GROUP_BG_NONINTERACTIVE;
                        break;
                    case ProcessList.SCHED_GROUP_TOP_APP:
                    case ProcessList.SCHED_GROUP_TOP_APP_BOUND:
                        processGroup = Process.THREAD_GROUP_TOP_APP;
                        break;
                    default:
                        processGroup = Process.THREAD_GROUP_DEFAULT;
                        break;
                }
                long oldId = Binder.clearCallingIdentity();
                try {
                    Process.setProcessGroup(app.pid, processGroup);
                    if (app.curSchedGroup == ProcessList.SCHED_GROUP_TOP_APP) {
                        // do nothing if we already switched to RT
                        if (oldSchedGroup != ProcessList.SCHED_GROUP_TOP_APP) {
                            // Switch VR thread for app to SCHED_FIFO
                            if (mInVrMode && app.vrThreadTid != 0) {
                                try {
                                    Process.setThreadScheduler(app.vrThreadTid,
                                        Process.SCHED_FIFO | Process.SCHED_RESET_ON_FORK, 1);
                                } catch (IllegalArgumentException e) {
                                    // thread died, ignore
                                }
                            }
                            if (mUseFifoUiScheduling) {
                                // Switch UI pipeline for app to SCHED_FIFO
                                app.savedPriority = Process.getThreadPriority(app.pid);
                                try {
                                    Process.setThreadScheduler(app.pid,
                                        Process.SCHED_FIFO | Process.SCHED_RESET_ON_FORK, 1);
                                } catch (IllegalArgumentException e) {
                                    // thread died, ignore
                                }
                                if (app.renderThreadTid != 0) {
                                    try {
                                        Process.setThreadScheduler(app.renderThreadTid,
                                            Process.SCHED_FIFO | Process.SCHED_RESET_ON_FORK, 1);
                                    } catch (IllegalArgumentException e) {
                                        // thread died, ignore
                                    }
                                    if (DEBUG_OOM_ADJ) {
                                        Slog.d("UI_FIFO", "Set RenderThread (TID " +
                                            app.renderThreadTid + ") to FIFO");
                                    }
                                } else {
                                    if (DEBUG_OOM_ADJ) {
                                        Slog.d("UI_FIFO", "Not setting RenderThread TID");
                                    }
                                }
                            } else {
                                // Boost priority for top app UI and render threads
                                Process.setThreadPriority(app.pid, -10);
                                if (app.renderThreadTid != 0) {
                                    try {
                                        Process.setThreadPriority(app.renderThreadTid, -10);
                                    } catch (IllegalArgumentException e) {
                                        // thread died, ignore
                                    }
                                }
                            }
                        }
                    } else if (oldSchedGroup == ProcessList.SCHED_GROUP_TOP_APP &&
                               app.curSchedGroup != ProcessList.SCHED_GROUP_TOP_APP) {
                        // Reset VR thread to SCHED_OTHER
                        // Safe to do even if we're not in VR mode
                        if (app.vrThreadTid != 0) {
                            Process.setThreadScheduler(app.vrThreadTid, Process.SCHED_OTHER, 0);
                        }
                        if (mUseFifoUiScheduling) {
                            // Reset UI pipeline to SCHED_OTHER
                            Process.setThreadScheduler(app.pid, Process.SCHED_OTHER, 0);
                            Process.setThreadPriority(app.pid, app.savedPriority);
                            if (app.renderThreadTid != 0) {
                                Process.setThreadScheduler(app.renderThreadTid,
                                    Process.SCHED_OTHER, 0);
                                Process.setThreadPriority(app.renderThreadTid, -4);
                            }
                        } else {
                            // Reset priority for top app UI and render threads
                            Process.setThreadPriority(app.pid, 0);
                            if (app.renderThreadTid != 0) {
                                Process.setThreadPriority(app.renderThreadTid, 0);
                            }
                        }
                    }
                } catch (Exception e) {
                    Slog.w(TAG, "Failed setting process group of " + app.pid
                            + " to " + app.curSchedGroup);
                    e.printStackTrace();
                } finally {
                    Binder.restoreCallingIdentity(oldId);
                }
            }
        }
        if (app.repForegroundActivities != app.foregroundActivities) {
            app.repForegroundActivities = app.foregroundActivities;
            changes |= ProcessChangeItem.CHANGE_ACTIVITIES;
        }
        if (app.repProcState != app.curProcState) {
            app.repProcState = app.curProcState;
            changes |= ProcessChangeItem.CHANGE_PROCESS_STATE;
            if (app.thread != null) {
                try {
                    if (false) {
                        //RuntimeException h = new RuntimeException("here");
                        Slog.i(TAG, "Sending new process state " + app.repProcState
                                + " to " + app /*, h*/);
                    }
                    app.thread.setProcessState(app.repProcState);
                } catch (RemoteException e) {
                }
            }
        }
        if (app.setProcState == ActivityManager.PROCESS_STATE_NONEXISTENT
                || ProcessList.procStatesDifferForMem(app.curProcState, app.setProcState)) {
            if (false && mTestPssMode && app.setProcState >= 0 && app.lastStateTime <= (now-200)) {
                // Experimental code to more aggressively collect pss while
                // running test...  the problem is that this tends to collect
                // the data right when a process is transitioning between process
                // states, which well tend to give noisy data.
                long start = SystemClock.uptimeMillis();
                long pss = Debug.getPss(app.pid, mTmpLong, null);
                recordPssSampleLocked(app, app.curProcState, pss, mTmpLong[0], mTmpLong[1], now);
                mPendingPssProcesses.remove(app);
                Slog.i(TAG, "Recorded pss for " + app + " state " + app.setProcState
                        + " to " + app.curProcState + ": "
                        + (SystemClock.uptimeMillis()-start) + "ms");
            }
            app.lastStateTime = now;
            app.nextPssTime = ProcessList.computeNextPssTime(app.curProcState, true,
                    mTestPssMode, isSleepingLocked(), now);
            if (DEBUG_PSS) Slog.d(TAG_PSS, "Process state change from "
                    + ProcessList.makeProcStateString(app.setProcState) + " to "
                    + ProcessList.makeProcStateString(app.curProcState) + " next pss in "
                    + (app.nextPssTime-now) + ": " + app);
        } else {
            if (now > app.nextPssTime || (now > (app.lastPssTime+ProcessList.PSS_MAX_INTERVAL)
                    && now > (app.lastStateTime+ProcessList.minTimeFromStateChange(
                    mTestPssMode)))) {
                requestPssLocked(app, app.setProcState);
                app.nextPssTime = ProcessList.computeNextPssTime(app.curProcState, false,
                        mTestPssMode, isSleepingLocked(), now);
            } else if (false && DEBUG_PSS) Slog.d(TAG_PSS,
                    "Not requesting PSS of " + app + ": next=" + (app.nextPssTime-now));
        }
        if (app.setProcState != app.curProcState) {
            if (DEBUG_SWITCH || DEBUG_OOM_ADJ) Slog.v(TAG_OOM_ADJ,
                    "Proc state change of " + app.processName
                            + " to " + app.curProcState);
            boolean setImportant = app.setProcState < ActivityManager.PROCESS_STATE_SERVICE;
            boolean curImportant = app.curProcState < ActivityManager.PROCESS_STATE_SERVICE;
            if (setImportant && !curImportant) {
                // This app is no longer something we consider important enough to allow to
                // use arbitrary amounts of battery power.  Note
                // its current wake lock time to later know to kill it if
                // it is not behaving well.
                BatteryStatsImpl stats = mBatteryStatsService.getActiveStatistics();
                synchronized (stats) {
                    app.lastWakeTime = stats.getProcessWakeTime(app.info.uid,
                            app.pid, nowElapsed);
                }
                app.lastCpuTime = app.curCpuTime;

            }
            // Inform UsageStats of important process state change
            // Must be called before updating setProcState
            maybeUpdateUsageStatsLocked(app, nowElapsed);

            app.setProcState = app.curProcState;
            if (app.setProcState >= ActivityManager.PROCESS_STATE_HOME) {
                app.notCachedSinceIdle = false;
            }
            if (!doingAll) {
                setProcessTrackerStateLocked(app, mProcessStats.getMemFactorLocked(), now);
            } else {
                app.procStateChanged = true;
            }
        } else if (app.reportedInteraction && (nowElapsed-app.interactionEventTime)
                > USAGE_STATS_INTERACTION_INTERVAL) {
            // For apps that sit around for a long time in the interactive state, we need
            // to report this at least once a day so they don't go idle.
            maybeUpdateUsageStatsLocked(app, nowElapsed);
        }

        if (changes != 0) {
            if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                    "Changes in " + app + ": " + changes);
            int i = mPendingProcessChanges.size()-1;
            ProcessChangeItem item = null;
            while (i >= 0) {
                item = mPendingProcessChanges.get(i);
                if (item.pid == app.pid) {
                    if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                            "Re-using existing item: " + item);
                    break;
                }
                i--;
            }
            if (i < 0) {
                // No existing item in pending changes; need a new one.
                final int NA = mAvailProcessChanges.size();
                if (NA > 0) {
                    item = mAvailProcessChanges.remove(NA-1);
                    if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                            "Retrieving available item: " + item);
                } else {
                    item = new ProcessChangeItem();
                    if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                            "Allocating new item: " + item);
                }
                item.changes = 0;
                item.pid = app.pid;
                item.uid = app.info.uid;
                if (mPendingProcessChanges.size() == 0) {
                    if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                            "*** Enqueueing dispatch processes changed!");
                    mUiHandler.obtainMessage(DISPATCH_PROCESSES_CHANGED_UI_MSG).sendToTarget();
                }
                mPendingProcessChanges.add(item);
            }
            item.changes |= changes;
            item.processState = app.repProcState;
            item.foregroundActivities = app.repForegroundActivities;
            if (DEBUG_PROCESS_OBSERVERS) Slog.i(TAG_PROCESS_OBSERVERS,
                    "Item " + Integer.toHexString(System.identityHashCode(item))
                    + " " + app.toShortString() + ": changes=" + item.changes
                    + " procState=" + item.processState
                    + " foreground=" + item.foregroundActivities
                    + " type=" + app.adjType + " source=" + app.adjSource
                    + " target=" + app.adjTarget);
        }

        return success;
    }

    private final void enqueueUidChangeLocked(UidRecord uidRec, int uid, int change) {
        final UidRecord.ChangeItem pendingChange;
        if (uidRec == null || uidRec.pendingChange == null) {
            if (mPendingUidChanges.size() == 0) {
                if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                        "*** Enqueueing dispatch uid changed!");
                mUiHandler.obtainMessage(DISPATCH_UIDS_CHANGED_UI_MSG).sendToTarget();
            }
            final int NA = mAvailUidChanges.size();
            if (NA > 0) {
                pendingChange = mAvailUidChanges.remove(NA-1);
                if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                        "Retrieving available item: " + pendingChange);
            } else {
                pendingChange = new UidRecord.ChangeItem();
                if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                        "Allocating new item: " + pendingChange);
            }
            if (uidRec != null) {
                uidRec.pendingChange = pendingChange;
                if (change == UidRecord.CHANGE_GONE && !uidRec.idle) {
                    // If this uid is going away, and we haven't yet reported it is gone,
                    // then do so now.
                    change = UidRecord.CHANGE_GONE_IDLE;
                }
            } else if (uid < 0) {
                throw new IllegalArgumentException("No UidRecord or uid");
            }
            pendingChange.uidRecord = uidRec;
            pendingChange.uid = uidRec != null ? uidRec.uid : uid;
            mPendingUidChanges.add(pendingChange);
        } else {
            pendingChange = uidRec.pendingChange;
            if (change == UidRecord.CHANGE_GONE && pendingChange.change == UidRecord.CHANGE_IDLE) {
                change = UidRecord.CHANGE_GONE_IDLE;
            }
        }
        pendingChange.change = change;
        pendingChange.processState = uidRec != null
                ? uidRec.setProcState : ActivityManager.PROCESS_STATE_NONEXISTENT;
    }

    private void maybeUpdateProviderUsageStatsLocked(ProcessRecord app, String providerPkgName,
            String authority) {
        if (app == null) return;
        if (app.curProcState <= ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND) {
            UserState userState = mUserController.getStartedUserStateLocked(app.userId);
            if (userState == null) return;
            final long now = SystemClock.elapsedRealtime();
            Long lastReported = userState.mProviderLastReportedFg.get(authority);
            if (lastReported == null || lastReported < now - 60 * 1000L) {
                if (mSystemReady) {
                    // Cannot touch the user stats if not system ready
                    mUsageStatsService.reportContentProviderUsage(
                            authority, providerPkgName, app.userId);
                }
                userState.mProviderLastReportedFg.put(authority, now);
            }
        }
    }

    private void maybeUpdateUsageStatsLocked(ProcessRecord app, long nowElapsed) {
        if (DEBUG_USAGE_STATS) {
            Slog.d(TAG, "Checking proc [" + Arrays.toString(app.getPackageList())
                    + "] state changes: old = " + app.setProcState + ", new = "
                    + app.curProcState);
        }
        if (mUsageStatsService == null) {
            return;
        }
        boolean isInteraction;
        // To avoid some abuse patterns, we are going to be careful about what we consider
        // to be an app interaction.  Being the top activity doesn't count while the display
        // is sleeping, nor do short foreground services.
        if (app.curProcState <= ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE) {
            isInteraction = true;
            app.fgInteractionTime = 0;
        } else if (app.curProcState <= ActivityManager.PROCESS_STATE_TOP_SLEEPING) {
            if (app.fgInteractionTime == 0) {
                app.fgInteractionTime = nowElapsed;
                isInteraction = false;
            } else {
                isInteraction = nowElapsed > app.fgInteractionTime + SERVICE_USAGE_INTERACTION_TIME;
            }
        } else {
            // If the app was being forced to the foreground, by say a Toast, then
            // no need to treat it as an interaction
            isInteraction = app.forcingToForeground == null
                    && app.curProcState <= ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
            app.fgInteractionTime = 0;
        }
        if (isInteraction && (!app.reportedInteraction
                || (nowElapsed-app.interactionEventTime) > USAGE_STATS_INTERACTION_INTERVAL)) {
            app.interactionEventTime = nowElapsed;
            String[] packages = app.getPackageList();
            if (packages != null) {
                for (int i = 0; i < packages.length; i++) {
                    mUsageStatsService.reportEvent(packages[i], app.userId,
                            UsageEvents.Event.SYSTEM_INTERACTION);
                }
            }
        }
        app.reportedInteraction = isInteraction;
        if (!isInteraction) {
            app.interactionEventTime = 0;
        }
    }

    private final void setProcessTrackerStateLocked(ProcessRecord proc, int memFactor, long now) {
        if (proc.thread != null) {
            if (proc.baseProcessTracker != null) {
                proc.baseProcessTracker.setState(proc.repProcState, memFactor, now, proc.pkgList);
            }
        }
    }

    private final boolean updateOomAdjLocked(ProcessRecord app, int cachedAdj,
            ProcessRecord TOP_APP, boolean doingAll, long now) {
        if (app.thread == null) {
            return false;
        }

        computeOomAdjLocked(app, cachedAdj, TOP_APP, doingAll, now);

        return applyOomAdjLocked(app, doingAll, now, SystemClock.elapsedRealtime());
    }

    final void updateProcessForegroundLocked(ProcessRecord proc, boolean isForeground,
            boolean oomAdj) {
        if (isForeground != proc.foregroundServices) {
            proc.foregroundServices = isForeground;
            ArrayList<ProcessRecord> curProcs = mForegroundPackages.get(proc.info.packageName,
                    proc.info.uid);
            if (isForeground) {
                if (curProcs == null) {
                    curProcs = new ArrayList<ProcessRecord>();
                    mForegroundPackages.put(proc.info.packageName, proc.info.uid, curProcs);
                }
                if (!curProcs.contains(proc)) {
                    curProcs.add(proc);
                    mBatteryStatsService.noteEvent(BatteryStats.HistoryItem.EVENT_FOREGROUND_START,
                            proc.info.packageName, proc.info.uid);
                }
            } else {
                if (curProcs != null) {
                    if (curProcs.remove(proc)) {
                        mBatteryStatsService.noteEvent(
                                BatteryStats.HistoryItem.EVENT_FOREGROUND_FINISH,
                                proc.info.packageName, proc.info.uid);
                        if (curProcs.size() <= 0) {
                            mForegroundPackages.remove(proc.info.packageName, proc.info.uid);
                        }
                    }
                }
            }
            if (oomAdj) {
                updateOomAdjLocked();
            }
        }
    }

    private final ActivityRecord resumedAppLocked() {
        ActivityRecord act = mStackSupervisor.resumedAppLocked();
        String pkg;
        int uid;
        if (act != null) {
            pkg = act.packageName;
            uid = act.info.applicationInfo.uid;
        } else {
            pkg = null;
            uid = -1;
        }
        // Has the UID or resumed package name changed?
        if (uid != mCurResumedUid || (pkg != mCurResumedPackage
                && (pkg == null || !pkg.equals(mCurResumedPackage)))) {
            if (mCurResumedPackage != null) {
                mBatteryStatsService.noteEvent(BatteryStats.HistoryItem.EVENT_TOP_FINISH,
                        mCurResumedPackage, mCurResumedUid);
            }
            mCurResumedPackage = pkg;
            mCurResumedUid = uid;
            if (mCurResumedPackage != null) {
                mBatteryStatsService.noteEvent(BatteryStats.HistoryItem.EVENT_TOP_START,
                        mCurResumedPackage, mCurResumedUid);
            }
        }
        return act;
    }

    final boolean updateOomAdjLocked(ProcessRecord app) {
        final ActivityRecord TOP_ACT = resumedAppLocked();
        final ProcessRecord TOP_APP = TOP_ACT != null ? TOP_ACT.app : null;
        final boolean wasCached = app.cached;

        mAdjSeq++;

        // This is the desired cached adjusment we want to tell it to use.
        // If our app is currently cached, we know it, and that is it.  Otherwise,
        // we don't know it yet, and it needs to now be cached we will then
        // need to do a complete oom adj.
        final int cachedAdj = app.curRawAdj >= ProcessList.CACHED_APP_MIN_ADJ
                ? app.curRawAdj : ProcessList.UNKNOWN_ADJ;
        boolean success = updateOomAdjLocked(app, cachedAdj, TOP_APP, false,
                SystemClock.uptimeMillis());
        if (wasCached != app.cached || app.curRawAdj == ProcessList.UNKNOWN_ADJ) {
            // Changed to/from cached state, so apps after it in the LRU
            // list may also be changed.
            updateOomAdjLocked();
        }
        return success;
    }

    final void updateOomAdjLocked() {
        final ActivityRecord TOP_ACT = resumedAppLocked();
        final ProcessRecord TOP_APP = TOP_ACT != null ? TOP_ACT.app : null;
        final long now = SystemClock.uptimeMillis();
        final long nowElapsed = SystemClock.elapsedRealtime();
        final long oldTime = now - ProcessList.MAX_EMPTY_TIME;
        final int N = mLruProcesses.size();

        if (false) {
            RuntimeException e = new RuntimeException();
            e.fillInStackTrace();
            Slog.i(TAG, "updateOomAdj: top=" + TOP_ACT, e);
        }

        // Reset state in all uid records.
        for (int i=mActiveUids.size()-1; i>=0; i--) {
            final UidRecord uidRec = mActiveUids.valueAt(i);
            if (false && DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                    "Starting update of " + uidRec);
            uidRec.reset();
        }

        mStackSupervisor.rankTaskLayersIfNeeded();

        mAdjSeq++;
        mNewNumServiceProcs = 0;
        mNewNumAServiceProcs = 0;

        final int emptyProcessLimit;
        final int cachedProcessLimit;
        if (mProcessLimit <= 0) {
            emptyProcessLimit = cachedProcessLimit = 0;
        } else if (mProcessLimit == 1) {
            emptyProcessLimit = 1;
            cachedProcessLimit = 0;
        } else {
            emptyProcessLimit = ProcessList.computeEmptyProcessLimit(mProcessLimit);
            cachedProcessLimit = mProcessLimit - emptyProcessLimit;
        }

        // Let's determine how many processes we have running vs.
        // how many slots we have for background processes; we may want
        // to put multiple processes in a slot of there are enough of
        // them.
        int numSlots = (ProcessList.CACHED_APP_MAX_ADJ
                - ProcessList.CACHED_APP_MIN_ADJ + 1) / 2;
        int numEmptyProcs = N - mNumNonCachedProcs - mNumCachedHiddenProcs;
        if (numEmptyProcs > cachedProcessLimit) {
            // If there are more empty processes than our limit on cached
            // processes, then use the cached process limit for the factor.
            // This ensures that the really old empty processes get pushed
            // down to the bottom, so if we are running low on memory we will
            // have a better chance at keeping around more cached processes
            // instead of a gazillion empty processes.
            numEmptyProcs = cachedProcessLimit;
        }
        int emptyFactor = numEmptyProcs/numSlots;
        if (emptyFactor < 1) emptyFactor = 1;
        int cachedFactor = (mNumCachedHiddenProcs > 0 ? mNumCachedHiddenProcs : 1)/numSlots;
        if (cachedFactor < 1) cachedFactor = 1;
        int stepCached = 0;
        int stepEmpty = 0;
        int numCached = 0;
        int numEmpty = 0;
        int numTrimming = 0;

        mNumNonCachedProcs = 0;
        mNumCachedHiddenProcs = 0;

        // First update the OOM adjustment for each of the
        // application processes based on their current state.
        int curCachedAdj = ProcessList.CACHED_APP_MIN_ADJ;
        int nextCachedAdj = curCachedAdj+1;
        int curEmptyAdj = ProcessList.CACHED_APP_MIN_ADJ;
        int nextEmptyAdj = curEmptyAdj+2;
        for (int i=N-1; i>=0; i--) {
            ProcessRecord app = mLruProcesses.get(i);
            if (!app.killedByAm && app.thread != null) {
                app.procStateChanged = false;
                computeOomAdjLocked(app, ProcessList.UNKNOWN_ADJ, TOP_APP, true, now);

                // If we haven't yet assigned the final cached adj
                // to the process, do that now.
                if (app.curAdj >= ProcessList.UNKNOWN_ADJ) {
                    switch (app.curProcState) {
                        case ActivityManager.PROCESS_STATE_CACHED_ACTIVITY:
                        case ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT:
                            // This process is a cached process holding activities...
                            // assign it the next cached value for that type, and then
                            // step that cached level.
                            app.curRawAdj = curCachedAdj;
                            app.curAdj = app.modifyRawOomAdj(curCachedAdj);
                            if (DEBUG_LRU && false) Slog.d(TAG_LRU, "Assigning activity LRU #" + i
                                    + " adj: " + app.curAdj + " (curCachedAdj=" + curCachedAdj
                                    + ")");
                            if (curCachedAdj != nextCachedAdj) {
                                stepCached++;
                                if (stepCached >= cachedFactor) {
                                    stepCached = 0;
                                    curCachedAdj = nextCachedAdj;
                                    nextCachedAdj += 2;
                                    if (nextCachedAdj > ProcessList.CACHED_APP_MAX_ADJ) {
                                        nextCachedAdj = ProcessList.CACHED_APP_MAX_ADJ;
                                    }
                                }
                            }
                            break;
                        default:
                            // For everything else, assign next empty cached process
                            // level and bump that up.  Note that this means that
                            // long-running services that have dropped down to the
                            // cached level will be treated as empty (since their process
                            // state is still as a service), which is what we want.
                            app.curRawAdj = curEmptyAdj;
                            app.curAdj = app.modifyRawOomAdj(curEmptyAdj);
                            if (DEBUG_LRU && false) Slog.d(TAG_LRU, "Assigning empty LRU #" + i
                                    + " adj: " + app.curAdj + " (curEmptyAdj=" + curEmptyAdj
                                    + ")");
                            if (curEmptyAdj != nextEmptyAdj) {
                                stepEmpty++;
                                if (stepEmpty >= emptyFactor) {
                                    stepEmpty = 0;
                                    curEmptyAdj = nextEmptyAdj;
                                    nextEmptyAdj += 2;
                                    if (nextEmptyAdj > ProcessList.CACHED_APP_MAX_ADJ) {
                                        nextEmptyAdj = ProcessList.CACHED_APP_MAX_ADJ;
                                    }
                                }
                            }
                            break;
                    }
                }

                applyOomAdjLocked(app, true, now, nowElapsed);

                // Count the number of process types.
                switch (app.curProcState) {
                    case ActivityManager.PROCESS_STATE_CACHED_ACTIVITY:
                    case ActivityManager.PROCESS_STATE_CACHED_ACTIVITY_CLIENT:
                        mNumCachedHiddenProcs++;
                        numCached++;
                        if (numCached > cachedProcessLimit) {
                            app.kill("cached #" + numCached, true);
                        }
                        break;
                    case ActivityManager.PROCESS_STATE_CACHED_EMPTY:
                        if (numEmpty > ProcessList.TRIM_EMPTY_APPS
                                && app.lastActivityTime < oldTime) {
                            app.kill("empty for "
                                    + ((oldTime + ProcessList.MAX_EMPTY_TIME - app.lastActivityTime)
                                    / 1000) + "s", true);
                        } else {
                            numEmpty++;
                            if (numEmpty > emptyProcessLimit) {
                                app.kill("empty #" + numEmpty, true);
                            }
                        }
                        break;
                    default:
                        mNumNonCachedProcs++;
                        break;
                }

                if (app.isolated && app.services.size() <= 0) {
                    // If this is an isolated process, and there are no
                    // services running in it, then the process is no longer
                    // needed.  We agressively kill these because we can by
                    // definition not re-use the same process again, and it is
                    // good to avoid having whatever code was running in them
                    // left sitting around after no longer needed.
                    app.kill("isolated not needed", true);
                } else {
                    // Keeping this process, update its uid.
                    final UidRecord uidRec = app.uidRecord;
                    if (uidRec != null && uidRec.curProcState > app.curProcState) {
                        uidRec.curProcState = app.curProcState;
                    }
                }

                if (app.curProcState >= ActivityManager.PROCESS_STATE_HOME
                        && !app.killedByAm) {
                    numTrimming++;
                }
            }
        }

        mNumServiceProcs = mNewNumServiceProcs;

        // Now determine the memory trimming level of background processes.
        // Unfortunately we need to start at the back of the list to do this
        // properly.  We only do this if the number of background apps we
        // are managing to keep around is less than half the maximum we desire;
        // if we are keeping a good number around, we'll let them use whatever
        // memory they want.
        final int numCachedAndEmpty = numCached + numEmpty;
        int memFactor;
        if (numCached <= ProcessList.TRIM_CACHED_APPS
                && numEmpty <= ProcessList.TRIM_EMPTY_APPS) {
            if (numCachedAndEmpty <= ProcessList.TRIM_CRITICAL_THRESHOLD) {
                memFactor = ProcessStats.ADJ_MEM_FACTOR_CRITICAL;
            } else if (numCachedAndEmpty <= ProcessList.TRIM_LOW_THRESHOLD) {
                memFactor = ProcessStats.ADJ_MEM_FACTOR_LOW;
            } else {
                memFactor = ProcessStats.ADJ_MEM_FACTOR_MODERATE;
            }
        } else {
            memFactor = ProcessStats.ADJ_MEM_FACTOR_NORMAL;
        }
        // We always allow the memory level to go up (better).  We only allow it to go
        // down if we are in a state where that is allowed, *and* the total number of processes
        // has gone down since last time.
        if (DEBUG_OOM_ADJ) Slog.d(TAG_OOM_ADJ, "oom: memFactor=" + memFactor
                + " last=" + mLastMemoryLevel + " allowLow=" + mAllowLowerMemLevel
                + " numProcs=" + mLruProcesses.size() + " last=" + mLastNumProcesses);
        if (memFactor > mLastMemoryLevel) {
            if (!mAllowLowerMemLevel || mLruProcesses.size() >= mLastNumProcesses) {
                memFactor = mLastMemoryLevel;
                if (DEBUG_OOM_ADJ) Slog.d(TAG_OOM_ADJ, "Keeping last mem factor!");
            }
        }
        if (memFactor != mLastMemoryLevel) {
            EventLogTags.writeAmMemFactor(memFactor, mLastMemoryLevel);
        }
        mLastMemoryLevel = memFactor;
        mLastNumProcesses = mLruProcesses.size();
        boolean allChanged = mProcessStats.setMemFactorLocked(memFactor, !isSleepingLocked(), now);
        final int trackerMemFactor = mProcessStats.getMemFactorLocked();
        if (memFactor != ProcessStats.ADJ_MEM_FACTOR_NORMAL) {
            if (mLowRamStartTime == 0) {
                mLowRamStartTime = now;
            }
            int step = 0;
            int fgTrimLevel;
            switch (memFactor) {
                case ProcessStats.ADJ_MEM_FACTOR_CRITICAL:
                    fgTrimLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL;
                    break;
                case ProcessStats.ADJ_MEM_FACTOR_LOW:
                    fgTrimLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW;
                    break;
                default:
                    fgTrimLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE;
                    break;
            }
            int factor = numTrimming/3;
            int minFactor = 2;
            if (mHomeProcess != null) minFactor++;
            if (mPreviousProcess != null) minFactor++;
            if (factor < minFactor) factor = minFactor;
            int curLevel = ComponentCallbacks2.TRIM_MEMORY_COMPLETE;
            for (int i=N-1; i>=0; i--) {
                ProcessRecord app = mLruProcesses.get(i);
                if (allChanged || app.procStateChanged) {
                    setProcessTrackerStateLocked(app, trackerMemFactor, now);
                    app.procStateChanged = false;
                }
                if (app.curProcState >= ActivityManager.PROCESS_STATE_HOME
                        && !app.killedByAm) {
                    if (app.trimMemoryLevel < curLevel && app.thread != null) {
                        try {
                            if (DEBUG_SWITCH || DEBUG_OOM_ADJ) Slog.v(TAG_OOM_ADJ,
                                    "Trimming memory of " + app.processName + " to " + curLevel);
                            app.thread.scheduleTrimMemory(curLevel);
                        } catch (RemoteException e) {
                        }
                        if (false) {
                            // For now we won't do this; our memory trimming seems
                            // to be good enough at this point that destroying
                            // activities causes more harm than good.
                            if (curLevel >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
                                    && app != mHomeProcess && app != mPreviousProcess) {
                                // Need to do this on its own message because the stack may not
                                // be in a consistent state at this point.
                                // For these apps we will also finish their activities
                                // to help them free memory.
                                mStackSupervisor.scheduleDestroyAllActivities(app, "trim");
                            }
                        }
                    }
                    app.trimMemoryLevel = curLevel;
                    step++;
                    if (step >= factor) {
                        step = 0;
                        switch (curLevel) {
                            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                                curLevel = ComponentCallbacks2.TRIM_MEMORY_MODERATE;
                                break;
                            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
                                curLevel = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND;
                                break;
                        }
                    }
                } else if (app.curProcState == ActivityManager.PROCESS_STATE_HEAVY_WEIGHT) {
                    if (app.trimMemoryLevel < ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
                            && app.thread != null) {
                        try {
                            if (DEBUG_SWITCH || DEBUG_OOM_ADJ) Slog.v(TAG_OOM_ADJ,
                                    "Trimming memory of heavy-weight " + app.processName
                                    + " to " + ComponentCallbacks2.TRIM_MEMORY_BACKGROUND);
                            app.thread.scheduleTrimMemory(
                                    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND);
                        } catch (RemoteException e) {
                        }
                    }
                    app.trimMemoryLevel = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND;
                } else {
                    if ((app.curProcState >= ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND
                            || app.systemNoUi) && app.pendingUiClean) {
                        // If this application is now in the background and it
                        // had done UI, then give it the special trim level to
                        // have it free UI resources.
                        final int level = ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN;
                        if (app.trimMemoryLevel < level && app.thread != null) {
                            try {
                                if (DEBUG_SWITCH || DEBUG_OOM_ADJ) Slog.v(TAG_OOM_ADJ,
                                        "Trimming memory of bg-ui " + app.processName
                                        + " to " + level);
                                app.thread.scheduleTrimMemory(level);
                            } catch (RemoteException e) {
                            }
                        }
                        app.pendingUiClean = false;
                    }
                    if (app.trimMemoryLevel < fgTrimLevel && app.thread != null) {
                        try {
                            if (DEBUG_SWITCH || DEBUG_OOM_ADJ) Slog.v(TAG_OOM_ADJ,
                                    "Trimming memory of fg " + app.processName
                                    + " to " + fgTrimLevel);
                            app.thread.scheduleTrimMemory(fgTrimLevel);
                        } catch (RemoteException e) {
                        }
                    }
                    app.trimMemoryLevel = fgTrimLevel;
                }
            }
        } else {
            if (mLowRamStartTime != 0) {
                mLowRamTimeSinceLastIdle += now - mLowRamStartTime;
                mLowRamStartTime = 0;
            }
            for (int i=N-1; i>=0; i--) {
                ProcessRecord app = mLruProcesses.get(i);
                if (allChanged || app.procStateChanged) {
                    setProcessTrackerStateLocked(app, trackerMemFactor, now);
                    app.procStateChanged = false;
                }
                if ((app.curProcState >= ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND
                        || app.systemNoUi) && app.pendingUiClean) {
                    if (app.trimMemoryLevel < ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
                            && app.thread != null) {
                        try {
                            if (DEBUG_SWITCH || DEBUG_OOM_ADJ) Slog.v(TAG_OOM_ADJ,
                                    "Trimming memory of ui hidden " + app.processName
                                    + " to " + ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
                            app.thread.scheduleTrimMemory(
                                    ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
                        } catch (RemoteException e) {
                        }
                    }
                    app.pendingUiClean = false;
                }
                app.trimMemoryLevel = 0;
            }
        }

        if (mAlwaysFinishActivities) {
            // Need to do this on its own message because the stack may not
            // be in a consistent state at this point.
            mStackSupervisor.scheduleDestroyAllActivities(null, "always-finish");
        }

        if (allChanged) {
            requestPssAllProcsLocked(now, false, mProcessStats.isMemFactorLowered());
        }

        // Update from any uid changes.
        for (int i=mActiveUids.size()-1; i>=0; i--) {
            final UidRecord uidRec = mActiveUids.valueAt(i);
            int uidChange = UidRecord.CHANGE_PROCSTATE;
            if (uidRec.setProcState != uidRec.curProcState) {
                if (DEBUG_UID_OBSERVERS) Slog.i(TAG_UID_OBSERVERS,
                        "Changes in " + uidRec + ": proc state from " + uidRec.setProcState
                        + " to " + uidRec.curProcState);
                if (ActivityManager.isProcStateBackground(uidRec.curProcState)) {
                    if (!ActivityManager.isProcStateBackground(uidRec.setProcState)) {
                        uidRec.lastBackgroundTime = nowElapsed;
                        if (!mHandler.hasMessages(IDLE_UIDS_MSG)) {
                            // Note: the background settle time is in elapsed realtime, while
                            // the handler time base is uptime.  All this means is that we may
                            // stop background uids later than we had intended, but that only
                            // happens because the device was sleeping so we are okay anyway.
                            mHandler.sendEmptyMessageDelayed(IDLE_UIDS_MSG, BACKGROUND_SETTLE_TIME);
                        }
                    }
                } else {
                    if (uidRec.idle) {
                        uidChange = UidRecord.CHANGE_ACTIVE;
                        uidRec.idle = false;
                    }
                    uidRec.lastBackgroundTime = 0;
                }
                uidRec.setProcState = uidRec.curProcState;
                enqueueUidChangeLocked(uidRec, -1, uidChange);
                noteUidProcessState(uidRec.uid, uidRec.curProcState);
            }
        }

        if (mProcessStats.shouldWriteNowLocked(now)) {
            mHandler.post(new Runnable() {
                @Override public void run() {
                    synchronized (ActivityManagerService.this) {
                        mProcessStats.writeStateAsyncLocked();
                    }
                }
            });
        }

        if (DEBUG_OOM_ADJ) {
            final long duration = SystemClock.uptimeMillis() - now;
            if (false) {
                Slog.d(TAG_OOM_ADJ, "Did OOM ADJ in " + duration + "ms",
                        new RuntimeException("here").fillInStackTrace());
            } else {
                Slog.d(TAG_OOM_ADJ, "Did OOM ADJ in " + duration + "ms");
            }
        }
    }

    final void idleUids() {
        synchronized (this) {
            final long nowElapsed = SystemClock.elapsedRealtime();
            final long maxBgTime = nowElapsed - BACKGROUND_SETTLE_TIME;
            long nextTime = 0;
            for (int i=mActiveUids.size()-1; i>=0; i--) {
                final UidRecord uidRec = mActiveUids.valueAt(i);
                final long bgTime = uidRec.lastBackgroundTime;
                if (bgTime > 0 && !uidRec.idle) {
                    if (bgTime <= maxBgTime) {
                        uidRec.idle = true;
                        doStopUidLocked(uidRec.uid, uidRec);
                    } else {
                        if (nextTime == 0 || nextTime > bgTime) {
                            nextTime = bgTime;
                        }
                    }
                }
            }
            if (nextTime > 0) {
                mHandler.removeMessages(IDLE_UIDS_MSG);
                mHandler.sendEmptyMessageDelayed(IDLE_UIDS_MSG,
                        nextTime + BACKGROUND_SETTLE_TIME - nowElapsed);
            }
        }
    }

    final void runInBackgroundDisabled(int uid) {
        synchronized (this) {
            UidRecord uidRec = mActiveUids.get(uid);
            if (uidRec != null) {
                // This uid is actually running...  should it be considered background now?
                if (uidRec.idle) {
                    doStopUidLocked(uidRec.uid, uidRec);
                }
            } else {
                // This uid isn't actually running...  still send a report about it being "stopped".
                doStopUidLocked(uid, null);
            }
        }
    }

    final void doStopUidLocked(int uid, final UidRecord uidRec) {
        mServices.stopInBackgroundLocked(uid);
        enqueueUidChangeLocked(uidRec, uid, UidRecord.CHANGE_IDLE);
    }

    final void trimApplications() {
        synchronized (this) {
            int i;

            // First remove any unused application processes whose package
            // has been removed.
            for (i=mRemovedProcesses.size()-1; i>=0; i--) {
                final ProcessRecord app = mRemovedProcesses.get(i);
                if (app.activities.size() == 0
                        && app.curReceiver == null && app.services.size() == 0) {
                    Slog.i(
                        TAG, "Exiting empty application process "
                        + app.toShortString() + " ("
                        + (app.thread != null ? app.thread.asBinder() : null)
                        + ")\n");
                    if (app.pid > 0 && app.pid != MY_PID) {
                        app.kill("empty", false);
                    } else {
                        try {
                            app.thread.scheduleExit();
                        } catch (Exception e) {
                            // Ignore exceptions.
                        }
                    }
                    cleanUpApplicationRecordLocked(app, false, true, -1, false /*replacingPid*/);
                    mRemovedProcesses.remove(i);

                    if (app.persistent) {
                        addAppLocked(app.info, false, null /* ABI override */);
                    }
                }
            }

            // Now update the oom adj for all processes.
            updateOomAdjLocked();
        }
    }

    /** This method sends the specified signal to each of the persistent apps */
    public void signalPersistentProcesses(int sig) throws RemoteException {
        if (sig != Process.SIGNAL_USR1) {
            throw new SecurityException("Only SIGNAL_USR1 is allowed");
        }

        synchronized (this) {
            if (checkCallingPermission(android.Manifest.permission.SIGNAL_PERSISTENT_PROCESSES)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Requires permission "
                        + android.Manifest.permission.SIGNAL_PERSISTENT_PROCESSES);
            }

            for (int i = mLruProcesses.size() - 1 ; i >= 0 ; i--) {
                ProcessRecord r = mLruProcesses.get(i);
                if (r.thread != null && r.persistent) {
                    Process.sendSignal(r.pid, sig);
                }
            }
        }
    }

    private void stopProfilerLocked(ProcessRecord proc, int profileType) {
        if (proc == null || proc == mProfileProc) {
            proc = mProfileProc;
            profileType = mProfileType;
            clearProfilerLocked();
        }
        if (proc == null) {
            return;
        }
        try {
            proc.thread.profilerControl(false, null, profileType);
        } catch (RemoteException e) {
            throw new IllegalStateException("Process disappeared");
        }
    }

    private void clearProfilerLocked() {
        if (mProfileFd != null) {
            try {
                mProfileFd.close();
            } catch (IOException e) {
            }
        }
        mProfileApp = null;
        mProfileProc = null;
        mProfileFile = null;
        mProfileType = 0;
        mAutoStopProfiler = false;
        mSamplingInterval = 0;
    }

    public boolean profileControl(String process, int userId, boolean start,
            ProfilerInfo profilerInfo, int profileType) throws RemoteException {

        try {
            synchronized (this) {
                // note: hijacking SET_ACTIVITY_WATCHER, but should be changed to
                // its own permission.
                if (checkCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER)
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Requires permission "
                            + android.Manifest.permission.SET_ACTIVITY_WATCHER);
                }

                if (start && (profilerInfo == null || profilerInfo.profileFd == null)) {
                    throw new IllegalArgumentException("null profile info or fd");
                }

                ProcessRecord proc = null;
                if (process != null) {
                    proc = findProcessLocked(process, userId, "profileControl");
                }

                if (start && (proc == null || proc.thread == null)) {
                    throw new IllegalArgumentException("Unknown process: " + process);
                }

                if (start) {
                    stopProfilerLocked(null, 0);
                    setProfileApp(proc.info, proc.processName, profilerInfo);
                    mProfileProc = proc;
                    mProfileType = profileType;
                    ParcelFileDescriptor fd = profilerInfo.profileFd;
                    try {
                        fd = fd.dup();
                    } catch (IOException e) {
                        fd = null;
                    }
                    profilerInfo.profileFd = fd;
                    proc.thread.profilerControl(start, profilerInfo, profileType);
                    fd = null;
                    mProfileFd = null;
                } else {
                    stopProfilerLocked(proc, profileType);
                    if (profilerInfo != null && profilerInfo.profileFd != null) {
                        try {
                            profilerInfo.profileFd.close();
                        } catch (IOException e) {
                        }
                    }
                }

                return true;
            }
        } catch (RemoteException e) {
            throw new IllegalStateException("Process disappeared");
        } finally {
            if (profilerInfo != null && profilerInfo.profileFd != null) {
                try {
                    profilerInfo.profileFd.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private ProcessRecord findProcessLocked(String process, int userId, String callName) {
        userId = mUserController.handleIncomingUser(Binder.getCallingPid(), Binder.getCallingUid(),
                userId, true, ALLOW_FULL_ONLY, callName, null);
        ProcessRecord proc = null;
        try {
            int pid = Integer.parseInt(process);
            synchronized (mPidsSelfLocked) {
                proc = mPidsSelfLocked.get(pid);
            }
        } catch (NumberFormatException e) {
        }

        if (proc == null) {
            ArrayMap<String, SparseArray<ProcessRecord>> all
                    = mProcessNames.getMap();
            SparseArray<ProcessRecord> procs = all.get(process);
            if (procs != null && procs.size() > 0) {
                proc = procs.valueAt(0);
                if (userId != UserHandle.USER_ALL && proc.userId != userId) {
                    for (int i=1; i<procs.size(); i++) {
                        ProcessRecord thisProc = procs.valueAt(i);
                        if (thisProc.userId == userId) {
                            proc = thisProc;
                            break;
                        }
                    }
                }
            }
        }

        return proc;
    }

    public boolean dumpHeap(String process, int userId, boolean managed,
            String path, ParcelFileDescriptor fd) throws RemoteException {

        try {
            synchronized (this) {
                // note: hijacking SET_ACTIVITY_WATCHER, but should be changed to
                // its own permission (same as profileControl).
                if (checkCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER)
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Requires permission "
                            + android.Manifest.permission.SET_ACTIVITY_WATCHER);
                }

                if (fd == null) {
                    throw new IllegalArgumentException("null fd");
                }

                ProcessRecord proc = findProcessLocked(process, userId, "dumpHeap");
                if (proc == null || proc.thread == null) {
                    throw new IllegalArgumentException("Unknown process: " + process);
                }

                boolean isDebuggable = "1".equals(SystemProperties.get(SYSTEM_DEBUGGABLE, "0"));
                if (!isDebuggable) {
                    if ((proc.info.flags&ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                        throw new SecurityException("Process not debuggable: " + proc);
                    }
                }

                proc.thread.dumpHeap(managed, path, fd);
                fd = null;
                return true;
            }
        } catch (RemoteException e) {
            throw new IllegalStateException("Process disappeared");
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                } catch (IOException e) {
                }
            }
        }
    }

    @Override
    public void setDumpHeapDebugLimit(String processName, int uid, long maxMemSize,
            String reportPackage) {
        if (processName != null) {
            enforceCallingPermission(android.Manifest.permission.SET_DEBUG_APP,
                    "setDumpHeapDebugLimit()");
        } else {
            synchronized (mPidsSelfLocked) {
                ProcessRecord proc = mPidsSelfLocked.get(Binder.getCallingPid());
                if (proc == null) {
                    throw new SecurityException("No process found for calling pid "
                            + Binder.getCallingPid());
                }
                if (!Build.IS_DEBUGGABLE
                        && (proc.info.flags&ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                    throw new SecurityException("Not running a debuggable build");
                }
                processName = proc.processName;
                uid = proc.uid;
                if (reportPackage != null && !proc.pkgList.containsKey(reportPackage)) {
                    throw new SecurityException("Package " + reportPackage + " is not running in "
                            + proc);
                }
            }
        }
        synchronized (this) {
            if (maxMemSize > 0) {
                mMemWatchProcesses.put(processName, uid, new Pair(maxMemSize, reportPackage));
            } else {
                if (uid != 0) {
                    mMemWatchProcesses.remove(processName, uid);
                } else {
                    mMemWatchProcesses.getMap().remove(processName);
                }
            }
        }
    }

    @Override
    public void dumpHeapFinished(String path) {
        synchronized (this) {
            if (Binder.getCallingPid() != mMemWatchDumpPid) {
                Slog.w(TAG, "dumpHeapFinished: Calling pid " + Binder.getCallingPid()
                        + " does not match last pid " + mMemWatchDumpPid);
                return;
            }
            if (mMemWatchDumpFile == null || !mMemWatchDumpFile.equals(path)) {
                Slog.w(TAG, "dumpHeapFinished: Calling path " + path
                        + " does not match last path " + mMemWatchDumpFile);
                return;
            }
            if (DEBUG_PSS) Slog.d(TAG_PSS, "Dump heap finished for " + path);
            mHandler.sendEmptyMessage(POST_DUMP_HEAP_NOTIFICATION_MSG);
        }
    }

    /** In this method we try to acquire our lock to make sure that we have not deadlocked */
    public void monitor() {
        synchronized (this) { }
    }

    void onCoreSettingsChange(Bundle settings) {
        for (int i = mLruProcesses.size() - 1; i >= 0; i--) {
            ProcessRecord processRecord = mLruProcesses.get(i);
            try {
                if (processRecord.thread != null) {
                    processRecord.thread.setCoreSettings(settings);
                }
            } catch (RemoteException re) {
                /* ignore */
            }
        }
    }

    // Multi-user methods

    /**
     * Start user, if its not already running, but don't bring it to foreground.
     */
    @Override
    public boolean startUserInBackground(final int userId) {
        return mUserController.startUser(userId, /* foreground */ false);
    }

    @Override
    public boolean unlockUser(int userId, byte[] token, byte[] secret, IProgressListener listener) {
        return mUserController.unlockUser(userId, token, secret, listener);
    }

    @Override
    public boolean switchUser(final int targetUserId) {
        enforceShellRestriction(UserManager.DISALLOW_DEBUGGING_FEATURES, targetUserId);
        UserInfo currentUserInfo;
        UserInfo targetUserInfo;
        synchronized (this) {
            int currentUserId = mUserController.getCurrentUserIdLocked();
            currentUserInfo = mUserController.getUserInfo(currentUserId);
            targetUserInfo = mUserController.getUserInfo(targetUserId);
            if (targetUserInfo == null) {
                Slog.w(TAG, "No user info for user #" + targetUserId);
                return false;
            }
            if (!targetUserInfo.isDemo() && UserManager.isDeviceInDemoMode(mContext)) {
                Slog.w(TAG, "Cannot switch to non-demo user #" + targetUserId
                        + " when device is in demo mode");
                return false;
            }
            if (!targetUserInfo.supportsSwitchTo()) {
                Slog.w(TAG, "Cannot switch to User #" + targetUserId + ": not supported");
                return false;
            }
            if (targetUserInfo.isManagedProfile()) {
                Slog.w(TAG, "Cannot switch to User #" + targetUserId + ": not a full user");
                return false;
            }
            mUserController.setTargetUserIdLocked(targetUserId);
        }
        Pair<UserInfo, UserInfo> userNames = new Pair<>(currentUserInfo, targetUserInfo);
        mUiHandler.removeMessages(START_USER_SWITCH_UI_MSG);
        mUiHandler.sendMessage(mUiHandler.obtainMessage(START_USER_SWITCH_UI_MSG, userNames));
        return true;
    }

    void scheduleStartProfilesLocked() {
        if (!mHandler.hasMessages(START_PROFILES_MSG)) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(START_PROFILES_MSG),
                    DateUtils.SECOND_IN_MILLIS);
        }
    }

    @Override
    public int stopUser(final int userId, boolean force, final IStopUserCallback callback) {
        return mUserController.stopUser(userId, force, callback);
    }

    @Override
    public UserInfo getCurrentUser() {
        return mUserController.getCurrentUser();
    }

    @Override
    public boolean isUserRunning(int userId, int flags) {
        if (!mUserController.isSameProfileGroup(userId, UserHandle.getCallingUserId())
                && checkCallingPermission(INTERACT_ACROSS_USERS)
                    != PackageManager.PERMISSION_GRANTED) {
            String msg = "Permission Denial: isUserRunning() from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " requires " + INTERACT_ACROSS_USERS;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        synchronized (this) {
            return mUserController.isUserRunningLocked(userId, flags);
        }
    }

    @Override
    public int[] getRunningUserIds() {
        if (checkCallingPermission(INTERACT_ACROSS_USERS)
                != PackageManager.PERMISSION_GRANTED) {
            String msg = "Permission Denial: isUserRunning() from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid()
                    + " requires " + INTERACT_ACROSS_USERS;
            Slog.w(TAG, msg);
            throw new SecurityException(msg);
        }
        synchronized (this) {
            return mUserController.getStartedUserArrayLocked();
        }
    }

    @Override
    public void registerUserSwitchObserver(IUserSwitchObserver observer, String name) {
        mUserController.registerUserSwitchObserver(observer, name);
    }

    @Override
    public void unregisterUserSwitchObserver(IUserSwitchObserver observer) {
        mUserController.unregisterUserSwitchObserver(observer);
    }

    ApplicationInfo getAppInfoForUser(ApplicationInfo info, int userId) {
        if (info == null) return null;
        ApplicationInfo newInfo = new ApplicationInfo(info);
        newInfo.initForUser(userId);
        return newInfo;
    }

    public boolean isUserStopped(int userId) {
        synchronized (this) {
            return mUserController.getStartedUserStateLocked(userId) == null;
        }
    }

    ActivityInfo getActivityInfoForUser(ActivityInfo aInfo, int userId) {
        if (aInfo == null
                || (userId < 1 && aInfo.applicationInfo.uid < UserHandle.PER_USER_RANGE)) {
            return aInfo;
        }

        ActivityInfo info = new ActivityInfo(aInfo);
        info.applicationInfo = getAppInfoForUser(info.applicationInfo, userId);
        return info;
    }

    private boolean processSanityChecksLocked(ProcessRecord process) {
        if (process == null || process.thread == null) {
            return false;
        }

        boolean isDebuggable = "1".equals(SystemProperties.get(SYSTEM_DEBUGGABLE, "0"));
        if (!isDebuggable) {
            if ((process.info.flags&ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                return false;
            }
        }

        return true;
    }

    public boolean startBinderTracking() throws RemoteException {
        synchronized (this) {
            mBinderTransactionTrackingEnabled = true;
            // TODO: hijacking SET_ACTIVITY_WATCHER, but should be changed to its own
            // permission (same as profileControl).
            if (checkCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new SecurityException("Requires permission "
                        + android.Manifest.permission.SET_ACTIVITY_WATCHER);
            }

            for (int i = 0; i < mLruProcesses.size(); i++) {
                ProcessRecord process = mLruProcesses.get(i);
                if (!processSanityChecksLocked(process)) {
                    continue;
                }
                try {
                    process.thread.startBinderTracking();
                } catch (RemoteException e) {
                    Log.v(TAG, "Process disappared");
                }
            }
            return true;
        }
    }

    public boolean stopBinderTrackingAndDump(ParcelFileDescriptor fd) throws RemoteException {
        try {
            synchronized (this) {
                mBinderTransactionTrackingEnabled = false;
                // TODO: hijacking SET_ACTIVITY_WATCHER, but should be changed to its own
                // permission (same as profileControl).
                if (checkCallingPermission(android.Manifest.permission.SET_ACTIVITY_WATCHER)
                        != PackageManager.PERMISSION_GRANTED) {
                    throw new SecurityException("Requires permission "
                            + android.Manifest.permission.SET_ACTIVITY_WATCHER);
                }

                if (fd == null) {
                    throw new IllegalArgumentException("null fd");
                }

                PrintWriter pw = new FastPrintWriter(new FileOutputStream(fd.getFileDescriptor()));
                pw.println("Binder transaction traces for all processes.\n");
                for (ProcessRecord process : mLruProcesses) {
                    if (!processSanityChecksLocked(process)) {
                        continue;
                    }

                    pw.println("Traces for process: " + process.processName);
                    pw.flush();
                    try {
                        TransferPipe tp = new TransferPipe();
                        try {
                            process.thread.stopBinderTrackingAndDump(
                                    tp.getWriteFd().getFileDescriptor());
                            tp.go(fd.getFileDescriptor());
                        } finally {
                            tp.kill();
                        }
                    } catch (IOException e) {
                        pw.println("Failure while dumping IPC traces from " + process +
                                ".  Exception: " + e);
                        pw.flush();
                    } catch (RemoteException e) {
                        pw.println("Got a RemoteException while dumping IPC traces from " +
                                process + ".  Exception: " + e);
                        pw.flush();
                    }
                }
                fd = null;
                return true;
            }
        } finally {
            if (fd != null) {
                try {
                    fd.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private final class LocalService extends ActivityManagerInternal {
        @Override
        public String checkContentProviderAccess(String authority, int userId) {
            return ActivityManagerService.this.checkContentProviderAccess(authority, userId);
        }

        @Override
        public void onWakefulnessChanged(int wakefulness) {
            ActivityManagerService.this.onWakefulnessChanged(wakefulness);
        }

        @Override
        public int startIsolatedProcess(String entryPoint, String[] entryPointArgs,
                String processName, String abiOverride, int uid, Runnable crashHandler) {
            return ActivityManagerService.this.startIsolatedProcess(entryPoint, entryPointArgs,
                    processName, abiOverride, uid, crashHandler);
        }

        @Override
        public SleepToken acquireSleepToken(String tag) {
            Preconditions.checkNotNull(tag);

            ComponentName requestedVrService = null;
            ComponentName callingVrActivity = null;
            int userId = -1;
            synchronized (ActivityManagerService.this) {
                if (mFocusedActivity != null) {
                    requestedVrService = mFocusedActivity.requestedVrComponent;
                    callingVrActivity = mFocusedActivity.info.getComponentName();
                    userId = mFocusedActivity.userId;
                }
            }

            if (requestedVrService != null) {
                applyVrMode(false, requestedVrService, userId, callingVrActivity, true);
            }

            synchronized (ActivityManagerService.this) {
                SleepTokenImpl token = new SleepTokenImpl(tag);
                mSleepTokens.add(token);
                updateSleepIfNeededLocked();
                return token;
            }
        }

        @Override
        public ComponentName getHomeActivityForUser(int userId) {
            synchronized (ActivityManagerService.this) {
                ActivityRecord homeActivity = mStackSupervisor.getHomeActivityForUser(userId);
                return homeActivity == null ? null : homeActivity.realActivity;
            }
        }

        @Override
        public void onUserRemoved(int userId) {
            synchronized (ActivityManagerService.this) {
                ActivityManagerService.this.onUserStoppedLocked(userId);
            }
        }

        @Override
        public void onLocalVoiceInteractionStarted(IBinder activity,
                IVoiceInteractionSession voiceSession, IVoiceInteractor voiceInteractor) {
            synchronized (ActivityManagerService.this) {
                ActivityManagerService.this.onLocalVoiceInteractionStartedLocked(activity,
                        voiceSession, voiceInteractor);
            }
        }

        @Override
        public void notifyStartingWindowDrawn() {
            synchronized (ActivityManagerService.this) {
                mStackSupervisor.mActivityMetricsLogger.notifyStartingWindowDrawn();
            }
        }

        @Override
        public void notifyAppTransitionStarting(int reason) {
            synchronized (ActivityManagerService.this) {
                mStackSupervisor.mActivityMetricsLogger.notifyTransitionStarting(reason);
            }
        }

        @Override
        public void notifyAppTransitionFinished() {
            synchronized (ActivityManagerService.this) {
                mStackSupervisor.notifyAppTransitionDone();
            }
        }

        @Override
        public void notifyAppTransitionCancelled() {
            synchronized (ActivityManagerService.this) {
                mStackSupervisor.notifyAppTransitionDone();
            }
        }

        @Override
        public List<IBinder> getTopVisibleActivities() {
            synchronized (ActivityManagerService.this) {
                return mStackSupervisor.getTopVisibleActivities();
            }
        }

        @Override
        public void notifyDockedStackMinimizedChanged(boolean minimized) {
            synchronized (ActivityManagerService.this) {
                mStackSupervisor.setDockedStackMinimized(minimized);
            }
        }

        @Override
        public void killForegroundAppsForUser(int userHandle) {
            synchronized (ActivityManagerService.this) {
                final ArrayList<ProcessRecord> procs = new ArrayList<>();
                final int NP = mProcessNames.getMap().size();
                for (int ip = 0; ip < NP; ip++) {
                    final SparseArray<ProcessRecord> apps = mProcessNames.getMap().valueAt(ip);
                    final int NA = apps.size();
                    for (int ia = 0; ia < NA; ia++) {
                        final ProcessRecord app = apps.valueAt(ia);
                        if (app.persistent) {
                            // We don't kill persistent processes.
                            continue;
                        }
                        if (app.removed) {
                            procs.add(app);
                        } else if (app.userId == userHandle && app.foregroundActivities) {
                            app.removed = true;
                            procs.add(app);
                        }
                    }
                }

                final int N = procs.size();
                for (int i = 0; i < N; i++) {
                    removeProcessLocked(procs.get(i), false, true, "kill all fg");
                }
            }
        }

        @Override
        public void setPendingIntentWhitelistDuration(IIntentSender target, long duration) {
            if (!(target instanceof PendingIntentRecord)) {
                Slog.w(TAG, "markAsSentFromNotification(): not a PendingIntentRecord: " + target);
                return;
            }
            ((PendingIntentRecord) target).setWhitelistDuration(duration);
        }

        @Override
        public void updatePersistentConfigurationForUser(@NonNull Configuration values,
                int userId) {
            Preconditions.checkNotNull(values, "Configuration must not be null");
            Preconditions.checkArgumentNonnegative(userId, "userId " + userId + " not supported");
            synchronized (ActivityManagerService.this) {
                updateConfigurationLocked(values, null, false, true, userId,
                        false /* deferResume */);
            }
        }

        @Override
        public int startActivitiesAsPackage(String packageName, int userId, Intent[] intents,
                Bundle bOptions) {
            Preconditions.checkNotNull(intents, "intents");
            final String[] resolvedTypes = new String[intents.length];
            for (int i = 0; i < intents.length; i++) {
                resolvedTypes[i] = intents[i].resolveTypeIfNeeded(mContext.getContentResolver());
            }

            // UID of the package on user userId.
            // "= 0" is needed because otherwise catch(RemoteException) would make it look like
            // packageUid may not be initialized.
            int packageUid = 0;
            try {
                packageUid = AppGlobals.getPackageManager().getPackageUid(
                        packageName, PackageManager.MATCH_DEBUG_TRIAGED_MISSING, userId);
            } catch (RemoteException e) {
                // Shouldn't happen.
            }

            synchronized (ActivityManagerService.this) {
                return startActivitiesInPackage(packageUid, packageName, intents, resolvedTypes,
                        /*resultTo*/ null, bOptions, userId);
            }
        }

        @Override
        public int getUidProcessState(int uid) {
            return getUidState(uid);
        }
    }

    private final class SleepTokenImpl extends SleepToken {
        private final String mTag;
        private final long mAcquireTime;

        public SleepTokenImpl(String tag) {
            mTag = tag;
            mAcquireTime = SystemClock.uptimeMillis();
        }

        @Override
        public void release() {
            synchronized (ActivityManagerService.this) {
                if (mSleepTokens.remove(this)) {
                    updateSleepIfNeededLocked();
                }
            }
        }

        @Override
        public String toString() {
            return "{\"" + mTag + "\", acquire at " + TimeUtils.formatUptime(mAcquireTime) + "}";
        }
    }

    /**
     * An implementation of IAppTask, that allows an app to manage its own tasks via
     * {@link android.app.ActivityManager.AppTask}.  We keep track of the callingUid to ensure that
     * only the process that calls getAppTasks() can call the AppTask methods.
     */
    class AppTaskImpl extends IAppTask.Stub {
        private int mTaskId;
        private int mCallingUid;

        public AppTaskImpl(int taskId, int callingUid) {
            mTaskId = taskId;
            mCallingUid = callingUid;
        }

        private void checkCaller() {
            if (mCallingUid != Binder.getCallingUid()) {
                throw new SecurityException("Caller " + mCallingUid
                        + " does not match caller of getAppTasks(): " + Binder.getCallingUid());
            }
        }

        @Override
        public void finishAndRemoveTask() {
            checkCaller();

            synchronized (ActivityManagerService.this) {
                long origId = Binder.clearCallingIdentity();
                try {
                    // We remove the task from recents to preserve backwards
                    if (!removeTaskByIdLocked(mTaskId, false, REMOVE_FROM_RECENTS)) {
                        throw new IllegalArgumentException("Unable to find task ID " + mTaskId);
                    }
                } finally {
                    Binder.restoreCallingIdentity(origId);
                }
            }
        }

        @Override
        public ActivityManager.RecentTaskInfo getTaskInfo() {
            checkCaller();

            synchronized (ActivityManagerService.this) {
                long origId = Binder.clearCallingIdentity();
                try {
                    TaskRecord tr = mStackSupervisor.anyTaskForIdLocked(mTaskId);
                    if (tr == null) {
                        throw new IllegalArgumentException("Unable to find task ID " + mTaskId);
                    }
                    return createRecentTaskInfoFromTaskRecord(tr);
                } finally {
                    Binder.restoreCallingIdentity(origId);
                }
            }
        }

        @Override
        public void moveToFront() {
            checkCaller();
            // Will bring task to front if it already has a root activity.
            final long origId = Binder.clearCallingIdentity();
            try {
                synchronized (this) {
                    mStackSupervisor.startActivityFromRecentsInner(mTaskId, null);
                }
            } finally {
                Binder.restoreCallingIdentity(origId);
            }
        }

        @Override
        public int startActivity(IBinder whoThread, String callingPackage,
                Intent intent, String resolvedType, Bundle bOptions) {
            checkCaller();

            int callingUser = UserHandle.getCallingUserId();
            TaskRecord tr;
            IApplicationThread appThread;
            synchronized (ActivityManagerService.this) {
                tr = mStackSupervisor.anyTaskForIdLocked(mTaskId);
                if (tr == null) {
                    throw new IllegalArgumentException("Unable to find task ID " + mTaskId);
                }
                appThread = ApplicationThreadNative.asInterface(whoThread);
                if (appThread == null) {
                    throw new IllegalArgumentException("Bad app thread " + appThread);
                }
            }
            return mActivityStarter.startActivityMayWait(appThread, -1, callingPackage, intent,
                    resolvedType, null, null, null, null, 0, 0, null, null,
                    null, bOptions, false, callingUser, null, tr);
        }

        @Override
        public void setExcludeFromRecents(boolean exclude) {
            checkCaller();

            synchronized (ActivityManagerService.this) {
                long origId = Binder.clearCallingIdentity();
                try {
                    TaskRecord tr = mStackSupervisor.anyTaskForIdLocked(mTaskId);
                    if (tr == null) {
                        throw new IllegalArgumentException("Unable to find task ID " + mTaskId);
                    }
                    Intent intent = tr.getBaseIntent();
                    if (exclude) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    } else {
                        intent.setFlags(intent.getFlags()
                                & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                    }
                } finally {
                    Binder.restoreCallingIdentity(origId);
                }
            }
        }
    }

    /**
     * Kill processes for the user with id userId and that depend on the package named packageName
     */
    @Override
    public void killPackageDependents(String packageName, int userId) {
        enforceCallingPermission(android.Manifest.permission.KILL_UID, "killPackageDependents()");
        if (packageName == null) {
            throw new NullPointerException(
                    "Cannot kill the dependents of a package without its name.");
        }

        long callingId = Binder.clearCallingIdentity();
        IPackageManager pm = AppGlobals.getPackageManager();
        int pkgUid = -1;
        try {
            pkgUid = pm.getPackageUid(packageName, MATCH_DEBUG_TRIAGED_MISSING, userId);
        } catch (RemoteException e) {
        }
        if (userId != UserHandle.USER_ALL && pkgUid == -1) {
            throw new IllegalArgumentException(
                    "Cannot kill dependents of non-existing package " + packageName);
        }
        try {
            synchronized(this) {
                killPackageProcessesLocked(packageName, UserHandle.getAppId(pkgUid), userId,
                        ProcessList.FOREGROUND_APP_ADJ, false, true, true, false,
                        "dep: " + packageName);
            }
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Override
    public boolean canBypassWorkChallenge(PendingIntent intent) throws RemoteException {
        final int userId = intent.getCreatorUserHandle().getIdentifier();
        if (!mUserController.isUserRunningLocked(userId, ActivityManager.FLAG_AND_LOCKED)) {
            return false;
        }
        IIntentSender target = intent.getTarget();
        if (!(target instanceof PendingIntentRecord)) {
            return false;
        }
        final PendingIntentRecord record = (PendingIntentRecord) target;
        final ResolveInfo rInfo = mStackSupervisor.resolveIntent(record.key.requestIntent,
                record.key.requestResolvedType, userId, PackageManager.MATCH_DIRECT_BOOT_AWARE);
        // For direct boot aware activities, they can be shown without triggering a work challenge
        // before the profile user is unlocked.
        return rInfo != null && rInfo.activityInfo != null;
    }
}
