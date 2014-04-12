/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.voidstar.glass.sample.pinDrop;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.google.android.glass.app.Card;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;
import com.google.android.glass.timeline.TimelineManager;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * Service owning the LiveCard living in the timeline.
 */
public class PinDropService extends Service {

    private static final String TAG = "PinDrop";
    private static final String LIVE_CARD_TAG = "pindrop";

    private LocationManager mLocationManager;
    private TimelineManager mTimelineManager;
    private LiveCard mLiveCard;
    
    private List<PinDropLocationListener> locationListeners = new ArrayList<PinDropLocationListener>();
    private DownloadMapTileTask downloadTask;
    
    private final MenuBinder mBinder = new MenuBinder();
    
    private String coords;
    private boolean hasLocation;
    
    private class PinDropLocationListener implements LocationListener {

		@Override
		public void onLocationChanged(Location location) {
				coords = String.valueOf(location.getLatitude()) + "," + String.valueOf(location.getLongitude());
				Log.d(TAG, "Location obtained from " + location.getProvider() + ": " + coords);
				hasLocation = true;

				downloadTask = new DownloadMapTileTask();
				downloadTask.execute(coords);
			}
		
		@Override
		public void onProviderDisabled(String provider) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onProviderEnabled(String provider) {
			// TODO Auto-generated method stub
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			// TODO Auto-generated method stub
		}
    }

    private class DownloadMapTileTask extends AsyncTask<String, Void, Bitmap> {
    	String savedCoords;
    	
		@Override
		protected Bitmap doInBackground(String... params) {
			savedCoords = params[0];
			
			try {
				Log.d(TAG, "Downloading map tile with URL");
				
				URL staticMapUrl = new URL(
						"http://maps.googleapis.com/maps/api/staticmap?center=" + params[0] + 
							"&markers=" + params[0] +
							"&size=640x360&zoom=21&maptype=hybrid&sensor=true");
						
				Log.d(TAG, staticMapUrl.toString());
				
				InputStream stream = staticMapUrl.openStream(); // Lag here while content loads
				
				BufferedInputStream bufferedStream = new BufferedInputStream(stream);
				
				Log.d(TAG, "Bytes: " + String.valueOf(bufferedStream.available()));
				
				Bitmap output = BitmapFactory.decodeStream(bufferedStream);
				stream.close();
				bufferedStream.close();
				Log.d(TAG, "Loaded image");
				return output;
			}
			catch (Exception e) {
				Log.e(TAG, "Failed to load image");
				e.printStackTrace();
			}
			
			return null;
		}
		
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			RemoteViews pinDropView = new RemoteViews(getPackageName(), R.layout.card_pin_dropped);
			if (bitmap != null) pinDropView.setImageViewBitmap(R.id.map_tile, bitmap);
			pinDropView.setTextViewText(R.id.pin_coords, savedCoords);
			
			mLiveCard.setViews(pinDropView);
			
			Log.d(TAG, "Sent new views");

			AudioManager audio = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
			audio.playSoundEffect(Sounds.SUCCESS);
		}
    }

    public class MenuBinder extends Binder {
    	public void startNavigation() {
    		Log.d(TAG, "Starting navigation");

    		Intent intent = new Intent(Intent.ACTION_VIEW);
    		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		intent.setData(Uri.parse("google.navigation:q=" + coords));
    		startActivity(intent);
    	}

    	public boolean hasLocation() {
    		return hasLocation;
    	}

		public void spawnStaticCard() {
			// todo next
		}
    }

    @Override
    public void onCreate() {
    	super.onCreate();
    	mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    	mTimelineManager = TimelineManager.from(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
    	return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {  	
    	if (mLiveCard == null) { 	
    		Log.d(TAG, "Connecting mLocationManager");

    		Criteria criteria = new Criteria();
    		criteria.setAccuracy(Criteria.ACCURACY_COARSE);
    		
    		PinDropLocationListener listener = new PinDropLocationListener();
    		locationListeners.add(listener);
    		mLocationManager.requestSingleUpdate(criteria, listener, null);

    		Log.d(TAG, "Publishing LiveCard");
    		mLiveCard = mTimelineManager.createLiveCard(LIVE_CARD_TAG);

    		// Keep track of the callback to remove it before unpublishing.
    		mLiveCard.setViews(new RemoteViews(getPackageName(), R.layout.activity_waiting));

    		Intent menuIntent = new Intent(this, MenuActivity.class);
    		menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    		mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));

    		mLiveCard.publish(PublishMode.REVEAL);
    		Log.d(TAG, "Done publishing LiveCard");

    		//hasPlayedSfx = false;
    	} else {
    		// TODO(alainv): Jump to the LiveCard when API is available.
    	}

    	return START_STICKY;
    }

    @Override
    public void onDestroy() {
    	clearAsyncs();

    	if (mLiveCard != null && mLiveCard.isPublished()) {
    		Log.d(TAG, "Unpublishing LiveCard");
    		mLiveCard.unpublish();
    		mLiveCard = null;
    	}
    	super.onDestroy();
    }

    private void clearAsyncs() {
    	for (PinDropLocationListener listener : locationListeners) {
    		mLocationManager.removeUpdates(listener);
    	}

    	locationListeners.clear();

    	if (downloadTask != null) downloadTask.cancel(true);
    }
}
