/*
* Copyright (C) 2015 Sensible Solutions Sweden AB
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
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattDescriptor;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class GattServerPlugin extends CordovaPlugin
{
	// Service UUID:s
	// Immediate alert service
	private static final UUID IMMEDIATE_ALERT_SERVICE = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
	
	// Service characteristics
	// Immediate alert service
	private static final UUID ALERT_LEVEL_CHAR = UUID.fromString("00002A06-0000-1000-8000-00805f9b34fb");
	private static final int ALERT_LEVEL_CHARACTERISTIC_VALUE = 2;
	private static final int ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE = 17;
	private static final int ALERT_LEVEL_CHARACTERISTIC_OFFSET = 0;
	
	// Immediate alert levels
	private static final byte[] ALERT_LEVEL_LOW = new byte[] {0x00};
	private static final byte[] ALERT_LEVEL_MILD = new byte[] {0x01};
	private static final byte[] ALERT_LEVEL_HIGH = new byte[] {0x02};
	
	// General callback variables
	private CallbackContext serverRunningCallbackContext = null;
	
	// Action Name Strings
	//private final String initializeActionName = "initialize";
	private final String START_GATT_SERVER = "startServer";
	
	// Object keys
	private final String keyStatus = "status";
	private final String keyError = "error";
	private final String keyMessage = "message";
	
	// Status Types
	private final String statusServerStarted = "serverStarted";
	private final String statusWriteRequest = "serverRemoteWriteRequest";
	//private final String statusServerStopped = "scanStopped";
  
	// Error Types
	//private final String errorInitialize = "initialize";
	private final String errorStartServer = "startServer";
	
	// Error Messages
	private final String logServerAlreadyRunning = "GATT server is already running";
	
	// Bluetooth GATT interface callbacks
	private final BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {
		
		@Override
		public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
			// Not implemented
		}

		@Override
		public void onServiceAdded(int status, BluetoothGattService service) {
			// Not implemented
		}

		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
			// Not implemented
		}
			
		// Remote client characteristic write request
		@Override
		public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
			
			try {
	    			Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
	    			Ringtone r = RingtoneManager.getRingtone(cordova.getActivity().getApplicationContext(), notification);
	    			r.play();
			} catch (Exception e) {
	    			
			}
				
			//Notify user of started server and save callback
			JSONObject returnObj = new JSONObject();
			addProperty(returnObj, keyStatus, statusWriteRequest);
			addProperty(returnObj, "device", device.getAddress());
			addProperty(returnObj, "characteristic", characteristic.getUuid());
			addProperty(returnObj, "value", value.toString());
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			//callbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext.sendPluginResult(pluginResult);		
		}

		@Override
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
			// Not implemented
		}

		@Override
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			// Not implemented
		}

		@Override
		public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
			// Not implemented
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
			callbackContext.error("Invalid action");
			return false;
		} 
		catch(Exception e) {
			System.err.println("Exception: " + e.getMessage());
			callbackContext.error(e.getMessage());
			return false;
		} 
	}
	
	private void startServerAction(CallbackContext callbackContext)
	{
		/*if (isNotInitialized(callbackContext, true))
		{
			return;
		}*/
		try {
    			Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    			Ringtone r = RingtoneManager.getRingtone(cordova.getActivity().getApplicationContext(), notification);
    			r.play();
		} catch (Exception e) {
    			
		}

		JSONObject returnObj = new JSONObject();
		
		//If the GATT server is already running, don't start it again. Invoke the error callback and return
		if (serverRunningCallbackContext != null)
		{
			addProperty(returnObj, keyError, errorStartServer);
			addProperty(returnObj, keyMessage, logServerAlreadyRunning);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			//callbackContext.error(returnObj);
			//serverRunningCallbackContext.error(returnObj);	// Added 7/8 instead of line above
			return;
		}
		
		final BluetoothManager bluetoothManager = (BluetoothManager) cordova.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
		
		/*BluetoothGattServerCallback mBluetoothGattServerCallback = new BluetoothGattServerCallback() {
		
			@Override
			public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
				// Not implemented
			}

			@Override
			public void onServiceAdded(int status, BluetoothGattService service) {
				// Not implemented
			}

			@Override
			public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
				// Not implemented
			}
			
			// Remote client characteristic write request
			@Override
			public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
				super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
				
				
				
			}

			@Override
			public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
				// Not implemented
			}

			@Override
			public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
				// Not implemented
			}

			@Override
			public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
				// Not implemented
			}
		};*/
		
		// Create and add an Immediate Alert service with proper characteristic
		BluetoothGattServer gattServer = bluetoothManager.openGattServer(cordova.getActivity().getApplicationContext(), mBluetoothGattServerCallback);
		BluetoothGattService service = new BluetoothGattService(IMMEDIATE_ALERT_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);
		BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(ALERT_LEVEL_CHAR, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
		characteristic.setValue(ALERT_LEVEL_CHARACTERISTIC_VALUE, ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE, ALERT_LEVEL_CHARACTERISTIC_OFFSET);
		service.addCharacteristic(characteristic);
		gattServer.addService(service);
		
		//Save the callback context for setting up GATT server
		serverRunningCallbackContext = callbackContext;

		//Notify user of started server and save callback
		addProperty(returnObj, keyStatus, statusServerStarted);
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
		pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
		//callbackContext.sendPluginResult(pluginResult);
		serverRunningCallbackContext.sendPluginResult(pluginResult);	// Added 7/8 instead of line above
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
}
