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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public class GattServerPlugin extends CordovaPlugin
{
	// Immediate alert service
	public final static UUID IMMEDIATE_ALERT_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");		// Service UUID
	private final static UUID ALERT_LEVEL_CHAR_UUID = UUID.fromString("00002A06-0000-1000-8000-00805f9b34fb");				// Characteristic UUID
	//private static final int ALERT_LEVEL_CHARACTERISTIC_VALUE = 2;
	//private static final int ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE = 17;
	//private static final int ALERT_LEVEL_CHARACTERISTIC_OFFSET = 0;
	
	// Immediate alert levels
	private final static byte[] ALERT_LEVEL_LOW = {0x00};			// No alert
	private final static byte[] ALERT_LEVEL_MILD = {0x01};
	private final static byte[] ALERT_LEVEL_HIGH = {0x02};
	
	// Linkloss service
	public final static UUID LINKLOSS_SERVICE_UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb");				// Service UUID
	
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
	private final String statusServiceAdded = "serviceAdded";
	private final String statusServiceExists = "serviceAlreadyProvided";
	private final String statusWriteRequest = "serviceRemoteWriteRequest";
	private final String statusConnectionState = "serverConnectionState";
	//private final String statusServerStopped = "scanStopped";
  
	// Error Types
	//private final String errorInitialize = "initialize";
	private final String errorStartServer = "startServer";
	private final String errorConnectionState = "serverConnectionState";
	
	// Error Messages
	private final String logServerAlreadyRunning = "GATT server is already running";
	private final String logService = "Immediate Alert service could not be added";
	private final String logConnectionState = "Connection state changed with error";
	
	private BluetoothGattServer gattServer;
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
			addProperty(returnObj, "value", value.toString());
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			//callbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext.sendPluginResult(pluginResult);

			if (responseNeeded)
				gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
		}
		
		@Override
		public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
			
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
				pluginResult.setKeepCallback(true);	
				serverRunningCallbackContext.sendPluginResult(pluginResult);
			}
		}

		@Override
		public void onServiceAdded(int status, BluetoothGattService service) {
			// Not implemented
			/*try {
	    			Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
	    			Ringtone r = RingtoneManager.getRingtone(cordova.getActivity().getApplicationContext(), notification);
	    			r.play();
			} catch (Exception e) {
	    			
			}
			try {
				 super.onServiceAdded(status, service);
            } catch (Exception ex) {
				System.err.println("Exception: " + ex.getMessage());
				serverRunningCallbackContext.error(ex.getMessage());
				return;
			}*/
			
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
		JSONObject returnObj = new JSONObject();
		
		//If the GATT server is already running, don't start it again. Invoke the success callback and return
		if (serverRunningCallbackContext != null)
		{
			addProperty(returnObj, keyStatus, statusServiceExists);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			//callbackContext.error(returnObj);
			//serverRunningCallbackContext.error(returnObj);	// Added 7/8 instead of line above
			return;
		}
		
		final BluetoothManager bluetoothManager = (BluetoothManager) cordova.getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
		
		//Save the callback context for setting up GATT server
		serverRunningCallbackContext = callbackContext;
		
		gattServer = bluetoothManager.openGattServer(cordova.getActivity().getApplicationContext(), mBluetoothGattServerCallback);
		// Create an Immediate Alert service if not already provided by the device
		final BluetoothGattService immediateAlertService = new BluetoothGattService(IMMEDIATE_ALERT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
		if(gattServer.getService(IMMEDIATE_ALERT_SERVICE_UUID) == null){
			final BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(ALERT_LEVEL_CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, BluetoothGattCharacteristic.PERMISSION_WRITE);
			//characteristic.setValue(ALERT_LEVEL_CHARACTERISTIC_VALUE, ALERT_LEVEL_CHARACTERISTIC_FORMATTYPE, ALERT_LEVEL_CHARACTERISTIC_OFFSET);
			characteristic.setValue(ALERT_LEVEL_HIGH);
			immediateAlertService.addCharacteristic(characteristic);
		}
		else {
			//Notify user of added service(s) and save callback
			addProperty(returnObj, keyStatus, statusServiceExists);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			//callbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext.sendPluginResult(pluginResult);	// Added 7/8 instead of line above
			return;
		}
		
		// Add Immediate Alert service, notify user and save callback
		if(gattServer.addService(immediateAlertService)) {
			addProperty(returnObj, keyStatus, statusServiceAdded);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, returnObj);
			pluginResult.setKeepCallback(true);					// Save the callback so it can be invoked several times
			//callbackContext.sendPluginResult(pluginResult);
			serverRunningCallbackContext.sendPluginResult(pluginResult);	// Added 7/8 instead of line above
		}
		else {
			//Notify user of error adding service and save callback
			addProperty(returnObj, keyError, errorStartServer);
			addProperty(returnObj, keyMessage, logService);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR, returnObj);
			pluginResult.setKeepCallback(true);	
			serverRunningCallbackContext.sendPluginResult(pluginResult);
			return;
		}
		
		// Test
		//BluetoothAdapter bluetoothAdapter;
		//bluetoothAdapter = bluetoothManager.getAdapter();
		//BluetoothDevice device = bluetoothAdapter.getRemoteDevice("D8:35:DA:54:1E:55");
		//gattServer.connect(device, false);
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
