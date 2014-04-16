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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

/**
 * Activity showing the options menu.
 */
public class MenuActivity extends Activity {
	// This is technically an Immersion!
	// Because Services have no UI, we need to open this Activity, which in turn opens its menu!
	
	PinDropService.MenuBinder mBinder;
	
	private static String TAG = "PinDropMenu";
	
	boolean hasLocation;
	
	/*
	 * Links this Activity to the Service that spawned it, so the Menu can send and receive information
	 */
	private ServiceConnection mConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			if (service instanceof PinDropService.MenuBinder) {
				mBinder = (PinDropService.MenuBinder)service;
				hasLocation = mBinder.hasLocation();
				Log.d(TAG, hasLocation ? "Received has location" : "Received no location");
				//openOptionsMenu();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {}
	};
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindService(new Intent(this, PinDropService.class), mConnection, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        openOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pindropmenu, menu);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (!hasLocation) {
        	menu.findItem(R.id.directions).setVisible(false);
        	menu.findItem(R.id.remember).setVisible(false);
        }
        else {
        	menu.findItem(R.id.directions).setVisible(true);
        	menu.findItem(R.id.remember).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        switch (item.getItemId()) {
        	case R.id.directions:
        		mBinder.startNavigation();
        		return true;
        	case R.id.remember:
        		mBinder.addToTimeline(); // TODO: Add Mirror functionality!
        		return true;
            case R.id.stop: // IT IS CRITICALLY IMPORTANT TO ADD THIS OR THE GLASSWARE CAN'T BE KILLED IN USERSPACE!
                stopService(new Intent(this, PinDropService.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        // Nothing else to do, closing the Activity.
        finish();
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    	unbindService(mConnection); // Don't leak Services!
    }
}
