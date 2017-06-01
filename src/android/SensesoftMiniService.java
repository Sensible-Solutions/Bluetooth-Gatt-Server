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

import android.app.PendingIntent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.app.NotificationManager;
import android.app.Notification;


public class SensesoftMiniService extends Service {
  
  // The identifier for the ongoing 'foreground service' notification
  public static final int ONGOING_NOTIFICATION_ID = 846729;
  // Title of the ongoing 'foreground service' notification
  private static final String ONGOING_NOTIFICATION_TITLE = "SenseSoft Mini";
  // Default text of the ongoing 'foreground service' notification
  private static final String ONGOING_NOTIFICATION_TEXT = "Connected with alarm clip.";
  // Default ticker text of the ongoing 'foreground service' notification
  //private static final String ONGOING_NOTIFICATION_TEXT = "SenseSoft Mini";
  
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
  * Called when the service is being created.
  */
  @Override
  public void onCreate() {
     super.onCreate();
    // Make the service run in the foreground to prevent app from being killed by OS
    startForeground(NOTIFICATION_ID, makeOngoingNotification());
  }

  /*
  * A foreground service must provide a notification for the status bar, which is placed under the Ongoing heading.
  * This means that the notification cannot be dismissed unless the service is either stopped or removed from the foreground.
  */
  private Notification makeOngoingNotification() {
    
    Intent appIntent = cordova.getActivity().getIntent();	// If used, will start app if not running otherwise bring it to the foreground
    appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    
    //Notification notification = new Notification.Builder(this)
    NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext())
      .setContentTitle(ONGOING_NOTIFICATION_TITLE)
      .setContentText(ONGOING_NOTIFICATION_TEXT)
       //.setTicker(ONGOING_NOTIFICATION_TICKER)
      .setOngoing(true)
      .setSmallIcon(cordova.getActivity().getApplicationContext().getApplicationInfo().icon)
      .setPriority(NotificationCompat.PRIORITY_MIN)     // Prevents the notification from being visable on the lockscreen
      .setContentIntent(PendingIntent.getActivity(cordova.getActivity().getApplicationContext(), ONGOING_NOTIFICATION_ID, appIntent, PendingIntent.FLAG_UPDATE_CURRENT))
      .build();

    return mBuilder;
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
