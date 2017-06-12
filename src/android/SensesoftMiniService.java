/*
* Copyright (C) 2015-2017 Sensible Solutions Sweden AB
*
* Foreground service to keep bluetooth connection alive when system
* enters doze mode (Android 6.0+).
* 
*/

package com.sensiblesolutions.gattserver;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;


public class SensesoftMiniService extends Service {
  
    // The identifier for the ongoing 'foreground service' notification
    public static final int ONGOING_NOTIFICATION_ID = 846729;
    // Title of the ongoing 'foreground service' notification
    private static final String ONGOING_NOTIFICATION_TITLE = "SenseSoft Mini";
    // Default text of the ongoing 'foreground service' notification
    private static final String ONGOING_NOTIFICATION_TEXT = "You are connected/connecting to an alarm clip.";

     // Interface for clients that bind
    private final IBinder mBinder = new SensesoftMiniBinder(); 
  
    // Wakelock used to prevent CPU from going to sleep
    private WakeLock wakeLock = null;
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
    public IBinder onBind(Intent intent) {
        // Called after the first client (only) is binding to the service with bindService()
      
	// Need a wakelock to keep the cpu running so bluetooth connection doesn't disconnects when device goes to "sleep"
	if (wakeLock == null){
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SSMWakelockTag");
		wakeLock.setReferenceCounted(false);
		wakeLock.acquire();
	}
      
        return mBinder;
    }
	
    @Override
    public boolean onUnbind(Intent intent) {
        // Called when all clients have unbound with unbindService()
	    
	// Release the wake lock if it has been acquired but not yet released
	if (wakeLock != null){
		if (wakeLock.isHeld()){
			wakeLock.release();
		}
		wakeLock = null;
	}
	    
        return false;
    }

    /*
    * Called when the service is being created.
    */
    @Override
    public void onCreate() {
        super.onCreate();
        // Configure the service run in the foreground to prevent app from being killed by OS
        startForeground(ONGOING_NOTIFICATION_ID, makeOngoingNotification(ONGOING_NOTIFICATION_TEXT));
    }
    
     /*
     * Called when the service is no longer used and is being destroyed (when the
     * last client unbinds from the service, the system destroys the service).
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove the service from the foreground state
        stopForeground(true);
	//getNotificationManager().cancel(ONGOING_NOTIFICATION_ID);
	// Release the wake lock if it has been acquired but not yet released
	if (wakeLock != null){
		if (wakeLock.isHeld()){
			wakeLock.release();
		}
		wakeLock = null;
	}
    }

    /*
    * A foreground service must provide a notification for the status bar, which is placed under the Ongoing heading.
    * This means that the notification cannot be dismissed unless the service is either stopped or removed from the foreground.
    */
    private Notification makeOngoingNotification(String contentText) {

        //Intent appIntent = getApplicationContext().getPackageManager().getLaunchIntentForPackage(getApplicationContext().getPackageName()); // If used, app will always be started (even if it's already running)
        //Intent appIntent = org.apache.cordova.CordovaPlugin.cordova.getActivity().getIntent(); // If used, will start app if not running otherwise bring it to the foreground
	//appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        //Notification notification = new Notification.Builder(this)
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext())
          .setContentTitle(ONGOING_NOTIFICATION_TITLE)
          .setContentText(contentText)
           //.setTicker(ONGOING_NOTIFICATION_TICKER)
          .setOngoing(true)
          //.setColorized(true)     // Recommended to use background color for ongoing foreground service notifications (Android O)
          .setColor(0xff00ffff)     // Semi transparent blue (argb). Only works if setColorized(true)
          .setSmallIcon(getApplicationContext().getApplicationInfo().icon)
          .setPriority(NotificationCompat.PRIORITY_MIN);     // Prevents the notification from being visable on the lockscreen
          //.setContentIntent(PendingIntent.getActivity(getApplicationContext(), ONGOING_NOTIFICATION_ID, appIntent, PendingIntent.FLAG_UPDATE_CURRENT));
      
        return mBuilder.build();
    }
  
    /**
     * Update the ongoing notification.
    */
    protected void updateOngoingNotification(String contentText) {

        Notification notification = makeOngoingNotification(contentText);
        NotificationManager serviceNotificationManager;
        serviceNotificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        serviceNotificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
    }


}
