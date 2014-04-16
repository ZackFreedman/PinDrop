
package com.voidstar.glass.sample.pinDrop;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.timeline.LiveCard;
import com.google.android.glass.timeline.LiveCard.PublishMode;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Criteria;
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
    private LiveCard mLiveCard;
    
    private List<PinDropLocationListener> locationListeners = new ArrayList<PinDropLocationListener>();
    private DownloadMapTileTask downloadTask;
    
    private final MenuBinder mBinder = new MenuBinder();
    
    private String coords;
    private boolean hasLocation;
    
    /*
     * Specialized LocationListener that obtains a GPS location, extracts its coordinates,
     * and starts downloading the map tile
     */
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

    /*
     * Downloads a map tile from the Google Maps Static API.
     * This must be wrapped in an AsyncTask because the download takes too long
     * to run on the UI thread.
     */
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

    /*
     * Attaches the MenuActivity to the Live Card so its MenuItems can kill the app or start navigation.
     * Also allows the Get Directions entry to be hidden while the location loads. 
     */
    public class MenuBinder extends Binder {
    	public void startNavigation() {
    		Log.d(TAG, "Starting navigation");

    		Intent intent = new Intent(Intent.ACTION_VIEW);
    		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    		intent.setData(Uri.parse("google.navigation:q=" + coords)); // Special URI is handled by the "Get Directions To" app
    		startActivity(intent);
    	}

    	public boolean hasLocation() {
    		return hasLocation;
    	}

		public void addToTimeline() {
			// TODO: Use the Mirror API to add a Card to the Timeline so the user can drop a pin and return to it later
		}
    }

    @Override
    public void onCreate() {
    	// This runs when the Service is first created.
    	// If you launch the Glassware and the Live Card isn't visible, this is called before onStartCommand.
    	// If you launch the Glassware and the Live Card is visible, onStartCommand will be immediately called.
    	super.onCreate();
    	mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public IBinder onBind(Intent intent) {
    	// When a MenuActivity is created, its code will bind it to the instance of this service.
    	// This method allows it to collect the MenuBinder that lets it affect this Service.
    	return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {  	
    	// This method is called whenever the Glassware is invoked via voice commands or the OK Glass menu.
    	if (mLiveCard == null) { 	
    		Log.d(TAG, "Connecting mLocationManager");

    		Criteria criteria = new Criteria();
    		criteria.setAccuracy(Criteria.ACCURACY_COARSE);
    		
    		PinDropLocationListener listener = new PinDropLocationListener();
    		locationListeners.add(listener);
    		mLocationManager.requestSingleUpdate(criteria, listener, null);

    		mLiveCard = new LiveCard(this, LIVE_CARD_TAG);
    		mLiveCard.setViews(new RemoteViews(getPackageName(), R.layout.activity_waiting));
    		mLiveCard.attach(this); // Prevent this Service from being killed to free up memory

    		Intent menuIntent = new Intent(this, MenuActivity.class); // Since menus can only be attached to Activities, we create an activity to own and launch the menu.
    		menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    		mLiveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0)); // This Intent will be fired whenever the LiveCard is tapped.

    		Log.d(TAG, "Publishing LiveCard");
    		mLiveCard.publish(PublishMode.REVEAL); // Add the LiveCard to the Timeline and switch to it
    		Log.d(TAG, "Done publishing LiveCard");
    	} else {
    		mLiveCard.navigate(); // Switch to the app if it's already running
    	}

    	return START_STICKY; // No idea what this does. Your guess is as good as mine.
    }

    @Override
    public void onDestroy() {
    	clearAsyncs(); // Kill other threads to prevent leakage

    	if (mLiveCard != null && mLiveCard.isPublished()) {
    		Log.d(TAG, "Unpublishing LiveCard");
    		mLiveCard.unpublish(); // Buh-bye, LiveCard! It will live out the rest of its days in a LiveCard retirement home.
    		mLiveCard = null; // Never mind, the garbage collector ate it
    	}
    	super.onDestroy();
    }

    /*
     * Kills other threads to prevent leakage
     */
    private void clearAsyncs() {
    	for (PinDropLocationListener listener : locationListeners) {
    		mLocationManager.removeUpdates(listener);
    	}

    	locationListeners.clear();

    	if (downloadTask != null) downloadTask.cancel(true);
    }
}
