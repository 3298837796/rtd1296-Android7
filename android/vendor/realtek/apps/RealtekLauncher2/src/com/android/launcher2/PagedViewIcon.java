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

package com.android.launcher2;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.realtek.addon.helper.DebugHelper;

/**
 * An icon on a PagedView, specifically for items in the launcher's paged view (with compound
 * drawables on the top).
 */
public class PagedViewIcon extends TextView {
    /** A simple callback interface to allow a PagedViewIcon to notify when it has been pressed */
    public static interface PressedCallback {
        void iconPressed(PagedViewIcon icon);
        void iconClickedInTouchMode(PagedViewIcon icon);
    }

    @SuppressWarnings("unused")
    private static final String TAG = "PagedViewIcon";
    private static final float PRESS_ALPHA = 0.4f;

    private PagedViewIcon.PressedCallback mPressedCallback;
    private boolean mLockDrawableState = false;

    private Bitmap mIcon;

    public PagedViewIcon(Context context) {
        this(context, null);
        setFocusableInTouchMode(true);
    }

    public PagedViewIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        setFocusableInTouchMode(true);
    }

    public PagedViewIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setFocusableInTouchMode(true);
    }
    
    public boolean onTouchEvent (MotionEvent event){
        int action=event.getAction();
        
        if(action==MotionEvent.ACTION_DOWN){
            this.requestFocus();
        }
        
        // when user release mouse click
        if(action==MotionEvent.ACTION_UP){
            //DebugHelper.dumpSQA("onTouch:"+action);
            if(mPressedCallback!=null){
                mPressedCallback.iconClickedInTouchMode(this);
                return true;
            }
        }
        
        return super.onTouchEvent(event);
    }

    // COMMENT: the height and width got here is defined in dimens.xml -> app_icon_size
    public void applyFromApplicationInfo(ApplicationInfo info, boolean scaleUp,
            PagedViewIcon.PressedCallback cb) {
        mIcon = info.iconBitmap;
        
        // COMMENT: applyFromApplicationInfo, where app info is applied
        //DebugHelper.dump("applyFromApplicationInfo: "+info.title+" size:"+mIcon.getWidth()+" "+mIcon.getHeight());
        
        mPressedCallback = cb;
        setCompoundDrawablesWithIntrinsicBounds(null, new FastBitmapDrawable(mIcon), null, null);
        setText(info.title);
        setTag(info);
    }

    public void lockDrawableState() {
        mLockDrawableState = true;
    }

    public void resetDrawableState() {
        mLockDrawableState = false;
        post(new Runnable() {
            @Override
            public void run() {
                refreshDrawableState();
            }
        });
    }

    protected void drawableStateChanged() {
        super.drawableStateChanged();

        // We keep in the pressed state until resetDrawableState() is called to reset the press
        // feedback
        if (isPressed()) {
            setAlpha(PRESS_ALPHA);
            if (mPressedCallback != null) {
                mPressedCallback.iconPressed(this);
            }
        } else if (!mLockDrawableState) {
            setAlpha(1f);
        }
    }
    
    public String toString(){
        return "PagedViewIcon "+getText();
    }
}
