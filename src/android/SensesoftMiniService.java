/*
* Copyright (C) 2015-2018 Sensible Solutions Sweden AB
*
* Service to keep bluetooth connection alive when the app is put in background.
*
* A wake lock is needed to keep the cpu running so bluetooth connection doesn't disconnects when the device goes to "sleep".
* The doze mode in Android 6.0+ does not honour wake locks (even if the app is excluded from such battery optimization).
* Apps that have running foreground services (with the associated notification) are not restricted by doze mode.
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
import android.graphics.BitmapFactory;
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
    private static final String ONGOING_NOTIFICATION_TEXT = "You are connecting/connected to an alarm clip.";
    // Notification icons for the ongoing 'foreground service' notification
    private static final String NOTIFICATION_BT_ICON = "notification_bt_icon";
    private static final String NOTIFICATION_LARGE_ICON = "notification_large_icon";

     // Interface for clients that bind
    private final IBinder mBinder = new SensesoftMiniBinder(); 
  
    // Wakelock used to prevent CPU from going to sleep
    private WakeLock wakeLock = null;

    private boolean isForegroundService = false;
	
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
	
	// Remove the service from the foreground state
        if (isForegroundService){
		stopForeground(true);
		isForegroundService = false;
	}
	    
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
      
    }
    
     /*
     * Called when the service is no longer used and is being destroyed (when the
     * last client unbinds from the service, the system destroys the service).
     */
    @Override
    public void onDestroy() {
	    
        super.onDestroy();
	
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
    private Notification makeOngoingNotification(String contentText, Intent appIntent) {

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext())
          .setContentTitle(ONGOING_NOTIFICATION_TITLE)
          .setContentText(contentText)
          .setOngoing(true)
          //.setColorized(true)      	// Recommended to use background color for ongoing foreground service notifications (only Android O and above)
	  //.setColor(0x0027a1c6)	// Sets the background color of the small icon where it's used other than the in the system bar (always whiteish there)
          //.setSmallIcon(getApplicationContext().getApplicationInfo().icon)
	  .setSmallIcon(getApplicationContext().getResources().getIdentifier(NOTIFICATION_BT_ICON, "drawable", getApplicationContext().getPackageName()))
          .setLargeIcon(BitmapFactory.decodeResource(getApplicationContext().getResources(), getApplicationContext().getResources().getIdentifier(NOTIFICATION_LARGE_ICON, "drawable", getApplicationContext().getPackageName())))
	  .setPriority(NotificationCompat.PRIORITY_MIN)     // Minimum priority prevents the notification from being visable on the lockscreen
          .setContentIntent(PendingIntent.getActivity(getApplicationContext(), ONGOING_NOTIFICATION_ID, appIntent, PendingIntent.FLAG_UPDATE_CURRENT));
      
        return mBuilder.build();
    }
  
    /**
     * Update the ongoing notification.
    */
    protected void updateOngoingNotification(String contentText, Intent appIntent) {

        Notification notification = makeOngoingNotification(contentText, appIntent);
        NotificationManager serviceNotificationManager;
        serviceNotificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        serviceNotificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
    }

    /**
     * Puts this service in foreground state to prevent app from being killed by OS when system
     * enters doze mode (Android 6.0+) and the app is in the background. 
    */
    protected void enableForegroundService(Intent appIntent) {

	if (!isForegroundService){
		startForeground(ONGOING_NOTIFICATION_ID, makeOngoingNotification(ONGOING_NOTIFICATION_TEXT, appIntent));
		isForegroundService = true;
	}
    }

    /**
     * Removes this service from foreground state. 
    */
    /*protected void disableForegroundService() {
	
	if (isForegroundService){
        	stopForeground(true);
		isForegroundService = false;
	}
    }*/
}
