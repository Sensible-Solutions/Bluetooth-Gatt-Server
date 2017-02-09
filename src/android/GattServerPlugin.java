/*
* Copyright (C) 2015-2017 Sensible Solutions Sweden AB
*
*
* Cordova Plugin for the Bluetooth GATT Profile server role.
*
* This class provides Bluetooth GATT server role functionality,
* allowing applications to create and advertise the Bluetooth
* Smart immediate alert service.
* 
*/
 
package com.sensiblesolutions.gattserver;

//import com.sensiblesolutions.sensesoftnotificationsmini.R;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattDescriptor;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
//import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.AlertDialog;			// For showing debug messaages
import android.app.PendingIntent;
import android.content.DialogInterface;		// For showing debug messaages
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Vibrator;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.Manifest.permission;
//import android.R;

import java.lang.Enum;
import java.lang.System;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class GattServerPlugin extends CordovaPlugin
{
	// Immediate alert service
	private final static UUID IMMEDIATE_ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");	// Service UUID
	private final static UUID ALERT_LEVEL_CHAR_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");		// Characteristic UUID
	//private static final int ALERT_LEVEL_CHARACTERISTIC_VALUE = 2;
	//private static final int ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE = 17;
	//private static final int ALERT_LEVEL_CHARACTERISTIC_OFFSET = 0;
	
	// Immediate alert levels
	private final static byte[] ALERT_LEVEL_LOW = {0x00};			// No alert
	private final static byte[] ALERT_LEVEL_MILD = {0x01};
	private final static byte[] ALERT_LEVEL_HIGH = {0x02};
	
	// Linkloss service
	//private final static UUID LINKLOSS_SERVICE_UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb");				// Service UUID
	
	// General callback variables
	private CallbackContext serverRunningCallbackContext = null;
	
	// Action Name Strings
	//private final String initializeActionName = "initialize";
	private final static String START_GATT_SERVER = "startServer";
	private final static String RESET_ALARM = "resetAlarm";
	
	// Object keys
	private final static String keyStatus = "status";
	private final static String keyError = "error";
	private final static String keyMessage = "message";
	
	// Status Types
	private final static String statusServiceAdded = "serviceAdded";
	private final static String statusServiceExists = "serviceAlreadyProvided";
	private final static String statusWriteRequest = "characteristicWriteRequest";
	private final static String statusConnectionState = "serverConnectionState";
	private final static String statusAlarmReseted = "alarmReseted";
	//private final String statusServerStopped = "scanStopped";
  
	// Error Types
	//private final String errorInitialize = "initialize";
	//private final static String errorStartServer = "startServer";
	private final static String errorConnectionState = "serverConnectionState";
	private final static String errorNoPermission = "noPermission";
	//private final static String errorGattServer = "gattServer";
	private final static String errorServerState = "serverState";
	private final static String errorServerStateOff = "serverStateOff";
	private final static String errorServerStateUnsupported = "serverStateUnsupported";
	private final static String errorServerStateUnauthorized = "serverStateUnauthorized";	// iOS only
	private final static String errorServiceAdded = "serviceAdded";
	private final static String errorWriteRequest = "writeRequest";
	private final static String errorReadRequest = "readRequest";

	
	// Error Messages
	private final static String logServerAlreadyRunning = "GATT server is already running";
	private final static String logNoPermission = "No permission granted for local notifications";
	private final static String logService = "Immediate Alert service could not be added";
	private final static String logConnectionState = "Connection state changed with error";
	private final static String logStateUnsupported = "BLE is not supported by device";
	private final static String logStatePoweredOff = "BLE is turned off for device";
	private final static String logRequestNotSupported = "Request is not supported";
	
	private boolean isInBackground = false;			// Flag indicating if app is in the background
	private boolean iasInitialized = false; 		// Flag indicating if Immediate Alert Service has been initialized
	private BluetoothGattServer gattServer = null;
	private WakeLock wakeLock = null;			// Wakelock used to prevent CPU from going to sleep
	private NotificationManager alarmNotificationManager = null;
	//private NotificationCompat.Builder mBuilder = null;
	private Notification alarmNotification = null;
	//private MediaPlayer mediaPlayer = null;
	
	private AppSettings myAppSettings = null;
	
	private enum AlarmSound
	{
		SOUND_0,			// custom mp3 sound
		SOUND_1,			// custom mp3 sound
		SOUND_NOTIFICATION,		// Default notification sound
		SOUND_RINGTONE,			// Default ringtone sound
		SOUND_ALARM,			// Default alarm sound
		SOUND_OFF;			// No alarm sound
	}
	
	private class AppSettings
	{
		public boolean alert = true;			// Alarm on/off flag
		public AlarmSound sound = AlarmSound.SOUND_1;	// Sound flag
		public boolean vibration = true;		// Vibration on/off flag
		public boolean log = true;			// Alarm logging on/off flag
	}
	
	
	
	/*********************************************************************************************************************
	Bluetooth GATT interface callbacks
	*********************************************************************************************************************/
	private final BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {
		
		// Remote client characteristic write request
		@Override
		public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattCharacteristic characteristic, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value) {
			
			showDebugMsgBox("Write request: " + "value=" + String.valueOf((int)value[0]) + " offset=" + String.valueOf(offset));
			
			if(characteristic.getUuid() ==  ALERT_LEVEL_CHAR_UUID){
				
				int alertLevel = (int)value[0];
				characteristic.setValue(value);
				if(!iasInitialized && alertLevel != 0){
					// The first alarm received after a nRF8002 module has connected to the GATT server or
					// the alarm has been reseted by calling resetAlarm()
					iasInitialized = true;
					alarm(parseCharacteristicValue(alertLevel), device.getAddress());
				}
				else if (iasInitialized){
					// When an Immediate Alert level is set to trigger on "activated" on the nRF8002, it sends
					// "toggled" levels. That is, it sends "No Alert" on every second positive flank and the
					// configured alert level on every other. So interpret every write to this characteristic as
					// an alarm after the first alarm.
					alarm(parseCharacteristicValue(alertLevel), device.getAddress());
				}
				else {
					// Ignore first value(s) received. When a nRF8002 module connects to the GATT server
					// running Immediate Alert Service, it writes it's current alert level (sometimes twice).
					// This must not be interpreted as an alert.
					alarm(parseCharacteristicValue(alertLevel), device.getAddress()); // Added 2017-01-27 just to test sounds without having to manually trigger an alarm. Remove when done!!!
				}
				
				if (responseNeeded)
					gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
			}
			else {
				if (responseNeeded)
					gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
				
				JSONObject returnObj = new JSONObject();
				addProperty(returnObj, keyError, errorWriteRequest);
				addProperty(returnObj, keyMessage, logRequestNotSupported);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(true);
				serverRunningCallbackContext.sendPluginResult(pluginResult);
			}
		}
		
		@Override
		public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
			//Callback indicating when GATT client has connected/disconnected to/from a remote GATT server
			
			JSONObject returnObj = new JSONObject();
			// Notify user of connection status change
			if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
				addProperty(returnObj, keyStatus, statusConnectionState);
				addProperty(returnObj, "device", device.getAddress());
				addProperty(returnObj, "state", "connected");
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
				pluginResult.setKeepCallback(true);														// Save the callback so it can be invoked several times
				serverRunningCallbackContext.sendPluginResult(pluginResult);
			}
			else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
				addProperty(returnObj, keyStatus, statusConnectionState);
				addProperty(returnObj, "device", device.getAddress());
				addProperty(returnObj, "state", "disconnected");
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
				pluginResult.setKeepCallback(true);														// Save the callback so it can be invoked several times
				serverRunningCallbackContext.sendPluginResult(pluginResult);
			}
			else {
				addProperty(returnObj, keyError, errorConnectionState);
				addProperty(returnObj, keyMessage, logConnectionState + " " + status);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				//pluginResult.setKeepCallback(true);
				pluginResult.setKeepCallback(false);
				serverRunningCallbackContext.sendPluginResult(pluginResult);
				serverRunningCallbackContext = null;
			}
		}

		@Override
		public void onServiceAdded(int status, BluetoothGattService service) {
			
			//showDebugMsgBox("onServiceAdded called!");
			
			JSONObject returnObj = new JSONObject();
			
			if(status != BluetoothGatt.GATT_SUCCESS){
				// Notify user of error
				addProperty(returnObj, keyError, errorServiceAdded);
				addProperty(returnObj, keyMessage, logService);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(false);
				serverRunningCallbackContext.sendPluginResult(pluginResult);
				serverRunningCallbackContext = null;
				//return;
			}
			else {
				// Notify user and save callback
				addProperty(returnObj, keyStatus, statusServiceAdded);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
				// Save the callback so it can be invoked several times
				pluginResult.setKeepCallback(true);
				serverRunningCallbackContext.sendPluginResult(pluginResult);	
			}
			
		}

		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
			// Not supported/implemented
			
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
			
			// Not really needed since there are currently no read requests
			JSONObject returnObj = new JSONObject();
			addProperty(returnObj, keyError, errorReadRequest);
			addProperty(returnObj, keyMessage, logRequestNotSupported);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(true);
			serverRunningCallbackContext.sendPluginResult(pluginResult);
		
		}
			

		@Override
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
			// Not supported/implemented
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
		}

		@Override
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			// Not supported/implemented
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
		}

		@Override
		public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
			// Not supported/implemented
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null);
		}
	};
	
	
	/*********************************************************************************************************************
	Plugin Actions
	*********************************************************************************************************************/
	@Override
	public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException
	{
		// Will run on the WebCore thread which here is fine
		try {
			if (START_GATT_SERVER.equals(action)) { 
				startServerAction(callbackContext);
				return true;
			}
			else if (RESET_ALARM.equals(action)){
				resetAlarmAction(callbackContext);
				return true;
			}
			else if (action.equals("alarm")){
				alarmAction(callbackContext);
				return true;
			}
			callbackContext.error("Invalid action");
			return false;
		} 
		catch(Exception ex) {
			System.err.println("Exception: " + ex.getMessage());
			callbackContext.error(ex.getMessage());
			return false;
		} 
	}
	
	private void startServerAction(CallbackContext callbackContext)
	{
		// Note: the flag indicating that Immediate Alert Service has been initialized (iasInitialized) will also be
		// reseted when calling this function.
		showDebugMsgBox("startServerAction() called!");
		JSONObject returnObj = new JSONObject();
		
		// Acquire the wake lock if it hasn't been acquired but not yet released
		if (!wakeLock.isHeld())
			wakeLock.acquire();
		
		if(BluetoothAdapter.getDefaultAdapter() == null){
		    	// Device does not support Bluetooth, notify user of unsupported Bluetooth
			addProperty(returnObj, keyError, errorServerState);
			addProperty(returnObj, keyMessage, logStateUnsupported);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(false);
			callbackContext.sendPluginResult(pluginResult);
			return;
		} 
		else {
			if(!BluetoothAdapter.getDefaultAdapter().isEnabled()){
			        // Bluetooth is not enabled, notify user that Bluetooth is not enabled
				addProperty(returnObj, keyError, errorServerState);
				addProperty(returnObj, keyMessage, logStatePoweredOff);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(false);
				callbackContext.sendPluginResult(pluginResult);
				return;
		    	}
		}
		
		if(!NotificationManagerCompat.from(cordova.getActivity().getApplicationContext()).areNotificationsEnabled()){
			// The function areNotificationsEnabled() from the support library returns true if notifications are
			// enabled for the app and if API >= 19. If Api < 19 it will always return true (even if notifications
			// actually are disabled for the app).
			JSONObject returnJsonObj = new JSONObject();
			addProperty(returnJsonObj, keyError, errorNoPermission);
			addProperty(returnJsonObj, keyMessage, logNoPermission);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnJsonObj);
			pluginResult.setKeepCallback(true);		// Save the callback so it can be invoked several times
			callbackContext.sendPluginResult(pluginResult);
			// return;
		}
		
		iasInitialized = false; 	// Reset the flag indicating that Immediate Alert Service has been initialized
		
		// If GATT server has been initialized or the GATT server is already running, don't start it again
		if((gattServer != null) && (serverRunningCallbackContext != null))
		{
			addProperty(returnObj, keyStatus, statusServiceExists);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);		// Save the callback so it can be invoked several times
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			//iasInitialized = false;
			return;
		}
		
		// Open a GATT server if not already opened
		final BluetoothManager bluetoothManager = (BluetoothManager) cordova.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
		if(gattServer == null){
			gattServer = bluetoothManager.openGattServer(cordova.getActivity().getApplicationContext(), mBluetoothGattServerCallback);
			if(gattServer == null){
				// Notify user of unsupported Bluetooth Smart
				addProperty(returnObj, keyError, errorServerState);
				addProperty(returnObj, keyMessage, logStateUnsupported);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(false);
				callbackContext.sendPluginResult(pluginResult);
				return;
			}
		}
		
		// Create an Immediate Alert service if not already provided by the device
		final BluetoothGattService immediateAlertService = new BluetoothGattService(IMMEDIATE_ALERT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
		if(gattServer.getService(IMMEDIATE_ALERT_SERVICE_UUID) == null){
			final BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(ALERT_LEVEL_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
			//characteristic.setValue(ALERT_LEVEL_CHARACTERISTIC_VALUE, ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE, ALERT_LEVEL_CHARACTERISTIC_OFFSET);
			//characteristic.setValue(ALERT_LEVEL_HIGH);
			characteristic.setValue(ALERT_LEVEL_LOW);
			if(!immediateAlertService.addCharacteristic(characteristic)){
				// Notify user of error
				addProperty(returnObj, keyError, errorServiceAdded);
				addProperty(returnObj, keyMessage, logService);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(false);
				callbackContext.sendPluginResult(pluginResult);
				return;	
			}
		}
		else {
			// Notify user of added service(s) and save callback context
			serverRunningCallbackContext = callbackContext;
			addProperty(returnObj, keyStatus, statusServiceExists);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);		// Save the callback so it can be invoked several times
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			return;
		}
		
		//Save the callback context for setting up GATT server
		serverRunningCallbackContext = callbackContext;
		
		// Add Immediate Alert service (this will call the implementation od the onServiceAdded callback)
		gattServer.addService(immediateAlertService); 
	}
	
	private void resetAlarmAction(CallbackContext callbackContext)
	{
		// Resets the Immediate Alert Service initialized flag.
		// Should be called after a client has disconnected since when a nRF8002 module connects to the GATT server running
		// Immediate Alert Service, it writes it's current alert level (always "No Alert", that is alert level 0). This must
		// not be interpreted as an alert.
		
		iasInitialized = false;
		
		// Release the wake lock if it has been acquired but not yet released
		if (wakeLock.isHeld())
			wakeLock.release();
		
		// Section below removed 2017-01-24
		/*final BluetoothGattService iaService = gattServer.getService(IMMEDIATE_ALERT_SERVICE_UUID);
		if (iaService != null){
			final BluetoothGattCharacteristic alertLevelChar = iaService.getCharacteristic(ALERT_LEVEL_CHAR_UUID);
			if (alertLevelChar != null)
				alertLevelChar.setValue(ALERT_LEVEL_LOW);
		}*/
			
		// Notify user of reseted alarm
		JSONObject returnObj = new JSONObject();
		addProperty(returnObj, keyStatus, statusAlarmReseted);
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
		pluginResult.setKeepCallback(false);
		callbackContext.sendPluginResult(pluginResult);
	}
	
	private void alarmAction(CallbackContext callbackContext)
	{
		// Debug action function just to test local notifications from outside the plugin (can remove)
		
		// Show local notification
		/*long[] pattern = { 0, 200, 500 };
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext())
	        .setContentTitle("SenseSoft Notifications Mini")
	        .setContentText("Incoming SenseSoft Mini alarm!")
	        .setSmallIcon(cordova.getActivity().getApplicationContext().getApplicationInfo().icon)
	        .setPriority(NotificationCompat.PRIORITY_MAX)
	        //.setAutoCancel(true)
	        .setCategory(NotificationCompat.CATEGORY_ALARM)
	        .setGroup("SENSESOFT_MINI")
	        .setTicker("SenseSoft Mini")
	        .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_LIGHTS)
	        .setVibrate(pattern);
		
		//NotificationManager alarmNotificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the notification later on
		alarmNotificationManager.notify(1665, mBuilder.build());*/
	}
  	
	
	/*********************************************************************************************************************
	Helpers
	*********************************************************************************************************************/
	
	private void alarm(final String alertLevel, final String deviceUUID){
		
		if (isInBackground && NotificationManagerCompat.from(cordova.getActivity().getApplicationContext()).areNotificationsEnabled()){
			// Show local notification only if the app is in the background and notifications are enabled for the app.
			// The function areNotificationsEnabled() from the support library returns true if notifications are
			// enabled for the app and if API >= 19. If Api < 19 it will always return true (even if notifications
			// actually are disabled for the app).
			/*long[] pattern = { 0, 200, 500 };
			//Intent appActivity = cordova.getActivity().getApplicationContext().getPackageManager().getLaunchIntentForPackage(cordova.getActivity().getApplicationContext().getPackageName()); // If used, app will be restarted
			Intent appIntent = cordova.getActivity().getIntent();	// If used, will start app if not running otherwise bring it to the foreground
			appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			
			NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext())
			.setContentTitle("SenseSoft Notifications Mini")
			.setContentText("Incoming SenseSoft Mini alarm.")
			.setContentIntent(PendingIntent.getActivity(cordova.getActivity().getApplicationContext(), 0, appIntent, 0))
			.setSmallIcon(cordova.getActivity().getApplicationContext().getApplicationInfo().icon)
			//.setPriority(NotificationCompat.PRIORITY_MAX)
			//.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			//.setOngoing(true)
			.setAutoCancel(true)			// Not really needed since also clearing notifications when app is brought to foreground
			//.setOnlyAlertOnce(true)		// Set this flag if you would only like the sound, vibrate and ticker to be played if the notification is not already showing. 
			.setCategory(NotificationCompat.CATEGORY_ALARM)
			.setGroup("SENSESOFT_MINI")
			.setTicker("SenseSoft Mini");
			mBuilder.setVibrate(pattern);
			//mBuilder.setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_LIGHTS);	// Use instead of below to use the default notification sound
			Uri soundPath = Uri.parse("android.resource://" + cordova.getActivity().getApplicationContext().getPackageName() + "/raw/crash_short");
			//Uri soundPath = Uri.parse("android.resource://" + cordova.getActivity().getApplicationContext().getPackageName() + "/" + R.raw.crash_short);	// Also works if com.sensiblesolutions.sensesoftnotificationsmini.R has been imported
			//mBuilder.setSound(soundPath, AudioManager.STREAM_ALARM);	// If using this then the volume has to be changed with the device's alarm volume controllers
			mBuilder.setSound(soundPath, AudioManager.STREAM_NOTIFICATION);	// Use for all sounds (so volume easily can be changed with the device's notification volume controller)
			*/
			//NotificationManager mNotificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
			
			// Show local notification or update any on going one (no need to stop any sound playing since it will be replaced with the new sound)
			alarmNotification.when = System.currentTimeMillis();		// Set the time of the notification since was set when building the notification (default)
			alarmNotificationManager.notify(1665, alarmNotification);	// mId (here 1665) allows you to update any current notification with same mId (no need to stop sound)
			//alarmNotificationManager.notify(1665, mBuilder.build());	// mId (here 1665) allows you to update any current notification with same mId (no need to stop sound)
		}
		else if(!isInBackground){
			// Manually play alarm sound if app is in the foreground
			
			//Uri soundPath = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);	// Use when playing default notification
			//Uri soundPath = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);		// Use when playing default alarm
			//Uri soundPath = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);		// Use when playing default ringtone
			Uri soundPath = Uri.parse("android.resource://" + cordova.getActivity().getApplicationContext().getPackageName() + "/raw/crash_short");	// Use when playing own sound file (important: do NOT include file type extension!)
			// Below compiles if you import com.sensiblesolutions.sensesoftnotificationsmini.R (do NOT import android.R!)
			//Uri soundPath = Uri.parse("android.resource://" + cordova.getActivity().getApplicationContext().getPackageName() + "/" + R.raw.crash_short);
			// Below compiles if you do not import com.sensiblesolutions.sensesoftnotificationsmini.R
			//Uri soundPath = Uri.parse("android.resource://" + cordova.getActivity().getApplicationContext().getPackageName() + "/" + com.sensiblesolutions.sensesoftnotificationsmini.R.raw.crash_short);
			
			//showDebugMsgBox("soundPath: " + soundPath.toString());
			
			MediaPlayer mediaPlayer = new MediaPlayer();
			try {
				mediaPlayer.setDataSource(cordova.getActivity().getApplicationContext(), soundPath);
				// Use the notification stream when playing all sounds (notification, alarm, ringtone and own sound) so user easily can change the volume for all sounds with the device's notification volume controllers
				mediaPlayer.setAudioStreamType(AudioManager.STREAM_NOTIFICATION);
				//mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
				//mediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
				mediaPlayer.setLooping(false);
				mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
					@Override
					public void onCompletion(MediaPlayer mp)
					{
						mp.stop();
						//mp.release();
						//mp.reset();
					}
				});
				mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
					@Override
    					public void onPrepared(MediaPlayer mp) {
						// Called when MediaPlayer is ready
        					mp.start();
						// Vibrate the device if it has hardware vibrator and permission
						vibrateDevice();
   				 	}
				});
				//mediaPlayer.prepare();
				mediaPlayer.prepareAsync();	// prepare async to not block main thread
				
			} catch (Exception ex) {
				// Do nothing
				showDebugMsgBox("Error playing sound: " + ex.getMessage());
			}
		}
		
		// Notify user of started server and save callback
		JSONObject returnObj = new JSONObject();
		addProperty(returnObj, keyStatus, statusWriteRequest);
		addProperty(returnObj, "device", deviceUUID);
		addProperty(returnObj, "characteristic", ALERT_LEVEL_CHAR_UUID.toString());
		addProperty(returnObj, "value", alertLevel);
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
		pluginResult.setKeepCallback(true);		// Save the callback so it can be invoked several times
		serverRunningCallbackContext.sendPluginResult(pluginResult);
	}
	
	private void initAlarmNotification()
	{	
		// Builds the alarms notification
		
		//long[] pattern = {0, 1000, 1000};
		//long[] pattern_on = {0, 1000};		// Vibrate directly for 1000 ms
		//long[] pattern_off = {0, 0};		// Turns off vibration (must test if it works)
	
		//Intent appActivity = cordova.getActivity().getApplicationContext().getPackageManager().getLaunchIntentForPackage(cordova.getActivity().getApplicationContext().getPackageName()); // If used, app will always be restarted (even if it's already running)
		Intent appIntent = cordova.getActivity().getIntent();	// If used, will start app if not running otherwise bring it to the foreground
		appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		
		
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext()) // Automatically sets the when field (displayed time of notification) to System.currentTimeMillis() 
		.setContentTitle("SenseSoft Mini")
		.setContentText("Incoming SenseSoft Mini alarm.")
		.setContentIntent(PendingIntent.getActivity(cordova.getActivity().getApplicationContext(), 0, appIntent, 0))
		.setSmallIcon(cordova.getActivity().getApplicationContext().getApplicationInfo().icon)
		.setPriority(NotificationCompat.PRIORITY_HIGH)			// PRIORITY_HIGH and PRIORITY_MAX will result in a heads-up notification in Android >= 5
		//.setOngoing(true)
		.setAutoCancel(true)			// Not really needed since also clearing notifications when app is brought to foreground
		//.setOnlyAlertOnce(true)		// Set this flag if you would only like the sound, vibrate and ticker to be played if the notification is not already showing. 
		.setCategory(NotificationCompat.CATEGORY_ALARM)
		.setGroup("SENSESOFT_MINI")
		.setTicker("SenseSoft Mini")
		.setShowWhen(true);			// Default is false in Android >= 5 and true in Android < 5
		/*if (myAppSettings.vibration){
			mBuilder.setVibrate(pattern_on);	// Will vibrate on a notification if device has hardware vibrator and it's turned on in the app settings
		}
		else {
			mBuilder.setVibrate(pattern_off);	// Turns off vibration (must test if it works)
		}*/
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
			mBuilder.setVisibility(Notification.VISIBILITY_PRIVATE);	// Show this notification on all lockscreens, but conceal sensitive or private information on secure lockscreens
		}
		
		alarmNotification = mBuilder.build();
		this.setAlarmNotificationSound(myAppSettings.sound);
		this.setAlarmNotificationVibrate(myAppSettings.vibration);
	}

	private void setAlarmNotificationSound(final AlarmSound sound)
	{
		Uri soundPath = null;
		
		switch (sound) {
			case SOUND_0:
				// Custom sound 1
				soundPath = Uri.parse("android.resource://" + cordova.getActivity().getApplicationContext().getPackageName() + "/raw/alarm");
				//mBuilder.setSound(soundPath, AudioManager.STREAM_NOTIFICATION);	// Use the notification stream for playback so volume easily can be changed with the device's notification volume controller
				break;
			case SOUND_1:
				// Custom sound 2
				soundPath = Uri.parse("android.resource://" + cordova.getActivity().getApplicationContext().getPackageName() + "/raw/crash_short");
				//Uri soundPath = Uri.parse("android.resource://" + cordova.getActivity().getApplicationContext().getPackageName() + "/" + R.raw.crash_short);	// Also works if com.sensiblesolutions.sensesoftnotificationsmini.R has been imported
				//mBuilder.setSound(soundPath, AudioManager.STREAM_ALARM);	// If using this then the volume has to be changed with the device's alarm volume controllers
				//mBuilder.setSound(soundPath, AudioManager.STREAM_NOTIFICATION);	// Use the notification stream for playback so volume easily can be changed with the device's notification volume controller
				break;
			case SOUND_NOTIFICATION:
				// Device default notification sound
				soundPath = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				//mBuilder.setSound(soundPath, AudioManager.STREAM_NOTIFICATION);	// Use the notification stream for playback so volume easily can be changed with the device's notification volume controller
				break;
			case SOUND_RINGTONE:
				// Device default ringtone (only available on phones and not tablets)
				soundPath = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
				//mBuilder.setSound(soundPath, AudioManager.STREAM_NOTIFICATION);	// Use the notification stream for playback so volume easily can be changed with the device's notification volume controller
				break;
			case SOUND_ALARM:
				// Device default alarm sound
				soundPath = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
				//mBuilder.setSound(soundPath, AudioManager.STREAM_NOTIFICATION);	// Use the notification stream for playback so volume easily can be changed with the device's notification volume controller
				break;
			case SOUND_OFF:
				// No sound
				//mBuilder.setSound(soundPath, AudioManager.STREAM_NOTIFICATION); // Not sure it works by setting soundPath to null (test it!)
			default:
				// Device default notification sound
				soundPath = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
				//mBuilder.setSound(soundPath, AudioManager.STREAM_NOTIFICATION);	// Use the notification stream for playback so volume easily can be changed with the device's notification volume controller
				
		}
		
		alarmNotification.sound = soundPath;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP){
			// Use the notification stream for playback so volume easily can be changed with the device's notification volume controller
			AudioAttributes aAttributes = new AudioAttributes.Builder(alarmNotification.audioAttributes)
			.setUsage(AudioAttributes.USAGE_NOTIFICATION)
			.build();
			alarmNotification.audioAttributes = aAttributes;
		}
		else {
			// Use the notification stream for playback so volume easily can be changed with the device's notification volume controller
			alarmNotification.audioStreamType = AudioManager.STREAM_NOTIFICATION;
		}
	}
	
	
	private void setAlarmNotificationVibrate(final boolean vibrate)
	{
		//long[] pattern = {0, 1000, 1000};
		long[] pattern_on = {0, 1000};		// Vibrate directly for 1000 ms
		long[] pattern_off = {0, 0};		// Turns off vibration (must test if it works!)
		
		if (vibrate)
			alarmNotification.vibrate = pattern_on;
		else
			alarmNotification.vibrate = pattern_off;
	}
	
	private void vibrateDevice()
	{
		if (myAppSettings.vibration){
			// Check if device has vibrator and permission
			Vibrator vib = (Vibrator) cordova.getActivity().getSystemService(Context.VIBRATOR_SERVICE);
			if (vib.hasVibrator()){
				if (ContextCompat.checkSelfPermission(cordova.getActivity(), permission.VIBRATE) != PackageManager.PERMISSION_GRANTED){
					// Vibrate (works async)
					//long[] pattern = {0, 1000, 1000};
					//long[] pattern = {0, 1000};	// Vibrate directly for 1000 ms
					vib.vibrate(1000);		// Vibrate directly for 1000 ms
					//vib.vibrate(pattern, -1);	// -1 disables repeating (0 repeats)
				}
			}
		}
	}
	
	private void addProperty(JSONObject obj, String key, Object value)
	{
		try {
			obj.put(key, value);
		}
		catch (JSONException e)
		{ /* Ignore */ }
	}
  
	private String parseCharacteristicValue(final int value)
	{
		switch (value) {
			case 0:
				return "No Alert";
			case 1:
				return "Mild Alert";
			case 2:
				return "High Alert";
			default:
				return "Parse Error";
		}
	}
	
	private synchronized void showDebugMsgBox(final String message)
	{
		Runnable runnable = new Runnable() {
            		public void run() {
				AlertDialog.Builder debugAlert  = new AlertDialog.Builder(cordova.getActivity());
				debugAlert.setMessage(message);
				debugAlert.setTitle("Debug SSNM");
				debugAlert.setCancelable(false);
				debugAlert.setNeutralButton("OK", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
					}
				});
				debugAlert.create().show();
           		 };
		};
		cordova.getActivity().runOnUiThread(runnable);	// Run it on the ui thread as cordova plugins runs on the WebCore thread (also the plugin's JavaScript runs on the WebCore thread).
	}
	
	
	/*********************************************************************************************************************
	Cordova Plugin (see CordovaPlugin.java)
	*********************************************************************************************************************/
	
	@Override
	 protected void pluginInitialize() {
	 	// Called after plugin construction and fields have been initialized
		isInBackground = false;		// App is in foreground
		myAppSettings = new AppSettings();
		// Need a wakelock to keep the cpu running so bluetooth connection doesn't disconnects when device goes to "sleep" 
		PowerManager powerManager = (PowerManager) cordova.getActivity().getSystemService(Context.POWER_SERVICE);
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SSMWakelockTag");
		wakeLock.setReferenceCounted(false);

		initAlarmNotification();
		alarmNotificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
		//mediaPlayer = new MediaPlayer();
		
		super.pluginInitialize();
		showDebugMsgBox("pluginInitialize() called!");
	 }
	
	@Override
	public void onDestroy() {
		 // The final call you receive before your activity is destroyed
		super.onDestroy();
		// Release the wake lock if it has been acquired but not yet released
		if (wakeLock.isHeld())
			wakeLock.release();
	}
	
	/*@Override
	 public void onStart() {
		 // Called when the activity is becoming visible to the user
		 super.onStart();
    	}*/
	
	@Override
	 public void onStop() {
		// Called when the activity is no longer visible to the user
		//NotificationManager alarmNotificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
		alarmNotificationManager.cancelAll();
		super.onStop();
		//showDebugMsgBox("onStop() called!");
   	 }
	
	@Override
	public void onPause(boolean multitasking) {
		// Called when the system is about to start resuming a previous activity
		isInBackground = true;		// App is put in background
		super.onPause(multitasking);
		showDebugMsgBox("onPause() called!");
    	}
	
	@Override
	public void onResume(boolean multitasking) {
		// Called when the activity will start interacting with the user
		isInBackground = false;		// App is put in foreground
		//NotificationManager alarmNotificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
		alarmNotificationManager.cancelAll();
		super.onResume(multitasking);
		showDebugMsgBox("onResume() called!");
    	}
	
	@Override
    	public void onReset() {
		// Called when the WebView does a top-level navigation or refreshes
		// Plugins should stop any long-running processes and clean up internal state
		super.onReset();
		showDebugMsgBox("onReset() called!");
	}
}
