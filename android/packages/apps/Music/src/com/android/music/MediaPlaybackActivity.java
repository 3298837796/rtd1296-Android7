/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.music;

import com.android.music.MusicUtils.ServiceToken;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.audiofx.AudioEffect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v4.content.ContextCompat;
import android.support.v4.app.ActivityCompat;
import android.text.Layout;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import java.io.FileReader;
import java.io.BufferedReader;
import java.util.ArrayList;
import android.content.ContentValues;

public class MediaPlaybackActivity extends Activity implements MusicUtils.Defs,
    View.OnTouchListener, View.OnLongClickListener
{
    private static final int USE_AS_RINGTONE = CHILD_MENU_BASE;
    private static final String TAG = "MediaPlaybackActivity";
    private static final int PLAYBACK_SINGLE = 0;
    private static final int PLAYBACK_ID_PLAYLIST = 1;
    private static final int PLAYBACK_URL_PLAYLIST = 2;

    private boolean mSeeking = false;
    private boolean mDeviceHasDpad;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private IMediaPlaybackService mService = null;
    private RepeatingImageButton mPrevButton;
    private ImageButton mPauseButton;
    private RepeatingImageButton mNextButton;
    private ImageButton mRepeatButton;
    private ImageButton mShuffleButton;
    private ImageButton mQueueButton;
    private Worker mAlbumArtWorker;
    private AlbumArtHandler mAlbumArtHandler;
    private Toast mToast;
    private int mTouchSlop;
    private ServiceToken mToken;
    private boolean mFinishOnCompletion;
    private boolean mBroadcastStatus;
    private boolean mBackgroundPlay = false;
    private String mSourceFrom = null;
    private String mMediaConfig = null;
    private boolean mUseRTMediaPlayer = false;
    private final int READ_WRITE_CONTACTS = 0;

    private int mStartPlayType = PLAYBACK_SINGLE;

    public static final String KEY_SOURCE_FROM = "SourceFrom";
    public static final String KEY_MEDIA_CONFIG = "RTMediaPlayer.Config";
    private static final String MEDIA_BROWSER_USE_RT_MEDIA_PLAYER = "MEDIA_BROWSER_USE_RT_MEDIA_PLAYER";

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mBackgroundPlay = intent.getBooleanExtra("backgroundPlay", false);
            Log.d(TAG, "mIntentReceiver: mBackgroundPlay = " + mBackgroundPlay);
        }    
    };

    public MediaPlaybackActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle)
    {
        Log.d(TAG, "onCreate");
        super.onCreate(icicle);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mAlbumArtWorker = new Worker("album art worker");
        mAlbumArtHandler = new AlbumArtHandler(mAlbumArtWorker.getLooper());

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.audio_player);

        mCurrentTime = (TextView) findViewById(R.id.currenttime);
        mTotalTime = (TextView) findViewById(R.id.totaltime);
        mProgress = (ProgressBar) findViewById(android.R.id.progress);
        mAlbum = (ImageView) findViewById(R.id.album);
        mArtistName = (TextView) findViewById(R.id.artistname);
        mAlbumName = (TextView) findViewById(R.id.albumname);
        mTrackName = (TextView) findViewById(R.id.trackname);

        View v = (View)mArtistName.getParent(); 
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);

        v = (View)mAlbumName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);

        v = (View)mTrackName.getParent();
        v.setOnTouchListener(this);
        v.setOnLongClickListener(this);
        
        mPrevButton = (RepeatingImageButton) findViewById(R.id.prev);
        mPrevButton.setOnClickListener(mPrevListener);
        mPrevButton.setRepeatListener(mRewListener, 260);
        mPauseButton = (ImageButton) findViewById(R.id.pause);
        mPauseButton.requestFocus();
        mPauseButton.setOnClickListener(mPauseListener);
        mNextButton = (RepeatingImageButton) findViewById(R.id.next);
        mNextButton.setOnClickListener(mNextListener);
        mNextButton.setRepeatListener(mFfwdListener, 260);
        seekmethod = 1;

        mDeviceHasDpad = (getResources().getConfiguration().navigation ==
            Configuration.NAVIGATION_DPAD);
        
        mQueueButton = (ImageButton) findViewById(R.id.curplaylist);
        mQueueButton.setOnClickListener(mQueueListener);
        mShuffleButton = ((ImageButton) findViewById(R.id.shuffle));
        mShuffleButton.setOnClickListener(mShuffleListener);
        mRepeatButton = ((ImageButton) findViewById(R.id.repeat));
        mRepeatButton.setOnClickListener(mRepeatListener);
        
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);

        Intent intent = getIntent();
        mFinishOnCompletion = intent.getBooleanExtra(MediaStore.EXTRA_FINISH_ON_COMPLETION, true);
        mBroadcastStatus= intent.getBooleanExtra(
                "android.intent.playback.broadcast.status", false);
        mSourceFrom = intent.getStringExtra(KEY_SOURCE_FROM);
        mMediaConfig = intent.getStringExtra(KEY_MEDIA_CONFIG);
        mUseRTMediaPlayer = intent.getBooleanExtra(MEDIA_BROWSER_USE_RT_MEDIA_PLAYER, false);
        mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        Log.d(TAG, "onCreate - mFinishOnCompletion = " + mFinishOnCompletion);
        Log.d(TAG, "onCreate - mBroadcastStatus = " + mBroadcastStatus);
        Log.d(TAG, "onCreate - mSourceFrom = " + mSourceFrom);
        Log.d(TAG, "onCreate - mMediaTypeInfo = " + mMediaConfig);
        Log.d(TAG, "onCreate - mUseRTMediaPlayer = " + mUseRTMediaPlayer);
    }
    
    int mInitialX = -1;
    int mLastX = -1;
    int mTextWidth = 0;
    int mViewWidth = 0;
    boolean mDraggingLabel = false;
    
    TextView textViewForContainer(View v) {
        View vv = v.findViewById(R.id.artistname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.albumname);
        if (vv != null) return (TextView) vv;
        vv = v.findViewById(R.id.trackname);
        if (vv != null) return (TextView) vv;
        return null;
    }
    
    public boolean onTouch(View v, MotionEvent event) {
        Log.d(TAG, "onTouch");
        int action = event.getAction();
        TextView tv = textViewForContainer(v);
        if (tv == null) {
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            v.setBackgroundColor(0xff606060);
            mInitialX = mLastX = (int) event.getX();
            mDraggingLabel = false;
        } else if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_CANCEL) {
            v.setBackgroundColor(0);
            if (mDraggingLabel) {
                Message msg = mLabelScroller.obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(msg, 1000);
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (mDraggingLabel) {
                int scrollx = tv.getScrollX();
                int x = (int) event.getX();
                int delta = mLastX - x;
                if (delta != 0) {
                    mLastX = x;
                    scrollx += delta;
                    if (scrollx > mTextWidth) {
                        // scrolled the text completely off the view to the left
                        scrollx -= mTextWidth;
                        scrollx -= mViewWidth;
                    }
                    if (scrollx < -mViewWidth) {
                        // scrolled the text completely off the view to the right
                        scrollx += mViewWidth;
                        scrollx += mTextWidth;
                    }
                    tv.scrollTo(scrollx, 0);
                }
                return true;
            }
            int delta = mInitialX - (int) event.getX();
            if (Math.abs(delta) > mTouchSlop) {
                // start moving
                mLabelScroller.removeMessages(0, tv);
                
                // Only turn ellipsizing off when it's not already off, because it
                // causes the scroll position to be reset to 0.
                if (tv.getEllipsize() != null) {
                    tv.setEllipsize(null);
                }
                Layout ll = tv.getLayout();
                // layout might be null if the text just changed, or ellipsizing
                // was just turned off
                if (ll == null) {
                    return false;
                }
                // get the non-ellipsized line width, to determine whether scrolling
                // should even be allowed
                mTextWidth = (int) tv.getLayout().getLineWidth(0);
                mViewWidth = tv.getWidth();
                if (mViewWidth > mTextWidth) {
                    tv.setEllipsize(TruncateAt.END);
                    v.cancelLongPress();
                    return false;
                }
                mDraggingLabel = true;
                tv.setHorizontalFadingEdgeEnabled(true);
                v.cancelLongPress();
                return true;
            }
        }
        return false; 
    }

    Handler mLabelScroller = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            TextView tv = (TextView) msg.obj;
            int x = tv.getScrollX();
            x = x * 3 / 4;
            tv.scrollTo(x, 0);
            if (x == 0) {
                tv.setEllipsize(TruncateAt.END);
            } else {
                Message newmsg = obtainMessage(0, tv);
                mLabelScroller.sendMessageDelayed(newmsg, 15);
            }
        }
    };
    
    public boolean onLongClick(View view) {
        Log.d(TAG, "onLongClick");
        CharSequence title = null;
        String mime = null;
        String query = null;
        String artist;
        String album;
        String song;
        long audioid;
        
        try {
            artist = mService.getArtistName();
            album = mService.getAlbumName();
            song = mService.getTrackName();
            audioid = mService.getAudioId();
        } catch (RemoteException ex) {
            return true;
        } catch (NullPointerException ex) {
            // we might not actually have the service yet
            return true;
        }

        if (MediaStore.UNKNOWN_STRING.equals(album) &&
                MediaStore.UNKNOWN_STRING.equals(artist) &&
                song != null &&
                song.startsWith("recording")) {
            // not music
            return false;
        }

        if (audioid < 0) {
            return false;
        }

        Cursor c = MusicUtils.query(this,
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, audioid),
                new String[] {MediaStore.Audio.Media.IS_MUSIC}, null, null, null);
        boolean ismusic = true;
        if (c != null) {
            if (c.moveToFirst()) {
                ismusic = c.getInt(0) != 0;
            }
            c.close();
        }
        if (!ismusic) {
            return false;
        }

        boolean knownartist =
            (artist != null) && !MediaStore.UNKNOWN_STRING.equals(artist);

        boolean knownalbum =
            (album != null) && !MediaStore.UNKNOWN_STRING.equals(album);
        
        if (knownartist && view.equals(mArtistName.getParent())) {
            title = artist;
            query = artist;
            mime = MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE;
        } else if (knownalbum && view.equals(mAlbumName.getParent())) {
            title = album;
            if (knownartist) {
                query = artist + " " + album;
            } else {
                query = album;
            }
            mime = MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE;
        } else if (view.equals(mTrackName.getParent()) || !knownartist || !knownalbum) {
            if ((song == null) || MediaStore.UNKNOWN_STRING.equals(song)) {
                // A popup of the form "Search for null/'' using ..." is pretty
                // unhelpful, plus, we won't find any way to buy it anyway.
                return true;
            }

            title = song;
            if (knownartist) {
                query = artist + " " + song;
            } else {
                query = song;
            }
            mime = "audio/*"; // the specific type doesn't matter, so don't bother retrieving it
        } else {
            throw new RuntimeException("shouldn't be here");
        }
        title = getString(R.string.mediasearch, title);

        Intent i = new Intent();
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.setAction(MediaStore.INTENT_ACTION_MEDIA_SEARCH);
        i.putExtra(SearchManager.QUERY, query);
        if(knownartist) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ARTIST, artist);
        }
        if(knownalbum) {
            i.putExtra(MediaStore.EXTRA_MEDIA_ALBUM, album);
        }
        i.putExtra(MediaStore.EXTRA_MEDIA_TITLE, song);
        i.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, mime);

        startActivity(Intent.createChooser(i, title));
        return true;
    }

    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            Log.d(TAG, "onStartTrackingTouch");
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            Log.d(TAG, "onProgressChanged");
            if (!fromuser || (mService == null)) return;
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;
                try {
                    mService.seek(mPosOverride);
                } catch (RemoteException ex) {
                }

                // trackball event, allow progress updates
                if (!mFromTouch) {
                    refreshNow();
                    mPosOverride = -1;
                }
            }
        }
        public void onStopTrackingTouch(SeekBar bar) {
            Log.d(TAG, "onStopTrackingTouch");
            mPosOverride = -1;
            mFromTouch = false;
        }
    };
    
    private View.OnClickListener mQueueListener = new View.OnClickListener() {
        public void onClick(View v) {
            try {
                if (mBroadcastStatus == false) {
                    if (mService.getIdQueue() != null || mService.getUrlQueue() != null) {
                         startActivity(
                            new Intent(Intent.ACTION_EDIT)
                            .setDataAndType(Uri.EMPTY, "vnd.android.cursor.dir/track")
                            .putExtra("playlist", "nowplaying")
                            .putExtra("startType", mStartPlayType)
                        );
                    }
                }
            }
            catch (RemoteException ex) {
                Log.e(TAG, "exception", ex);
            }
        }
    };
    
    private View.OnClickListener mShuffleListener = new View.OnClickListener() {
        public void onClick(View v) {
            try {
               if ( mService.getIdQueue() != null || mService.getUrlQueue() != null )
                    toggleShuffle();
            }
            catch (RemoteException ex) {
                Log.e(TAG, "exception", ex);
            }
        }
    };

    private View.OnClickListener mRepeatListener = new View.OnClickListener() {
        public void onClick(View v) {
            try {
                if ( mService.getIdQueue() != null || mService.getUrlQueue() != null )
                    cycleRepeat();
            }
            catch (RemoteException ex) {
                Log.e(TAG, "exception", ex);
            }
            
        }
    };

    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            doPauseResume();
        }
    };

    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mService == null) return;
            try {
                if (mBroadcastStatus == false) {
                    if (mService.position() < 2000) {
                        mService.prev();
                    } else if (mService.duration() <= 0) {
                        mService.prev();
                    } else {
                        mService.seek(0);
                        mService.play();
                    }
                }
            } catch (RemoteException ex) {
            }
        }
    };

    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mService == null) return;
            try {
                if (mBroadcastStatus == false) {
                    mService.next();
                }
            } catch (RemoteException ex) {
            }
        }
    };

    private RepeatingImageButton.RepeatListener mRewListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            scanBackward(repcnt, howlong);
        }
    };
    
    private RepeatingImageButton.RepeatListener mFfwdListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            scanForward(repcnt, howlong);
        }
    };
   
    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        unregisterReceiver(mStatusListener);
        unregisterReceiver(mIntentReceiver);
        if (mBroadcastStatus && mService != null && mBackgroundPlay == false)
        {   try {
                mService.stop();
            } catch (Exception ex) {
                Log.d("MediaPlaybackActivity", "couldn't stop playback: ", ex);
            }
        }
        super.onPause();
    }
   
    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
        paused = true;
        mHandler.removeMessages(REFRESH);
        /*
        // Reset source from and broadcast status
        try {
            String currentSourceFrom = mService.getSourceFromString();
            if (mBackgroundPlay == false &&
                ((mBroadcastStatus == false) || (mBroadcastStatus == true && mSourceFrom.equals(currentSourceFrom)))) {
                mService.setSourceFromString(null);
                mService.setBroadcastStatus(false);
            }
        } catch (Exception ex) {
            Log.e(TAG, "exception", ex);
        }
        */
        MusicUtils.unbindFromService(mToken);
        mService = null;
        super.onStop();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        paused = false;
        mBackgroundPlay = false;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "onStart - Call requestPermissions");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, READ_WRITE_CONTACTS);
        }
        else {
            mToken = MusicUtils.bindToService(this, osc);
            if (mToken == null) {
                // something went wrong
                mHandler.sendEmptyMessage(QUIT);
            }
        }

        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent");
        setIntent(intent);
    }
    
    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.REPEATSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.STATUS_TRACK_END);
        f.addAction(MediaPlaybackService.STATUS_LIST_END);
        f.addAction(MediaPlaybackService.PREPARE_COMPLETED);
        registerReceiver(mStatusListener, new IntentFilter(f));

        IntentFilter propertyFilter = new IntentFilter("com.android.music.setProperty");
        registerReceiver(mIntentReceiver, propertyFilter);

        updateTrackInfo();
        setPauseButtonImage();
    }
    
    @Override
    public void onDestroy()
    {
        Log.d(TAG, "onDestroy");
        mAlbumArtWorker.quit();
        super.onDestroy();
        //System.out.println("***************** playback activity onDestroy\n");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        Log.d(TAG, "onRequestPermissionsResult");
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case READ_WRITE_CONTACTS:
                if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mToken = MusicUtils.bindToService(this, osc);
                    if (mToken == null) {
                        // something went wrong
                        mHandler.sendEmptyMessage(QUIT);
                    }
                    updateTrackInfo();
                    long next = refreshNow();
                    queueNextRefresh(next);
                } else {
                    finish();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Configuration Change : " + newConfig);
        Log.d(TAG, "Configuration Change keyboard : " + newConfig.keyboard);
        Log.d(TAG, "Configuration Change keyboardHidden : " + newConfig.keyboardHidden);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        super.onCreateOptionsMenu(menu);
        // Don't show the menu items if we got launched by path/filedescriptor, or
        // if we're in one shot mode. In most cases, these menu items are not
        // useful in those modes, so for consistency we never show them in these
        // modes, instead of tailoring them to the specific file being played.
        if (MusicUtils.getCurrentAudioId() >= 0) {
            menu.add(0, GOTO_START, 0, R.string.goto_start).setIcon(R.drawable.ic_menu_music_library);
            menu.add(0, PARTY_SHUFFLE, 0, R.string.party_shuffle); // icon will be set in onPrepareOptionsMenu()
            SubMenu sub = menu.addSubMenu(0, ADD_TO_PLAYLIST, 0,
                    R.string.add_to_playlist).setIcon(android.R.drawable.ic_menu_add);
            // these next two are in a separate group, so they can be shown/hidden as needed
            // based on the keyguard state
            /*
            menu.add(1, USE_AS_RINGTONE, 0, R.string.ringtone_menu_short)
                    .setIcon(R.drawable.ic_menu_set_as_ringtone);
            */
            menu.add(1, DELETE_ITEM, 0, R.string.delete_item)
                    .setIcon(R.drawable.ic_menu_delete);

            Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
            if (getPackageManager().resolveActivity(i, 0) != null) {
                menu.add(0, EFFECTS_PANEL, 0, R.string.effectspanel).setIcon(R.drawable.ic_menu_eq);
            }

            return true;
        }
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        Log.d(TAG, "onPrepareOptionsMenu");
        if (mService == null) return false;
        MenuItem item = menu.findItem(PARTY_SHUFFLE);
        if (item != null) {
            int shuffle = MusicUtils.getCurrentShuffleMode();
            if (shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
                item.setIcon(R.drawable.ic_menu_party_shuffle);
                item.setTitle(R.string.party_shuffle_off);
            } else {
                item.setIcon(R.drawable.ic_menu_party_shuffle);
                item.setTitle(R.string.party_shuffle);
            }
        }

        item = menu.findItem(ADD_TO_PLAYLIST);
        if (item != null) {
            SubMenu sub = item.getSubMenu();
            MusicUtils.makePlaylistMenu(this, sub);
        }

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        menu.setGroupVisible(1, !km.inKeyguardRestrictedInputMode());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.d(TAG, "onOptionsItemSelected");
        Log.d(TAG, "item.getItemId() = " + item.getItemId());
        Intent intent;
        try {
            switch (item.getItemId()) {
                case GOTO_START:
                    intent = new Intent();
                    intent.setClass(this, MusicBrowserActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                    break;
                case USE_AS_RINGTONE: {
                    // Set the system setting to make this the current ringtone
                    if (mService != null) {
                        MusicUtils.setRingtone(this, mService.getAudioId());
                    }
                    return true;
                }
                case PARTY_SHUFFLE:
                    MusicUtils.togglePartyShuffle();
                    setShuffleButtonImage();
                    break;
                    
                case NEW_PLAYLIST: {
                    intent = new Intent();
                    intent.setClass(this, CreatePlaylist.class);
                    startActivityForResult(intent, NEW_PLAYLIST);
                    return true;
                }

                case PLAYLIST_SELECTED: {
                    long [] list = new long[1];
                    list[0] = MusicUtils.getCurrentAudioId();
                    long playlist = item.getIntent().getLongExtra("playlist", 0);
                    MusicUtils.addToPlaylist(this, list, playlist);
                    return true;
                }
                
                case DELETE_ITEM: {
                    if (mService != null) {
                        long [] list = new long[1];
                        list[0] = MusicUtils.getCurrentAudioId();
                        Bundle b = new Bundle();
                        String f;
                        if (android.os.Environment.isExternalStorageRemovable()) {
                            f = getString(R.string.delete_song_desc, mService.getTrackName());
                        } else {
                            f = getString(R.string.delete_song_desc_nosdcard, mService.getTrackName());
                        }
                        b.putString("description", f);
                        b.putLongArray("items", list);
                        intent = new Intent();
                        intent.setClass(this, DeleteItems.class);
                        intent.putExtras(b);
                        startActivityForResult(intent, -1);
                    }
                    return true;
                }

                case EFFECTS_PANEL: {
                    Intent i = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                    i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mService.getAudioSessionId());
                    startActivityForResult(i, EFFECTS_PANEL);
                    return true;
                }
            }
        } catch (RemoteException ex) {
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case NEW_PLAYLIST:
                Uri uri = intent.getData();
                if (uri != null) {
                    long [] list = new long[1];
                    list[0] = MusicUtils.getCurrentAudioId();
                    int playlist = Integer.parseInt(uri.getLastPathSegment());
                    MusicUtils.addToPlaylist(this, list, playlist);
                }
                break;
        }
    }
    private final int keyboard[][] = {
        {
            KeyEvent.KEYCODE_Q,
            KeyEvent.KEYCODE_W,
            KeyEvent.KEYCODE_E,
            KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_T,
            KeyEvent.KEYCODE_Y,
            KeyEvent.KEYCODE_U,
            KeyEvent.KEYCODE_I,
            KeyEvent.KEYCODE_O,
            KeyEvent.KEYCODE_P,
        },
        {
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_F,
            KeyEvent.KEYCODE_G,
            KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_DEL,
        },
        {
            KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_COMMA,
            KeyEvent.KEYCODE_PERIOD,
            KeyEvent.KEYCODE_ENTER
        }

    };

    private int lastX;
    private int lastY;

    private boolean seekMethod1(int keyCode)
    {
        if (mService == null) return false;
        for(int x=0;x<10;x++) {
            for(int y=0;y<3;y++) {
                if(keyboard[y][x] == keyCode) {
                    int dir = 0;
                    // top row
                    if(x == lastX && y == lastY) dir = 0;
                    else if (y == 0 && lastY == 0 && x > lastX) dir = 1;
                    else if (y == 0 && lastY == 0 && x < lastX) dir = -1;
                    // bottom row
                    else if (y == 2 && lastY == 2 && x > lastX) dir = -1;
                    else if (y == 2 && lastY == 2 && x < lastX) dir = 1;
                    // moving up
                    else if (y < lastY && x <= 4) dir = 1; 
                    else if (y < lastY && x >= 5) dir = -1; 
                    // moving down
                    else if (y > lastY && x <= 4) dir = -1; 
                    else if (y > lastY && x >= 5) dir = 1; 
                    lastX = x;
                    lastY = y;
                    try {
                        mService.seek(mService.position() + dir * 5);
                    } catch (RemoteException ex) {
                    }
                    refreshNow();
                    return true;
                }
            }
        }
        lastX = -1;
        lastY = -1;
        return false;
    }

    private boolean seekMethod2(int keyCode)
    {
        if (mService == null) return false;
        for(int i=0;i<10;i++) {
            if(keyboard[0][i] == keyCode) {
                int seekpercentage = 100*i/10;
                try {
                    mService.seek(mService.duration() * seekpercentage / 100);
                } catch (RemoteException ex) {
                }
                refreshNow();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyUp");
        Log.d(TAG, "onKeyUp - keyCode = " + keyCode + "event = " + event);
        try {
            switch(keyCode)
            {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            if (mBroadcastStatus == false) {
                                mPauseButton.requestFocus();
                                if (mStartSeekPos < 1000) {
                                    mService.prev();
                                } else {
                                    mService.seek(0);
                                }
                            }
                        } else {
                            scanBackward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            if (mBroadcastStatus == false) {
                                mPauseButton.requestFocus();
                                mService.next();
                            }
                        } else {
                            scanForward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REPEAT:
                    if (mService.getIdQueue() != null || mService.getUrlQueue() != null) {
                        cycleRepeat();
                    }
                    return true;
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    finish();
                    break;
            }
        } catch (RemoteException ex) {
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean useDpadMusicControl() {
        if (mDeviceHasDpad && (mPrevButton.isFocused() ||
                mNextButton.isFocused() ||
                mPauseButton.isFocused())) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        Log.d(TAG, "onKeyDown");
        Log.d(TAG, "onKeyDown - keyCode = " + keyCode + "event = " + event);
        int direction = -1;
        int repcnt = event.getRepeatCount();

        if((seekmethod==0)?seekMethod1(keyCode):seekMethod2(keyCode))
            return true;

        switch(keyCode)
        {
/*
            // image scale
            case KeyEvent.KEYCODE_Q: av.adjustParams(-0.05, 0.0, 0.0, 0.0, 0.0,-1.0); break;
            case KeyEvent.KEYCODE_E: av.adjustParams( 0.05, 0.0, 0.0, 0.0, 0.0, 1.0); break;
            // image translate
            case KeyEvent.KEYCODE_W: av.adjustParams(    0.0, 0.0,-1.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_X: av.adjustParams(    0.0, 0.0, 1.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_A: av.adjustParams(    0.0,-1.0, 0.0, 0.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_D: av.adjustParams(    0.0, 1.0, 0.0, 0.0, 0.0, 0.0); break;
            // camera rotation
            case KeyEvent.KEYCODE_R: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 0.0,-1.0); break;
            case KeyEvent.KEYCODE_U: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 0.0, 1.0); break;
            // camera translate
            case KeyEvent.KEYCODE_Y: av.adjustParams(    0.0, 0.0, 0.0, 0.0,-1.0, 0.0); break;
            case KeyEvent.KEYCODE_N: av.adjustParams(    0.0, 0.0, 0.0, 0.0, 1.0, 0.0); break;
            case KeyEvent.KEYCODE_G: av.adjustParams(    0.0, 0.0, 0.0,-1.0, 0.0, 0.0); break;
            case KeyEvent.KEYCODE_J: av.adjustParams(    0.0, 0.0, 0.0, 1.0, 0.0, 0.0); break;

*/

            case KeyEvent.KEYCODE_SLASH:
                seekmethod = 1 - seekmethod;
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mPrevButton.hasFocus()) {
                    mPrevButton.requestFocus();
                }
                scanBackward(repcnt, event.getEventTime() - event.getDownTime());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!useDpadMusicControl()) {
                    break;
                }
                if (!mNextButton.hasFocus()) {
                    mNextButton.requestFocus();
                }
                scanForward(repcnt, event.getEventTime() - event.getDownTime());
                return true;

            case KeyEvent.KEYCODE_S:
                toggleShuffle();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_SPACE:
                doPauseResume();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private void scanBackward(int repcnt, long delta) {
        Log.d(TAG, "scanBackward");
        Log.d(TAG, "repcnt = " + repcnt + "delta = " + delta);
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos - delta;
                if (newpos < 0) {
                    // move to previous track
                    mService.prev();
                    long duration = mService.duration();
                    mStartSeekPos += duration;
                    newpos += duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }

    private void scanForward(int repcnt, long delta) {
        Log.d(TAG, "scanForward");
        Log.d(TAG, "repcnt = " + repcnt + "delta = " + delta);
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos + delta;
                long duration = mService.duration();
                if (newpos >= duration) {
                    // move to next track
                    mService.next();
                    mStartSeekPos -= duration; // is OK to go negative
                    newpos -= duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void doPauseResume() {
        Log.d(TAG, "doPauseResume");
        try {
            if(mService != null) {
                if (mService.isPlaying()) {
                    mService.pause();
                } else {
                    mService.play();
                }
                refreshNow();
                setPauseButtonImage();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void toggleShuffle() {
        Log.d(TAG, "toggleShuffle");
        if (mService == null) {
            Log.d(TAG, "toggleShuffle - mService is null");
            return;
        }
        if (mBroadcastStatus == true) {
            Log.d(TAG, "toggleShuffle - Ignore repeat command");
            return;
        }
        try {
            int shuffle = mService.getShuffleMode();
            if (shuffle == MediaPlaybackService.SHUFFLE_NONE) {
                mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NORMAL);
                if (mService.getRepeatMode() == MediaPlaybackService.REPEAT_CURRENT) {
                    mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                    setRepeatButtonImage();
                }
                showToast(R.string.shuffle_on_notif);
            } else if (shuffle == MediaPlaybackService.SHUFFLE_NORMAL ||
                    shuffle == MediaPlaybackService.SHUFFLE_AUTO) {
                mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                showToast(R.string.shuffle_off_notif);
            } else {
                Log.e("MediaPlaybackActivity", "Invalid shuffle mode: " + shuffle);
            }
            setShuffleButtonImage();
        } catch (RemoteException ex) {
        }
    }
    
    private void cycleRepeat() {
        Log.d(TAG, "cycleRepeat");
        if (mService == null) {
            Log.d(TAG, "cycleRepeat - mService is null");
            return;
        }
        if (mBroadcastStatus == true) {
            Log.d(TAG, "cycleRepeat - Ignore repeat command");
            return;
        }
        try {
            int mode = mService.getRepeatMode();
            Log.d(TAG, "cycleRepeat - mode = " + mode);
            if (mode == MediaPlaybackService.REPEAT_NONE) {
                Log.d(TAG, "cycleRepeat - none ==> all");
                mService.setRepeatMode(MediaPlaybackService.REPEAT_ALL);
                showToast(R.string.repeat_all_notif);
            } else if (mode == MediaPlaybackService.REPEAT_ALL) {
                Log.d(TAG, "cycleRepeat - all ==> one");
                mService.setRepeatMode(MediaPlaybackService.REPEAT_CURRENT);
                if (mService.getShuffleMode() != MediaPlaybackService.SHUFFLE_NONE) {
                    mService.setShuffleMode(MediaPlaybackService.SHUFFLE_NONE);
                    setShuffleButtonImage();
                }
                showToast(R.string.repeat_current_notif);
            } else {
                Log.d(TAG, "cycleRepeat - one ==> none");
                mService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
                showToast(R.string.repeat_off_notif);
            }
            setRepeatButtonImage();
        } catch (RemoteException ex) {
        }
        
    }
    
    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        mToast.setText(resid);
        mToast.show();
    }

    private void startPlayback() {
        Log.d(TAG, "startPlayback");
        if(mService == null)
            return;
        Intent intent = getIntent();
        String filename = "";
        Uri uri = intent.getData();
        Log.d(TAG, "startPlayback - uri = " + uri);
        long[] ids = null;
        String[] urls = null;
        String playlistPath = null;
        int position = 0;

        // Reset source from and broadcast status
        try {
            mService.setSourceFromString(null);
            mService.setBroadcastStatus(false);
            mService.setMediaConfigString(null);
        } catch (Exception ex) {
            Log.e(TAG, "exception", ex);
        }

        if (uri != null && uri.toString().length() > 0) {
            Log.d(TAG, "startPlayback - PLAYBACK_SINGLE");
            mStartPlayType = PLAYBACK_SINGLE;
            // If this is a file:// URI, just use the path directly instead
            // of going through the open-from-filedescriptor codepath.
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                filename = uri.getPath();
            } else {
                filename = uri.toString();
            }
            Log.d(TAG, "startPlayback - filename = " + filename);
            Log.d(TAG, "startPlayback - mSourceFrom = " + mSourceFrom);
            try {
                urls = new String[] { filename };
                mService.stop();
                mService.setSourceFromString(mSourceFrom);
                mService.setBroadcastStatus(mBroadcastStatus);
                mService.setMediaConfigString(mMediaConfig);
                mService.setUseRTMediaPlayer(mUseRTMediaPlayer);
                MusicUtils.playAll(this, urls, position);
                //mService.openFile(filename);
                //mService.play();
                mService.setRepeatMode(MediaPlaybackService.REPEAT_NONE);
                setIntent(new Intent());
            } catch (Exception ex) {
                Log.e("MediaPlaybackActivity", "couldn't start playback", ex);
            }
        }
        else if ((ids = intent.getLongArrayExtra("playlist-id")) != null )
        {
            Log.d(TAG, "startPlayback - PLAYBACK_ID_PLAYLIST");
            mStartPlayType = PLAYBACK_ID_PLAYLIST;
            try {
                position = intent.getIntExtra("position", 0);
                mService.stop();
                Log.d(TAG, "startPlayback - position = " + position);
                Log.d(TAG, "startPlayback - id = " + ids[position]);
                mService.setSourceFromString(mSourceFrom);
                mService.setBroadcastStatus(mBroadcastStatus);
                mService.setMediaConfigString(mMediaConfig);
                mService.setUseRTMediaPlayer(mUseRTMediaPlayer);
                MusicUtils.playAll(this, ids, position);
                intent.removeExtra("playlist-id");
            }
            catch (Exception ex) {
                Log.e("MediaPlaybackActivity", "couldn't start playback", ex);
            }
        }
        else if ((urls = intent.getStringArrayExtra("playlist-url")) != null )
        {
            Log.d(TAG, "startPlayback - PLAYBACK_URL_PLAYLIST");
            mStartPlayType = PLAYBACK_URL_PLAYLIST;            
            try {
                position = intent.getIntExtra("position", 0);
                mService.stop();
                Log.d(TAG, "startPlayback - position = " + position);
                Log.d(TAG, "startPlayback - url = " + urls[position]);
                Log.d(TAG, "startPlayback - urls = " + urls);
                Log.d(TAG, "startPlayback - urls.length = " + urls.length);
                mService.setSourceFromString(mSourceFrom);
                mService.setBroadcastStatus(mBroadcastStatus);
                mService.setMediaConfigString(mMediaConfig);
                mService.setUseRTMediaPlayer(mUseRTMediaPlayer);
                MusicUtils.playAll(this, urls, position);
                intent.removeExtra("playlist-url");
                intent.removeExtra("PlayListAddress");
            }
            catch (Exception ex) {
                Log.e("MediaPlaybackActivity", "couldn't start playback", ex);
            }
        }
        else if ((playlistPath = intent.getStringExtra("PlayListAddress")) != null )
        {
            Log.d(TAG, "startPlayback - PLAYBACK_URL_PLAYLIST from file");
            mStartPlayType = PLAYBACK_URL_PLAYLIST;
            String[] playlistArray = null;
            ArrayList<String> playlist = new ArrayList<String>();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(playlistPath));
                String filePath;
                while ((filePath = reader.readLine()) != null) {
                    playlist.add(filePath);
                    Log.d(TAG, "startPlayback - Add file path = " + filePath);
                }
                reader.close();

                try {
                    playlistArray = new String[playlist.size()];
                    playlist.toArray(playlistArray);
                    position = intent.getIntExtra("position", 0);
                    mService.stop();
                    mService.setUseRTMediaPlayer(mUseRTMediaPlayer);
                    Log.d(TAG, "startPlayback - position = " + position);
                    Log.d(TAG, "startPlayback - url = " + playlistArray[position]);
                    Log.d(TAG, "startPlayback - playlistArray = " + playlistArray);
                    Log.d(TAG, "startPlayback - playlistArray.length = " + playlistArray.length);
                    MusicUtils.playAll(this, playlistArray, position);
                    intent.removeExtra("playlist-url");
                    intent.removeExtra("PlayListAddress");
                }
                catch (Exception ex) {
                    Log.e("MediaPlaybackActivity", "couldn't start playback", ex);
                }
            }
            catch (Exception ex) {
                Log.e("MediaPlaybackActivity", "couldn't start playback", ex);
            }
        }

        /*
        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
        */
    }

    private ServiceConnection osc = new ServiceConnection() {
            public void onServiceConnected(ComponentName classname, IBinder obj) {
                Log.d(TAG, "onServiceConnected");
                mService = IMediaPlaybackService.Stub.asInterface(obj);

                startPlayback();
                //try {
                    // Assume something is playing when the service says it is,
                    // but also if the audio ID is valid but the service is paused.
                    //if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                    //        mService.getPath() != null) {
                        // something is playing now, we're done
                        mRepeatButton.setVisibility(View.VISIBLE);
                        mShuffleButton.setVisibility(View.VISIBLE);
                        mQueueButton.setVisibility(View.VISIBLE);
                        setRepeatButtonImage();
                        setShuffleButtonImage();
                        setPauseButtonImage();
                        return;
                    //}
                //} catch (RemoteException ex) {
                //}

                // Service is dead or not playing anything. If we got here as part
                // of a "play this file" Intent, exit. Otherwise go to the Music
                // app start screen.
                // Music app start screen is not available for URL playlist
                /*
                Log.d(TAG, "onServiceConnected - getIntent().getData() = " + getIntent().getData());
                if (getIntent().getData() == null && PLAYBACK_URL_PLAYLIST != mStartPlayType) {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setClass(MediaPlaybackActivity.this, MusicBrowserActivity.class);
                    startActivity(intent);
                }
                */
                //finish();
            }
            public void onServiceDisconnected(ComponentName classname) {
                Log.d(TAG, "onServiceDisconnected");
                mService = null;
            }
    };

    private void setRepeatButtonImage() {
        if (mService == null) return;
        try {
            switch (mService.getRepeatMode()) {
                case MediaPlaybackService.REPEAT_ALL:
                    mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_all_btn);
                    break;
                case MediaPlaybackService.REPEAT_CURRENT:
                    mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_once_btn);
                    break;
                default:
                    mRepeatButton.setImageResource(R.drawable.ic_mp_repeat_off_btn);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void setShuffleButtonImage() {
        if (mService == null) return;
        try {
            switch (mService.getShuffleMode()) {
                case MediaPlaybackService.SHUFFLE_NONE:
                    mShuffleButton.setImageResource(R.drawable.ic_mp_shuffle_off_btn);
                    break;
                case MediaPlaybackService.SHUFFLE_AUTO:
                    mShuffleButton.setImageResource(R.drawable.ic_mp_partyshuffle_on_btn);
                    break;
                default:
                    mShuffleButton.setImageResource(R.drawable.ic_mp_shuffle_on_btn);
                    break;
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void setPauseButtonImage() {
        try {
            if (mService != null && mService.isPlaying()) {
                mPauseButton.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                mPauseButton.setImageResource(android.R.drawable.ic_media_play);
            }
        } catch (RemoteException ex) {
        }
    }
    
    private ImageView mAlbum;
    private TextView mCurrentTime;
    private TextView mTotalTime;
    private TextView mArtistName;
    private TextView mAlbumName;
    private TextView mTrackName;
    private ProgressBar mProgress;
    private long mPosOverride = -1;
    private boolean mFromTouch = false;
    private long mDuration;
    private int seekmethod;
    private boolean paused;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;
    private static final int GET_ALBUM_ART = 3;
    private static final int ALBUM_ART_DECODED = 4;

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        Log.d(TAG, "refreshNow");
        if(mService == null)
            return 500;        
        try {
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            if ((pos >= 0) && (mDuration > 0)) {
                mCurrentTime.setText(MusicUtils.makeTimeString(this, pos / 1000));
                int progress = (int) (1000 * pos / mDuration);
                mProgress.setProgress(progress);
                
                if (mService.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                }
                else {
                    // blink the counter
                    int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    return 500;
                }
            } else {
                mCurrentTime.setText("--:--");
                mProgress.setProgress(0);
            }
            // calculate the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            long remaining = 1000 - (pos % 1000);
            //Log.d(TAG, "pos = " + pos);
            //Log.d(TAG, "mDuration = " + mDuration);
            
            // approximate how often we would need to refresh the slider to
            // move it smoothly
            int width = mProgress.getWidth();
            if (width == 0) width = 320;
            long smoothrefreshtime = mDuration / width;

            if (smoothrefreshtime > remaining) return remaining;
            if (smoothrefreshtime < 20) return 20;
            return smoothrefreshtime;
        } catch (RemoteException ex) {
        }
        return 500;
    }
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "mHandler.handleMessage");
            Log.d(TAG, "mHandler.handleMessage - msg.what = " + msg.what);
            switch (msg.what) {
                case ALBUM_ART_DECODED:
                    mAlbum.setImageBitmap((Bitmap)msg.obj);
                    mAlbum.getDrawable().setDither(true);
                    break;

                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;
                    
                case QUIT:
                    // This can be moved back to onCreate once the bug that prevents
                    // Dialogs from being started from onCreate/onResume is fixed.
                    new AlertDialog.Builder(MediaPlaybackActivity.this)
                            .setTitle(R.string.service_start_error_title)
                            .setMessage(R.string.service_start_error_msg)
                            .setPositiveButton(R.string.service_start_error_button,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            finish();
                                        }
                                    })
                            .setCancelable(false)
                            .show();
                    break;

                default:
                    break;
            }
        }
    };

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "mStatusListener.onReceive");
            String action = intent.getAction();
            if (action.equals(MediaPlaybackService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                setPauseButtonImage();
                queueNextRefresh(1);
            } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                setPauseButtonImage();
            }
            else if (action.equals(MediaPlaybackService.REPEATSTATE_CHANGED)) {
                try {
                    if (mService != null) {
                        int mode = mService.getRepeatMode();
                        if (mode == MediaPlaybackService.REPEAT_NONE)
                            showToast(R.string.repeat_off_notif);
                        else if (mode == MediaPlaybackService.REPEAT_ALL)
                            showToast(R.string.repeat_all_notif);
                        setRepeatButtonImage();
                        setShuffleButtonImage();
                    }
                } catch (RemoteException ex) {
                }
            }
            else if (action.equals(MediaPlaybackService.STATUS_TRACK_END))
            {
                Log.d(TAG, "PLAYBACK_SINGLE and Receive STATUS_TRACK_END");
                if (mFinishOnCompletion)
                {
                    finish();
                }
            }
            else if (action.equals(MediaPlaybackService.STATUS_LIST_END)) {
                Log.d(TAG, "PLAYBACK_LIST and Receive STATUS_LIST_END");
                if (mFinishOnCompletion)
                {
                    finish();
                }
            }
            else if (action.equals(MediaPlaybackService.PREPARE_COMPLETED)) {
                Log.d(TAG, "Receive PREPARE_COMPLETED");
                updateTrackInfo();
                long next = refreshNow();
                queueNextRefresh(next);

                try {
                    // Assume something is playing when the service says it is,
                    // but also if the audio ID is valid but the service is paused.
                    if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                            mService.getPath() != null) {
                        // something is playing now, we're done
                        mRepeatButton.setVisibility(View.VISIBLE);
                        mShuffleButton.setVisibility(View.VISIBLE);
                        mQueueButton.setVisibility(View.VISIBLE);
                        setRepeatButtonImage();
                        setShuffleButtonImage();
                        setPauseButtonImage();
                        return;
                    }
                } catch (RemoteException ex) {
                }
            }
        }
    };

    private static class AlbumSongIdWrapper {
        public long albumid;
        public long songid;
        AlbumSongIdWrapper(long aid, long sid) {
            albumid = aid;
            songid = sid;
        }
    }
    
    private void updateTrackInfo() {
        Log.d(TAG, "updateTrackInfo");
        if (mService == null) {
            return;
        }
        try {
            String path = mService.getPath();
            if (path == null) {
                finish();
                return;
            }
            
            long songid = mService.getAudioId(); 
            /*
            if (songid < 0 && path.toLowerCase().startsWith("http://")) {
                // Once we can get album art and meta data from MediaPlayer, we
                // can show that info again when streaming.
                ((View) mArtistName.getParent()).setVisibility(View.INVISIBLE);
                ((View) mAlbumName.getParent()).setVisibility(View.INVISIBLE);
                mAlbum.setVisibility(View.GONE);
                mTrackName.setText(path);
                mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
                mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(-1, -1)).sendToTarget();
            }
            else {*/
                ((View) mArtistName.getParent()).setVisibility(View.VISIBLE);
                ((View) mAlbumName.getParent()).setVisibility(View.VISIBLE);
                String artistName = mService.getArtistName();
                Log.d(TAG, "updateTrackInfo - artistName = " + artistName);
                if (artistName == null || MediaStore.UNKNOWN_STRING.equals(artistName)) {
                    artistName = getString(R.string.unknown_artist_name);
                }
                mArtistName.setText(artistName);
                String albumName = mService.getAlbumName();
                long albumid = mService.getAlbumId();
                Log.d(TAG, "updateTrackInfo - albumName = " + albumName);
                if (albumName == null || MediaStore.UNKNOWN_STRING.equals(albumName)) {
                    albumName = getString(R.string.unknown_album_name);
                    albumid = -1;
                }
                mAlbumName.setText(albumName);
                String trackName = mService.getTrackName(); 
                Log.d(TAG, "updateTrackInfo - trackName = " + trackName);
                if (trackName == null || MediaStore.UNKNOWN_STRING.equals(trackName)) {
                    trackName = mService.getPath();
                }
                mTrackName.setText(trackName);
                Log.d(TAG, "updateTrackInfo - albumid = " + albumid);
                Log.d(TAG, "updateTrackInfo - songid = " + songid);
                mAlbumArtHandler.removeMessages(GET_ALBUM_ART);
                mAlbumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(albumid, songid)).sendToTarget();
                mAlbum.setVisibility(View.VISIBLE);
            //}
            mDuration = mService.duration();
            mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));
        } catch (RemoteException ex) {
            finish();
        }
    }
    
    public class AlbumArtHandler extends Handler {
        private long mAlbumId = -1;
        
        public AlbumArtHandler(Looper looper) {
            super(looper);
        }
        @Override
        public void handleMessage(Message msg)
        {
            Log.d(TAG, "AlbumArtHandler.handleMessage");
            long albumid = ((AlbumSongIdWrapper) msg.obj).albumid;
            long songid = ((AlbumSongIdWrapper) msg.obj).songid;
            Log.d(TAG, "AlbumArtHandler.handleMessage - albumid = " + albumid);
            Log.d(TAG, "AlbumArtHandler.handleMessage - songid = " + songid);
            if (msg.what == GET_ALBUM_ART && (mAlbumId != albumid || albumid < 0)) {
                // while decoding the new image, show the default album art
                Message numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, null);
                mHandler.removeMessages(ALBUM_ART_DECODED);
                mHandler.sendMessageDelayed(numsg, 300);
                // Don't allow default artwork here, because we want to fall back to song-specific
                // album art if we can't find anything for the album.
                Bitmap bm = MusicUtils.getArtwork(MediaPlaybackActivity.this, songid, albumid, false);
                if (bm == null) {
                    bm = MusicUtils.getArtwork(MediaPlaybackActivity.this, songid, -1);
                    albumid = -1;
                }
                if (bm != null) {
                    numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, bm);
                    mHandler.removeMessages(ALBUM_ART_DECODED);
                    mHandler.sendMessage(numsg);
                }
                mAlbumId = albumid;
            }
        }
    }
    
    private static class Worker implements Runnable {
        private final Object mLock = new Object();
        private Looper mLooper;
        
        /**
         * Creates a worker thread with the given name. The thread
         * then runs a {@link android.os.Looper}.
         * @param name A name for the new thread
         */
        Worker(String name) {
            Thread t = new Thread(null, this, name);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
            synchronized (mLock) {
                while (mLooper == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }
        
        public Looper getLooper() {
            return mLooper;
        }
        
        public void run() {
            synchronized (mLock) {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mLock.notifyAll();
            }
            Looper.loop();
        }
        
        public void quit() {
            mLooper.quit();
        }
    }

    @Override
    public void finish(){
        Log.d(TAG, "finish");
        Intent rst = new Intent();
        String urlpath = null;
        try {
            urlpath = mService.getUrlPlayListPath();
        } catch (RemoteException ex) {
        }
        Log.d(TAG, "finish urlpath="+urlpath);
        ContentResolver contentResolver = getContentResolver();
        Uri queryUri = Uri.parse("content://com.android.music.ContentProvider.MusicUrlPathprovider/urlpath");
        Cursor cursor = contentResolver.query(queryUri, null, null, null, "id desc");
        if(!cursor.moveToNext()) {
            Uri insertUri = Uri.parse("content://com.android.music.ContentProvider.MusicUrlPathprovider/urlpath");
            ContentValues values = new ContentValues();
            values.put("path", urlpath);
            Uri uri = contentResolver.insert(insertUri, values);
        } else {
            Cursor cursor2 = contentResolver.query(queryUri, null, null, null, "id desc");
            while(cursor2.moveToNext()){
                int id = cursor2.getInt(cursor2.getColumnIndex("id"));
                String path = cursor2.getString(cursor2.getColumnIndex("path"));
                //Log.i(TAG, "id="+ id + ",path="+ path);
                Uri updateUri = Uri.parse("content://com.android.music.ContentProvider.MusicUrlPathprovider/urlpath/"+id);
                ContentValues values = new ContentValues();
                values.put("path", urlpath);
                contentResolver.update(updateUri, values, null, null);
            }
        }
        super.finish();
    }
}


