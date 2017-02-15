/*
* Copyright (C) 2015-2017 Sensible Solutions Sweden AB
*
*
* Cordova Plugin implementation for the Bluetooth GATT Profile server role.
*
* This class provides Bluetooth GATT server role functionality,
* allowing applications to create and advertise the Bluetooth
* Smart immediate alert service.
* 
*/

#import "GattServerPlugin.h"

//Plugin Name
NSString *const pluginName = @"gattserverplugin";

// Immediate Alert Service
NSString *const IMMEDIATE_ALERT_SERVICE_UUID = @"1802";			// Service UUID
NSString *const ALERT_LEVEL_CHAR_UUID = @"2A06";				// Characteristic UUID

// Object Keys
NSString *const keyStatus = @"status";
NSString *const keyError = @"error";
NSString *const keyMessage = @"message";
NSString *const keyIsBluetoothSharingAuthorized = @"isBluetoothSharingAuthorized";
	
//Status Types
NSString *const statusServiceAdded = @"serviceAdded";
NSString *const statusServiceExists = @"serviceAlreadyProvided";
NSString *const statusWriteRequest = @"characteristicWriteRequest";
//NSString *const statusConnectionState = @"serverConnectionState";
NSString *const statusPeripheralManager = @"serverState";
NSString *const statusAppSettings = @"appSettings";
NSString *const statusAlarmReseted =  @"alarmReseted";

// Error Types
NSString *const errorStartServer = @"startServer";
NSString *const errorNoPermission = @"noPermission";	// Added 2017-01-18
//NSString *const errorConnectionState = @"serverConnectionState";
NSString *const errorServiceAdded = @"serviceAdded";
NSString *const errorArguments = @"arguments";
//NSString *const errorPeripheralManager = @"serverState";
NSString *const errorServerState = @"serverState";
NSString *const errorServerStateOff = @"serverStateOff";
NSString *const errorServerStateUnsupported = @"serverStateUnsupported";
NSString *const errorServerStateUnauthorized = @"serverStateUnauthorized";
NSString *const errorWriteRequest = @"writeRequest";	// Added 2017-01-10
NSString *const errorReadRequest = @"readRequest";	// Added 2017-01-10

// Error Messages
NSString *const logServerAlreadyRunning = @"GATT server is already running";
NSString *const logService = @"Immediate Alert service could not be added";
NSString *const logConnectionState = @"Connection state changed with error";
NSString *const logNoPermission = @"No permission granted for local notifications";
NSString *const logStateOff = @"BLE is turned off for device";
NSString *const logStateOn = @"BLE is turned on for device";
NSString *const logStateUnsupported = @"BLE is not supported by device";
NSString *const logStateUnauthorized = @"BLE is turned off for app";
NSString *const logNoArgObj = @"Argument object can not be found";
NSString *const logRequestNotSupported = @"Request is not supported";	// Added 2017-01-10

// Settings keys
NSString *const KEY_ALERTS_SETTING = @"alerts";
NSString *const KEY_SOUND_SETTING = @"sound";
NSString *const KEY_VIBRATION_SETTING = @"vibration";
NSString *const KEY_LOG_SETTING = @"log";

NSTimeInterval const MIN_ALARM_INTERVAL = 3.0;		// Minimum allowed time interval in seconds between a previous alarm and a new alarm.
							// Any new alarms triggered in this time interval will be ignored.

@implementation GattServerPlugin

#pragma mark -
#pragma mark Interface

// Plugin actions
- (void)startServer:(CDVInvokedUrlCommand *)command
{
	// Check that BLE is supported and on
	if(peripheralManager != nil){
		switch ([peripheralManager state]) {
        		case CBPeripheralManagerStatePoweredOff: {
				// Notify user that BLE is off
				NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorServerStateOff, keyError, logStateOff, keyMessage, nil];
				CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
				[pluginResult setKeepCallbackAsBool:false];
				[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            			//break;
            			return;
			}
			case CBPeripheralManagerStateUnsupported: {
            			// Notify user that BLE is not supported by device
				NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorServerStateUnsupported, keyError, logStateUnsupported, keyMessage, nil];
				CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
				[pluginResult setKeepCallbackAsBool:false];
				[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            			//break;
            			return;
			}
			case CBPeripheralManagerStateUnauthorized: {
            			// Notify user that BLE is not on for the app
				NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorServerStateUnauthorized, keyError, logStateUnauthorized, keyMessage, nil];
				CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
				[pluginResult setKeepCallbackAsBool:false];
				[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            			//break;
            			return;
			}		
        		default: {
            			break;
			}
		}	
	}
	
	UIUserNotificationSettings *grantedSettings = [[UIApplication sharedApplication] currentUserNotificationSettings];
	if (grantedSettings.types == UIUserNotificationTypeNone) {
        	//NSLog(@"No notification permission granted");
        	//NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorStartServer, keyError, logNoPermission, keyMessage, nil]; // Removed 2017-01-18
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorNoPermission, keyError, logNoPermission, keyMessage, nil]; // Added 2018-01-18
        	CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
	 	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
		//return;	// Removed 2017-01-18
	}
	
	//If GATT server has been initialized or the GATT server is already running, don't start it again
	 //if (serverRunningCallback != nil)
	 if((peripheralManager != nil) && (serverRunningCallback != nil))
    {
	//NSLog(@"GATT server is already running");
	NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceExists, keyStatus, logServerAlreadyRunning, keyMessage, nil];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
        //NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorStartServer, keyError, logServerAlreadyRunning, keyMessage, nil];
        //CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
        //[pluginResult setKeepCallbackAsBool:false];
	[pluginResult setKeepCallbackAsBool:true];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        //UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"GATT server already running" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
        //[debugAlert show];
        iasInitialized = false;		// Added 2016-06-22
        return;
    }
    
    appSettingsAlert = nil;
    appSettingsSound = nil;
    appSettingsVibration = nil;
    appSettingsLog = nil;
    
	/*UIUserNotificationSettings *grantedSettings = [[UIApplication sharedApplication] currentUserNotificationSettings];
	if (grantedSettings.types == UIUserNotificationTypeNone) {
        	//NSLog(@"No notification permission granted");
        	//NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorStartServer, keyError, logNoPermission, keyMessage, nil]; // Removed 2017-01-18
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorNoPermission, keyError, logNoPermission, keyMessage, nil]; // Added 2018-01-18
        	CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
	 	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
		//return;	// Removed 2017-01-18
	}*/
	
	iasInitialized = false;
	//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"iasInitialized to false" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
	//[debugAlert show];
	
	 //Set the callback
    	serverRunningCallback = command.callbackId;
	
	// Initialize GATT server (if not has been initialized already), that is create a peripheral manager. This will call peripheralManagerDidUpdateState
	//self.peripheralManager = [[CBPeripheralManager alloc]initWithDelegate:self queue:nil];
	if (peripheralManager == nil){
		iasAdded = false;
		peripheralManager = [[CBPeripheralManager alloc]initWithDelegate:self queue:nil];
	}
	else if(!iasAdded) {
		// Try publish Immediate Alert service to the local peripheral’s GATT database if it isn't published already
		CBMutableService *service = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:IMMEDIATE_ALERT_SERVICE_UUID] primary:YES];
		CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID] properties:CBCharacteristicPropertyWriteWithoutResponse value:nil permissions:CBAttributePermissionsWriteable];
		service.characteristics = @[characteristic];
		[peripheralManager addService:service];
	}
	else {
	        NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceExists, keyStatus, nil];
	        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
	        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
	}
}

- (void)resetAlarm:(CDVInvokedUrlCommand *)command
{
	// Resets the Immediate Alert Service initialized flag.
	// Should be called after a client has disconnected since when a nRF8002 module connects to the GATT server running
	// Immediate Alert Service, it writes it's current alert level. This must not be interpreted as an alert.
	iasInitialized = false;
	NSDictionary *returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusAlarmReseted, keyStatus, nil];
	CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
	[pluginResult setKeepCallbackAsBool:false];
	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

// Alarms with appropiate sound etc
- (void) alarm:(NSString *)alertLevel deviceUUID:(NSString *)uuid
//- (void)alarm:(CDVInvokedUrlCommand *)command			// Used for manually calling and debuging instead of row above
{
	// Ignore alarm if not enough elapsed time since last alarm (to prevent responding to some
	// of the alarms triggered because of loose connection between clip contacts and sensor)
	if (alarmDate != nil){
		NSTimeInterval alarmInterval = [alarmDate timeIntervalSinceNow];
		alarmDate = [NSDate date];
		if (alarmInterval < MIN_ALARM_INTERVAL){
			return;
		}
	}
	else {
		alarmDate = [NSDate date];
	}
	
	// Show local notification if the app is in the background
	UIApplicationState currentState = [[UIApplication sharedApplication] applicationState];		// Added 2017-01-13
	if (currentState == UIApplicationStateBackground){		// If but not its code block added 2017-01-13
		if ([[UIApplication sharedApplication] respondsToSelector:@selector(currentUserNotificationSettings)]){			// Check it's iOS 8 and above
			UIUserNotificationSettings *grantedSettings = [[UIApplication sharedApplication] currentUserNotificationSettings];

			if (grantedSettings.types == UIUserNotificationTypeNone) {
				// Notifications are turned off completely for the app (removed 2017-01-13)
				//NSLog(@"No notification permission granted");
				//UIAlertView *notificationAlert = [[UIAlertView alloc] initWithTitle:@"SenseSoft Notifications" message:@"Notifications are currently not allowed. Please turn on notifications in settings app." delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
				//[notificationAlert show];
				//return;
			}
			/*else if (grantedSettings.types & UIUserNotificationTypeSound & UIUserNotificationTypeAlert & UIUserNotificationTypeBadge){
				//NSLog(@"Sound, alert and badge permissions ");
			}*/
			else if (grantedSettings.types & UIUserNotificationTypeAlert){
				//NSLog(@"Alert Permission Granted");
				UILocalNotification* localNotification = [[UILocalNotification alloc] init];
				// Specify after how many second the notification will be delivered
				//localNotification.fireDate = [NSDate dateWithTimeIntervalSinceNow:0];
				// Specify notification message text
				localNotification.alertBody = @"Incoming SenseSoft Mini alarm";
				// A short description of the reason for the alert (for apple watch) 
				localNotification.alertTitle = @"SenseSoft Notifications Mini";
				// Hide the alert button or slider
				localNotification.hasAction = false;
				// Specify timeZone for notification delivery
				localNotification.timeZone = [NSTimeZone defaultTimeZone];
				// Set the soundName property for the notification if notification sound is enabled
				if (grantedSettings.types & UIUserNotificationTypeSound){
					//localNotification.soundName = UILocalNotificationDefaultSoundName;
					//NSBundle* mainBundle = [NSBundle mainBundle];
					//localNotification.soundName = @"Resources/alarm.mp3";
					//localNotification.soundName = @"alarm.mp3";	// Works
					localNotification.soundName = @"crash_short.mp3";

					// Play sound manually from the main bundle if app is in foreground (because sound for local notifications are not played if the app is in the foreground)
					/*UIApplicationState currentState = [[UIApplication sharedApplication] applicationState];	// Section removed 2017-01-13
					if (currentState == UIApplicationStateActive) {
						if ([appSettingsVibration isEqualToString:@"on"])
							AudioServicesPlayAlertSound(alarmSound);	// If the user has configured the Settings application for vibration on ring, also invokes vibration (works)
						else
							AudioServicesPlaySystemSound(alarmSound);	// Works, no vibration
					}*/
				}
				// Increase app icon count by 1 when notification is sent if notification badge is enabled
				if (grantedSettings.types & UIUserNotificationTypeBadge)
					localNotification.applicationIconBadgeNumber = [[UIApplication sharedApplication] applicationIconBadgeNumber]+1; 
				//localNotification.applicationIconBadgeNumber = 1;
				// Show the local notification
				[[UIApplication sharedApplication] presentLocalNotificationNow:localNotification];
				// Schedule the local notification
				//[[UIApplication sharedApplication] scheduleLocalNotification:localNotification];
			}
		}
	}
	else {			// else and its code block added 2017-01-13
		// The app is in the foreground
		// Play sound manually from the main bundle if app is in foreground (because sound for local notifications are not played if the app is in the foreground)
		// Audio is played asynchronously so no need to play it in a background thread.
		if ([appSettingsVibration isEqualToString:@"on"])
			AudioServicesPlayAlertSound(alarmSound);	// If the user has configured the Settings application for vibration on ring, also invokes vibration (works)
		else
			AudioServicesPlaySystemSound(alarmSound);	// Works, no vibration
	}
	
	// Notify user and save callback
	if(serverRunningCallback != nil){
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusWriteRequest, keyStatus, uuid, @"device", ALERT_LEVEL_CHAR_UUID, @"characteristic", alertLevel, @"value", nil];
		CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
		[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
	}
}

// Set granted local notifications for app
- (void)setAlarmSettings:(CDVInvokedUrlCommand *)command
{
	NSDictionary* obj = [self getArgsObject:command.arguments];
	if ([self isNotArgsObject:obj :command])
        return;

	//appSettingsAlert = [command.arguments objectAtIndex:0];
	appSettingsAlert = [self getSetting:obj forKey:KEY_ALERTS_SETTING];
	//appSettingsSound = [command.arguments objectAtIndex:1];
	appSettingsSound = [self getSetting:obj forKey:KEY_SOUND_SETTING];
	//appSettingsVibration = [command.arguments objectAtIndex:2];
	appSettingsVibration = [self getSetting:obj forKey:KEY_VIBRATION_SETTING];
	//appSettingsLog= [command.arguments objectAtIndex:3];
	appSettingsLog = [self getSetting:obj forKey:KEY_LOG_SETTING];
	
	UIUserNotificationType types = UIUserNotificationTypeBadge;
	if ([appSettingsAlert isEqualToString:@"on"])
		types |= UIUserNotificationTypeAlert;
	if (![appSettingsSound isEqualToString:@"off"])
		types |= UIUserNotificationTypeSound;
	UIUserNotificationSettings *mySettings = [UIUserNotificationSettings settingsForTypes:types categories:nil];
	[[UIApplication sharedApplication] registerUserNotificationSettings:mySettings];
	
	CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];		// Added 2017-01-19
	[pluginResult setKeepCallbackAsBool:false];							// Added 2017-01-19
	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];		// Added 2017-01-19
}

// Get granted local notifications for app
- (void)getAlarmSettings:(CDVInvokedUrlCommand *)command
{
	// Notify user of settings
	NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusAppSettings, keyStatus, @"alert", appSettingsAlert, @"sound", appSettingsSound, @"vibration", appSettingsVibration, @"log", appSettingsLog, nil];
	CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
	[pluginResult setKeepCallbackAsBool:false];
	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

// Register for local notifications.
// In iOS 8 and later, apps that use either local (or remote notifications) must register the types of notifications they intend to deliver.
// The system then gives the user the ability to limit the types of notifications your app displays.
- (void)registerNotifications:(CDVInvokedUrlCommand *)command
{
	UIUserNotificationType types = UIUserNotificationTypeBadge | UIUserNotificationTypeSound | UIUserNotificationTypeAlert;
	UIUserNotificationSettings *mySettings = [UIUserNotificationSettings settingsForTypes:types categories:nil];
	[[UIApplication sharedApplication] registerUserNotificationSettings:mySettings];	// First time called, iOS presents a dialog that asks the user for permission to present the types of notifications the app registered
	
	CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];		// Added 2017-01-19
	[pluginResult setKeepCallbackAsBool:false];							// Added 2017-01-19
	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];		// Added 2017-01-19
}

// Check the app’s authorization status for sharing data while in the background state.
// Apps that specify to use Bluetooth background modes (central and/or peripheral) need
// bluetooth sharing to be enabled for the app in order to be able to process bluetooth
// related tasks while in the background.
- (void)isBluetoothSharingAuthorized:(CDVInvokedUrlCommand *)command
{
	NSMutableDictionary* returnObj = [NSMutableDictionary dictionary];
	if([CBPeripheralManager authorizationStatus] == CBPeripheralManagerAuthorizationStatusAuthorized)
		[returnObj setValue:[NSNumber numberWithBool:true] forKey:keyIsBluetoothSharingAuthorized];	// The app is authorized to share data using Bluetooth services while in the background state
	else
		[returnObj setValue:[NSNumber numberWithBool:false] forKey:keyIsBluetoothSharingAuthorized];	// Not authorized
		
	CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
	[pluginResult setKeepCallbackAsBool:false];
	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

// Sets the application badge number
- (void)setApplicationBadgeNumber:(CDVInvokedUrlCommand *)command	// Function added 2017-01-19
{
	//UIAlertView* debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug SSNMM" message:@"Hej" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
	//UIAlertView* debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug SSNMM" message:[@(*myNumber) stringValue] delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil]; 
	//[debugMessage show];
    	NSNumber *myNumber = [command.arguments objectAtIndex:0];
	[[UIApplication sharedApplication] setApplicationIconBadgeNumber:[myNumber intValue]];	// Also clears the notifications in the notification center
	
	//UIAlertView *debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug SSNMM" message:myNumber delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
	UIAlertView* debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug SSNMM" message:[myNumber stringValue] delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil]; 
	[debugMessage show];
	
	CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];		// Added 2017-01-19
	[pluginResult setKeepCallbackAsBool:false];							// Added 2017-01-19
	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];		// Added 2017-01-19
}


#pragma mark -
#pragma mark Delegates

// CBPeripheralManager Delegate Methods
-(void)peripheralManagerDidUpdateState:(CBPeripheralManager *)peripheral
{
    switch ([peripheral state]) {
        case CBPeripheralManagerStatePoweredOff: {
            //NSLog(@"BLE is turned off for device");
            		if(serverRunningCallback != nil){
				// Notify user that BLE is turned off
				//NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusPeripheralManager, keyError, logStatePoweredOff, keyMessage, nil];
				NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorServerStateOff, keyError, logStateOff, keyMessage, nil];
				CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
				//[pluginResult setKeepCallbackAsBool:true];	// Keep callback so that if turned off and then turned on again is working
				[pluginResult setKeepCallbackAsBool:false];
				[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
				serverRunningCallback = nil;			// "Stop" the GATT server
			}
            break;
		}
        case CBPeripheralManagerStatePoweredOn: {
            //NSLog(@"BLE is on");
            		// BLE is turned on for device
            		// Add Immediate Alert service if not already added and GATT server is running
            		if((!iasAdded) && (serverRunningCallback != nil)){
				// Publish Immediate Alert service to the local peripheral’s GATT database
				CBMutableService *service = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:IMMEDIATE_ALERT_SERVICE_UUID] primary:YES];
				//CBCharacteristicProperties properties = CBCharacteristicPropertyWriteWithoutResponse;
				//CBAttributePermissions permissions = CBAttributePermissionsWriteable;
				//CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:@"2A06"] properties:properties value:nil permissions:permissions];
				//CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID] properties:CBCharacteristicPropertyWriteWithoutResponse value:nil permissions:CBAttributePermissionsWriteEncryptionRequired];
				CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID] properties:CBCharacteristicPropertyWriteWithoutResponse value:nil permissions:CBAttributePermissionsWriteable];
				//service.characteristics = [NSArray arrayWithObject:[self createCharacteristic]];
				//service.characteristics = [NSArray arrayWithObject:[characteristic]];
				service.characteristics = @[characteristic];
				[peripheralManager addService:service];
				
				// Add Alert Notification Service if not already provided by the device (service not used by SenseSoft Mini)
				/*CBMutableService *service2 = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:@"1811"] primary:YES];
				CBMutableCharacteristic *characteristic2 = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:@"2a46"] properties:CBCharacteristicPropertyNotify value:nil permissions:CBAttributePermissionsReadable];
				unsigned char bytes[] = { 0xff};
	    			NSData *data = [NSData dataWithBytes:bytes length:1];
				CBMutableCharacteristic *characteristic3 = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:@"2a47"] properties:CBCharacteristicPropertyRead value:data permissions:CBAttributePermissionsReadable];
				CBMutableCharacteristic *characteristic4 = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:@"2a44"] properties:CBCharacteristicPropertyWrite value:nil permissions:CBAttributePermissionsWriteable];
				service2.characteristics = @[characteristic2,characteristic3,characteristic4];
				[peripheralManager addService:service2];*/
			}
			//else {
				// Notify user and save callback
				/*NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceExists, keyStatus, logServerAlreadyRunning, keyMessage, nil];
        			CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];[pluginResult setKeepCallbackAsBool:true];
				[pluginResult setKeepCallbackAsBool:true];
				[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
			}*/
            break;
        }
		case CBPeripheralManagerStateUnsupported: {
            //NSLog(@"BLE is not supported by device");
            		if(serverRunningCallback != nil){
				// Notify user and save callback
				NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorServerStateUnsupported, keyError, logStateUnsupported, keyMessage, nil];
				CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
				//[pluginResult setKeepCallbackAsBool:true];
				[pluginResult setKeepCallbackAsBool:false];
				[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
				serverRunningCallback = nil;			// "Stop" the GATT server
			}
            break;
		}
		case CBPeripheralManagerStateUnauthorized: {
            //NSLog(@"BLE is not on for app");
            		if(serverRunningCallback != nil){
				// Notify user and save callback
				NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorServerStateUnauthorized, keyError, logStateUnauthorized, keyMessage, nil];
				CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
				//[pluginResult setKeepCallbackAsBool:true];
				[pluginResult setKeepCallbackAsBool:false];
				[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
				serverRunningCallback = nil;			// "Stop" the GATT server
			}
            break;
		}		
        default: {
            break;
		}
    }
}

// Test if clip subscribes to the alert notification service
- (void)peripheralManager:(CBPeripheralManager *)peripheral central:(CBCentral *)central didUnsubscribeFromCharacteristic:(CBCharacteristic *)characteristic
{
	//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug Native" message:@"Unsubscribed!" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
	//[debugAlert show];
}

- (void)peripheralManager:(CBPeripheralManager *)peripheral central:(CBCentral *)central didSubscribeToCharacteristic:(CBCharacteristic *)characteristic
{
	//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug Native" message:@"Subscribed!" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
	//[debugAlert show];
}

-(void)peripheralManager:(CBPeripheralManager *)peripheral didAddService:(CBService *)service error:(NSError *)error
{
    if (error) {
    		if(serverRunningCallback != nil){
			 // Notify user and save callback
			NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorServiceAdded, keyError, logService, keyMessage, nil];
			CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
			//[pluginResult setKeepCallbackAsBool:true];
			[pluginResult setKeepCallbackAsBool:false];
			[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
			serverRunningCallback = nil;			// "Stop" the GATT server
			//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"didAddService error" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
	        	//[debugAlert show];
        	}
		
    }
    else {
    		if(serverRunningCallback != nil){
    			// Server is running
	    		iasAdded = true;
	        	// Notify user and save callback
			NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceAdded, keyStatus, nil];
			CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
			[pluginResult setKeepCallbackAsBool:true];
			[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
		}
    }
}

-(void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveReadRequest:(CBATTRequest *)request
{
   	// Read requests not supported/implemented
    	//[peripheralManager respondToRequest:request withResult:CBATTErrorSuccess];	// Removed 2017-01-10
	[peripheralManager respondToRequest:request withResult:CBATTErrorRequestNotSupported];	// Added 2017-01-10
	
    	//NSString *test = request.characteristic.UUID.UUIDString;
    	//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug Read Req" message:test delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
	//[debugAlert show];
	
	// Notify user and save callback
	if(serverRunningCallback != nil){	// if statement and it's code block added 2017-01-10
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorReadRequest, keyError, logRequestNotSupported, keyMessage, nil];
        	CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
	 	[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
	}
}

// Remote client characteristic write request
-(void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveWriteRequests:(NSArray *)requests
{
    	CBATTRequest *attributeRequest = [requests objectAtIndex:0];
	//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug Native" message:attributeRequest.characteristic.UUID.UUIDString delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
	//[debugAlert show];
	//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug Native" message:attributeRequest.central.identifier.UUIDString delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil]; // Test 2017-01-10
	//[debugAlert show];
	if ([attributeRequest.characteristic.UUID isEqual:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID]]) {
		// The central has send a write request of the alert level characteristic
		const uint8_t *data = [attributeRequest.value bytes];
		int alertLevel = data[0];
		NSMutableString *alertLevelParsed = [NSMutableString stringWithString:@""];
        	switch (alertLevel) {
		    case 0:	{
					[alertLevelParsed setString:@"No Alert"];
			break;
				}
		    case 1: {
					[alertLevelParsed setString:@"Mild Alert"];
			break;
				}
		    case 2: {
					[alertLevelParsed setString:@"High Alert"]; 
			break;
				}  
		    default: {
					[alertLevelParsed setString:@"Parse Error"];
			break;
				}
        	}
		// Debug dialog
		//UIAlertView *debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug" message:[NSString stringWithFormat: @"Immediate alert received with level: %@", alertLevelParsed] delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
		//[debugMessage show];
		if (!iasInitialized && alertLevel != 0){
			// The first alarm received after a nRF8002 module has connected for the first time to the GATT server or the alarm has been reseted by calling resetAlarm().
			iasInitialized = true;
			[self alarm:alertLevelParsed deviceUUID:attributeRequest.central.identifier.UUIDString];
			//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug 1" message:alertLevelParsed delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
			//[debugAlert show];
		}
		else if (iasInitialized){
			// When an Immediate Alert level is set to trigger on "activated" on the nRF8002, it sends "toggled" levels. That is, it sends "No Alert" on every second positive flank and the configured alert level on every other.
			// So interpret every write to this characteristic as an alarm.
			[self alarm:alertLevelParsed deviceUUID:attributeRequest.central.identifier.UUIDString];
			//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug 2" message:alertLevelParsed delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
			//[debugAlert show];
		}
		else {
			// Ignore first value(s) received. When a nRF8002 module connects to the GATT server running Immediate Alert Service, it writes it's current alert level (sometimes twice). This must not be interpreted as an alert.
			//UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug 0" message:alertLevelParsed delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
			//[debugAlert show];
		}
		
		// No need to respond to the write request since the it's of the type "request with no response"
	}
	else {		// else and it's code block added 2017-01-10
		[peripheralManager respondToRequest:attributeRequest withResult:CBATTErrorRequestNotSupported];
		// Notify user and save callback
		if(serverRunningCallback != nil){
			NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorWriteRequest, keyError, logRequestNotSupported, keyMessage, nil];
        		CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
			[pluginResult setKeepCallbackAsBool:true];
	 		[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
		}
	}
}

// Not working, that is is not called when a remote central has disconnected (since there is subscription for a characteristic
/*- (void)peripheralManager:(CBPeripheralManager *)peripheral central:(CBCentral *)central didUnsubscribeFromCharacteristic:(CBCharacteristic *)characteristic
{
	UIAlertView *debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"Remote central unsubsribed to a characteristic." delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
	[debugMessage show];
	// If this works, this CBPeripheralManagerDelegate is called when a remote central has disconnected ( - (void)peripheralManager:(CBPeripheralManager *)peripheral central:(CBCentral *)central didSubscribeToCharacteristic:(CBCharacteristic *)characteristic when connected)
	if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID]]){
		iasInitialized = false;
		UIAlertView *debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"Remote central unsubsribed to alert level characteristic." delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
		[debugMessage show];
	}
}*/


// Application delegates

// Called when app has started (also when "cold starting" the app by clicking on a local notification)
//- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions
- (void) didFinishLaunchingWithOptions:(NSNotification*) notification
{
	/*NSDictionary* launchOptions = [notification userInfo];
    	UILocalNotification* localNotification;
    	localNotification = [launchOptions objectForKey:
                         UIApplicationLaunchOptionsLocalNotificationKey];
    	if (localNotification) {
    	 	[self didReceiveLocalNotification:
         	[NSNotification notificationWithName:CDVLocalNotification
                                       object:localNotification]];
    	}*/
	UIAlertView *debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug SSNM" message:@"didFinishLaunchingWithOptions called!" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil]; 
	[debugMessage show];
	
    	[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];	// Also clears the notifications in the notification center
    	//return YES;
}

// Called after a local notification was received (if the app is the foreground
// or after the user has clicked on the notification, with an alert/action button or slider, when app was in the background)
- (void) didReceiveLocalNotification:(UILocalNotification*) notification
{ 
	// If the app is running while the notification is delivered, there is no alert displayed on screen and no sound played.
	// Manually display alert message and play sound.
	//UIApplicationState currentState = [[UIApplication sharedApplication] applicationState];
	//if (currentState == UIApplicationStateActive) { 
		// Play sound from the main bundle (because sound for local notifications are not played if the app is in the foreground)
		//AudioServicesPlaySystemSound(alarmSound);	// Works, no vibration
		//AudioServicesPlayAlertSound(alarmSound);	// If the user has configured the Settings application for vibration on ring, also invokes vibration (works)
		//UIAlertView *debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"You have a notification, please check"delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil]; 
		//[debugMessage show];
	//} 
	//application.applicationIconBadgeNumber = 0; 
	 //[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];		// Also clears the notifications
	
	/*UIAlertView *debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug SSNM" message:@"didRegisterUserNotificationSettings called!"delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil]; 
	[debugMessage show];
	UIApplicationState currentState = [[UIApplication sharedApplication] applicationState];		// Added 2017-01-18
	if (currentState == UIApplicationStateInactive) { 	// If statement and its code added 2017-01-18
		// User clicked on notification while the app was in the background
		[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];		// Also clears the notifications from notification center
	}*/
}

// Called when notification registration is completed (registration for local notifications is needed in IOS >= 8.0)
- (void) didRegisterUserNotificationSettings:(UIUserNotificationSettings*) settings
{
	// Not implemented
	
	//UIAlertView *debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug SSNM" message:@"didRegisterUserNotificationSettings called!"delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil]; 
	//[debugMessage show];
}

#pragma mark -
#pragma mark General helpers

-(NSDictionary*) getArgsObject:(NSArray *)args
{
    if (args == nil)
        return nil;
    if (args.count != 1)
        return nil;

    NSObject* arg = [args objectAtIndex:0];

    if (![arg isKindOfClass:[NSDictionary class]])
        return nil;

    return (NSDictionary *)[args objectAtIndex:0];
}

-(NSString*) getSetting:(NSDictionary *)obj forKey:(NSString *)key
{
    NSString* setting = [obj valueForKey:key];

    if (setting == nil)
        return nil;
    if (![setting isKindOfClass:[NSString class]])
        return nil;

    return setting;
}

- (BOOL) isNotArgsObject:(NSDictionary*) obj :(CDVInvokedUrlCommand *)command
{
    if (obj != nil)
        return false;

    NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorArguments, keyError, logNoArgObj, keyMessage, nil];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
    [pluginResult setKeepCallbackAsBool:false];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];

    return true;
}

#pragma mark -
#pragma mark CDVPlugin delegates

// Called after plugin is initialized
- (void) pluginInitialize
{
	// Registers obervers
    	NSNotificationCenter* center = [NSNotificationCenter defaultCenter];

    	//eventQueue = [[NSMutableArray alloc] init];

    	[center addObserver:self
        	selector:@selector(didReceiveLocalNotification:)
              	name:CDVLocalNotification
               	object:nil];

    	[center addObserver:self
               	selector:@selector(didFinishLaunchingWithOptions:)
              	name:UIApplicationDidFinishLaunchingNotification
               	object:nil];

    	/*[center addObserver:self
               	selector:@selector(didRegisterUserNotificationSettings:)
              	name:UIApplicationRegisterUserNotificationSettings
               	object:nil];*/
               	
        // Set up sound from main bundle to be played during alarms when the app is in the foreground
        //AudioServicesCreateSystemSoundID((__bridge CFURLRef) [NSURL fileURLWithPath :  [[NSBundle mainBundle] pathForResource:@"alarm" ofType:@"mp3"]], &alarmSound);
        AudioServicesCreateSystemSoundID((__bridge CFURLRef) [NSURL fileURLWithPath :  [[NSBundle mainBundle] pathForResource:@"crash_short" ofType:@"mp3"]], &alarmSound);


	// Test 2017-02-15 (for testing AVAudioPlayer)
	// Configure and activate the app’s audio session
	AVAudioSession *session = [AVAudioSession sharedInstance];
	[session setCategory:AVAudioSessionCategoryPlayback error:nil];
	//[session setPreferredIOBufferDuration:.005 error:nil];	// 0.005s is minimum allowed (default is about 20ms). Change/lower if experiencing high latency on playback
	[session setActive:YES error:nil];
}

// Called when the system is about to start resuming a previous activity (application is put in the background)
/*- (void) onPause
{
	// NOTE: if you want to use this, make sure you add the corresponding notification handler in CDVPlugin.m
}
// Called when the activity will start interacting with the user (application is retrieved from the background)
- (void) onResume
{
	// NOTE: if you want to use this, make sure you add the corresponding notification handler in CDVPlugin.m
}*/

// Called before app terminates
- (void) onAppTerminate
{
	[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];	// Also clears the notifications in the notification center
    
    	// Call the following function when the sound is no longer used
	// (must be done AFTER the sound is done playing)
	AudioServicesDisposeSystemSoundID(alarmSound);
	
	//CBMutableService *service = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:IMMEDIATE_ALERT_SERVICE_UUID] primary:YES];
	//[peripheralManager removeService:service];
	// Remove all, by the app, published services from the local GATT database.
	// Removes only the instance of the service that your app added to the database (using the addService: method).
	//[peripheralManager removeAllServices];
	 [super onAppTerminate];
}

// Called when plugin resets (navigates to a new page or refreshes)
/*- (void) onReset
{
	// Not implemented
	//[super onReset];
}*/

@end
