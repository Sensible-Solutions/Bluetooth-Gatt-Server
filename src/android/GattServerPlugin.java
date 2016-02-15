/*
* Copyright (C) 2015-2016 Sensible Solutions Sweden AB
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
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;
//import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.app.NotificationCompat;
import android.app.NotificationManager;
//import android.app.Notification;
import android.R;
import android.app.AlertDialog;			// For showing debug messaages
import android.content.DialogInterface;		// For showing debug messaages

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class GattServerPlugin extends CordovaPlugin
{
	// Immediate alert service
	private final static UUID IMMEDIATE_ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");		// Service UUID
	private final static UUID ALERT_LEVEL_CHAR_UUID = UUID.fromString("00002A06-0000-1000-8000-00805f9b34fb");				// Characteristic UUID
	//private static final int ALERT_LEVEL_CHARACTERISTIC_VALUE = 2;
	//private static final int ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE = 17;
	//private static final int ALERT_LEVEL_CHARACTERISTIC_OFFSET = 0;
	
	// Immediate alert levels
	private final static byte[] ALERT_LEVEL_LOW = {0x00};			// No alert
	private final static byte[] ALERT_LEVEL_MILD = {0x01};
	private final static byte[] ALERT_LEVEL_HIGH = {0x02};
	
	// Linkloss service
	private final static UUID LINKLOSS_SERVICE_UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb");				// Service UUID
	
	// General callback variables
	private CallbackContext serverRunningCallbackContext = null;
	
	// Action Name Strings
	//private final String initializeActionName = "initialize";
	private final static String START_GATT_SERVER = "startServer";
	
	// Object keys
	private final static String keyStatus = "status";
	private final static String keyError = "error";
	private final static String keyMessage = "message";
	
	// Status Types
	private final static String statusServiceAdded = "serviceAdded";
	private final static String statusServiceExists = "serviceAlreadyProvided";
	private final static String statusWriteRequest = "characteristicWriteRequest";
	private final static String statusConnectionState = "serverConnectionState";
	//private final String statusServerStopped = "scanStopped";
  
	// Error Types
	//private final String errorInitialize = "initialize";
	//private final static String errorStartServer = "startServer";
	private final static String errorConnectionState = "serverConnectionState";
	//private final static String errorGattServer = "gattServer";	// Added 2016-01-14
	private final static String errorServerState = "serverState";	// Added 2016-01-19
	private final static String errorServerStateOff = "serverStateOff";
	private final static String errorServerStateUnsupported = "serverStateUnsupported";
	private final static String errorServerStateUnauthorized = "serverStateUnauthorized";	// iOS only
	private final static String errorServiceAdded = "serviceAdded"; // Added 2016-01-19
	
	// Error Messages
	private final static String logServerAlreadyRunning = "GATT server is already running";
	private final static String logService = "Immediate Alert service could not be added";
	private final static String logConnectionState = "Connection state changed with error";
	private final static String logStateUnsupported = "BLE is not supported by device";	// Added 2016-01-14
	private final static String logStatePoweredOff = "BLE is turned off for device";	// Added 2016-01-14
	
	//private BluetoothGattServer gattServer;
	private BluetoothGattServer gattServer = null;		// Added 2016-01-19 instead of the line above
	//private BluetoothGattService immediateAlertService;
	
	// Bluetooth GATT interface callbacks
	private final BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {
		
		// Remote client characteristic write request
		@Override
		public void onCharacteristicWriteRequest(final BluetoothDevice device, final int requestId, final BluetoothGattCharacteristic characteristic, final boolean preparedWrite, final boolean responseNeeded, final int offset, final byte[] value) {
			//super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
			characteristic.setValue(value);
			
			try {
	    			Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
	    			Ringtone r = RingtoneManager.getRingtone(cordova.getActivity().getApplicationContext(), notification);
	    			r.play();
			} catch (Exception e) {
	    			
			}
			//super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
				
			//Notify user of started server and save callback
			JSONObject returnObj = new JSONObject();
			addProperty(returnObj, keyStatus, statusWriteRequest);
			addProperty(returnObj, "device", device.getAddress());
			addProperty(returnObj, "characteristic", characteristic.getUuid().toString());
			addProperty(returnObj, "value", parseCharacteristicValue(characteristic));
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			//callbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext.sendPluginResult(pluginResult);

			if (responseNeeded)
				gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
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
			
			JSONObject returnObj = new JSONObject();
			// If statement below added 2016-01-19 for testing
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
				pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
				serverRunningCallbackContext.sendPluginResult(pluginResult);	
			}
			
		}

		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
			// Not implemented
		}
			

		@Override
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
			// Not supported
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
		}

		@Override
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			// Not supported
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
		}

		@Override
		public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
			// Not supported
			gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null);
		}
	};
	
	//Actions
	@Override
	public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException
	{
		try {
			if (START_GATT_SERVER.equals(action)) { 
				startServerAction(callbackContext);
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
		/*AlertDialog.Builder debugAlert  = new AlertDialog.Builder(cordova.getActivity());
		//if(gattServer == null)
		//	debugAlert.setMessage("gattServer is null!");
		if(serverRunningCallbackContext == null)
			debugAlert.setMessage("serverRunningCallbackContext is null!");
		else
			debugAlert.setMessage("not null!");
		debugAlert.setTitle("GattServerPlugin Debug");
		debugAlert.setCancelable(false);
		//dlgAlert.setPositiveButton("OK", null);
		debugAlert.setNeutralButton("OK", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int id) {
		          	dialog.dismiss();  
		        }
		});
		debugAlert.create().show();*/
		
		
		JSONObject returnObj = new JSONObject();
		
		// If statement below added 2016-01-19 (moved up here 2016-01-21)
		//BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		//if (mBluetoothAdapter == null) {
		if(BluetoothAdapter.getDefaultAdapter() == null){
		    	// Device does not support Bluetooth
		    	//Notify user of unsupported Bluetooth
			addProperty(returnObj, keyError, errorServerState);
			addProperty(returnObj, keyMessage, logStateUnsupported);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(false);			// Save the callback so it can be invoked several times
			callbackContext.sendPluginResult(pluginResult);
			//serverRunningCallbackContext.sendPluginResult(pluginResult);
			//serverRunningCallbackContext = null;
			return;
		} 
		else {
			if(!BluetoothAdapter.getDefaultAdapter().isEnabled()){
			        // Bluetooth is not enabled
			        //Notify user that Bluetooth is not enabled
				addProperty(returnObj, keyError, errorServerState);
				addProperty(returnObj, keyMessage, logStatePoweredOff);
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
				pluginResult.setKeepCallback(false);			// Save the callback so it can be invoked several times
				callbackContext.sendPluginResult(pluginResult);
				//serverRunningCallbackContext.sendPluginResult(pluginResult);
				//serverRunningCallbackContext = null;
				return;
		    	}
		    	// Test 2016-01-26
		    	// See https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/bluetooth
			//BluetoothLeScanner scanner = getBluetoothLeScanner();
			//scanner.cleanup();
			//BluetoothAdapter.getDefaultAdapter().onBluetoothServiceDown();
			// end test
		}
		
		
		//If the GATT server is already running, don't start it again. Invoke the success callback and return
		//if (serverRunningCallbackContext != null)
		if((gattServer != null) && (serverRunningCallbackContext != null))
		{
			addProperty(returnObj, keyStatus, statusServiceExists);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			//callbackContext.error(returnObj);
			//serverRunningCallbackContext.error(returnObj);	// Added 7/8 instead of line above
			return;
		}
		
		//Save the callback context for setting up GATT server
		//serverRunningCallbackContext = callbackContext;
		
		// If statement below added 2016-01-19
		//BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		//if (mBluetoothAdapter == null) {
		/*if(BluetoothAdapter.getDefaultAdapter() == null){
		    	// Device does not support Bluetooth
		    	//Notify user of unsupported Bluetooth
			addProperty(returnObj, keyError, errorServerState);
			addProperty(returnObj, keyMessage, logStateUnsupported);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(false);			// Save the callback so it can be invoked several times
			//callbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext = null;
			return;
		} 
		else {
		    //if (!mBluetoothAdapter.isEnabled()) {
		    if(!BluetoothAdapter.getDefaultAdapter().isEnabled()){
		        // Bluetooth is not enabled
		        //Notify user that Bluetooth is not enabled
			addProperty(returnObj, keyError, errorServerState);
			addProperty(returnObj, keyMessage, logStatePoweredOff);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(false);			// Save the callback so it can be invoked several times
			//callbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext = null;
			return;
		    }
		}*/
		
		final BluetoothManager bluetoothManager = (BluetoothManager) cordova.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
		
		if(gattServer == null)
			gattServer = bluetoothManager.openGattServer(cordova.getActivity().getApplicationContext(), mBluetoothGattServerCallback);
		if(gattServer == null){		// If statement added 2016-01-14
			//Notify user of unsupported Bluetooth Smart
			addProperty(returnObj, keyError, errorServerState);
			addProperty(returnObj, keyMessage, logStateUnsupported);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(false);					// Save the callback so it can be invoked several times
			callbackContext.sendPluginResult(pluginResult);
			//serverRunningCallbackContext.sendPluginResult(pluginResult);
			//serverRunningCallbackContext = null;
			return;
		}
		
		// Create an Immediate Alert service if not already provided by the device
		final BluetoothGattService immediateAlertService = new BluetoothGattService(IMMEDIATE_ALERT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
		if(gattServer.getService(IMMEDIATE_ALERT_SERVICE_UUID) == null){
			final BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(ALERT_LEVEL_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
			//characteristic.setValue(ALERT_LEVEL_CHARACTERISTIC_VALUE, ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE, ALERT_LEVEL_CHARACTERISTIC_OFFSET);
			characteristic.setValue(ALERT_LEVEL_HIGH);
			//immediateAlertService.addCharacteristic(characteristic);
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
			//Notify user of added service(s) and save callback
			//Save the callback context
			serverRunningCallbackContext = callbackContext;
			addProperty(returnObj, keyStatus, statusServiceExists);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			//callbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext.sendPluginResult(pluginResult);	// Added 7/8 instead of line above
			return;
		}
		
		//Save the callback context for setting up GATT server
		serverRunningCallbackContext = callbackContext;
		
		// Add Immediate Alert service, notify user and save callback
		gattServer.addService(immediateAlertService);	// Added 2016-01-19 instead of if statement below // Will call onServiceAdded callback 
		/*if(gattServer.addService(immediateAlertService)) {
			addProperty(returnObj, keyStatus, statusServiceAdded);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			serverRunningCallbackContext.sendPluginResult(pluginResult);
		}
		else {
			addProperty(returnObj, keyError, errorServiceAdded);
			addProperty(returnObj, keyMessage, logService);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			PluginResult.setKeepCallback(false);
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext = null;
			return;
		}*/
		
		// Test
		//BluetoothAdapter bluetoothAdapter;
		//bluetoothAdapter = bluetoothManager.getAdapter();
		//BluetoothDevice device = bluetoothAdapter.getRemoteDevice("D8:35:DA:54:1E:55");
		//gattServer.connect(device, false);
	}
	
	private void alarmAction(CallbackContext callbackContext)
	{
		// Function is just so can test notifications from outside the plugin
		long[] pattern = { 0, 200, 500 };
		//NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(cordova.getActivity().getApplicationContext())
	        .setContentTitle("SenseSoft Notifications")
	        .setContentText("Incoming SenseSoft Mini alarm!")
	        //.setSmallIcon(R.drawable.screen_background_dark)
	        .setSmallIcon(cordova.getActivity().getApplicationContext().getApplicationInfo().icon)
	        .setPriority(NotificationCompat.PRIORITY_MAX)
	        //.setAutoCancel(true)
	        .setCategory(NotificationCompat.CATEGORY_ALARM)
	        .setGroup("SENSESOFT_MINI")
	        .setTicker("SenseSoft Mini")
	        .setDefaults(NotificationCompat.DEFAULT_SOUND | NotificationCompat.DEFAULT_LIGHTS)
	        .setVibrate(pattern);
	        //.setFullScreenIntent(PendingIntent intent, boolean highPriority)
	        //.setSound(Uri sound, STREAM_ALARM);
		
		//NotificationManager mNotificationManager = (NotificationManager) Context.getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationManager mNotificationManager = (NotificationManager) cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the notification later on.
		mNotificationManager.notify(1665, mBuilder.build());
	}
  
	  private void addProperty(JSONObject obj, String key, Object value)
	  {
			try
			{
			  obj.put(key, value);
			}
			catch (JSONException e)
			{ /* Ignore */ }
	  }
  
	private String parseCharacteristicValue(final BluetoothGattCharacteristic characteristic)
	{
		if (characteristic == null)
			return "";

		if (characteristic.getUuid() == ALERT_LEVEL_CHAR_UUID) {
			final int value = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
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
		else	
			return characteristic.getStringValue(0);
	}
	
	// Plugin initialize method for any start-up logic (see https://cordova.apache.org/docs/en/5.0.0/guide/platforms/android/plugin.html)
	/*@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    		super.initialize(cordova, webView);
    		// your init code here
	}*/
}
