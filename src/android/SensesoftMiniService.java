/*
* Copyright (C) 2015-2017 Sensible Solutions Sweden AB
*
* Foreground service to keep bluetooth connection alive when system
* enters doze mode (Android 6.0+).
* 
*/

package com.sensiblesolutions.gattserver;


import org.apache.cordova.CordovaPlugin;

import android.app.Service;
import android.os.Binder;
import android.os.IBinder;

import android.content.Intent;
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
    // Default text of the ongoing 'foreground service' notification while connected
    private static final String ONGOING_NOTIFICATION_TEXT_CONNECTED = "Connected with alarm clip.";
    // Default text of the ongoing 'foreground service' notification while connecting
    private static final String ONGOING_NOTIFICATION_TEXT_CONNECTING = "Connecting to alarm clip.";
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
    public IBinder onBind(Intent intent) {
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
        startForeground(ONGOING_NOTIFICATION_ID, makeOngoingNotification(ONGOING_NOTIFICATION_TEXT_CONNECTED));
    }
    
     /*
     * Called when the service is no longer used and is being destroyed
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Remove the service from the foreground state
        stopForeground(true);
    }

    /*
    * A foreground service must provide a notification for the status bar, which is placed under the Ongoing heading.
    * This means that the notification cannot be dismissed unless the service is either stopped or removed from the foreground.
    */
    private Notification makeOngoingNotification(String contentText) {

        Intent appIntent = CordovaPlugin.cordova.getActivity().getIntent();	// If used, will start app if not running otherwise bring it to the foreground
        appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        //Notification notification = new Notification.Builder(this)
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext())
          .setContentTitle(ONGOING_NOTIFICATION_TITLE)
          .setContentText(contentText)
           //.setTicker(ONGOING_NOTIFICATION_TICKER)
          .setOngoing(true)
          .setColorized(true)       // Recommended to use background color for ongoing foreground service notifications
          .setColor(0x800000ff)     // Semi transparent blue (argb). Only works if setColorized(true)
          .setSmallIcon(cordova.getActivity().getApplicationContext().getApplicationInfo().icon)
          .setPriority(NotificationCompat.PRIORITY_MIN)     // Prevents the notification from being visable on the lockscreen
          .setContentIntent(PendingIntent.getActivity(cordova.getActivity().getApplicationContext(), ONGOING_NOTIFICATION_ID, appIntent, PendingIntent.FLAG_UPDATE_CURRENT));
      
        return mBuilder.build();
    }
  
    /**
     * Update the ongoing notification.
    */
    protected void updateOngoingNotification(String contentText) {

        Notification notification = makeOngoingNotification(contentText);
        NotificationManager serviceNotificationManager;
        serviceNotificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        serviceNotificationManager.notify(ONGOING_NOTIFICATION_ID, notification);
    }


}
