
package com.realtek.bitmapfun.util;

import java.io.File;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class ImageFileSystemFetcher extends ImageResizer
{
    private static final String TAG = ImageFileSystemFetcher.class
            .getSimpleName();

    /**
     * Initialize providing a target image width and height for the processing
     * images.
     * 
     * @param context
     * @param loadingControl
     * @param imageWidth
     * @param imageHeight
     */
    public ImageFileSystemFetcher(Context context,
            LoadingControl loadingControl, int imageWidth,
            int imageHeight)
    {
        super(context, loadingControl, imageWidth, imageHeight);
    }

    /**
     * Initialize providing a single target image size (used for both width and
     * height);
     * 
     * @param context
     * @param loadingControl
     * @param imageSize
     */
    public ImageFileSystemFetcher(Context context,
            LoadingControl loadingControl, int imageSize)
    {
        super(context, loadingControl, imageSize);
    }

    /**
     * The main process method, which will be called by the ImageWorker in the
     * AsyncTask background thread.
     * 
     * @param data The data to load the bitmap, in this case, a regular http URL
     * @return The downloaded and resized bitmap
     */
    protected Bitmap processBitmap(String data, BitmapFactory.Options options)
    {
        return processBitmap(data, mImageWidth, mImageHeight, options);
    }

    /**
     * The main process method, which will be called by the ImageWorker in the
     * AsyncTask background thread.
     * 
     * @param data The data to load the bitmap, in this case, a regular http URL
     * @param imageWidth
     * @param imageHeight
     * @return The downloaded and resized bitmap
     */
    protected Bitmap processBitmap(String data, int imageWidth, int imageHeight, BitmapFactory.Options options)
    {
    	/*
        if (BuildConfig.DEBUG)
        {
            CommonUtils.debug(TAG, "processBitmap - " + data);
        }*/
    	
    	Log.d(TAG, "processBitmap - " + data);
        if (data == null)
        {
            return null;
        }

       
        
        
    	if(data !=null)
    	{
    		//android.resource:// 
    		
    		String tmpStr = data.substring(0, 19);

    		if(tmpStr.equals("android.resource://"))
    		{
    			return processBitmap(Integer.parseInt( data.substring(tmpStr.length(), data.length())), imageWidth,imageHeight, options);
    		}
    		else
    		{
    			 // Download a bitmap, write it to a file
    	        final File f = new File(data);

    	        if (f == null || f.exists() == false)
    	        {	
    	        	return null;
    	        }
    			// Return a sampled down version
    			return decodeSampledBitmapFromFile(data, imageWidth, imageHeight, options);
    		}	
    			
    	}	
    	

        return null;
    }

    @Override
    protected Bitmap processBitmap(Object data, BitmapFactory.Options options)
    {
        return processBitmap(String.valueOf(data), options);
    }
}
