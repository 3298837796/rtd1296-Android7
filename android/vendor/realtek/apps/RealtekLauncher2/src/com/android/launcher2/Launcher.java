
/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher2;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Dialog;
import android.app.SearchManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.os.SystemClock;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.speech.RecognizerIntent;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Advanceable;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.android.common.Search;
import com.android.launcher2.AppsCustomizePagedView.ContentType;
import com.android.launcher2.CellLayout.CellInfo;
import com.android.launcher2.DropTarget.DragObject;
import com.android.launcher2.LauncherSettings.Favorites;
import com.realtek.addon.dialog.RTKPopupWindowHelper;
import com.realtek.addon.exceptions.LauncherStateErrorException;
import com.realtek.addon.helper.ConstantsDef;
import com.realtek.addon.helper.CustomizedHelper;
import com.realtek.addon.helper.DebugHelper;
import com.realtek.launcher.R;
import java.net.NetworkInterface;
import android.os.SystemProperties;


/**
 * Default launcher application.
 */
public final class Launcher extends Activity
        implements View.OnClickListener, OnLongClickListener, LauncherModel.Callbacks,
                   View.OnTouchListener {
    static final String TAG = "Launcher";
    static final boolean LOGD = false;

    static final boolean PROFILE_STARTUP = false;
    static final boolean DEBUG_WIDGETS = false;
    static final boolean DEBUG_STRICT_MODE = false;

    private static final int MENU_GROUP_WALLPAPER = 1;
    private static final int MENU_WALLPAPER_SETTINGS = Menu.FIRST + 1;
    private static final int MENU_MANAGE_APPS = MENU_WALLPAPER_SETTINGS + 1;
    private static final int MENU_SYSTEM_SETTINGS = MENU_MANAGE_APPS + 1;
    private static final int MENU_HELP = MENU_SYSTEM_SETTINGS + 1;

    private static final int REQUEST_CREATE_SHORTCUT = 1;	// EX: bookmark of a browser
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_APPLICATION = 6;
    private static final int REQUEST_PICK_SHORTCUT = 7;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_PICK_WALLPAPER = 10;

    private static final int REQUEST_BIND_APPWIDGET = 11;

    static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";

    static final int SCREEN_COUNT = 5;
    static final int DEFAULT_SCREEN = 2;

    private static final String PREFERENCES = "launcher.preferences";
    // To turn on these properties, type
    // adb shell setprop log.tag.PROPERTY_NAME [VERBOSE | SUPPRESS]
    static final String FORCE_ENABLE_ROTATION_PROPERTY = "launcher_force_rotate";
    static final String DUMP_STATE_PROPERTY = "launcher_dump_state";

    // The Intent extra that defines whether to ignore the launch animation
    static final String INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION =
            "com.android.launcher.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CONTAINER = "launcher.add_container";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cell_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cell_y";
    // Type: boolean
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME = "launcher.rename_folder";
    // Type: long
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME_ID = "launcher.rename_folder_id";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_X = "launcher.add_span_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SPAN_Y = "launcher.add_span_y";
    // Type: parcelable
    private static final String RUNTIME_STATE_PENDING_ADD_WIDGET_INFO = "launcher.add_widget_info";

    private static final String TOOLBAR_ICON_METADATA_NAME = "com.android.launcher.toolbar_icon";
    private static final String TOOLBAR_SEARCH_ICON_METADATA_NAME =
            "com.android.launcher.toolbar_search_icon";
    private static final String TOOLBAR_VOICE_SEARCH_ICON_METADATA_NAME =
            "com.android.launcher.toolbar_voice_search_icon";

    /** The different states that Launcher can be in. */
    public enum State { NONE, WORKSPACE, APPS_CUSTOMIZE, APPS_CUSTOMIZE_SPRING_LOADED };
    // RTKCOMMENT: make mState public...
    public State mState = State.WORKSPACE;
    private AnimatorSet mStateAnimation;
    private AnimatorSet mDividerAnimator;

    static final int APPWIDGET_HOST_ID = 1024;
    private static final int EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT = 300;
    private static final int EXIT_SPRINGLOADED_MODE_LONG_TIMEOUT = 600;
    private static final int SHOW_CLING_DURATION = 550;
    private static final int DISMISS_CLING_DURATION = 250;

    private static final Object sLock = new Object();
    private static int sScreen = DEFAULT_SCREEN;

    // How long to wait before the new-shortcut animation automatically pans the workspace
    private static int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 10;

    private static final String S_EXT_STORAGE_PATH="/storage/";
    private static final String S_SD_CARD_PATH_PREFIX="mmcblk";
    
    public static final int S_TYPE_NO_STORAGE=1;
    public static final int S_TYPE_DATABASE_SCANNING=2;
    public static final int S_TYPE_UNMOUNT_STORAGE=3;

    private final BroadcastReceiver mCloseSystemDialogsReceiver
            = new CloseSystemDialogsIntentReceiver();
    private final ContentObserver mWidgetObserver = new AppWidgetResetObserver();

    private LayoutInflater mInflater;

    public Workspace mWorkspace;
    private View mQsbDivider;
    private View mDockDivider;
    private View mLauncherView;
    private DragLayer mDragLayer;
    private DragController mDragController;

    private AppWidgetManager mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    private ItemInfo mPendingAddInfo = new ItemInfo();
    private AppWidgetProviderInfo mPendingAddWidgetInfo;

    private int[] mTmpAddItemCellCoordinates = new int[2];

    private FolderInfo mFolderInfo;

    private Hotseat mHotseat;
    private View mAllAppsButton;

    private SearchDropTargetBar mSearchDropTargetBar;
    private AppsCustomizeTabHost mAppsCustomizeTabHost;
    private AppsCustomizePagedView mAppsCustomizeContent;
    private boolean mAutoAdvanceRunning = false;

    private Bundle mSavedState;
    // We set the state in both onCreate and then onNewIntent in some cases, which causes both
    // scroll issues (because the workspace may not have been measured yet) and extra work.
    // Instead, just save the state that we need to restore Launcher to, and commit it in onResume.
    private State mOnResumeState = State.NONE;

    private SpannableStringBuilder mDefaultKeySsb = null;

    private boolean mWorkspaceLoading = true;

    private boolean mPaused = true;
    private boolean mRestoring;
    private boolean mWaitingForResult;
    private boolean mOnResumeNeedsLoad;

    // Keep track of whether the user has left launcher
    private static boolean sPausedFromUserAction = false;

    private Bundle mSavedInstanceState;

    private LauncherModel mModel;
    private IconCache mIconCache;
    private boolean mUserPresent = true;
    private boolean mVisible = false;
    private boolean mAttached = false;

    private static LocaleConfiguration sLocaleConfiguration = null;

    private static HashMap<Long, FolderInfo> sFolders = new HashMap<Long, FolderInfo>();

    private Intent mAppMarketIntent = null;

    // Related to the auto-advancing of widgets
    private final int ADVANCE_MSG = 1;
    private final int RTK_ADVANCE_MSG = 2;
    
    private final String SWITCH_MOVE_MODE_AFTER_ADD_COMMAND = "SwitchMoveModeAfterAddCommand";
    private final String DRAW_FOCUS_AFTER_ON_RESUME = "DrawFocusAfterOnResume";
    private final String DRAW_FOCUS_AFTER_FAKE_DRAG_CANCELED = "DrawFocusAfterFakeDragCanceled";
    private final String SEND_FORCE_EXPAND_NOTIFICATION_PANEL = "ForceExpandNotificationPanel";
    
    private final int mAdvanceInterval = 20000;
    private final int mAdvanceStagger = 250;
    private long mAutoAdvanceSentTime;
    private long mAutoAdvanceTimeLeft = -1;
    private HashMap<View, AppWidgetProviderInfo> mWidgetsToAdvance =
        new HashMap<View, AppWidgetProviderInfo>();

    // Determines how long to wait after a rotation before restoring the screen orientation to
    // match the sensor state.
    private final int mRestoreScreenOrientationDelay = 500;

    // External icons saved in case of resource changes, orientation, etc.
    private static Drawable.ConstantState[] sGlobalSearchIcon = new Drawable.ConstantState[2];
    private static Drawable.ConstantState[] sVoiceSearchIcon = new Drawable.ConstantState[2];
    private static Drawable.ConstantState[] sAppMarketIcon = new Drawable.ConstantState[2];

    private Drawable mWorkspaceBackgroundDrawable;
    private Drawable mBlackBackgroundDrawable;

    private final ArrayList<Integer> mSynchronouslyBoundPages = new ArrayList<Integer>();

    static final ArrayList<String> sDumpLogs = new ArrayList<String>();

    // We only want to get the SharedPreferences once since it does an FS stat each time we get
    // it from the context.
    private SharedPreferences mSharedPrefs;

    // Holds the page that we need to animate to, and the icon views that we need to animate up
    // when we scroll to that page on resume.
    private int mNewShortcutAnimatePage = -1;
    private ArrayList<View> mNewShortcutAnimateViews = new ArrayList<View>();
    private ImageView mFolderIconImageView;
    private Bitmap mFolderIconBitmap;
    private Canvas mFolderIconCanvas;
    private Rect mRectForFolderAnimation = new Rect();

    private BubbleTextView mWaitingForResume;

    private HideFromAccessibilityHelper mHideFromAccessibilityHelper
        = new HideFromAccessibilityHelper();
    
    // RTKCOMMENT: variables for fake drag and drop
    public boolean bIsInFakeDragState=false;
    public int fakeDragStepWidth;
	public int fakeDragStepHeight;
	public View onLongClickSelectedView=null;
	public ArrayList<String> unRemovableWidgetLabelList=null;
	
	// RTKCOMMENT: label of each inapp widget
	public String settingWidgetLabel=null;
	public String playMusicWidgetLabel=null;
	public String fileBrowserWidgetLabel=null;
	public String videoAppWidgetLabel=null;
	public String favoriteAppWidgetLabel=null;
	public String appWidgetLabel=null;
	public String widiWidgetLabel=null;
	public String wifiStatusWidgetLabel=null;
	public String storageWidgetLabel=null;
	public String sourceWidgetLabel=null;
	
	public int focusFrameThickness=0;
	
	public FocusView mFocusFrameView=null;
	public View mWorkspaceFocusChild=null;
	
	public Rect workspaceBound = null;

	public View mDtvRootView = null;
	/**
	 * Variable to store latest view added into workspace, no matter the way how it is been added.
	 */
	public View mLatestAddedViewByOptionMenu=null;
	public boolean bSetNewAddedChildToFocusAndMoveMode=false;
	
	private ConnectivityManager connManager;
	
	private Dialog mMiracastDialog=null;
	//private Dialog mStorageUnmountDialog=null;
	
	// following members are used for storage widget
	public MediaScanStatusMonitor mMonitor;
    public ArrayList<String> mExtStorageList=new ArrayList<String>();
    
    private RTKPopupWindowHelper workspacePopupHelper=null;
    private RTKPopupWindowHelper appCustomPopupHelper=null;

    private ArrayList<String> mWhiteList = null;

    public boolean mBootCompleted = false;
    public boolean mScreenState = true;
    public boolean mHasPendingPreviewRequest = false;

    /*
    public ArrayList<EjectDialogListAdaptor> mEjectDialogList = new ArrayList<Launcher.EjectDialogListAdaptor>();
    
    class EjectDialogListAdaptor{
        public String mMountPath;
        public String mDisplayName;
        public CheckBox mCheckbox;
        
        public EjectDialogListAdaptor(String mount, String name){
            mMountPath=mount;
            mDisplayName=name;
        }
        
        public String toString(){
            return ""+mMountPath+" "+mDisplayName;
        }
    }
    */
    
    class MediaScanStatusMonitor extends BroadcastReceiver{
        
        public int scanningCount=0;
        
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            
            String action = arg1.getAction();
            Uri mData = arg1.getData();
            String uriPath = mData.getPath();
            
            Log.i(TAG,"onReceive:"+arg1.getAction()+" uriPath:"+uriPath);
            
            if(action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED))
                scanningCount=scanningCount+1;
            
            if(action.equals(Intent.ACTION_MEDIA_SCANNER_FINISHED))
                scanningCount=scanningCount-1;
        }
        
        public boolean isScanning(){
            return scanningCount>0?true:false;
        }
    }
    
    private Runnable mBuildLayersRunnable = new Runnable() {
        public void run() {
            if (mWorkspace != null) {
                mWorkspace.buildPageHardwareLayers();
            }
        }
    };

    private static ArrayList<PendingAddArguments> sPendingAddList
            = new ArrayList<PendingAddArguments>();

    private static boolean sForceEnableRotation = isPropertyEnabled(FORCE_ENABLE_ROTATION_PROPERTY);

    private static class PendingAddArguments {
        int requestCode;
        Intent intent;
        long container;
        int screen;
        int cellX;
        int cellY;
        // new field added for realtek launcher
        boolean bSwitchToMoveMode=false;
    }
    
    private boolean bLauncherIsForground=false;

    //public RTKPopupWindowHelper workspacePopupHelper=null;
    //public RTKPopupWindowHelper appCustomPopupHelper=null;
    
    private static boolean isPropertyEnabled(String propertyName) {
        return Log.isLoggable(propertyName, Log.VERBOSE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG_STRICT_MODE) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        super.onCreate(savedInstanceState);
        // For general debug purpose in Launcher. 
        DebugHelper.ENABLE_DEBUG_TRACE=false;
        // TRY TO FIX BUG: 42995
        // clearLauncherCache();
        LauncherApplication app = ((LauncherApplication)getApplication());
        mSharedPrefs = getSharedPreferences(LauncherApplication.getSharedPreferencesKey(),
                Context.MODE_PRIVATE);
        mModel = app.setLauncher(this);
        app.mLauncher = this;
        mIconCache = app.getIconCache();
        mDragController = new DragController(this);
        mInflater = getLayoutInflater();

        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mAppWidgetHost = new LauncherAppWidgetHost(this, APPWIDGET_HOST_ID);
        mAppWidgetHost.startListening();

        // If we are getting an onCreate, we can actually preempt onResume and unset mPaused here,
        // this also ensures that any synchronous binding below doesn't re-trigger another
        // LauncherModel load.
        mPaused = false;

        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing(
                    Environment.getExternalStorageDirectory() + "/launcher");
        }

        checkForLocaleChange();
        setContentView(R.layout.launcher);
        setupViews();
        showFirstRunWorkspaceCling();

        registerContentObservers();

        lockAllApps();

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        // Update customization drawer _after_ restoring the states
        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.onPackagesUpdated();
        }

        if (PROFILE_STARTUP) {
            android.os.Debug.stopMethodTracing();
        }

        if (!mRestoring) {
            if (sPausedFromUserAction) {
                // If the user leaves launcher, then we should just load items asynchronously when
                // they return.
                mModel.startLoader(true, -1);
            } else {
                // We only load the page synchronously if the user rotates (or triggers a
                // configuration change) while launcher is in the foreground
                mModel.startLoader(true, mWorkspace.getCurrentPage());
            }
        }

        if (!mModel.isAllAppsLoaded()) {
            ViewGroup appsCustomizeContentParent = (ViewGroup) mAppsCustomizeContent.getParent();
            mInflater.inflate(R.layout.apps_customize_progressbar, appsCustomizeContentParent);
        }

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);

        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mCloseSystemDialogsReceiver, filter);

        updateGlobalIcons();

        // On large interfaces, we want the screen to auto-rotate based on the current orientation
        unlockScreenOrientation(true);
        
        // RTKCOMMENT: init popup helper for workspace
        //workspacePopupHelper=new RTKPopupWindowHelper(this,RTKPopupWindowHelper.POPUP_MODE_WORKSPACE);
        
        // RTKCOMMENT: init un-removable widget label list
        //if(unRemovableWidgetLabelList==null){
    	unRemovableWidgetLabelList=new ArrayList<String>();
    	unRemovableWidgetLabelList.add(getResources().getString(R.string.w_app_drawer_label));
    	unRemovableWidgetLabelList.add(getResources().getString(R.string.w_storage_label));
    	// COMMENT: DTV widget is not listed here cause it can be found by checking class type
        //}
    	
    	//RTKCOMMENT: get label string via resource class.
    	settingWidgetLabel=getResources().getString(R.string.w_setting_label);
    	playMusicWidgetLabel=getResources().getString(R.string.w_google_music_label);
    	fileBrowserWidgetLabel=getResources().getString(R.string.w_filebrowser_label);
    	videoAppWidgetLabel=getResources().getString(R.string.w_video_app_label);
    	favoriteAppWidgetLabel=getResources().getString(R.string.w_favorite_app_label);
    	appWidgetLabel=getResources().getString(R.string.w_app_drawer_label);
    	widiWidgetLabel=getResources().getString(R.string.w_widi_label);
    	wifiStatusWidgetLabel=getResources().getString(R.string.w_wifi_status_label);
    	storageWidgetLabel=getResources().getString(R.string.w_storage_label);
    	sourceWidgetLabel=getResources().getString(R.string.w_dtv_label);
    	
    	focusFrameThickness=getResources().getDimensionPixelOffset(R.dimen.rtk_workspace_focus_frame_thickness);
    	
    	connManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
    	mMiracastDialog=null;
    	//mStorageUnmountDialog=null;
    	
    	mMonitor = new MediaScanStatusMonitor();
        IntentFilter filter2 = new IntentFilter();
        filter2.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        filter2.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        filter2.addDataScheme("file");
        registerReceiver(mMonitor, filter2);

        // init mWhiteList in onCreate
        mWhiteList = new ArrayList<String>();
        mWhiteList.add("com.example.RTKMiracastSink");
        mWhiteList.add("com.android.gallery3d");
        // Bug fix: 46661
        mWhiteList.add("com.android.bluetooth");
        // Bug fix: PHOENIX-249
        mWhiteList.add("com.android.music");
        //mWhiteList.add("com.test.pkg.2");

        mHandler.postDelayed(new Runnable() {
                public void run() {
                    Log.d(TAG, "delayRunnable check mBootCompleted:"+mBootCompleted);
                    if(mBootCompleted==false){
                        mBootCompleted = true;
                    }
                    if(mState == State.WORKSPACE && mBootCompleted){
                        Log.d(TAG, "draw focus frame");
                        clearWorkspaceFocusViewFrame();
                        redrawWorkspaceFocusViewFrame();
                    }
                }
                },
                3000);
    }
    
    // called when showing popup.
    public void updateExtStorageList(){
        
        // if popup is showing up, then do not refresh storage status
        //if(mStorageUnmountDialog!=null)
        //    return;
        
        mExtStorageList.clear();
        File file = new File(S_EXT_STORAGE_PATH);
        //boolean bIsDir = file.isDirectory();
        //Log.i(TAG,"xx "+bIsDir);
        String[] list = file.list();
        
        for(int i=0;i<list.length;i++){
            if(list[i].startsWith("sd") && !list[i].equals("sdcard0")){
                mExtStorageList.add(list[i]);
            }
            if(list[i].startsWith(S_SD_CARD_PATH_PREFIX)){
                mExtStorageList.add(list[i]);
            }
        }
    }

    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        sPausedFromUserAction = true;
    }

    private void updateGlobalIcons() {
        boolean searchVisible = false;
        boolean voiceVisible = false;
        // If we have a saved version of these external icons, we load them up immediately
        int coi = getCurrentOrientationIndexForGlobalIcons();
        if (sGlobalSearchIcon[coi] == null || sVoiceSearchIcon[coi] == null ||
                sAppMarketIcon[coi] == null) {
            updateAppMarketIcon();
            searchVisible = updateGlobalSearchIcon();
            voiceVisible = updateVoiceSearchIcon(searchVisible);
        }
        if (sGlobalSearchIcon[coi] != null) {
             updateGlobalSearchIcon(sGlobalSearchIcon[coi]);
             searchVisible = true;
        }
        if (sVoiceSearchIcon[coi] != null) {
            updateVoiceSearchIcon(sVoiceSearchIcon[coi]);
            voiceVisible = true;
        }
        if (sAppMarketIcon[coi] != null) {
            updateAppMarketIcon(sAppMarketIcon[coi]);
        }
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.onSearchPackagesChanged(searchVisible, voiceVisible);
        }
    }

    private void checkForLocaleChange() {
        if (sLocaleConfiguration == null) {
            new AsyncTask<Void, Void, LocaleConfiguration>() {
                @Override
                protected LocaleConfiguration doInBackground(Void... unused) {
                    LocaleConfiguration localeConfiguration = new LocaleConfiguration();
                    readConfiguration(Launcher.this, localeConfiguration);
                    return localeConfiguration;
                }

                @Override
                protected void onPostExecute(LocaleConfiguration result) {
                    sLocaleConfiguration = result;
                    checkForLocaleChange();  // recursive, but now with a locale configuration
                }
            }.execute();
            return;
        }

        final Configuration configuration = getResources().getConfiguration();

        final String previousLocale = sLocaleConfiguration.locale;
        final String locale = configuration.locale.toString();

        final int previousMcc = sLocaleConfiguration.mcc;
        final int mcc = configuration.mcc;

        final int previousMnc = sLocaleConfiguration.mnc;
        final int mnc = configuration.mnc;

        boolean localeChanged = !locale.equals(previousLocale) || mcc != previousMcc || mnc != previousMnc;

        if (localeChanged) {
            sLocaleConfiguration.locale = locale;
            sLocaleConfiguration.mcc = mcc;
            sLocaleConfiguration.mnc = mnc;

            mIconCache.flush();

            final LocaleConfiguration localeConfiguration = sLocaleConfiguration;
            new Thread("WriteLocaleConfiguration") {
                @Override
                public void run() {
                    writeConfiguration(Launcher.this, localeConfiguration);
                }
            }.start();
        }
    }

    private static class LocaleConfiguration {
        public String locale;
        public int mcc = -1;
        public int mnc = -1;
    }

    private static void readConfiguration(Context context, LocaleConfiguration configuration) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(context.openFileInput(PREFERENCES));
            configuration.locale = in.readUTF();
            configuration.mcc = in.readInt();
            configuration.mnc = in.readInt();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private static void writeConfiguration(Context context, LocaleConfiguration configuration) {
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(context.openFileOutput(PREFERENCES, MODE_PRIVATE));
            out.writeUTF(configuration.locale);
            out.writeInt(configuration.mcc);
            out.writeInt(configuration.mnc);
            out.flush();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            context.getFileStreamPath(PREFERENCES).delete();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    boolean isDraggingEnabled() {
        // We prevent dragging when we are loading the workspace as it is possible to pick up a view
        // that is subsequently removed from the workspace in startBinding().
        return !mModel.isLoadingWorkspace();
    }

    static int getScreen() {
        synchronized (sLock) {
            return sScreen;
        }
    }

    static void setScreen(int screen) {
        synchronized (sLock) {
            sScreen = screen;
        }
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private boolean completeAdd(PendingAddArguments args) {
    	
    	DebugHelper.dump("Break complete add.");
    	
    	
        boolean result = false;
        switch (args.requestCode) {
            case REQUEST_PICK_APPLICATION:
            	DebugHelper.dump("[CompleteAdd] REQUEST_PICK_APPLICATION");
                completeAddApplication(args.intent, args.container, args.screen, args.cellX,
                        args.cellY,args.bSwitchToMoveMode);
                break;
            case REQUEST_PICK_SHORTCUT:
            	DebugHelper.dump("[CompleteAdd] REQUEST_PICK_SHORTCUT");
                processShortcut(args.intent);
                break;
            case REQUEST_CREATE_SHORTCUT:
            	DebugHelper.dump("[CompleteAdd] REQUEST_CREATE_SHORTCUT");
                completeAddShortcut(args.intent, args.container, args.screen, args.cellX,
                        args.cellY,args.bSwitchToMoveMode);
                result = true;
                break;
            case REQUEST_CREATE_APPWIDGET:
            	DebugHelper.dump("[CompleteAdd] REQUEST_CREATE_APPWIDGET");
                int appWidgetId = args.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                completeAddAppWidget(appWidgetId, args.container, args.screen, null, null, false);
                result = true;
                break;
            case REQUEST_PICK_WALLPAPER:
                // We just wanted the activity result here so we can clear mWaitingForResult
                break;
        }
        // Before adding this resetAddInfo(), after a shortcut was added to a workspace screen,
        // if you turned the screen off and then back while in All Apps, Launcher would not
        // return to the workspace. Clearing mAddInfo.container here fixes this issue
        resetAddInfo();
        return result;
    }

    @Override
    protected void onActivityResult(
            final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == REQUEST_BIND_APPWIDGET) {
            int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (resultCode == RESULT_CANCELED) {
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId);
            } else if (resultCode == RESULT_OK) {
                addAppWidgetImpl(appWidgetId, mPendingAddInfo, null, mPendingAddWidgetInfo);
            }
            return;
        }
        boolean delayExitSpringLoadedMode = false;
        boolean isWidgetDrop = (requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET);
        mWaitingForResult = false;

        // We have special handling for widgets
        if (isWidgetDrop) {
            int appWidgetId = data != null ?
                    data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) : -1;
            if (appWidgetId < 0) {
                Log.e(TAG, "Error: appWidgetId (EXTRA_APPWIDGET_ID) was not returned from the \\" +
                        "widget configuration activity.");
                completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId);
            } else {
                completeTwoStageWidgetDrop(resultCode, appWidgetId);
            }
            return;
        }

        // The pattern used here is that a user PICKs a specific application,
        // which, depending on the target, might need to CREATE the actual target.

        // For example, the user would PICK_SHORTCUT for "Music playlist", and we
        // launch over to the Music app to actually CREATE_SHORTCUT.
        if (resultCode == RESULT_OK && mPendingAddInfo.container != ItemInfo.NO_ID) {
            final PendingAddArguments args = new PendingAddArguments();
            args.requestCode = requestCode;
            args.intent = data;
            args.container = mPendingAddInfo.container;
            args.screen = mPendingAddInfo.screen;
            args.cellX = mPendingAddInfo.cellX;
            args.cellY = mPendingAddInfo.cellY;
            args.bSwitchToMoveMode=mPendingAddInfo.bSwitchToMoveModeAfterAdd;
            if (isWorkspaceLocked()) {
                sPendingAddList.add(args);
            } else {
                delayExitSpringLoadedMode = completeAdd(args);
            }
        }
        mDragLayer.clearAnimatedView();
        // Exit spring loaded mode if necessary after cancelling the configuration of a widget
        exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED), delayExitSpringLoadedMode,
                null);
    }

    private void completeTwoStageWidgetDrop(final int resultCode, final int appWidgetId) {
        CellLayout cellLayout =
                (CellLayout) mWorkspace.getChildAt(mPendingAddInfo.screen);
        Runnable onCompleteRunnable = null;
        int animationType = 0;

        AppWidgetHostView boundWidget = null;
        if (resultCode == RESULT_OK) {
            animationType = Workspace.COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION;
            final AppWidgetHostView layout = mAppWidgetHost.createView(this, appWidgetId,
                    mPendingAddWidgetInfo);
            boundWidget = layout;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    completeAddAppWidget(appWidgetId, mPendingAddInfo.container,
                            mPendingAddInfo.screen, layout, null,
                            mPendingAddInfo.bSwitchToMoveModeAfterAdd);
                    exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED), false,
                            null);
                }
            };
        } else if (resultCode == RESULT_CANCELED) {
            animationType = Workspace.CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION;
            onCompleteRunnable = new Runnable() {
                @Override
                public void run() {
                    exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED), false,
                            null);
                }
            };
        }
        if (mDragLayer.getAnimatedView() != null) {
            mWorkspace.animateWidgetDrop(mPendingAddInfo, cellLayout,
                    (DragView) mDragLayer.getAnimatedView(), onCompleteRunnable,
                    animationType, boundWidget, true);
        } else {
            // The animated view may be null in the case of a rotation during widget configuration
            onCompleteRunnable.run();
        }
    }

    // can not kill this process
    private boolean checkIsInWhiteList(String name){
        int size = mWhiteList.size();
        for(int i=0;i<size;i++){
            if(name.equals(mWhiteList.get(i))){
                return true;
            }
        }
        return false;
    }

    private void killBackgroundTasks(){
        Log.i(TAG,"killBackgroundTasks");
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RecentTaskInfo> recentTasks = am.getRecentTasks(30,
            ActivityManager.RECENT_WITH_EXCLUDED);

        int size = recentTasks.size();

        for(int i=0;i<size;i++){
            ActivityManager.RecentTaskInfo recentInfo = recentTasks.get(i);
            Intent intent = new Intent(recentInfo.baseIntent);
            String pkgName = intent.getComponent().getPackageName();
            int id = recentInfo.id;
            int persistentId = recentInfo.persistentId;
            if(!pkgName.equals("com.realtek.launcher") && !checkIsInWhiteList(pkgName)){
                Log.i(TAG,"Kill package:"+pkgName);
                am.forceStopPackage(pkgName);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        bLauncherIsForground=true;
		
	Log.i(TAG, "onResume mOnResumeState:"+mOnResumeState+" mState:"+mState);

        boolean bShowSourceWidget = false;
        if(mOnResumeState == State.WORKSPACE || mState == State.WORKSPACE){
            bShowSourceWidget = true;
        }

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        // do not kill bg process in cts mode
        String ctsMode = SystemProperties.get("persist.rtk.ctstest","false");
        boolean bCTSMode = false;
        if(ctsMode.equals("1") || ctsMode.equals("true")){
            bCTSMode = true;
        }

        boolean doKillBGTasks = false;

        if(am.isLowRamDevice() && !bCTSMode && doKillBGTasks)
            killBackgroundTasks();
	    // BUG_FIX: 44895
	    if(appCustomPopupHelper!=null){
	        appCustomPopupHelper.forceDismissPopup();
	    }

        // Restore the previous launcher state
        if (mOnResumeState == State.WORKSPACE) {
            showWorkspace(false);
            // cause in showWorkspace will initSurfaceViewWidget.., so set it to false again
            bShowSourceWidget = false;
        } else if (mOnResumeState == State.APPS_CUSTOMIZE) {
            showAllApps(false,ContentType.Applications);
        }
        mOnResumeState = State.NONE;

        // Background was set to gradient in onPause(), restore to black if in all apps.
        setWorkspaceBackground(mState == State.WORKSPACE);

        // Process any items that were added while Launcher was away
        InstallShortcutReceiver.flushInstallQueue(this);

        mPaused = false;
        sPausedFromUserAction = false;
        if (mRestoring || mOnResumeNeedsLoad) {
            mWorkspaceLoading = true;
            mModel.startLoader(true, -1);
            mRestoring = false;
            mOnResumeNeedsLoad = false;
        }

        // Reset the pressed state of icons that were locked in the press state while activities
        // were launching
        if (mWaitingForResume != null) {
            // Resets the previous workspace icon press state
            mWaitingForResume.setStayPressed(false);
        }
        if (mAppsCustomizeContent != null) {
            // Resets the previous all apps icon press state
            mAppsCustomizeContent.resetDrawableState();
        }
        // It is possible that widgets can receive updates while launcher is not in the foreground.
        // Consequently, the widgets will be inflated in the orientation of the foreground activity
        // (framework issue). On resuming, we ensure that any widgets are inflated for the current
        // orientation.
        getWorkspace().reinflateWidgetsIfNecessary();

        // Again, as with the above scenario, it's possible that one or more of the global icons
        // were updated in the wrong orientation.
        updateGlobalIcons();
        sendRtkAdvMessageToHandler(DRAW_FOCUS_AFTER_ON_RESUME, 500);

        if(bShowSourceWidget){
            Log.d(TAG, "initSurfaceViewWidget");
            initSurfaceViewWidget("showWorkspace");
        }

    }
   
    public void uninitSurfaceViewWidget(String extraString){
    	Intent i = new Intent(ConstantsDef.UNINIT_SURFACEVIEW_WIDGET_INTENT);
        i.putExtra(ConstantsDef.UNINIT_EXTRA_NAME, extraString);
        sendBroadcast(i);
        DebugHelper.dump("uninitSurfaceViewWidget -------------> "+extraString);
    	
    }
    
    @Override
    protected void onPause() {
        // NOTE: We want all transitions from launcher to act as if the wallpaper were enabled
        // to be consistent.  So re-enable the flag here, and we will re-disable it as necessary
        // when Launcher resumes and we are still in AllApps.
        Log.i(TAG, "onPause");
        bLauncherIsForground=false;
        updateWallpaperVisibility(true);
        
        // RTKCOMMENT: send broadcast to notify SurfaceView widget.
        uninitSurfaceViewWidget("onPause");
        
        super.onPause();
        mPaused = true;
        
        if(bIsInFakeDragState){
            // BUG_FIX:41969 fix system crash bug
            DebugHelper.dump2("BUG_FIX:41969");
            //mDragController.confirmFakeDrag(mDragController.mMotionDownX, mDragController.mMotionDownY);
            mDragController.cancelFakeDrag();
            bIsInFakeDragState=false;
        }else{
            mDragController.cancelDrag();
            mDragController.resetLastGestureUpTime();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
		
	Log.i(TAG, "onStop");

	//We will stop the miracast function when user exit the home menu
	//Except the Miracast is connecting and displaying 
	ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);

	// get the info from the currently running task
         List< ActivityManager.RunningTaskInfo > taskInfo = am.getRunningTasks(1);	
	ComponentName componentInfo = taskInfo.get(0).topActivity;

	DebugHelper.dump("Package name= " + componentInfo.getPackageName());

    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Flag the loader to stop early before switching
        mModel.stopLoader();
        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.surrender();
        }
        return Boolean.TRUE;
    }

    // We can't hide the IME if it was forced open.  So don't bother
    /*
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            final InputMethodManager inputManager = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            inputManager.hideSoftInputFromWindow(lp.token, 0, new android.os.ResultReceiver(new
                        android.os.Handler()) {
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            Log.d(TAG, "ResultReceiver got resultCode=" + resultCode);
                        }
                    });
            Log.d(TAG, "called hideSoftInputFromWindow from onWindowFocusChanged");
        }
    }
    */

    private boolean acceptFilter() {
        final InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        return !inputManager.isFullscreenMode();
    }

    /**
     * RTK added API<br>
     * Designed to replace {@link #onSearchRequested()}, 
     * is triggered when KEYCODE_M is pressed.
     */
    public void onShowWorkspaceOptionMenu(){
		// get background drawable size:
		// BitmapDrawable bgDrawable = (BitmapDrawable) getResources().getDrawable(R.drawable.bg_option);
		// int width = bgDrawable.getBitmap().getWidth();
		// DebugHelper.dump("background drawable width="+width);
		workspacePopupHelper=new RTKPopupWindowHelper(
				this,
				RTKPopupWindowHelper.POPUP_MODE_WORKSPACE,
				getWorkspaceFocusView());
		
    	workspacePopupHelper.showsPopup(mDragLayer);
    }
    
    public void onShowAppCustomPageOptionMenu(View focusView){
    	appCustomPopupHelper=new RTKPopupWindowHelper(
    			this,
    			RTKPopupWindowHelper.POPUP_MODE_DRAWER,
    			focusView);
    	appCustomPopupHelper.showsPopup(mDragLayer);
    }
    
    
    /**
     * RTK added API<br>
     * A simple API to let Launcher switch to APP page
     */
    public void onShowAppDrawer(){
    	switchToAppCustomizedPage(ContentType.Applications);
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int uniChar = event.getUnicodeChar();
        final boolean handled = super.onKeyDown(keyCode, event);
        
        DebugHelper.dump("[RTKDEBUG]                                Launcher onKeyDown, handled:"+handled);
        
        final boolean isKeyNotWhitespace = uniChar > 0 && !Character.isWhitespace(uniChar);
        if (!handled && acceptFilter() && isKeyNotWhitespace) {
            boolean gotKey = TextKeyListener.getInstance().onKeyDown(mWorkspace, mDefaultKeySsb,
                    keyCode, event);
            if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
                // something usable has been typed - start a search
                // the typed text will be retrieved and cleared by
                // showSearchDialog()
                // If there are multiple keystrokes before the search dialog takes focus,
                // onSearchRequested() will be called for every keystroke,
                // but it is idempotent, so it's fine.
                
            	// RTKCOMMENT: do not launch searchRequest, only launch option menu request
            	
            	//return onSearchRequested();
            	
            	final int action = event.getAction();
                final boolean handleKeyEvent = (action != KeyEvent.ACTION_UP);
                
                if(handleKeyEvent){
                	switch(keyCode){
                	/*
                	case ConstantsDef.LAUNCHER_KEY_OPTION_MENU: //KeyEvent.KEYCODE_M:
                		// check Launcher state:
                		DebugHelper.dump("XXX"+mState);
                		if(mState==State.WORKSPACE)
                			onShowWorkspaceOptionMenu();
                		else if(mState==State.APPS_CUSTOMIZE)
                			onShowAppCustomPageOptionMenu(null);
                		else
                			showToastMessage(getResources().getString(R.string.toast_page_loading));
                		break;
                    */
                	case KeyEvent.KEYCODE_N:
                		onShowAppDrawer();
                		break;
                	default:
                		DebugHelper.dump("Launcher Handle key....");
                		break;
                	}
                }
                return true;
            }
        }

        // Eat the long press event so the keyboard doesn't come up.
        if (keyCode == KeyEvent.KEYCODE_MENU && event.isLongPress()) {
            return true;
        }
        
        if( keyCode == KeyEvent.KEYCODE_DPAD_UP || 
        	keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
        	keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
        	keyCode == KeyEvent.KEYCODE_DPAD_RIGHT){
        	
        	
        	if(mState==State.WORKSPACE){
        		if(mFocusFrameView==null){
        			DebugHelper.dump("workspace has no focus child");
        			// that means no focus
        			View v = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(0);
        			if(v!=null){
        				v.requestFocus();
        				redrawWorkspaceFocusViewFrame(v);
        			}
        		}
        	}
        }
        
        DebugHelper.dump("[RTKDEBUG]               launcher onKeyDown handled:"+handled);
        
        return handled;
    }
    
    
    public void showToastMessage(String message){
    	Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
    
    private String getTypedText() {
        return mDefaultKeySsb.toString();
    }

    private void clearTypedText() {
        mDefaultKeySsb.clear();
        mDefaultKeySsb.clearSpans();
        Selection.setSelection(mDefaultKeySsb, 0);
    }

    /**
     * Given the integer (ordinal) value of a State enum instance, convert it to a variable of type
     * State
     */
    private static State intToState(int stateOrdinal) {
        State state = State.WORKSPACE;
        final State[] stateValues = State.values();
        for (int i = 0; i < stateValues.length; i++) {
            if (stateValues[i].ordinal() == stateOrdinal) {
                state = stateValues[i];
                break;
            }
        }
        return state;
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    private void restoreState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        State state = intToState(savedState.getInt(RUNTIME_STATE, State.WORKSPACE.ordinal()));
        if (state == State.APPS_CUSTOMIZE) {
            mOnResumeState = State.APPS_CUSTOMIZE;
        }

        int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN, -1);
        if (currentScreen > -1) {
            mWorkspace.setCurrentPage(currentScreen);
        }

        final long pendingAddContainer = savedState.getLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, -1);
        final int pendingAddScreen = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SCREEN, -1);

        if (pendingAddContainer != ItemInfo.NO_ID && pendingAddScreen > -1) {
            mPendingAddInfo.container = pendingAddContainer;
            //mPendingAddInfo.screen = pendingAddScreen;
            mPendingAddInfo.cellX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
            mPendingAddInfo.cellY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
            mPendingAddInfo.spanX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_X);
            mPendingAddInfo.spanY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y);
            mPendingAddWidgetInfo = savedState.getParcelable(RUNTIME_STATE_PENDING_ADD_WIDGET_INFO);
            mWaitingForResult = true;
            mRestoring = true;
        }


        boolean renameFolder = savedState.getBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, false);
        if (renameFolder) {
            long id = savedState.getLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID);
            mFolderInfo = mModel.getFolderById(this, sFolders, id);
            mRestoring = true;
        }


        // Restore the AppsCustomize tab
        if (mAppsCustomizeTabHost != null) {
            // For realtek system which would change density runtime, and would cause apk reloaded,
            // if resume state is in app drawer page, now launcher would alway back to first page, first index
            
            mAppsCustomizeTabHost.setContentTypeImmediate(mAppsCustomizeTabHost.getContentTypeForTabTag(AppsCustomizeTabHost.APPS_TAB_TAG));
            mAppsCustomizeContent.loadAssociatedPages(0);
            
            //String curTab = savedState.getString("apps_customize_currentTab");
            //if (curTab != null) {
                // set TAB
                //mAppsCustomizeTabHost.setContentTypeImmediate(
                //        mAppsCustomizeTabHost.getContentTypeForTabTag(curTab));
                // set page
                //mAppsCustomizeContent.loadAssociatedPages(
                //        mAppsCustomizeContent.getCurrentPage());
            //}

            // set Focused item index
            int currentIndex = savedState.getInt("apps_customize_currentIndex");
            mAppsCustomizeContent.restorePageForIndex(currentIndex);
        }
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        final DragController dragController = mDragController;

        mLauncherView = findViewById(R.id.launcher);
        mDragLayer = (DragLayer) findViewById(R.id.drag_layer);
        mWorkspace = (Workspace) mDragLayer.findViewById(R.id.workspace);
        mQsbDivider = findViewById(R.id.qsb_divider);
        mDockDivider = findViewById(R.id.dock_divider);

        mLauncherView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        mWorkspaceBackgroundDrawable = getResources().getDrawable(R.drawable.workspace_bg);
        mBlackBackgroundDrawable = new ColorDrawable(Color.BLACK);

        // Setup the drag layer
        mDragLayer.setup(this, dragController);

        // Setup the hotseat
        mHotseat = (Hotseat) findViewById(R.id.hotseat);
        if (mHotseat != null) {
            mHotseat.setup(this);
        }

        // Setup the workspace
        mWorkspace.setHapticFeedbackEnabled(false);
        mWorkspace.setOnLongClickListener(this);
        mWorkspace.setOnClickListener(this);
        mWorkspace.setup(dragController);
        dragController.addDragListener(mWorkspace);

        // Get the search/delete bar
        mSearchDropTargetBar = (SearchDropTargetBar) mDragLayer.findViewById(R.id.qsb_bar);

        // Setup AppsCustomize
        mAppsCustomizeTabHost = (AppsCustomizeTabHost) findViewById(R.id.apps_customize_pane);
        mAppsCustomizeContent = (AppsCustomizePagedView)
                mAppsCustomizeTabHost.findViewById(R.id.apps_customize_pane_content);
        mAppsCustomizeContent.setup(this, dragController);

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        dragController.setDragScoller(mWorkspace);
        dragController.setScrollView(mDragLayer);
        dragController.setMoveTarget(mWorkspace);
        dragController.addDropTarget(mWorkspace);
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.setup(this, dragController);
        }
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from R.layout.application.
     */
    View createShortcut(ShortcutInfo info) {
        return createShortcut(R.layout.application,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param layoutResId The id of the XML layout used to create the shortcut.
     * @param parent The group the shortcut belongs to.
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from layoutResId.
     */
    View createShortcut(int layoutResId, ViewGroup parent, ShortcutInfo info) {
        BubbleTextView favorite = (BubbleTextView) mInflater.inflate(layoutResId, parent, false);
        favorite.applyFromShortcutInfo(info, mIconCache);
        favorite.setOnClickListener(this);
        return favorite;
    }
    
    /**
     * API added for Realtek Launcher.<br>
     * @param data :Intent of app going to be added into workspace
     * @return add success or not
     */
    public boolean completeAddApplicationToWorkspaceFromOptionMenu(Intent data){
    	int[] resultXY=new int[2];
    	int screen=0;
    	int spanX=1;
    	int spanY=1;
    	boolean canAdd = findAvailableCellInWorkspaceForAddedItem(resultXY, spanX, spanY);
    	
    	if(canAdd){
    		
    		// follow existing flow to add application.
    		PendingAddArguments args = new PendingAddArguments();
    		
    		args.requestCode=REQUEST_PICK_APPLICATION;
    		args.intent=data;
    		args.container=Favorites.CONTAINER_DESKTOP;
    		args.screen=screen;
    		args.cellX=resultXY[0];
    		args.cellY=resultXY[1];
    		args.bSwitchToMoveMode=true;
    		
    		completeAdd(args);
    		//completeAddApplication(data, Favorites.CONTAINER_DESKTOP, screen, resultXY[0],resultXY[1]);
    		//showWorkspace(true);	// <-- do not switch to workspace here.
    		return true;
    	}else{
    		// if unable to add workspace shortcut, show a message.
    		showToastMessage(getResources().getString(R.string.toast_workspace_no_space));
    		return false;
    	}
    }
    
    /**
     * Add an application shortcut to the workspace.
     *
     * @param data The intent describing the application.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    public void completeAddApplication(Intent data, long container, int screen, int cellX, int cellY,
    		boolean bSwitchToMoveMode) {
    	
        final int[] cellXY = mTmpAddItemCellCoordinates;
        final CellLayout layout = getCellLayout(container, screen);

        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
        } else if (!layout.findCellForSpan(cellXY, 1, 1)) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }

        final ShortcutInfo info = mModel.getShortcutInfo(getPackageManager(), data, this);

        if (info != null) {
            info.setActivity(data.getComponent(), Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            info.container = ItemInfo.NO_ID;
            // RTKCOMMENT: set extra control flag.
            info.bSwitchToMoveModeAfterAdd=bSwitchToMoveMode;
            mWorkspace.addApplicationShortcut(info, layout, container, screen, cellXY[0], cellXY[1],
                    isWorkspaceLocked(), cellX, cellY);
            
        } else {
            Log.e(TAG, "Couldn't find ActivityInfo for selected application: " + data);
        }
    }
    
    /**
     * API added for Realtek launcher.<br>
     * To un-install an apk
     * @param appInfo
     */
    public void completeUninstallApplication(ApplicationInfo appInfo){
    	/** always use existing API instead of create a new one by our selves
    	Uri packageUri = Uri.parse("package:"+appInfo.packageName);
    	Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
        startActivity(uninstallIntent);
        */
    	
    	startApplicationUninstallActivity(appInfo);
    }
    

    /**
     * Add a shortcut to the workspace.
     *
     * @param data The intent describing the shortcut.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    private void completeAddShortcut(Intent data, long container, int screen, int cellX,
            int cellY, boolean bSwicthToMoveMode) {
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        CellLayout layout = getCellLayout(container, screen);

        boolean foundCellSpan = false;

        ShortcutInfo info = mModel.infoFromShortcutIntent(this, data, null);
        if (info == null) {
            return;
        }
        // Add for Realtek Launcher.
        info.bSwitchToMoveModeAfterAdd=bSwicthToMoveMode;
        final View view = createShortcut(info);

        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
            foundCellSpan = true;

            // If appropriate, either create a folder or add to an existing folder
            if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY, 0,
                    true, null,null)) {
                return;
            }
            DragObject dragObject = new DragObject();
            dragObject.dragInfo = info;
            if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, 0, dragObject,
                    true)) {
                return;
            }
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(touchXY[0], touchXY[1], 1, 1, cellXY);
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
        }

        if (!foundCellSpan) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }
        // RTKCOMMENT: where workspace config is stored
        LauncherModel.addItemToDatabase(this, info, container, screen, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            mWorkspace.addInScreen(view, container, screen, cellXY[0], cellXY[1], 1, 1,
                    isWorkspaceLocked());
        }
    }

    static int[] getRealtekWidgetSpan(ComponentName component){
        if(component.getClassName().equals(com.realtek.addon.widget.SettingWidgetProvider.class.getName())||
           component.getClassName().equals(com.realtek.addon.widget.FavoriteAppWidgetProvider.class.getName())||
           component.getClassName().equals(com.realtek.addon.widget.VideoAppWidgetProvider.class.getName())||
           component.getClassName().equals(com.realtek.addon.widget.FilebrowserWidgetProvider.class.getName())||
           component.getClassName().equals(com.realtek.addon.widget.GoogleMusicWidgetProvider.class.getName())){
            int[] span = new int[2];
            span[0]=span[1]=1;
            //DebugHelper.dumpSQA("return 1x1");
            return span;
        }else if(component.getClassName().equals(com.realtek.addon.widget.WifiStatusWidgetProvider.class.getName())||
                 component.getClassName().equals(com.realtek.addon.widget.NetworkTrafficWidgetProvider.class.getName())){
            int[] span = new int[2];
            span[0]=3;
            span[1]=1;
            //DebugHelper.dumpSQA("return 3x1");
            return span;
        }else if(component.getClassName().equals(com.realtek.addon.widget.WidiWidgetProvider.class.getName())||
                component.getClassName().equals(com.realtek.addon.widget.StorageWidgetProvider.class.getName())){
            int[] span = new int[2];
            span[0]=2;
            span[1]=1;
            //DebugHelper.dumpSQA("return 2x1");
            return span;
        }else{
            //DebugHelper.dumpSQA("return null");
            return null;
        }
    }

    static int[] getSpanForWidget(Context context, ComponentName component, int minWidth,
            int minHeight) {
        Rect padding = AppWidgetHostView.getDefaultPaddingForWidget(context, component, null);
        // We want to account for the extra amount of padding that we are adding to the widget
        // to ensure that it gets the full amount of space that it has requested
        int requiredWidth = minWidth + padding.left + padding.right;
        int requiredHeight = minHeight + padding.top + padding.bottom;
        int[] span = getRealtekWidgetSpan(component);
        if(span!=null)
            return span;
        else
            return CellLayout.rectToCell(context.getResources(), requiredWidth, requiredHeight, null);
    }

    static int[] getSpanForWidget(Context context, AppWidgetProviderInfo info) {
        return getSpanForWidget(context, info.provider, info.minWidth, info.minHeight);
    }

    static int[] getMinSpanForWidget(Context context, AppWidgetProviderInfo info) {
        return getSpanForWidget(context, info.provider, info.minResizeWidth, info.minResizeHeight);
    }

    static int[] getSpanForWidget(Context context, PendingAddWidgetInfo info) {
        return getSpanForWidget(context, info.componentName, info.minWidth, info.minHeight);
    }

    static int[] getMinSpanForWidget(Context context, PendingAddWidgetInfo info) {
        return getSpanForWidget(context, info.componentName, info.minResizeWidth,
                info.minResizeHeight);
    }

    /**
     * Add a widget to the workspace.
     *
     * @param appWidgetId The app widget id
     * @param cellInfo The position on screen where to create the widget.
     */
    private void completeAddAppWidget(final int appWidgetId, long container, int screen,
            AppWidgetHostView hostView, AppWidgetProviderInfo appWidgetInfo,
            boolean bSwitchToMoveMode) {
    	
        if (appWidgetInfo == null) {
            appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        }

        // Calculate the grid spans needed to fit this widget
        CellLayout layout = getCellLayout(container, screen);

        int[] minSpanXY = getMinSpanForWidget(this, appWidgetInfo);
        int[] spanXY = getSpanForWidget(this, appWidgetInfo);

        // Try finding open space on Launcher screen
        // We have saved the position to which the widget was dragged-- this really only matters
        // if we are placing widgets on a "spring-loaded" screen
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        int[] finalSpan = new int[2];
        boolean foundCellSpan = false;
        if (mPendingAddInfo.cellX >= 0 && mPendingAddInfo.cellY >= 0) {
            cellXY[0] = mPendingAddInfo.cellX;
            cellXY[1] = mPendingAddInfo.cellY;
            spanXY[0] = mPendingAddInfo.spanX;
            spanXY[1] = mPendingAddInfo.spanY;
            foundCellSpan = true;
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(
                    touchXY[0], touchXY[1], minSpanXY[0], minSpanXY[1], spanXY[0],
                    spanXY[1], cellXY, finalSpan);
            spanXY[0] = finalSpan[0];
            spanXY[1] = finalSpan[1];
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, minSpanXY[0], minSpanXY[1]);
        }

        if (!foundCellSpan) {
            if (appWidgetId != -1) {
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new Thread("deleteAppWidgetId") {
                    public void run() {
                        mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                    }
                }.start();
            }
            showOutOfSpaceMessage(isHotseatLayout(layout));
            return;
        }

        // Build Launcher-specific widget info and save to database
        LauncherAppWidgetInfo launcherInfo = new LauncherAppWidgetInfo(appWidgetId,
                appWidgetInfo.provider,
                bSwitchToMoveMode);
        
        launcherInfo.spanX = spanXY[0];
        launcherInfo.spanY = spanXY[1];
        launcherInfo.minSpanX = mPendingAddInfo.minSpanX;
        launcherInfo.minSpanY = mPendingAddInfo.minSpanY;

        LauncherModel.addItemToDatabase(this, launcherInfo,
                container, screen, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            if (hostView == null) {
                // Perform actual inflation because we're live
                launcherInfo.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
                launcherInfo.hostView.setAppWidget(appWidgetId, appWidgetInfo);
            } else {
                // The AppWidgetHostView has already been inflated and instantiated
                launcherInfo.hostView = hostView;
            }

            launcherInfo.hostView.setTag(launcherInfo);
            launcherInfo.hostView.setVisibility(View.VISIBLE);
            launcherInfo.notifyWidgetSizeChanged(this);

            mWorkspace.addInScreen(launcherInfo.hostView, container, screen, cellXY[0], cellXY[1],
                    launcherInfo.spanX, launcherInfo.spanY, isWorkspaceLocked());

            addWidgetToAutoAdvanceIfNeeded(launcherInfo.hostView, appWidgetInfo);
        }
        resetAddInfo();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if(Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenState = true;
                if(mHasPendingPreviewRequest) {
                    Log.d(TAG, "SCREEN_ON mHasPendingPreviewRequest");
                    initSurfaceViewWidget("Pending");
                    mHasPendingPreviewRequest = false;
                }
            }

            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenState = false;
                mUserPresent = false;
                mDragLayer.clearAllResizeFrames();
                updateRunning();

                stopSourceDirectly();

                // Reset AllApps to its initial state only if we are not in the middle of
                // processing a multi-step drop
                if (mAppsCustomizeTabHost != null && mPendingAddInfo.container == ItemInfo.NO_ID) {
                    mAppsCustomizeTabHost.reset();
                    showWorkspace(false);
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mUserPresent = true;
                mScreenState = true;
                updateRunning();
            }
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Listen for broadcasts related to user-presence
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mReceiver, filter);

        mAttached = true;
        mVisible = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;

        if (mAttached) {
            unregisterReceiver(mReceiver);
            mAttached = false;
        }
        updateRunning();
    }

    public void onWindowVisibilityChanged(int visibility) {
        mVisible = visibility == View.VISIBLE;
        updateRunning();
        // The following code used to be in onResume, but it turns out onResume is called when
        // you're in All Apps and click home to go to the workspace. onWindowVisibilityChanged
        // is a more appropriate event to handle
        if (mVisible) {
            mAppsCustomizeTabHost.onWindowVisible();
            if (!mWorkspaceLoading) {
                final ViewTreeObserver observer = mWorkspace.getViewTreeObserver();
                // We want to let Launcher draw itself at least once before we force it to build
                // layers on all the workspace pages, so that transitioning to Launcher from other
                // apps is nice and speedy. Usually the first call to preDraw doesn't correspond to
                // a true draw so we wait until the second preDraw call to be safe
                observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    public boolean onPreDraw() {
                        // We delay the layer building a bit in order to give
                        // other message processing a time to run.  In particular
                        // this avoids a delay in hiding the IME if it was
                        // currently shown, because doing that may involve
                        // some communication back with the app.
                        mWorkspace.postDelayed(mBuildLayersRunnable, 500);

                        observer.removeOnPreDrawListener(this);
                        return true;
                    }
                });
            }
            // When Launcher comes back to foreground, a different Activity might be responsible for
            // the app market intent, so refresh the icon
            updateAppMarketIcon();
            clearTypedText();
        }
    }

    private void sendAdvanceMessage(long delay) {
        mHandler.removeMessages(ADVANCE_MSG);
        Message msg = mHandler.obtainMessage(ADVANCE_MSG);
        mHandler.sendMessageDelayed(msg, delay);
        mAutoAdvanceSentTime = System.currentTimeMillis();
    }

    private void updateRunning() {
        boolean autoAdvanceRunning = mVisible && mUserPresent && !mWidgetsToAdvance.isEmpty();
        if (autoAdvanceRunning != mAutoAdvanceRunning) {
            mAutoAdvanceRunning = autoAdvanceRunning;
            if (autoAdvanceRunning) {
                long delay = mAutoAdvanceTimeLeft == -1 ? mAdvanceInterval : mAutoAdvanceTimeLeft;
                sendAdvanceMessage(delay);
            } else {
                if (!mWidgetsToAdvance.isEmpty()) {
                    mAutoAdvanceTimeLeft = Math.max(0, mAdvanceInterval -
                            (System.currentTimeMillis() - mAutoAdvanceSentTime));
                }
                mHandler.removeMessages(ADVANCE_MSG);
                mHandler.removeMessages(0); // Remove messages sent using postDelayed()
            }
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ADVANCE_MSG) {
                int i = 0;
                for (View key: mWidgetsToAdvance.keySet()) {
                    final View v = key.findViewById(mWidgetsToAdvance.get(key).autoAdvanceViewId);
                    final int delay = mAdvanceStagger * i;
                    if (v instanceof Advanceable) {
                       postDelayed(new Runnable() {
                           public void run() {
                               ((Advanceable) v).advance();
                           }
                       }, delay);
                    }
                    i++;
                }
                sendAdvanceMessage(mAdvanceInterval);
            }else if(msg.what == RTK_ADVANCE_MSG){
                
                String objs = (String)msg.obj;
                
                if(objs.equals(SWITCH_MOVE_MODE_AFTER_ADD_COMMAND)){
                    handleWorkspaceMoveCommand();
                } else if(objs.equals(DRAW_FOCUS_AFTER_ON_RESUME)){
                    if(mState==State.WORKSPACE && mWorkspace!=null){
                        View tmpV=mWorkspace.findFocus();
                        if(tmpV!=null){
                            DebugHelper.dump2("onResume -> workspace focus:"+tmpV);
                            redrawWorkspaceFocusViewFrame(tmpV);
                        }
                    }
                } else if(objs.equals(DRAW_FOCUS_AFTER_FAKE_DRAG_CANCELED)){
                    redrawWorkspaceFocusViewFrame(mWorkspace.findFocus());
                } else if(objs.equals(SEND_FORCE_EXPAND_NOTIFICATION_PANEL)){
                    Intent i = new Intent("com.android.action.FORCE_EXPAND_NOTIFICATION");
                    sendBroadcast(i);
                }
            }
        }
    };

    void addWidgetToAutoAdvanceIfNeeded(View hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo == null || appWidgetInfo.autoAdvanceViewId == -1) return;
        View v = hostView.findViewById(appWidgetInfo.autoAdvanceViewId);
        if (v instanceof Advanceable) {
            mWidgetsToAdvance.put(hostView, appWidgetInfo);
            ((Advanceable) v).fyiWillBeAdvancedByHostKThx();
            updateRunning();
        }
    }

    void removeWidgetToAutoAdvance(View hostView) {
        if (mWidgetsToAdvance.containsKey(hostView)) {
            mWidgetsToAdvance.remove(hostView);
            updateRunning();
        }
    }

    public void removeAppWidget(LauncherAppWidgetInfo launcherInfo) {
        removeWidgetToAutoAdvance(launcherInfo.hostView);
        launcherInfo.hostView = null;
    }

    void showOutOfSpaceMessage(boolean isHotseatLayout) {
        int strId = (isHotseatLayout ? R.string.hotseat_out_of_space : R.string.out_of_space);
        Toast.makeText(this, getString(strId), Toast.LENGTH_SHORT).show();
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    void closeSystemDialogs() {
        getWindow().closeAllPanels();

        // Whatever we were doing is hereby canceled.
        mWaitingForResult = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Close the menu
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            // also will cancel mWaitingForResult.
            closeSystemDialogs();

            final boolean alreadyOnHome =
                    ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                        != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

            Runnable processIntent = new Runnable() {
                public void run() {
                    if (mWorkspace == null) {
                        // Can be cases where mWorkspace is null, this prevents a NPE
                        return;
                    }
                    Folder openFolder = mWorkspace.getOpenFolder();
                    // In all these cases, only animate if we're already on home
                    mWorkspace.exitWidgetResizeMode();
                    if (alreadyOnHome && mState == State.WORKSPACE && !mWorkspace.isTouchActive() &&
                            openFolder == null) {
                        mWorkspace.moveToDefaultScreen(true);
                    }

                    closeFolder();
                    exitSpringLoadedDragMode();

                    // If we are already on home, then just animate back to the workspace,
                    // otherwise, just wait until onResume to set the state back to Workspace
                    if (alreadyOnHome) {
                        showWorkspace(true);
                    } else {
                        mOnResumeState = State.WORKSPACE;
                    }

                    final View v = getWindow().peekDecorView();
                    if (v != null && v.getWindowToken() != null) {
                        InputMethodManager imm = (InputMethodManager)getSystemService(
                                INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }

                    // Reset AllApps to its initial state
                    if (!alreadyOnHome && mAppsCustomizeTabHost != null) {
                        mAppsCustomizeTabHost.reset();
                    }
                }
            };

            if (alreadyOnHome && !mWorkspace.hasWindowFocus()) {
                // Delay processing of the intent to allow the status bar animation to finish
                // first in order to avoid janky animations.
                mWorkspace.postDelayed(processIntent, 350);
            } else {
                // Process the intent immediately.
                processIntent.run();
            }

        }
    }

    @Override
    public void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        for (int page: mSynchronouslyBoundPages) {
            mWorkspace.restoreInstanceStateForChild(page);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getNextPage());
        super.onSaveInstanceState(outState);

        outState.putInt(RUNTIME_STATE, mState.ordinal());
        // We close any open folder since it will not be re-opened, and we need to make sure
        // this state is reflected.
        closeFolder();

        if (mPendingAddInfo.container != ItemInfo.NO_ID && mPendingAddInfo.screen > -1 &&
                mWaitingForResult) {
            outState.putLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, mPendingAddInfo.container);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SCREEN, mPendingAddInfo.screen);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X, mPendingAddInfo.cellX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y, mPendingAddInfo.cellY);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_X, mPendingAddInfo.spanX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SPAN_Y, mPendingAddInfo.spanY);
            outState.putParcelable(RUNTIME_STATE_PENDING_ADD_WIDGET_INFO, mPendingAddWidgetInfo);
        }

        if (mFolderInfo != null && mWaitingForResult) {
            outState.putBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, true);
            outState.putLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID, mFolderInfo.id);
        }

        // Save the current AppsCustomize tab
        if (mAppsCustomizeTabHost != null) {
            String currentTabTag = mAppsCustomizeTabHost.getCurrentTabTag();
            if (currentTabTag != null) {
                outState.putString("apps_customize_currentTab", currentTabTag);
            }
            int currentIndex = mAppsCustomizeContent.getSaveInstanceStateIndex();
            outState.putInt("apps_customize_currentIndex", currentIndex);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remove all pending runnables
        mHandler.removeMessages(ADVANCE_MSG);
        mHandler.removeMessages(0);
        mWorkspace.removeCallbacks(mBuildLayersRunnable);

        // Stop callbacks from LauncherModel
        LauncherApplication app = ((LauncherApplication) getApplication());
        mModel.stopLoader();
        app.setLauncher(null);

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }
        mAppWidgetHost = null;

        mWidgetsToAdvance.clear();

        TextKeyListener.getInstance().release();

        // Disconnect any of the callbacks and drawables associated with ItemInfos on the workspace
        // to prevent leaking Launcher activities on orientation change.
        if (mModel != null) {
            mModel.unbindItemInfosAndClearQueuedBindRunnables();
        }

        getContentResolver().unregisterContentObserver(mWidgetObserver);
        unregisterReceiver(mCloseSystemDialogsReceiver);

        mDragLayer.clearAllResizeFrames();
        ((ViewGroup) mWorkspace.getParent()).removeAllViews();
        mWorkspace.removeAllViews();
        mWorkspace = null;
        mDragController = null;
        // avoid potential memory leak problem
        if(mMonitor!=null) unregisterReceiver(mMonitor);

        LauncherAnimUtils.onDestroyActivity();
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode >= 0) mWaitingForResult = true;
        super.startActivityForResult(intent, requestCode);
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {

        showWorkspace(true);

        if (initialQuery == null) {
            // Use any text typed in the launcher as the initial query
            initialQuery = getTypedText();
        }
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString(Search.SOURCE, "launcher-search");
        }
        Rect sourceBounds = new Rect();
        if (mSearchDropTargetBar != null) {
            sourceBounds = mSearchDropTargetBar.getSearchBarBounds();
        }

        startGlobalSearch(initialQuery, selectInitialQuery,
            appSearchData, sourceBounds);
    }

    /**
     * Starts the global search activity. This code is a copied from SearchManager
     */
    public void startGlobalSearch(String initialQuery,
            boolean selectInitialQuery, Bundle appSearchData, Rect sourceBounds) {
        final SearchManager searchManager =
            (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName globalSearchActivity = CustomizedHelper.getFakeSearchActivity();//searchManager.getGlobalSearchActivity();
        if (globalSearchActivity == null) {
            Log.w(TAG, "No global search activity found.");
            return;
        }
        Intent intent = new Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setComponent(globalSearchActivity);
        // Make sure that we have a Bundle to put source in
        if (appSearchData == null) {
            appSearchData = new Bundle();
        } else {
            appSearchData = new Bundle(appSearchData);
        }
        // Set source to package name of app that starts global search, if not set already.
        if (!appSearchData.containsKey("source")) {
            appSearchData.putString("source", getPackageName());
        }
        intent.putExtra(SearchManager.APP_DATA, appSearchData);
        if (!TextUtils.isEmpty(initialQuery)) {
            intent.putExtra(SearchManager.QUERY, initialQuery);
        }
        if (selectInitialQuery) {
            intent.putExtra(SearchManager.EXTRA_SELECT_QUERY, selectInitialQuery);
        }
        intent.setSourceBounds(sourceBounds);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Log.e(TAG, "Global search activity not found: " + globalSearchActivity);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isWorkspaceLocked()) {
            return false;
        }

        super.onCreateOptionsMenu(menu);

        Intent manageApps = new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS);
        manageApps.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
        settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        String helpUrl = getString(R.string.help_url);
        Intent help = new Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl));
        help.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        menu.add(MENU_GROUP_WALLPAPER, MENU_WALLPAPER_SETTINGS, 0, R.string.menu_wallpaper)
            .setIcon(android.R.drawable.ic_menu_gallery)
            .setAlphabeticShortcut('W');
        menu.add(0, MENU_MANAGE_APPS, 0, R.string.menu_manage_apps)
            .setIcon(android.R.drawable.ic_menu_manage)
            .setIntent(manageApps)
            .setAlphabeticShortcut('M');
        menu.add(0, MENU_SYSTEM_SETTINGS, 0, R.string.menu_settings)
            .setIcon(android.R.drawable.ic_menu_preferences)
            .setIntent(settings)
            .setAlphabeticShortcut('P');
        if (!helpUrl.isEmpty()) {
            menu.add(0, MENU_HELP, 0, R.string.menu_help)
                .setIcon(android.R.drawable.ic_menu_help)
                .setIntent(help)
                .setAlphabeticShortcut('H');
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        if (mAppsCustomizeTabHost.isTransitioning()) {
            return false;
        }
        boolean allAppsVisible = (mAppsCustomizeTabHost.getVisibility() == View.VISIBLE);
        menu.setGroupVisible(MENU_GROUP_WALLPAPER, !allAppsVisible);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_WALLPAPER_SETTINGS:
            startWallpaper();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, true);
        // Use a custom animation for launching search
        return true;
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mWaitingForResult;
    }

    private void resetAddInfo() {
        mPendingAddInfo.container = ItemInfo.NO_ID;
        //mPendingAddInfo.screen = -1;
        mPendingAddInfo.cellX = mPendingAddInfo.cellY = -1;
        mPendingAddInfo.spanX = mPendingAddInfo.spanY = -1;
        mPendingAddInfo.minSpanX = mPendingAddInfo.minSpanY = -1;
        mPendingAddInfo.dropPos = null;
    }

    void addAppWidgetImpl(final int appWidgetId, ItemInfo info, AppWidgetHostView boundWidget,
            AppWidgetProviderInfo appWidgetInfo) {
    	
    	DebugHelper.dump("addAppWidgetImpl.");
    	
    	
        if (appWidgetInfo.configure != null) {
            mPendingAddWidgetInfo = appWidgetInfo;

            // Launch over to configure widget, if needed
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidgetInfo.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            startActivityForResultSafely(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            // Otherwise just add it
            completeAddAppWidget(appWidgetId, info.container, info.screen, boundWidget,
                    appWidgetInfo,info.bSwitchToMoveModeAfterAdd);
            // Exit spring loaded mode if necessary after adding the widget
            exitSpringLoadedDragModeDelayed(true, false, null);
        }
    }
    
    /**
     * API added for Realtek Launcher.<br>
     * A wrapper function between option menu and drop callback of adding shortcut.<br>
     * this API would prepare position information for existing drop callback function of adding shortcut. 
     * 
     * @param componentName component that the shortcut would lunch.
     * @param span span of the shortcut
     * @return success or not
     */
    public boolean processShortcutFromOptionMenu(ComponentName componentName, int[] span) {
    	
    	DebugHelper.dump("Launcher, process add shortcut from option menu");
    	
    	int[] targetCell=new int[2];
    	int screen=0;
    	long container=Favorites.CONTAINER_DESKTOP;
    	//int spanX=1;
    	//int spanY=1;
    	boolean canAdd = findAvailableCellInWorkspaceForAddedItem(targetCell, span[0], span[1]);
    	
    	if(canAdd){
    		processShortcutFromDrop(componentName,container,screen,targetCell,null,true);
    		//showWorkspace(true); // <-- do not switch to launcher here
    		return true;
    	}else{
    		showToastMessage(getResources().getString(R.string.toast_workspace_no_space));
    		return false;
    	}
    }
    
    /**
     * Process a shortcut drop.
     *
     * @param componentName The name of the component
     * @param screen The screen where it should be added
     * @param cell The cell it should be added to, optional
     * @param position The location on the screen where it was dropped, optional
     */
    void processShortcutFromDrop(ComponentName componentName, long container, int screen,
            int[] cell, int[] loc, boolean bSwitchToMoveMode) {
    	
    	DebugHelper.dump("[Launcher] processShortcutFromDrop");
    	
        resetAddInfo();
        mPendingAddInfo.container = container;
        //mPendingAddInfo.screen = screen;
        mPendingAddInfo.dropPos = loc;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }
        
        mPendingAddInfo.bSwitchToMoveModeAfterAdd=bSwitchToMoveMode;
        
        Intent createShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        createShortcutIntent.setComponent(componentName);
        processShortcut(createShortcutIntent);
    }

    /**
     * API added for Realtek Launcher.<br>
     * service function to let user add a widget into workspace via option menu.
     * 
     * @param info info about AppWidget that is going to be added
     * @param span span information of the widget
     * @return success or failed.
     */
    public boolean addAppWidgetFromOptionMenu(PendingAddWidgetInfo info, int[] span) {
    	long container = Favorites.CONTAINER_DESKTOP;
    	int screen = 0;
    	int[] targetCell=new int[2];
    	boolean canAdd=findAvailableCellInWorkspaceForAddedItem(targetCell, span[0], span[1]);
    	if(canAdd){
    		addAppWidgetFromDrop(info, container, screen, targetCell, span, null);
    		//showWorkspace(true); // <-- do not switch to workspace here
    		return true;
    	}else{
    		showToastMessage(getResources().getString(R.string.toast_workspace_no_space));
    		return false;
    	}
    }
    
    /**
     * 
     * Process a widget drop.
     *
     * @param info The PendingAppWidgetInfo of the widget being added.
     * @param screen The screen where it should be added
     * @param cell The cell it should be added to, optional
     * @param position The location on the screen where it was dropped, optional
     */
    void addAppWidgetFromDrop(PendingAddWidgetInfo info, long container, int screen,
            int[] cell, int[] span, int[] loc) {
    	
    	DebugHelper.dump("break.");
    	
        resetAddInfo();
        mPendingAddInfo.container = info.container = container;
        //mPendingAddInfo.screen = info.screen = screen;
        mPendingAddInfo.dropPos = loc;
        mPendingAddInfo.minSpanX = info.minSpanX;
        mPendingAddInfo.minSpanY = info.minSpanY;
        // add for realtek launcher
        mPendingAddInfo.bSwitchToMoveModeAfterAdd=info.bSwitchToMoveModeAfterAdd;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }
        if (span != null) {
            mPendingAddInfo.spanX = span[0];
            mPendingAddInfo.spanY = span[1];
        }

        AppWidgetHostView hostView = info.boundWidget;
        int appWidgetId;
        if (hostView != null) {
            appWidgetId = hostView.getAppWidgetId();
            addAppWidgetImpl(appWidgetId, info, hostView, info.info);
        } else {
            // In this case, we either need to start an activity to get permission to bind
            // the widget, or we need to start an activity to configure the widget, or both.
            appWidgetId = getAppWidgetHost().allocateAppWidgetId();
            Bundle options = info.bindOptions;

            boolean success = false;
            if (options != null) {
                success = mAppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,
                        info.componentName, options);
            } else {
                success = mAppWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId,
                        info.componentName);
            }
            if (success) {
                addAppWidgetImpl(appWidgetId, info, null, info.info);
            } else {
                mPendingAddWidgetInfo = info.info;
                Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.componentName);
                // TODO: we need to make sure that this accounts for the options bundle.
                // intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options);
                startActivityForResult(intent, REQUEST_BIND_APPWIDGET);
            }
        }
    }
    
    /**
     * callback to process adding a shortcut or application
     * @param intent
     */
    void processShortcut(Intent intent) {
    	DebugHelper.dump("Launcher processShortcut");
        // Handle case where user selected "Applications"
        String applicationName = getResources().getString(R.string.group_applications);
        String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (applicationName != null && applicationName.equals(shortcutName)) {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
            pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
            pickIntent.putExtra(Intent.EXTRA_TITLE, getText(R.string.title_select_application));
            startActivityForResultSafely(pickIntent, REQUEST_PICK_APPLICATION);
        } else {
            startActivityForResultSafely(intent, REQUEST_CREATE_SHORTCUT);
        }
    }

    void processWallpaper(Intent intent) {
        startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    FolderIcon addFolder(CellLayout layout, long container, final int screen, int cellX,
            int cellY) {
        final FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = getText(R.string.folder_name);

        // Update the model
        LauncherModel.addItemToDatabase(Launcher.this, folderInfo, container, screen, cellX, cellY,
                false);
        sFolders.put(folderInfo.id, folderInfo);

        // Create the view
        FolderIcon newFolder =
            FolderIcon.fromXml(R.layout.folder_icon, this, layout, folderInfo, mIconCache);
        mWorkspace.addInScreen(newFolder, container, screen, cellX, cellY, 1, 1,
                isWorkspaceLocked());
        return newFolder;
    }

    void removeFolder(FolderInfo folder) {
        sFolders.remove(folder.id);
    }

    private void startWallpaper() {
        showWorkspace(true);
        final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
        Intent chooser = Intent.createChooser(pickWallpaper,
                getText(R.string.chooser_wallpaper));
        // NOTE: Adds a configure option to the chooser if the wallpaper supports it
        //       Removed in Eclair MR1
//        WallpaperManager wm = (WallpaperManager)
//                getSystemService(Context.WALLPAPER_SERVICE);
//        WallpaperInfo wi = wm.getWallpaperInfo();
//        if (wi != null && wi.getSettingsActivity() != null) {
//            LabeledIntent li = new LabeledIntent(getPackageName(),
//                    R.string.configure_wallpaper, 0);
//            li.setClassName(wi.getPackageName(), wi.getSettingsActivity());
//            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { li });
//        }
        startActivityForResult(chooser, REQUEST_PICK_WALLPAPER);
    }

    /**
     * Registers various content observers. The current implementation registers
     * only a favorites observer to keep track of the favorites applications.
     */
    private void registerContentObservers() {
        ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(LauncherProvider.CONTENT_APPWIDGET_RESET_URI,
                true, mWidgetObserver);
    }
    
    /**
     * API added for Realtek Launcher.<br>
     * Called during fake drag & drop mode, prevent coordinate over bounded.<br>
     * the coordinate is absolute value.
     * @param min
     * @param max
     * @param nextCoordinate
     * @return
     */
    private boolean checkNextCoordinateValidate(int min, int max, int nextCoordinate) {
    	if(nextCoordinate>min && nextCoordinate<max)
    		return true;
    	else
    		return false;
    }
    
    public void handleKeyEventDuringFakeDragState(KeyEvent event){
    	
    	// lazy loading mechanism
    	if(workspaceBound==null){
    		workspaceBound=mWorkspace.getCurrentShortcutAndWidgetContainer().getRectOnScreen();
    	}
    	
    	if (event.getAction() == KeyEvent.ACTION_DOWN) {
    		
    		/*
    		int[] location=new int[2];
    		mWorkspace.getCurrentShortcutAndWidgetContainer().getLocationOnScreen(location);
    		int w=mWorkspace.getCurrentShortcutAndWidgetContainer().getWidth();
    		int h=mWorkspace.getCurrentShortcutAndWidgetContainer().getHeight();
    		DebugHelper.dump("CurrentShortcutAndWidgetContainer x="+location[0]+" y="+location[1]+" w="+w+" h="+h);
    		*/
    		
    		int nextCoordinate=-1;
    		
    		int keyCode = event.getKeyCode();
    		switch(keyCode){
    		case KeyEvent.KEYCODE_DPAD_UP:
    			nextCoordinate = mDragController.mMotionDownY-fakeDragStepHeight;
    			if(checkNextCoordinateValidate(workspaceBound.top,workspaceBound.bottom,nextCoordinate)){
    				mDragController.mMotionDownY=nextCoordinate;
        			mDragController.handleMoveEvent(mDragController.mMotionDownX, mDragController.mMotionDownY);
    			}else{
    				DebugHelper.dump("handleKeyEventDuringFakeDragState Y underflow.. break");
    			}
    			break;
    		case KeyEvent.KEYCODE_DPAD_DOWN:
    			nextCoordinate = mDragController.mMotionDownY+fakeDragStepHeight;
    			if(checkNextCoordinateValidate(workspaceBound.top, workspaceBound.bottom, nextCoordinate)){
    				mDragController.mMotionDownY=nextCoordinate;
        			mDragController.handleMoveEvent(mDragController.mMotionDownX, mDragController.mMotionDownY);
    			}else{
    				DebugHelper.dump("handleKeyEventDuringFakeDragState Y overflow.. break");
    			}
    			break;
    		case KeyEvent.KEYCODE_DPAD_RIGHT:
    			nextCoordinate=mDragController.mMotionDownX+fakeDragStepWidth;
    			if(checkNextCoordinateValidate(workspaceBound.left, workspaceBound.right, nextCoordinate)){
    				mDragController.mMotionDownX=nextCoordinate;
    				mDragController.handleMoveEvent(mDragController.mMotionDownX, mDragController.mMotionDownY);
    			}else{
    				DebugHelper.dump("handleKeyEventDuringFakeDragState X overflow.. break");
    			}
    			break;
    		case KeyEvent.KEYCODE_DPAD_LEFT:
    			nextCoordinate=mDragController.mMotionDownX-fakeDragStepWidth;
    			if(checkNextCoordinateValidate(workspaceBound.left, workspaceBound.right, nextCoordinate)){
    				mDragController.mMotionDownX=nextCoordinate;
    				mDragController.handleMoveEvent(mDragController.mMotionDownX, mDragController.mMotionDownY);
    			}else{
    				DebugHelper.dump("handleKeyEventDuringFakeDragState X underflow.. break");
    			}
    			break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
    		case KeyEvent.KEYCODE_ENTER:
    			mDragController.confirmFakeDrag(mDragController.mMotionDownX, mDragController.mMotionDownY);
    			bIsInFakeDragState=false;
    			break;
    		// BUG_FIX: 42047
    		case KeyEvent.KEYCODE_BACK:
    		    bIsInFakeDragState=false;
                mDragController.cancelFakeDrag();
                break;
    		// BUG_FIX: 42663 (let info key cancel fake drag mode, and send broadcast event)
    		case KeyEvent.KEYCODE_INFO:
    			bIsInFakeDragState=false;
    			mDragController.cancelFakeDrag();
    			// send intent to expand notification panel
    			sendRtkAdvMessageToHandler(SEND_FORCE_EXPAND_NOTIFICATION_PANEL, 500);
    			break;
    		}
    	}
    }
    
    /**
     * API added for Realtek Launcher.<br>
     * 
     * @return
     */
    private CellLayout getCellLayout(){
    	return getCellLayout(Favorites.CONTAINER_DESKTOP, 0);
    }
    
    /**
     * API added for Realtek Launcher.<br>
     * Display status of workspace cell occupation status. would be removed later
     */
    private void dumpWorkspaceCellOccupationStatus(){
    	CellLayout cl = getCellLayout();
    	DebugHelper.dump("break");
    	findAvailableCellInWorkspaceForAddedItem(null,1,1);
    }
    
    /**
     * API added for Realtek Launcher.<br>
     * it used {@link CellLayout#findCellForSpan(int[], int, int)} to find available cell for new added item.<br>
     * this wrapper API lets caller easier to use findCellSpan function.
     * 
     * @param resultCellXY :out, the result of available cell position found by CellLayout
     * @param spanX :how many cell span in X coordinate
     * @param spanY :how many cell span in Y coordinate
     * 
     * @return find result
     * 
     */
    private boolean findAvailableCellInWorkspaceForAddedItem(int[] resultCellXY, int spanX, int spanY){
    	if(resultCellXY==null || spanX<0 || spanY<0)
    		return false;
    	// fix mOccupied array before finding cell.
    	getCellLayout().fixOccupiedArray();
    	
    	boolean rst = getCellLayout().findCellForSpan(resultCellXY, spanX, spanY);
    	
    	// Bruce do final check again
    	// pass resultCellXY to CellLayout again to double check if target cell is empty
    	if(rst){
    	    View view=getCellLayout().isOccupiedAdv(resultCellXY[0], resultCellXY[1], spanX, spanY);
    	    if(view !=null){
    	        Log.e(TAG,"findAvailableCellInWorkspaceForAddedItem target cell is not empty:"+view);
    	        Log.e(TAG,"X:"+resultCellXY[0]+" Y:"+resultCellXY[1]);
    	    }
    	}
    	
    	return rst;
    }
    
    
    
    private boolean preHandleDebugKeyEvent(KeyEvent event){
    	
    	boolean needHandle = false;
    	boolean wasHandled = false;
    	int keyCode = event.getKeyCode();
    	
    	if(event.getAction()==KeyEvent.ACTION_DOWN)
    		needHandle=true;
    	
    	switch(keyCode){
    	case KeyEvent.KEYCODE_1:
    		wasHandled=true;
    		if(needHandle) {
    			DebugHelper.ENABLE_DEBUG_TRACE=!DebugHelper.ENABLE_DEBUG_TRACE;
    			showToastMessage("Launcher Debug Trace: "+(DebugHelper.ENABLE_DEBUG_TRACE?"enabled":"disabled"));
    		}
    		break;
    		
    	case KeyEvent.KEYCODE_2:
    		wasHandled=true;
    		if(needHandle) {
    			launcherDumpSys();
    		}
    		break;
    	case KeyEvent.KEYCODE_3:
    		wasHandled=true;
    		if(needHandle){
    	        dumpSystemPackagesName();
    		}
    		break;
    	}
    	return wasHandled;
    }
    
    // Debug function added for chihchiang
    private void dumpSystemPackagesName(){
        Log.i("LauncherDump","================= System Package =================");
        mAppsCustomizeContent.dumpMAppsPackageInfo();
        Log.i("LauncherDump","================= System Package =================");
    }

    private boolean switchSource() {
        Log.d(TAG, "switchSource");
        Intent i = new Intent(ConstantsDef.SWITCH_SURFACEVIEW_WIDGET_SOURCE_INTENT);
        i.putExtra(ConstantsDef.INIT_EXTRA_NAME, "switchSource");
        sendBroadcast(i);
        return true;
    }
    
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
	Log.e("Launcher", "get dispatchKeyEvent");
        //for Diagnostic tool
        long eventTime = event.getEventTime()-event.getDownTime();
        boolean  isLongPress = (eventTime> 5000 );
       
    	DebugHelper.dump("dispatch key event at launcher -> event: "+event);
    	// pre-handle debug key
    	if(preHandleDebugKeyEvent(event))
    		return true;
    	
    	if(bIsInFakeDragState){
    		DebugHelper.dump("Launcher dispatch key event state is under FakeDragState");
    		handleKeyEventDuringFakeDragState(event);
    		return true;
    	}
    	
    	if(getDragLayer().hasResizeFrames()){
    	    DebugHelper.dumpSQA("bypass keyEvent if resize frame exists");
    	    return true;
    	}
    	
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                // BUG_FIX: 42675
                // handle all menu key here.
                case ConstantsDef.LAUNCHER_KEY_OPTION_MENU:
                    if(mState==State.WORKSPACE){
                        DebugHelper.dumpSQA("handle menu key under workspace");
                        
                        View v = mWorkspace.findFocus();
                        if(v==null){
                            DebugHelper.dumpSQA("set workspace focus");
                            v=mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(0);
                            v.requestFocus();
                            redrawWorkspaceFocusViewFrame(v);
                        }
                        
                        onShowWorkspaceOptionMenu();
                        
                    }else{
                        // BUG_FIX: 42675
                        View v = mAppsCustomizeContent.getFocusedChild();
                        View focusedView = null;
                        
                        //DebugHelper.dumpSQA("view1:"+v);
                        
                        if(v!=null){
                            if(v instanceof PagedViewCellLayout){
                                View v2 = ((PagedViewCellLayout) v).getFocusedChild();
                                View v3 = ((PagedViewCellLayoutChildren)v2).getFocusedChild();
                                //DebugHelper.dumpSQA("view:"+v3);
                                if(v3 instanceof PagedViewIcon){
                                    focusedView=v3;
                                }
                            }else if(v instanceof PagedViewGridLayout){
                                View v2 = ((PagedViewGridLayout) v).getFocusedChild();
                                //DebugHelper.dumpSQA("view:"+v2);
                                if(v2 instanceof PagedViewWidget){
                                    focusedView=v2;
                                }
                            }
                        }
                        
                        if(focusedView!=null){
                            onShowAppCustomPageOptionMenu(focusedView);
                        }else{
                            showToastMessage("Option menu is not available");
                        }
                    }
                    return true;
                case KeyEvent.KEYCODE_HOME:
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (isPropertyEnabled(DUMP_STATE_PROPERTY)) {
                        dumpState();
                        return true;
                    }
                    break;
                case KeyEvent.KEYCODE_TV_INPUT:
                    if (mState==State.WORKSPACE) {
                        if (switchSource()) {
                            return true;
                        }
                    }
                    break;
                // BUG_FIX: 43612 check workspace focus child status
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if(mState==State.WORKSPACE){
                        View focusChild = mWorkspace.getCurrentShortcutAndWidgetContainer().getFocusedChild();
                        //Log.i("Launcher","FocusChild:"+focusChild);
                        if(focusChild==null){
                            if(mWorkspace.getCurrentShortcutAndWidgetContainer() != null && mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(0) != null){
                                mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(0).requestFocus();
                            }
                            // shows focus frame here.
                            if(mFocusFrameView==null){
                                View newFocusChild = mWorkspace.getCurrentShortcutAndWidgetContainer().getFocusedChild();
                                //Log.i("Launcher","DrawFocusFrame to new requested focus view:"+newFocusChild);
                                redrawWorkspaceFocusViewFrame(newFocusChild);
                            }
                            // consume this key event.
                            return true;
                        }else{
                            if(mFocusFrameView==null){
                                //Log.i("Launcher","DrawFocusFrame to existing focus child:"+focusChild);
                                redrawWorkspaceFocusViewFrame(focusChild);
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (isAllAppsVisible()) {
            showWorkspace(true);
        } else if (mWorkspace.getOpenFolder() != null) {
            Folder openFolder = mWorkspace.getOpenFolder();
            if (openFolder.isEditingName()) {
                openFolder.dismissEditingName();
            } else {
                closeFolder();
            }
        } else {
            mWorkspace.exitWidgetResizeMode();

            // Back button is a no-op here, but give at least some feedback for the button press
            mWorkspace.showOutlinesTemporarily();
        }
    }

    /**
     * Re-listen when widgets are reset.
     */
    private void onAppWidgetReset() {
        if (mAppWidgetHost != null) {
            mAppWidgetHost.startListening();
        }
    }

    /**
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    public void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
    	
    	DebugHelper.dump("Launcher onClick view:"+v);
    	//clearWorkspaceFocusViewFrame();
    	
    	// user click on empty part of the screen, so clear the focus widget if any exists.
    	if(v.getTag() instanceof CellInfo){
    		DebugHelper.dump("Launcher onClick on an empty part of screen");
    		((CellLayout)v).getShortcutsAndWidgets().clearChildWidgetFocus();
    		return;
    	}
    	
        if (v.getWindowToken() == null) {
            return;
        }

        if (!mWorkspace.isFinishedSwitchingState()) {
            return;
        }

        Object tag = v.getTag();
        
        redrawWorkspaceFocusViewFrame(v);
        
        if (tag instanceof ShortcutInfo) {
        	
        	DebugHelper.dump("OnClick on ShortcutInfo.....");
        	
            // Open shortcut
            final Intent intent = ((ShortcutInfo) tag).intent;
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));

            boolean success = startActivitySafely(v, intent, tag);

            if (success && v instanceof BubbleTextView) {
                mWaitingForResume = (BubbleTextView) v;
                mWaitingForResume.setStayPressed(true);
            }
        } else if (tag instanceof FolderInfo) {
            if (v instanceof FolderIcon) {
                FolderIcon fi = (FolderIcon) v;
                handleFolderClick(fi);
            }
        } else if (v == mAllAppsButton) {
            if (isAllAppsVisible()) {
                showWorkspace(true);
            } else {
                onClickAllAppsButton(v);
            }
        } else if (v instanceof LauncherAppWidgetHostView){
        	// handle onClick on LauncherAppWidgetHostView
        	DebugHelper.dump("OnClick on LauncherAppWidgetHostView");
        	LauncherAppWidgetHostView lav=(LauncherAppWidgetHostView)v;
        	lav.dumpBelongApkInfo();
        	launchWidgetBelongPackageManiActivity(lav.getAppWidgetInfo());
        }
    }

    public boolean onTouch(View v, MotionEvent event) {
        // this is an intercepted event being forwarded from mWorkspace;
        // clicking anywhere on the workspace causes the customization drawer to slide down
        showWorkspace(true);
        return false;
    }

    /**
     * Event handler for the search button
     *
     * @param v The view that was clicked.
     */
    public void onClickSearchButton(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        onSearchRequested();
    }

    /**
     * Event handler for the voice button
     *
     * @param v The view that was clicked.
     */
    public void onClickVoiceButton(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        try {
            final SearchManager searchManager =
                    (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            ComponentName activityName = CustomizedHelper.getFakeSearchActivity();//searchManager.getGlobalSearchActivity();
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (activityName != null) {
                intent.setPackage(activityName.getPackageName());
            }
            startActivity(null, intent, "onClickVoiceButton");
        } catch (ActivityNotFoundException e) {
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivitySafely(null, intent, "onClickVoiceButton");
        }
    }

    /**
     * Event handler for the "grid" button that appears on the home screen, which
     * enters all apps mode.
     *
     * @param v The view that was clicked.
     */
    public void onClickAllAppsButton(View v) {
        showAllApps(true,ContentType.Applications);
    }

    public void onTouchDownAllAppsButton(View v) {
        // Provide the same haptic feedback that the system offers for virtual keys.
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    public void onClickAppMarketButton(View v) {
        if (mAppMarketIntent != null) {
            startActivitySafely(v, mAppMarketIntent, "app market");
        } else {
            Log.e(TAG, "Invalid app market intent.");
        }
    }

    void startApplicationDetailsActivity(ComponentName componentName) {
        String packageName = componentName.getPackageName();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivitySafely(null, intent, "startApplicationDetailsActivity");
    }
    
    /**
     * RTKCOMMENT: original API to uninstall selected APK
     * @param appInfo
     */
    void startApplicationUninstallActivity(ApplicationInfo appInfo) {
        if ((appInfo.flags & ApplicationInfo.DOWNLOADED_FLAG) == 0) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            int messageId = R.string.uninstall_system_app_text;
            Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
        } else {
            String packageName = appInfo.componentName.getPackageName();
            String className = appInfo.componentName.getClassName();
            Intent intent = new Intent(
                    Intent.ACTION_DELETE, Uri.fromParts("package", packageName, className));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    void stopSourceDirectly() {
        if(mState==State.WORKSPACE){
            int childViewCount = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildCount();
            for(int i=0;i<childViewCount;i++){
                View childView = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(i);
                if(childView instanceof LauncherDtvWidgetHostView){
                    DebugHelper.dumpSQA("Stop DTV widget source");
                    LauncherDtvWidgetHostView dtvView = (LauncherDtvWidgetHostView)childView;
                    dtvView.stopSource();
                }
            }
        }
    }

    boolean startActivity(View v, Intent intent, Object tag) {
        
        //DebugHelper.dumpSQA("startActivity + tag:"+tag);

        //DebugHelper.backtrace();
        
        // BUG_FIX: 42808
        // always un-init camera when starting a activity (only when in workspace state)
        if(mState==State.WORKSPACE){
            int childViewCount = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildCount();
            for(int i=0;i<childViewCount;i++){
                View childView = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(i);
                if(childView instanceof LauncherDtvWidgetHostView){
                    DebugHelper.dumpSQA("Stop DTV widget source");
                    LauncherDtvWidgetHostView dtvView = (LauncherDtvWidgetHostView)childView;
                    dtvView.stopSource();
                }
            }
            DebugHelper.dumpSQA("Camera status:"+LauncherDtvWidgetHostView.isCameraInitialized);
        }
        
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            // Only launch using the new animation if the shortcut has not opted out (this is a
            // private contract between launcher and may be ignored in the future).
            boolean useLaunchAnimation = (v != null) &&
                    !intent.hasExtra(INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION);
            
            // RTKCOMMENT: remove animation
            //useLaunchAnimation=false;
            
            if (useLaunchAnimation) {
                ActivityOptions opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0,
                        v.getMeasuredWidth(), v.getMeasuredHeight());

                startActivity(intent, opts.toBundle());
            } else {
                startActivity(intent);
            }
            return true;
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity. "
                    + "tag="+ tag + " intent=" + intent, e);
        }
        return false;
    }

    boolean startActivitySafely(View v, Intent intent, Object tag) {
        boolean success = false;
        try {
            success = startActivity(v, intent, tag);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + tag + " intent=" + intent, e);
        }
        return success;
    }

    void startActivityForResultSafely(Intent intent, int requestCode) {
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
    }

    private void handleFolderClick(FolderIcon folderIcon) {
        final FolderInfo info = folderIcon.getFolderInfo();
        Folder openFolder = mWorkspace.getFolderForTag(info);

        // If the folder info reports that the associated folder is open, then verify that
        // it is actually opened. There have been a few instances where this gets out of sync.
        if (info.opened && openFolder == null) {
            Log.d(TAG, "Folder info marked as open, but associated folder is not open. Screen: "
                    + info.screen + " (" + info.cellX + ", " + info.cellY + ")");
            info.opened = false;
        }

        if (!info.opened && !folderIcon.getFolder().isDestroyed()) {
            // Close any open folder
            closeFolder();
            // Open the requested folder
            openFolder(folderIcon);
        } else {
            // Find the open folder...
            int folderScreen;
            if (openFolder != null) {
                folderScreen = mWorkspace.getPageForView(openFolder);
                // .. and close it
                closeFolder(openFolder);
                if (folderScreen != mWorkspace.getCurrentPage()) {
                    // Close any folder open on the current screen
                    closeFolder();
                    // Pull the folder onto this screen
                    openFolder(folderIcon);
                }
            }
        }
    }

    /**
     * This method draws the FolderIcon to an ImageView and then adds and positions that ImageView
     * in the DragLayer in the exact absolute location of the original FolderIcon.
     */
    private void copyFolderIconToImage(FolderIcon fi) {
        final int width = fi.getMeasuredWidth();
        final int height = fi.getMeasuredHeight();

        // Lazy load ImageView, Bitmap and Canvas
        if (mFolderIconImageView == null) {
            mFolderIconImageView = new ImageView(this);
        }
        if (mFolderIconBitmap == null || mFolderIconBitmap.getWidth() != width ||
                mFolderIconBitmap.getHeight() != height) {
            mFolderIconBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mFolderIconCanvas = new Canvas(mFolderIconBitmap);
        }

        DragLayer.LayoutParams lp;
        if (mFolderIconImageView.getLayoutParams() instanceof DragLayer.LayoutParams) {
            lp = (DragLayer.LayoutParams) mFolderIconImageView.getLayoutParams();
        } else {
            lp = new DragLayer.LayoutParams(width, height);
        }

        // The layout from which the folder is being opened may be scaled, adjust the starting
        // view size by this scale factor.
        float scale = mDragLayer.getDescendantRectRelativeToSelf(fi, mRectForFolderAnimation);
        lp.customPosition = true;
        lp.x = mRectForFolderAnimation.left;
        lp.y = mRectForFolderAnimation.top;
        lp.width = (int) (scale * width);
        lp.height = (int) (scale * height);

        mFolderIconCanvas.drawColor(0, PorterDuff.Mode.CLEAR);
        fi.draw(mFolderIconCanvas);
        mFolderIconImageView.setImageBitmap(mFolderIconBitmap);
        if (fi.getFolder() != null) {
            mFolderIconImageView.setPivotX(fi.getFolder().getPivotXForIconAnimation());
            mFolderIconImageView.setPivotY(fi.getFolder().getPivotYForIconAnimation());
        }
        // Just in case this image view is still in the drag layer from a previous animation,
        // we remove it and re-add it.
        if (mDragLayer.indexOfChild(mFolderIconImageView) != -1) {
            mDragLayer.removeView(mFolderIconImageView);
        }
        mDragLayer.addView(mFolderIconImageView, lp);
        if (fi.getFolder() != null) {
            fi.getFolder().bringToFront();
        }
    }

    private void growAndFadeOutFolderIcon(FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.5f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.5f);

        FolderInfo info = (FolderInfo) fi.getTag();
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            CellLayout cl = (CellLayout) fi.getParent().getParent();
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) fi.getLayoutParams();
            cl.setFolderLeaveBehindCell(lp.cellX, lp.cellY);
        }

        // Push an ImageView copy of the FolderIcon into the DragLayer and hide the original
        copyFolderIconToImage(fi);
        fi.setVisibility(View.INVISIBLE);

        ObjectAnimator oa = LauncherAnimUtils.ofPropertyValuesHolder(mFolderIconImageView, alpha,
                scaleX, scaleY);
        oa.setDuration(getResources().getInteger(R.integer.config_folderAnimDuration));
        oa.start();
    }

    private void shrinkAndFadeInFolderIcon(final FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 1.0f);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f);

        final CellLayout cl = (CellLayout) fi.getParent().getParent();

        // We remove and re-draw the FolderIcon in-case it has changed
        mDragLayer.removeView(mFolderIconImageView);
        copyFolderIconToImage(fi);
        ObjectAnimator oa = LauncherAnimUtils.ofPropertyValuesHolder(mFolderIconImageView, alpha,
                scaleX, scaleY);
        oa.setDuration(getResources().getInteger(R.integer.config_folderAnimDuration));
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cl != null) {
                    cl.clearFolderLeaveBehind();
                    // Remove the ImageView copy of the FolderIcon and make the original visible.
                    mDragLayer.removeView(mFolderIconImageView);
                    fi.setVisibility(View.VISIBLE);
                }
            }
        });
        oa.start();
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     *
     * @param folderInfo The FolderInfo describing the folder to open.
     */
    public void openFolder(FolderIcon folderIcon) {
        Folder folder = folderIcon.getFolder();
        FolderInfo info = folder.mInfo;

        info.opened = true;

        // Just verify that the folder hasn't already been added to the DragLayer.
        // There was a one-off crash where the folder had a parent already.
        if (folder.getParent() == null) {
            mDragLayer.addView(folder);
            mDragController.addDropTarget((DropTarget) folder);
        } else {
            Log.w(TAG, "Opening folder (" + folder + ") which already has a parent (" +
                    folder.getParent() + ").");
        }
        folder.animateOpen();
        growAndFadeOutFolderIcon(folderIcon);
    }

    public void closeFolder() {
        Folder folder = mWorkspace.getOpenFolder();
        if (folder != null) {
            if (folder.isEditingName()) {
                folder.dismissEditingName();
            }
            closeFolder(folder);

            // Dismiss the folder cling
            dismissFolderCling(null);
        }
    }

    void closeFolder(Folder folder) {
        folder.getInfo().opened = false;

        ViewGroup parent = (ViewGroup) folder.getParent().getParent();
        if (parent != null) {
            FolderIcon fi = (FolderIcon) mWorkspace.getViewForTag(folder.mInfo);
            shrinkAndFadeInFolderIcon(fi);
        }
        folder.animateClosed();
    }

    public boolean onLongClick(View v) {
    	// RTKCOMMENT: v is LauncherAppWidgetHostView or BubbleTextView.
    	DebugHelper.dump("Launcher onLongClick, v:"+v);
    	
    	onLongClickSelectedView=v;
    	
        if (!isDraggingEnabled()) return false;
        if (isWorkspaceLocked()) return false;
        if (mState != State.WORKSPACE) return false;
        if (!(v instanceof CellLayout)) {
        	DebugHelper.dump("Launcher : v is not CellLayout");
            v = (View) v.getParent().getParent();
        }
        
        resetAddInfo();
        CellLayout.CellInfo longClickCellInfo = (CellLayout.CellInfo) v.getTag();
        
        if(v.getTag() instanceof CellInfo){
    		DebugHelper.dump("Launcher onLongClick,always clear focus");
    		((CellLayout)v).getShortcutsAndWidgets().clearChildWidgetFocus();
    	}
        
        // This happens when long clicking an item with the dpad/trackball
        if (longClickCellInfo == null) {
            return true;
        }
        
        // The hotseat touch handling does not go through Workspace, and we always allow long press
        // on hotseat items.
        final View itemUnderLongClick = longClickCellInfo.cell;
        boolean allowLongPress = isHotseatLayout(v) || mWorkspace.allowLongPress();
        if (allowLongPress && !mDragController.isDragging()) {
            if (itemUnderLongClick == null) {
                // User long pressed on empty space
                mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                startWallpaper();
            } else {
                if (!(itemUnderLongClick instanceof Folder)) {
                    // User long pressed on an item
                    mWorkspace.startDrag(longClickCellInfo);
                }
            }
        }
        return true;
    }

    boolean isHotseatLayout(View layout) {
        return mHotseat != null && layout != null &&
                (layout instanceof CellLayout) && (layout == mHotseat.getLayout());
    }
    Hotseat getHotseat() {
        return mHotseat;
    }
    SearchDropTargetBar getSearchBar() {
        return mSearchDropTargetBar;
    }

    /**
     * Returns the CellLayout of the specified container at the specified screen.
     */
    CellLayout getCellLayout(long container, int screen) {
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            if (mHotseat != null) {
                return mHotseat.getLayout();
            } else {
                return null;
            }
        } else {
            return (CellLayout) mWorkspace.getChildAt(screen);
        }
    }

    Workspace getWorkspace() {
        return mWorkspace;
    }

    // Now a part of LauncherModel.Callbacks. Used to reorder loading steps.
    @Override
    public boolean isAllAppsVisible() {
        return (mState == State.APPS_CUSTOMIZE) || (mOnResumeState == State.APPS_CUSTOMIZE);
    }

    @Override
    public boolean isAllAppsButtonRank(int rank) {
        return mHotseat.isAllAppsButtonRank(rank);
    }

    /**
     * Helper method for the cameraZoomIn/cameraZoomOut animations
     * @param view The view being animated
     * @param scaleFactor The scale factor used for the zoom
     */
    private void setPivotsForZoom(View view, float scaleFactor) {
        view.setPivotX(view.getWidth() / 2.0f);
        view.setPivotY(view.getHeight() / 2.0f);
    }

    void disableWallpaperIfInAllApps() {
        // Only disable it if we are in all apps
        if (isAllAppsVisible()) {
            if (mAppsCustomizeTabHost != null &&
                    !mAppsCustomizeTabHost.isTransitioning()) {
                updateWallpaperVisibility(false);
            }
        }
    }

    private void setWorkspaceBackground(boolean workspace) {
        mLauncherView.setBackground(workspace ?
                mWorkspaceBackgroundDrawable : mBlackBackgroundDrawable);
    }

    void updateWallpaperVisibility(boolean visible) {
        int wpflags = visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0;
        int curflags = getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (wpflags != curflags) {
            getWindow().setFlags(wpflags, WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
        setWorkspaceBackground(visible);
    }

    private void dispatchOnLauncherTransitionPrepare(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionPrepare(this, animated, toWorkspace);
        }
    }

    private void dispatchOnLauncherTransitionStart(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStart(this, animated, toWorkspace);
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 0f);
    }

    private void dispatchOnLauncherTransitionStep(View v, float t) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionStep(this, t);
        }
    }

    private void dispatchOnLauncherTransitionEnd(View v, boolean animated, boolean toWorkspace) {
        if (v instanceof LauncherTransitionable) {
            ((LauncherTransitionable) v).onLauncherTransitionEnd(this, animated, toWorkspace);
        }

        // Update the workspace transition step as well
        dispatchOnLauncherTransitionStep(v, 1f);
    }

    /**
     * Things to test when changing the following seven functions.
     *   - Home from workspace
     *          - from center screen
     *          - from other screens
     *   - Home from all apps
     *          - from center screen
     *          - from other screens
     *   - Back from all apps
     *          - from center screen
     *          - from other screens
     *   - Launch app from workspace and quit
     *          - with back
     *          - with home
     *   - Launch app from all apps and quit
     *          - with back
     *          - with home
     *   - Go to a screen that's not the default, then all
     *     apps, and launch and app, and go back
     *          - with back
     *          -with home
     *   - On workspace, long press power and go back
     *          - with back
     *          - with home
     *   - On all apps, long press power and go back
     *          - with back
     *          - with home
     *   - On workspace, power off
     *   - On all apps, power off
     *   - Launch an app and turn off the screen while in that app
     *          - Go back with home key
     *          - Go back with back key  TODO: make this not go to workspace
     *          - From all apps
     *          - From workspace
     *   - Enter and exit car mode (becuase it causes an extra configuration changed)
     *          - From all apps
     *          - From the center workspace
     *          - From another workspace
     */

    /**
     * Zoom the camera out from the workspace to reveal 'toView'.
     * Assumes that the view to show is anchored at either the very top or very bottom
     * of the screen.
     */
    private void showAppsCustomizeHelper(final boolean animated, final boolean springLoaded) {
        if (mStateAnimation != null) {
            mStateAnimation.cancel();
            mStateAnimation = null;
        }
        final Resources res = getResources();

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomInTime);
        final int fadeDuration = res.getInteger(R.integer.config_appsCustomizeFadeInTime);
        //final float scale = (float) res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View fromView = mWorkspace;
        final AppsCustomizeTabHost toView = mAppsCustomizeTabHost;
        final int startDelay =
                res.getInteger(R.integer.config_workspaceAppsCustomizeAnimationStagger);

        //setPivotsForZoom(toView, scale);

        // Shrink workspaces away if going to AppsCustomize from workspace
        // RTKCOMMENT: this is why workspace zoom out
        Animator workspaceAnim =
                mWorkspace.getChangeStateAnimation(Workspace.State.NORMAL, animated);

        if (animated) {
            //toView.setScaleX(scale);
            //toView.setScaleY(scale);
            // animator for scale
            //final LauncherViewPropertyAnimator scaleAnim = new LauncherViewPropertyAnimator(toView);
            /*
        	scaleAnim.
                scaleX(1f).scaleY(1f).
                setDuration(duration).
                setInterpolator(new Workspace.ZoomOutInterpolator());
            */

            toView.setVisibility(View.VISIBLE);
            toView.setAlpha(0f);
            // animator for alpha
            final ObjectAnimator alphaAnim = ObjectAnimator
                .ofFloat(toView, "alpha", 0f, 1f)
                .setDuration(fadeDuration);
            alphaAnim.setInterpolator(new DecelerateInterpolator(1.5f));
            alphaAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (animation == null) {
                        throw new RuntimeException("animation is null");
                    }
                    float t = (Float) animation.getAnimatedValue();
                    dispatchOnLauncherTransitionStep(fromView, t);
                    dispatchOnLauncherTransitionStep(toView, t);
                }
            });

            // toView should appear right at the end of the workspace shrink
            // animation
            mStateAnimation = LauncherAnimUtils.createAnimatorSet();
            // RTKCOMMENT: disable scale animation for performance issue
            //mStateAnimation.play(scaleAnim).after(startDelay);
            mStateAnimation.play(alphaAnim).after(startDelay);

            mStateAnimation.addListener(new AnimatorListenerAdapter() {
                boolean animationCancelled = false;

                @Override
                public void onAnimationStart(Animator animation) {
                    updateWallpaperVisibility(true);
                    // Prepare the position
                    toView.setTranslationX(0.0f);
                    toView.setTranslationY(0.0f);
                    if(!mAppsCustomizeTabHost.getCurrentTabTag().equals(mAppsCustomizeTabHost.getTabTagForContentType(mAppsCustomizeContent.mInitContentType))){
                    	DebugHelper.dump("do switchToInitialTab when animation.");
                    	mAppsCustomizeContent.switchToInitialTab(mAppsCustomizeContent.mInitContentType);
                    }
                    
                    toView.setVisibility(View.VISIBLE);
                    toView.bringToFront();
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    dispatchOnLauncherTransitionEnd(fromView, animated, false);
                    dispatchOnLauncherTransitionEnd(toView, animated, false);

                    if (mWorkspace != null && !springLoaded && !LauncherApplication.isScreenLarge()) {
                        // Hide the workspace scrollbar
                        mWorkspace.hideScrollingIndicator(true);
                        hideDockDivider();
                    }
                    if (!animationCancelled) {
                        updateWallpaperVisibility(false);
                    }

                    // Hide the search bar
                    if (mSearchDropTargetBar != null) {
                        mSearchDropTargetBar.hideSearchBar(false);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    animationCancelled = true;
                }
            });

            if (workspaceAnim != null) {
                mStateAnimation.play(workspaceAnim);
            }

            boolean delayAnim = false;
            final ViewTreeObserver observer;

            dispatchOnLauncherTransitionPrepare(fromView, animated, false);
            dispatchOnLauncherTransitionPrepare(toView, animated, false);

            // If any of the objects being animated haven't been measured/laid out
            // yet, delay the animation until we get a layout pass
            if ((((LauncherTransitionable) toView).getContent().getMeasuredWidth() == 0) ||
                    (mWorkspace.getMeasuredWidth() == 0) ||
                    (toView.getMeasuredWidth() == 0)) {
                observer = mWorkspace.getViewTreeObserver();
                delayAnim = true;
            } else {
                observer = null;
            }

            final AnimatorSet stateAnimation = mStateAnimation;
            final Runnable startAnimRunnable = new Runnable() {
                public void run() {
                    // Check that mStateAnimation hasn't changed while
                    // we waited for a layout/draw pass
                    if (mStateAnimation != stateAnimation)
                        return;
                    //setPivotsForZoom(toView, scale);
                    dispatchOnLauncherTransitionStart(fromView, animated, false);
                    dispatchOnLauncherTransitionStart(toView, animated, false);
                    toView.post(new Runnable() {
                        public void run() {
                            // Check that mStateAnimation hasn't changed while
                            // we waited for a layout/draw pass
                            if (mStateAnimation != stateAnimation)
                                return;
                            mStateAnimation.start();
                        }
                    });
                }
            };
            if (delayAnim) {
                final OnGlobalLayoutListener delayedStart = new OnGlobalLayoutListener() {
                    public void onGlobalLayout() {
                        toView.post(startAnimRunnable);
                        observer.removeOnGlobalLayoutListener(this);
                    }
                };
                observer.addOnGlobalLayoutListener(delayedStart);
            } else {
                startAnimRunnable.run();
            }
        } else {
            toView.setTranslationX(0.0f);
            toView.setTranslationY(0.0f);
            //toView.setScaleX(1.0f);
            //toView.setScaleY(1.0f);
            toView.setVisibility(View.VISIBLE);
            toView.bringToFront();

            if (!springLoaded && !LauncherApplication.isScreenLarge()) {
                // Hide the workspace scrollbar
                mWorkspace.hideScrollingIndicator(true);
                hideDockDivider();

                // Hide the search bar
                if (mSearchDropTargetBar != null) {
                    mSearchDropTargetBar.hideSearchBar(false);
                }
            }
            dispatchOnLauncherTransitionPrepare(fromView, animated, false);
            dispatchOnLauncherTransitionStart(fromView, animated, false);
            dispatchOnLauncherTransitionEnd(fromView, animated, false);
            dispatchOnLauncherTransitionPrepare(toView, animated, false);
            dispatchOnLauncherTransitionStart(toView, animated, false);
            dispatchOnLauncherTransitionEnd(toView, animated, false);
            updateWallpaperVisibility(false);
        }
    }

    /**
     * Zoom the camera back into the workspace, hiding 'fromView'.
     * This is the opposite of showAppsCustomizeHelper.
     * @param animated If true, the transition will be animated.
     */
    @SuppressWarnings("all")
    private void hideAppsCustomizeHelper(State toState, final boolean animated,
            final boolean springLoaded, final Runnable onCompleteRunnable) {
    	
    	// RTKCOMMENT: from app view back to workspace
        if (mStateAnimation != null) {
            mStateAnimation.cancel();
            mStateAnimation = null;
        }
        Resources res = getResources();

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomOutTime);
        final int fadeOutDuration =
                res.getInteger(R.integer.config_appsCustomizeFadeOutTime);
        //final float scaleFactor = (float)
        //        res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View fromView = mAppsCustomizeTabHost;
        final View toView = mWorkspace;
        Animator workspaceAnim = null;

        /*
        if (toState == State.WORKSPACE) {
            int stagger = res.getInteger(R.integer.config_appsCustomizeWorkspaceAnimationStagger);
            workspaceAnim = mWorkspace.getChangeStateAnimation(
                    Workspace.State.NORMAL, animated, stagger);
        } else if (toState == State.APPS_CUSTOMIZE_SPRING_LOADED) {
            workspaceAnim = mWorkspace.getChangeStateAnimation(
                    Workspace.State.SPRING_LOADED, animated);
        }
        */
        // To avoid BUG: 43197
        workspaceAnim=null;

        //setPivotsForZoom(fromView, scaleFactor);
        updateWallpaperVisibility(true);
        showHotseat(animated);
        if (animated) {
            /*
        	final LauncherViewPropertyAnimator scaleAnim =
                    new LauncherViewPropertyAnimator(fromView);
            scaleAnim.
                scaleX(scaleFactor).scaleY(scaleFactor).
                setDuration(duration).
                setInterpolator(new Workspace.ZoomInInterpolator());
             */
            final ObjectAnimator alphaAnim = ObjectAnimator
                .ofFloat(fromView, "alpha", 1f, 0f)
                .setDuration(fadeOutDuration);
            alphaAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            alphaAnim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float t = 1f - (Float) animation.getAnimatedValue();
                    dispatchOnLauncherTransitionStep(fromView, t);
                    dispatchOnLauncherTransitionStep(toView, t);
                }
            });

            mStateAnimation = LauncherAnimUtils.createAnimatorSet();

            dispatchOnLauncherTransitionPrepare(fromView, animated, true);
            dispatchOnLauncherTransitionPrepare(toView, animated, true);
            mAppsCustomizeContent.pauseScrolling();

            mStateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    updateWallpaperVisibility(true);
                    fromView.setVisibility(View.GONE);
                    dispatchOnLauncherTransitionEnd(fromView, animated, true);
                    dispatchOnLauncherTransitionEnd(toView, animated, true);
                    if (mWorkspace != null) {
                        mWorkspace.hideScrollingIndicator(false);
                    }
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }
                    mAppsCustomizeContent.updateCurrentPageScroll();
                    mAppsCustomizeContent.resumeScrolling();
                }
            });

            //mStateAnimation.playTogether(scaleAnim, alphaAnim);
            // dead code here.
            if (workspaceAnim != null) {
                mStateAnimation.play(workspaceAnim);
            }
            dispatchOnLauncherTransitionStart(fromView, animated, true);
            dispatchOnLauncherTransitionStart(toView, animated, true);
            final Animator stateAnimation = mStateAnimation;
            mWorkspace.post(new Runnable() {
                public void run() {
                    if (stateAnimation != mStateAnimation)
                        return;
                    mStateAnimation.start();
                }
            });
        } else {
            fromView.setVisibility(View.GONE);
            dispatchOnLauncherTransitionPrepare(fromView, animated, true);
            dispatchOnLauncherTransitionStart(fromView, animated, true);
            dispatchOnLauncherTransitionEnd(fromView, animated, true);
            dispatchOnLauncherTransitionPrepare(toView, animated, true);
            dispatchOnLauncherTransitionStart(toView, animated, true);
            dispatchOnLauncherTransitionEnd(toView, animated, true);
            mWorkspace.hideScrollingIndicator(false);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            mAppsCustomizeTabHost.onTrimMemory();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            // When another window occludes launcher (like the notification shade, or recents),
            // ensure that we enable the wallpaper flag so that transitions are done correctly.
            updateWallpaperVisibility(true);
        } else {
            // When launcher has focus again, disable the wallpaper if we are in AllApps
            mWorkspace.postDelayed(new Runnable() {
                @Override
                public void run() {
                    disableWallpaperIfInAllApps();
                }
            }, 500);
        }
    }
    // RTKCOMMENT: make this API public
    public void showWorkspace(boolean animated) {
    	
    	Runnable runnable = new Runnable() {
			@Override
			public void run() {
				
				
				if(bSetNewAddedChildToFocusAndMoveMode==true){
					// clear the flag
					bSetNewAddedChildToFocusAndMoveMode=false;
					
					DebugHelper.dump("[Launcher] showWorkspace - bSetNewAddedChildToFocusAndMoveMode");
					// set added view to focus
					mLatestAddedViewByOptionMenu.requestFocus();
					// switch into move mode, use handle to do a short delay.
					//handleWorkspaceMoveCommand();
		            sendRtkAdvMessageToHandler(SWITCH_MOVE_MODE_AFTER_ADD_COMMAND, 300);
				}else{
					// restore original focus child
					if(mWorkspaceFocusChild!=null)
						mWorkspaceFocusChild.requestFocus();
					
					// restore focus frame of focus child
					redrawWorkspaceFocusViewFrame(mWorkspace.findFocus());
				}
				
				
			}
		};
    	
        showWorkspace(animated, runnable);
    }
    
    public void redrawWorkspaceFocusViewFrame() {
        Log.d("RealtekLauncher2","redrawWorkspaceFocusViewFrame");
        redrawWorkspaceFocusViewFrame(mWorkspace.findFocus());
    }

    public void initSurfaceViewWidget(String extraString){
    	
    	if(mState!=State.WORKSPACE){
    		DebugHelper.dumpSQA("Try to init dtv widget, but not in workspace mode, break");
    		try{
    			throw new Exception();
    		}catch(Exception e){
    			e.printStackTrace();
    		}
    		return;
    	}

    	Intent i = new Intent(ConstantsDef.INIT_SURFACEVIEW_WIDGET_INTENT);
    	i.putExtra(ConstantsDef.INIT_EXTRA_NAME, extraString);
    	sendBroadcast(i);
    	Log.d(TAG,"initSurfaceViewWidget -----> "+extraString);
    }
    
    // RTKCOMMENT: API to switch from app page to workspace
    void showWorkspace(boolean animated, Runnable onCompleteRunnable) {
        if (mState != State.WORKSPACE) {
        	
        	DebugHelper.dump("Launcher showWorkspace.....");
        	
            boolean wasInSpringLoadedMode = (mState == State.APPS_CUSTOMIZE_SPRING_LOADED);
            mWorkspace.setVisibility(View.VISIBLE);
            hideAppsCustomizeHelper(State.WORKSPACE, animated, false, onCompleteRunnable);

            // Show the search bar (only animate if we were showing the drop target bar in spring
            // loaded mode)
            if (mSearchDropTargetBar != null) {
                mSearchDropTargetBar.showSearchBar(wasInSpringLoadedMode);
            }

            // We only need to animate in the dock divider if we're going from spring loaded mode
            showDockDivider(animated && wasInSpringLoadedMode);

            // Set focus to the AppsCustomize button
            if (mAllAppsButton != null) {
                mAllAppsButton.requestFocus();
            }
        }

        mWorkspace.flashScrollingIndicator(animated);

        // Change the state *after* we've called all the transition code
        mState = State.WORKSPACE;

        // Resume the auto-advance of widgets
        mUserPresent = true;
        updateRunning();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        
        // finally, display focus frame
        // View v=mWorkspace.findFocus();
        // redrawWorkspaceFocusViewFrame(v);
        if(mScreenState){
            initSurfaceViewWidget("showWorkspace");
        }

    }

    void showAllApps(boolean animated,ContentType type) {
        if (mState != State.WORKSPACE) return;
        
        // RTKCOMMENT: set initial tab before app page shows up.
        mAppsCustomizeContent.mInitContentType=type;
        
        // RTKCOMMENT: if data laready ready, do swicth to app page
        if(mAppsCustomizeContent.mIsDataReady){
        	DebugHelper.dump("showAllApps, data is ready, process switchToInitialTab");
        	mAppsCustomizeContent.switchToInitialTab(mAppsCustomizeContent.mInitContentType);
        }
        
        showAppsCustomizeHelper(animated, false);
        mAppsCustomizeTabHost.requestFocus();

        // Change the state *after* we've called all the transition code
        // RTKCOMMENT: it would be a good practice if I can add State.FAVAPPS_CUSTOMIZE and State.VIDEOAPPS_CUSTOMIZE.
        mState = State.APPS_CUSTOMIZE;
        
        // Pause the auto-advance of widgets until we are out of AllApps
        mUserPresent = false;
        updateRunning();
        closeFolder();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView()
                .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    void enterSpringLoadedDragMode() {
        if (isAllAppsVisible()) {
            hideAppsCustomizeHelper(State.APPS_CUSTOMIZE_SPRING_LOADED, true, true, null);
            hideDockDivider();
            mState = State.APPS_CUSTOMIZE_SPRING_LOADED;
        }
    }

    void exitSpringLoadedDragModeDelayed(final boolean successfulDrop, boolean extendedDelay,
            final Runnable onCompleteRunnable) {
        if (mState != State.APPS_CUSTOMIZE_SPRING_LOADED) return;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (successfulDrop) {
                    // Before we show workspace, hide all apps again because
                    // exitSpringLoadedDragMode made it visible. This is a bit hacky; we should
                    // clean up our state transition functions
                    mAppsCustomizeTabHost.setVisibility(View.GONE);
                    showWorkspace(true, onCompleteRunnable);
                } else {
                    exitSpringLoadedDragMode();
                }
            }
        }, (extendedDelay ?
                EXIT_SPRINGLOADED_MODE_LONG_TIMEOUT :
                EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT));
    }

    void exitSpringLoadedDragMode() {
        if (mState == State.APPS_CUSTOMIZE_SPRING_LOADED) {
            final boolean animated = true;
            final boolean springLoaded = true;
            showAppsCustomizeHelper(animated, springLoaded);
            mState = State.APPS_CUSTOMIZE;
        }
        // Otherwise, we are not in spring loaded mode, so don't do anything.
    }

    void hideDockDivider() {
        if (mQsbDivider != null && mDockDivider != null) {
            mQsbDivider.setVisibility(View.INVISIBLE);
            mDockDivider.setVisibility(View.INVISIBLE);
        }
    }

    void showDockDivider(boolean animated) {
        if (mQsbDivider != null && mDockDivider != null) {
            mQsbDivider.setVisibility(View.VISIBLE);
            mDockDivider.setVisibility(View.VISIBLE);
            if (mDividerAnimator != null) {
                mDividerAnimator.cancel();
                mQsbDivider.setAlpha(1f);
                mDockDivider.setAlpha(1f);
                mDividerAnimator = null;
            }
            if (animated) {
                mDividerAnimator = LauncherAnimUtils.createAnimatorSet();
                mDividerAnimator.playTogether(LauncherAnimUtils.ofFloat(mQsbDivider, "alpha", 1f),
                        LauncherAnimUtils.ofFloat(mDockDivider, "alpha", 1f));
                int duration = 0;
                if (mSearchDropTargetBar != null) {
                    duration = mSearchDropTargetBar.getTransitionInDuration();
                }
                mDividerAnimator.setDuration(duration);
                mDividerAnimator.start();
            }
        }
    }

    void lockAllApps() {
        // TODO
    }

    void unlockAllApps() {
        // TODO
    }

    /**
     * Shows the hotseat area.
     */
    void showHotseat(boolean animated) {
        if (!LauncherApplication.isScreenLarge()) {
            if (animated) {
                if (mHotseat.getAlpha() != 1f) {
                    int duration = 0;
                    if (mSearchDropTargetBar != null) {
                        duration = mSearchDropTargetBar.getTransitionInDuration();
                    }
                    mHotseat.animate().alpha(1f).setDuration(duration);
                }
            } else {
                mHotseat.setAlpha(1f);
            }
        }
    }

    /**
     * Hides the hotseat area.
     */
    void hideHotseat(boolean animated) {
        if (!LauncherApplication.isScreenLarge()) {
            if (animated) {
                if (mHotseat.getAlpha() != 0f) {
                    int duration = 0;
                    if (mSearchDropTargetBar != null) {
                        duration = mSearchDropTargetBar.getTransitionOutDuration();
                    }
                    mHotseat.animate().alpha(0f).setDuration(duration);
                }
            } else {
                mHotseat.setAlpha(0f);
            }
        }
    }

    /**
     * Add an item from all apps or customize onto the given workspace screen.
     * If layout is null, add to the current screen.
     */
    void addExternalItemToScreen(ItemInfo itemInfo, final CellLayout layout) {
        if (!mWorkspace.addExternalItemToScreen(itemInfo, layout)) {
            showOutOfSpaceMessage(isHotseatLayout(layout));
        }
    }

    /** Maps the current orientation to an index for referencing orientation correct global icons */
    private int getCurrentOrientationIndexForGlobalIcons() {
        // default - 0, landscape - 1
        switch (getResources().getConfiguration().orientation) {
        case Configuration.ORIENTATION_LANDSCAPE:
            return 1;
        default:
            return 0;
        }
    }

    private Drawable getExternalPackageToolbarIcon(ComponentName activityName, String resourceName) {
        try {
            PackageManager packageManager = getPackageManager();
            // Look for the toolbar icon specified in the activity meta-data
            Bundle metaData = packageManager.getActivityInfo(
                    activityName, PackageManager.GET_META_DATA).metaData;
            if (metaData != null) {
                int iconResId = metaData.getInt(resourceName);
                if (iconResId != 0) {
                    Resources res = packageManager.getResourcesForActivity(activityName);
                    return res.getDrawable(iconResId);
                }
            }
        } catch (NameNotFoundException e) {
            // This can happen if the activity defines an invalid drawable
            Log.w(TAG, "Failed to load toolbar icon; " + activityName.flattenToShortString() +
                    " not found", e);
        } catch (Resources.NotFoundException nfe) {
            // This can happen if the activity defines an invalid drawable
            Log.w(TAG, "Failed to load toolbar icon from " + activityName.flattenToShortString(),
                    nfe);
        }
        return null;
    }

    // if successful in getting icon, return it; otherwise, set button to use default drawable
    private Drawable.ConstantState updateTextButtonWithIconFromExternalActivity(
            int buttonId, ComponentName activityName, int fallbackDrawableId,
            String toolbarResourceName) {
        Drawable toolbarIcon = getExternalPackageToolbarIcon(activityName, toolbarResourceName);
        Resources r = getResources();
        int w = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_width);
        int h = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_height);

        TextView button = (TextView) findViewById(buttonId);
        // If we were unable to find the icon via the meta-data, use a generic one
        if (toolbarIcon == null) {
            toolbarIcon = r.getDrawable(fallbackDrawableId);
            toolbarIcon.setBounds(0, 0, w, h);
            if (button != null) {
                button.setCompoundDrawables(toolbarIcon, null, null, null);
            }
            return null;
        } else {
            toolbarIcon.setBounds(0, 0, w, h);
            if (button != null) {
                button.setCompoundDrawables(toolbarIcon, null, null, null);
            }
            return toolbarIcon.getConstantState();
        }
    }

    // if successful in getting icon, return it; otherwise, set button to use default drawable
    private Drawable.ConstantState updateButtonWithIconFromExternalActivity(
            int buttonId, ComponentName activityName, int fallbackDrawableId,
            String toolbarResourceName) {
        ImageView button = (ImageView) findViewById(buttonId);
        Drawable toolbarIcon = getExternalPackageToolbarIcon(activityName, toolbarResourceName);

        if (button != null) {
            // If we were unable to find the icon via the meta-data, use a
            // generic one
            if (toolbarIcon == null) {
                button.setImageResource(fallbackDrawableId);
            } else {
                button.setImageDrawable(toolbarIcon);
            }
        }

        return toolbarIcon != null ? toolbarIcon.getConstantState() : null;

    }

    private void updateTextButtonWithDrawable(int buttonId, Drawable d) {
        TextView button = (TextView) findViewById(buttonId);
        button.setCompoundDrawables(d, null, null, null);
    }

    private void updateButtonWithDrawable(int buttonId, Drawable.ConstantState d) {
        ImageView button = (ImageView) findViewById(buttonId);
        button.setImageDrawable(d.newDrawable(getResources()));
    }

    private void invalidatePressedFocusedStates(View container, View button) {
        if (container instanceof HolographicLinearLayout) {
            HolographicLinearLayout layout = (HolographicLinearLayout) container;
            layout.invalidatePressedFocusedStates();
        } else if (button instanceof HolographicImageView) {
            HolographicImageView view = (HolographicImageView) button;
            view.invalidatePressedFocusedStates();
        }
    }

    private boolean updateGlobalSearchIcon() {
        final View searchButtonContainer = findViewById(R.id.search_button_container);
        final ImageView searchButton = (ImageView) findViewById(R.id.search_button);
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);
        final View voiceButtonProxy = findViewById(R.id.voice_button_proxy);

        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName activityName = CustomizedHelper.getFakeSearchActivity();//searchManager.getGlobalSearchActivity();
        if (activityName != null) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            sGlobalSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                    R.id.search_button, activityName, R.drawable.ic_home_search_normal_holo,
                    TOOLBAR_SEARCH_ICON_METADATA_NAME);
            if (sGlobalSearchIcon[coi] == null) {
                sGlobalSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                        R.id.search_button, activityName, R.drawable.ic_home_search_normal_holo,
                        TOOLBAR_ICON_METADATA_NAME);
            }

            if (searchButtonContainer != null) searchButtonContainer.setVisibility(View.VISIBLE);
            searchButton.setVisibility(View.VISIBLE);
            invalidatePressedFocusedStates(searchButtonContainer, searchButton);
            return true;
        } else {
            // We disable both search and voice search when there is no global search provider
            if (searchButtonContainer != null) searchButtonContainer.setVisibility(View.GONE);
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.GONE);
            searchButton.setVisibility(View.GONE);
            voiceButton.setVisibility(View.GONE);
            if (voiceButtonProxy != null) {
                voiceButtonProxy.setVisibility(View.GONE);
            }
            return false;
        }
    }

    private void updateGlobalSearchIcon(Drawable.ConstantState d) {
        final View searchButtonContainer = findViewById(R.id.search_button_container);
        final View searchButton = (ImageView) findViewById(R.id.search_button);
        updateButtonWithDrawable(R.id.search_button, d);
        invalidatePressedFocusedStates(searchButtonContainer, searchButton);
    }

    private boolean updateVoiceSearchIcon(boolean searchVisible) {
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);
        final View voiceButtonProxy = findViewById(R.id.voice_button_proxy);

        // We only show/update the voice search icon if the search icon is enabled as well
        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName globalSearchActivity = CustomizedHelper.getFakeSearchActivity();//searchManager.getGlobalSearchActivity();

        ComponentName activityName = null;
        if (globalSearchActivity != null) {
            // Check if the global search activity handles voice search
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setPackage(globalSearchActivity.getPackageName());
            activityName = intent.resolveActivity(getPackageManager());
        }

        if (activityName == null) {
            // Fallback: check if an activity other than the global search activity
            // resolves this
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            activityName = intent.resolveActivity(getPackageManager());
        }
        if (searchVisible && activityName != null) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            sVoiceSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                    R.id.voice_button, activityName, R.drawable.ic_home_voice_search_holo,
                    TOOLBAR_VOICE_SEARCH_ICON_METADATA_NAME);
            if (sVoiceSearchIcon[coi] == null) {
                sVoiceSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                        R.id.voice_button, activityName, R.drawable.ic_home_voice_search_holo,
                        TOOLBAR_ICON_METADATA_NAME);
            }
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.VISIBLE);
            voiceButton.setVisibility(View.VISIBLE);
            if (voiceButtonProxy != null) {
                voiceButtonProxy.setVisibility(View.VISIBLE);
            }
            invalidatePressedFocusedStates(voiceButtonContainer, voiceButton);
            return true;
        } else {
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.GONE);
            voiceButton.setVisibility(View.GONE);
            if (voiceButtonProxy != null) {
                voiceButtonProxy.setVisibility(View.GONE);
            }
            return false;
        }
    }

    private void updateVoiceSearchIcon(Drawable.ConstantState d) {
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);
        updateButtonWithDrawable(R.id.voice_button, d);
        invalidatePressedFocusedStates(voiceButtonContainer, voiceButton);
    }

    /**
     * Sets the app market icon
     */
    private void updateAppMarketIcon() {
        final View marketButton = findViewById(R.id.market_button);
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET);
        // Find the app market activity by resolving an intent.
        // (If multiple app markets are installed, it will return the ResolverActivity.)
        ComponentName activityName = intent.resolveActivity(getPackageManager());
        if (activityName != null) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            mAppMarketIntent = intent;
            sAppMarketIcon[coi] = updateTextButtonWithIconFromExternalActivity(
                    R.id.market_button, activityName, R.drawable.ic_launcher_market_holo,
                    TOOLBAR_ICON_METADATA_NAME);
            marketButton.setVisibility(View.VISIBLE);
        } else {
            // We should hide and disable the view so that we don't try and restore the visibility
            // of it when we swap between drag & normal states from IconDropTarget subclasses.
            marketButton.setVisibility(View.GONE);
            marketButton.setEnabled(false);
        }
    }

    private void updateAppMarketIcon(Drawable.ConstantState d) {
        // Ensure that the new drawable we are creating has the approprate toolbar icon bounds
        Resources r = getResources();
        Drawable marketIconDrawable = d.newDrawable(r);
        int w = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_width);
        int h = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_height);
        marketIconDrawable.setBounds(0, 0, w, h);

        updateTextButtonWithDrawable(R.id.market_button, marketIconDrawable);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        final boolean result = super.dispatchPopulateAccessibilityEvent(event);
        final List<CharSequence> text = event.getText();
        text.clear();
        // Populate event with a fake title based on the current state.
        if (mState == State.APPS_CUSTOMIZE) {
            text.add(getString(R.string.all_apps_button_label));
        } else {
            text.add(getString(R.string.all_apps_home_button_label));
        }
        return result;
    }

    /**
     * Receives notifications when system dialogs are to be closed.
     */
    private class CloseSystemDialogsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            closeSystemDialogs();
        }
    }

    /**
     * Receives notifications whenever the appwidgets are reset.
     */
    private class AppWidgetResetObserver extends ContentObserver {
        public AppWidgetResetObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            onAppWidgetReset();
        }
    }

    /**
     * If the activity is currently paused, signal that we need to re-run the loader
     * in onResume.
     *
     * This needs to be called from incoming places where resources might have been loaded
     * while we are paused.  That is becaues the Configuration might be wrong
     * when we're not running, and if it comes back to what it was when we
     * were paused, we are not restarted.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return true if we are currently paused.  The caller might be able to
     * skip some work in that case since we will come back again.
     */
    public boolean setLoadOnResume() {
        if (mPaused) {
            Log.i(TAG, "setLoadOnResume");
            mOnResumeNeedsLoad = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public int getCurrentWorkspaceScreen() {
        if (mWorkspace != null) {
            return mWorkspace.getCurrentPage();
        } else {
            return SCREEN_COUNT / 2;
        }
    }

    /**
     * Refreshes the shortcuts shown on the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void startBinding() {
        final Workspace workspace = mWorkspace;

        mNewShortcutAnimatePage = -1;
        mNewShortcutAnimateViews.clear();
        mWorkspace.clearDropTargets();
        int count = workspace.getChildCount();
        for (int i = 0; i < count; i++) {
            // Use removeAllViewsInLayout() to avoid an extra requestLayout() and invalidate().
            final CellLayout layoutParent = (CellLayout) workspace.getChildAt(i);
            layoutParent.removeAllViewsInLayout();
        }
        mWidgetsToAdvance.clear();
        if (mHotseat != null) {
            mHotseat.resetLayout();
        }
    }

    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindItems(ArrayList<ItemInfo> shortcuts, int start, int end) {
        setLoadOnResume();

        // Get the list of added shortcuts and intersect them with the set of shortcuts here
        Set<String> newApps = new HashSet<String>();
        newApps = mSharedPrefs.getStringSet(InstallShortcutReceiver.NEW_APPS_LIST_KEY, newApps);

        Workspace workspace = mWorkspace;
        for (int i = start; i < end; i++) {
            final ItemInfo item = shortcuts.get(i);

            // Short circuit if we are loading dock items for a configuration which has no dock
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                    mHotseat == null) {
                continue;
            }

            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    ShortcutInfo info = (ShortcutInfo) item;
                    String uri = info.intent.toUri(0).toString();
                    View shortcut = createShortcut(info);
                    workspace.addInScreen(shortcut, item.container, item.screen, item.cellX,
                            item.cellY, 1, 1, false);
                    boolean animateIconUp = false;
                    synchronized (newApps) {
                        if (newApps.contains(uri)) {
                            animateIconUp = newApps.remove(uri);
                        }
                    }
                    if (animateIconUp) {
                        // Prepare the view to be animated up
                        shortcut.setAlpha(0f);
                        shortcut.setScaleX(0f);
                        shortcut.setScaleY(0f);
                        mNewShortcutAnimatePage = item.screen;
                        
                        // check item screen id:
                        if(item.screen>0){
                            Log.d("RTK", "item info error:"+item.toString());
                            try{
                                throw new Exception();   
                            }catch(Exception e){
                                e.printStackTrace();
                            }
                        }
                        
                        if (!mNewShortcutAnimateViews.contains(shortcut)) {
                            mNewShortcutAnimateViews.add(shortcut);
                        }
                    }
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                    FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (FolderInfo) item, mIconCache);
                    workspace.addInScreen(newFolder, item.container, item.screen, item.cellX,
                            item.cellY, 1, 1, false);
                    break;
            }
        }

        workspace.requestLayout();
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindFolders(HashMap<Long, FolderInfo> folders) {
        setLoadOnResume();
        sFolders.clear();
        sFolders.putAll(folders);
    }

    
    // KEYPOINT: load existing app widget
    /**
     * Add the views for a widget to the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppWidget(LauncherAppWidgetInfo item) {
    	
    	DebugHelper.dump("Launcher bindAppWidget item="+item);
    	
        setLoadOnResume();

        final long start = DEBUG_WIDGETS ? SystemClock.uptimeMillis() : 0;
        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bindAppWidget: " + item);
        }
        final Workspace workspace = mWorkspace;

        final int appWidgetId = item.appWidgetId;
        final AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bindAppWidget: id=" + item.appWidgetId + " belongs to component " + appWidgetInfo.provider);
        }

        item.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);

        item.hostView.setTag(item);
        item.onBindAppWidget(this);

        workspace.addInScreen(item.hostView, item.container, item.screen, item.cellX,
                item.cellY, item.spanX, item.spanY, false);
        addWidgetToAutoAdvanceIfNeeded(item.hostView, appWidgetInfo);

        workspace.requestLayout();

        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bound widget id="+item.appWidgetId+" in "
                    + (SystemClock.uptimeMillis()-start) + "ms");
        }
        
        // RTKTODO: like bind shortcut, register click listener to widget
        DebugHelper.dump("|||||||||||||||||||| bind App Widget, setOnClickListener");
        item.hostView.setOnClickListener(this);
        
    }

    public void onPageBoundSynchronously(int page) {
        mSynchronouslyBoundPages.add(page);
    }

    /**
     * Callback saying that there aren't any more items to bind.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems() {
        setLoadOnResume();

        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentPage()).requestFocus();
            }
            mSavedState = null;
        }

        mWorkspace.restoreInstanceStateForRemainingPages();

        // If we received the result of any pending adds while the loader was running (e.g. the
        // widget configuration forced an orientation change), process them now.
        for (int i = 0; i < sPendingAddList.size(); i++) {
            completeAdd(sPendingAddList.get(i));
        }
        sPendingAddList.clear();

        // Update the market app icon as necessary (the other icons will be managed in response to
        // package changes in bindSearchablesChanged()
        updateAppMarketIcon();

        // Animate up any icons as necessary
        if (mVisible || mWorkspaceLoading) {
            Runnable newAppsRunnable = new Runnable() {
                @Override
                public void run() {
                    runNewAppsAnimation(false);
                }
            };

            boolean willSnapPage = mNewShortcutAnimatePage > -1 &&
                    mNewShortcutAnimatePage != mWorkspace.getCurrentPage();
            if (canRunNewAppsAnimation()) {
                // If the user has not interacted recently, then either snap to the new page to show
                // the new-apps animation or just run them if they are to appear on the current page
                if (willSnapPage) {
                    mWorkspace.snapToPage(mNewShortcutAnimatePage, newAppsRunnable);
                } else {
                    runNewAppsAnimation(false);
                }
            } else {
                // If the user has interacted recently, then just add the items in place if they
                // are on another page (or just normally if they are added to the current page)
                runNewAppsAnimation(willSnapPage);
            }
        }

        mWorkspaceLoading = false;
        
        // BUG_FIX: 42728
        DebugHelper.dumpSQA("Launcher state:"+mState);
        if(mState==State.APPS_CUSTOMIZE){
            mAppsCustomizeTabHost.requestFocus();
        }
    }

    private boolean canRunNewAppsAnimation() {
        long diff = System.currentTimeMillis() - mDragController.getLastGestureUpTime();
        return diff > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000);
    }

    /**
     * Runs a new animation that scales up icons that were added while Launcher was in the
     * background.
     *
     * @param immediate whether to run the animation or show the results immediately
     */
    private void runNewAppsAnimation(boolean immediate) {
        AnimatorSet anim = LauncherAnimUtils.createAnimatorSet();
        Collection<Animator> bounceAnims = new ArrayList<Animator>();

        // Order these new views spatially so that they animate in order
        Collections.sort(mNewShortcutAnimateViews, new Comparator<View>() {
            @Override
            public int compare(View a, View b) {
                CellLayout.LayoutParams alp = (CellLayout.LayoutParams) a.getLayoutParams();
                CellLayout.LayoutParams blp = (CellLayout.LayoutParams) b.getLayoutParams();
                int cellCountX = LauncherModel.getCellCountX();
                return (alp.cellY * cellCountX + alp.cellX) - (blp.cellY * cellCountX + blp.cellX);
            }
        });

        // Animate each of the views in place (or show them immediately if requested)
        if (immediate) {
            for (View v : mNewShortcutAnimateViews) {
                v.setAlpha(1f);
                v.setScaleX(1f);
                v.setScaleY(1f);
            }
        } else {
            for (int i = 0; i < mNewShortcutAnimateViews.size(); ++i) {
                View v = mNewShortcutAnimateViews.get(i);
                ValueAnimator bounceAnim = LauncherAnimUtils.ofPropertyValuesHolder(v,
                        PropertyValuesHolder.ofFloat("alpha", 1f),
                        PropertyValuesHolder.ofFloat("scaleX", 1f),
                        PropertyValuesHolder.ofFloat("scaleY", 1f));
                bounceAnim.setDuration(InstallShortcutReceiver.NEW_SHORTCUT_BOUNCE_DURATION);
                bounceAnim.setStartDelay(i * InstallShortcutReceiver.NEW_SHORTCUT_STAGGER_DELAY);
                bounceAnim.setInterpolator(new SmoothPagedView.OvershootInterpolator());
                bounceAnims.add(bounceAnim);
            }
            anim.playTogether(bounceAnims);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (mWorkspace != null) {
                        mWorkspace.postDelayed(mBuildLayersRunnable, 500);
                    }
                }
            });
            anim.start();
        }

        // Clean up
        mNewShortcutAnimatePage = -1;
        mNewShortcutAnimateViews.clear();
        new Thread("clearNewAppsThread") {
            public void run() {
                mSharedPrefs.edit()
                            .putInt(InstallShortcutReceiver.NEW_APPS_PAGE_KEY, -1)
                            .putStringSet(InstallShortcutReceiver.NEW_APPS_LIST_KEY, null)
                            .commit();
            }
        }.start();
    }

    @Override
    public void bindSearchablesChanged() {
        boolean searchVisible = updateGlobalSearchIcon();
        boolean voiceVisible = updateVoiceSearchIcon(searchVisible);
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.onSearchPackagesChanged(searchVisible, voiceVisible);
        }
    }

    /**
     * Add the icons for all apps.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAllApplications(final ArrayList<ApplicationInfo> apps) {
        Runnable setAllAppsRunnable = new Runnable() {
            public void run() {
                if (mAppsCustomizeContent != null) {
                    mAppsCustomizeContent.setApps(apps);
                }
            }
        };

        // Remove the progress bar entirely; we could also make it GONE
        // but better to remove it since we know it's not going to be used
        View progressBar = mAppsCustomizeTabHost.
            findViewById(R.id.apps_customize_progress_bar);
        if (progressBar != null) {
            ((ViewGroup)progressBar.getParent()).removeView(progressBar);

            // We just post the call to setApps so the user sees the progress bar
            // disappear-- otherwise, it just looks like the progress bar froze
            // which doesn't look great
            mAppsCustomizeTabHost.post(setAllAppsRunnable);
        } else {
            // If we did not initialize the spinner in onCreate, then we can directly set the
            // list of applications without waiting for any progress bars views to be hidden.
            setAllAppsRunnable.run();
        }
    }

    /**
     * A package was installed.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsAdded(ArrayList<ApplicationInfo> apps) {
        setLoadOnResume();

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.addApps(apps);
        }
    }
    
    /**
     * callback to handle category page is updated.
     * 
     * Implementation of the method from LauncherModel.Callbacks.<p>
     * Added by Realtek
     */
    public void bindCategoryPageUpdated(){
    	DebugHelper.dump("bindCategoryPageUpdated");
    	if(mAppsCustomizeContent != null){
    		mAppsCustomizeContent.updateCategoryPage();
    	}
    }
    
    /**
     * Callback to provide interface to launch app customized page
     */
    public void switchToAppCustomizedPage(ContentType type){

    	clearWorkspaceFocusViewFrame();
    	mWorkspaceFocusChild=mWorkspace.findFocus();
    	uninitSurfaceViewWidget("switchToAppPage");
    	showAllApps(true,type);
    }
    
    /**
     * A package was updated.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsUpdated(ArrayList<ApplicationInfo> apps) {
        setLoadOnResume();
        if (mWorkspace != null) {
            mWorkspace.updateShortcuts(apps);
        }

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.updateApps(apps);
        }
    }

    /**
     * A package was uninstalled.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsRemoved(ArrayList<String> packageNames, boolean permanent) {
        if (permanent) {
        	// RTKCOMMENT: remove shortcut of these apps
            mWorkspace.removeItems(packageNames);
        }

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.removeApps(packageNames);
        }

        // Notify the drag controller
        mDragController.onAppsRemoved(packageNames, this);
    }

    /**
     * A number of packages were updated.
     */
    public void bindPackagesUpdated() {
        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.onPackagesUpdated();
        }
    }

    private int mapConfigurationOriActivityInfoOri(int configOri) {
        final Display d = getWindowManager().getDefaultDisplay();
        int naturalOri = Configuration.ORIENTATION_LANDSCAPE;
        switch (d.getRotation()) {
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
            // We are currently in the same basic orientation as the natural orientation
            naturalOri = configOri;
            break;
        case Surface.ROTATION_90:
        case Surface.ROTATION_270:
            // We are currently in the other basic orientation to the natural orientation
            naturalOri = (configOri == Configuration.ORIENTATION_LANDSCAPE) ?
                    Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
            break;
        }

        int[] oriMap = {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        };
        // Since the map starts at portrait, we need to offset if this device's natural orientation
        // is landscape.
        int indexOffset = 0;
        if (naturalOri == Configuration.ORIENTATION_LANDSCAPE) {
            indexOffset = 1;
        }
        return oriMap[(d.getRotation() + indexOffset) % 4];
    }

    public boolean isRotationEnabled() {
        boolean enableRotation = sForceEnableRotation ||
                getResources().getBoolean(R.bool.allow_rotation);
        return enableRotation;
    }
    public void lockScreenOrientation() {
        if (isRotationEnabled()) {
            setRequestedOrientation(mapConfigurationOriActivityInfoOri(getResources()
                    .getConfiguration().orientation));
        }
    }
    public void unlockScreenOrientation(boolean immediate) {
        if (isRotationEnabled()) {
            if (immediate) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            } else {
                mHandler.postDelayed(new Runnable() {
                    public void run() {
                        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    }
                }, mRestoreScreenOrientationDelay);
            }
        }
    }

    /* Cling related */
    private boolean isClingsEnabled() {
        // disable clings when running in a test harness
        if(ActivityManager.isRunningInTestHarness()) return false;

        return CustomizedHelper.ENABLE_CLING;
    }

    private Cling initCling(int clingId, int[] positionData, boolean animate, int delay) {
        final Cling cling = (Cling) findViewById(clingId);
        if (cling != null) {
            cling.init(this, positionData);
            cling.setVisibility(View.VISIBLE);
            cling.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (animate) {
                cling.buildLayer();
                cling.setAlpha(0f);
                cling.animate()
                    .alpha(1f)
                    .setInterpolator(new AccelerateInterpolator())
                    .setDuration(SHOW_CLING_DURATION)
                    .setStartDelay(delay)
                    .start();
            } else {
                cling.setAlpha(1f);
            }
            cling.setFocusableInTouchMode(true);
            cling.post(new Runnable() {
                public void run() {
                    cling.setFocusable(true);
                    cling.requestFocus();
                }
            });
            mHideFromAccessibilityHelper.setImportantForAccessibilityToNo(
                    mDragLayer, clingId == R.id.all_apps_cling);
        }
        return cling;
    }

    private void dismissCling(final Cling cling, final String flag, int duration) {
        // To catch cases where siblings of top-level views are made invisible, just check whether
        // the cling is directly set to GONE before dismissing it.
        if (cling != null && cling.getVisibility() != View.GONE) {
            ObjectAnimator anim = LauncherAnimUtils.ofFloat(cling, "alpha", 0f);
            anim.setDuration(duration);
            anim.addListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    cling.setVisibility(View.GONE);
                    cling.cleanup();
                    // We should update the shared preferences on a background thread
                    new Thread("dismissClingThread") {
                        public void run() {
                            SharedPreferences.Editor editor = mSharedPrefs.edit();
                            editor.putBoolean(flag, true);
                            editor.commit();
                        }
                    }.start();
                };
            });
            anim.start();
            mHideFromAccessibilityHelper.restoreImportantForAccessibility(mDragLayer);
        }
    }

    private void removeCling(int id) {
        final View cling = findViewById(id);
        if (cling != null) {
            final ViewGroup parent = (ViewGroup) cling.getParent();
            parent.post(new Runnable() {
                @Override
                public void run() {
                    parent.removeView(cling);
                }
            });
            mHideFromAccessibilityHelper.restoreImportantForAccessibility(mDragLayer);
        }
    }

    private boolean skipCustomClingIfNoAccounts() {
        Cling cling = (Cling) findViewById(R.id.workspace_cling);
        boolean customCling = cling.getDrawIdentifier().equals("workspace_custom");
        if (customCling) {
            AccountManager am = AccountManager.get(this);
            Account[] accounts = am.getAccountsByType("com.google");
            return accounts.length == 0;
        }
        return false;
    }

    public void showFirstRunWorkspaceCling() {
        // Enable the clings only if they have not been dismissed before
        if (isClingsEnabled() &&
                !mSharedPrefs.getBoolean(Cling.WORKSPACE_CLING_DISMISSED_KEY, false) &&
                !skipCustomClingIfNoAccounts() ) {
            // If we're not using the default workspace layout, replace workspace cling
            // with a custom workspace cling (usually specified in an overlay)
            // For now, only do this on tablets
            if (mSharedPrefs.getInt(LauncherProvider.DEFAULT_WORKSPACE_RESOURCE_ID, 0) != 0 &&
                    getResources().getBoolean(R.bool.config_useCustomClings)) {
                // Use a custom cling
                View cling = findViewById(R.id.workspace_cling);
                ViewGroup clingParent = (ViewGroup) cling.getParent();
                int clingIndex = clingParent.indexOfChild(cling);
                clingParent.removeViewAt(clingIndex);
                View customCling = mInflater.inflate(R.layout.custom_workspace_cling, clingParent, false);
                clingParent.addView(customCling, clingIndex);
                customCling.setId(R.id.workspace_cling);
            }
            initCling(R.id.workspace_cling, null, false, 0);
        } else {
            removeCling(R.id.workspace_cling);
        }
    }
    public void showFirstRunAllAppsCling(int[] position) {
        // Enable the clings only if they have not been dismissed before
        if (isClingsEnabled() &&
                !mSharedPrefs.getBoolean(Cling.ALLAPPS_CLING_DISMISSED_KEY, false)) {
            initCling(R.id.all_apps_cling, position, true, 0);
        } else {
            removeCling(R.id.all_apps_cling);
        }
    }
    public Cling showFirstRunFoldersCling() {
        // Enable the clings only if they have not been dismissed before
        if (isClingsEnabled() &&
                !mSharedPrefs.getBoolean(Cling.FOLDER_CLING_DISMISSED_KEY, false)) {
            return initCling(R.id.folder_cling, null, true, 0);
        } else {
            removeCling(R.id.folder_cling);
            return null;
        }
    }
    public boolean isFolderClingVisible() {
        Cling cling = (Cling) findViewById(R.id.folder_cling);
        if (cling != null) {
            return cling.getVisibility() == View.VISIBLE;
        }
        return false;
    }
    public void dismissWorkspaceCling(View v) {
        Cling cling = (Cling) findViewById(R.id.workspace_cling);
        dismissCling(cling, Cling.WORKSPACE_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
    }
    public void dismissAllAppsCling(View v) {
        Cling cling = (Cling) findViewById(R.id.all_apps_cling);
        dismissCling(cling, Cling.ALLAPPS_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
    }
    public void dismissFolderCling(View v) {
        Cling cling = (Cling) findViewById(R.id.folder_cling);
        dismissCling(cling, Cling.FOLDER_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
    }

    /**
     * Prints out out state for debugging.
     */
    public void dumpState() {
        Log.d(TAG, "BEGIN launcher2 dump state for launcher " + this);
        Log.d(TAG, "mSavedState=" + mSavedState);
        Log.d(TAG, "mWorkspaceLoading=" + mWorkspaceLoading);
        Log.d(TAG, "mRestoring=" + mRestoring);
        Log.d(TAG, "mWaitingForResult=" + mWaitingForResult);
        Log.d(TAG, "mSavedInstanceState=" + mSavedInstanceState);
        Log.d(TAG, "sFolders.size=" + sFolders.size());
        mModel.dumpState();

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.dumpState();
        }
        Log.d(TAG, "END launcher2 dump state");
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.println(" ");
        writer.println("Debug logs: ");
        for (int i = 0; i < sDumpLogs.size(); i++) {
            writer.println("  " + sDumpLogs.get(i));
        }
    }

    public static void dumpDebugLogsToConsole() {
        Log.d(TAG, "");
        Log.d(TAG, "*********************");
        Log.d(TAG, "Launcher debug logs: ");
        for (int i = 0; i < sDumpLogs.size(); i++) {
            Log.d(TAG, "  " + sDumpLogs.get(i));
        }
        Log.d(TAG, "*********************");
        Log.d(TAG, "");
    }

    private String getSettingStorageComponentName() {
        String productName = SystemProperties.get("ro.product.name","");
        if(productName.contains("tv")){
            return "com.android.tv.settings$device.StorageResetActivity";
        }else{
            return "com.android.settings.Settings$StorageSettingsActivity";
        }
    }

    private String getSettingPkgName() {
        String productName = SystemProperties.get("ro.product.name","");
        if(productName.contains("tv")){
            return ConstantsDef.TV_SETTING_PKG_NAME;
        }else{
            return ConstantsDef.SETTING_PKG_NAME;
        }
    }

    /**
     * New API added for Realtek Launcher.<br>
     * check self widget info and launch action.
     * @param pInfo
     */
    public void launchSelfWidgetMainActivity(AppWidgetProviderInfo pInfo){
    	
    	String label = pInfo.label;
    	DebugHelper.dump("widgetLabel:"+label);
    	
    	if(label.equals(appWidgetLabel)){
    		switchToAppCustomizedPage(ContentType.Applications);
    	}else if(label.equals(favoriteAppWidgetLabel)){
    		switchToAppCustomizedPage(ContentType.Favorite);
    	}else if(label.equals(videoAppWidgetLabel)){
    		switchToAppCustomizedPage(ContentType.Video);
    	}else if(label.equals(settingWidgetLabel)){
    		// launch platform setting apk
    		final PackageManager packageManager = getPackageManager();
    		Intent intent=packageManager.getLaunchIntentForPackage(/*ConstantsDef.SETTING_PKG_NAME*/ getSettingPkgName() );
    		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
    	}else if(label.equals(fileBrowserWidgetLabel)){
    		// launch platform media browser apk
    		final PackageManager packageManager = getPackageManager();
    		Intent intent=packageManager.getLaunchIntentForPackage(ConstantsDef.MEDIA_BROWSER_PKG_NAME);
    		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
    	}else if(label.equals(playMusicWidgetLabel)){
    	    // launch platform media browser apk
            final PackageManager packageManager = getPackageManager();
            Intent intent=packageManager.getLaunchIntentForPackage(ConstantsDef.ANDROID_MUSIC_APP_PKG_NAME);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
    	}else if(label.equals(widiWidgetLabel)){
    	 // launch Intel Widi apk
            final PackageManager packageManager = getPackageManager();
            Intent intent=packageManager.getLaunchIntentForPackage(ConstantsDef.RTK_MIRACAST_APP_PKG_NAME);
            if(intent==null){
                showToastMessage("Package: "+ConstantsDef.RTK_MIRACAST_APP_PKG_NAME+" not found");
            }else{
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // Add for Tandy:
                // TODO: do something to P2P=> Widi no need to disable p2p.
                Log.i(TAG, "startActivity 2");
                startActivity(intent);
            }
    	}else if(label.equals(wifiStatusWidgetLabel)){
            closeSourceSinkPopupDialog();
            // wifi (miracast) status widget
            showSourceSinkPopupDialog();
    	}else if(label.equals(storageWidgetLabel)){
    	    
            String productName = SystemProperties.get("ro.product.name","");
            if(productName.contains("tv")){
                // launch platform setting apk
                final PackageManager packageManager = getPackageManager();
                Intent intent=packageManager.getLaunchIntentForPackage(/*ConstantsDef.SETTING_PKG_NAME*/ getSettingPkgName() );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            }

    	    // launch setting storage page
    	    Intent i = new Intent();
    	    ComponentName comp = new ComponentName(getSettingPkgName(), getSettingStorageComponentName());
    	    i.setComponent(comp);
    	    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	    startActivity(i);
    	    
    	    /*
    	    if(mExtStorageList.size()<=0){
    	        showStorageUnmountDialog(S_TYPE_NO_STORAGE);
    	    }else{
    	        if(mMonitor.isScanning()){
    	            showStorageUnmountDialog(S_TYPE_DATABASE_SCANNING);
    	        }else{
    	            showStorageUnmountDialog(S_TYPE_UNMOUNT_STORAGE);
    	        }
    	    }
    	    */
    	}else if(label.equals(sourceWidgetLabel)){
    	    //showToastMessage("on click source widget");
            int childViewCount = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildCount();
            for (int i=0;i<childViewCount;i++) {
                View childView = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(i);
                if (childView instanceof LauncherDtvWidgetHostView) {
                    LauncherDtvWidgetHostView dtvView = (LauncherDtvWidgetHostView)childView;
                    dtvView.startActivity();
                    break;
                }
            }
    	}
    	// RTKTODO: complete other widgets
    }
    
    /*
    public void showStorageUnmountDialog(int type){
        // debug purpose, remove later.
        type = S_TYPE_UNMOUNT_STORAGE;
        
        switch(type){
        case S_TYPE_DATABASE_SCANNING:
            {
                Log.i(TAG,"showStorageUnmountDialog - database scanning");
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setTitle(R.string.rtk_storage_dialog_title);
                alertDialogBuilder.setMessage(R.string.rtk_storage_dialog_database_scanning_msg).setCancelable(false).setPositiveButton(
                        "OK", 
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                closeStorageUnmountDialog();
                            }
                });
                mStorageUnmountDialog = alertDialogBuilder.create();
                mStorageUnmountDialog.show();
            }
            break;
        case S_TYPE_NO_STORAGE:
            {
                Log.i(TAG,"showStorageUnmountDialog - no storage");
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setTitle(R.string.rtk_storage_dialog_title);
                alertDialogBuilder.setMessage(R.string.rtk_storage_dialog_no_ext_storage_msg).setCancelable(false).setPositiveButton(
                        "OK", 
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                closeStorageUnmountDialog();
                            }
                });
                mStorageUnmountDialog = alertDialogBuilder.create();
                mStorageUnmountDialog.show();
            }
            break;
        case S_TYPE_UNMOUNT_STORAGE:
            {
                Log.i(TAG,"showStorageUnmountDialog - unmount storage");
                
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
                alertDialogBuilder.setTitle("Eject storage:");
                alertDialogBuilder.setCancelable(false).setPositiveButton(
                        "OK", 
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // process users selection
                                processBatchUnmountCommands();
                                closeStorageUnmountDialog();
                            }
                }).setNegativeButton(
                        "Cancel",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                for(EjectDialogListAdaptor item:mEjectDialogList){
                                    item.mCheckbox=null;
                                    item=null;
                                }
                                mEjectDialogList.clear();
                                closeStorageUnmountDialog();
                            }
                        });
                
                mStorageUnmountDialog = alertDialogBuilder.create();
                
                LayoutInflater mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                View v = mInflater.inflate(R.layout.storage_unmount_dialog, null);
                //TextView messageTextView = (TextView) v.findViewById(R.id.message_view);
                //messageTextView.setText("Eject Storage");
                
                generateStorageInfoList();
                
                ViewGroup vg = (ViewGroup)v.findViewById(R.id.dialog_view_group);
                
                for(EjectDialogListAdaptor item:mEjectDialogList){
                    View itemView = mInflater.inflate(R.layout.storage_unmount_checkbox, null);
                    TextView itemTextView = (TextView)itemView.findViewById(R.id.check_box_text);
                    CheckBox itemCheckbox = (CheckBox)itemView.findViewById(R.id.check_box);
                    item.mCheckbox=itemCheckbox;
                    itemTextView.setText(item.mDisplayName);
                    vg.addView(itemView);
                }
                
                ((AlertDialog)mStorageUnmountDialog).setView(v);
                mStorageUnmountDialog.show();
            }   
            break;
        default:
            Log.e(TAG,"showStorageUnmountDialog - type error");
            break;
        }
    }
    */
    
    /*
    private void processBatchUnmountCommands(){
        for(EjectDialogListAdaptor item:mEjectDialogList){
            if(item.mCheckbox.isChecked()){
                Log.i(TAG,"save eject device:"+item.mMountPath);
                
            }else{
                Log.i(TAG,"don't touch device:"+item.mMountPath);
            }
        }
        
        // just test for now
        StorageManager storageManager = (StorageManager)getSystemService(Context.STORAGE_SERVICE);
        storageManager.unmountObb("/storage/sdb1", true, new OnObbStateChangeListener() {
            public void onObbStateChange (String path, int state){
                Log.i(TAG,"onObbStateChange path:"+path+" "+state);
            }
        });
    }
    */
    
    /*
    private void generateStorageInfoList(){
        mEjectDialogList.clear();
        // parse /proc/mount
        try {
            FileInputStream fstream = new FileInputStream("/proc/mounts");
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String strLine;
            int index = 0;
            
            while ((strLine = br.readLine()) != null){
                if(strLine.contains("/mnt/media_rw/")){
                    // Log.i(TAG,strLine);
                    // get device label
                    int i = strLine.indexOf("/mnt/media_rw/");
                    String subString = strLine.substring(i);
                    //Log.i(TAG,"sub string:"+subString);
                    i = subString.indexOf(" ");
                    String deviceString = subString.substring("/mnt/media_rw/".length(), i);
                    //Log.i(TAG,"device:"+deviceString+".");
                    if(deviceString.startsWith("sd")){
                        // is USB
                        mEjectDialogList.add(new EjectDialogListAdaptor(deviceString, "USB_"+index));
                        index++;
                    }else{
                        mEjectDialogList.add(new EjectDialogListAdaptor(deviceString, "SD Card"));
                    }
                }
            }
            in.close();
            
            // dump what I've got!
            for(EjectDialogListAdaptor item:mEjectDialogList){
                Log.i(TAG,""+item);
            }
            
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }
    */
    
    /*
    public void closeStorageUnmountDialog(){
        if(mStorageUnmountDialog!=null){
            mStorageUnmountDialog.dismiss();
            mStorageUnmountDialog=null;
        }
    }
    */
    
    public void closeSourceSinkPopupDialog(){
        if(mMiracastDialog!=null){
            mMiracastDialog.dismiss();
            mMiracastDialog=null;
        }
    }
    
    public void showSourceSinkPopupDialog(){
        
        mMiracastDialog = new Dialog(this,android.R.style.Theme_Holo_Dialog);
        
        LayoutInflater mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = mInflater.inflate(R.layout.miracast_source_sink_dialog, null, false);
        
        // find components within, load initial values, register click listener
        Switch sourceSwitch= (Switch)v.findViewById(R.id.source_switch);
        Switch sinkSwitch= (Switch)v.findViewById(R.id.sink_switch);
        
        sourceSwitch.setChecked(getMiracastSourceMode());
        sinkSwitch.setChecked(getMiracastSinkMode());
        
        sourceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //DebugHelper.dumpSQA("buttonView:"+buttonView+" isChecked:"+isChecked);
                setMiracastSourceMode(isChecked);
            }
        });
        
        sinkSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                //DebugHelper.dumpSQA("buttonView:"+buttonView+" isChecked:"+isChecked);
                setMiracastSinkMode(isChecked);
            }
        });
        
        mMiracastDialog.addContentView(v, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        mMiracastDialog.setTitle(getResources().getString(R.string.rtk_miracast_dialog_title));
        
        mMiracastDialog.show();
    }
    
    public void setMiracastSourceMode(boolean onOff){
        if(onOff)
            android.provider.Settings.System.putInt(getContentResolver(), ConstantsDef.KEY_MIRACAST_SOURCE_ONOFF, 1);
        else
            android.provider.Settings.System.putInt(getContentResolver(), ConstantsDef.KEY_MIRACAST_SOURCE_ONOFF, 0);
    }
    
    public void setMiracastSinkMode(boolean onOff){
        if(onOff)
            android.provider.Settings.System.putInt(getContentResolver(), ConstantsDef.KEY_MIRACAST_SINK_ONOFF, 1);
        else
            android.provider.Settings.System.putInt(getContentResolver(), ConstantsDef.KEY_MIRACAST_SINK_ONOFF, 0);
        
    }
    
    public boolean getMiracastSinkMode(){
        int enable;
        try {
            enable = android.provider.Settings.System.getInt(getContentResolver(), ConstantsDef.KEY_MIRACAST_SINK_ONOFF);
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        
        if(enable==1)
            return true;
        else
            return false;
    }
    
    public boolean getMiracastSourceMode(){
        int enable;
        try {
            enable = android.provider.Settings.System.getInt(getContentResolver(), ConstantsDef.KEY_MIRACAST_SOURCE_ONOFF);
        } catch (SettingNotFoundException e) {
            //e.printStackTrace();
            //DebugHelper.dumpSQA("getMiracastSourceMode -> SettingNotFoundException");
            return false;
        }
        
        if(enable==1)
            return true;
        else
            return false;
    }
    
    public int getActivateNetworkInterface(){
        NetworkInfo info = connManager.getActiveNetworkInfo();
        if(info==null)
            return ConstantsDef.NET_NONE;
        else{
            DebugHelper.dump("[NET INFO] "+info.getTypeName()+", "+info.getType());
            if(info.getType()==ConnectivityManager.TYPE_ETHERNET)
                return ConstantsDef.NET_ETH0;
            else if(info.getType()==ConnectivityManager.TYPE_WIFI)
                return ConstantsDef.NET_WIFI;
            else if(info.getType()==ConnectivityManager.TYPE_PPPOE)
                return ConstantsDef.NET_PPPOE;
            else
                return ConstantsDef.NET_NONE;
        }
    }
    
    /*
    public int getP2PSoftAP(int activateNetInterface) {
        if (activateNetInterface == ConstantsDef.NET_ETH0) {
            return ConstantsDef.P2P;
        } else if (activateNetInterface == ConstantsDef.NET_WIFI) {
            if (isWifiConnected()) {
                return ConstantsDef.P2P;
            } else {
                return ConstantsDef.SOFTAP;
            }
        } else {
            // This is ConstantsDef.NET_NONE case.
            // Although there is no activate Net interface, but P2P interface
            // may be existed.
            if (isP2PInterfaceExisted() == true) {
                return ConstantsDef.P2P;
            } else {
                return ConstantsDef.SOFTAP;
            }
        }
    }
    */

   public boolean isP2PInterfaceExisted(){
   	boolean bP2pIfExisted = false;
   	try {
		NetworkInterface networkInterface = null;
		
		networkInterface = NetworkInterface.getByName("p2p0");
		if( networkInterface == null)
		{
			DebugHelper.dumpSQA( "p2p0 interface is NOT existed...");
		}
		else
		{
			bP2pIfExisted = true;
			DebugHelper.dumpSQA( "p2p0 interface is existed...");
		}
		DebugHelper.dumpSQA( "networkInterface is " + networkInterface);
	} catch (Exception e) {
		DebugHelper.dumpSQA( " exception getByName: " + e);
	}
	return bP2pIfExisted;
   }
    
    public boolean isWifiConnected(){
        NetworkInfo info=connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return info.isConnected();
    }
    
    /**
     * API added for Realtek Launcher.<br>
     * when drag & drop, check if workspace should show "X Remove" label.
     * 
     * @param v selected view during drag&drop
     * @return selected view can be removed or not
     */
    public boolean isRemovableView(View v){
    	if(v instanceof LauncherAppWidgetHostView){
    		// RTK DTV widget is always un-removable.
    		if(v instanceof LauncherDtvWidgetHostView){
        		return false;
        	}
    		LauncherAppWidgetHostView lav=(LauncherAppWidgetHostView)v;
    		String label = lav.getAppWidgetInfo().label;
    		String packageName = lav.getAppWidgetInfo().provider.getPackageName();
    		if(packageName.equals(getPackageName())&&unRemovableWidgetLabelList!=null){
    			for(String l:unRemovableWidgetLabelList){
    				if(l.equals(label))
    					return false;
    			}
    		}
    	}
    	return true;
    }
    
    public boolean isRemovableApp(ApplicationInfo appInfo){
    	DebugHelper.dump("mLauncher check if is removable app:"+appInfo);
    	boolean bIsSystemApp = appInfo.isSystemApp();
    	if(bIsSystemApp)
    		return false;
    	else
    		return true;
    }
    
    /**
     * API added by Realtek.<br>
     * find owner apk of the widget, and launch it's main activity if exists.
     * @param appWidgetInfo
     */
    public void launchWidgetBelongPackageManiActivity(AppWidgetProviderInfo pInfo){
    	String packageName = pInfo.provider.getPackageName();
    	
    	if(packageName.equals(getPackageName())){
    		DebugHelper.dump("Is self widget, special handling");
    		launchSelfWidgetMainActivity(pInfo);
    		return;
    	}
    	
    	List<ResolveInfo> rst=AllAppsList.findActivitiesForPackage(this, packageName);
    	
    	if(rst!=null && rst.size()>0){
    		final PackageManager packageManager = getPackageManager();
			Intent intent=packageManager.getLaunchIntentForPackage(packageName);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	        startActivity(intent);
    	}
    }
    
    /**
     * API added for Realtek Launcher.<br>
     * The service function is to let child view added by option menu enter move mode after add is complete.
     */
    public void setWorkspaceFocusToNewAddedChildAndEnterMoveMode(){
    	DebugHelper.dump("[Launcher] setWorkspaceFocusToNewAddedChild");
    	if(mLatestAddedViewByOptionMenu!=null){
    		mLatestAddedViewByOptionMenu.requestFocus();
    		// trigger move command.
    		handleWorkspaceMoveCommand();
    	}
    }
    
    /**
     * New API added for Realtek Launcher.<br>
     * handle workspace option menu, move widget or shortcut command.
     */
    public void handleWorkspaceMoveCommand(){
    	View v = mWorkspace.findFocus();
    	
    	if(v==null){
    		DebugHelper.dump("handleWorkspaceMoveCommand no focus child");
    		return;
    	}
    	
    	mWorkspaceFocusChild=v;
    	
    	DebugHelper.dump("handleWorkspaceMoveCommand focus child:"+v);
    	CellLayout cl=null;
    	if (!(v instanceof CellLayout)) {
            cl = (CellLayout) v.getParent().getParent();
            cl.setTagChildSelfCellInfo(v,mWorkspace.mCurrentPage);
        }
    	
    	if(cl==null){
    		DebugHelper.dump("handleWorkspaceMoveCommand cl==null, break");
    		return;
    	}
    	
    	int cellPedding = getResources().getDimensionPixelSize(R.dimen.rtk_workspace_cell_gap);
    	
    	// RTKCOMMENT: when moving, consider gap between each cell
    	fakeDragStepWidth = cl.getCellWidth()+cellPedding;
    	fakeDragStepHeight = cl.getCellHeight()+cellPedding;
    	
    	DebugHelper.dump("Cell w:"+fakeDragStepWidth+" h:"+fakeDragStepHeight);
    	
    	bIsInFakeDragState=true;
    	DebugHelper.dump("handleWorkspaceMoveCommand focus child's CellLayout:"+cl);
    	
    	if(cl.getTag() instanceof CellInfo){
    		DebugHelper.dump("Launcher handleWorkspaceMoveCommand,always clear all focus");
    		((CellLayout)cl).getShortcutsAndWidgets().clearChildWidgetFocus();
    	}
    	
    	CellLayout.CellInfo cellInfo = (CellLayout.CellInfo) cl.getTag();
    	DebugHelper.dump("handleWorkspaceMoveCommand cellInfo:"+cellInfo);
    	
    	if(cellInfo!=null)
    		mWorkspace.startFakeDrag(cellInfo);
    }
    
    private boolean isFolder(View v){
    	if(v instanceof FolderIcon)
    		return true;
    	else
    		return false;
    }
    
    private boolean isShortcut(View v){
    	if(v instanceof BubbleTextView)
    		return true;
    	else
    		return false;
    }
    
    private boolean isWidget(View v){
    	if(v instanceof LauncherAppWidgetHostView)
    		return true;
    	else
    		return false;
    }
    
    public View getWorkspaceFocusView(){
    	return mWorkspace.findFocus();
    }
    
    public void clearWorkspaceFocusViewFrame(){
    	if(mFocusFrameView!=null){
            Log.d("RealtekLauncher2", "clearWorkspaceFocusViewFrame");
    		mFocusFrameView.remove();
    		mFocusFrameView=null;
    	}
    }
    
    /**
     * API added for realtek launcher.<br>
     * add {@link FocusView} to {@link DragLayer} to show focus frame of current focus view.<br>
     * 
     * @param focusBaseView focused view in workspace which need display focus frame
     */
    public void redrawWorkspaceFocusViewFrame(View focusBaseView){
    	
    	// check state
    	if(mState!=State.WORKSPACE){
    		try {
				throw new LauncherStateErrorException();
			} catch (LauncherStateErrorException e) {
				// print error trace for debug.
				// then do nothing and just return.
				e.printStackTrace();
				return;
			}
    	}

    	clearWorkspaceFocusViewFrame();
    	
    	DebugHelper.dump("------------> redrawWorkspaceFocusViewFrame");
    	
    	if(getDragLayer().hasResizeFrames()){
    	    DebugHelper.dumpSQA("onDrawResizeFrame, resize frame detected, do nothing");
    	    return;
    	}
    	
    	// BUG_FIX: 42780
    	if(focusBaseView==null){
    	    showToastMessage("Reassign focus view in workspace");
    	    // assign a focus view in workspace, should not reach here in normal case.
    	    View v = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(0);
            if(v==null){
                Log.e(TAG, "Launcher getCurrentShortcutAndWidgetContainer failed");
                return;
            }

    	    v.requestFocus();
    	    focusBaseView=v;
    	}
    	
    	if(DebugHelper.ENABLE_DEBUG_TRACE){
    	    try{
    	        throw new Exception();
    	    }catch(Exception e){
    	        Log.i(TAG,"dump back trace when refreshing WhiteFocusFrame (debug mode)");
    	        e.printStackTrace();
    	    }
    	}
    	
    	mFocusFrameView = new FocusView(this, focusBaseView);
    	mFocusFrameView.show();
    }
    
    /**
     * API added for Realtek Launcher.<br>
     * handle workspace option menu, remove shortcut or widget command.
     */
    public void handleWorkspaceRemoveCommand(View v){
    	
        //View v = mWorkspace.findFocus();
    	
    	if(v==null){
    		DebugHelper.dump("handleWorkspaceMoveCommand no focus child");
    		return;
    	}
    	
    	DebugHelper.dump("Focus view in workspace:"+v);
    	
    	ItemInfo item = (ItemInfo)v.getTag();
    	
        if (isShortcut(v)) {
        	// case app in workspace or folder
            LauncherModel.deleteItemFromDatabase(this, item);
        } else if (isFolder(v)) {
        	// case folder
            // Remove the folder from the workspace and delete the contents from launcher model
            FolderInfo folderInfo = (FolderInfo) item;
            removeFolder(folderInfo);
            LauncherModel.deleteFolderContentsFromDatabase(this, folderInfo);
        } else if (isWidget(v)) {
        	// case widget
            // Remove the widget from the workspace
            removeAppWidget((LauncherAppWidgetInfo) item);
            LauncherModel.deleteItemFromDatabase(this, item);

            final LauncherAppWidgetInfo launcherAppWidgetInfo = (LauncherAppWidgetInfo) item;
            final LauncherAppWidgetHost appWidgetHost = getAppWidgetHost();
            if (appWidgetHost != null) {
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new Thread("deleteAppWidgetId") {
                    public void run() {
                        appWidgetHost.deleteAppWidgetId(launcherAppWidgetInfo.appWidgetId);
                    }
                }.start();
            }
        }
        
        // request CellLayout to remove deleted view
        CellLayout cl = (CellLayout) v.getParent().getParent();
        cl.removeView(v);
        clearWorkspaceFocusViewFrame();
        
        // BUG_FIX: 42045 [case remove]
        v = mWorkspace.findFocus();
        if(v==null){
            v=mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(0);
        }
        
        DebugHelper.dump2("onRemoveWorkspaceChildComplete new focus view:"+v);
        
        if(v!=null){
            v.requestFocus();
            redrawWorkspaceFocusViewFrame(v);
        }
    }
    
    /**
     * 
     * @param x
     * @param y
     * @return
     */
    public LauncherAppWidgetHostView findWidgetViewByXY(int x, int y){
    	DebugHelper.dump("findWidgetViewByXY x="+x+" y="+y);
    	
    	int childNum=mWorkspace.getCurrentShortcutAndWidgetContainer().getChildCount();
    	for(int i=0;i<childNum;i++){
    		View v = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(i);
    		if(v instanceof LauncherAppWidgetHostView){
    			LauncherAppWidgetHostView lav = (LauncherAppWidgetHostView)v;
    			if(lav.isInMyRange(x, y))
    				return lav;
    		}
    	}
    	return null;
    }
    
    public AppsCustomizeTabHost getTabHost(){
    	//return (AppsCustomizeTabHost) findViewById(R.id.apps_customize_pane);
    	return mAppsCustomizeTabHost;
    }
    
    /**
     * get current tab tag
     * @return tag of current tab
     */
    public String getCurrentDrawerTabTag(){
    	String tag = mAppsCustomizeTabHost.getCurrentTabTag();
    	DebugHelper.dump("current tab tag: "+tag);
    	return tag;
    }
    
    public void dumpmOccupiedInfo(){
    	DebugHelper.dump("dump occupied cell info");
    }
    
    public String getAppPageCurrentTabTag(){
        String tag = mAppsCustomizeTabHost.getCurrentTabTag();
        return tag;
    }
    
    public void sendRtkAdvMessageToHandler(String obj, int delay){
        Message message;
        message = mHandler.obtainMessage(RTK_ADVANCE_MSG,obj);
        mHandler.sendMessageDelayed(message, delay);
    }

    public void redrawFocusFrameDelay(int delay){
        sendRtkAdvMessageToHandler(DRAW_FOCUS_AFTER_FAKE_DRAG_CANCELED,delay);
    }
    
    public void setFocusOnBindWorkspaceComplete(){
        if(mState==State.WORKSPACE){
            View v = mWorkspace.getCurrentShortcutAndWidgetContainer().getChildAt(0);
            if(v!=null){
                v.requestFocus();
                v=mWorkspace.getCurrentShortcutAndWidgetContainer().getFocusedChild();
                DebugHelper.dumpSQA("setFocusOnBindWorkspaceComplete:"+v);
                redrawWorkspaceFocusViewFrame(v);
            }
        }
    }

    public boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
           String[] children = dir.list();
           for (int i = 0; i < children.length; i++) {
              boolean success = deleteDir(new File(dir, children[i]));
              if (!success) {
                 return false;
              }
           }
        }

        // The directory is now empty so delete it
        return dir.delete();
     }

    public void clearLauncherCache(){
        try {
            File dir = getCacheDir();
            if (dir != null && dir.isDirectory()) {
               boolean deleteRst = deleteDir(dir);
               DebugHelper.dumpSQA("clearLauncherCache:"+deleteRst);
            }else{
                DebugHelper.dumpSQA("clearLauncherCache - do nothing");
            }
         } catch (Exception e) {
            // TODO: handle exception
         }
    }

    /**
     * check if system foreground activity is Launcher.
     * @return result
     */
    public synchronized boolean launcherIsForeground(){
        return bLauncherIsForground;
    }
    
    /**
     * Debug dump function for Launcher, dump launcher current state
     */
    public void launcherDumpSys(){
        Log.i(TAG,"::::::: Current state dump :::::::");
        Log.i(TAG,"Launcher state:"+mState);
        if(mState==State.WORKSPACE){
            Log.i(TAG,"Workspace current focus:"+mWorkspace.getCurrentShortcutAndWidgetContainer().getFocusedChild());
            if(mFocusFrameView!=null)
                Log.i(TAG,"Workspace white focus frame focus:"+mFocusFrameView.mBaseView);
        }else{
            Log.i(TAG,"dump nothing in APP state");
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG,"Configuration Change :"+newConfig);
        Log.d(TAG,"Configuration Change keyboard: "+newConfig.keyboard);
        Log.d(TAG,"Configuration Change keyboardHidden: "+newConfig.keyboardHidden);

    }
}

interface LauncherTransitionable {
    View getContent();
    void onLauncherTransitionPrepare(Launcher l, boolean animated, boolean toWorkspace);
    void onLauncherTransitionStart(Launcher l, boolean animated, boolean toWorkspace);
    void onLauncherTransitionStep(Launcher l, float t);
    void onLauncherTransitionEnd(Launcher l, boolean animated, boolean toWorkspace);
}
