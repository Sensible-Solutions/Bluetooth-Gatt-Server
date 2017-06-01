/*
* Copyright (C) 2015-2017 Sensible Solutions Sweden AB
*
* Foreground service to keep bluetooth connection alive when system
* enters doze mode (Android 6.0+).
* 
*/

package com.sensiblesolutions.gattserver;

import android.app.Service;
import android.os.Binder;
import android.os.IBinder;

public class SensesoftMiniService extends Service {

  // Interface for clients that bind
  private final IBinder mBinder = new SensesoftMiniBinder(); 
  
  
  /*
  * Class used for the client Binder. Because we know this service always
  * runs in the same process as its clients, we don't need to deal with IPC.
  */
  public class SensesoftMiniBinder extends Binder {
    SensesoftMiniService getService() {
      // Return this instance of SensesoftMiniService so clients can call its public methods
      return SensesoftMiniService.this;
    }
  }
  
  /*
  * When binding to the service, return an interface for sending messages to the service.
  */
  @Override
  public IBinder onBind (Intent intent) {
    // A client is binding to the service with bindService()
    return mBinder;
  }

 
  
  


    /*
     * Put the service in a foreground state to prevent app from being killed
     * by the OS.
     */
    @Override
    public void onCreate () {
        super.onCreate();
        keepAwake();
    }

    /**
     * No need to run headless on destroy.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        sleepWell();
    }

    /**
     * Put the service in a foreground state to prevent app from being killed
     * by the OS.
     */
    private void keepAwake() {
        JSONObject settings = BackgroundMode.getSettings();
        boolean isSilent    = settings.optBoolean("silent", false);

        if (!isSilent) {
            startForeground(NOTIFICATION_ID, makeNotification());
        }

        PowerManager pm = (PowerManager)
                getSystemService(POWER_SERVICE);

        wakeLock = pm.newWakeLock(
                PARTIAL_WAKE_LOCK, "BackgroundMode");

        wakeLock.acquire();
    }

    /**
     * Stop background mode.
     */
    private void sleepWell() {
        stopForeground(true);
        getNotificationManager().cancel(NOTIFICATION_ID);

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

}
