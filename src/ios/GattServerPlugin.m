/*
* Copyright (C) 2015 Sensible Solutions Sweden AB
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
	
//Status Types
NSString *const statusServiceAdded = @"serviceAdded";
NSString *const statusServiceExists = @"serviceAlreadyProvided";
NSString *const statusWriteRequest = @"characteristicWriteRequest";
//NSString *const statusConnectionState = @"serverConnectionState";
NSString *const statusPeripheralManager = @"serverState";
NSString *const statusAppSettings = @"appSettings";

// Error Types
NSString *const errorStartServer = @"startServer";
//NSString *const errorConnectionState = @"serverConnectionState";
NSString *const errorServiceAdded = @"serviceAdded";
NSString *const errorArguments = @"arguments";

// Error Messages
NSString *const logServerAlreadyRunning = @"GATT server is already running";
NSString *const logService = @"Immediate Alert service could not be added";
NSString *const logConnectionState = @"Connection state changed with error";
NSString *const logNoPermission = @"No permission granted for local notifications";
NSString *const logStatePoweredOff = @"BLE is turned for device";
NSString *const logStateUnsupported = @"BLE is not supported by device";
NSString *const logStateUnauthorized = @"BLE is turned off for app";
NSString *const logNoArgObj = @"Argument object can not be found";

// Settings keys
NSString *const KEY_ALERTS_SETTING = @"alerts";
NSString *const KEY_SOUND_SETTING = @"sound";
NSString *const KEY_VIBRATION_SETTING = @"vibration";
NSString *const KEY_LOG_SETTING = @"log";



@implementation GattServerPlugin

#pragma mark -
#pragma mark Interface

// Plugin actions
- (void)startServer:(CDVInvokedUrlCommand *)command
{
	//If the GATT server is already running, don't start it again
	 if (serverRunningCallback != nil)
    {
	//NSLog(@"GATT server is already running");
        NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorStartServer, keyError, logServerAlreadyRunning, keyMessage, nil];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
        //[pluginResult setKeepCallbackAsBool:false];
		[pluginResult setKeepCallbackAsBool:true];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"GATT server already running" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
        [debugAlert show];
        return;
    }
    
    appSettingsAlert = nil;
    appSettingsSound = nil;
    appSettingsVibration = nil;
    appSettingsLog = nil;
    UIUserNotificationSettings *grantedSettings = [[UIApplication sharedApplication] currentUserNotificationSettings];
    
	if (grantedSettings.types == UIUserNotificationTypeNone) {
        //NSLog(@"No notification permission granted");
        NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: errorStartServer, keyError, logNoPermission, keyMessage, nil];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
		return;
	}
	
	iasInitialized = false;
	UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"iasInitialized to false" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
        [debugAlert show];
	
	 //Set the callback
    serverRunningCallback = command.callbackId;
	
	// Init GATT server, that is create a peripheral manager. This will call peripheralManagerDidUpdateState
	//self.peripheralManager = [[CBPeripheralManager alloc]initWithDelegate:self queue:nil];
	peripheralManager = [[CBPeripheralManager alloc]initWithDelegate:self queue:nil];
	
}

// Action function just to test local notifications
- (void)alarm: (NSString *) alertLevel
//- (void)alarm:(CDVInvokedUrlCommand *)command			// Used for manually calling and debuging instead of row above
{
	// Show local notification
	if ([[UIApplication sharedApplication] respondsToSelector:@selector(currentUserNotificationSettings)]){			// Check it's iOS 8 and above
		UIUserNotificationSettings *grantedSettings = [[UIApplication sharedApplication] currentUserNotificationSettings];

    		if (grantedSettings.types == UIUserNotificationTypeNone) {
        		//NSLog(@"No notification permission granted");
        		UIAlertView *notificationAlert = [[UIAlertView alloc] initWithTitle: @"SenseSoft Notifications" message:@"Notifications is currently not allowed. Please turn on notifications in settings app." delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
        		[notificationAlert show];
				return;
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
				localNotification.soundName = @"alarm.mp3";	// Works
				
				// Play sound manually from the main bundle if app is in foreground (because sound for local notifications are not played if the app is in the foreground)
				UIApplicationState currentState = [[UIApplication sharedApplication] applicationState];
				if (currentState == UIApplicationStateActive) {
					if ([appSettingsVibration isEqualToString:@"on"])
						AudioServicesPlayAlertSound(alarmSound);	// If the user has configured the Settings application for vibration on ring, also invokes vibration (works)
					else
						AudioServicesPlaySystemSound(alarmSound);	// Works, no vibration
				} 
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
	
	// Notify user and save callback
	NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusWriteRequest, keyStatus, @"NA", @"device", ALERT_LEVEL_CHAR_UUID, @"characteristic", alertLevel, @"value", nil];
	CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
	[pluginResult setKeepCallbackAsBool:true];
	[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
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
}

// Get granted local notifications for app
- (void)getAlarmSettings:(CDVInvokedUrlCommand *)command
{
	// Notify user of settings and save callback
	NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusAppSettings, keyStatus, @"alert", appSettingsAlert, @"sound", appSettingsSound, @"vibration", appSettingsVibration, @"log", appSettingsLog, nil];
	CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
	[pluginResult setKeepCallbackAsBool:true];
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
}

#pragma mark -
#pragma mark Delegates

// CBPeripheralManager Delegate Methods
-(void)peripheralManagerDidUpdateState:(CBPeripheralManager *)peripheral
{
    switch ([peripheral state]) {
        case CBPeripheralManagerStatePoweredOff: {
            //NSLog(@"BLE is turned off for device");
			// Notify user and save callback
			NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusPeripheralManager, keyError, logStatePoweredOff, keyMessage, nil];
			CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
			//[pluginResult setKeepCallbackAsBool:true];
			[pluginResult setKeepCallbackAsBool:false];
			[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
			serverRunningCallback = nil;
            break;
		}
        case CBPeripheralManagerStatePoweredOn: {
            //NSLog(@"BLE is on");
			// Add Immediate Alert service if not already provided by the device
			CBMutableService *service = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:IMMEDIATE_ALERT_SERVICE_UUID] primary:YES];
			//CBCharacteristicProperties properties = CBCharacteristicPropertyWriteWithoutResponse;
			//CBAttributePermissions permissions = CBAttributePermissionsWriteable;
			//CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:@"2A06"] properties:properties value:nil permissions:permissions];
			CBMutableCharacteristic *characteristic = [[CBMutableCharacteristic alloc]initWithType:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID] properties:CBCharacteristicPropertyWriteWithoutResponse value:nil permissions:CBAttributePermissionsWriteable];
			//service.characteristics = [NSArray arrayWithObject:[self createCharacteristic]];
			//service.characteristics = [NSArray arrayWithObject:[characteristic]];
			service.characteristics = @[characteristic];
			[peripheralManager addService:service];
			
            break;
        }
		case CBPeripheralManagerStateUnsupported: {
            //NSLog(@"BLE is not supported by device");
			// Notify user and save callback
			NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusPeripheralManager, keyError, logStateUnsupported, keyMessage, nil];
			CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
			//[pluginResult setKeepCallbackAsBool:true];
			[pluginResult setKeepCallbackAsBool:false];
			[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
			serverRunningCallback = nil;
            break;
		}
		case CBPeripheralManagerStateUnauthorized: {
            //NSLog(@"BLE is not on for app");
			// Notify user and save callback
			NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusPeripheralManager, keyError, logStateUnauthorized, keyMessage, nil];
			CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
			//[pluginResult setKeepCallbackAsBool:true];
			[pluginResult setKeepCallbackAsBool:false];
			[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
			serverRunningCallback = nil;
            break;
		}		
        default: {
            break;
		}
    }
}

-(void)peripheralManager:(CBPeripheralManager *)peripheral didAddService:(CBService *)service error:(NSError *)error
{
    if (error) {
		 // Notify user and save callback
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceAdded, keyError, logService, keyMessage, nil];
		CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
		//[pluginResult setKeepCallbackAsBool:true];
		[pluginResult setKeepCallbackAsBool:false];
		[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
		serverRunningCallback = nil;
		UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"didAddService error" delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
        	[debugAlert show];
		
    }
    else {
        // Notify user and save callback
		NSDictionary* returnObj = [NSDictionary dictionaryWithObjectsAndKeys: statusServiceAdded, keyStatus, nil];
		CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnObj];
		[pluginResult setKeepCallbackAsBool:true];
		[self.commandDelegate sendPluginResult:pluginResult callbackId:serverRunningCallback];
    }
}

-(void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveReadRequest:(CBATTRequest *)request
{
    // Not implemented
}

// Remote client characteristic write request
-(void)peripheralManager:(CBPeripheralManager *)peripheral didReceiveWriteRequests:(NSArray *)requests
{
    CBATTRequest *attributeRequest = [requests objectAtIndex:0];
	if ([attributeRequest.characteristic.UUID isEqual:[CBUUID UUIDWithString:ALERT_LEVEL_CHAR_UUID]]) {
		//NSLog(@"Alert Level is: %d",alertLevel);
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
		if (!iasInitialized){
			// Ignore first value received. When a nRF8002 module connects to the GATT server running Immediate Alert Service, it writes it's current alert level. This must not be interpreted as an alert.
			iasInitialized = true;
			UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug 1" message:alertLevelParsed delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
			[debugAlert show];
			return;
		}
		
		// When an Immediate Alert level is set to trigger on "activated" on the nRF8002, it sends "toggled" levels. That is, it sends "No Alert" on every second positive flank and the configured alert level on every other.
		// So interpret every write to this characteristic as an alarm
		[self alarm:alertLevelParsed];
		UIAlertView *debugAlert = [[UIAlertView alloc] initWithTitle: @"Debug 2" message:alertLevelParsed delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil];
		 [debugAlert show];
		//[self alarm];
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

// Called when app has started (by clicking on a local notification)
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
    	[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];	// Also clears the notifications
    	//return YES;
}

// Called after a local notification was received (if the app is the foreground)
- (void) didReceiveLocalNotification:(UILocalNotification*) notification
{ 
	// If the app is running while the notification is delivered, there is no alert displayed on screen and no sound played.
	// Manually display alert message and play sound.
	UIApplicationState currentState = [[UIApplication sharedApplication] applicationState];
	if (currentState == UIApplicationStateActive) { 
		// Play sound from the main bundle (because sound for local notifications are not played if the app is in the foreground)
		//AudioServicesPlaySystemSound(alarmSound);	// Works, no vibration
		//AudioServicesPlayAlertSound(alarmSound);	// If the user has configured the Settings application for vibration on ring, also invokes vibration (works)
		UIAlertView *debugMessage = [[UIAlertView alloc] initWithTitle: @"Debug" message:@"You have a notification, please check"delegate:self cancelButtonTitle:@"OK" otherButtonTitles:nil]; 
		[debugMessage show];
	} 
	//application.applicationIconBadgeNumber = 0; 
	 //[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];		// Also clears the notifications
}

// Called when notification registration is completed (registration for local notifications is needed in IOS >= 8.0)
- (void) didRegisterUserNotificationSettings:(UIUserNotificationSettings*) settings
{
	// Not implemented
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
        AudioServicesCreateSystemSoundID((__bridge CFURLRef) [NSURL fileURLWithPath :  [[NSBundle mainBundle] pathForResource:@"alarm" ofType:@"mp3"]], &alarmSound);
}

// Called before app terminates
- (void) onAppTerminate
{
	[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];	// Also clears the notifications
    
    	// Call the following function when the sound is no longer used
	// (must be done AFTER the sound is done playing)
	AudioServicesDisposeSystemSoundID(alarmSound);
	
	//CBMutableService *service = [[CBMutableService alloc] initWithType:[CBUUID UUIDWithString:IMMEDIATE_ALERT_SERVICE_UUID] primary:YES];
	//[peripheralManager removeService:service];
	// Remove all, by the app, published services from the local GATT database.
	// Removes only the instance of the service that your app added to the database (using the addService: method).
	//[peripheralManager removeAllServices];
}

// Called when plugin resets (navigates to a new page or refreshes)
- (void) onReset
{
	// Not implemented
}

@end
